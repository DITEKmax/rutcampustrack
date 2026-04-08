import { useState, useMemo, useCallback } from 'react'
import { AnimatePresence, motion } from 'motion/react'
import { Clock, MapPin, Users, Hourglass } from '@phosphor-icons/react'
import { useAuth } from '@/features/auth/AuthProvider'
import { useWeekSchedule, useSubjectName } from '@/features/schedule/api'
import { OfflineStaleNotice } from '@/features/schedule/OfflineStaleNotice'
import { useStompEvents } from './StompProvider'
import { CheckInButton } from './CheckInButton'
import { CheckInToast } from './CheckInToast'
import type { LessonResponse } from '@/features/schedule/types'

/**
 * CheckIn screen — the single-purpose page that answers "can I mark this
 * lesson right now?". Centered brand hero when an ACTIVE lesson exists,
 * soft waiting-state card when not.
 *
 * All feature logic preserved from the previous iteration — only layout,
 * copy, and visual treatment changed.
 */

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
    <motion.section
      initial={{ opacity: 0, y: 12 }}
      animate={{ opacity: 1, y: 0 }}
      transition={{ duration: 0.3, ease: 'easeOut' }}
      className="flex w-full max-w-sm flex-col items-center gap-5"
    >
      {/* Live indicator */}
      <div
        className="flex items-center gap-2 rounded-full px-3 py-1 text-[11px] font-medium uppercase tracking-wide"
        style={{
          background: 'color-mix(in oklab, var(--accent-primary) 12%, transparent)',
          border: '1px solid var(--border-accent)',
          color: 'var(--accent-primary)',
        }}
      >
        <span
          aria-hidden="true"
          className="size-1.5 rounded-full"
          style={{
            background: 'var(--accent-primary)',
            boxShadow: '0 0 6px var(--accent-primary)',
            animation: 'ruttrack-live-blink 1.6s ease-in-out infinite',
          }}
        />
        Идёт сейчас
      </div>

      {/* Subject name — big */}
      <h2
        className="text-center text-2xl font-bold leading-tight text-balance"
        style={{
          color: 'var(--text-primary)',
          fontFamily: 'var(--font-display)',
          letterSpacing: '-0.02em',
        }}
      >
        {subjectName ?? 'Предмет'}
      </h2>

      {/* Time + room meta */}
      <div
        className="flex items-center gap-4 text-sm tabular-nums"
        style={{ color: 'var(--text-secondary)' }}
      >
        <span className="inline-flex items-center gap-1.5">
          <Clock size={14} weight="fill" aria-hidden="true" />
          {formatTime(lesson.startTime)} — {formatTime(lesson.endTime)}
        </span>
        <span
          aria-hidden="true"
          className="size-1 rounded-full"
          style={{ background: 'var(--text-muted)' }}
        />
        <span className="inline-flex items-center gap-1.5">
          <MapPin size={14} weight="fill" aria-hidden="true" />
          Ауд. {lesson.room}
        </span>
      </div>

      {/* CTA */}
      <div className="flex flex-col items-center gap-3 pt-2">
        {personalStatus ? (
          <div
            className="flex items-center gap-2 rounded-full border px-5 py-3 text-sm font-semibold"
            style={{
              background: 'color-mix(in oklab, var(--accent-primary) 14%, transparent)',
              borderColor: 'var(--border-accent)',
              color: 'var(--accent-primary)',
            }}
          >
            Вы отметились
          </div>
        ) : (
          <CheckInButton onSuccess={handleSuccess} onError={handleError} />
        )}

        <p
          className="flex items-center gap-1.5 text-xs tabular-nums"
          style={{ color: 'var(--text-muted)' }}
        >
          <Users size={12} weight="fill" aria-hidden="true" />
          {count} отметилось
        </p>
      </div>

      <AnimatePresence>
        {toast && (
          <CheckInToast
            type={toast.type}
            message={toast.message}
            onDismiss={() => setToast(null)}
          />
        )}
      </AnimatePresence>

      <style>{`
        @keyframes ruttrack-live-blink {
          0%, 100% { opacity: 1; }
          50% { opacity: 0.35; }
        }
        @media (prefers-reduced-motion: reduce) {
          [aria-hidden="true"] { animation: none !important; }
        }
      `}</style>
    </motion.section>
  )
}

