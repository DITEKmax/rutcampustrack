import { useEffect, useMemo, useState } from 'react'
import {
  Archive,
  CaretDown,
  CircleNotch,
  FileText,
  Paperclip,
  Plus,
  Square,
  CheckSquare,
  WarningCircle,
  X,
} from '@phosphor-icons/react'
import { BottomSheet } from '@/shared/components/BottomSheet'
import { useSubjectMap } from '@/features/schedule/api'
import {
  EXCUSE_STATUS_LABELS,
  EXCUSE_TYPE_LABELS,
  EXCUSE_TYPE_OPTIONS,
  getHttpStatus,
  useStudentAttendanceRecords,
  useStudentExcuseTickets,
  useSubmitStudentExcuse,
  type AttendanceRecord,
  type ExcuseTicket,
  type ExcuseType,
} from '@/features/student/requests/studentRequestsApi'
import { cn } from '@/lib/utils'

type ExcuseTab = 'active' | 'archive'

interface DayGroup {
  date: string
  label: string
  records: AttendanceRecord[]
}

const ACTIVE_STATUSES: ExcuseTicket['status'][] = ['draft', 'submitted']
const ARCHIVE_STATUSES: ExcuseTicket['status'][] = ['approved', 'rejected']
const MAX_FILE_BYTES = 10 * 1024 * 1024

const DAY_NAMES = [
  'Воскресенье',
  'Понедельник',
  'Вторник',
  'Среда',
  'Четверг',
  'Пятница',
  'Суббота',
] as const
const MONTH_NAMES = [
  'января',
  'февраля',
  'марта',
  'апреля',
  'мая',
  'июня',
  'июля',
  'августа',
  'сентября',
  'октября',
  'ноября',
  'декабря',
] as const

function todayDateString(): string {
  const d = new Date()
  const y = d.getFullYear()
  const m = String(d.getMonth() + 1).padStart(2, '0')
  const day = String(d.getDate()).padStart(2, '0')
  return `${y}-${m}-${day}`
}

function isPastOrToday(isoDate: string): boolean {
  return isoDate <= todayDateString()
}

function formatDayLabel(isoDate: string): string {
  const d = new Date(`${isoDate}T00:00:00`)
  if (Number.isNaN(d.getTime())) return isoDate
  return `${DAY_NAMES[d.getDay()]}, ${d.getDate()} ${MONTH_NAMES[d.getMonth()]}`
}

function formatShortDateTime(value: string | null | undefined): string {
  if (!value) return ''
  const d = new Date(value)
  if (Number.isNaN(d.getTime())) return value
  return new Intl.DateTimeFormat('ru-RU', {
    day: '2-digit',
    month: '2-digit',
    year: 'numeric',
    hour: '2-digit',
    minute: '2-digit',
  }).format(d)
}

function groupByDay(records: AttendanceRecord[]): DayGroup[] {
  const byDate = new Map<string, AttendanceRecord[]>()
  for (const record of records) {
    const bucket = byDate.get(record.lessonDate) ?? []
    bucket.push(record)
    byDate.set(record.lessonDate, bucket)
  }
  return Array.from(byDate.entries())
    .sort(([a], [b]) => a.localeCompare(b))
    .map(([date, dayRecords]) => ({
      date,
      label: formatDayLabel(date),
      records: dayRecords.slice().sort((a, b) => a.lessonNumber - b.lessonNumber),
    }))
}

function statusSymbol(status: string): string {
  if (status === 'absent') return 'н'
  if (status === 'excused') return 'у'
  return '·'
}

function submitError(status: number): string {
  switch (status) {
    case 400:
    case 422:
      return 'Проверьте выбранные пары и причину.'
    case 403:
      return 'Староста проставляет уважительные пропуски через журнал.'
    case 409:
      return 'На одну из выбранных пар уже есть активный тикет.'
    case 413:
      return 'Файл должен быть не больше 10 МБ.'
    case 429:
      return 'Слишком много запросов. Подождите минуту.'
    default:
      return 'Не удалось подать тикет. Попробуйте еще раз.'
  }
}

