import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { StudentLateCheckinPage } from '../StudentLateCheckinPage'

const useStudentAttendanceRecordsMock = vi.fn()
const requestLateCheckinMock = vi.fn()

vi.mock('@/features/student/requests/studentRequestsApi', async (importOriginal) => {
  const actual = await importOriginal<typeof import('@/features/student/requests/studentRequestsApi')>()
  return {
    ...actual,
    useStudentAttendanceRecords: () => useStudentAttendanceRecordsMock(),
    useRequestLateCheckin: () => ({ mutateAsync: requestLateCheckinMock }),
  }
})

vi.mock('@/features/schedule/api', () => ({
  useSubjectMap: () => ({ subjectMap: { 42: 'Физика' } }),
}))

describe('StudentLateCheckinPage', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    useStudentAttendanceRecordsMock.mockReturnValue({
      data: [
        {
          lessonId: 101,
          subjectId: 42,
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
    requestLateCheckinMock.mockResolvedValue(undefined)
  })

  it('shows absent lessons and submits a late-checkin request', async () => {
    const user = userEvent.setup()
    render(<StudentLateCheckinPage />)

    expect(screen.getByText('Физика')).toBeInTheDocument()

    await user.click(screen.getByRole('button', { name: /запросить/i }))

    expect(requestLateCheckinMock).toHaveBeenCalledWith(101)
  })
})
