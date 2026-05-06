import { useEffect, useMemo, useState } from 'react'
import { motion } from 'motion/react'
import { Broom, CheckCircle, XCircle } from '@phosphor-icons/react'
import { cn } from '@/lib/utils'
import { BottomSheet } from '@/shared/components/BottomSheet'
import type { LessonResponse } from './types'
import { mapLessonActionError, useCancelLesson } from './lessonActionsApi'
import {
  useLessonAttendance,
  useHeadmanMarkAttendance,
  useHeadmanMarkBatch,
  type AttendanceSource,
  type HeadmanAttendanceStatus,
  type LessonAttendanceEntry,
} from './headmanSheetApi'

interface Props {
  open: boolean
  lesson: LessonResponse | null
  subjectName: string
  onClose: () => void
  onToast: (type: 'success' | 'error', message: string) => void
}

type Tab = 'roster' | 'manage' | 'report'

const CANCEL_REASON_MAX_LENGTH = 500

/** A student was self-marked → show "ст" badge so headman sees the origin. */
function isSelfMarked(source: AttendanceSource | null): boolean {
  return source === 'student_geo' || source === 'late_checkin'
}

function triggerHaptic() {
  try {
    // @ts-expect-error Telegram WebApp surface is optional and added at runtime.
    window.Telegram?.WebApp?.HapticFeedback?.impactOccurred?.('light')
  } catch {
    /* not in Telegram — fail silent */
  }
  if ('vibrate' in navigator) {
    try {
      navigator.vibrate?.(10)
    } catch {
      /* ignore */
    }
  }
}

