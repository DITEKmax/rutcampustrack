import { HeadmanRequestsApiError, type HeadmanRequestDetail, type HeadmanRequestsApi } from './headman-requests-client'

export type RequestDecision = 'APPROVED' | 'REJECTED'
export interface FrozenRequestDecision {
  readonly actor: HeadmanRequestsApi
  readonly id: string
  readonly decision: RequestDecision
  readonly reason: string | null
}
export type DecisionOutcome = { status: 'done' | 'conflict'; detail: HeadmanRequestDetail } | { status: 'unknown'; cause: unknown } | { status: 'rejected'; cause: unknown } | { status: 'blocked' }

/** The API instance is generation-bound to one authenticated actor. */
export function freezeRequestDecision(actor: HeadmanRequestsApi, detail: HeadmanRequestDetail, decision: RequestDecision, reason: string): FrozenRequestDecision {
  if (detail.summary.status !== 'PENDING') throw new Error('Заявка уже обработана.')
  const comment = decision === 'REJECTED' ? reason.trim() : null
  if (decision === 'REJECTED' && (!comment || comment.length > 300)) throw new Error('Укажи причину отклонения, не длиннее 300 символов.')
  return Object.freeze({ actor, id: detail.summary.id, decision, reason: comment })
}

export async function reconcileRequestDecision(pending: FrozenRequestDecision): Promise<DecisionOutcome | null> {
  try {
    const detail = await pending.actor.get(pending.id)
    if (detail.summary.id !== pending.id) throw new Error('Сервер вернул другую заявку.')
    if (detail.summary.status === 'PENDING') return null
    // GET cannot prove this actor committed the decision; only its authenticated POST can.
    return { status: 'conflict', detail }
  } catch (cause) { return { status: 'unknown', cause } }
}

export async function sendRequestDecision(pending: FrozenRequestDecision, replay: boolean, isWriteCurrent: () => boolean): Promise<DecisionOutcome> {
  if (!isWriteCurrent()) return { status: 'blocked' }
  if (replay) {
    const outcome = await reconcileRequestDecision(pending)
    if (outcome) return outcome
  }
  try {
    // Reconciliation is asynchronous. Recheck mounted owner, revision and access at the write boundary.
    if (!isWriteCurrent()) return { status: 'blocked' }
    const detail = await pending.actor.decide(pending.id, pending.decision, pending.reason)
    if (detail.summary.id !== pending.id || detail.summary.status !== pending.decision || detail.summary.decisionComment !== pending.reason) {
      return { status: 'unknown', cause: new Error('Сервер не подтвердил отправленное решение. Проверь результат.') }
    }
    return { status: 'done', detail }
  } catch (cause) {
    if (cause instanceof HeadmanRequestsApiError && cause.response.status === 409) {
      return await reconcileRequestDecision(pending) ?? { status: 'rejected', cause }
    }
    // A server/network failure may happen after commit. Never change or discard that payload.
    if (!(cause instanceof HeadmanRequestsApiError) || cause.response.status >= 500 || [408, 429].includes(cause.response.status)) return { status: 'unknown', cause }
    return { status: 'rejected', cause }
  }
}
