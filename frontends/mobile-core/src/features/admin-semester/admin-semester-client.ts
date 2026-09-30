import type { MobileProblemDetails } from '../../api/types'

export type AdminSemesterType = 'AUTUMN' | 'SPRING'
export type AdminSemesterTransition = 'NONE' | 'ARCHIVING' | 'RESTORING'
export type AdminSemesterArchiveAction = 'ARCHIVE' | 'RESTORE'
export type AdminSemesterArchiveOperationState = 'PENDING' | 'COMPLETED' | 'ERROR'
export type AdminSemesterArchiveParticipantState =
  | 'NOT_STARTED'
  | 'PENDING'
  | 'READY'
  | 'PREPARED_RESTORE'
  | 'RELEASE_PENDING'
  | 'RELEASED'

export interface AdminSemesterArchiveOperation {
  readonly operationId: string
  readonly semesterId: number
  readonly action: AdminSemesterArchiveAction
  readonly operationState: AdminSemesterArchiveOperationState
  readonly retryable: boolean
  readonly stateVersion: number
  readonly transition: AdminSemesterTransition
  readonly active: boolean
  readonly archived: boolean
  readonly releasePending: boolean
  readonly academic: AdminSemesterArchiveParticipantState
  readonly schedule: AdminSemesterArchiveParticipantState
  readonly attendance: AdminSemesterArchiveParticipantState
  readonly blockingReason: string | null
}

/** Current authority; its nested operation is an immutable historical snapshot. */
export interface AdminSemesterArchiveStatus {
  readonly semesterId: number
  readonly active: boolean
  readonly archived: boolean
  readonly transition: AdminSemesterTransition
  readonly stateVersion: number
  readonly releasePending: boolean
  readonly operation: AdminSemesterArchiveOperation | null
}

export interface AdminSemester {
  readonly id: number
  readonly name: string
  readonly dateFrom: string
  readonly dateTo: string
  readonly active: boolean
  readonly createdAt: string | null
  readonly archived: boolean
  readonly transition: AdminSemesterTransition
  readonly stateVersion: number
  readonly releasePending: boolean
  readonly isWriteBlocked: boolean
  /** Null for legacy rows whose type was never explicitly assigned. */
  readonly semesterType: AdminSemesterType | null
  /** First year of the academic year; null on unclassified legacy rows. */
  readonly academicYear: number | null
}

export interface CreateAdminSemesterInput {
  readonly name: string
  readonly dateFrom: string
  readonly dateTo: string
  readonly semesterType: AdminSemesterType
  readonly academicYear: number
}

