import { describe, it, expect, vi } from 'vitest'
import { render, screen } from '@testing-library/react'
import { PullToRefresh } from '../PullToRefresh'

describe('PullToRefresh', () => {
  it('renders children', () => {
    render(
      <PullToRefresh onRefresh={async () => {}}>
        <div>inner-content</div>
      </PullToRefresh>,
    )
    expect(screen.getByText('inner-content')).toBeInTheDocument()
  })

  it('does not call onRefresh on mount', () => {
    const onRefresh = vi.fn().mockResolvedValue(undefined)
    render(
      <PullToRefresh onRefresh={onRefresh}>
        <div>c</div>
      </PullToRefresh>,
    )
    expect(onRefresh).not.toHaveBeenCalled()
  })

  it('supports disabled prop (no-op touch listeners)', () => {
    // Just verify it renders with disabled — behaviour is touch-driven and
    // jsdom doesn't emit real touch events, so we keep this a smoke-test.
    const onRefresh = vi.fn()
    render(
      <PullToRefresh onRefresh={onRefresh} disabled>
        <div>disabled-content</div>
      </PullToRefresh>,
    )
    expect(screen.getByText('disabled-content')).toBeInTheDocument()
  })
})
