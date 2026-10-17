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
  HeadmanScheduleApi,
  HeadmanScheduleAssignment,
  HeadmanScheduleSemester,
  HeadmanLessonSlotCatalog,
} from '../schedule/headman-schedule-client'
import { HeadmanScheduleApiError } from '../schedule/headman-schedule-client'
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
  'listSemesters' | 'listAssignments' | 'academicWeekPolicy' | 'lessonSlots' | 'createOneOffLesson'
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
    command: Object.freeze({ ...cmd }),
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
/** Owner-bound durable command coordinator. Presentation never writes through the API directly. */
export class LessonManagementState {
  lessons: readonly HeadmanJournalLesson[] = []
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
    if (this.poll) clearTimeout(this.poll)
  }
  get locked(): boolean {
    return this.busy || this.intent !== null || this.storageBlocked
  }
  private publish(): void {
    if (this.alive) this.changed()
  }
  private fail(cause: unknown): void {
    if (!this.alive || cause instanceof StaleSessionGenerationError) return
    if (status(cause) === 401 || status(cause) === 403) {
      this.denied = true
      this.lessons = []
      this.assignments = []
      this.catalog = null
      this.policy = null
    }
    this.error = cause instanceof Error ? cause.message : 'Не удалось проверить действие пары.'
    this.onError(cause)
    this.publish()
  }
  async load(date: string): Promise<void> {
    const read = ++this.reads
    this.loading = true
    this.error = null
    this.publish()
    try {
      const [lessons, catalog, policy, semesters] = await Promise.all([
        this.journal.listLessons(this.owner.groupId, date, date),
        this.schedule.lessonSlots(),
        this.schedule.academicWeekPolicy(date),
        this.schedule.listSemesters(),
      ])
      if (!this.alive || read !== this.reads) return
      const semester = semesters.find((s) => s.active) ?? null
      const assignments = semester?.id
        ? await this.schedule.listAssignments(this.owner.groupId, semester.id)
        : []
      if (!this.alive || read !== this.reads) return
      this.lessons = lessons.filter(
        (l) => l.groupId === this.owner.groupId && l.date === date && l.current,
      )
      this.catalog = catalog
      this.policy = policy
      this.semester = semester
      this.assignments = assignments
      this.denied = false
    } catch (cause) {
      if (read === this.reads) this.fail(cause)
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
        if (
          receipt.date !== cmd.input.date ||
          receipt.lessonNumber !== cmd.input.lessonNumber ||
          receipt.groupId !== this.owner.groupId ||
          receipt.subjectId !== cmd.input.subjectId
        )
          throw new Error('Сервер не подтвердил исходную разовую пару.')
        this.finish('Разовая пара создана.')
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
      if (
        !mutationAcknowledged &&
        !retry &&
        !frozen.sent &&
        [400, 403, 404, 409, 422].includes(status(cause) ?? 0)
      ) {
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
