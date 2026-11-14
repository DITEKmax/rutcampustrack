import { onBeforeUnmount, onMounted } from 'vue'

const swatchSelector = '.design-color-palette__swatch'
const holdDuration = 400
const touchSlop = 8

type Point = { clientX: number; clientY: number }
type Box = { x: number; y: number; width: number; height: number }
type Swatch = Box & { element: HTMLElement }
type Drag = {
  board: HTMLElement
  selected: Swatch
  neighbors: Swatch[]
  width: number
  height: number
  startX: number
  startY: number
  initialX: number
  initialY: number
}
type TouchHold = {
  identifier: number
  start: Point
  timer: number
}

function swatchAt(event: Event) {
  return event.target instanceof Element ? event.target.closest<HTMLElement>(swatchSelector) : null
}

function clampOffset(offset: number, start: number, size: number, limit: number) {
  return Math.max(-start, Math.min(offset, limit - start - size))
}

function setOffset(element: HTMLElement, x: number, y: number) {
  element.style.setProperty('--swatch-shift-x', x + 'px')
  element.style.setProperty('--swatch-shift-y', y + 'px')
}

function clearOffset(element: HTMLElement) {
  element.style.removeProperty('--swatch-shift-x')
  element.style.removeProperty('--swatch-shift-y')
}

function moveAside(neighbor: Swatch, moving: Box, drag: Drag) {
  const centerX = neighbor.x + neighbor.width / 2 - (moving.x + moving.width / 2)
  const centerY = neighbor.y + neighbor.height / 2 - (moving.y + moving.height / 2)
  const gap = Math.min(moving.width, neighbor.width) * 0.16
  const overlapX = (moving.width + neighbor.width) / 2 + gap - Math.abs(centerX)
  const overlapY = (moving.height + neighbor.height) / 2 + gap - Math.abs(centerY)

  if (overlapX <= 0 || overlapY <= 0) {
    clearOffset(neighbor.element)
    return
  }

  const x = overlapX < overlapY ? Math.min(overlapX, neighbor.width * 0.6) * Math.sign(centerX || 1) : 0
  const y = overlapY <= overlapX ? Math.min(overlapY, neighbor.height * 0.6) * Math.sign(centerY || 1) : 0
  setOffset(
    neighbor.element,
    clampOffset(x, neighbor.x, neighbor.width, drag.width),
    clampOffset(y, neighbor.y, neighbor.height, drag.height),
  )
}