export function HeadmanLessonSheet({
  open,
  lesson,
  subjectName,
  onClose,
  onToast,
}: Props) {
  const [tab, setTab] = useState<Tab>('roster')
  const attendance = useLessonAttendance(lesson?.id ?? null)
  const markMutation = useHeadmanMarkAttendance()
  const markBatchMutation = useHeadmanMarkBatch()
  const cancelMutation = useCancelLesson()
  const [pendingUserId, setPendingUserId] = useState<number | null>(null)
  const [cancelReason, setCancelReason] = useState('')
  const [cancelTouched, setCancelTouched] = useState(false)

  const entries = attendance.data?.entries ?? []
  const trimmedCancelReason = cancelReason.trim()
  const cancelError = useMemo(() => {
    if (!cancelTouched) return null
    if (!trimmedCancelReason) return 'Укажи причину отмены'
    if (trimmedCancelReason.length > CANCEL_REASON_MAX_LENGTH) {
      return `Не больше ${CANCEL_REASON_MAX_LENGTH} символов`
    }
    return null
  }, [cancelTouched, trimmedCancelReason])

  useEffect(() => {
    setCancelReason('')
    setCancelTouched(false)
  }, [lesson?.id])

  const summary = useMemo(() => {
    const total = entries.length
    const present = entries.filter((e) => e.status === 'present').length
    const absent = entries.filter((e) => e.status === 'absent').length
    const excused = entries.filter(
      (e) => e.status === 'excused' || e.status === 'free_attendance',
    ).length
    const unmarked = entries.filter(
      (e) => e.status === 'absent' && !e.source,
    ).length
    return { total, present, absent, excused, unmarked }
  }, [entries])

  const handleMark = async (
    entry: LessonAttendanceEntry,
    next: HeadmanAttendanceStatus,
  ) => {
    if (!lesson) return
    // Toggle: tapping the active status clears it (revert to absent w/o source).
    const effective = entry.status === next ? 'absent' : next
    setPendingUserId(entry.userId)
    triggerHaptic()
    try {
      await markMutation.mutateAsync({
        lessonId: lesson.id,
        userId: entry.userId,
        status: effective,
      })
    } catch (e: unknown) {
      const status = (e as { response?: { status?: number } })?.response?.status
      onToast(
        'error',
        status === 403
          ? 'Нет прав на это действие'
          : 'Не удалось сохранить',
      )
    } finally {
      setPendingUserId(null)
    }
  }

  const handleBulkMark = async (next: HeadmanAttendanceStatus) => {
    if (!lesson) return
    const targets = entries.filter((e) => e.status !== next)
    if (targets.length === 0) return
    triggerHaptic()
    try {
      // M05 P2-10/4: один POST /attendance/marks/batch вместо N serial PUT'ов.
      // Pseudo-atomic — либо все отметки сохранены, либо ни одна.
      await markBatchMutation.mutateAsync({
        lessonId: lesson.id,
        items: targets.map((e) => ({ userId: e.userId, status: next })),
      })
      onToast('success', 'Готово')
    } catch (e: unknown) {
      const status = (e as { response?: { status?: number } })?.response?.status
      onToast(
        'error',
        status === 403
          ? 'Нет прав на это действие'
          : `Не удалось сохранить ${targets.length} отметок`,
      )
    }
  }

  const handleClose = () => {
    onClose()
    setTimeout(() => setTab('roster'), 300)
  }

  const handleCancelLesson = async () => {
    if (!lesson) return
    setCancelTouched(true)
    if (
      !trimmedCancelReason ||
      trimmedCancelReason.length > CANCEL_REASON_MAX_LENGTH
    ) {
      return
    }
    triggerHaptic()
    try {
      await cancelMutation.mutateAsync({
        lessonId: lesson.id,
        reason: trimmedCancelReason,
      })
      setCancelReason('')
      setCancelTouched(false)
      onToast('success', 'Пара отменена')
      handleClose()
    } catch (e: unknown) {
      const status = (e as { response?: { status?: number } })?.response?.status
      onToast('error', mapLessonActionError(status))
    }
  }

  const isOpen = open && !!lesson
  const isManageBusy =
    markMutation.isPending || markBatchMutation.isPending || cancelMutation.isPending

  return (
    <BottomSheet
      open={isOpen}
      onClose={handleClose}
      title="Действия с парой"
      heading={lesson ? subjectName : undefined}
      subtitle={
        lesson
          ? `Ауд. ${lesson.room} · ${lesson.startTime.slice(0, 5)}–${lesson.endTime.slice(0, 5)}`
          : undefined
      }
      maxHeight="100dvh"
      className="h-dvh rounded-t-none"
      closeOnBackdrop={false}
      closeOnEscape={false}
      closeOnSwipeDown={false}
      showDragHandle={false}
    >
      {lesson && (
        <>
          <Tabs tab={tab} onTab={setTab} />

          <div className="px-[var(--space-4)] pb-[var(--space-5)] pt-[var(--space-3)]">
            {tab === 'roster' && (
              <RosterTab
                entries={entries}
                isLoading={attendance.isLoading}
                error={!!attendance.error}
                pendingUserId={pendingUserId}
                onMark={handleMark}
              />
            )}
            {tab === 'manage' && (
              <ManageTab
                onAllPresent={() => handleBulkMark('present')}
                onClear={() => handleBulkMark('absent')}
                onCancelLesson={handleCancelLesson}
                cancelReason={cancelReason}
                cancelError={cancelError}
                cancelReasonMaxLength={CANCEL_REASON_MAX_LENGTH}
                onCancelReasonChange={(value) => {
                  setCancelReason(value)
                  if (cancelTouched) setCancelTouched(true)
                }}
                onCancelReasonBlur={() => setCancelTouched(true)}
                isBusy={isManageBusy}
                isCancelBusy={cancelMutation.isPending}
                canCancel={lesson.status !== 'CANCELLED'}
              />
            )}
            {tab === 'report' && <ReportTab summary={summary} />}
          </div>
        </>
      )}
    </BottomSheet>
  )
}

// ─── Tabs bar ────────────────────────────────────────────────────────────────

