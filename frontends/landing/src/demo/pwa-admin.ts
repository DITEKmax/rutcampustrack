import { createAdminGroupsReviewApi } from '../../../mobile-core/src/dev/admin-groups-fixtures'
import { createAdminSemesterReviewApi } from '../../../mobile-core/src/dev/admin-semester-fixtures'
import { createAdminUsersReviewApis } from '../../../mobile-core/src/dev/admin-users-fixtures'
import { adminPreview } from '../../../web-vue/src/dev/admin-visual-preview'
import type { PreviewHandler } from '../../../pwa-vue/src/dev/interactive-mock-transport'

export function createAdminDemo(): PreviewHandler {
  const groups = createAdminGroupsReviewApi('list')
  const semesters = createAdminSemesterReviewApi('list')
  const users = createAdminUsersReviewApis('detail-multirole')
  const credentials = new Map<string, { userId: string; credentialsVersion: string; state: string; origin: string | null; password: string | null }>()
  return async (url, method, body, role, source, headers) => {
    if (role !== 'ADMIN') return null
    const path = url.pathname
    const request = { method, headers, ...(source ? { body: source } : {}) }
    const credential = path.match(/^\/api\/auth\/admin\/users\/(\d+)\/(initial-password|generated-password|password-reset-link)$/)
    if (credential) {
      const id = credential[1]!
      if (credential[2] === 'password-reset-link') {
        const expiresInSeconds = 3600
        const expiresAt = new Date(Date.now() + expiresInSeconds * 1000).toISOString()
        const link = new URL('/presentation/password-reset', location.origin)
        link.hash = new URLSearchParams({ resetTicket: `preview-only-${crypto.randomUUID()}`, expiresAt }).toString()
        return Response.json({ url: link.href, expiresAt, expiresInSeconds })
      }
      const current = credentials.get(id) ?? { userId: id, credentialsVersion: '1', state: 'USER_CHANGED', origin: null, password: null }
      if (method === 'GET') return Response.json(current)
      const command = await body()
      if (command.expectedCredentialsVersion !== current.credentialsVersion)
        return Response.json({ detail: 'Обнови карточку пользователя.' }, { status: 409 })
      const next = { ...current, credentialsVersion: String(Number(current.credentialsVersion) + 1), state: 'GENERATED', origin: 'admin_reset', password: `preview-only-${id}-${Number(current.credentialsVersion) + 1}` }
      credentials.set(id, next)
      return Response.json(next)
    }
    if (path === '/api/academic/admin-access/me') return adminPreview(url, method)
    if (/^\/api\/academic\/(semesters|semester-archive-operations|semester-deletions)(\/|$)/.test(path)) {
      const response = await semesters.fetcher(url, request)
      if (path.endsWith('/delete-preview') && response.ok) return Response.json({ ...await response.json(), previewDigest: 'a'.repeat(64) })
      return response
    }
    if (/^\/api\/academic\/(?:group-deletions\/|groups\/(?:registry|promote)(?:\/|$))|^\/api\/academic\/groups\/\d+(?:\/headman(?:\/(?:preview|roster))?|\/restore|\/deletion-preview|\/deletions(?:\/[^/]+\/resolve)?)?$/.test(path))
      return groups.fetcher(url, request)
    if (path === '/api/academic/groups/options') {
      const search = url.searchParams.get('search')?.toLocaleLowerCase('ru') ?? ''
      const selected = await Promise.all((['ACTIVE', 'DRAFT', 'ARCHIVED'] as const)
        .filter(status => status !== 'ARCHIVED' || url.searchParams.get('includeArchived') === 'true')
        .map(status => groups.client.listGroups({ status, size: 100 })))
      const options = selected.flatMap(({ items }) => items)
        .filter(({ name }) => name.toLocaleLowerCase('ru').includes(search))
        .map(({ id, name, status }) => ({ id, name, archived: status === 'ARCHIVED' }))
      const number = Number(url.searchParams.get('page') ?? 0), size = Number(url.searchParams.get('size') ?? 20)
      return Response.json({ items: options.slice(number * size, (number + 1) * size), number, size, totalElements: options.length, totalPages: Math.ceil(options.length / size) })
    }
    if (/^\/api\/academic\/(users|admin-access\/catalog)(\/|$)/.test(path)) {
      if (path === '/api/academic/users/42' && method === 'GET') return adminPreview(url, method)
      return users.fetcher(url, request)
    }
    return null
  }
}
