import { useState, useEffect, useRef, useMemo, useCallback } from 'react'
import { AnimatePresence, motion } from 'motion/react'
import { CaretLeft, CaretRight } from '@phosphor-icons/react'
import { useAuth } from '@/features/auth/AuthProvider'
import { LoadingSpinner } from '@/shared/components/LoadingSpinner'
import { useNetworkStatus } from '@/shared/hooks/useNetworkStatus'
import { useStompEvents } from '@/features/checkin/StompProvider'
import { CheckInToast } from '@/features/checkin/CheckInToast'
import { useWeekSchedule, usePrefetchSubjects } from './api'
import { WeekDayTabs } from './WeekDayTabs'
import { LessonCard } from './LessonCard'
import { OfflineStaleNotice } from './OfflineStaleNotice'

const MONTH_ABBREV = ['янв', 'фев', 'мар', 'апр', 'мая', 'июн', 'июл', 'авг', 'сен', 'окт', 'ноя', 'дек']

function getMonday(date: Date): Date {
  const d = new Date(date)
  const day = d.getDay()
  // getDay(): 0=Sun, 1=Mon...6=Sat
  const diff = day === 0 ? -6 : 1 - day
  d.setDate(d.getDate() + diff)
  d.setHours(0, 0, 0, 0)
  return d
}

function addDays(date: Date, days: number): Date {
  const d = new Date(date)
  d.setDate(d.getDate() + days)
  return d
}

function formatDate(date: Date): string {
  const y = date.getFullYear()
  const m = String(date.getMonth() + 1).padStart(2, '0')
  const d = String(date.getDate()).padStart(2, '0')
  return `${y}-${m}-${d}`
}

function getTodayDayIndex(): number {
  const dow = new Date().getDay()
  // 0=Sun -> 5 (Saturday), 1=Mon -> 0, ..., 6=Sat -> 5
  if (dow === 0) return 5
  return dow - 1
}

function formatWeekRange(monday: Date): string {
  const saturday = addDays(monday, 5)
  const startDay = monday.getDate()
  const endDay = saturday.getDate()

  if (monday.getMonth() === saturday.getMonth()) {
    return `${startDay}-${endDay} ${MONTH_ABBREV[saturday.getMonth()]}`
  }
  return `${startDay} ${MONTH_ABBREV[monday.getMonth()]} - ${endDay} ${MONTH_ABBREV[saturday.getMonth()]}`
}

function isSameWeek(a: Date, b: Date): boolean {
  const mondayA = getMonday(a)
  const mondayB = getMonday(b)
  return mondayA.getTime() === mondayB.getTime()
}

