import { cn } from '@/lib/utils'
import { RED_ZONE_THRESHOLD } from './api'
import type { SubjectStats } from './types'

/**
 * Per-subject attendance card (brandbook §4.3-ish stat widget, compact).
 *
 * Red-zone (percentage < threshold) gets a destructive left border + a
 * destructive-tinted percentage — the exact Tailwind classes the test
 * asserts on (`border-l-4`, `border-l-destructive`, `text-destructive`).
 * Those classes map to Transit Grid `--accent-danger` through the shadcn
 * bridge in `index.css`, so they're on-brand.
 */
interface SubjectStatsCardProps {
  stats: SubjectStats
}

export function SubjectStatsCard({ stats }: SubjectStatsCardProps) {
  const isRedZone = stats.percentage < RED_ZONE_THRESHOLD
  const pct = Math.min(Math.max(stats.percentage, 0), 100)

  return (
    <div
      className={cn(
        'rounded-2xl border p-4',
        isRedZone && 'border-l-4 border-l-destructive',
      )}
      style={{
        background: 'var(--bg-secondary)',
        borderColor: isRedZone ? undefined : 'var(--border-subtle)',
      }}
    >
      <div className="flex items-center justify-between gap-3">
        <span
          className="line-clamp-1 flex-1 text-sm font-semibold text-balance"
          style={{
            color: 'var(--text-primary)',
            fontFamily: 'var(--font-heading)',
          }}
        >
          {stats.subjectName}
        </span>
        <span
          className={cn(
            'shrink-0 text-sm font-bold tabular-nums',
            isRedZone && 'text-destructive',
          )}
          style={{
            color: isRedZone ? undefined : 'var(--text-primary)',
            fontFamily: 'var(--font-display)',
          }}
        >
          {stats.percentage}%
        </span>
      </div>

      {/* Progress bar — compositor-only (width is set once, not animated) */}
      <div
        className="mt-2 h-2 w-full overflow-hidden rounded-full"
        style={{ background: 'var(--bg-surface)' }}
      >
        <div
          className="h-full rounded-full"
          style={{
            width: `${pct}%`,
            background: isRedZone ? 'var(--accent-danger)' : 'var(--gradient-brand)',
          }}
        />
      </div>

      <div
        className="mt-2 flex gap-4 text-[11px] font-medium tabular-nums"
        style={{ color: 'var(--text-muted)', fontFamily: 'var(--font-mono)' }}
      >
        <span>Н {stats.absent}</span>
        <span>У {stats.excused}</span>
      </div>
    </div>
  )
}
