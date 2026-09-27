import { StudentApiError, validateIdempotencyKey } from '../../api/student-client'
import type {
  StudentExcuseRequest,
  StudentLateCheckinRequest,
  StudentRequestDetail,
  StudentRequestOptions,
  StudentRequestSummary,
} from '../../api/types'
import { studentFeatureScopeIdentity, type StudentFeatureScope } from '../../shared/session-owner'
import { canCancelRequest } from './state'
import { acceptsRequestFile, requestAttachmentFormatHint } from './attachment-validation'
import type {
  ExcuseRequestPayload,
  LateCheckinRequestPayload,
  RequestAccessState,
  RequestAttachment,
  RequestBucket,
  RequestDetail,
  RequestFileLimits,
  RequestFileRef,
  RequestLesson,
  RequestLessonOption,
  RequestOptions,
  RequestReasonOption,
  RequestSummary,
  RequestKind,
} from './types'
import type { RequestsPort } from './requests-port'

export type RequestsCommandStatus = 'pending' | 'ambiguous' | 'validation-error' | 'completed'

export interface RequestsCommandView {
  kind: RequestKind
  status: RequestsCommandStatus
  idempotencyKey: string
  error: string | null
}

export interface RequestsBucketView {
  requests: RequestDetail[]
  loading: boolean
  loadingMore: boolean
  error: string | null
  stale: boolean
  page: number
  totalPages: number
  totalElements: number
}

export interface RequestsControllerView {
  bucket: RequestBucket
  access: RequestAccessState
  open: RequestsBucketView
  archive: RequestsBucketView
  options: RequestOptions | null
  optionsLoading: boolean
  optionsError: string | null
  mutation: 'idle' | 'submitting' | 'cancelling' | 'downloading'
  mutationError: string | null
  command: RequestsCommandView | null
}

export interface RequestsControllerOptions {
  port: () => RequestsPort | null
  scope: () => StudentFeatureScope | null
  offline: () => boolean
  readOnly: () => boolean
  view?: RequestsControllerView
  keyFactory?: () => string
}

export class RequestsError extends Error {
  constructor(
    readonly code: 'OFFLINE' | 'READ_ONLY' | 'FORBIDDEN' | 'NO_ACTIVE_SEMESTER' | 'AMBIGUOUS' | 'VALIDATION' | 'STALE' | 'UNAVAILABLE',
    message: string,
  ) {
    super(message)
    this.name = 'RequestsError'
  }
}

interface CommandRecord {
  kind: RequestKind
  idempotencyKey: string
  fingerprint: string
  payload: ExcuseRequestPayload | LateCheckinRequestPayload
  files: RequestFileRef[]
  status: RequestsCommandStatus
  error: string | null
  promise?: Promise<RequestDetail>
}

export const createRequestsControllerView = (): RequestsControllerView => ({
  bucket: 'open',
  access: 'allowed',
  open: defaultBucketView(),
  archive: defaultBucketView(),
  options: null,
  optionsLoading: false,
  optionsError: null,
  mutation: 'idle',
  mutationError: null,
  command: null,
})

export function shouldLoadRequestOptions(
  options: RequestOptions | null,
  optionsLoading: boolean,
  offline: boolean,
): boolean {
  return !offline && !options && !optionsLoading
}

function defaultBucketView(): RequestsBucketView {
  return {
    requests: [],
    loading: false,
    loadingMore: false,
    error: null,
    stale: false,
    page: -1,
    totalPages: 0,
    totalElements: 0,
  }
}

function cloneFileRef(file: RequestFileRef): RequestFileRef {
  return { ...file }
}

function clonePayload(payload: ExcuseRequestPayload | LateCheckinRequestPayload): ExcuseRequestPayload | LateCheckinRequestPayload {
  if ('files' in payload) return { ...payload, lessonIds: [...payload.lessonIds], files: payload.files.map(cloneFileRef) }
  return { ...payload }
}

