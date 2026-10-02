import { StaleSessionGenerationError } from '../../shared/session-owner'

export type HomeworkBindingMode = 'LESSON' | 'DATE'
export interface HeadmanHomeworkSubject { readonly id: number; readonly name: string }
export interface HeadmanHomeworkHistory {
  readonly id: number
  readonly revision: number
  readonly actorId: number | null
  readonly occurredAt: string
  readonly action: string
  readonly before: unknown
  readonly after: unknown
}

export interface HeadmanHomeworkSemester {
  readonly id: number
  readonly name: string
  readonly dateFrom: string | null
  readonly dateTo: string | null
  readonly active: boolean
}

export interface HeadmanManagedHomework {
  readonly id: number
  readonly title: string
  readonly description: string | null
  readonly link: string | null
  readonly subjectId: number
  readonly groupId: number
  readonly semesterId: number
  readonly publishedBy: number | null
  readonly lessonDate: string
  readonly lessonNumber: number | null
  readonly bindingMode: HomeworkBindingMode
  readonly revision: number | null
  readonly archived: boolean
}

export interface HeadmanHomeworkCreateInput {
  readonly title: string
  readonly description?: string | null
  readonly link?: string | null
  readonly subjectId: number
  readonly groupId: number
  readonly semesterId: number
  readonly lessonDate: string
  readonly lessonNumber: number | null
  readonly bindingMode: HomeworkBindingMode
  readonly requestKey: string
}

export interface HeadmanHomeworkUpdateInput {
  readonly title: string
  readonly description?: string | null
  readonly link?: string | null
  readonly requestKey: string
  readonly expectedRevision: number
}

export interface HeadmanHomeworkApiOptions {
  accessToken: () => string | null
  onUnauthorized?: () => Promise<void>
  assertCurrent?: () => void
  fetcher?: typeof fetch
}

export class HeadmanHomeworkApiError extends Error {
  constructor(
    readonly response: Response,
    readonly problem: unknown,
  ) {
    super(problemDetail(problem) ?? `HTTP ${response.status}`)
    this.name = 'HeadmanHomeworkApiError'
  }
}

export class HeadmanHomeworkApi {
  private readonly fetcher: typeof fetch

  constructor(private readonly options: HeadmanHomeworkApiOptions) {
    this.fetcher = options.fetcher ?? ((input, init) => globalThis.fetch(input, init))
  }

  async activeSemester(): Promise<HeadmanHomeworkSemester | null> {
    const semesters = await this.requestPaged(
      (page) => `/api/academic/semesters?page=${page}&size=100`,
      (value) => embeddedItems(value, ['semesterResponseList']).map(normalizeSemester).filter(isValue),
    )
    return semesters.find((semester) => semester.active) ?? null
  }

  listHomeworks(groupId: number, semesterId: number): Promise<readonly HeadmanManagedHomework[]> {
    assertPositiveInteger(groupId, 'groupId')
    assertPositiveInteger(semesterId, 'semesterId')
    return this.requestPaged(
      (page) => `/api/academic/homeworks?groupId=${groupId}&semesterId=${semesterId}&page=${page}&size=100`,
      (value) => embeddedItems(value, ['homeworkResponseList']).map(normalizeHomework).filter(isValue),
    )
  }

  listSubjects(): Promise<readonly HeadmanHomeworkSubject[]> {
    return this.requestPaged(
      (page) => `/api/academic/subjects?page=${page}&size=100`,
      (value) => embeddedItems(value, ['subjectResponseList']).flatMap((entry) => {
        const subject = unwrapEntity(entry)
        const id = positiveNumber(subject.id)
        const name = textValue(subject.name)
        return id !== null && name !== null ? [{ id, name }] : []
      }),
    )
  }

