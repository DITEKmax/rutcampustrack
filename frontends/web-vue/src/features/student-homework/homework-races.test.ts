import { createRenderer, nextTick, type App } from 'vue'
import { afterEach, describe, expect, it, vi } from 'vitest'
import type { StudentHomework } from '../../../../mobile-core/src/api/types'

const renderer = createRenderer({
  patchProp: () => undefined,
  insert: () => undefined,
  remove: () => undefined,
  createElement: () => ({}),
  createText: () => ({}),
  createComment: () => ({}),
  setText: () => undefined,
  setElementText: () => undefined,
  parentNode: () => null,
  nextSibling: () => null,
  querySelector: () => null,
  setScopeId: () => undefined,
  cloneNode: (node: object) => node,
  insertStaticContent: () => [{}, {}],
})
let app: App | undefined
afterEach(() => {
  app?.unmount()
  app = undefined
  vi.unstubAllGlobals()
  vi.resetModules()
})

const initial: StudentHomework = {
  semester: { id: '1', name: 'Осень', dateFrom: '2026-09-01', dateTo: '2026-12-31' },
  from: '2026-10-24',
  to: '2026-10-24',
  serverNow: '2026-10-24T09:00:00Z',
  items: [
    {
      id: '1',
      title: 'Конспект',
      description: 'Прочитай главу',
      lessonDate: '2026-10-24',
      lessonNumber: null,
      bindingMode: 'DATE',
      archived: false,
      link: null,
      subject: { id: '1', name: 'История' },
      completed: false,
      completedAt: null,
    },
  ],
}

describe('web homework concurrency', () => {
  it('keeps a confirmed completion when an older background read arrives afterwards', async () => {
    let resolveOldRead!: (response: Response) => void
    const oldRead = new Promise<Response>((resolve) => {
      resolveOldRead = resolve
    })
    let holdNextRead = false
    let completed = false
    vi.stubGlobal('window', new EventTarget())
    vi.stubGlobal(
      'document',
      Object.assign(new EventTarget(), { hidden: false, visibilityState: 'visible' }),
    )
    vi.stubGlobal('fetch', async (input: RequestInfo | URL) => {
      if (String(input).endsWith('/completion')) {
        completed = true
        return Response.json({ id: '1', completed: true, completedAt: '2026-10-24T09:05:00Z' })
      }
      if (holdNextRead) {
        holdNextRead = false
        return oldRead
      }
      return Response.json({
        ...initial,
        items: initial.items.map((item) => ({
          ...item,
          completed,
          completedAt: completed ? '2026-10-24T09:05:00Z' : null,
        })),
      })
    })
    const { useWebHomework } = await import('./use-homework')
    let state!: ReturnType<typeof useWebHomework>
    app = renderer.createApp({
      setup() {
        state = useWebHomework()
        return () => null
      },
    })
    app.mount({})
    await vi.waitFor(() => expect(state.feed.value?.items[0]?.completed).toBe(false))
    await nextTick()
    // Finish initial range initialization before beginning the independent old read.
    await vi.waitFor(() => expect(state.loading.value).toBe(false))
    holdNextRead = true
    const reading = state.load(true)
    await state.complete(state.feed.value!.items[0]!, true)
    expect(state.feed.value?.items[0]?.completed).toBe(true)
    resolveOldRead(Response.json(initial))
    await reading
    await nextTick()
    expect(state.feed.value?.items[0]?.completed).toBe(true)
    expect(state.groups.value[0]?.items[0]?.completed).toBe(true)
    expect(state.itemErrors['1']).toBeUndefined()
  })
})