function defaultKeyFactory(): string {
  const random = typeof globalThis.crypto?.randomUUID === 'function'
    ? globalThis.crypto.randomUUID().replaceAll('-', '')
    : `${Date.now().toString(36)}${Math.random().toString(36).slice(2)}`
  return `rct-${random}`.slice(0, 128)
}

function mapLesson(value: StudentRequestSummary['lessons'][number]): RequestLesson {
  const lesson: RequestLesson = { id: value.id, lessonNumber: value.lessonNumber, status: value.status, blocked: value.blocked }
  if (value.subjectId !== undefined) lesson.subjectId = value.subjectId
  if (value.subjectName !== undefined) lesson.subjectName = value.subjectName
  if (value.subjectType !== undefined) lesson.subjectType = value.subjectType
  if (value.semesterId !== undefined) lesson.semesterId = value.semesterId
  if (value.date !== undefined) lesson.date = value.date
  if (value.startsAt !== undefined) lesson.startsAt = value.startsAt
  if (value.endsAt !== undefined) lesson.endsAt = value.endsAt
  return lesson
}

function mapSummary(value: StudentRequestSummary, scope: StudentFeatureScope | null, context: { offline: boolean; readOnly: boolean }): RequestSummary {
  const summary: RequestSummary = {
    id: value.id,
    kind: value.kind,
    status: value.status,
    origin: value.origin,
    lessons: value.lessons.map(mapLesson),
    createdAt: value.createdAt,
    updatedAt: value.updatedAt,
  }
  return { ...summary, canCancel: canCancelRequest(summary, scope, context) }
}

function mapAttachment(value: StudentRequestDetail['attachments'][number]): RequestAttachment {
  return {
    id: value.id,
    name: value.name,
    contentType: value.contentType,
    sizeBytes: value.sizeBytes,
    state: value.state,
    expiresAt: value.expiresAt,
    expiredAt: value.expiredAt,
  }
}

function mapDetail(value: StudentRequestDetail, scope: StudentFeatureScope | null, context: { offline: boolean; readOnly: boolean }): RequestDetail {
  return {
    summary: mapSummary(value.summary, scope, context),
    reason: value.reason,
    comment: value.comment,
    decision: value.decision,
    attachments: value.attachments.map(mapAttachment),
    detailState: 'ready',
    detailError: null,
  }
}

function toRequestOptions(value: StudentRequestOptions): RequestOptions {
  const reasons: RequestReasonOption[] = value.reasons.map((reason) => ({
    code: reason.code,
    label: reason.label,
    commentRequired: reason.commentRequired,
  }))
  const files: RequestFileLimits = {
    maxFiles: value.files.maxFiles,
    maxBytesPerFile: value.files.maxBytesPerFile,
    maxBytesTotal: value.files.maxBytesTotal,
    contentTypes: [...value.files.contentTypes],
    extensions: [...value.files.extensions],
  }
  const lessons: RequestLessonOption[] = value.lessons.map((option) => ({
    lesson: mapLesson(option.lesson),
    excuseEligible: option.excuseEligible,
    lateCheckinEligible: option.lateCheckinEligible,
    pendingRequests: option.pendingRequests.map((pending) => ({
      id: pending.id,
      kind: pending.kind,
      origin: pending.origin,
    })),
  }))
  return {
    reasons,
    files,
    budget: {
      semesterId: value.budget.semesterId,
      limit: value.budget.limit,
      used: value.budget.used,
      remaining: value.budget.remaining,
    },
    lessons,
  }
}

function errorMessage(value: unknown, fallback: string): string {
  if (value instanceof StudentApiError) return value.problem?.detail || value.message || fallback
  if (value instanceof Error && value.message) return value.message
  return fallback
}

function isAuthError(value: unknown): boolean {
  return value instanceof StudentApiError
    ? value.response.status === 401 || value.response.status === 403
    : typeof value === 'object' && value !== null && 'status' in value && (value.status === 401 || value.status === 403)
}

