import { motion } from 'motion/react'
import type { OverallStats } from './api'
import { useCountUp } from './useCountUp'

interface StatCardsProps {
  overall: OverallStats
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
      transition={{ duration: 0.3, delay: index * 0.06, ease: 'easeOut' }}
      className="rounded-2xl border p-3"
      style={{ background: 'var(--bg-secondary)', borderColor: 'var(--border-subtle)' }}
    >
      <p
        className="text-2xl font-bold leading-none tabular-nums"
        style={{ color, fontFamily: 'var(--font-display)' }}
      >
        {format(animated)}
      </p>
      <p
        className="mt-2 text-[10px] font-medium uppercase tracking-wide"
        style={{ color: 'var(--text-muted)' }}
      >
        {label}
      </p>
    </motion.div>
  )
}

export function StatCards({ overall }: StatCardsProps) {
  return (
    <div className="grid grid-cols-3 gap-2">
      <StatCard
        value={overall.percentage}
        label="Посещ."
        color={colorForPercentage(overall.percentage)}
        index={0}
        format={(n) => `${Math.round(n)}%`}
      />
      <StatCard value={overall.absent} label="Пропусков" color="var(--accent-danger)" index={1} />
      <StatCard value={overall.excused} label="Уважит." color="var(--accent-warning)" index={2} />
    </div>
  )
}
