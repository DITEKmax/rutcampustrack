import { createAdminMapFixtures } from './admin-map-fixtures'
import {
  createAdminActivityFixture,
  type AdminActivityFixtureVariant,
} from './admin-dashboard-fixtures'
import {
  AdminAccessClient,
  type AdminAccessProfile,
} from '../features/admin-access/admin-access-client'
import { AdminMapClient } from '../api/map-client'
import { AdminDashboardClient } from '../features/admin-dashboard/admin-dashboard-client'
import type { ProfilePort, ProfileSnapshot } from '../features/profile/profile-types'
import { ProfileRequestError } from '../features/profile/profile-types'
import { profileFixture, sessionsFixture, historyFixture } from './profile-map-fixtures'

export const adminShellStates = [
  'overview-today',
  'overview-all-time',
  'overview-empty',
  'overview-loading',
  'overview-error',
  'overview-weeks',
  'overview-metric-options',
  'overview-point',
  'overview-coverage-empty',
  'overview-coverage-partial',
  'overview-geo-zero',
  'overview-activity-error',
  'overview-activity-loading',
  'overview-activity-retry',
  'more',
  'more-map-statistics',
  'roles',
  'roles-pending',
  'roles-error',
  'profile',
  'profile-error',
  'appearance',
  'security',
  'sessions',
  'history',
  'authority-limited',
  'authority-missing',
  'authority-revoked',
  'authority-loading',
  'authority-error',
  'map-only',
  'map-delete',
  'map-proof-revoked',
] as const

