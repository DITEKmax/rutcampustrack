import { useEffect, useMemo, useState, type ReactNode } from 'react'
import { Link } from 'react-router'
import {
  ArrowLeft,
  BookOpen,
  CalendarBlank,
  CircleNotch,
  WarningCircle,
} from '@phosphor-icons/react'
import { hapticFeedback } from '@telegram-apps/sdk-react'
import { motion } from 'motion/react'
import { useAuth } from '@/features/auth/AuthProvider'
import {
  useGroupSubjects,
  useJournal,
  useMarkAttendance,
} from '@/features/headman/shared/headmanApi'
import {
  ATTENDANCE_STATUS_LABELS,
  ATTENDANCE_STATUS_OPTIONS,
  JOURNAL_STATUS_SYMBOLS,
  type AttendanceStatus,
  type JournalCell,
} from '@/features/headman/shared/types'
import { cn } from '@/lib/utils'

export function HeadmanJournalPage() {
  const { user } = useAuth()
  const groupId = user?.groupId
  const isHeadman = user?.role === 'STUDENT' && user.isHeadman
  const headmanGroupId = isHeadman ? groupId : undefined
  const subjectsQuery = useGroupSubjects(isHeadman)
  const subjects = subjectsQuery.data ?? []
  const [date, setDate] = useState(todayLocalDate)
  const [subjectId, setSubjectId] = useState<number | undefined>()
  const journalQuery = useJournal(headmanGroupId, subjectId, date, date)

  useEffect(() => {
    if (!isHeadman || subjects.length === 0) return
    const selectedExists = subjects.some((subject) => subject.id === subjectId)
    if (!selectedExists) setSubjectId(subjects[0].id)
  }, [isHeadman, subjectId, subjects])

  const cells = useMemo(() => {
    return [...(journalQuery.data ?? [])].sort((left, right) => {
      const lessonDelta = (left.lessonNumber ?? 0) - (right.lessonNumber ?? 0)
      if (lessonDelta !== 0) return lessonDelta
      return left.studentName.localeCompare(right.studentName, 'ru')
    })
  }, [journalQuery.data])

  if (!isHeadman) {
    return (
      <PageShell>
        <BackLink />
        <InlineState text="Раздел доступен только старосте." />
      </PageShell>
    )
  }

  if (!groupId) {
    return (
      <PageShell>
        <BackLink />
        <InlineState text="Группа не назначена." />
      </PageShell>
    )
  }

  return (
    <PageShell>
      <BackLink />

      <header className="mb-5 flex items-start gap-3">
        <div
          className="grid size-11 shrink-0 place-items-center rounded-lg border"
          style={{
            background: 'color-mix(in oklab, var(--accent-secondary) 13%, transparent)',
            borderColor: 'color-mix(in oklab, var(--accent-secondary) 35%, transparent)',
            color: 'var(--accent-secondary)',
          }}
        >
          <BookOpen size={24} weight="duotone" aria-hidden="true" />
        </div>
        <div className="min-w-0">
          <p className="text-xs font-medium uppercase" style={{ color: 'var(--text-muted)' }}>
            Староста
          </p>
          <h1
            className="text-2xl font-bold leading-tight text-balance"
            style={{ fontFamily: 'var(--font-heading)' }}
          >
            Журнал
          </h1>
        </div>
      </header>

      <JournalFilters
        subjectId={subjectId}
        subjects={subjects}
        date={date}
        disabled={subjectsQuery.isLoading}
        onSubjectChange={setSubjectId}
        onDateChange={setDate}
      />

      {subjectsQuery.isLoading && <SkeletonList />}

      {!subjectsQuery.isLoading && subjectsQuery.isError && (
        <InlineError text="Не удалось загрузить предметы группы. Обновите страницу." />
      )}

      {!subjectsQuery.isLoading && !subjectsQuery.isError && subjects.length === 0 && (
        <EmptyState text="Предметов пока нет." />
      )}

      {!subjectsQuery.isLoading &&
        !subjectsQuery.isError &&
        subjects.length > 0 &&
        journalQuery.isLoading && <SkeletonList />}

      {!subjectsQuery.isLoading &&
        !subjectsQuery.isError &&
        subjects.length > 0 &&
        journalQuery.isError && (
          <InlineError text="Не удалось загрузить журнал. Проверьте дату и предмет." />
        )}

      {!subjectsQuery.isLoading &&
        !subjectsQuery.isError &&
        subjects.length > 0 &&
        !journalQuery.isLoading &&
        !journalQuery.isError &&
        cells.length === 0 && <EmptyState text="Нет занятий для выбранных параметров." />}

      {!journalQuery.isLoading && !journalQuery.isError && cells.length > 0 && (
        <div className="flex flex-col gap-3">
          {cells.map((cell, index) => (
            <JournalRow key={`${cell.lessonId}-${cell.studentId}`} cell={cell} index={index} />
          ))}
        </div>
      )}
    </PageShell>
  )
}

