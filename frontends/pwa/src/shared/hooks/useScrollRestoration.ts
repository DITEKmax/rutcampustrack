import { useEffect, useRef } from 'react'
import { useLocation, useNavigationType } from 'react-router'

const STORAGE_PREFIX = 'scroll:'
const RAF_RESTORE_ATTEMPTS = 3

/**
 * Scroll-position preservation per route (M07 G6/5).
 *
 * Сохраняет `scrollTop` переданного контейнера в sessionStorage на каждый
 * scroll (throttled через rAF), а при ре-монтировании страницы (route
 * change) восстанавливает позицию.
 *
 * Особенности:
 * - Восстановление делается в 3 rAF-попытки, чтобы пропустить
 *   initial-layout и дать lazy-chunk'у подгрузить контент (Suspense
 *   fallback → реальный размер). Если контейнер не набрал высоту — не
 *   скроллим; попробуем на следующем кадре.
 * - `POP` navigation (back/forward) восстанавливает, `PUSH`/`REPLACE`
 *   — сбрасывает в top. Это match UX браузера: переход «вперёд» = свежая
 *   позиция, «назад» = вернулись в исходную.
 * - `sessionStorage` (не localStorage) — чтобы данные не протекали
 *   между session'ами и не засоряли квоту.
 *
 * Usage — в AppShell над Outlet:
 *
 * ```tsx
 * const mainRef = useRef<HTMLElement>(null)
 * useScrollRestoration(mainRef)
 * <main ref={mainRef}>{children}</main>
 * ```
 */
export function useScrollRestoration(
  ref: React.RefObject<HTMLElement | null>,
): void {
  const { pathname } = useLocation()
  const navType = useNavigationType()
  const rafIdRef = useRef<number | null>(null)

  // Save on scroll (throttled via rAF).
  useEffect(() => {
    const el = ref.current
    if (!el) return

    const onScroll = () => {
      if (rafIdRef.current !== null) return
      rafIdRef.current = requestAnimationFrame(() => {
        rafIdRef.current = null
        try {
          sessionStorage.setItem(
            STORAGE_PREFIX + pathname,
            String(el.scrollTop),
          )
        } catch {
          // QuotaExceeded / private mode — silently skip
        }
      })
    }

    el.addEventListener('scroll', onScroll, { passive: true })
    return () => {
      el.removeEventListener('scroll', onScroll)
      if (rafIdRef.current !== null) cancelAnimationFrame(rafIdRef.current)
      rafIdRef.current = null
    }
  }, [ref, pathname])

  // Restore on mount / route change.
  useEffect(() => {
    const el = ref.current
    if (!el) return

    if (navType !== 'POP') {
      el.scrollTop = 0
      return
    }

    let stored: string | null = null
    try {
      stored = sessionStorage.getItem(STORAGE_PREFIX + pathname)
    } catch {
      return
    }
    if (stored === null) {
      el.scrollTop = 0
      return
    }
    const target = Number(stored)
    if (!Number.isFinite(target)) return

    let attempts = 0
    const tryRestore = () => {
      if (!el.isConnected) return
      if (el.scrollHeight >= target + el.clientHeight || attempts >= RAF_RESTORE_ATTEMPTS) {
        el.scrollTop = target
        return
      }
      attempts += 1
      requestAnimationFrame(tryRestore)
    }
    requestAnimationFrame(tryRestore)
  }, [ref, pathname, navType])
}
