import { createRenderer, h, markRaw, nextTick, ref, type App } from 'vue'
import { afterEach, describe, expect, it, vi } from 'vitest'
import TeacherAttachmentPreview from './TeacherAttachmentPreview.vue'
import TeacherManualExcuseScreen from './TeacherManualExcuseScreen.vue'
import { TeacherApi } from './teacher-client'
class Node {
  children: Node[] = []
  parent: Node | null = null
  props: Record<string, unknown> = {}
  text = ''
  constructor(readonly tag: string) {
    markRaw(this)
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
function find(node: Node, predicate: (node: Node) => boolean): Node | undefined {
  if (predicate(node)) return node
  for (const child of node.children) {
    const found = find(child, predicate)
    if (found) return found
  }
  return undefined
}
const apps: App[] = []
afterEach(() => {
  for (const app of apps.splice(0)) app.unmount()
  vi.useRealTimers()
  vi.restoreAllMocks()
  vi.unstubAllGlobals()
})
async function settle(): Promise<void> {
  for (let i = 0; i < 12; i++) {
    await new Promise((resolve) => setTimeout(resolve, 0))
    await nextTick()
  }
}

describe('actual teacher attachment retry control', () => {
  it('retries authenticated bytes through StudentWarningBlock and shows image', async () => {
    let downloads = 0
    const errors: unknown[] = []
    const api = markRaw(
      new TeacherApi({
        accessToken: () => null,
        fetcher: async () => {
          downloads += 1
          return downloads === 1
            ? new Response(JSON.stringify({ detail: 'Временно недоступно' }), {
                status: 503,
                headers: { 'Content-Type': 'application/json' },
              })
            : new Response(new Uint8Array([137, 80, 78, 71, 13, 10, 26, 10, 0]), {
                headers: { 'Content-Type': 'image/png' },
              })
        },
      }),
    )
    const root = new Node('root')
    const app = renderer.createApp({
      render: () =>
        h(TeacherAttachmentPreview, {
          api,
          requestId: 'request-a',
          attachment: {
            id: 'file-a',
            fileName: 'Справка.png',
            contentType: 'image/png',
            size: 9,
            uploadedAt: null,
            expiresAt: null,
          },
        }),
    })
    app.config.errorHandler = (cause) => {
      errors.push(cause)
    }
    apps.push(app)
    app.mount(root)
    await settle()
    expect(downloads).toBe(1)
    const retry = find(root, (node) => node.tag === 'button' && node.text.trim() === 'Повторить')
    expect(retry).toBeDefined()
    ;(retry!.props.onClick as () => void)()
    await settle()
    expect(downloads).toBe(2)
    expect(errors).toEqual([])
    expect(find(root, (node) => node.tag === 'img')).toBeDefined()
  })
})

async function settleManual(): Promise<void> {
  for (let i = 0; i < 50; i++) {
    await Promise.resolve()
    await nextTick()
  }
}

async function manualFixture() {
  vi.useFakeTimers()
  vi.setSystemTime(new Date('2026-11-06T08:00:00Z'))
  const page = Object.assign(new EventTarget(), { visibilityState: 'visible' })
  vi.stubGlobal('document', page)
  const create = vi.spyOn(URL, 'createObjectURL').mockImplementation(() => {
    return 'blob:teacher-' + create.mock.calls.length
  })
  const revoke = vi.spyOn(URL, 'revokeObjectURL').mockImplementation(() => undefined)
  let fileId = 'file-a'
  let hold = false
  let finish!: () => void
  const reads: RequestInit[] = []
  const downloads: string[] = []
  function rosterResponse() {
    return Response.json({
      lesson: {
        id: 11,
        groupId: 2,
        groupName: 'ИКБО',
        subjectId: 3,
        subjectName: 'Базы данных',
        semesterId: 7,
        lessonType: 'lecture',
        date: '2026-10-01',
        lessonNumber: 1,
        status: 'CLOSED',
        oneOff: false,
        moved: false,
        cancelled: false,
      },
      roster: [
        {
          studentId: 22,
          displayName: 'Анна Белова',
          status: 'EXCUSED',
          recordPresent: true,
          pendingTicket: false,
          autoAbsent: false,
          attachmentId: fileId,
          attachmentName: fileId + '.png',
          attachmentContentType: 'image/png',
          attachmentSize: 9,
          attachmentExpiresAt: '2026-11-06T09:00:00Z',
        },
      ],
      serverNow: '2026-11-06T08:00:00Z',
    })
  }
  const api = markRaw(
    new TeacherApi({
      accessToken: () => null,
      fetcher: async (input, init) => {
        const path = String(input)
        if (path === '/api/v1/teacher/lessons/11') {
          reads.push(init!)
          const response = rosterResponse()
          if (!hold) return response
          hold = false
          return new Promise<Response>((resolve) => {
            finish = () => resolve(response)
          })
        }
        if (!path.startsWith('/api/v1/teacher/lessons/11/students/22/excuse/attachments/'))
          throw new Error('Unexpected manual fixture path: ' + path)
        downloads.push(path.split('/').at(-1)!)
        return new Response(new Uint8Array([137, 80, 78, 71, 13, 10, 26, 10, 0]), {
          headers: { 'Content-Type': 'image/png' },
        })
      },
    }),
  )
  const root = new Node('root')
  const revision = ref(0)
  const app = renderer.createApp({
    render: () =>
      h(TeacherManualExcuseScreen, {
        api,
        lessonId: 11,
        studentId: 22,
        realtimeRevision: revision.value,
      }),
  })
  apps.push(app)
  app.mount(root)
  await settleManual()
  const inline = find(root, (node) => node.props['aria-label'] === 'Открыть file-a.png')!
  expect(inline).toBeDefined()
  ;(inline.props.onClick as () => void)()
  await settleManual()
  return {
    reads,
    downloads,
    revoke,
    revision,
    dialog: () => find(root, (node) => node.props.role === 'dialog'),
    file: (name: string) => find(root, (node) => node.props['aria-label'] === 'Открыть ' + name),
    replace: () => {
      fileId = 'file-b'
    },
    hold: () => {
      hold = true
    },
    finish: () => finish(),
    visibility: (state: 'hidden' | 'visible') => {
      page.visibilityState = state
      page.dispatchEvent(new Event('visibilitychange'))
    },
  }
}

describe('mounted teacher manual attachment authorization', () => {
  it('keeps the exact modal and Blob on unchanged metadata, then revokes on replacement', async () => {
    const view = await manualFixture()
    const dialog = view.dialog()
    expect(dialog).toBeDefined()
    expect(view.downloads).toEqual(['file-a'])
    await vi.advanceTimersByTimeAsync(15_000)
    expect(view.reads).toHaveLength(2)
    expect(view.reads.every((read) => read.cache === 'no-store')).toBe(true)
    expect(view.dialog()).toBe(dialog)
    expect(view.downloads).toEqual(['file-a'])

    view.hold()
    view.revision.value += 1
    await settleManual()
    expect(view.dialog()).toBe(dialog)
    expect(dialog!.props['aria-busy']).toBe(true)
    expect(
      find(dialog!, (node) => node.props.class === 'teacher-attachment-full__content')?.props
        .hidden,
    ).toBe(true)
    view.finish()
    await settleManual()
    expect(view.dialog()).toBe(dialog)
    expect(dialog!.props['aria-busy']).toBe(false)
    expect(view.downloads).toEqual(['file-a'])
    expect(view.revoke).not.toHaveBeenCalled()

    view.replace()
    await vi.advanceTimersByTimeAsync(15_000)
    await settleManual()
    expect(view.dialog()).toBeUndefined()
    expect(view.revoke).toHaveBeenCalledExactlyOnceWith('blob:teacher-1')
    expect(view.downloads).toEqual(['file-a', 'file-b'])
    expect(view.file('file-b.png')).toBeDefined()
  })

  it('starts the next verification 15 seconds after a slow successful read began', async () => {
    const view = await manualFixture()
    const dialog = view.dialog()
    view.hold()
    await vi.advanceTimersByTimeAsync(15_000)
    expect(view.reads).toHaveLength(2)

    view.replace()
    await vi.advanceTimersByTimeAsync(7_500)
    view.finish()
    await settleManual()
    expect(view.dialog()).toBe(dialog)
    expect(view.downloads).toEqual(['file-a'])

    await vi.advanceTimersByTimeAsync(7_499)
    expect(view.reads).toHaveLength(2)
    await vi.advanceTimersByTimeAsync(1)
    await settleManual()
    expect(view.reads).toHaveLength(3)
    expect(view.dialog()).toBeUndefined()
    expect(view.revoke).toHaveBeenCalledExactlyOnceWith('blob:teacher-1')
    expect(view.downloads).toEqual(['file-a', 'file-b'])
  })

  it('pauses while hidden, then fails closed at the deadline and rejects a late confirmation', async () => {
    const view = await manualFixture()
    view.visibility('hidden')
    await vi.advanceTimersByTimeAsync(45_000)
    expect(view.reads).toHaveLength(1)
    expect(view.dialog()?.props['aria-busy']).toBe(true)

    view.hold()
    view.visibility('visible')
    await settleManual()
    expect(view.reads).toHaveLength(2)
    await vi.advanceTimersByTimeAsync(8_000)
    expect(view.reads[1]!.signal?.aborted).toBe(true)
    expect(view.dialog()).toBeUndefined()
    expect(view.revoke).toHaveBeenCalledExactlyOnceWith('blob:teacher-1')

    view.finish()
    await vi.advanceTimersByTimeAsync(45_000)
    expect(view.reads).toHaveLength(2)
    expect(view.downloads).toEqual(['file-a'])
    expect(view.dialog()).toBeUndefined()
  })
})
