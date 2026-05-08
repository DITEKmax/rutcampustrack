import { act, createElement, type ReactNode } from 'react'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { renderHook, waitFor } from '@testing-library/react'
import { beforeEach, describe, expect, it, vi } from 'vitest'

vi.mock('@/shared/lib/axios', () => ({
  apiClient: {
    get: vi.fn(),
    patch: vi.fn(),
    post: vi.fn(),
    put: vi.fn(),
    delete: vi.fn(),
  },
}))

import { apiClient } from '@/shared/lib/axios'
import {
  useAssistants,
  useCreateAssistant,
  useCreateSubject,
  useDeleteAssistant,
  useDeleteSubject,
  useDecideExcuse,
  useDecideLateCheckin,
  useGroupExcuses,
  useGroupMembers,
  useGroupSubjects,
  useGroupTeachers,
  useDownloadHeadmanWeeklyReport,
  useExportHeadmanWeeklyReport,
  useHeadmanWeeklyReportWeeks,
  useJournal,
  useMarkAttendance,
  usePendingExcuses,
  usePendingLateCheckinRequests,
  useResolveThreshold,
  useSetSubjectThreshold,
  useTodayLesson,
  useUpdateSubject,
} from '../headmanApi'

function createWrapper() {
  const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } })
  return ({ children }: { children: ReactNode }) =>
    createElement(QueryClientProvider, { client: queryClient }, children)
}

