import { createRenderer, defineComponent, h, nextTick, onUnmounted, ref, type App } from 'vue'
import { afterEach, describe, expect, it, vi } from 'vitest'

class HostNode {
  children: HostNode[] = []
  parent: HostNode | null = null
  props: Record<string, unknown> = {}
  text = ''
  value = ''
  constructor(readonly tag: string) {}
  focus() {
    moveFocus(this)
  }
}
let focused: HostNode | null = null
function moveFocus(node: HostNode) {
  focused = node
}
const nodes = (node: HostNode): HostNode[] => [node, ...node.children.flatMap(nodes)]
const renderer = createRenderer<HostNode, HostNode>({
  createElement: (tag) => new HostNode(tag),
  createText: (text) => Object.assign(new HostNode('#text'), { text }),
  createComment: () => new HostNode('#comment'),
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
    const at = anchor ? parent.children.indexOf(anchor) : -1
    parent.children.splice(at < 0 ? parent.children.length : at, 0, node)
    node.parent = parent
  },
  remove: (node) => {
    if (focused && nodes(node).includes(focused)) focused = null
    node.parent?.children.splice(node.parent.children.indexOf(node), 1)
    node.parent = null
  },
})
function deferred<T>() {
  let resolve!: (value: T) => void
  const promise = new Promise<T>((done) => {
    resolve = done
  })
  return { promise, resolve }
}
const stats = [{ code: 'VIEW_STATS', label: 'Статистика' }]
function snapshot(user: string) {
  return {
    sessionId: '00000000-0000-4000-8000-000000000001',
    userId: user,
    displayName: 'Студент',
    sessionVersion: '1',
    rolesVersion: '1',
    activeRole: 'STUDENT',
    readOnly: false,
    roles: [
      {
        grantId: '1',
        role: 'STUDENT',
        status: 'ACTIVE',
        selectable: true,
        readOnly: false,
        groupId: '1',
      },
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
  focused = null
  vi.unstubAllGlobals()
  vi.resetModules()
})
async function mountFixture() {
  let serverPermissions = stats
  let user = '42'
  const reads: Array<Promise<Response>> = []
  vi.stubGlobal('fetch', async (input: RequestInfo | URL) => {
    const path = String(input)
    if (path.endsWith('/login')) return Response.json({ accessToken: 'test-token' })
    if (path.endsWith('/session')) return Response.json(snapshot(user))
    if (path.endsWith('/assistants/me/permissions'))
      return reads.shift() ?? Response.json(serverPermissions)
    throw new Error('Unexpected endpoint')
  })
  const { webSession } = await import('../auth/session')
  const { webAssistantAccess: access } = await import('./assistant')
  const { default: Boundary } = await import('../shell/AssistantRouteBoundary.vue')
  invalidate = webSession.invalidate
  await webSession.login('test', 'password')
  await access.load()
  const saved = { draft: '', cleanups: 0 }
  const Probe = {
    setup() {
      const draft = ref('')
      onUnmounted(() => {
        saved.draft = draft.value
        saved.cleanups++
      })
      return () =>
        h('div', [
          h('table', [h('tr', [h('td', 'Scoped statistics')])]),
          h('input', {
            value: draft.value,
            onInput: (event: { target: { value: string } }) => {
              draft.value = event.target.value
            },
          }),
        ])
    },
  }
  const root = new HostNode('root')
  const visible = access.has('VIEW_STATS')
  app = renderer.createApp({
    setup: () => () =>
      h('main', [
        visible.value ? h('a', { href: '/headman/statistics' }, 'Statistics navigation') : null,
        webSession.snapshot.value
          ? h(Boundary, { permission: 'VIEW_STATS' }, { default: () => h(Probe) })
          : null,
      ]),
  })
  app.component(
    'RouterLink',
    defineComponent({
      setup: (_props, context) => () => h('a', context.slots.default?.()),
    }),
  )
  app.mount(root)
  await nextTick()
  return {
    root,
    saved,
    access,
    webSession,
    reads,
    revoke: () => {
      serverPermissions = []
    },
    nextUser: () => {
      user = '43'
      serverPermissions = []
    },
  }
}
describe('mounted delegated route authority', () => {
  it('drains a revoke invalidation arriving during an old read and removes private tables and navigation', async () => {
    const f = await mountFixture()
    expect(nodes(f.root).some((node) => node.tag === 'table')).toBe(true)
    const held = deferred<Response>()
    f.reads.push(held.promise)
    const oldRead = f.access.load(true)
    f.revoke()
    const revokeEvent = f.access.load(true)
    held.resolve(Response.json(stats))
    await Promise.all([oldRead, revokeEvent])
    await nextTick()
    expect(f.access.loaded.value).toBe(true)
    expect(f.access.permissions.value).toEqual([])
    expect(nodes(f.root).some((node) => node.tag === 'table')).toBe(false)
    expect(nodes(f.root).some((node) => node.props.href === '/headman/statistics')).toBe(false)
    expect(f.saved.cleanups).toBe(1)
  })
  it('retains the mounted draft and host focus throughout a same-grant refresh', async () => {
    const f = await mountFixture()
    const input = nodes(f.root).find((node) => node.tag === 'input')!
    ;(input.props.onInput as (event: unknown) => void)({ target: { value: 'Unsaved selection' } })
    input.focus()
    await nextTick()
    const held = deferred<Response>()
    f.reads.push(held.promise)
    const refresh = f.access.load(true)
    await nextTick()
    expect(f.access.loaded.value).toBe(true)
    expect(nodes(f.root).find((node) => node.tag === 'input')).toBe(input)
    expect(focused).toBe(input)
    held.resolve(Response.json(stats))
    await refresh
    await nextTick()
    expect(nodes(f.root).find((node) => node.tag === 'input')).toBe(input)
    expect(input.value).toBe('Unsaved selection')
    expect(focused).toBe(input)
    expect(f.saved.cleanups).toBe(0)
  })
  it('clears the old mounted view on identity change and rejects the previous owner late permissions', async () => {
    const f = await mountFixture()
    const held = deferred<Response>()
    f.reads.push(held.promise)
    const oldRead = f.access.load(true).catch(() => undefined)
    f.webSession.invalidate()
    await nextTick()
    expect(nodes(f.root).some((node) => node.tag === 'table')).toBe(false)
    f.nextUser()
    await f.webSession.login('next', 'password')
    await f.access.load()
    held.resolve(Response.json(stats))
    await oldRead
    await nextTick()
    expect(f.webSession.snapshot.value?.userId).toBe('43')
    expect(f.access.permissions.value).toEqual([])
    expect(nodes(f.root).some((node) => node.tag === 'table')).toBe(false)
    expect(nodes(f.root).some((node) => node.props.href === '/headman/statistics')).toBe(false)
  })
})
