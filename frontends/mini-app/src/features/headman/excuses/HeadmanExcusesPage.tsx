import { useMemo, useState, type ReactNode } from 'react'
import { Link } from 'react-router'
import {
  ArrowLeft,
  Check,
  CircleNotch,
  FileText,
  WarningCircle,
  X,
} from '@phosphor-icons/react'
import { hapticFeedback } from '@telegram-apps/sdk-react'
import { motion } from 'motion/react'
import { useAuth } from '@/features/auth/AuthProvider'
import {
  useDecideExcuse,
  useGroupExcuses,
} from '@/features/headman/shared/headmanApi'
import {
  EXCUSE_STATUS_LABELS,
  EXCUSE_TYPE_LABELS,
  type ExcuseTicket,
} from '@/features/headman/shared/types'
import { cn } from '@/lib/utils'

type ExcuseTab = 'pending' | 'resolved'

const RESOLVED_STATUSES: ExcuseTicket['status'][] = ['approved', 'rejected']

export function HeadmanExcusesPage() {
  const { user } = useAuth()
  const groupId = user?.groupId
  const isHeadman = user?.role === 'STUDENT' && user.isHeadman
  const headmanGroupId = isHeadman ? groupId : undefined
  const ticketsQuery = useGroupExcuses(headmanGroupId)
  const decideExcuse = useDecideExcuse()
  const [tab, setTab] = useState<ExcuseTab>('pending')
  const [rejectingId, setRejectingId] = useState<string | null>(null)
  const [rejectComment, setRejectComment] = useState('')
  const [rowError, setRowError] = useState<string | null>(null)
  const [toast, setToast] = useState<string | null>(null)

  const tickets = ticketsQuery.data ?? []
  const pendingTickets = useMemo(
    () => tickets.filter((ticket) => ticket.status === 'submitted'),
    [tickets],
  )
  const resolvedTickets = useMemo(
    () => tickets.filter((ticket) => RESOLVED_STATUSES.includes(ticket.status)),
    [tickets],
  )
  const visibleTickets = tab === 'pending' ? pendingTickets : resolvedTickets

  const showToast = (text: string) => {
    setToast(text)
    window.setTimeout(() => setToast(null), 2400)
  }

  const decide = async (ticket: ExcuseTicket, approved: boolean) => {
    const comment = rejectComment.trim()
    if (!approved && !comment) {
      setRowError('Для отклонения нужен комментарий.')
      return
    }
    setRowError(null)
    try {
      await decideExcuse.mutateAsync({
        id: ticket.id,
        approved,
        decisionComment: approved ? null : comment,
      })
      setRejectingId(null)
      setRejectComment('')
      showToast(approved ? 'Тикет одобрен' : 'Тикет отклонен')
      if (hapticFeedback.notificationOccurred.isAvailable()) {
        hapticFeedback.notificationOccurred('success')
      }
    } catch {
      setRowError('Не удалось сохранить решение. Обновите страницу и попробуйте еще раз.')
      if (hapticFeedback.notificationOccurred.isAvailable()) {
        hapticFeedback.notificationOccurred('error')
      }
    }
  }

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
          <FileText size={24} weight="duotone" aria-hidden="true" />
        </div>
        <div className="min-w-0">
          <p className="text-xs font-medium uppercase" style={{ color: 'var(--text-muted)' }}>
            Староста
          </p>
          <h1
            className="text-2xl font-bold leading-tight text-balance"
            style={{ fontFamily: 'var(--font-heading)' }}
          >
            Уважительные пропуски
          </h1>
        </div>
      </header>

      <Tabs
        tab={tab}
        onTabChange={(next) => {
          setTab(next)
          setRejectingId(null)
          setRowError(null)
        }}
        pendingCount={pendingTickets.length}
        resolvedCount={resolvedTickets.length}
      />

      {ticketsQuery.isLoading && <SkeletonList />}

      {!ticketsQuery.isLoading && ticketsQuery.isError && (
        <InlineError text="Не удалось загрузить тикеты группы. Обновите страницу." />
      )}

      {!ticketsQuery.isLoading && !ticketsQuery.isError && visibleTickets.length === 0 && (
        <EmptyState tab={tab} />
      )}

      {!ticketsQuery.isLoading && !ticketsQuery.isError && visibleTickets.length > 0 && (
        <div className="flex flex-col gap-3">
          {visibleTickets.map((ticket, index) => (
            <TicketCard
              key={ticket.id}
              index={index}
              ticket={ticket}
              rejecting={rejectingId === ticket.id}
              rejectComment={rejectComment}
              rowError={rejectingId === ticket.id ? rowError : null}
              pending={decideExcuse.isPending}
              onRejectCommentChange={(value) => {
                setRejectComment(value)
                setRowError(null)
              }}
              onApprove={() => void decide(ticket, true)}
              onStartReject={() => {
                setRejectingId(ticket.id)
                setRejectComment('')
                setRowError(null)
              }}
              onCancelReject={() => {
                setRejectingId(null)
                setRejectComment('')
                setRowError(null)
              }}
              onReject={() => void decide(ticket, false)}
            />
          ))}
        </div>
      )}

      {toast && <Toast text={toast} />}
    </PageShell>
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

function Tabs({
  tab,
  onTabChange,
  pendingCount,
  resolvedCount,
}: {
  tab: ExcuseTab
  onTabChange: (tab: ExcuseTab) => void
  pendingCount: number
  resolvedCount: number
}) {
  return (
    <div
      role="tablist"
      aria-label="Фильтр тикетов"
      className="mb-5 inline-flex rounded-full border p-1"
      style={{ background: 'var(--bg-secondary)', borderColor: 'var(--border-subtle)' }}
    >
      <TabButton
        active={tab === 'pending'}
        onClick={() => onTabChange('pending')}
        label="На проверке"
        count={pendingCount}
      />
      <TabButton
        active={tab === 'resolved'}
        onClick={() => onTabChange('resolved')}
        label="Решенные"
        count={resolvedCount}
      />
    </div>
  )
}

function TabButton({
  active,
  onClick,
  label,
  count,
}: {
  active: boolean
  onClick: () => void
  label: string
  count: number
}) {
  return (
    <button
      type="button"
      role="tab"
      aria-selected={active}
      onClick={onClick}
      className="inline-flex min-h-9 items-center gap-1.5 rounded-full px-3 text-sm font-semibold"
      style={{
        background: active ? 'var(--bg-elevated)' : 'transparent',
        color: active ? 'var(--text-primary)' : 'var(--text-secondary)',
        boxShadow: active ? 'var(--shadow-sm)' : undefined,
      }}
    >
      {label}
      <span
        className="min-w-5 rounded-full px-1.5 text-center text-[11px] tabular-nums"
        style={{
          background: active ? 'var(--accent-primary)' : 'var(--bg-primary)',
          color: active ? 'var(--accent-primary-contrast)' : 'var(--text-muted)',
        }}
      >
        {count}
      </span>
    </button>
  )
}

function TicketCard({
  index,
  ticket,
  rejecting,
  rejectComment,
  rowError,
  pending,
  onRejectCommentChange,
  onApprove,
  onStartReject,
  onCancelReject,
  onReject,
}: {
  index: number
  ticket: ExcuseTicket
  rejecting: boolean
  rejectComment: string
  rowError: string | null
  pending: boolean
  onRejectCommentChange: (value: string) => void
  onApprove: () => void
  onStartReject: () => void
  onCancelReject: () => void
  onReject: () => void
}) {
  const actionable = ticket.status === 'submitted'
  return (
    <motion.article
      initial={{ opacity: 0, y: 8 }}
      animate={{ opacity: 1, y: 0 }}
      transition={{ duration: 0.16, delay: index * 0.025 }}
      className="rounded-lg border p-4"
      style={{ background: 'var(--bg-secondary)', borderColor: 'var(--border-subtle)' }}
    >
      <div className="flex items-start justify-between gap-3">
        <div className="min-w-0">
          <div className="mb-1 flex flex-wrap items-center gap-2">
            <StatusChip status={ticket.status} />
            <span className="text-xs" style={{ color: 'var(--text-muted)' }}>
              {formatDateTime(ticket.createdAt)}
            </span>
          </div>
          <h2
            className="truncate text-base font-semibold"
            style={{ color: 'var(--text-primary)', fontFamily: 'var(--font-heading)' }}
          >
            {ticket.studentName}
          </h2>
          <p className="mt-0.5 text-sm" style={{ color: 'var(--text-secondary)' }}>
            {EXCUSE_TYPE_LABELS[ticket.excuseType]} · {ticket.lessonIds.length} пар(ы)
          </p>
        </div>
      </div>

      {ticket.comment && (
        <section className="mt-3 rounded-lg px-3 py-2" style={{ background: 'var(--bg-surface)' }}>
          <h3 className="text-xs uppercase" style={{ color: 'var(--text-muted)' }}>
            Комментарий студента
          </h3>
          <p className="mt-1 whitespace-pre-wrap text-sm" style={{ color: 'var(--text-secondary)' }}>
            {ticket.comment}
          </p>
        </section>
      )}

      <section className="mt-3">
        <h3 className="text-xs uppercase" style={{ color: 'var(--text-muted)' }}>
          Пары
        </h3>
        <ul className="mt-1 flex flex-wrap gap-1.5">
          {ticket.lessonIds.map((lessonId) => (
            <li
              key={lessonId}
              className="rounded-full border px-2 py-0.5 text-xs tabular-nums"
              style={{ borderColor: 'var(--border-default)', color: 'var(--text-secondary)' }}
            >
              #{lessonId}
            </li>
          ))}
        </ul>
      </section>

      {ticket.decisionComment && (
        <section className="mt-3 rounded-lg px-3 py-2" style={{ background: 'var(--bg-surface)' }}>
          <h3 className="text-xs uppercase" style={{ color: 'var(--text-muted)' }}>
            Комментарий решения
          </h3>
          <p className="mt-1 whitespace-pre-wrap text-sm" style={{ color: 'var(--text-secondary)' }}>
            {ticket.decisionComment}
          </p>
        </section>
      )}

      {actionable && (
        <div className="mt-4">
          {rejecting ? (
            <RejectForm
              value={rejectComment}
              error={rowError}
              pending={pending}
              onChange={onRejectCommentChange}
              onCancel={onCancelReject}
              onSubmit={onReject}
            />
          ) : (
            <div className="grid grid-cols-2 gap-2">
              <DecisionButton kind="approve" pending={pending} onClick={onApprove} />
              <DecisionButton kind="reject" pending={pending} onClick={onStartReject} />
            </div>
          )}
        </div>
      )}
    </motion.article>
  )
}

function RejectForm({
  value,
  error,
  pending,
  onChange,
  onCancel,
  onSubmit,
}: {
  value: string
  error: string | null
  pending: boolean
  onChange: (value: string) => void
  onCancel: () => void
  onSubmit: () => void
}) {
  return (
    <div className="rounded-lg border p-3" style={{ borderColor: 'var(--border-subtle)' }}>
      <label htmlFor="reject-comment" className="mb-1.5 block text-sm font-semibold">
        Комментарий к отклонению
      </label>
      <textarea
        id="reject-comment"
        value={value}
        maxLength={1000}
        onChange={(event) => onChange(event.target.value)}
        className="min-h-[84px] w-full resize-none rounded-lg border bg-transparent px-3 py-2 text-sm outline-none"
        style={{
          borderColor: 'var(--border-default)',
          color: 'var(--text-primary)',
          background: 'var(--bg-primary)',
        }}
      />
      {error && (
        <p className="mt-2 text-xs" style={{ color: 'var(--accent-danger)' }} role="alert">
          {error}
        </p>
      )}
      <div className="mt-3 grid grid-cols-2 gap-2">
        <button
          type="button"
          onClick={onCancel}
          disabled={pending}
          className="inline-flex min-h-10 items-center justify-center rounded-lg border text-sm font-semibold"
          style={{ borderColor: 'var(--border-default)', color: 'var(--text-secondary)' }}
        >
          Отмена
        </button>
        <DecisionButton kind="reject" pending={pending} onClick={onSubmit} />
      </div>
    </div>
  )
}

function DecisionButton({
  kind,
  pending,
  onClick,
}: {
  kind: 'approve' | 'reject'
  pending: boolean
  onClick: () => void
}) {
  const approve = kind === 'approve'
  return (
    <button
      type="button"
      onClick={onClick}
      disabled={pending}
      className={cn(
        'inline-flex min-h-10 items-center justify-center gap-1.5 rounded-lg text-sm font-semibold',
        pending && 'cursor-not-allowed opacity-70',
      )}
      style={{
        background: approve ? 'var(--accent-primary)' : 'var(--bg-primary)',
        color: approve ? 'var(--accent-primary-contrast)' : 'var(--accent-danger)',
        border: approve ? undefined : '1px solid color-mix(in oklab, var(--accent-danger) 35%, transparent)',
      }}
    >
      {pending ? (
        <CircleNotch size={17} weight="bold" className="animate-spin" aria-hidden="true" />
      ) : approve ? (
        <Check size={17} weight="bold" aria-hidden="true" />
      ) : (
        <X size={17} weight="bold" aria-hidden="true" />
      )}
      {approve ? 'Одобрить' : 'Отклонить'}
    </button>
  )
}

function StatusChip({ status }: { status: ExcuseTicket['status'] }) {
  const color =
    status === 'approved'
      ? 'var(--accent-info)'
      : status === 'rejected'
        ? 'var(--accent-danger)'
        : 'var(--accent-warning)'
  return (
    <span
      className="inline-flex rounded-full border px-2 py-0.5 text-[11px] font-semibold"
      style={{
        color,
        borderColor: `color-mix(in oklab, ${color} 45%, transparent)`,
        background: `color-mix(in oklab, ${color} 12%, transparent)`,
      }}
    >
      {EXCUSE_STATUS_LABELS[status]}
    </span>
  )
}

function EmptyState({ tab }: { tab: ExcuseTab }) {
  return (
    <div className="flex flex-col items-center justify-center py-14 text-center">
      <div
        className="mb-4 grid size-20 place-items-center rounded-full border"
        style={{
          background: 'var(--bg-secondary)',
          borderColor: 'var(--border-subtle)',
          color: tab === 'pending' ? 'var(--accent-secondary)' : 'var(--text-muted)',
        }}
      >
        <FileText size={38} weight="duotone" aria-hidden="true" />
      </div>
      <h2 className="text-lg font-semibold">
        {tab === 'pending' ? 'Нет тикетов на проверке' : 'Решенных тикетов нет'}
      </h2>
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
          className="h-[132px] rounded-lg"
          style={{ background: 'var(--bg-secondary)' }}
        />
      ))}
    </div>
  )
}

function Toast({ text }: { text: string }) {
  return (
    <div
      role="status"
      aria-live="polite"
      className="fixed left-1/2 z-[var(--z-toast,70)] -translate-x-1/2 rounded-full border px-4 py-2 text-sm font-medium"
      style={{
        bottom: 'calc(80px + env(safe-area-inset-bottom))',
        background: 'var(--bg-elevated)',
        borderColor: 'var(--border-default)',
        color: 'var(--text-primary)',
      }}
    >
      {text}
    </div>
  )
}

function formatDateTime(value: string | null | undefined): string {
  if (!value) return ''
  const date = new Date(value)
  if (Number.isNaN(date.getTime())) return value
  return new Intl.DateTimeFormat('ru-RU', {
    day: '2-digit',
    month: '2-digit',
    hour: '2-digit',
    minute: '2-digit',
  }).format(date)
}
