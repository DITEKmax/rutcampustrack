import { describe, expect, it, vi } from 'vitest'
import { fireEvent, render, screen } from '@testing-library/react'
import { HeadmanLessonSheet } from '../HeadmanLessonSheet'
import type { LessonResponse } from '../types'

vi.mock('../headmanSheetApi', () => ({
  useLessonAttendance: () => ({
    data: {
      entries: [
        {
          userId: 10,
          displayName: 'Ivan Petrov',
          status: 'absent',
          source: null,
          excuseReason: null,
        },
      ],
    },
    isLoading: false,
    error: null,
  }),
  useHeadmanMarkAttendance: () => ({
    mutateAsync: vi.fn().mockResolvedValue(undefined),
    isPending: false,
  }),
  useHeadmanMarkBatch: () => ({
    mutateAsync: vi.fn().mockResolvedValue(undefined),
    isPending: false,
  }),
}))

const lesson: LessonResponse = {
  id: 1,
  scheduleItemId: 100,
  groupId: 5,
  subjectId: 10,
  date: '2026-04-06',
  status: 'ACTIVE',
  dayOfWeek: 1,
  lessonNumber: 1,
  startTime: '09:00:00',
  endTime: '10:35:00',
  weekType: 'ALL',
  room: '301',
  geoBlocked: false,
  createdAt: '2026-04-01T00:00:00Z',
}

describe('HeadmanLessonSheet', () => {
  it('opens as a full-screen sheet and closes only by the close button', () => {
    const onClose = vi.fn()

    const { container } = render(
      <HeadmanLessonSheet
        open
        lesson={lesson}
        subjectName="Algorithms"
        onClose={onClose}
        onToast={vi.fn()}
      />,
    )

    expect(container.querySelector('[data-bottom-sheet-panel="true"]')).toBeNull()

    const panel = document.body.querySelector('[data-bottom-sheet-panel="true"]')
    expect(panel).toHaveClass('h-dvh', 'rounded-t-none')
    expect(panel).toHaveStyle({ maxHeight: '100dvh' })
    expect(screen.getByText('Algorithms')).toBeInTheDocument()
    expect(screen.getByText((text) => text.includes('09:00') && text.includes('10:35')))
      .toBeInTheDocument()

    fireEvent.click(document.body.querySelector('[data-bottom-sheet-backdrop="true"]')!)
    fireEvent.keyDown(window, { key: 'Escape' })

    expect(onClose).not.toHaveBeenCalled()

    fireEvent.click(document.body.querySelector('button[aria-label]')!)

    expect(onClose).toHaveBeenCalledTimes(1)
  })
})
