import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { HeadmanGroupApi } from '../../../mobile-core/src/features/headman-group/headman-group-client'
import { assistants, groupId, semester } from '../../../web-vue/src/dev/headman-visual-data'
import { createInteractiveMockTransport } from './interactive-mock-transport'

const origin = 'http://127.0.0.1:14272'
const credentialPath = '/api/auth/headman/students/101'
const budgetPath = '/api/attendance/requests/students/101/late-checkin-budget'
const assistantsPath = '/api/academic/assistants'

function command(method: string, payload: unknown): RequestInit {
  return { method, body: JSON.stringify(payload) }
}

async function activateHeadman(fetcher: typeof fetch) {
  const response = await fetcher(
    '/api/auth/session/active-role',
    command('PUT', { role: 'HEADMAN', expectedSessionVersion: '1' }),
  )
  expect(response.ok).toBe(true)
  return new HeadmanGroupApi({ fetcher, accessToken: () => null })
}

beforeEach(() => {
  vi.stubGlobal('location', new URL(origin))
  vi.stubGlobal('sessionStorage', { getItem: () => null, setItem: vi.fn() })
  vi.stubGlobal('localStorage', { getItem: () => null })
})

afterEach(() => {
  vi.unstubAllGlobals()
  vi.restoreAllMocks()
})

describe('ordinary mobile headman preview', () => {
  it('generates one password per version and issues a loopback link without changing it', async () => {
    const fetcher = createInteractiveMockTransport()
    const api = await activateHeadman(fetcher)
    const initial = await api.readCredential(101)
    expect(initial.credentialsVersion).toBe('1')
    const attempts = await Promise.all([
      fetcher(`${credentialPath}/generated-password`, command('POST', { expectedCredentialsVersion: '1' })),
      fetcher(`${credentialPath}/generated-password`, command('POST', { expectedCredentialsVersion: '1' })),
    ])
    expect(attempts.map(response => response.status).sort()).toEqual([200, 409])
    const generated = await api.readCredential(101)
    expect(generated.credentialsVersion).toBe('2')
    expect(generated.state).toBe('GENERATED')
    expect(generated.origin).toBe('headman_reset')
    expect(generated.password?.startsWith('preview-only-')).toBe(true)

    const link = await api.issueRecoveryLink(101)
    const url = new URL(link.url)
    expect(url.origin).toBe(origin)
    expect(url.pathname).toBe('/password-reset')
    expect(new URLSearchParams(url.hash.slice(1)).has('resetTicket')).toBe(true)
    expect(link.expiresInSeconds).toBe(3600)
    expect((await api.readCredential(101)).credentialsVersion).toBe('2')
    expect((await api.readCredential(102)).credentialsVersion).toBe('1')

    const other = await activateHeadman(createInteractiveMockTransport())
    expect((await other.readCredential(101)).credentialsVersion).toBe('1')
    vi.spyOn(console, 'warn').mockImplementation(() => {})
    expect((await fetcher(`https://preview.invalid${credentialPath}/initial-password`)).status).toBe(501)
    await fetcher(
      '/api/auth/session/active-role',
      command('PUT', { role: 'STUDENT', expectedSessionVersion: '2' }),
    )
    expect((await fetcher(`${credentialPath}/initial-password`)).status).toBe(501)
  })

  it('replays only the original budget change and keeps the current balance', async () => {
    const fetcher = createInteractiveMockTransport()
    const api = await activateHeadman(fetcher)
    const before = await api.getLateCheckinBudget(101)
    const first = { semesterId: before.semesterId, increase: 2, requestKey: 'preview-increase-1' }
    const results = await Promise.all([
      api.increaseLateCheckinBudget(101, first),
      api.increaseLateCheckinBudget(101, first),
    ])
    const snapshot = { ...before, limit: before.limit + 2, remaining: before.remaining + 2 }
    expect(results).toEqual([snapshot, snapshot])

    const second = { ...first, increase: 1, requestKey: 'preview-increase-2' }
    const latest = await api.increaseLateCheckinBudget(101, second)
    expect(latest.limit).toBe(before.limit + 3)
    expect(await api.increaseLateCheckinBudget(101, first)).toEqual(snapshot)
    await expect(api.increaseLateCheckinBudget(102, first)).rejects.toMatchObject({ response: { status: 409 } })
    await expect(api.increaseLateCheckinBudget(101, { ...first, increase: 3 })).rejects.toMatchObject({ response: { status: 409 } })
    expect((await fetcher(budgetPath, command('POST', { ...first, increase: 0 }))).status).toBe(400)
    expect((await fetcher(budgetPath, command('POST', { ...first, semesterId: semester.id + 1 }))).status).toBe(409)
    expect(await api.getLateCheckinBudget(101)).toEqual(latest)
  })

  it('applies assistant rights against a set snapshot and lists active assistants only', async () => {
    const fetcher = createInteractiveMockTransport()
    const api = await activateHeadman(fetcher)
    const seed = assistants[0]!
    const changed = await api.updatePermissions(seed.id, ['MANAGE_HOMEWORK'], [...seed.permissions].reverse())
    expect((await api.listAssistants(groupId)).find(row => row.id === seed.id)).toEqual(changed)
    await expect(api.updatePermissions(seed.id, ['VIEW_STATS'], seed.permissions)).rejects.toMatchObject({
      response: { status: 409 },
      problem: { extras: { code: 'ASSISTANT_PERMISSIONS_CONFLICT' } },
    })
    const path = `${assistantsPath}/${seed.id}/permissions`
    expect((await fetcher(path, command('PATCH', { permissions: [] }))).status).toBe(400)
    const revoked = await api.updatePermissions(seed.id, [], changed.permissions)
    expect(revoked.active).toBe(false)
    expect((await api.listAssistants(groupId)).some(row => row.id === seed.id)).toBe(false)
    expect((await fetcher(`${assistantsPath}?groupId=${groupId + 1}`)).status).toBe(403)
    await expect(api.assignAssistant(groupId, 99999, ['VIEW_STATS'])).rejects.toMatchObject({ response: { status: 404 } })
    const assigned = await api.assignAssistant(groupId, seed.studentId, ['VIEW_STATS'])
    expect((await api.listAssistants(groupId)).find(row => row.id === assigned.id)).toEqual(assigned)
    const other = await activateHeadman(createInteractiveMockTransport())
    expect(await other.listAssistants(groupId)).toEqual(assistants)
  })
})
