import { useCallback, useMemo, useState } from 'react'
import { useNavigate } from 'react-router'
import {
  ArrowsClockwise,
  CalendarBlank,
  CaretLeft,
  CaretRight,
  Info,
  WarningCircle,
} from '@phosphor-icons/react'
import { motion } from 'motion/react'
import { useAuth } from '@/features/auth/AuthProvider'
import { SkeletonList } from '@/shared/components/Skeleton'
import { addDays, formatDate, formatWeekRange, getMonday } from '@/shared/lib/dateUtils'
import { useDateNavigation } from '@/shared/hooks/useDateNavigation'
import { useSwipeHandler } from '@/shared/hooks/useSwipeHandler'
import { cn } from '@/lib/utils'
import { useStudentAttendanceRecords } from '@/features/student/requests/studentRequestsApi'
import type { AttendanceStatus, LessonResponse } from './types'
import { useSubjectMap, useWeekSchedule } from './api'
import { WeekDayTabs } from './WeekDayTabs'
import { LessonCard } from './LessonCard'
import { LessonActionsSheet } from './LessonActionsSheet'
import { HeadmanLessonSheet } from './HeadmanLessonSheet'

/**
 * Schedule page for the Telegram Mini App.
 *
 * Page title comes from `AppHeader` (derived from route). This page only
 * owns the date subtitle + lesson list + states (loading / error / empty /
 * non-student / ok). Tapping an ACTIVE lesson dispatches haptic feedback
 * inside LessonCard then navigates to the check-in flow.
 */
function getTodayDayIndex(): number {
  const dow = new Date().getDay()
  if (dow === 0) return 5
  return dow - 1
}

