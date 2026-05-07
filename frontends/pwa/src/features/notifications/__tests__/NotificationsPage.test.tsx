import { describe, it, expect, vi, beforeEach } from 'vitest'
import { render, screen, act, waitFor } from '@testing-library/react'
import { MemoryRouter } from 'react-router'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'

import { NotificationsPage } from '../NotificationsPage'
import type { HistoryItem, HistoryPage } from '../notificationsApi'

type NotificationItem = {
  id: string
  type: string
  payload: Record<string, unknown>
  receivedAt: string
  read: boolean
  archived: boolean
}

const mocks = vi.hoisted(() => ({
  archive: vi.fn(),
  describeNotification: vi.fn(),
  fetchHistoryPage: vi.fn(),
  markAllNotificationsRead: vi.fn(),
  markNotificationRead: vi.fn(),
  state: {
    liveItems: [] as NotificationItem[],
    historyItems: [] as HistoryItem[],
  },
}))

function historyPage(items: HistoryItem[]): HistoryPage {
  return {
    items,
    totalElements: items.length,
    totalPages: items.length > 0 ? 1 : 0,
    pageNumber: 0,
  }
}

vi.mock('../NotificationCenter', () => ({
  useNotificationCenter: () => ({
    items: mocks.state.liveItems,
    unreadCount: mocks.state.liveItems.filter((i) => !i.read && !i.archived).length,
    markAllRead: vi.fn(),
    archive: mocks.archive,
    clearAll: vi.fn(),
  }),
  describeNotification: (r: NotificationItem) => mocks.describeNotification(r),
}))

vi.mock('../notificationsApi', () => ({
  fetchHistoryPage: (...args: unknown[]) => mocks.fetchHistoryPage(...args),
  fetchUnreadCount: vi.fn(),
  markAllNotificationsRead: (...args: unknown[]) =>
    mocks.markAllNotificationsRead(...args),
  markNotificationRead: (...args: unknown[]) => mocks.markNotificationRead(...args),
}))

function renderPage(): void {
  const queryClient = new QueryClient({
    defaultOptions: {
      queries: { retry: false },
      mutations: { retry: false },
    },
  })
  render(
    <QueryClientProvider client={queryClient}>
      <MemoryRouter>
        <NotificationsPage />
      </MemoryRouter>
    </QueryClientProvider>,
  )
}

describe('NotificationsPage', () => {
  beforeEach(() => {
    mocks.state.liveItems = []
    mocks.state.historyItems = []
    mocks.archive.mockClear()
    mocks.describeNotification.mockReset()
    mocks.fetchHistoryPage.mockReset()
    mocks.markAllNotificationsRead.mockReset()
    mocks.markNotificationRead.mockReset()
    mocks.describeNotification.mockImplementation((r: NotificationItem) => ({
      title: `TITLE:${r.type}`,
      body: typeof r.payload.subject_name === 'string' ? r.payload.subject_name : '',
    }))
    mocks.fetchHistoryPage.mockImplementation(() =>
      Promise.resolve(historyPage(mocks.state.historyItems)),
    )
    mocks.markAllNotificationsRead.mockResolvedValue(undefined)
    mocks.markNotificationRead.mockResolvedValue(undefined)
  })

  it('shows the empty state when there are no notifications', async () => {
    renderPage()
    expect(await screen.findByText('Тихо')).toBeInTheDocument()
  })

  it('calls markAllRead once when mounted', async () => {
    mocks.state.historyItems = [
      {
        id: 'a',
        userId: 42,
        type: 'LESSON_STARTED',
        payload: { subject_name: 'Алгебра' },
        sentAt: new Date().toISOString(),
        readAt: null,
      },
    ]
    renderPage()
    await waitFor(() =>
      expect(mocks.markAllNotificationsRead).toHaveBeenCalledTimes(1),
    )
  })

  it('renders each unarchived notification with its title', async () => {
    mocks.state.historyItems = [
      {
        id: 'a',
        userId: 42,
        type: 'LESSON_STARTED',
        payload: { subject_name: 'Алгебра' },
        sentAt: new Date(Date.now() - 1000).toISOString(),
        readAt: null,
      },
      {
        id: 'b',
        userId: 42,
        type: 'EXCUSE_APPROVED',
        payload: { subject_name: 'Физика' },
        sentAt: new Date().toISOString(),
        readAt: new Date().toISOString(),
      },
    ]
    mocks.state.liveItems = [
      {
        id: 'b',
        type: 'excuse.decided',
        payload: { subject_name: 'Физика' },
        receivedAt: new Date().toISOString(),
        read: true,
        archived: true,
      },
    ]
    renderPage()
    expect(await screen.findByText('TITLE:lesson.started')).toBeInTheDocument()
    expect(screen.queryByText('TITLE:excuse.decided')).toBeNull()
  })

  it('normalizes homework digest and reminder history types', async () => {
    mocks.state.historyItems = [
      {
        id: 'weekly',
        userId: 42,
        type: 'HOMEWORK_WEEKLY_DIGEST',
        payload: { subject_name: 'Math' },
        sentAt: new Date().toISOString(),
        readAt: null,
      },
      {
        id: 'due',
        userId: 42,
        type: 'HOMEWORK_DUE_REMINDER',
        payload: { subject_name: 'History' },
        sentAt: new Date().toISOString(),
        readAt: null,
      },
    ]

    renderPage()

    expect(await screen.findByText('TITLE:homework.weekly_digest')).toBeInTheDocument()
    expect(screen.getByText('TITLE:homework.due_reminder')).toBeInTheDocument()
  })
})

describe('NotificationCenter — native notification side effects', () => {
  it('service worker push handler requests sound + vibration', async () => {
    // This is a lightweight structural test: the sw.ts file references
    // silent:false and vibrate on showNotification. We read the text and
    // assert the options live there so a regression that removes them is
    // caught even without a full SW harness.
    const fs = await import('node:fs')
    const path = await import('node:path')
    const swPath = path.resolve(
      __dirname,
      '..',
      '..',
      '..',
      'sw.ts',
    )
    const source = fs.readFileSync(swPath, 'utf-8')
    expect(source).toMatch(/silent:\s*false/)
    expect(source).toMatch(/vibrate:\s*\[/)
  })
})

// Prevent vitest from complaining about missing act wrappers when
// Motion animates during mount.
describe('sanity', () => {
  it('act is callable', () => {
    act(() => {})
    expect(true).toBe(true)
  })
})
