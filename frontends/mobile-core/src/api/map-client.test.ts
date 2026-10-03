import { describe, expect, it, vi } from 'vitest'
import { StaleSessionGenerationError } from '../shared/session-owner'
import { AdminMapClient, CampusMapClient, MapApiError } from './map-client'

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

describe('AdminMapClient destructive operation boundary', () => {
  it('sends deletion once to the captured ID and does not refresh or replay a 401', async () => {
    const fetcher = vi.fn<typeof fetch>(async () => new Response('{}', { status: 401 }))
    const onUnauthorized = vi.fn(async () => {})
    const client = new AdminMapClient({ accessToken: () => 'token-a', currentGeneration: () => 1, fetcher, onUnauthorized })
    const payload = { operationId: 'operation-a', previewDigest: 'a'.repeat(64), password: 'test-password' }
    await expect(client.deleteInventory('FLOOR', '200', payload)).rejects.toBeInstanceOf(MapApiError)
    expect(fetcher).toHaveBeenCalledTimes(1)
    expect(onUnauthorized).not.toHaveBeenCalled()
    expect(fetcher.mock.calls[0]?.[0]).toBe('/api/academic/map/floors/200/deletion')
    expect(JSON.parse(String(fetcher.mock.calls[0]?.[1]?.body))).toEqual(payload)
  })

  it('rejects preview JSON completed after an owner change', async () => {
    let generation = 1
    let releaseBody: ((value: unknown) => void) | undefined
    const response = new Response('{}')
    vi.spyOn(response, 'json').mockImplementation(() => new Promise((resolve) => { releaseBody = resolve }))
    const client = new AdminMapClient({
      accessToken: () => 'token-a', currentGeneration: () => generation,
      fetcher: vi.fn(async () => response),
    })
    const pending = client.deletionPreview('FLOOR', '200')
    await Promise.resolve()
    await Promise.resolve()
    generation = 2
    releaseBody?.({ targetId: '200' })
    await expect(pending).rejects.toBeInstanceOf(StaleSessionGenerationError)
  })

  it('does not replay an authorized request when refresh switches the owner', async () => {
    let generation = 1
    const fetcher = vi.fn<typeof fetch>(async () => new Response('{}', { status: 401 }))
    const client = new AdminMapClient({
      accessToken: () => 'token-a', currentGeneration: () => generation, fetcher,
      onUnauthorized: async () => { generation = 2 },
    })
    await expect(client.listBuildings()).rejects.toBeInstanceOf(StaleSessionGenerationError)
    expect(fetcher).toHaveBeenCalledTimes(1)
  })
})
