import { computed, nextTick, onBeforeUnmount, ref, shallowRef, watch, type ShallowRef } from 'vue'
import { StaleSessionGenerationError } from '../../shared/session-owner'
import { journalScopeCurrent, type JournalMutationScope } from './headman-journal-operation'
import type { HeadmanAssistantPermission } from '../headman-group/headman-group-client'
import type { HeadmanRequestsApi } from '../headman-requests/headman-requests-client'
import {
  HeadmanJournalApiError,
  type HeadmanJournalApi,
  type HeadmanJournalApprovedTicket,
  type HeadmanJournalAttendanceStatus,
  type HeadmanJournalExcuseType,
  type HeadmanJournalLesson,
  type HeadmanJournalMarkCommand,
  type HeadmanJournalMarkCondition,
  type HeadmanJournalReport,
  type HeadmanJournalRosterEntry,
} from './headman-journal-client'

export const manualChoices = ['PRESENT', 'EXCUSED', 'ABSENT'] as const
export type ManualChoice = (typeof manualChoices)[number]
export type ExcuseCommand = Extract<HeadmanJournalMarkCommand, { status: 'EXCUSED' }>

export const excuseTypes = [
  { value: 'ILLNESS', label: 'Болезнь' },
  { value: 'MEDICAL_EXAMINATION', label: 'Медицинское обследование' },
  { value: 'COMPETITION_PARTICIPATION', label: 'Участие в соревновании' },
  { value: 'FAMILY_CIRCUMSTANCES', label: 'Семейные обстоятельства' },
  { value: 'SUMMONS', label: 'Повестка' },
  { value: 'UNIVERSITY_ORDER', label: 'Распоряжение университета' },
  { value: 'EXEMPTION', label: 'Освобождение' },
  { value: 'OTHER', label: 'Другая причина' },
] as const satisfies readonly { value: HeadmanJournalExcuseType; label: string }[]

export function excuseLabel(value: string | null): string | null {
  return excuseTypes.find((item) => item.value === value)?.label ?? value
}

export const manualStatusLabel: Record<HeadmanJournalAttendanceStatus, string> = {
  PRESENT: 'Был',
  ABSENT: 'Не был',
  EXCUSED: 'Уважительная причина',
  EMPTY: 'Нет отметки',
  CANCELLED: 'Пара отменена',
  UNSUPPORTED: 'Неподдерживаемый статус',
}
export const manualStatusSymbol: Record<HeadmanJournalAttendanceStatus, string> = {
  PRESENT: '+',
  ABSENT: 'н',
  EXCUSED: 'у',
  EMPTY: '·',
  CANCELLED: '—',
  UNSUPPORTED: '?',
}

export function rosterName(name: string): string {
  const [surname, ...names] = name.trim().split(/\s+/u)
  return [surname, ...names.slice(0, 2).map((part) => `${Array.from(part)[0]}.`)].join(' ')
}

export function reportMatchesLesson(
  report: HeadmanJournalReport,
  lesson: HeadmanJournalLesson,
  groupId: number | null,
  date: string,
): boolean {
  return (
    groupId !== null &&
    lesson.groupId === groupId &&
    lesson.date === date &&
    report.lessonId === lesson.id &&
    report.groupId === groupId &&
    report.lessonDate === date &&
    new Set(report.entries.map((entry) => entry.userId)).size === report.entries.length
  )
}

export function isAttendanceDenied(cause: unknown): boolean {
  return (
    cause instanceof StaleSessionGenerationError ||
    (cause instanceof HeadmanJournalApiError && [401, 403].includes(cause.response.status))
  )
}

export function markCondition(
  entry: HeadmanJournalRosterEntry,
): HeadmanJournalMarkCondition | null {
  if (entry.status === 'EMPTY') return { expectedStatus: 'EMPTY', expectedRevision: 'EMPTY' }
  if (
    (entry.status === 'PRESENT' || entry.status === 'ABSENT' || entry.status === 'EXCUSED') &&
    entry.markRevision
  ) {
    return { expectedStatus: entry.status, expectedRevision: entry.markRevision }
  }
  return null
}

