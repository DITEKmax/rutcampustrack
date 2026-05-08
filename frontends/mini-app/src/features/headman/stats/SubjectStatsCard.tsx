import { useEffect, useMemo, useState } from 'react'
import { AnimatePresence, motion } from 'motion/react'
import { CaretDown, Check, CircleNotch, WarningCircle } from '@phosphor-icons/react'
import {
  mapHeadmanApiError,
  useSetSubjectThreshold,
} from '@/features/headman/shared/headmanApi'
import { SUBJECT_TYPE_LABELS, type SubjectType } from '@/features/headman/shared/types'
import { cn } from '@/lib/utils'

export interface SubjectStudentRow {
  studentId: number
  studentName: string
  attendancePercent: number
}

interface SubjectStatsCardProps {
  subjectId: number
  subjectName: string
  subjectType?: SubjectType
  groupAttendancePercent: number
  threshold: number
  studentRows: SubjectStudentRow[]
}

export function SubjectStatsCard({
  subjectId,
  subjectName,
  subjectType,
  groupAttendancePercent,
  threshold,
  studentRows,
}: SubjectStatsCardProps) {
  const typeLabel = subjectType ? SUBJECT_TYPE_LABELS[subjectType] : null
  const [editValue, setEditValue] = useState(threshold)
  const [expanded, setExpanded] = useState(false)
  const [showSuccess, setShowSuccess] = useState(false)
  const setThreshold = useSetSubjectThreshold()
  const {
    mutate,
    reset,
    error,
    isError,
    isPending,
    isSuccess,
  } = setThreshold

  useEffect(() => {
    setEditValue(threshold)
  }, [threshold])

  useEffect(() => {
    if (!isSuccess) return
    setShowSuccess(true)
    const timeout = window.setTimeout(() => {
      setShowSuccess(false)
      reset()
    }, 1000)
    return () => window.clearTimeout(timeout)
  }, [isSuccess, reset])

  const sortedRows = useMemo(
    () => [...studentRows].sort((a, b) => a.attendancePercent - b.attendancePercent),
    [studentRows],
  )
  const isRedZone = groupAttendancePercent < threshold
  const errorStatus = (error as { response?: { status?: number } } | null)?.response?.status ?? 500
  const errorText = isError ? mapHeadmanApiError(errorStatus) : null

  const saveThreshold = () => {
    const minPercentage = Math.max(0, Math.min(100, Math.round(editValue)))
    mutate({ subjectId, minPercentage })
  }

  return (
    <article
      className="rounded-lg border px-3 py-3"
      style={{
        background: 'var(--bg-secondary)',
        borderColor: isRedZone
          ? 'color-mix(in oklab, var(--accent-danger) 45%, var(--border-subtle))'
          : 'var(--border-subtle)',
      }}
      data-testid={`subject-stats-card-${subjectId}`}
    >
      <header className="mb-3 flex items-start gap-3">
        <div className="min-w-0 flex-1">
          <div className="flex flex-wrap items-center gap-2">
            <h2
              className="min-w-0 text-sm font-semibold"
              style={{ color: 'var(--text-primary)', fontFamily: 'var(--font-heading)' }}
            >
              {subjectName}
            </h2>
            {typeLabel && <Badge text={typeLabel} />}
          </div>
          {isRedZone && (
            <p className="mt-1 inline-flex items-center gap-1 text-xs font-semibold" style={{ color: 'var(--accent-danger)' }}>
              <WarningCircle size={14} weight="fill" aria-hidden="true" />
              Низкая посещаемость
            </p>
          )}
        </div>
        <div className="shrink-0 text-right">
          <p
            className="text-2xl font-bold leading-none tabular-nums"
            style={{ color: isRedZone ? 'var(--accent-danger)' : 'var(--accent-primary)' }}
          >
            {Math.round(groupAttendancePercent)}%
          </p>
          <p className="mt-1 text-[10px]" style={{ color: 'var(--text-muted)' }}>
            группа
          </p>
        </div>
      </header>

      {sortedRows.length > 0 && (
        <section className="mb-3">
          <button
            type="button"
            onClick={() => setExpanded((value) => !value)}
            aria-expanded={expanded}
            aria-controls={`students-${subjectId}`}
            className="flex min-h-9 w-full items-center justify-between gap-2 rounded-lg px-2 text-sm font-semibold"
            style={{
              background: 'color-mix(in oklab, var(--text-primary) 4%, transparent)',
              color: 'var(--text-secondary)',
            }}
          >
            <span>{expanded ? 'Скрыть студентов' : `Студенты: ${sortedRows.length}`}</span>
            <CaretDown
              size={16}
              weight="bold"
              style={{
                transform: expanded ? 'rotate(180deg)' : 'rotate(0deg)',
                transition: 'transform 160ms ease-out',
              }}
            />
          </button>

          {expanded && (
            <div id={`students-${subjectId}`} className="mt-2 flex flex-col gap-1.5">
              {sortedRows.map((row) => {
                const belowThreshold = row.attendancePercent < threshold
                return (
                  <div key={row.studentId} className="flex min-h-7 items-center gap-2 text-sm">
                    <span
                      aria-hidden="true"
                      className="size-2 shrink-0 rounded-full"
                      style={{
                        background: belowThreshold ? 'var(--accent-danger)' : 'var(--accent-primary)',
                      }}
                    />
                    <span className="min-w-0 flex-1 truncate" style={{ color: 'var(--text-secondary)' }}>
                      {row.studentName}
                    </span>
                    <span
                      className="font-semibold tabular-nums"
                      style={{ color: belowThreshold ? 'var(--accent-danger)' : 'var(--text-primary)' }}
                    >
                      {Math.round(row.attendancePercent)}%
                    </span>
                  </div>
                )
              })}
            </div>
          )}
        </section>
      )}

      <footer className="flex items-center gap-2 border-t pt-3" style={{ borderColor: 'var(--border-subtle)' }}>
        <label htmlFor={`threshold-${subjectId}`} className="text-xs font-semibold" style={{ color: 'var(--text-secondary)' }}>
          Порог
        </label>
        <input
          id={`threshold-${subjectId}`}
          type="number"
          min="0"
          max="100"
          value={editValue}
          disabled={isPending}
          onChange={(event) => {
            const raw = event.target.valueAsNumber
            setEditValue(Number.isNaN(raw) ? 0 : Math.max(0, Math.min(100, raw)))
          }}
          aria-label="Порог посещаемости"
          className="h-9 w-16 rounded-lg border px-2 text-sm tabular-nums outline-none"
          style={{
            background: 'var(--bg-primary)',
            borderColor: errorText ? 'var(--accent-danger)' : 'var(--border-subtle)',
            color: 'var(--text-primary)',
          }}
        />
        <span className="text-sm" style={{ color: 'var(--text-muted)' }}>
          %
        </span>
        <button
          type="button"
          onClick={saveThreshold}
          disabled={isPending}
          aria-label="Сохранить порог"
          className={cn(
            'grid size-9 place-items-center rounded-full',
            isPending && 'opacity-60',
          )}
          style={{ color: 'var(--accent-primary)' }}
        >
          {isPending ? (
            <CircleNotch size={18} weight="bold" className="animate-spin" aria-hidden="true" />
          ) : (
            <Check size={18} weight="bold" aria-hidden="true" />
          )}
        </button>
        <AnimatePresence>
          {showSuccess && (
            <motion.span
              key="success"
              role="status"
              initial={{ opacity: 0, scale: 0.7 }}
              animate={{ opacity: 1, scale: 1 }}
              exit={{ opacity: 0, scale: 0.7 }}
              transition={{ duration: 0.18 }}
              style={{ color: 'var(--accent-primary)' }}
            >
              <Check size={16} weight="bold" aria-hidden="true" />
            </motion.span>
          )}
        </AnimatePresence>
      </footer>

      {errorText && (
        <p role="alert" className="mt-2 text-xs font-semibold" style={{ color: 'var(--accent-danger)' }}>
          {errorText}
        </p>
      )}
    </article>
  )
}

function Badge({ text }: { text: string }) {
  return (
    <span
      className="rounded-full px-2 py-0.5 text-[10px] font-semibold"
      style={{
        background: 'color-mix(in oklab, var(--text-primary) 6%, transparent)',
        color: 'var(--text-muted)',
      }}
    >
      {text}
    </span>
  )
}
