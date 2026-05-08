import { Link } from 'react-router'
import { motion } from 'motion/react'
import {
  BookOpen,
  CaretRight,
  ChartBar,
  ClockCountdown,
  FileText,
  Users,
  WarningCircle,
  type Icon,
} from '@phosphor-icons/react'
import { useAuth } from '@/features/auth/AuthProvider'
import { SkeletonList } from '@/shared/components/Skeleton'
import {
  useGroupExcuses,
  useGroupMembers,
  useGroupSubjects,
  usePendingLateCheckinRequests,
} from '@/features/headman/shared/headmanApi'
import { HeadmanWeeklyReportCard } from './HeadmanWeeklyReportCard'

interface HubCardContext {
  membersCount: number | undefined
  subjectsCount: number | undefined
  pendingExcusesCount: number | undefined
  pendingLateCheckinsCount: number | undefined
}

interface HubCard {
  route: string
  title: string
  Icon: Icon
  getMeta: (context: HubCardContext) => string
}

const HUB_CARDS: HubCard[] = [
  {
    route: '/group/overview',
    title: 'Обзор',
    Icon: ChartBar,
    getMeta: ({ membersCount }) => `Члены группы: ${membersCount ?? '-'}`,
  },
  {
    route: '/group/students',
    title: 'Студенты',
    Icon: Users,
    getMeta: ({ membersCount }) => `Члены группы: ${membersCount ?? '-'}`,
  },
  {
    route: '/group/subjects',
    title: 'Предметы',
    Icon: BookOpen,
    getMeta: ({ subjectsCount }) => `Предметов: ${subjectsCount ?? '-'}`,
  },
  {
    route: '/group/journal',
    title: 'Журнал',
    Icon: BookOpen,
    getMeta: ({ subjectsCount }) => `Отметки по предметам: ${subjectsCount ?? '-'}`,
  },
  {
    route: '/group/stats',
    title: 'Статистика',
    Icon: ChartBar,
    getMeta: ({ subjectsCount }) => `Пороги по предметам: ${subjectsCount ?? '-'}`,
  },
  {
    route: '/group/excuses',
    title: 'Уважительные пропуски',
    Icon: FileText,
    getMeta: ({ pendingExcusesCount }) => `На проверке: ${pendingExcusesCount ?? '-'}`,
  },
  {
    route: '/group/late-checkin',
    title: 'Запросы отметки',
    Icon: ClockCountdown,
    getMeta: ({ pendingLateCheckinsCount }) => `На проверке: ${pendingLateCheckinsCount ?? '-'}`,
  },
]

export function GroupHub() {
  const { user } = useAuth()
  const groupId = user?.groupId
  const isHeadman = user?.role === 'STUDENT' && user.isHeadman
  const headmanGroupId = isHeadman ? groupId : undefined
  const membersQuery = useGroupMembers(isHeadman)
  const subjectsQuery = useGroupSubjects(isHeadman)
  const excusesQuery = useGroupExcuses(headmanGroupId)
  const lateCheckinsQuery = usePendingLateCheckinRequests(headmanGroupId)

  if (!isHeadman) {
    return <HeadmanOnlyState />
  }

  const isLoading = membersQuery.isLoading
  const context: HubCardContext = {
    membersCount: membersQuery.data?.length,
    subjectsCount: subjectsQuery.data?.length,
    pendingExcusesCount: excusesQuery.data?.filter((ticket) => ticket.status === 'submitted').length,
    pendingLateCheckinsCount: lateCheckinsQuery.data?.filter((request) => request.status === 'pending').length,
  }

  return (
    <div className="min-h-full px-3 py-4 pb-[calc(72px+env(safe-area-inset-bottom))]">
      <header className="mb-4 flex items-start justify-between gap-3">
        <div>
          <p className="text-[11px] font-semibold uppercase tracking-wide" style={{ color: 'var(--text-muted)' }}>
            Староста
          </p>
          <h1
            className="mt-1 text-lg font-semibold"
            style={{ color: 'var(--text-primary)', fontFamily: 'var(--font-heading)' }}
          >
            Группа
          </h1>
        </div>
        <div
          className="grid size-11 place-items-center rounded-lg border"
          style={{
            background: 'color-mix(in oklab, var(--accent-primary) 12%, transparent)',
            borderColor: 'var(--border-accent)',
            color: 'var(--accent-primary)',
          }}
          aria-hidden="true"
        >
          <Users size={24} weight="duotone" />
        </div>
      </header>

      {isLoading && <SkeletonList count={5} />}

      {!isLoading && membersQuery.isError && (
        <InlineNotice text="Часть данных группы не загрузилась. Разделы останутся доступны." />
      )}

      {!isLoading && (
        <div className="grid grid-cols-1 gap-3">
          {HUB_CARDS.map((card, index) => (
            <motion.div
              key={card.route}
              initial={{ opacity: 0, y: 8 }}
              animate={{ opacity: 1, y: 0 }}
              transition={{ duration: 0.16, delay: index * 0.025, ease: 'easeOut' }}
            >
              <HubCardView card={card} context={context} />
            </motion.div>
          ))}
        </div>
      )}

      {!isLoading && <HeadmanWeeklyReportCard />}
    </div>
  )
}

