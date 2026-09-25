const FOCUSABLE_SELECTOR = [
  'a[href]',
  'button:not([disabled])',
  'input:not([disabled]):not([type="hidden"])',
  'select:not([disabled])',
  'textarea:not([disabled])',
  '[tabindex]:not([tabindex="-1"])',
].join(',')

function focusableElements(dialog: HTMLElement, documentRef: Document): HTMLElement[] {
  return Array.from(dialog.querySelectorAll<HTMLElement>(FOCUSABLE_SELECTOR)).filter((element) => {
    if (element.tabIndex < 0 || element.hasAttribute('disabled')) return false
    if (element.closest('[hidden], [inert], [aria-hidden="true"], fieldset[disabled]')) return false
    if (element.getClientRects().length === 0) return false
    const style = documentRef.defaultView?.getComputedStyle(element)
    return style?.display !== 'none' && style?.visibility !== 'hidden'
  })
}

export function enterNotificationsFocusScope(
  dialog: HTMLElement,
  appRoot: HTMLElement | null,
  initialFocus: HTMLElement | null,
  documentRef: Document = document,
  returnFocus: HTMLElement | null = documentRef.activeElement as HTMLElement | null,
): () => void {
  const priorInertValue = appRoot?.getAttribute('inert') ?? null
  appRoot?.setAttribute('inert', '')

  const onKeydown = (event: KeyboardEvent): void => {
    if (event.key !== 'Tab') return

    const focusable = focusableElements(dialog, documentRef)
    if (focusable.length === 0) {
      event.preventDefault()
      dialog.focus()
      return
    }

    const first = focusable[0]!
    const last = focusable[focusable.length - 1]!
    const active = documentRef.activeElement
    if (!dialog.contains(active)) {
      event.preventDefault()
      ;(event.shiftKey ? last : first).focus()
    } else if (event.shiftKey && (active === first || active === dialog)) {
      event.preventDefault()
      last.focus()
    } else if (!event.shiftKey && active === last) {
      event.preventDefault()
      first.focus()
    }
  }

  documentRef.addEventListener('keydown', onKeydown, true)
  ;(initialFocus && dialog.contains(initialFocus) ? initialFocus : dialog).focus()

  let released = false
  return () => {
    if (released) return
    released = true
    documentRef.removeEventListener('keydown', onKeydown, true)
    if (appRoot) {
      if (priorInertValue === null) appRoot.removeAttribute('inert')
      else appRoot.setAttribute('inert', priorInertValue)
    }

    queueMicrotask(() => {
      if (!returnFocus?.isConnected || returnFocus.closest('[hidden], [inert], [aria-hidden="true"]')) return
      returnFocus.focus()
    })
  }
}
