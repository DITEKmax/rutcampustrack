import { render, screen } from '@testing-library/react'
import { describe, expect, it } from 'vitest'
import type { TopMissedSubject } from './api'
import { TopMissedList } from './TopMissedList'

describe('TopMissedList', () => {
  it('renders short subject type labels beside top missed subjects', () => {
    const items: TopMissedSubject[] = [
      {
        subjectId: 1,
        subjectName: 'Математика',
        subjectType: 'lecture',
        absent: 3,
        total: 10,
      },
      {
        subjectId: 2,
        subjectName: 'Физика',
        subjectType: 'lab',
        absent: 2,
        total: 8,
      },
    ]

    render(<TopMissedList items={items} />)

    expect(screen.getByText('Математика')).toBeInTheDocument()
    expect(screen.getByText('ЛК')).toBeInTheDocument()
    expect(screen.getByText('Физика')).toBeInTheDocument()
    expect(screen.getByText('ЛЗ')).toBeInTheDocument()
  })
})