export function StudentExcusesPage() {
  const ticketsQuery = useStudentExcuseTickets()
  const recordsQuery = useStudentAttendanceRecords()
  const [tab, setTab] = useState<ExcuseTab>('active')
  const [sheetOpen, setSheetOpen] = useState(false)
  const [expandedTicketId, setExpandedTicketId] = useState<string | null>(null)
  const [toast, setToast] = useState<string | null>(null)

  const records = recordsQuery.data ?? []
  const subjectIds = useMemo(
    () => records.map((record) => record.subjectId),
    [records],
  )
  const { subjectMap } = useSubjectMap(subjectIds)

  const recordByLessonId = useMemo(() => {
    const map = new Map<number, AttendanceRecord>()
    for (const record of records) map.set(record.lessonId, record)
    return map
  }, [records])

  const tickets = ticketsQuery.data ?? []
  const activeTickets = useMemo(
    () => tickets.filter((ticket) => ACTIVE_STATUSES.includes(ticket.status)),
    [tickets],
  )
  const archiveTickets = useMemo(
    () => tickets.filter((ticket) => ARCHIVE_STATUSES.includes(ticket.status)),
    [tickets],
  )
  const visibleTickets = tab === 'active' ? activeTickets : archiveTickets

  const eligibleRecords = useMemo(
    () =>
      records
        .filter(
          (record) =>
            (record.status === 'absent' || record.status === 'excused') &&
            isPastOrToday(record.lessonDate),
        )
        .sort(
          (a, b) =>
            a.lessonDate.localeCompare(b.lessonDate) ||
            a.lessonNumber - b.lessonNumber,
        ),
    [records],
  )

  const showToast = (text: string) => {
    setToast(text)
    window.setTimeout(() => setToast(null), 2600)
  }

  return (
    <div className="min-h-full px-4 py-5">
      <PageHeader onCreate={() => setSheetOpen(true)} />

      <Tabs
        tab={tab}
        onTabChange={(next) => {
          setTab(next)
          setExpandedTicketId(null)
        }}
        activeCount={activeTickets.length}
        archiveCount={archiveTickets.length}
      />

      {ticketsQuery.isLoading && <SkeletonList />}

      {!ticketsQuery.isLoading && ticketsQuery.isError && (
        <InlineError text="Не удалось загрузить тикеты. Обновите страницу." />
      )}

      {!ticketsQuery.isLoading && !ticketsQuery.isError && visibleTickets.length === 0 && (
        <EmptyTickets tab={tab} />
      )}

      {!ticketsQuery.isLoading && !ticketsQuery.isError && visibleTickets.length > 0 && (
        <div className="overflow-hidden rounded-lg border" style={{
          background: 'var(--bg-secondary)',
          borderColor: 'var(--border-subtle)',
        }}>
          {visibleTickets.map((ticket) => (
            <TicketRow
              key={ticket.id}
              ticket={ticket}
              expanded={expandedTicketId === ticket.id}
              onToggle={() =>
                setExpandedTicketId((current) => (current === ticket.id ? null : ticket.id))
              }
              lessonLabel={(lessonId) =>
                lessonLabel(lessonId, recordByLessonId, subjectMap)
              }
            />
          ))}
        </div>
      )}

      <ExcuseFormSheet
        open={sheetOpen}
        onClose={() => setSheetOpen(false)}
        loadingRecords={recordsQuery.isLoading}
        eligibleRecords={eligibleRecords}
        subjectMap={subjectMap}
        onSubmitted={() => showToast('Тикет отправлен старосте')}
      />

      {toast && (
        <div
          role="status"
          aria-live="polite"
          className="fixed left-1/2 z-[var(--z-toast)] -translate-x-1/2 rounded-full border px-4 py-2 text-sm font-medium"
          style={{
            bottom: 'calc(80px + env(safe-area-inset-bottom))',
            background: 'var(--bg-elevated)',
            borderColor: 'var(--border-default)',
            color: 'var(--text-primary)',
          }}
        >
          {toast}
        </div>
      )}
    </div>
  )
}

function PageHeader({ onCreate }: { onCreate: () => void }) {
  return (
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
          <FileText size={24} weight="duotone" aria-hidden="true" />
        </div>
        <div className="min-w-0">
          <p className="text-xs font-medium uppercase" style={{ color: 'var(--text-muted)' }}>
            Студент
          </p>
          <h1 className="text-2xl font-bold leading-tight text-balance">
            Уважительные пропуски
          </h1>
        </div>
      </div>

      <button
        type="button"
        onClick={onCreate}
        aria-label="Подать тикет"
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
  )
}

