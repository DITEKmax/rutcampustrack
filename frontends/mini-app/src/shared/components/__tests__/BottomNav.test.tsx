import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter, Route, Routes, useLocation } from 'react-router'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { BottomNav } from '../BottomNav'

const useAuthMock = vi.fn()

vi.mock('@/features/auth/AuthProvider', () => ({
  useAuth: () => useAuthMock(),
}))

function LocationProbe() {
  const location = useLocation()
  return <output aria-label="current path">{location.pathname}</output>
}

function renderBottomNav(path = '/home') {
  return render(
    <MemoryRouter initialEntries={[path]}>
      <Routes>
        <Route
          path="*"
          element={
            <>
              <LocationProbe />
              <BottomNav />
            </>
          }
        />
      </Routes>
    </MemoryRouter>,
  )
}

describe('BottomNav', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    useAuthMock.mockReturnValue({
      user: { id: 10, role: 'STUDENT', groupId: 5, isHeadman: false },
    })
  })

  it('renders only student tabs for a regular student', () => {
    renderBottomNav()

    expect(screen.getByRole('button', { name: /главная/i })).toHaveAttribute('aria-current', 'page')
    expect(screen.getByRole('button', { name: /расписание/i })).toBeInTheDocument()
    expect(screen.queryByRole('button', { name: /группа/i })).not.toBeInTheDocument()
  })

  it('adds the group tab for a headman and navigates to it', async () => {
    const user = userEvent.setup()
    useAuthMock.mockReturnValue({
      user: { id: 10, role: 'STUDENT', groupId: 5, isHeadman: true },
    })

    renderBottomNav()

    await user.click(screen.getByRole('button', { name: /группа/i }))

    expect(screen.getByLabelText('current path')).toHaveTextContent('/group')
    expect(screen.getByRole('button', { name: /группа/i })).toHaveAttribute('aria-current', 'page')
  })
})
