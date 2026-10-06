import { describe, expect, it } from 'vitest'
import {
  openRequestAttachmentPopup,
  requestAttachmentPopupBlockedMessage,
  requestAttachmentPopupIsolationMessage,
  runRequestAttachmentOpen,
  runRequestAttachmentDownload,
  type RequestAttachmentActionDependencies,
  type RequestAttachmentDownloadDependencies,
  type RequestAttachmentPopup,
  type RequestAttachmentWindow,
} from './request-attachment-action'
import type { RequestAttachmentViewState } from './types'

function deferred<T>(): { promise: Promise<T>; resolve: (value: T) => void } {
  let resolve!: (value: T) => void
  const promise = new Promise<T>((resolvePromise) => { resolve = resolvePromise })
  return { promise, resolve }
}

function actionSetup(overrides: Partial<{
  openPopup: () => RequestAttachmentPopup | null
  download: () => Promise<Blob>
}> = {}): {
  deps: RequestAttachmentActionDependencies
  state: RequestAttachmentViewState
  popup: RequestAttachmentPopup
  setContext: (identity: string | null, generation: number) => void
  calls: { download: number; close: number; navigate: number; released: string[]; errors: unknown[] }
} {
  let identity: string | null = 'owner-1'
  let generation = 0
  const popup: RequestAttachmentPopup = { location: { href: '' }, opener: null }
  const calls = { download: 0, close: 0, navigate: 0, released: [] as string[], errors: [] as unknown[] }
  const state: RequestAttachmentViewState = { status: 'idle', error: null }
  const deps: RequestAttachmentActionDependencies = {
    ownerIdentity: identity,
    ownerGeneration: generation,
    currentOwnerIdentity: () => identity,
    currentOwnerGeneration: () => generation,
    isDisposed: () => false,
    openPopup: overrides.openPopup ?? (() => popup),
    download: async () => {
      calls.download += 1
      return overrides.download ? overrides.download() : new Blob(['attachment'])
    },
    createObjectUrl: () => 'blob:request-1',
    releaseObjectUrl: (url) => calls.released.push(url),
    scheduleRelease: (url) => calls.released.push(url),
    navigate: (target, url) => {
      calls.navigate += 1
      target.location.href = url
    },
    closePopup: () => { calls.close += 1 },
    setState: (next) => Object.assign(state, next),
    onError: (error) => calls.errors.push(error),
    errorMessage: (error) => error instanceof Error ? error.message : 'HTTP ' + String((error as { status?: number }).status ?? 'unknown'),
  }
  return {
    deps,
    state,
    popup,
    setContext: (nextIdentity, nextGeneration) => {
      identity = nextIdentity
      generation = nextGeneration
    },
    calls,
  }
}

async function settlePromiseChain(): Promise<void> {
  for (let index = 0; index < 6; index += 1) await Promise.resolve()
}

