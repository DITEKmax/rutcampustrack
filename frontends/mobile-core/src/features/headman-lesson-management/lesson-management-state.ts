import type {
  HeadmanJournalLesson,
  HeadmanLessonRoomInput,
  HeadmanLessonTransferRequest,
  HeadmanLessonTransferResponse,
  HeadmanJournalApi,
} from '../headman-journal/headman-journal-client'
import { HeadmanJournalApiError } from '../headman-journal/headman-journal-client'
import type {
  HeadmanOneOffCreateInput,
  HeadmanOneOffLesson,
  HeadmanScheduleApi,
  HeadmanScheduleAssignment,
  HeadmanScheduleSemester,
  HeadmanLessonSlotCatalog,
} from '../schedule/headman-schedule-client'
import { HeadmanScheduleApiError, canCorrectRejectedOneOff } from '../schedule/headman-schedule-client'
import type { AcademicWeekPolicy } from '../../domain/academic-week'
import { academicDate } from '../../domain/academic-week'
import { StaleSessionGenerationError } from '../../shared/session-owner'
export interface ManagementOwner {
  readonly userId: string
  readonly sessionId: string
  readonly groupId: number
}
export type ManagementJournal = Pick<
  HeadmanJournalApi,
  | 'listLessons'
  | 'cancelLesson'
  | 'restoreLesson'
  | 'transferLesson'
  | 'getLessonTransfer'
  | 'changeLessonRoom'
>
export type ManagementSchedule = Pick<
  HeadmanScheduleApi,
  | 'listSemesters'
  | 'listAssignments'
  | 'academicWeekPolicy'
  | 'lessonSlots'
  | 'createOneOffLesson'
  | 'listOneOffLessons'
>
const ONE_OFF_CREATED = 'Разовая пара создана'
type ConfirmedOneOff = Readonly<
  Pick<HeadmanOneOffLesson, 'id' | 'date' | 'lessonNumber' | 'physicalLessonId'>
>
export type LessonCommand =
  | {
      readonly kind: 'ROOM'
      readonly target: HeadmanJournalLesson
      readonly key: string
      readonly input: HeadmanLessonRoomInput
    }
  | {
      readonly kind: 'TRANSFER'
      readonly target: HeadmanJournalLesson
      readonly key: string
      readonly input: HeadmanLessonTransferRequest
      readonly receipt: HeadmanLessonTransferResponse | null
    }
  | { readonly kind: 'CANCEL'; readonly target: HeadmanJournalLesson; readonly reason: string }
  | { readonly kind: 'RESTORE'; readonly target: HeadmanJournalLesson }
  | { readonly kind: 'ONE_OFF'; readonly key: string; readonly input: HeadmanOneOffCreateInput }
export interface ManagementIntent {
  readonly owner: ManagementOwner
  readonly command: LessonCommand
  readonly sent: boolean
}
export const managementScope = (owner: ManagementOwner): string =>
  `rct:lesson-management:${owner.userId}:${owner.sessionId}:HEADMAN:${owner.groupId}`
const uuid = (v: unknown): boolean =>
  typeof v === 'string' && /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i.test(v)
