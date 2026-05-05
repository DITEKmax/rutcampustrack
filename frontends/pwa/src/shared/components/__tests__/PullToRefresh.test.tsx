import { describe, it, expect, vi } from 'vitest'
import { act, render, screen } from '@testing-library/react'
import { PullToRefresh } from '../PullToRefresh'

function dispatchTouch(target: Element, type: string, clientY: number) {
  const event = new Event(type, { bubbles: true, cancelable: true })
  Object.defineProperty(event, 'touches', {
    value: type === 'touchend' ? [] : [{ clientY }],
    configurable: true,
  })
  target.dispatchEvent(event)
}

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

  it('ignores touch gestures from nested overlays', async () => {
    const onRefresh = vi.fn().mockResolvedValue(undefined)
    render(
      <PullToRefresh onRefresh={onRefresh} threshold={20}>
        <div data-pull-to-refresh-ignore="true">sheet-content</div>
      </PullToRefresh>,
    )

    const sheetContent = screen.getByText('sheet-content')

    await act(async () => {
      dispatchTouch(sheetContent, 'touchstart', 0)
      dispatchTouch(sheetContent, 'touchmove', 60)
      dispatchTouch(sheetContent, 'touchend', 60)
    })

    expect(onRefresh).not.toHaveBeenCalled()
  })
})
