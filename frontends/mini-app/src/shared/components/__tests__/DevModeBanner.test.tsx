import { render, screen } from '@testing-library/react'
import { describe, it, expect, vi } from 'vitest'

describe('DevModeBanner', () => {
  it('renders when VITE_TMA_DEV is true', async () => {
    vi.stubEnv('VITE_TMA_DEV', 'true')
    vi.stubEnv('VITE_TMA_MOCK_USER', 'student')
    // Dynamic import to pick up env after stub
    const { DevModeBanner } = await import('../DevModeBanner')
    render(<DevModeBanner />)
    expect(screen.getByText(/DEV MODE/)).toBeInTheDocument()
    expect(screen.getByText(/student/)).toBeInTheDocument()
    vi.unstubAllEnvs()
  })

  it('renders nothing when VITE_TMA_DEV is not true', async () => {
    vi.stubEnv('VITE_TMA_DEV', 'false')
    // Need fresh module for env change
    vi.resetModules()
    const { DevModeBanner } = await import('../DevModeBanner')
    const { container } = render(<DevModeBanner />)
    expect(container.innerHTML).toBe('')
    vi.unstubAllEnvs()
  })
})
