import { createRenderer, h, markRaw, nextTick, reactive, type App, type Component } from 'vue'
import { afterEach, describe, expect, it, vi } from 'vitest'
import * as models from './notifications-v2-models'
import * as client from './notifications-client'
import * as v2client from './notifications-v2-client'
import NotificationsScreen from './NotificationsScreen.vue'
import NotificationSettings from './NotificationSettings.vue'
import NotificationColors from './NotificationColors.vue'
import NotificationInboxCard from './NotificationInboxCard.vue'
import { NotificationsUnreadState } from './notifications-unread'
import type {
  NotificationCatalog,
  NotificationPreferencesV2,
  NotificationsV2Api,
} from './notifications-v2-models'
class Node extends EventTarget {
  children: Node[] = []
  parent: Node | null = null
  props: Record<string, unknown> = {}
  text = ''
  value = ''
  selected = false
  selectedIndex = -1
  constructor(readonly tag: string) {
    super()
    markRaw(this)
  }
  focus(): void {}
  querySelector(): null {
    return null
  }
  contains(node: unknown): boolean {
    return nodes(this).includes(node as Node)
  }
  get tagName(): string {
    return this.tag.toUpperCase()
  }
  get options(): Node[] {
    return this.children.filter((node) => node.tag === 'option')
  }
  getRootNode(): { activeElement: null } {
    return { activeElement: null }
  }
}
const renderer = createRenderer<Node, Node>({
  createElement: (tag) => new Node(tag),
  createText: (text) => Object.assign(new Node('#text'), { text }),
  createComment: () => new Node('#comment'),
  setText: (node, text) => {
    node.text = text
  },
  setElementText: (node, text) => {
    node.text = text
    node.children = []
  },
  parentNode: (node) => node.parent,
  nextSibling: (node) => node.parent?.children[node.parent.children.indexOf(node) + 1] ?? null,
  patchProp: (node, key, _old, value) => {
    node.props[key] = value
    if (key === 'value') node.value = String(value ?? '')
  },
  insert: (node, parent, anchor = null) => {
    if (node.parent) node.parent.children.splice(node.parent.children.indexOf(node), 1)
    const index = anchor ? parent.children.indexOf(anchor) : -1
    parent.children.splice(index < 0 ? parent.children.length : index, 0, node)
    node.parent = parent
  },
  remove: (node) => {
    node.parent?.children.splice(node.parent.children.indexOf(node), 1)
    node.parent = null
  },
})
const apps: App[] = []
afterEach(() => {
  apps.splice(0).forEach((app) => app.unmount())
  vi.unstubAllGlobals()
})
function nodes(root: Node): Node[] {
  return [root, ...root.children.flatMap(nodes)]
}
function text(root: Node): string {
  return root.text + root.children.map(text).join(' ')
}
async function settle(): Promise<void> {
  for (let i = 0; i < 12; i++) {
    await Promise.resolve()
    await nextTick()
  }
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

function mount(component: Component, props: Record<string, unknown>) {
  vi.stubGlobal('document', {
    getElementById: () => null,
    activeElement: null,
    addEventListener() {},
    removeEventListener() {},
  })
  const root = new Node('root')
  const app = renderer.createApp({ render: () => h(component, props) })
  apps.push(app)
  app.mount(root)
  return { root, app }
}
function button(root: Node, label: string) {
  return nodes(root).find(
    (node) =>
      node.tag === 'button' &&
      (node.props['aria-label'] === label ||
        text(node).replace(/\s/g, '') === label.replace(/\s/g, '')),
  )
}
async function activate(root: Node, label: string) {
  const action = button(root, label)
  expect(action).toBeDefined()
  expect(action!.props.disabled).not.toBe(true)
  ;(action!.props.onClick as () => void)()
  await settle()
}
function host(
  ids: readonly models.NotificationTypeId[] = models.notificationTypeIds,
  initialMismatch = false,
) {
  const api = {
    inbox: vi.fn(async () => ({ items: [], nextCursor: null })),
    catalog: vi.fn<NotificationsV2Api['catalog']>().mockResolvedValue(catalog(ids)),
    getPreferences: vi
      .fn<NotificationsV2Api['getPreferences']>()
      .mockResolvedValue(profile(initialMismatch ? models.legacyNotificationTypeIds : ids)),
    updatePreferences: vi.fn<NotificationsV2Api['updatePreferences']>(),
  }
  const props = reactive({
    api: markRaw(api),
    contextKey: 'HEADMAN|synthetic',
    unreadOwner: markRaw(new NotificationsUnreadState()),
    realtimeRevision: 0,
  })
  return { ...mount(NotificationsScreen, props), props, api }
}
async function colors(root: Node) {
  await activate(root, 'Настройки уведомлений')
  const firstDelivery = nodes(root).find(
    (node) => node.tag === 'input' && node.props.type === 'checkbox',
  )
  expect(firstDelivery?.props.checked).toBe(false)
  await activate(root, 'Цвета уведомлений')
}
async function chooseGrey(root: Node) {
  const type = nodes(root).find(
    (node) =>
      node.tag === 'button' &&
      node.props['aria-expanded'] !== undefined &&
      text(node).trim().startsWith('Сервер: LESSON_STARTED'),
  )!
  expect(type).toBeDefined()
  ;(type.props.onClick as () => void)()
  await settle()
  const grey = nodes(root).find(
    (node) => node.tag === 'button' && node.props['aria-label'] === 'grey',
  )!
  expect(grey).toBeDefined()
  ;(grey.props.onClick as () => void)()
  await settle()
}
function greySelected(root: Node) {
  return nodes(root).some(
    (node) =>
      node.tag === 'button' &&
      node.props['aria-pressed'] === true &&
      node.props['aria-label'] === 'grey',
  )
}
async function refresh(view: ReturnType<typeof host>) {
  view.props.realtimeRevision++
  await settle()
}
function saveDisabled(root: Node) {
  return button(root, 'Сохранить цвета')?.props.disabled
}
function expectPrivateGone(root: Node) {
  expect(text(root)).not.toContain('Сервер: LESSON_STARTED')
  expect(nodes(root).filter((node) => node.tag === 'input')).toHaveLength(0)
}
describe('host catalog/profile/CAS compatibility', () => {
  it('keeps old rendering pair plus dirty21 on incoming compatible32 until explicit reload', async () => {
    const view = host(models.legacyNotificationTypeIds)
    await settle()
    await colors(view.root)
    await chooseGrey(view.root)
    view.api.catalog.mockResolvedValue(catalog())
    view.api.getPreferences.mockResolvedValue(profile(models.notificationTypeIds, 'p32'))
    await refresh(view)
    expect(
      nodes(view.root).filter((node) => node.props['aria-expanded'] !== undefined),
    ).toHaveLength(21)
    expect(greySelected(view.root)).toBe(true)
    expect(saveDisabled(view.root)).toBe(true)
    expect(button(view.root, 'Применить мой выбор')?.props.disabled).toBe(true)
    expect(view.api.updatePreferences).not.toHaveBeenCalled()
    await activate(view.root, 'Загрузить актуальные')
    expect(
      nodes(view.root).filter((node) => node.props['aria-expanded'] !== undefined),
    ).toHaveLength(32)
    expect(saveDisabled(view.root)).toBe(true)
  })
  it('retains confirmed state and dirty draft after mismatched catalog/prefs and blocks PUT', async () => {
    const view = host(models.legacyNotificationTypeIds)
    await settle()
    await colors(view.root)
    await chooseGrey(view.root)
    view.api.catalog.mockResolvedValue(catalog())
    await refresh(view)
    expect(greySelected(view.root)).toBe(true)
    expect(saveDisabled(view.root)).toBe(true)
    expect(view.api.updatePreferences).not.toHaveBeenCalled()
    expect(
      nodes(view.root).some((node) => node.props.role === 'alert' && text(node).trim().length > 0),
    ).toBe(true)
  })
  it('rejects an initial mismatched pair without rendering a partial settings state', async () => {
    const view = host(models.notificationTypeIds, true)
    await settle()
    await activate(view.root, 'Настройки уведомлений')
    expect(nodes(view.root).filter((node) => node.tag === 'input')).toHaveLength(0)
    expect(button(view.root, 'Сохранить настройки')).toBeUndefined()
    expect(view.api.updatePreferences).not.toHaveBeenCalled()
  })
  it('makes no second PUT for a cross-set409 and retains old OFF/color/mute', async () => {
    const view = host(models.legacyNotificationTypeIds)
    await settle()
    await colors(view.root)
    await chooseGrey(view.root)
    view.api.updatePreferences.mockRejectedValue(
      new v2client.NotificationsConflictError(profile(models.notificationTypeIds, 'p32')),
    )
    await activate(view.root, 'Сохранить цвета')
    expect(view.api.updatePreferences).toHaveBeenCalledTimes(1)
    expect(view.api.updatePreferences.mock.calls[0]![0]).toMatchObject({
      expectedRevision: 'p1',
      types: { LESSON_STARTED: { appPush: false, telegram: true, colorId: 'grey' } },
    })
    expect(view.api.updatePreferences.mock.calls[0]![0]).not.toHaveProperty('mutedUntil')
    expect(greySelected(view.root)).toBe(true)
    expect(button(view.root, 'Применить мой выбор')?.props.disabled).toBe(true)
    expect(saveDisabled(view.root)).toBe(true)
  })
  it.each([
    { ids: models.legacyNotificationTypeIds, name: 'legacy21' },
    { ids: models.notificationTypeIds, name: 'current32' },
  ])('allows one explicit $name same-set reapply and never unmutes', async ({ ids }) => {
    const view = host(ids)
    await settle()
    await colors(view.root)
    await chooseGrey(view.root)
    view.api.updatePreferences
      .mockRejectedValueOnce(new v2client.NotificationsConflictError(profile(ids, 'p2')))
      .mockImplementationOnce(async (update) => ({ ...profile(ids, 'p3'), types: update.types }))
    await activate(view.root, 'Сохранить цвета')
    expect(view.api.updatePreferences).toHaveBeenCalledTimes(1)
    expect(greySelected(view.root)).toBe(true)
    await activate(view.root, 'Применить мой выбор')
    expect(view.api.updatePreferences).toHaveBeenCalledTimes(2)
    const sent = view.api.updatePreferences.mock.calls[1]![0]
    expect(sent.expectedRevision).toBe('p2')
    expect(sent.types.LESSON_STARTED).toEqual({ appPush: false, telegram: true, colorId: 'grey' })
    expect(Object.keys(sent.types)).toHaveLength(ids.length)
    expect(sent).not.toHaveProperty('mutedUntil')
    expect(button(view.root, 'Применить мой выбор')).toBeUndefined()
  })
  it('keeps dirty draft during a503 retry with no automatic PUT', async () => {
    const view = host()
    await settle()
    await colors(view.root)
    await chooseGrey(view.root)
    view.api.getPreferences.mockRejectedValueOnce(
      new client.NotificationsApiError('synthetic unavailable', 503),
    )
    await refresh(view)
    expect(nodes(view.root).some((node) => node.props['data-tone'] === 'warning')).toBe(true)
    expect(greySelected(view.root)).toBe(true)
    expect(view.api.updatePreferences).not.toHaveBeenCalled()
    await activate(view.root, 'Повторить')
    expect(greySelected(view.root)).toBe(true)
    expect(view.api.updatePreferences).not.toHaveBeenCalled()
  })
  it.each(['p1', 'p2'])(
    'keeps delivery edits during a background read of %s and saves only the confirmed CAS revision',
    async (revision) => {
      const view = host()
      await settle()
      await activate(view.root, 'Настройки уведомлений')
      let resolve!: (value: NotificationPreferencesV2) => void
      view.api.getPreferences.mockReturnValueOnce(
        new Promise((done) => {
          resolve = done
        }),
      )
      await refresh(view)
      const delivery = nodes(view.root).find(
        (node) => node.tag === 'input' && text(node.parent!).includes('HOMEWORK_UPDATED'),
      )!
      expect(delivery.props.disabled).not.toBe(true)
      expect(button(view.root, 'Сохраняем…')?.props.disabled).toBe(true)
      expect(nodes(view.root).find((node) => node.props.role === 'switch')?.props.disabled).toBe(
        true,
      )
      expect(view.api.updatePreferences).not.toHaveBeenCalled()
      ;(delivery.props.onChange as (event: unknown) => void)({ target: { checked: true } })
      await settle()
      expect(delivery.props.checked).toBe(true)
      resolve(profile(undefined, revision))
      await settle()
      expect(delivery.props.checked).toBe(true)
      let resolveSave!: (value: NotificationPreferencesV2) => void
      view.api.updatePreferences.mockImplementationOnce(
        () =>
          new Promise((done) => {
            resolveSave = done
          }),
      )
      if (revision === 'p2') {
        expect(delivery.props.disabled).toBe(true)
        expect(button(view.root, 'Сохранить настройки')?.props.disabled).toBe(true)
        await activate(view.root, 'Применить мой выбор')
      } else {
        expect(delivery.props.disabled).not.toBe(true)
        const form = nodes(view.root).find((node) => node.tag === 'form')!
        ;(form.props.onSubmit as (event: unknown) => void)({ preventDefault() {} })
      }
      await settle()
      expect(view.api.updatePreferences).toHaveBeenCalledTimes(1)
      expect(view.api.updatePreferences.mock.calls[0]![0]).toMatchObject({
        expectedRevision: revision,
        types: { HOMEWORK_UPDATED: { appPush: true, telegram: true, colorId: 'red' } },
      })
      expect(view.api.updatePreferences.mock.calls[0]![0]).not.toHaveProperty('mutedUntil')
      expect(delivery.props.disabled).toBe(true)
      resolveSave({
        ...profile(undefined, 'p3'),
        types: view.api.updatePreferences.mock.calls[0]![0].types,
      })
      await settle()
      expect(delivery.props.disabled).not.toBe(true)
      expect(delivery.props.checked).toBe(true)
    },
  )
  it.each([
    { busy: true },
    { busy: true, refreshing: true, offline: true },
    { busy: true, refreshing: true, blocked: true },
  ])('keeps delivery blocked for a mutation or denied editing state %j', async (state) => {
    const view = mount(NotificationSettings, {
      catalog: catalog(),
      preferences: profile(),
      draft: null,
      ...state,
    })
    await settle()
    expect(
      nodes(view.root)
        .filter((node) => node.tag === 'input')
        .every((node) => node.props.disabled === true),
    ).toBe(true)
  })
  it('discards a late profile pair when context changes before resolution', async () => {
    const view = host()
    await settle()
    await colors(view.root)
    let resolve!: (value: NotificationPreferencesV2) => void
    view.api.getPreferences.mockReturnValueOnce(
      new Promise((done) => {
        resolve = done
      }),
    )
    view.props.realtimeRevision++
    await nextTick()
    view.props.contextKey = 'STUDENT|other-synthetic'
    await settle()
    resolve(profile())
    await settle()
    expectPrivateGone(view.root)
    view.app.unmount()
    await settle()
    expectPrivateGone(view.root)
  })
  it.each(['NotificationSettings', 'NotificationColors'])(
    '%s refuses catalog/draft mismatch before accessing a missing entry',
    async (name) => {
      const view = mount(
        name === 'NotificationSettings' ? NotificationSettings : NotificationColors,
        {
          catalog: catalog(),
          preferences: profile(),
          draft: profile(models.legacyNotificationTypeIds),
        },
      )
      await settle()
      expect(nodes(view.root).filter((node) => node.tag === 'input')).toHaveLength(0)
      expect(
        button(
          view.root,
          name === 'NotificationSettings' ? 'Сохранить настройки' : 'Сохранить цвета',
        )?.props.disabled,
      ).toBe(true)
    },
  )
  it.each(['NotificationSettings', 'NotificationColors'])(
    '%s presents server order/labels and actual32 OFF/colors',
    async (name) => {
      const server = catalog([...models.notificationTypeIds].reverse())
      const view = mount(
        name === 'NotificationSettings' ? NotificationSettings : NotificationColors,
        { catalog: server, preferences: profile(), draft: null },
      )
      await settle()
      const visible =
        name === 'NotificationSettings'
          ? nodes(view.root)
              .filter(
                (node) => node.tag === 'tr' && node.props.class === 'notification-settings__row',
              )
              .map((node) => text(node.children[0]!))
          : nodes(view.root)
              .filter((node) => node.tag === 'button' && node.props['aria-expanded'] !== undefined)
              .map((node) => text(node))
      expect(visible).toHaveLength(32)
      server.types.forEach((type, index) => expect(visible[index]).toContain(type.label))
      if (name === 'NotificationSettings')
        expect(
          nodes(view.root).filter((node) => node.tag === 'input' && node.props.checked === false),
        ).toHaveLength(32)
      else
        expect(
          nodes(view.root)
            .filter((node) => node.tag === 'button' && node.props['aria-expanded'] !== undefined)
            .every((node) => String(node.props['aria-label']).endsWith(': red')),
        ).toBe(true)
    },
  )
  it.each(['NotificationSettings', 'NotificationColors'])(
    '%s shows catalog-change state even if a prior same-set conflict remains',
    async (name) => {
      const view = mount(
        name === 'NotificationSettings' ? NotificationSettings : NotificationColors,
        {
          catalog: catalog(),
          preferences: profile(),
          draft: profile(),
          conflict: profile(models.notificationTypeIds, 'p2'),
          blocked: true,
        },
      )
      await settle()
      expect(button(view.root, 'Применить мой выбор')?.props.disabled).toBe(true)
      expect(button(view.root, 'Загрузить актуальные')?.props.disabled).not.toBe(true)
      expect(
        button(
          view.root,
          name === 'NotificationSettings' ? 'Сохранить настройки' : 'Сохранить цвета',
        )?.props.disabled,
      ).toBe(true)
    },
  )
})

describe('notification draft save availability', () => {
  it('disables an unchanged delivery form again when an edit is reverted, without PUT', async () => {
    const view = host()
    await settle()
    await activate(view.root, 'Настройки уведомлений')
    expect(button(view.root, 'Сохранить настройки')?.props.disabled).toBe(true)
    const delivery = nodes(view.root).find(
      (node) => node.tag === 'input' && node.props.type === 'checkbox',
    )!
    const change = delivery.props.onChange as (event: unknown) => void
    change({ target: { checked: true } })
    await settle()
    expect(button(view.root, 'Сохранить настройки')?.props.disabled).toBe(false)
    change({ target: { checked: false } })
    await settle()
    expect(button(view.root, 'Сохранить настройки')?.props.disabled).toBe(true)
    const form = nodes(view.root).find((node) => node.tag === 'form')!
    ;(form.props.onSubmit as (event: unknown) => void)({ preventDefault() {} })
    expect(view.api.updatePreferences).not.toHaveBeenCalled()
  })
  it('keeps a failed color edit retryable, then adopts the authoritative ACK as the saved baseline', async () => {
    const view = host()
    await settle()
    await colors(view.root)
    expect(saveDisabled(view.root)).toBe(true)
    await chooseGrey(view.root)
    expect(saveDisabled(view.root)).toBe(false)
    view.api.updatePreferences.mockRejectedValueOnce(
      new client.NotificationsApiError('synthetic failed', 500),
    )
    await activate(view.root, 'Сохранить цвета')
    expect(greySelected(view.root)).toBe(true)
    expect(saveDisabled(view.root)).toBe(false)
    let finish!: (saved: NotificationPreferencesV2) => void
    view.api.updatePreferences.mockImplementationOnce(
      () =>
        new Promise((resolve) => {
          finish = resolve
        }),
    )
    await activate(view.root, 'Сохранить цвета')
    expect(button(view.root, 'Сохраняем…')?.props.disabled).toBe(true)
    // The server may normalize an accepted value; its ACK owns the new baseline.
    finish(profile(undefined, 'p2'))
    await settle()
    expect(button(view.root, 'red')?.props['aria-pressed']).toBe(true)
    expect(saveDisabled(view.root)).toBe(true)
    await activate(view.root, 'grey')
    expect(saveDisabled(view.root)).toBe(false)
    await activate(view.root, 'red')
    expect(saveDisabled(view.root)).toBe(true)
  })
  it.each([{ offline: true }, { busy: true }, { blocked: true }])(
    'blocks dirty delivery and color saves for %j',
    async (state) => {
      const draft = profile()
      draft.types.LESSON_STARTED.colorId = 'grey'
      for (const component of [NotificationSettings, NotificationColors]) {
        const view = mount(component, {
          catalog: catalog(),
          preferences: profile(),
          draft,
          ...state,
        })
        await settle()
        expect(
          button(
            view.root,
            state.busy
              ? 'Сохраняем…'
              : component === NotificationSettings
                ? 'Сохранить настройки'
                : 'Сохранить цвета',
          )?.props.disabled,
        ).toBe(true)
      }
    },
  )
})

describe('inbox read controls after acknowledgement', () => {
  it('marks expanded unread once, supports retry after failure and keeps mark-unread available after ACK', async () => {
    vi.stubGlobal('window', { addEventListener() {}, removeEventListener() {} })
    const item: models.NotificationItem = {
      id: 'notice-1',
      type: 'LESSON_STARTED',
      heading: 'Началась пара',
      sentAt: '2026-11-04T09:00:00Z',
      orderAt: '2026-11-04T09:00:00Z',
      readAt: null,
      revision: 'n1',
      details: [],
      action: null,
    }
    const readState = vi.fn()
    const props = reactive({ item, pending: false, 'onRead-state': readState })
    const view = mount(NotificationInboxCard, props)
    await settle()
    await activate(view.root, 'Подробности: Началась пара')
    expect(readState).toHaveBeenCalledWith(expect.objectContaining({ revision: 'n1' }), true)
    await activate(view.root, 'Свернуть: Началась пара')
    await activate(view.root, 'Подробности: Началась пара')
    expect(readState).toHaveBeenCalledTimes(1)
    props.pending = true
    await settle()
    props.pending = false
    await settle()
    await activate(view.root, 'Свернуть: Началась пара')
    await activate(view.root, 'Подробности: Началась пара')
    expect(readState).toHaveBeenCalledTimes(2)
    props.item = { ...item, revision: 'n2', readAt: '2026-11-04T09:01:00Z' }
    await settle()
    expect(nodes(view.root).find((node) => node.tag === 'article')?.props['data-unread']).toBe(
      false,
    )
    await activate(view.root, 'Действия: Началась пара')
    await activate(view.root, 'Отметить непрочитанным')
    expect(readState).toHaveBeenLastCalledWith(expect.objectContaining({ revision: 'n2' }), false)
  })
})
