import { useState } from 'react'
import { Link } from 'react-router'
import { ArrowLeft, FileText, Check, X } from '@phosphor-icons/react'
import { useAuth } from '@/features/auth/AuthProvider'
import {
  useGroupExcuses,
  useDecideExcuse,
} from '@/features/headman/shared/headmanApi'
import {
  EXCUSE_STATUS_LABELS,
  EXCUSE_TYPE_LABELS,
  type ExcuseTicket,
} from '@/features/headman/shared/types'

/**
 * ExcusesPage — headman-side approval screen for group excuse tickets.
 *
 * Data source: `GET /api/attendance/excuses/group/{groupId}` (Phase 59).
 * Actions:   `PATCH /api/attendance/excuses/{id}/status` with {status, decisionComment}.
 *
 * Graceful degradation: 403/404 on the list endpoint → empty state.
 */
export function ExcusesPage() {
  const { user } = useAuth()
  const groupId = user?.groupId ?? 0

  const listQuery = useGroupExcuses(groupId)
  const decide = useDecideExcuse()

  const [rejectingId, setRejectingId] = useState<string | null>(null)
  const [rejectComment, setRejectComment] = useState('')
  const [validationError, setValidationError] = useState<string | null>(null)
  const [busyId, setBusyId] = useState<string | null>(null)
  const [toast, setToast] = useState<string | null>(null)

  if (!groupId) {
    return (
      <div className="min-h-screen p-6">
        <BackLink />
        <h1 className="text-lg font-semibold mb-8">Пропуски</h1>
        <EmptyState
          title="Группа не назначена"
          text="Ваш профиль не привязан к группе — функция недоступна."
        />
      </div>
    )
  }

  const tickets = listQuery.data ?? []
  const pending = tickets.filter(t => t.status === 'submitted')
  const resolved = tickets.filter(t => t.status !== 'submitted')

  const approve = async (id: string) => {
    setBusyId(id)
    try {
      await decide.mutateAsync({ id, approved: true, decisionComment: null })
      setToast('Пропуск одобрен')
    } catch {
      setToast('Не удалось одобрить пропуск')
    } finally {
      setBusyId(null)
      setTimeout(() => setToast(null), 2500)
    }
  }

  const startReject = (id: string) => {
    setRejectingId(id)
    setRejectComment('')
    setValidationError(null)
  }

  const confirmReject = async (id: string) => {
    const comment = rejectComment.trim()
    if (!comment) {
      setValidationError('Укажите причину отклонения')
      return
    }
    setBusyId(id)
    try {
      await decide.mutateAsync({ id, approved: false, decisionComment: comment })
      setRejectingId(null)
      setRejectComment('')
      setToast('Пропуск отклонён')
    } catch {
      setToast('Не удалось отклонить пропуск')
    } finally {
      setBusyId(null)
      setTimeout(() => setToast(null), 2500)
    }
  }

  return (
    <div className="min-h-screen p-6">
      <BackLink />
      <h1 className="text-lg font-semibold mb-6">Пропуски</h1>

      {listQuery.isLoading && <SkeletonList />}

      {!listQuery.isLoading && tickets.length === 0 && (
        <EmptyState
          title="Нет заявок"
          text="Заявки студентов на пропуски появятся здесь автоматически."
          icon={<FileText size={36} weight="duotone" style={{ color: 'var(--text-muted)' }} />}
        />
      )}

      {pending.length > 0 && (
        <section className="mb-6">
          <h2 className="text-sm font-semibold mb-3" style={{ color: 'var(--text-secondary)' }}>
            На рассмотрении · {pending.length}
          </h2>
          <div className="flex flex-col gap-3">
            {pending.map(ticket => (
              <TicketCard
                key={ticket.id}
                ticket={ticket}
                busy={busyId === ticket.id}
                isRejecting={rejectingId === ticket.id}
                rejectComment={rejectComment}
                validationError={validationError}
                onApprove={() => approve(ticket.id)}
                onStartReject={() => startReject(ticket.id)}
                onChangeRejectComment={setRejectComment}
                onConfirmReject={() => confirmReject(ticket.id)}
                onCancelReject={() => {
                  setRejectingId(null)
                  setRejectComment('')
                  setValidationError(null)
                }}
              />
            ))}
          </div>
        </section>
      )}

      {resolved.length > 0 && (
        <section>
          <h2 className="text-sm font-semibold mb-3" style={{ color: 'var(--text-secondary)' }}>
            Решения · {resolved.length}
          </h2>
          <div className="flex flex-col gap-3">
            {resolved.map(ticket => (
              <TicketCard
                key={ticket.id}
                ticket={ticket}
                busy={false}
                isRejecting={false}
                rejectComment=""
                validationError={null}
                onApprove={() => undefined}
                onStartReject={() => undefined}
                onChangeRejectComment={() => undefined}
                onConfirmReject={() => undefined}
                onCancelReject={() => undefined}
                resolved
              />
            ))}
          </div>
        </section>
      )}

      {toast && (
        <div
          role="status"
          aria-live="polite"
          className="fixed left-1/2 -translate-x-1/2 rounded-full px-4 py-2 text-sm"
          style={{
            bottom: 'calc(80px + env(safe-area-inset-bottom))',
            background: 'var(--bg-elevated)',
            border: '1px solid var(--border-default)',
            color: 'var(--text-primary)',
          }}
        >
          {toast}
        </div>
      )}
    </div>
  )
}

