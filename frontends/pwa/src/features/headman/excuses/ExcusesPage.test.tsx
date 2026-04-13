import { describe, it, expect } from 'vitest'
import { render, screen } from '@testing-library/react'
import { BrowserRouter } from 'react-router'
import { ExcusesPage } from './ExcusesPage'

function renderPage() {
  return render(
    <BrowserRouter>
      <ExcusesPage />
    </BrowserRouter>,
  )
}

describe('ExcusesPage (graceful degradation per D-10)', () => {
  it('Test 1: renders back link pointing to /group', () => {
    renderPage()
    const backLink = screen.getByRole('link', { name: /назад/i })
    expect(backLink).toHaveAttribute('href', '/group')
  })

  it('Test 2: renders page heading "Пропуски"', () => {
    renderPage()
    expect(screen.getByRole('heading', { level: 1, name: 'Пропуски' })).toBeInTheDocument()
  })

  it('Test 3: renders circle icon container with FileText phosphor icon', () => {
    const { container } = renderPage()
    // Icon circle container uses w-20 h-20 (80×80) rounded-full
    const circle = container.querySelector('.w-20.h-20.rounded-full')
    expect(circle).toBeTruthy()
    // Phosphor icons render as <svg>
    const svg = circle?.querySelector('svg')
    expect(svg).toBeTruthy()
  })

  it('Test 4: renders "Функция в разработке" heading and body copy', () => {
    renderPage()
    expect(screen.getByText('Функция в разработке')).toBeInTheDocument()
    expect(
      screen.getByText(/Запросы студентов на одобрение пропусков появятся здесь/i),
    ).toBeInTheDocument()
  })

  it('Test 5: renders no action buttons (no approve/reject/add buttons)', () => {
    renderPage()
    const buttons = screen.queryAllByRole('button')
    expect(buttons).toHaveLength(0)
  })
})
