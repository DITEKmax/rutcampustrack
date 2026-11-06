interface Point {
  x: number
  y: number
}
interface Anchor {
  lessonX: number | null
  y: number
}

const MIN_SCALE = 0.75
const MAX_SCALE = 1.75
const SCALE_PROPERTY = '--teacher-journal-scale'

function between(first: Touch, second: Touch): { center: Point; distance: number } {
  return {
    center: { x: (first.clientX + second.clientX) / 2, y: (first.clientY + second.clientY) / 2 },
    distance: Math.hypot(first.clientX - second.clientX, first.clientY - second.clientY),
  }
}

/** CSS zoom reflows the table, so the native scroll range and sticky name column follow it. */
export function createTeacherJournalZoom(
  getViewport: () => HTMLElement | null,
  getTable: () => HTMLTableElement | null,
  getNameHeading: () => HTMLTableCellElement | null,
) {
  let scale = 1
  let pinch: { ids: [number, number]; distance: number; scale: number; anchor: Anchor } | null =
    null
  let suppressTap = false
  let suppressUntil = 0

  function anchorAt(center: Point): Anchor | undefined {
    const rect = getTable()?.getBoundingClientRect()
    if (!rect?.width) return
    const name = getNameHeading()?.getBoundingClientRect()
    return {
      // The sticky name column may narrow while zooming on a small screen.
      lessonX:
        name && center.x < name.right ? null : (center.x - rect.left - (name?.width ?? 0)) / scale,
      y: (center.y - rect.top) / scale,
    }
  }

  function position(nextScale: number, anchor: Anchor, center: Point): void {
    const viewport = getViewport()
    const table = getTable()
    if (!viewport || !table) return
    const bounded = Math.min(MAX_SCALE, Math.max(MIN_SCALE, nextScale))
    if (bounded !== scale) {
      scale = bounded
      viewport.style.setProperty(SCALE_PROPERTY, String(scale))
    }
    const rect = table.getBoundingClientRect()
    if (anchor.lessonX !== null) {
      const nameWidth = getNameHeading()?.getBoundingClientRect().width ?? 0
      viewport.scrollLeft += rect.left + nameWidth + anchor.lessonX * scale - center.x
    }
    // The table is naturally as tall as its rows; vertical scrolling belongs to the page.
    const top = rect.top + anchor.y * scale - center.y
    if (Math.abs(top) > 0.5)
      viewport.ownerDocument.defaultView?.scrollBy({ top, behavior: 'instant' })
  }

  function touchStart(event: TouchEvent): void {
    const viewport = getViewport()
    if (!viewport) return
    const contacts = Array.from(event.touches)
    if (contacts.length === 1) {
      pinch = null
      suppressTap = false
      suppressUntil = 0
    }
    if (suppressTap && event.cancelable) event.preventDefault()
    const [first, second] = contacts
    if (
      contacts.length !== 2 ||
      !first ||
      !second ||
      !viewport.contains(first.target as Node) ||
      !viewport.contains(second.target as Node)
    )
      return
    const { center, distance } = between(first, second)
    const anchor = anchorAt(center)
    if (!distance || !anchor || !event.cancelable) return
    pinch = { ids: [first.identifier, second.identifier], distance, scale, anchor }
    suppressTap = true
    event.preventDefault()
  }

  function touchMove(event: TouchEvent): void {
    if (!suppressTap) return
    if (!event.cancelable) {
      pinch = null
      return
    }
    event.preventDefault()
    if (!pinch || event.touches.length !== 2) return
    const contacts = Array.from(event.touches)
    const [firstId, secondId] = pinch.ids
    const first = contacts.find((contact) => contact.identifier === firstId)
    const second = contacts.find((contact) => contact.identifier === secondId)
    if (!first || !second) return
    const { center, distance } = between(first, second)
    position(pinch.scale * (distance / pinch.distance), pinch.anchor, center)
  }

  function touchEnd(event: TouchEvent): void {
    if (!suppressTap) return
    if (event.cancelable) event.preventDefault()
    if (event.touches.length < 2 || event.type === 'touchcancel') pinch = null
    suppressTap = event.type !== 'touchcancel' && event.touches.length > 0
    suppressUntil = event.timeStamp + 350
  }

  function captureClick(event: MouseEvent): void {
    const pointer = (event as PointerEvent).pointerType
    if (event.detail === 0 || pointer === 'mouse' || pointer === 'pen') return
    if (suppressTap || event.timeStamp <= suppressUntil) {
      event.preventDefault()
      event.stopPropagation()
    }
  }

  function keydown(event: KeyboardEvent): void {
    const viewport = getViewport()
    if (
      !viewport ||
      event.target !== viewport ||
      event.ctrlKey ||
      event.metaKey ||
      event.altKey ||
      !['+', '=', '-', '−', '0'].includes(event.key)
    )
      return
    event.preventDefault()
    const rect = viewport.getBoundingClientRect()
    const center = {
      x: rect.left + rect.width / 2,
      y:
        (Math.max(0, rect.top) +
          Math.min(viewport.ownerDocument.defaultView?.innerHeight ?? rect.bottom, rect.bottom)) /
        2,
    }
    const anchor = anchorAt(center)
    if (!anchor) return
    const nextScale =
      event.key === '0' ? 1 : scale + (event.key === '+' || event.key === '=' ? 0.25 : -0.25)
    position(nextScale, anchor, center)
  }

  function reset(): void {
    scale = 1
    pinch = null
    suppressTap = false
    suppressUntil = 0
    const viewport = getViewport()
    if (viewport) {
      viewport.style.removeProperty(SCALE_PROPERTY)
      viewport.scrollLeft = 0
    }
  }

  return { touchStart, touchMove, touchEnd, captureClick, keydown, reset }
}
