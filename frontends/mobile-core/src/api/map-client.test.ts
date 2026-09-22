import { describe, expect, it, vi } from 'vitest'
import { StaleSessionGenerationError } from '../shared/session-owner'
import { CampusMapClient } from './map-client'

describe('CampusMapClient generation boundary', () => {
  it('rejects an asset response that crosses the authenticated owner generation', async () => {
    let generation = 4
    let releaseResponse: ((response: Response) => void) | undefined
    const fetcher = vi.fn(() => new Promise<Response>((resolve) => {
      releaseResponse = resolve
    }))
    const client = new CampusMapClient({
      accessToken: () => 'token-a',
      currentGeneration: () => generation,
      fetcher,
    })

    const pending = client.downloadAsset('20', '200', '1', 'svg', 'asset-1')
    generation = 5
    releaseResponse?.(new Response(new Blob(['<svg />']), {
      status: 200,
      headers: { 'Content-Type': 'image/svg+xml' },
    }))

    await expect(pending).rejects.toBeInstanceOf(StaleSessionGenerationError)
    expect(fetcher).toHaveBeenCalledTimes(1)
  })
})
