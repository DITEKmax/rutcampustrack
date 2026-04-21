import { useEffect, useRef, useState } from 'react'
import { AnimatePresence, motion } from 'motion/react'
import { Copy, WarningCircle, X } from '@phosphor-icons/react'
import type { ProblemDetails } from '@/api/problemDetails'
import { errorToastBus } from '@/shared/lib/errorToastBus'

/**
 * Глобальный хост RFC 9457 error toast'ов. Слушает `errorToastBus` и
 * показывает последний ProblemDetails с title + detail + traceId (hidden
 * behind copy-button). Авто-скрытие через 5с (сверхмягкая ошибка —
 * network/сервер); 401/403/429 обычно обрабатываются локально.
 *
 * Суrepression: одна и та же ошибка (status+type+detail) в течение 1.5с
 * показывается один раз — без debounce TanStack Query любит флудить на
 * прoxy-timeout'ах.
 */
export function ErrorToastHost() {
  const [current, setCurrent] = useState<ProblemDetails | null>(null)
  const [copied, setCopied] = useState(false)
  const lastKeyRef = useRef<{ key: string; ts: number } | null>(null)
  const timerRef = useRef<ReturnType<typeof setTimeout> | null>(null)

  useEffect(() => {
    return errorToastBus.subscribe((problem) => {
      // Suppress 401 — они обрабатываются auth refresh/logout.
      if (problem.status === 401) return

      const key = `${problem.status}|${problem.type}|${problem.detail}`
      const now = Date.now()
      if (lastKeyRef.current && lastKeyRef.current.key === key && now - lastKeyRef.current.ts < 1500) {
        return
      }
      lastKeyRef.current = { key, ts: now }

      setCurrent(problem)
      setCopied(false)
      if (timerRef.current) clearTimeout(timerRef.current)
      timerRef.current = setTimeout(() => setCurrent(null), 5000)
    })
  }, [])

  useEffect(() => () => {
    if (timerRef.current) clearTimeout(timerRef.current)
  }, [])

  const copyTraceId = async () => {
    if (!current?.traceId) return
    try {
      await navigator.clipboard.writeText(current.traceId)
      setCopied(true)
      setTimeout(() => setCopied(false), 1500)
    } catch {
      /* clipboard может не работать в non-secure context — молча игнорируем */
    }
  }

  const dismiss = () => {
    if (timerRef.current) clearTimeout(timerRef.current)
    setCurrent(null)
  }

  return (
    <AnimatePresence>
      {current && (
        <motion.div
          key={current.traceId ?? `${current.status}-${current.timestamp ?? Date.now()}`}
          initial={{ opacity: 0, y: 32 }}
          animate={{ opacity: 1, y: 0 }}
          exit={{ opacity: 0, y: 32 }}
          transition={{ duration: 0.2, ease: 'easeOut' }}
          role="alert"
          className="fixed inset-x-4 bottom-20 z-[var(--z-toast)] mx-auto flex max-w-sm flex-col gap-2 rounded-xl px-4 py-3"
          style={{
            background: 'var(--bg-elevated)',
            border: '1px solid color-mix(in oklab, var(--accent-danger) 32%, transparent)',
            boxShadow: 'var(--shadow-lg)',
          }}
        >
          <div className="flex items-start gap-3">
            <span
              aria-hidden="true"
              className="grid size-9 shrink-0 place-items-center rounded-full"
              style={{
                background: 'color-mix(in oklab, var(--accent-danger) 18%, transparent)',
                color: 'var(--accent-danger)',
              }}
            >
              <WarningCircle size={20} weight="fill" />
            </span>
            <div className="min-w-0 flex-1">
              <p
                className="text-sm font-semibold leading-tight"
                style={{ color: 'var(--text-primary)' }}
              >
                {current.title}
              </p>
              {current.detail && (
                <p
                  className="mt-0.5 text-xs leading-snug"
                  style={{ color: 'var(--text-secondary)' }}
                >
                  {current.detail}
                </p>
              )}
            </div>
            <button
              type="button"
              onClick={dismiss}
              aria-label="Закрыть уведомление"
              className="shrink-0 rounded-full p-1"
              style={{ color: 'var(--text-muted)' }}
            >
              <X size={16} weight="bold" />
            </button>
          </div>

          {current.traceId && (
            <button
              type="button"
              onClick={copyTraceId}
              className="inline-flex items-center gap-1.5 self-start rounded-full border px-2 py-0.5 text-[11px] font-medium"
              style={{
                background: 'var(--bg-surface)',
                borderColor: 'var(--border-subtle)',
                color: 'var(--text-muted)',
              }}
              title="Скопировать trace id для обращения в поддержку"
            >
              <Copy size={12} weight="bold" aria-hidden="true" />
              {copied ? 'Скопировано' : `trace: ${current.traceId.slice(0, 8)}…`}
            </button>
          )}
        </motion.div>
      )}
    </AnimatePresence>
  )
}
