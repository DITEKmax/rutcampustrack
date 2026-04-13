import { describe, it, expect, vi, beforeEach } from 'vitest'
import { render, screen, fireEvent, waitFor } from '@testing-library/react'
import { BrowserRouter } from 'react-router'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { StatsPage } from './StatsPage'

// Mock axios to prevent real requests
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
  useGroupSubjects: vi.fn(),
  useJournal: vi.fn(),
  useResolveThreshold: vi.fn(),
  useSetSubjectThreshold: vi.fn(),
  mapHeadmanApiError: vi.fn((status: number) => {
    if (status === 403) return 'У вас нет прав на эту операцию'
    return 'Ошибка сервера. Попробуйте ещё раз'
  }),
}))

vi.mock('@/features/auth/AuthProvider', () => ({
  useAuth: vi.fn(() => ({
    user: { id: 1, role: 'STUDENT', groupId: 42, isHeadman: true },
  })),
}))

import {
  useGroupSubjects,
  useJournal,
  useResolveThreshold,
  useSetSubjectThreshold,
} from '@/features/headman/shared/headmanApi'

function renderPage() {
  const queryClient = new QueryClient({
    defaultOptions: { queries: { retry: false } },
  })
  return render(
    <BrowserRouter>
      <QueryClientProvider client={queryClient}>
        <StatsPage />
      </QueryClientProvider>
    </BrowserRouter>,
  )
}

// Helpers for journal cell fixtures
function cell(
  studentId: number,
  studentName: string,
  status: 'present' | 'absent' | 'excused' | 'free_attendance' | 'cancelled',
  lessonId = 100,
  date = '2026-03-01',
) {
  return { lessonId, studentId, studentName, date, status }
}

const subjectsFixture = [
  { id: 1, name: 'Математика', teacherId: 10, teacherName: 'Иванов' },
  { id: 2, name: 'Физика', teacherId: 11, teacherName: 'Петров' },
]

// Subject 1 (Math): 1 student, 2 present + 2 absent = 50%
const mathCells = [
  cell(101, 'Алексей', 'present', 1),
  cell(101, 'Алексей', 'present', 2),
  cell(101, 'Алексей', 'absent', 3),
  cell(101, 'Алексей', 'absent', 4),
]

// Subject 2 (Physics): 1 student, 9 present + 1 absent = 90%
const physicsCells = [
  cell(101, 'Алексей', 'present', 10),
  cell(101, 'Алексей', 'present', 11),
  cell(101, 'Алексей', 'present', 12),
  cell(101, 'Алексей', 'present', 13),
  cell(101, 'Алексей', 'present', 14),
  cell(101, 'Алексей', 'present', 15),
  cell(101, 'Алексей', 'present', 16),
  cell(101, 'Алексей', 'present', 17),
  cell(101, 'Алексей', 'present', 18),
  cell(101, 'Алексей', 'absent', 19),
]

