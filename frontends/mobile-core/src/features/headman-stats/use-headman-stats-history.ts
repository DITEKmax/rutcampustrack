import { computed, nextTick, onScopeDispose, ref, shallowRef, watch } from 'vue'
import { StaleSessionGenerationError } from '../../shared/session-owner'
import {
  HeadmanJournalApiError,
  type HeadmanJournalApi,
  type HeadmanJournalMarkCommand,
} from '../headman-journal/headman-journal-client'
import { HeadmanStatsApiError, type HeadmanStatsApi } from './headman-stats-client'
import {
  headmanStatsHistoryFilterKey,
  type HeadmanStatsLesson,
  type HeadmanStatsLessonPage,
} from './headman-stats-history-client'

export interface HeadmanStatsHistoryScope {
  api: HeadmanStatsApi | null
  journalApi: HeadmanJournalApi | null
  groupId: number | null
  studentId: number
  semesterId: number | null
  subjectId?: number | null
  lessonTypes?: readonly string[]
  offline: boolean
  allowed: boolean
  markAllowed: boolean
  excuseAllowed: boolean
}
interface Intent {
  generation: number
  scope: HeadmanStatsHistoryScope
  lesson: HeadmanStatsLesson
}
interface RowIssue {
  needsRead: boolean
  message: string
}
export function useHeadmanStatsHistory(
  scope: () => HeadmanStatsHistoryScope,
  refreshed: () => void,
) {
  const data = shallowRef<HeadmanStatsLessonPage | null>(null)
  const loading = ref(false)
  const error = ref<string | null>(null)
  const feedback = ref<string | null>(null)
  const pending = ref<number | null>(null)
  const refreshing = ref<number | null>(null)
  const rowIssues = shallowRef<ReadonlyMap<number, RowIssue>>(new Map())
  const confirmation = shallowRef<Intent | null>(null)
  const excuse = shallowRef<Intent | null>(null)
  const page = ref(0)
  const denied = ref(false)
  let generation = 0
  let readRevision = 0
  let controller: AbortController | null = null
  let disposed = false
  function isCurrent(intent: Pick<Intent, 'generation' | 'scope'>): boolean {
    const now = scope()
    const before = intent.scope
    return (
      !disposed &&
      generation === intent.generation &&
      before.api === now.api &&
      before.journalApi === now.journalApi &&
      before.groupId === now.groupId &&
      before.studentId === now.studentId &&
      before.semesterId === now.semesterId &&
      headmanStatsHistoryFilterKey(before.subjectId, before.lessonTypes) ===
        headmanStatsHistoryFilterKey(now.subjectId, now.lessonTypes) &&
      before.markAllowed === now.markAllowed &&
      before.excuseAllowed === now.excuseAllowed &&
      !now.offline &&
      now.allowed
    )
  }
  const writable = computed(
    () =>
      !denied.value &&
      !scope().offline &&
      scope().allowed &&
      Boolean(scope().journalApi?.writable) &&
      !loading.value &&
      pending.value === null &&
      refreshing.value === null,
  )
  function setIssue(lessonId: number, issue: RowIssue | null): void {
    const next = new Map(rowIssues.value)
    if (issue) next.set(lessonId, issue)
    else next.delete(lessonId)
    rowIssues.value = next
  }
  function canEdit(lesson: HeadmanStatsLesson): boolean {
    return (
      writable.value &&
      lesson.editable &&
      !rowIssues.value.get(lesson.lessonId)?.needsRead &&
      (lesson.status === null || Boolean(lesson.markRevision))
    )
  }
  function snapshot(lesson: HeadmanStatsLesson): Intent | null {
    if (!canEdit(lesson)) return null
    const current = data.value?.items.find((item) => item.lessonId === lesson.lessonId)
    if (
      !current ||
      !canEdit(current) ||
      current.status !== lesson.status ||
      current.markRevision !== lesson.markRevision
    )
      return null
    return {
      generation,
      scope: { ...scope(), lessonTypes: [...(scope().lessonTypes ?? [])] },
      lesson: { ...current },
    }
  }
  function deny(cause: Error): void {
    denied.value = true
    generation += 1
    readRevision += 1
    controller?.abort()
    data.value = null
    loading.value = false
    pending.value = null
    refreshing.value = null
    rowIssues.value = new Map()
    feedback.value = null
    confirmation.value = null
    excuse.value = null
    error.value = cause.message
  }
  function replaceRow(lesson: HeadmanStatsLesson): void {
    const current = data.value
    if (!current) return
    data.value = {
      ...current,
      items: current.items.map((item) => (item.lessonId === lesson.lessonId ? lesson : item)),
    }
  }
  async function read(targetLessonId?: number): Promise<boolean> {
    const owner = { generation, scope: { ...scope(), lessonTypes: [...(scope().lessonTypes ?? [])] } }
    const api = owner.scope.api
    if (!api || owner.scope.groupId === null || owner.scope.offline || !owner.scope.allowed)
      return false
    const revision = ++readRevision
    controller?.abort()
    const abort = new AbortController()
    controller = abort
    const requestedPage = page.value
    if (targetLessonId === undefined) {
      loading.value = true
      if (!denied.value) error.value = null
    } else refreshing.value = targetLessonId
    confirmation.value = null
    excuse.value = null
    try {
      const result = await api.studentLessons(
        owner.scope.studentId,
        {
          page: requestedPage,
          size: 50,
          ...(owner.scope.subjectId
            ? {
                subjectId: owner.scope.subjectId,
                lessonTypes: [...(owner.scope.lessonTypes ?? [])],
              }
            : {}),
          ...(owner.scope.semesterId === null ? {} : { semesterId: owner.scope.semesterId }),
        },
        abort.signal,
      )
      if (!isCurrent(owner) || revision !== readRevision || abort.signal.aborted) return false
      if (result.page !== requestedPage) throw new Error('Сервер вернул другую страницу истории.')
      if (targetLessonId === undefined) {
        data.value = result
        denied.value = false
        rowIssues.value = new Map()
        error.value = null
      } else {
        const lesson = result.items.find((item) => item.lessonId === targetLessonId)
        if (!lesson) return false
        replaceRow(lesson)
        setIssue(targetLessonId, null)
      }
      return true
    } catch (cause) {
      if (
        !isCurrent(owner) ||
        revision !== readRevision ||
        abort.signal.aborted ||
        cause instanceof StaleSessionGenerationError
      )
        return false
      if (cause instanceof HeadmanStatsApiError && cause.response.status === 403) {
        deny(cause)
        return false
      }
      if (targetLessonId === undefined)
        error.value = cause instanceof Error ? cause.message : 'Не удалось загрузить историю занятий.'
      return false
    } finally {
      if (revision === readRevision) {
        loading.value = false
        refreshing.value = null
      }
    }
  }
  function load(): Promise<boolean> {
    if (pending.value !== null || refreshing.value !== null) return Promise.resolve(false)
    return read()
  }
  function retryRow(lessonId: number): Promise<boolean> {
    if (
      denied.value || pending.value !== null || loading.value || refreshing.value !== null ||
      !rowIssues.value.get(lessonId)?.needsRead
    )
      return Promise.resolve(false)
    return read(lessonId)
  }
  async function write(intent: Intent, command: HeadmanJournalMarkCommand | null): Promise<void> {
    if (!writable.value || !isCurrent(intent) || !intent.scope.journalApi)
      return
    const current = data.value?.items.find((item) => item.lessonId === intent.lesson.lessonId)
    const target = command?.status ?? current?.status
    if (target === 'EXCUSED' ? !scope().excuseAllowed : !scope().markAllowed) return
    if (
      !current ||
      !canEdit(current) ||
      current.status !== intent.lesson.status ||
      current.markRevision !== intent.lesson.markRevision
    ) {
      confirmation.value = null
      excuse.value = null
      return
    }
    if (command === null && !current.status) return
    confirmation.value = null
    excuse.value = null
    pending.value = current.lessonId
    feedback.value = null
    error.value = null
    setIssue(current.lessonId, null)
    try {
      if (command === null)
        await intent.scope.journalApi.clear(current.lessonId, intent.scope.studentId, {
          expectedStatus: current.status!,
          expectedRevision: current.markRevision!,
        })
      else {
        const ack = await intent.scope.journalApi.mark(
          current.lessonId,
          intent.scope.studentId,
          command,
          {
            expectedStatus: current.status ?? 'EMPTY',
            expectedRevision: current.status ? current.markRevision! : 'EMPTY',
          },
        )
        if (
          ack.lessonId !== current.lessonId ||
          ack.userId !== intent.scope.studentId ||
          ack.status !== command.status
        )
          throw new Error('Сервер вернул результат для другой отметки.')
      }
      if (!isCurrent(intent)) return
      // The receipt confirms the status, but does not contain a revision for the next write.
      replaceRow({ ...current, status: command?.status ?? null, source: null, markRevision: null })
      setIssue(current.lessonId, {
        needsRead: true,
        message: 'Изменение принято. Обнови отметку перед следующим изменением.',
      })
      feedback.value = `${command === null ? 'Статус снят' : 'Статус сохранён'} · ${current.lessonDate} · ${current.subjectName}`
      await read(current.lessonId)
      if (isCurrent(intent)) refreshed()
    } catch (cause) {
      if (!isCurrent(intent) || cause instanceof StaleSessionGenerationError) return
      if (cause instanceof HeadmanJournalApiError && cause.response.status === 403) {
        deny(cause)
        return
      }
      if (cause instanceof HeadmanJournalApiError && cause.response.status === 409) {
        setIssue(current.lessonId, {
          needsRead: true,
          message: 'Отметка уже изменилась. Сначала обнови её.',
        })
        if ((await read(current.lessonId)) && isCurrent(intent))
          setIssue(current.lessonId, {
            needsRead: false,
            message: 'Отметка уже изменилась. Проверь её и выбери действие заново.',
          })
      } else {
        setIssue(current.lessonId, {
          needsRead: false,
          message: cause instanceof Error ? cause.message : 'Не удалось изменить отметку.',
        })
      }
    } finally {
      if (isCurrent(intent)) pending.value = null
    }
  }
  function choose(lesson: HeadmanStatsLesson, status: 'PRESENT' | 'EXCUSED' | 'ABSENT'): Promise<void> | undefined {
    if (status === 'EXCUSED' ? !scope().excuseAllowed : !scope().markAllowed) return
    const intent = snapshot(lesson)
    if (!intent) return
    if (intent.lesson.status !== null) {
      if (intent.lesson.status !== status) return
      if (status === 'EXCUSED') confirmation.value = intent
      else return write(intent, null)
    } else if (status === 'EXCUSED') excuse.value = intent
    else return write(intent, { status })
  }
  function confirm(): Promise<void> | undefined {
    const intent = confirmation.value
    if (intent) return write(intent, null)
  }
  function saveExcuse(command: Extract<HeadmanJournalMarkCommand, { status: 'EXCUSED' }>): Promise<void> | undefined {
    const intent = excuse.value
    if (intent) return write(intent, command)
  }
  function cancel(): void {
    confirmation.value = null
    excuse.value = null
  }
  function changePage(next: number): void {
    if (pending.value !== null || loading.value || refreshing.value !== null || !data.value || next < 0 || next >= data.value.totalPages) return
    page.value = next
    data.value = null
    void load()
  }
  watch(
    [
      () => scope().api,
      () => scope().journalApi,
      () => scope().groupId,
      () => scope().studentId,
      () => scope().semesterId,
      () => {
        const { subjectId, lessonTypes } = scope()
        return headmanStatsHistoryFilterKey(subjectId, lessonTypes)
      },
      () => scope().offline,
      () => scope().allowed,
      () => scope().markAllowed,
      () => scope().excuseAllowed,
    ],
    () => {
      generation += 1
      const scheduledRead = ++readRevision
      controller?.abort()
      denied.value = false
      data.value = null
      loading.value = false
      error.value = null
      feedback.value = null
      pending.value = null
      refreshing.value = null
      rowIssues.value = new Map()
      confirmation.value = null
      excuse.value = null
      page.value = 0
      // Purge synchronously, then read once after related scope fields settle.
      void nextTick(() => {
        if (!disposed && scheduledRead === readRevision) void load()
      })
    },
    { immediate: true, flush: 'sync' },
  )
  onScopeDispose(() => {
    disposed = true
    generation += 1
    controller?.abort()
  })
  return {
    data,
    loading,
    error,
    feedback,
    pending,
    refreshing,
    rowIssues,
    confirmation,
    excuse,
    denied,
    load,
    retryRow,
    canEdit,
    choose,
    confirm,
    saveExcuse,
    cancel,
    changePage,
    writable,
  }
}