const decimal = (v: unknown): boolean => typeof v === 'string' && /^[1-9]\d*$/.test(v)
export function readManagementIntent(
  storage: Pick<Storage, 'getItem'>,
  owner: ManagementOwner,
): ManagementIntent | null {
  const raw = storage.getItem(managementScope(owner))
  if (!raw) return null
  const value = JSON.parse(raw) as ManagementIntent,
    cmd = value?.command
  if (
    !value?.owner ||
    managementScope(value.owner) !== managementScope(owner) ||
    typeof value.sent !== 'boolean' ||
    !cmd ||
    !['ROOM', 'TRANSFER', 'CANCEL', 'RESTORE', 'ONE_OFF'].includes(cmd.kind)
  )
    throw new Error('Сохранённое действие пары повреждено. Восстанови доступ к исходному запросу.')
  if (
    cmd.kind !== 'ONE_OFF' &&
    (!cmd.target ||
      cmd.target.groupId !== owner.groupId ||
      !Number.isSafeInteger(cmd.target.id) ||
      cmd.target.id <= 0 ||
      !decimal(cmd.target.occurrenceRevision) ||
      !decimal(cmd.target.occurrenceId) ||
      !cmd.target.current)
  )
    throw new Error('Не удалось восстановить физическую пару исходного действия.')
  if ((cmd.kind === 'ROOM' || cmd.kind === 'TRANSFER' || cmd.kind === 'ONE_OFF') && !uuid(cmd.key))
    throw new Error('Не удалось восстановить ключ исходного действия.')
  if (
    cmd.kind === 'ROOM' &&
    (!cmd.input ||
      cmd.input.expectedRevision !== cmd.target.occurrenceRevision ||
      (cmd.input.room !== null &&
        (typeof cmd.input.room !== 'string' || cmd.input.room.length > 64)))
  )
    throw new Error('Запрос аудитории повреждён.')
  if (
    cmd.kind === 'TRANSFER' &&
    (!cmd.input ||
      cmd.input.requestKey !== cmd.key ||
      cmd.input.expectedRevision !== cmd.target.occurrenceRevision ||
      academicDate(cmd.input.targetDate) === null ||
      !Number.isInteger(cmd.input.targetLessonNumber) ||
      cmd.input.targetLessonNumber < 1 ||
      cmd.input.targetLessonNumber > 8 ||
      (cmd.receipt !== null &&
        (!uuid(cmd.receipt.operationId) ||
          cmd.receipt.sourceLessonId !== String(cmd.target.id) ||
          cmd.receipt.occurrenceId !== cmd.target.occurrenceId)))
  )
    throw new Error('Запрос переноса повреждён.')
  if (
    cmd.kind === 'CANCEL' &&
    (typeof cmd.reason !== 'string' || !cmd.reason.trim() || cmd.reason.length > 300)
  )
    throw new Error('Причина отмены повреждена.')
  if (
    cmd.kind === 'ONE_OFF' &&
    (!cmd.input ||
      cmd.input.groupId !== owner.groupId ||
      academicDate(cmd.input.date) === null ||
      [cmd.input.assignmentId, cmd.input.subjectId, cmd.input.lessonNumber].some(
        (id) => !Number.isSafeInteger(id) || id <= 0,
      ) ||
      cmd.input.lessonNumber > 8 ||
      typeof cmd.input.startTime !== 'string' ||
      typeof cmd.input.endTime !== 'string')
  )
    throw new Error('Запрос разовой пары повреждён.')
  return Object.freeze({
    ...value,
    owner: Object.freeze({ ...value.owner }),
    command: Object.freeze(
      cmd.kind === 'ONE_OFF' ? { ...cmd, input: Object.freeze({ ...cmd.input }) } : { ...cmd },
    ),
  })
}
export function lessonCanTransfer(lesson: HeadmanJournalLesson, today: string): boolean {
  return (
    lesson.current &&
    lesson.status === 'PLANNED' &&
    lesson.date > today &&
    decimal(lesson.occurrenceId) &&
    decimal(lesson.occurrenceRevision) &&
    lesson.transferState !== 'PENDING'
  )
}
export function lessonCanRoom(lesson: HeadmanJournalLesson, now: number): boolean {
  return (
    lesson.current &&
    lesson.status === 'PLANNED' &&
    decimal(lesson.occurrenceId) &&
    decimal(lesson.occurrenceRevision) &&
    lesson.transferState !== 'PENDING' &&
    lesson.startTime !== null &&
    Date.parse(`${lesson.date}T${lesson.startTime}+03:00`) > now
  )
}
/** A transfer's null room preserves the server's source snapshot. */
export function transferEffectiveRoom(input: string, source: string | null): string | null {
  return input.trim() || source
}
function confirmsUnkeyed(
  cmd: Extract<LessonCommand, { kind: 'CANCEL' | 'RESTORE' }>,
  lesson: HeadmanJournalLesson,
): boolean {
  if (
    !lesson.current ||
    lesson.groupId !== cmd.target.groupId ||
    lesson.occurrenceId !== cmd.target.occurrenceId ||
    lesson.date !== cmd.target.date ||
    lesson.lessonNumber !== cmd.target.lessonNumber ||
    lesson.subjectId !== cmd.target.subjectId ||
    !Number.isSafeInteger(lesson.id) ||
    lesson.id <= 0 ||
    !decimal(lesson.occurrenceRevision) ||
    !decimal(cmd.target.occurrenceRevision) ||
    BigInt(lesson.occurrenceRevision!) <= BigInt(cmd.target.occurrenceRevision!)
  )
    return false
  return cmd.kind === 'CANCEL'
    ? lesson.id === cmd.target.id && lesson.status === 'CANCELLED'
    : ['PLANNED', 'ACTIVE', 'CLOSED'].includes(lesson.status)
}
export function datePlus(date: string, days: number): string {
  return new Date(Date.parse(date + 'T00:00:00Z') + days * 86_400_000).toISOString().slice(0, 10)
}
function status(cause: unknown): number | null {
  return cause instanceof HeadmanJournalApiError
    ? cause.response.status
    : cause instanceof HeadmanScheduleApiError
      ? cause.response.status
      : null
}
export function isManagementAccessDenied(cause: unknown): boolean {
  const responseStatus = status(cause)
  return responseStatus === 401 || responseStatus === 403
}
/** Owner-bound durable command coordinator. Presentation never writes through the API directly. */
export class LessonManagementState {
  lessons: readonly HeadmanJournalLesson[] = []
  loadedDate: string | null = null
  oneOffPhysicalIds: ReadonlySet<number> = new Set()
  oneOffReadError: string | null = null
  confirmedOneOff: ConfirmedOneOff | null = null
  assignments: readonly HeadmanScheduleAssignment[] = []
  semester: HeadmanScheduleSemester | null = null
  catalog: HeadmanLessonSlotCatalog | null = null
  policy: AcademicWeekPolicy | null = null
  intent: ManagementIntent | null = null
  busy = false
  loading = false
  denied = false
  storageBlocked = false
  error: string | null = null
  notice: string | null = null
  private pendingOneOffs: ReadonlyMap<number, ConfirmedOneOff> = new Map()
  private alive = true
  private reads = 0
  private poll: ReturnType<typeof setTimeout> | null = null
  constructor(
    readonly journal: ManagementJournal,
    readonly schedule: ManagementSchedule,
    readonly owner: ManagementOwner,
    readonly storage: Pick<Storage, 'getItem' | 'setItem' | 'removeItem'>,
    readonly changed: () => void,
    readonly onError: (cause: unknown) => void,
    readonly canWrite: (command: LessonCommand) => boolean = () => true,
  ) {
    try {
      this.intent = readManagementIntent(storage, owner)
    } catch (cause) {
      this.storageBlocked = true
      this.error = String(cause)
    }
  }
  dispose(): void {
    this.alive = false
    this.reads++
    this.pendingOneOffs = new Map()
    if (this.poll) clearTimeout(this.poll)
  }
  get locked(): boolean {
    return this.busy || this.intent !== null || this.storageBlocked
  }
  get oneOffNotice(): boolean {
    return this.notice === ONE_OFF_CREATED
  }
  dismissOneOffNotice(): void {
    if (this.oneOffNotice) this.notice = null
  }
  hasPendingOneOff(date: string, lessonNumber?: number): boolean {
    for (const pending of this.pendingOneOffs.values()) {
      if (
        pending.date === date &&
        (lessonNumber === undefined || pending.lessonNumber === lessonNumber)
      )
        return true
    }
    return false
  }
  private publish(): void {
    if (this.alive) this.changed()
  }
  private fail(cause: unknown, localReadback = false): void {
    if (!this.alive || cause instanceof StaleSessionGenerationError) return
    const accessLost = isManagementAccessDenied(cause)
    if (accessLost) {
      this.denied = true
      this.lessons = []
      this.loadedDate = null
      this.oneOffPhysicalIds = new Set()
      this.oneOffReadError = null
      this.confirmedOneOff = null
      this.pendingOneOffs = new Map()
      this.dismissOneOffNotice()
      this.assignments = []
      this.catalog = null
      this.policy = null
    }
    this.error = cause instanceof Error ? cause.message : 'Не удалось проверить действие пары.'
    if (!localReadback || accessLost) this.onError(cause)
    this.publish()
  }
  async load(date: string): Promise<void> {
    const read = ++this.reads
    const localReadback = this.hasPendingOneOff(date)
    this.loading = true
    this.error = null
    this.oneOffReadError = null
    this.publish()
    try {
      const [lessons, catalog, policy, semesters, oneOffs] = await Promise.all([
        this.journal.listLessons(this.owner.groupId, date, date),
        this.schedule.lessonSlots(),
        this.schedule.academicWeekPolicy(date),
        this.schedule.listSemesters(),
        this.schedule.listOneOffLessons(this.owner.groupId, date, date).then(
          (items) => ({ items, ok: true as const }),
          (cause: unknown) => ({ cause, ok: false as const }),
        ),
      ])
      if (!this.alive || read !== this.reads) return
      if (
        !oneOffs.ok &&
        (isManagementAccessDenied(oneOffs.cause) ||
          oneOffs.cause instanceof StaleSessionGenerationError)
      )
        throw oneOffs.cause
      const semester = semesters.find((s) => s.active) ?? null
      const assignments = semester?.id
        ? await this.schedule.listAssignments(this.owner.groupId, semester.id)
        : []
      if (!this.alive || read !== this.reads) return
      this.lessons = lessons.filter(
        (l) => l.groupId === this.owner.groupId && l.date === date && l.current,
      )
      this.loadedDate = date
      const projections = oneOffs.ok
        ? oneOffs.items.filter(
            (lesson) => lesson.groupId === this.owner.groupId && lesson.date === date,
          )
        : []
      const visibleIds = new Set(this.lessons.map((lesson) => lesson.id))
      const currentPhysicalIds = new Map(
        projections.map((lesson) => [lesson.id, lesson.physicalLessonId]),
      )
      const ids = new Set(currentPhysicalIds.values())
      const pendingAfterRead = new Map(this.pendingOneOffs)
      for (const [physicalId, pending] of this.pendingOneOffs) {
        const currentPhysicalId = currentPhysicalIds.get(pending.id)
        if (currentPhysicalId !== undefined && currentPhysicalId !== physicalId) {
          pendingAfterRead.delete(physicalId)
          continue
        }
        if (pending.date !== date) continue
        ids.add(physicalId)
        if (visibleIds.has(physicalId)) pendingAfterRead.delete(physicalId)
      }
      for (const lesson of projections) {
        if (!visibleIds.has(lesson.physicalLessonId))
          pendingAfterRead.set(lesson.physicalLessonId, lesson)
      }
      this.pendingOneOffs = pendingAfterRead
      this.oneOffPhysicalIds = ids
      if (!oneOffs.ok)
        this.oneOffReadError = 'Список разовых пар пока недоступен.'
      else if (this.hasPendingOneOff(date))
        this.oneOffReadError = 'Расписание ещё не отобразило все созданные пары.'
      this.catalog = catalog
      this.policy = policy
      this.semester = semester
      this.assignments = assignments
      this.denied = false
    } catch (cause) {
      if (read === this.reads) this.fail(cause, localReadback)
    } finally {
      if (this.alive && read === this.reads) {
        this.loading = false
        this.publish()
      }
    }
  }
  async targetLessons(date: string): Promise<readonly HeadmanJournalLesson[]> {
    return this.journal.listLessons(this.owner.groupId, date, date)
  }
  async targetOccupiedSlots(
    date: string,
    operation: 'transfer' | 'oneoff',
  ): Promise<ReadonlySet<number>> {
    const [lessons, oneOffs] = await Promise.all([
      this.targetLessons(date),
      this.schedule.listOneOffLessons(this.owner.groupId, date, date),
    ])
    const occupied = new Set<number>()
    const physicalIds = new Set<number>()
    for (const lesson of lessons) {
      if (lesson.groupId !== this.owner.groupId || lesson.date !== date || !lesson.current) continue
      physicalIds.add(lesson.id)
      if (
        lesson.lessonNumber !== null &&
        lesson.status !== 'TRANSFERRED' &&
        (operation === 'oneoff' || lesson.status !== 'CANCELLED')
      )
        occupied.add(lesson.lessonNumber)
    }
    for (const lesson of oneOffs) {
      if (
        lesson.groupId === this.owner.groupId &&
        lesson.date === date &&
        !physicalIds.has(lesson.physicalLessonId)
      )
        occupied.add(lesson.lessonNumber)
    }
    return occupied
  }
  private persist(intent: ManagementIntent): void {
    this.storage.setItem(managementScope(this.owner), JSON.stringify(intent))
    this.intent = readManagementIntent(this.storage, this.owner)
    this.publish()
  }
  private finish(message: string): void {
    this.storage.removeItem(managementScope(this.owner))
    this.intent = null
    this.notice = message
    this.error = null
    this.publish()
  }
  async submit(command: LessonCommand): Promise<void> {
    if (!this.alive || this.locked || this.denied) return
    this.dismissOneOffNotice()
    try {
      this.persist({ owner: this.owner, command, sent: false })
    } catch (cause) {
      this.storageBlocked = true
      this.fail(cause)
      return
    }
    await this.run(false)
  }
  /** Retry only keyed intents; unkeyed commands whose reply was lost are read-reconciled. */
  async recover(): Promise<void> {
    if (!this.intent || this.busy || this.denied || !this.alive) return
    const cmd = this.intent.command
    if ((cmd.kind === 'CANCEL' || cmd.kind === 'RESTORE') && this.intent.sent) {
      await this.reconcile()
      return
    }
    if (
      cmd.kind === 'TRANSFER' &&
      (cmd.receipt?.state === 'PENDING' ||
        (cmd.receipt?.state === 'ERROR' && !cmd.receipt.retryable))
    ) {
      await this.checkTransfer()
      return
    }
    if (cmd.kind !== 'ONE_OFF') {
      try {
        await this.journal.listLessons(this.owner.groupId, cmd.target.date, cmd.target.date)
      } catch (cause) {
        this.fail(cause)
        return
      }
      if (!this.alive) return
    }
    await this.run(true)
  }
  private async run(retry: boolean): Promise<void> {
    const frozen = this.intent
    if (!frozen || this.busy || !this.alive || !this.canWrite(frozen.command)) return
    this.busy = true
    this.error = null
    this.publish()
    let mutationAcknowledged = false
    try {
      if (!this.canWrite(frozen.command) || !this.alive) return
      this.persist({ ...frozen, sent: true })
      const cmd = frozen.command
      if (cmd.kind === 'ROOM') {
        const receipt = await this.journal.changeLessonRoom(cmd.target.id, cmd.input, cmd.key)
        if (!this.alive) return
        if (
          receipt.lessonId !== String(cmd.target.id) ||
          receipt.occurrenceId !== cmd.target.occurrenceId ||
          receipt.room !== cmd.input.room
        )
          throw new Error('Квитанция относится к другой физической паре.')
        this.finish('Аудитория пары изменена.')
      } else if (cmd.kind === 'TRANSFER') {
        const receipt = await this.journal.transferLesson(cmd.target.id, cmd.input)
        if (!this.alive) return
        this.acceptTransfer(cmd, receipt)
      } else if (cmd.kind === 'ONE_OFF') {
        const receipt = await this.schedule.createOneOffLesson(cmd.input, cmd.key)
        if (!this.alive) return
        this.finish(ONE_OFF_CREATED)
        const confirmed = Object.freeze({
          id: receipt.id,
          date: receipt.date,
          lessonNumber: receipt.lessonNumber,
          physicalLessonId: receipt.physicalLessonId,
        })
        this.confirmedOneOff = confirmed
        this.pendingOneOffs = new Map(this.pendingOneOffs).set(confirmed.physicalLessonId, confirmed)
      } else {
        if (cmd.kind === 'CANCEL') await this.journal.cancelLesson(cmd.target.id, cmd.reason)
        else await this.journal.restoreLesson(cmd.target.id)
        mutationAcknowledged = true
        if (!this.alive) return
        // Mutation ACK lacks occurrenceRevision; authoritative list GET supplies it.
        await this.confirmUnkeyed(cmd)
      }
    } catch (cause) {
      if (!this.alive || cause instanceof StaleSessionGenerationError) return
      // Only the first definitive refusal authorizes editing. Never drop a replayed uncertain intent.
      const refusal =
        frozen.command.kind === 'ONE_OFF'
          ? (cause instanceof HeadmanScheduleApiError && cause.response.status === 403) ||
            canCorrectRejectedOneOff(cause, true)
          : [400, 403, 404, 409, 422].includes(status(cause) ?? 0)
      if (!mutationAcknowledged && !retry && !frozen.sent && refusal) {
        try {
          this.storage.removeItem(managementScope(this.owner))
          this.intent = null
        } catch {
          this.storageBlocked = true
        }
      }
      this.fail(cause)
    } finally {
      if (this.alive) {
        this.busy = false
        this.publish()
      }
    }
  }
  async reconcile(): Promise<void> {
    const frozen = this.intent,
      cmd = frozen?.command
    if (
      !cmd ||
      (cmd.kind !== 'CANCEL' && cmd.kind !== 'RESTORE') ||
      this.busy ||
      !this.alive ||
      this.denied
    )
      return
    this.busy = true
    this.publish()
    try {
      await this.confirmUnkeyed(cmd)
    } catch (cause) {
      this.fail(cause)
    } finally {
      if (this.alive) {
        this.busy = false
        this.publish()
      }
    }
  }
  private async confirmUnkeyed(
    cmd: Extract<LessonCommand, { kind: 'CANCEL' | 'RESTORE' }>,
  ): Promise<void> {
    const lessons = await this.journal.listLessons(
      this.owner.groupId,
      cmd.target.date,
      cmd.target.date,
    )
    if (!this.alive) return
    if (lessons.some((lesson) => confirmsUnkeyed(cmd, lesson)))
      this.finish(cmd.kind === 'CANCEL' ? 'Пара отменена.' : 'Пара восстановлена.')
    else
      this.error =
        'Результат исходной команды ещё не подтверждён. Повторная запись заблокирована; проверь состояние позже.'
  }
  private acceptTransfer(
    cmd: Extract<LessonCommand, { kind: 'TRANSFER' }>,
    receipt: HeadmanLessonTransferResponse,
  ): void {
    if (
      receipt.sourceLessonId !== String(cmd.target.id) ||
      receipt.occurrenceId !== cmd.target.occurrenceId ||
      receipt.targetDate !== cmd.input.targetDate
    )
      throw new Error('Квитанция переноса относится к другому исходному действию.')
    if (receipt.state === 'COMPLETED') this.finish('Пара перенесена. История сохранена.')
    else {
      this.persist({ owner: this.owner, sent: true, command: { ...cmd, receipt } })
      this.error =
        receipt.state === 'ERROR'
          ? 'Перенос завершился ошибкой. Повтор доступен только для исходного запроса.'
          : 'Перенос принят. Ожидаем подтверждения участников.'
      if (receipt.state === 'PENDING')
        this.poll = setTimeout(() => {
          void this.checkTransfer()
        }, 1500)
    }
  }
  async checkTransfer(): Promise<void> {
    const cmd = this.intent?.command
    if (!cmd || cmd.kind !== 'TRANSFER' || !cmd.receipt || this.busy || this.denied || !this.alive)
      return
    this.busy = true
    this.publish()
    try {
      const receipt = await this.journal.getLessonTransfer(cmd.receipt.operationId)
      if (!this.alive) return
      this.acceptTransfer(cmd, receipt)
    } catch (cause) {
      this.fail(cause)
    } finally {
      if (this.alive) {
        this.busy = false
        this.publish()
      }
    }
  }
}
