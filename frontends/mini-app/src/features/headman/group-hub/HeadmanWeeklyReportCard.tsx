import { useMemo, useState } from 'react'
import {
  CalendarBlank,
  DownloadSimple,
  Files,
  WarningCircle,
} from '@phosphor-icons/react'
import { BottomSheet } from '@/shared/components/BottomSheet'
import {
  useDownloadHeadmanWeeklyReport,
  useExportHeadmanWeeklyReport,
  useHeadmanWeeklyReportWeeks,
} from '@/features/headman/shared/headmanApi'
import type {
  HeadmanWeeklyReportFormat,
  HeadmanWeeklyWeekOption,
  ReportBlobResponse,
} from '@/features/headman/shared/types'

const FORMATS: HeadmanWeeklyReportFormat[] = ['docx', 'pdf', 'png']
const FALLBACK_EXT: Record<HeadmanWeeklyReportFormat, string> = {
  docx: 'docx',
  pdf: 'pdf',
  png: 'png',
}

function headerValue(headers: unknown, name: string): string | null {
  const getter = (headers as { get?: (key: string) => unknown } | null)?.get
  const fromGetter = typeof getter === 'function' ? getter.call(headers, name) : null
  if (typeof fromGetter === 'string') return fromGetter
  const record = headers as Record<string, unknown> | null
  const value = record?.[name] ?? record?.[name.toLowerCase()]
  if (Array.isArray(value)) return String(value[0] ?? '')
  return typeof value === 'string' ? value : null
}

export function filenameFromContentDisposition(header: string | null): string | null {
  if (!header) return null
  const encoded = /filename\*\s*=\s*UTF-8''([^;]+)/i.exec(header)
  if (encoded?.[1]) {
    try {
      return decodeURIComponent(encoded[1].replace(/^"|"$/g, ''))
    } catch {
      return encoded[1].replace(/^"|"$/g, '')
    }
  }
  const plain = /filename\s*=\s*"?([^";]+)"?/i.exec(header)
  return plain?.[1] ?? null
}

export function saveReportBlobResponse(
  response: ReportBlobResponse,
  fallbackFilename: string,
): string {
  const filename =
    filenameFromContentDisposition(headerValue(response.headers, 'content-disposition')) ??
    fallbackFilename
  const url = URL.createObjectURL(response.data)
  const link = document.createElement('a')
  link.href = url
  link.download = filename
  link.target = '_blank'
  link.rel = 'noopener'
  document.body.appendChild(link)
  link.click()
  link.remove()
  window.setTimeout(() => URL.revokeObjectURL(url), 30_000)
  return filename
}

function formatIsoDate(iso: string): string {
  const [year, month, day] = iso.split('-')
  if (!year || !month || !day) return iso
  return `${day}.${month}.${year}`
}

function formatWeekRange(week: HeadmanWeeklyWeekOption): string {
  return `${formatIsoDate(week.weekStart)} - ${formatIsoDate(week.weekEnd)}`
}

function errorMessageFor(status?: number): string {
  if (status === 422) return 'Отчет не помещается в шаблон или неделя вне семестра.'
  if (status === 503) return 'Сервис подготовки PDF/PNG временно недоступен.'
  if (status === 403) return 'Скачивание доступно только старосте группы.'
  return 'Не удалось скачать отчет. Попробуйте позже.'
}

