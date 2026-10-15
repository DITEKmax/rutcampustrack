import { createRenderer, h, markRaw, nextTick, reactive, type App } from 'vue'
import { afterEach, describe, expect, it, vi } from 'vitest'
import AdminAttendanceScreen from './AdminAttendanceScreen.vue'
import type {
  AdminAttendanceClient,
  AdminAttendanceQuery,
  AdminAttendanceReport,
} from './admin-attendance-client'
import type { AdminAccessProfile } from '../admin-access/admin-access-client'
const exports = vi.hoisted(() => [] as unknown[])
vi.mock('../../shared/components/ExportButton.vue', () => ({
  default: {
    props: ['disabled', 'request'],
    setup(props: { disabled: boolean; request: (format: string) => unknown }) {
      return () =>
        h(
          'button',
          { disabled: props.disabled, onClick: () => exports.push(props.request('html')) },
          'Выгрузка',
        )
    },
  },
}))
class TestElement extends EventTarget {
  children: TestElement[] = []
  parentElement: TestElement | null = null
  props: Record<string, unknown> = {}
  selected = false
  selectedIndex = -1
  get options(): TestElement[] {
    return this.children.filter((node) => node.tag === 'option')
  }
  get multiple(): boolean {
    return Boolean(this.props.multiple)
  }
  value = ''
  text = ''
  composing = false
  constructor(readonly tag: string) {
    super()
    markRaw(this)
  }
  get tagName(): string {
    return this.tag.toUpperCase()
  }
  get type(): string {
    return String(this.props.type ?? '')
  }
  getRootNode(): typeof testDocument {
    return testDocument
  }
  focus(): void {
    testDocument.activeElement = this
  }
}
class TestDocument {
  activeElement: TestElement | null = null
}
const testDocument = new TestDocument()
const renderer = createRenderer<TestElement, TestElement>({
  createElement: (tag) => new TestElement(tag),
  createText: (text) => Object.assign(new TestElement('#text'), { text }),
  createComment: () => new TestElement('#comment'),
  setText: (node, text) => {
    node.text = text
  },
  setElementText: (node, text) => {
    node.text = text
    node.children = []
  },
  parentNode: (node) => node.parentElement,
  nextSibling: (node) =>
    node.parentElement?.children[node.parentElement.children.indexOf(node) + 1] ?? null,
  patchProp: (node, key, _old, value) => {
    node.props[key] = value
    if (key === 'value') node.value = String(value ?? '')
  },
  remove: (node) => {
    if (node.parentElement)
      node.parentElement.children = node.parentElement.children.filter((item) => item !== node)
    node.parentElement = null
  },
  insert: (node, parent, anchor) => {
    const index = anchor ? parent.children.indexOf(anchor) : -1
    parent.children.splice(index < 0 ? parent.children.length : index, 0, node)
    node.parentElement = parent
  },
})

const apps: App[] = []
afterEach(() => {
  for (const app of apps.splice(0)) app.unmount()
  vi.unstubAllGlobals()
  exports.splice(0)
})
function nodes(root: TestElement): TestElement[] {
  return [root, ...root.children.flatMap(nodes)]
}
function text(root: TestElement): string {
  return nodes(root)
    .map((node) => node.text)
    .join(' ')
}
function button(root: TestElement, label: string): TestElement {
  const node = nodes(root).find((node) => node.tag === 'button' && text(node).trim() === label)
  if (!node) throw new Error('Missing ' + label)
  return node
}

