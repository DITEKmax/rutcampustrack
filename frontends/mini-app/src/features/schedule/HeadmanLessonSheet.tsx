import { useEffect, useMemo, useState, type ReactNode } from 'react'
import { motion } from 'motion/react'
import {
  Broom,
  CheckCircle,
  CircleNotch,
  Lock,
  LockOpen,
  XCircle,
} from '@phosphor-icons/react'
import { hapticFeedback } from '@telegram-apps/sdk-react'
import { BottomSheet } from '@/shared/components/BottomSheet'
import { cn } from '@/lib/utils'
import type { LessonResponse } from './types'
import {
  mapLessonActionError,
  useBlockLesson,
  useCancelLesson,
  useHeadmanMarkAttendance,
  useHeadmanMarkBatch,
  useLessonAttendance,
  useUnblockLesson,
  type AttendanceSource,
  type HeadmanAttendanceStatus,
  type LessonAttendanceEntry,
} from './headmanSheetApi'

interface HeadmanLessonSheetProps {
  open: boolean
  lesson: LessonResponse | null
  subjectName: string
  onClose: () => void
  onToast: (type: 'success' | 'error', message: string) => void
  onLessonUpdated?: (lesson: LessonResponse) => void
}

type Tab = 'roster' | 'manage' | 'report'

const CANCEL_REASON_MAX_LENGTH = 500

function isSelfMarked(source: AttendanceSource | null): boolean {
  return source === 'student_geo' || source === 'late_checkin'
}

function notify(type: 'success' | 'error' | 'warning') {
  if (hapticFeedback.notificationOccurred.isAvailable()) {
    hapticFeedback.notificationOccurred(type)
  }
}

function selectionHaptic() {
  if (hapticFeedback.selectionChanged.isAvailable()) {
    hapticFeedback.selectionChanged()
  }
}