function isValidationFailure(value: unknown): boolean {
  if (!(value instanceof StudentApiError)) return false
  const code = value.problem?.code
  return value.response.status === 400
    || value.response.status === 413
    || code === 'INVALID_REQUEST'
    || code === 'INVALID_IDEMPOTENCY_KEY'
    || code === 'PAYLOAD_TOO_LARGE'
}

function stableFileFingerprint(file: RequestFileRef): unknown {
  return [file.id, file.name, file.size, file.type, file.lastModified ?? null, Boolean(file.file)]
}

function payloadFingerprint(kind: RequestKind, payload: ExcuseRequestPayload | LateCheckinRequestPayload): string {
  return JSON.stringify({
    kind,
    payload: 'files' in payload
      ? {
          lessonIds: [...payload.lessonIds],
          reason: payload.reason,
          comment: payload.comment,
          files: payload.files.map(stableFileFingerprint),
        }
      : { lessonId: payload.lessonId },
  })
}

export function validateExcusePayload(payload: ExcuseRequestPayload, options: RequestOptions | null): readonly string[] {
  const errors: string[] = []
  if (!options) return ['Параметры заявок ещё не загружены.']
  const lessons = options.lessons ?? []
  if (payload.lessonIds.length === 0) errors.push('Выбери хотя бы одну пару.')
  const selected = new Set(payload.lessonIds)
  for (const id of selected) {
    const option = lessons.find((value) => value.lesson?.id === id)
    if (!option || option.excuseEligible !== true || (option.pendingRequests?.length ?? 0) > 0) errors.push('Выбранная пара больше недоступна.')
  }
  const reason = options.reasons?.find((value) => value.code === payload.reason)
  if (!reason) errors.push('Выбери доступную причину.')
  else if (reason.commentRequired && payload.comment.trim().length === 0) errors.push('Добавь комментарий для выбранной причины.')

  const limits = options.files
  const files = payload.files
  if (limits?.maxFiles !== null && limits?.maxFiles !== undefined && files.length > limits.maxFiles) errors.push('Слишком много вложений.')
  let total = 0
  for (const file of files) {
    if (limits?.maxBytesPerFile !== null && limits?.maxBytesPerFile !== undefined && file.size > limits.maxBytesPerFile) errors.push(file.name + ': размер больше допустимого.')
    if (!acceptsRequestFile(file, limits)) {
      errors.push(file.name + ': формат не поддерживается. ' + (requestAttachmentFormatHint(limits) || 'MIME и расширение должны соответствовать.'))
    }
    if (!file.file) errors.push(file.name + ': файл больше недоступен.')
    total += file.size
  }
  if (limits?.maxBytesTotal !== null && limits?.maxBytesTotal !== undefined && total > limits.maxBytesTotal) errors.push('Общий размер вложений больше допустимого.')
  return errors
}

export function validateLateCheckinPayload(payload: LateCheckinRequestPayload, options: RequestOptions | null): readonly string[] {
  if (!options) return ['Параметры заявок ещё не загружены.']
  const option = (options.lessons ?? []).find((value) => value.lesson?.id === payload.lessonId)
  const errors: string[] = []
  if (!option || option.lateCheckinEligible !== true || (option.pendingRequests?.length ?? 0) > 0) errors.push('Выбранная пара больше недоступна.')
  const remaining = options.budget?.remaining
  if (typeof remaining === 'number' && Number.isFinite(remaining) && remaining <= 0) errors.push('Попытки закончились.')
  return errors
}

export class RequestsController {
  readonly view: RequestsControllerView
  private readonly commands = new Map<RequestKind, CommandRecord>()
  private readonly keyFactory: () => string
  private contextGeneration = 0
  private readonly bucketGenerations: Record<RequestBucket, number> = { open: 0, archive: 0 }
  private optionsGeneration = 0
  private disposed = false
  private lastIdentity: string | null

  constructor(private readonly options: RequestsControllerOptions) {
    this.view = options.view ?? createRequestsControllerView()
    this.keyFactory = options.keyFactory ?? defaultKeyFactory
    this.lastIdentity = this.identity()
    this.refreshAccess()
  }

