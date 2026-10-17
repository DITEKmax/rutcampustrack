import { createRenderer, h, markRaw, nextTick, reactive, type App } from 'vue'
import { afterEach, describe, expect, it, vi } from 'vitest'
import ExportButton from './ExportButton.vue'
import type {
  ExportStateListener,
  ReportDownloadPort,
  ReportDownloadTicketRequest,
  ReportFormatOption,
} from '../report-download-client'

vi.mock('./ExportButton.vue', async (importOriginal) => {
  const actual = await importOriginal<typeof import('./ExportButton.vue')>()
  if (process.env.RCT_EXPORT_CLIENT_GUARD_FIXTURE !== 'grant-gap') return actual
  const { createExportGrantGapFixture } = await import('./fixtures/export-grant-gap')
  return { ...actual, default: createExportGrantGapFixture(actual.default) }
})

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
async function click(root: Node, label: string): Promise<void> {
  const button = nodes(root).find((node) => node.tag === 'button' && text(node).trim() === label)
  expect(button).toBeDefined()
  ;(button!.props.onClick as () => void)()
  await settle()
}
const formats: readonly ReportFormatOption[] = ['docx', 'pdf', 'png', 'html', 'xlsx'].map(
  (code) => ({ code: code as ReportFormatOption['code'], label: code.toUpperCase() }),
)
function mount(
  port: ReportDownloadPort,
  catalogue: () => Promise<readonly ReportFormatOption[]> = async () => formats,
) {
  vi.stubGlobal('document', { activeElement: null })
  const state = reactive({ search: 'ЭК', disabled: false })
  const root = new Node('root')
  const app = renderer.createApp({
    setup: () => () =>
      h(ExportButton, {
        port,
        formats: catalogue,
        scopeKey: state.search,
        disabled: state.disabled,
        request: (format: ReportFormatOption['code']) => ({
          kind: 'ADMIN_GROUPS',
          adminGroups: {
            status: 'DRAFT',
            search: state.search,
            course: 2,
            sorts: [{ field: 'name', descending: true }],
            format,
          },
        }),
      }),
  })
  apps.push(app)
  app.mount(root)
  return { root, state, app }
}

describe('export action state and scope', () => {
  it('uses the server catalogue and captured filters, shows queue/preparing, and identifies the browser handoff', async () => {
    let progress!: ExportStateListener
    let finish!: (value: 'accepted') => void
    let sent!: ReportDownloadTicketRequest
    const download = vi.fn<ReportDownloadPort['download']>((request, _current, listener) => {
      sent = request
      progress = listener!
      listener?.('queued')
      return new Promise((resolve) => {
        finish = resolve
      })
    })
    const { root } = mount({ carrier: 'browser', download })
    await click(root, 'Выгрузка')
    expect(
      nodes(root)
        .filter((node) => node.tag === 'option')
        .map(text),
    ).toEqual(['DOCX', 'PDF', 'PNG', 'HTML', 'XLSX'])
    await click(root, 'Скачать')
    expect(sent).toEqual({
      kind: 'ADMIN_GROUPS',
      adminGroups: {
        status: 'DRAFT',
        search: 'ЭК',
        course: 2,
        sorts: [{ field: 'name', descending: true }],
        format: 'docx',
      },
    })
    expect(text(root)).toContain('Выгрузка в очереди')
    progress('preparing')
    await settle()
    expect(text(root)).toContain('Файл готовится')
    progress('ready')
    finish('accepted')
    await settle()
    expect(text(root)).toContain('Скачивание передано браузеру')
    expect(download).toHaveBeenCalledTimes(1)
  })

  it('invalidates a preparing operation on filter change and ignores its late completion', async () => {
    let current!: () => boolean
    let finish!: (value: 'accepted') => void
    const { root, state } = mount({
      carrier: 'telegram',
      download: (_request, guard, progress) => {
        current = guard
        progress?.('preparing')
        return new Promise((resolve) => {
          finish = resolve
        })
      },
    })
    await click(root, 'Выгрузка')
    await click(root, 'Скачать')
    expect(current()).toBe(true)
    state.search = 'Новый фильтр'
    await settle()
    expect(current()).toBe(false)
    finish('accepted')
    await settle()
    expect(text(root)).not.toContain('Telegram принял')
    expect(state.disabled).toBe(false)
  })

  it('invalidates a preparing operation on grant loss alone and ignores its late completion', async () => {
    let current!: () => boolean
    let finish!: (value: 'accepted') => void
    const { root, state } = mount({
      carrier: 'telegram',
      download: (_request, guard, progress) => {
        current = guard
        progress?.('preparing')
        return new Promise((resolve) => {
          finish = resolve
        })
      },
    })
    await click(root, 'Выгрузка')
    await click(root, 'Скачать')
    expect(current()).toBe(true)
    state.disabled = true
    await settle()
    expect(state.search).toBe('ЭК')
    expect(current()).toBe(false)
    finish('accepted')
    await settle()
    expect(text(root)).not.toContain('Telegram принял')
    expect(nodes(root).find((node) => node.tag === 'button')?.props.disabled).toBe(true)
  })

  it('drops a stale catalogue response after the scope changes', async () => {
    let release!: (value: readonly ReportFormatOption[]) => void
    const { root, state } = mount(
      { download: async () => 'accepted' },
      () =>
        new Promise((resolve) => {
          release = resolve
        }),
    )
    await click(root, 'Выгрузка')
    state.search = 'Другой scope'
    await settle()
    release(formats)
    await settle()
    expect(nodes(root).some((node) => node.tag === 'select')).toBe(false)
  })
})
