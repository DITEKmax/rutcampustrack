import type {
  RequestBudget,
  RequestFileRef,
  RequestLesson,
  RequestLessonOption,
  RequestKind,
  RequestPendingRef,
  RequestStatus,
  RequestSummary,
  RequestsDraft,
  RequestsDraftPatch,
} from './types'
import type { StudentFeatureScope } from '../../shared/session-owner'

export const defaultRequestsDraft = (ownerId: string, sessionGeneration: string): RequestsDraft => ({
  ownerId,
  sessionGeneration,
  bucket: 'open',
  view: 'inbox',
  excuseLessonIds: [],
  excuseReason: null,
  excuseComment: '',
  excuseFiles: [],
  lateLessonId: null,
})

const drafts = new Map<string, RequestsDraft>()

function draftKey(ownerId: string, sessionGeneration: string): string {
  return ownerId + '\u0000' + sessionGeneration
}

function cloneFileRef(file: RequestFileRef): RequestFileRef {
  return { ...file }
}

export function cloneRequestsDraft(draft: RequestsDraft): RequestsDraft {
  return {
    ...draft,
    excuseLessonIds: [...draft.excuseLessonIds],
    excuseFiles: draft.excuseFiles.map(cloneFileRef),
  }
}

export function readRequestsDraft(ownerId: string, sessionGeneration: string): RequestsDraft | null {
  const draft = drafts.get(draftKey(ownerId, sessionGeneration))
  return draft ? cloneRequestsDraft(draft) : null
}

export function getOrCreateRequestsDraft(ownerId: string, sessionGeneration: string): RequestsDraft {
  const key = draftKey(ownerId, sessionGeneration)
  const current = drafts.get(key)
  if (current) return cloneRequestsDraft(current)
  const draft = defaultRequestsDraft(ownerId, sessionGeneration)
  drafts.set(key, draft)
  return cloneRequestsDraft(draft)
}

export function updateRequestsDraft(
  ownerId: string,
  sessionGeneration: string,
  patch: RequestsDraftPatch,
): RequestsDraft {
  const current = getOrCreateRequestsDraft(ownerId, sessionGeneration)
  const next: RequestsDraft = {
    ...current,
    ...patch,
    ownerId,
    sessionGeneration,
    excuseLessonIds: patch.excuseLessonIds ? [...patch.excuseLessonIds] : [...current.excuseLessonIds],
    excuseFiles: patch.excuseFiles ? patch.excuseFiles.map(cloneFileRef) : current.excuseFiles.map(cloneFileRef),
  }
  drafts.set(draftKey(ownerId, sessionGeneration), next)
  return cloneRequestsDraft(next)
}

/** Clears this session's navigation/form state, including in-memory File refs. */
export function clearRequestsDraft(ownerId: string, sessionGeneration: string): void {
  drafts.delete(draftKey(ownerId, sessionGeneration))
}

/** Owner/session/logout boundary: purge all drafts for an owner or the whole feature. */
export function purgeRequestsDrafts(ownerId?: string, sessionGeneration?: string): void {
  if (ownerId === undefined) {
    drafts.clear()
    return
  }
  if (sessionGeneration !== undefined) {
    clearRequestsDraft(ownerId, sessionGeneration)
    return
  }
  for (const key of drafts.keys()) {
    if (key.startsWith(ownerId + '\u0000')) drafts.delete(key)
  }
}

export function requestKindLabel(kind: RequestKind): string {
  return kind === 'EXCUSE' ? 'Уважительная причина' : kind === 'LATE_CHECKIN' ? 'Забыл отметиться' : 'Заявка'
}

export function requestStatusLabel(status: RequestStatus): string {
  switch (status) {
    case 'PENDING': return 'На рассмотрении'
    case 'APPROVED': return 'Одобрена'
    case 'REJECTED': return 'Отклонена'
    case 'CANCELLED': return 'Отменена'
    default: return 'Статус не указан'
  }
}

export function requestStatusTone(status: RequestStatus): 'warning' | 'success' | 'danger' | 'muted' {
  switch (status) {
    case 'PENDING': return 'warning'
    case 'APPROVED': return 'success'
    case 'REJECTED': return 'danger'
    default: return 'muted'
  }
}

export function lessonTypeLabel(type: string | null | undefined): string {
  switch (type?.toUpperCase()) {
    case 'LECTURE': return 'Лекция'
    case 'PRACTICE': return 'Практика'
    case 'LAB': return 'Лабораторная'
    default: return type || 'Тип занятия не указан'
  }
}

export function formatRequestDate(value: string | null | undefined, prefix = 'Подана'): string {
  if (!value) return 'Дата подачи не указана'
  const date = new Date(value)
  if (Number.isNaN(date.getTime())) return 'Дата подачи не указана'
  const formatted = date.toLocaleDateString('ru-RU', { timeZone: 'Europe/Moscow', day: 'numeric', month: 'long' })
  return prefix + ' ' + formatted
}

