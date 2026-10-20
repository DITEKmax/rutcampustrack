import { getEventListeners } from 'node:events'
import { createRenderer, markRaw, nextTick, type App } from 'vue'
import { afterEach, describe, expect, it, vi } from 'vitest'
import PwaUpdateGate from './PwaUpdateGate.vue'
import { APP_UPDATE_REQUIRED_EVENT } from './pwa-version'

class Element {
  children: Element[] = []
  parent: Element | null = null
  props: Record<string, unknown> = {}
  text = ''
  focus = vi.fn()
  constructor(readonly tag: string) {
    markRaw(this)
  }
  setAttribute(name: string, value: string): void {
    this.props[name] = value
  }
  removeAttribute(name: string): void {
    delete this.props[name]
  }
}

class Worker extends EventTarget {
  state: ServiceWorkerState = 'installed'
  postMessage = vi.fn()
}

const fixtures: {
  app: App
  appRoot: Element
  window: EventTarget
  document: EventTarget
  container: EventTarget
  worker: Worker
}[] = []
afterEach(async () => {
  for (const fixture of fixtures.splice(0)) {
    fixture.app.unmount()
    await vi.advanceTimersByTimeAsync(10_000)
    expect(fixture.appRoot.props).not.toHaveProperty('inert')
    expect(getEventListeners(fixture.window, APP_UPDATE_REQUIRED_EVENT)).toHaveLength(0)
    expect(getEventListeners(fixture.window, 'online')).toHaveLength(0)
    expect(getEventListeners(fixture.document, 'visibilitychange')).toHaveLength(0)
    expect(getEventListeners(fixture.container, 'controllerchange')).toHaveLength(0)
    expect(getEventListeners(fixture.worker, 'statechange')).toHaveLength(0)
  }
  expect(vi.getTimerCount()).toBe(0)
  vi.useRealTimers()
  vi.unstubAllGlobals()
})

async function flush(): Promise<void> {
  for (let index = 0; index < 12; index++) {
    await Promise.resolve()
    await nextTick()
  }
}

function all(root: Element): Element[] {
  return [root, ...root.children.flatMap(all)]
}

function mount(waiting: boolean, updateMode: 'reject' | 'pending' | 'install') {
  vi.useFakeTimers()
  const body = new Element('body')
  const appRoot = new Element('main')
  const worker = new Worker()
  const container = Object.assign(new EventTarget(), {
    controller: {} as ServiceWorker | null,
    getRegistration: vi.fn(),
  })
  const registration = {
    waiting: waiting ? worker : null,
    installing: null as Worker | null,
    active: null as Worker | null,
    update: vi.fn(async () => {
      if (updateMode === 'reject') throw new TypeError('Synthetic update check unavailable')
      if (updateMode === 'pending') return new Promise<void>(() => {})
      registration.installing = worker
    }),
  }
  container.getRegistration.mockResolvedValue(registration)
  const reload = vi.fn()
  const window = Object.assign(new EventTarget(), {
    setTimeout: globalThis.setTimeout,
    clearTimeout: globalThis.clearTimeout,
    setInterval: globalThis.setInterval,
    clearInterval: globalThis.clearInterval,
    location: { reload },
  })
  const document = Object.assign(new EventTarget(), {
    visibilityState: 'visible',
    getElementById: (id: string) => (id === 'app' ? appRoot : null),
  })
  vi.stubGlobal('window', window)
  vi.stubGlobal('document', document)
  vi.stubGlobal('navigator', { serviceWorker: container })
  vi.stubGlobal(
    'fetch',
    vi.fn(async () => ({ ok: true, json: async () => ({}) })),
  )

  const renderer = createRenderer<Element, Element>({
    createElement: (tag) => new Element(tag),
    createText: (text) => Object.assign(new Element('#text'), { text }),
    createComment: () => new Element('#comment'),
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
    },
    remove: (node) => {
      if (node.parent) node.parent.children = node.parent.children.filter((item) => item !== node)
      node.parent = null
    },
    insert: (node, parent, anchor) => {
      const index = anchor ? parent.children.indexOf(anchor) : -1
      parent.children.splice(index < 0 ? parent.children.length : index, 0, node)
      node.parent = parent
    },
    querySelector: () => body,
  })
  const app: App = renderer.createApp(PwaUpdateGate)
  app.mount(new Element('root'))
  const fixture = { app, appRoot, body, worker, registration, container, window, document, reload }
  fixtures.push(fixture)
  window.dispatchEvent(new CustomEvent(APP_UPDATE_REQUIRED_EVENT, { detail: { reason: 'api' } }))
  return fixture
}