  private identity(): string | null {
    const scope = this.options.scope()
    return scope ? studentFeatureScopeIdentity(scope) : null
  }

  private contextChanged(): boolean {
    const identity = this.identity()
    if (identity === this.lastIdentity) return false
    this.lastIdentity = identity
    this.contextGeneration += 1
    this.bucketGenerations.open += 1
    this.bucketGenerations.archive += 1
    this.optionsGeneration += 1
    this.commands.clear()
    this.view.command = null
    this.view.mutation = 'idle'
    this.view.mutationError = null
    this.view.options = null
    this.view.optionsLoading = false
    this.view.optionsError = null
    this.view.open = defaultBucketView()
    this.view.archive = defaultBucketView()
    return true
  }

  private refreshAccess(bucket = this.view.bucket): RequestAccessState {
    const scope = this.options.scope()
    const access: RequestAccessState = !scope || scope.activeRole !== 'STUDENT' || !scope.userId
      ? 'forbidden'
      : bucket === 'open' && !scope.semesterId
        ? 'no-active-semester'
        : 'allowed'
    this.view.access = access
    return access
  }

  onContextChanged(): void {
    this.contextChanged()
    this.refreshAccess()
    if (this.options.offline()) {
      this.view.open.stale = this.view.open.requests.length > 0
      this.view.archive.stale = this.view.archive.requests.length > 0
    }
  }

  selectBucket(bucket: RequestBucket): void {
    this.view.bucket = bucket
    this.refreshAccess(bucket)
  }

  bucketView(bucket = this.view.bucket): RequestsBucketView {
    return bucket === 'open' ? this.view.open : this.view.archive
  }

  async loadTargetDetail(id: string, expectedKind: RequestKind): Promise<RequestDetail> {
    this.contextChanged()
    const scope = this.options.scope()
    if (!scope || scope.activeRole !== 'STUDENT' || !scope.userId) {
      throw new RequestsError('FORBIDDEN', 'Раздел недоступен.')
    }
    if (this.options.offline()) throw new RequestsError('OFFLINE', 'Офлайн: заявка станет доступна онлайн.')
    const identity = this.identity()
    const contextGeneration = this.contextGeneration
    const port = this.options.port()
    if (!identity || !port) throw new RequestsError('FORBIDDEN', 'Раздел недоступен.')

    const detail = await port.getRequest(id)
    if (!this.isCurrentContext(identity, contextGeneration)) {
      throw new RequestsError('STALE', 'Сессия заявки больше не актуальна.')
    }
    if (detail.summary.id !== id || detail.summary.kind !== expectedKind) {
      throw new RequestsError('UNAVAILABLE', 'Заявка больше недоступна.')
    }
    return mapDetail(detail, this.options.scope(), {
      offline: this.options.offline(),
      readOnly: this.options.readOnly(),
    })
  }

  async cancelTargetRequest(id: string, expectedKind: RequestKind): Promise<RequestDetail> {
    this.assertMutable()
    const identity = this.identity()
    const contextGeneration = this.contextGeneration
    const port = this.options.port()
    if (!identity || !port) throw new RequestsError('FORBIDDEN', 'Раздел недоступен.')

    const current = await port.getRequest(id)
    if (!this.isCurrentContext(identity, contextGeneration)) {
      throw new RequestsError('STALE', 'Сессия заявки больше не актуальна.')
    }
    this.assertMutable()
    if (!this.isCurrentContext(identity, contextGeneration)) {
      throw new RequestsError('STALE', 'Сессия заявки больше не актуальна.')
    }
    if (current.summary.id !== id || current.summary.kind !== expectedKind) {
      throw new RequestsError('UNAVAILABLE', 'Заявка больше недоступна.')
    }
    if (!canCancelRequest(current.summary, this.options.scope(), {
      offline: this.options.offline(),
      readOnly: this.options.readOnly(),
    })) {
      throw new RequestsError('VALIDATION', 'Эту заявку сейчас нельзя отменить.')
    }

    this.view.mutation = 'cancelling'
    this.view.mutationError = null
    try {
      const refreshed = await port.cancelRequest(id)
      if (!this.isCurrentContext(identity, contextGeneration)) {
        throw new RequestsError('STALE', 'Сессия заявки больше не актуальна.')
      }
      if (refreshed.summary.id !== id || refreshed.summary.kind !== expectedKind) {
        throw new RequestsError('UNAVAILABLE', 'Заявка больше недоступна.')
      }
      const mapped = mapDetail(refreshed, this.options.scope(), {
        offline: this.options.offline(),
        readOnly: this.options.readOnly(),
      })
      await this.refreshLoadedBuckets()
      if (!this.isCurrentContext(identity, contextGeneration)) {
        throw new RequestsError('STALE', 'Сессия заявки больше не актуальна.')
      }
      return mapped
    } catch (error) {
      if (!this.isCurrentContext(identity, contextGeneration)) {
        throw new RequestsError('STALE', 'Сессия заявки больше не актуальна.')
      }
      this.view.mutationError = errorMessage(error, 'Не удалось отменить заявку.')
      throw error
    } finally {
      if (this.isCurrentContext(identity, contextGeneration)) this.view.mutation = 'idle'
    }
  }