export function HeadmanLessonSheet({
  open,
  lesson,
  subjectName,
  onClose,
  onToast,
  onLessonUpdated,
}: HeadmanLessonSheetProps) {
  const [tab, setTab] = useState<Tab>('roster')
  const [pendingUserId, setPendingUserId] = useState<number | null>(null)
  const [cancelReason, setCancelReason] = useState('')
  const [cancelTouched, setCancelTouched] = useState(false)
  const attendance = useLessonAttendance(lesson?.id ?? null)
  const markMutation = useHeadmanMarkAttendance()
  const markBatchMutation = useHeadmanMarkBatch()
  const cancelMutation = useCancelLesson()
  const blockMutation = useBlockLesson()
  const unblockMutation = useUnblockLesson()

  const entries = attendance.data?.entries ?? []
  const trimmedCancelReason = cancelReason.trim()
  const cancelError = useMemo(() => {
    if (!cancelTouched) return null
    if (!trimmedCancelReason) return 'Укажите причину отмены.'
    if (trimmedCancelReason.length > CANCEL_REASON_MAX_LENGTH) {
      return `Не больше ${CANCEL_REASON_MAX_LENGTH} символов.`
    }
    return null
  }, [cancelTouched, trimmedCancelReason])

  useEffect(() => {
    setTab('roster')
    setCancelReason('')
    setCancelTouched(false)
    setPendingUserId(null)
  }, [lesson?.id])

  const summary = useMemo(() => {
    const total = entries.length
    const present = entries.filter((entry) => entry.status === 'present').length
    const absent = entries.filter((entry) => entry.status === 'absent').length
    const excused = entries.filter(
      (entry) => entry.status === 'excused' || entry.status === 'free_attendance',
    ).length
    const unmarked = entries.filter(
      (entry) => entry.status === 'absent' && !entry.source,
    ).length
    return { total, present, absent, excused, unmarked }
  }, [entries])

  const handleMark = async (
    entry: LessonAttendanceEntry,
    next: HeadmanAttendanceStatus,
  ) => {
    if (!lesson) return
    const effective = entry.status === next ? 'absent' : next
    setPendingUserId(entry.userId)
    selectionHaptic()
    try {
      await markMutation.mutateAsync({
        lessonId: lesson.id,
        userId: entry.userId,
        status: effective,
      })
    } catch (error) {
      onToast('error', mapLessonActionError(getHttpStatus(error)))
      notify('error')
    } finally {
      setPendingUserId(null)
    }
  }

  const handleBulkMark = async (next: HeadmanAttendanceStatus) => {
    if (!lesson) return
    const targets = entries.filter((entry) => entry.status !== next)
    if (targets.length === 0) return
    selectionHaptic()
    try {
      await markBatchMutation.mutateAsync({
        lessonId: lesson.id,
        items: targets.map((entry) => ({ userId: entry.userId, status: next })),
      })
      onToast('success', 'Готово.')
      notify('success')
    } catch (error) {
      onToast('error', mapLessonActionError(getHttpStatus(error)))
      notify('error')
    }
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
    selectionHaptic()
    try {
      await cancelMutation.mutateAsync({
        lessonId: lesson.id,
        reason: trimmedCancelReason,
      })
      onToast('success', 'Пара отменена.')
      notify('success')
      onClose()
    } catch (error) {
      onToast('error', mapLessonActionError(getHttpStatus(error)))
      notify('error')
    }
  }

  const handleToggleBlock = async () => {
    if (!lesson) return
    selectionHaptic()
    try {
      if (lesson.blockedByHeadman) {
        await unblockMutation.mutateAsync(lesson.id)
        onLessonUpdated?.({ ...lesson, blockedByHeadman: false })
        onToast('success', 'Блокировка снята.')
      } else {
        await blockMutation.mutateAsync(lesson.id)
        onLessonUpdated?.({ ...lesson, blockedByHeadman: true })
        onToast('success', 'Пара заблокирована.')
      }
      notify('success')
    } catch (error) {
      onToast('error', mapLessonActionError(getHttpStatus(error)))
      notify('error')
    }
  }

  const isOpen = open && !!lesson
  const isBlockBusy = blockMutation.isPending || unblockMutation.isPending
  const isBusy = markMutation.isPending || markBatchMutation.isPending || cancelMutation.isPending || isBlockBusy

  return (
    <BottomSheet
      open={isOpen}
      onClose={onClose}
      title="Действия с парой"
      heading={subjectName}
      subtitle={lesson ? `Ауд. ${lesson.room} · ${formatTime(lesson.startTime)}-${formatTime(lesson.endTime)}` : undefined}
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
          <div className="px-4 pb-5 pt-3">
            {tab === 'roster' && (
              <RosterTab
                entries={entries}
                isLoading={attendance.isLoading}
                error={attendance.isError}
                pendingUserId={pendingUserId}
                onMark={handleMark}
              />
            )}
            {tab === 'manage' && (
              <ManageTab
                onAllPresent={() => void handleBulkMark('present')}
                onClear={() => void handleBulkMark('absent')}
                onCancelLesson={() => void handleCancelLesson()}
                onToggleBlock={() => void handleToggleBlock()}
                cancelReason={cancelReason}
                cancelError={cancelError}
                cancelReasonMaxLength={CANCEL_REASON_MAX_LENGTH}
                onCancelReasonChange={(value) => {
                  setCancelReason(value)
                  if (cancelTouched) setCancelTouched(true)
                }}
                onCancelReasonBlur={() => setCancelTouched(true)}
                isBusy={isBusy}
                isCancelBusy={cancelMutation.isPending}
                canCancel={lesson.status !== 'CANCELLED'}
                isBlockedByHeadman={!!lesson.blockedByHeadman}
                isBlockBusy={isBlockBusy}
                canToggleBlock={
                  lesson.status === 'PLANNED' ||
                  lesson.status === 'ACTIVE' ||
                  !!lesson.blockedByHeadman
                }
              />
            )}
            {tab === 'report' && <ReportTab summary={summary} />}
          </div>
        </>
      )}
    </BottomSheet>
  )
}

