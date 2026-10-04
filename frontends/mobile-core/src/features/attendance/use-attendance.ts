import { computed, toValue, type MaybeRefOrGetter } from 'vue'
import { useQuery } from '@tanstack/vue-query'
import { StudentApiError, type StudentApi } from '../../api/student-client'
import type {
  StudentAttendanceDay,
  StudentAttendanceResponse,
  StudentAttendanceSubject,
  StudentAttendanceTypeCard,
  StudentAttendanceLesson,
  StudentAttendanceSeriesPoint,
} from '../../api/types'
import { studentFeatureScopeIdentity, type StudentFeatureScope } from '../../shared/session-owner'
import type { AttendanceReadState, AttendanceViewModel } from './attendance-view-model'

export type StudentAttendanceScopeInput = MaybeRefOrGetter<StudentFeatureScope | null | undefined>

export class AttendanceScopeError extends Error {
  constructor() {
    super('Attendance scope is not ready')
    this.name = 'AttendanceScopeError'
  }
}

export class AttendanceStaleResponseError extends Error {
  constructor() {
    super('Attendance response belongs to an earlier identity')
    this.name = 'AttendanceStaleResponseError'
  }
}

export type AttendanceQueryKey = readonly [
  'student',
  'attendance',
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

export function normalizeAttendanceScope(scope: StudentFeatureScope | null | undefined): StudentFeatureScope {
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

export function isAttendanceScopeReady(scope: StudentFeatureScope | null | undefined): scope is StudentFeatureScope {
  if (!scope) return false
  return scope.activeRole === 'STUDENT'
    && typeof scope.userId === 'string' && scope.userId.length > 0
    && typeof scope.semesterId === 'string' && scope.semesterId.length > 0
    && Number.isInteger(scope.resetGeneration) && scope.resetGeneration >= 0
}

export function attendanceQueryKey(scope: StudentFeatureScope | null | undefined): AttendanceQueryKey {
  const value = normalizeAttendanceScope(scope)
  return [
    'student',
    'attendance',
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

function mapLesson(lesson: StudentAttendanceLesson): AttendanceViewModel['days'][number]['lessons'][number] {
  return {
    id: lesson.id,
    date: lesson.date,
    number: lesson.number,
    subject: { ...lesson.subject },
    type: lesson.type,
    schedule: { ...lesson.schedule },
    status: lesson.status,
    requestOptions: lesson.requestOptions.map((option) => ({ ...option })),
  }
}

function mapDay(day: StudentAttendanceDay): AttendanceViewModel['days'][number] {
  return {
    date: day.date,
    weekday: day.weekday,
    dayNumber: day.dayNumber,
    state: day.state,
    lessons: day.lessons.map(mapLesson),
  }
}

function mapTypeCard(card: StudentAttendanceTypeCard): AttendanceViewModel['subjects'][number]['typeCards'][number] {
  return {
    type: card.type,
    metrics: { ...card.metrics },
    history: card.history.map((segment) => ({ ...segment })),
  }
}

function mapSubject(subject: StudentAttendanceSubject): AttendanceViewModel['subjects'][number] {
  return {
    id: subject.id,
    name: subject.name,
    typeCards: subject.typeCards.map(mapTypeCard),
  }
}

function mapPoint(point: StudentAttendanceSeriesPoint): AttendanceViewModel['graph']['days'][number] {
  return {
    id: point.id,
    label: point.label,
    dateFrom: point.dateFrom,
    dateTo: point.dateTo,
    state: point.state,
    metrics: { ...point.metrics },
  }
}

export function toAttendanceViewModel(value: StudentAttendanceResponse): AttendanceViewModel {
  return {
    semester: { ...value.semester },
    serverNow: value.serverNow,
    metrics: { ...value.metrics },
    days: value.days.map(mapDay),
    subjects: value.subjects.map(mapSubject),
    graph: {
      days: value.graph.days.map(mapPoint),
      weeks: value.graph.weeks.map(mapPoint),
    },
  }
}

function hasAttendanceProjection(value: AttendanceViewModel): boolean {
  return value.metrics.planned > 0
    || value.days.some((day) => day.lessons.length > 0)
    || value.subjects.length > 0
    || value.graph.days.length > 0
    || value.graph.weeks.length > 0
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
    message: error instanceof Error && error.message ? error.message : 'Не удалось получить посещаемость.',
    retryable: true,
  }
}

function shouldRetry(failureCount: number, error: unknown): boolean {
  if (failureCount >= 1) return false
  return !(error instanceof StudentApiError && error.response.status >= 400 && error.response.status < 500)
}

function errorState(error: unknown): Extract<AttendanceReadState<never>, { status: 'error' | 'forbidden' }> {
  if (error instanceof StudentApiError && error.response.status === 403) {
    return {
      status: 'forbidden',
      reason: error.problem?.detail ?? 'У тебя нет доступа к посещаемости.',
    }
  }
  const details = readErrorDetails(error)
  return { status: 'error', ...details }
}

/** One query owner for the signed student attendance projection. */
export function useAttendance(
  api: StudentApi,
  scopeInput: StudentAttendanceScopeInput,
  offlineInput: MaybeRefOrGetter<boolean>,
) {
  const scope = computed(() => normalizeAttendanceScope(toValue(scopeInput)))
  const scopeReady = computed(() => isAttendanceScopeReady(toValue(scopeInput)))
  const offline = computed(() => Boolean(toValue(offlineInput)))
  const queryKey = computed(() => attendanceQueryKey(scope.value))

  const query = useQuery<StudentAttendanceResponse>({
    queryKey,
    queryFn: async ({ signal }) => {
      const requestScope = scope.value
      if (!isAttendanceScopeReady(requestScope)) throw new AttendanceScopeError()
      const response = await api.getAttendance(requestScope.semesterId!, signal)
      if (studentFeatureScopeIdentity(scope.value) !== studentFeatureScopeIdentity(requestScope)) {
        throw new AttendanceStaleResponseError()
      }
      return response
    },
    enabled: computed(() => scopeReady.value && !offline.value),
    retry: shouldRetry,
  })

  const data = computed(() => query.data.value ? toAttendanceViewModel(query.data.value) : null)
  const terminalReadOnly = computed(() => query.data.value?.terminalReadOnly === true)
  const state = computed<AttendanceReadState<AttendanceViewModel>>(() => {
    if (offline.value) return { status: 'offline' }
    if (!scope.value.semesterId) return { status: 'no-semester' }
    if (query.error.value) return errorState(query.error.value)
    if (data.value) return hasAttendanceProjection(data.value)
      ? { status: 'ready', data: data.value }
      : { status: 'empty' }
    if (query.isPending.value || !scopeReady.value) return { status: 'loading' }
    return { status: 'empty' }
  })

  return { query, scope, scopeReady, data, state, terminalReadOnly, queryKey }
}