  private assertReadable(bucket: RequestBucket): void {
    this.contextChanged()
    const access = this.refreshAccess(bucket)
    if (access === 'forbidden') throw new RequestsError('FORBIDDEN', 'Раздел недоступен.')
    if (access === 'no-active-semester') throw new RequestsError('NO_ACTIVE_SEMESTER', 'Нет активного семестра.')
    if (!this.options.port()) throw new RequestsError('FORBIDDEN', 'Раздел недоступен.')
  }

  private assertMutable(): void {
    this.contextChanged()
    if (this.options.offline()) throw new RequestsError('OFFLINE', 'Офлайн: действие станет доступно онлайн.')
    if (this.options.readOnly() || this.options.scope()?.readOnly) throw new RequestsError('READ_ONLY', 'Действие недоступно в режиме только чтения.')
    const scope = this.options.scope()
    if (!scope || scope.activeRole !== 'STUDENT' || !scope.userId) throw new RequestsError('FORBIDDEN', 'Раздел недоступен.')
    if (!scope.semesterId) throw new RequestsError('NO_ACTIVE_SEMESTER', 'Нет активного семестра.')
    if (!this.options.port()) throw new RequestsError('FORBIDDEN', 'Раздел недоступен.')
  }

  private isCurrentContext(identity: string, contextGeneration: number): boolean {
    return !this.disposed && identity === this.identity() && contextGeneration === this.contextGeneration
  }

  private isCurrentBucket(identity: string, contextGeneration: number, bucket: RequestBucket, generation: number): boolean {
    return this.isCurrentContext(identity, contextGeneration) && generation === this.bucketGenerations[bucket]
  }

  private isCurrentOptions(identity: string, contextGeneration: number, generation: number): boolean {
    return this.isCurrentContext(identity, contextGeneration) && generation === this.optionsGeneration
  }

  private isCurrentIdentity(identity: string): boolean {
    return !this.disposed && identity === this.identity()
  }

  async loadBucket(bucket = this.view.bucket, page = 0): Promise<void> {
    this.assertReadable(bucket)
    if (this.options.offline()) {
      const state = this.bucketView(bucket)
      state.stale = state.requests.length > 0
      return
    }
    const state = this.bucketView(bucket)
    const append = page > 0
    if (append && (state.loading || state.loadingMore)) return
    const identity = this.identity()
    if (!identity) throw new RequestsError('FORBIDDEN', 'Раздел недоступен.')
    const contextGeneration = this.contextGeneration
    const generation = ++this.bucketGenerations[bucket]
    state.error = null
    state.stale = false
    state.loading = !append
    state.loadingMore = append
    try {
      const response = await this.options.port()!.listRequests(bucket === 'open' ? 'OPEN' : 'ARCHIVE', page)
      if (!this.isCurrentBucket(identity, contextGeneration, bucket, generation)) return
      const summaries = response.content.slice(0, 10)
      const details = await this.hydrate(summaries, identity, contextGeneration, bucket, generation)
      if (!this.isCurrentBucket(identity, contextGeneration, bucket, generation)) return
      state.page = response.page
      state.totalPages = response.totalPages
      state.totalElements = response.totalElements
      state.requests = append ? mergeDetails(state.requests, details) : details
    } catch (error) {
      if (!this.isCurrentBucket(identity, contextGeneration, bucket, generation)) return
      state.error = errorMessage(error, 'Не удалось получить заявки.')
      throw error
    } finally {
      if (this.isCurrentBucket(identity, contextGeneration, bucket, generation)) {
        state.loading = false
        state.loadingMore = false
      }
    }
  }

