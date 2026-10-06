import { StaleSessionGenerationError } from '../../shared/session-owner'

export type HeadmanSubjectType = 'LECTURE' | 'PRACTICE' | 'LAB'

export interface HeadmanSubjectAssignment {
  readonly id: number
  readonly teacherId: number
  readonly semesterId: number
  readonly lessonType: HeadmanSubjectType
  readonly validFrom: string
  readonly validUntilExclusive: string | null
}

export interface HeadmanPendingAdoption {
  readonly targetAssignmentId: number; readonly adoptionOperationId: string; readonly state?: string
}
export interface HeadmanSubjectPendingAdoption extends HeadmanPendingAdoption, HeadmanSubjectInitialAssignment { readonly subjectId: number; readonly groupId: number; readonly state: string; readonly validUntilExclusive: string | null }
export interface HeadmanPendingClosure {
  readonly operationId: string; readonly assignmentId: number; readonly requestKey: string; readonly validUntilExclusive: string
  readonly teacherId: number; readonly subjectId: number; readonly groupId: number; readonly semesterId: number; readonly lessonType: HeadmanSubjectType
}
export interface HeadmanAdoptionAssignment extends HeadmanSubjectAssignment { readonly subjectId: number; readonly groupId: number }
export interface HeadmanSubject {
  readonly id: number
  readonly name: string
  readonly type: HeadmanSubjectType
  readonly groupId: number
  readonly lessonTypes: readonly HeadmanSubjectType[]
  readonly teacherIds: readonly number[]
  readonly pendingAdoptions?: readonly HeadmanSubjectPendingAdoption[]
  readonly pendingClosures?: readonly HeadmanPendingClosure[]
  readonly assignments: readonly HeadmanSubjectAssignment[]
}

export interface HeadmanTeacher {
  readonly id: number
  readonly fullName: string
  readonly employeeNumber: string | null
}

export interface HeadmanSemester {
  readonly id: number
  readonly name: string
  readonly dateFrom: string
  readonly dateTo: string
  readonly active: boolean
}

export interface AssignmentReplacementInput {
  readonly replacementTeacherId: string
  readonly effectiveFrom: string
  readonly requestKey: string
}

/** The academic service returns lifecycle strings; unknown future states are preserved safely. */
export interface AssignmentReplacementResponse {
  readonly operationId: string
  readonly sourceAssignmentId: number
  readonly targetAssignmentId: number
  readonly sourceTeacherId: number
  readonly targetTeacherId: number
  readonly subjectId: number
  readonly groupId: number
  readonly semesterId: number
  readonly lessonType: string
  readonly effectiveFrom: string
  readonly sourceValidUntilExclusive: string | null
  readonly targetValidUntilExclusive: string | null
  readonly state: string
  readonly scheduleReceiptState: string | null
  readonly movedCount: number
  readonly skippedCount: number
}

export interface HeadmanSubjectInitialAssignment {
  readonly teacherId: number
  readonly semesterId: number
  readonly lessonType: HeadmanSubjectType
  readonly validFrom: string
  readonly validUntilExclusive?: string | null
}

export interface CreateHeadmanSubjectInput {
  readonly name: string
  readonly type: HeadmanSubjectType
  readonly lessonTypes: readonly HeadmanSubjectType[]
  readonly initialAssignments: readonly HeadmanSubjectInitialAssignment[]
}

export interface AddHeadmanSubjectTeacherInput {
  readonly semesterId: number
  readonly lessonType: HeadmanSubjectType
  readonly validFrom: string
  readonly validUntilExclusive?: string | null
}

export interface HeadmanSubjectsApiOptions {
  readonly accessToken: () => string | null
  readonly onUnauthorized?: () => Promise<void>
  readonly assertCurrent?: () => void
  fetcher?: typeof fetch
}

export class HeadmanSubjectsApiError extends Error {
  constructor(
    readonly response: Response,
    readonly problem: unknown,
  ) {
    super(problemDetail(problem) ?? `HTTP ${response.status}`)
    this.name = 'HeadmanSubjectsApiError'
  }
}

