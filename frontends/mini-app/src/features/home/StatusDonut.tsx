import { motion } from 'motion/react'
import { Cell, Pie, PieChart, ResponsiveContainer } from 'recharts'
import type { StatusBreakdown } from './api'

interface StatusDonutProps {
  breakdown: StatusBreakdown
}

interface Slice {
  key: string
  label: string
  value: number
  color: string
}

function buildSlices(breakdown: StatusBreakdown): Slice[] {
  return [
    { key: 'present', label: 'Присутствовал', value: breakdown.present, color: 'var(--accent-primary)' },
    { key: 'absent', label: 'Прогул', value: breakdown.absent, color: 'var(--accent-danger)' },
    {
      key: 'excused',
      label: 'Уважит.',
      value: breakdown.excused + breakdown.freeAttendance,
      color: 'var(--accent-secondary)',
    },
  ]
}

export function StatusDonut({ breakdown }: StatusDonutProps) {
  const slices = buildSlices(breakdown)
  const total = slices.reduce((sum, item) => sum + item.value, 0)
  const nonZero = slices.filter((item) => item.value > 0)
  const chartData = nonZero.length
    ? nonZero
    : [{ key: 'empty', label: 'Нет данных', value: 1, color: 'var(--border-subtle)' }]

  return (
    <motion.section
      initial={{ opacity: 0, y: 10 }}
      animate={{ opacity: 1, y: 0 }}
      transition={{ duration: 0.3, delay: 0.2, ease: 'easeOut' }}
      className="rounded-2xl border p-4"
      style={{ background: 'var(--bg-secondary)', borderColor: 'var(--border-subtle)' }}
    >
      <h3
        className="mb-3 text-xs font-semibold uppercase tracking-wide"
        style={{ color: 'var(--text-muted)', fontFamily: 'var(--font-heading)' }}
      >
        Статус пар
      </h3>

      <div className="relative mx-auto size-[168px]">
        <ResponsiveContainer width="100%" height="100%">
          <PieChart>
            <Pie
              data={chartData}
              dataKey="value"
              nameKey="label"
              innerRadius="66%"
              outerRadius="100%"
              strokeWidth={0}
              startAngle={90}
              endAngle={-270}
              isAnimationActive
            >
              {chartData.map((slice) => (
                <Cell key={slice.key} fill={slice.color} />
              ))}
            </Pie>
          </PieChart>
        </ResponsiveContainer>
        <div className="pointer-events-none absolute inset-0 flex flex-col items-center justify-center">
          <span
            className="text-3xl leading-none tabular-nums"
            style={{ color: 'var(--text-primary)', fontFamily: 'var(--font-display)' }}
          >
            {total}
          </span>
          <span className="mt-1 text-xs uppercase tracking-wide" style={{ color: 'var(--text-muted)' }}>
            пар
          </span>
        </div>
      </div>

      <ul className="mt-3 grid grid-cols-2 gap-x-3 gap-y-2">
        {slices.map((slice) => (
          <li key={slice.key} className="flex items-center gap-2 text-xs">
            <span aria-hidden="true" className="size-2 shrink-0 rounded-full" style={{ background: slice.color }} />
            <span className="truncate" style={{ color: 'var(--text-secondary)' }}>
              {slice.label}
            </span>
            <span className="ml-auto tabular-nums font-semibold" style={{ color: 'var(--text-primary)' }}>
              {slice.value}
            </span>
          </li>
        ))}
      </ul>
    </motion.section>
  )
}
