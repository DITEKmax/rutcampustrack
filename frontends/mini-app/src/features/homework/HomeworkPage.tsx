import { useCallback, useMemo, useState } from 'react'
import { AnimatePresence, motion } from 'motion/react'
import { ArrowsClockwise, Books, Funnel, WarningCircle } from '@phosphor-icons/react'
import { useAuth } from '@/features/auth/AuthProvider'
import { useSubjectMap } from '@/features/schedule/api'
import { SkeletonList } from '@/shared/components/Skeleton'
import { addDays, formatDate, getMonday } from '@/shared/lib/dateUtils'
import { cn } from '@/lib/utils'
import { useActiveSemester, useHomeworkList, useToggleHomework } from './api'
import { DayView } from './DayView'
import { WeekView } from './WeekView'
import { MonthView } from './MonthView'
import { ModeSwitcher, type HomeworkMode } from './ModeSwitcher'
import type { HomeworkResponse } from './types'

function firstDayOfCurrentMonth(): Date {
  const date = new Date()
  date.setDate(1)
  date.setHours(0, 0, 0, 0)
  return date
}

export function HomeworkPage() {
  const { user } = useAuth()
  const { data: semester, isLoading: semesterLoading } = useActiveSemester()
  const {
    data: homeworks,
    isLoading: homeworkLoading,
    isError,
    refetch,
  } = useHomeworkList(user?.groupId, semester?.id)
  const toggleMutation = useToggleHomework(user?.groupId, semester?.id)

  const [mode, setMode] = useState<HomeworkMode>('day')
  const [selectedDate, setSelectedDate] = useState<Date>(() => addDays(new Date(), 1))
  const [weekMonday, setWeekMonday] = useState<Date>(() => getMonday(new Date()))
  const [currentMonth, setCurrentMonth] = useState<Date>(firstDayOfCurrentMonth)
  const [filterUncompleted, setFilterUncompleted] = useState(false)
  const [toggling, setToggling] = useState<Set<number>>(new Set())

  const subjectIds = useMemo(
    () => (homeworks ?? []).map((homework) => homework.subjectId),
    [homeworks],
  )
  const { subjectMap } = useSubjectMap(subjectIds)

  const filteredHomeworks = useMemo(() => {
    const all = homeworks ?? []
    return filterUncompleted ? all.filter((homework) => !homework.completed) : all
  }, [filterUncompleted, homeworks])

  const dayHomeworks = useMemo(() => {
    const iso = formatDate(selectedDate)
    return filteredHomeworks
      .filter((homework) => homework.lessonDate === iso)
      .sort((a, b) => a.lessonNumber - b.lessonNumber)
  }, [filteredHomeworks, selectedDate])

  const onToggleComplete = useCallback(
    (homework: HomeworkResponse) => {
      if (toggling.has(homework.id)) return
      setToggling((prev) => new Set(prev).add(homework.id))
      void toggleMutation
        .mutateAsync({ id: homework.id, completed: homework.completed })
        .finally(() => {
          setToggling((prev) => {
            const next = new Set(prev)
            next.delete(homework.id)
            return next
          })
        })
    },
    [toggleMutation, toggling],
  )

  const loading = semesterLoading || homeworkLoading
  const hasGroup = !!user?.groupId

  return (
    <div className="flex min-h-full flex-col gap-3 px-3 pt-3 pb-[calc(72px+env(safe-area-inset-bottom))]">
      <ModeSwitcher value={mode} onChange={setMode} />

      <button
        type="button"
        onClick={() => setFilterUncompleted((value) => !value)}
        className={cn(
          'inline-flex w-fit items-center gap-2 rounded-full border px-3 py-1.5',
          'text-xs font-semibold transition-colors',
        )}
        style={{
          background: filterUncompleted
            ? 'color-mix(in oklab, var(--accent-primary) 14%, transparent)'
            : 'var(--bg-secondary)',
          borderColor: filterUncompleted ? 'var(--accent-primary)' : 'var(--border-subtle)',
          color: filterUncompleted ? 'var(--accent-primary)' : 'var(--text-secondary)',
        }}
        aria-pressed={filterUncompleted}
      >
        <Funnel size={14} weight={filterUncompleted ? 'fill' : 'regular'} aria-hidden="true" />
        Только невыполненные
      </button>

      {!hasGroup && (
        <MessageCard
          tone="info"
          title="Нет группы"
          text="Ваш Telegram-аккаунт не привязан к учебной группе."
        />
      )}

      {hasGroup && loading && <SkeletonList count={5} />}

      {hasGroup && !loading && !semester && (
        <MessageCard
          tone="warning"
          title="Активный семестр не найден"
          text="Дождитесь активации семестра или обратитесь к старосте."
        />
      )}

      {hasGroup && !loading && isError && <ErrorState onRetry={() => refetch()} />}

      {hasGroup && !loading && !isError && semester && filteredHomeworks.length === 0 && (
        <MessageCard
          tone="info"
          title={filterUncompleted ? 'Невыполненных заданий нет' : 'Нет домашних заданий'}
          text={filterUncompleted ? 'Все задания отмечены как выполненные.' : 'Пока ничего не задано.'}
        />
      )}

      {hasGroup && !loading && !isError && semester && filteredHomeworks.length > 0 && (
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
                subjectMap={subjectMap}
                toggling={toggling}
                onToggleComplete={onToggleComplete}
              />
            )}

            {mode === 'week' && (
              <WeekView
                monday={weekMonday}
                onMondayChange={setWeekMonday}
                homeworks={filteredHomeworks}
                subjectMap={subjectMap}
                toggling={toggling}
                onToggleComplete={onToggleComplete}
              />
            )}

            {mode === 'month' && (
              <MonthView
                month={currentMonth}
                onMonthChange={setCurrentMonth}
                homeworks={filteredHomeworks}
                onPickDate={(date) => {
                  setSelectedDate(date)
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

function ErrorState({ onRetry }: { onRetry: () => void }) {
  return (
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
        style={{ color: 'var(--text-primary)', fontFamily: 'var(--font-heading)' }}
      >
        Не удалось загрузить задания
      </p>
      <button
        type="button"
        onClick={onRetry}
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
  )
}

function MessageCard({
  tone,
  title,
  text,
}: {
  tone: 'warning' | 'info'
  title: string
  text: string
}) {
  const fg = tone === 'warning' ? 'var(--accent-warning)' : 'var(--accent-info)'
  return (
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
          background: `color-mix(in oklab, ${fg} 14%, transparent)`,
          border: `1px solid color-mix(in oklab, ${fg} 30%, transparent)`,
          color: fg,
        }}
      >
        <Books size={22} weight="duotone" />
      </div>
      <p
        className="text-sm font-semibold text-balance"
        style={{ color: 'var(--text-primary)', fontFamily: 'var(--font-heading)' }}
      >
        {title}
      </p>
      <p className="max-w-[28ch] text-xs text-pretty" style={{ color: 'var(--text-secondary)' }}>
        {text}
      </p>
    </div>
  )
}
