import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { BrowserRouter } from 'react-router'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { HeadmanLateCheckinPage } from '../HeadmanLateCheckinPage'

const useAuthMock = vi.fn()
const usePendingLateCheckinRequestsMock = vi.fn()
const decideLateCheckinMock = vi.fn()

vi.mock('@/features/auth/AuthProvider', () => ({
  useAuth: () => useAuthMock(),
}))

vi.mock('@/features/headman/shared/headmanApi', () => ({
  usePendingLateCheckinRequests: (groupId: number | undefined) =>
    usePendingLateCheckinRequestsMock(groupId),
  useDecideLateCheckin: () => ({ mutateAsync: decideLateCheckinMock, isPending: false }),
}))

function renderPage() {
  return render(
    <BrowserRouter>
      <HeadmanLateCheckinPage />
    </BrowserRouter>,
  )
}

describe('HeadmanLateCheckinPage', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    useAuthMock.mockReturnValue({
      user: { id: 1, role: 'STUDENT', groupId: 5, isHeadman: true },
    })
    usePendingLateCheckinRequestsMock.mockReturnValue({
      data: [
        {
          id: 'lcr-1',
          studentId: 10,
          groupId: 5,
          lessonId: 101,
          studentName: 'Иван Иванов',
          status: 'pending',
          decisionBy: null,
          decisionAt: null,
          createdAt: '2026-05-01T10:00:00Z',
          updatedAt: '2026-05-01T10:00:00Z',
        },
        {
          id: 'lcr-2',
          studentId: 11,
          groupId: 5,
          lessonId: 102,
          studentName: 'Мария Петрова',
          status: 'approved',
          decisionBy: 1,
          decisionAt: '2026-05-02T10:00:00Z',
          createdAt: '2026-05-01T10:00:00Z',
          updatedAt: '2026-05-02T10:00:00Z',
        },
      ],
      isLoading: false,
      isError: false,
    })
    decideLateCheckinMock.mockResolvedValue(undefined)
  })

  it('loads pending requests for the headman group and approves a request', async () => {
    const user = userEvent.setup()
    renderPage()

    expect(usePendingLateCheckinRequestsMock).toHaveBeenCalledWith(5)
    expect(screen.getByText('Иван Иванов')).toBeInTheDocument()

    await user.click(screen.getByRole('button', { name: /одобрить/i }))

    expect(decideLateCheckinMock).toHaveBeenCalledWith({
      id: 'lcr-1',
      approved: true,
    })
  })

  it('shows resolved requests after tab switch', async () => {
    const user = userEvent.setup()
    renderPage()

    await user.click(screen.getByRole('tab', { name: /решенные/i }))

    expect(screen.getByText('Мария Петрова')).toBeInTheDocument()
  })
})