function BackLink() {
  return (
    <Link to="/group" aria-label="Назад" className="inline-flex items-center gap-2 mb-4 text-sm">
      <ArrowLeft size={20} /> Назад
    </Link>
  )
}

function SkeletonList() {
  return (
    <div className="flex flex-col gap-3">
      {Array.from({ length: 3 }).map((_, i) => (
        <div
          key={i}
          className="h-[84px] rounded-lg animate-pulse"
          style={{ background: 'var(--bg-surface)' }}
        />
      ))}
    </div>
  )
}

function EmptyState({
  title,
  text,
  icon,
}: {
  title: string
  text: string
  icon?: React.ReactNode
}) {
  return (
    <div className="flex flex-col items-center justify-center text-center py-12">
      {icon && (
        <div
          className="w-20 h-20 rounded-full flex items-center justify-center mb-4"
          style={{
            background: 'var(--bg-secondary)',
            border: '1px solid var(--border-subtle)',
          }}
        >
          {icon}
        </div>
      )}
      <h2 className="text-base font-semibold mb-2">{title}</h2>
      <p className="text-sm max-w-[280px]" style={{ color: 'var(--text-secondary)' }}>
        {text}
      </p>
    </div>
  )
}

interface TicketCardProps {
  ticket: ExcuseTicket
  busy: boolean
  isRejecting: boolean
  rejectComment: string
  validationError: string | null
  onApprove: () => void
  onStartReject: () => void
  onChangeRejectComment: (v: string) => void
  onConfirmReject: () => void
  onCancelReject: () => void
  resolved?: boolean
}

function TicketCard({
  ticket,
  busy,
  isRejecting,
  rejectComment,
  validationError,
  onApprove,
  onStartReject,
  onChangeRejectComment,
  onConfirmReject,
  onCancelReject,
  resolved,
}: TicketCardProps) {
  return (
    <article
      className="rounded-lg p-4"
      style={{
        border: '1px solid var(--border-subtle)',
        background: 'var(--bg-surface)',
        opacity: resolved ? 0.72 : 1,
      }}
    >
      <header className="flex items-start justify-between gap-3">
        <div>
          <strong className="block text-sm">{ticket.studentName}</strong>
          <span className="text-xs" style={{ color: 'var(--text-muted)' }}>
            {EXCUSE_TYPE_LABELS[ticket.excuseType]} · {ticket.lessonIds.length} урок(ов)
          </span>
        </div>
        {resolved && (
          <span
            className="text-xs font-medium px-2 py-0.5 rounded"
            style={{
              background: 'var(--bg-secondary)',
              color:
                ticket.status === 'approved'
                  ? 'var(--color-success)'
                  : 'var(--color-error)',
            }}
          >
            {EXCUSE_STATUS_LABELS[ticket.status]}
          </span>
        )}
      </header>

      {ticket.comment && (
        <p className="mt-2 text-sm" style={{ color: 'var(--text-secondary)' }}>
          {ticket.comment}
        </p>
      )}

      {resolved && ticket.decisionComment && (
        <p className="mt-2 text-sm italic" style={{ color: 'var(--text-muted)' }}>
          {ticket.decisionComment}
        </p>
      )}

      {!resolved && !isRejecting && (
        <div className="mt-3 flex gap-2">
          <button
            type="button"
            disabled={busy}
            onClick={onApprove}
            className="flex-1 inline-flex items-center justify-center gap-1 rounded-md px-3 py-2 text-sm font-semibold"
            style={{
              background: 'var(--accent-primary)',
              color: 'var(--accent-primary-contrast)',
            }}
          >
            <Check size={16} weight="bold" /> Одобрить
          </button>
          <button
            type="button"
            disabled={busy}
            onClick={onStartReject}
            className="flex-1 inline-flex items-center justify-center gap-1 rounded-md px-3 py-2 text-sm"
            style={{
              border: '1px solid var(--border-default)',
              color: 'var(--text-primary)',
            }}
          >
            <X size={16} weight="bold" /> Отклонить
          </button>
        </div>
      )}

      {isRejecting && (
        <div className="mt-3 flex flex-col gap-2">
          <input
            type="text"
            value={rejectComment}
            onChange={e => onChangeRejectComment(e.target.value)}
            placeholder="Укажите причину отклонения"
            className="w-full rounded-md px-3 py-2 text-sm"
            style={{
              border: `1px solid ${validationError ? 'var(--color-error)' : 'var(--border-default)'}`,
              background: 'var(--bg-primary)',
              color: 'var(--text-primary)',
            }}
          />
          {validationError && (
            <p className="text-xs" style={{ color: 'var(--color-error)' }}>
              {validationError}
            </p>
          )}
          <div className="flex gap-2">
            <button
              type="button"
              disabled={busy}
              onClick={onConfirmReject}
              className="flex-1 rounded-md px-3 py-2 text-sm font-semibold"
              style={{
                background: 'var(--accent-primary)',
                color: 'var(--accent-primary-contrast)',
              }}
            >
              Подтвердить отклонение
            </button>
            <button
              type="button"
              onClick={onCancelReject}
              className="flex-1 rounded-md px-3 py-2 text-sm"
              style={{
                border: '1px solid var(--border-default)',
                color: 'var(--text-primary)',
              }}
            >
              Отмена
            </button>
          </div>
        </div>
      )}
    </article>
  )
}
