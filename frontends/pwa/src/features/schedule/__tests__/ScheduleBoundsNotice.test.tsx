import { describe, it, expect, vi } from 'vitest'
import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { ScheduleBoundsNotice } from '../ScheduleBoundsNotice'

describe('ScheduleBoundsNotice', () => {
  it('before-start — shows semester start date', () => {
    render(
      <ScheduleBoundsNotice
        kind="before-start"
        activeFrom={new Date(2026, 1, 1)} // 2026-02-01
      />,
    )
    expect(screen.getByText(/Семестр ещё не начался/)).toBeInTheDocument()
    expect(screen.getByText(/1 февраля 2026/)).toBeInTheDocument()
  })

  it('after-end — shows end date and next-semester date if present', () => {
    render(
      <ScheduleBoundsNotice
        kind="after-end"
        activeTo={new Date(2026, 5, 30)}
        nextFrom={new Date(2026, 8, 1)}
      />,
    )
    // Title "Семестр закончился" и описание дублируют заголовок — ищем по
    // роли, чтобы не конфликтовать с текстом в <p>.
    expect(
      screen.getByRole('heading', { name: /Семестр закончился/ }),
    ).toBeInTheDocument()
    expect(
      screen.getByText(/30 июня 2026.*1 сентября 2026/),
    ).toBeInTheDocument()
  })

  it('no-active — generic vacation message', () => {
    render(<ScheduleBoundsNotice kind="no-active" />)
    expect(screen.getByText(/Сейчас каникулы/)).toBeInTheDocument()
    expect(screen.getByText(/пока не назначен/)).toBeInTheDocument()
  })

  it('renders return button when onReturn is provided', async () => {
    const onReturn = vi.fn()
    render(
      <ScheduleBoundsNotice
        kind="after-end"
        activeTo={new Date(2026, 5, 30)}
        onReturn={onReturn}
      />,
    )
    const btn = screen.getByRole('button', { name: /Вернуться к сегодня/ })
    await userEvent.click(btn)
    expect(onReturn).toHaveBeenCalledTimes(1)
  })

  it('does not render return button when onReturn is absent', () => {
    render(
      <ScheduleBoundsNotice
        kind="after-end"
        activeTo={new Date(2026, 5, 30)}
      />,
    )
    expect(screen.queryByRole('button', { name: /Вернуться к сегодня/ })).toBeNull()
  })
})
