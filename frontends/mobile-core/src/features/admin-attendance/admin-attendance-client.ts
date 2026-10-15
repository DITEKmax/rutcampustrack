import type { AdminDashboardApiOptions } from '../admin-dashboard/admin-dashboard-client'
import { AdminDashboardApiError } from '../admin-dashboard/admin-dashboard-client'
import type { ReportFormatOption } from '../../shared/report-download-client'

export const adminAttendanceSorts = [
  'GROUP_NAME',
  'PRESENT_PERCENT',
  'PRESENT_OR_EXCUSED_PERCENT',
  'COUNTED_LESSONS',
  'PRESENT',
  'ABSENT',
  'EXCUSED',
] as const
export type AdminAttendanceSort = (typeof adminAttendanceSorts)[number]
export interface AdminAttendanceSemester {
  readonly id: string
  readonly name: string
  readonly dateFrom: string
  readonly dateTo: string
  readonly active: boolean
  readonly archived: boolean
}
export interface AdminAttendanceQuery {
  readonly semesterId: string
  readonly search: string
  readonly sort: AdminAttendanceSort
  readonly direction: 'ASC' | 'DESC'
  readonly page: number
  readonly size: number
}
export interface AdminAttendanceRow {
  readonly groupId: string
  readonly groupName: string
  readonly countedLessons: number
  readonly presentCount: number
  readonly absentCount: number
  readonly excusedCount: number
  readonly opportunities: number
  readonly presentPercent: number | null
  readonly presentOrExcusedPercent: number | null
  readonly dataState: 'AVAILABLE' | 'NO_DATA'
}
export interface AdminAttendanceReport extends AdminAttendanceQuery {
  readonly semester: AdminAttendanceSemester
  readonly totalElements: number
  readonly totalPages: number
  readonly observedAt: string
  readonly rows: readonly AdminAttendanceRow[]
}
function record(value: unknown): Record<string, unknown> {
  if (!value || typeof value !== 'object' || Array.isArray(value))
    throw new Error('Некорректный ответ отчёта посещаемости.')
  return value as Record<string, unknown>
}
function text(value: unknown): string {
  if (typeof value !== 'string') throw new Error('Некорректный текст отчёта посещаемости.')
  return value
}
function id(value: unknown): string {
  const result = text(value)
  if (!/^[1-9][0-9]{0,18}$/.test(result)) throw new Error('Некорректный идентификатор отчёта.')
  return result
}
function count(value: unknown): number {
  if (typeof value !== 'number' || !Number.isSafeInteger(value) || value < 0)
    throw new Error('Некорректное количество в отчёте.')
  return value
}
function rate(value: unknown): number | null {
  if (value === null) return null
  if (typeof value !== 'number' || !Number.isFinite(value) || value < 0 || value > 100)
    throw new Error('Некорректный процент в отчёте.')
  return value
}
function semester(value: unknown): AdminAttendanceSemester {
  const row = record(value)
  const dateFrom = text(row.dateFrom),
    dateTo = text(row.dateTo)
  if (
    ![dateFrom, dateTo].every((day) => /^\d{4}-\d{2}-\d{2}$/.test(day)) ||
    dateFrom > dateTo ||
    typeof row.active !== 'boolean' ||
    typeof row.archived !== 'boolean'
  )
    throw new Error('Некорректный учебный период отчёта.')
  return {
    id: id(row.id),
    name: text(row.name),
    dateFrom,
    dateTo,
    active: row.active,
    archived: row.archived,
  }
}
function reportRow(value: unknown): AdminAttendanceRow {
  const row = record(value),
    opportunities = count(row.opportunities),
    presentPercent = rate(row.presentPercent),
    presentOrExcusedPercent = rate(row.presentOrExcusedPercent)
  if (
    !(
      row.dataState === 'NO_DATA' &&
      opportunities === 0 &&
      presentPercent === null &&
      presentOrExcusedPercent === null
    ) &&
    !(
      row.dataState === 'AVAILABLE' &&
      opportunities > 0 &&
      presentPercent !== null &&
      presentOrExcusedPercent !== null
    )
  )
    throw new Error('Состояние данных отчёта не подтверждено.')
  return {
    groupId: id(row.groupId),
    groupName: text(row.groupName),
    countedLessons: count(row.countedLessons),
    presentCount: count(row.presentCount),
    absentCount: count(row.absentCount),
    excusedCount: count(row.excusedCount),
    opportunities,
    presentPercent,
    presentOrExcusedPercent,
    dataState: row.dataState as AdminAttendanceRow['dataState'],
  }
}
/** Server-owned metrics and filtering; no client attendance formula or registry capability. */
export class AdminAttendanceClient {
  private readonly fetcher: typeof fetch
  constructor(private readonly options: AdminDashboardApiOptions) {
    this.fetcher = options.fetcher ?? ((input, init) => globalThis.fetch(input, init))
  }
  async getSemesters(signal?: AbortSignal): Promise<readonly AdminAttendanceSemester[]> {
    const result = record(await this.request('/semesters', signal))
    if (!Array.isArray(result.items)) throw new Error('Список семестров отчёта недоступен.')
    const rows = result.items.map(semester)
    if (new Set(rows.map((row) => row.id)).size !== rows.length)
      throw new Error('Список семестров отчёта неоднозначен.')
    return rows
  }
  async getReport(
    query: AdminAttendanceQuery,
    signal?: AbortSignal,
  ): Promise<AdminAttendanceReport> {
    const params = new URLSearchParams({
      semesterId: id(query.semesterId),
      search: query.search,
      sort: query.sort,
      direction: query.direction,
      page: String(query.page),
      size: String(query.size),
    })
    const value = record(await this.request(`?${params}`, signal))
    const selected = semester(value.semester)
    if (
      selected.id !== query.semesterId ||
      value.search !== query.search ||
      value.sort !== query.sort ||
      value.direction !== query.direction ||
      value.page !== query.page ||
      value.size !== query.size ||
      !Array.isArray(value.rows)
    )
      throw new Error('Срез отчёта не совпал с выбранными фильтрами.')
    const rows = value.rows.map(reportRow)
    if (rows.length > query.size || new Set(rows.map((row) => row.groupId)).size !== rows.length)
      throw new Error('Строки отчёта неоднозначны.')
    return {
      ...query,
      semester: selected,
      rows,
      totalElements: count(value.totalElements),
      totalPages: count(value.totalPages),
      observedAt: text(value.observedAt),
    }
  }
  async getFormats(): Promise<readonly ReportFormatOption[]> {
    const value = await this.request('/formats')
    if (!Array.isArray(value)) throw new Error('Форматы отчёта недоступны.')
    const rows = value.map((item) => {
      const row = record(item),
        code = text(row.code)
      if (!['html', 'pdf', 'docx', 'xlsx', 'png'].includes(code))
        throw new Error('Неизвестный формат отчёта.')
      return {
        code: code as ReportFormatOption['code'],
        label: text(row.label),
        mime: text(row.mime),
        extension: text(row.extension),
      }
    })
    if (new Set(rows.map((row) => row.code)).size !== 5 || rows.length !== 5)
      throw new Error('Перечень форматов отчёта неполон.')
    return rows
  }
  private async request(suffix: string, signal?: AbortSignal, retried = false): Promise<unknown> {
    this.options.assertCurrent?.()
    const token = this.options.accessToken()
    const response = await this.fetcher(`/api/academic/admin/attendance${suffix}`, {
      headers: {
        Accept: 'application/json',
        ...(token ? { Authorization: `Bearer ${token}` } : {}),
      },
      credentials: 'include',
      cache: 'no-store',
      ...(signal ? { signal } : {}),
    })
    this.options.assertCurrent?.()
    if (response.status === 401 && !retried && this.options.onUnauthorized) {
      await this.options.onUnauthorized()
      this.options.assertCurrent?.()
      return this.request(suffix, signal, true)
    }
    if (!response.ok) throw new AdminDashboardApiError(response, null)
    const value: unknown = await response.json()
    this.options.assertCurrent?.()
    return value
  }
}
