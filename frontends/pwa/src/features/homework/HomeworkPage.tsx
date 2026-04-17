import { useMemo, useState } from 'react'
import { AnimatePresence, motion } from 'motion/react'
import { BookOpen, Funnel } from '@phosphor-icons/react'
import { useAuth } from '@/features/auth/AuthProvider'
import { SkeletonList } from '@/shared/components/Skeleton'
import { addDays, formatDate, getMonday } from '@/shared/lib/dateUtils'
import {
  useActiveSemesterId,
  useHomeworksForGroup,
  useMarkHomeworkComplete,
  useUnmarkHomeworkComplete,
} from './api'
import { DayView } from './DayView'
import { WeekView } from './WeekView'
import { MonthView } from './MonthView'
import { ModeSwitcher, type HomeworkMode } from './ModeSwitcher'
import type { HomeworkResponse } from './types'
import { cn } from '@/lib/utils'

/**
 * Student-facing homework page. Container with three modes (day/week/month),
 * mirroring the web-panel student view (D-09: default = tomorrow, day mode).
 *
 * Headmen land on the same page but manage CRUD inline under lesson cards
 * on the /schedule route — this page is read-only for both roles.
 */
export default function HomeworkPage() {
  const { user } = useAuth()
  const groupId = user?.groupId

  const { data: semesterId, isLoading: semesterLoading } = useActiveSemesterId()
  const {
    data: homeworks,
    isLoading: listLoading,
    isError,
  } = useHomeworksForGroup(groupId, semesterId)

  const [mode, setMode] = useState<HomeworkMode>('day')
  const [selectedDate, setSelectedDate] = useState<Date>(() =>
    addDays(new Date(), 1),
  )
  const [weekMonday, setWeekMonday] = useState<Date>(() => getMonday(new Date()))
  const [currentMonth, setCurrentMonth] = useState<Date>(() => {
    const d = new Date()
    d.setDate(1)
    d.setHours(0, 0, 0, 0)
    return d
  })
  const [filterUncompleted, setFilterUncompleted] = useState(false)

  const markMutation = useMarkHomeworkComplete()
  const unmarkMutation = useUnmarkHomeworkComplete()

  const filteredHomeworks = useMemo(() => {
    const all = homeworks ?? []
    return filterUncompleted ? all.filter((hw) => !hw.completed) : all
  }, [homeworks, filterUncompleted])

  const dayHomeworks = useMemo(() => {
    const iso = formatDate(selectedDate)
    return filteredHomeworks
      .filter((hw) => hw.lessonDate === iso)
      .sort((a, b) => a.lessonNumber - b.lessonNumber)
  }, [filteredHomeworks, selectedDate])

  const [toggling, setToggling] = useState<Set<number>>(new Set())

  const onToggleComplete = (hw: HomeworkResponse, next: boolean) => {
    setToggling((prev) => {
      const s = new Set(prev)
      s.add(hw.id)
      return s
    })
    const req = next
      ? markMutation.mutateAsync(hw.id)
      : unmarkMutation.mutateAsync(hw.id)
    void req.finally(() => {
      setToggling((prev) => {
        const s = new Set(prev)
        s.delete(hw.id)
        return s
      })
    })
  }

  const isLoading = semesterLoading || listLoading

  return (
    <div className="flex min-h-full flex-col gap-3 px-4 pb-4 pt-3">
      <div className="flex items-center gap-2">
        <ModeSwitcher value={mode} onChange={setMode} />
      </div>

      <button
        type="button"
        onClick={() => setFilterUncompleted((v) => !v)}
        className={cn(
          'inline-flex w-fit items-center gap-2 rounded-full border px-3 py-1.5',
          'text-xs font-medium transition-colors',
        )}
        style={{
          background: filterUncompleted
            ? 'color-mix(in oklab, var(--accent-primary) 14%, transparent)'
            : 'var(--bg-secondary)',
          borderColor: filterUncompleted
            ? 'var(--accent-primary)'
            : 'var(--border-subtle)',
          color: filterUncompleted
            ? 'var(--accent-primary)'
            : 'var(--text-secondary)',
        }}
        aria-pressed={filterUncompleted}
      >
        <Funnel size={14} weight={filterUncompleted ? 'fill' : 'regular'} />
        Только невыполненные
      </button>

      {!groupId ? (
        <EmptyState
          title="Нет группы"
          description="Ваш аккаунт не привязан к группе. Обратитесь к администратору."
        />
      ) : isLoading ? (
        <SkeletonList count={4} />
      ) : isError ? (
        <EmptyState
          title="Не удалось загрузить"
          description="Проверьте подключение и попробуйте снова."
        />
      ) : !semesterId ? (
        <EmptyState
          title="Нет активного семестра"
          description="Дождитесь, пока администратор активирует текущий семестр."
        />
      ) : (
        <AnimatePresence mode="wait">
          <motion.div
            key={mode}
            initial={{ opacity: 0, y: 8 }}
            animate={{ opacity: 1, y: 0 }}
            exit={{ opacity: 0 }}
            transition={{ duration: 0.15, ease: 'easeOut' }}
          >
            {mode === 'day' && (
              <DayView
                date={selectedDate}
                onDateChange={setSelectedDate}
                homeworks={dayHomeworks}
                onToggleComplete={onToggleComplete}
                toggling={toggling}
              />
            )}
            {mode === 'week' && (
              <WeekView
                monday={weekMonday}
                onMondayChange={setWeekMonday}
                homeworks={filteredHomeworks}
                onToggleComplete={onToggleComplete}
                toggling={toggling}
              />
            )}
            {mode === 'month' && (
              <MonthView
                month={currentMonth}
                onMonthChange={setCurrentMonth}
                homeworks={filteredHomeworks}
                onPickDate={(d) => {
                  setSelectedDate(d)
                  setMode('day')
                }}
              />
            )}
          </motion.div>
        </AnimatePresence>
      )}
    </div>
  )
}

function EmptyState({
  title,
  description,
}: {
  title: string
  description: string
}) {
  return (
    <div
      className="mt-6 flex flex-col items-center gap-3 rounded-2xl border border-dashed p-8 text-center"
      style={{
        borderColor: 'var(--border-default)',
        background: 'color-mix(in oklab, var(--bg-secondary) 50%, transparent)',
      }}
    >
      <div
        className="grid size-14 place-items-center rounded-full"
        style={{
          background: 'color-mix(in oklab, var(--accent-primary) 12%, transparent)',
          border: '1px solid var(--border-accent)',
          color: 'var(--accent-primary)',
        }}
      >
        <BookOpen size={24} weight="duotone" />
      </div>
      <h2
        className="text-lg font-semibold text-balance"
        style={{
          color: 'var(--text-primary)',
          fontFamily: 'var(--font-heading)',
        }}
      >
        {title}
      </h2>
      <p
        className="max-w-xs text-sm text-pretty"
        style={{ color: 'var(--text-secondary)' }}
      >
        {description}
      </p>
    </div>
  )
}