export function HeadmanWeeklyReportCard() {
  const weeksQuery = useHeadmanWeeklyReportWeeks(true)
  const downloadWeek = useDownloadHeadmanWeeklyReport()
  const exportWeeks = useExportHeadmanWeeklyReport()
  const weeks = weeksQuery.data?.weeks ?? []
  const defaultWeek = useMemo(
    () => weeks.find((week) => week.current) ?? weeks[0] ?? null,
    [weeks],
  )
  const [selectedWeekStart, setSelectedWeekStart] = useState('')
  const [singleFormat, setSingleFormat] = useState<HeadmanWeeklyReportFormat>('docx')
  const [multiFormat, setMultiFormat] = useState<HeadmanWeeklyReportFormat>('docx')
  const [multiSelected, setMultiSelected] = useState<string[]>([])
  const [sheetOpen, setSheetOpen] = useState(false)
  const [actionError, setActionError] = useState<string | null>(null)

  const effectiveWeekStart = selectedWeekStart || defaultWeek?.weekStart || ''
  const selectedWeek = weeks.find((week) => week.weekStart === effectiveWeekStart) ?? defaultWeek
  const selectedLabel = selectedWeek
    ? `${selectedWeek.label || `Н${selectedWeek.weekOfSemester}`} · ${formatWeekRange(selectedWeek)}`
    : 'Неделя не выбрана'

  const openMultiWeekSheet = () => {
    setActionError(null)
    setMultiFormat(singleFormat)
    if (multiSelected.length === 0 && defaultWeek) {
      setMultiSelected([defaultWeek.weekStart])
    }
    setSheetOpen(true)
  }

  const toggleMultiWeek = (weekStart: string, checked: boolean) => {
    const selected = new Set(multiSelected)
    if (checked) selected.add(weekStart)
    else selected.delete(weekStart)
    setMultiSelected(weeks.map((week) => week.weekStart).filter((start) => selected.has(start)))
    setActionError(null)
  }

  const handleSingleDownload = async () => {
    if (!effectiveWeekStart) return
    setActionError(null)
    try {
      const response = await downloadWeek.mutateAsync({
        weekStart: effectiveWeekStart,
        format: singleFormat,
      })
      saveReportBlobResponse(
        response,
        `weekly-report-${effectiveWeekStart}.${FALLBACK_EXT[singleFormat]}`,
      )
    } catch (error) {
      setActionError(errorMessageFor(getHttpStatus(error)))
    }
  }

  const handleMultiDownload = async () => {
    if (multiSelected.length === 0) {
      setActionError('Выберите хотя бы одну неделю.')
      return
    }
    setActionError(null)
    try {
      const response = await exportWeeks.mutateAsync({
        weekStarts: multiSelected,
        format: multiFormat,
      })
      saveReportBlobResponse(response, `weekly-report.${FALLBACK_EXT[multiFormat]}`)
      setSheetOpen(false)
    } catch (error) {
      setActionError(errorMessageFor(getHttpStatus(error)))
    }
  }

  return (
    <section
      className="mt-4 rounded-lg border px-3 py-3"
      style={{ background: 'var(--bg-secondary)', borderColor: 'var(--border-subtle)' }}
      aria-labelledby="headman-weekly-report-title"
    >
      <div className="flex items-start gap-3">
        <div
          className="grid size-11 shrink-0 place-items-center rounded-lg border"
          style={{
            background: 'color-mix(in oklab, var(--accent-primary) 12%, transparent)',
            borderColor: 'var(--border-accent)',
            color: 'var(--accent-primary)',
          }}
        >
          <Files size={23} weight="duotone" aria-hidden="true" />
        </div>
        <div className="min-w-0 flex-1">
          <h2
            id="headman-weekly-report-title"
            className="text-sm font-semibold"
            style={{ color: 'var(--text-primary)', fontFamily: 'var(--font-heading)' }}
          >
            Отчеты старосты
          </h2>
          <p className="mt-0.5 text-xs" style={{ color: 'var(--text-muted)' }}>
            Недельный отчет группы в DOCX, PDF или PNG.
          </p>
        </div>
      </div>

      {weeksQuery.isLoading && (
        <div className="mt-4 flex items-center gap-2 text-sm" style={{ color: 'var(--text-muted)' }}>
          <CalendarBlank size={18} aria-hidden="true" />
          Загружаем недели семестра...
        </div>
      )}

      {!weeksQuery.isLoading && (weeksQuery.isError || weeks.length === 0) && (
        <InlineError text="Не удалось загрузить недели активного семестра." />
      )}

      {!weeksQuery.isLoading && !weeksQuery.isError && weeks.length > 0 && (
        <div className="mt-4 grid gap-3">
          <label className="grid gap-1.5 text-xs font-medium" style={{ color: 'var(--text-secondary)' }}>
            Неделя
            <select
              value={effectiveWeekStart}
              onChange={(event) => setSelectedWeekStart(event.target.value)}
              className="min-h-11 rounded-lg border px-3 text-sm outline-none"
              style={{
                background: 'var(--bg-primary)',
                borderColor: 'var(--border-subtle)',
                color: 'var(--text-primary)',
              }}
            >
              {weeks.map((week) => (
                <option key={week.weekStart} value={week.weekStart}>
                  {week.label || `Н${week.weekOfSemester}`} · {formatWeekRange(week)}
                </option>
              ))}
            </select>
          </label>

          <ReportFormatToggle
            value={singleFormat}
            onChange={setSingleFormat}
            ariaLabel="Формат отчета за одну неделю"
          />

          <button
            type="button"
            onClick={() => void handleSingleDownload()}
            disabled={!effectiveWeekStart || downloadWeek.isPending}
            className="inline-flex min-h-11 items-center justify-center gap-2 rounded-lg px-4 text-sm font-semibold disabled:opacity-55"
            style={{ background: 'var(--accent-primary)', color: 'var(--accent-primary-contrast)' }}
          >
            <DownloadSimple size={18} weight="bold" aria-hidden="true" />
            {downloadWeek.isPending ? 'Готовим файл...' : 'Скачать неделю'}
          </button>

          <button
            type="button"
            onClick={openMultiWeekSheet}
            className="inline-flex min-h-11 items-center justify-center gap-2 rounded-lg border px-4 text-sm font-semibold"
            style={{
              background: 'var(--bg-primary)',
              borderColor: 'var(--border-subtle)',
              color: 'var(--text-primary)',
            }}
          >
            <Files size={18} aria-hidden="true" />
            Скачать несколько недель
          </button>

          <p className="text-xs" style={{ color: 'var(--text-muted)' }}>
            {selectedLabel}
          </p>

          {actionError && <InlineError text={actionError} />}
        </div>
      )}

      <BottomSheet
        open={sheetOpen}
        onClose={() => setSheetOpen(false)}
        title="Скачать несколько недель"
        heading="Скачать несколько недель"
        subtitle={weeksQuery.data?.semesterName ?? undefined}
      >
        <div className="grid gap-4 px-5 pb-5">
          <ReportFormatToggle
            value={multiFormat}
            onChange={setMultiFormat}
            ariaLabel="Формат отчета за несколько недель"
          />

          <div className="grid max-h-[42vh] gap-2 overflow-y-auto pr-1">
            {weeks.map((week) => {
              const checked = multiSelected.includes(week.weekStart)
              return (
                <label
                  key={week.weekStart}
                  className="flex items-center gap-3 rounded-lg border p-3"
                  style={{
                    borderColor: checked
                      ? 'color-mix(in oklab, var(--accent-primary) 50%, transparent)'
                      : 'var(--border-subtle)',
                    background: checked
                      ? 'color-mix(in oklab, var(--accent-primary) 10%, transparent)'
                      : 'var(--bg-primary)',
                  }}
                >
                  <input
                    type="checkbox"
                    checked={checked}
                    onChange={(event) => toggleMultiWeek(week.weekStart, event.target.checked)}
                    className="size-5 accent-[var(--accent-primary)]"
                  />
                  <span className="min-w-0 flex-1">
                    <span className="block text-sm font-semibold" style={{ color: 'var(--text-primary)' }}>
                      {week.label || `Н${week.weekOfSemester}`}
                      {week.current && (
                        <span
                          className="ml-2 rounded-full px-2 py-0.5 text-[10px] font-medium"
                          style={{
                            background: 'var(--bg-elevated)',
                            color: 'var(--accent-secondary)',
                          }}
                        >
                          текущая
                        </span>
                      )}
                    </span>
                    <span className="block text-xs" style={{ color: 'var(--text-secondary)' }}>
                      {formatWeekRange(week)}
                    </span>
                  </span>
                </label>
              )
            })}
          </div>

          {actionError && <InlineError text={actionError} />}

          <button
            type="button"
            onClick={() => void handleMultiDownload()}
            disabled={exportWeeks.isPending || multiSelected.length === 0}
            className="inline-flex min-h-11 items-center justify-center gap-2 rounded-lg px-4 text-sm font-semibold disabled:opacity-55"
            style={{ background: 'var(--accent-primary)', color: 'var(--accent-primary-contrast)' }}
          >
            <DownloadSimple size={18} weight="bold" aria-hidden="true" />
            {exportWeeks.isPending ? 'Готовим файл...' : 'Скачать выбранные недели'}
          </button>
        </div>
      </BottomSheet>
    </section>
  )
}