export class HeadmanSubjectsApi {
  private readonly fetcher: typeof fetch

  constructor(private readonly options: HeadmanSubjectsApiOptions) {
    this.fetcher = options.fetcher ?? ((input, init) => globalThis.fetch(input, init))
  }

  listSubjects(groupId: number): Promise<readonly HeadmanSubject[]> {
    assertPositiveInteger(groupId, 'groupId')
    return this.requestPaged(
      (page) => `/api/academic/subjects?page=${page}&size=100`,
      'subjectResponseList',
      normalizeSubject,
    )
  }

  getSubject(subjectId: number): Promise<HeadmanSubject> {
    return this.request<unknown>(`/api/academic/subjects/${positiveInteger(subjectId, 'subjectId')}`, { cache: 'no-store' }).then((value) => normalizeSubject(readModel(value)))
  }

  listSemesters(): Promise<readonly HeadmanSemester[]> {
    return this.requestPaged(
      (page) => `/api/academic/semesters?page=${page}&size=100`,
      'semesterResponseList',
      normalizeSemester,
    )
  }

  async searchTeachers(search: string, page = 0): Promise<HeadmanTeacherPage> {
    if (!Number.isSafeInteger(page) || page < 0) throw new RangeError('page must be a non-negative integer')
    const query = new URLSearchParams({ page: String(page), size: '20' })
    if (search.trim()) query.set('search', search.trim())
    return normalizeTeacherPage(await this.request<unknown>(`/api/academic/users/teachers/search?${query.toString()}`))
  }

  async resolveTeachers(ids: readonly number[]): Promise<readonly HeadmanTeacher[]> {
    const unique = [...new Set(ids)]
    unique.forEach((id) => assertPositiveInteger(id, 'teacherId'))
    if (unique.length === 0) return []
    if (unique.length > 100) throw new RangeError('teacherIds must contain at most 100 ids')
    const query = new URLSearchParams({ ids: unique.join(',') })
    const value = await this.request<unknown>(`/api/academic/users/by-ids?${query.toString()}`)
    return embeddedItems(value, 'userSummaryResponseList').map(normalizeSummaryTeacher)
  }

  replaceAssignment(assignmentId: number, input: AssignmentReplacementInput): Promise<AssignmentReplacementResponse> {
    assertPositiveInteger(assignmentId, 'assignmentId')
    const payload = normalizeReplacementInput(input)
    return this.request<unknown>(`/api/academic/assignments/${assignmentId}/replace`, {
      method: 'POST',
      body: JSON.stringify(payload),
    }).then(normalizeAssignmentReplacement)
  }

  getReplacementStatus(operationId: string): Promise<AssignmentReplacementResponse> {
    const id = uuid(operationId, 'operationId')
    return this.request<unknown>(`/api/academic/assignments/replacements/${encodeURIComponent(id)}`)
      .then(normalizeAssignmentReplacement)
  }

  createSubject(input: CreateHeadmanSubjectInput): Promise<HeadmanSubject> {
    const payload = normalizeCreateInput(input)
    return this.request<unknown>('/api/academic/subjects', {
      method: 'POST',
      body: JSON.stringify(payload),
    }, false, 201).then((value) => normalizeSubject(readModel(value)))
  }

  updateSubject(subjectId: number, input: Omit<CreateHeadmanSubjectInput, 'initialAssignments'>): Promise<HeadmanSubject> {
    const name = requiredText(input.name, 'name')
    const type = subjectType(input.type, 'type')
    const lessonTypes = [...new Set(input.lessonTypes.map((value) => subjectType(value, 'lessonTypes')))]
    if (!lessonTypes.length || !lessonTypes.includes(type)) throw new RangeError('lessonTypes must include type')
    return this.request<unknown>(`/api/academic/subjects/${positiveInteger(subjectId, 'subjectId')}`, {
      method: 'PUT', body: JSON.stringify({ name, type, lessonTypes }),
    }).then((value) => normalizeSubject(readModel(value)))
  }