describe('request attachment download', () => {
  function downloadSetup(download?: () => Promise<Blob>) {
    const setup = actionSetup({ download })
    const saved: string[] = []
    const deps: RequestAttachmentDownloadDependencies = { ...setup.deps, save: (url) => saved.push(url) }
    return { ...setup, deps, saved }
  }

  it('saves only the authenticated blob and schedules its release', async () => {
    const setup = downloadSetup()
    await runRequestAttachmentDownload(setup.deps)
    expect(setup.saved).toEqual(['blob:request-1'])
    expect(setup.calls.released).toEqual(['blob:request-1'])
    expect(setup.state.status).toBe('idle')
  })

  it.each(['request change', 'context invalidation', 'dispose'] as const)('does not publish a late blob after %s', async (reason) => {
    const response = deferred<Blob>()
    const setup = downloadSetup(() => response.promise)
    let created = 0
    setup.deps.createObjectUrl = () => { created += 1; return 'blob:stale' }
    const operation = runRequestAttachmentDownload(setup.deps)
    if (reason === 'request change') setup.setContext('owner-2', 0)
    else if (reason === 'context invalidation') setup.setContext('owner-1', 1)
    else setup.deps.isDisposed = () => true
    response.resolve(new Blob(['stale']))
    await operation
    expect(created).toBe(0)
    expect(setup.saved).toEqual([])
    expect(setup.calls.errors).toEqual([])
  })

  it('releases an allocated URL when its owner changes during allocation', async () => {
    const setup = downloadSetup()
    setup.deps.createObjectUrl = () => { setup.setContext(null, 1); return 'blob:stale' }
    await runRequestAttachmentDownload(setup.deps)
    expect(setup.saved).toEqual([])
    expect(setup.calls.released).toEqual(['blob:stale'])
  })

  it('releases the URL if the browser cannot start the download', async () => {
    const setup = downloadSetup()
    setup.deps.save = () => { throw new Error('download blocked') }
    await runRequestAttachmentDownload(setup.deps)
    expect(setup.calls.released).toEqual(['blob:request-1'])
    expect(setup.state).toEqual({ status: 'error', error: 'download blocked' })
  })

  it.each([403, 404, 410, 503])('reports HTTP %i without allocating or saving a file', async (status) => {
    const setup = downloadSetup(() => Promise.reject({ status }))
    await runRequestAttachmentDownload(setup.deps)
    expect(setup.saved).toEqual([])
    expect(setup.calls.released).toEqual([])
    expect(setup.state).toEqual({ status: 'error', error: `HTTP ${status}` })
  })

  it('ignores a late denial from an old context', async () => {
    let reject!: (cause: unknown) => void
    const response = new Promise<Blob>((_, rejectPromise) => { reject = rejectPromise })
    const setup = downloadSetup(() => response)
    const operation = runRequestAttachmentDownload(setup.deps)
    setup.setContext(null, 1)
    reject({ status: 403 })
    await operation
    expect(setup.calls.errors).toEqual([])
    expect(setup.state.status).toBe('pending')
  })
})