  async loadMore(bucket = this.view.bucket): Promise<void> {
    const state = this.bucketView(bucket)
    if (state.page < 0 || state.page + 1 >= state.totalPages) return
    await this.loadBucket(bucket, state.page + 1)
  }

  private async hydrate(
    summaries: readonly StudentRequestSummary[],
    identity: string,
    contextGeneration: number,
    bucket: RequestBucket,
    generation: number,
  ): Promise<RequestDetail[]> {
    const result: RequestDetail[] = new Array(summaries.length)
    let nextIndex = 0
    const worker = async (): Promise<void> => {
      while (nextIndex < summaries.length) {
        if (!this.isCurrentBucket(identity, contextGeneration, bucket, generation)) return
        const index = nextIndex++
        const summary = summaries[index]
        if (!summary) continue
        const projected = mapSummary(summary, this.options.scope(), { offline: this.options.offline(), readOnly: this.options.readOnly() })
        try {
          const detail = await this.options.port()!.getRequest(summary.id)
          if (!this.isCurrentBucket(identity, contextGeneration, bucket, generation)) return
          if (detail.summary.id !== summary.id) {
            result[index] = unavailableDetail(projected, 'Сервис вернул другую заявку.')
          } else {
            result[index] = mapDetail(detail, this.options.scope(), { offline: this.options.offline(), readOnly: this.options.readOnly() })
          }
        } catch (error) {
          if (isAuthError(error)) throw error
          result[index] = unavailableDetail(projected, errorMessage(error, 'Детали заявки недоступны.'), error instanceof StudentApiError ? 'error' : 'unavailable')
        }
      }
    }
    await Promise.all(Array.from({ length: Math.min(3, summaries.length) }, () => worker()))
    return result.filter((value): value is RequestDetail => Boolean(value))
  }

  async loadOptions(): Promise<void> {
    this.contextChanged()
    const access = this.refreshAccess('open')
    if (access === 'forbidden') throw new RequestsError('FORBIDDEN', 'Раздел недоступен.')
    if (access === 'no-active-semester') throw new RequestsError('NO_ACTIVE_SEMESTER', 'Нет активного семестра.')
    if (this.options.offline()) return
    this.view.optionsLoading = true
    this.view.optionsError = null
    const identity = this.identity()
    const contextGeneration = this.contextGeneration
    const generation = ++this.optionsGeneration
    try {
      const value = await this.options.port()!.getRequestOptions()
      if (!identity || !this.isCurrentOptions(identity, contextGeneration, generation)) return
      this.view.options = toRequestOptions(value)
    } catch (error) {
      if (!identity || !this.isCurrentOptions(identity, contextGeneration, generation)) return
      this.view.optionsError = errorMessage(error, 'Не удалось получить параметры заявок.')
      throw error
    } finally {
      if (!identity || this.isCurrentOptions(identity, contextGeneration, generation)) this.view.optionsLoading = false
    }
  }

  commandState(kind: RequestKind): RequestsCommandView | null {
    const command = this.commands.get(kind)
    return command ? { kind, status: command.status, idempotencyKey: command.idempotencyKey, error: command.error } : null
  }

  abandonCommand(kind: RequestKind): void {
    const command = this.commands.get(kind)
    if (!command || command.status !== 'ambiguous') return
    this.commands.delete(kind)
    if (this.view.command?.kind === kind) this.view.command = null
  }

