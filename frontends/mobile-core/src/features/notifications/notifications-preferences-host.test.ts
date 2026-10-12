import { readFileSync } from 'node:fs'
import { compileScript, parse } from '@vue/compiler-sfc'
import ts from 'typescript'
import * as vue from 'vue'
import { afterEach, describe, expect, it, vi } from 'vitest'
import * as models from './notifications-v2-models'
import * as drafts from './notification-preferences-draft'
import * as client from './notifications-client'
import * as v2client from './notifications-v2-client'
import * as session from '../../shared/session-owner'
import * as mutations from './notifications-mutations'
import { NotificationsUnreadState } from './notifications-unread'
import type {
  NotificationCatalog,
  NotificationPreferencesV2,
  NotificationPreferencesV2Update,
  NotificationsV2Api,
} from './notifications-v2-models'

type Host = {
  catalog: vue.Ref<NotificationCatalog | null>
  preferences: vue.Ref<NotificationPreferencesV2 | null>
  draft: vue.Ref<NotificationPreferencesV2 | null>
  conflict: vue.Ref<NotificationPreferencesV2 | null>
  preferencesError: vue.Ref<string | null>
  preferencesErrorTone: vue.Ref<string>
  preferencesBlocked: vue.Ref<boolean>
  preferencesBusy: vue.Ref<boolean>
  loadPreferences(preserve?: boolean): Promise<void>
  savePreferences(update: NotificationPreferencesV2Update): Promise<void>
  reloadPreferences(): void
  reapplyPreferences(): void
}
/** Run the actual SFC setup, with only lifecycle/DOM and unrelated child components disconnected.
 * This proves async state/PUT boundaries; it is deliberately not a browser/render acceptance. */
const scopes: vue.EffectScope[] = []
afterEach(() => {
  for (const scope of scopes.splice(0)) scope.stop()
})
function setupSfc(name: string, props: Record<string, unknown>): Record<string, unknown> {
  const filename = new URL(`./${name}.vue`, import.meta.url).pathname.replace(
    /^\/(?=[A-Za-z]:)/,
    '',
  )
  const { descriptor } = parse(readFileSync(filename, 'utf8'), { filename })
  const script = compileScript(descriptor, { id: `ui32-${name}` })
  const compiled = ts.transpileModule(script.content, {
    compilerOptions: { module: ts.ModuleKind.CommonJS, target: ts.ScriptTarget.ES2022 },
  }).outputText
  const imports: Record<string, unknown> = {
    vue: { ...vue, onMounted: () => {}, onBeforeUnmount: () => {}, useId: () => 'synthetic-id' },
    './notifications-v2-models': models,
    './notification-preferences-draft': drafts,
    './notifications-client': client,
    './notifications-v2-client': v2client,
    '../../shared/session-owner': session,
    './notifications-mutations': mutations,
  }
  const exports: {
    default?: {
      setup: (
        props: Record<string, unknown>,
        context: { expose: () => void; emit: () => void },
      ) => unknown
    }
  } = {}
  new Function('require', 'exports', compiled)((id: string) => imports[id] ?? {}, exports)
  const scope = vue.effectScope()
  const result = scope.run(() =>
    exports.default!.setup(props, { expose: () => {}, emit: () => {} }),
  )
  scopes.push(scope)
  return result as Record<string, unknown>
}
function profile(
  ids: readonly string[] = models.notificationTypeIds,
  revision = 'p1',
): NotificationPreferencesV2 {
  return {
    revision,
    splitChannels: true,
    mutedUntil: '2026-10-12T12:00:00Z',
    telegramLinked: false,
    types: Object.fromEntries(
      ids.map((id) => [id, { appPush: false, telegram: true, colorId: 'red' }]),
    ) as NotificationPreferencesV2['types'],
  }
}
function catalog(
  ids: readonly models.NotificationTypeId[] = models.notificationTypeIds,
): NotificationCatalog {
  return {
    schemaVersion: 2,
    revision: 'synthetic-catalog',
    groups: [{ id: 'group', label: 'Серверная группа' }],
    types: ids.map((id) => ({ id, groupId: 'group', label: `Сервер: ${id}`, roles: ['HEADMAN'] })),
    palette: models.notificationPaletteIds.map((id) => ({ id, label: id })),
  }
}
function host() {
  const api = {
    catalog: vi.fn<NotificationsV2Api['catalog']>(),
    getPreferences: vi.fn<NotificationsV2Api['getPreferences']>(),
    updatePreferences: vi.fn<NotificationsV2Api['updatePreferences']>(),
  }
  const props = vue.reactive({
    api,
    contextKey: 'HEADMAN|synthetic',
    unreadOwner: new NotificationsUnreadState(),
  })
  const state = setupSfc('NotificationsScreen', props) as unknown as Host
  return { state, api, props }
}
const settled = async () => {
  for (let i = 0; i < 8; i++) await Promise.resolve()
}

