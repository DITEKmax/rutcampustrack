import { describe, it, expect, vi, beforeEach } from 'vitest'
import { render, screen } from '@testing-library/react'

const mockUseNetworkStatus = vi.fn()

vi.mock('@/shared/hooks/useNetworkStatus', () => ({
  useNetworkStatus: () => mockUseNetworkStatus(),
}))

import { OfflineStaleNotice } from '../OfflineStaleNotice'

describe('OfflineStaleNotice', () => {
  beforeEach(() => {
    vi.clearAllMocks()
  })

  it('shows dynamic relative timestamp when offline with dataUpdatedAt 5 minutes ago', () => {
    mockUseNetworkStatus.mockReturnValue({ isOnline: false })
    const fiveMinAgo = Date.now() - 5 * 60 * 1000

    render(<OfflineStaleNotice dataUpdatedAt={fiveMinAgo} />)

    expect(screen.getByRole('status')).toHaveTextContent(/Офлайн/)
    expect(screen.getByRole('status')).toHaveTextContent(/5 мин назад/)
  })

  it('shows dynamic relative timestamp when offline with dataUpdatedAt 90 minutes ago', () => {
    mockUseNetworkStatus.mockReturnValue({ isOnline: false })
    const ninetyMinAgo = Date.now() - 90 * 60 * 1000

    render(<OfflineStaleNotice dataUpdatedAt={ninetyMinAgo} />)

    expect(screen.getByRole('status')).toHaveTextContent(/Офлайн/)
    expect(screen.getByRole('status')).toHaveTextContent(/90 мин назад/)
  })

  it('is hidden when online', () => {
    mockUseNetworkStatus.mockReturnValue({ isOnline: true })

    const { container } = render(<OfflineStaleNotice dataUpdatedAt={Date.now()} />)
    expect(container.innerHTML).toBe('')
  })

  it('shows "Нет данных" when offline without dataUpdatedAt', () => {
    mockUseNetworkStatus.mockReturnValue({ isOnline: false })

    render(<OfflineStaleNotice />)

    expect(screen.getByRole('status')).toHaveTextContent(/Нет данных/)
  })
})
