import { describe, expect, it, vi } from 'vitest'
import { StaleSessionGenerationError } from '../../shared/session-owner'
import { NotificationsApiError } from './notifications-client'
import { createGenerationBoundNotificationsV2Api, NotificationsConflictError, parseNotificationAction, parseNotificationPreferencesV2, parseNotificationCatalog, parseNotificationItem } from './notifications-v2-client'
import { notificationTypeIds, legacyNotificationTypeIds, notificationPaletteIds, addedNotificationTypeIds, type NotificationPreferencesV2 } from './notifications-v2-models'
import { NotificationsUnreadState } from './notifications-unread'
const json = (value: unknown, status = 200) => new Response(JSON.stringify(value), { status, headers: { 'Content-Type': 'application/json' } })
const prefs = (ids: readonly string[] = notificationTypeIds): NotificationPreferencesV2 => ({ revision: 'r1', splitChannels: false, types: Object.fromEntries(ids.map(id => [id, { appPush: true, telegram: true, colorId: 'violet' }])) as NotificationPreferencesV2['types'], mutedUntil: '2026-10-11T12:00:00Z', telegramLinked: true })
function setup(fetcher: typeof fetch) {
  const state = { generation: 1, role: 'HEADMAN' as 'HEADMAN' | 'STUDENT', context: 'session1|roles1', token: 'fixture' }
  const owner = { currentGeneration: () => state.generation, currentRole: () => state.role, currentContext: () => state.context, accessTokenFor: () => state.token, refreshFor: vi.fn(async () => { state.token = 'refreshed' }) }
  return { state, owner, api: createGenerationBoundNotificationsV2Api(owner, fetcher) }
}
describe('Notifications V2 authoritative context and CAS', () => {
  it('rejects a response from another signed role rather than showing its private count', async () => {
    const { api } = setup(vi.fn(async () => json({ role: 'STUDENT', count: 7 })))
    await expect(api.unreadCount()).rejects.toMatchObject({ status: 403 })
  })
  it('fences late HTTP after selected role or authority changes even at the same generation', async () => {
    let resolve!: (response: Response) => void
    const fetcher = vi.fn(() => new Promise<Response>(accept => { resolve = accept }))
    const { api, state } = setup(fetcher)
    const task = api.unreadCount()
    state.context = 'session1|roles2'
    resolve(json({ role: 'HEADMAN', count: 9 }))
    await expect(task).rejects.toBeInstanceOf(StaleSessionGenerationError)
  })
  it('refreshes one unauthorized request and still validates selected role on retry', async () => {
    const { api, owner } = setup(vi.fn().mockResolvedValueOnce(json({}, 401)).mockResolvedValueOnce(json({ role: 'HEADMAN', count: 105 })))
    expect(await api.unreadCount()).toBe(105)
    expect(owner.refreshFor).toHaveBeenCalledTimes(1)
  })
  it('sends atomic complete flags/color revision and omits an unchanged pause', async () => {
    let body: Record<string, unknown> | null = null
    const { api } = setup(vi.fn(async (_input, init) => { body = JSON.parse(String(init?.body)); return json({ ...prefs(), revision: 'r2' }) }))
    const saved = prefs()
    await api.updatePreferences({ expectedRevision: saved.revision, splitChannels: false, types: saved.types })
    expect(body).toEqual({ expectedRevision: 'r1', splitChannels: false, types: saved.types })
    expect(body).not.toHaveProperty('mutedUntil')
  })
  it('exposes current server conflict without overwriting the callers draft', async () => {
    const current = { ...prefs(), revision: 'r2' }
    const { api } = setup(vi.fn(async () => json({ currentPreferences: current }, 409)))
    const draft = prefs(); draft.types.LESSON_STARTED.appPush = false; draft.types.LESSON_STARTED.telegram = false
    try { await api.updatePreferences({ expectedRevision: 'r1', splitChannels: false, types: draft.types }); expect.unreachable() }
    catch (error) { expect(error).toBeInstanceOf(NotificationsConflictError); expect((error as NotificationsConflictError).currentPreferences?.revision).toBe('r2') }
    expect(draft.types.LESSON_STARTED.appPush).toBe(false)
  })
  it('rejects incomplete settings or an unknown arbitrary palette', () => {
    const incomplete = prefs(); delete (incomplete.types as Partial<typeof incomplete.types>).LESSON_STARTED
    expect(() => parseNotificationPreferencesV2(incomplete)).toThrow(NotificationsApiError)
    expect(() => parseNotificationPreferencesV2({ ...prefs(), types: { ...prefs().types, LESSON_STARTED: { appPush: true, telegram: true, colorId: '#fff' } } })).toThrow()
  })
  it('preserves large decimal action IDs and rejects lossy/URL/malformed targets', () => {
    expect(parseNotificationAction({ kind: 'lesson', role: 'TEACHER', lessonId: '9007199254740993' })).toEqual({ kind: 'lesson', role: 'TEACHER', lessonId: '9007199254740993' })
    expect(parseNotificationAction({ kind: 'lesson', role: 'TEACHER', lessonId: Number('9007199254740993') })).toBeNull()
    expect(parseNotificationAction({ kind: 'homework', role: 'STUDENT', homeworkId: '1', lessonDate: '2026-02-30' })).toBeNull()
    expect(parseNotificationAction({ kind: 'url', role: 'ADMIN', url: 'https://example.test' })).toBeNull()
  })
  it('retains a confirmed mutation count when an earlier REST count arrives late', async () => {
    let resolve!: (count: number) => void
    const state = new NotificationsUnreadState()
    state.bind('HEADMAN|session1', { unreadCount: () => new Promise<number>(accept => { resolve = accept }) })
    const task = state.refresh()
    state.acknowledge(2)
    resolve(10)
    await task
    expect(state.view).toMatchObject({ count: 2, status: 'ready' })
  })
})

