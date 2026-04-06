import { describe, it, expect, vi, beforeEach } from 'vitest'
import { render, screen } from '@testing-library/react'
import { PushPermissionCard } from '../PushPermissionCard'
import type { PushState } from '../usePushSubscription'

const mockSubscribe = vi.fn()
const mockUnsubscribe = vi.fn()

let mockState: PushState = 'default'
let mockLoading = false
let mockError: string | null = null

vi.mock('../usePushSubscription', () => ({
  usePushSubscription: () => ({
    state: mockState,
    loading: mockLoading,
    error: mockError,
    subscribe: mockSubscribe,
    unsubscribe: mockUnsubscribe,
  }),
}))

describe('PushPermissionCard', () => {
  beforeEach(() => {
    mockState = 'default'
    mockLoading = false
    mockError = null
    mockSubscribe.mockClear()
    mockUnsubscribe.mockClear()
  })

  it('renders enable button when state is default', () => {
    render(<PushPermissionCard />)
    expect(screen.getByText('Включить уведомления')).toBeInTheDocument()
  })

  it('renders disable button when state is granted', () => {
    mockState = 'granted'
    render(<PushPermissionCard />)
    expect(screen.getByText('Отключить уведомления')).toBeInTheDocument()
  })

  it('renders unsupported message when state is unsupported', () => {
    mockState = 'unsupported'
    render(<PushPermissionCard />)
    expect(screen.getByText('Уведомления недоступны')).toBeInTheDocument()
  })

  it('renders blocked message when state is denied', () => {
    mockState = 'denied'
    render(<PushPermissionCard />)
    expect(screen.getByText(/заблокированы/)).toBeInTheDocument()
  })

  it('disables enable button when loading', () => {
    mockLoading = true
    render(<PushPermissionCard />)
    const button = screen.getByText('Включить уведомления').closest('button')
    expect(button).toBeDisabled()
  })

  it('disables enable button when denied', () => {
    mockState = 'denied'
    render(<PushPermissionCard />)
    const button = screen.getByText('Включить уведомления').closest('button')
    expect(button).toBeDisabled()
  })

  it('shows iOS standalone warning when error is iOS_NOT_STANDALONE', () => {
    mockError = 'iOS_NOT_STANDALONE'
    render(<PushPermissionCard />)
    expect(screen.getByText(/Установите RutTrack на домашний экран/)).toBeInTheDocument()
  })

  it('shows generic error message', () => {
    mockError = 'Network error'
    render(<PushPermissionCard />)
    expect(screen.getByText('Network error')).toBeInTheDocument()
  })
})
