import { useMemo, useState } from 'react'
import { CheckCircle, CircleNotch, ClockCountdown, WarningCircle } from '@phosphor-icons/react'
import { motion } from 'motion/react'
import { useSubjectMap } from '@/features/schedule/api'
import {
  getHttpStatus,
  useRequestLateCheckin,
  useStudentAttendanceRecords,
  type AttendanceRecord,
} from '@/features/student/requests/studentRequestsApi'
import { cn } from '@/lib/utils'

interface DayGroup {
  date: string
  label: string
  records: AttendanceRecord[]
}

const DAY_NAMES = ['вс', 'пн', 'вт', 'ср', 'чт', 'пт', 'сб'] as const
const MONTH_NAMES = [
  'янв',
  'фев',
  'мар',
  'апр',
  'май',
  'июн',
  'июл',
  'авг',
  'сен',
  'окт',
  'ноя',
  'дек',
] as const

function todayDateString(): string {
  const d = new Date()
  const y = d.getFullYear()
  const m = String(d.getMonth() + 1).padStart(2, '0')
  const day = String(d.getDate()).padStart(2, '0')
  return `${y}-${m}-${day}`
}

function isPastOrToday(isoDate: string): boolean {
  return isoDate <= todayDateString()
}

function formatDayLabel(isoDate: string): string {
  const d = new Date(`${isoDate}T00:00:00`)
  if (Number.isNaN(d.getTime())) return isoDate
  return `${DAY_NAMES[d.getDay()]}, ${d.getDate()} ${MONTH_NAMES[d.getMonth()]}`
}

function groupByDay(records: AttendanceRecord[]): DayGroup[] {
  const byDate = new Map<string, AttendanceRecord[]>()
  for (const record of records) {
    const bucket = byDate.get(record.lessonDate) ?? []
    bucket.push(record)
    byDate.set(record.lessonDate, bucket)
  }
  return Array.from(byDate.entries())
    .sort(([a], [b]) => a.localeCompare(b))
    .map(([date, dayRecords]) => ({
      date,
      label: formatDayLabel(date),
      records: dayRecords.slice().sort((a, b) => a.lessonNumber - b.lessonNumber),
    }))
}

function lateCheckinError(status: number): string {
  switch (status) {
    case 400:
    case 422:
      return 'Заявку можно подать только на активную или прошедшую пару.'
    case 403:
      return 'Староста отмечает себя через журнал.'
    case 404:
      return 'Пара не найдена или не относится к вашей группе.'
    case 429:
      return 'Слишком много запросов. Подождите минуту.'
    default:
      return 'Не удалось отправить запрос. Попробуйте еще раз.'
  }
}

export function StudentLateCheckinPage() {
  const recordsQuery = useStudentAttendanceRecords()
  const requestLateCheckin = useRequestLateCheckin()
  const [sentRows, setSentRows] = useState<Set<number>>(new Set())
  const [pendingRows, setPendingRows] = useState<Set<number>>(new Set())
  const [rowErrors, setRowErrors] = useState<Record<number, string>>({})

  const absentRecords = useMemo(
    () =>
      (recordsQuery.data ?? []).filter(
        (record) => record.status === 'absent' && isPastOrToday(record.lessonDate),
      ),
    [recordsQuery.data],
  )
  const dayGroups = useMemo(() => groupByDay(absentRecords), [absentRecords])
  const { subjectMap } = useSubjectMap(absentRecords.map((record) => record.subjectId))

  const markPending = (lessonId: number, pending: boolean) => {
    setPendingRows((prev) => {
      const next = new Set(prev)
      if (pending) next.add(lessonId)
      else next.delete(lessonId)
      return next
    })
  }

  const markSent = (lessonId: number) => {
    setSentRows((prev) => new Set(prev).add(lessonId))
  }

  const clearRowError = (lessonId: number) => {
    setRowErrors((prev) => {
      const next = { ...prev }
      delete next[lessonId]
      return next
    })
  }

  const requestForRecord = async (lessonId: number) => {
    if (sentRows.has(lessonId) || pendingRows.has(lessonId)) return
    clearRowError(lessonId)
    markPending(lessonId, true)
    try {
      await requestLateCheckin.mutateAsync(lessonId)
      markSent(lessonId)
    } catch (error) {
      const status = getHttpStatus(error)
      if (status === 409) {
        markSent(lessonId)
      } else {
        setRowErrors((prev) => ({ ...prev, [lessonId]: lateCheckinError(status) }))
      }
    } finally {
      markPending(lessonId, false)
    }
  }

  return (
    <div className="min-h-full px-4 py-5">
      <PageHeader />

      {recordsQuery.isLoading && <SkeletonList />}

      {!recordsQuery.isLoading && recordsQuery.isError && (
        <InlineError text="Не удалось загрузить список занятий. Обновите страницу." />
      )}

      {!recordsQuery.isLoading && !recordsQuery.isError && absentRecords.length === 0 && (
        <EmptyState />
      )}

      {!recordsQuery.isLoading && !recordsQuery.isError && dayGroups.length > 0 && (
        <div className="flex flex-col gap-5">
          {dayGroups.map((group) => (
            <section key={group.date} aria-label={`Занятия за ${group.label}`}>
              <header className="mb-2 flex items-baseline gap-2 px-1">
                <h2
                  className="text-sm font-semibold lowercase"
                  style={{ color: 'var(--text-primary)', fontFamily: 'var(--font-heading)' }}
                >
                  {group.label}
                </h2>
                <span
                  className="text-xs tabular-nums"
                  style={{ color: 'var(--text-muted)' }}
                >
                  {group.records.length}
                </span>
              </header>

              <div className="overflow-hidden rounded-lg border" style={{
                background: 'var(--bg-secondary)',
                borderColor: 'var(--border-subtle)',
              }}>
                {group.records.map((record) => (
                  <LateCheckinRow
                    key={record.lessonId}
                    record={record}
                    subjectName={subjectMap[record.subjectId] ?? 'Предмет'}
                    sent={sentRows.has(record.lessonId)}
                    pending={pendingRows.has(record.lessonId)}
                    error={rowErrors[record.lessonId] ?? null}
                    onRequest={() => { void requestForRecord(record.lessonId) }}
                  />
                ))}
              </div>
            </section>
          ))}
        </div>
      )}
    </div>
  )
}

