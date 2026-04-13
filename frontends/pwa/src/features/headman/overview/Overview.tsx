import { Link } from 'react-router'
import { ArrowLeft, Users, Calendar, FileText, Clock } from '@phosphor-icons/react'
import { motion } from 'framer-motion'
import { useAuth } from '@/features/auth/AuthProvider'
import {
  useGroupMembers,
  useTodayLesson,
  usePendingExcuses,
  usePendingLateCheckins,
} from '@/features/headman/shared/headmanApi'

export function Overview() {
  const { user } = useAuth()
  const groupId = user?.groupId

  const membersQuery = useGroupMembers()
  const todayLessonQuery = useTodayLesson(groupId ?? 0)
  const pendingExcusesQuery = usePendingExcuses(groupId ?? 0)
  const pendingLateCheckinsQuery = usePendingLateCheckins(groupId ?? 0)

  const memberCount = membersQuery.data?.length ?? 0
  const todayLesson = todayLessonQuery.data
  const excuseCount = pendingExcusesQuery.data?.length ?? 0
  const lateCheckinCount = pendingLateCheckinsQuery.data?.length ?? 0

  if (!groupId) {
    return (
      <div className="p-6">
        <Link to="/group" aria-label="Назад" className="inline-flex items-center gap-2 mb-4">
          <ArrowLeft size={20} />
          <span>Назад</span>
        </Link>
        <h1 className="text-base font-semibold mb-4">Обзор</h1>
        <p style={{ color: 'var(--text-muted)' }} className="text-sm">
          Группа не назначена
        </p>
      </div>
    )
  }

  return (
    <div className="p-6">
      <Link to="/group" aria-label="Назад" className="inline-flex items-center gap-2 mb-4">
        <ArrowLeft size={20} />
        <span>Назад</span>
      </Link>
      <h1 className="text-base font-semibold mb-6">Обзор</h1>

      {/* Stat cards grid */}
      <div className="grid grid-cols-1 sm:grid-cols-2 gap-4 mb-6">
        {/* Member count card */}
        <motion.div
          initial={{ opacity: 0, y: 8 }}
          animate={{ opacity: 1, y: 0 }}
          transition={{ duration: 0.15 }}
          className="rounded-lg p-4"
          style={{ background: 'var(--bg-secondary)', border: '1px solid var(--border-subtle)' }}
        >
          <div className="flex items-start justify-between">
            <div>
              <p className="text-sm font-medium mb-1" style={{ color: 'var(--text-muted)' }}>
                Члены группы
              </p>
              <p
                className="text-lg font-semibold"
                style={{ color: 'var(--accent-primary)' }}
              >
                {membersQuery.isLoading ? '—' : memberCount}
              </p>
            </div>
            <Users size={32} weight="duotone" style={{ color: 'var(--accent-primary)' }} />
          </div>
        </motion.div>

        {/* Today's lesson card */}
        <motion.div
          initial={{ opacity: 0, y: 8 }}
          animate={{ opacity: 1, y: 0 }}
          transition={{ duration: 0.15, delay: 0.05 }}
          className="rounded-lg p-4"
          style={{ background: 'var(--bg-secondary)', border: '1px solid var(--border-subtle)' }}
        >
          <div className="flex items-start justify-between">
            <div className="flex-1 min-w-0">
              <p className="text-sm font-medium mb-1" style={{ color: 'var(--text-muted)' }}>
                Текущая пара
              </p>
              {todayLessonQuery.isLoading ? (
                <p className="text-sm" style={{ color: 'var(--text-muted)' }}>
                  Загрузка…
                </p>
              ) : todayLesson ? (
                <>
                  <p className="text-base font-semibold truncate">{todayLesson.subjectName}</p>
                  <p
                    className="text-sm mt-0.5"
                    style={{
                      color: 'var(--text-muted)',
                      fontFamily: '"JetBrains Mono", monospace',
                      fontVariantNumeric: 'tabular-nums',
                    }}
                  >
                    {todayLesson.startsAt.slice(11, 16)}–{todayLesson.endsAt.slice(11, 16)}
                  </p>
                </>
              ) : (
                <p className="text-sm" style={{ color: 'var(--text-muted)' }}>
                  На сегодня пар больше нет
                </p>
              )}
            </div>
            <Calendar size={32} weight="duotone" style={{ color: 'var(--accent-primary)' }} />
          </div>
        </motion.div>
      </div>

      {/* Pending tickets section */}
      <h2 className="text-sm font-semibold mb-3" style={{ color: 'var(--text-muted)' }}>
        Ожидают действия
      </h2>
      <div className="flex flex-col gap-3">
        {/* Excuse tickets card */}
        <motion.div
          initial={{ opacity: 0, y: 8 }}
          animate={{ opacity: 1, y: 0 }}
          transition={{ duration: 0.15, delay: 0.1 }}
        >
          <Link
            to="/group/excuses"
            className="flex items-center gap-3 rounded-lg p-4"
            style={{ background: 'var(--bg-secondary)', border: '1px solid var(--border-subtle)' }}
          >
            <FileText size={20} style={{ color: 'var(--accent-primary)', flexShrink: 0 }} />
            <span className="flex-1 text-sm font-medium">Пропусков ждут одобрения</span>
            {excuseCount > 0 ? (
              <span
                className="text-xs font-bold px-2 py-0.5 rounded-full text-white"
                style={{ background: 'var(--accent-danger, #ef4444)' }}
              >
                {excuseCount}
              </span>
            ) : (
              <span className="text-xs" style={{ color: 'var(--text-muted)' }}>
                Нет ожидающих запросов
              </span>
            )}
          </Link>
        </motion.div>

        {/* Late check-in card */}
        <motion.div
          initial={{ opacity: 0, y: 8 }}
          animate={{ opacity: 1, y: 0 }}
          transition={{ duration: 0.15, delay: 0.15 }}
        >
          <Link
            to="/group/late-checkin"
            className="flex items-center gap-3 rounded-lg p-4"
            style={{ background: 'var(--bg-secondary)', border: '1px solid var(--border-subtle)' }}
          >
            <Clock size={20} style={{ color: 'var(--accent-primary)', flexShrink: 0 }} />
            <span className="flex-1 text-sm font-medium">Запросы опоздалой отметки</span>
            {lateCheckinCount > 0 ? (
              <span
                className="text-xs font-bold px-2 py-0.5 rounded-full text-white"
                style={{ background: 'var(--accent-danger, #ef4444)' }}
              >
                {lateCheckinCount}
              </span>
            ) : (
              <span className="text-xs" style={{ color: 'var(--text-muted)' }}>
                Нет ожидающих запросов
              </span>
            )}
          </Link>
        </motion.div>
      </div>
    </div>
  )
}
