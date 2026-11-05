import { createRenderer, h, markRaw, nextTick, ref, shallowReactive, type App } from 'vue'
import { afterEach, describe, expect, it, vi } from 'vitest'
import StatisticsScreen from './StatisticsScreen.vue'
import type {
  StatisticsOverviewData,
  StatisticsRankingData,
  StatisticsReadState,
  StatisticsSeriesPoint,
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
  getBoundingClientRect(): { width: number } {
    return { width: 280 }
  }
  get textContent(): string {
    return this.text + this.children.map((node) => node.textContent).join(' ')
  }
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
afterEach(() => {
  apps.splice(0).forEach((app) => app.unmount())
  vi.unstubAllGlobals()
})
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
function mount(overview: StatisticsOverviewData = data) {
  const state = ref<StatisticsReadState<StatisticsRankingData>>({
    status: 'ready',
    data: {
      available: true,
      page: 1,
      size: 20,
      total: 42,
      ownPosition: 22,
      rows: [
        { id: '1', name: 'Иванов Иван Олегович', position: 22, percentage: 0, isSelf: true },
        { id: '2', name: '<script>synthetic</script>', position: 23, percentage: 0, isSelf: false },
      ],
    },
  })
  const root = new Node('root')
  const pages: number[] = []
  const openRanking = vi.fn()
  const app = renderer.createApp({
    setup: () => () =>
      h(StatisticsScreen, {
        state: { status: 'ready', data: overview },
        rankingVisible: true,
        rankingState: state.value,
        onRankingPage: (page: number) => pages.push(page),
        onOpenRanking: openRanking,
      }),
  })
  app.mount(root)
  apps.push(app)
  return { root, state, pages, openRanking }
}
describe('subject summary authoritative percentages', () => {
  it('uses all three supplied percentages, keeps unknown distinct from zero and ignores future/planned totals', () => {
    const { root } = mount({
      ...data,
      subjects: [
        {
          id: 'known',
          name: 'Предмет с отметками',
          metrics: {
            ...metrics,
            held: 4,
            planned: 100,
            present: { count: 999, percent: 50 },
            excused: { count: 999, percent: 25 },
            absent: { count: 999, percent: 25 },
          },
        },
        {
          id: 'unknown',
          name: 'Предмет без полных данных',
          metrics: {
            ...metrics,
            held: 0,
            planned: 10,
            present: { count: 0, percent: null },
            excused: { count: 0, percent: 0 },
            absent: { count: 0, percent: null },
          },
        },
      ],
    })
    const summaries = all(
      root,
      (n) => n.tag === 'button' && n.props.class === 'statistics-subject-summary',
    )
    expect(
      summaries.map((summary) =>
        all(summary, (n) =>
          String(n.props.class ?? '').startsWith('statistics-subject-summary__metric '),
        ).map((n) => text(n).replace(/\s+/gu, '')),
      ),
    ).toEqual([
      ['Был50%', 'Уважительнаяпричина25%', 'Небыл25%'],
      ['Был—', 'Уважительнаяпричина0%', 'Небыл—'],
    ])
  })
})

describe('compact statistics accepted metrics and readonly own-group list', () => {
  it('renders peers as text, emphasizes self and expands the group through its chevron', () => {
    const { root, pages, openRanking } = mount()
    const rows = all(
      root,
      (n) => String(n.props.class ?? '').includes('statistics-ranking__row') && n.tag === 'li',
    )
    expect(rows).toHaveLength(2)
    expect(text(rows[0]!)).toContain('Иванов И. О.')
    const name = all(rows[0]!, (n) => n.props.class === 'statistics-ranking__name')[0]!
    expect(name.children.find((n) => n.props['aria-hidden'] === 'true')?.text).toBe('Иванов И. О.')
    expect(name.children.find((n) => n.props.class === 'statistics-visually-hidden')?.text).toBe(
      'Иванов Иван Олегович',
    )
    expect(text(rows[1]!)).toContain('<script>synthetic</script>')
    expect(rows[0]!.props['data-self']).toBe(true)
    expect(text(rows[0]!)).not.toContain('Это я')
    expect(text(rows[0]!)).toContain('0%')
    expect(all(rows[0]!, (n) => ['button', 'a', 'input', 'script'].includes(n.tag))).toHaveLength(0)
    const toggle = all(
      root,
      (n) => n.tag === 'button' && n.props['aria-label'] === 'Рейтинг группы',
    )[0]!
    expect(toggle.props['aria-expanded']).toBe(true)
    expect(all(toggle, (n) => n.tag === 'svg')).toHaveLength(1)
    ;(toggle.props.onClick as () => void)()
    expect(openRanking).toHaveBeenCalledOnce()
    expect(all(root, (n) => n.tag === 'nav')).toHaveLength(0)
    expect(pages).toEqual([])
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

function chartPoints(): StatisticsSeriesPoint[] {
  return [
    {
      id: 'first',
      label: 'Первая неделя',
      dateFrom: '2026-09-01',
      dateTo: '2026-09-06',
      state: 'DATA',
      metrics: {
        ...metrics,
        held: 4,
        present: { count: 999, percent: 33.3 },
        presentOrExcused: { count: 1, percent: 87.6 },
      },
      groupPresent: { numerator: 999, denominator: 1, percent: 66.7 },
    },
    {
      id: 'zero',
      label: 'Вторая неделя',
      dateFrom: '2026-09-07',
      dateTo: '2026-09-13',
      state: 'DATA',
      metrics: { ...metrics, held: 4, planned: 4 },
      groupPresent: null,
    },
    {
      id: 'unknown',
      label: 'Третья неделя',
      dateFrom: '2026-09-14',
      dateTo: '2026-09-20',
      state: 'NO_DATA',
      metrics: { ...metrics, present: { count: 0, percent: null } },
      groupPresent: { numerator: 0, denominator: 4, percent: 0 },
    },
  ]
}

async function mountChart() {
  // Existing screen tests keep their chart stub; this carrier renders the actual SFC.
  const { default: Chart } = await vi.importActual<typeof import('./StatisticsSemesterChart.vue')>(
    './StatisticsSemesterChart.vue',
  )
  vi.stubGlobal(
    'ResizeObserver',
    class {
      observe(): void {}
      unobserve(): void {}
      disconnect(): void {}
    },
  )
  vi.stubGlobal('getComputedStyle', () => ({ columnGap: '8px' }))
  vi.stubGlobal('document', {
    fonts: Object.assign(new EventTarget(), { ready: Promise.resolve() }),
  })
  const props = shallowReactive({
    points: chartPoints(),
    hiddenGraph: false,
    loading: false,
    errorMessage: null as string | null,
  })
  const root = new Node('root')
  const app = renderer.createApp({
    render: () => h(Chart, props),
  })
  app.mount(root)
  apps.push(app)
  await nextTick()
  return { root, props }
}

describe('semester chart accessible same-point data', () => {
  it('renders all ordered own/group PRESENT points with periods and distinguishes null from zero', async () => {
    const { root } = await mountChart()
    const table = all(root, (node) => node.tag === 'table')[0]
    expect(table).toBeDefined()
    expect(all(root, (node) => node.tag === 'details' || node.tag === 'summary')).toHaveLength(0)
    expect(text(all(table!, (node) => node.tag === 'caption')[0]!)).toContain('Посещаемость')
    expect(
      all(table!, (node) => node.tag === 'th' && node.props.scope === 'col').map((node) =>
        text(node).trim(),
      ),
    ).toEqual(['Период', 'Моя посещаемость — был', 'Средняя своей группы — был'])
    const rows = all(
      table!,
      (node) => node.tag === 'tr' && node.children.some((cell) => cell.tag === 'td'),
    )
    expect(rows).toHaveLength(3)
    expect(rows.map((row) => text(all(row, (node) => node.props.scope === 'row')[0]!))).toEqual([
      expect.stringContaining('Первая неделя'),
      expect.stringContaining('Вторая неделя'),
      expect.stringContaining('Третья неделя'),
    ])
    expect(text(rows[0]!)).toContain('01.09 — 06.09')
    expect(rows.map((row) => all(row, (node) => node.tag === 'td').map(text))).toEqual([
      ['33%', '67%'],
      ['0%', '—'],
      ['—', '0%'],
    ])
    expect(text(table!)).not.toContain('88%')
  })

  it('replaces old rows with the same new point set when the chart period changes', async () => {
    const { root, props } = await mountChart()
    const point = chartPoints()[0]!
    props.points = [
      { ...point, id: 'day', label: 'Новый день', dateFrom: '2026-10-01', dateTo: '2026-10-01' },
    ]
    await nextTick()
    const table = all(root, (node) => node.tag === 'table')[0]!
    expect(table).toBeDefined()
    expect(text(table)).toContain('Новый день')
    expect(text(table)).toContain('01.10')
    expect(text(table)).not.toContain('Первая неделя')
    expect(all(table, (node) => node.tag === 'td').map(text)).toEqual(['33%', '67%'])
  })

  it.each(['hidden', 'loading', 'error', 'empty'] as const)(
    'removes the old accessible table when the graph becomes %s',
    async (state) => {
      const { root, props } = await mountChart()
      expect(all(root, (node) => node.tag === 'table')).toHaveLength(1)
      if (state === 'hidden') props.hiddenGraph = true
      if (state === 'loading') props.loading = true
      if (state === 'error') props.errorMessage = 'Синтетический отказ доступа'
      if (state === 'empty') props.points = []
      await nextTick()
      expect(all(root, (node) => node.tag === 'table' || node.tag === 'details')).toHaveLength(0)
      expect(text(root)).not.toContain('Первая неделя')
    },
  )
})