  /** Explicit reconciliation releases the ambiguous command after the caller
   * has inspected the server state (the controller never guesses an outcome). */
  reconcileCommand(kind: RequestKind): void {
    const command = this.commands.get(kind)
    if (!command || command.status !== 'ambiguous') return
    command.status = 'completed'
    command.error = null
    this.publishCommand(command)
  }

  async submitExcuse(payload: ExcuseRequestPayload): Promise<RequestDetail> {
    this.assertMutable()
    const errors = validateExcusePayload(payload, this.view.options)
    if (errors.length > 0) throw new RequestsError('VALIDATION', errors.join(' '))
    const command: StudentExcuseRequest = {
      lessonIds: [...payload.lessonIds],
      reason: payload.reason as StudentExcuseRequest['reason'],
      comment: payload.comment,
    }
    const files = payload.files.map((value) => value.file).filter((value): value is File => Boolean(value))
    return this.runCommand('EXCUSE', payload, files, () => this.options.port()!.submitExcuse(command, files, this.commands.get('EXCUSE')!.idempotencyKey))
  }

  async submitLateCheckin(payload: LateCheckinRequestPayload): Promise<RequestDetail> {
    this.assertMutable()
    const errors = validateLateCheckinPayload(payload, this.view.options)
    if (errors.length > 0) throw new RequestsError('VALIDATION', errors.join(' '))
    const command: StudentLateCheckinRequest = { lessonId: payload.lessonId }
    return this.runCommand('LATE_CHECKIN', payload, [], () => this.options.port()!.submitLateCheckin(command, this.commands.get('LATE_CHECKIN')!.idempotencyKey))
  }

  private runCommand(
    kind: RequestKind,
    payload: ExcuseRequestPayload | LateCheckinRequestPayload,
    files: readonly File[],
    send: () => Promise<StudentRequestDetail>,
  ): Promise<RequestDetail> {
    const fingerprint = payloadFingerprint(kind, payload)
    const current = this.commands.get(kind)
    if (current?.status === 'ambiguous' && current.fingerprint !== fingerprint) {
      return Promise.reject(new RequestsError('AMBIGUOUS', 'Предыдущая отправка не подтверждена. Сначала сверни её результат или откажись от команды.'))
    }
    if (current?.status === 'pending' && current.promise) return current.promise
    const record: CommandRecord = current?.status === 'ambiguous'
      ? current
      : {
          kind,
          idempotencyKey: this.keyFactory(),
          fingerprint,
          payload: clonePayload(payload),
          files: files.map((file) => ({ id: file.name, name: file.name, size: file.size, type: file.type, file })),
          status: 'pending',
          error: null,
        }
    validateIdempotencyKey(record.idempotencyKey)
    record.status = 'pending'
    record.error = null
    this.commands.set(kind, record)
    this.publishCommand(record)
    this.view.mutation = 'submitting'
    this.view.mutationError = null
    const commandIdentity = this.identity()
    const promise = send()
      .then(async (value) => {
        if (!commandIdentity || !this.isCurrentIdentity(commandIdentity)) throw new RequestsError('STALE', 'Сессия заявки больше не актуальна.')
        const mapped = mapDetail(value, this.options.scope(), { offline: this.options.offline(), readOnly: this.options.readOnly() })
        record.status = 'completed'
        record.error = null
        this.publishCommand(record)
        this.view.mutation = 'idle'
        await this.loadBucket('open', 0).catch(() => undefined)
        return mapped
      })
      .catch((error: unknown) => {
        if (!commandIdentity || !this.isCurrentIdentity(commandIdentity)) throw new RequestsError('STALE', 'Сессия заявки больше не актуальна.')
        const ambiguous = !isValidationFailure(error)
        record.status = ambiguous ? 'ambiguous' : 'validation-error'
        record.error = errorMessage(error, ambiguous ? 'Результат отправки не подтверждён.' : 'Заявка не прошла проверку.')
        this.publishCommand(record)
        this.view.mutation = 'idle'
        this.view.mutationError = record.error
        throw error
      })
    record.promise = promise
    return promise
  }

