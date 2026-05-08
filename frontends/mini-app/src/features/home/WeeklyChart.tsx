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

interface WeeklyChartProps {
  weekly: WeeklyStat[]
}

interface TooltipPayload {
  payload?: Array<{ payload: WeeklyStat }>
  active?: boolean
}

function CustomTooltip({ active, payload }: TooltipPayload) {
  if (!active || !payload?.length) return null
  const row = payload[0].payload
  return (
    <div
      className="rounded-lg border px-3 py-2 text-xs"
      style={{
        background: 'var(--bg-elevated)',
        borderColor: 'var(--border-default)',
        color: 'var(--text-primary)',
        boxShadow: 'var(--shadow-md)',
      }}
    >
      <div className="font-semibold">{row.label}</div>
      <div className="tabular-nums" style={{ color: 'var(--accent-secondary)' }}>
        {Math.round(row.percentage)}% · {row.attended}/{row.total}
      </div>
    </div>
  )
}

export function WeeklyChart({ weekly }: WeeklyChartProps) {
  const data = weekly.map((item) => ({
    ...item,
    pct: Math.round(item.percentage * 10) / 10,
  }))

  return (
    <motion.section
      initial={{ opacity: 0, y: 10 }}
      animate={{ opacity: 1, y: 0 }}
      transition={{ duration: 0.3, delay: 0.26, ease: 'easeOut' }}
      className="rounded-2xl border p-4"
      style={{ background: 'var(--bg-secondary)', borderColor: 'var(--border-subtle)' }}
    >
      <h3
        className="mb-3 text-xs font-semibold uppercase tracking-wide"
        style={{ color: 'var(--text-muted)', fontFamily: 'var(--font-heading)' }}
      >
        По неделям
      </h3>

      {data.length === 0 ? (
        <p className="py-8 text-center text-xs" style={{ color: 'var(--text-muted)' }}>
          Пока нет данных за семестр
        </p>
      ) : (
        <div className="h-[170px] w-full">
          <ResponsiveContainer width="100%" height="100%">
            <AreaChart data={data} margin={{ top: 8, right: 8, left: -24, bottom: 4 }}>
              <defs>
                <linearGradient id="miniWeeklyFill" x1="0" y1="0" x2="0" y2="1">
                  <stop offset="0%" stopColor="var(--accent-secondary)" stopOpacity={0.35} />
                  <stop offset="100%" stopColor="var(--accent-secondary)" stopOpacity={0} />
                </linearGradient>
              </defs>
              <CartesianGrid stroke="var(--border-subtle)" strokeDasharray="3 3" vertical={false} />
              <XAxis
                dataKey="label"
                tick={{ fill: 'var(--text-muted)', fontSize: 10 }}
                axisLine={false}
                tickLine={false}
                interval="preserveStartEnd"
              />
              <YAxis
                domain={[0, 100]}
                ticks={[0, 50, 100]}
                tick={{ fill: 'var(--text-muted)', fontSize: 10 }}
                axisLine={false}
                tickLine={false}
                width={38}
                tickFormatter={(value) => `${value}%`}
              />
              <Tooltip content={<CustomTooltip />} cursor={{ stroke: 'var(--accent-secondary)', strokeOpacity: 0.3 }} />
              <Area
                type="monotone"
                dataKey="pct"
                stroke="var(--accent-secondary)"
                strokeWidth={2}
                fill="url(#miniWeeklyFill)"
                dot={{ r: 3, stroke: 'var(--accent-secondary)', fill: 'var(--bg-secondary)', strokeWidth: 2 }}
                activeDot={{ r: 5 }}
                isAnimationActive
              />
            </AreaChart>
          </ResponsiveContainer>
        </div>
      )}
    </motion.section>
  )
}