  addTeacher(
    subjectId: number,
    teacherId: number,
    input: AddHeadmanSubjectTeacherInput,
  ): Promise<void> {
    assertPositiveInteger(subjectId, 'subjectId')
    assertPositiveInteger(teacherId, 'teacherId')
    const payload = normalizeAddInput(input)
    return this.request<unknown>(`/api/academic/subjects/${subjectId}/teachers/${teacherId}`, {
      method: 'POST',
      body: JSON.stringify(payload),
    }, false, 201).then(() => undefined)
  }

  removeTeacher(subjectId: number, teacherId: number, assignmentId: number, validUntilExclusive: string, requestKey: string): Promise<void> {
    const query = new URLSearchParams({ assignmentId: String(positiveInteger(assignmentId, 'assignmentId')), validUntilExclusive: requiredDate(validUntilExclusive, 'validUntilExclusive') })
    return this.request<void>(`/api/academic/subjects/${positiveInteger(subjectId, 'subjectId')}/teachers/${positiveInteger(teacherId, 'teacherId')}?${query}`, {
      method: 'DELETE', headers: { 'Idempotency-Key': uuid(requestKey, 'requestKey') },
    }, false, 204)
  }

  resumeAdoption(assignmentId: number, operationId: string | null = null): Promise<HeadmanAdoptionAssignment> {
    const query = operationId === null ? '' : `?operationId=${encodeURIComponent(uuid(operationId, 'operationId'))}`
    return this.request<unknown>(`/api/academic/assignments/${positiveInteger(assignmentId, 'assignmentId')}/resume-adoption${query}`, { method: 'POST' }, false, 200).then((value) => {
      const source = requiredRecord(readModel(value), 'adoption assignment')
      return { ...normalizeAssignment(source), subjectId: positiveInteger(source.subjectId, 'subjectId'), groupId: positiveInteger(source.groupId, 'groupId') }
    })
  }

  private async requestPaged<T>(
    path: (page: number) => string,
    key: string,
    normalize: (value: unknown) => T,
  ): Promise<readonly T[]> {
    const items: T[] = []
    let nextPath = path(0)
    const visited = new Set<string>()
    for (let page = 0; page < 100; page += 1) {
      if (!visited.add(nextPath)) throw new Error('Сервер вернул зацикленную страницу.')
      const value = await this.request<unknown>(nextPath)
      items.push(...embeddedItems(value, key).map(normalize))
      const totalPages = pageTotalPages(value)
      if (totalPages !== null && page + 1 < totalPages) {
        nextPath = path(page + 1)
        continue
      }
      const next = nextPageHref(value)
      if (next) {
        nextPath = next
        continue
      }
      return items
    }
    throw new Error('Сервер вернул слишком много страниц.')
  }

  private async request<T>(path: string, init?: RequestInit, retried = false, expectedStatus?: number): Promise<T> {
    this.options.assertCurrent?.()
    const token = this.options.accessToken()
    const headers = new Headers(init?.headers)
    if (!headers.has('Accept')) headers.set('Accept', 'application/json')
    if (typeof init?.body === 'string' && !headers.has('Content-Type')) headers.set('Content-Type', 'application/json')
    if (token) headers.set('Authorization', `Bearer ${token}`)
    const response = await this.fetcher(path, { ...init, headers, credentials: 'include' })
    this.options.assertCurrent?.()
    if (response.ok) {
      if (expectedStatus !== undefined && response.status !== expectedStatus) throw new HeadmanSubjectsApiError(response, { detail: 'Ответ сервера не подтверждает завершение операции.' })
      if (response.status === 204) return undefined as T
      const value = await response.json() as T
      this.options.assertCurrent?.()
      return value
    }
    if (response.status === 401 && !retried && this.options.onUnauthorized) {
      await this.options.onUnauthorized()
      this.options.assertCurrent?.()
      return this.request<T>(path, init, true, expectedStatus)
    }
    let problem: unknown = null
    try {
      problem = await response.json()
    } catch {
      // Keep the HTTP status when the gateway returned no JSON body.
    }
    this.options.assertCurrent?.()
    throw new HeadmanSubjectsApiError(response, problem)
  }
}

