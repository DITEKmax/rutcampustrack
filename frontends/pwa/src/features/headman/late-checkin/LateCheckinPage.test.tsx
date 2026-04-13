import { describe, it, expect } from 'vitest'
import { render, screen } from '@testing-library/react'
import { BrowserRouter } from 'react-router'
import { LateCheckinPage } from './LateCheckinPage'

function renderPage() {
  return render(
    <BrowserRouter>
      <LateCheckinPage />
    </BrowserRouter>,
  )
}

describe('LateCheckinPage (graceful degradation per D-10)', () => {
  it('Test 1: renders back link pointing to /group', () => {
    renderPage()
    const backLink = screen.getByRole('link', { name: /назад/i })
    expect(backLink).toHaveAttribute('href', '/group')
  })

  it('Test 2: renders page heading "Запросы отметки"', () => {
    renderPage()
    expect(screen.getByRole('heading', { level: 1, name: 'Запросы отметки' })).toBeInTheDocument()
  })

  it('Test 3: renders circle icon container with Clock phosphor icon', () => {
    const { container } = renderPage()
    const circle = container.querySelector('.w-20.h-20.rounded-full')
    expect(circle).toBeTruthy()
    const svg = circle?.querySelector('svg')
    expect(svg).toBeTruthy()
  })

  it('Test 4: renders "Функция в разработке" heading and body copy', () => {
    renderPage()
    expect(screen.getByText('Функция в разработке')).toBeInTheDocument()
    expect(
      screen.getByText(/Запросы студентов на опоздалую отметку появятся здесь/i),
    ).toBeInTheDocument()
  })

  it('Test 5: renders no action buttons', () => {
    renderPage()
    const buttons = screen.queryAllByRole('button')
    expect(buttons).toHaveLength(0)
  })
})
