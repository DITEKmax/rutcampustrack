import { describe, expect, it, vi } from 'vitest'
import { StaleSessionGenerationError } from '../shared/session-owner'
import { AdminMapClient, CampusMapClient, MapApiError } from './map-client'
import type { AdminMapPlanDeletionPreview, MapFormat } from './types'

describe('CampusMapClient generation boundary', () => {
  it('rejects PNG bytes completed after the session changed', async () => {
    let generation = 1
    let releaseBody: ((value: Blob) => void) | undefined
    const response = new Response('image', { headers: { 'Content-Type': 'image/png' } })
    vi.spyOn(response, 'blob').mockImplementation(
      () =>
        new Promise((resolve) => {
          releaseBody = resolve
        }),
    )
    const client = new CampusMapClient({
      accessToken: () => 'synthetic',
      currentGeneration: () => generation,
      fetcher: async () => response,
    })
    const pending = client.downloadAsset('20', '200', '1', 'png', 'asset-png')
    await Promise.resolve()
    await Promise.resolve()
    generation = 2
    releaseBody?.(new Blob(['image'], { type: 'image/png' }))
    await expect(pending).rejects.toBeInstanceOf(StaleSessionGenerationError)
  })
  it('rejects empty or mismatched canonical PNG bytes', async () => {
    for (const blob of [
      new Blob([], { type: 'image/png' }),
      new Blob(['<svg/>'], { type: 'image/svg+xml' }),
    ]) {
      const client = new CampusMapClient({
        accessToken: () => 'synthetic',
        fetcher: async () => new Response(blob),
      })
      await expect(client.downloadAsset('20', '200', '1', 'png', 'asset-png')).rejects.toThrow(Error)
    }
  })
  it('rejects an asset response that crosses the authenticated owner generation', async () => {
    let generation = 4
    let releaseResponse: ((response: Response) => void) | undefined
    const fetcher = vi.fn(
      () =>
        new Promise<Response>((resolve) => {
          releaseResponse = resolve
        }),
    )
    const client = new CampusMapClient({
      accessToken: () => 'token-a',
      currentGeneration: () => generation,
      fetcher,
    })

    const pending = client.downloadAsset('20', '200', '1', 'svg', 'asset-1')
    generation = 5
    releaseResponse?.(
      new Response(new Blob(['<svg />']), {
        status: 200,
        headers: { 'Content-Type': 'image/svg+xml' },
      }),
    )

    await expect(pending).rejects.toBeInstanceOf(StaleSessionGenerationError)
    expect(fetcher).toHaveBeenCalledTimes(1)
  })
})

describe('AdminMapClient destructive operation boundary', () => {
  it('does not replay a non-idempotent creation after 401 or silently continue with a refreshed session', async () => {
    const fetcher = vi.fn<typeof fetch>(async () => new Response('{}', { status: 401 }))
    const onUnauthorized = vi.fn(async () => {})
    const client = new AdminMapClient({
      accessToken: () => null,
      currentGeneration: () => 1,
      fetcher,
      onUnauthorized,
    })
    await expect(client.createBuilding('2')).rejects.toBeInstanceOf(MapApiError)
    expect(fetcher).toHaveBeenCalledTimes(1)
    expect(onUnauthorized).not.toHaveBeenCalled()
    expect(JSON.parse(String(fetcher.mock.calls[0]?.[1]?.body))).toEqual({ code: '2', label: null })
  })
  it('sends deletion once to the captured ID and does not refresh or replay a 401', async () => {
    const fetcher = vi.fn<typeof fetch>(async () => new Response('{}', { status: 401 }))
    const onUnauthorized = vi.fn(async () => {})
    const client = new AdminMapClient({
      accessToken: () => 'token-a',
      currentGeneration: () => 1,
      fetcher,
      onUnauthorized,
    })
    const payload = {
      operationId: 'operation-a',
      previewDigest: 'a'.repeat(64),
      password: 'test-password',
    }
    await expect(client.deleteInventory('FLOOR', '200', payload)).rejects.toBeInstanceOf(
      MapApiError,
    )
    expect(fetcher).toHaveBeenCalledTimes(1)
    expect(onUnauthorized).not.toHaveBeenCalled()
    expect(fetcher.mock.calls[0]?.[0]).toBe('/api/academic/map/floors/200/deletion')
    expect(JSON.parse(String(fetcher.mock.calls[0]?.[1]?.body))).toEqual(payload)
  })

  it('rejects preview JSON completed after an owner change', async () => {
    let generation = 1
    let releaseBody: ((value: unknown) => void) | undefined
    const response = new Response('{}')
    vi.spyOn(response, 'json').mockImplementation(
      () =>
        new Promise((resolve) => {
          releaseBody = resolve
        }),
    )
    const client = new AdminMapClient({
      accessToken: () => 'token-a',
      currentGeneration: () => generation,
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
      accessToken: () => 'token-a',
      currentGeneration: () => generation,
      fetcher,
      onUnauthorized: async () => {
        generation = 2
      },
    })
    await expect(client.listBuildings()).rejects.toBeInstanceOf(StaleSessionGenerationError)
    expect(fetcher).toHaveBeenCalledTimes(1)
  })
})