function Tabs({ tab, onTab }: { tab: Tab; onTab: (v: Tab) => void }) {
  const items: { key: Tab; label: string }[] = [
    { key: 'roster', label: 'Пары' },
    { key: 'manage', label: 'Управление' },
    { key: 'report', label: 'Отчёт' },
  ]
  return (
    <div
      className="mx-[var(--space-4)] mt-[var(--space-1)] flex gap-1 rounded-full border p-1"
      style={{
        background: 'var(--bg-surface)',
        borderColor: 'var(--border-subtle)',
      }}
    >
      {items.map((it) => {
        const selected = tab === it.key
        return (
          <button
            key={it.key}
            type="button"
            onClick={() => onTab(it.key)}
            className="flex-1 rounded-full py-2 text-[var(--text-xs)] font-semibold transition-colors"
            style={{
              background: selected ? 'var(--gradient-brand)' : 'transparent',
              color: selected
                ? 'var(--accent-primary-contrast)'
                : 'var(--text-secondary)',
              boxShadow: selected ? 'var(--glow-primary)' : 'none',
            }}
          >
            {it.label}
          </button>
        )
      })}
    </div>
  )
}

// ─── Roster tab ──────────────────────────────────────────────────────────────

function RosterTab({
  entries,
  isLoading,
  error,
  pendingUserId,
  onMark,
}: {
  entries: LessonAttendanceEntry[]
  isLoading: boolean
  error: boolean
  pendingUserId: number | null
  onMark: (e: LessonAttendanceEntry, s: HeadmanAttendanceStatus) => void
}) {
  if (isLoading) {
    return (
      <div className="flex flex-col gap-[var(--space-2)] py-[var(--space-2)]">
        {Array.from({ length: 6 }).map((_, i) => (
          <div
            key={i}
            className="h-12 animate-pulse rounded-[var(--radius-md)]"
            style={{ background: 'var(--bg-surface)' }}
          />
        ))}
      </div>
    )
  }
  if (error) {
    return (
      <p
        className="py-10 text-center text-[var(--text-sm)]"
        style={{ color: 'var(--accent-danger)' }}
      >
        Не удалось загрузить список. Попробуй ещё раз.
      </p>
    )
  }
  if (entries.length === 0) {
    return (
      <p
        className="py-10 text-center text-[var(--text-sm)]"
        style={{ color: 'var(--text-muted)' }}
      >
        В группе пока нет студентов
      </p>
    )
  }
  return (
    <ul className="flex flex-col">
      {entries.map((entry, idx) => (
        <li
          key={entry.userId}
          className="flex items-center gap-[var(--space-3)] py-[var(--space-2)]"
          style={{
            borderBottom:
              idx < entries.length - 1
                ? '1px solid var(--border-subtle)'
                : 'none',
          }}
        >
          <StatusDot entry={entry} />
          <div className="min-w-0 flex-1">
            <p
              className="truncate text-[var(--text-sm)]"
              style={{ color: 'var(--text-primary)' }}
            >
              {entry.displayName}
            </p>
            {isSelfMarked(entry.source) && entry.status === 'present' && (
              <span
                className="text-[10px] uppercase tracking-wide"
                style={{ color: 'var(--accent-info)' }}
              >
                ст · сам отметился
              </span>
            )}
            {(entry.status === 'excused' || entry.status === 'free_attendance') &&
              entry.excuseReason && (
                <span
                  className="text-[10px] tracking-wide truncate block"
                  style={{ color: 'var(--accent-warning)' }}
                  title={entry.excuseReason}
                >
                  у · {entry.excuseReason}
                </span>
              )}
          </div>
          <div className="flex items-center gap-1.5">
            <MarkBtn
              label="+"
              active={entry.status === 'present'}
              color="var(--accent-primary)"
              loading={pendingUserId === entry.userId}
              onClick={() => onMark(entry, 'present')}
            />
            <MarkBtn
              label="н"
              active={entry.status === 'absent' && !!entry.source}
              color="var(--accent-danger)"
              loading={pendingUserId === entry.userId}
              onClick={() => onMark(entry, 'absent')}
            />
            <MarkBtn
              label="у"
              active={
                entry.status === 'excused' || entry.status === 'free_attendance'
              }
              color="var(--accent-warning)"
              loading={pendingUserId === entry.userId}
              onClick={() => onMark(entry, 'excused')}
            />
          </div>
        </li>
      ))}
    </ul>
  )
}

