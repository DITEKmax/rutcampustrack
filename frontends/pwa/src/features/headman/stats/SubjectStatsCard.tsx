import { useState, useEffect } from 'react'
import { AnimatePresence, motion } from 'motion/react'
import { Check } from '@phosphor-icons/react'
import { useSetSubjectThreshold, mapHeadmanApiError } from '@/features/headman/shared/headmanApi'

export interface SubjectStudentRow {
  studentId: number
  studentName: string
  attendancePercent: number
}

export interface SubjectStatsCardProps {
  subjectId: number
  groupId: number
  subjectName: string
  groupAttendancePercent: number
  threshold: number
  studentRows: SubjectStudentRow[]
}

const MAX_VISIBLE_ROWS = 10

/**
 * SubjectStatsCard — per-subject card with group average, per-student rows, and
 * inline threshold editor (UI-SPEC §Component Inventory #9).
 *
 * Red-zone logic: if `groupAttendancePercent < threshold`, card gets a danger
 * left-border + warning badge; per-student red dots appear when the student
 * percent is below threshold.
 *
 * Threshold editor:
 *   input (0..100) → Check button → useSetSubjectThreshold.mutate({ subjectId, minPercentage })
 *   On success: brief fade-in check animation.
 *   On error: inline red text via mapHeadmanApiError.
 */
export function SubjectStatsCard(props: SubjectStatsCardProps) {
  const { subjectId, subjectName, groupAttendancePercent, threshold, studentRows } = props

  const [editValue, setEditValue] = useState<number>(threshold)
  const [showSuccess, setShowSuccess] = useState(false)
  const { mutate, isPending, isError, error, isSuccess, reset } = useSetSubjectThreshold()

  // Keep local state in sync when threshold prop changes from parent (e.g. after cache refetch)
  useEffect(() => {
    setEditValue(threshold)
  }, [threshold])

  // Brief success animation, then hide
  useEffect(() => {
    if (isSuccess) {
      setShowSuccess(true)
      const t = setTimeout(() => {
        setShowSuccess(false)
        reset()
      }, 1000)
      return () => clearTimeout(t)
    }
  }, [isSuccess, reset])

  const isRedZone = groupAttendancePercent < threshold
  const visibleRows = studentRows.slice(0, MAX_VISIBLE_ROWS)
  const extraCount = Math.max(0, studentRows.length - MAX_VISIBLE_ROWS)

  function handleSave() {
    const clamped = Math.max(0, Math.min(100, Math.round(editValue)))
    mutate({ subjectId, minPercentage: clamped })
  }

  function handleInputChange(e: React.ChangeEvent<HTMLInputElement>) {
    const raw = e.target.valueAsNumber
    if (Number.isNaN(raw)) {
      setEditValue(0)
    } else {
      setEditValue(Math.max(0, Math.min(100, raw)))
    }
  }

  const errorStatus =
    (error as { response?: { status?: number } } | null)?.response?.status ?? 500
  const errorText = isError ? mapHeadmanApiError(errorStatus) : null

  return (
    <div
      className="rounded-lg p-4 mb-4"
      style={{
        background: 'var(--bg-secondary)',
        borderLeft: isRedZone ? '4px solid var(--accent-danger)' : undefined,
      }}
      data-testid={`subject-stats-card-${subjectId}`}
    >
      {/* Section 1 — subject header */}
      <div className="flex items-center gap-2 mb-3">
        <h2 className="text-base font-semibold flex-1">{subjectName}</h2>
        {isRedZone && (
          <>
            <span
              aria-hidden="true"
              className="inline-block w-2 h-2 rounded-full"
              style={{ background: 'var(--accent-danger)' }}
            />
            <span
              className="text-xs font-semibold px-2 py-0.5 rounded"
              style={{ background: 'rgba(239,68,68,0.12)', color: 'var(--accent-danger)' }}
            >
              Низкая посещаемость
            </span>
          </>
        )}
      </div>

      {/* Section 2 — group metric */}
      <div className="mb-4">
        <div
          className="text-lg font-semibold"
          style={{
            color: isRedZone ? 'var(--accent-danger)' : 'var(--accent-primary)',
          }}
        >
          {Math.round(groupAttendancePercent)}%
        </div>
        <div
          className="text-sm font-semibold"
          style={{ color: 'var(--text-secondary)' }}
        >
          Средняя посещаемость группы
        </div>
      </div>

      {/* Section 3 — per-student rows */}
      {visibleRows.length > 0 && (
        <div className="mb-4 flex flex-col gap-1.5">
          {visibleRows.map((row) => {
            const belowThreshold = row.attendancePercent < threshold
            return (
              <div
                key={row.studentId}
                className="flex items-center gap-2 text-sm"
              >
                {belowThreshold && (
                  <span
                    aria-hidden="true"
                    className="inline-block w-2 h-2 rounded-full flex-shrink-0"
                    style={{ background: 'var(--accent-danger)' }}
                  />
                )}
                <span className="flex-1 truncate">{row.studentName}</span>
                <span className="font-semibold tabular-nums">
                  {Math.round(row.attendancePercent)}%
                </span>
              </div>
            )
          })}
          {extraCount > 0 && (
            <p
              className="text-xs mt-1"
              style={{ color: 'var(--text-muted)' }}
            >
              Ещё {extraCount} студентов — смотрите в веб-панели
            </p>
          )}
        </div>
      )}

      {/* Section 4 — threshold editor */}
      <div className="flex items-center gap-2 pt-2 border-t" style={{ borderColor: 'var(--border-subtle)' }}>
        <label
          htmlFor={`threshold-${subjectId}`}
          className="text-sm font-semibold"
        >
          Порог:
        </label>
        <input
          id={`threshold-${subjectId}`}
          type="number"
          min="0"
          max="100"
          value={editValue}
          disabled={isPending}
          onChange={handleInputChange}
          className="w-16 text-sm px-2 py-1 rounded border tabular-nums"
          style={{
            background: 'var(--bg-surface)',
            borderColor: isError ? 'var(--accent-danger)' : 'var(--border-subtle)',
            color: 'var(--text-primary)',
          }}
          aria-label="Порог посещаемости"
        />
        <span className="text-sm" style={{ color: 'var(--text-muted)' }}>
          %
        </span>
        <button
          type="button"
          onClick={handleSave}
          disabled={isPending}
          aria-label="Сохранить порог"
          className="w-6 h-6 rounded flex items-center justify-center"
          style={{
            background: 'transparent',
            color: 'var(--accent-primary)',
            opacity: isPending ? 0.4 : 1,
          }}
        >
          <Check size={16} weight="bold" />
        </button>
        <AnimatePresence>
          {showSuccess && (
            <motion.span
              key="success"
              role="status"
              initial={{ opacity: 0, scale: 0.6 }}
              animate={{ opacity: 1, scale: 1 }}
              exit={{ opacity: 0, scale: 0.6 }}
              transition={{ duration: 0.3 }}
              className="inline-flex items-center"
              style={{ color: 'var(--accent-primary)' }}
            >
              <Check size={16} weight="bold" />
            </motion.span>
          )}
        </AnimatePresence>
      </div>
      {errorText && (
        <p
          role="alert"
          className="text-sm font-semibold mt-2"
          style={{ color: 'var(--accent-danger)' }}
        >
          {errorText}
        </p>
      )}
    </div>
  )
}
