import type { CSSProperties } from 'react'
import type { LessonStatus, AttendanceStatus } from './types'

/**
 * Transit Grid status pill (brandbook §4.4), mini-app variant.
 *
 * Same visual language as the PWA StatusBadge so users moving between
 * Telegram Mini App and the browser PWA recognize the same chips instantly.
 * All colors flow from Transit Grid status tokens defined in tokens.css.
 */
interface StatusBadgeProps {
  status: LessonStatus | AttendanceStatus
}

type StatusKey = LessonStatus | AttendanceStatus

const config: Record<StatusKey, { label: string; tone: string; strikethrough?: boolean }> = {
  present:         { label: 'б',             tone: 'status-present' },
  absent:          { label: 'н',             tone: 'status-absent' },
  excused:         { label: 'у',             tone: 'status-excused' },
  free_attendance: { label: 'сп',            tone: 'status-free' },
  ACTIVE:          { label: 'Идёт',          tone: 'lesson-active' },
  PLANNED:         { label: 'Запланировано', tone: 'lesson-planned' },
  CANCELLED:       { label: 'Отменена',      tone: 'lesson-muted', strikethrough: true },
  CLOSED:          { label: 'Завершено',     tone: 'lesson-muted' },
}

export function StatusBadge({ status }: StatusBadgeProps) {
  const conf = config[status]
  if (!conf) return null

  return (
    <span
      data-tone={conf.tone}
      className={[
        'inline-flex items-center whitespace-nowrap',
        'px-2 py-0.5 rounded-full',
        'text-[10px] font-medium tabular-nums',
        'border',
        conf.strikethrough ? 'line-through' : '',
      ]
        .filter(Boolean)
        .join(' ')}
      style={toneStyle(conf.tone)}
    >
      {conf.label}
    </span>
  )
}

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