const conflictReviewMessage = 'Отметка изменилась. Проверь актуальную отметку.'

export type ManualRowIssue = {
  readonly message: string
  readonly kind: 'rejected' | 'conflict' | 'accepted'
}

type ManualScope = {
  api: HeadmanJournalApi | null
  requestsApi: HeadmanRequestsApi | null
  groupId: number | null
  date: string
  lesson: HeadmanJournalLesson | null
  actorUserId: string | null
  offline: boolean
  readOnly: boolean
  permissions: readonly HeadmanAssistantPermission[] | null
}
type FocusTarget = { userId: number; choice: ManualChoice; source: EventTarget | null }
type ReplacementChoice = Exclude<ManualChoice, 'EXCUSED'>
export type ManualMarkDraft = FocusTarget & {
  condition: HeadmanJournalMarkCondition
  ticket: HeadmanJournalApprovedTicket | null
} & (
  | { kind: 'reason' | 'linked' | 'unknown'; removing: boolean }
  | { kind: 'replace'; replacement: ReplacementChoice }
)

function currentExcuseTicket(entry: HeadmanJournalRosterEntry): HeadmanJournalApprovedTicket | null {
  return entry.status === 'EXCUSED' && entry.currentExcuseRequestId
    ? { kind: 'EXCUSE', id: entry.currentExcuseRequestId, status: 'APPROVED' }
    : null
}

function canEditReason(entry: HeadmanJournalRosterEntry): boolean {
  return entry.status !== 'EXCUSED' ||
    (entry.source?.toLowerCase() === 'headman' && !currentExcuseTicket(entry))
}

function sameDraftMark(draft: ManualMarkDraft, entry: HeadmanJournalRosterEntry): boolean {
  const condition = markCondition(entry)
  return !!condition &&
    draft.condition.expectedStatus === condition.expectedStatus &&
    draft.condition.expectedRevision === condition.expectedRevision &&
    (draft.ticket?.id ?? null) === (currentExcuseTicket(entry)?.id ?? null)
}

type ManualOptions = {
  scope: () => ManualScope
  report: ShallowRef<HeadmanJournalReport | null>
  loading: () => boolean
  refresh: () => Promise<boolean>
  deny: () => void
  error: (cause: unknown) => void
  focus: (userId: number, choice: ManualChoice) => void
}