export interface AdminSemesterOverlap {
  readonly overlaps: boolean
  readonly conflictingName: string | null
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
    readonly archiveOperation: AdminSemesterArchiveOperation | null = null,
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
    const payload = normalizeSemesterInput(input)
    return this.request<unknown>(AdminSemesterClient.basePath, {
      method: 'POST',
      body: JSON.stringify(payload),
    }).then(normalizeSemester)
  }

  getSemester(id: number, signal?: AbortSignal): Promise<AdminSemester> {
    assertPositiveInteger(id, 'semesterId')
    return this.request<unknown>(`${AdminSemesterClient.basePath}/${id}`, signal ? { signal } : undefined)
      .then(normalizeSemester)
  }

  updateSemester(id: number, input: CreateAdminSemesterInput): Promise<AdminSemester> {
    assertPositiveInteger(id, 'semesterId')
    const payload = normalizeSemesterInput(input)
    return this.request<unknown>(`${AdminSemesterClient.basePath}/${id}`, {
      method: 'PUT',
      body: JSON.stringify(payload),
    }).then(normalizeSemester)
  }

  checkOverlap(from: string, to: string, excludeId?: number, signal?: AbortSignal): Promise<AdminSemesterOverlap> {
    const dateFrom = requiredDate(from, 'from')
    const dateTo = requiredDate(to, 'to')
    if (dateTo < dateFrom) throw new RangeError('to must be on or after from')
    const query = new URLSearchParams({ from: dateFrom, to: dateTo })
    if (excludeId !== undefined) {
      assertPositiveInteger(excludeId, 'excludeId')
      query.set('excludeId', String(excludeId))
    }
    return this.request<unknown>(`${AdminSemesterClient.basePath}/overlap?${query.toString()}`, signal ? { signal } : undefined)
      .then(normalizeOverlap)
  }

  /** One PATCH is the complete server-side activation transaction. */
  activateSemester(id: number): Promise<AdminSemester> {
    assertPositiveInteger(id, 'semesterId')
    return this.request<unknown>(`${AdminSemesterClient.basePath}/${id}/activate`, {
      method: 'PATCH',
    }).then(normalizeSemester)
  }

  archiveSemester(id: number, idempotencyKey: string, signal?: AbortSignal): Promise<AdminSemesterArchiveOperation> {
    return this.changeArchiveState(id, 'ARCHIVE', idempotencyKey, signal)
  }

  restoreSemester(id: number, idempotencyKey: string, signal?: AbortSignal): Promise<AdminSemesterArchiveOperation> {
    return this.changeArchiveState(id, 'RESTORE', idempotencyKey, signal)
  }

  getArchiveOperation(operationId: string, signal?: AbortSignal): Promise<AdminSemesterArchiveOperation> {
    const id = requiredText(operationId, 'operationId')
    return this.request<unknown>(
      `/api/academic/semester-archive-operations/${encodeURIComponent(id)}`,
      signal ? { signal } : undefined,
    ).then((value) => {
      const operation = normalizeArchiveOperation(value)
      if (operation.operationId !== id) throw new Error('Сервер вернул другую операцию архивации семестра.')
      return operation
    })
  }

  getArchiveStatus(id: number, signal?: AbortSignal): Promise<AdminSemesterArchiveStatus> {
    assertPositiveInteger(id, 'semesterId')
    return this.request<unknown>(
      `${AdminSemesterClient.basePath}/${id}/archive/status`,
      signal ? { signal } : undefined,
    ).then(normalizeArchiveStatus)
  }

  private changeArchiveState(
    id: number,
    action: AdminSemesterArchiveAction,
    idempotencyKey: string,
    signal?: AbortSignal,
  ): Promise<AdminSemesterArchiveOperation> {
    assertPositiveInteger(id, 'semesterId')
    assertUuid(idempotencyKey, 'idempotencyKey')
    return this.request<unknown>(`${AdminSemesterClient.basePath}/${id}/${action.toLowerCase()}`, {
      method: 'POST',
      body: null,
      headers: { 'Idempotency-Key': idempotencyKey },
      ...(signal ? { signal } : {}),
    }).then((value) => {
      const operation = normalizeArchiveOperation(value)
      if (operation.semesterId !== id || operation.action !== action) {
        throw new Error('Сервер вернул операцию для другого семестра или действия.')
      }
      return operation
    })
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
    let body: unknown = null
    try {
      body = await response.json() as unknown
    } catch {
      // Preserve the HTTP status when a gateway cannot return Problem Details.
    }
    this.options.assertCurrent?.()
    const problem = isRecord(body) ? body as MobileProblemDetails : null
    const archiveOperation = response.status === 503 ? findArchiveOperation(body) : null
    throw new AdminSemesterApiError(response, problem, archiveOperation)
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
  const semesterType = nullableSemesterType(record.semesterType)
  const academicYear = nullableAcademicYear(record.academicYear)
  if ((semesterType === null) !== (academicYear === null)) {
    throw new Error('Сервер вернул неполную классификацию семестра.')
  }
  return {
    id: positiveInteger(record.id, 'semester.id'),
    name: requiredText(record.name, 'semester.name'),
    dateFrom: requiredDate(record.dateFrom, 'semester.dateFrom'),
    dateTo: requiredDate(record.dateTo, 'semester.dateTo'),
    active: requiredBoolean(record.active, 'semester.active'),
    createdAt: nullableText(record.createdAt),
    archived: requiredBoolean(record.archived, 'semester.archived'),
    transition: requiredTransition(record.transition, 'semester.transition'),
    stateVersion: nonNegativeInteger(record.stateVersion, 'semester.stateVersion'),
    releasePending: requiredBoolean(record.releasePending, 'semester.releasePending'),
    isWriteBlocked: requiredBoolean(record.writeBlocked, 'semester.writeBlocked'),
    semesterType,
    academicYear,
  }
}

function normalizeArchiveOperation(value: unknown): AdminSemesterArchiveOperation {
  const record = requiredRecord(value, 'semester archive operation')
  const action = record.action
  if (action !== 'ARCHIVE' && action !== 'RESTORE') {
    throw new Error('Сервер вернул некорректное действие архивации семестра.')
  }
  const operationState = record.operationState
  if (operationState !== 'PENDING' && operationState !== 'COMPLETED' && operationState !== 'ERROR') {
    throw new Error('Сервер вернул некорректное состояние архивации семестра.')
  }
  return {
    operationId: requiredText(record.operationId, 'semester archive operation.operationId'),
    semesterId: positiveInteger(record.semesterId, 'semester archive operation.semesterId'),
    action,
    operationState,
    retryable: requiredBoolean(record.retryable, 'semester archive operation.retryable'),
    stateVersion: nonNegativeInteger(record.stateVersion, 'semester archive operation.stateVersion'),
    transition: requiredTransition(record.transition, 'semester archive operation.transition'),
    active: requiredBoolean(record.active, 'semester archive operation.active'),
    archived: requiredBoolean(record.archived, 'semester archive operation.archived'),
    releasePending: requiredBoolean(record.releasePending, 'semester archive operation.releasePending'),
    academic: requiredParticipantState(record.academic, 'semester archive operation.academic'),
    schedule: requiredParticipantState(record.schedule, 'semester archive operation.schedule'),
    attendance: requiredParticipantState(record.attendance, 'semester archive operation.attendance'),
    blockingReason: optionalText(record.blockingReason, 'semester archive operation.blockingReason'),
  }
}

