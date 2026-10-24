import { describe, expect, it, vi } from 'vitest'
import type { Client, StompConfig } from '@stomp/stompjs'
import { usePwaAuth } from './auth'
import { useTmaSession } from '../../tma-vue/src/tma-session'
import { createGenerationBoundNotificationsRealtime } from '../../mobile-core/src/features/notifications/notifications-realtime'

const profile = {
  sessionId: '00000000-0000-4000-8000-000000000042',
  userId: '42',
  displayName: 'Student',
  sessionVersion: '3',
  rolesVersion: '4',
  activeRole: 'STUDENT',
  readOnly: false,
  roles: [
    {
      grantId: '17',
      role: 'STUDENT',
      status: 'ACTIVE',
      groupId: '17',
      selectable: true,
      readOnly: false,
    },
    { grantId: '18', role: 'TEACHER', status: 'ACTIVE', selectable: true, readOnly: false },
  ],
  passwordPolicy: {
    minCodePoints: 12,
    maxUtf8Bytes: 72,
    requiresDecimalDigit: true,
    specialCategories: ['P', 'S'],
    normalization: 'NONE',
  },
}

for (const platform of ['PWA', 'TMA'] as const)
  describe(`${platform} real session owner group retirement`, () => {
    it.each(['close', 'ticket403'] as const)(
      'retires the old Student generation after %s without a business replay',
      async (signal) => {
        let removed = false
        const paths: string[] = []
        const fetcher: typeof fetch = async (input) => {
          const path = String(input)
          paths.push(path)
          if (path === '/api/auth/session')
            return Response.json(
              removed
                ? { ...profile, activeRole: null, sessionVersion: '4', roles: [profile.roles[1]] }
                : profile,
            )
          if (path === '/api/auth/refresh' || path === '/api/auth/tma')
            return Response.json({ accessToken: 'candidate', expiresIn: 3600 })
          if (path === '/api/auth/ws-ticket')
            return removed
              ? Response.json({ code: 'WRONG_ROLE' }, { status: 403 })
              : Response.json({ ticket: '123e4567-e89b-42d3-a456-426614174000' })
          throw new Error(`Unexpected request ${path}`)
        }
        const pwa = usePwaAuth({ fetcher })
        const tma = useTmaSession({ fetcher, getInitData: () => 'synthetic-init-data' })
        const auth = platform === 'PWA' ? pwa : tma
        if (platform === 'PWA') {
          pwa.setToken('old')
          await pwa.getSessionFor(pwa.currentGeneration())
        } else {
          await tma.authenticateFor(tma.currentGeneration())
          await tma.getProfileFor(tma.currentGeneration())
        }
        const initial = auth.currentGeneration()
        const retire = vi.fn()
        auth.subscribeInvalidation(retire)
        let config!: StompConfig
        const fake = {
          activate: vi.fn(),
          deactivate: vi.fn(async () => undefined),
          subscribe: vi.fn(),
        }
        const realtime = createGenerationBoundNotificationsRealtime({
          owner: {
            currentGeneration: auth.currentGeneration,
            accessTokenFor: () => auth.accessToken.value,
            refreshFor: (generation) =>
              platform === 'PWA' ? pwa.refreshFor(generation) : tma.authenticateFor(generation),
          },
          scope: { userId: '42', groupId: '17', headman: false },
          fetcher,
          onChanged: vi.fn(),
          createClient(value) {
            config = value
            return fake
          },
        })
        removed = true
        if (signal === 'close') config.onWebSocketClose?.({} as never)
        else await config.beforeConnect?.(fake as unknown as Client)
        await vi.waitFor(() => expect(auth.currentGeneration()).toBe(initial + 1))
        expect(retire).toHaveBeenCalledOnce()
        const requests = paths.length
        await config.beforeConnect?.(fake as unknown as Client)
        config.onWebSocketClose?.({} as never)
        expect(paths).toHaveLength(requests)
        expect(paths.every((path) => path.startsWith('/api/auth/'))).toBe(true)
        realtime.dispose()
      },
    )
  })
