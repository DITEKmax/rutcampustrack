import { createRenderer, h, markRaw, nextTick, type App } from 'vue'
import { afterEach, expect, it, vi } from 'vitest'
import { CampusMapClient } from '../../api/map-client'
import type { MapFormatSlot } from '../../api/types'
import MapScreen from './MapScreen.vue'
vi.mock('./MapSelect.vue', () => ({ default: { setup: () => () => null } }))

class Element {
  children: Element[] = []
  parent: Element | null = null
  props: Record<string, unknown> = {}
  text = ''
  scrollLeft = 0
  scrollTop = 0
  clientWidth = 240
  clientHeight = 240
  naturalWidth = 240
  naturalHeight = 240
  capture: number | null = null
  constructor(readonly tag: string) {
    markRaw(this)
  }
  get src() {
    return this.props.src
  }
  scrollTo(x: number, y: number) {
    this.scrollLeft = Math.max(0, x)
    this.scrollTop = Math.max(0, y)
  }
  scrollBy(x: number, y: number) {
    this.scrollTo(this.scrollLeft + x, this.scrollTop + y)
  }
  hasPointerCapture(id: number) {
    return this.capture === id
  }
  setPointerCapture(id: number) {
    this.capture = id
  }
  releasePointerCapture() {
    this.capture = null
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
  patchProp: (node, key, _before, value) => {
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
function all(node: Element, match: (node: Element) => boolean): Element[] {
  return [...(match(node) ? [node] : []), ...node.children.flatMap((child) => all(child, match))]
}
function content(node: Element): string {
  return node.text + node.children.map(content).join(' ')
}
async function flush() {
  for (let n = 0; n < 8; n += 1) {
    await Promise.resolve()
    await nextTick()
  }
}
let app: App | undefined
afterEach(() => {
  app?.unmount()
  app = undefined
  vi.unstubAllGlobals()
  vi.restoreAllMocks()
})

it.each([true, false])('keeps the loaded map usable (student: %s)', async (student) => {
  vi.stubGlobal('HTMLImageElement', Element)
  const client = new CampusMapClient({ accessToken: () => null })
  vi.spyOn(client, 'getManifest').mockResolvedValue({
    data: {
      schemaVersion: 1,
      validationPolicyVersion: 1,
      revision: 'local-test',
      buildings: [
        { id: 'b', label: 'Корпус 1', floors: [{ id: 'f', label: 'Этаж 1', plan: null }] },
      ],
    },
    etag: null,
  })
  const slot: MapFormatSlot = {
    format: 'svg',
    state: 'ready',
    contentType: 'image/svg+xml',
    id: 'asset',
    bytes: 6,
    sha256: null,
    width: 240,
    height: 240,
    viewBox: [0, 0, 240, 240],
  }
  vi.spyOn(client, 'getFloorPlan').mockResolvedValue({
    buildingId: 'b',
    floorId: 'f',
    label: 'Первый этаж',
    version: '1',
    svg: slot,
    png: { ...slot, format: 'png', contentType: 'image/png', state: 'absent', id: null },
  })
  vi.spyOn(client, 'downloadAsset').mockResolvedValue(
    new Blob(['<svg/>'], { type: 'image/svg+xml' }),
  )
  const recordFloorOpen = vi
    .spyOn(client, 'recordFloorOpen')
    .mockRejectedValue(new Error('Usage request failed'))
  const root = new Element('root')
  app = renderer.createApp({
    render: () => h(MapScreen, { client, studentPresentation: student, compactPresentation: !student }),
  })
  app.mount(root)
  await flush()
  const button = (label: string) =>
    all(root, (n) => n.tag === 'button' && n.props['aria-label'] === label)[0]!
  const zoomIn = button('Увеличить масштаб схемы'),
    zoomOut = button('Уменьшить масштаб схемы'),
    reset = button('Сбросить масштаб схемы')
  expect(reset).toBeDefined()
  expect(zoomIn.props.disabled).toBe(true)
  const image = all(root, (n) => n.tag === 'img')[0]!
  ;(image.props.onLoad as (event: unknown) => void)({ currentTarget: image })
  await flush()
  expect(recordFloorOpen).toHaveBeenCalledOnce()
  expect(content(root)).not.toContain('Открытие не сохранено')
  expect(zoomIn.props.disabled).toBe(false)
  expect(zoomOut.props.disabled).toBe(true)
  const click = async (node: Element) => {
    ;(node.props.onClick as () => void)()
    await nextTick()
  }
  for (let n = 0; n < 14; n += 1) await click(zoomIn)
  expect(zoomIn.props.disabled).toBe(true)
  expect(content(root)).toContain('400%')
  const area = all(root, (n) => n.props.role === 'region')[0]!
  const preventDefault = vi.fn()
  ;(area.props.onKeydown as (event: unknown) => void)({ key: 'ArrowRight', preventDefault })
  expect(area.scrollLeft).toBeGreaterThan(0)
  ;(area.props.onPointerdown as (event: unknown) => void)({
    pointerId: 4,
    pointerType: 'mouse',
    button: 0,
    clientX: 100,
    clientY: 100,
    preventDefault,
  })
  ;(area.props.onPointermove as (event: unknown) => void)({
    pointerId: 4,
    clientX: 80,
    clientY: 80,
    preventDefault,
  })
  expect(area.capture).toBe(4)
  expect(area.scrollTop).toBeGreaterThan(0)
  await click(reset)
  expect(area.capture).toBeNull()
  expect(area.scrollLeft).toBe(0)
  expect(area.scrollTop).toBe(0)
  expect(content(root)).toContain('100%')
  expect(reset.props.disabled).toBe(true)
  ;(area.props.onKeydown as (event: unknown) => void)({ key: '+', preventDefault })
  await nextTick()
  expect(content(root)).toContain('125%')
  await click(zoomOut)
  expect(content(root)).toContain('100%')
  expect(all(root, (n) => n.tag === 'a' && n.props.download === 'campus-map.svg')).toHaveLength(1)
  expect(content(root)).not.toContain('Версия')
})
