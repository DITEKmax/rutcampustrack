import { afterEach, describe, expect, it, vi } from 'vitest'
import { createRenderer, h, markRaw, nextTick, ref, type App } from 'vue'
interface FakePage {
  getViewport(options: { scale: number }): { width: number; height: number }
  render(): { cancel(): void; promise: Promise<void> }
}
const pdf = vi.hoisted(() => ({
  tasks: [] as { destroy(): Promise<void>; promise: Promise<unknown> }[],
  requests: [] as number[],
  renderPages: [] as number[],
  defer: null as null | ((page: number) => Promise<FakePage>),
  fail: false,
}))
vi.mock('pdfjs-dist/legacy/build/pdf.mjs', () => ({
  GlobalWorkerOptions: {},
  getDocument: () => {
    const task = {
      destroy: vi.fn(async () => {}),
      promise: pdf.fail
        ? Promise.reject(new Error('PDF повреждён'))
        : Promise.resolve({
            numPages: 2,
            getPage: async (number: number) => {
              pdf.requests.push(number)
              if (pdf.defer) return pdf.defer(number)
              return page(number)
            },
          }),
    }
    pdf.tasks.push(task)
    return task
  },
}))
import TeacherPdfDocumentPreview from './TeacherPdfDocumentPreview.vue'
function page(number: number) {
  return {
    getViewport: ({ scale }: { scale: number }) => ({ width: 400 * scale, height: 600 * scale }),
    render: () => {
      pdf.renderPages.push(number)
      return { cancel: vi.fn(), promise: Promise.resolve() }
    },
  }
}
class Node {
  children: Node[] = []
  parent: Node | null = null
  props: Record<string, unknown> = {}
  style: Record<string, unknown> = {}
  text = ''
  width = 0
  height = 0
  clientWidth = 640
  get parentElement() {
    return this.parent
  }
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
  insert: (node, parent, anchor) => {
    if (node.parent) node.parent.children = node.parent.children.filter((n) => n !== node)
    node.parent = parent
    const index = anchor ? parent.children.indexOf(anchor) : -1
    parent.children.splice(index < 0 ? parent.children.length : index, 0, node)
  },
  remove: (node) => {
    if (node.parent) node.parent.children = node.parent.children.filter((n) => n !== node)
    node.parent = null
  },
  insertStaticContent: (content, parent) => {
    const node = Object.assign(new Node('#text'), { text: content.replace(/<[^>]*>/g, '') })
    node.parent = parent
    parent.children.push(node)
    return [node, node]
  },
})
const apps: App[] = []
async function flush() {
  for (let i = 0; i < 50; i++) {
    await Promise.resolve()
    await nextTick()
  }
}
function all(node: Node): Node[] {
  return [node, ...node.children.flatMap(all)]
}
function text(node: Node): string {
  return node.text + node.children.map(text).join('')
}
async function click(root: Node, label: string) {
  const target = all(root).find(
    (n) => n.tag === 'button' && (n.props['aria-label'] === label || text(n).trim() === label),
  )
  expect(target).toBeDefined()
  expect(target!.props.disabled).not.toBe(true)
  const invoke = target!.props.onClick as () => void
  invoke()
  await flush()
}
function mount() {
  vi.stubGlobal('window', { devicePixelRatio: 1 })
  const fetcher = vi.fn(async () => new Response(new Uint8Array([37, 80, 68, 70, 45])))
  vi.stubGlobal('fetch', fetcher)
  const url = ref('blob:first'),
    root = new Node('root')
  const app = renderer.createApp({
    render: () => h(TeacherPdfDocumentPreview, { url: url.value, label: 'Документ.pdf' }),
  })
  app.mount(root)
  apps.push(app)
  return { root, url, app, fetcher }
}
afterEach(() => {
  apps.splice(0).forEach((app) => app.unmount())
  pdf.tasks = []
  pdf.requests = []
  pdf.renderPages = []
  pdf.defer = null
  pdf.fail = false
  vi.unstubAllGlobals()
})
describe('teacher private PDF document page owner', () => {
  it('renders every page on demand with previous/next and a truthful page counter', async () => {
    const c = mount()
    await flush()
    expect(text(c.root)).toContain('Страница 1 из 2')
    expect(pdf.renderPages).toEqual([1])
    await click(c.root, 'Следующая страница PDF')
    expect(text(c.root)).toContain('Страница 2 из 2')
    expect(pdf.renderPages).toEqual([1, 2])
    await click(c.root, 'Предыдущая страница PDF')
    expect(text(c.root)).toContain('Страница 1 из 2')
    expect(pdf.renderPages).toEqual([1, 2, 1])
    c.app.unmount()
    apps.pop()
    expect(pdf.tasks[0]!.destroy).toHaveBeenCalledOnce()
  })
  it('ignores a late page after another page selection and destroys the prior URL owner', async () => {
    const c = mount()
    await flush()
    let finish!: (page: FakePage) => void
    pdf.defer = (number) =>
      number === 2
        ? new Promise((resolve) => {
            finish = resolve
          })
        : Promise.resolve(page(number))
    await click(c.root, 'Следующая страница PDF')
    await click(c.root, 'Предыдущая страница PDF')
    finish(page(2))
    await flush()
    expect(pdf.renderPages).toEqual([1, 1])
    c.url.value = 'blob:second'
    await flush()
    expect(pdf.tasks[0]!.destroy).toHaveBeenCalledOnce()
    expect(c.fetcher).toHaveBeenLastCalledWith(
      'blob:second',
      expect.objectContaining({ signal: expect.any(AbortSignal) }),
    )
    expect(text(c.root)).toContain('Страница 1 из 2')
    c.url.value = 'https://private.invalid/file'
    await flush()
    expect(text(c.root)).toContain('Нет безопасного адреса PDF')
    expect(c.fetcher).toHaveBeenCalledTimes(2)
  })
  it('offers a real reload after a parse error and never loops into an external viewer', async () => {
    pdf.fail = true
    const c = mount()
    await flush()
    expect(text(c.root)).toContain('PDF повреждён')
    pdf.fail = false
    await click(c.root, 'Повторить')
    expect(pdf.tasks[0]!.destroy).toHaveBeenCalledOnce()
    expect(text(c.root)).toContain('Страница 1 из 2')
    expect(pdf.renderPages).toEqual([1])
  })
})
