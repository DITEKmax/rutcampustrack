import {
  HeadmanRequestsApiError,
  type HeadmanRequestDetail,
  type HeadmanRequestsApi,
  type HeadmanRequestDecisionInput,
} from './headman-requests-client'

export type RequestDecision = 'APPROVED' | 'REJECTED'
export interface FrozenRequestDecision {
  readonly actor: HeadmanRequestsApi
  readonly id: string
  readonly decision: RequestDecision
  readonly reason: string | null
  readonly selection?: HeadmanRequestDecisionInput | undefined
  readonly includedOccurrenceIds?: readonly string[] | undefined
}
export type DecisionOutcome =
  | { status: 'done' | 'conflict'; detail: HeadmanRequestDetail }
  | { status: 'unknown'; cause: unknown }
  | { status: 'rejected'; cause: unknown }
  | { status: 'blocked' }

/** Local draft only: never creates a key or changes server/original lesson evidence. */
export function updateRequestExclusions(
  detail: HeadmanRequestDetail,
  current: readonly string[],
  occurrenceId: string,
  exclude: boolean,
): readonly string[] {
  const lessons = detail.currentAffectedLessons
  if (
    detail.summary.status !== 'PENDING' ||
    detail.summary.kind !== 'EXCUSE' ||
    !detail.decisionRevision ||
    !lessons?.length ||
    lessons.some((lesson) => !lesson.occurrenceId || !lesson.occurrenceRevision)
  )
    throw new Error('Обнови заявку перед выбором пар.')
  const ids = lessons.map((lesson) => lesson.occurrenceId!)
  if (
    new Set(ids).size !== ids.length ||
    new Set(current).size !== current.length ||
    current.some((id) => !ids.includes(id)) ||
    !ids.includes(occurrenceId)
  )
    throw new Error('Состав пар изменился. Обнови заявку и выбери пары заново.')
  const next = exclude
    ? current.includes(occurrenceId)
      ? [...current]
      : [...current, occurrenceId]
    : current.filter((id) => id !== occurrenceId)
  if (next.length >= ids.length) throw new Error('Оставь хотя бы одну пару для одобрения.')
  return Object.freeze(next)
}

/** The API instance is generation-bound to one authenticated actor. */
export function freezeRequestDecision(
  actor: HeadmanRequestsApi,
  detail: HeadmanRequestDetail,
  decision: RequestDecision,
  reason: string,
  excludedOccurrenceIds: readonly string[] = [],
): FrozenRequestDecision {
  if (detail.summary.status !== 'PENDING') throw new Error('Заявка уже обработана.')
  const comment = decision === 'REJECTED' ? reason.trim() : null
  if (decision === 'REJECTED' && (!comment || comment.length > 300))
    throw new Error('Укажи причину отклонения, не длиннее 300 символов.')
  if (excludedOccurrenceIds.length && (decision !== 'APPROVED' || detail.summary.kind !== 'EXCUSE'))
    throw new Error('Эту заявку нельзя одобрить по части пар.')
  let selection: HeadmanRequestDecisionInput | undefined
  let includedOccurrenceIds: readonly string[] | undefined
  if (detail.summary.kind === 'EXCUSE') {
    const targets = detail.currentAffectedLessons
    if (
      !detail.decisionRevision ||
      !targets?.length ||
      targets.some((lesson) => !lesson.occurrenceId || !lesson.occurrenceRevision)
    )
      throw new Error('Обнови заявку: сервер должен подтвердить текущий состав и версию решения.')
    const ids = targets.map((lesson) => lesson.occurrenceId!)
    if (
      new Set(ids).size !== ids.length ||
      new Set(excludedOccurrenceIds).size !== excludedOccurrenceIds.length ||
      excludedOccurrenceIds.some((id) => !ids.includes(id))
    )
      throw new Error('Состав пар изменился. Обнови заявку и выбери пары заново.')
    const excluded = [...excludedOccurrenceIds].sort(
      (a, b) => a.length - b.length || a.localeCompare(b),
    )
    includedOccurrenceIds = Object.freeze(ids.filter((id) => !excluded.includes(id)))
    if (decision === 'APPROVED' && !includedOccurrenceIds.length)
      throw new Error('Оставь хотя бы одну пару для одобрения.')
    selection = Object.freeze({
      expectedRevision: detail.decisionRevision,
      excludedOccurrenceIds: Object.freeze(excluded),
      requestKey: crypto.randomUUID(),
    })
  }
  return Object.freeze({
    actor,
    id: detail.summary.id,
    decision,
    reason: comment,
    selection,
    includedOccurrenceIds,
  })
}

