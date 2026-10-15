import { computed, onScopeDispose, ref, toValue, watch, type MaybeRefOrGetter } from 'vue'
import { useQuery, useQueryClient } from '@tanstack/vue-query'
import { StudentApiError, type StudentApi } from '../../api/student-client'
import type {
  StudentStatisticsOverviewResponse,
  StudentStatisticsSeriesPoint,
  StudentStatisticsSubjectDetailResponse,
  StudentStatisticsSubjectSummary,
  StudentStatisticsTypeCard,
  StudentStatisticsRankingResponse,
} from '../../api/types'
import { studentFeatureScopeIdentity, type StudentFeatureScope } from '../../shared/session-owner'
import type {
  StatisticsGraphRange,
  StatisticsHistorySegment,
  StatisticsLessonType,
  StatisticsOverviewData,
  StatisticsReadState,
  StatisticsSubjectDetailData,
} from './statistics-view-model'
import { STATISTICS_TYPE_ORDER } from './statistics-view-model'

export type StudentStatisticsScopeInput = MaybeRefOrGetter<StudentFeatureScope | null | undefined>

export class StatisticsScopeError extends Error {
  constructor() {
    super('Statistics scope is not ready')
    this.name = 'StatisticsScopeError'
  }
}

export class StatisticsStaleResponseError extends Error {
  constructor() {
    super('Statistics response belongs to an earlier identity')
    this.name = 'StatisticsStaleResponseError'
  }
}

type StatisticsQueryKey = readonly [
  'student',
  'statistics',
  string | null,
  string | null,
  string | null,
  string | null,
  string | null,
  string | null,
  string | null,
  boolean,
  number,
]

type StatisticsDetailQueryKey = readonly [
  ...StatisticsQueryKey,
  'subject',
  string | null,
  StatisticsGraphRange,
  string,
]

function normalizeStatisticsScope(
  scope: StudentFeatureScope | null | undefined,
): StudentFeatureScope {
  return {
    userId: scope?.userId ?? null,
    activeRole: scope?.activeRole ?? null,
    groupId: scope?.groupId ?? null,
    semesterId: scope?.semesterId ?? null,
    sessionId: scope?.sessionId ?? null,
    sessionVersion: scope?.sessionVersion ?? null,
    rolesVersion: scope?.rolesVersion ?? null,
    readOnly: scope?.readOnly ?? false,
    resetGeneration:
      Number.isInteger(scope?.resetGeneration) && (scope?.resetGeneration ?? 0) >= 0
        ? (scope?.resetGeneration ?? 0)
        : 0,
  }
}

function isStatisticsScopeReady(
  scope: StudentFeatureScope | null | undefined,
): scope is StudentFeatureScope {
  if (!scope) return false
  return (
    scope.activeRole === 'STUDENT' &&
    typeof scope.userId === 'string' &&
    scope.userId.length > 0 &&
    typeof scope.semesterId === 'string' &&
    scope.semesterId.length > 0 &&
    Number.isInteger(scope.resetGeneration) &&
    scope.resetGeneration >= 0
  )
}

function statisticsQueryKey(scope: StudentFeatureScope | null | undefined): StatisticsQueryKey {
  const value = normalizeStatisticsScope(scope)
  return [
    'student',
    'statistics',
    value.userId,
    value.activeRole,
    value.groupId,
    value.semesterId,
    value.sessionId,
    value.sessionVersion,
    value.rolesVersion,
    value.readOnly,
    value.resetGeneration,
  ]
}

function statisticsDetailQueryKey(
  scope: StudentFeatureScope | null | undefined,
  subjectId: string | null,
  range: StatisticsGraphRange,
  selectedTypes: readonly StatisticsLessonType[],
): StatisticsDetailQueryKey {
  return [...statisticsQueryKey(scope), 'subject', subjectId, range, selectedTypes.join(',')]
}

function mapSeriesPoint(
  point: StudentStatisticsSeriesPoint,
): StatisticsOverviewData['semesterSeries'][number] {
  return {
    id: point.id,
    label: point.label,
    dateFrom: point.dateFrom,
    dateTo: point.dateTo,
    state: point.state,
    metrics: { ...point.metrics },
    ...(point.groupPresent !== undefined
      ? { groupPresent: decodeGroupPresent(point.groupPresent) }
      : {}),
  }
}