  private publishCommand(record: CommandRecord): void {
    this.view.command = {
      kind: record.kind,
      status: record.status,
      idempotencyKey: record.idempotencyKey,
      error: record.error,
    }
  }

  async cancelRequest(id: string): Promise<void> {
    this.assertMutable()
    const bucket = this.findBucket(id)
    const detail = bucket ? this.bucketView(bucket).requests.find((item) => item.summary.id === id) : null
    if (!bucket || !detail || !canCancelRequest(detail.summary, this.options.scope(), { offline: this.options.offline(), readOnly: this.options.readOnly() })) {
      throw new RequestsError('VALIDATION', 'Эту заявку сейчас нельзя отменить.')
    }
    const requestBucket = bucket
    this.view.mutation = 'cancelling'
    this.view.mutationError = null
    const commandIdentity = this.identity()
    try {
      await this.options.port()!.cancelRequest(id)
      if (!commandIdentity || !this.isCurrentIdentity(commandIdentity)) throw new RequestsError('STALE', 'Сессия заявки больше не актуальна.')
      await this.loadBucket(requestBucket, 0)
      await this.loadBucket(requestBucket === 'open' ? 'archive' : 'open', 0).catch(() => undefined)
    } catch (error) {
      if (!commandIdentity || !this.isCurrentIdentity(commandIdentity)) throw new RequestsError('STALE', 'Сессия заявки больше не актуальна.')
      if (error instanceof StudentApiError && error.response.status === 409) {
        await this.loadBucket(requestBucket, 0).catch(() => undefined)
      }
      this.view.mutationError = errorMessage(error, 'Не удалось отменить заявку.')
      throw error
    } finally {
      if (!commandIdentity || this.isCurrentIdentity(commandIdentity)) this.view.mutation = 'idle'
    }
  }

  async downloadAttachment(requestId: string, attachmentId: string): Promise<Blob> {
    this.assertReadable(this.view.bucket)
    if (this.options.offline()) throw new RequestsError('OFFLINE', 'Вложение доступно только онлайн.')
    const commandIdentity = this.identity()
    this.view.mutation = 'downloading'
    try {
      const value = await this.options.port()!.downloadRequestAttachment(requestId, attachmentId)
      if (!commandIdentity || !this.isCurrentIdentity(commandIdentity)) throw new RequestsError('STALE', 'Сессия заявки больше не актуальна.')
      return value
    } finally {
      if (!commandIdentity || this.isCurrentIdentity(commandIdentity)) this.view.mutation = 'idle'
    }
  }

  dispose(): void {
    this.disposed = true
    this.contextGeneration += 1
    this.bucketGenerations.open += 1
    this.bucketGenerations.archive += 1
    this.optionsGeneration += 1
    this.commands.clear()
  }

  private findBucket(id: string): RequestBucket | null {
    if (this.view.open.requests.some((item) => item.summary.id === id)) return 'open'
    if (this.view.archive.requests.some((item) => item.summary.id === id)) return 'archive'
    return null
  }

  private async refreshLoadedBuckets(): Promise<void> {
    const loadedBuckets = (['open', 'archive'] as const).filter((bucket) => {
      const state = this.bucketView(bucket)
      return state.page >= 0 || state.loading || state.loadingMore
    })
    await Promise.all(loadedBuckets.map((bucket) => this.loadBucket(bucket, 0).catch(() => undefined)))
  }
}

function unavailableDetail(summary: RequestSummary, message: string, state: 'unavailable' | 'error' = 'unavailable'): RequestDetail {
  return {
    summary,
    detailState: state,
    detailError: message,
  }
}

function mergeDetails(previous: readonly RequestDetail[], next: readonly RequestDetail[]): RequestDetail[] {
  const byId = new Map(previous.map((value) => [value.summary.id, value]))
  for (const value of next) byId.set(value.summary.id, value)
  return [...byId.values()]
}