describe('headmanApi', () => {
  beforeEach(() => {
    vi.clearAllMocks()
  })

  it('fetches and normalizes group members', async () => {
    ;(apiClient.get as ReturnType<typeof vi.fn>).mockResolvedValue({
      data: {
        _embedded: {
          userResponseList: [
            { id: 1, login: 's1', fullName: 'Иванов Иван', headman: true },
            { id: 2, login: 's2', firstName: 'Петр', lastName: 'Петров' },
          ],
        },
      },
    })

    const { result } = renderHook(() => useGroupMembers(), { wrapper: createWrapper() })

    await waitFor(() => expect(result.current.isSuccess).toBe(true))
    expect(result.current.data).toEqual([
      { id: 1, login: 's1', fullName: 'Иванов Иван', isHeadman: true, isAssistant: false },
      { id: 2, login: 's2', fullName: 'Петров Петр', isHeadman: false, isAssistant: false },
    ])
    expect(apiClient.get).toHaveBeenCalledWith('/academic/groups/my/members', {
      params: { page: 0, size: 100 },
    })
  })

  it('fetches and normalizes group subjects', async () => {
    ;(apiClient.get as ReturnType<typeof vi.fn>).mockResolvedValue({
      data: {
        _embedded: {
          subjectResponseList: [
            { id: 10, name: 'Физика', type: 'LAB', groupId: 5, teacherIds: [7] },
          ],
        },
      },
    })

    const { result } = renderHook(() => useGroupSubjects(), { wrapper: createWrapper() })

    await waitFor(() => expect(result.current.isSuccess).toBe(true))
    expect(result.current.data).toEqual([
      {
        id: 10,
        name: 'Физика',
        type: 'LAB',
        groupId: 5,
        teacherIds: [7],
        createdAt: null,
      },
    ])
    expect(apiClient.get).toHaveBeenCalledWith('/academic/subjects', {
      params: { page: 0, size: 100 },
    })
  })

  it('fetches teachers', async () => {
    ;(apiClient.get as ReturnType<typeof vi.fn>).mockResolvedValue({
      data: {
        _embedded: {
          userResponseList: [
            { id: 7, login: 'teacher1', role: 'TEACHER', fullName: 'Павлов Петр' },
          ],
        },
      },
    })

    const { result } = renderHook(() => useGroupTeachers(), { wrapper: createWrapper() })

    await waitFor(() => expect(result.current.isSuccess).toBe(true))
    expect(result.current.data?.[0]).toMatchObject({
      id: 7,
      login: 'teacher1',
      role: 'TEACHER',
      fullName: 'Павлов Петр',
    })
    expect(apiClient.get).toHaveBeenCalledWith('/academic/users/teachers')
  })

  it('picks active lesson for overview', async () => {
    ;(apiClient.get as ReturnType<typeof vi.fn>).mockResolvedValue({
      data: {
        _embedded: {
          lessonResponseList: [
            { id: 1, status: 'PLANNED', startTime: '12:20:00', endTime: '13:50:00', room: '201' },
            { id: 2, status: 'ACTIVE', startTime: '10:40:00', endTime: '12:10:00', room: '101' },
          ],
        },
      },
    })

    const { result } = renderHook(() => useTodayLesson(5), { wrapper: createWrapper() })

    await waitFor(() => expect(result.current.isSuccess).toBe(true))
    expect(result.current.data).toMatchObject({
      lessonId: 2,
      startsAt: '10:40:00',
      endsAt: '12:10:00',
      room: '101',
    })
  })

  it('fetches and flattens journal cells', async () => {
    ;(apiClient.get as ReturnType<typeof vi.fn>).mockResolvedValue({
      data: {
        groupId: 5,
        subjectId: 10,
        students: [
          {
            userId: 7,
            displayName: 'Иван Иванов',
            records: [
              {
                lessonId: 44,
                date: '2026-05-08',
                lessonNumber: 2,
                status: 'PRESENT',
                symbol: '+',
                excuseReason: null,
              },
            ],
          },
        ],
      },
    })

    const { result } = renderHook(() => useJournal(5, 10, '2026-05-08', '2026-05-08'), {
      wrapper: createWrapper(),
    })

    await waitFor(() => expect(result.current.isSuccess).toBe(true))
    expect(result.current.data).toEqual([
      {
        lessonId: 44,
        studentId: 7,
        studentName: 'Иван Иванов',
        date: '2026-05-08',
        lessonNumber: 2,
        status: 'present',
        symbol: '+',
        excuseReason: null,
      },
    ])
    expect(apiClient.get).toHaveBeenCalledWith('/attendance/reports/journal', {
      params: {
        groupId: 5,
        subjectId: 10,
        dateFrom: '2026-05-08',
        dateTo: '2026-05-08',
      },
    })
  })

  it('marks attendance in backend enum case', async () => {
    ;(apiClient.put as ReturnType<typeof vi.fn>).mockResolvedValue({ data: undefined })

    const { result } = renderHook(() => useMarkAttendance(), { wrapper: createWrapper() })

    await act(async () => {
      await result.current.mutateAsync({
        lessonId: 44,
        studentId: 7,
        status: 'free_attendance',
      })
    })

    expect(apiClient.put).toHaveBeenCalledWith('/attendance/lessons/44/students/7', {
      status: 'FREE_ATTENDANCE',
    })
  })

  it('resolves subject threshold', async () => {
    ;(apiClient.get as ReturnType<typeof vi.fn>).mockResolvedValue({
      data: { minPercentage: 80, level: 'SUBJECT', sourceId: 10 },
    })

    const { result } = renderHook(() => useResolveThreshold(5, 10), {
      wrapper: createWrapper(),
    })

    await waitFor(() => expect(result.current.isSuccess).toBe(true))
    expect(result.current.data).toEqual({
      minPercentage: 80,
      level: 'SUBJECT',
      sourceId: 10,
    })
    expect(apiClient.get).toHaveBeenCalledWith('/academic/thresholds/resolve', {
      params: { groupId: 5, subjectId: 10 },
    })
  })

  it('sets subject threshold', async () => {
    ;(apiClient.put as ReturnType<typeof vi.fn>).mockResolvedValue({
      data: { id: 12, subjectId: 10, minPercentage: 82 },
    })

    const { result } = renderHook(() => useSetSubjectThreshold(), {
      wrapper: createWrapper(),
    })

    await act(async () => {
      await result.current.mutateAsync({ subjectId: 10, minPercentage: 82 })
    })

    expect(apiClient.put).toHaveBeenCalledWith(
      '/academic/thresholds/subject',
      { minPercentage: 82 },
      { params: { subjectId: 10 } },
    )
  })

  it('fetches headman weekly report weeks', async () => {
    ;(apiClient.get as ReturnType<typeof vi.fn>).mockResolvedValue({
      data: {
        semesterId: 2,
        semesterName: 'Весна',
        weeks: [
          {
            weekOfSemester: 1,
            isoWeek: 18,
            label: 'Н1',
            weekStart: '2026-04-27',
            weekEnd: '2026-05-03',
            current: true,
          },
        ],
      },
    })

    const { result } = renderHook(() => useHeadmanWeeklyReportWeeks(true), {
      wrapper: createWrapper(),
    })

    await waitFor(() => expect(result.current.isSuccess).toBe(true))
    expect(result.current.data?.weeks[0]).toMatchObject({
      weekOfSemester: 1,
      weekStart: '2026-04-27',
      current: true,
    })
    expect(apiClient.get).toHaveBeenCalledWith('/attendance/reports/headman-weekly/weeks')
  })

  it('downloads one headman weekly report as blob', async () => {
    const blob = new Blob(['doc'])
    ;(apiClient.get as ReturnType<typeof vi.fn>).mockResolvedValue({
      data: blob,
      headers: { 'content-disposition': 'attachment; filename="weekly.docx"' },
    })

    const { result } = renderHook(() => useDownloadHeadmanWeeklyReport(), {
      wrapper: createWrapper(),
    })

    await act(async () => {
      await result.current.mutateAsync({ weekStart: '2026-04-27', format: 'docx' })
    })

    expect(apiClient.get).toHaveBeenCalledWith(
      '/attendance/reports/headman-weekly/current',
      {
        params: { weekStart: '2026-04-27', format: 'docx' },
        responseType: 'blob',
      },
    )
  })

  it('exports selected headman weekly reports as blob', async () => {
    const blob = new Blob(['zip'])
    ;(apiClient.post as ReturnType<typeof vi.fn>).mockResolvedValue({
      data: blob,
      headers: {},
    })

    const { result } = renderHook(() => useExportHeadmanWeeklyReport(), {
      wrapper: createWrapper(),
    })

    await act(async () => {
      await result.current.mutateAsync({
        weekStarts: ['2026-04-27', '2026-05-04'],
        format: 'pdf',
      })
    })

    expect(apiClient.post).toHaveBeenCalledWith(
      '/attendance/reports/headman-weekly/export',
      { weekStarts: ['2026-04-27', '2026-05-04'], format: 'pdf' },
      { responseType: 'blob' },
    )
  })

  it('returns empty pending excuses on 404', async () => {
    ;(apiClient.get as ReturnType<typeof vi.fn>).mockRejectedValue({
      response: { status: 404 },
    })

    const { result } = renderHook(() => usePendingExcuses(5), { wrapper: createWrapper() })

    await waitFor(() => expect(result.current.isSuccess).toBe(true))
    expect(result.current.data).toEqual([])
  })

  it('fetches and normalizes group excuse tickets', async () => {
    ;(apiClient.get as ReturnType<typeof vi.fn>).mockResolvedValue({
      data: {
        _embedded: {
          excuseTicketResponseList: [
            {
              id: 'ticket-1',
              studentId: 7,
              groupId: 5,
              studentName: 'Иван Иванов',
              lessonIds: [11, 12],
              excuseType: 'ILLNESS',
              comment: 'Справка',
              status: 'SUBMITTED',
              createdAt: '2026-05-08T08:00:00Z',
              updatedAt: '2026-05-08T08:00:00Z',
            },
          ],
        },
      },
    })

    const { result } = renderHook(() => useGroupExcuses(5), { wrapper: createWrapper() })

    await waitFor(() => expect(result.current.isSuccess).toBe(true))
    expect(result.current.data?.[0]).toMatchObject({
      id: 'ticket-1',
      studentName: 'Иван Иванов',
      status: 'submitted',
      comment: 'Справка',
      decisionComment: null,
    })
    expect(apiClient.get).toHaveBeenCalledWith('/attendance/excuses/group/5', {
      params: { page: 0, size: 100, status: undefined },
    })
  })

  it('sends headman excuse decision in backend enum case', async () => {
    ;(apiClient.patch as ReturnType<typeof vi.fn>).mockResolvedValue({ data: {} })

    const { result } = renderHook(() => useDecideExcuse(), { wrapper: createWrapper() })

    await act(async () => {
      await result.current.mutateAsync({
        id: 'ticket-1',
        approved: false,
        decisionComment: 'Нет документа',
      })
    })

    expect(apiClient.patch).toHaveBeenCalledWith('/attendance/excuses/ticket-1/status', {
      status: 'REJECTED',
      decisionComment: 'Нет документа',
    })
  })

  it('fetches group late checkin requests', async () => {
    ;(apiClient.get as ReturnType<typeof vi.fn>).mockResolvedValue({
      data: {
        _embedded: {
          lateCheckinRequestResponseList: [
            {
              id: 'request-1',
              studentId: 9,
              groupId: 5,
              lessonId: 44,
              studentName: 'Анна Петрова',
              status: 'PENDING',
              createdAt: '2026-05-08T08:00:00Z',
              updatedAt: '2026-05-08T08:00:00Z',
            },
          ],
        },
      },
    })

    const { result } = renderHook(() => usePendingLateCheckinRequests(5), {
      wrapper: createWrapper(),
    })

    await waitFor(() => expect(result.current.isSuccess).toBe(true))
    expect(result.current.data?.[0]).toMatchObject({
      id: 'request-1',
      lessonId: 44,
      status: 'pending',
      decisionAt: null,
    })
    expect(apiClient.get).toHaveBeenCalledWith('/attendance/late-checkin/group/5', {
      params: { page: 0, size: 100 },
    })
  })

  it('sends late checkin decision', async () => {
    ;(apiClient.post as ReturnType<typeof vi.fn>).mockResolvedValue({ data: {} })

    const { result } = renderHook(() => useDecideLateCheckin(), { wrapper: createWrapper() })

    await act(async () => {
      await result.current.mutateAsync({ id: 'request-1', approved: true })
    })

    expect(apiClient.post).toHaveBeenCalledWith('/attendance/late-checkin/request-1/decision', {
      approved: true,
    })
  })

  it('fetches assistants for the headman group', async () => {
    ;(apiClient.get as ReturnType<typeof vi.fn>).mockResolvedValue({
      data: {
        _embedded: {
          assistantResponseList: [
            {
              id: 15,
              studentId: 9,
              groupId: 5,
              fullName: 'Анна Петрова',
              login: 'student00009',
              permissions: ['MARK_ATTENDANCE'],
              active: true,
            },
          ],
        },
      },
    })

    const { result } = renderHook(() => useAssistants(5), { wrapper: createWrapper() })

    await waitFor(() => expect(result.current.isSuccess).toBe(true))
    expect(result.current.data).toEqual([
      {
        id: 15,
        studentId: 9,
        groupId: 5,
        fullName: 'Анна Петрова',
        login: 'student00009',
        permissions: ['MARK_ATTENDANCE'],
        active: true,
      },
    ])
    expect(apiClient.get).toHaveBeenCalledWith('/academic/assistants', {
      params: { groupId: 5 },
    })
  })

  it('creates assistant with group id and permissions', async () => {
    ;(apiClient.post as ReturnType<typeof vi.fn>).mockResolvedValue({
      data: {
        id: 15,
        studentId: 9,
        groupId: 5,
        fullName: 'Анна Петрова',
        permissions: ['MARK_ATTENDANCE'],
      },
    })

    const { result } = renderHook(() => useCreateAssistant(), { wrapper: createWrapper() })

    await act(async () => {
      await result.current.mutateAsync({
        studentId: 9,
        groupId: 5,
        permissions: ['MARK_ATTENDANCE'],
      })
    })

    expect(apiClient.post).toHaveBeenCalledWith('/academic/assistants', {
      studentId: 9,
      groupId: 5,
      permissions: ['MARK_ATTENDANCE'],
    })
  })

  it('deletes assistant by assistant id', async () => {
    ;(apiClient.delete as ReturnType<typeof vi.fn>).mockResolvedValue({ data: undefined })

    const { result } = renderHook(() => useDeleteAssistant(), { wrapper: createWrapper() })

    await act(async () => {
      await result.current.mutateAsync(15)
    })

    expect(apiClient.delete).toHaveBeenCalledWith('/academic/assistants/15')
  })

  it('creates subject with type and teacher ids', async () => {
    ;(apiClient.post as ReturnType<typeof vi.fn>).mockResolvedValue({
      data: { id: 10, name: 'Физика', type: 'LAB', groupId: 5, teacherIds: [7] },
    })

    const { result } = renderHook(() => useCreateSubject(), { wrapper: createWrapper() })

    await act(async () => {
      await result.current.mutateAsync({ name: 'Физика', type: 'LAB', teacherIds: [7] })
    })

    expect(apiClient.post).toHaveBeenCalledWith('/academic/subjects', {
      name: 'Физика',
      type: 'LAB',
      teacherIds: [7],
    })
  })

  it('updates subject and syncs teacher assignments', async () => {
    ;(apiClient.put as ReturnType<typeof vi.fn>).mockResolvedValue({
      data: { id: 10, name: 'Физика', type: 'PRACTICE', groupId: 5, teacherIds: [8] },
    })
    ;(apiClient.delete as ReturnType<typeof vi.fn>).mockResolvedValue({ data: undefined })
    ;(apiClient.post as ReturnType<typeof vi.fn>).mockResolvedValue({ data: undefined })

    const { result } = renderHook(() => useUpdateSubject(), { wrapper: createWrapper() })

    await act(async () => {
      await result.current.mutateAsync({
        id: 10,
        name: 'Физика',
        type: 'PRACTICE',
        previousTeacherIds: [7],
        teacherIds: [8],
      })
    })

    expect(apiClient.put).toHaveBeenCalledWith('/academic/subjects/10', {
      name: 'Физика',
      type: 'PRACTICE',
    })
    expect(apiClient.delete).toHaveBeenCalledWith('/academic/subjects/10/teachers/7')
    expect(apiClient.post).toHaveBeenCalledWith('/academic/subjects/10/teachers/8')
  })

  it('deletes subject by id', async () => {
    ;(apiClient.delete as ReturnType<typeof vi.fn>).mockResolvedValue({ data: undefined })

    const { result } = renderHook(() => useDeleteSubject(), { wrapper: createWrapper() })

    await act(async () => {
      await result.current.mutateAsync(10)
    })

    expect(apiClient.delete).toHaveBeenCalledWith('/academic/subjects/10')
  })
})
