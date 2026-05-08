import { fireEvent, render, screen, waitFor } from '@testing-library/react'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { ProfilePage } from '../ProfilePage'

const logoutMock = vi.fn()
const useMeMock = vi.fn()
const useGroupInfoMock = vi.fn()

vi.mock('@/features/auth/AuthProvider', () => ({
  useAuth: () => ({
    user: { id: 42, role: 'STUDENT', groupId: 5, isHeadman: false },
    logout: logoutMock,
  }),
}))

vi.mock('@/features/home/api', () => ({
  useMe: () => useMeMock(),
  useGroupInfo: (groupId: number | undefined | null) => useGroupInfoMock(groupId),
}))

describe('ProfilePage', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    logoutMock.mockResolvedValue(undefined)
    useMeMock.mockReturnValue({
      data: {
        id: 42,
        login: 'student42',
        firstName: 'Иван',
        lastName: 'Петров',
        fullName: 'Петров Иван',
        role: 'STUDENT',
        groupId: 5,
        headman: false,
        avatarId: null,
      },
      isLoading: false,
      isError: false,
    })
    useGroupInfoMock.mockReturnValue({ data: { id: 5, name: 'ИС-21' } })
  })

  it('renders Telegram identity profile without login/password form', () => {
    render(<ProfilePage />)

    expect(screen.getByText('Петров Иван')).toBeInTheDocument()
    expect(screen.getByText('Группа ИС-21')).toBeInTheDocument()
    expect(screen.getByText('Telegram Mini App')).toBeInTheDocument()
    expect(screen.getByText(/Логин и пароль здесь не используются/)).toBeInTheDocument()
    expect(screen.queryByLabelText(/логин/i)).not.toBeInTheDocument()
    expect(screen.queryByLabelText(/пароль/i)).not.toBeInTheDocument()
  })

  it('restarts TMA auth through AuthProvider logout', async () => {
    render(<ProfilePage />)

    fireEvent.click(screen.getByRole('button', { name: 'Перезапустить вход' }))

    await waitFor(() => {
      expect(logoutMock).toHaveBeenCalledTimes(1)
    })
  })
})
