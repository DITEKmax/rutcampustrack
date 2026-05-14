import { fireEvent, render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { HeadmanLessonSheet } from '../HeadmanLessonSheet'
import type { LessonResponse } from '../types'

const markAttendanceMutation = vi.hoisted(() => vi.fn())
const markBatchMutation = vi.hoisted(() => vi.fn())
const cancelLessonMutation = vi.hoisted(() => vi.fn())
const blockLessonMutation = vi.hoisted(() => vi.fn())
const unblockLessonMutation = vi.hoisted(() => vi.fn())

vi.mock('../headmanSheetApi', () => ({
  useLessonAttendance: () => ({
    data: {
      entries: [
        {
          userId: 10,
          displayName: 'Иван Петров',
          status: 'absent',
          symbol: 'н',
          source: null,
          excuseReason: null,
        },
      ],
    },
    isLoading: false,
    isError: false,
  }),
  useHeadmanMarkAttendance: () => ({
    mutateAsync: markAttendanceMutation,
    isPending: false,
  }),
  useHeadmanMarkBatch: () => ({
    mutateAsync: markBatchMutation,
    isPending: false,
  }),
  useCancelLesson: () => ({
    mutateAsync: cancelLessonMutation,
    isPending: false,
  }),
  useBlockLesson: () => ({
    mutateAsync: blockLessonMutation,
    isPending: false,
  }),
  useUnblockLesson: () => ({
    mutateAsync: unblockLessonMutation,
    isPending: false,
  }),
  mapLessonActionError: () => 'Ошибка отмены',
}))

const lesson: LessonResponse = {
  id: 44,
  scheduleItemId: 100,
  groupId: 5,
  subjectId: 10,
  date: '2026-05-08',
  status: 'ACTIVE',
  dayOfWeek: 5,
  lessonNumber: 2,
  startTime: '10:40:00',
  endTime: '12:10:00',
  weekType: 'ALL',
  room: '301',
  geoBlocked: false,
  createdAt: '2026-05-08T00:00:00Z',
}

describe('HeadmanLessonSheet', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    markAttendanceMutation.mockResolvedValue(undefined)
    markBatchMutation.mockResolvedValue(undefined)
    cancelLessonMutation.mockResolvedValue(undefined)
    blockLessonMutation.mockResolvedValue(undefined)
    unblockLessonMutation.mockResolvedValue(undefined)
  })

  it('opens as full-screen sheet and ignores Escape close', () => {
    const onClose = vi.fn()

    render(
      <HeadmanLessonSheet
        open
        lesson={lesson}
        subjectName="Алгоритмы"
        onClose={onClose}
        onToast={vi.fn()}
      />,
    )

    expect(screen.getByRole('dialog', { name: 'Действия с парой' })).toBeInTheDocument()
    expect(screen.getByText('Алгоритмы')).toBeInTheDocument()

    fireEvent.keyDown(window, { key: 'Escape' })
    expect(onClose).not.toHaveBeenCalled()

    fireEvent.click(screen.getByLabelText('Закрыть'))
    expect(onClose).toHaveBeenCalledTimes(1)
  })

  it('marks a student from roster', async () => {
    const user = userEvent.setup()

    render(
      <HeadmanLessonSheet
        open
        lesson={lesson}
        subjectName="Алгоритмы"
        onClose={vi.fn()}
        onToast={vi.fn()}
      />,
    )

    await user.click(screen.getByRole('button', { name: '+' }))

    await waitFor(() => {
      expect(markAttendanceMutation).toHaveBeenCalledWith({
        lessonId: 44,
        userId: 10,
        status: 'present',
      })
    })
  })

  it('cancels a lesson from manage tab with trimmed reason', async () => {
    const user = userEvent.setup()
    const onClose = vi.fn()
    const onToast = vi.fn()

    render(
      <HeadmanLessonSheet
        open
        lesson={lesson}
        subjectName="Алгоритмы"
        onClose={onClose}
        onToast={onToast}
      />,
    )

    await user.click(screen.getByRole('button', { name: 'Управление' }))
    await user.type(screen.getByLabelText('Причина отмены'), '  Преподаватель заболел  ')
    await user.click(screen.getByRole('button', { name: 'Отменить пару' }))

    await waitFor(() => {
      expect(cancelLessonMutation).toHaveBeenCalledWith({
        lessonId: 44,
        reason: 'Преподаватель заболел',
      })
    })
    expect(onToast).toHaveBeenCalledWith('success', 'Пара отменена.')
    expect(onClose).toHaveBeenCalledTimes(1)
  })

  it('blocks an active lesson from manage tab', async () => {
    const user = userEvent.setup()
    const onToast = vi.fn()
    const onLessonUpdated = vi.fn()
    const activeLesson = { ...lesson, status: 'ACTIVE' as const }

    render(
      <HeadmanLessonSheet
        open
        lesson={activeLesson}
        subjectName="Алгоритмы"
        onClose={vi.fn()}
        onToast={onToast}
        onLessonUpdated={onLessonUpdated}
      />,
    )

    await user.click(screen.getByRole('button', { name: 'Управление' }))
    await user.click(screen.getByRole('button', { name: /Заблокировать самостоятельную отметку/i }))

    await waitFor(() => {
      expect(blockLessonMutation).toHaveBeenCalledWith(44)
    })
    expect(onLessonUpdated).toHaveBeenCalledWith({ ...activeLesson, blockedByHeadman: true })
    expect(onToast).toHaveBeenCalledWith('success', 'Пара заблокирована.')
  })
})
