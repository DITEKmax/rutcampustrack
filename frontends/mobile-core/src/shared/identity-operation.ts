/** One authenticated client owns these transient intents; nothing is persisted. */
export class IdentityOperationError extends Error {
  constructor(
    readonly state: 'PENDING' | 'UNKNOWN' | 'REJECTED' | 'FAILED_SAFE',
    readonly operationId: string | null = null,
  ) {
    super(
      state === 'PENDING'
        ? 'Сервер выполняет действие. Проверь результат чуть позже: повторного действия не будет.'
        : state === 'UNKNOWN'
          ? 'Результат пока неизвестен. Проверь его с тем же номером операции перед новым действием.'
          : 'Действие не выполнено. Обнови данные и подтверди новое действие.',
    )
    this.name = 'IdentityOperationError'
  }
}

interface Intent {
  readonly id: string
  readonly path: string
  readonly fingerprint: string
  init: RequestInit
  state: 'PENDING' | 'UNKNOWN'
  retryAt: number
  readonly retainUntilAcknowledged: boolean
  completed: boolean
}
export interface IdentityOperationResult {
  readonly path: string
  readonly response: Response
}
type Transport = (path: string, init?: RequestInit) => Promise<Response>

/** Bounded status checks and exact, same-key replay through the existing authenticated transport. */
export class IdentityOperations {
  private intent: Intent | null = null
  private active: Promise<IdentityOperationResult> | null = null

  constructor(
    private readonly transport: Transport,
    private readonly assertCurrent: () => void,
    private readonly pause: (milliseconds: number) => Promise<void> = (milliseconds) =>
      new Promise((resolve) => setTimeout(resolve, milliseconds)),
  ) {}

  get pending(): boolean {
    this.guard()
    return this.intent !== null
  }

  acknowledge(): void {
    this.guard()
    if (this.intent?.completed) this.intent = null
  }

  private clear(): void {
    this.intent = null
    this.active = null
  }

  async run(path: string, init: RequestInit, id: string = crypto.randomUUID()): Promise<Response> {
    this.guard()
    const fingerprint = intentFingerprint(path, init)
    if (this.intent && this.intent.fingerprint !== fingerprint)
      throw new IdentityOperationError(this.intent.state)
    if (!this.intent) {
      if (!/^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/.test(id))
        throw new Error('Некорректный номер операции.')
      const headers = new Headers(init.headers)
      headers.set('Idempotency-Key', id)
      // Navigation may stop observing the request, but must not discard its accepted intent.
      const detached = { ...init }
      delete detached.signal
      this.intent = {
        id,
        path,
        fingerprint,
        init: { ...detached, headers },
        state: 'UNKNOWN',
        retryAt: 0,
        retainUntilAcknowledged: Boolean(init.signal),
        completed: false,
      }
      return (await this.execute(false)).response.clone()
    }
    return (await this.execute(true)).response.clone()
  }

  async resume(): Promise<IdentityOperationResult> {
    this.guard()
    if (!this.intent) throw new Error('Нет незавершённого действия.')
    const result = await this.execute(true)
    return { ...result, response: result.response.clone() }
  }

  async inspect(
    operationId: string,
  ): Promise<'PENDING' | 'SUCCEEDED' | 'REJECTED' | 'FAILED_SAFE'> {
    if (!/^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/.test(operationId))
      throw new Error('Некорректный номер операции.')
    const response = await this.send(`/api/academic/identity-operations/${operationId}`, {
      cache: 'no-store',
    })
    const value = (await response.json()) as unknown
    this.guard()
    return operationReceipt(value, operationId, response).status
  }

  private guard(): void {
    try {
      this.assertCurrent()
    } catch (cause) {
      this.clear()
      throw cause
    }
  }

  private execute(resuming: boolean): Promise<IdentityOperationResult> {
    if (this.active) return this.active
    const intent = this.intent!
    const work = this.finish(intent, resuming)
    this.active = work
    void work
      .finally(() => {
        if (this.active === work) this.active = null
      })
      .catch(() => {})
    return work
  }

  private async send(path: string, init: RequestInit): Promise<Response> {
    this.guard()
    const controller = new AbortController()
    let rejectDeadline!: (reason: Error) => void
    const deadline = new Promise<never>((_resolve, reject) => {
      rejectDeadline = reject
    })
    const timeout = setTimeout(() => {
      controller.abort()
      rejectDeadline(new DOMException('Identity operation request timed out', 'AbortError'))
    }, 10_000)
    try {
      const snapshot = async (): Promise<Response> => {
        const response = await this.transport(path, { ...init, signal: controller.signal })
        this.guard()
        const intent = this.intent
        const location = response.headers.get('Location')
        if (
          response.status === 202 &&
          intent &&
          response.headers.get('X-Operation-Id') === intent.id &&
          (!location || validLocation(location, intent.id))
        ) {
          intent.state = 'PENDING'
          this.forgetPassword(intent)
        }
        // Keep the same deadline through receipt and final-body reads. Consumers receive
        // a buffered response, so a stalled network body cannot keep their screen busy.
        const body = response.status === 204 ? null : await response.arrayBuffer()
        this.guard()
        return new Response(body, {
          status: response.status,
          statusText: response.statusText,
          headers: response.headers,
        })
      }
      return await Promise.race([snapshot(), deadline])
    } finally {
      clearTimeout(timeout)
    }
  }

