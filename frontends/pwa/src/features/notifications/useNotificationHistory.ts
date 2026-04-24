import {
  useInfiniteQuery,
  useMutation,
  useQuery,
  useQueryClient,
} from '@tanstack/react-query'
import {
  fetchHistoryPage,
  fetchUnreadCount,
  markAllNotificationsRead,
  markNotificationRead,
  type HistoryPage,
} from './notificationsApi'

/**
 * TanStack Query hooks для notification history (M10 G6).
 *
 * Ключи:
 * - ['notifications','list', { unreadOnly }] — бесконечная пагинация.
 * - ['notifications','unread-count'] — badge.
 *
 * STOMP live updates invalidate'ят unread-count (см. NotificationCenter).
 */

const PAGE_SIZE = 20

const keys = {
  all: ['notifications'] as const,
  list: (unreadOnly: boolean) =>
    ['notifications', 'list', { unreadOnly }] as const,
  unreadCount: ['notifications', 'unread-count'] as const,
}

export function useNotificationHistory(unreadOnly: boolean = false) {
  return useInfiniteQuery<HistoryPage, Error>({
    queryKey: keys.list(unreadOnly),
    initialPageParam: 0,
    queryFn: ({ pageParam }) =>
      fetchHistoryPage(pageParam as number, PAGE_SIZE, unreadOnly),
    getNextPageParam: (last) =>
      last.pageNumber + 1 < last.totalPages ? last.pageNumber + 1 : undefined,
    staleTime: 30_000,
  })
}

export function useUnreadCount() {
  return useQuery<number, Error>({
    queryKey: keys.unreadCount,
    queryFn: fetchUnreadCount,
    staleTime: 30_000,
  })
}

export function useMarkNotificationRead() {
  const qc = useQueryClient()
  return useMutation<void, Error, string>({
    mutationFn: markNotificationRead,
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: keys.all })
    },
  })
}

export function useMarkAllRead() {
  const qc = useQueryClient()
  return useMutation<void, Error, void>({
    mutationFn: markAllNotificationsRead,
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: keys.all })
    },
  })
}

export const notificationsQueryKeys = keys
