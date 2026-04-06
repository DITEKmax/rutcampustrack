import { useState, useMemo, useCallback } from 'react'
import { AnimatePresence } from 'motion/react'
import { useAuth } from '@/features/auth/AuthProvider'
import { useWeekSchedule, useSubjectName } from '@/features/schedule/api'
import { StatusBadge } from '@/features/schedule/StatusBadge'
import { OfflineStaleNotice } from '@/features/schedule/OfflineStaleNotice'
import { useStompEvents } from './StompProvider'
import { CheckInButton } from './CheckInButton'
import { CheckInToast } from './CheckInToast'
import type { LessonResponse } from '@/features/schedule/types'

function formatTime(time: string): string {
  return time.slice(0, 5)
}

function getTodayStr(): string {
  const d = new Date()
  const y = d.getFullYear()
  const m = String(d.getMonth() + 1).padStart(2, '0')
  const day = String(d.getDate()).padStart(2, '0')
  return `${y}-${m}-${day}`
}

function ActiveLessonCard({ lesson }: { lesson: LessonResponse }) {
  const { data: subjectName } = useSubjectName(lesson.subjectId)
  const { attendanceCounts, personalStatuses, markPersonalStatus } = useStompEvents()
  const [toast, setToast] = useState<{ type: 'success' | 'error'; message: string } | null>(null)

  const personalStatus = personalStatuses[lesson.id] ?? null
  const count = attendanceCounts[lesson.id] ?? 0

  const handleSuccess = useCallback(() => {
    setToast({ type: 'success', message: 'Отметка принята' })
    markPersonalStatus(lesson.id, 'present')
  }, [lesson.id, markPersonalStatus])

  const handleError = useCallback((msg: string) => {
    setToast({ type: 'error', message: msg })
  }, [])

  return (
    <>
      <h2 className="text-2xl font-semibold text-center">
        {subjectName ?? 'Предмет'}
      </h2>
      <p className="text-base text-muted-foreground text-center">
        {formatTime(lesson.startTime)} - {formatTime(lesson.endTime)}
      </p>
      <p className="text-sm text-muted-foreground text-center">
        Ауд. {lesson.room}
      </p>
      <div className="flex justify-center">
        {personalStatus ? (
          <StatusBadge status={personalStatus} />
        ) : (
          <StatusBadge status={lesson.status} />
        )}
      </div>

      {!personalStatus && (
        <CheckInButton
          onSuccess={handleSuccess}
          onError={handleError}
        />
      )}

      <p className="text-xs text-muted-foreground text-center">
        {count} чел
      </p>

      <AnimatePresence>
        {toast && (
          <CheckInToast
            type={toast.type}
            message={toast.message}
            onDismiss={() => setToast(null)}
          />
        )}
      </AnimatePresence>
    </>
  )
}

function NextLessonHint({ lesson }: { lesson: LessonResponse }) {
  const { data: subjectName } = useSubjectName(lesson.subjectId)
  return (
    <p className="text-sm text-muted-foreground text-center">
      Следующая: {subjectName ?? 'Предмет'} в {formatTime(lesson.startTime)}
    </p>
  )
}

export function CheckInScreen() {
  const { user } = useAuth()
  const groupId = user?.groupId ?? 0
  const todayStr = useMemo(() => getTodayStr(), [])

  const { data: lessons, dataUpdatedAt } = useWeekSchedule(groupId, todayStr, todayStr)

  const activeLesson = useMemo(
    () => (lessons ?? []).find((l) => l.status === 'ACTIVE'),
    [lessons]
  )

  const nextPlanned = useMemo(() => {
    if (activeLesson) return null
    return (lessons ?? [])
      .filter((l) => l.status === 'PLANNED')
      .sort((a, b) => a.startTime.localeCompare(b.startTime))[0] ?? null
  }, [lessons, activeLesson])

  return (
    <div className="flex flex-col min-h-full">
      <OfflineStaleNotice dataUpdatedAt={dataUpdatedAt} />

      <div className="flex flex-col items-center justify-center px-4 pt-8 gap-6 min-h-[60vh]">
        {activeLesson ? (
          <ActiveLessonCard lesson={activeLesson} />
        ) : (
          <>
            <h2 className="text-xl font-semibold text-center text-muted-foreground">
              Сейчас нет активных пар
            </h2>
            {nextPlanned ? (
              <NextLessonHint lesson={nextPlanned} />
            ) : (
              <p className="text-sm text-muted-foreground text-center">
                На сегодня пар больше нет
              </p>
            )}
          </>
        )}
      </div>
    </div>
  )
}
