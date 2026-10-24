import {
  createRenderer,
  defineComponent,
  h,
  nextTick,
  onBeforeUnmount,
  ref,
  watch,
  type App,
} from 'vue'
import { createMemoryHistory, createRouter, onBeforeRouteLeave, RouterView } from 'vue-router'
import { afterEach, describe, expect, it, vi } from 'vitest'
import { adminPermissionCodes } from '../../../../mobile-core/src/features/admin-access/admin-access-client'
import type { AdminUser } from '../../../../mobile-core/src/features/admin-users/admin-users-client'

class Node {
  parent: Node | null = null
  children: Node[] = []
  props: Record<string, unknown> = {}
  text = ''
  constructor(readonly tag: string) {}
}
const all = (node: Node): Node[] => [node, ...node.children.flatMap(all)]
const text = (node: Node): string => node.text + node.children.map(text).join('')
const renderer = createRenderer<Node, Node>({
  createElement: (tag) => new Node(tag),
  createText: (value) => Object.assign(new Node('#text'), { text: value }),
  createComment: () => new Node('#comment'),
  setText: (node, value) => {
    node.text = value
  },
  setElementText: (node, value) => {
    node.text = value
    node.children = []
  },
  parentNode: (node) => node.parent,
  nextSibling: (node) => node.parent?.children[node.parent.children.indexOf(node) + 1] ?? null,
  patchProp: (node, key, _previous, value) => {
    node.props[key] = value
  },
  insert: (node, parent, anchor = null) => {
    if (node.parent) node.parent.children.splice(node.parent.children.indexOf(node), 1)
    const at = anchor ? parent.children.indexOf(anchor) : -1
    parent.children.splice(at < 0 ? parent.children.length : at, 0, node)
    node.parent = parent
  },
  remove: (node) => {
    node.parent?.children.splice(node.parent.children.indexOf(node), 1)
    node.parent = null
  },
})
const flush = async () => {
  for (let i = 0; i < 8; i++) {
    await nextTick()
    await new Promise<void>((resolve) => setImmediate(resolve))
  }
}
function deferred<T>() {
  let resolve!: (value: T) => void
  const promise = new Promise<T>((done) => {
    resolve = done
  })
  return { promise, resolve }
}
let app: App | undefined
let invalidate: (() => void) | undefined
afterEach(() => {
  app?.unmount()
  app = undefined
  invalidate?.()
  invalidate = undefined
  vi.unstubAllGlobals()
  vi.resetModules()
})

