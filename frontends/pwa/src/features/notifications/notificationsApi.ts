import { apiClient } from '@/shared/lib/axios'

/**
 * Backend notification history API client (M10 G6).
 *
 * Backend persist'ит только user-facing events (excuse.*, late_checkin.*,
 * attendance.marked — D6). Broadcast events (lesson.*, homework.*) живут
 * только в sessionStorage — для них нет server-side history.
 */

export interface HistoryItem {
  id: string
  userId: number
  type: string
  payload: Record<string, unknown>
  sentAt: string
  readAt: string | null
  _links?: Record<string, { href: string }>
}

interface PagedHistoryResponse {
  _embedded?: {
    notificationHistoryDtoList?: HistoryItem[]
  }
  page: {
    size: number
    totalElements: number
    totalPages: number
    number: number
  }
}

export interface HistoryPage {
  items: HistoryItem[]
  totalElements: number
  totalPages: number
  pageNumber: number
}

export async function fetchHistoryPage(
  page: number,
  size: number = 20,
  unreadOnly: boolean = false,
): Promise<HistoryPage> {
  const { data } = await apiClient.get<PagedHistoryResponse>('/notifications', {
    params: { page, size, unreadOnly, sort: 'sentAt,desc' },
  })
  return {
    items: data._embedded?.notificationHistoryDtoList ?? [],
    totalElements: data.page.totalElements,
    totalPages: data.page.totalPages,
    pageNumber: data.page.number,
  }
}

export async function fetchUnreadCount(): Promise<number> {
  const { data } = await apiClient.get<{ count: number }>(
    '/notifications/unread-count',
  )
  return data.count
}

export async function markNotificationRead(id: string): Promise<void> {
  await apiClient.patch(`/notifications/${encodeURIComponent(id)}/read`)
}

export async function markAllNotificationsRead(): Promise<void> {
  await apiClient.post('/notifications/mark-all-read')
}
