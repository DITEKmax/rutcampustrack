import { describe, it, expect, vi, beforeEach } from 'vitest'
import { render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import type { ReactNode } from 'react'
import { StudentExcusesPage } from './StudentExcusesPage'

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
    lessonId: 501,
    subjectId: 11,
    lessonDate: '2026-05-01',
    lessonNumber: 3,
    status: 'absent',
    symbol: 'н',
    source: 'auto_scheduler',
    ...overrides,
  }
}

function ticket(overrides = {}) {
  return {
    id: 'ticket-1',
    studentId: 1,
    groupId: 5,
    studentName: 'Иванов И.И.',
    lessonIds: [501],
    excuseType: 'ILLNESS',
    comment: 'Болел',
    status: 'submitted',
    decisionBy: null,
    decisionComment: null,
    decisionAt: null,
    createdAt: '2026-05-02T09:00:00Z',
    updatedAt: '2026-05-02T09:00:00Z',
    ...overrides,
  }
}

function mockCommonData({
  tickets = [],
  records = [],
}: {
  tickets?: unknown[]
  records?: unknown[]
} = {}) {
  mockedGet.mockImplementation(async (url: string) => {
    if (url === '/attendance/excuses/me') {
      return {
        data: {
          _embedded: {
            excuseTicketResponseList: tickets,
          },
        },
      }
    }
    if (url === '/attendance/reports/student/records') {
      return {
        data: {
          _embedded: {
            attendanceRecordEntryList: records,
          },
        },
      }
    }
    if (url === '/academic/subjects/11') return { data: { id: 11, name: 'История' } }
    if (url === '/academic/subjects/12') return { data: { id: 12, name: 'Физика' } }
    return { data: {} }
  })
}

describe('StudentExcusesPage', () => {
  beforeEach(() => {
    vi.clearAllMocks()
  })

  it('separates active tickets and archive', async () => {
    mockCommonData({
      tickets: [
        ticket({ id: 'active', status: 'submitted', excuseType: 'ILLNESS' }),
        ticket({
          id: 'archived',
          status: 'approved',
          excuseType: 'OTHER',
          decisionAt: '2026-05-03T09:00:00Z',
        }),
      ],
    })

    const user = userEvent.setup()
    wrap(<StudentExcusesPage />)

    expect(await screen.findByText('Болезнь')).toBeInTheDocument()
    expect(screen.queryByText('Другое')).not.toBeInTheDocument()

    await user.click(screen.getByRole('tab', { name: /архив/i }))

    expect(await screen.findByText('Другое')).toBeInTheDocument()
    expect(screen.getByText('Одобрено')).toBeInTheDocument()
  })

  it('creates a ticket from selected past absent and excused lessons', async () => {
    mockCommonData({
      records: [
        record({ lessonId: 501, subjectId: 11, status: 'absent' }),
        record({ lessonId: 502, subjectId: 12, status: 'excused' }),
        record({ lessonId: 503, subjectId: 12, status: 'present' }),
        record({ lessonId: 504, subjectId: 12, lessonDate: '2999-01-01' }),
      ],
    })
    mockedPost.mockResolvedValue({ data: {} })

    const user = userEvent.setup()
    wrap(<StudentExcusesPage />)

    await user.click(await screen.findByRole('button', { name: /подать тикет/i }))
    await user.selectOptions(await screen.findByLabelText('Причина'), 'ILLNESS')
    await user.type(screen.getByLabelText('Комментарий'), 'Справка будет позже')
    await user.click(await screen.findByRole('button', { name: /История/i }))
    await user.click(await screen.findByRole('button', { name: /Физика/i }))
    await user.click(screen.getByRole('button', { name: /отправить тикет/i }))

    await waitFor(() => {
      expect(mockedPost).toHaveBeenCalledWith('/attendance/excuses', {
        lessonIds: [501, 502],
        excuseType: 'ILLNESS',
        comment: 'Справка будет позже',
      })
    })
  })

  it('sends the attachment through the multipart endpoint', async () => {
    mockCommonData({ records: [record({ lessonId: 501, subjectId: 11 })] })
    mockedPost.mockResolvedValue({ data: {} })
    const { container } = wrap(<StudentExcusesPage />)

    const user = userEvent.setup()
    await user.click(await screen.findByRole('button', { name: /подать тикет/i }))
    await user.selectOptions(await screen.findByLabelText('Причина'), 'OTHER')
    await user.click(await screen.findByRole('button', { name: /История/i }))

    const fileInput = container.ownerDocument.querySelector<HTMLInputElement>('input[type="file"]')
    expect(fileInput).not.toBeNull()
    await user.upload(fileInput as HTMLInputElement, new File(['doc'], 'note.txt', { type: 'text/plain' }))
    await user.click(screen.getByRole('button', { name: /отправить тикет/i }))

    await waitFor(() => {
      expect(mockedPost).toHaveBeenCalledWith(
        '/attendance/excuses/with-file',
        expect.any(FormData),
      )
    })
  })
})