export function SchedulePage() {
  const { user } = useAuth()
  const groupId = user?.groupId ?? 0
  const { isOnline } = useNetworkStatus()
  const { attendanceCounts, personalStatuses, markPersonalStatus } = useStompEvents()

  const [currentWeekStart, setCurrentWeekStart] = useState(() => getMonday(new Date()))
  const [selectedDayIndex, setSelectedDayIndex] = useState(getTodayDayIndex)
  const [toast, setToast] = useState<{ type: 'success' | 'error'; message: string } | null>(null)
  const hasAutoScrolled = useRef(false)

  const weekStart = formatDate(currentWeekStart)
  const weekEnd = formatDate(addDays(currentWeekStart, 5))

  const weekDates = useMemo(
    () => Array.from({ length: 6 }, (_, i) => addDays(currentWeekStart, i)),
    [currentWeekStart]
  )

  const { data: lessons, isLoading, dataUpdatedAt } = useWeekSchedule(groupId, weekStart, weekEnd)

  // Prefetch subject names to avoid waterfall
  const subjectIds = useMemo(
    () => (lessons ?? []).map((l) => l.subjectId),
    [lessons]
  )
  usePrefetchSubjects(subjectIds)

  // Filter lessons for selected day
  const dayLessons = useMemo(() => {
    if (!lessons) return []
    const backendDow = selectedDayIndex + 1 // 1=Mon..6=Sat
    return lessons
      .filter((l) => l.dayOfWeek === backendDow)
      .sort((a, b) => a.lessonNumber - b.lessonNumber)
  }, [lessons, selectedDayIndex])

  // Auto-scroll to current/next lesson on initial mount (D-03)
  useEffect(() => {
    if (hasAutoScrolled.current || !dayLessons.length) return

    const active = dayLessons.find((l) => l.status === 'ACTIVE')
    const target = active ?? dayLessons.find((l) => l.status === 'PLANNED')
    if (target) {
      // Wait a tick for DOM to render
      requestAnimationFrame(() => {
        document
          .querySelector(`[data-lesson-id="${target.id}"]`)
          ?.scrollIntoView({ behavior: 'smooth', block: 'center' })
      })
      hasAutoScrolled.current = true
    }
  }, [dayLessons])

  const isCurrentWeek = isSameWeek(currentWeekStart, new Date())

  const handleSwipeWeek = (direction: 'prev' | 'next') => {
    setCurrentWeekStart((prev) => addDays(prev, direction === 'next' ? 7 : -7))
  }

  const handleTodayPill = () => {
    setCurrentWeekStart(getMonday(new Date()))
    setSelectedDayIndex(getTodayDayIndex())
  }

  const handleDaySwipe = (_e: never, info: { offset: { x: number } }) => {
    if (info.offset.x < -50 && selectedDayIndex < 5) {
      setSelectedDayIndex((prev) => prev + 1)
    } else if (info.offset.x > 50 && selectedDayIndex > 0) {
      setSelectedDayIndex((prev) => prev - 1)
    }
  }

  const handleCheckinSuccess = useCallback(
    (lessonId: number) => {
      setToast({ type: 'success', message: 'Отметка принята' })
      markPersonalStatus(lessonId, 'present')
    },
    [markPersonalStatus]
  )

  const handleCheckinError = useCallback((msg: string) => {
    setToast({ type: 'error', message: msg })
  }, [])

  return (
    <div className="flex flex-col min-h-full">
      {/* Week navigation header */}
      <div className="flex items-center justify-between px-4 py-2">
        <button
          onClick={() => handleSwipeWeek('prev')}
          className="min-h-[44px] min-w-[44px] flex items-center justify-center rounded-lg text-muted-foreground"
          aria-label="Предыдущая неделя"
        >
          <CaretLeft size={24} />
        </button>
        <span className="text-sm font-medium">{formatWeekRange(currentWeekStart)}</span>
        <button
          onClick={() => handleSwipeWeek('next')}
          className="min-h-[44px] min-w-[44px] flex items-center justify-center rounded-lg text-muted-foreground"
          aria-label="Следующая неделя"
        >
          <CaretRight size={24} />
        </button>
      </div>

      {/* Day tabs */}
      <WeekDayTabs
        selectedDay={selectedDayIndex}
        onSelectDay={setSelectedDayIndex}
        weekDates={weekDates}
        onSwipeWeek={handleSwipeWeek}
        hasOfflineBanner={!isOnline}
      />

      {/* Offline stale notice (D-15) */}
      <OfflineStaleNotice dataUpdatedAt={dataUpdatedAt} />

      {/* Lesson list */}
      <AnimatePresence mode="wait">
        <motion.div
          key={`${weekStart}-${selectedDayIndex}`}
          initial={{ opacity: 0, x: 8 }}
          animate={{ opacity: 1, x: 0 }}
          exit={{ opacity: 0 }}
          transition={{ duration: 0.12 }}
          drag="x"
          dragDirectionLock
          dragConstraints={{ left: 0, right: 0 }}
          onDragEnd={handleDaySwipe as never}
          className="flex flex-col gap-3 px-4 pt-2 pb-20"
        >
          {isLoading ? (
            <LoadingSpinner />
          ) : dayLessons.length === 0 ? (
            <div className="flex flex-col items-center justify-center py-16">
              <h2 className="text-xl font-semibold text-center">Занятий нет</h2>
              <p className="text-sm text-muted-foreground text-center mt-1">
                В этот день пар не запланировано
              </p>
            </div>
          ) : (
            <motion.div
              className="flex flex-col gap-3"
              initial="hidden"
              animate="visible"
              variants={{
                visible: { transition: { staggerChildren: 0.04 } },
                hidden: {},
              }}
            >
              {dayLessons.map((lesson) => (
                <motion.div
                  key={lesson.id}
                  variants={{
                    hidden: { opacity: 0 },
                    visible: { opacity: 1 },
                  }}
                >
                  <LessonCard
                    lesson={lesson}
                    attendanceCount={attendanceCounts[lesson.id]}
                    personalStatus={personalStatuses[lesson.id] ?? null}
                    onCheckin={() => handleCheckinSuccess(lesson.id)}
                    onCheckinError={handleCheckinError}
                  />
                </motion.div>
              ))}
            </motion.div>
          )}
        </motion.div>
      </AnimatePresence>

      {/* Check-in toast */}
      <AnimatePresence>
        {toast && (
          <CheckInToast
            type={toast.type}
            message={toast.message}
            onDismiss={() => setToast(null)}
          />
        )}
      </AnimatePresence>

      {/* Floating "Сегодня" pill (D-04) */}
      <AnimatePresence>
        {!isCurrentWeek && (
          <motion.button
            initial={{ y: 16, opacity: 0 }}
            animate={{ y: 0, opacity: 1 }}
            exit={{ y: 16, opacity: 0 }}
            transition={{ duration: 0.2 }}
            onClick={handleTodayPill}
            className="fixed bottom-16 left-1/2 -translate-x-1/2 z-30 bg-primary text-primary-foreground rounded-full px-4 py-2 text-sm font-medium shadow-lg"
          >
            Сегодня
          </motion.button>
        )}
      </AnimatePresence>
    </div>
  )
}
