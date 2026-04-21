import type { CSSProperties } from 'react'
import { cn } from '@/lib/utils'
import type { LessonStatus, AttendanceStatus } from './types'
import { STATUS_SYMBOLS } from './statusSymbols'

/**
 * Transit Grid status pill (brandbook §4.4 "status chips").
 *
 * Attendance codes (+/н/у/сп) map to --status-* tokens defined in tokens.css,
 * so both themes render correctly. Lesson statuses (ACTIVE/PLANNED/…) use
 * accent colors — ACTIVE is the only one that claims the primary accent
 * (single-accent-per-view rule from baseline-ui).
 *
 * For `excused` pass `reason` to append the human-readable cause after "у"
 * (e.g. "у · Болезнь"). Kept optional so non-attendance callers are unaffected.
 */
interface StatusBadgeProps {
  status: LessonStatus | AttendanceStatus
  reason?: string | null
}

type StatusKey = LessonStatus | AttendanceStatus

const config: Record<StatusKey, { label: string; tone: string; strikethrough?: boolean }> = {
  present:         { label: STATUS_SYMBOLS.present,         tone: 'status-present' },
  absent:          { label: STATUS_SYMBOLS.absent,          tone: 'status-absent' },
  excused:         { label: STATUS_SYMBOLS.excused,         tone: 'status-excused' },
  free_attendance: { label: STATUS_SYMBOLS.free_attendance, tone: 'status-free' },
  cancelled:       { label: 'Отменена',      tone: 'lesson-muted', strikethrough: true },
  ACTIVE:          { label: 'Идёт',          tone: 'lesson-active' },
  PLANNED:         { label: 'Запланировано', tone: 'lesson-planned' },
  CANCELLED:       { label: 'Отменена',      tone: 'lesson-muted', strikethrough: true },
  CLOSED:          { label: 'Завершено',     tone: 'lesson-muted' },
}

export function StatusBadge({ status, reason }: StatusBadgeProps) {
  const conf = config[status]
  if (!conf) return null

  const showReason =
    (status === 'excused' || status === 'free_attendance') &&
    typeof reason === 'string' &&
    reason.trim().length > 0

  return (
    <span
      data-tone={conf.tone}
      className={cn(
        'inline-flex items-center whitespace-nowrap',
        'px-2.5 py-0.5 rounded-full',
        'text-[11px] font-medium tabular-nums',
        'border',
        conf.strikethrough && 'line-through',
      )}
      style={toneStyle(conf.tone)}
      title={showReason ? reason! : undefined}
    >
      {conf.label}
      {showReason && (
        <span className="ml-1 opacity-80 font-normal">· {reason}</span>
      )}
    </span>
  )
}

/**
 * Resolve a tone name to inline CSS variables. Kept inline (not a Tailwind
 * plugin) so every value flows straight from Transit Grid tokens without a
 * config layer. Single source of truth for badge colors.
 */
function toneStyle(tone: string): CSSProperties {
  switch (tone) {
    case 'status-present':
      return {
        background: 'color-mix(in oklab, var(--status-present) 16%, transparent)',
        color: 'var(--status-present)',
        borderColor: 'color-mix(in oklab, var(--status-present) 32%, transparent)',
      }
    case 'status-absent':
      return {
        background: 'color-mix(in oklab, var(--status-absent) 16%, transparent)',
        color: 'var(--status-absent)',
        borderColor: 'color-mix(in oklab, var(--status-absent) 32%, transparent)',
      }
    case 'status-excused':
      return {
        background: 'color-mix(in oklab, var(--status-excused) 16%, transparent)',
        color: 'var(--status-excused)',
        borderColor: 'color-mix(in oklab, var(--status-excused) 32%, transparent)',
      }
    case 'status-free':
      return {
        background: 'color-mix(in oklab, var(--status-free-attendance) 16%, transparent)',
        color: 'var(--status-free-attendance)',
        borderColor: 'color-mix(in oklab, var(--status-free-attendance) 32%, transparent)',
      }
    case 'lesson-active':
      return {
        background: 'var(--accent-primary)',
        color: 'var(--accent-primary-contrast)',
        borderColor: 'transparent',
      }
    case 'lesson-planned':
      return {
        background: 'var(--bg-surface)',
        color: 'var(--text-secondary)',
        borderColor: 'var(--border-subtle)',
      }
    case 'lesson-muted':
    default:
      return {
        background: 'var(--bg-surface)',
        color: 'var(--text-muted)',
        borderColor: 'var(--border-subtle)',
      }
  }
}
