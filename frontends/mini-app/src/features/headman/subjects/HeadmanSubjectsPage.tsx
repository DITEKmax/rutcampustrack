import { useEffect, useMemo, useState, type ReactNode } from 'react'
import { Link } from 'react-router'
import {
  ArrowLeft,
  BookOpen,
  CheckSquare,
  CircleNotch,
  PencilSimple,
  Plus,
  Square,
  Trash,
  WarningCircle,
  X,
} from '@phosphor-icons/react'
import { hapticFeedback } from '@telegram-apps/sdk-react'
import { motion } from 'motion/react'
import { useAuth } from '@/features/auth/AuthProvider'
import {
  mapHeadmanApiError,
  useCreateSubject,
  useDeleteSubject,
  useGroupSubjects,
  useGroupTeachers,
  useUpdateSubject,
} from '@/features/headman/shared/headmanApi'
import {
  SUBJECT_TYPE_LABELS,
  SUBJECT_TYPE_OPTIONS,
  type Subject,
  type SubjectType,
  type Teacher,
} from '@/features/headman/shared/types'
import { cn } from '@/lib/utils'

interface SubjectFormState {
  open: boolean
  mode: 'create' | 'edit'
  subject: Subject | null
}

export function HeadmanSubjectsPage() {
  const { user } = useAuth()
  const isHeadman = user?.role === 'STUDENT' && user.isHeadman
  const subjectsQuery = useGroupSubjects(isHeadman)
  const teachersQuery = useGroupTeachers(isHeadman)
  const deleteSubject = useDeleteSubject()
  const [form, setForm] = useState<SubjectFormState>({
    open: false,
    mode: 'create',
    subject: null,
  })
  const [deleteTarget, setDeleteTarget] = useState<Subject | null>(null)
  const [deleteError, setDeleteError] = useState<string | null>(null)
  const [toast, setToast] = useState<string | null>(null)

  const subjects = subjectsQuery.data ?? []
  const teachers = teachersQuery.data ?? []
  const teacherMap = useMemo(() => {
    const map = new Map<number, Teacher>()
    for (const teacher of teachers) map.set(teacher.id, teacher)
    return map
  }, [teachers])

  const showToast = (text: string) => {
    setToast(text)
    window.setTimeout(() => setToast(null), 2400)
  }

  const confirmDelete = async () => {
    if (!deleteTarget) return
    setDeleteError(null)
    try {
      await deleteSubject.mutateAsync(deleteTarget.id)
      setDeleteTarget(null)
      showToast('Предмет удален')
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

  return (
    <PageShell>
      <BackLink />

      <header className="mb-5 flex items-start justify-between gap-3">
        <div className="flex min-w-0 items-start gap-3">
          <div
            className="grid size-11 shrink-0 place-items-center rounded-lg border"
            style={{
              background: 'color-mix(in oklab, var(--accent-secondary) 13%, transparent)',
              borderColor: 'color-mix(in oklab, var(--accent-secondary) 35%, transparent)',
              color: 'var(--accent-secondary)',
            }}
          >
            <BookOpen size={25} weight="duotone" aria-hidden="true" />
          </div>
          <div className="min-w-0">
            <p className="text-xs font-medium uppercase" style={{ color: 'var(--text-muted)' }}>
              Староста
            </p>
            <h1
              className="text-2xl font-bold leading-tight text-balance"
              style={{ fontFamily: 'var(--font-heading)' }}
            >
              Предметы
            </h1>
          </div>
        </div>

        <button
          type="button"
          onClick={() => setForm({ open: true, mode: 'create', subject: null })}
          aria-label="Создать предмет"
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

      {(subjectsQuery.isLoading || teachersQuery.isLoading) && <SkeletonList />}

      {!subjectsQuery.isLoading && subjectsQuery.isError && (
        <InlineError text="Не удалось загрузить предметы. Обновите страницу." />
      )}

      {!teachersQuery.isLoading && teachersQuery.isError && (
        <InlineError text="Не удалось загрузить преподавателей. Предметы можно просматривать." />
      )}

      {!subjectsQuery.isLoading && !subjectsQuery.isError && subjects.length === 0 && (
        <EmptyState />
      )}

      {!subjectsQuery.isLoading && !subjectsQuery.isError && subjects.length > 0 && (
        <div className="flex flex-col gap-3">
          {subjects.map((subject, index) => (
            <SubjectCard
              key={subject.id}
              index={index}
              subject={subject}
              teacherMap={teacherMap}
              onEdit={() => setForm({ open: true, mode: 'edit', subject })}
              onDelete={() => {
                setDeleteError(null)
                setDeleteTarget(subject)
              }}
            />
          ))}
        </div>
      )}

      <SubjectFormSheet
        open={form.open}
        mode={form.mode}
        subject={form.subject}
        teachers={teachers}
        onClose={() => setForm((current) => ({ ...current, open: false }))}
        onSaved={(mode) => showToast(mode === 'create' ? 'Предмет создан' : 'Предмет обновлен')}
      />

      {deleteTarget && (
        <DeleteSubjectDialog
          subject={deleteTarget}
          pending={deleteSubject.isPending}
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

function SubjectCard({
  index,
  subject,
  teacherMap,
  onEdit,
  onDelete,
}: {
  index: number
  subject: Subject
  teacherMap: Map<number, Teacher>
  onEdit: () => void
  onDelete: () => void
}) {
  const teacherNames = subject.teacherIds
    .map((teacherId) => teacherMap.get(teacherId)?.fullName)
    .filter(Boolean)

  return (
    <motion.article
      initial={{ opacity: 0, y: 8 }}
      animate={{ opacity: 1, y: 0 }}
      transition={{ duration: 0.16, delay: index * 0.025 }}
      className="rounded-lg border p-4"
      style={{ background: 'var(--bg-secondary)', borderColor: 'var(--border-subtle)' }}
    >
      <div className="flex items-start gap-3">
        <div className="min-w-0 flex-1">
          <div className="mb-1 flex flex-wrap items-center gap-2">
            <span
              className="rounded-full border px-2 py-0.5 text-[11px] font-semibold"
              style={{
                borderColor: 'var(--border-accent)',
                color: 'var(--accent-primary)',
                background: 'color-mix(in oklab, var(--accent-primary) 10%, transparent)',
              }}
            >
              {SUBJECT_TYPE_LABELS[subject.type]}
            </span>
          </div>
          <h2
            className="truncate text-base font-semibold"
            style={{ color: 'var(--text-primary)', fontFamily: 'var(--font-heading)' }}
          >
            {subject.name}
          </h2>
          <p className="mt-0.5 text-sm" style={{ color: 'var(--text-secondary)' }}>
            {teacherNames.length > 0 ? teacherNames.join(', ') : 'Преподаватель не назначен'}
          </p>
        </div>
        <div className="flex shrink-0 items-center gap-1">
          <button
            type="button"
            onClick={onEdit}
            aria-label="Редактировать предмет"
            className="grid size-9 place-items-center rounded-full"
            style={{ color: 'var(--text-secondary)', background: 'var(--bg-primary)' }}
          >
            <PencilSimple size={18} weight="bold" aria-hidden="true" />
          </button>
          <button
            type="button"
            onClick={onDelete}
            aria-label="Удалить предмет"
            className="grid size-9 place-items-center rounded-full"
            style={{ color: 'var(--accent-danger)', background: 'var(--bg-primary)' }}
          >
            <Trash size={18} weight="bold" aria-hidden="true" />
          </button>
        </div>
      </div>
    </motion.article>
  )
}

function SubjectFormSheet({
  open,
  mode,
  subject,
  teachers,
  onClose,
  onSaved,
}: {
  open: boolean
  mode: 'create' | 'edit'
  subject: Subject | null
  teachers: Teacher[]
  onClose: () => void
  onSaved: (mode: 'create' | 'edit') => void
}) {
  const createSubject = useCreateSubject()
  const updateSubject = useUpdateSubject()
  const [name, setName] = useState('')
  const [type, setType] = useState<SubjectType>('LECTURE')
  const [teacherIds, setTeacherIds] = useState<number[]>([])
  const [error, setError] = useState<string | null>(null)
  const mutationPending = createSubject.isPending || updateSubject.isPending

  useEffect(() => {
    if (!open) return
    setName(subject?.name ?? '')
    setType(subject?.type ?? 'LECTURE')
    setTeacherIds(subject?.teacherIds ?? [])
    setError(null)
  }, [open, subject])

  if (!open) return null

  const toggleTeacher = (teacherId: number) => {
    setTeacherIds((current) =>
      current.includes(teacherId)
        ? current.filter((id) => id !== teacherId)
        : [...current, teacherId],
    )
    setError(null)
  }

  const submit = async () => {
    const trimmedName = name.trim()
    if (trimmedName.length < 2) {
      setError('Название должно быть не короче двух символов.')
      return
    }

    try {
      if (mode === 'create') {
        await createSubject.mutateAsync({ name: trimmedName, type, teacherIds })
      } else if (subject) {
        await updateSubject.mutateAsync({
          id: subject.id,
          name: trimmedName,
          type,
          previousTeacherIds: subject.teacherIds,
          teacherIds,
        })
      }
      onSaved(mode)
      onClose()
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

  return (
    <div className="fixed inset-0 z-[var(--z-modal,60)] flex items-end bg-black/45" role="dialog" aria-modal="true" aria-label={mode === 'create' ? 'Создать предмет' : 'Редактировать предмет'}>
      <motion.div
        initial={{ y: '100%' }}
        animate={{ y: 0 }}
        transition={{ type: 'spring', stiffness: 520, damping: 42 }}
        className="max-h-[90dvh] w-full overflow-y-auto rounded-t-2xl border-t px-4 pb-[calc(18px+env(safe-area-inset-bottom))] pt-4"
        style={{ background: 'var(--bg-secondary)', borderColor: 'var(--border-subtle)' }}
      >
        <div className="mb-4 flex items-center justify-between gap-3">
          <h2 className="text-base font-semibold" style={{ fontFamily: 'var(--font-heading)' }}>
            {mode === 'create' ? 'Новый предмет' : 'Редактировать предмет'}
          </h2>
          <button
            type="button"
            onClick={onClose}
            aria-label="Закрыть"
            className="grid size-9 place-items-center rounded-full"
            style={{ color: 'var(--text-secondary)' }}
          >
            <X size={20} weight="bold" aria-hidden="true" />
          </button>
        </div>

        <label className="mb-1.5 block text-sm font-semibold" htmlFor="subject-name">
          Название
        </label>
        <input
          id="subject-name"
          value={name}
          onChange={(event) => {
            setName(event.target.value)
            setError(null)
          }}
          className="h-11 w-full rounded-lg border bg-transparent px-3 text-sm outline-none"
          style={{
            borderColor: 'var(--border-default)',
            color: 'var(--text-primary)',
            background: 'var(--bg-primary)',
          }}
        />

        <section className="mt-4">
          <h3 className="mb-2 text-sm font-semibold">Тип</h3>
          <div className="grid grid-cols-3 gap-2">
            {SUBJECT_TYPE_OPTIONS.map((option) => (
              <button
                key={option}
                type="button"
                onClick={() => setType(option)}
                className="min-h-10 rounded-lg border px-2 text-xs font-semibold"
                style={{
                  borderColor: type === option ? 'var(--border-accent)' : 'var(--border-subtle)',
                  background:
                    type === option
                      ? 'color-mix(in oklab, var(--accent-primary) 12%, transparent)'
                      : 'var(--bg-primary)',
                  color: type === option ? 'var(--accent-primary)' : 'var(--text-secondary)',
                }}
              >
                {SUBJECT_TYPE_LABELS[option]}
              </button>
            ))}
          </div>
        </section>

        <section className="mt-4">
          <h3 className="mb-2 text-sm font-semibold">Преподаватели</h3>
          {teachers.length === 0 ? (
            <EmptyBox text="Список преподавателей пуст." />
          ) : (
            <div className="flex flex-col gap-2">
              {teachers.map((teacher) => {
                const selected = teacherIds.includes(teacher.id)
                return (
                  <button
                    key={teacher.id}
                    type="button"
                    onClick={() => toggleTeacher(teacher.id)}
                    aria-pressed={selected}
                    className="flex min-h-11 items-center gap-3 rounded-lg border px-3 text-left"
                    style={{
                      borderColor: selected ? 'var(--border-accent)' : 'var(--border-subtle)',
                      background: selected
                        ? 'color-mix(in oklab, var(--accent-primary) 10%, transparent)'
                        : 'var(--bg-primary)',
                    }}
                  >
                    {selected ? (
                      <CheckSquare size={21} weight="fill" aria-hidden="true" style={{ color: 'var(--accent-primary)' }} />
                    ) : (
                      <Square size={21} weight="regular" aria-hidden="true" style={{ color: 'var(--text-muted)' }} />
                    )}
                    <span className="min-w-0 flex-1 truncate text-sm font-medium">
                      {teacher.fullName}
                    </span>
                  </button>
                )
              })}
            </div>
          )}
        </section>

        {error && (
          <p className="mt-3 text-sm" style={{ color: 'var(--accent-danger)' }} role="alert">
            {error}
          </p>
        )}

        <button
          type="button"
          onClick={() => void submit()}
          disabled={mutationPending}
          className={cn(
            'mt-5 inline-flex min-h-11 w-full items-center justify-center gap-2 rounded-lg text-sm font-semibold',
            mutationPending && 'cursor-not-allowed opacity-70',
          )}
          style={{ background: 'var(--accent-primary)', color: 'var(--accent-primary-contrast)' }}
        >
          {mutationPending && (
            <CircleNotch size={18} weight="bold" className="animate-spin" aria-hidden="true" />
          )}
          Сохранить
        </button>
      </motion.div>
    </div>
  )
}

function DeleteSubjectDialog({
  subject,
  pending,
  error,
  onCancel,
  onConfirm,
}: {
  subject: Subject
  pending: boolean
  error: string | null
  onCancel: () => void
  onConfirm: () => void
}) {
  return (
    <div className="fixed inset-0 z-[var(--z-modal,60)] grid place-items-center bg-black/45 px-4" role="dialog" aria-modal="true" aria-label="Удалить предмет">
      <div className="w-full max-w-sm rounded-lg border p-4" style={{ background: 'var(--bg-secondary)', borderColor: 'var(--border-subtle)' }}>
        <h2 className="text-base font-semibold" style={{ color: 'var(--text-primary)' }}>
          Удалить предмет?
        </h2>
        <p className="mt-2 text-sm" style={{ color: 'var(--text-secondary)' }}>
          «{subject.name}» будет удален без возможности восстановления.
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
            Удалить
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

function EmptyState() {
  return (
    <div className="flex flex-col items-center justify-center py-14 text-center">
      <div
        className="mb-4 grid size-20 place-items-center rounded-full border"
        style={{
          background: 'var(--bg-secondary)',
          borderColor: 'var(--border-subtle)',
          color: 'var(--accent-secondary)',
        }}
      >
        <BookOpen size={38} weight="duotone" aria-hidden="true" />
      </div>
      <h2 className="text-lg font-semibold">Предметов пока нет</h2>
    </div>
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
      className="mb-3 flex items-start gap-2 rounded-lg border px-4 py-3 text-sm"
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
          className="h-[88px] rounded-lg"
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
