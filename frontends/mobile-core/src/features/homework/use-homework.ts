import { computed, nextTick, reactive, ref, toValue, watch, type MaybeRefOrGetter } from 'vue'
import { useMutation, useQuery, useQueryClient } from '@tanstack/vue-query'
import type { StudentApi } from '../../api/student-client'
import type { StudentHomework, StudentHomeworkCompletion } from '../../api/types'
import {
  homeworkRangeKey,
  normalizeHomeworkRange,
  previousHomeworkRange,
  type HomeworkDateRange,
} from '../../domain/homework'

export interface StudentHomeworkQueryScope {
  /** Cache identity only; the bearer/session remains owned by the adapter. */
  userId: string | null
  activeRole: string | null
  groupId: string | null
  semesterId: string | null
  sessionId: string | null
  sessionVersion: string | null
  rolesVersion: string | null
  readOnly: boolean
  resetGeneration: number
}

export type StudentHomeworkScopeInput = MaybeRefOrGetter<StudentHomeworkQueryScope | null | undefined>

export interface HomeworkUseOptions {
  offline?: MaybeRefOrGetter<boolean>
  readOnly?: MaybeRefOrGetter<boolean>
  /** Current snapshot homework used when Vue Query has no feed data yet. */
  fallback?: MaybeRefOrGetter<StudentHomework | null | undefined>
  initialRange?: HomeworkDateRange | null
  retry?: number | false
}

export interface HomeworkCompletionInput {
  id: string
  completed: boolean
  scope: StudentHomeworkQueryScope
  range: HomeworkDateRange | null
}

export interface HomeworkItemState {
  pending: boolean
  desired: boolean | null
  error: Error | null
}

export class HomeworkScopeError extends Error {
  constructor() {
    super('Homework scope is not ready')
    this.name = 'HomeworkScopeError'
  }
}

export class HomeworkReadOnlyError extends Error {
  constructor() {
    super('Изменение заданий недоступно в режиме только чтения')
    this.name = 'HomeworkReadOnlyError'
  }
}

export class HomeworkStaleResponseError extends Error {
  constructor() {
    super('Homework response belongs to an earlier identity')
    this.name = 'HomeworkStaleResponseError'
  }
}

export class HomeworkCompletionAckError extends Error {
  constructor(message: string) {
    super(message)
    this.name = 'HomeworkCompletionAckError'
  }
}

export type HomeworkQueryKey = readonly [
  'student',
  'homework',
  string | null,
  string | null,
  string | null,
  string | null,
  string | null,
  string | null,
  string | null,
  boolean,
  number,
  string,
]