function HubCardView({
  card,
  context,
}: {
  card: HubCard
  context: HubCardContext
}) {
  return (
    <Link
      to={card.route}
      className="flex min-h-[76px] items-center gap-3 rounded-lg border px-3 py-3 text-left"
      style={{ background: 'var(--bg-secondary)', borderColor: 'var(--border-subtle)' }}
    >
      <span
        aria-hidden="true"
        className="grid size-11 shrink-0 place-items-center rounded-lg border"
        style={{
          background: 'color-mix(in oklab, var(--accent-primary) 12%, transparent)',
          borderColor: 'var(--border-accent)',
          color: 'var(--accent-primary)',
        }}
      >
        <card.Icon size={23} weight="duotone" />
      </span>
      <span className="min-w-0 flex-1">
        <span
          className="block text-sm font-semibold"
          style={{ color: 'var(--text-primary)', fontFamily: 'var(--font-heading)' }}
        >
          {card.title}
        </span>
        <span className="mt-0.5 block text-xs" style={{ color: 'var(--text-muted)' }}>
          {card.getMeta(context)}
        </span>
      </span>
      <CaretRight size={18} weight="bold" style={{ color: 'var(--text-muted)' }} />
    </Link>
  )
}

function HeadmanOnlyState() {
  return (
    <div className="flex min-h-[60vh] flex-col items-center justify-center gap-3 px-4 text-center">
      <div
        className="grid size-14 place-items-center rounded-full"
        style={{
          background: 'color-mix(in oklab, var(--accent-warning) 14%, transparent)',
          border: '1px solid color-mix(in oklab, var(--accent-warning) 30%, transparent)',
          color: 'var(--accent-warning)',
        }}
      >
        <WarningCircle size={24} weight="duotone" />
      </div>
      <p className="text-base font-semibold" style={{ color: 'var(--text-primary)' }}>
        Раздел старосты
      </p>
      <p className="max-w-[28ch] text-sm" style={{ color: 'var(--text-secondary)' }}>
        Доступен только студенту со статусом старосты.
      </p>
    </div>
  )
}

function InlineNotice({ text }: { text: string }) {
  return (
    <div
      role="status"
      className="mb-3 flex items-start gap-2 rounded-lg border px-3 py-2 text-xs"
      style={{
        background: 'color-mix(in oklab, var(--accent-warning) 10%, transparent)',
        borderColor: 'color-mix(in oklab, var(--accent-warning) 30%, transparent)',
        color: 'var(--accent-warning)',
      }}
    >
      <WarningCircle size={16} weight="bold" className="mt-0.5 shrink-0" aria-hidden="true" />
      <span>{text}</span>
    </div>
  )
}
