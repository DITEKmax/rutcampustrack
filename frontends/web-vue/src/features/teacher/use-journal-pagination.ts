import { onBeforeUnmount, ref, watch, type Ref, type ShallowRef } from 'vue'
import { TeacherApiError, type TeacherJournalQuery, type TeacherJournalResponse } from '../../../../mobile-core/src/features/teacher/teacher-client'
import { mergeJournalPage } from '../../../../mobile-core/src/features/teacher/teacher-journal-model'
import { teacherError, type TeacherScope } from './teacher-state'

class JournalPageDrift extends Error {
  constructor() {
    super('Список пар изменился. Обнови журнал, чтобы продолжить.')
  }
}

function mergeVerifiedPage(
  previous: TeacherJournalResponse | null,
  next: TeacherJournalResponse,
  page: number,
  pageSize: number | undefined,
) {
  const previousCount = previous?.lessons.length ?? 0
  const expectedCount = Math.min(next.pageSize, Math.max(0, next.totalLessons - previousCount))
  const merged = mergeJournalPage(previous, next)
  if (next.page !== page ||
      next.pageSize !== (previous?.pageSize ?? pageSize ?? next.pageSize) ||
      (previous && next.totalLessons !== previous.totalLessons) ||
      next.lessons.length !== expectedCount ||
      merged.lessons.length !== previousCount + expectedCount ||
      next.hasMore !== (merged.lessons.length < next.totalLessons))
    throw new JournalPageDrift()
  return merged
}

/** Append one owned server page when the horizontal journal edge becomes visible. */
export function useJournalPagination(
  scope: TeacherScope,
  data: ShallowRef<TeacherJournalResponse | null>,
  initialLoading: Ref<boolean>,
  query: ShallowRef<TeacherJournalQuery | null>,
) {
  const region = ref<HTMLElement | null>(null)
  const edge = ref<HTMLElement | null>(null)
  const loading = ref(false)
  const refreshing = ref(false)
  const error = ref('')
  const automatic = typeof IntersectionObserver === 'function'
  let observer: IntersectionObserver | null = null
  let revision = 0
  let alive = true
  const reloadRequired = ref(false)
  let retrying = false

  watch(query, () => { revision++; loading.value = false; refreshing.value = false; error.value = ''; reloadRequired.value = false }, { flush: 'sync' })

  async function firstPage(requested: TeacherJournalQuery) {
    const next = await scope.api.journal(requested)
    return mergeVerifiedPage(null, next, 0, requested.pageSize)
  }
  /** Replace only a complete refresh of the window already opened under this applied query. */
  async function refreshWindow(requested: TeacherJournalQuery): Promise<TeacherJournalResponse> {
    const lastPage = data.value?.page ?? 0
    const run = ++revision
    const current = () => alive && scope.current() && run === revision && query.value === requested
    refreshing.value = true
    loading.value = false
    error.value = ''
    let merged: TeacherJournalResponse | null = null
    try {
      for (let page = 0; page <= lastPage; page++) {
        if (!current()) throw new Error('Контекст журнала изменился. Обнови данные.')
        const next = await scope.api.journal({ ...requested, page, includeMetrics: page === 0 })
        scope.transport.assertCurrent()
        if (!current()) throw new Error('Контекст журнала изменился. Обнови данные.')
        merged = mergeVerifiedPage(merged, next, page, requested.pageSize)
        if (!next.hasMore) break
      }
      reloadRequired.value = false
      return merged!
    } catch (cause) {
      if (run === revision) reloadRequired.value = true
      throw cause
    } finally {
      if (run === revision) refreshing.value = false
    }
  }

  async function more(retry = false) {
    const requested = query.value, previous = data.value
    if (!requested || !previous?.hasMore || initialLoading.value || loading.value || refreshing.value || reloadRequired.value || (error.value && !retry)) return
    const run = ++revision
    const current = () => alive && scope.current() && run === revision && query.value === requested && data.value === previous
    loading.value = true
    error.value = ''
    try {
      const next = await scope.api.journal({ ...requested, page: previous.page + 1, includeMetrics: false })
      scope.transport.assertCurrent()
      if (!current()) return
      data.value = mergeVerifiedPage(previous, next, previous.page + 1, requested.pageSize)
    } catch (cause) {
      if (!current()) return
      error.value = teacherError(cause)
      if (cause instanceof JournalPageDrift) reloadRequired.value = true
      if (cause instanceof TeacherApiError && [401, 403, 404].includes(cause.response.status)) {
        data.value = null
        query.value = null
        error.value = teacherError(cause)
        reloadRequired.value = true
      }
    } finally {
      if (alive && scope.current() && run === revision) loading.value = false
    }
  }

  watch([region, edge, data, query, loading, refreshing, initialLoading], () => {
    observer?.disconnect()
    observer = null
    if (!automatic || !region.value || !edge.value || !data.value?.hasMore || !query.value || loading.value || refreshing.value || reloadRequired.value || initialLoading.value || error.value) return
    // IntersectionObserver accepts pixels; derive its eight-rem prefetch distance from the actual root font.
    const margin = Number.parseFloat(getComputedStyle(document.documentElement).fontSize) * 8
    const nextObserver = new IntersectionObserver(entries => {
      if (observer === nextObserver && entries.some(entry => entry.isIntersecting)) void more()
    }, { root: region.value, rootMargin: `0px ${margin}px 0px 0px` })
    observer = nextObserver
    observer.observe(edge.value)
  }, { flush: 'post' })
  onBeforeUnmount(() => { alive = false; revision++; observer?.disconnect() })
  async function retry() {
    if (!query.value || reloadRequired.value) {
      if (retrying) return
      retrying = true
      try {
        await scope.load()
      } finally {
        retrying = false
      }
    } else await more(true)
  }
  return { region, edge, loading, refreshing, error, reloadRequired, automatic, more: retry, firstPage, refreshWindow }
}
