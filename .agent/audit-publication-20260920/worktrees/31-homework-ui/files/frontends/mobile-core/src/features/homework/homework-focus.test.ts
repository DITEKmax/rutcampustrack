import { describe, expect, it } from 'vitest'
import {
  canRestoreHomeworkFocus,
  type HomeworkFocusRequest,
} from './homework-focus'

const body = {} as Element
const completion = {} as Element
const otherControl = {} as Element

const request: HomeworkFocusRequest = {
  id: 'math',
  expectedCompleted: true,
  origin: completion,
  feedIdentity: 'semester:from:to',
  ownerKey: 'student-a:group-a:0',
}

describe('homework completion focus ownership', () => {
  it('restores the same assignment after a confirmed ACK reorder', () => {
    expect(canRestoreHomeworkFocus({
      request,
      item: { completed: true, completedAt: '2026-09-07T09:30:00Z' },
      pending: false,
      activeElement: body,
      documentBody: body,
      feedIdentity: request.feedIdentity,
      ownerKey: request.ownerKey,
    })).toBe(true)
  })

  it('does not steal focus after the user moves elsewhere while ACK is delayed', () => {
    expect(canRestoreHomeworkFocus({
      request,
      item: { completed: true, completedAt: '2026-09-07T09:30:00Z' },
      pending: false,
      activeElement: otherControl,
      documentBody: body,
      feedIdentity: request.feedIdentity,
      ownerKey: request.ownerKey,
    })).toBe(false)
  })

  it('rejects pending, malformed, and stale-owner responses', () => {
    const base = {
      request,
      pending: true,
      activeElement: body,
      documentBody: body,
      feedIdentity: request.feedIdentity,
      ownerKey: request.ownerKey,
    }
    expect(canRestoreHomeworkFocus({ ...base, item: { completed: true, completedAt: '2026-09-07T09:30:00Z' } })).toBe(false)
    expect(canRestoreHomeworkFocus({ ...base, pending: false, item: { completed: true, completedAt: null } })).toBe(false)
    expect(canRestoreHomeworkFocus({
      ...base,
      pending: false,
      item: { completed: true, completedAt: '2026-09-07T09:30:00Z' },
      ownerKey: 'student-b:group-a:0',
    })).toBe(false)
  })
})