function StatusDot({ entry }: { entry: LessonAttendanceEntry }) {
  let color = 'var(--text-muted)'
  if (entry.status === 'present') color = 'var(--accent-primary)'
  else if (entry.status === 'absent' && entry.source) color = 'var(--accent-danger)'
  else if (entry.status === 'excused' || entry.status === 'free_attendance')
    color = 'var(--accent-warning)'
  return (
    <span
      aria-hidden="true"
      className="size-2 shrink-0 rounded-full"
      style={{ background: color }}
    />
  )
}

function MarkBtn({
  label,
  active,
  color,
  loading,
  onClick,
}: {
  label: string
  active: boolean
  color: string
  loading: boolean
  onClick: () => void
}) {
  return (
    <motion.button
      type="button"
      whileTap={{ scale: 0.94 }}
      animate={loading ? { boxShadow: `0 0 0 2px ${color}` } : { boxShadow: 'none' }}
      transition={{ duration: 0.3 }}
      onClick={onClick}
      aria-pressed={active}
      className="grid size-10 place-items-center rounded-full text-[13px] font-bold tabular-nums"
      style={
        active
          ? {
              background: color,
              color: 'var(--bg-primary)',
              border: `1px solid ${color}`,
            }
          : {
              background: 'transparent',
              color: 'var(--text-muted)',
              border: '1px solid var(--border-subtle)',
            }
      }
    >
      {label}
    </motion.button>
  )
}

// ─── Manage tab ──────────────────────────────────────────────────────────────

function ManageTab({
  onAllPresent,
  onClear,
  onCancelLesson,
  cancelReason,
  cancelError,
  cancelReasonMaxLength,
  onCancelReasonChange,
  onCancelReasonBlur,
  isBusy,
  isCancelBusy,
  canCancel,
}: {
  onAllPresent: () => void
  onClear: () => void
  onCancelLesson: () => void
  cancelReason: string
  cancelError: string | null
  cancelReasonMaxLength: number
  onCancelReasonChange: (value: string) => void
  onCancelReasonBlur: () => void
  isBusy: boolean
  isCancelBusy: boolean
  canCancel: boolean
}) {
  return (
    <div className="flex flex-col gap-[var(--space-3)]">
      <ManageRow
        icon={<CheckCircle size={22} weight="duotone" />}
        title="Отметить всех присутствующими"
        subtitle="Ставит «+» тем, у кого ещё не выбран статус"
        onClick={onAllPresent}
        disabled={isBusy}
      />
      <ManageRow
        icon={<Broom size={22} weight="duotone" />}
        title="Сбросить все отметки"
        subtitle="Вернуть всех в «не отмечено». Самоотметки сохранятся в истории."
        onClick={onClear}
        disabled={isBusy}
        tone="danger"
      />
      <CancelLessonBlock
        reason={cancelReason}
        error={cancelError}
        maxLength={cancelReasonMaxLength}
        onReasonChange={onCancelReasonChange}
        onReasonBlur={onCancelReasonBlur}
        onSubmit={onCancelLesson}
        disabled={isBusy}
        isBusy={isCancelBusy}
        canCancel={canCancel}
      />
    </div>
  )
}

