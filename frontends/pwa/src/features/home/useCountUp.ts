import { useEffect, useState } from 'react'

/**
 * Animates a numeric value from 0 to `target` over `durationMs`.
 * Respects prefers-reduced-motion — in that case returns target immediately.
 */
export function useCountUp(target: number, durationMs = 800): number {
  const [value, setValue] = useState(0)

  useEffect(() => {
    const prefersReduce =
      typeof window !== 'undefined' &&
      window.matchMedia?.('(prefers-reduced-motion: reduce)')?.matches
    if (prefersReduce || durationMs <= 0 || target === 0) {
      setValue(target)
      return
    }
    let frame = 0
    const start = performance.now()
    const step = (now: number) => {
      const progress = Math.min(1, (now - start) / durationMs)
      // ease-out cubic
      const eased = 1 - Math.pow(1 - progress, 3)
      setValue(target * eased)
      if (progress < 1) {
        frame = requestAnimationFrame(step)
      }
    }
    frame = requestAnimationFrame(step)
    return () => cancelAnimationFrame(frame)
  }, [target, durationMs])

  return value
}