export async function reconcileRequestDecision(
  pending: FrozenRequestDecision,
): Promise<DecisionOutcome | null> {
  try {
    const detail = await pending.actor.get(pending.id)
    if (detail.summary.id !== pending.id) throw new Error('Сервер вернул другую заявку.')
    if (detail.summary.status === 'PENDING') return null
    // The immutable receipt proves the exact selected intent after a lost response.
    return { status: matchesDecisionReceipt(pending, detail) ? 'done' : 'conflict', detail }
  } catch (cause) {
    return { status: 'unknown', cause }
  }
}

export async function sendRequestDecision(
  pending: FrozenRequestDecision,
  replay: boolean,
  isWriteCurrent: () => boolean,
): Promise<DecisionOutcome> {
  if (!isWriteCurrent()) return { status: 'blocked' }
  if (replay) {
    const outcome = await reconcileRequestDecision(pending)
    if (outcome) return outcome
  }
  try {
    // Reconciliation is asynchronous. Recheck mounted owner, revision and access at the write boundary.
    if (!isWriteCurrent()) return { status: 'blocked' }
    const detail = await pending.actor.decide(
      pending.id,
      pending.decision,
      pending.reason,
      pending.selection,
    )
    if (
      detail.summary.id !== pending.id ||
      detail.summary.status !== pending.decision ||
      detail.summary.decisionComment !== pending.reason ||
      (pending.selection && !matchesDecisionReceipt(pending, detail))
    ) {
      return {
        status: 'unknown',
        cause: new Error('Сервер не подтвердил отправленное решение. Проверь результат.'),
      }
    }
    return { status: 'done', detail }
  } catch (cause) {
    if (cause instanceof HeadmanRequestsApiError && cause.response.status === 409) {
      return (await reconcileRequestDecision(pending)) ?? { status: 'rejected', cause }
    }
    // A server/network failure may happen after commit. Never change or discard that payload.
    if (
      !(cause instanceof HeadmanRequestsApiError) ||
      cause.response.status >= 500 ||
      [408, 429].includes(cause.response.status)
    )
      return { status: 'unknown', cause }
    return { status: 'rejected', cause }
  }
}

function matchesDecisionReceipt(
  pending: FrozenRequestDecision,
  detail: HeadmanRequestDetail,
): boolean {
  const receipt = detail.decisionSelection,
    intent = pending.selection
  if (
    !intent ||
    !receipt ||
    detail.summary.status !== pending.decision ||
    detail.summary.decisionComment !== pending.reason ||
    receipt.requestKey !== intent.requestKey ||
    receipt.expectedRevision !== intent.expectedRevision
  )
    return false
  const same = (actual: readonly (string | null | undefined)[], expected: readonly string[]) =>
    actual.length === expected.length &&
    actual.every((id) => !!id && expected.includes(id)) &&
    new Set(actual).size === actual.length
  return (
    same(
      receipt.excludedLessons.map((lesson) => lesson.occurrenceId),
      intent.excludedOccurrenceIds,
    ) &&
    same(
      receipt.includedLessons.map((lesson) => lesson.occurrenceId),
      pending.includedOccurrenceIds ?? [],
    )
  )
}