function decodeGroupPresent(
  value: unknown,
): NonNullable<StudentStatisticsSeriesPoint['groupPresent']> | null {
  if (value === null) return null
  if (!value || typeof value !== 'object') throw new TypeError('Некорректное сравнение с группой')
  const metric = value as Record<string, unknown>
  if (
    typeof metric.numerator !== 'number' ||
    !Number.isSafeInteger(metric.numerator) ||
    metric.numerator < 0 ||
    typeof metric.denominator !== 'number' ||
    !Number.isSafeInteger(metric.denominator) ||
    metric.denominator < metric.numerator ||
    metric.denominator < 0 ||
    (metric.percent !== null &&
      (typeof metric.percent !== 'number' ||
        !Number.isFinite(metric.percent) ||
        metric.percent < 0 ||
        metric.percent > 100 ||
        metric.denominator === 0))
  )
    throw new TypeError('Некорректное сравнение с группой')
  return {
    numerator: metric.numerator,
    denominator: metric.denominator,
    percent: metric.percent as number | null,
  }
}

function mapSubjectSummary(
  subject: StudentStatisticsSubjectSummary,
): StatisticsOverviewData['subjects'][number] {
  return {
    id: subject.id,
    name: subject.name,
    metrics: { ...subject.metrics },
  }
}

function mapHistorySegment(segment: StatisticsHistorySegment): StatisticsHistorySegment {
  return { ...segment }
}

function mapTypeCard(
  card: StudentStatisticsTypeCard,
): StatisticsSubjectDetailData['typeCards'][number] {
  return {
    type: card.type,
    metrics: { ...card.metrics },
    history: card.history.map(mapHistorySegment),
  }
}

export function toStatisticsOverview(
  value: StudentStatisticsOverviewResponse,
): StatisticsOverviewData {
  return {
    metrics: { ...value.metrics },
    ownRank: { ...value.ownRank },
    semesterSeries: value.semesterSeries.map(mapSeriesPoint),
    subjects: value.subjects.map(mapSubjectSummary),
  }
}

export function toStatisticsSubjectDetail(
  value: StudentStatisticsSubjectDetailResponse,
): StatisticsSubjectDetailData {
  return {
    subjectId: String(value.subjectId),
    name: value.name,
    availableTypes: [...value.availableTypes],
    selectedTypes: [...value.selectedTypes],
    selectedAggregate: { ...value.selectedAggregate },
    series: value.series.map(mapSeriesPoint),
    typeCards: value.typeCards.map(mapTypeCard),
  }
}

function hasOverviewProjection(value: StatisticsOverviewData): boolean {
  return (
    value.metrics.planned > 0 ||
    value.ownRank.participantCount > 0 ||
    value.semesterSeries.length > 0 ||
    value.subjects.length > 0
  )
}

function hasSubjectProjection(value: StatisticsSubjectDetailData): boolean {
  return (
    value.selectedAggregate.planned > 0 ||
    value.availableTypes.length > 0 ||
    value.series.length > 0 ||
    value.typeCards.length > 0
  )
}

function readErrorDetails(error: unknown): { code: string; message: string; retryable: boolean } {
  if (error instanceof StudentApiError) {
    const status = error.response.status
    const problemCode = error.problem?.extras?.code
    return {
      code: typeof problemCode === 'string' ? problemCode : `HTTP_${status}`,
      message: error.problem?.detail ?? error.message,
      retryable: status === 408 || status === 425 || status === 429 || status >= 500,
    }
  }
  return {
    code: error instanceof Error ? error.name : 'NETWORK',
    message:
      error instanceof Error && error.message ? error.message : 'Не удалось получить статистику.',
    retryable: true,
  }
}

function shouldRetry(failureCount: number, error: unknown): boolean {
  if (failureCount >= 1) return false
  return !(
    error instanceof StudentApiError &&
    error.response.status >= 400 &&
    error.response.status < 500
  )
}

