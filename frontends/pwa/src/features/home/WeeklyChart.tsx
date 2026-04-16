import { motion } from 'motion/react'
import {
  Area,
  AreaChart,
  CartesianGrid,
  ResponsiveContainer,
  Tooltip,
  XAxis,
  YAxis,
} from 'recharts'
import type { WeeklyStat } from './api'

interface Props {
  weekly: WeeklyStat[]
}

interface TooltipPayload {
  label?: string
  payload?: Array<{ payload: WeeklyStat }>
  active?: boolean
}

function CustomTooltip({ active, payload }: TooltipPayload) {
  if (!active || !payload?.length) return null
  const row = payload[0].payload
  return (
    <div
      className="rounded-[var(--radius-sm)] border px-3 py-2 text-[var(--text-xs)]"
      style={{
        background: 'var(--bg-elevated)',
        borderColor: 'var(--border-default)',
        color: 'var(--text-primary)',
        boxShadow: 'var(--shadow-md)',
      }}
    >
      <div className="font-semibold">{row.label}</div>
      <div
        className="tabular-nums"
        style={{ color: 'var(--accent-secondary)' }}
      >
        {Math.round(row.percentage)}% · {row.attended}/{row.total}
      </div>
    </div>
  )
}

export function WeeklyChart({ weekly }: Props) {
  const data = weekly.map((w) => ({
    ...w,
    // Recharts needs primitive keys for axis + tooltip.
    pct: Math.round(w.percentage * 10) / 10,
  }))
  const empty = data.length === 0

  return (
    <motion.section
      initial={{ opacity: 0, y: 10 }}
      animate={{ opacity: 1, y: 0 }}
      transition={{ duration: 0.4, delay: 0.4, ease: [0.16, 1, 0.3, 1] }}
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
        По неделям
      </h3>

      {empty ? (
        <p
          className="py-8 text-center text-[var(--text-xs)]"
          style={{ color: 'var(--text-muted)' }}
        >
          Пока нет данных за семестр
        </p>
      ) : (
        <div className="h-[180px] w-full">
          <ResponsiveContainer width="100%" height="100%">
            <AreaChart data={data} margin={{ top: 8, right: 8, left: -24, bottom: 4 }}>
              <defs>
                <linearGradient id="weeklyFill" x1="0" y1="0" x2="0" y2="1">
                  <stop offset="0%" stopColor="var(--accent-secondary)" stopOpacity={0.35} />
                  <stop offset="100%" stopColor="var(--accent-secondary)" stopOpacity={0} />
                </linearGradient>
              </defs>
              <CartesianGrid stroke="var(--border-subtle)" strokeDasharray="3 3" vertical={false} />
              <XAxis
                dataKey="label"
                tick={{ fill: 'var(--text-muted)', fontSize: 11 }}
                axisLine={false}
                tickLine={false}
                interval="preserveStartEnd"
              />
              <YAxis
                domain={[0, 100]}
                ticks={[0, 25, 50, 75, 100]}
                tick={{ fill: 'var(--text-muted)', fontSize: 10 }}
                axisLine={false}
                tickLine={false}
                width={40}
                tickFormatter={(v) => `${v}%`}
              />
              <Tooltip
                content={<CustomTooltip />}
                cursor={{ stroke: 'var(--accent-secondary)', strokeOpacity: 0.3 }}
              />
              <Area
                type="monotone"
                dataKey="pct"
                stroke="var(--accent-secondary)"
                strokeWidth={2}
                fill="url(#weeklyFill)"
                dot={{ r: 3, stroke: 'var(--accent-secondary)', fill: 'var(--bg-secondary)', strokeWidth: 2 }}
                activeDot={{ r: 5 }}
                isAnimationActive
                animationDuration={900}
                animationEasing="ease-out"
              />
            </AreaChart>
          </ResponsiveContainer>
        </div>
      )}
    </motion.section>
  )
}
