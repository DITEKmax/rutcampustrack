import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { BrowserRouter } from 'react-router'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { HeadmanExcusesPage } from '../HeadmanExcusesPage'

const useAuthMock = vi.fn()
const useGroupExcusesMock = vi.fn()
const decideExcuseMock = vi.fn()

vi.mock('@/features/auth/AuthProvider', () => ({
  useAuth: () => useAuthMock(),
}))

vi.mock('@/features/headman/shared/headmanApi', () => ({
  useGroupExcuses: (groupId: number | undefined) => useGroupExcusesMock(groupId),
  useDecideExcuse: () => ({ mutateAsync: decideExcuseMock, isPending: false }),
}))

function renderPage() {
  return render(
    <BrowserRouter>
      <HeadmanExcusesPage />
    </BrowserRouter>,
  )
}

describe('HeadmanExcusesPage', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    useAuthMock.mockReturnValue({
      user: { id: 1, role: 'STUDENT', groupId: 5, isHeadman: true },
    })
    useGroupExcusesMock.mockReturnValue({
      data: [
        {
          id: 't-1',
          studentId: 10,
          groupId: 5,
          studentName: 'Иван Иванов',
          lessonIds: [101, 102],
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
          id: 't-2',
          studentId: 11,
          groupId: 5,
          studentName: 'Мария Петрова',
          lessonIds: [103],
          excuseType: 'OTHER',
          status: 'approved',
          comment: null,
          decisionBy: 1,
          decisionComment: 'Ок',
          decisionAt: '2026-05-02T10:00:00Z',
          createdAt: '2026-05-01T10:00:00Z',
          updatedAt: '2026-05-02T10:00:00Z',
        },
      ],
      isLoading: false,
      isError: false,
    })
    decideExcuseMock.mockResolvedValue(undefined)
  })

  it('loads pending tickets for the headman group and approves a ticket', async () => {
    const user = userEvent.setup()
    renderPage()

    expect(useGroupExcusesMock).toHaveBeenCalledWith(5)
    expect(screen.getByText('Иван Иванов')).toBeInTheDocument()

    await user.click(screen.getByRole('button', { name: /одобрить/i }))

    expect(decideExcuseMock).toHaveBeenCalledWith({
      id: 't-1',
      approved: true,
      decisionComment: null,
    })
  })

  it('shows resolved tickets after tab switch', async () => {
    const user = userEvent.setup()
    renderPage()

    await user.click(screen.getByRole('tab', { name: /решенные/i }))

    expect(screen.getByText('Мария Петрова')).toBeInTheDocument()
  })
})