const catalog = (ids: readonly string[] = notificationTypeIds) => ({
  schemaVersion: 2, revision: 'catalog-fixture', groups: [{ id: 'server-group', label: 'Серверная группа' }],
  types: ids.map(id => ({ id, groupId: 'server-group', label: `Сервер: ${id}`, roles: ['HEADMAN'] })),
  palette: notificationPaletteIds.map(id => ({ id, label: id })),
})
describe('complete transitional 21 and current 32 wire profiles', () => {
  it.each([{ ids: legacyNotificationTypeIds, name: 'legacy21' }, { ids: notificationTypeIds, name: 'current32' }])('preserves exactly $name profile keys, server labels/order and OFF/color/mute', async ({ ids }) => {
    const original = prefs(ids)
    original.types.LESSON_STARTED = { appPush: false, telegram: false, colorId: 'red' }
    if (ids.length === 32) original.types.ADMIN_ACCESS_CHANGED = { appPush: false, telegram: false, colorId: 'grey' }
    expect(parseNotificationPreferencesV2(original)).toEqual(original)
    const presented = catalog([...ids].reverse())
    expect(parseNotificationCatalog(presented)).toEqual(presented)
    let sent: unknown
    const { api } = setup(vi.fn(async (_input, init) => { sent = JSON.parse(String(init?.body)); return json(original) }))
    await api.updatePreferences({ expectedRevision: original.revision, splitChannels: false, types: original.types })
    expect(sent).toEqual({ expectedRevision: 'r1', splitChannels: false, types: original.types })
    expect(sent).not.toHaveProperty('mutedUntil')
    expect(Object.keys((sent as { types: object }).types)).toEqual([...ids])
  })
  it.each(Array.from({ length: 10 }, (_, i) => i + 22))('rejects partial %i type migration for both catalog and preferences', size => {
    const ids = notificationTypeIds.slice(0, size)
    expect(() => parseNotificationCatalog(catalog(ids))).toThrow(NotificationsApiError)
    expect(() => parseNotificationPreferencesV2(prefs(ids))).toThrow(NotificationsApiError)
  })
  it('rejects duplicate, unknown, missing legacy keys and malformed flags without issuing PUT', async () => {
    const duplicate = catalog(); duplicate.types[31] = duplicate.types[0]!
    expect(() => parseNotificationCatalog(duplicate)).toThrow(NotificationsApiError)
    const replaced = [...legacyNotificationTypeIds.slice(1), 'ADMIN_ACCESS_CHANGED']
    expect(() => parseNotificationCatalog(catalog(replaced))).toThrow(NotificationsApiError)
    expect(() => parseNotificationPreferencesV2(prefs(replaced))).toThrow(NotificationsApiError)
    const unknown = [...legacyNotificationTypeIds.slice(1), 'ATTENDANCE_RED_ZONE']
    expect(() => parseNotificationCatalog(catalog(unknown))).toThrow(NotificationsApiError)
    expect(() => parseNotificationPreferencesV2(prefs(unknown))).toThrow(NotificationsApiError)
    expect(() => parseNotificationPreferencesV2({ ...prefs(), types: { ...prefs().types, LESSON_UPDATED: { appPush: 'false', telegram: false, colorId: 'red' } } })).toThrow(NotificationsApiError)
    expect(() => parseNotificationPreferencesV2({ ...prefs(), types: { ...prefs().types, ADMIN_ACCESS_CHANGED: { appPush: false, telegram: true, colorId: 'grey' } } })).toThrow(NotificationsApiError)
    expect(() => parseNotificationPreferencesV2({ ...prefs(), types: { ...prefs().types, ADMIN_ACCESS_CHANGED: { appPush: false, colorId: 'grey' } } })).toThrow(NotificationsApiError)
    const fetcher = vi.fn(async () => json(prefs()))
    const { api } = setup(fetcher)
    await expect(api.updatePreferences({ expectedRevision: 'r1', splitChannels: false, types: prefs(notificationTypeIds.slice(0, 22)).types })).rejects.toBeInstanceOf(NotificationsApiError)
    expect(fetcher).not.toHaveBeenCalled()
  })
  it.each(addedNotificationTypeIds)('accepts added %s history with null action without inventing a target', type => {
    expect(parseNotificationItem({ id: 'synthetic_item', type, heading: 'Синтетическое уведомление', sentAt: '2026-10-10T12:00:00Z', orderAt: '2026-10-10T12:00:00Z', readAt: null, revision: '0', details: [], action: null }).action).toBeNull()
  })
})