function Tabs({ tab, onTab }: { tab: Tab; onTab: (value: Tab) => void }) {
  const items: { key: Tab; label: string }[] = [
    { key: 'roster', label: 'Список' },
    { key: 'manage', label: 'Управление' },
    { key: 'report', label: 'Отчет' },
  ]
  return (
    <div
      className="mx-4 mt-1 grid grid-cols-3 gap-1 rounded-full border p-1"
      style={{ background: 'var(--bg-secondary)', borderColor: 'var(--border-subtle)' }}
    >
      {items.map((item) => {
        const selected = tab === item.key
        return (
          <button
            key={item.key}
            type="button"
            onClick={() => onTab(item.key)}
            className="min-h-9 rounded-full px-2 text-xs font-semibold transition-colors"
            style={{
              background: selected ? 'var(--accent-primary)' : 'transparent',
              color: selected ? 'var(--accent-primary-contrast)' : 'var(--text-secondary)',
            }}
          >
            {item.label}
          </button>
        )
      })}
    </div>
  )
}

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
  onMark: (entry: LessonAttendanceEntry, status: HeadmanAttendanceStatus) => void
}) {
  if (isLoading) {
    return (
      <div className="flex flex-col gap-2 py-2" aria-label="Загрузка">
        {Array.from({ length: 6 }).map((_, index) => (
          <div
            key={index}
            className="h-12 animate-pulse rounded-lg"
            style={{ background: 'var(--bg-secondary)' }}
          />
        ))}
      </div>
    )
  }

  if (error) {
    return <InlineState tone="danger" text="Не удалось загрузить список. Попробуйте еще раз." />
  }

  if (entries.length === 0) {
    return <InlineState text="В группе пока нет студентов." />
  }

  return (
    <ul className="flex flex-col">
      {entries.map((entry, index) => (
        <li
          key={entry.userId}
          className="flex items-center gap-3 py-2"
          style={{
            borderBottom:
              index < entries.length - 1 ? '1px solid var(--border-subtle)' : 'none',
          }}
        >
          <StatusDot entry={entry} />
          <div className="min-w-0 flex-1">
            <p className="truncate text-sm font-medium" style={{ color: 'var(--text-primary)' }}>
              {entry.displayName}
            </p>
            {isSelfMarked(entry.source) && entry.status === 'present' && (
              <p className="text-[10px] uppercase" style={{ color: 'var(--accent-info)' }}>
                ст · сам отметился
              </p>
            )}
            {(entry.status === 'excused' || entry.status === 'free_attendance') &&
              entry.excuseReason && (
                <p className="truncate text-[10px]" style={{ color: 'var(--accent-warning)' }}>
                  у · {entry.excuseReason}
                </p>
              )}
          </div>
          <div className="grid grid-cols-3 gap-1.5">
            <MarkButton
              label="+"
              active={entry.status === 'present'}
              color="var(--accent-primary)"
              loading={pendingUserId === entry.userId}
              onClick={() => onMark(entry, 'present')}
            />
            <MarkButton
              label="н"
              active={entry.status === 'absent' && !!entry.source}
              color="var(--accent-danger)"
              loading={pendingUserId === entry.userId}
              onClick={() => onMark(entry, 'absent')}
            />
            <MarkButton
              label="у"
              active={entry.status === 'excused' || entry.status === 'free_attendance'}
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
  else if (entry.status === 'excused' || entry.status === 'free_attendance') {
    color = 'var(--accent-warning)'
  }
  return <span aria-hidden="true" className="size-2 shrink-0 rounded-full" style={{ background: color }} />
}

function MarkButton({
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
      whileTap={{ scale: loading ? 1 : 0.94 }}
      disabled={loading}
      onClick={onClick}
      aria-pressed={active}
      className="grid size-10 place-items-center rounded-full text-[13px] font-bold"
      style={
        active
          ? { background: color, border: `1px solid ${color}`, color: 'var(--bg-primary)' }
          : {
              background: 'transparent',
              border: '1px solid var(--border-subtle)',
              color: 'var(--text-muted)',
            }
      }
    >
      {loading ? <CircleNotch size={16} weight="bold" className="animate-spin" /> : label}
    </motion.button>
  )
}

function ManageTab({
  onAllPresent,
  onClear,
  onCancelLesson,
  onToggleBlock,
  cancelReason,
  cancelError,
  cancelReasonMaxLength,
  onCancelReasonChange,
  onCancelReasonBlur,
  isBusy,
  isCancelBusy,
  canCancel,
  isBlockedByHeadman,
  isBlockBusy,
  canToggleBlock,
}: {
  onAllPresent: () => void
  onClear: () => void
  onCancelLesson: () => void
  onToggleBlock: () => void
  cancelReason: string
  cancelError: string | null
  cancelReasonMaxLength: number
  onCancelReasonChange: (value: string) => void
  onCancelReasonBlur: () => void
  isBusy: boolean
  isCancelBusy: boolean
  canCancel: boolean
  isBlockedByHeadman: boolean
  isBlockBusy: boolean
  canToggleBlock: boolean
}) {
  const blockTitle = isBlockedByHeadman
    ? 'Разблокировать самостоятельную отметку'
    : 'Заблокировать самостоятельную отметку'
  const blockSubtitle = isBlockedByHeadman
    ? 'Студенты снова смогут отмечаться, когда пара будет активна.'
    : canToggleBlock
      ? 'Студенты увидят, что отметка заблокирована старостой.'
      : 'Доступно только для текущих и будущих пар.'

  return (
    <div className="flex flex-col gap-3">
      <ManageRow
        icon={isBlockedByHeadman ? <LockOpen size={22} weight="duotone" /> : <Lock size={22} weight="duotone" />}
        title={blockTitle}
        subtitle={blockSubtitle}
        onClick={onToggleBlock}
        disabled={isBusy || isBlockBusy || !canToggleBlock}
        tone="warning"
      />
      <ManageRow
        icon={<CheckCircle size={22} weight="duotone" />}
        title="Отметить всех присутствующими"
        subtitle="Ставит + всем, у кого выбран другой статус."
        onClick={onAllPresent}
        disabled={isBusy}
      />
      <ManageRow
        icon={<Broom size={22} weight="duotone" />}
        title="Сбросить все отметки"
        subtitle="Возвращает студентов в статус н."
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
  icon: ReactNode
  title: string
  subtitle: string
  onClick: () => void
  disabled?: boolean
  tone?: 'danger' | 'warning'
}) {
  const accent = tone === 'danger'
    ? 'var(--accent-danger)'
    : tone === 'warning'
      ? 'var(--accent-warning)'
      : 'var(--accent-primary)'
  return (
    <motion.button
      type="button"
      whileTap={{ scale: disabled ? 1 : 0.98 }}
      disabled={disabled}
      onClick={onClick}
      className={cn(
        'flex w-full items-center gap-3 rounded-lg border px-3 py-3 text-left',
        disabled && 'opacity-50',
      )}
      style={{ background: 'var(--bg-secondary)', borderColor: 'var(--border-subtle)' }}
    >
      <span
        aria-hidden="true"
        className="grid size-11 shrink-0 place-items-center rounded-lg border"
        style={{
          background: `color-mix(in oklab, ${accent} 12%, transparent)`,
          borderColor: `color-mix(in oklab, ${accent} 35%, transparent)`,
          color: accent,
        }}
      >
        {icon}
      </span>
      <span className="min-w-0 flex-1">
        <span className="block text-sm font-semibold" style={{ color: 'var(--text-primary)' }}>
          {title}
        </span>
        <span className="mt-0.5 block text-xs" style={{ color: 'var(--text-muted)' }}>
          {subtitle}
        </span>
      </span>
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
  const submitDisabled = disabled || !canCancel || !trimmed || trimmed.length > maxLength
  return (
    <form
      onSubmit={(event) => {
        event.preventDefault()
        onSubmit()
      }}
      className="rounded-lg border px-3 py-3"
      style={{
        background: 'var(--bg-secondary)',
        borderColor: 'color-mix(in oklab, var(--accent-danger) 35%, var(--border-subtle))',
      }}
    >
      <div className="flex items-start gap-3">
        <span
          aria-hidden="true"
          className="grid size-11 shrink-0 place-items-center rounded-lg border"
          style={{
            background: 'color-mix(in oklab, var(--accent-danger) 12%, transparent)',
            borderColor: 'color-mix(in oklab, var(--accent-danger) 35%, transparent)',
            color: 'var(--accent-danger)',
          }}
        >
          <XCircle size={22} weight="duotone" />
        </span>
        <span className="min-w-0 flex-1">
          <span className="block text-sm font-semibold" style={{ color: 'var(--text-primary)' }}>
            Отменить пару
          </span>
          <span className="mt-0.5 block text-xs" style={{ color: 'var(--text-muted)' }}>
            Причина сохранится в расписании.
          </span>
        </span>
      </div>

      <label
        htmlFor="headman-cancel-reason"
        className="mt-3 block text-xs font-semibold"
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
        onChange={(event) => onReasonChange(event.target.value)}
        onBlur={onReasonBlur}
        className="mt-1 min-h-20 w-full resize-none rounded-lg border px-3 py-2 text-sm outline-none disabled:opacity-50"
        style={{
          background: 'var(--bg-primary)',
          borderColor: error ? 'var(--accent-danger)' : 'var(--border-subtle)',
          color: 'var(--text-primary)',
        }}
      />
      <div className="mt-1 flex min-h-4 items-center justify-between gap-2">
        <span className="text-[11px]" style={{ color: error ? 'var(--accent-danger)' : 'transparent' }}>
          {error ?? ' '}
        </span>
        <span className="shrink-0 text-[11px] tabular-nums" style={{ color: 'var(--text-muted)' }}>
          {trimmed.length}/{maxLength}
        </span>
      </div>

      <button
        type="submit"
        disabled={submitDisabled}
        className="mt-3 inline-flex h-10 w-full items-center justify-center rounded-lg text-sm font-semibold disabled:opacity-50"
        style={{ background: 'var(--accent-danger)', color: 'var(--bg-primary)' }}
      >
        {isBusy ? 'Отменяем...' : 'Отменить пару'}
      </button>
    </form>
  )
}

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
    { label: 'Отсутствует', value: summary.absent, color: 'var(--accent-danger)' },
    { label: 'Уважительные', value: summary.excused, color: 'var(--accent-warning)' },
    { label: 'Не проставлено', value: summary.unmarked, color: 'var(--text-muted)' },
  ]
  return (
    <div className="flex flex-col gap-3">
      <div
        className="rounded-lg border p-4 text-center"
        style={{ background: 'var(--bg-secondary)', borderColor: 'var(--border-subtle)' }}
      >
        <p className="text-xs uppercase" style={{ color: 'var(--text-muted)' }}>
          Всего в группе
        </p>
        <p
          className="mt-1 text-4xl font-bold leading-none tabular-nums"
          style={{ color: 'var(--text-primary)', fontFamily: 'var(--font-heading)' }}
        >
          {summary.total}
        </p>
      </div>
      <ul className="flex flex-col gap-2">
        {rows.map((row) => (
          <li
            key={row.label}
            className="flex items-center gap-3 rounded-lg border px-3 py-3"
            style={{ background: 'var(--bg-secondary)', borderColor: 'var(--border-subtle)' }}
          >
            <span aria-hidden="true" className="size-2 shrink-0 rounded-full" style={{ background: row.color }} />
            <span className="flex-1 text-sm" style={{ color: 'var(--text-secondary)' }}>
              {row.label}
            </span>
            <span className="text-sm font-semibold tabular-nums" style={{ color: row.color }}>
              {row.value}
            </span>
          </li>
        ))}
      </ul>
    </div>
  )
}

function InlineState({ text, tone = 'muted' }: { text: string; tone?: 'muted' | 'danger' }) {
  return (
    <p
      className="py-10 text-center text-sm"
      style={{ color: tone === 'danger' ? 'var(--accent-danger)' : 'var(--text-muted)' }}
    >
      {text}
    </p>
  )
}

function formatTime(time: string): string {
  return time.slice(0, 5)
}

function getHttpStatus(error: unknown): number | undefined {
  return (error as { response?: { status?: number } })?.response?.status
}
