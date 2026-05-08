import { useCallback } from 'react'
import type { PanInfo } from 'motion/react'

interface SwipeHandlerOptions {
  horizontalThreshold?: number
  verticalThreshold?: number
  velocityThreshold?: number
  onSwipeLeft?: () => void
  onSwipeRight?: () => void
  onSwipeUp?: () => void
  onSwipeDown?: () => void
}

export function useSwipeHandler({
  horizontalThreshold = 60,
  verticalThreshold = 100,
  velocityThreshold = 500,
  onSwipeLeft,
  onSwipeRight,
  onSwipeUp,
  onSwipeDown,
}: SwipeHandlerOptions) {
  return useCallback(
    (_event: unknown, info: PanInfo) => {
      const absX = Math.abs(info.offset.x)
      const absY = Math.abs(info.offset.y)

      if (absX > absY) {
        const triggered =
          absX >= horizontalThreshold || Math.abs(info.velocity.x) >= velocityThreshold
        if (!triggered) return
        if (info.offset.x < 0) onSwipeLeft?.()
        else onSwipeRight?.()
        return
      }

      const triggered =
        absY >= verticalThreshold || Math.abs(info.velocity.y) >= velocityThreshold
      if (!triggered) return
      if (info.offset.y < 0) onSwipeUp?.()
      else onSwipeDown?.()
    },
    [
      horizontalThreshold,
      onSwipeDown,
      onSwipeLeft,
      onSwipeRight,
      onSwipeUp,
      velocityThreshold,
      verticalThreshold,
    ],
  )
}