export function normalizeHomeworkScope(scope: StudentHomeworkQueryScope | null | undefined): StudentHomeworkQueryScope {
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

export function homeworkScopeIdentity(scope: StudentHomeworkQueryScope | null | undefined): string {
  const value = normalizeHomeworkScope(scope)
  return JSON.stringify([
    value.userId,
    value.activeRole,
    value.groupId,
    value.semesterId,
    value.sessionId,
    value.sessionVersion,
    value.rolesVersion,
    value.readOnly,
    value.resetGeneration,
  ])
}

export function isHomeworkScopeReady(scope: StudentHomeworkQueryScope | null | undefined): scope is StudentHomeworkQueryScope {
  if (!scope) return false
  return scope.activeRole === 'STUDENT'
    && typeof scope.userId === 'string' && scope.userId.length > 0
    && typeof scope.groupId === 'string' && scope.groupId.length > 0
    && typeof scope.semesterId === 'string' && scope.semesterId.length > 0
    && Number.isInteger(scope.resetGeneration) && scope.resetGeneration >= 0
}

export function homeworkQueryKey(
  scope: StudentHomeworkQueryScope | null | undefined,
  range: HomeworkDateRange | null,
): HomeworkQueryKey {
  const value = normalizeHomeworkScope(scope)
  return [
    'student',
    'homework',
    value.userId,
    value.activeRole,
    value.groupId,
    value.semesterId,
    value.sessionId,
    value.sessionVersion,
    value.rolesVersion,
    value.readOnly,
    value.resetGeneration,
    homeworkRangeKey(range),
  ]
}

export function homeworkScopeQueryPrefix(scope: StudentHomeworkQueryScope | null | undefined): readonly unknown[] {
  const value = normalizeHomeworkScope(scope)
  return [
    'student',
    'homework',
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

/**
 * Accepts the exact desired state only when the server returned a complete,
 * self-consistent completion receipt. This keeps unknown and contradictory
 * responses from appearing successful in the UI.
 */
export function validateHomeworkCompletionAck(
  value: unknown,
  expectedId: string,
  expectedCompleted: boolean,
): StudentHomeworkCompletion {
  if (!isRecord(value)
    || typeof value.id !== 'string'
    || typeof value.completed !== 'boolean'
    || (typeof value.completedAt !== 'string' && value.completedAt !== null)) {
    throw new HomeworkCompletionAckError('Сервер вернул неполный ответ о задании')
  }
  if (value.id !== expectedId || value.completed !== expectedCompleted) {
    throw new HomeworkCompletionAckError('Сервер вернул состояние задания, отличное от запрошенного')
  }
  if (expectedCompleted && (value.completedAt === null || !Number.isFinite(Date.parse(value.completedAt)))) {
    throw new HomeworkCompletionAckError('Сервер не вернул дату выполнения задания')
  }
  if (!expectedCompleted && value.completedAt !== null) {
    throw new HomeworkCompletionAckError('Сервер вернул дату для невыполненного задания')
  }
  return {
    id: value.id,
    completed: value.completed,
    completedAt: value.completedAt,
  }
}

function isRecord(value: unknown): value is Record<string, unknown> {
  return typeof value === 'object' && value !== null
}

function optionsFrom(
  value: HomeworkUseOptions | MaybeRefOrGetter<boolean> | undefined,
  extra: Omit<HomeworkUseOptions, 'offline'> | undefined,
): HomeworkUseOptions {
  // A plain object is the options overload, including the valid empty object.
  // Refs expose `value`; getter functions are the legacy offline overload.
  if (value && typeof value === 'object' && !('value' in value)) {
    return value as HomeworkUseOptions
  }
  const options: HomeworkUseOptions = { ...extra }
  if (value !== undefined) options.offline = value as MaybeRefOrGetter<boolean>
  return options
}

/**
 * Shared response owner for the PWA and TMA homework views. Scope is explicit
 * so Vue Query never reuses one student's response for another identity.
 */
export function useHomework(
  api: StudentApi,
  scopeInput: StudentHomeworkScopeInput,
  options?: HomeworkUseOptions | MaybeRefOrGetter<boolean>,
  extraOptions?: Omit<HomeworkUseOptions, 'offline'>,
) {
  const optionsValue = optionsFrom(options, extraOptions)
  const queryClient = useQueryClient()
  const scope = computed(() => normalizeHomeworkScope(toValue(scopeInput)))
  const scopeReady = computed(() => isHomeworkScopeReady(toValue(scopeInput)))
  const offline = computed(() => Boolean(toValue(optionsValue.offline)))
  const readOnly = computed(() => Boolean(toValue(optionsValue.readOnly)))
  const fallback = computed(() => toValue(optionsValue.fallback))
  const selectedRange = ref<HomeworkDateRange | null>(normalizeHomeworkRange(optionsValue.initialRange))
  let rangeRequestRevision = 0
  const range = computed(() => selectedRange.value)
  const scopeIdentity = computed(() => homeworkScopeIdentity(scope.value))
  const queryKey = computed(() => homeworkQueryKey(scope.value, range.value))
  const itemStates = reactive<Record<string, HomeworkItemState>>({})
  const retryCommands = new Map<string, HomeworkCompletionInput>()
  const inFlight = new Map<string, Promise<StudentHomeworkCompletion>>()

  const query = useQuery<StudentHomework>({
    queryKey,
    queryFn: async () => {
      const requestScope = scope.value
      const requestRange = range.value
      if (!isHomeworkScopeReady(requestScope)) throw new HomeworkScopeError()
      const data = await api.getHomework(requestRange?.from, requestRange?.to)
      if (homeworkScopeIdentity(scope.value) !== homeworkScopeIdentity(requestScope)) {
        throw new HomeworkStaleResponseError()
      }
      return data
    },
    enabled: computed(() => scopeReady.value && !offline.value),
    retry: optionsValue.retry ?? 1,
  })

  const mutation = useMutation<StudentHomeworkCompletion, unknown, HomeworkCompletionInput>({
    mutationFn: async (input) => {
      if (readOnly.value || isArchived(input.id)) throw new HomeworkReadOnlyError()
      if (!isHomeworkScopeReady(input.scope) || homeworkScopeIdentity(scope.value) !== homeworkScopeIdentity(input.scope)) {
        throw new HomeworkStaleResponseError()
      }
      const response = await api.setHomeworkCompletion(input.id, { completed: input.completed })
      if (readOnly.value) throw new HomeworkReadOnlyError()
      if (homeworkScopeIdentity(scope.value) !== homeworkScopeIdentity(input.scope)) {
        throw new HomeworkStaleResponseError()
      }
      return validateHomeworkCompletionAck(response, input.id, input.completed)
    },
  })

  function ensureItemState(id: string): HomeworkItemState {
    const existing = itemStates[id]
    if (existing) return existing
    const state: HomeworkItemState = { pending: false, desired: null, error: null }
    itemStates[id] = state
    return state
  }

  function isPending(id: string): boolean {
    return ensureItemState(id).pending
  }

  function itemError(id: string): string | null {
    return ensureItemState(id).error?.message ?? null
  }

  function isArchived(id: string): boolean {
    return (query.data.value ?? fallback.value)?.items.some((item) => item.id === id && item.archived) ?? false
  }

  function submitCompletion(id: string, completed: boolean): Promise<StudentHomeworkCompletion> {
    if (readOnly.value || isArchived(id)) return Promise.reject(new HomeworkReadOnlyError())
    if (offline.value) return Promise.reject(new HomeworkScopeError())
    const requestScope = scope.value
    if (!isHomeworkScopeReady(requestScope)) return Promise.reject(new HomeworkScopeError())
    const existing = inFlight.get(id)
    if (existing) return existing

    const input: HomeworkCompletionInput = {
      id,
      completed,
      scope: { ...requestScope },
      range: range.value ? { ...range.value } : null,
    }
    const state = ensureItemState(id)
    state.pending = true
    state.desired = completed
    state.error = null
    retryCommands.set(id, input)

    const request = mutation.mutateAsync(input)
      .then((ack) => {
      if (homeworkScopeIdentity(scope.value) === homeworkScopeIdentity(input.scope)) {
        retryCommands.delete(id)
        const targetKey = homeworkQueryKey(input.scope, input.range)
          queryClient.setQueryData<StudentHomework>(targetKey, (current) => {
            // Snapshot fallback is only the current feed. Never seed a
            // historical range from it, and never publish after the scope
            // identity check above has detached this generation.
            const source = current ?? (input.range === null ? fallback.value : null)
            return source ? applyHomeworkAck(source, ack) : current
          })
          void queryClient.invalidateQueries({ queryKey: homeworkScopeQueryPrefix(input.scope) })
          const currentState = itemStates[id]
          if (currentState) {
            currentState.pending = false
            currentState.desired = null
            currentState.error = null
          }
        }
        return ack
      })
      .catch((error: unknown) => {
        if (homeworkScopeIdentity(scope.value) === homeworkScopeIdentity(input.scope)) {
          const currentState = ensureItemState(id)
          currentState.pending = false
          currentState.desired = null
          currentState.error = toError(error)
        }
        throw error
      })
      .finally(() => {
        if (inFlight.get(id) === request) inFlight.delete(id)
      })
    inFlight.set(id, request)
    return request
  }

  function retryCompletion(id: string): Promise<StudentHomeworkCompletion> | null {
    const input = retryCommands.get(id)
    if (!input || isPending(id)) return null
    // The scope watcher clears stale commands on its next flush, but a caller
    // can retry in the same tick as an owner change. Validate the saved command
    // before submitCompletion captures the new scope and drops the old owner.
    if (homeworkScopeIdentity(scope.value) !== homeworkScopeIdentity(input.scope)) {
      retryCommands.delete(id)
      return null
    }
    return submitCompletion(id, input.completed)
  }

  function loadPrevious(): HomeworkDateRange | null {
    const previous = previousHomeworkRange(query.data.value ?? null)
    if (!previous) return null
    rangeRequestRevision += 1
    selectedRange.value = previous
    return previous
  }

  async function openDate(date: string): Promise<StudentHomework> {
    const targetRange = normalizeHomeworkRange({ from: date, to: date })
    if (!targetRange) throw new HomeworkScopeError()
    if (!scopeReady.value || offline.value) throw new HomeworkScopeError()
    const requestIdentity = scopeIdentity.value
    const requestRevision = ++rangeRequestRevision
    selectedRange.value = targetRange
    await nextTick()
    const isCurrentRequest = (): boolean => rangeRequestRevision === requestRevision
      && scopeIdentity.value === requestIdentity
      && selectedRange.value?.from === date
      && selectedRange.value.to === date
    if (!isCurrentRequest()) {
      throw new HomeworkStaleResponseError()
    }

    // Refetch through the existing scoped query observer so notification
    // navigation uses the same API owner and cache identity as the feed.
    let result
    try {
      result = await query.refetch()
    } catch (error) {
      if (!isCurrentRequest()) throw new HomeworkStaleResponseError()
      throw error
    }
    if (!isCurrentRequest()) {
      throw new HomeworkStaleResponseError()
    }
    if (result.isError) throw result.error
    if (!result.data) throw new HomeworkScopeError()
    return result.data
  }

  function returnToToday(): void {
    rangeRequestRevision += 1
    selectedRange.value = null
  }

  watch(scopeIdentity, () => {
    // A retry command is an intent for the identity that failed. Retaining it
    // across logout/group/semester replacement could replay that intent for a
    // different student when the same homework id is reused.
    retryCommands.clear()
    rangeRequestRevision += 1
    selectedRange.value = null
    for (const state of Object.values(itemStates)) {
      state.pending = false
      state.desired = null
      state.error = null
    }
    inFlight.clear()
  })

  const canLoadPrevious = computed(() => previousHomeworkRange(query.data.value ?? null) !== null)

  return {
    query,
    mutation,
    scope,
    range,
    queryKey,
    scopeReady,
    offline,
    readOnly,
    isHistorical: computed(() => selectedRange.value !== null),
    canLoadPrevious,
    loadPrevious,
    openDate,
    returnToToday,
    submitCompletion,
    retryCompletion,
    isPending,
    itemError,
    itemStates,
  }
}

function applyHomeworkAck(feed: StudentHomework, ack: StudentHomeworkCompletion): StudentHomework {
  return {
    ...feed,
    items: feed.items.map((item) => item.id === ack.id
      ? { ...item, completed: ack.completed, completedAt: ack.completedAt }
      : item),
  }
}

function toError(value: unknown): Error {
  return value instanceof Error ? value : new Error('Не удалось обновить задание')
}
