import { fireEvent, render, screen } from '@testing-library/react'
import { MemoryRouter } from 'react-router'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { DrawerMenu } from '../DrawerMenu'

const mockUseAuth = vi.fn()

vi.mock('@/features/auth/AuthProvider', () => ({
  useAuth: () => mockUseAuth(),
}))

function renderDrawer(onClose = vi.fn()) {
  mockUseAuth.mockReturnValue({
    user: { id: 1, role: 'STUDENT', groupId: 5, isHeadman: false },
  })
  render(
    <MemoryRouter>
      <DrawerMenu open onClose={onClose} />
    </MemoryRouter>,
  )
  return onClose
}

describe('DrawerMenu', () => {
  beforeEach(() => {
    vi.clearAllMocks()
  })

  it('renders secondary Mini App routes', () => {
    renderDrawer()

    expect(screen.getByRole('dialog', { name: 'Меню' })).toBeInTheDocument()
    expect(screen.getByRole('link', { name: 'Домашние задания' })).toHaveAttribute(
      'href',
      '/homework',
    )
    expect(screen.getByRole('link', { name: 'Запрос отметки' })).toHaveAttribute(
      'href',
      '/late-checkin',
    )
    expect(screen.getByRole('link', { name: 'Уважительные пропуски' })).toHaveAttribute(
      'href',
      '/excuses',
    )
    expect(screen.getByRole('link', { name: 'Профиль' })).toHaveAttribute('href', '/profile')
  })

  it('renders headman routes for headman users', () => {
    mockUseAuth.mockReturnValue({
      user: { id: 1, role: 'STUDENT', groupId: 5, isHeadman: true },
    })
    render(
      <MemoryRouter>
        <DrawerMenu open onClose={vi.fn()} />
      </MemoryRouter>,
    )

    expect(screen.getByRole('link', { name: 'Группа' })).toHaveAttribute('href', '/group')
    expect(screen.getByRole('link', { name: 'Обзор группы' })).toHaveAttribute(
      'href',
      '/group/overview',
    )
    expect(screen.getByRole('link', { name: 'Студенты группы' })).toHaveAttribute(
      'href',
      '/group/students',
    )
    expect(screen.getByRole('link', { name: 'Предметы группы' })).toHaveAttribute(
      'href',
      '/group/subjects',
    )
    expect(screen.getByRole('link', { name: 'Журнал группы' })).toHaveAttribute(
      'href',
      '/group/journal',
    )
    expect(screen.getByRole('link', { name: 'Статистика группы' })).toHaveAttribute(
      'href',
      '/group/stats',
    )
    expect(screen.getByRole('link', { name: 'Пропуски группы' })).toHaveAttribute(
      'href',
      '/group/excuses',
    )
    expect(screen.getByRole('link', { name: 'Запросы отметки группы' })).toHaveAttribute(
      'href',
      '/group/late-checkin',
    )
  })

  it('closes from close button and nav click', () => {
    const onClose = renderDrawer()

    fireEvent.click(screen.getByLabelText('Закрыть меню'))
    fireEvent.click(screen.getByRole('link', { name: 'Профиль' }))

    expect(onClose).toHaveBeenCalledTimes(2)
  })

  it('closes on Escape', () => {
    const onClose = renderDrawer()

    fireEvent.keyDown(window, { key: 'Escape' })

    expect(onClose).toHaveBeenCalledTimes(1)
  })
})