export function useHeadmanManualMarks(options: ManualOptions) {
  const pendingUserId = ref<number | null>(null)
  const refreshUserId = ref<number | null>(null)
  const draft = shallowRef<ManualMarkDraft | null>(null)
  const issues = shallowRef<Readonly<Record<number, ManualRowIssue>>>({})
  const staleRows = shallowRef<ReadonlySet<number>>(new Set())
  let generation = 0

  function reset(): void {
    generation += 1
    pendingUserId.value = null
    refreshUserId.value = null
    draft.value = null
    issues.value = {}
    staleRows.value = new Set()
  }

  watch(
    () => {
      const scope = options.scope()
      return [
        scope.api,
        scope.requestsApi,
        scope.groupId,
        scope.date,
        scope.lesson?.id,
        scope.actorUserId,
        scope.offline,
        scope.readOnly,
        scope.permissions?.join('\u0000') ?? null,
      ] as const
    },
    (next, previous) => {
      // A geo ACK can replace the lesson object without changing this write owner.
      if (next.some((value, index) => value !== previous[index])) reset()
    },
    { flush: 'sync' },
  )
  onBeforeUnmount(reset)

  const blocked = computed(
    () => pendingUserId.value !== null || refreshUserId.value !== null || options.loading(),
  )
  const draftEntry = computed(
    () => options.report.value?.entries.find((row) => row.userId === draft.value?.userId) ?? null,
  )

  function restoreFocus(target: FocusTarget, owner: number, fromForm = false): void {
    void nextTick(() => {
      if (generation !== owner) return
      // Keep focus where the user moved it while a request was in flight.
      const active = typeof document !== 'undefined' ? document.activeElement : null
      if (active && active !== document.body && active !== target.source) return
      if (!fromForm && active === target.source) return
      options.focus(target.userId, target.choice)
    })
  }

  function closeDraft(): boolean {
    if (!draft.value) return false
    if (pendingUserId.value === draft.value.userId) return true
    if (draft.value.kind !== 'replace' && draft.value.removing) {
      draft.value = { ...draft.value, removing: false }
      return true
    }
    const target = draft.value
    draft.value = null
    restoreFocus(target, generation, true)
    return true
  }

  const reportOwned = computed(() => {
    const scope = options.scope()
    const report = options.report.value
    return Boolean(
      scope.api?.writable &&
        scope.lesson &&
        report &&
        reportMatchesLesson(report, scope.lesson, scope.groupId, scope.date) &&
        !scope.offline &&
        !scope.readOnly &&
        scope.lesson.status !== 'TRANSFERRED' &&
        scope.lesson.status !== 'CANCELLED' &&
        scope.lesson.status !== 'UNSUPPORTED' &&
        !(scope.lesson.status === 'PLANNED' && !report.editable),
    )
  })
  const rowsById = computed(
    () => new Map(options.report.value?.entries.map((entry) => [entry.userId, entry])),
  )

  function canOpenLinked(entry: HeadmanJournalRosterEntry): boolean {
    const { groupId, date, lesson, permissions, offline } = options.scope()
    const report = options.report.value
    return !!lesson && !!report && reportMatchesLesson(report, lesson, groupId, date) &&
      rowsById.value.get(entry.userId) === entry && !offline &&
      (permissions === null || permissions.includes('MANAGE_EXCUSES')) &&
      currentExcuseTicket(entry) !== null && markCondition(entry) !== null
  }

  function canWrite(entry: HeadmanJournalRosterEntry, choice: ManualChoice): boolean {
    const permissions = options.scope().permissions
    const allowed = (permission: HeadmanAssistantPermission) =>
      permissions === null || permissions.includes(permission)
    return (
      reportOwned.value &&
      rowsById.value.get(entry.userId) === entry &&
      entry.editable &&
      markCondition(entry) !== null &&
      allowed(entry.status === 'EXCUSED' ? 'MANAGE_EXCUSES' : 'MARK_ATTENDANCE') &&
      (choice !== 'EXCUSED' || allowed('MANAGE_EXCUSES')) &&
      !staleRows.value.has(entry.userId)
    )
  }

  function canChoose(entry: HeadmanJournalRosterEntry, choice: ManualChoice): boolean {
    return canWrite(entry, choice) || (choice === 'EXCUSED' && canOpenLinked(entry))
  }

  function setIssue(userId: number, issue: ManualRowIssue | null): void {
    const next = { ...issues.value }
    if (issue) next[userId] = issue
    else delete next[userId]
    issues.value = next
  }

  function reportRefreshed(): void {
    const target = draft.value
    const entry = draftEntry.value
    if (target && (!entry || !sameDraftMark(target, entry))) {
      if (entry && target.kind === 'reason' && !target.removing && canEditReason(entry)) {
        setIssue(target.userId, { kind: 'conflict', message: conflictReviewMessage })
      } else {
        draft.value = null
        if (entry) {
          setIssue(target.userId, { kind: 'conflict', message: conflictReviewMessage })
          restoreFocus(target, generation, true)
        }
      }
    }
    staleRows.value = new Set()
    issues.value = Object.fromEntries(
      Object.entries(issues.value).filter(([, issue]) => issue.kind !== 'accepted'),
    )
  }

  function deny(): void {
    reset()
    options.deny()
  }

  async function refreshRow(userId: number, source: EventTarget | null): Promise<void> {
    if (blocked.value) return
    const owner = generation
    refreshUserId.value = userId
    try {
      const refreshed = await options.refresh()
      if (generation !== owner) return
      const issue = issues.value[userId]
      if (refreshed && issue?.kind === 'conflict')
        setIssue(userId, { kind: 'conflict', message: conflictReviewMessage })
      else if (!refreshed && !issue)
        setIssue(userId, { kind: 'rejected', message: 'Не удалось обновить состав группы.' })
    } finally {
      if (generation === owner) {
        refreshUserId.value = null
        restoreFocus({ userId, choice: 'PRESENT', source }, owner, true)
      }
    }
  }

  function project(
    entry: HeadmanJournalRosterEntry,
    command: HeadmanJournalMarkCommand | null,
    timestamp: string | null,
  ): void {
    const report = options.report.value
    if (!report) return
    const status = command?.status ?? 'EMPTY'
    const excuse = command?.status === 'EXCUSED' ? command : null
    const retainAttachment = Boolean(excuse && !excuse.file && entry.status === 'EXCUSED')
    const confirmed: HeadmanJournalRosterEntry = {
      ...entry,
      status,
      markRevision: null,
      currentExcuseRequestId: null,
      symbol: manualStatusSymbol[status],
      timestamp,
      source: command ? 'headman' : null,
      excuseType: excuse?.excuseType ?? null,
      excuseReason: excuseLabel(excuse?.excuseType ?? null),
      comment: excuse?.comment?.trim() || null,
      attachmentId: retainAttachment ? entry.attachmentId : null,
      attachmentName: retainAttachment ? entry.attachmentName : null,
      attachmentContentType: retainAttachment ? entry.attachmentContentType : null,
      attachmentSize: retainAttachment ? entry.attachmentSize : null,
    }
    options.report.value = {
      ...report,
      entries: report.entries.map((row) => (row.userId === entry.userId ? confirmed : row)),
    }
  }

  function mutationScope(): JournalMutationScope | null {
    const { api, lesson, groupId } = options.scope()
    return api && lesson ? { api, lessonId: lesson.id, groupId, generation } : null
  }

  async function write(
    entry: HeadmanJournalRosterEntry,
    target: FocusTarget,
    command: HeadmanJournalMarkCommand | null,
    fromForm: boolean,
  ): Promise<void> {
    const { api, lesson } = options.scope()
    const condition = markCondition(entry)
    if (!api || !lesson || !condition || blocked.value || !canWrite(entry, target.choice)) return
    const owner = mutationScope()
    if (!owner) return
    const current = () => journalScopeCurrent(owner, mutationScope())
    const userId = entry.userId
    pendingUserId.value = userId
    setIssue(userId, null)
    try {
      let timestamp: string | null = null
      try {
        if (command) {
          const ack = await api.mark(lesson.id, userId, command, condition)
          if (ack.lessonId !== lesson.id || ack.userId !== userId || ack.status !== command.status)
            throw new Error('Сервер не подтвердил выбранную отметку.')
          timestamp = ack.timestamp
        } else {
          if (condition.expectedStatus === 'EMPTY') return
          await api.clear(lesson.id, userId, {
            expectedStatus: condition.expectedStatus,
            expectedRevision: condition.expectedRevision,
          })
        }
      } catch (cause) {
        if (!current()) return
        if (isAttendanceDenied(cause)) {
          deny()
          if (!(cause instanceof StaleSessionGenerationError)) options.error(cause)
          return
        }
        if (cause instanceof HeadmanJournalApiError && cause.response.status === 409) {
          staleRows.value = new Set(staleRows.value).add(userId)
          const refreshed = await options.refresh()
          if (current())
            setIssue(userId, {
              kind: 'conflict',
              message: refreshed
                ? conflictReviewMessage
                : 'Отметка изменилась. Обнови состав и проверь текущий статус.',
            })
          return
        }
        setIssue(userId, {
          kind: 'rejected',
          message: cause instanceof Error ? cause.message : 'Не удалось изменить отметку.',
        })
        return
      }

      if (!current()) return
      staleRows.value = new Set(staleRows.value).add(userId)
      project(entry, command, timestamp)
      if (fromForm) {
        draft.value = null
        restoreFocus(target, owner.generation, true)
      }
      // A successful write has no fresh revision. The only safe retry is this read.
      const refreshed = await options.refresh()
      if (current() && !refreshed)
        setIssue(userId, {
          kind: 'accepted',
          message: 'Отметка сохранена. Обнови состав, чтобы снова менять эту строку.',
        })
    } finally {
      if (current()) {
        pendingUserId.value = null
        if (!fromForm) restoreFocus(target, owner.generation)
      }
    }
  }

  function choose(
    entry: HeadmanJournalRosterEntry,
    choice: ManualChoice,
    source: EventTarget | null,
  ) {
    if (draft.value || blocked.value || !canChoose(entry, choice)) return
    const target = { userId: entry.userId, choice, source }
    const condition = markCondition(entry)
    if (entry.status === 'EXCUSED' || choice === 'EXCUSED') {
      if (!condition) return
      setIssue(entry.userId, null)
      const ticket = currentExcuseTicket(entry)
      draft.value = choice === 'EXCUSED'
        ? {
            ...target, condition, ticket, removing: false,
            kind: ticket ? 'linked' : canEditReason(entry) ? 'reason' : 'unknown',
          }
        : { ...target, condition, ticket, kind: 'replace', replacement: choice }
    } else if (entry.status === choice) void write(entry, target, null, false)
    else void write(entry, target, { status: choice }, false)
  }

  function requestRemoval(): void {
    const target = draft.value
    const entry = draftEntry.value
    if (!target || target.kind === 'replace' || !entry || entry.status !== 'EXCUSED' ||
        blocked.value || !sameDraftMark(target, entry) || !canWrite(entry, 'EXCUSED') ||
        issues.value[target.userId]?.kind === 'conflict') return
    draft.value = { ...target, removing: true }
  }

  function confirmChange(): void {
    const target = draft.value
    const entry = draftEntry.value
    if (!target || !entry || !sameDraftMark(target, entry) ||
        issues.value[target.userId]?.kind === 'conflict') return
    if (target.kind === 'replace')
      void write(entry, target, { status: target.replacement }, true)
    else if (target.removing && entry.status === 'EXCUSED')
      void write(entry, target, null, true)
  }

  function reviewConflict(): void {
    const target = draft.value
    const entry = draftEntry.value
    const condition = entry && markCondition(entry)
    if (
      !target ||
      target.kind !== 'reason' ||
      target.removing ||
      !entry ||
      !condition ||
      !canEditReason(entry) ||
      blocked.value ||
      !canWrite(entry, 'EXCUSED') ||
      issues.value[target.userId]?.kind !== 'conflict'
    )
      return
    draft.value = { ...target, condition }
    setIssue(target.userId, null)
  }

  function submitExcuse(command: ExcuseCommand): void {
    const target = draft.value
    const entry = draftEntry.value
    if (
      !target ||
      target.kind !== 'reason' ||
      target.removing ||
      !entry ||
      !canEditReason(entry) ||
      !sameDraftMark(target, entry) ||
      issues.value[target.userId]?.kind === 'conflict' ||
      !excuseTypes.some((item) => item.value === command.excuseType)
    )
      return
    void write(entry, target, command, true)
  }

  return {
    pendingUserId,
    draft,
    draftEntry,
    blocked,
    staleRows,
    issues,
    canWrite,
    canChoose,
    choose,
    requestRemoval,
    confirmChange,
    submitExcuse,
    reviewConflict,
    closeDraft,
    refreshRow,
    reportRefreshed,
    deny,
  }
}