function button(body: Element): Element {
  return all(body).find((node) => node.tag === 'button')!
}

function click(body: Element): void {
  const updateButton = button(body)
  expect(updateButton).toBeDefined()
  expect(updateButton.props.disabled).toBe(false)
  void (updateButton.props.onClick as () => Promise<void>)()
}

function expectBlocked(fixture: ReturnType<typeof mount>): void {
  expect(all(fixture.body).some((node) => node.props.role === 'dialog')).toBe(true)
  expect(fixture.appRoot.props).toHaveProperty('inert')
  expect(fixture.reload).not.toHaveBeenCalled()
}

describe('PWA required update activation', () => {
  it.each(['reject', 'pending'] as const)(
    'installs an already waiting worker despite a %s network update check',
    async (mode) => {
      const fixture = mount(true, mode)
      await flush()
      click(fixture.body)
      await flush()
      expect(fixture.worker.postMessage).toHaveBeenCalledExactlyOnceWith({ type: 'SKIP_WAITING' })
      expectBlocked(fixture)
      expect(button(fixture.body).props.disabled).toBe(true)
      fixture.container.dispatchEvent(new Event('controllerchange'))
      await flush()
      expect(fixture.reload).toHaveBeenCalledTimes(1)
      expect(fixture.registration.update).not.toHaveBeenCalled()
      expect(getEventListeners(fixture.container, 'controllerchange')).toHaveLength(0)
      expect(getEventListeners(fixture.worker, 'statechange')).toHaveLength(0)
    },
  )

  it('checks for an update when none is waiting and waits for installation before activation', async () => {
    const fixture = mount(false, 'install')
    await flush()
    click(fixture.body)
    await flush()
    expect(fixture.registration.update).toHaveBeenCalledTimes(1)
    expect(fixture.worker.postMessage).not.toHaveBeenCalled()
    expectBlocked(fixture)
    fixture.registration.waiting = fixture.worker
    fixture.worker.state = 'installed'
    fixture.worker.dispatchEvent(new Event('statechange'))
    await flush()
    expect(fixture.worker.postMessage).toHaveBeenCalledExactlyOnceWith({ type: 'SKIP_WAITING' })
    expectBlocked(fixture)
    fixture.container.dispatchEvent(new Event('controllerchange'))
    await flush()
    expect(fixture.reload).toHaveBeenCalledTimes(1)
  })

  it('keeps the hard gate and permits retry after the unchanged five-second activation deadline', async () => {
    const fixture = mount(true, 'reject')
    await flush()
    click(fixture.body)
    await flush()
    expect(fixture.worker.postMessage).toHaveBeenCalledTimes(1)
    await vi.advanceTimersByTimeAsync(4_999)
    expectBlocked(fixture)
    expect(button(fixture.body).props.disabled).toBe(true)
    await vi.advanceTimersByTimeAsync(1)
    await flush()
    expectBlocked(fixture)
    expect(button(fixture.body).props.disabled).toBe(false)
    expect(getEventListeners(fixture.container, 'controllerchange')).toHaveLength(0)
    expect(getEventListeners(fixture.worker, 'statechange')).toHaveLength(0)
    click(fixture.body)
    await flush()
    expect(fixture.worker.postMessage).toHaveBeenCalledTimes(2)
    fixture.container.dispatchEvent(new Event('controllerchange'))
    await flush()
    expect(fixture.reload).toHaveBeenCalledTimes(1)
  })

  it('does not reload when no worker is waiting and the network update check fails', async () => {
    const fixture = mount(false, 'reject')
    await flush()
    click(fixture.body)
    await flush()
    expect(fixture.registration.update).toHaveBeenCalledTimes(1)
    expect(fixture.worker.postMessage).not.toHaveBeenCalled()
    expectBlocked(fixture)
    expect(button(fixture.body).props.disabled).toBe(false)
  })
})