describe('AdminMapClient plan deletion preview scope', () => {
  const preview = (format?: MapFormat): AdminMapPlanDeletionPreview => ({
    buildingCode: '20',
    floorCode: '200',
    format: format ?? null,
    label: 'Схема этажа',
    files: 2,
    bytes: 1024,
    previewDigest: 'a'.repeat(64),
  })

  it('retains omitted/null legacy reads without claiming a confirmed deletion scope', async () => {
    for (const format of [undefined, 'png', 'svg'] as const) {
      for (const legacy of [preview(format), { ...preview(format), deletionScope: null }]) {
        const client = new AdminMapClient({
          accessToken: () => null,
          fetcher: async () => Response.json(legacy),
        })
        const result = await client.planDeletionPreview('20', '200', format)
        expect(result).toEqual(legacy)
        expect(result.deletionScope == null).toBe(true)
        expect(result.deletionScope).not.toBe('ALL_VERSIONS')
      }
    }
  })

  it('exposes only the confirmed scope matching the captured whole-plan or format intent', async () => {
    for (const [format, deletionScope] of [
      [undefined, 'ALL_VERSIONS'],
      ['png', 'CURRENT_FORMAT'],
      ['svg', 'CURRENT_FORMAT'],
    ] as const) {
      const confirmed: AdminMapPlanDeletionPreview = { ...preview(format), deletionScope }
      const client = new AdminMapClient({
        accessToken: () => null,
        fetcher: async () => Response.json(confirmed),
      })
      await expect(client.planDeletionPreview('20', '200', format)).resolves.toEqual(confirmed)
    }
  })

  it('rejects a valid scope belonging to a different deletion intent', async () => {
    for (const [format, deletionScope] of [
      [undefined, 'CURRENT_FORMAT'],
      ['png', 'ALL_VERSIONS'],
      ['svg', 'ALL_VERSIONS'],
    ] as const) {
      const client = new AdminMapClient({
        accessToken: () => null,
        fetcher: async () => Response.json({ ...preview(format), deletionScope }),
      })
      await expect(client.planDeletionPreview('20', '200', format)).rejects.toBeInstanceOf(TypeError)
    }
  })

  it('rejects present malformed capabilities instead of treating them as legacy absence', async () => {
    for (const format of [undefined, 'png', 'svg'] as const) {
      for (const deletionScope of ['all_versions', 'ALL_VERSIONS ', true, {}, []]) {
        const client = new AdminMapClient({
          accessToken: () => null,
          fetcher: async () => Response.json({ ...preview(format), deletionScope }),
        })
        await expect(client.planDeletionPreview('20', '200', format)).rejects.toBeInstanceOf(TypeError)
      }
    }
  })
})

