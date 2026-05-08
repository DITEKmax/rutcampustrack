import { motion } from 'motion/react'
import type { TopMissedSubject } from './api'

interface TopMissedListProps {
  items: TopMissedSubject[]
}

function subjectTypeLabel(type: string | undefined): string | null {
  switch ((type ?? '').trim().toLowerCase()) {
    case 'lecture':
      return 'ЛК'
    case 'practice':
      return 'ПЗ'
    case 'lab':
      return 'ЛЗ'
    default:
      return null
  }
}

function RankBadge({ rank }: { rank: number }) {
  const color =
    rank === 1
      ? 'var(--accent-danger)'
      : rank === 2
        ? '#F97316'
        : 'var(--accent-warning)'
  return (
    <span
      aria-hidden="true"
      className="grid size-7 shrink-0 place-items-center rounded-full text-xs font-bold tabular-nums"
      style={{
        background: `color-mix(in oklab, ${color} 18%, transparent)`,
        color,
        border: `1px solid color-mix(in oklab, ${color} 45%, transparent)`,
        fontFamily: 'var(--font-mono)',
      }}
    >
      {rank}
    </span>
  )
}

export function TopMissedList({ items }: TopMissedListProps) {
  return (
    <motion.section
      initial={{ opacity: 0, y: 10 }}
      animate={{ opacity: 1, y: 0 }}
      transition={{ duration: 0.3, delay: 0.32, ease: 'easeOut' }}
      className="rounded-2xl border p-4"
      style={{ background: 'var(--bg-secondary)', borderColor: 'var(--border-subtle)' }}
    >
      <h3
        className="mb-3 text-xs font-semibold uppercase tracking-wide"
        style={{ color: 'var(--text-muted)', fontFamily: 'var(--font-heading)' }}
      >
        Топ пропусков
      </h3>
      {items.length === 0 ? (
        <p className="py-6 text-center text-xs" style={{ color: 'var(--text-muted)' }}>
          Пропусков пока нет.
        </p>
      ) : (
        <ul className="flex flex-col">
          {items.map((item, idx) => {
            const typeLabel = subjectTypeLabel(item.subjectType)
            return (
              <li
                key={item.subjectId}
                className="flex items-center gap-3 py-3"
                style={{
                  borderBottom: idx < items.length - 1 ? '1px solid var(--border-subtle)' : 'none',
                }}
              >
                <RankBadge rank={idx + 1} />
                <div className="min-w-0 flex-1">
                  <div className="flex min-w-0 items-center gap-2">
                    <p className="truncate text-sm" style={{ color: 'var(--text-primary)' }}>
                      {item.subjectName}
                    </p>
                    {typeLabel && (
                      <span
                        className="shrink-0 rounded-full px-2 py-0.5 text-[10px] font-bold leading-none"
                        style={{
                          background: 'color-mix(in oklab, var(--accent-warning) 14%, transparent)',
                          border: '1px solid color-mix(in oklab, var(--accent-warning) 32%, transparent)',
                          color: 'var(--accent-warning)',
                          fontFamily: 'var(--font-mono)',
                        }}
                      >
                        {typeLabel}
                      </span>
                    )}
                  </div>
                  <p className="text-xs" style={{ color: 'var(--text-muted)' }}>
                    всего {item.total} пар
                  </p>
                </div>
                <span className="shrink-0 text-sm font-semibold tabular-nums" style={{ color: 'var(--accent-danger)' }}>
                  {item.absent} пр.
                </span>
              </li>
            )
          })}
        </ul>
      )}
    </motion.section>
  )
}
