import type { MobileProblemDetails } from '../../api/types'

export interface AdminSemester {
  readonly id: number
  readonly name: string
  readonly dateFrom: string
  readonly dateTo: string
  readonly active: boolean
  readonly createdAt: string | null
}

export interface CreateAdminSemesterInput {
  readonly name: string
  readonly dateFrom: string
  readonly dateTo: string
}

export interface AdminSemesterApiOptions {
  readonly accessToken: () => string | null
  readonly onUnauthorized?: () => Promise<void>
  readonly assertCurrent?: () => void
  readonly fetcher?: typeof fetch
}

export class AdminSemesterApiError extends Error {
  constructor(
    readonly response: Response,
    readonly problem: MobileProblemDetails | null,
  ) {
    super(problem?.detail || problem?.title || `HTTP ${response.status}`)
    this.name = 'AdminSemesterApiError'
  }
}

/** ADMIN semester list and the two supported mutations in JS-ADMIN-08/09. */
export class AdminSemesterClient {
  private static readonly basePath = '/api/academic/semesters'
  private static readonly pageSize = 200
  private readonly fetcher: typeof fetch

  constructor(private readonly options: AdminSemesterApiOptions) {
    this.fetcher = options.fetcher ?? ((input, init) => globalThis.fetch(input, init))
  }

  async listSemesters(signal?: AbortSignal): Promise<readonly AdminSemester[]> {
    const semesters: AdminSemester[] = []
    let page = await this.request<unknown>(`${AdminSemesterClient.basePath}?size=${AdminSemesterClient.pageSize}`, signal ? { signal } : undefined)
    let normalized = normalizeSemesterPage(page)
    semesters.push(...normalized.items)

    while (normalized.totalPages !== null && normalized.number + 1 < normalized.totalPages) {
      const nextNumber = normalized.number + 1
      page = await this.request<unknown>(
        `${AdminSemesterClient.basePath}?page=${nextNumber}&size=${AdminSemesterClient.pageSize}`,
        signal ? { signal } : undefined,
      )
      normalized = normalizeSemesterPage(page)
      if (normalized.number !== nextNumber) {
        throw new Error('Сервер вернул непоследовательную страницу семестров.')
      }
      semesters.push(...normalized.items)
    }

    return semesters
  }

  createSemester(input: CreateAdminSemesterInput): Promise<AdminSemester> {
    const payload = normalizeCreateInput(input)
    return this.request<unknown>(AdminSemesterClient.basePath, {
      method: 'POST',
      body: JSON.stringify(payload),
    }).then(normalizeSemester)
  }

  /** One PATCH is the complete server-side activation transaction. */
  activateSemester(id: number): Promise<AdminSemester> {
    assertPositiveInteger(id, 'semesterId')
    return this.request<unknown>(`${AdminSemesterClient.basePath}/${id}/activate`, {
      method: 'PATCH',
    }).then(normalizeSemester)
  }

  private async request<T>(path: string, init?: RequestInit, retried = false): Promise<T> {
    const response = await this.requestResponse(path, init, retried)
    const value = await response.json() as T
    this.options.assertCurrent?.()
    return value
  }

  private async requestResponse(path: string, init?: RequestInit, retried = false): Promise<Response> {
    this.options.assertCurrent?.()
    const token = this.options.accessToken()
    const headers = new Headers(init?.headers)
    if (!headers.has('Accept')) headers.set('Accept', 'application/json')
    if (typeof init?.body === 'string' && !headers.has('Content-Type')) {
      headers.set('Content-Type', 'application/json')
    }
    if (token) headers.set('Authorization', `Bearer ${token}`)
    const response = await this.fetcher(path, { ...init, headers, credentials: 'include' })
    this.options.assertCurrent?.()
    if (response.ok) return response
    if (response.status === 401 && !retried && this.options.onUnauthorized) {
      await this.options.onUnauthorized()
      return this.requestResponse(path, init, true)
    }
    let problem: MobileProblemDetails | null = null
    try {
      problem = await response.json() as MobileProblemDetails
    } catch {
      // Preserve the HTTP status when a gateway cannot return Problem Details.
    }
    this.options.assertCurrent?.()
    throw new AdminSemesterApiError(response, problem)
  }
}

