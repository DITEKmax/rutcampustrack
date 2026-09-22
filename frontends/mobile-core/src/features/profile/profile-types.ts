export type ProfileRole = 'STUDENT' | 'HEADMAN' | 'TEACHER' | 'ADMIN'
export type ProfileRoleStatus = 'ACTIVE' | 'SUSPENDED' | 'EXPELLED' | 'GRADUATED' | 'DISMISSED' | 'ARCHIVED'
export type ProfileTheme = 'light' | 'dark' | 'system'
export type ProfileResolvedTheme = 'light' | 'dark'
export type ProfileRoute = 'statistics' | 'map' | 'requests' | 'profile' | 'role-switch' | 'appearance' | 'security' | 'sessions' | 'history'
export type ProfileAuthMethod = 'PASSWORD' | 'OTP' | 'TMA'
export type ProfileHistoryType = 'LOGIN' | 'ROLE_CHANGED' | 'CURRENT_LOGOUT' | 'LOGOUT_ALL' | 'PASSWORD_CHANGED' | 'SECURITY_REVOKED'

export interface ProfileRoleGrant {
  grantId: string
  role: ProfileRole
  status: ProfileRoleStatus
  groupId?: string | null
  contextLabel?: string | null
  selectable: boolean
  readOnly: boolean
}

export interface ProfilePasswordPolicy {
  minCodePoints: number
  maxUtf8Bytes: number
  requiresDecimalDigit: boolean
  specialCategories: readonly ('P' | 'S')[]
  normalization: 'NONE'
}

export interface ProfileSnapshot {
  sessionId: string
  userId: string
  displayName: string
  groupLabel?: string | null
  sessionVersion: string
  rolesVersion: string
  activeRole: ProfileRole | null
  roles: readonly ProfileRoleGrant[]
  readOnly: boolean
  passwordPolicy: ProfilePasswordPolicy
}

export interface ProfileRoleSelection {
  accessToken: string
  expiresIn: number
  session: ProfileSnapshot
}

export interface ProfilePageRequest {
  cursor?: string
  limit?: number
}

export interface ProfileSessionSummary {
  sessionId: string
  authMethod?: ProfileAuthMethod | null
  clientLabel?: string | null
  locationLabel?: string | null
  createdAt: string
  lastSeenAt: string
  current: boolean
}

export interface ProfileSessionsPage {
  items: readonly ProfileSessionSummary[]
  nextCursor: string | null
}

export interface ProfileHistoryEvent {
  id: string
  type: ProfileHistoryType
  occurredAt: string
  authMethod?: ProfileAuthMethod | null
  clientLabel?: string | null
  locationLabel?: string | null
}

export interface ProfileHistoryPage {
  items: readonly ProfileHistoryEvent[]
  nextCursor: string | null
}

export type ProfileRequestErrorCode =
  | 'SESSION_STATE_STALE'
  | 'SESSION_VERSION_CONFLICT'
  | 'REFRESH_ALREADY_ROTATED'
  | 'ROLE_NOT_GRANTED'
  | 'ROLE_NOT_SELECTABLE'
  | 'ROLE_READ_ONLY'
  | 'BOOTSTRAP_SCOPE_DENIED'
  | 'CURRENT_PASSWORD_INVALID'
  | 'PASSWORD_POLICY_VIOLATION'
  | 'INVALID_CURSOR'
  | 'AUTHORITY_UNAVAILABLE'
  | 'INVALID_SESSION'
  | 'SESSION_REVOKED'
  | 'REFRESH_REJECTED'
  | 'OFFLINE_MUTATION_DISABLED'
  | 'ACCOUNT_INVALIDATED'
  | 'NETWORK'
  | 'UNKNOWN'

export class ProfileRequestError extends Error {
  readonly code: ProfileRequestErrorCode
  readonly status: number | undefined
  readonly details: unknown

  constructor(code: ProfileRequestErrorCode, message: string, status?: number, details?: unknown) {
    super(message)
    this.name = 'ProfileRequestError'
    this.code = code
    this.status = status
    this.details = details
  }
}

export interface ProfilePort {
  getSnapshot(): Promise<ProfileSnapshot>
  selectRole(input: { role: ProfileRole; expectedSessionVersion: string }): Promise<ProfileRoleSelection>
  listSessions(input?: ProfilePageRequest): Promise<ProfileSessionsPage>
  listHistory(input?: ProfilePageRequest): Promise<ProfileHistoryPage>
  changePassword(input: { currentPassword: string; newPassword: string }): Promise<void>
  logoutAll(): Promise<void>
  recoverPassword?(): void | Promise<void>
  navigate?(route: ProfileRoute): void | Promise<void>
  setTheme?(theme: ProfileTheme): void | Promise<void>
  onInvalidated?(reason: 'logout-all' | 'password-changed' | 'account-invalidated'): void | Promise<void>
  onRefreshAlreadyRotated?(error: ProfileRequestError): void | Promise<void>
  isOnline(): boolean
}

