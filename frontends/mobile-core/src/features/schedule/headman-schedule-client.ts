import { StaleSessionGenerationError } from '../../shared/session-owner'
import type { components as AcademicComponents } from '../../../../pwa/src/api/generated/academic.types'
import type { components as ScheduleComponents } from '../../../../pwa/src/api/generated/schedule.types'

type ScheduleItem = ScheduleComponents['schemas']['EntityModelScheduleItemResponse']
type CreateScheduleItemRequest = ScheduleComponents['schemas']['CreateScheduleItemRequest']
type Assignment = AcademicComponents['schemas']['EntityModelAssignmentResponse']
type Semester = AcademicComponents['schemas']['EntityModelSemesterResponse']
type PageMetadata = AcademicComponents['schemas']['PageMetadata']

const MAX_PAGE_REQUESTS = 100

export type HeadmanScheduleItem = ScheduleItem
export type HeadmanScheduleAssignment = Assignment
export type HeadmanScheduleSemester = Semester
export type HeadmanScheduleCreateInput = CreateScheduleItemRequest

export interface HeadmanScheduleApiOptions {
  accessToken: () => string | null
  onUnauthorized?: () => Promise<void>
  fetcher?: typeof fetch
}

export class HeadmanScheduleApiError extends Error {
  constructor(
    readonly response: Response,
    readonly problem: unknown,
  ) {
    super(problemDetail(problem) ?? `HTTP ${response.status}`)
    this.name = 'HeadmanScheduleApiError'
  }
}

export class HeadmanScheduleApi {
  private readonly fetcher: typeof fetch

  constructor(private readonly options: HeadmanScheduleApiOptions) {
    this.fetcher = options.fetcher ?? ((input, init) => globalThis.fetch(input, init))
  }

  listScheduleItems(groupId: number, semesterId: number): Promise<readonly HeadmanScheduleItem[]> {
    assertNumericId(groupId, 'groupId')
    assertNumericId(semesterId, 'semesterId')
    return this.requestPaged(
      (page) => {
        const query = new URLSearchParams({ groupId: String(groupId), semesterId: String(semesterId), page: String(page), size: '100' })
        return `/api/schedule/items?${query.toString()}`
      },
      'scheduleItemResponseList',
    )
  }

  listAssignments(groupId: number, semesterId: number): Promise<readonly HeadmanScheduleAssignment[]> {
    assertNumericId(groupId, 'groupId')
    assertNumericId(semesterId, 'semesterId')
    return this.requestPaged(
      (page) => {
        const query = new URLSearchParams({ groupId: String(groupId), semesterId: String(semesterId), page: String(page), size: '100' })
        return `/api/academic/assignments?${query.toString()}`
      },
      'assignmentResponseList',
    )
  }

  listSemesters(): Promise<readonly HeadmanScheduleSemester[]> {
    return this.requestPaged(
      (page) => `/api/academic/semesters?page=${page}&size=100`,
      'semesterResponseList',
    )
  }

  createScheduleItem(input: HeadmanScheduleCreateInput, idempotencyKey: string): Promise<HeadmanScheduleItem> {
    validateHeadmanIdempotencyKey(idempotencyKey)
    return this.request('/api/schedule/items', {
      method: 'POST',
      headers: { 'Idempotency-Key': idempotencyKey },
      body: JSON.stringify(input),
    })
  }

  private async requestPaged<T>(path: (page: number) => string, key: string): Promise<readonly T[]> {
    const items: T[] = []
    let nextPath = path(0)
    for (let page = 0; page < MAX_PAGE_REQUESTS; page += 1) {
      const value = await this.request<unknown>(nextPath)
      items.push(...embeddedItems<T>(value, key))
      const metadata = pageMetadata(value)
      if (metadata?.totalPages && metadata.totalPages > page + 1) {
        nextPath = path(page + 1)
        continue
      }
      const nextHref = nextPageHref(value)
      if (nextHref) {
        nextPath = nextHref
        continue
      }
      return items
    }
    throw new Error('Сервер вернул слишком много страниц расписания')
  }

  private async request<T>(path: string, init?: RequestInit, retried = false): Promise<T> {
    const token = this.options.accessToken()
    const headers = new Headers(init?.headers)
    if (!headers.has('Accept')) headers.set('Accept', 'application/json')
    if (typeof init?.body === 'string' && !headers.has('Content-Type')) headers.set('Content-Type', 'application/json')
    if (token) headers.set('Authorization', `Bearer ${token}`)
    const response = await this.fetcher(path, { ...init, headers, credentials: 'include' })
    if (response.ok) return response.json() as Promise<T>
    if (response.status === 401 && !retried && this.options.onUnauthorized) {
      await this.options.onUnauthorized()
      return this.request<T>(path, init, true)
    }

    let problem: unknown = null
    try {
      problem = await response.json()
    } catch {
      // Preserve the HTTP status when the gateway did not return JSON.
    }
    throw new HeadmanScheduleApiError(response, problem)
  }
}

export interface HeadmanScheduleApiGenerationOwner {
  currentGeneration(): number
  accessTokenFor(generation: number): string | null
  refreshFor(generation: number): Promise<void>
}

export function createGenerationBoundHeadmanScheduleApi(
  owner: HeadmanScheduleApiGenerationOwner,
  fetcher?: typeof fetch,
): HeadmanScheduleApi {
  const generation = owner.currentGeneration()
  const assertCurrent = (): void => {
    if (generation !== owner.currentGeneration()) throw new StaleSessionGenerationError()
  }

  const options: HeadmanScheduleApiOptions = {
    accessToken: () => {
      assertCurrent()
      return owner.accessTokenFor(generation)
    },
    onUnauthorized: async () => {
      assertCurrent()
      await owner.refreshFor(generation)
      assertCurrent()
    },
  }
  if (fetcher) options.fetcher = fetcher
  return new HeadmanScheduleApi(options)
}

function validateHeadmanIdempotencyKey(value: string): void {
  if (!/^[\x21-\x7e]{16,128}$/.test(value)) {
    throw new TypeError('Idempotency-Key must contain 16–128 visible ASCII characters')
  }
}

function assertNumericId(value: number, name: string): void {
  if (!Number.isSafeInteger(value) || value <= 0) throw new RangeError(`${name} must be a positive integer`)
}

function embeddedItems<T>(value: unknown, key: string): readonly T[] {
  if (!isRecord(value) || !isRecord(value._embedded)) return []
  const items = value._embedded[key]
  return Array.isArray(items) ? items as T[] : []
}

function pageMetadata(value: unknown): PageMetadata | null {
  if (!isRecord(value) || !isRecord(value.page)) return null
  const totalPages = value.page.totalPages
  return typeof totalPages === 'number' && Number.isSafeInteger(totalPages) && totalPages >= 0
    ? { totalPages }
    : null
}

function nextPageHref(value: unknown): string | null {
  if (!isRecord(value) || !isRecord(value._links) || !isRecord(value._links.next)) return null
  const href = value._links.next.href
  return typeof href === 'string' && href.startsWith('/api/') ? href : null
}

function isRecord(value: unknown): value is Record<string, unknown> {
  return typeof value === 'object' && value !== null
}

function problemDetail(value: unknown): string | null {
  if (!isRecord(value)) return null
  return typeof value.detail === 'string'
    ? value.detail
    : typeof value.title === 'string' ? value.title : null
}
