import { motion } from 'motion/react'
import type { TopMissedSubject } from './api'

interface Props {
  items: TopMissedSubject[]
}

/**
 * Rank circle with gradient colour from red (rank 1) down to yellow (rank 5+).
 * Position number inside a subtle filled disk.
 */
function RankBadge({ rank }: { rank: number }) {
  const color =
    rank === 1
      ? 'var(--accent-danger)'
      : rank === 2
      ? '#F97316' /* dense orange — between danger and warning */
      : rank === 3
      ? 'var(--accent-warning)'
      : 'var(--accent-warning)'
  return (
    <span
      aria-hidden="true"
      className="grid size-7 shrink-0 place-items-center rounded-full text-[var(--text-xs)] font-bold tabular-nums"
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

export function TopMissedList({ items }: Props) {
  return (
    <motion.section
      initial={{ opacity: 0, y: 10 }}
      animate={{ opacity: 1, y: 0 }}
      transition={{ duration: 0.4, delay: 0.48, ease: [0.16, 1, 0.3, 1] }}
      className="rounded-[var(--radius-lg)] border p-[var(--space-4)]"
      style={{
        background: 'var(--bg-secondary)',
        borderColor: 'var(--border-subtle)',
      }}
    >
      <h3
        className="mb-[var(--space-3)] text-[var(--text-sm)] font-semibold uppercase tracking-wide"
        style={{ color: 'var(--text-muted)', fontFamily: 'var(--font-heading)' }}
      >
        Топ пропусков
      </h3>
      {items.length === 0 ? (
        <p
          className="py-6 text-center text-[var(--text-xs)]"
          style={{ color: 'var(--text-muted)' }}
        >
          Пропусков пока нет — так держать!
        </p>
      ) : (
        <ul className="flex flex-col">
          {items.map((item, idx) => (
            <li
              key={item.subjectId}
              className="flex items-center gap-[var(--space-3)] py-[var(--space-3)]"
              style={{
                borderBottom:
                  idx < items.length - 1
                    ? '1px solid var(--border-subtle)'
                    : 'none',
              }}
            >
              <RankBadge rank={idx + 1} />
              <div className="min-w-0 flex-1">
                <p
                  className="truncate text-[var(--text-sm)]"
                  style={{ color: 'var(--text-primary)' }}
                >
                  {item.subjectName}
                </p>
                <p
                  className="text-[var(--text-xs)]"
                  style={{ color: 'var(--text-muted)' }}
                >
                  всего {item.total} пар
                </p>
              </div>
              <span
                className="shrink-0 text-[var(--text-sm)] font-semibold tabular-nums"
                style={{ color: 'var(--accent-danger)' }}
              >
                {item.absent} пр.
              </span>
            </li>
          ))}
        </ul>
      )}
    </motion.section>
  )
}
