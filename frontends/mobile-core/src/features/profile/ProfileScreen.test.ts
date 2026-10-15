import { createSSRApp } from 'vue'
import { renderToString } from '@vue/server-renderer'
import { describe, expect, it } from 'vitest'
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