function ReportFormatToggle({
  value,
  onChange,
  ariaLabel,
}: {
  value: HeadmanWeeklyReportFormat
  onChange: (value: HeadmanWeeklyReportFormat) => void
  ariaLabel: string
}) {
  return (
    <div
      role="group"
      aria-label={ariaLabel}
      className="grid grid-cols-3 gap-1 rounded-lg border p-1"
      style={{ background: 'var(--bg-primary)', borderColor: 'var(--border-subtle)' }}
    >
      {FORMATS.map((format) => (
        <button
          key={format}
          type="button"
          aria-pressed={value === format}
          onClick={() => onChange(format)}
          className="min-h-9 rounded-md px-3 text-xs font-bold transition-colors"
          style={{
            background: value === format ? 'var(--accent-primary)' : 'transparent',
            color: value === format ? 'var(--accent-primary-contrast)' : 'var(--text-secondary)',
            fontFamily: 'var(--font-mono)',
          }}
        >
          {format.toUpperCase()}
        </button>
      ))}
    </div>
  )
}

function InlineError({ text }: { text: string }) {
  return (
    <div
      role="alert"
      className="mt-3 flex items-start gap-2 rounded-lg border px-3 py-2 text-xs"
      style={{
        background: 'color-mix(in oklab, var(--accent-danger) 9%, transparent)',
        borderColor: 'color-mix(in oklab, var(--accent-danger) 35%, transparent)',
        color: 'var(--accent-danger)',
      }}
    >
      <WarningCircle size={16} weight="fill" className="mt-0.5 shrink-0" aria-hidden="true" />
      <span>{text}</span>
    </div>
  )
}

function getHttpStatus(error: unknown): number | undefined {
  return (error as { response?: { status?: number } })?.response?.status
}
