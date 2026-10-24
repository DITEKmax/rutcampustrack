import { describe, expect, it, vi } from 'vitest'
import { HeadmanRequestsApi } from '../../../../mobile-core/src/features/headman-requests/headman-requests-client'
import {
  reconcileRequestDecision,
  type FrozenRequestDecision,
} from '../../../../mobile-core/src/features/headman-requests/request-decision'
import { restoreRequestDecision, serializeRequestDecision } from './request-decision-state'

const key = 'd6ab481d-8b40-4220-bb88-b2aee1ce495a'
const original = new HeadmanRequestsApi({ accessToken: () => null })
const pending: FrozenRequestDecision = {
  actor: original,
  id: 'ticket-1',
  decision: 'REJECTED',
  reason: 'Причина отказа',
  selection: { requestKey: key, expectedRevision: 'a'.repeat(64), excludedOccurrenceIds: [] },
  includedOccurrenceIds: ['44'],
}
function terminal(requestKey = key) {
  return {
    summary: {
      id: 'ticket-1',
      kind: 'EXCUSE',
      status: 'REJECTED',
      lessonCount: 1,
      alreadyMarkedCount: 0,
      decisionComment: pending.reason,
    },
    lessons: [],
    attachments: [],
    currentAffectedLessons: null,
    decisionRevision: null,
    decisionSelection: {
      requestKey,
      expectedRevision: pending.selection!.expectedRevision,
      includedLessons: [{ lessonId: 63, occurrenceId: '44', occurrenceRevision: '1' }],
      excludedLessons: [],
    },
  }
}
describe('durable request decision readback', () => {
  it('restores the original key into the new mounted API and resolves using GET only', async () => {
    const fetcher = vi.fn<typeof fetch>().mockResolvedValue(Response.json(terminal()))
    const current = new HeadmanRequestsApi({ accessToken: () => null, fetcher })
    const saved = serializeRequestDecision(pending)
    expect(JSON.parse(saved)).not.toHaveProperty('actor')
    const restored = restoreRequestDecision(saved, current)
    expect(restored.actor).toBe(current)
    expect(restored.selection?.requestKey).toBe(key)
    expect((await reconcileRequestDecision(restored))?.status).toBe('done')
    expect(fetcher).toHaveBeenCalledTimes(1)
    expect(fetcher.mock.calls[0]?.[0]).toBe('/api/attendance/requests/ticket-1')
    expect(fetcher.mock.calls[0]?.[1]?.method ?? 'GET').toBe('GET')
  })
  it('keeps a different command receipt distinct even when status and comment match', async () => {
    const current = new HeadmanRequestsApi({
      accessToken: () => null,
      fetcher: async () => Response.json(terminal('another-command-key')),
    })
    const restored = restoreRequestDecision(serializeRequestDecision(pending), current)
    expect((await reconcileRequestDecision(restored))?.status).toBe('conflict')
  })
  it('rejects overlapping or malformed saved selection instead of changing its payload', () => {
    const saved = JSON.parse(serializeRequestDecision(pending))
    saved.selection.excludedOccurrenceIds = ['44']
    expect(() => restoreRequestDecision(JSON.stringify(saved), original)).toThrow(
      'исходное решение',
    )
    saved.selection.excludedOccurrenceIds = []
    saved.selection.expectedRevision = 'unknown'
    expect(() => restoreRequestDecision(JSON.stringify(saved), original)).toThrow(
      'исходное решение',
    )
  })
})
