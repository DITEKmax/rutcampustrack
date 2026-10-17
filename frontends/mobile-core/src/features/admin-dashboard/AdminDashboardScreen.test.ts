import { createRenderer, h, markRaw, nextTick, reactive, type App } from 'vue'
import { afterEach, describe, expect, it, vi } from 'vitest'
import AdminDashboardScreen from './AdminDashboardScreen.vue'
import { createAdminActivityFixture } from '../../dev/admin-dashboard-fixtures'
import type { AdminDashboardClient, AdminDashboardStats } from './admin-dashboard-client'

vi.mock('./AdminActivityChart.vue', () => ({ default: () => h('div') }))
vi.mock('../../shared/components/MobileToolsSheet.vue', () => ({ default: () => h('div') }))
class Element {
  children: Element[] = []
  parent: Element | null = null
  props: Record<string, unknown> = {}
  text = ''
  constructor(readonly tag: string) {
    markRaw(this)
  }
}
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
})
const apps: App[] = []
afterEach(() => {
  apps.splice(0).forEach((app) => app.unmount())
  vi.restoreAllMocks()
})
function text(root: Element): string {
  return root.text + root.children.map(text).join('')
}
async function flush(): Promise<void> {
  for (let index = 0; index < 10; index++) {
    await Promise.resolve()
    await nextTick()
  }
}
function stats(count: number): AdminDashboardStats {
  return {
    totalStudents: count,
    totalTeachers: 4,
    totalGroups: 2,
    activeGroups: 2,
    activeSemesterName: null,
  }
}
function pending<T>(): {
  promise: Promise<T>
  resolve: (value: T) => void
  reject: (error: unknown) => void
} {
  let resolve!: (value: T) => void, reject!: (error: unknown) => void
  return {
    promise: new Promise<T>((done, fail) => {
      resolve = done
      reject = fail
    }),
    resolve,
    reject,
  }
}
function mount(client: AdminDashboardClient): {
  root: Element
  props: { client: AdminDashboardClient; invalidationRevision: number }
  app: App
} {
  const props = reactive({ client: markRaw(client), invalidationRevision: 0 })
  const root = new Element('root'),
    app = renderer.createApp({ setup: () => () => h(AdminDashboardScreen, props) })
  apps.push(app)
  app.mount(root)
  return { root, props, app }
}
describe('dashboard event invalidation lifecycle', () => {
  it.each(['ready', 'geo-zero', 'unavailable'] as const)(
    'shows truthful absolute geo counts across activity periods for %s',
    async (variant) => {
      const getActivity = vi.fn(async (period: 'today' | 'all_time') =>
        createAdminActivityFixture(period, 'day', variant),
      )
      const { root } = mount({
        getStats: async () => stats(17),
        getActivity,
      } as unknown as AdminDashboardClient)
      await flush()
      const descendants = (node: Element): Element[] => [
        node,
        ...node.children.flatMap(descendants),
      ]
      const geo = () =>
        descendants(root).find((node) => node.props['data-admin-activity-metric'] === 'geoMarks')!
      const assertGeo = (period: 'today' | 'all_time') => {
        const metric = createAdminActivityFixture(period, 'day', variant).summary.metrics.geoMarks
        if (metric.state === 'AVAILABLE') {
          const number = new Intl.NumberFormat('ru-RU')
          expect(text(geo())).toContain(
            `${number.format(metric.numerator!)} из ${number.format(metric.denominator!)}`,
          )
          if (metric.denominator === 0) expect(text(geo())).not.toContain('%')
        } else {
          expect(text(geo())).not.toContain(' из ')
          expect(text(geo())).not.toContain('%')
        }
      }
      assertGeo('today')
      const allTime = descendants(root).find(
        (node) => node.tag === 'button' && text(node).trim() === 'Всё время',
      )!
      ;(allTime.props.onClick as () => void)()
      await flush()
      assertGeo('all_time')
      expect(getActivity.mock.calls.at(-1)?.[0]).toBe('all_time')
    },
  )

  it('keeps scale growth visible across activity periods and makes older missing history explicit', async () => {
    const known = {
      ...stats(17),
      dailyGrowth: {
        status: 'AVAILABLE' as const,
        baselineDate: '2026-09-30',
        studentsDelta: 2,
        teachersDelta: -1,
        groupsDelta: 0,
      },
    }
    const getStats = vi.fn().mockResolvedValueOnce(known).mockResolvedValueOnce(stats(19))
    const getActivity = vi.fn(async (period: 'today' | 'all_time') =>
      createAdminActivityFixture(period),
    )
    const { root, props } = mount({ getStats, getActivity } as unknown as AdminDashboardClient)
    await flush()
    expect(text(root)).toContain('прирост +2 за сутки')
    expect(text(root)).toContain('снижение −1 за сутки')
    expect(text(root)).toContain('без изменений за сутки')
    const descendants = (node: Element): Element[] => [node, ...node.children.flatMap(descendants)]
    const allTime = descendants(root).find(
      (node) => node.tag === 'button' && text(node).trim() === 'Всё время',
    )!
    ;(allTime.props.onClick as () => void)()
    await flush()
    expect(getActivity.mock.calls.at(-1)?.[0]).toBe('all_time')
    expect(getStats).toHaveBeenCalledTimes(1)
    expect(text(root)).toContain('прирост +2 за сутки')
    expect(text(root)).toContain('снижение −1 за сутки')
    expect(text(root)).toContain('без изменений за сутки')
    props.invalidationRevision++
    await flush()
    expect(text(root).match(/нет данных за вчера/g)).toHaveLength(3)
    expect(text(root)).toContain('19')
    expect(text(root)).not.toContain('прирост +2 за сутки')
  })

  it('preserves visible data during quiet refresh and rejects a superseded response', async () => {
    const old = pending<AdminDashboardStats>(),
      latest = pending<AdminDashboardStats>()
    const signals: AbortSignal[] = []
    const getStats = vi
      .fn()
      .mockImplementationOnce(async () => stats(17))
      .mockImplementationOnce((signal: AbortSignal) => {
        signals.push(signal)
        return old.promise
      })
      .mockImplementationOnce((signal: AbortSignal) => {
        signals.push(signal)
        return latest.promise
      })
    const getActivity = vi.fn(async () => createAdminActivityFixture())
    const { root, props } = mount({ getStats, getActivity } as unknown as AdminDashboardClient)
    await flush()
    props.invalidationRevision++
    await flush()
    expect(text(root)).toContain('17')
    expect(text(root)).not.toContain('Загружаем данные')
    props.invalidationRevision++
    await flush()
    expect(signals[0]!.aborted).toBe(true)
    latest.resolve(stats(23))
    await flush()
    old.resolve(stats(99))
    await flush()
    expect(text(root)).toContain('23')
    expect(text(root)).not.toContain('99')
    expect(getActivity).toHaveBeenCalledTimes(3)
  })
  it('keeps prior values beside a retryable error and aborts requests on disposal', async () => {
    const request = pending<AdminDashboardStats>(),
      activityRequest = pending<ReturnType<typeof createAdminActivityFixture>>()
    let signal!: AbortSignal, activitySignal!: AbortSignal
    const getStats = vi
      .fn()
      .mockResolvedValueOnce(stats(17))
      .mockImplementationOnce((value: AbortSignal) => {
        signal = value
        return request.promise
      })
    const getActivity = vi
      .fn()
      .mockResolvedValueOnce(createAdminActivityFixture())
      .mockImplementationOnce((_period: string, _bucket: string, value: AbortSignal) => {
        activitySignal = value
        return activityRequest.promise
      })
    const { root, props, app } = mount({ getStats, getActivity } as unknown as AdminDashboardClient)
    await flush()
    props.invalidationRevision++
    await flush()
    request.reject(new Error('synthetic-unavailable'))
    await flush()
    expect(text(root)).toContain('17')
    expect(text(root)).toContain('Статистику не удалось загрузить')
    app.unmount()
    expect(signal.aborted).toBe(true)
    expect(activitySignal.aborted).toBe(true)
    activityRequest.resolve(createAdminActivityFixture())
    await flush()
  })
})
