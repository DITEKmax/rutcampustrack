import { createSSRApp } from 'vue'
import { renderToString } from '@vue/server-renderer'
import { describe, expect, it, vi } from 'vitest'
import ProfileScreen from './ProfileScreen.vue'
import { DEFAULT_PASSWORD_POLICY, type ProfileSnapshot } from './profile-types'

const snapshot: ProfileSnapshot = {
  sessionId: '00000000-0000-4000-8000-000000000042',
  userId: '42',
  displayName: 'Синтетический профиль',
  sessionVersion: '1',
  rolesVersion: '1',
  activeRole: 'STUDENT',
  roles: [],
  readOnly: false,
  passwordPolicy: DEFAULT_PASSWORD_POLICY,
}

describe('own profile Telegram metadata', () => {
  it.each([
    ['880000042', 'Telegram ID: 880000042'],
    [null, 'Telegram ID: не привязан'],
    [undefined, 'Telegram ID: нет данных'],
  ])(
    'renders the readonly binding state %s without conflating unknown and unbound',
    async (telegramId, expected) => {
      const profile = telegramId === undefined ? snapshot : { ...snapshot, telegramId }
      const html = await renderToString(createSSRApp(ProfileScreen, { snapshot: profile }))
      expect(html).toContain(expected)
      expect(html).not.toContain('<input')
      expect(html).not.toContain('Привязать Telegram')
    },
  )
})

describe('own delegated rights readout', () => {
  it('renders the supplied own labels as readonly escaped text', async () => {
    const html = await renderToString(
      createSSRApp(ProfileScreen, {
        snapshot,
        delegatedPermissions: [
          { code: 'VIEW_STATS', label: 'Просматривать статистику' },
          { code: 'MANAGE_HOMEWORK', label: '<img src=x onerror=alert(1)>' },
        ],
      }),
    )
    const region = html.match(
      /<section[^>]*aria-label="Дополнительные права"[\s\S]*?<\/section>/,
    )?.[0]
    expect(region).toBeDefined()
    expect(region).toContain('Просматривать статистику')
    expect(region).toContain('&lt;img src=x onerror=alert(1)&gt;')
    expect(region).not.toMatch(/<(?:input|button|img)\b|role="switch"/)
  })

  it.each([
    ['empty', snapshot, []],
    [
      'headman authority',
      { ...snapshot, activeRole: 'HEADMAN' as const },
      [{ code: 'VIEW_STATS' as const, label: 'Просматривать статистику' }],
    ],
    [
      'readonly authority',
      { ...snapshot, readOnly: true },
      [{ code: 'VIEW_STATS' as const, label: 'Просматривать статистику' }],
    ],
  ])('hides the whole delegated block for %s', async (_, profile, permissions) => {
    const html = await renderToString(
      createSSRApp(ProfileScreen, {
        snapshot: profile,
        delegatedPermissions: permissions,
      }),
    )
    expect(html).not.toContain('Дополнительные права')
    expect(html).not.toContain('Просматривать статистику')
  })
})

describe('D024 live profile incoming login entry', () => {
  it.each(['ready', 'readonly', 'bootstrap', 'offline', 'loading'] as const)(
    'gates the entry for %s authority',
    async (mode) => {
      const port = { isOnline: () => mode !== 'offline', preview: vi.fn(), decide: vi.fn() }
      const html = await renderToString(
        createSSRApp(ProfileScreen, {
          snapshot: {
            ...snapshot,
            readOnly: mode === 'readonly',
            activeRole: mode === 'bootstrap' ? null : 'STUDENT',
          },
          loading: mode === 'loading',
          browserLoginApproval: port,
        }),
      )
      expect(html.includes('Вход в браузере')).toBe(mode === 'ready')
      expect(html).not.toContain('Данные QR для входа')
      expect(port.preview).not.toHaveBeenCalled()
      expect(port.decide).not.toHaveBeenCalled()
    },
  )
})
