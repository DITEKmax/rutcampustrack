import { describe, it, expect, vi, beforeEach } from 'vitest'
import { render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import type { ReactNode } from 'react'
import { StudentLateCheckinPage } from './StudentLateCheckinPage'

vi.mock('@/shared/lib/axios', () => ({
  apiClient: {
    get: vi.fn(),
    post: vi.fn(),
    interceptors: {
      request: { use: vi.fn() },
      response: { use: vi.fn() },
    },
  },
  setAccessTokenGetter: vi.fn(),
  setTokenRefreshCallback: vi.fn(),
  setAuthLogoutCallback: vi.fn(),
}))

import { apiClient } from '@/shared/lib/axios'

const mockedGet = vi.mocked(apiClient.get)
const mockedPost = vi.mocked(apiClient.post)

function wrap(ui: ReactNode) {
  const qc = new QueryClient({ defaultOptions: { queries: { retry: false, gcTime: 0 } } })
  return render(<QueryClientProvider client={qc}>{ui}</QueryClientProvider>)
}

function record(overrides: Partial<{
  lessonId: number
  subjectId: number
  lessonDate: string
  lessonNumber: number
  status: string
}> = {}) {
  return {
    lessonId: 101,
    subjectId: 7,
    lessonDate: '2026-05-01',
    lessonNumber: 2,
    status: 'absent',
    symbol: 'н',
    source: 'auto_scheduler',
    ...overrides,
  }
}

describe('StudentLateCheckinPage', () => {
  beforeEach(() => {
    vi.clearAllMocks()
  })

  it('renders only past absent records grouped by day', async () => {
    mockedGet.mockImplementation(async (url: string) => {
      if (url === '/attendance/reports/student/records') {
        return {
          data: {
            _embedded: {
              attendanceRecordEntryList: [
                record({ lessonId: 101, subjectId: 7, status: 'absent' }),
                record({ lessonId: 102, subjectId: 8, status: 'present' }),
                record({ lessonId: 103, subjectId: 9, lessonDate: '2999-01-01' }),
              ],
            },
          },
        }
      }
      if (url === '/academic/subjects/7') return { data: { id: 7, name: 'Математика' } }
      return { data: { id: 0, name: 'Скрытый предмет' } }
    })

    wrap(<StudentLateCheckinPage />)

    expect(await screen.findByText('Математика')).toBeInTheDocument()
    expect(screen.getByText('Пара №2')).toBeInTheDocument()
    expect(screen.queryByText('Скрытый предмет')).not.toBeInTheDocument()
  })

  it('disables the row after a successful request', async () => {
    mockedGet.mockImplementation(async (url: string) => {
      if (url === '/attendance/reports/student/records') {
        return {
          data: {
            _embedded: {
              attendanceRecordEntryList: [record({ lessonId: 777, subjectId: 7 })],
            },
          },
        }
      }
      if (url === '/academic/subjects/7') return { data: { id: 7, name: 'Физика' } }
      return { data: {} }
    })
    mockedPost.mockResolvedValue({ data: {} })

    const user = userEvent.setup()
    wrap(<StudentLateCheckinPage />)

    const button = await screen.findByRole('button', { name: /запросить/i })
    await user.click(button)

    await waitFor(() => {
      expect(mockedPost).toHaveBeenCalledWith('/attendance/late-checkin/777', {})
    })
    expect(await screen.findByRole('button', { name: /заявка отправлена/i }))
      .toBeDisabled()
  })

  it('treats duplicate 409 as an already submitted request', async () => {
    mockedGet.mockImplementation(async (url: string) => {
      if (url === '/attendance/reports/student/records') {
        return {
          data: {
            _embedded: {
              attendanceRecordEntryList: [record({ lessonId: 888, subjectId: 7 })],
            },
          },
        }
      }
      if (url === '/academic/subjects/7') return { data: { id: 7, name: 'Физика' } }
      return { data: {} }
    })
    mockedPost.mockRejectedValue({ response: { status: 409 } })

    const user = userEvent.setup()
    wrap(<StudentLateCheckinPage />)

    await user.click(await screen.findByRole('button', { name: /запросить/i }))

    expect(await screen.findByRole('button', { name: /заявка отправлена/i }))
      .toBeDisabled()
  })
})
