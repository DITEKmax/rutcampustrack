import type { MobileProblemDetails } from '../../api/types'
export const adminPermissionCodes = ['DASHBOARD_VIEW','USERS_VIEW','USERS_MANAGE','USER_CREDENTIALS_MANAGE','USERS_ARCHIVE','GROUPS_VIEW','GROUPS_MANAGE','SEMESTERS_VIEW','SEMESTERS_MANAGE','CAMPUS_MAP_MANAGE','DATA_DELETE','ADMIN_ACCESS_MANAGE'] as const
const knownPermission = (code: string) => (adminPermissionCodes as readonly string[]).includes(code)
export type AdminAccessMode = 'FULL' | 'LIMITED'
export interface AdminPermission { readonly code: string; readonly label: string; readonly group: string; readonly requires: readonly string[]; readonly fullOnly: boolean; readonly description: string }
export interface AdminAccessCatalog { readonly permissions: readonly AdminPermission[] }
export interface AdminAccessProfile { readonly userId: number; readonly assigned: boolean; readonly mode: AdminAccessMode | null; readonly permissions: readonly string[]; readonly version: number }
export interface AdminAccessSelection { readonly mode: AdminAccessMode; readonly permissions: readonly string[] }
export interface AdminAccessUpdate extends AdminAccessSelection { readonly expectedVersion: number }
export interface AdminAccessOptions { readonly accessToken: () => string | null; readonly onUnauthorized?: () => Promise<void>; readonly assertCurrent?: () => void; readonly fetcher?: typeof fetch }
export function adminHasPermission(profile: AdminAccessProfile | null | undefined, code: string): boolean { return knownPermission(code) && profile?.assigned === true && (profile.mode === 'FULL' || profile.mode === 'LIMITED' && profile.permissions.includes(code)) }
export class AdminAccessApiError extends Error {
  constructor(readonly response: Response, readonly problem: MobileProblemDetails | null, readonly currentProfile: AdminAccessProfile | null = null) { super(problem?.detail ?? 'Не удалось обновить права доступа.'); this.name = 'AdminAccessApiError' }
}
export class AdminAccessClient {
  private readonly fetcher: typeof fetch
  constructor(private readonly options: AdminAccessOptions) { this.fetcher = options.fetcher ?? ((input,init) => globalThis.fetch(input,init)) }
  async getCatalog(signal?: AbortSignal): Promise<AdminAccessCatalog> {
    const value = record(await this.request('/api/academic/admin-access/catalog',signal ? {signal} : undefined))
    if (!Array.isArray(value.permissions)) throw new Error('Не удалось загрузить доступные права.')
    const permissions = value.permissions.map(item => {
      const permission = record(item)
      if (typeof permission.fullOnly !== 'boolean' || !Array.isArray(permission.requires)) throw new Error('Не удалось загрузить доступные права.')
      return {code:textValue(permission.code),label:textValue(permission.label),group:textValue(permission.group),requires:permission.requires.map(textValue),fullOnly:permission.fullOnly,description:textValue(permission.description)}
    })
    if (new Set(permissions.map(item => item.code)).size !== permissions.length || permissions.some(item => item.requires.some(code => !permissions.some(candidate => candidate.code === code)))) throw new Error('Не удалось подтвердить состав прав.')
    return { permissions }
  }
  async getMe(signal?: AbortSignal): Promise<AdminAccessProfile> { return normalizeAdminAccessProfile(await this.request('/api/academic/admin-access/me',signal ? {signal} : undefined)) }
  async getUserAccess(userId: number,signal?: AbortSignal): Promise<AdminAccessProfile> { const profile = normalizeAdminAccessProfile(await this.request(this.path(userId),signal ? {signal} : undefined)); if (profile.userId !== userId) throw new Error('Не удалось подтвердить пользователя.'); return profile }
  async updateUserAccess(userId: number,input: AdminAccessUpdate,signal?: AbortSignal): Promise<AdminAccessProfile> {
    if (!Number.isSafeInteger(input.expectedVersion) || input.expectedVersion < 0 || !['FULL','LIMITED'].includes(input.mode) || input.mode === 'FULL' && input.permissions.length || input.mode === 'LIMITED' && !input.permissions.length) throw new Error('Проверь выбранные права доступа.')
    const profile = normalizeAdminAccessProfile(await this.request(this.path(userId),{method:'PUT',body:JSON.stringify(input),...(signal ? {signal} : {})}))
    if (profile.userId !== userId) throw new Error('Не удалось подтвердить пользователя.')
    return profile
  }
  async revokeUserAccess(userId: number,expectedVersion: number,signal?: AbortSignal): Promise<void> {
    if (!Number.isSafeInteger(expectedVersion) || expectedVersion < 0) throw new Error('Обнови текущие права доступа.')
    await this.request(this.path(userId),{method:'DELETE',body:JSON.stringify({expectedVersion}),...(signal ? {signal} : {})})
  }
  private path(userId: number): string { if (!Number.isSafeInteger(userId) || userId <= 0) throw new Error('Пользователь недоступен.'); return `/api/academic/users/${userId}/admin-access` }
  private async request(path: string,init?: RequestInit,retried = false): Promise<unknown> {
    this.options.assertCurrent?.()
    const headers = new Headers(init?.headers)
    headers.set('Accept','application/json')
    if (init?.body) headers.set('Content-Type','application/json')
    const token = this.options.accessToken()
    if (token) headers.set('Authorization',`Bearer ${token}`)
    const response = await this.fetcher(path,{...init,headers,credentials:'include',cache:'no-store'})
    this.options.assertCurrent?.()
    if (response.ok) { const value: unknown = response.status === 204 ? null : await response.json(); this.options.assertCurrent?.(); return value }
    if (response.status === 401 && !retried && this.options.onUnauthorized) { await this.options.onUnauthorized(); this.options.assertCurrent?.(); return this.request(path,init,true) }
    let problem: MobileProblemDetails | null = null, currentProfile: AdminAccessProfile | null = null
    try { const value = record(await response.json()); problem = value as MobileProblemDetails; if (value.currentProfile) { const profile = normalizeAdminAccessProfile(value.currentProfile); const target = path.match(/users\/(\d+)\/admin-access$/)?.[1]; if (!target || profile.userId === Number(target)) currentProfile = profile } } catch { /* HTTP status remains authoritative. */ }
    this.options.assertCurrent?.()
    throw new AdminAccessApiError(response,problem,currentProfile)
  }
}
export function normalizeAdminAccessProfile(value: unknown): AdminAccessProfile {
  const profile = record(value)
  if (!Number.isSafeInteger(profile.userId) || Number(profile.userId) <= 0 || typeof profile.assigned !== 'boolean' || !Number.isSafeInteger(profile.version) || Number(profile.version) < 0 || !Array.isArray(profile.permissions) || ![null,'FULL','LIMITED'].includes(profile.mode as null | string)) throw new Error('Не удалось подтвердить права доступа.')
  const permissions = profile.permissions.map(textValue)
  if (new Set(permissions).size !== permissions.length || permissions.some(code => !knownPermission(code)) || profile.mode === 'LIMITED' && (!permissions.length || permissions.includes('ADMIN_ACCESS_MANAGE')) || profile.mode === 'FULL' && permissions.length !== adminPermissionCodes.length) throw new Error('Не удалось подтвердить состав прав доступа.')
  if (profile.assigned && Number(profile.version) < 1) throw new Error('Не удалось подтвердить версию прав доступа.')
  if (profile.assigned && profile.mode === null || !profile.assigned && (profile.mode !== null || permissions.length)) throw new Error('Не удалось подтвердить права доступа.')
  return {userId:Number(profile.userId),assigned:profile.assigned,mode:profile.mode as AdminAccessMode | null,permissions,version:Number(profile.version)}
}
function record(value: unknown): Record<string,unknown> { if (!value || typeof value !== 'object' || Array.isArray(value)) throw new Error('Не удалось прочитать права доступа.'); return value as Record<string,unknown> }
function textValue(value: unknown): string { if (typeof value !== 'string' || !value.trim()) throw new Error('Не удалось прочитать права доступа.'); return value }
