import { describe, it, expect, vi, beforeEach } from 'vitest'
import { render, screen } from '@testing-library/react'
import { BrowserRouter } from 'react-router'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { GroupHub } from './GroupHub'

vi.mock('@/shared/lib/axios', () => ({
  apiClient: {
    get: vi.fn(),
    post: vi.fn(),
    put: vi.fn(),
    delete: vi.fn(),
    interceptors: {
      request: { use: vi.fn() },
      response: { use: vi.fn() },
    },
  },
  setAccessTokenGetter: vi.fn(),
  setTokenRefreshCallback: vi.fn(),
  setAuthLogoutCallback: vi.fn(),
}))

vi.mock('@/features/headman/shared/headmanApi', () => ({
  useGroupMembers: vi.fn(() => ({ data: [{ id: 1, fullName: 'Иванов' }, { id: 2, fullName: 'Петров' }], isLoading: false }),),
  useGroupSubjects: vi.fn(() => ({ data: [{ id: 1, name: 'Математика' }, { id: 2, name: 'Физика' }, { id: 3, name: 'История' }], isLoading: false }),),
}))

function renderGroupHub() {
  const queryClient = new QueryClient({
    defaultOptions: { queries: { retry: false } },
  })
  return render(
    <BrowserRouter>
      <QueryClientProvider client={queryClient}>
        <GroupHub />
      </QueryClientProvider>
    </BrowserRouter>,
  )
}

describe('GroupHub', () => {
  beforeEach(() => {
    vi.clearAllMocks()
  })

  it('Test 1: renders a heading "Группа"', () => {
    renderGroupHub()
    expect(screen.getByRole('heading', { name: 'Группа' })).toBeInTheDocument()
  })

  it('Test 2: renders exactly 7 navigation links with correct hrefs', () => {
    renderGroupHub()
    const links = screen.getAllByRole('link')
    expect(links).toHaveLength(7)

    const hrefs = links.map((l) => l.getAttribute('href'))
    expect(hrefs).toEqual(
      expect.arrayContaining([
        '/group/overview',
        '/group/students',
        '/group/subjects',
        '/group/journal',
        '/group/excuses',
        '/group/late-checkin',
        '/group/stats',
      ]),
    )
  })

  it('Test 3: each card shows the correct Russian title', () => {
    renderGroupHub()
    expect(screen.getByText('Обзор')).toBeInTheDocument()
    expect(screen.getByText('Студенты')).toBeInTheDocument()
    expect(screen.getByText('Предметы')).toBeInTheDocument()
    expect(screen.getByText('Журнал')).toBeInTheDocument()
    expect(screen.getByText('Пропуски')).toBeInTheDocument()
    expect(screen.getByText('Запросы отметки')).toBeInTheDocument()
    expect(screen.getByText('Статистика')).toBeInTheDocument()
  })
})
