import { useMemo, useState, type ReactNode } from 'react'
import { Link } from 'react-router'
import {
  ArrowLeft,
  CheckSquare,
  CircleNotch,
  Plus,
  Square,
  Trash,
  UserCircle,
  WarningCircle,
  X,
} from '@phosphor-icons/react'
import { hapticFeedback } from '@telegram-apps/sdk-react'
import { motion } from 'motion/react'
import { useAuth } from '@/features/auth/AuthProvider'
import {
  mapHeadmanApiError,
  useAssistants,
  useCreateAssistant,
  useDeleteAssistant,
  useGroupMembers,
} from '@/features/headman/shared/headmanApi'
import {
  ASSISTANT_PERMISSION_LABELS,
  ASSISTANT_PERMISSION_OPTIONS,
  type Assistant,
  type AssistantPermission,
  type GroupMember,
} from '@/features/headman/shared/types'
import { cn } from '@/lib/utils'

export function HeadmanStudentsPage() {
  const { user } = useAuth()
  const groupId = user?.groupId
  const isHeadman = user?.role === 'STUDENT' && user.isHeadman
  const headmanGroupId = isHeadman ? groupId : undefined
  const membersQuery = useGroupMembers(isHeadman)
  const assistantsQuery = useAssistants(headmanGroupId)
  const deleteAssistant = useDeleteAssistant()
  const [addOpen, setAddOpen] = useState(false)
  const [deleteTarget, setDeleteTarget] = useState<Assistant | null>(null)
  const [toast, setToast] = useState<string | null>(null)
  const [deleteError, setDeleteError] = useState<string | null>(null)

  const members = membersQuery.data ?? []
  const assistants = assistantsQuery.data ?? []
  const assistantIds = useMemo(
    () => new Set(assistants.filter((assistant) => assistant.active).map((assistant) => assistant.studentId)),
    [assistants],
  )
  const candidates = useMemo(
    () => members.filter((member) => !member.isHeadman && !assistantIds.has(member.id)),
    [assistantIds, members],
  )

  const showToast = (text: string) => {
    setToast(text)
    window.setTimeout(() => setToast(null), 2400)
  }

  const confirmDelete = async () => {
    if (!deleteTarget) return
    setDeleteError(null)
    try {
      await deleteAssistant.mutateAsync(deleteTarget.id)
      setDeleteTarget(null)
      showToast('Помощник отозван')
      if (hapticFeedback.notificationOccurred.isAvailable()) {
        hapticFeedback.notificationOccurred('success')
      }
    } catch (error) {
      setDeleteError(mapHeadmanApiError(getHttpStatus(error)))
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

      <header className="mb-5 flex items-start justify-between gap-3">
        <div className="flex min-w-0 items-start gap-3">
          <div
            className="grid size-11 shrink-0 place-items-center rounded-lg border"
            style={{
              background: 'color-mix(in oklab, var(--accent-primary) 12%, transparent)',
              borderColor: 'var(--border-accent)',
              color: 'var(--accent-primary)',
            }}
          >
            <UserCircle size={25} weight="duotone" aria-hidden="true" />
          </div>
          <div className="min-w-0">
            <p className="text-xs font-medium uppercase" style={{ color: 'var(--text-muted)' }}>
              Староста
            </p>
            <h1
              className="text-2xl font-bold leading-tight text-balance"
              style={{ fontFamily: 'var(--font-heading)' }}
            >
              Студенты
            </h1>
          </div>
        </div>

        <button
          type="button"
          onClick={() => setAddOpen(true)}
          aria-label="Добавить помощника"
          className="inline-flex size-10 shrink-0 items-center justify-center rounded-full"
          style={{
            background: 'var(--accent-primary)',
            color: 'var(--accent-primary-contrast)',
            boxShadow: 'var(--glow-primary)',
          }}
        >
          <Plus size={22} weight="bold" aria-hidden="true" />
        </button>
      </header>

      {(membersQuery.isLoading || assistantsQuery.isLoading) && <SkeletonList />}

      {!membersQuery.isLoading && membersQuery.isError && (
        <InlineError text="Не удалось загрузить список группы. Обновите страницу." />
      )}

      {!membersQuery.isLoading && !membersQuery.isError && assistantsQuery.isError && (
        <InlineError text="Не удалось загрузить помощников. Список студентов доступен." />
      )}

      {!membersQuery.isLoading && !membersQuery.isError && (
        <div className="flex flex-col gap-5">
          <section>
            <SectionTitle title="Группа" count={members.length} />
            {members.length === 0 ? (
              <EmptyBox text="В группе пока нет студентов." />
            ) : (
              <div className="overflow-hidden rounded-lg border" style={{ borderColor: 'var(--border-subtle)', background: 'var(--bg-secondary)' }}>
                {members.map((member) => (
                  <MemberRow
                    key={member.id}
                    member={member}
                    isAssistant={assistantIds.has(member.id) || member.isAssistant}
                  />
                ))}
              </div>
            )}
          </section>

          <section>
            <SectionTitle title="Помощники старосты" count={assistants.length} />
            {assistants.length === 0 ? (
              <EmptyBox text="Помощники еще не назначены." />
            ) : (
              <div className="flex flex-col gap-3">
                {assistants.map((assistant) => (
                  <AssistantCard
                    key={assistant.id}
                    assistant={assistant}
                    onDelete={() => {
                      setDeleteError(null)
                      setDeleteTarget(assistant)
                    }}
                  />
                ))}
              </div>
            )}
          </section>
        </div>
      )}

      <AddAssistantSheet
        open={addOpen}
        groupId={groupId}
        candidates={candidates}
        onClose={() => setAddOpen(false)}
        onCreated={() => showToast('Помощник назначен')}
      />

      {deleteTarget && (
        <DeleteAssistantDialog
          assistant={deleteTarget}
          pending={deleteAssistant.isPending}
          error={deleteError}
          onCancel={() => {
            setDeleteTarget(null)
            setDeleteError(null)
          }}
          onConfirm={() => void confirmDelete()}
        />
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

function SectionTitle({ title, count }: { title: string; count: number }) {
  return (
    <div className="mb-2 flex items-baseline gap-2 px-1">
      <h2
        className="text-sm font-semibold"
        style={{ color: 'var(--text-primary)', fontFamily: 'var(--font-heading)' }}
      >
        {title}
      </h2>
      <span className="text-xs tabular-nums" style={{ color: 'var(--text-muted)' }}>
        {count}
      </span>
    </div>
  )
}

function MemberRow({
  member,
  isAssistant,
}: {
  member: GroupMember
  isAssistant: boolean
}) {
  return (
    <article className="flex min-h-[64px] items-center gap-3 border-b px-3 py-3 last:border-b-0" style={{ borderColor: 'var(--border-subtle)' }}>
      <Avatar name={member.fullName} />
      <div className="min-w-0 flex-1">
        <p className="truncate text-sm font-semibold" style={{ color: 'var(--text-primary)' }}>
          {member.fullName}
        </p>
        <p className="mt-0.5 truncate text-xs" style={{ color: 'var(--text-muted)' }}>
          {member.login}
        </p>
      </div>
      <div className="flex shrink-0 flex-wrap justify-end gap-1">
        {member.isHeadman && <Badge text="Староста" tone="primary" />}
        {isAssistant && <Badge text="Помощник" tone="info" />}
      </div>
    </article>
  )
}

function AssistantCard({
  assistant,
  onDelete,
}: {
  assistant: Assistant
  onDelete: () => void
}) {
  return (
    <article className="rounded-lg border p-3" style={{ background: 'var(--bg-secondary)', borderColor: 'var(--border-subtle)' }}>
      <div className="flex items-start gap-3">
        <Avatar name={assistant.fullName} />
        <div className="min-w-0 flex-1">
          <p className="truncate text-sm font-semibold" style={{ color: 'var(--text-primary)' }}>
            {assistant.fullName}
          </p>
          {assistant.login && (
            <p className="mt-0.5 truncate text-xs" style={{ color: 'var(--text-muted)' }}>
              {assistant.login}
            </p>
          )}
        </div>
        <button
          type="button"
          onClick={onDelete}
          aria-label="Отозвать помощника"
          className="grid size-9 shrink-0 place-items-center rounded-full"
          style={{ color: 'var(--accent-danger)', background: 'var(--bg-primary)' }}
        >
          <Trash size={18} weight="bold" aria-hidden="true" />
        </button>
      </div>
      <div className="mt-3 flex flex-wrap gap-1.5">
        {assistant.permissions.map((permission) => (
          <span
            key={permission}
            className="rounded-full border px-2 py-0.5 text-[11px] font-semibold"
            style={{
              borderColor: 'var(--border-default)',
              color: 'var(--text-secondary)',
              background: 'var(--bg-surface)',
            }}
          >
            {ASSISTANT_PERMISSION_LABELS[permission]}
          </span>
        ))}
      </div>
    </article>
  )
}

function AddAssistantSheet({
  open,
  groupId,
  candidates,
  onClose,
  onCreated,
}: {
  open: boolean
  groupId: number
  candidates: GroupMember[]
  onClose: () => void
  onCreated: () => void
}) {
  const createAssistant = useCreateAssistant()
  const [studentId, setStudentId] = useState<number | ''>('')
  const [permissions, setPermissions] = useState<AssistantPermission[]>(['MARK_ATTENDANCE'])
  const [error, setError] = useState<string | null>(null)

  const close = () => {
    setStudentId('')
    setPermissions(['MARK_ATTENDANCE'])
    setError(null)
    onClose()
  }

  const togglePermission = (permission: AssistantPermission) => {
    setPermissions((current) => {
      if (current.includes(permission)) {
        return current.filter((item) => item !== permission)
      }
      return [...current, permission]
    })
    setError(null)
  }

  const submit = async () => {
    if (!studentId) {
      setError('Выберите студента.')
      return
    }
    if (permissions.length === 0) {
      setError('Выберите хотя бы одно право.')
      return
    }
    try {
      await createAssistant.mutateAsync({ studentId: Number(studentId), groupId, permissions })
      onCreated()
      close()
      if (hapticFeedback.notificationOccurred.isAvailable()) {
        hapticFeedback.notificationOccurred('success')
      }
    } catch (err) {
      setError(mapHeadmanApiError(getHttpStatus(err)))
      if (hapticFeedback.notificationOccurred.isAvailable()) {
        hapticFeedback.notificationOccurred('error')
      }
    }
  }

  if (!open) return null

  return (
    <div className="fixed inset-0 z-[var(--z-modal,60)] flex items-end bg-black/45" role="dialog" aria-modal="true" aria-label="Добавить помощника">
      <motion.div
        initial={{ y: '100%' }}
        animate={{ y: 0 }}
        exit={{ y: '100%' }}
        transition={{ type: 'spring', stiffness: 520, damping: 42 }}
        className="max-h-[88dvh] w-full overflow-y-auto rounded-t-2xl border-t px-4 pb-[calc(18px+env(safe-area-inset-bottom))] pt-4"
        style={{ background: 'var(--bg-secondary)', borderColor: 'var(--border-subtle)' }}
      >
        <div className="mb-4 flex items-center justify-between gap-3">
          <h2 className="text-base font-semibold" style={{ fontFamily: 'var(--font-heading)' }}>
            Добавить помощника
          </h2>
          <button
            type="button"
            onClick={close}
            aria-label="Закрыть"
            className="grid size-9 place-items-center rounded-full"
            style={{ color: 'var(--text-secondary)' }}
          >
            <X size={20} weight="bold" aria-hidden="true" />
          </button>
        </div>

        <label className="mb-1.5 block text-sm font-semibold" htmlFor="assistant-student">
          Студент
        </label>
        <select
          id="assistant-student"
          value={studentId}
          onChange={(event) => {
            setStudentId(event.target.value ? Number(event.target.value) : '')
            setError(null)
          }}
          className="h-11 w-full rounded-lg border bg-transparent px-3 text-sm outline-none"
          style={{
            borderColor: 'var(--border-default)',
            color: 'var(--text-primary)',
            background: 'var(--bg-primary)',
          }}
        >
          <option value="">Выберите студента</option>
          {candidates.map((member) => (
            <option key={member.id} value={member.id}>
              {member.fullName}
            </option>
          ))}
        </select>

        <section className="mt-4">
          <h3 className="mb-2 text-sm font-semibold">Права</h3>
          <div className="flex flex-col gap-2">
            {ASSISTANT_PERMISSION_OPTIONS.map((permission) => (
              <button
                key={permission}
                type="button"
                onClick={() => togglePermission(permission)}
                aria-pressed={permissions.includes(permission)}
                className="flex min-h-11 items-center gap-3 rounded-lg border px-3 text-left"
                style={{
                  borderColor: permissions.includes(permission)
                    ? 'var(--border-accent)'
                    : 'var(--border-subtle)',
                  background: permissions.includes(permission)
                    ? 'color-mix(in oklab, var(--accent-primary) 10%, transparent)'
                    : 'var(--bg-primary)',
                }}
              >
                {permissions.includes(permission) ? (
                  <CheckSquare size={21} weight="fill" aria-hidden="true" style={{ color: 'var(--accent-primary)' }} />
                ) : (
                  <Square size={21} weight="regular" aria-hidden="true" style={{ color: 'var(--text-muted)' }} />
                )}
                <span className="text-sm font-medium">{ASSISTANT_PERMISSION_LABELS[permission]}</span>
              </button>
            ))}
          </div>
        </section>

        {error && (
          <p className="mt-3 text-sm" style={{ color: 'var(--accent-danger)' }} role="alert">
            {error}
          </p>
        )}

        <button
          type="button"
          onClick={() => void submit()}
          disabled={createAssistant.isPending}
          className={cn(
            'mt-5 inline-flex min-h-11 w-full items-center justify-center gap-2 rounded-lg text-sm font-semibold',
            createAssistant.isPending && 'cursor-not-allowed opacity-70',
          )}
          style={{ background: 'var(--accent-primary)', color: 'var(--accent-primary-contrast)' }}
        >
          {createAssistant.isPending && (
            <CircleNotch size={18} weight="bold" className="animate-spin" aria-hidden="true" />
          )}
          Сохранить
        </button>
      </motion.div>
    </div>
  )
}

function DeleteAssistantDialog({
  assistant,
  pending,
  error,
  onCancel,
  onConfirm,
}: {
  assistant: Assistant
  pending: boolean
  error: string | null
  onCancel: () => void
  onConfirm: () => void
}) {
  return (
    <div className="fixed inset-0 z-[var(--z-modal,60)] grid place-items-center bg-black/45 px-4" role="dialog" aria-modal="true" aria-label="Отозвать помощника">
      <div className="w-full max-w-sm rounded-lg border p-4" style={{ background: 'var(--bg-secondary)', borderColor: 'var(--border-subtle)' }}>
        <h2 className="text-base font-semibold" style={{ color: 'var(--text-primary)' }}>
          Отозвать помощника?
        </h2>
        <p className="mt-2 text-sm" style={{ color: 'var(--text-secondary)' }}>
          {assistant.fullName} потеряет права помощника старосты.
        </p>
        {error && (
          <p className="mt-3 text-sm" style={{ color: 'var(--accent-danger)' }} role="alert">
            {error}
          </p>
        )}
        <div className="mt-4 grid grid-cols-2 gap-2">
          <button
            type="button"
            onClick={onConfirm}
            disabled={pending}
            className="inline-flex min-h-10 items-center justify-center gap-1.5 rounded-lg text-sm font-semibold"
            style={{ background: 'var(--accent-danger)', color: 'white', opacity: pending ? 0.7 : 1 }}
          >
            {pending && <CircleNotch size={17} weight="bold" className="animate-spin" aria-hidden="true" />}
            Отозвать
          </button>
          <button
            type="button"
            onClick={onCancel}
            disabled={pending}
            className="min-h-10 rounded-lg border text-sm font-semibold"
            style={{ borderColor: 'var(--border-default)', color: 'var(--text-secondary)' }}
          >
            Отмена
          </button>
        </div>
      </div>
    </div>
  )
}

function Avatar({ name }: { name: string }) {
  const initial = name.trim().charAt(0).toUpperCase() || 'С'
  return (
    <div
      className="grid size-10 shrink-0 place-items-center rounded-full text-sm font-bold"
      style={{ background: 'var(--accent-secondary)', color: 'white' }}
      aria-hidden="true"
    >
      {initial}
    </div>
  )
}

function Badge({ text, tone }: { text: string; tone: 'primary' | 'info' }) {
  return (
    <span
      className="rounded-full px-2 py-0.5 text-[11px] font-semibold"
      style={{
        background: tone === 'primary' ? 'var(--accent-primary)' : 'var(--accent-info)',
        color: 'white',
      }}
    >
      {text}
    </span>
  )
}

function EmptyBox({ text }: { text: string }) {
  return (
    <div
      className="rounded-lg border px-4 py-6 text-center text-sm"
      style={{ borderColor: 'var(--border-subtle)', color: 'var(--text-secondary)' }}
    >
      {text}
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
      {Array.from({ length: 5 }).map((_, index) => (
        <motion.div
          key={index}
          initial={{ opacity: 0.6 }}
          animate={{ opacity: 1 }}
          transition={{ repeat: Infinity, repeatType: 'reverse', duration: 0.9 }}
          className="h-[64px] rounded-lg"
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

function getHttpStatus(error: unknown): number {
  return (error as { response?: { status?: number } })?.response?.status ?? 500
}
