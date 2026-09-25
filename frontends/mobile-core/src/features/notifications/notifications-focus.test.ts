import { describe, expect, it } from 'vitest'
import { enterNotificationsFocusScope } from './notifications-focus'

type KeyListener = (event: KeyboardEvent) => void

class FakeDocument {
  activeElement: FakeElement | null = null
  private keydown: KeyListener | null = null

  addEventListener(type: string, listener: EventListenerOrEventListenerObject): void {
    if (type === 'keydown' && typeof listener === 'function') this.keydown = listener as KeyListener
  }

  removeEventListener(type: string, listener: EventListenerOrEventListenerObject): void {
    if (type === 'keydown' && this.keydown === listener) this.keydown = null
  }

  dispatchKey(key: string, shiftKey = false): { defaultPrevented: boolean } {
    let defaultPrevented = false
    const event = {
      key,
      shiftKey,
      preventDefault() { defaultPrevented = true },
    } as KeyboardEvent
    this.keydown?.(event)
    if (key === 'Enter' && !defaultPrevented) this.activeElement?.activate()
    return { defaultPrevented }
  }
}

class FakeElement {
  readonly children: FakeElement[] = []
  readonly attributes = new Map<string, string>()
  activations = 0
  isConnected = true
  parent: FakeElement | null = null
  onActivate: (() => void) | null = null

  constructor(
    private readonly ownerDocument: FakeDocument,
    readonly tabbable = false,
  ) {}

  get tabIndex(): number {
    return this.tabbable ? 0 : -1
  }

  append(...children: FakeElement[]): void {
    for (const child of children) {
      child.parent = this
      this.children.push(child)
    }
  }

  contains(target: FakeElement | null): boolean {
    if (!target) return false
    return target === this || this.children.some((child) => child.contains(target))
  }

  closest(selector: string): FakeElement | null {
    if (selector.includes('[inert]') && this.hasAttribute('inert')) return this
    if (selector.includes('[hidden]') && this.hasAttribute('hidden')) return this
    if (selector.includes('[aria-hidden="true"]') && this.getAttribute('aria-hidden') === 'true') return this
    if (selector.includes('fieldset[disabled]') && this.hasAttribute('disabled')) return this
    return this.parent?.closest(selector) ?? null
  }

  focus(): void {
    if (!this.closest('[inert]')) this.ownerDocument.activeElement = this
  }

  activate(): void {
    this.activations += 1
    this.onActivate?.()
  }

  getClientRects(): DOMRectList {
    return (this.tabbable ? [{}] : []) as unknown as DOMRectList
  }

  getAttribute(name: string): string | null {
    return this.attributes.get(name) ?? null
  }

  hasAttribute(name: string): boolean {
    return this.attributes.has(name)
  }

  removeAttribute(name: string): void {
    this.attributes.delete(name)
  }

  setAttribute(name: string, value: string): void {
    this.attributes.set(name, value)
  }

  querySelectorAll<T extends HTMLElement>(selector: string): NodeListOf<T> {
    void selector
    const descendants: FakeElement[] = []
    const collect = (node: FakeElement): void => {
      for (const child of node.children) {
        descendants.push(child)
        collect(child)
      }
    }
    collect(this)
    return descendants.filter((element) => element.tabbable) as unknown as NodeListOf<T>
  }
}

function asHTMLElement(value: FakeElement): HTMLElement {
  return value as unknown as HTMLElement
}

function asDocument(value: FakeDocument): Document {
  return value as unknown as Document
}

describe('notification modal focus scope', () => {
  it('contains Tab and Shift+Tab, activates only the modal control, and restores its trigger', async () => {
    const documentRef = new FakeDocument()
    const appRoot = new FakeElement(documentRef)
    const trigger = new FakeElement(documentRef, true)
    const hiddenRouteAction = new FakeElement(documentRef, true)
    appRoot.append(trigger, hiddenRouteAction)
    trigger.focus()

    const dialog = new FakeElement(documentRef)
    const backButton = new FakeElement(documentRef, true)
    const lastSettingsButton = new FakeElement(documentRef, true)
    dialog.append(backButton, lastSettingsButton)
    let closed = false
    const focusScope = { release: () => {} }
    backButton.onActivate = () => {
      closed = true
      focusScope.release()
    }

    focusScope.release = enterNotificationsFocusScope(
      asHTMLElement(dialog),
      asHTMLElement(appRoot),
      asHTMLElement(backButton),
      asDocument(documentRef),
    )

    expect(appRoot.hasAttribute('inert')).toBe(true)
    expect(documentRef.activeElement).toBe(backButton)
    trigger.focus()
    expect(documentRef.activeElement).toBe(backButton)
    expect(documentRef.dispatchKey('Tab', true).defaultPrevented).toBe(true)
    expect(documentRef.activeElement).toBe(lastSettingsButton)
    expect(documentRef.dispatchKey('Tab').defaultPrevented).toBe(true)
    expect(documentRef.activeElement).toBe(backButton)

    documentRef.dispatchKey('Enter')
    await Promise.resolve()

    expect(closed).toBe(true)
    expect(backButton.activations).toBe(1)
    expect(hiddenRouteAction.activations).toBe(0)
    expect(appRoot.hasAttribute('inert')).toBe(false)
    expect(documentRef.activeElement).toBe(trigger)
  })
})