async function fixture(options: { outcome?: 'known' | 'lost' | 'held'; target?: number } = {}) {
  const { outcome = 'known', target = 12 } = options
  const secret = crypto.randomUUID()
  const initialSecret = crypto.randomUUID()
  let actor = '12'
  let sessionNumber = 1
  let activeRole = 'ADMIN'
  let sessionVersion = '1'
  let holdLogin = false
  let version = '1'
  let generated = 0
  const readStarted = deferred<void>(),
    readReply = deferred<Response>()
  const generateStarted = deferred<void>(),
    generateReply = deferred<Response>()
  const loginStarted = deferred<void>(),
    loginReply = deferred<Response>()
  vi.stubGlobal('window', new EventTarget())
  const session = () => ({
    sessionId: `00000000-0000-4000-8000-${String(sessionNumber).padStart(12, '0')}`,
    userId: actor,
    displayName: 'Синтетический владелец',
    sessionVersion,
    rolesVersion: '1',
    activeRole,
    readOnly: false,
    roles: [
      { grantId: '1', role: 'ADMIN', status: 'ACTIVE', selectable: true, readOnly: false },
      { grantId: '2', role: 'STUDENT', status: 'ACTIVE', selectable: true, readOnly: false },
    ],
    passwordPolicy: {
      minCodePoints: 12,
      maxUtf8Bytes: 72,
      requiresDecimalDigit: true,
      specialCategories: ['P', 'S'],
      normalization: 'NONE',
    },
  })
  const credentials = () => ({
    userId: String(target),
    credentialsVersion: version,
    state: 'GENERATED',
    origin: 'admin_reset',
    password: version === '1' ? initialSecret : secret,
  })
  vi.stubGlobal('fetch', async (input: RequestInfo | URL) => {
    const path = String(input)
    if (path.endsWith('/login')) {
      if (holdLogin) {
        loginStarted.resolve()
        return loginReply.promise
      }
      return Response.json({ accessToken: 'synthetic-token' })
    }
    if (path.endsWith('/session')) return Response.json(session())
    if (path.endsWith('/session/active-role')) {
      activeRole = 'STUDENT'
      sessionVersion = '2'
      return Response.json({ accessToken: 'synthetic-token', expiresIn: 60, session: session() })
    }
    if (path.endsWith('/refresh')) return new Response(null, { status: 401 })
    if (path.endsWith('/admin-access/me'))
      return Response.json({
        userId: Number(actor),
        assigned: true,
        mode: 'FULL',
        version: 1,
        permissions: adminPermissionCodes,
      })
    if (path.endsWith('/generated-password')) {
      generated++
      version = '2'
      generateStarted.resolve()
      if (outcome === 'lost') throw new TypeError('Lost acknowledgement')
      if (outcome === 'held') return generateReply.promise
      return Response.json(credentials())
    }
    if (path.endsWith('/initial-password')) {
      if (outcome === 'lost' && version === '2') {
        readStarted.resolve()
        return readReply.promise
      }
      return Response.json(credentials())
    }
    throw new Error('Unexpected fixture endpoint')
  })
  const { webSession } = await import('../../auth/session')
  const { reauthenticationReceipt: receipt } = await import('../../auth/reauthentication-receipt')
  const { default: Receipt } = await import('../../auth/ReauthenticationReceipt.vue')
  const { default: Credentials } = await import('./UserCredentials.vue')
  await webSession.login('synthetic', 'unused')
  await flush()
  invalidate = webSession.invalidate
  const user: AdminUser = {
    id: target,
    login: 'synthetic',
    lastName: 'Синтетический',
    firstName: 'Владелец',
    middleName: null,
    fullName: 'Синтетический Владелец',
    role: 'ADMIN',
    status: 'ACTIVE',
    groupId: null,
    employeeNumber: null,
    telegramId: null,
    createdAt: null,
    roles: [
      {
        role: 'ADMIN',
        status: 'ACTIVE',
        groupId: null,
        groupName: null,
        selectable: true,
        readOnly: false,
        applicableStatuses: ['ACTIVE'],
        canUpdate: true,
        blockedReason: null,
      },
    ],
  }
  // eslint-disable-next-line vue/one-component-per-file -- Test-only parent owns the child navigation lock.
  const Users = defineComponent({
    setup() {
      const locked = ref(false)
      // The existing UsersPage parent boundary blocks leaving while a child owns an unfinished action.
      onBeforeRouteLeave(() => !locked.value)
      return () =>
        h(Credentials, {
          user,
          onLocked: (value: boolean) => {
            locked.value = value
          },
        })
    },
  })
  // AppShell keys its routed private page by the session generation.
  // eslint-disable-next-line vue/one-component-per-file -- Test-only shell retires the private route owner.
  const Shell = defineComponent({
    setup: () => () =>
      webSession.snapshot.value?.activeRole === 'ADMIN'
        ? h(Users, { key: webSession.generation.value })
        : h('main'),
  })
  const router = createRouter({
    history: createMemoryHistory(),
    routes: [
      { path: '/users', name: 'users', component: Shell },
      { path: '/login', name: 'login', component: { render: () => h('input', { id: 'login' }) } },
    ],
  })
  await router.push('/users')
  const root = new Node('root')
  app = renderer.createApp({
    setup() {
      const stop = watch(webSession.phase, (phase) => {
        if (phase === 'guest') void router.replace({ name: 'login' })
      })
      onBeforeUnmount(stop)
      return () => h('div', [h(Receipt), h(RouterView)])
    },
  })
  app.use(router)
  app.mount(root)
  await flush()
  async function click(label: string) {
    const button = all(root).find((node) => node.tag === 'button' && text(node).trim() === label)
    if (!button || button.props.disabled) throw new Error('Required public action unavailable')
    await (button.props.onClick as () => unknown)()
    await flush()
  }
  const loginAs = (userId: string, held = false) => {
    actor = userId
    sessionNumber++
    holdLogin = held
    return webSession.login('successor', 'unused')
  }
  const prepareGeneration = async () => {
    await click('Показать текущий пароль')
    await click('Сгенерировать новый пароль')
  }
  return {
    root,
    router,
    webSession,
    receipt,
    secret,
    user,
    click,
    readStarted,
    readReply,
    generateStarted,
    loginStarted,
    loginAs,
    prepareGeneration,
    acknowledgeGeneration: () => generateReply.resolve(Response.json(credentials())),
    acknowledgeLogin: () => loginReply.resolve(Response.json({ accessToken: 'synthetic-token' })),
    generated: () => generated,
  }
}