export interface AdminSemesterApiGenerationOwner {
  currentGeneration(): number
  accessTokenFor(generation: number): string | null
  refreshFor(generation: number): Promise<void>
}

interface NormalizedSemesterPage {
  readonly items: readonly AdminSemester[]
  readonly number: number
  readonly totalPages: number | null
}

function normalizeSemesterPage(value: unknown): NormalizedSemesterPage {
  if (Array.isArray(value)) return { items: value.map(normalizeSemester), number: 0, totalPages: null }
  const record = requiredRecord(value, 'semester page')
  const embedded = record._embedded
  const items = embedded === undefined || embedded === null
    ? []
    : readSemesterItems(embedded)
  const page = record.page
  if (page === undefined || page === null) return { items, number: 0, totalPages: null }
  const pageRecord = requiredRecord(page, 'semester page.page')
  const number = nonNegativeInteger(pageRecord.number, 'semester page.number')
  const totalPages = nonNegativeInteger(pageRecord.totalPages, 'semester page.totalPages')
  if (number >= totalPages && totalPages > 0) throw new Error('Сервер вернул некорректную страницу семестров.')
  return { items, number, totalPages }
}

function readSemesterItems(value: unknown): readonly AdminSemester[] {
  const embeddedRecord = requiredRecord(value, 'semester page._embedded')
  const items = embeddedRecord.semesterResponseList
  if (items === undefined || items === null) return []
  if (!Array.isArray(items)) throw new Error('Сервер вернул некорректный список семестров.')
  return items.map(normalizeSemester)
}

function normalizeSemester(value: unknown): AdminSemester {
  const record = requiredRecord(value, 'semester')
  return {
    id: positiveInteger(record.id, 'semester.id'),
    name: requiredText(record.name, 'semester.name'),
    dateFrom: requiredDate(record.dateFrom, 'semester.dateFrom'),
    dateTo: requiredDate(record.dateTo, 'semester.dateTo'),
    active: requiredBoolean(record.active, 'semester.active'),
    createdAt: nullableText(record.createdAt),
  }
}

function normalizeCreateInput(input: CreateAdminSemesterInput): CreateAdminSemesterInput {
  const name = requiredText(input.name, 'name')
  const dateFrom = requiredDate(input.dateFrom, 'dateFrom')
  const dateTo = requiredDate(input.dateTo, 'dateTo')
  if (dateTo < dateFrom) throw new RangeError('dateTo must be on or after dateFrom')
  return { name, dateFrom, dateTo }
}

function requiredRecord(value: unknown, field: string): Record<string, unknown> {
  if (typeof value !== 'object' || value === null || Array.isArray(value)) {
    throw new Error(`Сервер вернул некорректные данные ${field}.`)
  }
  return value as Record<string, unknown>
}

function requiredText(value: unknown, field: string): string {
  if (typeof value !== 'string' || value.trim() === '') throw new Error(`Сервер вернул пустое поле ${field}.`)
  return value
}

function nullableText(value: unknown): string | null {
  return typeof value === 'string' && value.trim() !== '' ? value : null
}

function requiredDate(value: unknown, field: string): string {
  const text = requiredText(value, field)
  if (!/^\d{4}-\d{2}-\d{2}$/.test(text)) throw new Error(`Сервер вернул некорректную дату ${field}.`)
  return text
}

function requiredBoolean(value: unknown, field: string): boolean {
  if (typeof value !== 'boolean') throw new Error(`Сервер вернул некорректное поле ${field}.`)
  return value
}

function positiveInteger(value: unknown, field: string): number {
  if (typeof value !== 'number' || !Number.isSafeInteger(value) || value <= 0) {
    throw new Error(`Сервер вернул некорректный идентификатор ${field}.`)
  }
  return value
}

function nonNegativeInteger(value: unknown, field: string): number {
  if (typeof value !== 'number' || !Number.isSafeInteger(value) || value < 0) {
    throw new Error(`Сервер вернул некорректное поле ${field}.`)
  }
  return value
}

function assertPositiveInteger(value: number, field: string): void {
  if (!Number.isSafeInteger(value) || value <= 0) throw new RangeError(`${field} must be a positive integer`)
}
