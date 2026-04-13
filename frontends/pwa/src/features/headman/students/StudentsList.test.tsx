import { describe, it, expect, vi, beforeEach } from 'vitest'
import { render, screen, fireEvent, waitFor } from '@testing-library/react'
import { BrowserRouter } from 'react-router'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { StudentsList } from './StudentsList'

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
  useGroupMembers: vi.fn(),
  useCreateAssistant: vi.fn(),
  useDeleteAssistant: vi.fn(),
}))

import {
  useGroupMembers,
  useCreateAssistant,
  useDeleteAssistant,
} from '@/features/headman/shared/headmanApi'

const mockMembers = [
  { id: 1, fullName: 'Иванов Иван Иванович', login: 'student00001', isHeadman: true },
  { id: 2, fullName: 'Петров Пётр Петрович', login: 'student00002', isAssistant: true },
  { id: 3, fullName: 'Сидоров Сидор Сидорович', login: 'student00003' },
]

function renderStudentsList() {
  const queryClient = new QueryClient({
    defaultOptions: { queries: { retry: false } },
  })
  return render(
    <BrowserRouter>
      <QueryClientProvider client={queryClient}>
        <StudentsList />
      </QueryClientProvider>
    </BrowserRouter>,
  )
}

describe('StudentsList', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    vi.mocked(useGroupMembers).mockReturnValue({
      data: mockMembers,
      isLoading: false,
    } as any)
    vi.mocked(useCreateAssistant).mockReturnValue({
      mutate: vi.fn(),
      isPending: false,
      isError: false,
      error: null,
    } as any)
    vi.mocked(useDeleteAssistant).mockReturnValue({
      mutate: vi.fn(),
      isPending: false,
    } as any)
  })

  it('Test 1: renders list of all group members with full names', () => {
    renderStudentsList()
    // Иванов — headman, appears once in main list only
    expect(screen.getAllByText('Иванов Иван Иванович').length).toBeGreaterThanOrEqual(1)
    // Петров — assistant, appears in main list AND assistants section
    expect(screen.getAllByText('Петров Пётр Петрович').length).toBeGreaterThanOrEqual(1)
    expect(screen.getAllByText('Сидоров Сидор Сидорович').length).toBeGreaterThanOrEqual(1)
  })

  it('Test 2: renders "Староста" badge for isHeadman=true and "Помощник" badge for isAssistant=true', () => {
    renderStudentsList()
    // Badges may appear in both main list and assistants sub-section
    expect(screen.getAllByText('Староста').length).toBeGreaterThanOrEqual(1)
    expect(screen.getAllByText('Помощник').length).toBeGreaterThanOrEqual(1)
  })

  it('Test 3: clicking floating "+" button opens AddAssistantModal', () => {
    renderStudentsList()
    const addButton = screen.getByRole('button', { name: /добавить помощника/i })
    fireEvent.click(addButton)
    expect(screen.getByText('Добавить помощника')).toBeInTheDocument()
  })

  it('Test 4: submitting AddAssistantModal calls useCreateAssistant.mutate with correct payload', async () => {
    const mutateFn = vi.fn()
    vi.mocked(useCreateAssistant).mockReturnValue({
      mutate: mutateFn,
      isPending: false,
      isError: false,
      error: null,
    } as any)

    renderStudentsList()
    // Open modal
    const addButton = screen.getByRole('button', { name: /добавить помощника/i })
    fireEvent.click(addButton)

    // Select student (id=3 is not an assistant)
    const select = screen.getByRole('combobox')
    fireEvent.change(select, { target: { value: '3' } })

    // Check "Управление студентами" checkbox
    const checkbox = screen.getByLabelText('Управление студентами')
    fireEvent.click(checkbox)

    // Submit
    const saveButton = screen.getByRole('button', { name: 'Сохранить' })
    fireEvent.click(saveButton)

    await waitFor(() => {
      expect(mutateFn).toHaveBeenCalledWith(
        expect.objectContaining({ studentId: 3 }),
        expect.anything(),
      )
    })
  })

  it('Test 5: clicking delete on assistant shows confirmation; confirming calls useDeleteAssistant.mutate', async () => {
    const deleteFn = vi.fn()
    vi.mocked(useDeleteAssistant).mockReturnValue({
      mutate: deleteFn,
      isPending: false,
    } as any)

    renderStudentsList()
    // Find assistant delete button (member id=2 isAssistant=true)
    const deleteButtons = screen.getAllByRole('button', { name: /удалить помощника/i })
    expect(deleteButtons.length).toBeGreaterThanOrEqual(1)
    fireEvent.click(deleteButtons[0])

    // Confirm dialog should appear
    expect(screen.getByText('Убрать помощника?')).toBeInTheDocument()

    // Confirm deletion
    const confirmButton = screen.getByRole('button', { name: 'Удалить' })
    fireEvent.click(confirmButton)

    await waitFor(() => {
      expect(deleteFn).toHaveBeenCalled()
    })
  })
})
