import { createRenderer, nextTick, type App } from 'vue'
import { createMemoryHistory, createRouter, type Router } from 'vue-router'
import { afterEach, describe, expect, it, vi } from 'vitest'

const source = vi.hoisted(() => ({ router: null as Router | null }))
vi.mock('./router', () => ({
  get router() {
    return source.router
  },
}))
vi.mock('./features', () => ({ webFeatures: [], canShowFeature: () => false }))
vi.mock('../../../mobile-core/src/features/notifications/notifications-realtime', () => ({
  createGenerationBoundNotificationsRealtime: () => ({ dispose() {} }),
}))
vi.mock('../features/notifications/NotificationHost.vue', () => ({
  default: { render: () => null },
}))
vi.mock('../shared/ThemeSelect.vue', () => ({ default: { render: () => null } }))
vi.mock('../auth/ReauthenticationReceipt.vue', () => ({ default: { render: () => null } }))
vi.mock('../features/admin/ExportControl.vue', () => ({ default: { render: () => null } }))

class HostDocument extends EventTarget {
  visibilityState = 'visible'
  activeElement: HostNode | null = null
}
let document: HostDocument
class HostNode {
  children: HostNode[] = []
  parent: HostNode | null = null
  props: Record<string, unknown> = {}
  events = new Map<string, Array<(event: { target: HostNode }) => void>>()
  text = ''
  value = ''
  _value: unknown
  selected = false
  selectedIndex = -1
  constructor(readonly tag: string) {}
  get options() {
    return this.children.filter((n) => n.tag === 'option')
  }
  get type() {
    return this.props.type ?? 'text'
  }
  getRootNode() {
    return document
  }
  focus() {
    document.activeElement = this
  }
  addEventListener(name: string, fn: (event: { target: HostNode }) => void) {
    this.events.set(name, [...(this.events.get(name) ?? []), fn])
  }
  input(value: string) {
    this.value = value
    this.events.get('input')?.forEach((fn) => fn({ target: this }))
  }
}
const nodes = (n: HostNode): HostNode[] => [n, ...n.children.flatMap(nodes)]
const text = (n: HostNode): string => n.text + n.children.map(text).join(' ')
const renderer = createRenderer<HostNode, HostNode>({
  createElement: (tag) => new HostNode(tag),
  createText: (value) => Object.assign(new HostNode('#text'), { text: value }),
  createComment: () => new HostNode('#comment'),
  setText: (node, value) => {
    node.text = value
  },
  setElementText: (node, value) => {
    node.text = value
    node.children = []
  },
  parentNode: (node) => node.parent,
  nextSibling: (node) => node.parent?.children[node.parent.children.indexOf(node) + 1] ?? null,
  patchProp: (node, key, _old, value) => {
    node.props[key] = value
    if (key === 'value') {
      node.value = String(value ?? '')
      node._value = value
    }
  },
  insert: (node, parent, anchor = null) => {
    if (node.parent) node.parent.children.splice(node.parent.children.indexOf(node), 1)
    const at = anchor ? parent.children.indexOf(anchor) : -1
    parent.children.splice(at < 0 ? parent.children.length : at, 0, node)
    node.parent = parent
  },
  remove: (node) => {
    if (document.activeElement && nodes(node).includes(document.activeElement))
      document.activeElement = null
    node.parent?.children.splice(node.parent.children.indexOf(node), 1)
    node.parent = null
  },
})
function snapshot(userId = '2', rolesVersion = '5', activeRole: string | null = 'ADMIN') {
  return {
    sessionId: `00000000-0000-4000-8000-${userId.padStart(12, '0')}`,
    userId,
    displayName: 'Преподаватель',
    sessionVersion: '1',
    rolesVersion,
    activeRole,
    readOnly: false,
    roles: [
      { grantId: '1', role: 'TEACHER', status: 'ACTIVE', selectable: true, readOnly: false },
      ...(activeRole === 'ADMIN'
        ? [{ grantId: '2', role: 'ADMIN', status: 'ACTIVE', selectable: true, readOnly: false }]
        : []),
    ],
    passwordPolicy: {
      minCodePoints: 12,
      maxUtf8Bytes: 72,
      requiresDecimalDigit: true,
      specialCategories: ['P', 'S'],
      normalization: 'NONE',
    },
  }
}
let app: App | undefined
let invalidate: (() => void) | undefined
afterEach(() => {
  app?.unmount()
  app = undefined
  invalidate?.()
  invalidate = undefined
  source.router = null
  vi.clearAllTimers()
  vi.useRealTimers()
  vi.unstubAllGlobals()
  vi.resetModules()
})
async function settle() {
  for (let n = 0; n < 8; n++) {
    await new Promise<void>((resolve) => setImmediate(resolve))
    await nextTick()
  }
}
async function mounted() {
  vi.useFakeTimers({ toFake: ['setTimeout', 'clearTimeout'] })
  document = new HostDocument()
  vi.stubGlobal('document', document)
  vi.stubGlobal('Document', HostDocument)
  vi.stubGlobal('ShadowRoot', class {})
  vi.stubGlobal('window', new EventTarget())
  let server = snapshot()
  let permissions = ['USERS_VIEW', 'GROUPS_VIEW']
  let probe: ((init?: RequestInit) => Promise<Response>) | null = null
  let refresh: ((init?: RequestInit) => Promise<Response>) | null = null
  const requests: Array<{ path: string; method: string }> = []
  vi.stubGlobal('fetch', async (input: RequestInfo | URL, init?: RequestInit) => {
    const path = String(input),
      method = init?.method ?? 'GET'
    requests.push({ path, method })
    if (path.endsWith('/login')) return Response.json({ accessToken: `login-${server.userId}` })
    if (path.endsWith('/refresh'))
      return refresh ? refresh(init) : Response.json({ accessToken: `refresh-${server.userId}` })
    if (path.endsWith('/session')) return probe ? probe(init) : Response.json(server)
    if (path.endsWith('/admin-access/me'))
      return Response.json({
        userId: Number(server.userId),
        assigned: true,
        mode: 'LIMITED',
        version: Number(server.rolesVersion),
        permissions,
      })
    if (path.startsWith('/api/academic/groups/registry?'))
      return Response.json({
        items: [],
        number: 0,
        size: 30,
        totalElements: 0,
        totalPages: 0,
        activeCount: 0,
        draftCount: 0,
        archivedCount: 0,
      })
    throw new Error('Unexpected request')
  })
  const { webSession } = await import('../auth/session')
  invalidate = webSession.invalidate
  await webSession.login('teacher', 'fixture')
  const [{ default: Root }, { default: Shell }, { default: Groups }] = await Promise.all([
    import('../App.vue'),
    import('../shell/AppShell.vue'),
    import('../features/admin/GroupsPage.vue'),
  ])
  const router = createRouter({
    history: createMemoryHistory(),
    routes: [
      {
        path: '/admin',
        component: Shell,
        meta: { requiredRoles: ['ADMIN'] },
        children: [{ path: 'groups', component: Groups }],
      },
      { path: '/', name: 'role-home', component: { render: () => 'Выбери роль' } },
      {
        path: '/login',
        name: 'login',
        meta: { guest: true },
        component: { render: () => 'Войти' },
      },
    ],
  })
  source.router = router
  await router.push('/admin/groups')
  const root = new HostNode('root')
  app = renderer.createApp(Root)
  app.use(router)
  app.mount(root)
  await settle()
  const controls = () =>
    nodes(root).filter((n) => String(n.props.class ?? '').includes('admin-toolbar'))
  expect(controls()).toHaveLength(1)
  return {
    webSession,
    root,
    requests,
    controls,
    search: () => nodes(root).find((n) => n.tag === 'input' && n.props.type === 'search')!,
    change: (version: string, nextPermissions = permissions, role: string | null = 'ADMIN') => {
      server = snapshot(server.userId, version, role)
      permissions = nextPermissions
    },
    successor: () => {
      server = snapshot('43')
    },
    probe: (handler: typeof probe) => {
      probe = handler
    },
    refresh: (handler: typeof refresh) => {
      refresh = handler
    },
    reads: (suffix: string) => requests.filter((r) => r.path.endsWith(suffix)).length,
    groupsReads: () =>
      requests.filter((r) => r.path.startsWith('/api/academic/groups/registry?')).length,
  }
}