describe('self credential reauthentication handoff', () => {
  it('renders a directly handed known receipt and clears it on a new login', async () => {
    const f = await fixture()
    f.receipt.show({ kind: 'generated-password', targetLabel: f.user.fullName, password: f.secret })
    f.webSession.invalidate()
    await flush()
    expect(all(f.root).some((node) => node.tag === 'output' && text(node) === f.secret)).toBe(true)
    expect(
      all(f.root).some((node) => node.tag === 'h2' && text(node).includes(f.user.fullName)),
    ).toBe(true)
    await f.loginAs('13')
    await flush()
    expect(all(f.root).some((node) => node.tag === 'output')).toBe(false)
  })

  it('opens guest login after a known self generation while preserving its volatile receipt', async () => {
    const f = await fixture()
    await f.prepareGeneration()
    await f.click('Заменить пароль')
    expect(all(f.root).some((node) => node.tag === 'output' && text(node) === f.secret)).toBe(true)
    expect(f.router.currentRoute.value.name === 'login').toBe(true)
    expect(f.generated() === 1).toBe(true)
    await f.loginAs('12')
    await flush()
    expect(f.receipt.value.value === null).toBe(true)
    expect(all(f.root).some((node) => node.tag === 'output')).toBe(false)
  })

  it('keeps a self unknown receipt when a revoked-session refresh retires its held read', async () => {
    const f = await fixture({ outcome: 'lost' })
    await f.prepareGeneration()
    await f.click('Заменить пароль')
    await f.router.push('/login')
    await flush()
    expect(f.router.currentRoute.value.name === 'users').toBe(true)
    const checking = f.click('Проверить результат генерации')
    await f.readStarted.promise
    await f.webSession.refresh().catch(() => undefined)
    await flush()
    f.readReply.resolve(new Response(null, { status: 401 }))
    await checking
    await flush()
    expect(f.receipt.value.value?.kind === 'credential-outcome-unknown').toBe(true)
    expect(all(f.root).some((node) => node.tag === 'output')).toBe(false)
    expect(f.router.currentRoute.value.name === 'login').toBe(true)
    expect(f.generated() === 1).toBe(true)
  })

  it('keeps only an unknown outcome when a self generation ACK arrives after retirement', async () => {
    const f = await fixture({ outcome: 'held' })
    await f.prepareGeneration()
    const generating = f.click('Заменить пароль')
    await f.generateStarted.promise
    await f.webSession.refresh().catch(() => undefined)
    await flush()
    expect(f.receipt.value.value?.kind === 'credential-outcome-unknown').toBe(true)
    f.acknowledgeGeneration()
    await generating
    expect(f.receipt.value.value?.kind === 'credential-outcome-unknown').toBe(true)
    expect(all(f.root).some((node) => node.tag === 'output')).toBe(false)
    expect(f.router.currentRoute.value.name === 'login').toBe(true)
    expect(f.generated() === 1).toBe(true)
  })

  it.each(['12', '13'])(
    'does not carry a late self ACK into successor actor %s',
    async (successor) => {
      const f = await fixture({ outcome: 'held' })
      await f.prepareGeneration()
      const generating = f.click('Заменить пароль')
      await f.generateStarted.promise
      await f.loginAs(successor)
      await flush()
      const successorIdentity = f.webSession.identity.value
      f.acknowledgeGeneration()
      await generating
      expect(f.webSession.identity.value === successorIdentity).toBe(true)
      expect(f.webSession.phase.value === 'ready').toBe(true)
      expect(f.receipt.value.value === null).toBe(true)
      expect(all(f.root).some((node) => node.tag === 'output')).toBe(false)
      expect(f.generated() === 1).toBe(true)
    },
  )

  it('retires the self intent as soon as a new login begins, before its reply', async () => {
    const f = await fixture({ outcome: 'held' })
    await f.prepareGeneration()
    const generating = f.click('Заменить пароль')
    await f.generateStarted.promise
    const loggingIn = f.loginAs('13', true)
    await f.loginStarted.promise
    f.acknowledgeGeneration()
    await generating
    expect(f.receipt.value.value === null).toBe(true)
    f.acknowledgeLogin()
    await loggingIn
    await flush()
    expect(f.webSession.snapshot.value?.userId === '13').toBe(true)
    expect(f.receipt.value.value === null).toBe(true)
    expect(all(f.root).some((node) => node.tag === 'output')).toBe(false)
  })

  it('does not carry a late self ACK across a role change', async () => {
    const f = await fixture({ outcome: 'held' })
    await f.prepareGeneration()
    const generating = f.click('Заменить пароль')
    await f.generateStarted.promise
    await f.webSession.selectRole('STUDENT')
    await flush()
    f.acknowledgeGeneration()
    await generating
    expect(f.webSession.snapshot.value?.activeRole === 'STUDENT').toBe(true)
    expect(f.receipt.value.value === null).toBe(true)
    expect(all(f.root).some((node) => node.tag === 'output')).toBe(false)
    expect(f.generated() === 1).toBe(true)
  })

  it('keeps another target’s confirmed credentials inside the current owner', async () => {
    const f = await fixture({ target: 13 })
    await f.prepareGeneration()
    await f.click('Заменить пароль')
    expect(f.webSession.snapshot.value?.userId === '12').toBe(true)
    expect(all(f.root).some((node) => node.tag === 'output' && text(node) === f.secret)).toBe(true)
    expect(f.receipt.value.value === null).toBe(true)
    await f.webSession.refresh().catch(() => undefined)
    await flush()
    expect(f.receipt.value.value === null).toBe(true)
    expect(all(f.root).some((node) => node.tag === 'output')).toBe(false)
    expect(f.generated() === 1).toBe(true)
  })

  it('does not create a self receipt for another target’s late ACK', async () => {
    const f = await fixture({ outcome: 'held', target: 13 })
    await f.prepareGeneration()
    const generating = f.click('Заменить пароль')
    await f.generateStarted.promise
    await f.webSession.refresh().catch(() => undefined)
    await flush()
    f.acknowledgeGeneration()
    await generating
    expect(f.receipt.value.value === null).toBe(true)
    expect(all(f.root).some((node) => node.tag === 'output')).toBe(false)
    expect(f.generated() === 1).toBe(true)
  })
})
