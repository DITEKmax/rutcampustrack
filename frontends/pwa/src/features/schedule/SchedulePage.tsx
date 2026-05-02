import { useState, useEffect, useRef, useMemo, useCallback } from 'react'
import { AnimatePresence, motion } from 'motion/react'
import { CaretLeft, CaretRight, CalendarBlank } from '@phosphor-icons/react'
import { useAuth } from '@/features/auth/AuthProvider'
import { SkeletonList } from '@/shared/components/Skeleton'
import { useNetworkStatus } from '@/shared/hooks/useNetworkStatus'
import { useSwipeHandler } from '@/shared/hooks/useSwipeHandler'
import { useDateNavigation } from '@/shared/hooks/useDateNavigation'
import {
  addDays,
  formatDate,
  formatWeekRange,
  getMonday,
  parseLocalDate,
} from '@/shared/lib/dateUtils'
import { useStompEvents } from '@/features/checkin/StompProvider'
import { CheckInToast } from '@/features/checkin/CheckInToast'
import { cn } from '@/lib/utils'
import { useQueryClient } from '@tanstack/react-query'
import { PullToRefresh } from '@/shared/components/PullToRefresh'
import {
  useWeekSchedule,
  useSubjectMap,
  useSubjectName,
  useStudentRecords,
} from './api'
import {
  useActiveSemester,
  useActiveSemesterId,
  useAllSemesters,
  useHomeworksForGroup,
} from '@/features/homework/api'
import { LessonHomeworkSection } from '@/features/homework/LessonHomeworkSection'
import { WeekDayTabs } from './WeekDayTabs'
import { LessonCard } from './LessonCard'
import { OfflineStaleNotice } from './OfflineStaleNotice'
import { LessonActionsSheet } from './LessonActionsSheet'
import { HeadmanLessonSheet } from './HeadmanLessonSheet'
import { ScheduleBoundsNotice } from './ScheduleBoundsNotice'
import {
  useBlockLesson,
  useUnblockLesson,
  mapLessonActionError,
} from './lessonActionsApi'
import type { AttendanceStatus, LessonResponse } from './types'

function getTodayDayIndex(): number {
  const dow = new Date().getDay()
  if (dow === 0) return 5
  return dow - 1
}

function isSameWeek(a: Date, b: Date): boolean {
  const mondayA = getMonday(a)
  const mondayB = getMonday(b)
  return mondayA.getTime() === mondayB.getTime()
}

const BLOCK_WINDOW_DAYS = 14

