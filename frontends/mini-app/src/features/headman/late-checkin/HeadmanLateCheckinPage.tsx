import { useMemo, useState, type ReactNode } from 'react'
import { Link } from 'react-router'
import {
  ArrowLeft,
  Check,
  CircleNotch,
  ClockCountdown,
  WarningCircle,
  X,
} from '@phosphor-icons/react'
import { hapticFeedback } from '@telegram-apps/sdk-react'
import { motion } from 'motion/react'
import { useAuth } from '@/features/auth/AuthProvider'
import {
  useDecideLateCheckin,
  usePendingLateCheckinRequests,
} from '@/features/headman/shared/headmanApi'
import {
  LATE_CHECKIN_STATUS_LABELS,
  type LateCheckinRequest,
} from '@/features/headman/shared/types'
import { cn } from '@/lib/utils'

type LateCheckinTab = 'pending' | 'resolved'

const RESOLVED_STATUSES: LateCheckinRequest['status'][] = ['approved', 'rejected']

export function HeadmanLateCheckinPage() {
  const { user } = useAuth()
  const groupId = user?.groupId
  const isHeadman = user?.role === 'STUDENT' && user.isHeadman
  const headmanGroupId = isHeadman ? groupId : undefined
  const requestsQuery = usePendingLateCheckinRequests(headmanGroupId)
  const decideLateCheckin = useDecideLateCheckin()
  const [tab, setTab] = useState<LateCheckinTab>('pending')
  const [rowError, setRowError] = useState<string | null>(null)
  const [toast, setToast] = useState<string | null>(null)

  const requests = requestsQuery.data ?? []
  const pendingRequests = useMemo(
    () => requests.filter((request) => request.status === 'pending'),
    [requests],
  )
  const resolvedRequests = useMemo(
    () => requests.filter((request) => RESOLVED_STATUSES.includes(request.status)),
    [requests],
  )
  const visibleRequests = tab === 'pending' ? pendingRequests : resolvedRequests

  const showToast = (text: string) => {
    setToast(text)
    window.setTimeout(() => setToast(null), 2400)
  }

  const decide = async (request: LateCheckinRequest, approved: boolean) => {
    setRowError(null)
    try {
      await decideLateCheckin.mutateAsync({ id: request.id, approved })
      showToast(approved ? 'Запрос одобрен' : 'Запрос отклонен')
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
            background: 'color-mix(in oklab, var(--accent-primary) 12%, transparent)',
            borderColor: 'var(--border-accent)',
            color: 'var(--accent-primary)',
          }}
        >
          <ClockCountdown size={24} weight="duotone" aria-hidden="true" />
        </div>
        <div className="min-w-0">
          <p className="text-xs font-medium uppercase" style={{ color: 'var(--text-muted)' }}>
            Староста
          </p>
          <h1
            className="text-2xl font-bold leading-tight text-balance"
            style={{ fontFamily: 'var(--font-heading)' }}
          >
            Запросы отметки
          </h1>
        </div>
      </header>

      <Tabs
        tab={tab}
        onTabChange={(next) => {
          setTab(next)
          setRowError(null)
        }}
        pendingCount={pendingRequests.length}
        resolvedCount={resolvedRequests.length}
      />

      {requestsQuery.isLoading && <SkeletonList />}

      {!requestsQuery.isLoading && requestsQuery.isError && (
        <InlineError text="Не удалось загрузить запросы группы. Обновите страницу." />
      )}

      {!requestsQuery.isLoading && !requestsQuery.isError && visibleRequests.length === 0 && (
        <EmptyState tab={tab} />
      )}

      {!requestsQuery.isLoading && !requestsQuery.isError && visibleRequests.length > 0 && (
        <div className="flex flex-col gap-3">
          {visibleRequests.map((request, index) => (
            <RequestCard
              key={request.id}
              index={index}
              request={request}
              rowError={rowError}
              pending={decideLateCheckin.isPending}
              onApprove={() => void decide(request, true)}
              onReject={() => void decide(request, false)}
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
  tab: LateCheckinTab
  onTabChange: (tab: LateCheckinTab) => void
  pendingCount: number
  resolvedCount: number
}) {
  return (
    <div
      role="tablist"
      aria-label="Фильтр запросов"
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

function RequestCard({
  index,
  request,
  rowError,
  pending,
  onApprove,
  onReject,
}: {
  index: number
  request: LateCheckinRequest
  rowError: string | null
  pending: boolean
  onApprove: () => void
  onReject: () => void
}) {
  const actionable = request.status === 'pending'
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
            <StatusChip status={request.status} />
            <span className="text-xs" style={{ color: 'var(--text-muted)' }}>
              {formatDateTime(request.createdAt)}
            </span>
          </div>
          <h2
            className="truncate text-base font-semibold"
            style={{ color: 'var(--text-primary)', fontFamily: 'var(--font-heading)' }}
          >
            {request.studentName}
          </h2>
          <p className="mt-0.5 text-sm" style={{ color: 'var(--text-secondary)' }}>
            Пара #{request.lessonId}
          </p>
        </div>
      </div>

      <dl className="mt-3 grid grid-cols-2 gap-3 rounded-lg px-3 py-2 text-sm" style={{ background: 'var(--bg-surface)' }}>
        <div>
          <dt className="text-xs uppercase" style={{ color: 'var(--text-muted)' }}>
            Студент
          </dt>
          <dd className="mt-0.5 tabular-nums">{request.studentId}</dd>
        </div>
        <div>
          <dt className="text-xs uppercase" style={{ color: 'var(--text-muted)' }}>
            Обновлено
          </dt>
          <dd className="mt-0.5">{formatDateTime(request.updatedAt)}</dd>
        </div>
      </dl>

      {request.decisionAt && (
        <p className="mt-3 text-xs" style={{ color: 'var(--text-muted)' }}>
          Решение: {formatDateTime(request.decisionAt)}
        </p>
      )}

      {actionable && (
        <div className="mt-4 grid grid-cols-2 gap-2">
          <DecisionButton kind="approve" pending={pending} onClick={onApprove} />
          <DecisionButton kind="reject" pending={pending} onClick={onReject} />
        </div>
      )}

      {rowError && actionable && (
        <p className="mt-2 text-xs" style={{ color: 'var(--accent-danger)' }} role="alert">
          {rowError}
        </p>
      )}
    </motion.article>
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

function StatusChip({ status }: { status: LateCheckinRequest['status'] }) {
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
      {LATE_CHECKIN_STATUS_LABELS[status]}
    </span>
  )
}

function EmptyState({ tab }: { tab: LateCheckinTab }) {
  return (
    <div className="flex flex-col items-center justify-center py-14 text-center">
      <div
        className="mb-4 grid size-20 place-items-center rounded-full border"
        style={{
          background: 'var(--bg-secondary)',
          borderColor: 'var(--border-subtle)',
          color: tab === 'pending' ? 'var(--accent-primary)' : 'var(--text-muted)',
        }}
      >
        <ClockCountdown size={38} weight="duotone" aria-hidden="true" />
      </div>
      <h2 className="text-lg font-semibold">
        {tab === 'pending' ? 'Нет запросов на проверке' : 'Решенных запросов нет'}
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
          className="h-[128px] rounded-lg"
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
