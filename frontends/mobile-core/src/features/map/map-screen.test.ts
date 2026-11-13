import { createRenderer, h, markRaw, nextTick, type App } from 'vue'
import { afterEach, expect, it, vi } from 'vitest'
import { CampusMapClient } from '../../api/map-client'
import type { MapFormatSlot } from '../../api/types'
import type { MobileHostAdapter } from '../../shared/host'
import MapScreen from './MapScreen.vue'
vi.mock('./MapSelect.vue', () => ({ default: { setup: () => () => null } }))

class Element {
  children: Element[] = []
  parent: Element | null = null
  props: Record<string, unknown> = {}
  style = { display: '' }
  text = ''
  scrollLeft = 0
  scrollTop = 0
  clientWidth = 240
  clientHeight = 240
  naturalWidth = 240
  naturalHeight = 240
  captures = new Set<number>()
  open = false
  focused = false
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
  getBoundingClientRect() {
    return { left: 30, top: 70 }
  }
  hasPointerCapture(id: number) {
    return this.captures.has(id)
  }
  setPointerCapture(id: number) {
    this.captures.add(id)
  }
  releasePointerCapture(id: number) {
    this.captures.delete(id)
  }
  showModal() {
    this.open = true
  }
  close() {
    this.open = false
  }
  focus() {
    this.focused = true
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

async function mountMap(student: boolean, host?: MobileHostAdapter) {
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
    render: () =>
      h(MapScreen, {
        client,
        studentPresentation: student,
        compactPresentation: !student,
        ...(host ? { host } : {}),
      }),
  })
  app.mount(root)
  await flush()
  const button = (label: string) =>
    all(root, (n) => n.tag === 'button' && n.props['aria-label'] === label)[0]!
  const ready = async () => {
    const image = all(root, (n) => n.tag === 'img')[0]!
    ;(image.props.onLoad as (event: unknown) => void)({ currentTarget: image })
    await flush()
  }
  return { root, button, ready, recordFloorOpen }
}

it.each([true, false])('keeps the loaded map usable (student: %s)', async (student) => {
  const { root, button, ready, recordFloorOpen } = await mountMap(student)
  const zoomIn = button('Увеличить масштаб схемы'),
    zoomOut = button('Уменьшить масштаб схемы'),
    reset = button('Сбросить масштаб схемы')
  expect(reset).toBeDefined()
  expect(zoomIn.props.disabled).toBe(true)
  await ready()
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
  expect(area.hasPointerCapture(4)).toBe(true)
  expect(area.scrollTop).toBeGreaterThan(0)
  await click(reset)
  expect(area.captures.size).toBe(0)
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

it('pins a two-finger zoom to the map and continues panning after lift or cancel', async () => {
  const release = vi.fn()
  const suspend = vi.fn(() => release)
  const { root, ready } = await mountMap(true, {
    backOwner: 'host',
    suspendVerticalSwipes: suspend,
  })
  await ready()
  const area = all(root, (n) => n.props.role === 'region')[0]!
  const pointer = (handler: string, id: number, x = 0, y = 0) =>
    (area.props[handler] as (event: unknown) => void)({
      pointerId: id,
      pointerType: 'touch',
      button: 0,
      clientX: x + 30,
      clientY: y + 70,
      preventDefault: vi.fn(),
    })

  pointer('onPointerdown', 1, 60, 70)
  pointer('onPointerdown', 2, 100, 70)
  expect(area.captures).toEqual(new Set([1, 2]))
  pointer('onPointermove', 1, 40, 70)
  pointer('onPointermove', 2, 120, 70)
  await nextTick()
  expect(content(root)).toContain('200%')
  expect(area.scrollLeft).toBeCloseTo(80)
  expect(area.scrollTop).toBeCloseTo(70)
  expect(suspend).toHaveBeenCalledOnce()

  pointer('onPointermove', 1, -200, 70)
  await nextTick()
  expect(content(root)).toContain('400%')
  pointer('onPointermove', 1, 110, 70)
  await nextTick()
  expect(content(root)).toContain('100%')
  expect(area.scrollLeft).toBe(0)
  expect(area.scrollTop).toBe(0)

  pointer('onPointermove', 1, 100, 70)
  await nextTick()
  expect(content(root)).toContain('200%')
  const left = area.scrollLeft
  const top = area.scrollTop
  pointer('onPointerup', 1)
  expect(area.captures).toEqual(new Set([2]))
  expect(release).not.toHaveBeenCalled()
  pointer('onPointermove', 2, 95, 60)
  expect(area.scrollLeft).toBeCloseTo(left + 25)
  expect(area.scrollTop).toBeCloseTo(top + 10)

  pointer('onPointerdown', 3, 120, 60)
  pointer('onPointercancel', 2)
  pointer('onLostpointercapture', 2)
  expect(area.captures).toEqual(new Set([3]))
  const afterCancel = area.scrollLeft
  pointer('onPointermove', 3, 110, 60)
  expect(area.scrollLeft).toBeCloseTo(afterCancel + 10)
  pointer('onLostpointercapture', 3)
  expect(area.captures.size).toBe(0)
  expect(release).toHaveBeenCalledOnce()
})

it('reuses the zoomed plan and yields Telegram Back when the fullscreen view closes', async () => {
  const releaseBack = vi.fn()
  const releaseSwipes = vi.fn()
  const claimBack = vi.fn<(callback: () => void) => () => void>().mockReturnValue(releaseBack)
  const { root, button, ready, recordFloorOpen } = await mountMap(true, {
    backOwner: 'host',
    claimBack,
    suspendVerticalSwipes: () => releaseSwipes,
  })
  await ready()
  const modal = all(root, (n) => n.tag === 'dialog')[0]!
  const area = all(root, (n) => n.props.role === 'region')[0]!
  const expand = button('Развернуть схему на весь экран')
  const click = (node: Element) => (node.props.onClick as () => unknown)()
  click(button('Увеличить масштаб схемы'))
  await flush()
  const originalCenter = area.scrollLeft
  await click(expand)
  expect(modal.open).toBe(true)
  expect(all(modal, (n) => n.props.role === 'region')).toEqual([area])
  expect(content(modal)).toContain('125%')
  expect(area.scrollLeft).toBe(originalCenter)
  expect(recordFloorOpen).toHaveBeenCalledOnce()
  expect(claimBack).toHaveBeenCalledOnce()

  claimBack.mock.calls[0]![0]()
  await flush()
  expect(modal.open).toBe(false)
  expect(expand.focused).toBe(true)
  expect(all(modal, (n) => n.props.role === 'region')).toHaveLength(0)
  expect(all(root, (n) => n.props.role === 'region')).toEqual([area])
  expect(content(root)).toContain('125%')
  expect(releaseBack).toHaveBeenCalledTimes(1)
  expect(releaseSwipes).toHaveBeenCalledTimes(1)

  await click(expand)
  click(button('Вернуться к карте'))
  await flush()
  expect(modal.open).toBe(false)
  expect(releaseBack).toHaveBeenCalledTimes(2)
  expect(releaseSwipes).toHaveBeenCalledTimes(2)

  await click(expand)
  ;(modal.props.onCancel as (event: unknown) => void)({ preventDefault: vi.fn() })
  await flush()
  expect(modal.open).toBe(false)
  expect(releaseBack).toHaveBeenCalledTimes(3)
  expect(releaseSwipes).toHaveBeenCalledTimes(3)

  await click(expand)
  app?.unmount()
  app = undefined
  expect(releaseBack).toHaveBeenCalledTimes(4)
  expect(releaseSwipes).toHaveBeenCalledTimes(4)
})
