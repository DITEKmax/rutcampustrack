import {
  normalizeAdminDashboardActivity,
  type AdminDashboardActivity,
  type AdminActivityPeriod,
  type AdminActivityBucket,
} from './admin-dashboard-activity'
export type * from './admin-dashboard-activity'
import type { MobileProblemDetails } from '../../api/types'

export interface AdminDashboardDailyGrowth {
  readonly status: 'AVAILABLE' | 'UNKNOWN_HISTORY'
  readonly baselineDate: string | null
  readonly studentsDelta: number | null
  readonly teachersDelta: number | null
  readonly groupsDelta: number | null
}

export interface AdminDashboardStats {
  readonly totalStudents: number
  readonly totalTeachers: number
  readonly totalGroups: number
  readonly activeGroups: number
  readonly activeSemesterName: string | null
  readonly dailyGrowth?: AdminDashboardDailyGrowth
}

export interface AdminDashboardApiOptions {
  readonly accessToken: () => string | null
  readonly onUnauthorized?: () => Promise<void>
  readonly assertCurrent?: () => void
  readonly fetcher?: typeof fetch
}

export class AdminDashboardApiError extends Error {
  constructor(
    readonly response: Response,
    readonly problem: MobileProblemDetails | null,
  ) {
    super(problem?.detail || problem?.title || `HTTP ${response.status}`)
    this.name = 'AdminDashboardApiError'
  }
}

/** Read-only ADMIN dashboard summary for JS-ADMIN-11. */
export class AdminDashboardClient {
  private static readonly path = '/api/academic/dashboard/stats'
  private readonly fetcher: typeof fetch

  constructor(private readonly options: AdminDashboardApiOptions) {
    this.fetcher = options.fetcher ?? ((input, init) => globalThis.fetch(input, init))
  }

  getStats(signal?: AbortSignal): Promise<AdminDashboardStats> {
    return this.request<unknown>(AdminDashboardClient.path, signal ? { signal } : undefined).then(
      normalizeDashboardStats,
    )
  }

  getActivity(
    period: AdminActivityPeriod,
    bucket: AdminActivityBucket,
    signal?: AbortSignal,
  ): Promise<AdminDashboardActivity> {
    const query = new URLSearchParams({ period, bucket })
    return this.request<unknown>(
      `/api/academic/dashboard/activity?${query}`,
      signal ? { signal } : undefined,
    ).then((value) => normalizeAdminDashboardActivity(value, period, bucket))
  }

  private async request<T>(path: string, init?: RequestInit, retried = false): Promise<T> {
    const response = await this.requestResponse(path, init, retried)
    const value = (await response.json()) as T
    this.options.assertCurrent?.()
    return value
  }

  private async requestResponse(
    path: string,
    init?: RequestInit,
    retried = false,
  ): Promise<Response> {
    this.options.assertCurrent?.()
    const token = this.options.accessToken()
    const headers = new Headers(init?.headers)
    if (!headers.has('Accept')) headers.set('Accept', 'application/json')
    if (token) headers.set('Authorization', `Bearer ${token}`)
    const response = await this.fetcher(path, {
      ...init,
      cache: 'no-store',
      headers,
      credentials: 'include',
    })
    this.options.assertCurrent?.()
    if (response.ok) return response
    if (response.status === 401 && !retried && this.options.onUnauthorized) {
      await this.options.onUnauthorized()
      return this.requestResponse(path, init, true)
    }
    let problem: MobileProblemDetails | null = null
    try {
      problem = (await response.json()) as MobileProblemDetails
    } catch {
      // Keep the HTTP status when the gateway cannot return Problem Details.
    }
    this.options.assertCurrent?.()
    throw new AdminDashboardApiError(response, problem)
  }
}

function normalizeDashboardStats(value: unknown): AdminDashboardStats {
  const record = requiredRecord(value, 'dashboard statistics')
  const totalGroups = nonNegativeInteger(record.totalGroups, 'totalGroups')
  const activeGroups = nonNegativeInteger(record.activeGroups, 'activeGroups')
  if (totalGroups !== activeGroups) {
    throw new Error('Сервер вернул несогласованные данные действующих групп.')
  }

  const activeSemesterName = record.activeSemesterName
  if (activeSemesterName !== null && typeof activeSemesterName !== 'string') {
    throw new Error('Сервер вернул некорректное название активного семестра.')
  }

  return {
    totalStudents: nonNegativeInteger(record.totalStudents, 'totalStudents'),
    totalTeachers: nonNegativeInteger(record.totalTeachers, 'totalTeachers'),
    totalGroups,
    activeGroups,
    activeSemesterName:
      typeof activeSemesterName === 'string' && activeSemesterName.trim() !== ''
        ? activeSemesterName
        : null,
    dailyGrowth: normalizeDailyGrowth(record.dailyGrowth),
  }
}

function normalizeDailyGrowth(value: unknown): AdminDashboardDailyGrowth {
  if (value === undefined || value === null) {
    // Older servers/constructor-based fixtures have no prospective history extension.
    return {
      status: 'UNKNOWN_HISTORY',
      baselineDate: null,
      studentsDelta: null,
      teachersDelta: null,
      groupsDelta: null,
    }
  }
  const record = requiredRecord(value, 'dailyGrowth')
  const status = record.status
  const date = record.baselineDate
  if (
    (status !== 'AVAILABLE' && status !== 'UNKNOWN_HISTORY') ||
    typeof date !== 'string' ||
    !/^\d{4}-\d{2}-\d{2}$/.test(date) ||
    !Number.isFinite(Date.parse(`${date}T00:00:00Z`)) ||
    new Date(`${date}T00:00:00Z`).toISOString().slice(0, 10) !== date
  ) {
    throw new Error('Сервер вернул некорректную базу суточного прироста.')
  }
  const delta = (field: 'studentsDelta' | 'teachersDelta' | 'groupsDelta'): number | null => {
    const result = record[field]
    if (
      status === 'UNKNOWN_HISTORY'
        ? result !== null
        : typeof result !== 'number' || !Number.isSafeInteger(result)
    ) {
      throw new Error('Сервер вернул некорректный суточный прирост.')
    }
    return result as number | null
  }
  return {
    status,
    baselineDate: date,
    studentsDelta: delta('studentsDelta'),
    teachersDelta: delta('teachersDelta'),
    groupsDelta: delta('groupsDelta'),
  }
}

function requiredRecord(value: unknown, field: string): Record<string, unknown> {
  if (typeof value !== 'object' || value === null || Array.isArray(value)) {
    throw new Error(`Сервер вернул некорректные данные ${field}.`)
  }
  return value as Record<string, unknown>
}

function nonNegativeInteger(value: unknown, field: string): number {
  if (typeof value !== 'number' || !Number.isSafeInteger(value) || value < 0) {
    throw new Error(`Сервер вернул некорректное поле ${field}.`)
  }
  return value
}
