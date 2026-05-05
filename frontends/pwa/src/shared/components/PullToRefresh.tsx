import { useEffect, useRef, useState } from 'react'
import { ArrowClockwise } from '@phosphor-icons/react'
import { cn } from '@/lib/utils'

interface Props {
  /**
   * Called when the user releases the drag past `threshold`. Return a promise
   * — the spinner keeps rendering until it resolves.
   */
  onRefresh: () => Promise<unknown>
  /** Pixels of pull before refresh triggers. Default 80. */
  threshold?: number
  /** Max travel in pixels (rubber-band cap). Default threshold * 1.6. */
  maxPull?: number
  /** Disable entirely — useful when another gesture owns touches. */
  disabled?: boolean
  children: React.ReactNode
  className?: string
}

type Phase = 'idle' | 'pulling' | 'ready' | 'refreshing'

/**
 * Pull-to-refresh container (M07 G6/1).
 *
 * Слушает touch-события через delegated listener на root-контейнере, а не
 * через React-handlers — нужно `{ passive: false }` для `preventDefault`
 * при pulling, что React synthetic events не даёт выставить надёжно на
 * all-browsers (Chrome > 56 игнорирует non-passive preventDefault в passive
 * listener'е).
 *
 * UX: spring-feel резинки через `offset = sqrt(rawPull * threshold)`
 * — прогресс замедляется по мере натяжения, что ощущается «туго» на
 * последних 40% как в native iOS.
 */
export function PullToRefresh({
  onRefresh,
  threshold = 80,
  maxPull,
  disabled,
  children,
  className,
}: Props) {
  const cap = maxPull ?? threshold * 1.6
  const rootRef = useRef<HTMLDivElement>(null)
  const startYRef = useRef<number | null>(null)
  const capturedRef = useRef(false)
  const [phase, setPhase] = useState<Phase>('idle')
  const [offset, setOffset] = useState(0)

  useEffect(() => {
    const el = rootRef.current
    if (!el || disabled) return

    // Walk UP from the PullToRefresh root — its parent (motion.div / Outlet
     // wrapper) usually isn't overflow, the real scroll container is <main> in
     // AppShell. Searching from e.target down to `el` (as the previous version
     // did) returned el.parentElement, which had scrollTop=0 forever and made
     // the handler think the user was always at the top — blocking upward
     // scrolls anywhere on the page after the first downward gesture.
    const findScrollable = (): HTMLElement | null => {
      let node: HTMLElement | null = el.parentElement
      while (node) {
        const style = getComputedStyle(node)
        const overflowY = style.overflowY
        if ((overflowY === 'auto' || overflowY === 'scroll') && node.scrollHeight > node.clientHeight) {
          return node
        }
        node = node.parentElement
      }
      return (document.scrollingElement as HTMLElement | null) ?? document.documentElement
    }

    const shouldIgnoreTarget = (target: EventTarget | null): boolean =>
      target instanceof Element &&
      target.closest('[data-pull-to-refresh-ignore="true"]') !== null

    const onTouchStart = (e: TouchEvent) => {
      if (phase === 'refreshing') return
      if (e.touches.length !== 1) return
      if (shouldIgnoreTarget(e.target)) {
        startYRef.current = null
        capturedRef.current = false
        return
      }
      const scrollable = findScrollable()
      if (scrollable && scrollable.scrollTop > 0) {
        startYRef.current = null
        return
      }
      startYRef.current = e.touches[0].clientY
      capturedRef.current = false
    }

    const onTouchMove = (e: TouchEvent) => {
      if (phase === 'refreshing') return
      if (startYRef.current === null) return
      const dy = e.touches[0].clientY - startYRef.current
      if (dy <= 0) {
        setOffset(0)
        setPhase('idle')
        return
      }
      // Claim the gesture once user pulls > 6px — avoids swallowing taps.
      if (!capturedRef.current && dy > 6) {
        capturedRef.current = true
      }
      if (!capturedRef.current) return
      e.preventDefault()
      const eased = Math.min(cap, Math.sqrt(dy * threshold))
      setOffset(eased)
      setPhase(eased >= threshold ? 'ready' : 'pulling')
    }

    const onTouchEnd = () => {
      if (startYRef.current === null) return
      startYRef.current = null
      if (!capturedRef.current) return
      capturedRef.current = false

      if (phase === 'ready') {
        setPhase('refreshing')
        setOffset(threshold * 0.75)
        Promise.resolve(onRefresh())
          .finally(() => {
            setPhase('idle')
            setOffset(0)
          })
      } else {
        setPhase('idle')
        setOffset(0)
      }
    }

    el.addEventListener('touchstart', onTouchStart, { passive: true })
    el.addEventListener('touchmove', onTouchMove, { passive: false })
    el.addEventListener('touchend', onTouchEnd)
    el.addEventListener('touchcancel', onTouchEnd)

    return () => {
      el.removeEventListener('touchstart', onTouchStart)
      el.removeEventListener('touchmove', onTouchMove)
      el.removeEventListener('touchend', onTouchEnd)
      el.removeEventListener('touchcancel', onTouchEnd)
    }
  }, [cap, threshold, onRefresh, phase, disabled])

  const progress = Math.min(1, offset / threshold)
  const spinning = phase === 'refreshing'

  return (
    <div ref={rootRef} className={cn('relative', className)}>
      {/* Indicator */}
      <div
        aria-hidden={!spinning && phase === 'idle'}
        className="pointer-events-none absolute left-0 right-0 top-0 flex justify-center"
        style={{
          transform: `translateY(${Math.max(0, offset - 40)}px)`,
          transition: phase === 'idle' ? 'transform 240ms ease-out' : undefined,
        }}
      >
        <div
          className="grid size-9 place-items-center rounded-full border"
          style={{
            background: 'var(--bg-secondary)',
            borderColor: 'var(--border-subtle)',
            color:
              phase === 'ready' || spinning
                ? 'var(--accent-primary)'
                : 'var(--text-muted)',
            opacity: Math.min(1, progress * 1.5),
            boxShadow: spinning ? 'var(--glow-primary)' : 'none',
          }}
        >
          <ArrowClockwise
            size={18}
            weight="bold"
            className={spinning ? 'animate-spin' : undefined}
            style={{
              transform: spinning ? undefined : `rotate(${progress * 360}deg)`,
              transition: spinning ? undefined : 'transform 60ms linear',
            }}
          />
        </div>
      </div>

      <div
        style={{
          transform: `translateY(${offset}px)`,
          transition: phase === 'idle' ? 'transform 240ms ease-out' : undefined,
        }}
      >
        {children}
      </div>
    </div>
  )
}
