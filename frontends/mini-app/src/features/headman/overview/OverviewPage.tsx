import type { ReactNode } from 'react'
import { Link } from 'react-router'
import {
  ArrowLeft,
  CalendarBlank,
  CaretRight,
  Clock,
  FileText,
  Users,
  WarningCircle,
} from '@phosphor-icons/react'
import { motion } from 'motion/react'
import { useAuth } from '@/features/auth/AuthProvider'
import {
  useGroupExcuses,
  useGroupMembers,
  usePendingLateCheckinRequests,
  useTodayLesson,
} from '@/features/headman/shared/headmanApi'

export function OverviewPage() {
  const { user } = useAuth()
  const groupId = user?.groupId
  const isHeadman = user?.role === 'STUDENT' && user.isHeadman
  const headmanGroupId = isHeadman ? groupId : undefined

  const membersQuery = useGroupMembers(isHeadman)
  const todayLessonQuery = useTodayLesson(headmanGroupId)
  const excusesQuery = useGroupExcuses(headmanGroupId)
  const lateCheckinsQuery = usePendingLateCheckinRequests(headmanGroupId)

  if (!isHeadman) {
    return (
      <div className="px-3 py-4">
        <BackLink />
        <InlineState text="Раздел доступен только старосте." />
      </div>
    )
  }

  if (!groupId) {
    return (
      <div className="px-3 py-4">
        <BackLink />
        <InlineState text="Группа не назначена." />
      </div>
    )
  }

  const memberCount = membersQuery.data?.length ?? 0
  const todayLesson = todayLessonQuery.data
  const excuseCount = excusesQuery.data?.filter((ticket) => ticket.status === 'submitted').length ?? 0
  const lateCheckinCount = lateCheckinsQuery.data?.filter((request) => request.status === 'pending').length ?? 0

  return (
    <div className="min-h-full px-3 py-4 pb-[calc(72px+env(safe-area-inset-bottom))]">
      <BackLink />

      <div className="mb-4">
        <p className="text-[11px] font-semibold uppercase tracking-wide" style={{ color: 'var(--text-muted)' }}>
          Группа
        </p>
        <h1
          className="mt-1 text-lg font-semibold"
          style={{ color: 'var(--text-primary)', fontFamily: 'var(--font-heading)' }}
        >
          Обзор
        </h1>
      </div>

      <div className="grid grid-cols-1 gap-3">
        <StatCard
          index={0}
          icon={<Users size={26} weight="duotone" />}
          label="Члены группы"
          value={membersQuery.isLoading ? '-' : String(memberCount)}
        />
        <StatCard
          index={1}
          icon={<CalendarBlank size={26} weight="duotone" />}
          label="Текущая пара"
          value={
            todayLessonQuery.isLoading
              ? 'Загрузка...'
              : todayLesson
                ? todayLesson.subjectName
                : 'На сегодня пар больше нет'
          }
          detail={todayLesson ? `${formatTime(todayLesson.startsAt)}-${formatTime(todayLesson.endsAt)}` : undefined}
        />
      </div>

      <h2 className="mb-2 mt-5 text-sm font-semibold" style={{ color: 'var(--text-muted)' }}>
        Ожидают действия
      </h2>

      <div className="flex flex-col gap-3">
        <ActionCard
          to="/group/excuses"
          icon={<FileText size={20} weight="duotone" />}
          title="Пропуски ждут одобрения"
          count={excuseCount}
          loading={excusesQuery.isLoading}
        />
        <ActionCard
          to="/group/late-checkin"
          icon={<Clock size={20} weight="duotone" />}
          title="Запросы опоздалой отметки"
          count={lateCheckinCount}
          loading={lateCheckinsQuery.isLoading}
        />
      </div>
    </div>
  )
}

function BackLink() {
  return (
    <Link
      to="/group"
      className="mb-4 inline-flex items-center gap-2 rounded-full border px-3 py-1.5 text-xs font-semibold"
      style={{
        background: 'var(--bg-secondary)',
        borderColor: 'var(--border-subtle)',
        color: 'var(--text-secondary)',
      }}
    >
      <ArrowLeft size={14} weight="bold" aria-hidden="true" />
      Назад
    </Link>
  )
}

function StatCard({
  index,
  icon,
  label,
  value,
  detail,
}: {
  index: number
  icon: ReactNode
  label: string
  value: string
  detail?: string
}) {
  return (
    <motion.div
      initial={{ opacity: 0, y: 8 }}
      animate={{ opacity: 1, y: 0 }}
      transition={{ duration: 0.16, delay: index * 0.04 }}
      className="rounded-lg border p-4"
      style={{
        background: 'var(--bg-secondary)',
        borderColor: 'var(--border-subtle)',
      }}
    >
      <div className="flex items-start justify-between gap-3">
        <div className="min-w-0 flex-1">
          <p className="text-sm font-medium" style={{ color: 'var(--text-muted)' }}>
            {label}
          </p>
          <p className="mt-1 truncate text-lg font-semibold" style={{ color: 'var(--text-primary)' }}>
            {value}
          </p>
          {detail && (
            <p className="mt-0.5 text-xs tabular-nums" style={{ color: 'var(--text-muted)' }}>
              {detail}
            </p>
          )}
        </div>
        <span
          className="grid size-11 shrink-0 place-items-center rounded-lg border"
          style={{
            background: 'color-mix(in oklab, var(--accent-primary) 12%, transparent)',
            borderColor: 'var(--border-accent)',
            color: 'var(--accent-primary)',
          }}
          aria-hidden="true"
        >
          {icon}
        </span>
      </div>
    </motion.div>
  )
}

function ActionCard({
  to,
  icon,
  title,
  count,
  loading,
}: {
  to: string
  icon: ReactNode
  title: string
  count: number
  loading: boolean
}) {
  return (
    <Link
      to={to}
      className="flex min-h-[68px] items-center gap-3 rounded-lg border px-3 py-3"
      style={{
        background: 'var(--bg-secondary)',
        borderColor: 'var(--border-subtle)',
      }}
    >
      <span
        aria-hidden="true"
        className="grid size-10 shrink-0 place-items-center rounded-lg border"
        style={{
          background: 'color-mix(in oklab, var(--accent-primary) 12%, transparent)',
          borderColor: 'var(--border-accent)',
          color: 'var(--accent-primary)',
        }}
      >
        {icon}
      </span>
      <span className="min-w-0 flex-1 text-sm font-medium" style={{ color: 'var(--text-primary)' }}>
        {title}
      </span>
      <span
        className="rounded-full px-2 py-1 text-xs font-bold tabular-nums"
        style={{
          background: count > 0 ? 'var(--accent-danger)' : 'var(--bg-surface)',
          color: count > 0 ? 'white' : 'var(--text-muted)',
        }}
      >
        {loading ? '-' : count > 0 ? count : 'нет'}
      </span>
      <CaretRight size={18} weight="bold" style={{ color: 'var(--text-muted)' }} />
    </Link>
  )
}

function InlineState({ text }: { text: string }) {
  return (
    <div
      className="flex min-h-[40vh] flex-col items-center justify-center gap-3 text-center"
      style={{ color: 'var(--text-secondary)' }}
    >
      <WarningCircle size={28} weight="duotone" />
      <p className="text-sm">{text}</p>
    </div>
  )
}

function formatTime(value: string): string {
  if (!value) return ''
  if (value.includes('T')) return value.slice(11, 16)
  return value.slice(0, 5)
}
