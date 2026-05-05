import { describe, it, expect, vi, beforeEach } from 'vitest'

type MockResp<T> = { data: T }

const getMock = vi.fn()
const patchMock = vi.fn()
const postMock = vi.fn()
const putMock = vi.fn()

vi.mock('@/shared/lib/axios', () => ({
  apiClient: {
    get: (...args: unknown[]) => getMock(...args),
    patch: (...args: unknown[]) => patchMock(...args),
    post: (...args: unknown[]) => postMock(...args),
    put: (...args: unknown[]) => putMock(...args),
  },
}))

import {
  fetchNotificationPreferences,
  fetchHistoryPage,
  fetchUnreadCount,
  markAllNotificationsRead,
  markNotificationRead,
  updateNotificationPreferences,
} from '../notificationsApi'

describe('notificationsApi', () => {
  beforeEach(() => {
    getMock.mockReset()
    patchMock.mockReset()
    postMock.mockReset()
    putMock.mockReset()
  })

  it('fetchHistoryPage парсит PagedModel HATEOAS', async () => {
    const resp: MockResp<unknown> = {
      data: {
        _embedded: {
          notificationHistoryDtoList: [
            {
              id: 'doc-1',
              userId: 42,
              type: 'EXCUSE_APPROVED',
              payload: { group_id: 7 },
              sentAt: '2026-04-24T12:00:00Z',
              readAt: null,
            },
          ],
        },
        page: { size: 20, totalElements: 1, totalPages: 1, number: 0 },
      },
    }
    getMock.mockResolvedValue(resp)

    const result = await fetchHistoryPage(0, 20, false)

    expect(getMock).toHaveBeenCalledWith('/notifications', {
      params: { page: 0, size: 20, unreadOnly: false, sort: 'sentAt,desc' },
    })
    expect(result.items).toHaveLength(1)
    expect(result.items[0].id).toBe('doc-1')
    expect(result.totalPages).toBe(1)
    expect(result.pageNumber).toBe(0)
  })

  it('fetchHistoryPage возвращает empty при отсутствии _embedded', async () => {
    getMock.mockResolvedValue({
      data: { page: { size: 20, totalElements: 0, totalPages: 0, number: 0 } },
    })
    const result = await fetchHistoryPage(0, 20, true)
    expect(result.items).toEqual([])
    expect(result.totalElements).toBe(0)
  })

  it('fetchUnreadCount извлекает .count', async () => {
    getMock.mockResolvedValue({ data: { count: 7 } })
    await expect(fetchUnreadCount()).resolves.toBe(7)
    expect(getMock).toHaveBeenCalledWith('/notifications/unread-count')
  })

  it('markNotificationRead url-кодирует id', async () => {
    patchMock.mockResolvedValue({ data: null })
    await markNotificationRead('abc/special?x')
    expect(patchMock).toHaveBeenCalledWith('/notifications/abc%2Fspecial%3Fx/read')
  })

  it('markAllNotificationsRead делает POST без body', async () => {
    postMock.mockResolvedValue({ data: null })
    await markAllNotificationsRead()
    expect(postMock).toHaveBeenCalledWith('/notifications/mark-all-read')
  })

  it('fetchNotificationPreferences reads categories and mute', async () => {
    getMock.mockResolvedValue({
      data: {
        categories: { reminders: false },
        mutedUntil: '2026-05-05T10:00:00Z',
      },
    })

    const result = await fetchNotificationPreferences()

    expect(getMock).toHaveBeenCalledWith('/notifications/preferences')
    expect(result.categories.reminders).toBe(false)
    expect(result.mutedUntil).toBe('2026-05-05T10:00:00Z')
  })

  it('updateNotificationPreferences sends server payload', async () => {
    const payload = { categories: { reminders: true }, mutedUntil: null }
    putMock.mockResolvedValue({ data: payload })

    const result = await updateNotificationPreferences(payload)

    expect(putMock).toHaveBeenCalledWith('/notifications/preferences', payload)
    expect(result.categories.reminders).toBe(true)
  })
})
