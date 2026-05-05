import { describe, expect, it, vi } from 'vitest'
import { fireEvent, render, screen } from '@testing-library/react'
import { BottomSheet } from '../BottomSheet'

describe('BottomSheet', () => {
  it('can restrict dismissal to the close button', () => {
    const onClose = vi.fn()

    const { container } = render(
      <BottomSheet
        open
        onClose={onClose}
        title="Attendance"
        heading="Algorithms"
        closeOnBackdrop={false}
        closeOnEscape={false}
        closeOnSwipeDown={false}
        showDragHandle={false}
      >
        <div>Roster</div>
      </BottomSheet>,
    )

    fireEvent.click(container.querySelector('[data-bottom-sheet-backdrop="true"]')!)
    fireEvent.keyDown(window, { key: 'Escape' })

    expect(onClose).not.toHaveBeenCalled()

    fireEvent.click(screen.getByRole('button'))

    expect(onClose).toHaveBeenCalledTimes(1)
    expect(container.querySelector('[data-bottom-sheet-panel="true"]')).toHaveAttribute(
      'data-pull-to-refresh-ignore',
      'true',
    )
  })
})
