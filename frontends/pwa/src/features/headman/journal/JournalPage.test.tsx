import { describe, it, expect, vi, beforeEach } from 'vitest'
import { render, screen, fireEvent, waitFor } from '@testing-library/react'
import { BrowserRouter } from 'react-router'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { JournalPage } from './JournalPage'

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
  useMarkAttendance: vi.fn(),
}))

vi.mock('@/features/auth/AuthProvider', () => ({
  useAuth: vi.fn(() => ({
    user: { id: 1, role: 'STUDENT', groupId: 42, isHeadman: true },
  })),
}))

import {
  useGroupSubjects,
  useJournal,
  useMarkAttendance,
} from '@/features/headman/shared/headmanApi'

const mockSubjects = [
  { id: 7, name: 'Математика', teacherId: 10, teacherName: 'Иванов' },
  { id: 8, name: 'Физика', teacherId: 11, teacherName: 'Петров' },
]

const mockJournalCells = [
  {
    lessonId: 100,
    studentId: 1,
    studentName: 'Иванов Иван',
    date: '2026-04-13',
    status: 'present' as const,
  },
  {
    lessonId: 100,
    studentId: 2,
    studentName: 'Петров Пётр',
    date: '2026-04-13',
    status: 'absent' as const,
  },
]

function renderPage() {
  const queryClient = new QueryClient({
    defaultOptions: { queries: { retry: false } },
  })
  return render(
    <BrowserRouter>
      <QueryClientProvider client={queryClient}>
        <JournalPage />
      </QueryClientProvider>
    </BrowserRouter>,
  )
}

describe('JournalPage', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    vi.mocked(useGroupSubjects).mockReturnValue({
      data: mockSubjects,
      isLoading: false,
    } as any)
    vi.mocked(useJournal).mockReturnValue({
      data: mockJournalCells,
      isLoading: false,
      isError: false,
    } as any)
    vi.mocked(useMarkAttendance).mockReturnValue({
      mutate: vi.fn(),
    } as any)
  })

  it('Test 1: Step 1 renders subject select, date input, and "Загрузить журнал" button', () => {
    renderPage()
    expect(screen.getByText(/Выберите предмет и дату/)).toBeInTheDocument()
    expect(screen.getByRole('combobox', { name: /предмет/i })).toBeInTheDocument()
    expect(screen.getByLabelText(/дата/i)).toBeInTheDocument()
    expect(screen.getByRole('button', { name: /загрузить журнал/i })).toBeInTheDocument()
  })

  it('Test 2: button is disabled when no subject selected', () => {
    renderPage()
    const btn = screen.getByRole('button', { name: /загрузить журнал/i })
    expect(btn).toBeDisabled()
  })

  it('Test 3: selecting a subject and clicking button transitions to Step 2 with breadcrumb', async () => {
    renderPage()
    const select = screen.getByRole('combobox', { name: /предмет/i })
    fireEvent.change(select, { target: { value: '7' } })
    const btn = screen.getByRole('button', { name: /загрузить журнал/i })
    fireEvent.click(btn)

    await waitFor(() => {
      expect(screen.getByText(/Математика/)).toBeInTheDocument()
    })
    // Student rows should be rendered
    expect(screen.getByText('Иванов Иван')).toBeInTheDocument()
    expect(screen.getByText('Петров Пётр')).toBeInTheDocument()
  })

  it('Test 4: empty journal data → Step 2 shows "На эту дату занятий нет"', async () => {
    vi.mocked(useJournal).mockReturnValue({
      data: [],
      isLoading: false,
      isError: false,
    } as any)

    renderPage()
    const select = screen.getByRole('combobox', { name: /предмет/i })
    fireEvent.change(select, { target: { value: '7' } })
    fireEvent.click(screen.getByRole('button', { name: /загрузить журнал/i }))

    await waitFor(() => {
      expect(screen.getByText('На эту дату занятий нет')).toBeInTheDocument()
    })
  })

  it('Test 5: tapping a segment calls useMarkAttendance.mutate with { lessonId, userId, status }', async () => {
    const mutate = vi.fn()
    vi.mocked(useMarkAttendance).mockReturnValue({ mutate } as any)

    renderPage()
    const select = screen.getByRole('combobox', { name: /предмет/i })
    fireEvent.change(select, { target: { value: '7' } })
    fireEvent.click(screen.getByRole('button', { name: /загрузить журнал/i }))

    await waitFor(() => {
      expect(screen.getByText('Иванов Иван')).toBeInTheDocument()
    })

    // Click "н" (absent) segment on the first student (present → tap н)
    const absentButtons = screen.getAllByLabelText('Отсутствовал')
    fireEvent.click(absentButtons[0]) // student 1 (Иванов)

    expect(mutate).toHaveBeenCalledWith(
      { lessonId: 100, userId: 1, status: 'absent' },
      expect.anything(),
    )
  })

  it('Test 6: back arrow in Step 2 returns to Step 1 with selectors preserved', async () => {
    renderPage()
    const select = screen.getByRole('combobox', { name: /предмет/i })
    fireEvent.change(select, { target: { value: '7' } })
    fireEvent.click(screen.getByRole('button', { name: /загрузить журнал/i }))

    await waitFor(() => {
      expect(screen.getByText('Иванов Иван')).toBeInTheDocument()
    })

    const backBtn = screen.getByRole('button', { name: /назад/i })
    fireEvent.click(backBtn)

    expect(screen.getByText(/Выберите предмет и дату/)).toBeInTheDocument()
    const selectAfter = screen.getByRole('combobox', { name: /предмет/i }) as HTMLSelectElement
    expect(selectAfter.value).toBe('7')
  })

  it('Test 7: API load error → Step 1 shows inline error "Ошибка загрузки..."', () => {
    vi.mocked(useJournal).mockReturnValue({
      data: undefined,
      isLoading: false,
      isError: true,
      error: new Error('network'),
    } as any)

    renderPage()
    expect(
      screen.getByText('Ошибка загрузки. Проверьте подключение и попробуйте снова.'),
    ).toBeInTheDocument()
  })
})
