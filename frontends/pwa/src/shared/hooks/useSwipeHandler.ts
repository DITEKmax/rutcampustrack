import { useCallback } from 'react'
import type { PanInfo } from 'motion/react'

export type SwipeDirection = 'left' | 'right' | 'up' | 'down'

/**
 * Configuration for {@link useSwipeHandler}.
 *
 * Порог по умолчанию — 60px горизонтально / 100px вертикально. Это
 * компромисс между ложными срабатываниями (motion/react при тапе
 * даёт offset 2-5px) и усталостью (выше 100px пользователь явно
 * перелистывает, ниже — нечёткая интенция).
 *
 * `velocityThreshold` — альтернативный триггер: быстрый flick даже
 * короткого расстояния считаем свайпом. Motion отдаёт velocity в px/s.
 * 500 — стандарт iOS/Android native (см. WWDC 2019 "Designing Fluid
 * Interfaces").
 */
export interface SwipeHandlerOptions {
  /** Horizontal threshold in pixels for left/right swipes. */
  horizontalThreshold?: number
  /** Vertical threshold in pixels for up/down swipes. */
  verticalThreshold?: number
  /** Velocity threshold in px/s. Swipe triggers on either threshold OR velocity. */
  velocityThreshold?: number
  onSwipeLeft?: () => void
  onSwipeRight?: () => void
  onSwipeUp?: () => void
  onSwipeDown?: () => void
}

const DEFAULT_HORIZONTAL = 60
const DEFAULT_VERTICAL = 100
const DEFAULT_VELOCITY = 500

/**
 * Unified swipe-detection handler for motion/react `onDragEnd`.
 *
 * Возвращает колбек `(event, info) => void` — подставляется напрямую
 * в `onDragEnd={handler}`. Детерминированно выбирает доминирующую ось
 * (abs(x) vs abs(y)) чтобы диагональные жесты не срабатывали дважды.
 *
 * Использование:
 *
 * ```tsx
 * const onDragEnd = useSwipeHandler({
 *   onSwipeLeft: () => nextPage(),
 *   onSwipeRight: () => prevPage(),
 *   horizontalThreshold: 80,
 * })
 *
 * <motion.div drag="x" dragConstraints={{ left: 0, right: 0 }} onDragEnd={onDragEnd}>
 * ```
 */
export function useSwipeHandler(options: SwipeHandlerOptions) {
  const {
    horizontalThreshold = DEFAULT_HORIZONTAL,
    verticalThreshold = DEFAULT_VERTICAL,
    velocityThreshold = DEFAULT_VELOCITY,
    onSwipeLeft,
    onSwipeRight,
    onSwipeUp,
    onSwipeDown,
  } = options

  return useCallback(
    (_event: unknown, info: PanInfo) => {
      const { offset, velocity } = info
      const absX = Math.abs(offset.x)
      const absY = Math.abs(offset.y)

      if (absX > absY) {
        const triggered =
          absX >= horizontalThreshold ||
          Math.abs(velocity.x) >= velocityThreshold
        if (!triggered) return
        if (offset.x < 0) onSwipeLeft?.()
        else onSwipeRight?.()
      } else {
        const triggered =
          absY >= verticalThreshold ||
          Math.abs(velocity.y) >= velocityThreshold
        if (!triggered) return
        if (offset.y < 0) onSwipeUp?.()
        else onSwipeDown?.()
      }
    },
    [
      horizontalThreshold,
      verticalThreshold,
      velocityThreshold,
      onSwipeLeft,
      onSwipeRight,
      onSwipeUp,
      onSwipeDown,
    ],
  )
}