  private async finish(intent: Intent, resuming: boolean): Promise<IdentityOperationResult> {
    try {
      if (resuming && Date.now() < intent.retryAt) throw new IdentityOperationError(intent.state)
      let response: Response | null = null
      if (!resuming || intent.completed) response = await this.send(intent.path, intent.init)
      for (let checks = 0; checks < 3; checks += 1) {
        if (response) {
          if (response.status !== 202) {
            return this.complete(intent, response)
          }
          const receipt = (await response.json()) as unknown
          this.guard()
          this.readStatus(intent, receipt, response)
        }
        if (intent.retryAt > Date.now()) {
          const delay = intent.retryAt - Date.now()
          // Do not hold the screen busy for the server's potentially long retry interval.
          if (delay > 2_000) throw new IdentityOperationError(intent.state)
          await this.pause(delay)
        }
        this.guard()
        let status: Response
        try {
          status = await this.send(`/api/academic/identity-operations/${intent.id}`, {
            cache: 'no-store',
          })
        } catch (cause) {
          if (httpStatus(cause) !== 404 || intent.state !== 'UNKNOWN') throw cause
          response = await this.send(intent.path, intent.init)
          continue
        }
        const receipt = (await status.json()) as unknown
        this.guard()
        const state = this.readStatus(intent, receipt, status)
        if (state === 'SUCCEEDED') {
          // The original endpoint supplies the original body type and fresh authorized credentials.
          response = await this.send(intent.path, intent.init)
          if (response.status !== 202) {
            return this.complete(intent, response)
          }
        } else response = null
      }
      if (response && response.status !== 202) return this.complete(intent, response)
      throw new IdentityOperationError(intent.state)
    } catch (cause) {
      this.guard()
      if (cause instanceof IdentityOperationError) throw cause
      // An observation failure, including 4xx, cannot establish that an earlier
      // same-key attempt had no effect. Only a durable terminal receipt releases it.
      intent.completed = false
      throw new IdentityOperationError(intent.state)
    }
  }

  private complete(intent: Intent, response: Response): IdentityOperationResult {
    this.guard()
    const id = response.headers.get('X-Operation-Id')
    if (id && id !== intent.id) throw new Error('Invalid operation response')
    intent.completed = true
    intent.state = 'PENDING'
    this.forgetPassword(intent)
    if (!intent.retainUntilAcknowledged && this.intent === intent) this.intent = null
    return { path: intent.path, response }
  }

  private forgetPassword(intent: Intent): void {
    if (typeof intent.init.body !== 'string') return
    const body = JSON.parse(intent.init.body) as Record<string, unknown>
    delete body.password
    intent.init = { ...intent.init, body: JSON.stringify(body) }
  }

  private readStatus(intent: Intent, value: unknown, response: Response): string {
    const receipt = operationReceipt(value, intent.id, response)
    const status = receipt.status
    intent.state = 'PENDING'
    // Password is only an initial proof; never keep or replay it after confirmed acceptance.
    this.forgetPassword(intent)
    if (status === 'REJECTED' || status === 'FAILED_SAFE') {
      if (this.intent === intent) this.intent = null
      throw new IdentityOperationError(status, intent.id)
    }
    const retry = Number(response.headers.get('Retry-After') ?? receipt.retryAfterSeconds)
    intent.retryAt =
      Date.now() + Math.max(0, Math.min(30, Number.isFinite(retry) ? retry : 2)) * 1_000
    return String(status)
  }
}

function operationReceipt(
  value: unknown,
  id: string,
  response: Response,
): {
  status: 'PENDING' | 'SUCCEEDED' | 'REJECTED' | 'FAILED_SAFE'
  retryAfterSeconds: number
} {
  if (typeof value !== 'object' || value === null) throw new Error('Invalid operation receipt')
  const receipt = value as Record<string, unknown>
  if (
    receipt.operationId !== id ||
    !['PENDING', 'SUCCEEDED', 'REJECTED', 'FAILED_SAFE'].includes(String(receipt.status)) ||
    !Number.isInteger(receipt.retryAfterSeconds) ||
    Number(receipt.retryAfterSeconds) < 0 ||
    Number(receipt.retryAfterSeconds) > 30 ||
    (response.headers.has('X-Operation-Id') && response.headers.get('X-Operation-Id') !== id)
  )
    throw new Error('Invalid operation receipt')
  const location = response.headers.get('Location')
  if (location && !validLocation(location, id)) throw new Error('Invalid operation location')
  return {
    status: receipt.status as 'PENDING' | 'SUCCEEDED' | 'REJECTED' | 'FAILED_SAFE',
    retryAfterSeconds: Number(receipt.retryAfterSeconds),
  }
}

function intentFingerprint(path: string, init: RequestInit): string {
  const body =
    typeof init.body === 'string' ? (JSON.parse(init.body) as Record<string, unknown>) : {}
  delete body.password
  delete body.operationId
  return JSON.stringify([path, init.method, body])
}
function httpStatus(cause: unknown): number | null {
  if (typeof cause !== 'object' || cause === null || !('response' in cause)) return null
  return cause.response instanceof Response ? cause.response.status : null
}
function validLocation(location: string, id: string): boolean {
  const paths = [`/academic/identity-operations/${id}`, `/api/academic/identity-operations/${id}`]
  if (paths.includes(location)) return true
  if (typeof globalThis.location === 'undefined') return false
  return paths.some((path) => location === `${globalThis.location.origin}${path}`)
}
