import type { MapFormatSlot, MapManifest, MapPlan } from '../api/types'
import { DEFAULT_PASSWORD_POLICY, type ProfileRole, type ProfileHistoryEvent, type ProfileSessionSummary, type ProfileSnapshot } from '../features/profile/profile-types'

export const profileMapScenarios = [
  ['profile', 'Профиль'], ['more', 'Ещё · 3 уведомления'], ['more-unread-zero', 'Ещё · 0 уведомлений'], ['more-unread-unknown', 'Ещё · счётчик неизвестен'], ['more-unread-many', 'Ещё · 124 уведомления'],
  ['headman-more', 'Староста · Ещё'], ['headman-more-partial', 'Староста · Ещё · недоступные разделы'], ['headman-profile', 'Староста · общий профиль'], ['headman-appearance', 'Староста · оформление'], ['headman-roles', 'Староста · сменить роль'], ['headman-roles-readonly', 'Староста · роль только чтение'], ['headman-security', 'Староста · безопасность'], ['headman-sessions', 'Староста · сеансы'], ['headman-history', 'Староста · история'], ['headman-map', 'Староста · карта'], ['roles', 'Сменить роль'], ['roles-offline', 'Роли · офлайн'], ['roles-error', 'Роли · отказ'], ['roles-pending', 'Роли · сохранение'], ['roles-readonly', 'Роли · только чтение'],
  ['appearance', 'Оформление'], ['security', 'Безопасность'], ['security-offline', 'Безопасность · офлайн'], ['security-error', 'Безопасность · отказ'], ['security-race', 'Безопасность · поздний ответ'],
  ['sessions', 'Сеансы'], ['sessions-empty', 'Сеансы · пусто'], ['sessions-error', 'Сеансы · ошибка'], ['sessions-offline', 'Сеансы · офлайн'], ['sessions-terminating', 'Сеансы · завершение другого'], ['sessions-current-terminating', 'Сеансы · завершение текущего'], ['sessions-page-error', 'Сеансы · частичная загрузка'], ['sessions-terminate-error', 'Сеансы · отказ завершения'], ['history', 'История'], ['history-empty', 'История · пусто'], ['history-error', 'История · ошибка'],
  ['profile-loading', 'Профиль · загрузка'], ['profile-error', 'Профиль · ошибка'], ['profile-long', 'Профиль · длинные строки'],
  ['map', 'Карта · нет схемы'], ['map-empty', 'Карта · нет корпусов'], ['map-no-floors', 'Карта · нет этажей'], ['map-loading', 'Карта · загрузка'], ['map-error', 'Карта · ошибка'], ['map-offline', 'Карта · офлайн'], ['map-processing', 'Карта · обработка'], ['map-failed', 'Карта · проверка файла'], ['map-formats', 'Карта · проверка форматов'], ['map-png', 'Карта · только PNG'], ['map-svg', 'Карта · только SVG'], ['map-usage-error', 'Карта · ошибка статистики'], ['map-long', 'Карта · длинные строки'],
] as const
export function fixtureSessionId(owner: number): string {
  return '10000000-0000-4000-8000-' + String(owner + 1).padStart(12, '0')
}
export function profileFixture(owner: number, long = false, activeRole: ProfileRole = 'STUDENT'): ProfileSnapshot {
  return { sessionId: fixtureSessionId(owner), userId: 'fixture-user-' + owner, displayName: long ? 'Александра Константинопольская-Воскресенская' : owner ? 'Анна Волкова' : 'Иван Кузнецов', groupLabel: activeRole === 'HEADMAN' ? 'ИУ6-42' : long ? 'Группа информационных технологий ИКБО-01-24 — учебный контекст' : 'ИКБО-01-24', sessionVersion: String(owner + 1), rolesVersion: '1', activeRole, readOnly: false, passwordPolicy: DEFAULT_PASSWORD_POLICY,
    roles: [ { grantId: 'student', role: 'STUDENT', status: 'ACTIVE', contextLabel: 'Иван Кузнецов', selectable: true, readOnly: false }, { grantId: 'headman', role: 'HEADMAN', status: 'ACTIVE', contextLabel: activeRole === 'HEADMAN' ? 'Группа ИУ6-42' : 'Группа ИКБО-10-23', selectable: true, readOnly: false }, { grantId: 'teacher', role: 'TEACHER', status: 'ACTIVE', contextLabel: 'Кафедра ИУ-5', selectable: true, readOnly: false } ] }
}
export const sessionsFixture: readonly ProfileSessionSummary[] = [
  { sessionId: fixtureSessionId(0), clientLabel: 'Windows · Chrome', authMethod: 'PASSWORD', locationLabel: null, createdAt: '2026-10-05T09:00:00Z', lastSeenAt: '2026-10-05T10:00:00Z', current: true },
  { sessionId: '20000000-0000-4000-8000-000000000001', clientLabel: 'Android · RutCampusTrack', authMethod: 'TMA', locationLabel: null, createdAt: '2026-10-04T09:00:00Z', lastSeenAt: '2026-10-05T08:00:00Z', current: false },
]
export const historyFixture: readonly ProfileHistoryEvent[] = [
  { id: 'login', type: 'LOGIN', occurredAt: '2026-10-05T09:00:00Z', clientLabel: 'Windows · Chrome', locationLabel: null, authMethod: 'OTP' },
  { id: 'password', type: 'PASSWORD_CHANGED', occurredAt: '2026-10-04T09:00:00Z' },
  { id: 'logout', type: 'CURRENT_LOGOUT', occurredAt: '2026-10-03T09:00:00Z', clientLabel: 'Chrome' },
]
export function mapSlot(format: 'svg' | 'png', state: MapFormatSlot['state']): MapFormatSlot {
  return { format, state, contentType: format === 'svg' ? 'image/svg+xml' : 'image/png', id: state === 'ready' ? 'fixture-' + format : null, bytes: 0, sha256: null, width: state === 'ready' ? 24 : null, height: state === 'ready' ? 24 : null, viewBox: null }
}
export function mapFixture(state: string): MapManifest {
  return { schemaVersion: 1, validationPolicyVersion: 1, revision: 'fixture-1', buildings: state === 'map-empty' ? [] : [ { id: 'building-5', label: state === 'map-long' ? 'Корпус 5 — учебный корпус с длинным названием' : 'Корпус 5', floors: state === 'map-no-floors' ? [] : ['3', '2', '1'].map((id) => ({ id, label: id + ' этаж', plan: null })) }, { id: 'building-6', label: 'Корпус 6', floors: [{ id: '1', label: '1 этаж', plan: null }] } ] }
}
export function mapPlanFixture(state: string, buildingId: string, floorId: string): MapPlan | null {
  if (['map', 'map-offline', 'map-long'].includes(state)) return null
  const ready = ['map-formats', 'map-png', 'map-svg', 'map-usage-error'].includes(state)
  const status = state === 'map-failed' ? 'failed' : state === 'map-processing' ? 'processing' : 'absent'
  return { buildingId, floorId, version: 'fixture-1', label: ready ? 'Проверка форматов: иконка, не схема кампуса' : 'Схема этажа', svg: mapSlot('svg', ready && state !== 'map-png' ? 'ready' : status), png: mapSlot('png', ready && state !== 'map-svg' ? 'ready' : status) }
}