async function flush(): Promise<void> {
  for (let i = 0; i < 15; i++) {
    await Promise.resolve()
    await nextTick()
  }
}
const semester = {
  id: '31',
  name: 'Весна',
  dateFrom: '2026-02-01',
  dateTo: '2026-06-30',
  active: false,
  archived: true,
}
function result(query: AdminAttendanceQuery): AdminAttendanceReport {
  return {
    ...query,
    semester,
    observedAt: '2026-10-15T10:00:00Z',
    totalElements: 2,
    totalPages: 1,
    rows: [
      {
        groupId: '7',
        groupName: 'Своя группа',
        countedLessons: 2,
        presentCount: 0,
        absentCount: 4,
        excusedCount: 0,
        opportunities: 4,
        presentPercent: 0,
        presentOrExcusedPercent: 0,
        dataState: 'AVAILABLE',
      },
      {
        groupId: '8',
        groupName: 'Пустая группа',
        countedLessons: 0,
        presentCount: 0,
        absentCount: 0,
        excusedCount: 0,
        opportunities: 0,
        presentPercent: null,
        presentOrExcusedPercent: null,
        dataState: 'NO_DATA',
      },
    ],
  }
}
function mount(
  overrides: Partial<{
    getSemesters: () => Promise<unknown>
    getReport: (query: AdminAttendanceQuery) => Promise<unknown>
  }> = {},
) {
  vi.stubGlobal('document', testDocument)
  vi.stubGlobal('Document', TestDocument)
  const client = {
    getSemesters: vi.fn(async () => [semester]),
    getReport: vi.fn(async (query: AdminAttendanceQuery) => result(query)),
    ...overrides,
  } as unknown as AdminAttendanceClient
  const authority: AdminAccessProfile = {
    userId: 1,
    assigned: true,
    mode: 'LIMITED',
    permissions: ['DASHBOARD_VIEW'],
    version: 1,
  }
  const props = reactive({ client: markRaw(client), authority, online: true })
  const root = new TestElement('root'),
    app = renderer.createApp({ render: () => h(AdminAttendanceScreen, props) })
  apps.push(app)
  app.mount(root)
  return { root, props, client }
}
describe('current administrator group attendance view', () => {
  it('renders canonical server counts, both rates and unknown separately from a genuine zero, with no personal drilldown', async () => {
    const { root } = mount()
    await flush()
    const rows = nodes(root).filter((node) => node.tag === 'tr' && node.props['data-group-id'])
    expect(text(rows[0]!)).toContain('0 %')
    expect(text(rows[1]!)).toContain('Нет данных')
    expect(
      nodes(rows[1]!).filter((node) => node.tag === 'td' && text(node) === 'Нет данных'),
    ).toHaveLength(2)
    expect(
      nodes(root).some((node) => node.tag === 'button' && text(node).includes('Своя группа')),
    ).toBe(false)
    ;(button(root, 'Выгрузка').props.onClick as () => void)()
    expect(exports).toEqual([
      {
        kind: 'ADMIN_GROUP_ATTENDANCE',
        adminGroupAttendance: {
          semesterId: '31',
          search: '',
          sort: 'GROUP_NAME',
          direction: 'ASC',
          format: 'html',
        },
      },
    ])
  })
  it('applies the trimmed server filter and exports all matching rows without page parameters', async () => {
    const { root, client } = mount()
    await flush()
    const field = nodes(root).find((node) => node.tag === 'input')!
    ;(field.props['onUpdate:modelValue'] as (value: string) => void)('  АБ  ')
    const form = nodes(root).find((node) => node.tag === 'form')!
    ;(form.props.onSubmit as (event: Event) => void)(new Event('submit', { cancelable: true }))
    await flush()
    expect(client.getReport).toHaveBeenLastCalledWith(
      { semesterId: '31', search: 'АБ', sort: 'GROUP_NAME', direction: 'ASC', page: 0, size: 25 },
      expect.any(AbortSignal),
    )
    ;(button(root, 'Выгрузка').props.onClick as () => void)()
    expect(exports).toEqual([
      {
        kind: 'ADMIN_GROUP_ATTENDANCE',
        adminGroupAttendance: {
          semesterId: '31',
          search: 'АБ',
          sort: 'GROUP_NAME',
          direction: 'ASC',
          format: 'html',
        },
      },
    ])
  })
  it('clears data synchronously after revocation or offline and ignores the old owner response', async () => {
    let resolve!: (report: AdminAttendanceReport) => void
    const held = new Promise<AdminAttendanceReport>((done) => {
      resolve = done
    })
    const { root, props, client } = mount({ getReport: () => held })
    await flush()
    props.authority = { ...props.authority, permissions: ['USERS_VIEW'], version: 2 }
    await nextTick()
    resolve(
      result({
        semesterId: '31',
        search: '',
        sort: 'GROUP_NAME',
        direction: 'ASC',
        page: 0,
        size: 25,
      }),
    )
    await flush()
    expect(text(root)).not.toContain('Своя группа')
    expect(text(root)).toContain('Нет доступа')
    expect(client.getSemesters).toHaveBeenCalledTimes(1)
    props.online = false
    await flush()
    expect(text(root)).toContain('подключись к сети')
  })
  it('never publishes a late report from the previous owner while the new owner is loading', async () => {
    let finishOld!: (value: AdminAttendanceReport) => void
    let finishCurrent!: (value: AdminAttendanceReport) => void
    const old = new Promise<AdminAttendanceReport>((done) => {
      finishOld = done
    })
    const current = new Promise<AdminAttendanceReport>((done) => {
      finishCurrent = done
    })
    const { root, props } = mount({ getReport: () => old })
    await flush()
    const query: AdminAttendanceQuery = {
      semesterId: '31',
      search: '',
      sort: 'GROUP_NAME',
      direction: 'ASC',
      page: 0,
      size: 25,
    }
    props.client = markRaw({
      getSemesters: async () => [semester],
      getReport: () => current,
    } as unknown as AdminAttendanceClient)
    props.authority = { ...props.authority, userId: 2, version: 2 }
    await flush()
    const prior = result(query)
    finishOld({ ...prior, rows: [{ ...prior.rows[0]!, groupName: 'Старый закрытый scope' }] })
    await flush()
    expect(text(root)).not.toContain('Старый закрытый scope')
    expect(text(root)).not.toContain('Выгрузка')
    finishCurrent(result(query))
    await flush()
    expect(text(root)).toContain('Своя группа')
  })
  it('shows an actual dependency error and retries without fabricating zero rows or accepting a stale query', async () => {
    let calls = 0
    const { root } = mount({
      getReport: async (query) => {
        if (++calls === 1) throw new Error('unavailable')
        return result(query)
      },
    })
    await flush()
    expect(text(root)).toContain('Посещаемость не удалось загрузить')
    expect(text(root)).not.toContain('Своя группа')
    ;(button(root, 'Повторить').props.onClick as () => void)()
    await flush()
    expect(text(root)).toContain('Своя группа')
    expect(text(root)).not.toContain('Посещаемость не удалось загрузить')
  })
})
