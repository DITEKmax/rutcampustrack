import type { HeadmanRequestsApi } from '../../../../mobile-core/src/features/headman-requests/headman-requests-client'
import type { FrozenRequestDecision } from '../../../../mobile-core/src/features/headman-requests/request-decision'

export function serializeRequestDecision(pending: FrozenRequestDecision): string {
  return JSON.stringify({
    version: 1,
    id: pending.id,
    decision: pending.decision,
    reason: pending.reason,
    selection: pending.selection,
    includedOccurrenceIds: pending.includedOccurrenceIds,
  })
}

/** Restore only the original readback intent; the API belongs to this mounted authority. */
export function restoreRequestDecision(
  raw: string,
  actor: HeadmanRequestsApi,
): FrozenRequestDecision {
  const value: unknown = JSON.parse(raw)
  const invalid = () => new Error('Не удалось восстановить исходное решение по заявке.')
  if (!value || typeof value !== 'object') throw invalid()
  const row = value as Record<string, unknown>
  if (
    row.version !== 1 ||
    typeof row.id !== 'string' ||
    !/^[A-Za-z0-9_-]{1,128}$/.test(row.id) ||
    !['APPROVED', 'REJECTED'].includes(String(row.decision)) ||
    !(row.reason === null || typeof row.reason === 'string') ||
    (row.decision === 'APPROVED' && row.reason !== null) ||
    (row.decision === 'REJECTED' &&
      (typeof row.reason !== 'string' || !row.reason.trim() || row.reason.length > 300))
  )
    throw invalid()
  if (row.selection === undefined) {
    if (row.includedOccurrenceIds !== undefined) throw invalid()
    return Object.freeze({
      actor,
      id: row.id,
      decision: row.decision as 'APPROVED' | 'REJECTED',
      reason: row.reason,
    })
  }
  if (!row.selection || typeof row.selection !== 'object') throw invalid()
  const selection = row.selection as Record<string, unknown>
  const ids = (value: unknown): value is string[] =>
    Array.isArray(value) &&
    value.every((id: unknown) => typeof id === 'string' && /^[1-9][0-9]*$/.test(id)) &&
    new Set(value).size === value.length
  if (
    typeof selection.requestKey !== 'string' ||
    !/^[\x21-\x7e]{16,128}$/.test(selection.requestKey) ||
    typeof selection.expectedRevision !== 'string' ||
    !/^[a-f0-9]{64}$/.test(selection.expectedRevision) ||
    !ids(selection.excludedOccurrenceIds) ||
    !ids(row.includedOccurrenceIds)
  )
    throw invalid()
  const excluded = selection.excludedOccurrenceIds
  if (
    row.includedOccurrenceIds.some((id) => excluded.includes(id)) ||
    (row.decision === 'APPROVED' && !row.includedOccurrenceIds.length)
  )
    throw invalid()
  return Object.freeze({
    actor,
    id: row.id,
    decision: row.decision as 'APPROVED' | 'REJECTED',
    reason: row.reason,
    selection: Object.freeze({
      requestKey: selection.requestKey,
      expectedRevision: selection.expectedRevision,
      excludedOccurrenceIds: Object.freeze([...selection.excludedOccurrenceIds]),
    }),
    includedOccurrenceIds: Object.freeze([...row.includedOccurrenceIds]),
  })
}