export function SchedulePage() {
  const { user } = useAuth()
  const navigate = useNavigate()
  const [currentWeekStart, setCurrentWeekStart] = useState(() => getMonday(new Date()))
  const [selectedDayIndex, setSelectedDayIndex] = useState(getTodayDayIndex)
  const [actionLesson, setActionLesson] = useState<LessonResponse | null>(null)
  const [headmanLesson, setHeadmanLesson] = useState<LessonResponse | null>(null)
  const [toast, setToast] = useState<{ type: 'success' | 'error'; message: string } | null>(null)

  const isStudent = user?.role === 'STUDENT' && user.groupId
  const isHeadman = user?.role === 'STUDENT' && user.isHeadman
  const weekNav = useDateNavigation({
    value: currentWeekStart,
    onChange: setCurrentWeekStart,
  })
  const weekDates = useMemo(
    () => Array.from({ length: 6 }, (_, index) => addDays(currentWeekStart, index)),
    [currentWeekStart],
  )
  const weekStart = formatDate(currentWeekStart)
  const weekEnd = formatDate(addDays(currentWeekStart, 5))
  const { data: lessons, isLoading, isError, refetch } = useWeekSchedule(
    isStudent ? user.groupId : undefined,
    weekStart,
    weekEnd,
  )

  const subjectIds = useMemo(() => (lessons ?? []).map((lesson) => lesson.subjectId), [lessons])
  const { subjectMap } = useSubjectMap(subjectIds)
  const { data: attendanceRecords } = useStudentAttendanceRecords(!!isStudent)
  const personalStatuses = useMemo<Record<number, AttendanceStatus>>(() => {
    const map: Record<number, AttendanceStatus> = {}
    for (const record of attendanceRecords ?? []) {
      if (
        record.status === 'present' ||
        record.status === 'absent' ||
        record.status === 'excused' ||
        record.status === 'free_attendance'
      ) {
        map[record.lessonId] = record.status
      }
    }
    return map
  }, [attendanceRecords])
  const selectedLessons = useMemo(() => {
    const backendDay = selectedDayIndex + 1
    return (lessons ?? [])
      .filter((lesson) => lesson.dayOfWeek === backendDay)
      .sort((a, b) => a.lessonNumber - b.lessonNumber)
  }, [lessons, selectedDayIndex])

  const handleSwipeWeek = useCallback(
    (direction: 'prev' | 'next') => {
      if (direction === 'next') weekNav.goNext()
      else weekNav.goPrev()
    },
    [weekNav],
  )

  const handleToday = useCallback(() => {
    weekNav.goToToday()
    setSelectedDayIndex(getTodayDayIndex())
  }, [weekNav])

  const handleDaySwipe = useSwipeHandler({
    horizontalThreshold: 50,
    onSwipeLeft: () => {
      if (selectedDayIndex < 5) setSelectedDayIndex((prev) => prev + 1)
    },
    onSwipeRight: () => {
      if (selectedDayIndex > 0) setSelectedDayIndex((prev) => prev - 1)
    },
  })

  if (!isStudent) {
    return <NonStudentState />
  }

  const isSelectedScheduleToday = weekNav.isAtToday && selectedDayIndex === getTodayDayIndex()

  return (
    <div className="flex min-h-full flex-col pb-[calc(72px+env(safe-area-inset-bottom))]">
      <WeekNavigator
        label={formatWeekRange(currentWeekStart)}
        onPrev={weekNav.goPrev}
        onNext={weekNav.goNext}
        canPrev={weekNav.canGoPrev}
        canNext={weekNav.canGoNext}
      />

      <WeekDayTabs
        selectedDay={selectedDayIndex}
        onSelectDay={setSelectedDayIndex}
        weekDates={weekDates}
        onSwipeWeek={handleSwipeWeek}
        canSwipePrev={weekNav.canGoPrev}
        canSwipeNext={weekNav.canGoNext}
      />

      <motion.div
        className="flex flex-col gap-3 px-3 pt-3"
        drag="x"
        dragDirectionLock
        dragConstraints={{ left: 0, right: 0 }}
        onDragEnd={handleDaySwipe}
      >
        {isLoading && <SkeletonList count={4} />}

        {isError && (
          <ErrorState onRetry={() => refetch()} />
        )}

        {!isLoading && !isError && selectedLessons.length === 0 && <EmptyDayState />}

        {!isLoading &&
          !isError &&
          selectedLessons.map((lesson) => (
            <LessonCard
              key={lesson.id}
              lesson={lesson}
              subjectName={subjectMap[lesson.subjectId] ?? '…'}
              personalStatus={personalStatuses[lesson.id] ?? null}
              onCheckin={(id) => navigate(`/checkin/${id}`)}
              onOpenActions={() => {
                if (isHeadman) setHeadmanLesson(lesson)
                else setActionLesson(lesson)
              }}
            />
          ))}
      </motion.div>

      <LessonActionsSheet
        open={!!actionLesson}
        lesson={actionLesson}
        subjectName={actionLesson ? subjectMap[actionLesson.subjectId] ?? 'Предмет' : 'Предмет'}
        personalStatus={actionLesson ? personalStatuses[actionLesson.id] ?? null : null}
        onClose={() => setActionLesson(null)}
      />

      <HeadmanLessonSheet
        open={!!headmanLesson}
        lesson={headmanLesson}
        subjectName={headmanLesson ? subjectMap[headmanLesson.subjectId] ?? 'Предмет' : 'Предмет'}
        onClose={() => setHeadmanLesson(null)}
        onToast={(type, message) => {
          setToast({ type, message })
          window.setTimeout(() => setToast(null), 2400)
        }}
      />

      {toast && <ScheduleToast type={toast.type} message={toast.message} />}

      {!isSelectedScheduleToday && (
        <button
          type="button"
          onClick={handleToday}
          className={cn(
            'fixed left-1/2 z-[calc(var(--z-sticky)+1)] -translate-x-1/2',
            'flex items-center gap-2 rounded-full px-5 py-2.5',
            'text-sm font-semibold shadow-lg',
          )}
          style={{
            bottom: 'calc(72px + 16px + env(safe-area-inset-bottom))',
            background: 'var(--gradient-brand)',
            color: 'var(--accent-primary-contrast)',
            boxShadow: 'var(--glow-primary)',
          }}
        >
          <CalendarBlank size={16} weight="fill" aria-hidden="true" />
          Сегодня
        </button>
      )}
    </div>
  )
}