function JournalFilters({
  subjectId,
  subjects,
  date,
  disabled,
  onSubjectChange,
  onDateChange,
}: {
  subjectId: number | undefined
  subjects: { id: number; name: string }[]
  date: string
  disabled: boolean
  onSubjectChange: (subjectId: number | undefined) => void
  onDateChange: (date: string) => void
}) {
  return (
    <section className="mb-4 grid grid-cols-1 gap-3">
      <label className="flex flex-col gap-1.5 text-xs font-medium" style={{ color: 'var(--text-secondary)' }}>
        Предмет
        <select
          value={subjectId ?? ''}
          disabled={disabled || subjects.length === 0}
          onChange={(event) => {
            const next = Number(event.target.value)
            onSubjectChange(Number.isFinite(next) && next > 0 ? next : undefined)
          }}
          className="min-h-11 rounded-lg border px-3 text-sm outline-none"
          style={{
            background: 'var(--bg-secondary)',
            borderColor: 'var(--border-subtle)',
            color: 'var(--text-primary)',
          }}
        >
          <option value="">Выберите предмет</option>
          {subjects.map((subject) => (
            <option key={subject.id} value={subject.id}>
              {subject.name}
            </option>
          ))}
        </select>
      </label>

      <label className="flex flex-col gap-1.5 text-xs font-medium" style={{ color: 'var(--text-secondary)' }}>
        Дата
        <span
          className="flex min-h-11 items-center gap-2 rounded-lg border px-3"
          style={{
            background: 'var(--bg-secondary)',
            borderColor: 'var(--border-subtle)',
            color: 'var(--text-primary)',
          }}
        >
          <CalendarBlank size={18} weight="duotone" aria-hidden="true" />
          <input
            type="date"
            value={date}
            onChange={(event) => onDateChange(event.target.value)}
            className="min-w-0 flex-1 bg-transparent text-sm outline-none"
            style={{ color: 'var(--text-primary)' }}
          />
        </span>
      </label>
    </section>
  )
}

function JournalRow({ cell, index }: { cell: JournalCell; index: number }) {
  const markAttendance = useMarkAttendance()
  const [status, setStatus] = useState(cell.status)
  const [error, setError] = useState<string | null>(null)

  useEffect(() => {
    setStatus(cell.status)
    setError(null)
  }, [cell.status])

  const mark = async (next: AttendanceStatus) => {
    if (next === status || markAttendance.isPending) return
    const previous = status
    setStatus(next)
    setError(null)
    if (hapticFeedback.selectionChanged.isAvailable()) {
      hapticFeedback.selectionChanged()
    }
    try {
      await markAttendance.mutateAsync({
        lessonId: cell.lessonId,
        studentId: cell.studentId,
        status: next,
      })
      if (hapticFeedback.notificationOccurred.isAvailable()) {
        hapticFeedback.notificationOccurred('success')
      }
    } catch {
      setStatus(previous)
      setError('Не удалось сохранить отметку.')
      if (hapticFeedback.notificationOccurred.isAvailable()) {
        hapticFeedback.notificationOccurred('error')
      }
    }
  }

  return (
    <motion.article
      initial={{ opacity: 0, y: 8 }}
      animate={{ opacity: 1, y: 0 }}
      transition={{ duration: 0.16, delay: index * 0.018, ease: 'easeOut' }}
      className="rounded-lg border px-3 py-3"
      style={{ background: 'var(--bg-secondary)', borderColor: 'var(--border-subtle)' }}
    >
      <div className="mb-3 flex items-start justify-between gap-3">
        <div className="min-w-0">
          <h2
            className="truncate text-sm font-semibold"
            style={{ color: 'var(--text-primary)', fontFamily: 'var(--font-heading)' }}
          >
            {cell.studentName}
          </h2>
          <p className="mt-0.5 text-xs" style={{ color: 'var(--text-muted)' }}>
            {formatLessonMeta(cell)}
          </p>
        </div>
        <span
          className="grid h-8 min-w-8 shrink-0 place-items-center rounded-full border px-2 text-xs font-bold"
          style={{
            background: 'color-mix(in oklab, var(--accent-primary) 10%, transparent)',
            borderColor: 'var(--border-accent)',
            color: 'var(--accent-primary)',
          }}
        >
          {status === cell.status && cell.symbol ? cell.symbol : JOURNAL_STATUS_SYMBOLS[status]}
        </span>
      </div>

      <div className="grid grid-cols-4 gap-1.5">
        {ATTENDANCE_STATUS_OPTIONS.map((option) => (
          <StatusButton
            key={option}
            option={option}
            active={option === status}
            disabled={markAttendance.isPending || status === 'cancelled'}
            onClick={() => void mark(option)}
          />
        ))}
      </div>

      {cell.excuseReason && (
        <p className="mt-2 text-xs" style={{ color: 'var(--text-muted)' }}>
          {cell.excuseReason}
        </p>
      )}

      {error && (
        <p role="alert" className="mt-2 text-xs font-medium" style={{ color: 'var(--accent-danger)' }}>
          {error}
        </p>
      )}
    </motion.article>
  )
}

