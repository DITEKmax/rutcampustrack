import { useState } from 'react'
import { AnimatePresence, motion } from 'motion/react'
import {
  FileText,
  HandWaving,
  X,
  Paperclip,
  CheckCircle,
} from '@phosphor-icons/react'
import { cn } from '@/lib/utils'
import type { LessonResponse } from './types'
import {
  EXCUSE_TYPES,
  mapLessonActionError,
  useCreateExcuse,
  useRequestLateCheckin,
  type ExcuseType,
} from './lessonActionsApi'

interface Props {
  open: boolean
  lesson: LessonResponse | null
  subjectName: string
  onClose: () => void
  onToast: (type: 'success' | 'error', message: string) => void
  /** Student's attendance status for this lesson, if known. */
  personalStatus?: string | null
}

type Screen = 'menu' | 'excuse' | 'excuse-success' | 'late-checkin-success'

/**
 * Bottom sheet with student actions on a single lesson:
 * - submit an excuse ticket (with optional file up to 10MB)
 * - ask headman to confirm presence (late check-in)
 *
 * Dismissed by backdrop tap, handle swipe-down (via drag), or close button.
 */
export function LessonActionsSheet({
  open,
  lesson,
  subjectName,
  onClose,
  onToast,
  personalStatus,
}: Props) {
  const [screen, setScreen] = useState<Screen>('menu')
  const [excuseType, setExcuseType] = useState<ExcuseType>('ILLNESS')
  const [comment, setComment] = useState('')
  const [file, setFile] = useState<File | null>(null)

  const excuseMutation = useCreateExcuse()
  const lateCheckinMutation = useRequestLateCheckin()

  const reset = () => {
    setScreen('menu')
    setExcuseType('ILLNESS')
    setComment('')
    setFile(null)
    excuseMutation.reset()
    lateCheckinMutation.reset()
  }

  const handleClose = () => {
    onClose()
    // Reset after the exit animation so the UI doesn't flash.
    setTimeout(reset, 300)
  }

  const handleExcuseSubmit = async () => {
    if (!lesson) return
    try {
      await excuseMutation.mutateAsync({
        lessonIds: [lesson.id],
        excuseType,
        comment: comment.trim() || undefined,
        file,
      })
      setScreen('excuse-success')
      onToast('success', 'Тикет отправлен старосте')
    } catch (e: unknown) {
      const status = (e as { response?: { status?: number } })?.response?.status
      onToast('error', mapLessonActionError(status))
    }
  }

  const handleLateCheckin = async () => {
    if (!lesson) return
    try {
      await lateCheckinMutation.mutateAsync(lesson.id)
      setScreen('late-checkin-success')
      onToast('success', 'Запрос отправлен старосте')
    } catch (e: unknown) {
      const status = (e as { response?: { status?: number } })?.response?.status
      onToast('error', mapLessonActionError(status))
    }
  }

  const onFileChange = (e: React.ChangeEvent<HTMLInputElement>) => {
    const f = e.target.files?.[0] ?? null
    if (f && f.size > 10 * 1024 * 1024) {
      onToast('error', 'Файл больше 10 МБ')
      return
    }
    setFile(f)
  }

  return (
    <AnimatePresence>
      {open && lesson && (
        <>
          {/* Backdrop */}
          <motion.div
            key="sheet-backdrop"
            initial={{ opacity: 0 }}
            animate={{ opacity: 1 }}
            exit={{ opacity: 0 }}
            transition={{ duration: 0.2 }}
            onClick={handleClose}
            className="fixed inset-0 z-[var(--z-overlay)] bg-black/55 backdrop-blur-sm"
            aria-hidden="true"
          />

          {/* Sheet */}
          <motion.div
            key="sheet"
            role="dialog"
            aria-modal="true"
            aria-label="Действия с парой"
            initial={{ y: '100%' }}
            animate={{ y: 0 }}
            exit={{ y: '100%' }}
            transition={{ duration: 0.3, ease: [0.16, 1, 0.3, 1] }}
            drag="y"
            dragConstraints={{ top: 0, bottom: 0 }}
            dragElastic={{ top: 0, bottom: 0.35 }}
            onDragEnd={(_e, info) => {
              if (info.offset.y > 120) handleClose()
            }}
            className={cn(
              'fixed inset-x-0 bottom-0 z-[var(--z-modal)]',
              'rounded-t-[var(--radius-lg)] border-t',
              'max-h-[92vh] overflow-hidden',
              'pb-[env(safe-area-inset-bottom)]',
            )}
            style={{
              background: 'var(--bg-elevated)',
              borderColor: 'var(--border-default)',
              boxShadow: 'var(--shadow-lg)',
            }}
          >
            {/* Drag handle */}
            <div className="flex justify-center pt-[var(--space-2)] pb-[var(--space-1)]">
              <span
                aria-hidden="true"
                className="block h-1 w-10 rounded-full"
                style={{ background: 'var(--border-default)' }}
              />
            </div>

            {/* Header */}
            <div className="flex items-start gap-[var(--space-3)] px-[var(--space-5)] pb-[var(--space-3)]">
              <div className="min-w-0 flex-1">
                <h2
                  className="truncate text-[var(--text-lg)] font-semibold"
                  style={{
                    color: 'var(--text-primary)',
                    fontFamily: 'var(--font-heading)',
                  }}
                >
                  {subjectName}
                </h2>
                <p
                  className="text-[var(--text-xs)]"
                  style={{ color: 'var(--text-muted)' }}
                >
                  Ауд. {lesson.room} · {lesson.startTime.slice(0, 5)}–
                  {lesson.endTime.slice(0, 5)}
                </p>
              </div>
              <button
                type="button"
                onClick={handleClose}
                aria-label="Закрыть"
                className="grid size-9 place-items-center rounded-full"
                style={{ color: 'var(--text-secondary)' }}
              >
                <X size={18} weight="bold" />
              </button>
            </div>

            {/* Content */}
            <div className="max-h-[calc(92vh-80px)] overflow-y-auto px-[var(--space-5)] pb-[var(--space-5)]">
              {screen === 'menu' && (
                <MenuScreen
                  onExcuse={() => setScreen('excuse')}
                  onLateCheckin={handleLateCheckin}
                  lessonStatus={lesson.status}
                  personalStatus={personalStatus ?? null}
                  isLateCheckinLoading={lateCheckinMutation.isPending}
                />
              )}

              {screen === 'excuse' && (
                <ExcuseForm
                  excuseType={excuseType}
                  onExcuseType={setExcuseType}
                  comment={comment}
                  onComment={setComment}
                  file={file}
                  onFileChange={onFileChange}
                  onSubmit={handleExcuseSubmit}
                  onBack={() => setScreen('menu')}
                  isSubmitting={excuseMutation.isPending}
                />
              )}

              {(screen === 'excuse-success' ||
                screen === 'late-checkin-success') && (
                <SuccessScreen
                  title={
                    screen === 'excuse-success'
                      ? 'Тикет отправлен'
                      : 'Запрос отправлен'
                  }
                  text={
                    screen === 'excuse-success'
                      ? 'Староста получит уведомление и примет решение.'
                      : 'Староста подтвердит или отклонит в ближайшее время.'
                  }
                  onClose={handleClose}
                />
              )}
            </div>
          </motion.div>
        </>
      )}
    </AnimatePresence>
  )
}

