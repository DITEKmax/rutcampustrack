import { useMemo, useState } from 'react'
import {
  CalendarBlank,
  DownloadSimple,
  Files,
  WarningCircle,
} from '@phosphor-icons/react'
import { BottomSheet } from '@/shared/components/BottomSheet'
import { useAuth } from '@/features/auth/AuthProvider'
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
      className="grid grid-cols-3 gap-1 rounded-lg border border-[var(--border-subtle)] bg-[var(--bg-surface)] p-1"
    >
      {FORMATS.map((format) => (
        <button
          key={format}
          type="button"
          aria-pressed={value === format}
          onClick={() => onChange(format)}
          className="h-9 rounded-md px-3 text-xs font-bold transition-colors"
          style={{
            background: value === format ? 'var(--accent-primary)' : 'transparent',
            color:
              value === format
                ? 'var(--accent-primary-contrast)'
                : 'var(--text-secondary)',
            fontFamily: 'var(--font-mono)',
          }}
        >
          {format.toUpperCase()}
        </button>
      ))}
    </div>
  )
}

export function HeadmanWeeklyReportCard() {
  const { user } = useAuth()
  const isHeadman = !!user?.isHeadman
  const weeksQuery = useHeadmanWeeklyReportWeeks(isHeadman)
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

  if (!isHeadman) return null

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
    } catch (err) {
      setActionError(errorMessageFor((err as { response?: { status?: number } })?.response?.status))
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
    } catch (err) {
      setActionError(errorMessageFor((err as { response?: { status?: number } })?.response?.status))
    }
  }

  return (
    <section
      className="mt-8 rounded-lg border border-[var(--border-subtle)] bg-[var(--bg-secondary)] p-4"
      aria-labelledby="headman-weekly-report-title"
    >
      <div className="flex items-start gap-3">
        <div
          className="grid size-10 shrink-0 place-items-center rounded-lg"
          style={{
            background: 'color-mix(in oklab, var(--accent-primary) 14%, transparent)',
            color: 'var(--accent-primary)',
          }}
        >
          <Files size={22} weight="fill" aria-hidden="true" />
        </div>
        <div className="min-w-0 flex-1">
          <h2
            id="headman-weekly-report-title"
            className="text-base font-semibold"
            style={{ fontFamily: 'var(--font-heading)' }}
          >
            Отчеты старосты
          </h2>
          <p className="mt-1 text-sm text-[var(--text-secondary)]">
            Скачивание недельного отчета группы в DOCX, PDF или PNG.
          </p>
        </div>
      </div>

      {weeksQuery.isLoading ? (
        <div className="mt-4 flex items-center gap-3 text-sm text-[var(--text-muted)]">
          <CalendarBlank size={18} aria-hidden="true" />
          Загружаем недели активного семестра…
        </div>
      ) : weeksQuery.isError || weeks.length === 0 ? (
        <div
          className="mt-4 flex items-center gap-2 rounded-lg border p-3 text-sm"
          style={{
            borderColor: 'color-mix(in oklab, var(--accent-danger) 32%, transparent)',
            background: 'color-mix(in oklab, var(--accent-danger) 10%, transparent)',
            color: 'var(--accent-danger)',
          }}
          role="alert"
        >
          <WarningCircle size={18} aria-hidden="true" />
          Не удалось загрузить недели активного семестра.
        </div>
      ) : (
        <>
          <div className="mt-5 grid gap-3">
            <label className="grid gap-2 text-sm">
              <span className="font-medium text-[var(--text-primary)]">Неделя</span>
              <select
                value={effectiveWeekStart}
                onChange={(event) => setSelectedWeekStart(event.target.value)}
                className="h-11 rounded-lg border border-[var(--border-default)] bg-[var(--bg-surface)] px-3 text-sm text-[var(--text-primary)]"
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
              onClick={handleSingleDownload}
              disabled={!effectiveWeekStart || downloadWeek.isPending}
              className="inline-flex h-11 items-center justify-center gap-2 rounded-lg bg-[var(--accent-primary)] px-4 text-sm font-semibold text-[var(--accent-primary-contrast)] disabled:opacity-55"
            >
              <DownloadSimple size={18} weight="bold" aria-hidden="true" />
              {downloadWeek.isPending ? 'Готовим файл…' : 'Скачать неделю'}
            </button>

            <button
              type="button"
              onClick={openMultiWeekSheet}
              className="inline-flex h-11 items-center justify-center gap-2 rounded-lg border border-[var(--border-default)] bg-[var(--bg-surface)] px-4 text-sm font-semibold text-[var(--text-primary)]"
            >
              <Files size={18} aria-hidden="true" />
              Скачать несколько недель
            </button>
          </div>

          <p className="mt-3 text-xs text-[var(--text-muted)]">{selectedLabel}</p>

          {actionError && (
            <div
              className="mt-4 flex items-center gap-2 rounded-lg border p-3 text-sm"
              style={{
                borderColor: 'color-mix(in oklab, var(--accent-danger) 32%, transparent)',
                background: 'color-mix(in oklab, var(--accent-danger) 10%, transparent)',
                color: 'var(--accent-danger)',
              }}
              role="alert"
            >
              <WarningCircle size={18} aria-hidden="true" />
              {actionError}
            </div>
          )}
        </>
      )}

      <BottomSheet
        open={sheetOpen}
        onClose={() => setSheetOpen(false)}
        title="Скачать несколько недель"
        heading="Скачать несколько недель"
        subtitle={weeksQuery.data?.semesterName}
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
                      : 'var(--bg-surface)',
                  }}
                >
                  <input
                    type="checkbox"
                    checked={checked}
                    onChange={(event) => toggleMultiWeek(week.weekStart, event.target.checked)}
                    className="size-5 accent-[var(--accent-primary)]"
                  />
                  <span className="min-w-0 flex-1">
                    <span className="block text-sm font-semibold text-[var(--text-primary)]">
                      {week.label || `Н${week.weekOfSemester}`}
                      {week.current ? (
                        <span className="ml-2 rounded-full bg-[var(--bg-elevated)] px-2 py-0.5 text-[0.68rem] font-medium text-[var(--accent-secondary)]">
                          текущая
                        </span>
                      ) : null}
                    </span>
                    <span className="block text-xs text-[var(--text-secondary)]">
                      {formatWeekRange(week)}
                    </span>
                  </span>
                </label>
              )
            })}
          </div>

          {actionError && (
            <div
              className="flex items-center gap-2 rounded-lg border p-3 text-sm"
              style={{
                borderColor: 'color-mix(in oklab, var(--accent-danger) 32%, transparent)',
                background: 'color-mix(in oklab, var(--accent-danger) 10%, transparent)',
                color: 'var(--accent-danger)',
              }}
              role="alert"
            >
              <WarningCircle size={18} aria-hidden="true" />
              {actionError}
            </div>
          )}

          <button
            type="button"
            onClick={handleMultiDownload}
            disabled={exportWeeks.isPending || multiSelected.length === 0}
            className="inline-flex h-11 items-center justify-center gap-2 rounded-lg bg-[var(--accent-primary)] px-4 text-sm font-semibold text-[var(--accent-primary-contrast)] disabled:opacity-55"
          >
            <DownloadSimple size={18} weight="bold" aria-hidden="true" />
            {exportWeeks.isPending ? 'Готовим файл…' : 'Скачать выбранные недели'}
          </button>
        </div>
      </BottomSheet>
    </section>
  )
}