describe('StatsPage', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    vi.mocked(useSetSubjectThreshold).mockReturnValue({
      mutate: vi.fn(),
      isPending: false,
      isError: false,
      error: null,
      isSuccess: false,
      reset: vi.fn(),
    } as any)
  })

  it('Test 1: renders 3 loading skeleton cards while subjects query is loading', () => {
    vi.mocked(useGroupSubjects).mockReturnValue({
      data: undefined,
      isLoading: true,
    } as any)
    vi.mocked(useJournal).mockReturnValue({ data: [], isLoading: false } as any)
    vi.mocked(useResolveThreshold).mockReturnValue({
      data: { subjectId: 0, groupId: 0, minPercentage: 75, source: 'global' },
      isLoading: false,
    } as any)

    const { container } = renderPage()
    const skeletons = container.querySelectorAll('.animate-pulse')
    expect(skeletons.length).toBeGreaterThanOrEqual(3)
  })

  it('Test 2: renders one SubjectStatsCard per subject', () => {
    vi.mocked(useGroupSubjects).mockReturnValue({
      data: subjectsFixture,
      isLoading: false,
    } as any)
    vi.mocked(useJournal).mockImplementation(((_g: number, subjectId: number) => {
      return {
        data: subjectId === 1 ? mathCells : physicsCells,
        isLoading: false,
      }
    }) as any)
    vi.mocked(useResolveThreshold).mockReturnValue({
      data: { subjectId: 0, groupId: 0, minPercentage: 75, source: 'global' },
      isLoading: false,
    } as any)

    renderPage()
    expect(screen.getByText('Математика')).toBeInTheDocument()
    expect(screen.getByText('Физика')).toBeInTheDocument()
  })

  it('Test 3: sorts subjects by red-zone severity — lowest attendance (red-zone) first', () => {
    vi.mocked(useGroupSubjects).mockReturnValue({
      data: subjectsFixture,
      isLoading: false,
    } as any)
    vi.mocked(useJournal).mockImplementation(((_g: number, subjectId: number) => ({
      data: subjectId === 1 ? mathCells : physicsCells,
      isLoading: false,
    })) as any)
    vi.mocked(useResolveThreshold).mockReturnValue({
      data: { subjectId: 0, groupId: 0, minPercentage: 75, source: 'global' },
      isLoading: false,
    } as any)

    renderPage()
    const headings = screen.getAllByRole('heading', { level: 2 })
    // Math (50%) is below threshold 75 → red-zone; Physics (90%) is above → should come after
    expect(headings[0].textContent).toBe('Математика')
    expect(headings[1].textContent).toBe('Физика')
  })

  it('Test 4: renders "Группе не назначены предметы" when subjects list is empty', () => {
    vi.mocked(useGroupSubjects).mockReturnValue({ data: [], isLoading: false } as any)
    vi.mocked(useJournal).mockReturnValue({ data: [], isLoading: false } as any)
    vi.mocked(useResolveThreshold).mockReturnValue({
      data: { subjectId: 0, groupId: 0, minPercentage: 75, source: 'global' },
      isLoading: false,
    } as any)

    renderPage()
    expect(screen.getByText('Группе не назначены предметы')).toBeInTheDocument()
  })

  it('Test 5: computes per-subject group attendance % from journal cells (present+excused+free_attendance count; cancelled excluded)', () => {
    // Single subject with mixed statuses
    const mixed = [
      cell(101, 'А', 'present'),
      cell(101, 'А', 'excused'),
      cell(101, 'А', 'free_attendance'),
      cell(101, 'А', 'absent'),
      cell(101, 'А', 'cancelled'), // excluded
    ]
    // 3 present-like / 4 non-cancelled = 75%
    vi.mocked(useGroupSubjects).mockReturnValue({
      data: [subjectsFixture[0]],
      isLoading: false,
    } as any)
    vi.mocked(useJournal).mockReturnValue({ data: mixed, isLoading: false } as any)
    vi.mocked(useResolveThreshold).mockReturnValue({
      data: { subjectId: 1, groupId: 42, minPercentage: 50, source: 'global' },
      isLoading: false,
    } as any)

    renderPage()
    // Group avg = 75%, single student also 75% — both render; assert presence
    expect(screen.getAllByText('75%').length).toBeGreaterThanOrEqual(1)
  })

  it('Test 6: editing threshold and clicking Save triggers useSetSubjectThreshold.mutate with { subjectId, minPercentage }', async () => {
    const mutateFn = vi.fn()
    vi.mocked(useSetSubjectThreshold).mockReturnValue({
      mutate: mutateFn,
      isPending: false,
      isError: false,
      error: null,
      isSuccess: false,
      reset: vi.fn(),
    } as any)
    vi.mocked(useGroupSubjects).mockReturnValue({
      data: [subjectsFixture[0]],
      isLoading: false,
    } as any)
    vi.mocked(useJournal).mockReturnValue({ data: mathCells, isLoading: false } as any)
    vi.mocked(useResolveThreshold).mockReturnValue({
      data: { subjectId: 1, groupId: 42, minPercentage: 75, source: 'global' },
      isLoading: false,
    } as any)

    renderPage()
    const input = screen.getByLabelText(/порог посещаемости/i) as HTMLInputElement
    fireEvent.change(input, { target: { value: '80' } })
    const saveBtn = screen.getByRole('button', { name: /сохранить порог/i })
    fireEvent.click(saveBtn)

    await waitFor(() => {
      expect(mutateFn).toHaveBeenCalledWith({ subjectId: 1, minPercentage: 80 })
    })
  })

  it('Test 7: error on threshold save shows inline mapped error copy', () => {
    vi.mocked(useSetSubjectThreshold).mockReturnValue({
      mutate: vi.fn(),
      isPending: false,
      isError: true,
      error: { response: { status: 403 } },
      isSuccess: false,
      reset: vi.fn(),
    } as any)
    vi.mocked(useGroupSubjects).mockReturnValue({
      data: [subjectsFixture[0]],
      isLoading: false,
    } as any)
    vi.mocked(useJournal).mockReturnValue({ data: mathCells, isLoading: false } as any)
    vi.mocked(useResolveThreshold).mockReturnValue({
      data: { subjectId: 1, groupId: 42, minPercentage: 75, source: 'global' },
      isLoading: false,
    } as any)

    renderPage()
    expect(screen.getByRole('alert')).toHaveTextContent(/нет прав/i)
  })
})
