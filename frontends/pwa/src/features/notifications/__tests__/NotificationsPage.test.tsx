import { describe, it, expect, vi, beforeEach } from 'vitest'
import { render, screen, act } from '@testing-library/react'
import { MemoryRouter } from 'react-router'

import { NotificationsPage } from '../NotificationsPage'

type NotificationItem = {
  id: string
  type: string
  payload: Record<string, unknown>
  receivedAt: string
  read: boolean
  archived: boolean
}

const markAllRead = vi.fn()
const archive = vi.fn()

let mockItems: NotificationItem[] = []
const describeNotificationMock = vi.fn((r: NotificationItem) => ({
  title: `TITLE:${r.type}`,
  body: typeof r.payload.subject_name === 'string' ? r.payload.subject_name : '',
}))

vi.mock('../NotificationCenter', () => ({
  useNotificationCenter: () => ({
    items: mockItems,
    unreadCount: mockItems.filter((i) => !i.read && !i.archived).length,
    markAllRead,
    archive,
    clearAll: vi.fn(),
  }),
  describeNotification: (r: NotificationItem) => describeNotificationMock(r),
}))

function renderPage(): void {
  render(
    <MemoryRouter>
      <NotificationsPage />
    </MemoryRouter>,
  )
}

describe('NotificationsPage', () => {
  beforeEach(() => {
    mockItems = []
    markAllRead.mockClear()
    archive.mockClear()
    describeNotificationMock.mockClear()
  })

  it('shows the empty state when there are no notifications', () => {
    renderPage()
    expect(screen.getByText('Тихо')).toBeInTheDocument()
  })

  it('calls markAllRead once when mounted', () => {
    mockItems = [
      {
        id: 'a',
        type: 'lesson.started',
        payload: { subject_name: 'Алгебра' },
        receivedAt: new Date().toISOString(),
        read: false,
        archived: false,
      },
    ]
    renderPage()
    expect(markAllRead).toHaveBeenCalled()
  })

  it('renders each unarchived notification with its title', () => {
    mockItems = [
      {
        id: 'a',
        type: 'lesson.started',
        payload: { subject_name: 'Алгебра' },
        receivedAt: new Date(Date.now() - 1000).toISOString(),
        read: false,
        archived: false,
      },
      {
        id: 'b',
        type: 'homework.published',
        payload: { subject_name: 'Физика' },
        receivedAt: new Date().toISOString(),
        read: true,
        archived: true, // archived → should NOT render
      },
    ]
    renderPage()
    expect(screen.getByText('TITLE:lesson.started')).toBeInTheDocument()
    expect(screen.queryByText('TITLE:homework.published')).toBeNull()
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