function errorState(
  error: unknown,
): Extract<StatisticsReadState<never>, { status: 'error' | 'forbidden' }> {
  if (error instanceof StudentApiError && error.response.status === 403) {
    return {
      status: 'forbidden',
      reason: error.problem?.detail ?? 'У тебя нет доступа к статистике.',
    }
  }
  return { status: 'error', ...readErrorDetails(error) }
}

function normalizeTypes(types: readonly StatisticsLessonType[]): readonly StatisticsLessonType[] {
  return STATISTICS_TYPE_ORDER.filter((type) => types.includes(type))
}

/** One query owner for the signed student statistics projections. */
export function useStatistics(
  api: StudentApi,
  scopeInput: StudentStatisticsScopeInput,
  offlineInput: MaybeRefOrGetter<boolean>,
) {
  const queryClient = useQueryClient()
  const scope = computed(() => normalizeStatisticsScope(toValue(scopeInput)))
  const scopeReady = computed(() => isStatisticsScopeReady(toValue(scopeInput)))
  const offline = computed(() => Boolean(toValue(offlineInput)))
  const selectedSubjectId = ref<string | null>(null)
  const range = ref<StatisticsGraphRange>('weeks')
  // null is the initial ALL choice; [] is an explicit local NONE choice.
  const selectedTypes = ref<readonly StatisticsLessonType[] | null>(null)
  const rankingVisible = ref(false)
  const rankingPage = ref<number | null>(null)
  watch(
    () => studentFeatureScopeIdentity(scope.value),
    () => {
      selectedSubjectId.value = null
      selectedTypes.value = null
      range.value = 'weeks'
      rankingVisible.value = false
      rankingPage.value = null
    },
    { flush: 'sync' },
  )
  const allDetailQueryKey = computed(() =>
    statisticsDetailQueryKey(scope.value, selectedSubjectId.value, 'weeks', []),
  )
  const availableTypes = computed(() => allResponse.value?.availableTypes ?? [])
  const effectiveTypes = computed(() =>
    selectedTypes.value === null
      ? normalizeTypes(availableTypes.value)
      : normalizeTypes(selectedTypes.value.filter((type) => availableTypes.value.includes(type))),
  )
  const requestTypes = computed(() =>
    effectiveTypes.value.length === availableTypes.value.length ? [] : effectiveTypes.value,
  )
  const queryKey = computed(() => statisticsQueryKey(scope.value))
  const detailQueryKey = computed(() =>
    statisticsDetailQueryKey(scope.value, selectedSubjectId.value, range.value, requestTypes.value),
  )

  const query = useQuery<StudentStatisticsOverviewResponse>({
    queryKey,
    queryFn: async ({ signal }) => {
      const requestScope = scope.value
      if (!isStatisticsScopeReady(requestScope)) throw new StatisticsScopeError()
      const response = await api.getStatistics(requestScope.semesterId!, signal)
      if (studentFeatureScopeIdentity(scope.value) !== studentFeatureScopeIdentity(requestScope)) {
        throw new StatisticsStaleResponseError()
      }
      toStatisticsOverview(response)
      return response
    },
    enabled: computed(() => scopeReady.value && !offline.value),
    retry: shouldRetry,
  })

  const rankingQueryKey = computed(
    () => [...statisticsQueryKey(scope.value), 'ranking', rankingPage.value] as const,
  )
  const rankingQuery = useQuery<StudentStatisticsRankingResponse>({
    queryKey: rankingQueryKey,
    enabled: computed(
      () =>
        rankingVisible.value && scopeReady.value && Boolean(scope.value.groupId) && !offline.value,
    ),
    queryFn: async ({ signal, queryKey: key }) => {
      const requestScope = scope.value
      if (!isStatisticsScopeReady(requestScope) || !requestScope.groupId)
        throw new StatisticsScopeError()
      const requestedPage = (key as typeof rankingQueryKey.value)[12]
      const response = await api.getStatisticsRanking(
        requestScope.semesterId!,
        requestedPage,
        20,
        signal,
      )
      if (
        signal.aborted ||
        offline.value ||
        JSON.stringify(rankingQueryKey.value) !== JSON.stringify(key) ||
        studentFeatureScopeIdentity(scope.value) !== studentFeatureScopeIdentity(requestScope)
      )
        throw new StatisticsStaleResponseError()
      if (response.rows.some((row) => row.isSelf !== (row.id === requestScope.userId)))
        throw new TypeError('Некорректный текущий участник рейтинга')
      return response
    },
    retry: shouldRetry,
  })
  watch(
    [queryKey, offline],
    (_, previous) => {
      if (previous) {
        const previousRanking = [...previous[0], 'ranking']
        void queryClient.cancelQueries({ queryKey: previousRanking })
        queryClient.removeQueries({ queryKey: previousRanking })
      }
    },
    { flush: 'sync' },
  )
  onScopeDispose(() => {
    const currentRanking = [...queryKey.value, 'ranking']
    void queryClient.cancelQueries({ queryKey: currentRanking })
    queryClient.removeQueries({ queryKey: currentRanking })
  })
  const rankingState = computed<StatisticsReadState<StudentStatisticsRankingResponse>>(() => {
    if (offline.value) return { status: 'offline' }
    if (!scopeReady.value || !scope.value.groupId)
      return { status: 'forbidden', reason: 'Рейтинг доступен только для своей текущей группы.' }
    void rankingQuery.data.value
    void rankingQuery.error.value
    void rankingQuery.fetchStatus.value
    const current = queryClient.getQueryState<StudentStatisticsRankingResponse>(
      rankingQueryKey.value,
    )
    if (current?.error) return errorState(current.error)
    if (!current?.data || current.fetchStatus === 'fetching') return { status: 'loading' }
    if (!current.data.available || !current.data.rows.length) return { status: 'empty' }
    return { status: 'ready', data: current.data }
  })

  function openRanking(): void {
    rankingVisible.value = true
  }
  function setRankingPage(page: number): void {
    const state = rankingState.value
    if (
      state.status !== 'ready' ||
      !Number.isSafeInteger(page) ||
      page < 0 ||
      page * state.data.size >= state.data.total
    )
      return
    rankingPage.value = page
  }
  function retryRanking(): void {
    void rankingQuery.refetch()
  }

  async function fetchDetail(signal: AbortSignal, key: StatisticsDetailQueryKey) {
    const requestScope = scope.value
    const subjectId = key[12]
    const requestRange = key[13]
    const types = normalizeTypes(key[14].split(',') as StatisticsLessonType[])
    if (!isStatisticsScopeReady(requestScope) || !subjectId) throw new StatisticsScopeError()
    const response = await api.getStatisticsSubject(
      subjectId,
      requestScope.semesterId!,
      requestRange,
      types,
      signal,
    )
    if (
      studentFeatureScopeIdentity(scope.value) !== studentFeatureScopeIdentity(requestScope) ||
      selectedSubjectId.value !== subjectId
    )
      throw new StatisticsStaleResponseError()
    toStatisticsSubjectDetail(response)
    return response
  }
  // The authoritative ALL projection and graph projection share the same cache
  // key when ALL is selected. TanStack deduplicates their in-flight request.
  const allDetailQuery = useQuery<StudentStatisticsSubjectDetailResponse>({
    queryKey: allDetailQueryKey,
    queryFn: ({ signal, queryKey: key }) => fetchDetail(signal, key as StatisticsDetailQueryKey),
    enabled: computed(() => scopeReady.value && !offline.value && selectedSubjectId.value !== null),
    retry: shouldRetry,
  })
  const allResponse = computed(() => {
    void allDetailQuery.data.value
    return queryClient.getQueryData<StudentStatisticsSubjectDetailResponse>(allDetailQueryKey.value)
  })
  const detailQuery = useQuery<StudentStatisticsSubjectDetailResponse>({
    queryKey: detailQueryKey,
    queryFn: async ({ signal, queryKey: key }) => {
      const response = await fetchDetail(signal, key as StatisticsDetailQueryKey)
      if (JSON.stringify(detailQueryKey.value) !== JSON.stringify(key))
        throw new StatisticsStaleResponseError()
      return response
    },
    enabled: computed(
      () =>
        scopeReady.value &&
        !offline.value &&
        selectedSubjectId.value !== null &&
        allResponse.value !== undefined &&
        effectiveTypes.value.length > 0 &&
        JSON.stringify(detailQueryKey.value) !== JSON.stringify(allDetailQueryKey.value),
    ),
    retry: shouldRetry,
  })

  // Read only the exact current cache key. Observer refs can lag one Vue tick
  // after a scope/filter change; that previous projection must never be rendered.
  const graphResponse = computed(() => {
    void detailQuery.data.value
    return queryClient.getQueryData<StudentStatisticsSubjectDetailResponse>(detailQueryKey.value)
  })
  const graphError = computed(() => {
    void detailQuery.error.value
    return queryClient.getQueryState(detailQueryKey.value)?.error
  })
  const graphFetchStatus = computed(() => {
    void detailQuery.fetchStatus.value
    void allDetailQuery.fetchStatus.value
    return queryClient.getQueryState(detailQueryKey.value)?.fetchStatus
  })

  const overviewData = computed(() =>
    query.data.value ? toStatisticsOverview(query.data.value) : null,
  )
  const detailData = computed<StatisticsSubjectDetailData | null>(() => {
    if (!allResponse.value || !scopeReady.value || !selectedSubjectId.value) return null
    const all = toStatisticsSubjectDetail(allResponse.value)
    const graph = graphResponse.value
    return {
      ...all,
      selectedTypes: effectiveTypes.value,
      selectedAggregate: graph?.selectedAggregate ?? all.selectedAggregate,
      series: effectiveTypes.value.length > 0 ? (graph?.series.map(mapSeriesPoint) ?? []) : [],
      graphStatus:
        effectiveTypes.value.length === 0
          ? 'ready'
          : graphFetchStatus.value === 'fetching'
            ? 'loading'
            : graphError.value
              ? 'error'
              : graph
                ? 'ready'
                : 'loading',
      ...(graphError.value ? { graphMessage: readErrorDetails(graphError.value).message } : {}),
    }
  })
  const overviewState = computed<StatisticsReadState<StatisticsOverviewData>>(() => {
    if (offline.value) return { status: 'offline' }
    if (query.error.value) return errorState(query.error.value)
    if (overviewData.value)
      return hasOverviewProjection(overviewData.value)
        ? { status: 'ready', data: overviewData.value }
        : { status: 'empty' }
    if (!scopeReady.value)
      return {
        status: 'forbidden',
        reason: 'Статистика появится, когда будет выбран активный семестр.',
      }
    if (query.isPending.value) return { status: 'loading' }
    return { status: 'empty' }
  })
  const detailState = computed<StatisticsReadState<StatisticsSubjectDetailData>>(() => {
    if (offline.value) return { status: 'offline' }
    if (!selectedSubjectId.value) return { status: 'empty' }
    if (allDetailQuery.error.value && !detailData.value)
      return errorState(allDetailQuery.error.value)
    if (detailData.value)
      return hasSubjectProjection(detailData.value)
        ? { status: 'ready', data: detailData.value }
        : { status: 'empty' }
    if (allDetailQuery.isPending.value || !scopeReady.value) return { status: 'loading' }
    return { status: 'empty' }
  })

  function openSubject(subjectId: string): void {
    if (!subjectId) return
    selectedSubjectId.value = subjectId
    selectedTypes.value = null
  }

  function closeSubject(): void {
    selectedSubjectId.value = null
    selectedTypes.value = null
  }

  function setRange(nextRange: StatisticsGraphRange): void {
    range.value = nextRange
  }

  function setTypes(types: readonly StatisticsLessonType[]): void {
    const normalized = normalizeTypes(types)
    selectedTypes.value = normalized
  }

  function retryDetail(): void {
    void allDetailQuery.refetch()
    if (
      effectiveTypes.value.length > 0 &&
      JSON.stringify(detailQueryKey.value) !== JSON.stringify(allDetailQueryKey.value)
    )
      void detailQuery.refetch()
  }

  return {
    rankingQuery,
    rankingState,
    rankingVisible,
    openRanking,
    setRankingPage,
    retryRanking,
    query,
    allDetailQuery,
    retryDetail,
    detailQuery,
    scope,
    scopeReady,
    overviewData,
    detailData,
    overviewState,
    detailState,
    selectedSubjectId,
    range,
    selectedTypes,
    openSubject,
    closeSubject,
    setRange,
    setTypes,
    queryKey,
    detailQueryKey,
  }
}