function WeekNavigator({
  label,
  onPrev,
  onNext,
  canPrev,
  canNext,
}: {
  label: string
  onPrev: () => void
  onNext: () => void
  canPrev: boolean
  canNext: boolean
}) {
  return (
    <div
      className="flex items-center justify-between gap-2 px-3 py-2"
      style={{ borderBottom: '1px solid var(--border-subtle)' }}
    >
      <button
        type="button"
        onClick={onPrev}
        disabled={!canPrev}
        className="grid size-11 place-items-center rounded-full transition-colors disabled:cursor-not-allowed disabled:opacity-30"
        style={{ color: 'var(--text-secondary)' }}
        aria-label="Предыдущая неделя"
      >
        <CaretLeft size={20} weight="bold" />
      </button>

      <div className="flex flex-col items-center gap-0.5">
        <span
          className="text-[10px] font-medium uppercase tracking-wide"
          style={{ color: 'var(--text-muted)' }}
        >
          Неделя
        </span>
        <span
          className="text-sm font-semibold tracking-tight tabular-nums"
          style={{ color: 'var(--text-primary)' }}
        >
          {label}
        </span>
      </div>

      <button
        type="button"
        onClick={onNext}
        disabled={!canNext}
        className="grid size-11 place-items-center rounded-full transition-colors disabled:cursor-not-allowed disabled:opacity-30"
        style={{ color: 'var(--text-secondary)' }}
        aria-label="Следующая неделя"
      >
        <CaretRight size={20} weight="bold" />
      </button>
    </div>
  )
}

function NonStudentState() {
  return (
    <div className="flex min-h-[60vh] flex-col items-center justify-center gap-3 px-4 text-center">
      <div
        className="grid size-14 place-items-center rounded-full"
        style={{
          background: 'color-mix(in oklab, var(--accent-info) 14%, transparent)',
          border: '1px solid color-mix(in oklab, var(--accent-info) 30%, transparent)',
          color: 'var(--accent-info)',
        }}
      >
        <Info size={24} weight="duotone" />
      </div>
      <p
        className="text-base font-semibold text-balance"
        style={{
          color: 'var(--text-primary)',
          fontFamily: 'var(--font-heading)',
        }}
      >
        Расписание студентов
      </p>
      <p
        className="max-w-[26ch] text-sm text-pretty"
        style={{ color: 'var(--text-secondary)' }}
      >
        Доступно только для студенческих учётных записей.
      </p>
    </div>
  )
}

function EmptyDayState() {
  return (
    <div
      className="flex flex-col items-center gap-3 rounded-2xl border border-dashed p-6 text-center"
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
        <CalendarBlank size={22} weight="duotone" />
      </div>
      <p
        className="text-sm font-semibold text-balance"
        style={{
          color: 'var(--text-primary)',
          fontFamily: 'var(--font-heading)',
        }}
      >
        Сегодня пар нет
      </p>
      <p
        className="max-w-[24ch] text-xs text-pretty"
        style={{ color: 'var(--text-secondary)' }}
      >
        Наслаждайтесь свободным днём.
      </p>
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
        style={{
          color: 'var(--text-primary)',
          fontFamily: 'var(--font-heading)',
        }}
      >
        Не удалось загрузить расписание
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

function ScheduleToast({
  type,
  message,
}: {
  type: 'success' | 'error'
  message: string
}) {
  return (
    <div
      role="status"
      aria-live="polite"
      className="fixed left-1/2 z-[var(--z-toast,70)] -translate-x-1/2 rounded-full border px-4 py-2 text-sm font-medium"
      style={{
        bottom: 'calc(80px + env(safe-area-inset-bottom))',
        background: 'var(--bg-elevated)',
        borderColor: type === 'success' ? 'var(--accent-primary)' : 'var(--accent-danger)',
        color: type === 'success' ? 'var(--accent-primary)' : 'var(--accent-danger)',
      }}
    >
      {message}
    </div>
  )
}