describe('idle authority on a mounted ADMIN page', () => {
  it('keeps the actual mounted form, focus and domain projection through unchanged authority checks', async () => {
    const f = await mounted(),
      field = f.search(),
      generation = f.webSession.generation.value
    field.input('Несохранённый фильтр')
    field.focus()
    await nextTick()
    await vi.advanceTimersByTimeAsync(15000)
    await settle()
    expect(f.reads('/session')).toBe(4)
    expect(f.groupsReads()).toBe(1)
    expect(f.webSession.generation.value).toBe(generation)
    expect(f.search()).toBe(field)
    expect(field.value).toBe('Несохранённый фильтр')
    expect(document.activeElement).toBe(field)
  })

  it('withdraws cached groups and controls after an idle permission revision even with the same active role', async () => {
    const f = await mounted(),
      field = f.search()
    field.input('Черновик не блокирует отзыв')
    f.change('6', ['USERS_VIEW'])
    await vi.advanceTimersByTimeAsync(5000)
    await settle()
    expect(f.webSession.snapshot.value?.activeRole).toBe('ADMIN')
    expect(f.webSession.snapshot.value?.rolesVersion).toBe('6')
    expect(f.controls()).toHaveLength(0)
    expect(text(f.root)).not.toContain('Группы не найдены.')
    expect(nodes(f.root)).not.toContain(field)
    expect(f.groupsReads()).toBe(1)
  })

  it('preserves the mounted form when an expired bearer refreshes to the same authority', async () => {
    const f = await mounted(),
      field = f.search(),
      generation = f.webSession.generation.value
    field.input('Несохранённый фильтр')
    field.focus()
    let stale = true
    f.probe(async () => {
      if (stale) {
        stale = false
        return new Response(null, { status: 401 })
      }
      return Response.json(snapshot())
    })
    await vi.advanceTimersByTimeAsync(5000)
    await settle()
    expect(f.reads('/refresh')).toBe(1)
    expect(f.webSession.generation.value).toBe(generation)
    expect(f.search()).toBe(field)
    expect(field.value).toBe('Несохранённый фильтр')
    expect(document.activeElement).toBe(field)
    expect(f.groupsReads()).toBe(1)
  })

  it('reconciles a revoked old ADMIN bearer through normal refresh without navigation or a focus signal', async () => {
    const f = await mounted()
    f.change('7', [], null)
    let stale = true
    f.probe(async () => {
      if (stale) {
        stale = false
        return new Response(null, { status: 401 })
      }
      return Response.json(snapshot('2', '7', null))
    })
    await vi.advanceTimersByTimeAsync(5000)
    await settle()
    expect(f.reads('/refresh')).toBe(1)
    expect(f.webSession.snapshot.value?.activeRole).toBeNull()
    expect(
      f.webSession.snapshot.value?.roles.some((r) => r.role === 'TEACHER' && r.selectable),
    ).toBe(true)
    expect(f.controls()).toHaveLength(0)
    expect(text(f.root)).toContain('Выбери роль')
    expect(f.requests.filter((r) => r.method !== 'GET').map((r) => r.path)).toEqual([
      '/api/auth/login',
      '/api/auth/refresh',
    ])
  })

  it('stops polling hidden pages and resumes authority reads when visible', async () => {
    const f = await mounted()
    document.visibilityState = 'hidden'
    document.dispatchEvent(new Event('visibilitychange'))
    await vi.advanceTimersByTimeAsync(20000)
    expect(f.reads('/session')).toBe(1)
    document.visibilityState = 'visible'
    document.dispatchEvent(new Event('visibilitychange'))
    await vi.advanceTimersByTimeAsync(5000)
    await settle()
    expect(f.reads('/session')).toBe(2)
    expect(f.groupsReads()).toBe(1)
  })

  it('retires protected content on a bounded unknown read and recovers through ordinary session retry', async () => {
    const f = await mounted()
    f.probe(
      (init) =>
        new Promise((_resolve, reject) =>
          init?.signal?.addEventListener('abort', () => reject(new TypeError('Unavailable'))),
        ),
    )
    await vi.advanceTimersByTimeAsync(9999)
    expect(f.reads('/session')).toBe(2)
    expect(f.controls()).toHaveLength(1)
    await vi.advanceTimersByTimeAsync(1)
    await settle()
    expect(f.webSession.phase.value).toBe('error')
    expect(f.controls()).toHaveLength(0)
    expect(text(f.root)).toContain('Сессия недоступна')
    f.probe(null)
    await f.webSession.bootstrap()
    await settle()
    expect(f.webSession.phase.value).toBe('ready')
    expect(f.controls()).toHaveLength(1)
  })

  it('bounds an already running refresh when the authority probe also rejects the old bearer', async () => {
    const f = await mounted()
    f.refresh(
      (init) =>
        new Promise((_resolve, reject) =>
          init?.signal?.addEventListener('abort', () => reject(new TypeError('Unavailable'))),
        ),
    )
    const refreshing = f.webSession.refresh().catch(() => undefined)
    f.probe(async () => new Response(null, { status: 401 }))
    await vi.advanceTimersByTimeAsync(10000)
    await refreshing
    await settle()
    expect(f.reads('/refresh')).toBe(1)
    expect(f.webSession.phase.value).toBe('error')
    expect(f.controls()).toHaveLength(0)
    f.refresh(null)
    f.probe(null)
    await f.webSession.bootstrap()
    await settle()
    expect(f.controls()).toHaveLength(1)
  })

  it.each(['200', '401', 'network'] as const)(
    'ignores an obsolete authority %s after a successor login',
    async (outcome) => {
      const f = await mounted()
      let resolve!: (value: Response) => void, reject!: (cause: Error) => void
      const old = new Promise<Response>((yes, no) => {
        resolve = yes
        reject = no
      })
      f.probe(() => old)
      await vi.advanceTimersByTimeAsync(5000)
      f.webSession.invalidate()
      f.successor()
      f.probe(null)
      await f.webSession.login('successor', 'fixture')
      if (outcome === 'network') reject(new TypeError('Old network failure'))
      else
        resolve(outcome === '401' ? new Response(null, { status: 401 }) : Response.json(snapshot()))
      await settle()
      expect(f.webSession.phase.value).toBe('ready')
      expect(f.webSession.snapshot.value?.userId).toBe('43')
      expect(f.webSession.accessToken()).toBe('login-43')
      expect(f.reads('/refresh')).toBe(0)
    },
  )
})