export interface HeadmanTeacherPage {
  readonly items: readonly HeadmanTeacher[]
  readonly page: number
  readonly totalPages: number | null
}

export interface HeadmanSubjectsApiGenerationOwner {
  currentGeneration(): number
  accessTokenFor(generation: number): string | null
  refreshFor(generation: number): Promise<void>
}

export function createGenerationBoundHeadmanSubjectsApi(
  owner: HeadmanSubjectsApiGenerationOwner,
  fetcher?: typeof fetch,
): HeadmanSubjectsApi {
  const generation = owner.currentGeneration()
  const assertCurrent = (): void => {
    if (generation !== owner.currentGeneration()) throw new StaleSessionGenerationError()
  }
  const options: HeadmanSubjectsApiOptions = {
    accessToken: () => {
      assertCurrent()
      return owner.accessTokenFor(generation)
    },
    onUnauthorized: async () => {
      assertCurrent()
      await owner.refreshFor(generation)
      assertCurrent()
    },
    assertCurrent,
  }
  if (fetcher) options.fetcher = fetcher
  return new HeadmanSubjectsApi(options)
}

function normalizeCreateInput(input: CreateHeadmanSubjectInput): CreateHeadmanSubjectInput & { teacherIds: null } {
  const name = requiredText(input.name, 'name')
  const type = subjectType(input.type, 'type')
  const lessonTypes = [...new Set(input.lessonTypes.map((value) => subjectType(value, 'lessonTypes')))]
  if (lessonTypes.length === 0 || lessonTypes.length > 3 || !lessonTypes.includes(type)) {
    throw new RangeError('lessonTypes must contain 1–3 unique values including type')
  }
  const initialAssignments = input.initialAssignments.map((assignment) => ({
    teacherId: positiveInteger(assignment.teacherId, 'teacherId'),
    semesterId: positiveInteger(assignment.semesterId, 'semesterId'),
    lessonType: subjectType(assignment.lessonType, 'lessonType'),
    validFrom: requiredDate(assignment.validFrom, 'validFrom'),
    validUntilExclusive: nullableDate(assignment.validUntilExclusive),
  }))
  initialAssignments.forEach((assignment) => {
    if (!lessonTypes.includes(assignment.lessonType)) throw new RangeError('assignment lessonType must be selected')
    if (assignment.validUntilExclusive !== null && assignment.validUntilExclusive <= assignment.validFrom) {
      throw new RangeError('validUntilExclusive must be after validFrom')
    }
  })
  return { name, type, lessonTypes, initialAssignments, teacherIds: null }
}

function normalizeAddInput(input: AddHeadmanSubjectTeacherInput): AddHeadmanSubjectTeacherInput {
  const result = {
    semesterId: positiveInteger(input.semesterId, 'semesterId'),
    lessonType: subjectType(input.lessonType, 'lessonType'),
    validFrom: requiredDate(input.validFrom, 'validFrom'),
    validUntilExclusive: nullableDate(input.validUntilExclusive),
  }
  if (result.validUntilExclusive !== null && result.validUntilExclusive <= result.validFrom) {
    throw new RangeError('validUntilExclusive must be after validFrom')
  }
  return result
}

function normalizeReplacementInput(input: AssignmentReplacementInput): AssignmentReplacementInput {
  const replacementTeacherId = requiredText(input.replacementTeacherId, 'replacementTeacherId')
  if (!/^[1-9][0-9]*$/.test(replacementTeacherId)) {
    throw new RangeError('replacementTeacherId must be a positive decimal string')
  }
  return {
    replacementTeacherId,
    effectiveFrom: requiredDate(input.effectiveFrom, 'effectiveFrom'),
    requestKey: uuid(input.requestKey, 'requestKey'),
  }
}

