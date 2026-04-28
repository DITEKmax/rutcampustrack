import { useState } from 'react'
import { Link } from 'react-router'
import { ArrowLeft, Clock, Check, X } from '@phosphor-icons/react'
import { useAuth } from '@/features/auth/AuthProvider'
import {
  usePendingLateCheckinRequests,
  useDecideLateCheckin,
} from '@/features/headman/shared/headmanApi'
import type { LateCheckinRequest } from '@/features/headman/shared/types'

/**
 * LateCheckinPage — headman-side approval screen for student late-checkin requests.
 *
 * Data source: `GET /api/attendance/late-checkin/pending` (backend Phase 61 — web channel).
 * Actions:     `POST /api/attendance/late-checkin/{id}/decision` with { approved }.
 *
 * The Telegram-bot inline-button flow continues to work in parallel — backend's
 * `applyDecision` is idempotent so a race between the two channels resolves to
 * the first decision winning and the second becoming a no-op.
 */
export function LateCheckinPage() {
  const { user } = useAuth()
  const groupId = user?.groupId ?? 0

  const listQuery = usePendingLateCheckinRequests(groupId)
  const decide = useDecideLateCheckin()

  const [busyId, setBusyId] = useState<string | null>(null)
  const [toast, setToast] = useState<string | null>(null)

  if (!groupId) {
    return (
      <div className="min-h-screen p-6">
        <BackLink />
        <h1 className="text-lg font-semibold mb-8">Запросы отметки</h1>
        <EmptyState
          title="Группа не назначена"
          text="Ваш профиль не привязан к группе — функция недоступна."
        />
      </div>
    )
  }

  const requests = listQuery.data ?? []

  const act = async (requestId: string, approved: boolean) => {
    setBusyId(requestId)
    try {
      await decide.mutateAsync({ requestId, approved })
      setToast(approved ? 'Запрос подтверждён' : 'Запрос отклонён')
    } catch {
      setToast('Не удалось отправить решение')
    } finally {
      setBusyId(null)
      setTimeout(() => setToast(null), 2500)
    }
  }

  return (
    <div className="min-h-screen p-6">
      <BackLink />
      <h1 className="text-lg font-semibold mb-6">Запросы отметки</h1>

      {listQuery.isLoading && <SkeletonList />}

      {!listQuery.isLoading && requests.length === 0 && (
        <EmptyState
          title="Нет активных запросов"
          text="Запросы студентов на подтверждение присутствия появятся здесь автоматически."
          icon={<Clock size={36} weight="duotone" style={{ color: 'var(--text-muted)' }} />}
        />
      )}

      {requests.length > 0 && (
        <section>
          <h2 className="text-sm font-semibold mb-3" style={{ color: 'var(--text-secondary)' }}>
            Заявки · {requests.length}
          </h2>
          <div className="flex flex-col gap-3">
            {requests.map(req => (
              <RequestCard
                key={req.id}
                request={req}
                busy={busyId === req.id}
                onApprove={() => act(req.id, true)}
                onReject={() => act(req.id, false)}
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
          className="h-[80px] rounded-lg animate-pulse"
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

function RequestCard({
  request,
  busy,
  onApprove,
  onReject,
}: {
  request: LateCheckinRequest
  busy: boolean
  onApprove: () => void
  onReject: () => void
}) {
  const created = new Date(request.createdAt)
  const timeLabel = Number.isFinite(created.getTime())
    ? created.toLocaleString('ru-RU', {
        day: '2-digit',
        month: '2-digit',
        year: 'numeric',
        hour: '2-digit',
        minute: '2-digit',
      })
    : '—'
  return (
    <article
      className="rounded-lg p-4"
      style={{
        border: '1px solid var(--border-subtle)',
        background: 'var(--bg-surface)',
      }}
    >
      <header className="flex items-start justify-between gap-3">
        <div>
          <strong className="block text-sm">{request.studentName}</strong>
          <span className="text-xs" style={{ color: 'var(--text-muted)' }}>
            Пара #{request.lessonId}
          </span>
        </div>
        <span className="text-xs font-mono" style={{ color: 'var(--text-muted)' }}>
          {timeLabel}
        </span>
      </header>
      {request.status === 'pending' ? (
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
            <Check size={16} weight="bold" /> Подтвердить
          </button>
          <button
            type="button"
            disabled={busy}
            onClick={onReject}
            className="flex-1 inline-flex items-center justify-center gap-1 rounded-md px-3 py-2 text-sm"
            style={{
              border: '1px solid var(--border-default)',
              color: 'var(--text-primary)',
            }}
          >
            <X size={16} weight="bold" /> Отклонить
          </button>
        </div>
      ) : (
        <div className="mt-3 flex items-center justify-between gap-2">
          <span
            className="text-xs font-medium px-2 py-1 rounded"
            style={{
              background: 'var(--bg-secondary)',
              color: request.status === 'approved' ? 'var(--color-success)' : 'var(--color-error)',
            }}
          >
            {statusLabel(request.status)}
          </span>
          <span className="text-xs font-mono" style={{ color: 'var(--text-muted)' }}>
            {formatDate(request.decisionAt ?? request.updatedAt)}
          </span>
        </div>
      )}
    </article>
  )
}

function statusLabel(status: LateCheckinRequest['status']) {
  if (status === 'approved') return 'Одобрено'
  if (status === 'rejected') return 'Отклонено'
  return 'На рассмотрении'
}

function formatDate(value: string | null | undefined) {
  if (!value) return '—'
  const date = new Date(value)
  if (!Number.isFinite(date.getTime())) return '—'
  return date.toLocaleString('ru-RU', {
    day: '2-digit',
    month: '2-digit',
    year: 'numeric',
    hour: '2-digit',
    minute: '2-digit',
  })
}