export function createAdminShellFixtures(state: string) {
  const snapshot: ProfileSnapshot = {
    ...profileFixture(0, false, 'ADMIN'),
    displayName: 'Иванов Иван Иванович',
    groupLabel: null,
    roles: [
      { grantId: 'student', role: 'STUDENT', status: 'ACTIVE', selectable: true, readOnly: false },
      { grantId: 'headman', role: 'HEADMAN', status: 'ACTIVE', selectable: true, readOnly: false },
      { grantId: 'teacher', role: 'TEACHER', status: 'ACTIVE', selectable: true, readOnly: false },
      { grantId: 'admin', role: 'ADMIN', status: 'ACTIVE', selectable: true, readOnly: false },
    ],
  }
  let fail = state.endsWith('-error')
  let statsFail = state === 'overview-error'
  let activityFail = state === 'overview-activity-error' || state === 'overview-activity-retry'
  let activityRequests = 0
  let activitySuccesses = 0
  const dashboard = new AdminDashboardClient({
    accessToken: () => null,
    fetcher: async (input, init) => {
      const url = new URL(String(input), 'https://synthetic.invalid')
      if ((init?.method ?? 'GET') !== 'GET') throw new Error('Unexpected review API request')
      if (url.pathname === '/api/academic/dashboard/activity') {
        activityRequests++
        if (state === 'overview-activity-loading')
          await new Promise<void>((resolve, reject) => {
            const timer = window.setTimeout(resolve, 60000)
            init?.signal?.addEventListener(
              'abort',
              () => {
                clearTimeout(timer)
                reject(new DOMException('Aborted', 'AbortError'))
              },
              { once: true },
            )
          })
        if (activityFail) {
          activityFail = false
          return Response.json({ title: 'Недоступно' }, { status: 503 })
        }
        const period = url.searchParams.get('period') === 'all_time' ? 'all_time' : 'today'
        const bucket = url.searchParams.get('bucket') === 'week' ? 'week' : 'day'
        const variant: AdminActivityFixtureVariant =
          state === 'overview-empty'
            ? 'empty'
            : state === 'overview-coverage-empty'
              ? 'coverage-empty'
              : state === 'overview-coverage-partial'
                ? 'partial'
                : state === 'overview-geo-zero'
                  ? 'geo-zero'
                  : 'ready'
        activitySuccesses++
        return Response.json(createAdminActivityFixture(period, bucket, variant))
      }
      if (url.pathname !== '/api/academic/dashboard/stats')
        throw new Error('Unexpected review API request')
      if (state === 'overview-loading')
        await new Promise<void>((resolve, reject) => {
          const timer = window.setTimeout(resolve, 60000)
          init?.signal?.addEventListener(
            'abort',
            () => {
              clearTimeout(timer)
              reject(new DOMException('Aborted', 'AbortError'))
            },
            { once: true },
          )
        })
      if (statsFail) {
        statsFail = false
        return Response.json({ title: 'Недоступно' }, { status: 503 })
      }
      const empty = state === 'overview-empty'
      return Response.json({
        totalStudents: empty ? 0 : 22091,
        totalTeachers: empty ? 0 : 385,
        totalGroups: empty ? 0 : 142,
        activeGroups: empty ? 0 : 142,
        activeSemesterName: empty ? null : 'Осенний семестр 2026/2027',
      })
    },
  })
  let sessions = sessionsFixture.map((item) => ({ ...item }))
  const profile: ProfilePort = {
    async getSnapshot() {
      if (state === 'profile-error' && fail) {
        fail = false
        throw new ProfileRequestError('NETWORK', 'Не удалось загрузить профиль. Попробуй ещё раз.')
      }
      return snapshot
    },
    async selectRole(input) {
      if (state === 'roles-error' && fail) {
        fail = false
        throw new ProfileRequestError(
          'ROLE_NOT_SELECTABLE',
          'Роль пока недоступна. Попробуй ещё раз.',
        )
      }
      if (state === 'roles-pending') await new Promise((resolve) => setTimeout(resolve, 60000))
      snapshot.activeRole = input.role
      snapshot.sessionVersion = String(Number(input.expectedSessionVersion) + 1)
      return { session: snapshot, accessToken: 'synthetic-review-only', expiresIn: 3600 }
    },
    async listSessions() {
      return { items: sessions, nextCursor: null }
    },
    async listHistory() {
      return { items: historyFixture, nextCursor: null }
    },
    async terminateSession(id) {
      sessions = sessions.filter((item) => item.sessionId !== id)
    },
    async changePassword() {
      /* Synthetic ACK, no permanent data. */
    },
    async logoutAll() {
      /* Synthetic ACK, no permanent session. */
    },
    isOnline: () => true,
  }
  const authority: AdminAccessProfile = {
    userId: 1,
    assigned: true,
    mode: 'FULL',
    permissions: [
      'DASHBOARD_VIEW',
      'USERS_VIEW',
      'USERS_MANAGE',
      'USER_CREDENTIALS_MANAGE',
      'USERS_ARCHIVE',
      'GROUPS_VIEW',
      'GROUPS_MANAGE',
      'SEMESTERS_VIEW',
      'SEMESTERS_MANAGE',
      'CAMPUS_MAP_MANAGE',
      'DATA_DELETE',
      'ADMIN_ACCESS_MANAGE',
    ],
    version: 1,
  }
  let authorityReads = 0
  const access = new AdminAccessClient({
    accessToken: () => null,
    fetcher: async (input, init) => {
      if (String(input) !== '/api/academic/admin-access/me' || (init?.method ?? 'GET') !== 'GET')
        throw new Error('Unexpected authority review API request')
      authorityReads++
      if (state === 'authority-loading')
        await new Promise<void>((resolve, reject) => {
          const timer = window.setTimeout(resolve, 60000)
          init?.signal?.addEventListener(
            'abort',
            () => {
              clearTimeout(timer)
              reject(new DOMException('Aborted', 'AbortError'))
            },
            { once: true },
          )
        })
      if (state === 'authority-error' && authorityReads === 1)
        return Response.json(
          { code: 'ADMIN_AUTHORITY_UNAVAILABLE', detail: 'Не удалось подтвердить права доступа.' },
          { status: 503 },
        )
      if (state === 'authority-missing' || (state === 'authority-revoked' && authorityReads > 1))
        return Response.json({
          userId: 1,
          assigned: false,
          mode: null,
          permissions: [],
          version: 2,
        })
      if (state.startsWith('map-'))
        return Response.json({
          ...authority,
          mode: 'LIMITED',
          permissions: ['CAMPUS_MAP_MANAGE', ...(state === 'map-only' ? [] : ['DATA_DELETE'])],
          version: 2,
        })
      if (state === 'authority-limited')
        return Response.json({
          ...authority,
          mode: 'LIMITED',
          permissions: ['DASHBOARD_VIEW'],
          version: 2,
        })
      return Response.json(authority)
    },
  })
  const mapInventory = [
    {
      id: '20',
      code: '1',
      label: 'Корпус 1',
      floors: [
        {
          id: '200',
          buildingId: '20',
          code: '1',
          label: 'Этаж 1',
          currentPlan: null,
          openCount: 0,
          openCountPeriod: 'all_time',
        },
      ],
    },
  ]
  const map = state.startsWith('map-')
    ? createAdminMapFixtures(
        state === 'map-proof-revoked'
          ? 'proof-revoked'
          : state === 'map-delete'
            ? 'delete'
            : 'full',
      ).client
    : new AdminMapClient({
        accessToken: () => null,
        currentGeneration: () => 1,
        fetcher: async (input, init) => {
          const path = String(input)
          if (path === '/api/academic/map/buildings' && (init?.method ?? 'GET') === 'GET')
            return Response.json(mapInventory)
          throw new Error('Unexpected map review API request')
        },
      })
  return {
    dashboard,
    profile,
    snapshot,
    authority,
    access,
    map,
    activityCounters: () => ({ requests: activityRequests, successes: activitySuccesses }),
  }
}