function normalizeAssignmentReplacement(value: unknown): AssignmentReplacementResponse {
  const record = requiredRecord(value, 'assignment replacement')
  return {
    operationId: uuid(record.operationId, 'replacement.operationId'),
    sourceAssignmentId: positiveInteger(record.sourceAssignmentId, 'replacement.sourceAssignmentId'),
    targetAssignmentId: positiveInteger(record.targetAssignmentId, 'replacement.targetAssignmentId'),
    sourceTeacherId: positiveInteger(record.sourceTeacherId, 'replacement.sourceTeacherId'),
    targetTeacherId: positiveInteger(record.targetTeacherId, 'replacement.targetTeacherId'),
    subjectId: positiveInteger(record.subjectId, 'replacement.subjectId'),
    groupId: positiveInteger(record.groupId, 'replacement.groupId'),
    semesterId: positiveInteger(record.semesterId, 'replacement.semesterId'),
    lessonType: requiredText(record.lessonType, 'replacement.lessonType'),
    effectiveFrom: requiredDate(record.effectiveFrom, 'replacement.effectiveFrom'),
    sourceValidUntilExclusive: nullableDate(record.sourceValidUntilExclusive),
    targetValidUntilExclusive: nullableDate(record.targetValidUntilExclusive),
    // Do not treat an unrecognized service state as success in the screen.
    state: requiredText(record.state, 'replacement.state'),
    scheduleReceiptState: nullableText(record.scheduleReceiptState),
    movedCount: nonNegativeInteger(record.movedCount, 'replacement.movedCount'),
    skippedCount: nonNegativeInteger(record.skippedCount, 'replacement.skippedCount'),
  }
}

function normalizeSubject(value: unknown): HeadmanSubject {
  const record = requiredRecord(value, 'subject')
  const lessonTypes = arrayOf(record.lessonTypes).map((item) => subjectType(item, 'subject.lessonTypes'))
  const assignments = arrayOf(record.assignments).map(normalizeAssignment)
  return {
    id: positiveInteger(record.id, 'subject.id'),
    name: requiredText(record.name, 'subject.name'),
    type: subjectType(record.type, 'subject.type'),
    groupId: positiveInteger(record.groupId, 'subject.groupId'),
    lessonTypes,
    teacherIds: arrayOf(record.teacherIds).map((id) => positiveInteger(id, 'subject.teacherIds')),
    assignments,
    pendingAdoptions: arrayOf(record.pendingAdoptions).map(normalizeSubjectPendingAdoption),
    pendingClosures: arrayOf(record.pendingClosures).map(normalizePendingClosure),
  }
}

function normalizeAssignment(value: unknown): HeadmanSubjectAssignment {
  const record = requiredRecord(value, 'subject.assignment')
  return {
    id: positiveInteger(record.id, 'assignment.id'),
    teacherId: positiveInteger(record.teacherId, 'assignment.teacherId'),
    semesterId: positiveInteger(record.semesterId, 'assignment.semesterId'),
    lessonType: subjectType(record.lessonType, 'assignment.lessonType'),
    validFrom: requiredDate(record.validFrom, 'assignment.validFrom'),
    validUntilExclusive: nullableDate(record.validUntilExclusive),
  }
}

function normalizeSemester(value: unknown): HeadmanSemester {
  const record = requiredRecord(value, 'semester')
  return {
    id: positiveInteger(record.id, 'semester.id'),
    name: requiredText(record.name, 'semester.name'),
    dateFrom: requiredDate(record.dateFrom, 'semester.dateFrom'),
    dateTo: requiredDate(record.dateTo, 'semester.dateTo'),
    active: record.active === true,
  }
}