describe('host catalog/profile/CAS compatibility', () => {
  it('keeps old rendering pair plus dirty21 on incoming compatible32 until explicit reload', async () => {
    const { state, api } = host()
    const old = profile(models.legacyNotificationTypeIds)
    const oldCatalog = catalog(models.legacyNotificationTypeIds)
    api.catalog.mockResolvedValue(oldCatalog)
    api.getPreferences.mockResolvedValue(old)
    await state.loadPreferences()
    state.draft.value = drafts.changeNotificationColor(state.draft.value!, 'LESSON_STARTED', 'grey')
    const dirty = structuredClone(state.draft.value)
    const current = profile(models.notificationTypeIds, 'p32')
    api.catalog.mockResolvedValue(catalog())
    api.getPreferences.mockResolvedValue(current)
    await state.loadPreferences(true)
    expect(state.catalog.value).toEqual(oldCatalog)
    expect(state.preferences.value).toEqual(old)
    expect(state.draft.value).toEqual(dirty)
    expect(state.conflict.value).toEqual(current)
    state.reapplyPreferences()
    await settled()
    expect(api.updatePreferences).not.toHaveBeenCalled()
    expect(state.preferencesBlocked.value).toBe(true)
    expect(state.preferencesError.value).toContain('Каталог')
    state.reloadPreferences()
    await settled()
    expect(state.catalog.value?.types).toHaveLength(32)
    expect(state.draft.value).toEqual(current)
    expect(state.conflict.value).toBeNull()
    expect(state.preferencesBlocked.value).toBe(false)
  })
  it('retains confirmed state and dirty draft after mismatched catalog/prefs and blocks PUT', async () => {
    const { state, api } = host()
    api.catalog.mockResolvedValue(catalog(models.legacyNotificationTypeIds))
    api.getPreferences.mockResolvedValue(profile(models.legacyNotificationTypeIds))
    await state.loadPreferences()
    state.draft.value = drafts.changeNotificationColor(state.draft.value!, 'LESSON_STARTED', 'grey')
    const before = {
      catalog: state.catalog.value,
      confirmed: state.preferences.value,
      draft: state.draft.value,
    }
    api.catalog.mockResolvedValue(catalog())
    api.getPreferences.mockResolvedValue(profile(models.legacyNotificationTypeIds))
    await state.loadPreferences(true)
    expect(state.catalog.value).toBe(before.catalog)
    expect(state.preferences.value).toBe(before.confirmed)
    expect(state.draft.value).toBe(before.draft)
    await state.savePreferences(drafts.notificationPreferencesUpdate(state.draft.value!, 'p1'))
    expect(api.updatePreferences).not.toHaveBeenCalled()
    expect(state.preferencesError.value).toContain('не совпадают')
  })
  it('rejects an initial mismatched pair without rendering a partial settings state', async () => {
    const { state, api } = host()
    api.catalog.mockResolvedValue(catalog())
    api.getPreferences.mockResolvedValue(profile(models.legacyNotificationTypeIds))
    await state.loadPreferences()
    expect(state.catalog.value).toBeNull()
    expect(state.preferences.value).toBeNull()
    expect(state.draft.value).toBeNull()
    expect(state.preferencesBlocked.value).toBe(true)
  })
  it('makes no second PUT for a cross-set409 and retains old OFF/color/mute', async () => {
    const { state, api } = host()
    api.catalog.mockResolvedValue(catalog(models.legacyNotificationTypeIds))
    api.getPreferences.mockResolvedValue(profile(models.legacyNotificationTypeIds))
    await state.loadPreferences()
    state.draft.value = drafts.changeNotificationColor(state.draft.value!, 'LESSON_STARTED', 'grey')
    const before = structuredClone(state.draft.value)
    api.updatePreferences.mockRejectedValue(
      new v2client.NotificationsConflictError(profile(models.notificationTypeIds, 'p32')),
    )
    await state.savePreferences(drafts.notificationPreferencesUpdate(state.draft.value!, 'p1'))
    state.reapplyPreferences()
    await settled()
    expect(api.updatePreferences).toHaveBeenCalledTimes(1)
    expect(state.draft.value).toEqual(before)
    expect(state.conflict.value?.revision).toBe('p32')
  })
  it.each([
    { ids: models.legacyNotificationTypeIds, name: 'legacy21' },
    { ids: models.notificationTypeIds, name: 'current32' },
  ])('allows one explicit $name same-set reapply and never unmutes', async ({ ids }) => {
    const { state, api } = host()
    api.catalog.mockResolvedValue(catalog(ids))
    api.getPreferences.mockResolvedValue(profile(ids))
    await state.loadPreferences()
    state.draft.value = drafts.changeNotificationColor(state.draft.value!, 'LESSON_STARTED', 'grey')
    api.updatePreferences
      .mockRejectedValueOnce(new v2client.NotificationsConflictError(profile(ids, 'p2')))
      .mockResolvedValueOnce({ ...state.draft.value, revision: 'p3' })
    await state.savePreferences(drafts.notificationPreferencesUpdate(state.draft.value, 'p1'))
    state.reapplyPreferences()
    await settled()
    expect(api.updatePreferences).toHaveBeenCalledTimes(2)
    const sent = api.updatePreferences.mock.calls[1]![0]
    expect(sent.expectedRevision).toBe('p2')
    expect(sent.types.LESSON_STARTED.colorId).toBe('grey')
    expect(Object.keys(sent.types)).toHaveLength(ids.length)
    expect(sent).not.toHaveProperty('mutedUntil')
    expect(state.conflict.value).toBeNull()
  })
  it('keeps dirty draft during a503 retry with no automatic PUT', async () => {
    const { state, api } = host()
    api.catalog.mockResolvedValue(catalog())
    api.getPreferences.mockResolvedValue(profile())
    await state.loadPreferences()
    state.draft.value = drafts.changeNotificationColor(state.draft.value!, 'LESSON_STARTED', 'grey')
    const before = structuredClone(state.draft.value)
    api.getPreferences.mockRejectedValueOnce(
      new client.NotificationsApiError('Временно недоступно', 503),
    )
    await state.loadPreferences(true)
    expect(state.preferencesErrorTone.value).toBe('warning')
    state.reloadPreferences()
    await settled()
    expect(state.draft.value).toEqual(before)
    expect(api.updatePreferences).not.toHaveBeenCalled()
  })
  it('discards a late profile pair when context changes before resolution', async () => {
    const { state, api, props } = host()
    let resolve!: (profile: NotificationPreferencesV2) => void
    api.catalog.mockResolvedValue(catalog())
    api.getPreferences.mockReturnValue(
      new Promise((accept) => {
        resolve = accept
      }),
    )
    const task = state.loadPreferences()
    props.contextKey = 'STUDENT|other-synthetic'
    await vue.nextTick()
    resolve(profile())
    await task
    expect(state.preferences.value).toBeNull()
  })
  it.each(['NotificationSettings', 'NotificationColors'])(
    '%s refuses catalog/draft mismatch before accessing a missing entry',
    (name) => {
      const state = setupSfc(name, {
        catalog: catalog(),
        preferences: profile(),
        draft: profile(models.legacyNotificationTypeIds),
      })
      expect((state.groups as vue.ComputedRef<unknown[]>).value).toEqual([])
      expect((state.disabled as vue.ComputedRef<boolean>).value).toBe(true)
    },
  )
  it.each(['NotificationSettings', 'NotificationColors'])(
    '%s presents server order/labels and actual32 OFF/colors',
    (name) => {
      const server = catalog([...models.notificationTypeIds].reverse())
      const saved = profile()
      const state = setupSfc(name, { catalog: server, preferences: saved, draft: null })
      const groups = (
        state.groups as vue.ComputedRef<
          {
            types: {
              id: models.NotificationTypeId
              label: string
              preferences: models.NotificationTypePreferences
            }[]
          }[]
        >
      ).value
      const rows = groups.flatMap((group) => group.types)
      expect(rows.map((row) => row.id)).toEqual(server.types.map((type) => type.id))
      expect(rows.map((row) => row.label)).toEqual(server.types.map((type) => type.label))
      expect(rows.find((row) => row.id === 'ADMIN_ACCESS_CHANGED')?.preferences).toEqual({
        appPush: false,
        telegram: true,
        colorId: 'red',
      })
    },
  )
  it.each(['NotificationSettings', 'NotificationColors'])(
    '%s shows catalog-change state even if a prior same-set conflict remains',
    (name) => {
      const state = setupSfc(name, {
        catalog: catalog(),
        preferences: profile(),
        draft: profile(),
        conflict: profile(models.notificationTypeIds, 'p2'),
        blocked: true,
      })
      expect((state.catalogChanged as vue.ComputedRef<boolean>).value).toBe(true)
      expect((state.disabled as vue.ComputedRef<boolean>).value).toBe(true)
    },
  )
})
