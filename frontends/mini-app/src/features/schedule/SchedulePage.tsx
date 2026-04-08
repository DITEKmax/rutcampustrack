import { useNavigate } from 'react-router'
import { CalendarBlank, Info, WarningCircle, ArrowsClockwise } from '@phosphor-icons/react'
import { useAuth } from '@/features/auth/AuthProvider'
import { SkeletonList } from '@/shared/components/Skeleton'
import { useTodaySchedule, useSubjectName } from './api'
import { LessonCard } from './LessonCard'
import type { LessonResponse } from './types'

/**
 * Schedule page for the Telegram Mini App.
 *
 * Page title comes from `AppHeader` (derived from route). This page only
 * owns the date subtitle + lesson list + states (loading / error / empty /
 * non-student / ok). Tapping an ACTIVE lesson dispatches haptic feedback
 * inside LessonCard then navigates to the check-in flow.
 */
function SubjectNameCard({
  lesson,
  onCheckin,
}: {
  lesson: LessonResponse
  onCheckin: (id: number) => void
}) {
  const { data: subject } = useSubjectName(lesson.subjectId)
  return <LessonCard lesson={lesson} subjectName={subject?.name ?? '…'} onCheckin={onCheckin} />
}

export function SchedulePage() {
  const { user } = useAuth()
  const navigate = useNavigate()

  const isStudent = user?.role === 'STUDENT' && user.groupId
  const { data: lessons, isLoading, isError, refetch } = useTodaySchedule(
    isStudent ? user.groupId : undefined,
  )

  const today = new Date().toLocaleDateString('ru-RU', {
    weekday: 'long',
    day: 'numeric',
    month: 'long',
  })

  if (!isStudent) {
    return <NonStudentState />
  }

  return (
    <div className="px-3 pt-3 pb-[calc(72px+env(safe-area-inset-bottom))]">
      <p
        className="text-[11px] font-medium uppercase capitalize tracking-wide"
        style={{ color: 'var(--text-muted)', fontFamily: 'var(--font-mono)' }}
      >
        Сегодня
      </p>
      <p
        className="mt-0.5 text-sm font-semibold capitalize text-balance"
        style={{ color: 'var(--text-primary)' }}
      >
        {today}
      </p>

      <div className="mt-4 flex flex-col gap-3">
        {isLoading && <SkeletonList count={4} />}

        {isError && (
          <ErrorState onRetry={() => refetch()} />
        )}

        {!isLoading && !isError && lessons?.length === 0 && <EmptyDayState />}

        {!isLoading &&
          !isError &&
          lessons?.map((lesson) => (
            <SubjectNameCard
              key={lesson.id}
              lesson={lesson}
              onCheckin={(id) => navigate(`/checkin/${id}`)}
            />
          ))}
      </div>
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
