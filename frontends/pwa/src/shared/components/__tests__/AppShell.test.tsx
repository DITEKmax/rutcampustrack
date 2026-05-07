import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter, Route, Routes } from 'react-router'
import { describe, expect, it, vi } from 'vitest'

import { AppShell } from '../AppShell'
import { ThemeProvider } from '@/shared/theme/ThemeProvider'

vi.mock('@/features/auth/AuthProvider', () => ({
  useAuth: () => ({
    isAuthenticated: true,
    user: { id: 1, role: 'STUDENT', groupId: 5, isHeadman: false },
    accessToken: 'token',
    isBootstrapping: false,
    login: vi.fn(),
    logout: vi.fn(),
  }),
}))

vi.mock('@/features/notifications/NotificationCenter', () => ({
  useNotificationCenter: () => ({
    unreadCount: 0,
  }),
}))

function renderShell(initialEntry: string) {
  render(
    <MemoryRouter initialEntries={[initialEntry]}>
      <Routes>
        <Route
          path="/"
          element={
            <ThemeProvider>
              <AppShell />
            </ThemeProvider>
          }
        >
          <Route path="checkin" element={<div data-testid="checkin-page">Отметка</div>} />
          <Route path="schedule" element={<div data-testid="schedule-page">Расписание</div>} />
          <Route path="homework" element={<div data-testid="homework-page">Домашние задания</div>} />
          <Route path="late-checkin" element={<div data-testid="late-checkin-page">Запрос отметки</div>} />
          <Route path="excuses" element={<div data-testid="excuses-page">Уважительные пропуски</div>} />
        </Route>
      </Routes>
    </MemoryRouter>,
  )
}

describe('AppShell navigation', () => {
  it('opens schedule from check-in on the first bottom-nav tap', async () => {
    const user = userEvent.setup()
    renderShell('/checkin')

    expect(screen.getByTestId('checkin-page')).toBeInTheDocument()

    await user.click(screen.getByRole('link', { name: /расписание/i }))

    expect(await screen.findByTestId('schedule-page')).toBeInTheDocument()
  })

  it('opens homework from the drawer on the first menu item tap', async () => {
    const user = userEvent.setup()
    renderShell('/checkin')

    await user.click(screen.getByRole('button', { name: /открыть меню/i }))
    await user.click(screen.getByRole('link', { name: /домашние задания/i }))

    expect(await screen.findByTestId('homework-page')).toBeInTheDocument()
  })

  it('opens student request pages from the drawer', async () => {
    const user = userEvent.setup()
    renderShell('/checkin')

    await user.click(screen.getByRole('button', { name: /открыть меню/i }))
    await user.click(screen.getByRole('link', { name: /запрос отметки/i }))
    expect(await screen.findByTestId('late-checkin-page')).toBeInTheDocument()

    await user.click(screen.getByRole('button', { name: /открыть меню/i }))
    await user.click(screen.getByRole('link', { name: /уважительные пропуски/i }))
    expect(await screen.findByTestId('excuses-page')).toBeInTheDocument()
  })
})
