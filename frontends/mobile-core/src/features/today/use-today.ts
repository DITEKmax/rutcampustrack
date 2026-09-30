import { computed, ref, toValue, watch, type MaybeRefOrGetter, type Ref } from 'vue'
import { useMutation, useQuery, useQueryClient } from '@tanstack/vue-query'
import { applyCheckinAck, isPendingCheckin } from '../../domain/checkin'
import type { StudentApi } from '../../api/student-client'
import type { StudentCheckinAck, StudentCheckinCommand, StudentToday } from '../../api/types'
import { studentFeatureScopeIdentity, type StudentFeatureScope } from '../../shared/session-owner'

export type StudentTodayQueryScope = StudentFeatureScope
export type StudentTodayScopeInput = MaybeRefOrGetter<StudentTodayQueryScope | null | undefined>

export class TodayStaleResponseError extends Error {
  constructor() {
    super('Today response belongs to an earlier identity')
    this.name = 'TodayStaleResponseError'
  }
}

export class TodayReadOnlyError extends Error {
  constructor() {
    super('Отметка посещаемости недоступна в режиме только чтения')
    this.name = 'TodayReadOnlyError'
  }
}

export type TodayQueryKey = readonly [
  'student',
  'today',
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

export interface TodayCheckinInput {
  lessonId: string
  command: StudentCheckinCommand
  key: string
  scope?: StudentTodayQueryScope
}

export function normalizeTodayScope(scope: StudentTodayQueryScope | null | undefined): StudentTodayQueryScope {
  return {
    userId: scope?.userId ?? null,
    activeRole: scope?.activeRole ?? null,
    groupId: scope?.groupId ?? null,
    semesterId: scope?.semesterId ?? null,
    sessionId: scope?.sessionId ?? null,
    sessionVersion: scope?.sessionVersion ?? null,
    rolesVersion: scope?.rolesVersion ?? null,
    readOnly: scope?.readOnly ?? false,
    resetGeneration: Number.isInteger(scope?.resetGeneration) && (scope?.resetGeneration ?? 0) >= 0
      ? scope?.resetGeneration ?? 0
      : 0,
  }
}

export function todayQueryKey(scope: StudentTodayQueryScope | null | undefined): TodayQueryKey {
  const value = normalizeTodayScope(scope)
  return [
    'student',
    'today',
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

export function todayScopeQueryPrefix(scope: StudentTodayQueryScope | null | undefined): readonly unknown[] {
  return todayQueryKey(scope)
}

export function useToday(
  api: StudentApi,
  enabled: Ref<boolean>,
  offline: Ref<boolean>,
  scopeInput?: StudentTodayScopeInput,
  activeToday: Ref<boolean> = ref(true),
) {
  const queryClient = useQueryClient()
  const scope = computed(() => normalizeTodayScope(toValue(scopeInput)))
  const queryKey = computed(() => todayQueryKey(scope.value))
  const hasPendingCheckin = ref(false)

  const query = useQuery<StudentToday>({
    queryKey,
    queryFn: async () => {
      const requestScope = scope.value
      const data = await api.getToday()
      if (studentFeatureScopeIdentity(scope.value) !== studentFeatureScopeIdentity(requestScope)) {
        throw new TodayStaleResponseError()
      }
      return data
    },
    // Readonly authority may still read Today. Mutations have their own
    // guards in the owner and mutation boundary.
    enabled: computed(() => enabled.value && !offline.value),
    refetchInterval: computed(() => enabled.value
      && !offline.value
      && activeToday.value
      && hasPendingCheckin.value
      ? 15_000
      : false),
    refetchIntervalInBackground: false,
    retry: 1,
  })

  watch(() => query.data.value, (data) => {
    hasPendingCheckin.value = data?.lessons.some(isPendingCheckin) ?? false
  }, { immediate: true })

  watch(activeToday, (active, wasActive) => {
    if (active && !wasActive && enabled.value && !offline.value) void query.refetch()
  })

  async function refresh(expectedScope: StudentTodayQueryScope | null | undefined = scope.value): Promise<void> {
    const requestScope = normalizeTodayScope(expectedScope)
    if (!enabled.value
      || offline.value
      || studentFeatureScopeIdentity(scope.value) !== studentFeatureScopeIdentity(requestScope)) return
    await query.refetch()
  }

  const mutation = useMutation<StudentCheckinAck, unknown, TodayCheckinInput>({
    mutationFn: async (input) => {
      const requestScope = normalizeTodayScope(input.scope ?? scope.value)
      if (requestScope.readOnly) throw new TodayReadOnlyError()
      if (studentFeatureScopeIdentity(scope.value) !== studentFeatureScopeIdentity(requestScope)) {
        throw new TodayStaleResponseError()
      }
      const ack = await api.checkin(input.lessonId, input.command, input.key)
      if (studentFeatureScopeIdentity(scope.value) !== studentFeatureScopeIdentity(requestScope)) {
        throw new TodayStaleResponseError()
      }
      return ack
    },
    onSuccess: (ack: StudentCheckinAck, input) => {
      const ownerScope = normalizeTodayScope(input.scope ?? scope.value)
      if (studentFeatureScopeIdentity(scope.value) !== studentFeatureScopeIdentity(ownerScope)) return
      queryClient.setQueryData<StudentToday>(todayQueryKey(ownerScope), (current) => current ? applyCheckinAck(current, ack) : current)
      void queryClient.invalidateQueries({ queryKey: todayScopeQueryPrefix(ownerScope) })
    },
  })

  return { query, mutation, scope, queryKey, refresh }
}
