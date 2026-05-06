import { Link } from 'react-router'
import { AnimatePresence, motion } from 'motion/react'
import {
  ChartBar,
  Users,
  BookOpen,
  Table,
  FileText,
  Clock,
  Percent,
  CaretRight,
  type Icon,
} from '@phosphor-icons/react'
import { LoadingSpinner } from '@/shared/components/LoadingSpinner'
import { useGroupMembers, useGroupSubjects } from '@/features/headman/shared/headmanApi'
import { useAuth } from '@/features/auth/AuthProvider'
import { HeadmanWeeklyReportCard } from './HeadmanWeeklyReportCard'
import { StudentAttendanceStatsBlock } from './StudentAttendanceStatsBlock'

interface HubCard {
  route: string
  title: string
  Icon: Icon
  getMeta: (membersCount: number | undefined, subjectsCount: number | undefined) => string
}

const HUB_CARDS: HubCard[] = [
  {
    route: '/group/overview',
    title: 'Обзор',
    Icon: ChartBar,
    getMeta: (m) => `Члены группы: ${m ?? '—'}`,
  },
  {
    route: '/group/students',
    title: 'Студенты',
    Icon: Users,
    getMeta: (m) => `${m ?? '—'} студентов в группе`,
  },
  {
    route: '/group/subjects',
    title: 'Предметы',
    Icon: BookOpen,
    getMeta: (_, s) => `${s ?? '—'} предметов`,
  },
  {
    route: '/group/journal',
    title: 'Журнал',
    Icon: Table,
    getMeta: () => 'Отметить посещение',
  },
  {
    route: '/group/excuses',
    title: 'Пропуски',
    Icon: FileText,
    getMeta: () => 'Запросы отпусков',
  },
  {
    route: '/group/late-checkin',
    title: 'Запросы отметки',
    Icon: Clock,
    getMeta: () => 'Запросы опоздалой отметки',
  },
  {
    route: '/group/stats',
    title: 'Статистика',
    Icon: Percent,
    getMeta: () => 'Посещаемость группы',
  },
]

export function GroupHub() {
  const { user } = useAuth()
  const { data: members, isLoading: membersLoading } = useGroupMembers()
  const { data: subjects, isLoading: subjectsLoading } = useGroupSubjects()

  const isLoading = membersLoading || subjectsLoading

  if (isLoading) {
    return (
      <div className="flex flex-col items-center justify-center min-h-screen gap-4 p-6">
        <LoadingSpinner />
        <p className="text-sm text-[var(--text-muted)]">Загружаем данные группы…</p>
      </div>
    )
  }

  const membersCount = members?.length
  const subjectsCount = subjects?.length
  const groupId = user?.groupId ?? 0

  return (
    <div className="p-6 pb-24">
      <h1
        className="text-lg font-semibold mb-8"
        style={{ fontFamily: 'var(--font-display)' }}
        role="heading"
        aria-level={1}
      >
        Группа
      </h1>

      <AnimatePresence>
        <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-3 gap-6">
          {HUB_CARDS.map((card, index) => (
            <motion.div
              key={card.route}
              initial={{ opacity: 0, scale: 0.95 }}
              animate={{ opacity: 1, scale: 1 }}
              transition={{
                duration: 0.2,
                ease: 'easeOut',
                delay: index * 0.04,
              }}
            >
              <Link
                to={card.route}
                className="flex flex-col justify-between min-h-[120px] p-4 rounded-lg border border-[var(--border-subtle)] bg-[var(--bg-secondary)] hover:shadow-md transition-shadow"
                style={{ borderRadius: '8px' }}
              >
                <div className="flex items-center justify-between gap-2">
                  <div className="flex items-center gap-2">
                    <card.Icon
                      size={24}
                      weight="fill"
                      style={{ color: 'var(--accent-primary)' }}
                    />
                    <span
                      className="text-base font-semibold"
                      style={{ fontFamily: 'var(--font-heading)' }}
                    >
                      {card.title}
                    </span>
                  </div>
                  <CaretRight
                    size={16}
                    style={{ color: 'var(--text-muted)', flexShrink: 0 }}
                  />
                </div>
                <p className="text-sm mt-2" style={{ color: 'var(--text-secondary)' }}>
                  {card.getMeta(membersCount, subjectsCount)}
                </p>
              </Link>
            </motion.div>
          ))}
        </div>
      </AnimatePresence>

      {groupId > 0 && (
        <StudentAttendanceStatsBlock
          groupId={groupId}
          members={members ?? []}
          subjects={subjects ?? []}
        />
      )}

      <HeadmanWeeklyReportCard />
    </div>
  )
}
