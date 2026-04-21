import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest'
import { renderHook, act } from '@testing-library/react'
import { useDateNavigation } from '../useDateNavigation'

describe('useDateNavigation', () => {
  beforeEach(() => {
    // Freeze "today" = 2026-04-15 (Wednesday). Monday of week = 2026-04-13.
    vi.useFakeTimers()
    vi.setSystemTime(new Date(2026, 3, 15, 12, 0, 0))
  })

  afterEach(() => {
    vi.useRealTimers()
  })

  it('day: goPrev subtracts 1 day, goNext adds 1 day', () => {
    const onChange = vi.fn()
    const { result } = renderHook(() =>
      useDateNavigation({
        unit: 'day',
        value: new Date(2026, 3, 10),
        onChange,
      }),
    )
    act(() => result.current.goNext())
    expect(onChange).toHaveBeenCalledWith(new Date(2026, 3, 11))

    onChange.mockClear()
    act(() => result.current.goPrev())
    expect(onChange).toHaveBeenCalledWith(new Date(2026, 3, 9))
  })

  it('week: snaps to Monday before adding/subtracting 7 days', () => {
    const onChange = vi.fn()
    // Wed 2026-04-15 → normalized Mon 2026-04-13 → next = 2026-04-20
    const { result } = renderHook(() =>
      useDateNavigation({
        unit: 'week',
        value: new Date(2026, 3, 15),
        onChange,
      }),
    )
    act(() => result.current.goNext())
    expect(onChange).toHaveBeenCalledWith(new Date(2026, 3, 20))
  })

  it('month: snaps to first-of-month and shifts by ±1 month', () => {
    const onChange = vi.fn()
    const { result } = renderHook(() =>
      useDateNavigation({
        unit: 'month',
        value: new Date(2026, 3, 15),
        onChange,
      }),
    )
    act(() => result.current.goNext())
    expect(onChange).toHaveBeenCalledWith(new Date(2026, 4, 1))

    onChange.mockClear()
    act(() => result.current.goPrev())
    expect(onChange).toHaveBeenCalledWith(new Date(2026, 2, 1))
  })

  it('respects bounds — canGoPrev=false blocks goPrev', () => {
    const onChange = vi.fn()
    const { result } = renderHook(() =>
      useDateNavigation({
        unit: 'day',
        value: new Date(2026, 3, 10),
        onChange,
        bounds: { min: new Date(2026, 3, 10) },
      }),
    )
    expect(result.current.canGoPrev).toBe(false)
    act(() => result.current.goPrev())
    expect(onChange).not.toHaveBeenCalled()

    // goNext still works
    act(() => result.current.goNext())
    expect(onChange).toHaveBeenCalledWith(new Date(2026, 3, 11))
  })

  it('respects bounds — canGoNext=false blocks goNext', () => {
    const onChange = vi.fn()
    const { result } = renderHook(() =>
      useDateNavigation({
        unit: 'day',
        value: new Date(2026, 3, 20),
        onChange,
        bounds: { max: new Date(2026, 3, 20) },
      }),
    )
    expect(result.current.canGoNext).toBe(false)
    act(() => result.current.goNext())
    expect(onChange).not.toHaveBeenCalled()
  })

  it('week bounds: period-overlap — can navigate if week partially intersects bounds', () => {
    const onChange = vi.fn()
    // week-end = 2026-06-29 (Mon 2026-06-29) .. Sat 2026-07-04
    // bounds.max = 2026-06-30 — Mon's week starts 2026-06-29 (Mon),
    // weekStart = 2026-06-29 ≤ bounds.max → canGoNext checks next week (2026-07-06) vs bounds.max — out
    const { result } = renderHook(() =>
      useDateNavigation({
        unit: 'week',
        value: new Date(2026, 5, 29), // Mon 2026-06-29
        onChange,
        bounds: { max: new Date(2026, 5, 30) }, // 2026-06-30
      }),
    )
    expect(result.current.canGoNext).toBe(false)
    expect(result.current.canGoPrev).toBe(true)
  })

  it('goToToday dispatches today in current unit (snapped)', () => {
    const onChange = vi.fn()
    const { result } = renderHook(() =>
      useDateNavigation({
        unit: 'week',
        value: new Date(2026, 2, 1),
        onChange,
      }),
    )
    act(() => result.current.goToToday())
    // Monday of 2026-04-15 = 2026-04-13
    expect(onChange).toHaveBeenCalledWith(new Date(2026, 3, 13))
  })

  it('isAtToday correctly reports for normalized values', () => {
    const onChange = vi.fn()
    const { result } = renderHook(() =>
      useDateNavigation({
        unit: 'day',
        value: new Date(2026, 3, 15, 23, 59),
        onChange,
      }),
    )
    expect(result.current.isAtToday).toBe(true)
  })
})
