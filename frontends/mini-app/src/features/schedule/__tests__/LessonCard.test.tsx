import { fireEvent, render, screen } from '@testing-library/react'
import { describe, expect, it, vi } from 'vitest'
import { LessonCard } from '../LessonCard'
import type { LessonResponse } from '../types'

function activeLesson(): LessonResponse {
  return {
    id: 77,
    scheduleItemId: 7,
    groupId: 5,
    subjectId: 42,
    date: '2026-05-12',
    status: 'ACTIVE',
    dayOfWeek: 2,
    lessonNumber: 1,
    startTime: '09:00:00',
    endTime: '10:30:00',
    weekType: 'ALL',
    room: 'A-305',
    geoBlocked: false,
    createdAt: '2026-05-12T06:00:00Z',
  }
}

describe('LessonCard', () => {
  it('shows check-in copy and triggers check-in directly for an active lesson', () => {
    const onCheckin = vi.fn()

    render(
      <LessonCard
        lesson={activeLesson()}
        subjectName="Математика"
        personalStatus={null}
        onCheckin={onCheckin}
      />,
    )

    fireEvent.click(screen.getByRole('button', { name: /отметиться на паре/i }))

    expect(screen.getByText('Отметиться')).toBeInTheDocument()
    expect(onCheckin).toHaveBeenCalledWith(77)
  })
})
