import { useState, type FormEvent } from 'react'
import { motion } from 'motion/react'
import { WarningCircle, X } from '@phosphor-icons/react'
import { cn } from '@/lib/utils'
import {
  useCreateHomework,
  useUpdateHomework,
} from './api'
import type { HomeworkResponse } from './types'

interface Props {
  mode: 'create' | 'edit'
  groupId: number
  semesterId: number
  subjectId: number
  lessonDate: string
  lessonNumber: number
  initial?: HomeworkResponse | null
  onDone: () => void
  onCancel: () => void
}

const TITLE_MAX = 255
const DESC_MAX = 4000
const LINK_MAX = 2048

/**
 * Compact create/edit form for a single homework item. Rendered inline under
 * a LessonCard — NOT inside a modal/sheet — so a headman can assign multiple
 * homeworks back-to-back without closing anything.
 *
 * Lesson binding (subject/date/number/group/semester) comes from the parent
 * lesson context and is immutable post-create (backend D-01).
 */
export function HomeworkInlineForm({
  mode,
  groupId,
  semesterId,
  subjectId,
  lessonDate,
  lessonNumber,
  initial,
  onDone,
  onCancel,
}: Props) {
  const [title, setTitle] = useState(initial?.title ?? '')
  const [description, setDescription] = useState(initial?.description ?? '')
  const [link, setLink] = useState(initial?.link ?? '')
  const [apiError, setApiError] = useState<string | null>(null)

  const createMut = useCreateHomework()
  const updateMut = useUpdateHomework()
  const submitting = createMut.isPending || updateMut.isPending

  const titleTrim = title.trim()
  const invalid =
    titleTrim.length === 0 ||
    titleTrim.length > TITLE_MAX ||
    description.length > DESC_MAX ||
    link.length > LINK_MAX

  const submit = async (e: FormEvent) => {
    e.preventDefault()
    if (invalid || submitting) return
    setApiError(null)
    const desc = description.trim() ? description.trim() : null
    const lnk = link.trim() ? link.trim() : null
    try {
      if (mode === 'edit' && initial) {
        await updateMut.mutateAsync({
          id: initial.id,
          req: { title: titleTrim, description: desc, link: lnk },
        })
      } else {
        await createMut.mutateAsync({
          title: titleTrim,
          description: desc,
          link: lnk,
          subjectId,
          groupId,
          semesterId,
          lessonDate,
          lessonNumber,
        })
      }
      onDone()
    } catch (err: unknown) {
      const status = (err as { response?: { status?: number } })?.response?.status
      if (status === 403) {
        setApiError('Нельзя редактировать чужое задание.')
      } else if (status === 404) {
        setApiError('Задание не найдено. Обновите страницу.')
      } else {
        setApiError('Не удалось сохранить. Попробуйте ещё раз.')
      }
    }
  }

  return (
    <motion.form
      initial={{ opacity: 0, height: 0 }}
      animate={{ opacity: 1, height: 'auto' }}
      exit={{ opacity: 0, height: 0 }}
      transition={{ duration: 0.2, ease: [0.16, 1, 0.3, 1] }}
      onSubmit={submit}
      className="overflow-hidden"
      aria-label={mode === 'edit' ? 'Редактировать задание' : 'Создать задание'}
    >
      <div
        className="mt-2 flex flex-col gap-3 rounded-xl border p-3"
        style={{
          background: 'var(--bg-primary, var(--bg-secondary))',
          borderColor: 'var(--accent-primary)',
        }}
      >
        <div className="flex items-start justify-between gap-2">
          <p
            className="text-xs font-semibold uppercase tracking-wide"
            style={{ color: 'var(--accent-primary)' }}
          >
            {mode === 'edit' ? 'Редактирование' : 'Новое задание'}
          </p>
          <button
            type="button"
            onClick={onCancel}
            aria-label="Закрыть форму"
            className="grid size-7 place-items-center rounded-full"
            style={{ color: 'var(--text-muted)' }}
          >
            <X size={14} weight="bold" />
          </button>
        </div>

        <Field label="Название" required>
          <input
            type="text"
            value={title}
            onChange={(e) => setTitle(e.target.value)}
            maxLength={TITLE_MAX}
            required
            placeholder="Напр.: Решить №12-18 из учебника"
            className={cn(
              'w-full rounded-lg border px-3 py-2 text-sm',
              'focus:outline-none focus-visible:ring-2',
            )}
            style={{
              background: 'var(--bg-secondary)',
              borderColor: 'var(--border-subtle)',
              color: 'var(--text-primary)',
            }}
          />
        </Field>

        <Field label="Описание" optional>
          <textarea
            value={description}
            onChange={(e) => setDescription(e.target.value)}
            maxLength={DESC_MAX}
            rows={3}
            placeholder="Что именно нужно сделать"
            className={cn(
              'w-full resize-y rounded-lg border px-3 py-2 text-sm',
              'focus:outline-none focus-visible:ring-2',
            )}
            style={{
              background: 'var(--bg-secondary)',
              borderColor: 'var(--border-subtle)',
              color: 'var(--text-primary)',
              minHeight: 60,
            }}
          />
        </Field>

        <Field label="Ссылка" optional>
          <input
            type="url"
            value={link}
            onChange={(e) => setLink(e.target.value)}
            maxLength={LINK_MAX}
            placeholder="https://..."
            inputMode="url"
            className={cn(
              'w-full rounded-lg border px-3 py-2 text-sm',
              'focus:outline-none focus-visible:ring-2',
            )}
            style={{
              background: 'var(--bg-secondary)',
              borderColor: 'var(--border-subtle)',
              color: 'var(--text-primary)',
            }}
          />
        </Field>

        {apiError && (
          <div
            role="alert"
            className="inline-flex items-center gap-2 rounded-lg px-3 py-2 text-xs"
            style={{
              background:
                'color-mix(in oklab, var(--accent-danger) 12%, transparent)',
              border:
                '1px solid color-mix(in oklab, var(--accent-danger) 28%, transparent)',
              color: 'var(--accent-danger)',
            }}
          >
            <WarningCircle size={14} weight="bold" />
            {apiError}
          </div>
        )}

        <div className="flex justify-end gap-2">
          <button
            type="button"
            onClick={onCancel}
            disabled={submitting}
            className={cn(
              'rounded-full border px-4 py-1.5 text-sm font-medium',
              'disabled:opacity-50',
            )}
            style={{
              background: 'var(--bg-secondary)',
              borderColor: 'var(--border-subtle)',
              color: 'var(--text-secondary)',
            }}
          >
            Отмена
          </button>
          <button
            type="submit"
            disabled={invalid || submitting}
            className={cn(
              'rounded-full px-4 py-1.5 text-sm font-semibold',
              'disabled:opacity-50',
            )}
            style={{
              background: 'var(--gradient-brand)',
              color: 'var(--accent-primary-contrast)',
              boxShadow: 'var(--glow-primary)',
            }}
          >
            {submitting
              ? 'Сохраняем...'
              : mode === 'edit'
                ? 'Сохранить'
                : 'Создать'}
          </button>
        </div>
      </div>
    </motion.form>
  )
}

function Field({
  label,
  required,
  optional,
  children,
}: {
  label: string
  required?: boolean
  optional?: boolean
  children: React.ReactNode
}) {
  return (
    <label className="flex flex-col gap-1">
      <span
        className="text-[11px] font-medium uppercase tracking-wide"
        style={{ color: 'var(--text-muted)' }}
      >
        {label}
        {required && ' *'}
        {optional && ' (необязательно)'}
      </span>
      {children}
    </label>
  )
}