function NextLessonHint({ lesson }: { lesson: LessonResponse }) {
  const { data: subjectName } = useSubjectName(lesson.subjectId)
  return (
    <div
      className="flex w-full max-w-sm flex-col items-center gap-4 rounded-2xl border p-6 text-center"
      style={{
        background: 'var(--bg-secondary)',
        borderColor: 'var(--border-subtle)',
      }}
    >
      <div
        className="grid size-14 place-items-center rounded-full"
        style={{
          background: 'color-mix(in oklab, var(--accent-secondary) 14%, transparent)',
          border: '1px solid color-mix(in oklab, var(--accent-secondary) 30%, transparent)',
          color: 'var(--accent-secondary)',
        }}
      >
        <Hourglass size={26} weight="duotone" />
      </div>
      <div className="flex flex-col gap-1.5">
        <p
          className="text-xs font-medium uppercase tracking-wide"
          style={{ color: 'var(--text-muted)' }}
        >
          Следующая пара
        </p>
        <p
          className="text-base font-semibold text-balance"
          style={{ color: 'var(--text-primary)', fontFamily: 'var(--font-heading)' }}
        >
          {subjectName ?? 'Предмет'}
        </p>
        <p
          className="text-sm tabular-nums"
          style={{ color: 'var(--text-secondary)' }}
        >
          начало в {formatTime(lesson.startTime)}
        </p>
      </div>
    </div>
  )
}

function IdleState({ nextPlanned }: { nextPlanned: LessonResponse | null }) {
  return (
    <div className="flex flex-col items-center gap-6 text-center">
      <div
        className="grid size-20 place-items-center rounded-full"
        style={{
          background: 'color-mix(in oklab, var(--text-primary) 6%, transparent)',
          border: '1px solid var(--border-subtle)',
          color: 'var(--text-muted)',
        }}
      >
        <Clock size={36} weight="duotone" />
      </div>
      <div className="flex flex-col items-center gap-2">
        <h2
          className="text-xl font-bold text-balance"
          style={{ color: 'var(--text-primary)', fontFamily: 'var(--font-display)' }}
        >
          Нет активных пар
        </h2>
        <p
          className="max-w-xs text-sm text-pretty"
          style={{ color: 'var(--text-secondary)' }}
        >
          Отметка станет доступна за 5 минут до начала следующего занятия.
        </p>
      </div>
      {nextPlanned ? (
        <NextLessonHint lesson={nextPlanned} />
      ) : (
        <p className="text-xs" style={{ color: 'var(--text-muted)' }}>
          На сегодня пар больше нет
        </p>
      )}
    </div>
  )
}

export function CheckInScreen() {
  const { user } = useAuth()
  const groupId = user?.groupId ?? 0
  const todayStr = useMemo(() => getTodayStr(), [])

  const { data: lessons, dataUpdatedAt } = useWeekSchedule(groupId, todayStr, todayStr)

  const activeLesson = useMemo(
    () => (lessons ?? []).find((l) => l.status === 'ACTIVE'),
    [lessons],
  )

  const nextPlanned = useMemo(() => {
    if (activeLesson) return null
    return (
      (lessons ?? [])
        .filter((l) => l.status === 'PLANNED')
        .sort((a, b) => a.startTime.localeCompare(b.startTime))[0] ?? null
    )
  }, [lessons, activeLesson])

  return (
    <div className="flex min-h-full flex-col">
      <OfflineStaleNotice dataUpdatedAt={dataUpdatedAt} />

      <div className="flex flex-1 items-center justify-center px-4 py-10">
        {activeLesson ? (
          <ActiveLessonCard lesson={activeLesson} />
        ) : (
          <IdleState nextPlanned={nextPlanned} />
        )}
      </div>
    </div>
  )
}