  history(id: number): Promise<readonly HeadmanHomeworkHistory[]> {
    assertPositiveInteger(id, 'id')
    return this.requestPaged(
      (page) => `/api/academic/homeworks/${id}/history?page=${page}&size=100`,
      (value) => {
        if (!isRecord(value) || !Array.isArray(value.content)) throw new Error('Некорректная история ДЗ')
        return value.content.map((entry: unknown) => {
          const row = unwrapEntity(entry)
          const historyId = positiveNumber(row.id)
          const revision = positiveNumber(row.revision)
          const occurredAt = textValue(row.occurredAt)
          const action = textValue(row.action)
          if (historyId === null || revision === null || occurredAt === null || action === null) {
            throw new Error('Некорректная запись истории ДЗ')
          }
          return { id: historyId, revision, occurredAt, action, actorId: positiveNumber(row.actorId), before: row.before, after: row.after }
        })
      },
    )
  }

  async createHomework(input: HeadmanHomeworkCreateInput): Promise<HeadmanManagedHomework | null> {
    validateCreateInput(input)
    const response = await this.response('/api/academic/homeworks', {
      method: 'POST',
      body: JSON.stringify({
        title: input.title.trim(),
        description: optionalText(input.description),
        link: optionalText(input.link),
        subjectId: input.subjectId,
        groupId: input.groupId,
        semesterId: input.semesterId,
        lessonDate: input.lessonDate,
        lessonNumber: input.lessonNumber,
        bindingMode: input.bindingMode,
        requestKey: input.requestKey,
      }),
    })
    if (!response.ok) throw await this.apiError(response)
    const value: unknown = await response.json()
    this.options.assertCurrent?.()
    if (response.status === 202) {
      if (!isRecord(value) || value.state !== 'PENDING'
        || textValue(value.requestKey)?.toLowerCase() !== input.requestKey.toLowerCase()
        || !/^[1-9][0-9]*$/.test(String(value.homeworkId)) || !/^[1-9][0-9]*$/.test(String(value.bindingId))) {
        throw new Error('Сервер вернул некорректное подтверждение публикации ДЗ')
      }
      return null
    }
    const created = normalizeHomework(value)
    if (created === null || created.groupId !== input.groupId || created.subjectId !== input.subjectId
      || created.semesterId !== input.semesterId || created.bindingMode !== input.bindingMode
      || created.lessonDate !== input.lessonDate || created.lessonNumber !== input.lessonNumber) {
      throw new Error('Сервер вернул ДЗ для другой привязки')
    }
    return created
  }

  async updateHomework(id: number, input: HeadmanHomeworkUpdateInput): Promise<HeadmanManagedHomework | null> {
    assertPositiveInteger(id, 'id')
    validateTextInput(input.title, 'title', 255)
    validateOptionalText(input.description, 'description', 4000)
    validateOptionalText(input.link, 'link', 2048)
    validateRequestKey(input.requestKey)
    assertPositiveInteger(input.expectedRevision, 'expectedRevision')
    const updated = normalizeHomework(await this.request<unknown>(`/api/academic/homeworks/${id}`, {
      method: 'PUT',
      body: JSON.stringify({
        title: input.title.trim(),
        description: optionalText(input.description),
        link: optionalText(input.link),
        requestKey: input.requestKey,
        expectedRevision: input.expectedRevision,
      }),
    }))
    if (updated === null || updated.id !== id || updated.revision === null) throw new Error('Сервер вернул некорректную версию ДЗ')
    return updated
  }

  async deleteHomework(id: number): Promise<void> {
    assertPositiveInteger(id, 'id')
    await this.request<void>(`/api/academic/homeworks/${id}`, { method: 'DELETE' })
  }

  private async requestPaged<T>(
    path: (page: number) => string,
    read: (value: unknown) => readonly T[],
  ): Promise<readonly T[]> {
    const items: T[] = []
    const visitedPaths = new Set<string>()
    let nextPath = path(0)
    let page = 0
    for (;;) {
      if (!visitedPaths.add(nextPath)) throw new Error('Сервер вернул зацикленную пагинацию домашних заданий')
      const value = await this.request<unknown>(nextPath)
      items.push(...read(value))
      const metadata = pageMetadata(value)
      if (metadata !== null && metadata.totalPages > page + 1) {
        nextPath = path(page + 1)
        page += 1
        continue
      }
      const nextHref = nextPageHref(value)
      if (nextHref !== null) {
        nextPath = nextHref
        page += 1
        continue
      }
      return items
    }
  }

