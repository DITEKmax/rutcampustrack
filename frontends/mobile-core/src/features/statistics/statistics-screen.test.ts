import { createRenderer, h, markRaw, nextTick, ref, type App } from 'vue'
import { afterEach, describe, expect, it, vi } from 'vitest'
import StatisticsScreen from './StatisticsScreen.vue'
import type {
  StatisticsOverviewData,
  StatisticsRankingData,
  StatisticsReadState,
} from './statistics-view-model'
vi.mock('./StatisticsSemesterChart.vue', () => ({ default: { setup: () => () => null } }))
class Node {
  children: Node[] = []
  parent: Node | null = null
  props: Record<string, unknown> = {}
  text = ''
  value: unknown = ''
  selected = false
  checked = false
  constructor(readonly tag: string) {
    markRaw(this)
  }
  querySelectorAll(selector: string): Node[] {
    const result: Node[] = []
    const walk = (node: Node) => {
      if (selector === '[role="option"]' && node.props.role === 'option') result.push(node)
      node.children.forEach(walk)
    }
    this.children.forEach(walk)
    return result
  }
  focus(): void {}
  close(): void {}
  showModal(): void {}
  addEventListener(): void {}
  getRootNode(): { activeElement: null } {
    return { activeElement: null }
  }
  get tagName(): string {
    return this.tag.toUpperCase()
  }
  get options(): Node[] {
    return this.children.filter((node) => node.tag === 'option')
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
  patchProp: (node, key, _before, value) => {
    node.props[key] = value
  },
  insert: (node, parent, anchor = null) => {
    const old = node.parent
    if (old) old.children.splice(old.children.indexOf(node), 1)
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
afterEach(() => apps.splice(0).forEach((app) => app.unmount()))
function text(node: Node): string {
  return node.text + node.children.map(text).join(' ')
}
function all(node: Node, predicate: (node: Node) => boolean): Node[] {
  return [...(predicate(node) ? [node] : []), ...node.children.flatMap((n) => all(n, predicate))]
}
const metrics = {
  present: { count: 0, percent: 0 },
  presentOrExcused: { count: 0, percent: null },
  excused: { count: 0, percent: null },
  absent: { count: 0, percent: 0 },
  held: 0,
  planned: 2,
}
const data: StatisticsOverviewData = {
  metrics,
  ownRank: { available: true, position: 22, participantCount: 42 },
  semesterSeries: [],
  subjects: [],
}
function mount() {
  const state = ref<StatisticsReadState<StatisticsRankingData>>({
    status: 'ready',
    data: {
      available: true,
      page: 1,
      size: 20,
      total: 42,
      ownPosition: 22,
      rows: [
        { id: '1', name: '<script>synthetic</script>', position: 22, percentage: 0, isSelf: true },
      ],
    },
  })
  const root = new Node('root')
  const pages: number[] = []
  const app = renderer.createApp({
    setup: () => () =>
      h(StatisticsScreen, {
        state: { status: 'ready', data },
        rankingVisible: true,
        rankingState: state.value,
        onRankingPage: (page: number) => pages.push(page),
      }),
  })
  app.mount(root)
  apps.push(app)
  return { root, state, pages }
}
describe('compact statistics accepted metrics and readonly own-group list', () => {
  it('renders combined null distinctly from true zero and keeps the primary pair adjacent', () => {
    const { root } = mount()
    const cards = all(root, (n) =>
      String(n.props.class ?? '')
        .split(' ')
        .includes('statistics-metric'),
    )
    expect(cards).toHaveLength(4)
    expect(text(cards[0]!)).toContain('0%')
    expect(text(cards[1]!)).toContain('—')
    expect(text(cards[1]!)).toContain('был или уважительная причина, 0')
    expect(text(root)).toContain('Проведено 0 из 2 пар')
  })
  it('renders peers as text, marks self by words and emits bounded server pages', () => {
    const { root, pages } = mount()
    const rows = all(
      root,
      (n) => String(n.props.class ?? '').includes('statistics-ranking__row') && n.tag === 'li',
    )
    expect(rows).toHaveLength(1)
    expect(text(rows[0]!)).toContain('<script>synthetic</script>')
    expect(text(rows[0]!)).toContain('Это я')
    expect(text(rows[0]!)).toContain('0%')
    expect(all(rows[0]!, (n) => ['button', 'a', 'input', 'script'].includes(n.tag))).toHaveLength(0)
    const next = all(root, (n) => n.tag === 'button' && text(n).trim() === 'Ниже')[0]!
    ;(next.props.onClick as () => void)()
    expect(pages).toEqual([2])
  })
  it('removes previous peer rows while paging and on authority failure', async () => {
    const { root, state } = mount()
    state.value = { status: 'loading' }
    await nextTick()
    expect(text(root)).not.toContain('synthetic')
    expect(text(root)).toContain('Загружаем рейтинг')
    state.value = { status: 'forbidden', reason: 'Доступ отозван' }
    await nextTick()
    expect(text(root)).not.toContain('synthetic')
    expect(text(root)).toContain('Доступ отозван')
    expect(all(root, (n) => n.tag === 'li')).toHaveLength(0)
  })
})