export function SchedulePage() {
  const { user } = useAuth()
  const groupId = user?.groupId ?? 0
  const isHeadman = !!user?.isHeadman
  const { isOnline } = useNetworkStatus()
  const { attendanceCounts, personalStatuses, markPersonalStatus } = useStompEvents()
  const queryClient = useQueryClient()

  const [currentWeekStart, setCurrentWeekStart] = useState(() => getMonday(new Date()))
  const [selectedDayIndex, setSelectedDayIndex] = useState(getTodayDayIndex)
  const [toast, setToast] = useState<{ type: 'success' | 'error'; message: string } | null>(null)
  const [activeLesson, setActiveLesson] = useState<LessonResponse | null>(null)
  const [headmanLesson, setHeadmanLesson] = useState<LessonResponse | null>(null)
  const hasAutoScrolled = useRef(false)

  const blockMutation = useBlockLesson()
  const unblockMutation = useUnblockLesson()
  const activeSubject = useSubjectName(activeLesson?.subjectId)
  const headmanSubject = useSubjectName(headmanLesson?.subjectId)

  const { data: activeSemester, isFetched: semesterFetched } = useActiveSemester()
  const { data: allSemesters } = useAllSemesters()

  const semesterBounds = useMemo(() => {
    if (!activeSemester) return null
    return {
      from: parseLocalDate(activeSemester.dateFrom),
      to: parseLocalDate(activeSemester.dateTo),
    }
  }, [activeSemester])

  const nextSemesterStart = useMemo(() => {
    if (!allSemesters) return null
    const today = new Date()
    today.setHours(0, 0, 0, 0)
    const upcoming = allSemesters
      .map((s) => parseLocalDate(s.dateFrom))
      .filter((d) => d.getTime() > today.getTime())
      .sort((a, b) => a.getTime() - b.getTime())
    return upcoming[0] ?? null
  }, [allSemesters])

  /**
   * Показываем info-screen только когда `semesterFetched` (загрузка
   * завершилась) — это убирает false-positive во время initial render
   * и делает тесты/unit-env предсказуемыми (mock без /semesters не
   * блокирует рендер lesson-карточек).
   */
  const outOfBounds = useMemo((): 'before-start' | 'after-end' | 'no-active' | null => {
    if (!semesterFetched) return null
    if (!activeSemester) return 'no-active'
    if (!semesterBounds) return null
    const weekEndDate = addDays(currentWeekStart, 5)
    if (weekEndDate < semesterBounds.from) return 'before-start'
    if (currentWeekStart > semesterBounds.to) return 'after-end'
    return null
  }, [semesterFetched, activeSemester, semesterBounds, currentWeekStart])

  const weekNav = useDateNavigation({
    unit: 'week',
    value: currentWeekStart,
    onChange: setCurrentWeekStart,
    bounds: semesterBounds
      ? { min: semesterBounds.from, max: semesterBounds.to }
      : undefined,
  })

  const weekStart = formatDate(currentWeekStart)
  const weekEnd = formatDate(addDays(currentWeekStart, 5))

  const weekDates = useMemo(
    () => Array.from({ length: 6 }, (_, i) => addDays(currentWeekStart, i)),
    [currentWeekStart],
  )

  const { data: lessons, isLoading, dataUpdatedAt } = useWeekSchedule(groupId, weekStart, weekEnd)
  const { data: records } = useStudentRecords()
  const { data: semesterId } = useActiveSemesterId()
  const { data: homeworks } = useHomeworksForGroup(groupId, semesterId)

  const recordStatuses = useMemo(() => {
    const map: Record<number, AttendanceStatus> = {}
    for (const r of records ?? []) {
      if (
        r.status === 'present' ||
        r.status === 'absent' ||
        r.status === 'excused' ||
        r.status === 'free_attendance'
      ) {
        map[r.lessonId] = r.status
      }
    }
    return map
  }, [records])

  const subjectIds = useMemo(
    () => (lessons ?? []).map((l) => l.subjectId),
    [lessons],
  )
  const { subjectMap } = useSubjectMap(subjectIds)

  const dayLessons = useMemo(() => {
    if (!lessons) return []
    const backendDow = selectedDayIndex + 1
    return lessons
      .filter((l) => l.dayOfWeek === backendDow)
      .sort((a, b) => a.lessonNumber - b.lessonNumber)
  }, [lessons, selectedDayIndex])

  useEffect(() => {
    if (hasAutoScrolled.current || !dayLessons.length) return

    const active = dayLessons.find((l) => l.status === 'ACTIVE')
    const target = active ?? dayLessons.find((l) => l.status === 'PLANNED')
    if (target) {
      requestAnimationFrame(() => {
        document
          .querySelector(`[data-lesson-id="${target.id}"]`)
          ?.scrollIntoView({ behavior: 'smooth', block: 'center' })
      })
      hasAutoScrolled.current = true
    }
  }, [dayLessons])

  const isCurrentWeek = isSameWeek(currentWeekStart, new Date())

  const handleSwipeWeek = useCallback(
    (direction: 'prev' | 'next') => {
      if (direction === 'next') weekNav.goNext()
      else weekNav.goPrev()
    },
    [weekNav],
  )

  const handleTodayPill = useCallback(() => {
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

  const handleCheckinSuccess = useCallback(
    (lessonId: number) => {
      setToast({ type: 'success', message: 'Отметка принята' })
      markPersonalStatus(lessonId, 'present')
    },
    [markPersonalStatus],
  )

  const handleCheckinError = useCallback((msg: string) => {
    setToast({ type: 'error', message: msg })
  }, [])

  const handleToggleBlock = useCallback(
    async (lesson: LessonResponse) => {
      try {
        if (lesson.blockedByHeadman) {
          await unblockMutation.mutateAsync(lesson.id)
          setToast({ type: 'success', message: 'Блокировка снята' })
        } else {
          await blockMutation.mutateAsync(lesson.id)
          setToast({ type: 'success', message: 'Пара заблокирована' })
        }
      } catch (e: unknown) {
        const status = (e as { response?: { status?: number } })?.response?.status
        setToast({ type: 'error', message: mapLessonActionError(status) })
      }
    },
    [blockMutation, unblockMutation],
  )

  const isLessonBlockable = useCallback((lesson: LessonResponse) => {
    if (lesson.status !== 'PLANNED') return false
    const lessonDate = new Date(lesson.date)
    lessonDate.setHours(0, 0, 0, 0)
    const today = new Date()
    today.setHours(0, 0, 0, 0)
    const limit = new Date(today)
    limit.setDate(limit.getDate() + BLOCK_WINDOW_DAYS)
    return lessonDate >= today && lessonDate <= limit
  }, [])

  const handleRefresh = useCallback(async () => {
    await Promise.all([
      queryClient.invalidateQueries({ queryKey: ['schedule'] }),
      queryClient.invalidateQueries({ queryKey: ['studentRecords'] }),
      queryClient.invalidateQueries({ queryKey: ['homeworks'] }),
    ])
  }, [queryClient])

  return (
    <PullToRefresh onRefresh={handleRefresh}>
    <div className="flex min-h-full flex-col">
      {/* Week nav strip */}
      <div
        className="flex items-center justify-between gap-2 px-3 py-2"
        style={{ borderBottom: '1px solid var(--border-subtle)' }}
      >
        <button
          type="button"
          onClick={() => handleSwipeWeek('prev')}
          disabled={!weekNav.canGoPrev}
          className={cn(
            'grid size-11 place-items-center rounded-full',
            'transition-colors duration-200 ease-out',
            'disabled:opacity-30 disabled:cursor-not-allowed',
          )}
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
            className="text-sm font-semibold tabular-nums tracking-tight"
            style={{ color: 'var(--text-primary)' }}
          >
            {formatWeekRange(currentWeekStart)}
          </span>
        </div>

        <button
          type="button"
          onClick={() => handleSwipeWeek('next')}
          disabled={!weekNav.canGoNext}
          className={cn(
            'grid size-11 place-items-center rounded-full',
            'transition-colors duration-200 ease-out',
            'disabled:opacity-30 disabled:cursor-not-allowed',
          )}
          style={{ color: 'var(--text-secondary)' }}
          aria-label="Следующая неделя"
        >
          <CaretRight size={20} weight="bold" />
        </button>
      </div>

      {/* Day tabs */}
      <WeekDayTabs
        selectedDay={selectedDayIndex}
        onSelectDay={setSelectedDayIndex}
        weekDates={weekDates}
        onSwipeWeek={handleSwipeWeek}
        hasOfflineBanner={!isOnline}
        canSwipePrev={weekNav.canGoPrev}
        canSwipeNext={weekNav.canGoNext}
      />

      {/* Offline stale notice */}
      <OfflineStaleNotice dataUpdatedAt={dataUpdatedAt} />

      {/* Lesson list */}
      <AnimatePresence mode="wait">
        <motion.div
          key={`${weekStart}-${selectedDayIndex}`}
          initial={{ opacity: 0, x: 8 }}
          animate={{ opacity: 1, x: 0 }}
          exit={{ opacity: 0 }}
          transition={{ duration: 0.15, ease: 'easeOut' }}
          drag="x"
          dragDirectionLock
          dragConstraints={{ left: 0, right: 0 }}
          onDragEnd={handleDaySwipe}
          className="flex flex-col gap-3 px-4 pb-4 pt-3"
        >
          {outOfBounds ? (
            <ScheduleBoundsNotice
              kind={outOfBounds}
              activeFrom={semesterBounds?.from ?? null}
              activeTo={semesterBounds?.to ?? null}
              nextFrom={nextSemesterStart}
              onReturn={handleTodayPill}
            />
          ) : isLoading ? (
            <SkeletonList count={4} />
          ) : dayLessons.length === 0 ? (
            <EmptyDayState />
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
              {dayLessons.map((lesson) => {
                const lessonHomeworks = (homeworks ?? []).filter(
                  (hw) =>
                    hw.lessonDate === lesson.date &&
                    hw.lessonNumber === lesson.lessonNumber,
                )
                const preview =
                  lessonHomeworks.length > 0
                    ? {
                        title: lessonHomeworks[0].title,
                        count: lessonHomeworks.length,
                      }
                    : undefined
                return (
                  <motion.div
                    key={lesson.id}
                    variants={{
                      hidden: { opacity: 0 },
                      visible: { opacity: 1 },
                    }}
                    className="flex flex-col gap-2"
                  >
                    <LessonCard
                      lesson={lesson}
                      subjectName={subjectMap[lesson.subjectId]}
                      attendanceCount={attendanceCounts[lesson.id]}
                      personalStatus={
                        personalStatuses[lesson.id] ??
                        recordStatuses[lesson.id] ??
                        null
                      }
                      onCheckin={() => handleCheckinSuccess(lesson.id)}
                      onCheckinError={handleCheckinError}
                      onOpenActions={
                        isHeadman
                          ? () => setHeadmanLesson(lesson)
                          : () => setActiveLesson(lesson)
                      }
                      onToggleBlock={
                        isHeadman ? () => handleToggleBlock(lesson) : undefined
                      }
                      isHeadman={isHeadman}
                      isBlockable={isHeadman && isLessonBlockable(lesson)}
                      homeworkPreview={preview}
                    />
                    <LessonHomeworkSection
                      homeworks={homeworks ?? []}
                      groupId={groupId}
                      semesterId={semesterId ?? null}
                      subjectId={lesson.subjectId}
                      lessonDate={lesson.date}
                      lessonNumber={lesson.lessonNumber}
                      isCancelled={lesson.status === 'CANCELLED'}
                      canManage={isHeadman}
                      currentUserId={user?.id}
                    />
                  </motion.div>
                )
              })}
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

      {/* Lesson actions sheet (excuse / request check-in) — student only */}
      <LessonActionsSheet
        open={!!activeLesson}
        lesson={activeLesson}
        subjectName={activeSubject.data ?? 'Пара'}
        personalStatus={
          activeLesson
            ? personalStatuses[activeLesson.id] ??
              recordStatuses[activeLesson.id] ??
              null
            : null
        }
        onClose={() => setActiveLesson(null)}
        onToast={(type, message) => setToast({ type, message })}
      />

      {/* Headman bulk-mark sheet (manual attendance on a single lesson) */}
      <HeadmanLessonSheet
        open={!!headmanLesson}
        lesson={headmanLesson}
        subjectName={headmanSubject.data ?? 'Пара'}
        onClose={() => setHeadmanLesson(null)}
        onToast={(type, message) => setToast({ type, message })}
      />

      {/* Floating "Сегодня" pill */}
      <AnimatePresence>
        {!isCurrentWeek && (
          <motion.button
            key="today-pill"
            type="button"
            initial={{ y: 16, opacity: 0 }}
            animate={{ y: 0, opacity: 1 }}
            exit={{ y: 16, opacity: 0 }}
            transition={{ duration: 0.2, ease: 'easeOut' }}
            whileTap={{ scale: 0.96 }}
            onClick={handleTodayPill}
            className={cn(
              'fixed left-1/2 z-[var(--z-sticky)] -translate-x-1/2',
              'flex items-center gap-2 rounded-full px-5 py-2.5',
              'text-sm font-semibold',
            )}
            style={{
              bottom: 'calc(80px + env(safe-area-inset-bottom))',
              background: 'var(--gradient-brand)',
              color: 'var(--accent-primary-contrast)',
              boxShadow: 'var(--glow-primary)',
            }}
          >
            <CalendarBlank size={16} weight="fill" aria-hidden="true" />
            Сегодня
          </motion.button>
        )}
      </AnimatePresence>
    </div>
    </PullToRefresh>
  )
}

function EmptyDayState() {
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
        <CalendarBlank size={24} weight="duotone" />
      </div>
      <h2
        className="text-lg font-semibold text-balance"
        style={{
          color: 'var(--text-primary)',
          fontFamily: 'var(--font-heading)',
        }}
      >
        Занятий нет
      </h2>
      <p
        className="max-w-xs text-sm text-pretty"
        style={{ color: 'var(--text-secondary)' }}
      >
        В этот день пар не запланировано. Хорошее время
        чтобы отдохнуть или подготовиться.
      </p>
    </div>
  )
}
