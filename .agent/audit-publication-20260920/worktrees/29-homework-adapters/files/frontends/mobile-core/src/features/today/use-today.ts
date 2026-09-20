import { computed, toValue, type MaybeRefOrGetter, type Ref } from 'vue'
import { useMutation, useQuery, useQueryClient } from '@tanstack/vue-query'
import { applyCheckinAck } from '../../domain/checkin'
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

export type TodayQueryKey = readonly [
  'student',
  'today',
  string | null,
  string | null,
  string | null,
  string | null,
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
) {
  const queryClient = useQueryClient()
  const scope = computed(() => normalizeTodayScope(toValue(scopeInput)))
  const queryKey = computed(() => todayQueryKey(scope.value))

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
    enabled: computed(() => enabled.value && !offline.value),
    retry: 1,
  })

  const mutation = useMutation<StudentCheckinAck, unknown, TodayCheckinInput>({
    mutationFn: async (input) => {
      const requestScope = normalizeTodayScope(input.scope ?? scope.value)
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

  return { query, mutation, scope, queryKey }
}
