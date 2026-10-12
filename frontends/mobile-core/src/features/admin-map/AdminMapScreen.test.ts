import {
  compile,
  ssrContextKey,
  createRenderer,
  h,
  markRaw,
  nextTick,
  reactive,
  ref,
  type App,
} from 'vue'
import { afterEach, describe, expect, it, vi } from 'vitest'
import AdminMapScreen from './AdminMapScreen.vue'
import mapSource from './AdminMapScreen.vue?raw'
import { compileScript, parse } from '@vue/compiler-sfc'
const descriptor = parse(mapSource).descriptor
AdminMapScreen.render = compile(descriptor.template!.content, {
  bindingMetadata: compileScript(descriptor, { id: 'admin-map-authority-test' }).bindings!,
  prefixIdentifiers: true,
})
import { AdminMapClient } from '../../api/map-client'
import type { AdminMapPlan } from '../../api/types'
import type { AdminAccessProfile } from '../admin-access/admin-access-client'
import {
  createAdminAuthorityOwner,
  createAdminGenerationReader,
} from '../../shared/admin-authority'
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
function click(node: TestElement): void {
  ;(node.props.onClick as () => void)()
}
async function input(node: TestElement, value: string): Promise<void> {
  node.value = value
  node.dispatchEvent(new Event('input'))
  await nextTick()
}
async function settle(): Promise<void> {
  for (let i = 0; i < 12; i++) {
    await Promise.resolve()
    await nextTick()
  }
}
function access(deletion = false): AdminAccessProfile {
  return {
    userId: 1,
    assigned: true,
    mode: 'LIMITED',
    permissions: ['CAMPUS_MAP_MANAGE', ...(deletion ? ['DATA_DELETE'] : [])],
    version: 1,
  }
}
const inventory = [
  {
    id: '20',
    code: '1',
    label: 'Корпус1',
    floors: [
      {
        id: '200',
        buildingId: '20',
        code: '1',
        label: 'Этаж1',
        currentPlan: null,
        openCount: 0,
        openCountPeriod: 'all_time',
      },
    ],
  },
]
function mount(client: AdminMapClient, authority: AdminAccessProfile | null) {
  vi.stubGlobal('document', testDocument)
  vi.stubGlobal('Document', TestDocument)
  const props = reactive({
    client: markRaw(client),
    authority: authority ? markRaw(authority) : null,
  })
  const root = new TestElement('root')
  const app = renderer.createApp({ setup: () => () => h(AdminMapScreen, props) })
  app.provide(ssrContextKey, { modules: new Set() })
  apps.push(app)
  app.mount(root)
  return { root, props }
}
const slot = (format: 'png' | 'svg') => ({
  format,
  state: 'ready' as const,
  contentType: format === 'png' ? ('image/png' as const) : ('image/svg+xml' as const),
  id: 'asset-' + format,
  bytes: 3,
  sha256: 'a'.repeat(64),
  width: 1,
  height: 1,
  viewBox: null,
})
const plan: AdminMapPlan = {
  buildingId: '20',
  floorId: '200',
  version: '2',
  catalogRevision: 'r2',
  label: 'plan',
  png: slot('png'),
  svg: slot('svg'),
}
function chooseFile(root: TestElement): File {
  const file = new File(['map'], 'plan.png', { type: 'image/png' })
  const node = nodes(root).find(
    (node) => node.tag === 'input' && String(node.props.accept).includes('png'),
  )!
  ;(node.props.onChange as (event: unknown) => void)({ target: { files: [file] } })
  return file
}
async function selectTarget(root: TestElement, building = '1', floor = '1'): Promise<void> {
  await input(nodes(root).find((node) => node.props['data-field'] === 'building-code')!, building)
  await input(nodes(root).find((node) => node.props['data-field'] === 'floor-code')!, floor)
  await settle()
}
async function draft(root: TestElement, building = '1', floor = '1'): Promise<File> {
  await selectTarget(root, building, floor)
  const file = chooseFile(root)
  await settle()
  return file
}
function target(url: unknown, currentPlan: AdminMapPlan | null = null) {
  const query = new URL(String(url), 'http://localhost').searchParams
  const buildingCode = query.get('buildingCode')!,
    floorCode = query.get('floorCode')!
  return {
    buildingCode,
    floorCode,
    status: 'found',
    floor: { ...inventory[0]!.floors[0], code: floorCode, currentPlan },
  }
}
function readyResponse(url: unknown, currentPlan: AdminMapPlan | null = plan): Response {
  return String(url).includes('/assets/')
    ? new Response('map', {
        headers: { 'Content-Type': String(url).includes('/png/') ? 'image/png' : 'image/svg+xml' },
      })
    : Response.json(target(url, currentPlan))
}
describe('mounted typed admin map authority and session boundary', () => {
  it('does not load or expose controls before authority is confirmed or a target is typed', async () => {
    const fetcher = vi.fn<typeof fetch>(async (url) => readyResponse(url))
    const { root } = mount(
      new AdminMapClient({ accessToken: () => null, currentGeneration: () => 1, fetcher }),
      null,
    )
    await settle()
    expect(fetcher).not.toHaveBeenCalled()
    expect(text(root)).toContain('Управление картой недоступно')
    expect(nodes(root).some((node) => node.tag === 'input')).toBe(false)
  })
  it('allows cross-tab generation cleanup to finish while the map sync watcher runs', async () => {
    const generation = ref(1),
      owner = createAdminAuthorityOwner()
    owner.bind({ getMe: async () => access() }, 'owner', '1')
    await owner.refresh()
    const revision = owner.snapshot.revision,
      fetcher = vi.fn<typeof fetch>(async (url) => readyResponse(url, null))
    const client = new AdminMapClient({
      accessToken: () => null,
      currentGeneration: createAdminGenerationReader(
        () => generation.value,
        () => owner.isCurrent(revision),
        1,
      ),
      fetcher,
    })
    const { root } = mount(client, access())
    await selectTarget(root)
    expect(() => {
      generation.value = 2
    }).not.toThrow()
    await settle()
    expect(client.isCurrentGeneration(1)).toBe(false)
    expect(fetcher).toHaveBeenCalledTimes(1)
    expect(nodes(root).find((node) => node.props['data-field'] === 'building-code')!.value).toBe('')
    owner.dispose()
  })
  it('drops late target JSON when authority disappears', async () => {
    let release!: (value: unknown) => void
    const response = new Response('{}')
    response.json = () =>
      new Promise((resolve) => {
        release = resolve
      })
    const { root, props } = mount(
      new AdminMapClient({
        accessToken: () => null,
        currentGeneration: () => 1,
        fetcher: async () => response,
      }),
      access(),
    )
    await selectTarget(root)
    props.authority = null
    release(target('/plans/target?buildingCode=1&floorCode=1', plan))
    await settle()
    expect(text(root)).toContain('Управление картой недоступно')
    expect(nodes(root).some((node) => node.tag === 'img')).toBe(false)
  })
  it('uploads one exact target/files command without precreating inventory or DATA_DELETE', async () => {
    const fetcher = vi.fn<typeof fetch>(async (url, init) =>
      init?.method === 'POST'
        ? Response.json(
            { status: 'created', buildingCreated: false, floorCreated: false, plan },
            { status: 201 },
          )
        : readyResponse(url, null),
    )
    const { root } = mount(
      new AdminMapClient({ accessToken: () => null, currentGeneration: () => 1, fetcher }),
      access(),
    )
    const file = await draft(root)
    expect(nodes(root).filter((node) => node.props['data-action'] === 'delete')).toHaveLength(0)
    click(button(root, 'Сохранить схему'))
    await settle()
    const posts = fetcher.mock.calls.filter(([, init]) => init?.method === 'POST')
    expect(posts).toHaveLength(1)
    expect(posts[0]![0]).toBe('/api/academic/map/plans')
    const body = posts[0]![1]!.body as FormData
    expect(body.get('buildingCode')).toBe('1')
    expect(body.get('floorCode')).toBe('1')
    expect((body.get('png') as File).name).toBe(file.name)
    expect(text(root)).toContain('Схема добавлена')
  })
  it('keeps password proof and format deletion protected and clears the dialog when rights are revoked', async () => {
    const fetcher = vi.fn<typeof fetch>(async (url, init) => {
      if (String(url).includes('/deletion-preview'))
        return Response.json({
          buildingCode: '1',
          floorCode: '1',
          format: 'png',
          label: 'Схема',
          files: 1,
          bytes: 3,
          previewDigest: 'a'.repeat(64),
        })
      if (init?.method === 'POST')
        return Response.json({ code: 'CURRENT_PASSWORD_INVALID' }, { status: 403 })
      return readyResponse(url)
    })
    const { root, props } = mount(
      new AdminMapClient({ accessToken: () => null, currentGeneration: () => 1, fetcher }),
      access(true),
    )
    await selectTarget(root)
    click(nodes(root).find((node) => node.props['aria-label'] === 'Удалить PNG')!)
    await settle()
    await input(nodes(root).find((node) => node.props.type === 'password')!, 'synthetic-proof')
    click(button(root, 'Подтвердить удаление'))
    await settle()
    expect(text(root)).toContain('Пароль не подошёл')
    expect(nodes(root).some((node) => node.props.role === 'dialog')).toBe(true)
    const posted = fetcher.mock.calls.find(([, init]) => init?.method === 'POST')!
    expect(posted[0]).toBe('/api/academic/map/plans/target/deletion')
    expect(JSON.parse(String(posted[1]!.body))).toMatchObject({
      buildingCode: '1',
      floorCode: '1',
      format: 'png',
      previewDigest: 'a'.repeat(64),
    })
    props.authority = markRaw({ ...access(), version: 2 })
    await settle()
    expect(nodes(root).some((node) => node.props.type === 'password')).toBe(false)
    expect(fetcher.mock.calls.filter(([, init]) => init?.method === 'POST')).toHaveLength(1)
  })
  it('preserves target and staged files after rejected publication', async () => {
    const fetcher = vi.fn<typeof fetch>(async (url, init) =>
      init?.method === 'POST'
        ? Response.json({ detail: 'Файл отклонён' }, { status: 400 })
        : readyResponse(url, null),
    )
    const { root } = mount(
      new AdminMapClient({ accessToken: () => null, currentGeneration: () => 1, fetcher }),
      access(),
    )
    await draft(root, '2', '3')
    click(button(root, 'Сохранить схему'))
    await settle()
    expect(text(root)).toContain('Файл отклонён')
    expect(text(root)).toContain('plan.png')
    expect(nodes(root).find((node) => node.props['data-field'] === 'building-code')!.value).toBe(
      '2',
    )
    click(button(root, 'Сохранить схему'))
    await settle()
    expect(fetcher.mock.calls.filter(([, init]) => init?.method === 'POST')).toHaveLength(2)
  })
  it('reconciles unknown publication without another POST or fabricated success', async () => {
    let published = false
    const fetcher = vi.fn<typeof fetch>(async (url, init) => {
      if (init?.method === 'POST') {
        published = true
        throw new TypeError('lost ACK')
      }
      return readyResponse(url, published ? plan : null)
    })
    const { root } = mount(
      new AdminMapClient({ accessToken: () => null, currentGeneration: () => 1, fetcher }),
      access(),
    )
    await draft(root)
    click(button(root, 'Сохранить схему'))
    await settle()
    expect(text(root)).toContain('Исход загрузки неизвестен')
    expect(text(root)).toContain('plan.png')
    expect(button(root, 'Сохранить схему').props.disabled).toBe(true)
    click(button(root, 'Проверить текущую схему'))
    await settle()
    expect(text(root)).toContain('На сервере появилась новая схема')
    expect(text(root)).not.toContain('Изменение сохранено')
    click(button(root, 'Завершить проверку'))
    await settle()
    expect(text(root)).not.toContain('plan.png')
    expect(fetcher.mock.calls.filter(([, init]) => init?.method === 'POST')).toHaveLength(1)
  })
  it('drops late upload ACK and files when the authenticated client changes', async () => {
    let release!: (response: Response) => void
    const fetcher = vi.fn<typeof fetch>(async (url, init) =>
      init?.method === 'POST'
        ? new Promise((resolve) => {
            release = resolve
          })
        : readyResponse(url, null),
    )
    const nextFetcher = vi.fn<typeof fetch>(async (url) => readyResponse(url, null)),
      { root, props } = mount(
        new AdminMapClient({ accessToken: () => null, currentGeneration: () => 1, fetcher }),
        access(),
      )
    await draft(root, '2')
    click(button(root, 'Сохранить схему'))
    await settle()
    props.client = markRaw(
      new AdminMapClient({
        accessToken: () => null,
        currentGeneration: () => 2,
        fetcher: nextFetcher,
      }),
    )
    await settle()
    release(
      Response.json(
        { status: 'created', buildingCreated: true, floorCreated: true, plan },
        { status: 201 },
      ),
    )
    await settle()
    expect(text(root)).not.toContain('Изменение сохранено')
    expect(text(root)).not.toContain('plan.png')
    expect(nextFetcher).not.toHaveBeenCalled()
  })
  it('never displays late asset bytes for a changed typed target', async () => {
    const releases: ((response: Response) => void)[] = []
    const fetcher = vi.fn<typeof fetch>(async (url) =>
      String(url).includes('/assets/')
        ? new Promise((resolve) => {
            releases.push(resolve)
          })
        : readyResponse(
            url,
            new URL(String(url), 'http://localhost').searchParams.get('floorCode') === '1'
              ? plan
              : null,
          ),
    )
    const { root } = mount(
      new AdminMapClient({ accessToken: () => null, currentGeneration: () => 1, fetcher }),
      access(),
    )
    await selectTarget(root)
    expect(releases).toHaveLength(2)
    await input(nodes(root).find((node) => node.props['data-field'] === 'floor-code')!, '2')
    await settle()
    releases[0]!(new Response('map', { headers: { 'Content-Type': 'image/svg+xml' } }))
    releases[1]!(new Response('map', { headers: { 'Content-Type': 'image/png' } }))
    await settle()
    expect(nodes(root).some((node) => node.tag === 'img')).toBe(false)
    expect(nodes(root).some((node) => node.tag === 'a')).toBe(false)
  })
  it('removes old assets after acknowledged replacement with identical internal IDs', async () => {
    let version = '1'
    const fetcher = vi.fn<typeof fetch>(async (url, init) => {
      if (init?.method === 'POST') {
        version = '2'
        return Response.json({
          status: 'replaced',
          buildingCreated: false,
          floorCreated: false,
          plan: { ...plan, version },
        })
      }
      return readyResponse(url, { ...plan, version })
    })
    const { root } = mount(
      new AdminMapClient({ accessToken: () => null, currentGeneration: () => 1, fetcher }),
      access(),
    )
    await selectTarget(root)
    const oldUrl = nodes(root).find((node) => node.tag === 'a')!.props.href
    chooseFile(root)
    await settle()
    expect(nodes(root).find((node) => node.tag === 'a')!.props.href).toBe(oldUrl)
    click(button(root, 'Сохранить схему'))
    await settle()
    expect(nodes(root).find((node) => node.tag === 'a')!.props.href).not.toBe(oldUrl)
    expect(
      fetcher.mock.calls.filter(([url]) => String(url).includes('/versions/2/assets/')),
    ).toHaveLength(2)
  })
  it('rejects a late deletion preview after the typed target changes', async () => {
    let release!: (response: Response) => void
    const fetcher = vi.fn<typeof fetch>(async (url) =>
      String(url).includes('/deletion-preview')
        ? new Promise((resolve) => {
            release = resolve
          })
        : readyResponse(url),
    )
    const { root } = mount(
      new AdminMapClient({ accessToken: () => null, currentGeneration: () => 1, fetcher }),
      access(true),
    )
    await selectTarget(root)
    click(nodes(root).find((node) => node.props['aria-label'] === 'Удалить PNG')!)
    await settle()
    await input(nodes(root).find((node) => node.props['data-field'] === 'floor-code')!, '2')
    await settle()
    release(
      Response.json({
        buildingCode: '1',
        floorCode: '1',
        format: 'png',
        label: 'Схема',
        files: 1,
        bytes: 3,
        previewDigest: 'a'.repeat(64),
      }),
    )
    await settle()
    expect(nodes(root).some((node) => node.props.type === 'password')).toBe(false)
    expect(fetcher.mock.calls.some(([, init]) => init?.method === 'POST')).toBe(false)
  })
  it('does not normalize spaces, exponents, zero or aliases into a target request', async () => {
    const fetcher = vi.fn<typeof fetch>(async (url) => readyResponse(url)),
      { root } = mount(
        new AdminMapClient({ accessToken: () => null, currentGeneration: () => 1, fetcher }),
        access(),
      )
    for (const building of ['0', '01', ' 1', '1 ', '1e2', '１', '1'.repeat(65)])
      await selectTarget(root, building)
    expect(fetcher).not.toHaveBeenCalled()
    expect(
      nodes(root).find((node) => node.props['data-field'] === 'building-code')!.props[
        'aria-invalid'
      ],
    ).toBe(true)
  })
})