function ManageRow({
  icon,
  title,
  subtitle,
  onClick,
  disabled,
  tone,
}: {
  icon: React.ReactNode
  title: string
  subtitle: string
  onClick: () => void
  disabled?: boolean
  tone?: 'danger'
}) {
  const accent = tone === 'danger' ? 'var(--accent-danger)' : 'var(--accent-primary)'
  return (
    <motion.button
      type="button"
      whileTap={{ scale: disabled ? 1 : 0.98 }}
      disabled={disabled}
      onClick={onClick}
      className={cn(
        'flex w-full items-center gap-[var(--space-3)] rounded-[var(--radius-md)] border',
        'px-[var(--space-4)] py-[var(--space-4)] text-left',
        disabled && 'opacity-50',
      )}
      style={{
        background: 'var(--bg-secondary)',
        borderColor: 'var(--border-subtle)',
      }}
    >
      <span
        aria-hidden="true"
        className="grid size-11 shrink-0 place-items-center rounded-[var(--radius-sm)]"
        style={{
          background: `color-mix(in oklab, ${accent} 14%, transparent)`,
          color: accent,
          border: `1px solid color-mix(in oklab, ${accent} 40%, transparent)`,
        }}
      >
        {icon}
      </span>
      <div className="min-w-0 flex-1">
        <p
          className="text-[var(--text-sm)] font-semibold"
          style={{ color: 'var(--text-primary)' }}
        >
          {title}
        </p>
        <p
          className="text-[var(--text-xs)]"
          style={{ color: 'var(--text-muted)' }}
        >
          {subtitle}
        </p>
      </div>
    </motion.button>
  )
}

function CancelLessonBlock({
  reason,
  error,
  maxLength,
  onReasonChange,
  onReasonBlur,
  onSubmit,
  disabled,
  isBusy,
  canCancel,
}: {
  reason: string
  error: string | null
  maxLength: number
  onReasonChange: (value: string) => void
  onReasonBlur: () => void
  onSubmit: () => void
  disabled: boolean
  isBusy: boolean
  canCancel: boolean
}) {
  const trimmed = reason.trim()
  const submitDisabled =
    disabled || !canCancel || !trimmed || trimmed.length > maxLength
  return (
    <form
      onSubmit={(event) => {
        event.preventDefault()
        onSubmit()
      }}
      className="rounded-[var(--radius-md)] border px-[var(--space-4)] py-[var(--space-4)]"
      style={{
        background: 'var(--bg-secondary)',
        borderColor: 'color-mix(in oklab, var(--accent-danger) 35%, var(--border-subtle))',
      }}
    >
      <div className="flex items-start gap-[var(--space-3)]">
        <span
          aria-hidden="true"
          className="grid size-11 shrink-0 place-items-center rounded-[var(--radius-sm)]"
          style={{
            background: 'color-mix(in oklab, var(--accent-danger) 14%, transparent)',
            color: 'var(--accent-danger)',
            border: '1px solid color-mix(in oklab, var(--accent-danger) 40%, transparent)',
          }}
        >
          <XCircle size={22} weight="duotone" />
        </span>
        <div className="min-w-0 flex-1">
          <p
            className="text-[var(--text-sm)] font-semibold"
            style={{ color: 'var(--text-primary)' }}
          >
            Отменить пару
          </p>
          <p
            className="text-[var(--text-xs)]"
            style={{ color: 'var(--text-muted)' }}
          >
            Болезнь, приказ, замена расписания
          </p>
        </div>
      </div>

      <label
        htmlFor="headman-cancel-reason"
        className="mt-[var(--space-3)] block text-[var(--text-xs)] font-semibold"
        style={{ color: 'var(--text-secondary)' }}
      >
        Причина отмены
      </label>
      <textarea
        id="headman-cancel-reason"
        value={reason}
        maxLength={maxLength}
        rows={3}
        disabled={disabled || !canCancel}
        aria-invalid={!!error}
        aria-describedby={
          error ? 'headman-cancel-reason-error' : 'headman-cancel-reason-count'
        }
        onChange={(event) => onReasonChange(event.target.value)}
        onBlur={onReasonBlur}
        placeholder="Например: преподаватель заболел"
        className={cn(
          'mt-1 min-h-20 w-full resize-none rounded-[var(--radius-md)] border',
          'px-[var(--space-3)] py-[var(--space-2)] text-[var(--text-sm)] outline-none',
          'transition-colors disabled:opacity-50',
        )}
        style={{
          background: 'var(--bg-primary)',
          borderColor: error ? 'var(--accent-danger)' : 'var(--border-subtle)',
          color: 'var(--text-primary)',
        }}
      />
      <div className="mt-1 flex min-h-4 items-center justify-between gap-2">
        <span
          id="headman-cancel-reason-error"
          className="text-[11px]"
          style={{ color: error ? 'var(--accent-danger)' : 'transparent' }}
        >
          {error ?? ' '}
        </span>
        <span
          id="headman-cancel-reason-count"
          className="shrink-0 text-[11px] tabular-nums"
          style={{ color: 'var(--text-muted)' }}
        >
          {trimmed.length}/{maxLength}
        </span>
      </div>

      <motion.button
        type="submit"
        whileTap={{ scale: submitDisabled ? 1 : 0.98 }}
        disabled={submitDisabled}
        className="mt-[var(--space-3)] inline-flex h-10 w-full items-center justify-center rounded-[var(--radius-md)] text-[var(--text-sm)] font-semibold disabled:opacity-50"
        style={{
          background: 'var(--accent-danger)',
          color: 'var(--bg-primary)',
        }}
      >
        {isBusy ? 'Отменяем...' : 'Отменить пару'}
      </motion.button>
    </form>
  )
}

