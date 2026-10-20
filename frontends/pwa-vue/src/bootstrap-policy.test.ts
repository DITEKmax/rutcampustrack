import { readFileSync } from 'node:fs'
import { createRenderer, h, markRaw, nextTick, type App } from 'vue'
import { QueryClient, VueQueryPlugin } from '@tanstack/vue-query'
import { afterEach, describe, expect, it, vi } from 'vitest'
import { ProfileRequestError, SemesterSnapshotStore } from '@rct/mobile-core'
import { AuthProtocolError, AuthRequestError } from './auth-client'
import { isOfflineBootstrapRecoveryError } from './bootstrap-policy'
import AppComponent from './App.vue'

vi.mock('@rct/mobile-core', async importOriginal => {
  const actual = await importOriginal<typeof import('@rct/mobile-core')>()
  const { h } = await import('vue')
  return { ...actual, StudentFeatureOwner: {
    props: ['scope', 'offline', 'readOnly', 'onRetryConnection', 'onLogout', 'onRecover'],
    setup(props: any) { return () => h('section', [
      h('output', { 'data-owner': true }, JSON.stringify({ userId: props.scope?.userId, sessionId: props.scope?.sessionId, offline: props.offline, readOnly: props.readOnly })),
      h('button', { onClick: props.onRetryConnection }, 'Retry connection'),
      h('button', { onClick: props.onLogout }, 'Logout'),
      h('button', { onClick: props.onRecover }, 'Recover password'),
    ]) },
  } }
})
vi.mock('./PwaUpdateGate.vue', () => ({ default: { render: () => null } }))
vi.mock('./features/password-recovery/PasswordRecoveryScreen.vue', () => ({ default: { render: () => h('section', { 'data-recovery': true }) } }))
vi.mock('./features/install/InstallOffer.vue', () => ({ default: { render: () => null } }))
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
  style: Record<string, string> = {}
  focus(): void {}
  contains(node: unknown): boolean { return nodes(this).includes(node as Node) }
  getAttribute(key: string): string | null { return this.props[key] === undefined ? null : String(this.props[key]) }
  setAttribute(key: string, value: string) { this.props[key] = value }
  removeAttribute(key: string) { delete this.props[key] }
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
  querySelector: selector => selector === 'body' ? (document as unknown as { body: Node }).body : null,
  insertStaticContent: (content, parent, anchor) => {
    const node = Object.assign(new Node('#text'), { text: content }); node.parent = parent; parent.children.push(node); return [node, node]
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
async function click(root: Node, label: string): Promise<void> {
  const button = nodes(root).find((node) => node.tag === 'button' && text(node).trim() === label)
  expect(button).toBeDefined()
  ;(button!.props.onClick as () => void)()
  await settle()
}

describe('PWA cold bootstrap recovery policy', () => {
  it('allows a rejected fetch-shaped Auth NETWORK failure to consult the committed snapshot', () => {
    const error = new AuthRequestError(
      'refresh',
      undefined,
      { code: 'NETWORK', title: 'Нет соединения с сервером' },
      new TypeError('fetch failed'),
    )

    expect(isOfflineBootstrapRecoveryError(error)).toBe(true)
  })

  it.each([
    'INVALID_SESSION',
    'SESSION_REVOKED',
    'ACCOUNT_INVALIDATED',
    'REFRESH_REJECTED',
  ] as const)('keeps %s fail closed', (code) => {
    const error = new AuthRequestError('refresh', undefined, { code })

    expect(isOfflineBootstrapRecoveryError(error)).toBe(false)
  })

  it.each([401, 403, 409, 503] as const)(
    'keeps HTTP %s with a NETWORK problem code fail closed',
    (status) => {
      expect(
        isOfflineBootstrapRecoveryError(
          new AuthRequestError('session', status, { code: 'NETWORK' }),
        ),
      ).toBe(false)
    },
  )

  it('keeps stale-session and protocol errors fail closed', () => {
    expect(
      isOfflineBootstrapRecoveryError(new ProfileRequestError('SESSION_STATE_STALE', 'stale')),
    ).toBe(false)
    expect(
      isOfflineBootstrapRecoveryError(new AuthProtocolError('refresh', 502, 'malformed response')),
    ).toBe(false)
  })

  it('does not treat an untyped profile NETWORK error as auth transport loss', () => {
    expect(isOfflineBootstrapRecoveryError(new ProfileRequestError('NETWORK', 'network'))).toBe(
      false,
    )
  })

  it('keeps StudentApi-like and unknown bootstrap failures outside recovery', () => {
    const studentApiLike = Object.assign(new Error('BFF HTTP 403'), {
      status: 403,
      code: 'UNKNOWN',
    })

    expect(isOfflineBootstrapRecoveryError(studentApiLike)).toBe(false)
    expect(isOfflineBootstrapRecoveryError(new Error('malformed bootstrap response'))).toBe(false)
  })

})

const queryClients: QueryClient[] = []
afterEach(() => { queryClients.splice(0).forEach(client => client.clear()); vi.restoreAllMocks() })
function storage() {
  const values = new Map<string, string>()
  return { getItem: (key: string) => values.get(key) ?? null, setItem: (key: string, value: string) => values.set(key, value), removeItem: (key: string) => values.delete(key), clear: () => values.clear() }
}
const bff = JSON.parse(readFileSync(new URL('../../mobile-core/fixtures/session.json', import.meta.url), 'utf8'))
const schedule = JSON.parse(readFileSync(new URL('../../mobile-core/fixtures/semester-schedule.json', import.meta.url), 'utf8'))
const profile = () => ({ sessionId: bff.sessionId, userId: bff.user.id, displayName: bff.user.displayName,
  groupLabel: bff.group.name, sessionVersion: bff.sessionVersion, rolesVersion: bff.rolesVersion,
  activeRole: 'STUDENT', roles: [{ grantId: '17', role: 'STUDENT', status: 'ACTIVE', groupId: '17', contextLabel: 'Group', selectable: true, readOnly: false }],
  readOnly: false, passwordPolicy: { minCodePoints: 12, maxUtf8Bytes: 72, requiresDecimalDigit: true, specialCategories: ['P', 'S'], normalization: 'NONE' } })
function deferred() {
  let resolve!: (response: Response) => void, reject!: (reason: unknown) => void
  const promise = new Promise<Response>((done, fail) => { resolve = done; reject = fail })
  return { promise, resolve, reject }
}
function mountApp(options: { initialRefresh?: ReturnType<typeof deferred>; initialProfile?: unknown } = {}) {
  const channels: EventTarget[] = []
  const browser = Object.assign(new EventTarget(), { location: { origin: 'https://synthetic.invalid', pathname: '/', search: '', hash: '', href: 'https://synthetic.invalid/' },
    localStorage: storage(), sessionStorage: storage(), BroadcastChannel: class extends EventTarget { constructor() { super(); channels.push(this) }; postMessage() {}; close() {} }, innerHeight: 900, setInterval: globalThis.setInterval, clearInterval: globalThis.clearInterval, history: { replaceState() {} } })
  const documentTarget = Object.assign(new EventTarget(), { documentElement: new Node('html'), body: new Node('body'), defaultView: null, visibilityState: 'visible', getElementById: () => null, querySelector: () => null })
  vi.stubGlobal('window', browser)
  vi.stubGlobal('document', documentTarget)
  vi.stubGlobal('navigator', { onLine: true })
  const requestLog: { url: string; method: string; authorization: string | null }[] = []
  let refresh = options.initialRefresh
  let nextCurrent: ReturnType<typeof deferred> | undefined
  let currentProfile = options.initialProfile ?? profile()
  let currentSession = bff
  let onInvalidate: (() => void) | undefined
  vi.spyOn(SemesterSnapshotStore.prototype, 'invalidatePendingWrites').mockImplementation(() => { const callback = onInvalidate; onInvalidate = undefined; callback?.() })
  const fetcher = vi.fn<typeof fetch>(async (input, init) => {
    const url = new URL(String(input), browser.location.origin)
    const method = init?.method ?? 'GET'
    requestLog.push({ url: url.pathname, method, authorization: new Headers(init?.headers).get('Authorization') })
    if (url.pathname === '/api/auth/refresh') {
      if (refresh) { const pending = refresh; refresh = undefined; return pending.promise }
      return Response.json({ accessToken: 'synthetic-access', expiresIn: 3600 })
    }
    if (url.pathname === '/api/auth/session') {
      if (nextCurrent) { const pending = nextCurrent; nextCurrent = undefined; return pending.promise }
      return Response.json(currentProfile)
    }
    if (url.pathname === '/api/auth/logout') return new Response(null, { status: 204 })
    if (url.pathname === '/api/v1/student/session') return Response.json(currentSession)
    if (url.pathname.includes('/schedule')) return Response.json(schedule)
    if (url.pathname.includes('/permissions')) return Response.json([])
    if (url.pathname.includes('unread')) return Response.json({ unreadCount: 0 })
    return new Response(null, { status: 403 })
  })
  vi.stubGlobal('fetch', fetcher)
  const snapshot = { ownerId: bff.user.id, scopeKey: JSON.stringify(['student-read-model-v1', bff.user.id, 'STUDENT', bff.group.id, bff.semester.id]),
    scope: { userId: bff.user.id, activeRole: 'STUDENT', groupId: bff.group.id, semesterId: bff.semester.id }, schedule, etag: null, homework: null }
  vi.spyOn(SemesterSnapshotStore.prototype, 'hasUnresolvedCleanup').mockReturnValue(false)
  const readCurrent = vi.spyOn(SemesterSnapshotStore.prototype, 'readCurrent').mockResolvedValue(snapshot as any)
  vi.spyOn(SemesterSnapshotStore.prototype, 'read').mockResolvedValue(null)
  vi.spyOn(SemesterSnapshotStore.prototype, 'clearMismatchedCurrent').mockResolvedValue(null)
  const cleanup = { previousOwnerId: null, ownerId: bff.user.id, retryRequired: false, failures: [] }
  vi.spyOn(SemesterSnapshotStore.prototype, 'switchToDetailed').mockResolvedValue(cleanup as any)
  vi.spyOn(SemesterSnapshotStore.prototype, 'clearDetailed').mockResolvedValue(cleanup as any)
  vi.spyOn(SemesterSnapshotStore.prototype, 'write').mockResolvedValue(undefined)
  const root = new Node('root')
  const app = renderer.createApp(AppComponent, { installPrompt: { subscribe: () => () => {}, snapshot: () => ({ status: 'unavailable' }) } })
  const client = new QueryClient({ defaultOptions: { queries: { retry: false } } })
  queryClients.push(client); app.use(VueQueryPlugin, { queryClient: client }); apps.push(app); app.mount(root)
  return { root, app, browser, requestLog, readCurrent, invalidate: () => channels.forEach(channel => channel.dispatchEvent(new MessageEvent('message', { data: { type: 'rct-auth-invalidate-v1' } }))), deferCurrent: () => (nextCurrent = deferred()), setAuthority: (value: any) => { currentProfile = value; currentSession = { ...bff, sessionVersion: value.sessionVersion, rolesVersion: value.rolesVersion } }, onInvalidate: (callback: () => void) => { onInvalidate = callback } }
}
function owner(root: Node) {
  const output = nodes(root).find(node => node.props['data-owner'])
  return output ? JSON.parse(output.text) : null
}
async function admitted(view: ReturnType<typeof mountApp>) {
  await vi.waitFor(() => expect(owner(view.root)).toMatchObject({ userId: bff.user.id, offline: false, readOnly: false }))
}
describe('actual PWA bootstrap and reconnect boundaries', () => {
  it('admits a matching real Auth/BFF owner and keeps failed narrow recovery typed', async () => {
    const view = mountApp()
    await admitted(view)
    expect(view.readCurrent).not.toHaveBeenCalled()
    expect(view.requestLog).toEqual(expect.arrayContaining([
      expect.objectContaining({ url: '/api/auth/refresh', method: 'POST' }),
      expect.objectContaining({ url: '/api/auth/session', method: 'GET', authorization: 'Bearer synthetic-access' }),
      expect.objectContaining({ url: '/api/v1/student/session', method: 'GET', authorization: 'Bearer synthetic-access' }),
    ]))
  })
  it('recovers a cold network failure from the committed read-only snapshot', async () => {
    const first = deferred(); const view = mountApp({ initialRefresh: first })
    first.reject(new TypeError('synthetic connection lost'))
    await vi.waitFor(() => expect(owner(view.root)).toMatchObject({ userId: bff.user.id, offline: true, readOnly: true }))
    expect(view.readCurrent).toHaveBeenCalledTimes(1)
  })
  it.each([401, 403, 500])('refuses snapshot hydration on confirmed/protocol HTTP %i', async status => {
    const first = deferred(); const view = mountApp({ initialRefresh: first })
    first.resolve(Response.json({ status, title: 'synthetic failure' }, { status }))
    await settle()
    expect(view.readCurrent).not.toHaveBeenCalled()
    expect(owner(view.root)).toBeNull()
  })
  it('keeps the admitted owner read-only until explicit current-only retry completes', async () => {
    const view = mountApp(); await admitted(view)
    view.browser.dispatchEvent(new Event('offline')); await settle()
    const current = view.deferCurrent()
    const refreshBefore = view.requestLog.filter(row => row.url === '/api/auth/refresh').length
    await click(view.root, 'Retry connection')
    expect(owner(view.root)).toMatchObject({ offline: true, readOnly: true })
    expect(view.requestLog.filter(row => row.url === '/api/auth/refresh')).toHaveLength(refreshBefore)
    current.resolve(Response.json(profile()))
    await admitted(view)
    expect(view.requestLog.filter(row => row.url === '/api/auth/refresh')).toHaveLength(refreshBefore)
  })
  it('coalesces overlapping online events while an earlier admission is pending', async () => {
    const first = deferred(); const view = mountApp({ initialRefresh: first })
    view.browser.dispatchEvent(new Event('offline'))
    for (let count = 0; count < 3; count++) view.browser.dispatchEvent(new Event('online'))
    expect(view.requestLog.filter(row => row.url === '/api/auth/refresh')).toHaveLength(1)
    first.resolve(Response.json({ accessToken: 'synthetic-access', expiresIn: 3600 }))
    await admitted(view)
    await settle()
    expect(view.requestLog.filter(row => row.url === '/api/auth/refresh')).toHaveLength(2)
  })
  it.each(['offline', 'unmount', 'browser-offline'] as const)('cancels queued demand after %s', async reason => {
    const first = deferred(); const view = mountApp({ initialRefresh: first })
    view.browser.dispatchEvent(new Event('online'))
    if (reason === 'offline') view.browser.dispatchEvent(new Event('offline'))
    else if (reason === 'unmount') view.app.unmount()
    else Object.defineProperty(navigator, 'onLine', { value: false, configurable: true })
    first.resolve(Response.json({ accessToken: 'synthetic-access', expiresIn: 3600 }))
    await settle()
    expect(view.requestLog.filter(row => row.url === '/api/auth/refresh')).toHaveLength(1)
    if (reason === 'unmount') expect(owner(view.root)).toBeNull()
  })
  it('does not reconnect automatically after an uncertain failed refresh', async () => {
    const first = deferred(); const view = mountApp({ initialRefresh: first })
    view.browser.dispatchEvent(new Event('online'))
    first.reject(new TypeError('lost refresh response'))
    await settle()
    expect(view.requestLog.filter(row => row.url === '/api/auth/refresh')).toHaveLength(1)
    expect(owner(view.root)).toMatchObject({ offline: true, readOnly: true })
  })
  it.each(['logout', 'new-generation', 'auto-bootstrap-disabled', 'password-recovery'] as const)(
    'cancels queued demand and rejects late current data after %s', async reason => {
      const view = mountApp(); await admitted(view)
      const pending = view.deferCurrent()
      view.browser.dispatchEvent(new Event('online'))
      await settle()
      const sentBefore = view.requestLog.filter(row => row.url === '/api/auth/refresh').length
      view.browser.dispatchEvent(new Event('online'))
      if (reason === 'new-generation') view.invalidate()
      else if (reason === 'logout') {
        await click(view.root, 'Logout')
      } else if (reason === 'auto-bootstrap-disabled') {
        view.browser.localStorage.setItem('rct-pwa-explicit-logout-v1', 'confirmed')
      } else await click(view.root, 'Recover password')
      pending.resolve(Response.json(profile()))
      await settle()
      expect(view.requestLog.filter(row => row.url === '/api/auth/refresh')).toHaveLength(sentBefore)
      if (reason !== 'auto-bootstrap-disabled') expect(owner(view.root)).toBeNull()
    },
  )
  it.each(['absent', 'stale', 'valid'] as const)(
    'reconciles changed authority with %s online demand using real auth generation', async demand => {
      const view = mountApp(); await admitted(view)
      const pending = view.deferCurrent()
      view.browser.dispatchEvent(new Event('online'))
      await settle()
      const refreshBefore = view.requestLog.filter(row => row.url === '/api/auth/refresh').length
      const changed = { ...profile(), rolesVersion: '8' }
      view.setAuthority(changed)
      if (demand === 'stale') view.browser.dispatchEvent(new Event('online'))
      if (demand === 'valid') view.onInvalidate(() => view.browser.dispatchEvent(new Event('online')))
      pending.resolve(Response.json(changed))
      await admitted(view)
      await settle()
      expect(view.requestLog.filter(row => row.url === '/api/auth/refresh')).toHaveLength(refreshBefore + (demand === 'valid' ? 1 : 0))
      expect(owner(view.root)).toMatchObject({ userId: bff.user.id, offline: false, readOnly: false })
    },
  )
  it('makes lost online demand observable as a single admission without a queued event', async () => {
    const first = deferred(); const view = mountApp({ initialRefresh: first })
    view.browser.dispatchEvent(new Event('offline'))
    // Negative control: withhold the online event from the public browser boundary.
    // The ordinary successful bootstrap alone must not manufacture a queued recheck.
    first.resolve(Response.json({ accessToken: 'synthetic-access', expiresIn: 3600 }))
    await admitted(view)
    await settle()
    expect(view.requestLog.filter(row => row.url === '/api/auth/refresh')).toHaveLength(1)
  })

})
