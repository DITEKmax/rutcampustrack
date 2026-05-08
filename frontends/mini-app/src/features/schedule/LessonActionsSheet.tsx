import type { ChangeEvent, ReactNode } from 'react'
import { useCallback, useState } from 'react'
import { hapticFeedback } from '@telegram-apps/sdk-react'
import {
  CaretLeft,
  CheckCircle,
  CircleNotch,
  FileText,
  HandWaving,
  Paperclip,
  WarningCircle,
} from '@phosphor-icons/react'
import { motion } from 'motion/react'
import { BottomSheet } from '@/shared/components/BottomSheet'
import { useMainButton } from '@/shared/hooks/useMainButton'
import {
  EXCUSE_TYPE_LABELS,
  EXCUSE_TYPE_OPTIONS,
  getHttpStatus,
  useRequestLateCheckin,
  useSubmitStudentExcuse,
  type ExcuseType,
} from '@/features/student/requests/studentRequestsApi'
import { cn } from '@/lib/utils'
import type { AttendanceStatus, LessonResponse } from './types'

interface LessonActionsSheetProps {
  open: boolean
  lesson: LessonResponse | null
  subjectName: string
  personalStatus?: AttendanceStatus | null
  onClose: () => void
}

type SheetScreen = 'menu' | 'excuse' | 'success'
type SuccessKind = 'excuse' | 'late-checkin'

const MAX_FILE_BYTES = 10 * 1024 * 1024

export function LessonActionsSheet({
  open,
  lesson,
  subjectName,
  personalStatus = null,
  onClose,
}: LessonActionsSheetProps) {
  const [screen, setScreen] = useState<SheetScreen>('menu')
  const [successKind, setSuccessKind] = useState<SuccessKind>('excuse')
  const [excuseType, setExcuseType] = useState<ExcuseType>('ILLNESS')
  const [comment, setComment] = useState('')
  const [file, setFile] = useState<File | null>(null)
  const [error, setError] = useState<string | null>(null)

  const lateCheckinMutation = useRequestLateCheckin()
  const excuseMutation = useSubmitStudentExcuse()

  const reset = useCallback(() => {
    setScreen('menu')
    setSuccessKind('excuse')
    setExcuseType('ILLNESS')
    setComment('')
    setFile(null)
    setError(null)
    lateCheckinMutation.reset()
    excuseMutation.reset()
  }, [excuseMutation, lateCheckinMutation])

  const handleClose = useCallback(() => {
    onClose()
    reset()
  }, [onClose, reset])

  const handleLateCheckin = useCallback(async () => {
    if (!lesson || lateCheckinMutation.isPending) return
    setError(null)
    try {
      await lateCheckinMutation.mutateAsync(lesson.id)
      setSuccessKind('late-checkin')
      setScreen('success')
      notify('success')
    } catch (unknownError) {
      const status = getHttpStatus(unknownError)
      if (status === 409) {
        setSuccessKind('late-checkin')
        setScreen('success')
        notify('success')
        return
      }
      setError(mapLessonActionError(status))
      notify('error')
    }
  }, [lateCheckinMutation, lesson])

  const handleExcuseSubmit = useCallback(async () => {
    if (!lesson || excuseMutation.isPending) return
    setError(null)
    try {
      await excuseMutation.mutateAsync({
        lessonIds: [lesson.id],
        excuseType,
        comment: comment.trim() || null,
        file,
      })
      setSuccessKind('excuse')
      setScreen('success')
      notify('success')
    } catch (unknownError) {
      setError(mapLessonActionError(getHttpStatus(unknownError)))
      notify('error')
    }
  }, [comment, excuseMutation, excuseType, file, lesson])

  useMainButton({
    text: excuseMutation.isPending ? 'Отправляем...' : 'Отправить тикет',
    isEnabled: open && screen === 'excuse' && !!lesson && !excuseMutation.isPending,
    isVisible: open && screen === 'excuse',
    onClick: () => {
      void handleExcuseSubmit()
    },
  })

  const handleFileChange = (event: ChangeEvent<HTMLInputElement>) => {
    const selectedFile = event.target.files?.[0] ?? null
    if (selectedFile && selectedFile.size > MAX_FILE_BYTES) {
      setError('Файл должен быть не больше 10 МБ.')
      setFile(null)
      notify('error')
      return
    }
    setError(null)
    setFile(selectedFile)
  }

  const isOpen = open && !!lesson

  return (
    <BottomSheet
      open={isOpen}
      onClose={handleClose}
      title="Действия с парой"
      heading={subjectName}
      subtitle={lesson ? `Ауд. ${lesson.room} · ${formatTime(lesson.startTime)}-${formatTime(lesson.endTime)}` : undefined}
    >
      {lesson && (
        <div className="flex flex-col gap-3 px-4 pb-5">
          {error && <InlineError text={error} />}

          {screen === 'menu' && (
            <MenuScreen
              lessonStatus={lesson.status}
              personalStatus={personalStatus}
              isLateCheckinLoading={lateCheckinMutation.isPending}
              onExcuse={() => {
                setError(null)
                setScreen('excuse')
              }}
              onLateCheckin={() => {
                void handleLateCheckin()
              }}
            />
          )}

          {screen === 'excuse' && (
            <ExcuseForm
              excuseType={excuseType}
              onExcuseType={setExcuseType}
              comment={comment}
              onComment={setComment}
              file={file}
              onFileChange={handleFileChange}
              onSubmit={() => {
                void handleExcuseSubmit()
              }}
              onBack={() => {
                setError(null)
                setScreen('menu')
              }}
              isSubmitting={excuseMutation.isPending}
            />
          )}

          {screen === 'success' && (
            <SuccessScreen
              kind={successKind}
              onClose={handleClose}
            />
          )}
        </div>
      )}
    </BottomSheet>
  )
}