export function formatDecisionDate(value: string | null | undefined): string {
  if (!value) return 'Решение принято'
  const date = new Date(value)
  if (Number.isNaN(date.getTime())) return 'Решение принято'
  return 'Решение принято ' + date.toLocaleDateString('ru-RU', { timeZone: 'Europe/Moscow', day: 'numeric', month: 'long' })
}

export function formatLessonDate(value: string | null | undefined): string {
  if (!value) return 'Дата не указана'
  const date = new Date(value + 'T12:00:00Z')
  if (Number.isNaN(date.getTime())) return 'Дата не указана'
  return date.toLocaleDateString('ru-RU', { timeZone: 'Europe/Moscow', day: 'numeric', month: 'long' })
}

export function formatLessonTime(lesson: RequestLesson): string {
  const startsAt = lesson.startsAt?.slice(0, 5)
  const endsAt = lesson.endsAt?.slice(0, 5)
  return startsAt && endsAt ? startsAt + '–' + endsAt : startsAt || endsAt || 'Время не указано'
}

export function requestTime(value: string | null | undefined): string {
  if (!value) return ''
  const date = new Date(value)
  return Number.isNaN(date.getTime()) ? '' : date.toLocaleTimeString('ru-RU', { timeZone: 'Europe/Moscow', hour: '2-digit', minute: '2-digit' })
}

/** Server eligibility is authoritative; a pending request only fences its own kind.
 * Missing/unknown pending kinds remain blocking for either manual submission. */
export function canRequestLesson(option: RequestLessonOption | null | undefined, kind: RequestKind): boolean {
  if (!option || (kind === 'EXCUSE' ? option.excuseEligible : option.lateCheckinEligible) !== true) return false
  const otherKind = kind === 'EXCUSE' ? 'LATE_CHECKIN' : 'EXCUSE'
  return !(option.pendingRequests ?? []).some((pending) => pending.kind !== otherKind)
}

export function pendingReason(pending: readonly RequestPendingRef[] | null | undefined): string | null {
  return pending && pending.length > 0 ? 'Есть активная заявка по этой паре' : null
}

export function budgetLabel(budget: RequestBudget | null | undefined): string {
  if (!budget || budget.remaining === null || budget.remaining === undefined || budget.limit === null || budget.limit === undefined) {
    return 'Лимит попыток не указан'
  }
  return budget.remaining + ' из ' + budget.limit
}

export function selectedLessonOptions(
  options: readonly RequestLessonOption[] | null | undefined,
  ids: readonly string[],
): RequestLessonOption[] {
  const selected = new Set(ids)
  return (options ?? []).filter((option) => option.lesson?.id && selected.has(option.lesson.id))
}

/** Apply a controlled checkbox change while preserving the remaining selection order. */
export function updateSelectedLessonIds(
  selectedIds: readonly string[],
  id: string,
  selected: boolean,
): string[] {
  const next = new Set(selectedIds)
  if (selected) next.add(id)
  else next.delete(id)
  return [...next]
}

/** Remove one stale or unavailable lesson from a retained draft selection. */
export function removeSelectedLessonId(selectedIds: readonly string[], id: string): string[] {
  return updateSelectedLessonIds(selectedIds, id, false)
}

/**
 * Cancellation is a local affordance projection. The BFF still owns the
 * final decision and may reject a request that changed between reads.
 */
export function canCancelRequest(
  summary: Pick<RequestSummary, 'id' | 'kind' | 'status'>,
  scope: StudentFeatureScope | null,
  context: { offline: boolean; readOnly: boolean },
): boolean {
  return Boolean(summary.id)
    && summary.status === 'PENDING'
    && (summary.kind === 'EXCUSE' || summary.kind === 'LATE_CHECKIN')
    && scope?.activeRole === 'STUDENT'
    && Boolean(scope.userId && scope.sessionId)
    && !scope.readOnly
    && !context.offline
    && !context.readOnly
}

export function requestsSessionGeneration(scope: StudentFeatureScope): string {
  return JSON.stringify([
    scope.resetGeneration,
    scope.sessionId,
    scope.sessionVersion,
    scope.rolesVersion,
  ])
}

/** Retained selections fail closed until the user explicitly removes invalid IDs. */
export function requestSelectionEligible(options: readonly RequestLessonOption[], ids: readonly string[], kind: RequestKind): boolean {
  return ids.length > 0 && new Set(ids).size === ids.length && (kind !== 'LATE_CHECKIN' || ids.length === 1)
    && ids.every((id) => options.some((option) => option.lesson?.id === id && canRequestLesson(option, kind)))
}