describe('request attachment action', () => {
  it('acquires a WindowProxy without noopener features and severs opener synchronously', () => {
    let opener: unknown = { owner: 'parent' }
    let setterCalls = 0
    const popup: RequestAttachmentPopup = {
      location: { href: '' },
      get opener() {
        return opener
      },
      set opener(value: unknown) {
        setterCalls += 1
        opener = value
      },
    }
    const openCalls: Array<readonly [string, string, string | undefined]> = []
    const browserWindow: RequestAttachmentWindow = {
      open: (url, target, features) => {
        openCalls.push([url, target, features])
        return popup
      },
    }

    const acquired = openRequestAttachmentPopup(browserWindow)

    expect(acquired).toBe(popup)
    expect(openCalls).toEqual([['', '_blank', undefined]])
    expect(setterCalls).toBe(1)
    expect(opener).toBeNull()
  })

  it('closes and fails closed when opener isolation cannot be applied', () => {
    let closeCalls = 0
    const popup: RequestAttachmentPopup = {
      location: { href: '' },
      get opener() {
        return { owner: 'parent' }
      },
      set opener(_value: unknown) {
        throw new Error('opener setter rejected')
      },
      close: () => { closeCalls += 1 },
    }

    expect(() => openRequestAttachmentPopup({ open: () => popup })).toThrow('opener setter rejected')
    expect(closeCalls).toBe(1)
  })

  it('navigates the synchronously opened popup after the blob arrives', async () => {
    const setup = actionSetup()

    runRequestAttachmentOpen(setup.deps)
    await settlePromiseChain()

    expect(setup.calls.download).toBe(1)
    expect(setup.calls.navigate).toBe(1)
    expect(setup.popup.location.href).toBe('blob:request-1')
    expect(setup.state).toEqual({ status: 'idle', error: null })
    expect(setup.calls.released).toEqual(['blob:request-1'])
  })

  it('shows a retryable inline error for HTTP 410', async () => {
    const setup = actionSetup({ download: async () => { throw { status: 410 } } })

    runRequestAttachmentOpen(setup.deps)
    await settlePromiseChain()

    expect(setup.calls.download).toBe(1)
    expect(setup.calls.close).toBe(1)
    expect(setup.state).toEqual({ status: 'error', error: 'HTTP 410' })
    expect(setup.calls.errors).toHaveLength(1)
  })

  it('shows a retryable inline error for HTTP 5xx', async () => {
    const setup = actionSetup({ download: async () => { throw { status: 503 } } })

    runRequestAttachmentOpen(setup.deps)
    await settlePromiseChain()

    expect(setup.state).toEqual({ status: 'error', error: 'HTTP 503' })
    expect(setup.calls.close).toBe(1)
  })

  it('shows a retryable inline error for a network failure', async () => {
    const setup = actionSetup({ download: async () => { throw new TypeError('Сеть недоступна') } })

    runRequestAttachmentOpen(setup.deps)
    await settlePromiseChain()

    expect(setup.state).toEqual({ status: 'error', error: 'Сеть недоступна' })
    expect(setup.calls.close).toBe(1)
  })

  it('reports a blocked popup before starting the download', () => {
    let downloads = 0
    const setup = actionSetup({
      openPopup: () => null,
      download: async () => {
        downloads += 1
        return new Blob(['attachment'])
      },
    })

    runRequestAttachmentOpen(setup.deps)

    expect(downloads).toBe(0)
    expect(setup.state).toEqual({ status: 'error', error: requestAttachmentPopupBlockedMessage })
  })

  it('surfaces popup isolation failure without downloading or navigating', () => {
    const setup = actionSetup({
      openPopup: () => { throw new Error('opener setter rejected') },
    })

    runRequestAttachmentOpen(setup.deps)

    expect(setup.calls.download).toBe(0)
    expect(setup.calls.navigate).toBe(0)
    expect(setup.state).toEqual({ status: 'error', error: requestAttachmentPopupIsolationMessage })
  })

  it('opens the popup synchronously and fences a late old-owner response', async () => {
    const response = deferred<Blob>()
    const setup = actionSetup({ download: () => response.promise })
    runRequestAttachmentOpen(setup.deps)

    expect(setup.calls.download).toBe(0)
    expect(setup.state.status).toBe('pending')
    await settlePromiseChain()
    expect(setup.calls.download).toBe(1)

    setup.setContext('owner-2', 1)
    response.resolve(new Blob(['old owner']))
    await settlePromiseChain()

    expect(setup.calls.navigate).toBe(0)
    expect(setup.calls.close).toBe(1)
    expect(setup.state.status).toBe('pending')
  })

  it('rejects an old response after the owner returns with the same identity', async () => {
    const response = deferred<Blob>()
    const setup = actionSetup({ download: () => response.promise })
    runRequestAttachmentOpen(setup.deps)
    await settlePromiseChain()

    setup.setContext('owner-2', 1)
    setup.setContext('owner-1', 2)
    response.resolve(new Blob(['old owner generation']))
    await settlePromiseChain()

    expect(setup.calls.navigate).toBe(0)
    expect(setup.calls.close).toBe(1)
    expect(setup.state.status).toBe('pending')
  })

  it('does not create or retain an object URL after teardown while pending', async () => {
    const response = deferred<Blob>()
    const setup = actionSetup({ download: () => response.promise })
    let disposed = false
    setup.deps.isDisposed = () => disposed

    runRequestAttachmentOpen(setup.deps)
    await settlePromiseChain()
    disposed = true
    response.resolve(new Blob(['torn down']))
    await settlePromiseChain()

    expect(setup.calls.navigate).toBe(0)
    expect(setup.calls.close).toBe(1)
    expect(setup.calls.released).toEqual([])
    expect(setup.state.status).toBe('pending')
  })
})