export function usePaletteDrag() {
  let drag: Drag | undefined
  let pointerId: number | undefined
  let touchHold: TouchHold | undefined
  let frame = 0
  let listeners: AbortController | undefined

  function begin(board: HTMLElement, element: HTMLElement, start: Point) {
    // Offset geometry stays anchored to the layout even during the previous return transition.
    const items = [...board.querySelectorAll<HTMLElement>(swatchSelector)].map(swatch => ({
      element: swatch,
      x: swatch.offsetLeft,
      y: swatch.offsetTop,
      width: swatch.offsetWidth,
      height: swatch.offsetHeight,
    }))
    const selected = items.find(item => item.element === element)
    if (!selected) return

    const boardRect = board.getBoundingClientRect()
    const selectedRect = element.getBoundingClientRect()
    const initialX = selectedRect.left - boardRect.left - selected.x
    const initialY = selectedRect.top - boardRect.top - selected.y
    drag = {
      board,
      selected,
      neighbors: items.filter(item => item !== selected),
      width: board.clientWidth,
      height: board.clientHeight,
      startX: start.clientX,
      startY: start.clientY,
      initialX,
      initialY,
    }
    element.setAttribute('data-dragging', '')
    setOffset(element, initialX, initialY)
  }

  function move(point: Point) {
    cancelAnimationFrame(frame)
    frame = requestAnimationFrame(() => {
      if (!drag) return
      const { selected, neighbors, width, height, startX, startY, initialX, initialY } = drag
      const x = clampOffset(initialX + point.clientX - startX, selected.x, selected.width, width)
      const y = clampOffset(initialY + point.clientY - startY, selected.y, selected.height, height)
      setOffset(selected.element, x, y)
      const moving = { ...selected, x: selected.x + x, y: selected.y + y }
      for (const neighbor of neighbors) moveAside(neighbor, moving, drag)
    })
  }

  function finish() {
    if (touchHold) window.clearTimeout(touchHold.timer)
    touchHold = undefined
    cancelAnimationFrame(frame)

    const current = drag
    const captured = pointerId
    drag = undefined
    pointerId = undefined
    if (!current) return

    current.selected.element.removeAttribute('data-dragging')
    clearOffset(current.selected.element)
    for (const neighbor of current.neighbors) clearOffset(neighbor.element)
    if (captured !== undefined && current.board.hasPointerCapture(captured)) {
      current.board.releasePointerCapture(captured)
    }
  }

  function onPointerDown(event: PointerEvent) {
    if (event.pointerType !== 'mouse' || event.button !== 0 || !event.isPrimary || drag || touchHold) return
    const element = swatchAt(event)
    if (!element || !(event.currentTarget instanceof HTMLElement)) return

    event.preventDefault()
    event.currentTarget.setPointerCapture(event.pointerId)
    pointerId = event.pointerId
    begin(event.currentTarget, element, event)
  }

  function onPointerMove(event: PointerEvent) {
    if (event.pointerId !== pointerId) return
    if (event.buttons & 1) move(event)
    else finish()
  }

  function onPointerEnd(event: PointerEvent) {
    if (event.pointerId === pointerId) finish()
  }

  function onTouchStart(event: TouchEvent) {
    if (drag || touchHold || event.touches.length !== 1) return
    const element = swatchAt(event)
    const board = event.currentTarget
    const touch = event.changedTouches[0]
    if (!touch || !element || !(board instanceof HTMLElement)) return

    const start = { clientX: touch.clientX, clientY: touch.clientY }
    const timer = window.setTimeout(() => begin(board, element, start), holdDuration)
    touchHold = { identifier: touch.identifier, start, timer }
  }

  function onTouchMove(event: TouchEvent) {
    if (!touchHold) return
    const touch = event.touches[0]
    if (event.touches.length !== 1 || touch?.identifier !== touchHold.identifier) {
      finish()
      return
    }

    if (drag) {
      if (!event.cancelable) {
        finish()
        return
      }
      event.preventDefault()
      move(touch)
    } else if (Math.hypot(touch.clientX - touchHold.start.clientX, touch.clientY - touchHold.start.clientY) > touchSlop) {
      finish()
    }
  }

  function onTouchEnd(event: TouchEvent) {
    if (!touchHold) return
    if (drag && event.cancelable) event.preventDefault()
    finish()
  }

  function onContextMenu(event: MouseEvent) {
    if (touchHold || (event instanceof PointerEvent && event.pointerType === 'touch' && swatchAt(event))) {
      event.preventDefault()
    }
  }

  onMounted(() => {
    listeners = new AbortController()
    const options = { signal: listeners.signal }
    window.addEventListener('blur', finish, options)
    window.addEventListener('pagehide', finish, options)
    window.addEventListener('resize', finish, options)
    window.addEventListener('scroll', finish, options)
    window.addEventListener('keydown', event => {
      if (event.key === 'Escape') finish()
    }, options)
    window.addEventListener('touchstart', event => {
      if (event.touches.length > 1 && touchHold) finish()
    }, { ...options, passive: true })
    document.addEventListener('visibilitychange', () => {
      if (document.hidden) finish()
    }, options)
  })

  onBeforeUnmount(() => {
    finish()
    listeners?.abort()
  })

  return { onPointerDown, onPointerMove, onPointerEnd, onTouchStart, onTouchMove, onTouchEnd, finish, onContextMenu }
}
