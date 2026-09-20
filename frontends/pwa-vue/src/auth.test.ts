import { describe, expect, it, vi } from 'vitest'
import { usePwaAuth } from './auth'

describe('PWA memory session', () => {
  it('uses one refresh request for concurrent bootstrap callers and retains only the access token in memory', async () => {
    const fetcher = vi.fn().mockResolvedValue(new Response(JSON.stringify({ accessToken: 'memory-only-token' }), { status: 200 }))
    vi.stubGlobal('fetch', fetcher)
    const auth = usePwaAuth()
    await Promise.all([auth.refresh(), auth.refresh()])
    expect(fetcher).toHaveBeenCalledTimes(1)
    expect(auth.accessToken.value).toBe('memory-only-token')
    vi.unstubAllGlobals()
  })

  it('clears the user snapshot after an explicit logout even when the remote endpoint is unavailable', async () => {
    vi.stubGlobal('fetch', vi.fn().mockRejectedValue(new Error('offline')))
    const clearSnapshot = vi.fn().mockResolvedValue(undefined)
    const auth = usePwaAuth()
    await expect(auth.logout(clearSnapshot)).resolves.toBeUndefined()
    expect(auth.accessToken.value).toBeNull()
    expect(clearSnapshot).toHaveBeenCalledOnce()
    vi.unstubAllGlobals()
  })
})
