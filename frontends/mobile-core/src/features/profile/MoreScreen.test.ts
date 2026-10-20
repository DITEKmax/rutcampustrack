import { createRenderer, h, markRaw, nextTick, type App } from 'vue'
import { afterEach, describe, expect, it, vi } from 'vitest'
import MoreScreen from './MoreScreen.vue'
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

describe('MoreScreen route availability', () => {
  it('renders Statistics and Requests as actionable while Map remains unavailable', async () => {
    const navigate = vi.fn()
    const root = new Node('root')
    const app = renderer.createApp({ render: () => h(MoreScreen, { theme: 'dark', onNavigate: navigate }) })
    apps.push(app)
    app.mount(root)
    for (const [label, route] of [['Статистика', 'statistics'], ['Заявки', 'requests']]) {
      const button = nodes(root).find(node => node.tag === 'button' && text(node).trim() === label)!
      expect(button.props.disabled).toBe(false)
      await click(root, label!)
      expect(navigate).toHaveBeenLastCalledWith(route)
    }
    const map = nodes(root).find(node => node.tag === 'button' && text(node).trim() === 'Карта')!
    expect(map.props.disabled).toBe(true)
    expect(map.props['aria-disabled']).toBe(true)
    navigate.mockClear()
    // Custom renderer invokes the handler directly, also exercising the real navigation guard.
    ;(map.props.onClick as () => void)()
    expect(navigate).not.toHaveBeenCalled()
  })
})
