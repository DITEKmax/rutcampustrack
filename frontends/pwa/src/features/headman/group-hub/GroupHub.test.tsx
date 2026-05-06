import { describe, it, expect, vi, beforeEach } from 'vitest'
import { render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { BrowserRouter } from 'react-router'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { GroupHub } from './GroupHub'

const testState = vi.hoisted(() => ({
  isHeadman: true,
  downloadMutateAsync: vi.fn(),
  exportMutateAsync: vi.fn(),
  journalQueries: [] as Array<{
    data: Array<{
      lessonId: number
      studentId: number
      studentName: string
      date: string
      status: 'present' | 'absent' | 'excused' | 'free_attendance' | 'cancelled'
    }>
    isLoading: boolean
    isError: boolean
  }>,
}))

vi.mock('@/shared/lib/axios', () => ({
  apiClient: {
    get: vi.fn(),
    post: vi.fn(),
    put: vi.fn(),
    delete: vi.fn(),
    interceptors: {
      request: { use: vi.fn() },
      response: { use: vi.fn() },
    },
  },
  setAccessTokenGetter: vi.fn(),
  setTokenRefreshCallback: vi.fn(),
  setAuthLogoutCallback: vi.fn(),
}))

vi.mock('@/features/headman/shared/headmanApi', () => ({
  useGroupMembers: vi.fn(() => ({ data: [{ id: 1, fullName: 'Иванов Иван' }, { id: 2, fullName: 'Петров Петр' }], isLoading: false }),),
  useGroupSubjects: vi.fn(() => ({ data: [{ id: 1, name: 'Математика' }, { id: 2, name: 'Физика' }, { id: 3, name: 'История' }], isLoading: false }),),
  useSubjectJournals: vi.fn(() => testState.journalQueries),
  useHeadmanWeeklyReportWeeks: vi.fn(() => ({
    data: {
      semesterId: 1,
      semesterName: 'Spring',
      semesterDateFrom: '2026-04-29',
      semesterDateTo: '2026-05-17',
      weeks: [
        { weekOfSemester: 1, isoWeek: 18, label: 'Н1', weekStart: '2026-04-27', weekEnd: '2026-05-03', current: false },
        { weekOfSemester: 2, isoWeek: 19, label: 'Н2', weekStart: '2026-05-04', weekEnd: '2026-05-10', current: true },
        { weekOfSemester: 3, isoWeek: 20, label: 'Н3', weekStart: '2026-05-11', weekEnd: '2026-05-17', current: false },
      ],
    },
    isLoading: false,
    isError: false,
  })),
  useDownloadHeadmanWeeklyReport: vi.fn(() => ({
    mutateAsync: testState.downloadMutateAsync,
    isPending: false,
  })),
  useExportHeadmanWeeklyReport: vi.fn(() => ({
    mutateAsync: testState.exportMutateAsync,
    isPending: false,
  })),
}))

vi.mock('@/features/auth/AuthProvider', () => ({
  useAuth: vi.fn(() => ({
    user: { id: 1, role: 'STUDENT', groupId: 5, isHeadman: testState.isHeadman },
  })),
}))

function renderGroupHub() {
  const queryClient = new QueryClient({
    defaultOptions: { queries: { retry: false } },
  })
  return render(
    <BrowserRouter>
      <QueryClientProvider client={queryClient}>
        <GroupHub />
      </QueryClientProvider>
    </BrowserRouter>,
  )
}

describe('GroupHub', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    testState.isHeadman = true
    testState.journalQueries = [
      {
        data: [
          { lessonId: 1, studentId: 1, studentName: 'Иванов Иван', date: '2026-05-04', status: 'present' },
          { lessonId: 2, studentId: 1, studentName: 'Иванов Иван', date: '2026-05-05', status: 'absent' },
          { lessonId: 3, studentId: 2, studentName: 'Петров Петр', date: '2026-05-05', status: 'excused' },
        ],
        isLoading: false,
        isError: false,
      },
      {
        data: [
          { lessonId: 4, studentId: 1, studentName: 'Иванов Иван', date: '2026-05-06', status: 'free_attendance' },
          { lessonId: 5, studentId: 1, studentName: 'Иванов Иван', date: '2026-05-07', status: 'present' },
          { lessonId: 6, studentId: 2, studentName: 'Петров Петр', date: '2026-05-07', status: 'free_attendance' },
        ],
        isLoading: false,
        isError: false,
      },
      {
        data: [],
        isLoading: false,
        isError: false,
      },
    ]
    testState.downloadMutateAsync.mockResolvedValue({
      data: new Blob(['docx']),
      headers: { 'content-disposition': "attachment; filename*=UTF-8''report.docx" },
    })
    testState.exportMutateAsync.mockResolvedValue({
      data: new Blob(['zip']),
      headers: { 'content-disposition': "attachment; filename*=UTF-8''report.zip" },
    })
    Object.defineProperty(URL, 'createObjectURL', {
      value: vi.fn(() => 'blob:report'),
      configurable: true,
    })
    Object.defineProperty(URL, 'revokeObjectURL', {
      value: vi.fn(),
      configurable: true,
    })
  })

  it('Test 1: renders a heading "Группа"', () => {
    renderGroupHub()
    expect(screen.getByRole('heading', { name: 'Группа' })).toBeInTheDocument()
  })

  it('Test 2: renders exactly 7 navigation links with correct hrefs', () => {
    renderGroupHub()
    const links = screen.getAllByRole('link')
    expect(links).toHaveLength(7)

    const hrefs = links.map((l) => l.getAttribute('href'))
    expect(hrefs).toEqual(
      expect.arrayContaining([
        '/group/overview',
        '/group/students',
        '/group/subjects',
        '/group/journal',
        '/group/excuses',
        '/group/late-checkin',
        '/group/stats',
      ]),
    )
  })

  it('Test 3: each card shows the correct Russian title', () => {
    renderGroupHub()
    expect(screen.getByText('Обзор')).toBeInTheDocument()
    expect(screen.getByText('Студенты')).toBeInTheDocument()
    expect(screen.getByText('Предметы')).toBeInTheDocument()
    expect(screen.getByText('Журнал')).toBeInTheDocument()
    expect(screen.getByText('Пропуски')).toBeInTheDocument()
    expect(screen.getByText('Запросы отметки')).toBeInTheDocument()
    expect(screen.getByText('Статистика')).toBeInTheDocument()
  })

  it('Test 4: renders headman reports block for headman', () => {
    renderGroupHub()
    expect(screen.getByRole('heading', { name: 'Отчеты старосты' })).toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'Скачать неделю' })).toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'Скачать несколько недель' })).toBeInTheDocument()
  })

  it('Test 5: hides reports block for non-headman user', () => {
    testState.isHeadman = false
    renderGroupHub()
    expect(screen.queryByRole('heading', { name: 'Отчеты старосты' })).not.toBeInTheDocument()
  })

  it('Test 6: renders aggregated student attendance breakdown by student, not by subject', () => {
    renderGroupHub()

    expect(screen.getByRole('heading', { name: 'Статистика по студентам' })).toBeInTheDocument()
    expect(screen.getByLabelText('Иванов: посещение 50%, уважительная причина 25%, пропуски 25%')).toBeInTheDocument()
    expect(screen.getByLabelText('Петров: посещение 0%, уважительная причина 100%, пропуски 0%')).toBeInTheDocument()
  })

  it('Test 7: downloads selected single week as blob', async () => {
    const user = userEvent.setup()
    renderGroupHub()

    await user.click(screen.getByRole('button', { name: 'Скачать неделю' }))

    await waitFor(() => {
      expect(testState.downloadMutateAsync).toHaveBeenCalledWith({
        weekStart: '2026-05-04',
        format: 'docx',
      })
    })
    expect(URL.createObjectURL).toHaveBeenCalled()
  })

  it('Test 8: exports non-consecutive weeks from bottom sheet', async () => {
    const user = userEvent.setup()
    renderGroupHub()

    await user.click(screen.getByRole('button', { name: 'Скачать несколько недель' }))
    await user.click(screen.getByLabelText(/Н1/))
    await user.click(screen.getByLabelText(/Н3/))
    await user.click(screen.getByLabelText(/Н2/))
    await user.click(screen.getByRole('button', { name: 'Скачать выбранные недели' }))

    await waitFor(() => {
      expect(testState.exportMutateAsync).toHaveBeenCalledWith({
        weekStarts: ['2026-04-27', '2026-05-11'],
        format: 'docx',
      })
    })
  })
})
