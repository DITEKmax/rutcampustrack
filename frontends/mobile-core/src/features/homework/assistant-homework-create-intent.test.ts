import { describe, expect, it, vi } from 'vitest'
import {
  intentAfterHomeworkCreateFailure,
  reuseOrCreateAssistantHomeworkIntent,
  sameAssistantHomeworkDraftContext,
  type AssistantHomeworkDraftContext,
} from './assistant-homework-create-intent'

const context: AssistantHomeworkDraftContext = {
  userId: 41,
  groupId: 7,
  semesterId: 12,
  selectedDate: '2026-09-29',
  lessonId: 300,
  lessonDate: '2026-09-29',
  subjectId: 88,
  lessonNumber: 2,
}

describe('assistant homework create intent', () => {
  it('reuses the same payload and key after an uncertain save, and refuses another lesson', async () => {
    const firstInput = {
      title: 'Тема 2',
      description: 'Конспект',
      link: 'https://example.test/material',
    }
    let intent = reuseOrCreateAssistantHomeworkIntent(null, context, firstInput, () => 'request-key-a')
    expect(intent).not.toBeNull()
    const createHomework = vi.fn()
      .mockRejectedValueOnce(new TypeError('connection lost after send'))
      .mockResolvedValueOnce(null)

    try {
      await createHomework(intent!.input)
    } catch {
      intent = intentAfterHomeworkCreateFailure(intent, null)
    }

    expect(intent).not.toBeNull()
    const retry = reuseOrCreateAssistantHomeworkIntent(intent, context, {
      title: 'Changed after uncertain save',
      description: '',
      link: '',
    }, () => 'request-key-b')
    expect(retry).toBe(intent)
    expect(Object.isFrozen(retry)).toBe(true)
    expect(Object.isFrozen(retry!.context)).toBe(true)
    expect(Object.isFrozen(retry!.input)).toBe(true)
    await createHomework(retry!.input)
    expect(createHomework).toHaveBeenCalledTimes(2)
    expect(createHomework.mock.calls[0]?.[0]).toBe(createHomework.mock.calls[1]?.[0])
    expect(retry!.input.requestKey).toBe('request-key-a')
    expect(sameAssistantHomeworkDraftContext(retry!.context, { ...context, lessonId: 301 })).toBe(false)
    expect(reuseOrCreateAssistantHomeworkIntent(retry, { ...context, lessonId: 301 }, firstInput, () => 'request-key-c'))
      .toBeNull()
  })

  it('clears a rejected validation intent but preserves a 409 conflict key', () => {
    const intent = reuseOrCreateAssistantHomeworkIntent(null, context, {
      title: 'Тема 2', description: '', link: '',
    }, () => 'request-key-a')
    expect(intentAfterHomeworkCreateFailure(intent, 409)).toBe(intent)
    expect(intentAfterHomeworkCreateFailure(intent, 422)).toBeNull()
  })
})
