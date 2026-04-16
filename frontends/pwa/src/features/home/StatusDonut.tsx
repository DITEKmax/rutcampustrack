import { motion } from 'motion/react'
import { PieChart, Pie, Cell, ResponsiveContainer } from 'recharts'
import type { StatusBreakdown } from './api'

interface Props {
  breakdown: StatusBreakdown
}

interface Slice {
  key: string
  label: string
  value: number
  color: string
}

function buildSlices(b: StatusBreakdown): Slice[] {
  const presentExceptForgot = Math.max(0, b.present - b.forgotToCheckin)
  return [
    { key: 'present', label: 'Присутствовал', value: presentExceptForgot, color: 'var(--accent-primary)' },
    { key: 'absent', label: 'Прогул', value: b.absent, color: 'var(--accent-danger)' },
    { key: 'excused', label: 'Уважит.', value: b.excused, color: 'var(--accent-secondary)' },
    { key: 'free', label: 'Больничный', value: b.freeAttendance, color: 'var(--accent-info)' },
    { key: 'forgot', label: 'Забыл отметиться', value: b.forgotToCheckin, color: 'var(--accent-warning)' },
  ]
}

export function StatusDonut({ breakdown }: Props) {
  const slices = buildSlices(breakdown)
  const total = slices.reduce((s, x) => s + x.value, 0)
  const nonZero = slices.filter((s) => s.value > 0)

  return (
    <motion.section
      initial={{ opacity: 0, y: 10 }}
      animate={{ opacity: 1, y: 0 }}
      transition={{ duration: 0.4, delay: 0.32, ease: [0.16, 1, 0.3, 1] }}
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
        Статус пар
      </h3>

      <div className="relative mx-auto size-[180px]">
        <ResponsiveContainer width="100%" height="100%">
          <PieChart>
            <Pie
              data={nonZero.length ? nonZero : [{ key: 'empty', label: 'Нет данных', value: 1, color: 'var(--border-subtle)' }]}
              dataKey="value"
              nameKey="label"
              innerRadius="66%"
              outerRadius="100%"
              strokeWidth={0}
              startAngle={90}
              endAngle={-270}
              isAnimationActive
              animationDuration={900}
              animationEasing="ease-out"
            >
              {(nonZero.length ? nonZero : [{ color: 'var(--border-subtle)' }]).map((s, i) => (
                <Cell key={i} fill={s.color} />
              ))}
            </Pie>
          </PieChart>
        </ResponsiveContainer>
        <div className="pointer-events-none absolute inset-0 flex flex-col items-center justify-center">
          <span
            className="leading-none tabular-nums"
            style={{
              color: 'var(--text-primary)',
              fontFamily: 'var(--font-display)',
              fontSize: 'var(--text-3xl)',
            }}
          >
            {total}
          </span>
          <span
            className="mt-1 text-[var(--text-xs)] uppercase tracking-wide"
            style={{ color: 'var(--text-muted)' }}
          >
            пар
          </span>
        </div>
      </div>

      <ul className="mt-[var(--space-4)] grid grid-cols-2 gap-x-[var(--space-3)] gap-y-[var(--space-2)]">
        {slices.map((s) => (
          <li key={s.key} className="flex items-center gap-2 text-[var(--text-xs)]">
            <span
              aria-hidden="true"
              className="size-2 shrink-0 rounded-full"
              style={{ background: s.color }}
            />
            <span className="truncate" style={{ color: 'var(--text-secondary)' }}>
              {s.label}
            </span>
            <span
              className="ml-auto tabular-nums font-semibold"
              style={{ color: 'var(--text-primary)' }}
            >
              {s.value}
            </span>
          </li>
        ))}
      </ul>
    </motion.section>
  )
}
