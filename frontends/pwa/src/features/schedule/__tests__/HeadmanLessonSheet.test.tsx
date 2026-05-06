import { beforeEach, describe, expect, it, vi } from 'vitest'
import { fireEvent, render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { HeadmanLessonSheet } from '../HeadmanLessonSheet'
import type { LessonResponse } from '../types'

const cancelLessonMutation = vi.hoisted(() => vi.fn())

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

vi.mock('../lessonActionsApi', () => ({
  useCancelLesson: () => ({
    mutateAsync: cancelLessonMutation,
    isPending: false,
  }),
  mapLessonActionError: () => 'Ошибка отмены',
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
  beforeEach(() => {
    cancelLessonMutation.mockReset()
    cancelLessonMutation.mockResolvedValue(undefined)
  })

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

  it('cancels a lesson from the manage tab with a trimmed reason', async () => {
    const user = userEvent.setup()
    const onClose = vi.fn()
    const onToast = vi.fn()

    render(
      <HeadmanLessonSheet
        open
        lesson={lesson}
        subjectName="Algorithms"
        onClose={onClose}
        onToast={onToast}
      />,
    )

    await user.click(screen.getByRole('button', { name: 'Управление' }))
    await user.type(
      screen.getByLabelText('Причина отмены'),
      '  Преподаватель заболел  ',
    )
    await user.click(screen.getByRole('button', { name: 'Отменить пару' }))

    await waitFor(() => {
      expect(cancelLessonMutation).toHaveBeenCalledWith({
        lessonId: lesson.id,
        reason: 'Преподаватель заболел',
      })
    })
    expect(onToast).toHaveBeenCalledWith('success', 'Пара отменена')
    expect(onClose).toHaveBeenCalledTimes(1)
  })
})