function PageHeader() {
  return (
    <header className="mb-5 flex items-start gap-3">
      <div
        className="grid size-11 shrink-0 place-items-center rounded-lg border"
        style={{
          background: 'color-mix(in oklab, var(--accent-primary) 12%, transparent)',
          borderColor: 'var(--border-accent)',
          color: 'var(--accent-primary)',
        }}
      >
        <ClockCountdown size={24} weight="duotone" aria-hidden="true" />
      </div>
      <div className="min-w-0">
        <p className="text-xs font-medium uppercase" style={{ color: 'var(--text-muted)' }}>
          Студент
        </p>
        <h1 className="text-2xl font-bold leading-tight text-balance">
          Запрос отметки
        </h1>
      </div>
    </header>
  )
}

function LateCheckinRow({
  record,
  subjectName,
  sent,
  pending,
  error,
  onRequest,
}: {
  record: AttendanceRecord
  subjectName: string
  sent: boolean
  pending: boolean
  error: string | null
  onRequest: () => void
}) {
  return (
    <article
      className="border-b px-4 py-3 last:border-b-0"
      style={{ borderColor: 'var(--border-subtle)' }}
    >
      <div className="flex items-start justify-between gap-3">
        <div className="min-w-0">
          <p
            className="truncate text-base font-semibold"
            style={{ color: 'var(--text-primary)', fontFamily: 'var(--font-heading)' }}
          >
            {subjectName}
          </p>
          <div className="mt-1 flex items-center gap-2 text-xs" style={{ color: 'var(--text-muted)' }}>
            <span className="tabular-nums">Пара №{record.lessonNumber}</span>
            <span aria-hidden="true">·</span>
            <span
              className="inline-flex min-w-6 justify-center rounded border px-1.5 py-0.5 text-[11px] font-semibold"
              style={{
                color: 'var(--accent-danger)',
                borderColor: 'color-mix(in oklab, var(--accent-danger) 35%, transparent)',
                background: 'color-mix(in oklab, var(--accent-danger) 10%, transparent)',
              }}
              aria-label="Статус: отсутствовал"
            >
              н
            </span>
          </div>
        </div>

        <button
          type="button"
          onClick={onRequest}
          disabled={sent || pending}
          aria-busy={pending}
          className={cn(
            'inline-flex min-h-9 shrink-0 items-center justify-center gap-1.5 rounded-full border px-3',
            'text-xs font-semibold transition-colors',
            (sent || pending) && 'cursor-not-allowed opacity-70',
          )}
          style={{
            borderColor: sent ? 'var(--border-default)' : 'var(--accent-primary)',
            color: sent ? 'var(--text-secondary)' : 'var(--accent-primary)',
            background: sent ? 'var(--bg-elevated)' : 'transparent',
          }}
        >
          {pending ? (
            <CircleNotch size={15} weight="bold" className="animate-spin" aria-hidden="true" />
          ) : sent ? (
            <CheckCircle size={15} weight="fill" aria-hidden="true" />
          ) : (
            <ClockCountdown size={15} weight="bold" aria-hidden="true" />
          )}
          <span>{sent ? 'Заявка отправлена' : pending ? 'Отправляем' : 'Запросить'}</span>
        </button>
      </div>

      {error && (
        <p className="mt-2 text-xs" style={{ color: 'var(--accent-danger)' }} role="alert">
          {error}
        </p>
      )}
    </article>
  )
}

function EmptyState() {
  return (
    <div className="flex flex-col items-center justify-center py-14 text-center">
      <div
        className="mb-4 grid size-20 place-items-center rounded-full border"
        style={{
          background: 'var(--bg-secondary)',
          borderColor: 'var(--border-subtle)',
          color: 'var(--accent-primary)',
        }}
      >
        <CheckCircle size={38} weight="duotone" aria-hidden="true" />
      </div>
      <h2 className="text-lg font-semibold">Нет пропущенных занятий</h2>
      <p className="mt-2 max-w-[280px] text-sm" style={{ color: 'var(--text-secondary)' }}>
        Для прошедших пар со статусом «н» заявки появятся здесь.
      </p>
    </div>
  )
}

function InlineError({ text }: { text: string }) {
  return (
    <div
      role="alert"
      className="flex items-start gap-2 rounded-lg border px-4 py-3 text-sm"
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
          className="h-[74px] rounded-lg"
          style={{ background: 'var(--bg-secondary)' }}
        />
      ))}
    </div>
  )
}