function StatusButton({
  option,
  active,
  disabled,
  onClick,
}: {
  option: AttendanceStatus
  active: boolean
  disabled: boolean
  onClick: () => void
}) {
  return (
    <button
      type="button"
      aria-pressed={active}
      aria-label={ATTENDANCE_STATUS_LABELS[option]}
      disabled={disabled}
      onClick={onClick}
      className={cn(
        'grid min-h-10 place-items-center rounded-lg border px-1',
        'text-xs font-bold transition-colors',
        disabled && 'opacity-70',
      )}
      style={{
        background: active
          ? 'var(--accent-primary)'
          : 'color-mix(in oklab, var(--text-primary) 4%, transparent)',
        borderColor: active ? 'var(--accent-primary)' : 'var(--border-subtle)',
        color: active ? 'var(--accent-primary-contrast)' : 'var(--text-secondary)',
      }}
    >
      {disabled && active ? (
        <CircleNotch size={15} weight="bold" className="animate-spin" aria-hidden="true" />
      ) : (
        JOURNAL_STATUS_SYMBOLS[option]
      )}
    </button>
  )
}

function PageShell({ children }: { children: ReactNode }) {
  return (
    <div className="min-h-full px-3 py-4 pb-[calc(72px+env(safe-area-inset-bottom))]">
      {children}
    </div>
  )
}

function BackLink() {
  return (
    <Link
      to="/group"
      className="mb-4 inline-flex items-center gap-2 rounded-full border px-3 py-1.5 text-xs font-semibold"
      style={{
        background: 'var(--bg-secondary)',
        borderColor: 'var(--border-subtle)',
        color: 'var(--text-secondary)',
      }}
    >
      <ArrowLeft size={14} weight="bold" aria-hidden="true" />
      Назад
    </Link>
  )
}

function EmptyState({ text }: { text: string }) {
  return (
    <div className="flex min-h-[28vh] flex-col items-center justify-center gap-3 text-center">
      <div
        className="grid size-14 place-items-center rounded-full border"
        style={{
          background: 'var(--bg-secondary)',
          borderColor: 'var(--border-subtle)',
          color: 'var(--accent-secondary)',
        }}
      >
        <BookOpen size={28} weight="duotone" aria-hidden="true" />
      </div>
      <p className="max-w-[30ch] text-sm" style={{ color: 'var(--text-secondary)' }}>
        {text}
      </p>
    </div>
  )
}

function InlineState({ text }: { text: string }) {
  return (
    <div className="flex min-h-[40vh] flex-col items-center justify-center gap-3 text-center">
      <WarningCircle size={28} weight="duotone" style={{ color: 'var(--accent-warning)' }} />
      <p className="text-sm" style={{ color: 'var(--text-secondary)' }}>
        {text}
      </p>
    </div>
  )
}

function InlineError({ text }: { text: string }) {
  return (
    <div
      role="alert"
      className="mb-3 flex items-start gap-2 rounded-lg border px-4 py-3 text-sm"
      style={{
        background: 'color-mix(in oklab, var(--accent-danger) 9%, transparent)',
        borderColor: 'color-mix(in oklab, var(--accent-danger) 35%, transparent)',
        color: 'var(--accent-danger)',
      }}
    >
      <WarningCircle size={18} weight="fill" className="mt-0.5 shrink-0" aria-hidden="true" />
      <span>{text}</span>
    </div>
  )
}

function SkeletonList() {
  return (
    <div className="flex flex-col gap-3" aria-label="Загрузка">
      {Array.from({ length: 4 }).map((_, index) => (
        <motion.div
          key={index}
          initial={{ opacity: 0.6 }}
          animate={{ opacity: 1 }}
          transition={{ repeat: Infinity, repeatType: 'reverse', duration: 0.9 }}
          className="h-[116px] rounded-lg"
          style={{ background: 'var(--bg-secondary)' }}
        />
      ))}
    </div>
  )
}

function formatLessonMeta(cell: JournalCell): string {
  const parts = []
  if (cell.lessonNumber) parts.push(`${cell.lessonNumber} пара`)
  if (cell.date) parts.push(formatDate(cell.date))
  return parts.join(' · ') || 'Пара'
}

function formatDate(value: string): string {
  const [year, month, day] = value.split('-')
  if (!year || !month || !day) return value
  return `${day}.${month}.${year}`
}

function todayLocalDate(): string {
  const date = new Date()
  const localTime = date.getTime() - date.getTimezoneOffset() * 60_000
  return new Date(localTime).toISOString().slice(0, 10)
}