export const DEFAULT_PASSWORD_POLICY: ProfilePasswordPolicy = {
  minCodePoints: 12,
  maxUtf8Bytes: 72,
  requiresDecimalDigit: true,
  specialCategories: ['P', 'S'],
  normalization: 'NONE',
}

const ROLE_LABELS: Record<ProfileRole, string> = {
  STUDENT: 'Студент',
  HEADMAN: 'Староста',
  TEACHER: 'Преподаватель',
  ADMIN: 'Администратор',
}

const STATUS_LABELS: Record<ProfileRoleStatus, string> = {
  ACTIVE: 'Активна',
  SUSPENDED: 'Приостановлена',
  EXPELLED: 'Отчислен',
  GRADUATED: 'Выпустился',
  DISMISSED: 'Уволен',
  ARCHIVED: 'Архивная',
}

const HISTORY_LABELS: Record<ProfileHistoryType, string> = {
  LOGIN: 'Вход в аккаунт',
  ROLE_CHANGED: 'Смена роли',
  CURRENT_LOGOUT: 'Выход с устройства',
  LOGOUT_ALL: 'Выход со всех устройств',
  PASSWORD_CHANGED: 'Смена пароля',
  SECURITY_REVOKED: 'Сессии отозваны',
}

export function roleLabel(role: ProfileRole): string {
  return ROLE_LABELS[role]
}

export function statusLabel(status: ProfileRoleStatus): string {
  return STATUS_LABELS[status]
}

export function historyLabel(type: ProfileHistoryType): string {
  return HISTORY_LABELS[type]
}

export function displayInitials(displayName: string): string {
  const words = displayName.trim().split(/\s+/u).filter(Boolean)
  if (words.length === 0) return '?'
  const first = words[0]?.[0] ?? '?'
  const second = words.length > 1 ? (words.at(-1)?.[0] ?? '') : ''
  return `${first}${second}`.toLocaleUpperCase('ru-RU')
}

export function roleInitial(role: ProfileRole): string {
  return roleLabel(role).slice(0, 1)
}

export function truthfulMetadata(...values: readonly (string | null | undefined)[]): string | null {
  const parts = values.map((value) => value?.trim()).filter((value): value is string => Boolean(value))
  return parts.length > 0 ? parts.join(' · ') : null
}

export function formatProfileInstant(value: string): string {
  const parsed = new Date(value)
  if (Number.isNaN(parsed.getTime())) return value
  return parsed.toLocaleString('ru-RU', { dateStyle: 'medium', timeStyle: 'short' })
}

function hasUnpairedSurrogate(value: string): boolean {
  for (let index = 0; index < value.length; index += 1) {
    const code = value.charCodeAt(index)
    if (code >= 0xd800 && code <= 0xdbff) {
      const next = value.charCodeAt(index + 1)
      if (!(next >= 0xdc00 && next <= 0xdfff)) return true
      index += 1
    } else if (code >= 0xdc00 && code <= 0xdfff) {
      return true
    }
  }
  return false
}

export type PasswordValidationIssue = 'UNPAIRED_SURROGATE' | 'MIN_CODE_POINTS' | 'MAX_UTF8_BYTES' | 'DECIMAL_DIGIT' | 'SPECIAL_CHARACTER'

export function validateNewPassword(value: string, policy: ProfilePasswordPolicy): readonly PasswordValidationIssue[] {
  const issues: PasswordValidationIssue[] = []
  if (hasUnpairedSurrogate(value)) issues.push('UNPAIRED_SURROGATE')
  const codePoints = Array.from(value).length
  if (codePoints < policy.minCodePoints) issues.push('MIN_CODE_POINTS')
  if (!hasUnpairedSurrogate(value) && new TextEncoder().encode(value).byteLength > policy.maxUtf8Bytes) issues.push('MAX_UTF8_BYTES')
  if (policy.requiresDecimalDigit && !/\p{Nd}/u.test(value)) issues.push('DECIMAL_DIGIT')
  if (!policy.specialCategories.some((category) => (category === 'P' ? /\p{P}/u : /\p{S}/u).test(value))) issues.push('SPECIAL_CHARACTER')
  return issues
}