function normalizeTeacherPage(value: unknown): HeadmanTeacherPage {
  const record = requiredRecord(value, 'teacher page')
  const page = requiredRecord(record.page ?? {}, 'teacher page.page')
  return {
    items: embeddedItems(value, 'teacherLookupResponseList').map(normalizeTeacher),
    page: nonNegativeInteger(page.number ?? 0, 'teacher page.number'),
    totalPages: page.totalPages === undefined || page.totalPages === null
      ? null
      : nonNegativeInteger(page.totalPages, 'teacher page.totalPages'),
  }
}

function normalizeTeacher(value: unknown): HeadmanTeacher {
  const record = requiredRecord(value, 'teacher')
  return {
    id: positiveInteger(record.id, 'teacher.id'),
    fullName: requiredText(record.fullName ?? displayName(record), 'teacher.fullName'),
    employeeNumber: nullableText(record.employeeNumber),
  }
}

function normalizeSummaryTeacher(value: unknown): HeadmanTeacher {
  const record = requiredRecord(value, 'teacher summary')
  return {
    id: positiveInteger(record.id, 'teacher.id'),
    fullName: requiredText(record.fullName, 'teacher.fullName'),
    employeeNumber: null,
  }
}

function readModel(value: unknown): unknown {
  if (!isRecord(value)) return value
  return value.content ?? value
}

function embeddedItems(value: unknown, key: string): readonly unknown[] {
  if (!isRecord(value) || !isRecord(value._embedded)) return []
  const items = value._embedded[key]
  return Array.isArray(items) ? items : []
}

function pageTotalPages(value: unknown): number | null {
  if (!isRecord(value) || !isRecord(value.page)) return null
  const totalPages = value.page.totalPages
  return typeof totalPages === 'number' && Number.isSafeInteger(totalPages) && totalPages >= 0 ? totalPages : null
}

function nextPageHref(value: unknown): string | null {
  if (!isRecord(value) || !isRecord(value._links) || !isRecord(value._links.next)) return null
  const href = value._links.next.href
  return typeof href === 'string' && href.startsWith('/api/') ? href : null
}

function displayName(record: Record<string, unknown>): string | null {
  const parts = [record.lastName, record.firstName, record.middleName]
    .filter((part): part is string => typeof part === 'string' && part.trim() !== '')
  return parts.length > 0 ? parts.join(' ') : null
}

function subjectType(value: unknown, field: string): HeadmanSubjectType {
  if (value === 'LECTURE' || value === 'PRACTICE' || value === 'LAB') return value
  throw new Error(`Сервер вернул некорректный тип ${field}.`)
}

function requiredRecord(value: unknown, field: string): Record<string, unknown> {
  if (!isRecord(value)) throw new Error(`Сервер вернул некорректные данные ${field}.`)
  return value
}

function arrayOf(value: unknown): readonly unknown[] {
  return Array.isArray(value) ? value : []
}

function requiredText(value: unknown, field: string): string {
  if (typeof value !== 'string' || value.trim() === '') throw new Error(`Сервер вернул пустое поле ${field}.`)
  return value.trim()
}

function nullableText(value: unknown): string | null {
  return typeof value === 'string' && value.trim() !== '' ? value.trim() : null
}

function requiredDate(value: unknown, field: string): string {
  const text = requiredText(value, field)
  if (!/^\d{4}-\d{2}-\d{2}$/.test(text)) throw new Error(`Сервер вернул некорректную дату ${field}.`)
  return text
}

function nullableDate(value: unknown): string | null {
  if (value === null || value === undefined || value === '') return null
  return requiredDate(value, 'date')
}

function positiveInteger(value: unknown, field: string): number {
  if (typeof value !== 'number' || !Number.isSafeInteger(value) || value <= 0) {
    throw new Error(`Сервер вернул некорректный идентификатор ${field}.`)
  }
  return value
}

function nonNegativeInteger(value: unknown, field: string): number {
  if (typeof value !== 'number' || !Number.isSafeInteger(value) || value < 0) throw new Error(`Сервер вернул некорректное поле ${field}.`)
  return value
}

