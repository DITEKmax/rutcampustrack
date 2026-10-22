import { computed, onScopeDispose, ref, shallowRef, watch } from 'vue'
import { StaleSessionGenerationError } from '../../shared/session-owner'
import {
  HeadmanJournalApiError,
  type HeadmanJournalApi,
  type HeadmanJournalMarkCommand,
} from '../headman-journal/headman-journal-client'
import { HeadmanStatsApiError, type HeadmanStatsApi } from './headman-stats-client'
import type { HeadmanStatsLesson, HeadmanStatsLessonPage } from './headman-stats-history-client'

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
export function useHeadmanStatsHistory(
  scope: () => HeadmanStatsHistoryScope,
  refreshed: () => void,
) {
  const data = shallowRef<HeadmanStatsLessonPage | null>(null)
  const loading = ref(false)
  const error = ref<string | null>(null)
  const feedback = ref<string | null>(null)
  const pending = ref<number | null>(null)
  const confirmation = shallowRef<Intent | null>(null)
  const excuse = shallowRef<Intent | null>(null)
  const page = ref(0)
  const denied = ref(false)
  let generation = 0
  let readRevision = 0
  let controller: AbortController | null = null
  let disposed = false
  function isCurrent(intent: Intent): boolean {
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
      before.subjectId === now.subjectId &&
      JSON.stringify(before.lessonTypes ?? []) === JSON.stringify(now.lessonTypes ?? []) &&
      before.markAllowed === now.markAllowed &&
      before.excuseAllowed === now.excuseAllowed &&
      !now.offline &&
      now.allowed
    )
  }
  function snapshot(lesson: HeadmanStatsLesson): Intent | null {
    if (
      denied.value ||
      !lesson.editable ||
      pending.value !== null ||
      loading.value ||
      scope().offline ||
      !scope().allowed ||
      !scope().journalApi?.writable
    )
      return null
    const current = data.value?.items.find((item) => item.lessonId === lesson.lessonId)
    if (
      !current ||
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
    feedback.value = null
    confirmation.value = null
    excuse.value = null
    error.value = cause.message
  }
  async function load(): Promise<boolean> {
    const owner = { generation, scope: { ...scope() }, lesson: {} as HeadmanStatsLesson }
    const api = owner.scope.api
    if (!api || owner.scope.groupId === null || owner.scope.offline || !owner.scope.allowed)
      return false
    const revision = ++readRevision
    controller?.abort()
    const abort = new AbortController()
    controller = abort
    loading.value = true
    if (!denied.value) error.value = null
    confirmation.value = null
    excuse.value = null
    try {
      const result = await api.studentLessons(
        owner.scope.studentId,
        {
          page: page.value,
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
      data.value = result
      denied.value = false
      error.value = null
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
      error.value = cause instanceof Error ? cause.message : 'Не удалось загрузить историю занятий.'
      return false
    } finally {
      if (revision === readRevision) loading.value = false
    }
  }
  async function write(intent: Intent, command: HeadmanJournalMarkCommand | null): Promise<void> {
    if (denied.value || !isCurrent(intent) || pending.value !== null || !intent.scope.journalApi)
      return
    const current = data.value?.items.find((item) => item.lessonId === intent.lesson.lessonId)
    const target = command?.status ?? current?.status
    if (target === 'EXCUSED' ? !scope().excuseAllowed : !scope().markAllowed) return
    if (
      !current?.editable ||
      current.status !== intent.lesson.status ||
      current.markRevision !== intent.lesson.markRevision
    ) {
      confirmation.value = null
      excuse.value = null
      await load()
      error.value = 'Отметка изменилась. Проверь новую отметку и повтори действие.'
      return
    }
    if ((command === null && !current.status) || (current.status && !current.markRevision)) {
      await load()
      error.value = 'Обнови отметку перед изменением.'
      return
    }
    confirmation.value = null
    excuse.value = null
    pending.value = current.lessonId
    feedback.value = null
    error.value = null
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
      data.value = null
      const loaded = await load()
      if (!isCurrent(intent)) return
      feedback.value = loaded
        ? `${command === null ? 'Статус снят' : 'Статус сохранён'} · ${current.lessonDate} · ${current.subjectName}`
        : 'Изменение принято сервером. Обнови историю, чтобы увидеть актуальный статус.'
      refreshed()
    } catch (cause) {
      if (!isCurrent(intent) || cause instanceof StaleSessionGenerationError) return
      if (cause instanceof HeadmanJournalApiError && cause.response.status === 403) {
        deny(cause)
        return
      }
      if (cause instanceof HeadmanJournalApiError && cause.response.status === 409) {
        await load()
        if (isCurrent(intent))
          error.value =
            command === null
              ? 'Отметка уже изменилась. Проверь её и подтверди снятие заново.'
              : 'Отметка уже изменилась. Проверь её и повтори действие.'
      } else error.value = cause instanceof Error ? cause.message : 'Не удалось изменить отметку.'
    } finally {
      if (isCurrent(intent)) pending.value = null
    }
  }
  function choose(lesson: HeadmanStatsLesson, status: 'PRESENT' | 'EXCUSED' | 'ABSENT'): void {
    if (status === 'EXCUSED' ? !scope().excuseAllowed : !scope().markAllowed) return
    const intent = snapshot(lesson)
    if (!intent) return
    if (intent.lesson.status !== null) {
      if (intent.lesson.status !== status) return
      if (status === 'EXCUSED') confirmation.value = intent
      else void write(intent, null)
    } else if (status === 'EXCUSED') excuse.value = intent
    else void write(intent, { status })
  }
  function confirm(): void {
    const intent = confirmation.value
    if (intent) void write(intent, null)
  }
  function saveExcuse(command: Extract<HeadmanJournalMarkCommand, { status: 'EXCUSED' }>): void {
    const intent = excuse.value
    if (intent) void write(intent, command)
  }
  function cancel(): void {
    confirmation.value = null
    excuse.value = null
  }
  function changePage(next: number): void {
    if (pending.value !== null || !data.value || next < 0 || next >= data.value.totalPages) return
    page.value = next
    data.value = null
    void load()
  }
  watch(
    () => {
      const owner = scope()
      return [
        owner.api,
        owner.journalApi,
        owner.groupId,
        owner.studentId,
        owner.semesterId,
        owner.subjectId,
        JSON.stringify(owner.lessonTypes ?? []),
        owner.offline,
        owner.allowed,
        owner.markAllowed,
        owner.excuseAllowed,
      ] as const
    },
    () => {
      generation += 1
      readRevision += 1
      controller?.abort()
      denied.value = false
      data.value = null
      loading.value = false
      error.value = null
      feedback.value = null
      pending.value = null
      confirmation.value = null
      excuse.value = null
      page.value = 0
      void load()
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
    confirmation,
    excuse,
    denied,
    load,
    choose,
    confirm,
    saveExcuse,
    cancel,
    changePage,
    writable: computed(
      () =>
        !denied.value &&
        !scope().offline &&
        scope().allowed &&
        Boolean(scope().journalApi?.writable) &&
        !loading.value &&
        pending.value === null,
    ),
  }
}