// ─── Report tab ──────────────────────────────────────────────────────────────

function ReportTab({
  summary,
}: {
  summary: {
    total: number
    present: number
    absent: number
    excused: number
    unmarked: number
  }
}) {
  const rows: { label: string; value: number; color: string }[] = [
    { label: 'Присутствует', value: summary.present, color: 'var(--accent-primary)' },
    { label: 'Отсутствует (Н)', value: summary.absent, color: 'var(--accent-danger)' },
    { label: 'Уважительные', value: summary.excused, color: 'var(--accent-warning)' },
    { label: 'Не проставлено', value: summary.unmarked, color: 'var(--text-muted)' },
  ]
  return (
    <div className="flex flex-col gap-[var(--space-3)]">
      <div
        className="rounded-[var(--radius-md)] border p-[var(--space-4)] text-center"
        style={{
          background: 'var(--bg-secondary)',
          borderColor: 'var(--border-subtle)',
        }}
      >
        <p
          className="text-[var(--text-xs)] uppercase tracking-wide"
          style={{ color: 'var(--text-muted)' }}
        >
          Всего в группе
        </p>
        <p
          className="mt-1 leading-none tabular-nums"
          style={{
            color: 'var(--text-primary)',
            fontFamily: 'var(--font-display)',
            fontSize: 'var(--text-4xl)',
          }}
        >
          {summary.total}
        </p>
      </div>
      <ul className="flex flex-col gap-[var(--space-2)]">
        {rows.map((r) => (
          <li
            key={r.label}
            className="flex items-center gap-[var(--space-3)] rounded-[var(--radius-md)] border px-[var(--space-4)] py-[var(--space-3)]"
            style={{
              background: 'var(--bg-secondary)',
              borderColor: 'var(--border-subtle)',
            }}
          >
            <span
              aria-hidden="true"
              className="size-2 shrink-0 rounded-full"
              style={{ background: r.color }}
            />
            <span
              className="flex-1 text-[var(--text-sm)]"
              style={{ color: 'var(--text-secondary)' }}
            >
              {r.label}
            </span>
            <span
              className="tabular-nums text-[var(--text-sm)] font-semibold"
              style={{ color: r.color }}
            >
              {r.value}
            </span>
          </li>
        ))}
      </ul>
    </div>
  )
}
