import { describe, it, expect, vi, beforeEach } from 'vitest'
import { render, screen, fireEvent, waitFor } from '@testing-library/react'
import { BrowserRouter } from 'react-router'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { SubjectsList } from './SubjectsList'

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
  useGroupTeachers: vi.fn(),
  useCreateSubject: vi.fn(),
  useUpdateSubject: vi.fn(),
  useDeleteSubject: vi.fn(),
}))

vi.mock('@/features/auth/AuthProvider', () => ({
  useAuth: vi.fn(() => ({
    user: { id: 1, role: 'STUDENT', groupId: 42, isHeadman: true },
  })),
}))

import {
  useGroupSubjects,
  useGroupTeachers,
  useCreateSubject,
  useUpdateSubject,
  useDeleteSubject,
} from '@/features/headman/shared/headmanApi'

const mockSubjects = [
  { id: 1, name: 'Математика', teacherId: 10, teacherName: 'Профессор Иванов' },
  { id: 2, name: 'Физика', teacherId: 11, teacherName: 'Доцент Петров' },
]

const mockTeachers = [
  { id: 10, fullName: 'Профессор Иванов', login: 'teacher00001' },
  { id: 11, fullName: 'Доцент Петров', login: 'teacher00002' },
]

function renderSubjectsList() {
  const queryClient = new QueryClient({
    defaultOptions: { queries: { retry: false } },
  })
  return render(
    <BrowserRouter>
      <QueryClientProvider client={queryClient}>
        <SubjectsList />
      </QueryClientProvider>
    </BrowserRouter>,
  )
}

describe('SubjectsList', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    vi.mocked(useGroupSubjects).mockReturnValue({
      data: mockSubjects,
      isLoading: false,
    } as any)
    vi.mocked(useGroupTeachers).mockReturnValue({
      data: mockTeachers,
      isLoading: false,
    } as any)
    vi.mocked(useCreateSubject).mockReturnValue({
      mutate: vi.fn(),
      isPending: false,
      isError: false,
      error: null,
    } as any)
    vi.mocked(useUpdateSubject).mockReturnValue({
      mutate: vi.fn(),
      isPending: false,
      isError: false,
      error: null,
    } as any)
    vi.mocked(useDeleteSubject).mockReturnValue({
      mutate: vi.fn(),
      isPending: false,
    } as any)
  })

  it('Test 1: renders list of subjects with subject name and teacher name', () => {
    renderSubjectsList()
    expect(screen.getByText('Математика')).toBeInTheDocument()
    expect(screen.getByText('Профессор Иванов')).toBeInTheDocument()
    expect(screen.getByText('Физика')).toBeInTheDocument()
    expect(screen.getByText('Доцент Петров')).toBeInTheDocument()
  })

  it('Test 2: clicking floating "+" opens SubjectFormModal in CREATE mode with heading "Новый предмет"', () => {
    renderSubjectsList()
    const addButton = screen.getByRole('button', { name: /создать предмет/i })
    fireEvent.click(addButton)
    expect(screen.getByText('Новый предмет')).toBeInTheDocument()
  })

  it('Test 3: submitting CREATE form calls useCreateSubject.mutate with { name, teacherId }', async () => {
    const createFn = vi.fn()
    vi.mocked(useCreateSubject).mockReturnValue({
      mutate: createFn,
      isPending: false,
      isError: false,
      error: null,
    } as any)

    renderSubjectsList()
    // Open CREATE modal
    const addButton = screen.getByRole('button', { name: /создать предмет/i })
    fireEvent.click(addButton)

    // Fill in name
    const nameInput = screen.getByRole('textbox', { name: /название/i })
    fireEvent.change(nameInput, { target: { value: 'Химия' } })

    // Select teacher
    const teacherSelect = screen.getByRole('combobox', { name: /преподаватель/i })
    fireEvent.change(teacherSelect, { target: { value: '10' } })

    // Submit
    const saveButton = screen.getByRole('button', { name: 'Сохранить' })
    fireEvent.click(saveButton)

    await waitFor(() => {
      expect(createFn).toHaveBeenCalledWith(
        expect.objectContaining({ name: 'Химия' }),
        expect.anything(),
      )
    })
  })

  it('Test 4: clicking subject row opens SubjectFormModal in EDIT mode with heading "Редактировать предмет"', () => {
    renderSubjectsList()
    // Click edit button for first subject
    const editButtons = screen.getAllByRole('button', { name: /редактировать предмет/i })
    fireEvent.click(editButtons[0])
    expect(screen.getByText('Редактировать предмет')).toBeInTheDocument()
    // Should have prefilled name
    expect(screen.getByDisplayValue('Математика')).toBeInTheDocument()
  })

  it('Test 5: delete button with confirmation "Удалить этот предмет?" calls useDeleteSubject.mutate on confirm', async () => {
    const deleteFn = vi.fn()
    vi.mocked(useDeleteSubject).mockReturnValue({
      mutate: deleteFn,
      isPending: false,
    } as any)

    renderSubjectsList()
    // Click trash icon for first subject
    const deleteButtons = screen.getAllByRole('button', { name: /удалить предмет/i })
    fireEvent.click(deleteButtons[0])

    // Confirm dialog
    expect(screen.getByText('Удалить этот предмет?')).toBeInTheDocument()

    // Confirm
    const confirmButton = screen.getByRole('button', { name: 'Удалить' })
    fireEvent.click(confirmButton)

    await waitFor(() => {
      expect(deleteFn).toHaveBeenCalled()
    })
  })
})
