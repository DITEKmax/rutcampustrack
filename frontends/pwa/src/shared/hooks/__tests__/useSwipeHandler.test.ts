import { describe, it, expect, vi } from 'vitest'
import { renderHook } from '@testing-library/react'
import type { PanInfo } from 'motion/react'
import { useSwipeHandler } from '../useSwipeHandler'

function makeInfo(offset: { x: number; y: number }, velocity?: { x: number; y: number }): PanInfo {
  return {
    point: { x: 0, y: 0 },
    offset,
    velocity: velocity ?? { x: 0, y: 0 },
    delta: { x: 0, y: 0 },
  } as PanInfo
}

describe('useSwipeHandler', () => {
  it('triggers onSwipeLeft when horizontal offset exceeds negative threshold', () => {
    const onSwipeLeft = vi.fn()
    const { result } = renderHook(() =>
      useSwipeHandler({ onSwipeLeft, horizontalThreshold: 50 }),
    )
    result.current({}, makeInfo({ x: -60, y: 0 }))
    expect(onSwipeLeft).toHaveBeenCalledTimes(1)
  })

  it('triggers onSwipeRight when horizontal offset exceeds positive threshold', () => {
    const onSwipeRight = vi.fn()
    const { result } = renderHook(() =>
      useSwipeHandler({ onSwipeRight, horizontalThreshold: 50 }),
    )
    result.current({}, makeInfo({ x: 80, y: 0 }))
    expect(onSwipeRight).toHaveBeenCalledTimes(1)
  })

  it('does not trigger when offset is below threshold and velocity low', () => {
    const onSwipeLeft = vi.fn()
    const onSwipeRight = vi.fn()
    const { result } = renderHook(() =>
      useSwipeHandler({ onSwipeLeft, onSwipeRight, horizontalThreshold: 50 }),
    )
    result.current({}, makeInfo({ x: -20, y: 0 }, { x: 100, y: 0 }))
    expect(onSwipeLeft).not.toHaveBeenCalled()
    expect(onSwipeRight).not.toHaveBeenCalled()
  })

  it('triggers on fast flick even with small offset (velocity threshold)', () => {
    const onSwipeLeft = vi.fn()
    const { result } = renderHook(() =>
      useSwipeHandler({
        onSwipeLeft,
        horizontalThreshold: 200,
        velocityThreshold: 400,
      }),
    )
    result.current({}, makeInfo({ x: -30, y: 0 }, { x: -600, y: 0 }))
    expect(onSwipeLeft).toHaveBeenCalledTimes(1)
  })

  it('picks dominant axis — vertical swipe does not fire horizontal callback', () => {
    const onSwipeLeft = vi.fn()
    const onSwipeDown = vi.fn()
    const { result } = renderHook(() =>
      useSwipeHandler({
        onSwipeLeft,
        onSwipeDown,
        horizontalThreshold: 50,
        verticalThreshold: 50,
      }),
    )
    // x=-40, y=120 — vertical dominates
    result.current({}, makeInfo({ x: -40, y: 120 }))
    expect(onSwipeLeft).not.toHaveBeenCalled()
    expect(onSwipeDown).toHaveBeenCalledTimes(1)
  })

  it('calls onSwipeUp for vertical-negative swipe above threshold', () => {
    const onSwipeUp = vi.fn()
    const { result } = renderHook(() =>
      useSwipeHandler({ onSwipeUp, verticalThreshold: 80 }),
    )
    result.current({}, makeInfo({ x: 0, y: -100 }))
    expect(onSwipeUp).toHaveBeenCalledTimes(1)
  })

  it('is a noop when no matching callback is provided', () => {
    const { result } = renderHook(() =>
      useSwipeHandler({ horizontalThreshold: 50 }),
    )
    expect(() =>
      result.current({}, makeInfo({ x: 200, y: 0 })),
    ).not.toThrow()
  })
})