function Tabs({
  tab,
  onTabChange,
  activeCount,
  archiveCount,
}: {
  tab: ExcuseTab
  onTabChange: (tab: ExcuseTab) => void
  activeCount: number
  archiveCount: number
}) {
  return (
    <div
      role="tablist"
      aria-label="Фильтр тикетов"
      className="mb-5 inline-flex rounded-full border p-1"
      style={{ background: 'var(--bg-secondary)', borderColor: 'var(--border-subtle)' }}
    >
      <TabButton
        active={tab === 'active'}
        onClick={() => onTabChange('active')}
        label="Активные"
        count={activeCount}
      />
      <TabButton
        active={tab === 'archive'}
        onClick={() => onTabChange('archive')}
        label="Архив"
        count={archiveCount}
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

function TicketRow({
  ticket,
  expanded,
  onToggle,
  lessonLabel,
}: {
  ticket: ExcuseTicket
  expanded: boolean
  onToggle: () => void
  lessonLabel: (lessonId: number) => string
}) {
  return (
    <article className="border-b last:border-b-0" style={{ borderColor: 'var(--border-subtle)' }}>
      <button
        type="button"
        onClick={onToggle}
        aria-expanded={expanded}
        className="grid w-full grid-cols-[1fr_auto] items-center gap-3 px-4 py-3 text-left"
      >
        <div className="min-w-0">
          <div className="mb-1 flex flex-wrap items-center gap-2">
            <StatusChip status={ticket.status} />
            <span className="text-xs" style={{ color: 'var(--text-muted)' }}>
              {formatShortDateTime(ticket.createdAt)}
            </span>
          </div>
          <p
            className="truncate text-sm font-semibold"
            style={{ color: 'var(--text-primary)', fontFamily: 'var(--font-heading)' }}
          >
            {EXCUSE_TYPE_LABELS[ticket.excuseType]}
          </p>
          <p className="mt-0.5 text-xs" style={{ color: 'var(--text-muted)' }}>
            {ticket.lessonIds.length} пар(ы)
          </p>
        </div>
        <CaretDown
          size={18}
          weight="bold"
          aria-hidden="true"
          className={cn('transition-transform', expanded && 'rotate-180')}
          style={{ color: 'var(--text-secondary)' }}
        />
      </button>

      {expanded && (
        <div
          className="border-t px-4 pb-4 pt-3"
          style={{ borderColor: 'var(--border-subtle)', background: 'var(--bg-surface)' }}
        >
          <dl className="grid grid-cols-2 gap-3 text-sm">
            <div>
              <dt className="text-xs uppercase" style={{ color: 'var(--text-muted)' }}>
                Причина
              </dt>
              <dd className="mt-1">{EXCUSE_TYPE_LABELS[ticket.excuseType]}</dd>
            </div>
            <div>
              <dt className="text-xs uppercase" style={{ color: 'var(--text-muted)' }}>
                Статус
              </dt>
              <dd className="mt-1">{EXCUSE_STATUS_LABELS[ticket.status]}</dd>
            </div>
          </dl>

          {ticket.comment && (
            <section className="mt-3">
              <h3 className="text-xs uppercase" style={{ color: 'var(--text-muted)' }}>
                Комментарий
              </h3>
              <p className="mt-1 whitespace-pre-wrap text-sm" style={{ color: 'var(--text-secondary)' }}>
                {ticket.comment}
              </p>
            </section>
          )}

          {ticket.decisionComment && (
            <section className="mt-3">
              <h3 className="text-xs uppercase" style={{ color: 'var(--text-muted)' }}>
                Решение старосты
              </h3>
              <p className="mt-1 whitespace-pre-wrap text-sm" style={{ color: 'var(--text-secondary)' }}>
                {ticket.decisionComment}
              </p>
            </section>
          )}

          {ticket.decisionAt && (
            <p className="mt-3 text-xs" style={{ color: 'var(--text-muted)' }}>
              Решение: {formatShortDateTime(ticket.decisionAt)}
            </p>
          )}

          <section className="mt-3">
            <h3 className="text-xs uppercase" style={{ color: 'var(--text-muted)' }}>
              Пары
            </h3>
            <ul className="mt-1 flex flex-col gap-1 text-sm" style={{ color: 'var(--text-secondary)' }}>
              {ticket.lessonIds.map((lessonId) => (
                <li key={lessonId}>{lessonLabel(lessonId)}</li>
              ))}
            </ul>
          </section>
        </div>
      )}
    </article>
  )
}

function StatusChip({ status }: { status: ExcuseTicket['status'] }) {
  const color =
    status === 'approved'
      ? 'var(--accent-info)'
      : status === 'rejected'
        ? 'var(--accent-danger)'
        : status === 'submitted'
          ? 'var(--accent-warning)'
          : 'var(--text-muted)'
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

function ExcuseFormSheet({
  open,
  onClose,
  loadingRecords,
  eligibleRecords,
  subjectMap,
  onSubmitted,
}: {
  open: boolean
  onClose: () => void
  loadingRecords: boolean
  eligibleRecords: AttendanceRecord[]
  subjectMap: Record<number, string>
  onSubmitted: () => void
}) {
  const submit = useSubmitStudentExcuse()
  const [selectedIds, setSelectedIds] = useState<Set<number>>(new Set())
  const [excuseType, setExcuseType] = useState<ExcuseType | ''>('')
  const [comment, setComment] = useState('')
  const [file, setFile] = useState<File | null>(null)
  const [fileInputKey, setFileInputKey] = useState(0)
  const [validationError, setValidationError] = useState<string | null>(null)
  const [fileError, setFileError] = useState<string | null>(null)

  useEffect(() => {
    if (!open) {
      setSelectedIds(new Set())
      setExcuseType('')
      setComment('')
      setFile(null)
      setFileInputKey((key) => key + 1)
      setValidationError(null)
      setFileError(null)
    }
  }, [open])

  const dayGroups = useMemo(() => groupByDay(eligibleRecords), [eligibleRecords])

  const toggleLesson = (lessonId: number) => {
    setSelectedIds((prev) => {
      const next = new Set(prev)
      if (next.has(lessonId)) next.delete(lessonId)
      else next.add(lessonId)
      return next
    })
    setValidationError(null)
  }

  const onFileSelected = (event: React.ChangeEvent<HTMLInputElement>) => {
    const nextFile = event.target.files?.[0] ?? null
    setFileError(null)
    if (!nextFile) {
      setFile(null)
      return
    }
    if (nextFile.size > MAX_FILE_BYTES) {
      setFile(null)
      setFileError('Файл должен быть не больше 10 МБ.')
      setFileInputKey((key) => key + 1)
      return
    }
    setFile(nextFile)
  }

  const clearFile = () => {
    setFile(null)
    setFileError(null)
    setFileInputKey((key) => key + 1)
  }

  const submitForm = async () => {
    const lessonIds = Array.from(selectedIds)
    if (lessonIds.length === 0) {
      setValidationError('Выберите хотя бы одну пару.')
      return
    }
    if (!excuseType) {
      setValidationError('Выберите причину пропуска.')
      return
    }
    setValidationError(null)
    try {
      await submit.mutateAsync({
        lessonIds,
        excuseType,
        comment: comment.trim() || null,
        file,
      })
      onSubmitted()
      onClose()
    } catch (error) {
      setValidationError(submitError(getHttpStatus(error)))
    }
  }

  return (
    <BottomSheet
      open={open}
      onClose={onClose}
      title="Новый тикет"
      heading="Новый тикет"
      subtitle={selectedIds.size > 0 ? `Выбрано: ${selectedIds.size}` : 'Выберите пары и причину'}
      maxHeight="92vh"
    >
      <div className="flex flex-col gap-5 px-4 pb-5">
        <section>
          <label className="mb-1.5 block text-sm font-semibold" htmlFor="excuse-type">
            Причина
          </label>
          <select
            id="excuse-type"
            value={excuseType}
            onChange={(event) => {
              setExcuseType(event.target.value as ExcuseType)
              setValidationError(null)
            }}
            className="h-11 w-full rounded-lg border bg-transparent px-3 text-sm outline-none"
            style={{
              borderColor: 'var(--border-default)',
              color: 'var(--text-primary)',
              background: 'var(--bg-primary)',
            }}
          >
            <option value="">Выберите причину</option>
            {EXCUSE_TYPE_OPTIONS.map((type) => (
              <option key={type} value={type}>
                {EXCUSE_TYPE_LABELS[type]}
              </option>
            ))}
          </select>
        </section>

        <section>
          <label className="mb-1.5 block text-sm font-semibold" htmlFor="excuse-comment">
            Комментарий
          </label>
          <textarea
            id="excuse-comment"
            value={comment}
            maxLength={1000}
            onChange={(event) => setComment(event.target.value)}
            className="min-h-[88px] w-full resize-none rounded-lg border bg-transparent px-3 py-2 text-sm outline-none"
            style={{
              borderColor: 'var(--border-default)',
              color: 'var(--text-primary)',
              background: 'var(--bg-primary)',
            }}
            placeholder="Необязательно"
          />
        </section>

        <section>
          <label className="mb-1.5 block text-sm font-semibold" htmlFor="excuse-file">
            Документ
          </label>
          <input
            key={fileInputKey}
            id="excuse-file"
            type="file"
            onChange={onFileSelected}
            className="hidden"
          />
          {file ? (
            <div
              className="flex items-center justify-between gap-3 rounded-lg border px-3 py-2"
              style={{ borderColor: 'var(--border-default)', background: 'var(--bg-primary)' }}
            >
              <span className="min-w-0 truncate text-sm">
                <Paperclip size={16} weight="bold" className="mr-1 inline" aria-hidden="true" />
                {file.name}
              </span>
              <button
                type="button"
                onClick={clearFile}
                aria-label="Убрать файл"
                className="grid size-8 shrink-0 place-items-center rounded-full"
                style={{ color: 'var(--text-secondary)' }}
              >
                <X size={16} weight="bold" aria-hidden="true" />
              </button>
            </div>
          ) : (
            <label
              htmlFor="excuse-file"
              className="inline-flex min-h-10 cursor-pointer items-center gap-2 rounded-full border px-4 text-sm font-semibold"
              style={{ borderColor: 'var(--border-default)', color: 'var(--text-primary)' }}
            >
              <Paperclip size={17} weight="bold" aria-hidden="true" />
              Прикрепить файл
            </label>
          )}
          {fileError && (
            <p className="mt-2 text-xs" style={{ color: 'var(--accent-danger)' }} role="alert">
              {fileError}
            </p>
          )}
          <p className="mt-2 text-xs" style={{ color: 'var(--text-muted)' }}>
            Файл отправится старосте в Telegram и не сохранится в системе.
          </p>
        </section>

        <section>
          <h3 className="mb-2 text-sm font-semibold">Пары</h3>
          {loadingRecords && <SkeletonList compact />}
          {!loadingRecords && eligibleRecords.length === 0 && (
            <div className="rounded-lg border px-4 py-6 text-center text-sm" style={{
              borderColor: 'var(--border-subtle)',
              color: 'var(--text-secondary)',
            }}>
              Нет прошедших пар со статусом «н» или «у».
            </div>
          )}
          {!loadingRecords && dayGroups.length > 0 && (
            <div className="flex flex-col gap-4">
              {dayGroups.map((group) => (
                <div key={group.date}>
                  <p className="mb-1.5 text-xs font-semibold lowercase" style={{ color: 'var(--text-muted)' }}>
                    {group.label}
                  </p>
                  <div className="overflow-hidden rounded-lg border" style={{
                    borderColor: 'var(--border-subtle)',
                    background: 'var(--bg-primary)',
                  }}>
                    {group.records.map((record) => (
                      <LessonSelectRow
                        key={record.lessonId}
                        record={record}
                        subjectName={subjectMap[record.subjectId] ?? 'Предмет'}
                        selected={selectedIds.has(record.lessonId)}
                        onToggle={() => toggleLesson(record.lessonId)}
                      />
                    ))}
                  </div>
                </div>
              ))}
            </div>
          )}
        </section>

        {validationError && (
          <p className="text-sm" style={{ color: 'var(--accent-danger)' }} role="alert">
            {validationError}
          </p>
        )}

        <button
          type="button"
          onClick={() => { void submitForm() }}
          disabled={submit.isPending}
          className="inline-flex min-h-11 w-full items-center justify-center gap-2 rounded-lg text-sm font-semibold"
          style={{
            background: 'var(--accent-primary)',
            color: 'var(--accent-primary-contrast)',
            opacity: submit.isPending ? 0.7 : 1,
          }}
        >
          {submit.isPending && (
            <CircleNotch size={18} weight="bold" className="animate-spin" aria-hidden="true" />
          )}
          Отправить тикет
        </button>
      </div>
    </BottomSheet>
  )
}

function LessonSelectRow({
  record,
  subjectName,
  selected,
  onToggle,
}: {
  record: AttendanceRecord
  subjectName: string
  selected: boolean
  onToggle: () => void
}) {
  return (
    <button
      type="button"
      onClick={onToggle}
      aria-pressed={selected}
      className="flex w-full items-center gap-3 border-b px-3 py-3 text-left last:border-b-0"
      style={{
        borderColor: 'var(--border-subtle)',
        background: selected
          ? 'color-mix(in oklab, var(--accent-primary) 10%, transparent)'
          : 'transparent',
      }}
    >
      {selected ? (
        <CheckSquare size={22} weight="fill" aria-hidden="true" style={{ color: 'var(--accent-primary)' }} />
      ) : (
        <Square size={22} weight="regular" aria-hidden="true" style={{ color: 'var(--text-muted)' }} />
      )}
      <div className="min-w-0 flex-1">
        <p className="truncate text-sm font-semibold">{subjectName}</p>
        <p className="mt-0.5 text-xs" style={{ color: 'var(--text-muted)' }}>
          Пара №{record.lessonNumber}
        </p>
      </div>
      <span
        className="inline-flex min-w-6 justify-center rounded border px-1.5 py-0.5 text-[11px] font-semibold"
        style={{
          color: record.status === 'absent' ? 'var(--accent-danger)' : 'var(--accent-warning)',
          borderColor:
            record.status === 'absent'
              ? 'color-mix(in oklab, var(--accent-danger) 35%, transparent)'
              : 'color-mix(in oklab, var(--accent-warning) 45%, transparent)',
          background:
            record.status === 'absent'
              ? 'color-mix(in oklab, var(--accent-danger) 10%, transparent)'
              : 'color-mix(in oklab, var(--accent-warning) 12%, transparent)',
        }}
      >
        {statusSymbol(record.status)}
      </span>
    </button>
  )
}

function lessonLabel(
  lessonId: number,
  recordByLessonId: Map<number, AttendanceRecord>,
  subjectMap: Record<number, string>,
): string {
  const record = recordByLessonId.get(lessonId)
  if (!record) return `Пара #${lessonId}`
  const subject = subjectMap[record.subjectId] ?? 'Предмет'
  return `${record.lessonDate} · №${record.lessonNumber} · ${subject}`
}

function EmptyTickets({ tab }: { tab: ExcuseTab }) {
  return (
    <div className="flex flex-col items-center justify-center py-14 text-center">
      <div
        className="mb-4 grid size-20 place-items-center rounded-full border"
        style={{
          background: 'var(--bg-secondary)',
          borderColor: 'var(--border-subtle)',
          color: tab === 'active' ? 'var(--accent-secondary)' : 'var(--text-muted)',
        }}
      >
        {tab === 'active' ? (
          <FileText size={38} weight="duotone" aria-hidden="true" />
        ) : (
          <Archive size={38} weight="duotone" aria-hidden="true" />
        )}
      </div>
      <h2 className="text-lg font-semibold">
        {tab === 'active' ? 'Нет активных тикетов' : 'Архив пуст'}
      </h2>
      <p className="mt-2 max-w-[280px] text-sm" style={{ color: 'var(--text-secondary)' }}>
        {tab === 'active'
          ? 'Поданные заявки появятся здесь до решения старосты.'
          : 'Одобренные и отклоненные тикеты будут храниться здесь.'}
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

function SkeletonList({ compact = false }: { compact?: boolean }) {
  return (
    <div className="flex flex-col gap-3" aria-label="Загрузка">
      {Array.from({ length: compact ? 2 : 4 }).map((_, index) => (
        <div
          key={index}
          className={cn('animate-pulse rounded-lg', compact ? 'h-[58px]' : 'h-[76px]')}
          style={{ background: 'var(--bg-secondary)' }}
        />
      ))}
    </div>
  )
}
