import { describe, expect, it, vi } from 'vitest'
import { HeadmanGroupApiError } from './headman-group-client'
import { HeadmanHomeworkApiError } from '../homework/headman-homework-client'
import {
  createAssistantPermissionRefresher,
  isAssistantCapabilityForbiddenError,
  isAssistantPermissionReadForbidden,
} from './assistant-permission-refresh'
import type { HeadmanAssistantPermission } from './headman-group-client'

function deferred<T>() {
  let resolve!: (value: T) => void
  let reject!: (cause: unknown) => void
  const promise = new Promise<T>((accept, fail) => { resolve = accept; reject = fail })
  return { promise, resolve, reject }
}

describe('assistant permission refresh ownership', () => {
  it('coalesces requests and ignores a late result after the owner changes', async () => {
    let generation = 4
    let ownerKey: string | null = 'student-a:group-1'
    const oldResponse = deferred<readonly HeadmanAssistantPermission[]>()
    const newResponse = deferred<readonly HeadmanAssistantPermission[]>()
    const load = vi.fn()
      .mockReturnValueOnce(oldResponse.promise)
      .mockReturnValueOnce(newResponse.promise)
      .mockResolvedValue(['VIEW_STATS'])
    const apply = vi.fn()
    const refresher = createAssistantPermissionRefresher({
      currentGeneration: () => generation,
      currentOwnerKey: () => ownerKey,
      load,
      apply,
    })

    const oldRefresh = refresher.refresh('forbidden')
    const coalesced = refresher.refresh('foreground')
    await Promise.resolve()
    expect(load).toHaveBeenCalledTimes(1)

    ownerKey = 'student-b:group-1'
    const newRefresh = refresher.refresh('forbidden')
    await Promise.resolve()
    expect(load).toHaveBeenCalledTimes(2)

    oldResponse.resolve(['MANAGE_HOMEWORK'])
    expect(await oldRefresh).toBe(false)
    expect(await coalesced).toBe(false)
    expect(apply).not.toHaveBeenCalled()

    newResponse.resolve(['VIEW_STATS'])
    expect(await newRefresh).toBe(true)
    expect(apply).toHaveBeenCalledExactlyOnceWith(['VIEW_STATS'])

    generation += 1
    expect(await refresher.refresh('forbidden')).toBe(true)
  })

  it('limits foreground refreshes while allowing a forbidden response to refresh immediately', async () => {
    let currentTime = 10_000
    const load = vi.fn<() => Promise<readonly HeadmanAssistantPermission[]>>().mockResolvedValue(['VIEW_STATS'])
    const refresher = createAssistantPermissionRefresher({
      currentGeneration: () => 1,
      currentOwnerKey: () => 'student-a:group-1',
      load: async () => load(),
      apply: () => undefined,
      now: () => currentTime,
      foregroundCooldownMs: 30_000,
      forbiddenCooldownMs: 1_000,
    })

    expect(await refresher.refresh('foreground')).toBe(true)
    expect(await refresher.refresh('foreground')).toBe(false)
    expect(load).toHaveBeenCalledTimes(1)
    expect(await refresher.refresh('forbidden')).toBe(true)
    expect(load).toHaveBeenCalledTimes(2)

    currentTime += 30_000
    expect(await refresher.refresh('foreground')).toBe(true)
    expect(load).toHaveBeenCalledTimes(3)
  })

  it('ignores a late forbidden refresh error after the owner changes', async () => {
    let ownerKey: string | null = 'student-a:group-1'
    const response = deferred<readonly HeadmanAssistantPermission[]>()
    const apply = vi.fn()
    const refresher = createAssistantPermissionRefresher({
      currentGeneration: () => 1,
      currentOwnerKey: () => ownerKey,
      load: async () => response.promise,
      apply,
    })

    const pending = refresher.refresh('forbidden')
    await Promise.resolve()
    ownerKey = 'student-b:group-1'
    response.reject(new HeadmanHomeworkApiError(new Response(null, { status: 401 }), null))

    await expect(pending).resolves.toBe(false)
    expect(apply).not.toHaveBeenCalled()
  })

  it('treats homework 403 as capability denial and permission-list 403 as operational', () => {
    const response = new Response(null, { status: 403 })
    expect(isAssistantCapabilityForbiddenError(new HeadmanHomeworkApiError(response, null))).toBe(true)
    expect(isAssistantPermissionReadForbidden(new HeadmanGroupApiError(response, null))).toBe(true)
    expect(isAssistantCapabilityForbiddenError(new Error('HTTP 403'))).toBe(false)
  })
})