describe('AdminMapClient atomic plan publication', () => {
  const slot = (format: 'png' | 'svg') => ({
    format,
    state: 'ready',
    id: 'asset',
    bytes: 3,
    contentType: format === 'png' ? 'image/png' : 'image/svg+xml',
  })
  const plan = {
    buildingId: '20',
    floorId: '200',
    version: '1',
    catalogRevision: 'r1',
    png: slot('png'),
    svg: slot('svg'),
  }
  const file = new File(['map'], 'plan.png', { type: 'image/png' })
  it('preserves exact 64-digit codes and multipart fields while requiring the frozen ACK', async () => {
    const fetcher = vi.fn<typeof fetch>(async () =>
      Response.json(
        { status: 'created', buildingCreated: true, floorCreated: true, plan },
        { status: 201 },
      ),
    )
    const client = new AdminMapClient({ accessToken: () => null, fetcher })
    expect(
      await client.uploadPlan({ buildingCode: '9'.repeat(64), floorCode: '2', png: file }),
    ).toMatchObject({ status: 'created' })
    const [url, init] = fetcher.mock.calls[0]!
    expect(url).toBe('/api/academic/map/plans')
    const form = init!.body as FormData
    expect(form.get('buildingCode')).toBe('9'.repeat(64))
    expect(form.get('floorCode')).toBe('2')
    expect(form.get('label')).toBeNull()
    expect(form.get('png')).toBeInstanceOf(File)
    expect(new Headers(init!.headers).has('Content-Type')).toBe(false)
  })
  it('rejects spaces, signs, leading zeroes, exponent, Unicode digits and overlength before fetch', async () => {
    const fetcher = vi.fn<typeof fetch>()
    const client = new AdminMapClient({ accessToken: () => null, fetcher })
    for (const code of [
      ' 1',
      '1 ',
      '+1',
      '-1',
      '01',
      '1e2',
      '1.0',
      '١',
      '９',
      '9'.repeat(65),
      '0',
      '',
    ])
      await expect(
        client.uploadPlan({ buildingCode: code, floorCode: '1', png: file }),
      ).rejects.toBeInstanceOf(TypeError)
    await expect(client.uploadPlan({ buildingCode: '1', floorCode: '1' })).rejects.toBeInstanceOf(
      TypeError,
    )
    await expect(
      client.uploadPlan({ buildingCode: '1', floorCode: '1', png: file, label: 'x'.repeat(256) }),
    ).rejects.toBeInstanceOf(TypeError)
    expect(fetcher).not.toHaveBeenCalled()
  })
  it('rejects malformed/status-mismatched acknowledgements without replay', async () => {
    for (const body of [
      { status: 'created', buildingCreated: false, floorCreated: false, plan },
      { status: 'replaced', buildingCreated: true, floorCreated: true, plan },
      {
        status: 'replaced',
        buildingCreated: false,
        floorCreated: false,
        plan: { ...plan, png: {} },
      },
      { status: 'other', plan },
      {},
    ]) {
      const fetcher = vi.fn<typeof fetch>(async () => Response.json(body, { status: 200 }))
      const client = new AdminMapClient({ accessToken: () => null, fetcher })
      await expect(
        client.uploadPlan({ buildingCode: '1', floorCode: '1', png: file }),
      ).rejects.toBeInstanceOf(TypeError)
      expect(fetcher).toHaveBeenCalledTimes(1)
    }
  })
  it('rejects upload JSON completed after generation changes', async () => {
    let generation = 1
    let release!: (value: unknown) => void
    const response = new Response('{}', { status: 201 })
    response.json = () =>
      new Promise((resolve) => {
        release = resolve
      })
    const client = new AdminMapClient({
      accessToken: () => null,
      currentGeneration: () => generation,
      fetcher: async () => response,
    })
    const pending = client.uploadPlan({ buildingCode: '1', floorCode: '1', png: file })
    await Promise.resolve()
    await Promise.resolve()
    generation = 2
    release({ status: 'created', buildingCreated: false, floorCreated: false, plan })
    await expect(pending).rejects.toBeInstanceOf(StaleSessionGenerationError)
  })
  it('never refreshes/replays upload after 401 or a lost ACK', async () => {
    const onUnauthorized = vi.fn(async () => {})
    for (const fetcher of [
      vi.fn<typeof fetch>(async () => new Response('{}', { status: 401 })),
      vi.fn<typeof fetch>(async () => {
        throw new TypeError('lost ACK')
      }),
    ]) {
      const client = new AdminMapClient({ accessToken: () => null, fetcher, onUnauthorized })
      await expect(
        client.uploadPlan({ buildingCode: '1', floorCode: '1', png: file }),
      ).rejects.toThrow()
      expect(fetcher).toHaveBeenCalledTimes(1)
    }
    expect(onUnauthorized).not.toHaveBeenCalled()
  })
})