// ─── Sub-screens ─────────────────────────────────────────────────────────────

function MenuScreen({
  onExcuse,
  onLateCheckin,
  lessonStatus,
  personalStatus,
  isLateCheckinLoading,
}: {
  onExcuse: () => void
  onLateCheckin: () => void
  lessonStatus: LessonResponse['status']
  personalStatus: string | null
  isLateCheckinLoading: boolean
}) {
  const isLessonActive = lessonStatus === 'ACTIVE'
  const isPast = lessonStatus === 'CLOSED'
  const isFuture = lessonStatus === 'PLANNED'
  const isAbsent = personalStatus === 'absent'

  // Late-checkin (request headman to confirm attendance) is useful:
  //  - while a lesson is ongoing (student couldn't geo-check-in),
  //  - or after the lesson ended with an "absent" mark (forgot to check in).
  // It is not meaningful for future lessons or ones the student already
  // attended / already has an excuse for.
  const canRequestLateCheckin =
    (isLessonActive && personalStatus !== 'present') ||
    (isPast && isAbsent)

  const lateCheckinSubtitle = canRequestLateCheckin
    ? isLessonActive
      ? 'Попросить старосту подтвердить, что ты был на паре'
      : 'Попросить старосту зачесть, что ты был на этой паре'
    : isFuture
    ? 'Доступно пока идёт пара или после того, как её закрыли с «н»'
    : personalStatus === 'present'
    ? 'Ты уже отмечен как присутствующий'
    : 'Сейчас недоступно для этой пары'

  return (
    <div className="flex flex-col gap-[var(--space-3)]">
      <ActionRow
        icon={<FileText size={22} weight="duotone" />}
        title="Подать уважительную"
        subtitle="Болезнь, приказ, повестка, свобод. посещение"
        onClick={onExcuse}
      />
      <ActionRow
        icon={<HandWaving size={22} weight="duotone" />}
        title="Запросить посещение"
        subtitle={lateCheckinSubtitle}
        onClick={onLateCheckin}
        disabled={!canRequestLateCheckin || isLateCheckinLoading}
        loading={isLateCheckinLoading}
      />
    </div>
  )
}