function MenuScreen({
  lessonStatus,
  personalStatus,
  isLateCheckinLoading,
  onExcuse,
  onLateCheckin,
}: {
  lessonStatus: LessonResponse['status']
  personalStatus: AttendanceStatus | null
  isLateCheckinLoading: boolean
  onExcuse: () => void
  onLateCheckin: () => void
}) {
  const isActive = lessonStatus === 'ACTIVE'
  const isPast = lessonStatus === 'CLOSED'
  const isFuture = lessonStatus === 'PLANNED'
  const isAbsent = personalStatus === 'absent'
  const isPresent = personalStatus === 'present'
  const hasExcuse = personalStatus === 'excused' || personalStatus === 'free_attendance'

  const canSubmitExcuse = isAbsent
  const canRequestLateCheckin = (isActive && !isPresent) || (isPast && isAbsent)

  const excuseSubtitle = canSubmitExcuse
    ? 'Болезнь, приказ, повестка или другое основание'
    : hasExcuse
      ? 'Уважительная уже проставлена'
      : isPresent
        ? 'Ты уже отмечен как присутствующий'
        : isFuture
          ? 'Доступно после пары, если стоит "н"'
          : 'Подать можно только для статуса "н"'

  const lateCheckinSubtitle = canRequestLateCheckin
    ? isActive
      ? 'Попросить старосту подтвердить посещение'
      : 'Попросить зачесть посещение прошедшей пары'
    : isFuture
      ? 'Доступно во время пары или после отметки "н"'
      : isPresent
        ? 'Ты уже отмечен как присутствующий'
        : 'Сейчас недоступно для этой пары'

  return (
    <div className="flex flex-col gap-3">
      <ActionRow
        icon={<FileText size={22} weight="duotone" />}
        title="Подать уважительную"
        subtitle={excuseSubtitle}
        onClick={onExcuse}
        disabled={!canSubmitExcuse}
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
  icon: ReactNode
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
        'flex w-full items-center gap-3 rounded-lg border px-3 py-3 text-left',
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
        className="grid size-11 shrink-0 place-items-center rounded-lg border"
        style={{
          background: 'color-mix(in oklab, var(--accent-primary) 12%, transparent)',
          borderColor: 'var(--border-accent)',
          color: 'var(--accent-primary)',
        }}
      >
        {icon}
      </span>
      <span className="min-w-0 flex-1">
        <span
          className="block text-sm font-semibold"
          style={{ color: 'var(--text-primary)' }}
        >
          {title}
        </span>
        <span
          className="mt-0.5 block text-xs"
          style={{ color: 'var(--text-muted)' }}
        >
          {subtitle}
        </span>
      </span>
      {loading && (
        <CircleNotch
          className="shrink-0 animate-spin"
          size={18}
          weight="bold"
          style={{ color: 'var(--accent-primary)' }}
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
  onExcuseType: (value: ExcuseType) => void
  comment: string
  onComment: (value: string) => void
  file: File | null
  onFileChange: (event: ChangeEvent<HTMLInputElement>) => void
  onSubmit: () => void
  onBack: () => void
  isSubmitting: boolean
}) {
  return (
    <form
      onSubmit={(event) => {
        event.preventDefault()
        if (!isSubmitting) onSubmit()
      }}
      className="flex flex-col gap-4"
    >
      <button
        type="button"
        onClick={onBack}
        className="inline-flex w-fit items-center gap-1 rounded-full border px-3 py-1.5 text-xs font-semibold"
        style={{
          background: 'var(--bg-secondary)',
          borderColor: 'var(--border-subtle)',
          color: 'var(--text-secondary)',
        }}
      >
        <CaretLeft size={14} weight="bold" aria-hidden="true" />
        Назад
      </button>

      <div>
        <span
          className="mb-2 block text-xs font-semibold uppercase"
          style={{ color: 'var(--text-muted)' }}
        >
          Тип причины
        </span>
        <div className="flex flex-wrap gap-2">
          {EXCUSE_TYPE_OPTIONS.map((type) => {
            const selected = type === excuseType
            return (
              <button
                key={type}
                type="button"
                onClick={() => onExcuseType(type)}
                className="rounded-full border px-3 py-1.5 text-xs font-semibold"
                style={
                  selected
                    ? {
                        background: 'color-mix(in oklab, var(--accent-primary) 16%, transparent)',
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
                {EXCUSE_TYPE_LABELS[type]}
              </button>
            )
          })}
        </div>
      </div>

      <label className="block">
        <span
          className="mb-2 block text-xs font-semibold uppercase"
          style={{ color: 'var(--text-muted)' }}
        >
          Комментарий
        </span>
        <textarea
          value={comment}
          onChange={(event) => onComment(event.target.value)}
          rows={3}
          maxLength={1000}
          placeholder="Например: справка из поликлиники"
          className="w-full resize-none rounded-lg border px-3 py-2 text-sm outline-none"
          style={{
            background: 'var(--bg-surface)',
            borderColor: 'var(--border-subtle)',
            color: 'var(--text-primary)',
          }}
        />
      </label>

      <label
        className="flex cursor-pointer items-center gap-3 rounded-lg border px-3 py-3"
        style={{
          background: 'var(--bg-secondary)',
          borderColor: 'var(--border-subtle)',
          color: 'var(--text-secondary)',
        }}
      >
        <Paperclip size={18} weight="bold" aria-hidden="true" />
        <span className="min-w-0 flex-1 truncate text-sm">
          {file ? file.name : 'Прикрепить файл до 10 МБ'}
        </span>
        <input
          type="file"
          className="hidden"
          accept=".pdf,.jpg,.jpeg,.png,.heic,.doc,.docx"
          onChange={onFileChange}
        />
      </label>

      <button
        type="submit"
        disabled={isSubmitting}
        className="inline-flex min-h-11 items-center justify-center gap-2 rounded-full px-5 text-sm font-semibold"
        style={{
          background: 'var(--gradient-brand)',
          color: 'var(--accent-primary-contrast)',
          boxShadow: isSubmitting ? 'none' : 'var(--glow-primary)',
          opacity: isSubmitting ? 0.65 : 1,
        }}
      >
        {isSubmitting && <CircleNotch size={16} weight="bold" className="animate-spin" />}
        {isSubmitting ? 'Отправляем...' : 'Отправить'}
      </button>
    </form>
  )
}

function SuccessScreen({ kind, onClose }: { kind: SuccessKind; onClose: () => void }) {
  return (
    <div className="flex flex-col items-center gap-4 py-6 text-center">
      <span
        aria-hidden="true"
        className="grid size-16 place-items-center rounded-full border"
        style={{
          background: 'color-mix(in oklab, var(--accent-primary) 14%, transparent)',
          borderColor: 'var(--border-accent)',
          color: 'var(--accent-primary)',
        }}
      >
        <CheckCircle size={32} weight="fill" />
      </span>
      <div>
        <h3
          className="text-lg font-semibold"
          style={{ color: 'var(--text-primary)', fontFamily: 'var(--font-heading)' }}
        >
          {kind === 'excuse' ? 'Тикет отправлен' : 'Запрос отправлен'}
        </h3>
        <p className="mt-1 text-sm" style={{ color: 'var(--text-secondary)' }}>
          Статус обновится после решения старосты.
        </p>
      </div>
      <button
        type="button"
        onClick={onClose}
        className="rounded-full px-5 py-2.5 text-sm font-semibold"
        style={{
          background: 'var(--gradient-brand)',
          color: 'var(--accent-primary-contrast)',
          boxShadow: 'var(--glow-primary)',
        }}
      >
        Готово
      </button>
    </div>
  )
}

function InlineError({ text }: { text: string }) {
  return (
    <div
      role="alert"
      className="flex items-start gap-2 rounded-lg border px-3 py-2 text-xs"
      style={{
        background: 'color-mix(in oklab, var(--accent-danger) 10%, transparent)',
        borderColor: 'color-mix(in oklab, var(--accent-danger) 30%, transparent)',
        color: 'var(--accent-danger)',
      }}
    >
      <WarningCircle size={16} weight="bold" className="mt-0.5 shrink-0" aria-hidden="true" />
      <span>{text}</span>
    </div>
  )
}

function notify(kind: 'success' | 'error') {
  if (hapticFeedback.notificationOccurred.isAvailable()) {
    hapticFeedback.notificationOccurred(kind)
  }
}

function formatTime(time: string): string {
  return time.slice(0, 5)
}

function mapLessonActionError(status: number): string {
  switch (status) {
    case 400:
    case 422:
      return 'Проверьте выбранную пару и параметры запроса.'
    case 403:
      return 'Для старосты это действие выполняется через журнал.'
    case 404:
      return 'Пара не найдена или не относится к вашей группе.'
    case 409:
      return 'Для этой пары уже есть активный запрос.'
    case 413:
      return 'Файл должен быть не больше 10 МБ.'
    case 429:
      return 'Слишком много запросов. Подождите минуту.'
    default:
      return 'Не удалось выполнить действие. Попробуйте еще раз.'
  }
}
