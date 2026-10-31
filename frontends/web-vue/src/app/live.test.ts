import { effectScope, nextTick, ref, type EffectScope } from 'vue'
import { afterEach, describe, expect, it, vi } from 'vitest'

const source = vi.hoisted(() => ({ changed: (): void => undefined }))
vi.mock('../../../mobile-core/src/features/notifications/notifications-realtime', () => ({
  createGenerationBoundNotificationsRealtime: (options: { onChanged(): void }) => {
    source.changed = options.onChanged
    return { dispose() {} }
  },
}))

let scope: EffectScope | undefined
afterEach(() => {
  scope?.stop()
  scope = undefined
  vi.unstubAllGlobals()
  vi.resetModules()
})

async function admitted() {
  vi.stubGlobal('window', { addEventListener() {}, removeEventListener() {} })
  vi.stubGlobal('fetch', async (input: RequestInfo | URL) => {
    const path = String(input)
    if (path.endsWith('/login')) return Response.json({ accessToken: 'token' })
    if (path.endsWith('/session'))
      return Response.json({
        sessionId: '00000000-0000-4000-8000-000000000001',
        userId: '42',
        displayName: 'Анна',
        sessionVersion: '1',
        rolesVersion: '1',
        activeRole: 'HEADMAN',
        readOnly: false,
        roles: [
          {
            grantId: '1',
            role: 'HEADMAN',
            status: 'ACTIVE',
            groupId: '1',
            selectable: true,
            readOnly: false,
          },
        ],
        passwordPolicy: {
          minCodePoints: 12,
          maxUtf8Bytes: 72,
          requiresDecimalDigit: true,
          specialCategories: ['P', 'S'],
          normalization: 'NONE',
        },
      })
    throw new Error(`Unexpected request ${path}`)
  })
  const { webSession } = await import('../auth/session')
  await webSession.login('anna', 'password')
  const { useWebLiveRefresh } = await import('./live')
  return { webSession, useWebLiveRefresh }
}

describe('live projection refresh while editing', () => {
  it('preserves the dirty draft and applies the latest server projection once editing ends', async () => {
    const { useWebLiveRefresh } = await admitted()
    const editing = ref(true)
    const draft = ref('Несохранённая причина')
    const projection = ref('Старая серверная версия')
    let server = 'Новая серверная версия'
    scope = effectScope()
    scope.run(() =>
      useWebLiveRefresh(
        async () => {
          projection.value = server
        },
        { blocked: editing },
      ),
    )
    source.changed()
    server = 'Последняя серверная версия'
    source.changed()
    await nextTick()
    expect(draft.value).toBe('Несохранённая причина')
    expect(projection.value).toBe('Старая серверная версия')
    editing.value = false
    await nextTick()
    expect(projection.value).toBe('Последняя серверная версия')
    expect(draft.value).toBe('Несохранённая причина')
  })

  it('does not apply a queued old-account projection after authority invalidation', async () => {
    const { webSession, useWebLiveRefresh } = await admitted()
    const editing = ref(true)
    const visible = ref('Текущий черновик')
    scope = effectScope()
    scope.run(() =>
      useWebLiveRefresh(
        async () => {
          visible.value = 'Данные предыдущего аккаунта'
        },
        { blocked: editing },
      ),
    )
    source.changed()
    webSession.invalidate()
    editing.value = false
    await nextTick()
    expect(webSession.snapshot.value).toBeNull()
    expect(visible.value).toBe('Текущий черновик')
  })
})
