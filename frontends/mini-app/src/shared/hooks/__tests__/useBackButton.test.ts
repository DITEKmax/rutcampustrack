import { describe, it, expect, vi, beforeEach } from 'vitest'
import { renderHook } from '@testing-library/react'
import { BrowserRouter } from 'react-router'
import { backButton } from '@telegram-apps/sdk-react'
import { useBackButton } from '../useBackButton'

describe('useBackButton', () => {
  beforeEach(() => {
    vi.clearAllMocks()
  })

  it('shows backButton on mount when available', () => {
    renderHook(() => useBackButton(), { wrapper: BrowserRouter })
    expect(backButton.show).toHaveBeenCalled()
  })

  it('registers onClick handler on mount', () => {
    renderHook(() => useBackButton(), { wrapper: BrowserRouter })
    expect(backButton.onClick).toHaveBeenCalled()
  })

  it('hides backButton and unsubscribes on unmount', () => {
    const unsubscribe = vi.fn()
    vi.mocked(backButton.onClick).mockReturnValue(unsubscribe)

    const { unmount } = renderHook(() => useBackButton(), { wrapper: BrowserRouter })
    unmount()

    expect(unsubscribe).toHaveBeenCalled()
    expect(backButton.hide).toHaveBeenCalled()
  })
})
