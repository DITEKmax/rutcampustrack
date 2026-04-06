import { describe, it, expect, vi, beforeEach } from 'vitest'
import { render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { MemoryRouter } from 'react-router'
import type { ReactNode } from 'react'

// Mock axios
vi.mock('@/shared/lib/axios', () => ({
  apiClient: {
    get: vi.fn(),
    post: vi.fn(),
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

// Mock useAuth
vi.mock('@/features/auth/AuthProvider', () => ({
  useAuth: () => ({
    isAuthenticated: true,
    user: { id: 1, role: 'STUDENT', groupId: 2 },
    accessToken: 'fake-token',
    login: vi.fn(),
    logout: vi.fn(),
  }),
}))

// Mock useNetworkStatus
vi.mock('@/shared/hooks/useNetworkStatus', () => ({
  useNetworkStatus: () => ({ isOnline: true }),
}))

// Mock useActiveSemester
vi.mock('../api', async (importOriginal) => {
  const actual = await importOriginal<typeof import('../api')>()
  return {
    ...actual,
    useActiveSemester: () => ({ data: 5, isLoading: false, isError: false }),
    useSubjectName: () => ({ data: 'Математика', isLoading: false }),
  }
})

// Mock useHomework separately for HomeworkPage tests
const mockUseHomework = vi.fn()
const mockUseToggleHomework = vi.fn()

import { apiClient } from '@/shared/lib/axios'
import { HomeworkItem } from '../HomeworkItem'
import type { HomeworkResponse } from '../types'

const mockedGet = vi.mocked(apiClient.get)

function createWrapper() {
  const queryClient = new QueryClient({
    defaultOptions: {
      queries: { retry: false, gcTime: 0 },
    },
  })
  return function Wrapper({ children }: { children: ReactNode }) {
    return (
      <QueryClientProvider client={queryClient}>
        <MemoryRouter>{children}</MemoryRouter>
      </QueryClientProvider>
    )
  }
}

const makeHomework = (overrides: Partial<HomeworkResponse> = {}): HomeworkResponse => ({
  id: 1,
  title: 'Задача по матану',
  description: null,
  link: null,
  subjectId: 10,
  groupId: 2,
  semesterId: 5,
  publishedBy: 100,
  completed: false,
  createdAt: '2025-03-01T10:00:00Z',
  ...overrides,
})

describe('HomeworkItem', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    mockUseHomework.mockReturnValue({ data: [], isLoading: false, isError: false, refetch: vi.fn() })
    mockUseToggleHomework.mockReturnValue({ mutate: vi.fn() })
    mockedGet.mockResolvedValue({ data: {} })
  })

  it('HW-02 (checkbox toggle fires onToggle): calls onToggle with (id, true) when unchecked item is clicked', async () => {
    const user = userEvent.setup()
    const onToggle = vi.fn()

    render(
      <HomeworkItem homework={makeHomework({ completed: false })} onToggle={onToggle} error={null} />,
      { wrapper: createWrapper() }
    )

    const checkbox = screen.getByRole('checkbox')
    await user.click(checkbox)

    expect(onToggle).toHaveBeenCalledWith(1, true)
  })

  it('HW-02 (done item styling): done item title has line-through class', () => {
    const onToggle = vi.fn()

    render(
      <HomeworkItem homework={makeHomework({ completed: true })} onToggle={onToggle} error={null} />,
      { wrapper: createWrapper() }
    )

    const title = screen.getByText('Задача по матану')
    expect(title.className).toContain('line-through')
  })

  it('HW-02 (error display): renders inline error text when error prop is set', () => {
    const onToggle = vi.fn()

    render(
      <HomeworkItem
        homework={makeHomework()}
        onToggle={onToggle}
        error="Не удалось сохранить"
      />,
      { wrapper: createWrapper() }
    )

    expect(screen.getByText('Не удалось сохранить')).toBeInTheDocument()
  })
})

// HomeworkPage integration tests (mocking api module hooks)
vi.mock('../HomeworkPage', async (importOriginal) => {
  return importOriginal()
})

describe('HomeworkPage integration', () => {
  beforeEach(() => {
    vi.clearAllMocks()
  })

  it('HW-01 (empty state): renders "Нет заданий" when homework array is empty', async () => {
    mockedGet.mockImplementation(async (url: string) => {
      if (url.includes('/academic/semesters')) {
        return {
          data: {
            _embedded: {
              semesterResponseList: [
                { id: 5, name: 'Весна 2025', startDate: '2025-02-01', endDate: '2025-06-30', active: true },
              ],
            },
          },
        }
      }
      if (url.includes('/academic/homeworks')) {
        return { data: { _embedded: { homeworkResponseList: [] } } }
      }
      return { data: {} }
    })

    // Dynamically import HomeworkPage after mocks are set
    const { HomeworkPage } = await import('../HomeworkPage')

    render(<HomeworkPage />, { wrapper: createWrapper() })

    await waitFor(() => {
      expect(screen.getByText('Нет заданий')).toBeInTheDocument()
    })

    expect(screen.getByText('Домашних заданий пока нет')).toBeInTheDocument()
  })

  it('HW-01 (list renders): renders homework titles when data is returned', async () => {
    mockedGet.mockImplementation(async (url: string) => {
      if (url.includes('/academic/semesters')) {
        return {
          data: {
            _embedded: {
              semesterResponseList: [
                { id: 5, name: 'Весна 2025', startDate: '2025-02-01', endDate: '2025-06-30', active: true },
              ],
            },
          },
        }
      }
      if (url.includes('/academic/subjects/')) {
        return { data: { id: 10, name: 'Математика' } }
      }
      if (url.includes('/academic/homeworks')) {
        return {
          data: {
            _embedded: {
              homeworkResponseList: [
                {
                  id: 1,
                  title: 'Задача по матану',
                  description: null,
                  link: null,
                  subjectId: 10,
                  groupId: 2,
                  semesterId: 5,
                  publishedBy: 100,
                  completed: false,
                  createdAt: '2025-03-01T10:00:00Z',
                },
                {
                  id: 2,
                  title: 'Лабораторная по физике',
                  description: null,
                  link: null,
                  subjectId: 11,
                  groupId: 2,
                  semesterId: 5,
                  publishedBy: 101,
                  completed: false,
                  createdAt: '2025-03-02T10:00:00Z',
                },
              ],
            },
          },
        }
      }
      return { data: {} }
    })

    const { HomeworkPage } = await import('../HomeworkPage')

    render(<HomeworkPage />, { wrapper: createWrapper() })

    await waitFor(() => {
      expect(screen.getByText('Задача по матану')).toBeInTheDocument()
    })

    expect(screen.getByText('Лабораторная по физике')).toBeInTheDocument()
  })
})