  private async request<T>(path: string, init?: RequestInit, retried = false): Promise<T> {
    const response = await this.response(path, init, retried)
    if (response.ok) {
      if (response.status === 204) {
        this.options.assertCurrent?.()
        return undefined as T
      }
      const value = await response.json() as T
      this.options.assertCurrent?.()
      return value
    }
    throw await this.apiError(response)
  }

  private async response(path: string, init?: RequestInit, retried = false): Promise<Response> {
    this.options.assertCurrent?.()
    const token = this.options.accessToken()
    const headers = new Headers(init?.headers)
    if (!headers.has('Accept')) headers.set('Accept', 'application/json')
    if (typeof init?.body === 'string' && !headers.has('Content-Type')) headers.set('Content-Type', 'application/json')
    if (token) headers.set('Authorization', `Bearer ${token}`)
    const response = await this.fetcher(path, { ...init, headers, credentials: 'include' })
    this.options.assertCurrent?.()
    if (response.status === 401 && !retried && this.options.onUnauthorized) {
      await this.options.onUnauthorized()
      this.options.assertCurrent?.()
      return this.response(path, init, true)
    }
    return response
  }

  private async apiError(response: Response): Promise<HeadmanHomeworkApiError> {
    let problem: unknown = null
    try {
      problem = await response.json()
    } catch {
      // Preserve the HTTP status when the gateway did not return JSON.
    }
    this.options.assertCurrent?.()
    return new HeadmanHomeworkApiError(response, problem)
  }
}

export interface HeadmanHomeworkApiGenerationOwner {
  currentGeneration(): number
  accessTokenFor(generation: number): string | null
  refreshFor(generation: number): Promise<void>
}

export function createGenerationBoundHeadmanHomeworkApi(
  owner: HeadmanHomeworkApiGenerationOwner,
  fetcher?: typeof fetch,
): HeadmanHomeworkApi {
  const generation = owner.currentGeneration()
  const assertCurrent = (): void => {
    if (generation !== owner.currentGeneration()) throw new StaleSessionGenerationError()
  }
  return new HeadmanHomeworkApi({
    assertCurrent,
    accessToken: () => {
      assertCurrent()
      return owner.accessTokenFor(generation)
    },
    onUnauthorized: async () => {
      assertCurrent()
      await owner.refreshFor(generation)
      assertCurrent()
    },
    ...(fetcher ? { fetcher } : {}),
  })
}

function validateCreateInput(input: HeadmanHomeworkCreateInput): void {
  validateTextInput(input.title, 'title', 255)
  validateOptionalText(input.description, 'description', 4000)
  validateOptionalText(input.link, 'link', 2048)
  assertPositiveInteger(input.subjectId, 'subjectId')
  assertPositiveInteger(input.groupId, 'groupId')
  assertPositiveInteger(input.semesterId, 'semesterId')
  validateRequestKey(input.requestKey)
  if (!/^\d{4}-\d{2}-\d{2}$/.test(input.lessonDate)) throw new RangeError('lessonDate должен быть ISO date')
  if (input.bindingMode === 'DATE') {
    if (input.lessonNumber !== null) throw new RangeError('Для ДЗ на дату номер пары должен быть null')
  } else if (input.bindingMode !== 'LESSON' || input.lessonNumber === null
    || !Number.isSafeInteger(input.lessonNumber) || input.lessonNumber < 1 || input.lessonNumber > 8) {
    throw new RangeError('lessonNumber должен быть от 1 до 8')
  }
}

function validateRequestKey(value: string): void {
  if (!/^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i.test(value)) {
    throw new RangeError('Ключ запроса должен быть UUID')
  }
}

function assertPositiveInteger(value: number, name: string): void {
  if (!Number.isSafeInteger(value) || value <= 0) throw new RangeError(`${name} должен быть положительным целым числом`)
}

