import { motion } from 'motion/react'
import type { OverallStats } from './api'
import { useCountUp } from './useCountUp'

interface Props {
  overall: OverallStats
  delay?: number
}

function colorForPercentage(pct: number): string {
  if (pct >= 80) return 'var(--accent-primary)'
  if (pct >= 50) return 'var(--accent-warning)'
  return 'var(--accent-danger)'
}

function StatCard({
  value,
  label,
  color,
  index,
  format = (n) => String(Math.round(n)),
}: {
  value: number
  label: string
  color: string
  index: number
  format?: (n: number) => string
}) {
  const animated = useCountUp(value, 800)
  return (
    <motion.div
      initial={{ opacity: 0, y: 10 }}
      animate={{ opacity: 1, y: 0 }}
      transition={{ duration: 0.4, delay: index * 0.08, ease: [0.16, 1, 0.3, 1] }}
      className="rounded-[var(--radius-md)] border p-[var(--space-4)]"
      style={{
        background: 'var(--bg-secondary)',
        borderColor: 'var(--border-subtle)',
      }}
    >
      <p
        className="font-bold leading-none tabular-nums"
        style={{
          color,
          fontFamily: 'var(--font-display)',
          fontSize: 'clamp(1.75rem, 8vw, 2.5rem)',
        }}
      >
        {format(animated)}
      </p>
      <p
        className="mt-[var(--space-2)] text-[var(--text-xs)] font-medium uppercase tracking-wide"
        style={{ color: 'var(--text-muted)' }}
      >
        {label}
      </p>
    </motion.div>
  )
}

export function StatCards({ overall }: Props) {
  const percentageColor = colorForPercentage(overall.percentage)
  return (
    <div className="grid grid-cols-3 gap-[var(--space-3)]">
      <StatCard
        value={overall.percentage}
        label="Посещ."
        color={percentageColor}
        index={0}
        format={(n) => `${Math.round(n)}%`}
      />
      <StatCard
        value={overall.absent}
        label="Пропусков"
        color="var(--accent-danger)"
        index={1}
      />
      <StatCard
        value={overall.excused}
        label="Уважит."
        color="var(--accent-warning)"
        index={2}
      />
    </div>
  )
}