function ActionRow({
  icon,
  title,
  subtitle,
  onClick,
  disabled,
  loading,
}: {
  icon: React.ReactNode
  title: string
  subtitle: string
  onClick: () => void
  disabled?: boolean
  loading?: boolean
}) {
  return (
    <motion.button
      type="button"
      whileTap={{ scale: disabled ? 1 : 0.98 }}
      onClick={disabled ? undefined : onClick}
      className={cn(
        'flex w-full items-center gap-[var(--space-3)] rounded-[var(--radius-md)] border',
        'px-[var(--space-4)] py-[var(--space-4)] text-left',
        'transition-opacity duration-150',
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
          background:
            'color-mix(in oklab, var(--accent-primary) 14%, transparent)',
          color: 'var(--accent-primary)',
          border: '1px solid var(--border-accent)',
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
      {loading && (
        <span
          className="size-4 animate-spin rounded-full border-2"
          style={{
            borderColor: 'var(--border-default)',
            borderTopColor: 'var(--accent-primary)',
          }}
        />
      )}
    </motion.button>
  )
}

function ExcuseForm({
  excuseType,
  onExcuseType,
  comment,
  onComment,
  file,
  onFileChange,
  onSubmit,
  onBack,
  isSubmitting,
}: {
  excuseType: ExcuseType
  onExcuseType: (v: ExcuseType) => void
  comment: string
  onComment: (v: string) => void
  file: File | null
  onFileChange: (e: React.ChangeEvent<HTMLInputElement>) => void
  onSubmit: () => void
  onBack: () => void
  isSubmitting: boolean
}) {
  return (
    <form
      onSubmit={(e) => {
        e.preventDefault()
        if (!isSubmitting) onSubmit()
      }}
      className="flex flex-col gap-[var(--space-4)]"
    >
      <div className="flex flex-col gap-[var(--space-2)]">
        <label
          className="text-[var(--text-xs)] font-semibold uppercase tracking-wide"
          style={{ color: 'var(--text-muted)' }}
        >
          Тип причины
        </label>
        <div className="flex flex-wrap gap-[var(--space-2)]">
          {EXCUSE_TYPES.map((t) => {
            const selected = excuseType === t.value
            return (
              <button
                key={t.value}
                type="button"
                onClick={() => onExcuseType(t.value)}
                className={cn(
                  'rounded-full border px-[var(--space-3)] py-1.5 text-[var(--text-xs)] font-medium transition-colors',
                )}
                style={
                  selected
                    ? {
                        background:
                          'color-mix(in oklab, var(--accent-primary) 18%, transparent)',
                        borderColor: 'var(--border-accent)',
                        color: 'var(--accent-primary)',
                      }
                    : {
                        background: 'var(--bg-secondary)',
                        borderColor: 'var(--border-subtle)',
                        color: 'var(--text-secondary)',
                      }
                }
              >
                {t.label}
              </button>
            )
          })}
        </div>
      </div>

      <div className="flex flex-col gap-[var(--space-2)]">
        <label
          htmlFor="excuse-comment"
          className="text-[var(--text-xs)] font-semibold uppercase tracking-wide"
          style={{ color: 'var(--text-muted)' }}
        >
          Комментарий (необязательно)
        </label>
        <textarea
          id="excuse-comment"
          value={comment}
          onChange={(e) => onComment(e.target.value)}
          maxLength={1000}
          rows={3}
          placeholder="Например: справка из поликлиники №52"
          className="w-full resize-none rounded-[var(--radius-md)] border px-[var(--space-3)] py-[var(--space-3)] text-[var(--text-sm)]"
          style={{
            background: 'var(--bg-surface)',
            borderColor: 'var(--border-subtle)',
            color: 'var(--text-primary)',
          }}
        />
        <span
          className="text-right text-[10px] tabular-nums"
          style={{ color: 'var(--text-muted)' }}
        >
          {comment.length}/1000
        </span>
      </div>

      <label
        className={cn(
          'flex items-center gap-[var(--space-3)] rounded-[var(--radius-md)] border p-[var(--space-3)]',
          'cursor-pointer',
        )}
        style={{
          background: 'var(--bg-secondary)',
          borderColor: 'var(--border-subtle)',
        }}
      >
        <Paperclip size={18} weight="bold" style={{ color: 'var(--text-secondary)' }} />
        <span
          className="flex-1 truncate text-[var(--text-sm)]"
          style={{ color: file ? 'var(--text-primary)' : 'var(--text-muted)' }}
        >
          {file ? file.name : 'Прикрепить файл (до 10 МБ)'}
        </span>
        <input
          type="file"
          className="hidden"
          onChange={onFileChange}
          accept=".pdf,.jpg,.jpeg,.png,.heic,.doc,.docx"
        />
      </label>

      <div className="flex gap-[var(--space-3)]">
        <button
          type="button"
          onClick={onBack}
          className="flex-1 rounded-[var(--radius-md)] border px-[var(--space-4)] py-[var(--space-3)] text-[var(--text-sm)] font-medium"
          style={{
            background: 'var(--bg-secondary)',
            borderColor: 'var(--border-subtle)',
            color: 'var(--text-secondary)',
          }}
        >
          Назад
        </button>
        <button
          type="submit"
          disabled={isSubmitting}
          className="flex-1 rounded-[var(--radius-md)] px-[var(--space-4)] py-[var(--space-3)] text-[var(--text-sm)] font-semibold transition-opacity"
          style={{
            background: 'var(--gradient-brand)',
            color: 'var(--accent-primary-contrast)',
            boxShadow: 'var(--glow-primary)',
            opacity: isSubmitting ? 0.6 : 1,
          }}
        >
          {isSubmitting ? 'Отправка...' : 'Отправить'}
        </button>
      </div>
    </form>
  )
}

function SuccessScreen({
  title,
  text,
  onClose,
}: {
  title: string
  text: string
  onClose: () => void
}) {
  return (
    <div className="flex flex-col items-center gap-[var(--space-4)] py-[var(--space-5)] text-center">
      <motion.span
        initial={{ scale: 0.7, opacity: 0 }}
        animate={{ scale: 1, opacity: 1 }}
        transition={{ type: 'spring', stiffness: 320, damping: 22 }}
        aria-hidden="true"
        className="grid size-16 place-items-center rounded-full"
        style={{
          background:
            'color-mix(in oklab, var(--accent-primary) 18%, transparent)',
          color: 'var(--accent-primary)',
          border: '1px solid var(--border-accent)',
          boxShadow: 'var(--glow-primary)',
        }}
      >
        <CheckCircle size={32} weight="fill" />
      </motion.span>
      <div className="flex flex-col gap-[var(--space-2)]">
        <h3
          className="text-[var(--text-lg)] font-semibold"
          style={{
            color: 'var(--text-primary)',
            fontFamily: 'var(--font-heading)',
          }}
        >
          {title}
        </h3>
        <p
          className="text-[var(--text-sm)]"
          style={{ color: 'var(--text-secondary)' }}
        >
          {text}
        </p>
      </div>
      <button
        type="button"
        onClick={onClose}
        className="mt-[var(--space-2)] rounded-full px-[var(--space-5)] py-[var(--space-3)] text-[var(--text-sm)] font-semibold"
        style={{
          background: 'var(--gradient-brand)',
          color: 'var(--accent-primary-contrast)',
          boxShadow: 'var(--glow-primary)',
        }}
      >
        Отлично
      </button>
    </div>
  )
}

