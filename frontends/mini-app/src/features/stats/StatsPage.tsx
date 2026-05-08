import { ChartBar, WarningCircle, ArrowsClockwise } from '@phosphor-icons/react'
import { SkeletonList } from '@/shared/components/Skeleton'
import { useAuth } from '@/features/auth/AuthProvider'
import { useStudentStats, useStudentThresholds } from './api'
import { SubjectStatsCard } from './SubjectStatsCard'

/**
 * Statistics page for the Telegram Mini App.
 *
 * Title comes from AppHeader. This page owns the overall stats hero, the
 * per-subject list, and the standard three states (loading / error / empty).
 */
export function StatsPage() {
  const { user } = useAuth()
  const { data, isLoading, isError, refetch } = useStudentStats()
  const thresholds = useStudentThresholds(user?.groupId, data?.subjects)

  return (
    <div className="px-3 pt-3 pb-[calc(72px+env(safe-area-inset-bottom))]">
      {isLoading && <SkeletonList count={4} />}

      {isError && (
        <div className="flex min-h-[40vh] flex-col items-center justify-center gap-3 text-center">
          <div
            className="grid size-12 place-items-center rounded-full"
            style={{
              background: 'color-mix(in oklab, var(--accent-danger) 14%, transparent)',
              border: '1px solid color-mix(in oklab, var(--accent-danger) 30%, transparent)',
              color: 'var(--accent-danger)',
            }}
          >
            <WarningCircle size={22} weight="fill" />
          </div>
          <p
            className="text-sm font-semibold text-balance"
            style={{
              color: 'var(--text-primary)',
              fontFamily: 'var(--font-heading)',
            }}
          >
            Не удалось загрузить статистику
          </p>
          <p
            className="max-w-[28ch] text-xs text-pretty"
            style={{ color: 'var(--text-secondary)' }}
          >
            Проверьте соединение и попробуйте снова.
          </p>
          <button
            type="button"
            onClick={() => refetch()}
            className="inline-flex items-center gap-1.5 rounded-full border px-3 py-1.5 text-xs font-semibold"
            style={{
              borderColor: 'var(--border-accent)',
              color: 'var(--accent-primary)',
              background: 'color-mix(in oklab, var(--accent-primary) 10%, transparent)',
            }}
          >
            <ArrowsClockwise size={12} weight="bold" />
            Обновить
          </button>
        </div>
      )}

      {!isLoading && !isError && (!data || data.subjects.length === 0) && (
        <div
          className="mt-4 flex flex-col items-center gap-3 rounded-2xl border border-dashed p-6 text-center"
          style={{
            borderColor: 'var(--border-default)',
            background: 'color-mix(in oklab, var(--bg-secondary) 50%, transparent)',
          }}
        >
          <div
            className="grid size-12 place-items-center rounded-full"
            style={{
              background: 'color-mix(in oklab, var(--accent-primary) 12%, transparent)',
              border: '1px solid var(--border-accent)',
              color: 'var(--accent-primary)',
            }}
          >
            <ChartBar size={22} weight="duotone" />
          </div>
          <p
            className="text-sm font-semibold text-balance"
            style={{
              color: 'var(--text-primary)',
              fontFamily: 'var(--font-heading)',
            }}
          >
            Нет данных о посещаемости
          </p>
          <p
            className="max-w-[28ch] text-xs text-pretty"
            style={{ color: 'var(--text-secondary)' }}
          >
            Статистика появится после первых пар.
          </p>
        </div>
      )}

      {!isLoading && !isError && data && data.subjects.length > 0 && (
        <div className="flex flex-col gap-3">
          {/* Overall hero — big Clash Display percentage */}
          <section
            className="relative overflow-hidden rounded-2xl border p-4"
            style={{
              background: 'var(--bg-secondary)',
              borderColor: 'var(--border-subtle)',
            }}
          >
            <span
              aria-hidden="true"
              className="pointer-events-none absolute -right-12 -top-12 size-36 rounded-full opacity-50"
              style={{ background: 'var(--gradient-glow)' }}
            />
            <div className="relative flex items-baseline justify-between gap-3">
              <div className="flex flex-col gap-0.5">
                <p
                  className="text-[11px] font-medium uppercase tracking-wide"
                  style={{ color: 'var(--text-muted)', fontFamily: 'var(--font-mono)' }}
                >
                  Общая посещаемость
                </p>
                <p
                  className="text-4xl font-bold leading-none tabular-nums"
                  style={{
                    color: 'var(--text-primary)',
                    fontFamily: 'var(--font-display)',
                    letterSpacing: '-0.02em',
                  }}
                >
                  {data.overall.percentage}
                  <span
                    className="text-xl font-bold"
                    style={{ color: 'var(--accent-primary)' }}
                  >
                    %
                  </span>
                </p>
              </div>
            </div>
            <dl
              className="relative mt-3 grid grid-cols-3 gap-2 border-t pt-3"
              style={{ borderColor: 'var(--border-subtle)' }}
            >
              <OverallStat label="Был" value={data.overall.attended} />
              <OverallStat label="Отсутств." value={data.overall.absent} />
              <OverallStat label="Уважит." value={data.overall.excused} />
            </dl>
          </section>

          {data.subjects.map((subj) => (
            <SubjectStatsCard key={subj.subjectId} stats={subj} threshold={thresholds[subj.subjectId]} />
          ))}
        </div>
      )}
    </div>
  )
}

function OverallStat({ label, value }: { label: string; value: number }) {
  return (
    <div className="flex flex-col gap-0.5">
      <dt
        className="text-[10px] font-medium uppercase tracking-wide"
        style={{ color: 'var(--text-muted)' }}
      >
        {label}
      </dt>
      <dd
        className="text-sm font-semibold tabular-nums"
        style={{ color: 'var(--text-primary)' }}
      >
        {value}
      </dd>
    </div>
  )
}
