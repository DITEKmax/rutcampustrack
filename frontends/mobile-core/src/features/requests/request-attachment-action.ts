import type { RequestAttachmentViewState } from './types'

/** The minimal popup surface keeps the action testable without a DOM. */
export interface RequestAttachmentPopup {
  location: { href: string }
  opener: unknown
  close?: () => void
}

export interface RequestAttachmentWindow {
  open: (url: string, target: string, features?: string) => unknown
}

/**
 * Acquire a real WindowProxy while retaining the user-click gesture, then
 * sever its opener synchronously. Passing `noopener` in the feature string
 * can make window.open return null even when the target was created; a null
 * result is therefore handled as blocked, while a failed isolation setter is
 * fail-closed before any async download starts.
 */
export function openRequestAttachmentPopup(browserWindow: RequestAttachmentWindow): RequestAttachmentPopup | null {
  const popup = browserWindow.open('', '_blank') as RequestAttachmentPopup | null
  if (!popup) return null
  try {
    popup.opener = null
    if (popup.opener !== null) throw new Error('Не удалось безопасно изолировать окно вложения.')
    return popup
  } catch (error) {
    try {
      popup.close?.()
    } catch {
      // The browser may close the target between acquisition and isolation.
    }
    throw error
  }
}

export interface RequestAttachmentActionDependencies {
  ownerIdentity: string | null
  ownerGeneration: number
  currentOwnerIdentity: () => string | null
  currentOwnerGeneration: () => number
  isDisposed: () => boolean
  openPopup: () => RequestAttachmentPopup | null
  download: () => Promise<Blob>
  createObjectUrl: (blob: Blob) => string
  releaseObjectUrl: (url: string) => void
  scheduleRelease: (url: string) => void
  navigate: (popup: RequestAttachmentPopup, url: string) => void
  closePopup: (popup: RequestAttachmentPopup) => void
  onPopupOpened?: (popup: RequestAttachmentPopup) => void
  onPopupNavigated?: (popup: RequestAttachmentPopup) => void
  setState: (state: RequestAttachmentViewState) => void
  onError?: (error: unknown) => void
  errorMessage: (error: unknown) => string
}

export const requestAttachmentPopupBlockedMessage = 'Не удалось открыть вложение: разреши всплывающие окна и повтори.'
export const requestAttachmentPopupIsolationMessage = 'Не удалось безопасно открыть вложение. Повтори попытку.'

const idleState = (): RequestAttachmentViewState => ({ status: 'idle', error: null })
const pendingState = (): RequestAttachmentViewState => ({ status: 'pending', error: null })
const errorState = (message: string): RequestAttachmentViewState => ({ status: 'error', error: message })

/**
 * Opens the blank target during the click event, then fills it from the
 * authenticated blob response. The owner supplies identity/generation and
 * teardown fences so a late response cannot publish or keep an old popup alive.
 */
export function runRequestAttachmentOpen(deps: RequestAttachmentActionDependencies): void {
  if (!deps.ownerIdentity
    || deps.isDisposed()
    || deps.ownerIdentity !== deps.currentOwnerIdentity()
    || deps.ownerGeneration !== deps.currentOwnerGeneration()) return

  let popup: RequestAttachmentPopup | null = null
  let objectUrl: string | null = null
  let navigated = false
  const isCurrent = (): boolean => !deps.isDisposed()
    && deps.ownerIdentity === deps.currentOwnerIdentity()
    && deps.ownerGeneration === deps.currentOwnerGeneration()
  const close = (): void => {
    if (!popup || navigated) return
    deps.closePopup(popup)
    popup = null
  }

  try {
    // This call must remain synchronous with the user's click to avoid popup
    // blockers. The async download starts only after the blank target exists.
    popup = deps.openPopup()
  } catch (error) {
    deps.onError?.(error)
    deps.setState(errorState(requestAttachmentPopupIsolationMessage))
    return
  }
  if (!popup) {
    deps.setState(errorState(requestAttachmentPopupBlockedMessage))
    return
  }

  deps.onPopupOpened?.(popup)
  deps.setState(pendingState())
  void Promise.resolve()
    .then(() => {
      if (!isCurrent()) {
        close()
        return Promise.reject(new Error('Сессия заявки больше не актуальна.'))
      }
      return deps.download()
    })
    .then((blob) => {
      if (!isCurrent()) {
        close()
        return
      }
      objectUrl = deps.createObjectUrl(blob)
      if (!isCurrent()) {
        deps.releaseObjectUrl(objectUrl)
        objectUrl = null
        close()
        return
      }
      deps.navigate(popup!, objectUrl)
      navigated = true
      deps.onPopupNavigated?.(popup!)
      deps.scheduleRelease(objectUrl)
      objectUrl = null
      deps.setState(idleState())
    })
    .catch((error: unknown) => {
      if (objectUrl) {
        deps.releaseObjectUrl(objectUrl)
        objectUrl = null
      }
      close()
      if (!isCurrent()) return
      deps.onError?.(error)
      deps.setState(errorState(deps.errorMessage(error)))
    })
}

export type RequestAttachmentDownloadDependencies = Pick<RequestAttachmentActionDependencies,
  'ownerIdentity' | 'ownerGeneration' | 'currentOwnerIdentity' | 'currentOwnerGeneration'
  | 'isDisposed' | 'download' | 'createObjectUrl' | 'releaseObjectUrl' | 'scheduleRelease'
  | 'setState' | 'onError' | 'errorMessage'> & {
  save: (url: string) => void
}

/** The authenticated response may only become a download while its request is current. */
export async function runRequestAttachmentDownload(deps: RequestAttachmentDownloadDependencies): Promise<void> {
  const isCurrent = (): boolean => Boolean(deps.ownerIdentity) && !deps.isDisposed()
    && deps.ownerIdentity === deps.currentOwnerIdentity()
    && deps.ownerGeneration === deps.currentOwnerGeneration()
  if (!isCurrent()) return
  deps.setState(pendingState())
  let objectUrl: string | null = null
  try {
    const blob = await deps.download()
    if (!isCurrent()) return
    objectUrl = deps.createObjectUrl(blob)
    if (!isCurrent()) return
    deps.save(objectUrl)
    deps.scheduleRelease(objectUrl)
    objectUrl = null
    deps.setState(idleState())
  } catch (error) {
    if (!isCurrent()) return
    deps.onError?.(error)
    deps.setState(errorState(deps.errorMessage(error)))
  } finally {
    if (objectUrl) deps.releaseObjectUrl(objectUrl)
  }
}