function normalizeArchiveStatus(value: unknown): AdminSemesterArchiveStatus {
  const record = requiredRecord(value, 'semester archive status')
  const operation = record.operation
  const status: AdminSemesterArchiveStatus = {
    semesterId: positiveInteger(record.semesterId, 'semester archive status.semesterId'),
    active: requiredBoolean(record.active, 'semester archive status.active'),
    archived: requiredBoolean(record.archived, 'semester archive status.archived'),
    transition: requiredTransition(record.transition, 'semester archive status.transition'),
    stateVersion: nonNegativeInteger(record.stateVersion, 'semester archive status.stateVersion'),
    releasePending: requiredBoolean(record.releasePending, 'semester archive status.releasePending'),
    operation: operation === undefined || operation === null ? null : normalizeArchiveOperation(operation),
  }
  if (status.operation && status.operation.semesterId !== status.semesterId) {
    throw new Error('Сервер вернул операцию для другого семестра.')
  }
  return status
}

function findArchiveOperation(value: unknown): AdminSemesterArchiveOperation | null {
  const record = isRecord(value) ? value : null
  const candidate = record?.operation ?? value
  if (!isRecord(candidate) || typeof candidate.operationId !== 'string') return null
  try {
    return normalizeArchiveOperation(candidate)
  } catch {
    return null
  }
}

function requiredTransition(value: unknown, field: string): AdminSemesterTransition {
  if (value === 'NONE' || value === 'ARCHIVING' || value === 'RESTORING') return value
  throw new Error(`Сервер вернул некорректное поле ${field}.`)
}

function requiredParticipantState(value: unknown, field: string): AdminSemesterArchiveParticipantState {
  if (value === 'NOT_STARTED' || value === 'PENDING' || value === 'READY'
    || value === 'PREPARED_RESTORE' || value === 'RELEASE_PENDING' || value === 'RELEASED') return value
  throw new Error(`Сервер вернул некорректное поле ${field}.`)
}

function optionalText(value: unknown, field: string): string | null {
  if (value === undefined || value === null || value === '') return null
  return requiredText(value, field)
}

function normalizeSemesterInput(input: CreateAdminSemesterInput): CreateAdminSemesterInput {
  const name = requiredText(input.name, 'name')
  const dateFrom = requiredDate(input.dateFrom, 'dateFrom')
  const dateTo = requiredDate(input.dateTo, 'dateTo')
  if (dateTo < dateFrom) throw new RangeError('dateTo must be on or after dateFrom')
  const semesterType = input.semesterType
  if (semesterType !== 'AUTUMN' && semesterType !== 'SPRING') {
    throw new RangeError('semesterType must be AUTUMN or SPRING')
  }
  if (!Number.isInteger(input.academicYear) || input.academicYear < 1 || input.academicYear > 9998) {
    throw new RangeError('academicYear must be an integer from 1 to 9998')
  }
  return { name, dateFrom, dateTo, semesterType, academicYear: input.academicYear }
}

function normalizeOverlap(value: unknown): AdminSemesterOverlap {
  const record = requiredRecord(value, 'semester overlap')
  return {
    overlaps: requiredBoolean(record.overlaps, 'semester overlap.overlaps'),
    conflictingName: nullableText(record.conflictingName),
  }
}

function nullableSemesterType(value: unknown): AdminSemesterType | null {
  if (value === undefined || value === null || value === '') return null
  if (value === 'AUTUMN' || value === 'SPRING') return value
  throw new Error('Сервер вернул некорректный тип семестра.')
}

function nullableAcademicYear(value: unknown): number | null {
  if (value === undefined || value === null || value === '') return null
  if (typeof value !== 'number' || !Number.isSafeInteger(value) || value < 1 || value > 9998) {
    throw new Error('Сервер вернул некорректный учебный год семестра.')
  }
  return value
}

function requiredRecord(value: unknown, field: string): Record<string, unknown> {
  if (typeof value !== 'object' || value === null || Array.isArray(value)) {
    throw new Error(`Сервер вернул некорректные данные ${field}.`)
  }
  return value as Record<string, unknown>
}

function isRecord(value: unknown): value is Record<string, unknown> {
  return typeof value === 'object' && value !== null && !Array.isArray(value)
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

function assertUuid(value: string, field: string): void {
  if (!/^[\da-f]{8}-[\da-f]{4}-[1-8][\da-f]{3}-[89ab][\da-f]{3}-[\da-f]{12}$/i.test(value)) {
    throw new RangeError(`${field} must be a UUID`)
  }
}