function validateTextInput(value: string, name: string, maxLength: number): void {
  if (!value.trim()) throw new RangeError(`${name} обязателен`)
  if (Array.from(value).length > maxLength) throw new RangeError(`${name} не может быть длиннее ${maxLength} символов`)
}

function validateOptionalText(value: string | null | undefined, name: string, maxLength: number): void {
  if (value !== null && value !== undefined && Array.from(value).length > maxLength) {
    throw new RangeError(`${name} не может быть длиннее ${maxLength} символов`)
  }
}

function optionalText(value: string | null | undefined): string | null {
  const normalized = value?.trim() ?? ''
  return normalized || null
}

function normalizeSemester(value: Record<string, unknown>): HeadmanHomeworkSemester | null {
  const source = unwrapEntity(value)
  const id = positiveNumber(source.id)
  if (id === null) return null
  return {
    id,
    name: textValue(source.name) ?? `Семестр #${id}`,
    dateFrom: textValue(source.dateFrom),
    dateTo: textValue(source.dateTo),
    active: source.active === true,
  }
}

function normalizeHomework(value: unknown): HeadmanManagedHomework | null {
  const source = unwrapEntity(value)
  const id = positiveNumber(source.id)
  const subjectId = positiveNumber(source.subjectId)
  const groupId = positiveNumber(source.groupId)
  const semesterId = positiveNumber(source.semesterId)
  const lessonDate = textValue(source.lessonDate)
  const lessonNumber = positiveNumber(source.lessonNumber)
  const bindingMode = source.bindingMode ?? 'LESSON'
  if (id === null || subjectId === null || groupId === null || semesterId === null || lessonDate === null
    || (bindingMode !== 'LESSON' && bindingMode !== 'DATE')
    || (bindingMode === 'LESSON' ? lessonNumber === null : source.lessonNumber !== null)) {
    return null
  }
  return {
    id,
    title: textValue(source.title) ?? `Задание #${id}`,
    description: textValue(source.description),
    link: textValue(source.link),
    subjectId,
    groupId,
    semesterId,
    publishedBy: positiveNumber(source.publishedBy),
    lessonDate,
    lessonNumber,
    bindingMode,
    revision: positiveNumber(source.revision),
    archived: source.archived === true,
  }
}

function embeddedItems(value: unknown, keys: readonly string[]): Record<string, unknown>[] {
  if (!isRecord(value) || !isRecord(value._embedded)) return []
  for (const key of keys) {
    const items = value._embedded[key]
    if (Array.isArray(items)) return items.filter(isRecord)
  }
  const first = Object.values(value._embedded).find(Array.isArray)
  return Array.isArray(first) ? first.filter(isRecord) : []
}

function pageMetadata(value: unknown): { totalPages: number } | null {
  if (!isRecord(value)) return null
  const totalPages = isRecord(value.page) ? value.page.totalPages : value.totalPages
  return typeof totalPages === 'number' && Number.isSafeInteger(totalPages) && totalPages >= 0
    ? { totalPages }
    : null
}

function nextPageHref(value: unknown): string | null {
  if (!isRecord(value) || !isRecord(value._links) || !isRecord(value._links.next)) return null
  const href = value._links.next.href
  return typeof href === 'string' && href.startsWith('/api/') ? href : null
}

function unwrapEntity(value: unknown): Record<string, unknown> {
  if (!isRecord(value)) return {}
  return isRecord(value.content) ? value.content : value
}

function positiveNumber(value: unknown): number | null {
  return typeof value === 'number' && Number.isSafeInteger(value) && value > 0 ? value : null
}

function textValue(value: unknown): string | null {
  return typeof value === 'string' && value.trim() ? value.trim() : null
}

function isRecord(value: unknown): value is Record<string, unknown> {
  return typeof value === 'object' && value !== null
}

function isValue<T>(value: T | null): value is T {
  return value !== null
}

function problemDetail(value: unknown): string | null {
  if (!isRecord(value)) return null
  return textValue(value.detail) ?? textValue(value.title)
}
