import { describe, expect, it } from 'vitest'
import { CampusMapClient } from '../../../../mobile-core/src/api/map-client'
import type { MapFormatSlot, MapPlan } from '../../../../mobile-core/src/api/types'
import { createMapState } from './map-state'

function plan(floorId: string): MapPlan {
  const png: MapFormatSlot = {
    format: 'png',
    state: 'ready',
    id: floorId,
    contentType: 'image/png',
    bytes: 1,
    sha256: null,
    width: 2,
    height: 2,
    viewBox: null,
  }
  return {
    buildingId: 'one',
    floorId,
    version: '1',
    label: floorId,
    png,
    svg: { ...png, format: 'svg', state: 'absent', id: null, contentType: 'image/svg+xml' },
  }
}
function fixture(fetcher: typeof fetch, assertCurrent: () => void = () => {}) {
  let serial = 0
  const active = new Set<string>()
  const urls = {
    createObjectURL() {
      const url = `blob:map-${++serial}`
      active.add(url)
      return url
    },
    revokeObjectURL(url: string) {
      active.delete(url)
    },
  }
  const client = new CampusMapClient({ accessToken: () => null, fetcher })
  return { state: createMapState(client, assertCurrent, urls), active }
}
function manifest() {
  return Response.json({
    schemaVersion: 1,
    validationPolicyVersion: 1,
    revision: '1',
    buildings: [
      {
        id: 'one',
        label: 'Корпус 1',
        floors: ['first', 'second'].map((id) => ({ id, label: id, plan: plan(id) })),
      },
    ],
  })
}
describe('web campus map asset ownership', () => {
  it('retains the ready SVG and completes PNG when same-plan refresh interrupts its pending download', async () => {
    let release!: (value: Response) => void
    let started!: () => void
    const pngStarted = new Promise<void>((resolve) => {
      started = resolve
    })
    const oldPng = new Promise<Response>((resolve) => {
      release = resolve
    })
    let firstPng = true
    const { state, active } = fixture(async (input) => {
      const path = String(input)
      if (path.endsWith('/manifest')) return manifest()
      if (path.endsWith('/plan')) {
        const value = plan('first')
        return Response.json({
          plan: { ...value, svg: { ...value.svg, id: 'svg', state: 'ready' } },
        })
      }
      if (path.includes('/svg/'))
        return new Response('svg', { headers: { 'Content-Type': 'image/svg+xml' } })
      if (firstPng) {
        firstPng = false
        started()
        return oldPng
      }
      return new Response('published PNG', { headers: { 'Content-Type': 'image/png' } })
    })
    const initial = state.load()
    await pngStarted
    // SVG and PNG decode independently; wait for the already-ready preview before refresh.
    for (let tick = 0; !state.assets.value.svg && tick < 20; tick++) await Promise.resolve()
    const visible = state.preview.value
    expect(visible).toBeDefined()
    await state.load()
    release(new Response('obsolete PNG', { headers: { 'Content-Type': 'image/png' } }))
    await initial
    expect(state.preview.value).toBe(visible)
    expect(await state.assets.value.png?.blob.text()).toBe('published PNG')
    expect(state.guardAsset('png')).toBe(true)
    expect(state.assetErrors.value).toEqual({})
    expect(state.floorLoading.value).toBe(false)
    expect(active.size).toBe(2)
    state.dispose()
    expect(active.size).toBe(0)
  })
  it('retains the visible asset across a live refresh of the same published plan', async () => {
    const { state, active } = fixture(async (input) => {
      if (String(input).endsWith('/manifest')) return manifest()
      if (String(input).endsWith('/plan')) return Response.json({ plan: plan('first') })
      return new Response('png', { headers: { 'Content-Type': 'image/png' } })
    })
    await state.load()
    const visible = state.preview.value
    await state.load()
    expect(state.preview.value).toBe(visible)
    expect(active).toEqual(new Set([visible!.url]))
    state.dispose()
  })
  it('keeps only the newly selected floor when the previous floor asset arrives late', async () => {
    let finishFirst!: (value: Response) => void
    const oldAsset = new Promise<Response>((resolve) => {
      finishFirst = resolve
    })
    let started!: () => void
    const firstStarted = new Promise<void>((resolve) => {
      started = resolve
    })
    const { state, active } = fixture(async (input) => {
      const path = String(input)
      if (path.endsWith('/manifest')) return manifest()
      if (path.endsWith('/plan'))
        return Response.json({ plan: plan(path.includes('/first/') ? 'first' : 'second') })
      if (path.endsWith('/png/first')) {
        started()
        return oldAsset
      }
      return new Response('second', { headers: { 'Content-Type': 'image/png' } })
    })
    const initial = state.load()
    await firstStarted
    await state.selectFloor('second')
    const selected = state.preview.value!
    expect(await selected.blob.text()).toBe('second')
    finishFirst(new Response('first', { headers: { 'Content-Type': 'image/png' } }))
    await initial
    expect(state.plan.value?.floorId).toBe('second')
    expect(state.preview.value).toBe(selected)
    expect(active).toEqual(new Set([selected.url]))
    state.dispose()
    expect(active.size).toBe(0)
  })

  it('does not expose a downloaded file after its admitted authority has changed', async () => {
    let admitted = true
    let finish!: (value: Response) => void
    let started!: () => void
    const startedPromise = new Promise<void>((resolve) => {
      started = resolve
    })
    const download = new Promise<Response>((resolve) => {
      finish = resolve
    })
    const { state, active } = fixture(
      async (input) => {
        if (String(input).endsWith('/manifest')) return manifest()
        if (String(input).endsWith('/plan')) return Response.json({ plan: plan('first') })
        started()
        return download
      },
      () => {
        if (!admitted) throw new Error('authority changed')
      },
    )
    const initial = state.load()
    await startedPromise
    admitted = false
    finish(new Response('private file', { headers: { 'Content-Type': 'image/png' } }))
    await initial
    expect(state.preview.value).toBeUndefined()
    expect(active.size).toBe(0)
    expect(() => state.guardAsset('png')).toThrow('authority changed')
    state.dispose()
  })

  it('preserves a ready PNG when SVG download fails and retains the failed open outcome without repeating it', async () => {
    let recorded = false
    let repeatedEffect = false
    const { state } = fixture(async (input, init) => {
      const path = String(input)
      if (path.endsWith('/manifest')) return manifest()
      if (path.endsWith('/plan')) {
        const value = plan('first')
        return Response.json({
          plan: { ...value, svg: { ...value.svg, id: 'svg', state: 'ready' } },
        })
      }
      if (path.endsWith('/svg/svg')) return new Response(null, { status: 503 })
      if (init?.method === 'POST') {
        if (recorded) repeatedEffect = true
        recorded = true
        throw new TypeError('response lost after commit')
      }
      return new Response('png', { headers: { 'Content-Type': 'image/png' } })
    })
    await state.load()
    expect(state.assetErrors.value.svg).toBeTruthy()
    expect(await state.preview.value?.blob.text()).toBe('png')
    expect(state.guardAsset('png')).toBe(true)
    await state.recordOpen(state.preview.value!.url)
    await state.recordOpen(state.preview.value!.url)
    expect(recorded).toBe(true)
    expect(repeatedEffect).toBe(false)
    expect(state.usageError.value).toContain('не подтверждено')
    state.dispose()
  })
})
