import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { StudentExcusesPage } from '../StudentExcusesPage'

const useStudentExcuseTicketsMock = vi.fn()
const useStudentAttendanceRecordsMock = vi.fn()

vi.mock('@/features/student/requests/studentRequestsApi', async (importOriginal) => {
  const actual = await importOriginal<typeof import('@/features/student/requests/studentRequestsApi')>()
  return {
    ...actual,
    useStudentExcuseTickets: () => useStudentExcuseTicketsMock(),
    useStudentAttendanceRecords: () => useStudentAttendanceRecordsMock(),
    useSubmitStudentExcuse: () => ({ mutateAsync: vi.fn(), isPending: false, reset: vi.fn() }),
  }
})

vi.mock('@/features/schedule/api', () => ({
  useSubjectMap: () => ({ subjectMap: { 101: 'Физика', 102: 'История' } }),
}))

describe('StudentExcusesPage', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    useStudentAttendanceRecordsMock.mockReturnValue({
      data: [
        {
          lessonId: 501,
          subjectId: 101,
          lessonDate: '2026-05-01',
          lessonNumber: 2,
          status: 'absent',
          symbol: 'н',
          source: 'system',
        },
      ],
      isLoading: false,
      isError: false,
    })
    useStudentExcuseTicketsMock.mockReturnValue({
      data: [
        {
          id: 'e-1',
          studentId: 10,
          groupId: 5,
          studentName: 'Иван Иванов',
          lessonIds: [501],
          excuseType: 'ILLNESS',
          status: 'submitted',
          comment: 'Справка',
          decisionBy: null,
          decisionComment: null,
          decisionAt: null,
          createdAt: '2026-05-01T10:00:00Z',
          updatedAt: '2026-05-01T10:00:00Z',
        },
        {
          id: 'e-2',
          studentId: 10,
          groupId: 5,
          studentName: 'Иван Иванов',
          lessonIds: [502],
          excuseType: 'OTHER',
          status: 'approved',
          comment: null,
          decisionBy: 1,
          decisionComment: 'Принято',
          decisionAt: '2026-05-02T10:00:00Z',
          createdAt: '2026-05-01T10:00:00Z',
          updatedAt: '2026-05-02T10:00:00Z',
        },
      ],
      isLoading: false,
      isError: false,
    })
  })

  it('shows active and archive tickets', async () => {
    const user = userEvent.setup()
    render(<StudentExcusesPage />)

    await user.click(screen.getByRole('button', { name: /болезнь/i }))
    expect(screen.getByText('Справка')).toBeInTheDocument()
    expect(screen.getByText(/активные/i)).toBeInTheDocument()

    await user.click(screen.getByRole('tab', { name: /архив/i }))

    await user.click(screen.getByRole('button', { name: /другое/i }))
    expect(screen.getByText('Принято')).toBeInTheDocument()
  })
})