function assertPositiveInteger(value: number, field: string): void {
  if (!Number.isSafeInteger(value) || value <= 0) throw new RangeError(`${field} must be a positive integer`)
}

function uuid(value: unknown, field: string): string {
  const text = requiredText(value, field)
  if (!/^[0-9a-f]{8}-[0-9a-f]{4}-[1-8][0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/i.test(text)) {
    throw new Error(`Сервер вернул некорректный UUID ${field}.`)
  }
  return text
}

function isRecord(value: unknown): value is Record<string, unknown> {
  return typeof value === 'object' && value !== null && !Array.isArray(value)
}

function problemDetail(value: unknown): string | null {
  if (!isRecord(value)) return null
  return typeof value.detail === 'string'
    ? value.detail
    : typeof value.title === 'string' ? value.title : null
}

function normalizePendingAdoption(value: unknown): HeadmanPendingAdoption {
  const source = requiredRecord(value, 'pending adoption')
  return { targetAssignmentId: positiveInteger(source.targetAssignmentId, 'targetAssignmentId'), adoptionOperationId: uuid(source.adoptionOperationId, 'adoptionOperationId'), ...(typeof source.state === 'string' ? { state: source.state } : {}) }
}
function normalizePendingClosure(value: unknown): HeadmanPendingClosure {
  const source = requiredRecord(value, 'pending closure')
  return { operationId: uuid(source.operationId, 'closure.operationId'), assignmentId: positiveInteger(source.assignmentId, 'closure.assignmentId'), requestKey: uuid(source.requestKey, 'closure.requestKey'), validUntilExclusive: requiredDate(source.validUntilExclusive, 'closure.validUntilExclusive'),
    teacherId: positiveInteger(source.teacherId, 'closure.teacherId'), subjectId: positiveInteger(source.subjectId, 'closure.subjectId'), groupId: positiveInteger(source.groupId, 'closure.groupId'), semesterId: positiveInteger(source.semesterId, 'closure.semesterId'), lessonType: subjectType(source.lessonType, 'closure.lessonType') }
}
function normalizeSubjectPendingAdoption(value: unknown): HeadmanSubjectPendingAdoption {
  const source = requiredRecord(value, 'subject pending adoption'), handle = normalizePendingAdoption(source)
  if (!Object.prototype.hasOwnProperty.call(source, 'validUntilExclusive')) throw new Error('Сервер не вернул исходную границу назначения.')
  const tuple = normalizeAssignment({ ...source, id: handle.targetAssignmentId })
  return { ...handle, teacherId: tuple.teacherId, semesterId: tuple.semesterId, lessonType: tuple.lessonType, validFrom: tuple.validFrom, validUntilExclusive: tuple.validUntilExclusive,
    subjectId: positiveInteger(source.subjectId, 'pending.subjectId'), groupId: positiveInteger(source.groupId, 'pending.groupId'), state: requiredText(source.state, 'pending.state') }
}
export function headmanAdoptionPending(cause: unknown): { subjectId: number; pendingAdoptions: readonly HeadmanPendingAdoption[] } | null {
  if (!(cause instanceof HeadmanSubjectsApiError) || cause.response.status !== 503 || !isRecord(cause.problem)
    || typeof cause.problem.type !== 'string' || !cause.problem.type.endsWith('assignment-adoption-pending') || !isRecord(cause.problem.extras)) return null
  try {
    const source = cause.problem.extras
    if (!Array.isArray(source.pendingAdoptions) || !source.pendingAdoptions.length) return null
    return { subjectId: positiveInteger(source.subjectId, 'subjectId'), pendingAdoptions: source.pendingAdoptions.map(normalizePendingAdoption) }
  } catch { return null }
}
export function headmanIdempotencyMismatch(cause: unknown): boolean {
  return cause instanceof HeadmanSubjectsApiError && cause.response.status === 409 && isRecord(cause.problem)
    && isRecord(cause.problem.extras) && cause.problem.extras.code === 'IDEMPOTENCY_PAYLOAD_MISMATCH'
}
