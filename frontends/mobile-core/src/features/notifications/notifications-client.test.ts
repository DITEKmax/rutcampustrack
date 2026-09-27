import { describe, expect, it, vi } from 'vitest'
import { StaleSessionGenerationError } from '../../shared/session-owner'
import {
  createGenerationBoundNotificationsApi,
  NotificationsApiError,
} from './notifications-client'

function jsonResponse(value: unknown, status = 200): Response {
  return new Response(JSON.stringify(value), {
    status,
    headers: { 'Content-Type': 'application/json' },
  })
}

function historyResponse(payload: unknown = {}, type = 'LESSON_REMINDER'): Response {
  return jsonResponse({
    _embedded: {
      notificationHistoryDtoList: [{
        id: 'evt-123',
        userId: 987,
        type,
        sentAt: '2026-09-25T09:00:00Z',
        readAt: null,
        payload,
        _links: { self: { href: 'https://attacker.invalid/notification' } },
      }],
    },
    page: { number: 0, size: 20, totalPages: 1, totalElements: 1 },
  })
}

function sessionOwner(generation = 1) {
  return {
    generation,
    token: 'token-one',
    currentGeneration() { return this.generation },
    accessTokenFor(captured: number) {
      if (captured !== this.generation) throw new StaleSessionGenerationError()
      return this.token
    },
    refreshFor: vi.fn(async function (this: { token: string }) { this.token = 'token-two' }),
  }
}

describe('generation-bound notifications API', () => {
  it('drops an in-flight response from an old session generation', async () => {
    const owner = sessionOwner()
    let finish!: (response: Response) => void
    const fetcher = vi.fn(() => new Promise<Response>((resolve) => { finish = resolve }))
    const api = createGenerationBoundNotificationsApi(owner, fetcher)

    const pending = api.listHistory(0)
    owner.generation += 1
    finish(historyResponse({ subjectName: 'Алгебра' }))

    await expect(pending).rejects.toBeInstanceOf(StaleSessionGenerationError)
  })

  it('retries one 401 with the refreshed bearer and limits payload fields', async () => {
    const owner = sessionOwner()
    const fetcher = vi.fn()
      .mockResolvedValueOnce(new Response(null, { status: 401 }))
      .mockResolvedValueOnce(historyResponse({
        subjectName: 'Алгебра',
        lessonNumber: 5,
        reason: 'private explanation',
        message: '<img src=x onerror=alert(1)>',
        url: 'https://attacker.invalid/',
      }))
    const api = createGenerationBoundNotificationsApi(owner, fetcher)

    const result = await api.listHistory(0)

    expect(owner.refreshFor).toHaveBeenCalledTimes(1)
    expect((fetcher.mock.calls[0]![1] as RequestInit).headers).toBeInstanceOf(Headers)
    const firstHeaders = (fetcher.mock.calls[0]![1] as RequestInit).headers as Headers
    const secondHeaders = (fetcher.mock.calls[1]![1] as RequestInit).headers as Headers
    expect(firstHeaders.get('Authorization')).toBe('Bearer token-one')
    expect(secondHeaders.get('Authorization')).toBe('Bearer token-two')
    expect(result.items[0]?.context).toEqual([
      { label: 'Предмет', value: 'Алгебра' },
      { label: 'Занятие', value: '5' },
    ])
    expect(result.items[0]).not.toHaveProperty('userId')
    expect(result.items[0]).not.toHaveProperty('_links')
  })

  it('parses only a safe homework ID and real lesson date from supported event payloads', async () => {
    const owner = sessionOwner()
    const fetcher = vi.fn()
      .mockResolvedValueOnce(historyResponse({ homework_id: 81, lesson_date: '2026-09-24', link: 'https://invalid.example/' }, 'HOMEWORK_PUBLISHED'))
      .mockResolvedValueOnce(historyResponse({ homework_id: 82, lesson_date: '2026-02-30' }, 'HOMEWORK_UPDATED'))
      .mockResolvedValueOnce(historyResponse({ homework_id: '9007199254740992', lesson_date: '2026-09-24' }, 'HOMEWORK_PUBLISHED'))
      .mockResolvedValueOnce(historyResponse({ homework_id: 83, lesson_date: '2026-09-24' }, 'LESSON_STARTED'))
    const api = createGenerationBoundNotificationsApi(owner, fetcher)

    const valid = await api.listHistory(0)
    const invalidDate = await api.listHistory(0)
    const unsafeId = await api.listHistory(0)
    const unsupportedType = await api.listHistory(0)

    expect(valid.items[0]?.target).toEqual({ kind: 'homework', homeworkId: '81', lessonDate: '2026-09-24' })
    expect(invalidDate.items[0]?.target).toBeNull()
    expect(unsafeId.items[0]?.target).toBeNull()
    expect(unsupportedType.items[0]?.target).toBeNull()
  })

  it('rejects unsafe item IDs before constructing an API request', async () => {
    const owner = sessionOwner()
    const fetcher = vi.fn()
    const api = createGenerationBoundNotificationsApi(owner, fetcher)

    await expect(api.markRead('../preferences')).rejects.toBeInstanceOf(NotificationsApiError)
    expect(fetcher).not.toHaveBeenCalled()
  })

  it('does not retry authorization or other permission errors beyond the single 401 refresh', async () => {
    const owner = sessionOwner()
    const fetcher = vi.fn().mockResolvedValue(new Response(null, { status: 403 }))
    const api = createGenerationBoundNotificationsApi(owner, fetcher)

    await expect(api.unreadCount()).rejects.toMatchObject({ status: 403 })
    expect(fetcher).toHaveBeenCalledTimes(1)
    expect(owner.refreshFor).not.toHaveBeenCalled()
  })
})
