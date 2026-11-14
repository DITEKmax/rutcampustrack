import type { AdminMapBuildingResponse, AdminMapFloorResponse, AdminMapPlan, MapFormat, MapFormatSlot, MapManifest, MapPlan } from '../../../mobile-core/src/api/types'
import type { PreviewHandler } from '../../../pwa-vue/src/dev/interactive-mock-transport'
import svg0 from './floor-plans/1-0.svg?raw'
import svg1 from './floor-plans/1-1.svg?raw'
import png0 from './floor-plans/1-0.png?inline'
import png1 from './floor-plans/1-1.png?inline'

const width = 7945
const height = 2904
const contentTypes = { png: 'image/png', svg: 'image/svg+xml' } as const
const source = [
  { png: png0, svg: svg0, sha256: {
    png: 'ad1140ef388a49076c9cde889fc28de5747e3d7122a1e1cf83fc5ab30299da84',
    svg: '8ca0e54f59862436e508eb2936d7ef7d5a3359890711c57d1b7c4933299b79cb',
  } },
  { png: png1, svg: svg1, sha256: {
    png: '1a51f81370a3655585edc6e4b1f71f0536f96e4155762fc379ffb64a0df13003',
    svg: 'e9238bd1e2c5f7b537bb77d8cfe9e05c3cd791321cfa44d690b812e46c800d6a',
  } },
] as const

function asset(floor: typeof source[number], format: MapFormat): Blob {
  const data = format === 'svg' ? floor.svg
    : Uint8Array.from(atob(floor.png.slice(floor.png.indexOf(',') + 1)), (character) => character.charCodeAt(0))
  return new Blob([data], { type: contentTypes[format] })
}

const floors = source.map((floor, index) => {
  const id = String(index)
  const label = `Этаж ${id}`
  const blobs = { png: asset(floor, 'png'), svg: asset(floor, 'svg') }
  const slot = (format: MapFormat): MapFormatSlot => ({
    format, state: 'ready', contentType: contentTypes[format], id: `1-${id}-${format}`,
    bytes: blobs[format].size, sha256: floor.sha256[format], width, height,
    viewBox: format === 'svg' ? [0, 0, width, height] : null,
  })
  const plan: MapPlan = { buildingId: '1', floorId: id, version: '1', label, png: slot('png'), svg: slot('svg') }
  return { id, label, plan, blobs }
})
const manifest: MapManifest = {
  schemaVersion: 1, validationPolicyVersion: 1, revision: 'demo-campus-1',
  buildings: [{ id: '1', label: 'Корпус 1', floors: floors.map(({ id, label, plan }) => ({ id, label, plan })) }],
}

export function createCampusDemo(): PreviewHandler {
  return async (url, method) => {
    const path = url.pathname
    if (path === '/api/v1/map/manifest' && method === 'GET') return Response.json(manifest)
    const match = /^\/api\/v1\/map\/buildings\/1\/floors\/(0|1)\/(.*)$/.exec(path)
    const floor = floors.find(({ id }) => id === match?.[1])
    if (!floor || !match) return null
    if (match[2] === 'plan' && method === 'GET') return Response.json({ plan: floor.plan })
    if (match[2] === 'opens' && method === 'POST') return new Response(null, { status: 204 })
    if (method === 'GET') {
      for (const format of ['svg', 'png'] as const) {
        if (match[2] === `plans/1/assets/${format}/${floor.plan[format].id}`)
          return new Response(floor.blobs[format])
      }
    }
    return null
  }
}

export function createAdminMapDemo(): PreviewHandler {
  const base = '/api/academic/map'
  const buildings = new Map<string, AdminMapBuildingResponse>([['1', {
    id: '1', code: '1', label: 'Учебный корпус',
    floors: floors.map(({ id, label, plan }, index) => ({
      id: `100${id}`, buildingId: '1', code: id, label, openCount: [127, 242][index]!,
      openCountPeriod: 'all_time', currentPlan: { ...plan, floorId: `100${id}`, catalogRevision: 'demo-campus-admin-1' },
    })),
  }]])
  const files = new Map<string, Blob>(floors.flatMap(floor => (['png', 'svg'] as const).map(format => [floor.plan[format].id!, floor.blobs[format]] as const)))
  const versions = new Map<string, AdminMapPlan>()
  for (const floor of buildings.get('1')!.floors) {
    const plan = floor.currentPlan!
    versions.set(`${floor.id}/${plan.version}`, plan)
  }
  let nextBuilding = 2
  let nextFloor = 2000
  const missing = () => Response.json({ detail: 'Схема не найдена.' }, { status: 404 })
  const allFloors = () => [...buildings.values()].flatMap(item => item.floors)
  function changeFloor(floor: AdminMapFloorResponse) {
    const building = buildings.get(floor.buildingId)!
    buildings.set(building.id, { ...building, floors: [...building.floors.filter(item => item.id !== floor.id), floor].sort((a, b) => Number(a.code) - Number(b.code)) })
  }
  function createBuilding(code: string, label: string | null = null) {
    const building = { id: String(nextBuilding++), code, label: label ?? `Корпус ${code}`, floors: [] }
    buildings.set(building.id, building)
    return building
  }
  function createFloor(buildingId: string, code: string, label: string | null = null) {
    const floor: AdminMapFloorResponse = { id: String(nextFloor++), buildingId, code, label: label ?? `Этаж ${code}`, openCount: 0, openCountPeriod: 'all_time', currentPlan: null }
    changeFloor(floor)
    return floor
  }
  const absent = (format: MapFormat): MapFormatSlot => ({
    format, state: 'absent', contentType: contentTypes[format], id: null, bytes: 0, sha256: null, height: null, width: null, viewBox: null,
  })
  async function publish(form: FormData, floor: AdminMapFloorResponse): Promise<AdminMapPlan> {
    const version = String(Math.max(0, ...[...versions.values()].filter(item => item.floorId === floor.id).map(item => Number(item.version))) + 1)
    async function update(format: MapFormat): Promise<MapFormatSlot> {
      const file = form.get(format)
      if (!(file instanceof File)) return floor.currentPlan?.[format] ?? absent(format)
      const id = `floor-${floor.id}-${version}-${format}`
      const hash = new Uint8Array(await crypto.subtle.digest('SHA-256', await file.arrayBuffer()))
      files.set(id, new Blob([file], { type: contentTypes[format] }))
      return { ...absent(format), state: 'ready', id, bytes: file.size,
        sha256: [...hash].map(byte => byte.toString(16).padStart(2, '0')).join('') }
    }
    const plan = { buildingId: floor.buildingId, floorId: floor.id, version, catalogRevision: `admin-demo-${floor.id}-${version}`,
      label: String(form.get('label') ?? floor.label ?? `Этаж ${floor.code}`),
      png: await update('png'), svg: await update('svg') }
    versions.set(`${floor.id}/${version}`, plan)
    changeFloor({ ...floor, currentPlan: plan })
    return plan
  }
  return async (url, method, body, role, source) => {
    if (role !== 'ADMIN' || !url.pathname.startsWith(base + '/')) return null
    const path = url.pathname.slice(base.length)
    if (path === '/buildings' && method === 'GET') return Response.json([...buildings.values()])
    if (path === '/floors' && method === 'GET') return Response.json(url.searchParams.has('buildingId')
      ? buildings.get(url.searchParams.get('buildingId')!)?.floors ?? [] : allFloors())
    if (path === '/buildings' && method === 'POST') {
      const command = await body()
      return Response.json(createBuilding(String(command.code), command.label as string | null), { status: 201 })
    }
    if (path === '/floors' && method === 'POST') {
      const command = await body()
      return Response.json(createFloor(String(command.buildingId), String(command.code), command.label as string | null), { status: 201 })
    }
    const inventory = path.match(/^\/(buildings|floors)\/([^/]+)(?:\/(deletion-preview|deletion|floors))?$/)
    if (inventory) {
      const isBuilding = inventory[1] === 'buildings'
      const id = inventory[2]!, action = inventory[3]
      const building = isBuilding ? buildings.get(id) : undefined
      const floor = isBuilding ? undefined : allFloors().find(item => item.id === id)
      if (!building && !floor) return missing()
      if (method === 'GET' && action === 'floors') return Response.json(building?.floors ?? [])
      if (method === 'PUT' && !action) {
        const command = await body()
        const code = String(command.code)
        const properties = { code, label: typeof command.label === 'string' ? command.label : isBuilding ? `Корпус ${code}` : `Этаж ${code}` }
        if (building) {
          const next = { ...building, ...properties }
          buildings.set(id, next)
          return Response.json(next)
        }
        const next = { ...floor!, ...properties }
        changeFloor(next)
        return Response.json(next)
      }
      if (method === 'GET' && action === 'deletion-preview') {
        const selected = building?.floors ?? [floor!]
        const plans = [...versions.values()].filter(plan => selected.some(item => item.id === plan.floorId))
        const assets = plans.flatMap(plan => [plan.png, plan.svg]).filter(slot => slot.state === 'ready')
        return Response.json({ targetType: isBuilding ? 'BUILDING' : 'FLOOR', targetId: id,
          buildingId: building?.id ?? floor!.buildingId, label: building?.label ?? floor?.label ?? id,
          versions: plans.length, assets: assets.length, bytes: assets.reduce((sum, slot) => sum + slot.bytes, 0),
          openCount: selected.reduce((sum, item) => sum + (item.openCount ?? 0), 0),
          remainingFloors: building ? 0 : (buildings.get(floor!.buildingId)?.floors.length ?? 1) - 1,
          previewDigest: 'a'.repeat(64),
        })
      }
      if (method === 'POST' && action === 'deletion') {
        const command = await body()
        if (building) buildings.delete(id)
        if (floor) {
          const owner = buildings.get(floor.buildingId)!
          buildings.set(owner.id, { ...owner, floors: owner.floors.filter(item => item.id !== id) })
        }
        for (const [key, plan] of versions) if (plan.buildingId === building?.id || plan.floorId === floor?.id) versions.delete(key)
        return Response.json({ operationId: command.operationId, targetId: id, targetType: isBuilding ? 'BUILDING' : 'FLOOR', status: 'COMPLETED' })
      }
    }
    if (path.startsWith('/plans/target')) {
      const command = method === 'POST' ? await body() : Object.fromEntries(url.searchParams)
      const buildingCode = String(command.buildingCode), floorCode = String(command.floorCode)
      const floor = [...buildings.values()].find(item => item.code === buildingCode)?.floors.find(item => item.code === floorCode)
      if (path === '/plans/target') return Response.json({ buildingCode, floorCode, status: floor ? 'found' : 'absent', floor: floor ?? null })
      const plan = floor?.currentPlan
      if (!floor || !plan) return missing()
      const format = (command.format as MapFormat | null | undefined) ?? null
      const slots = [plan.png, plan.svg].filter(slot => slot.state === 'ready' && (!format || format === slot.format))
      if (path.endsWith('/deletion-preview'))
        return Response.json({ buildingCode, floorCode, format, deletionScope: format ? 'CURRENT_FORMAT' : 'ALL_VERSIONS',
          label: plan.label, files: slots.length, bytes: slots.reduce((sum, file) => sum + file.bytes, 0), previewDigest: 'a'.repeat(64) })
      if (path.endsWith('/deletion') && method === 'POST') {
        const currentPlan = format ? { ...plan, [format]: absent(format) } : null
        changeFloor({ ...floor, currentPlan })
        if (currentPlan) versions.set(`${floor.id}/${plan.version}`, currentPlan)
        else for (const [key, item] of versions) if (item.floorId === floor.id) versions.delete(key)
        return Response.json({ operationId: command.operationId, buildingCode, floorCode, format, status: 'COMPLETED' })
      }
    }
    if (path === '/plans' && method === 'POST' && source instanceof FormData) {
      const buildingCode = String(source.get('buildingCode')), floorCode = String(source.get('floorCode'))
      const existing = [...buildings.values()].find(item => item.code === buildingCode)
      const building = existing ?? createBuilding(buildingCode)
      const previous = building.floors.find(item => item.code === floorCode)
      const floor = previous ?? createFloor(building.id, floorCode)
      const plan = await publish(source, floor)
      const replaced = previous?.currentPlan != null
      return Response.json({ status: replaced ? 'replaced' : 'created', buildingCreated: !existing,
        floorCreated: !previous, plan }, { status: replaced ? 200 : 201 })
    }
    const version = path.match(/^\/floors\/([^/]+)\/versions(?:\/([^/]+)(?:\/assets\/(png|svg)\/([^/]+))?)?$/)
    if (version) {
      const floor = allFloors().find(item => item.id === version[1])
      if (!floor) return missing()
      if (method === 'POST' && source instanceof FormData) return Response.json(await publish(source, floor), { status: 201 })
      const plan = versions.get(`${floor.id}/${version[2]}`)
      if (!plan) return missing()
      const format = version[3] as MapFormat | undefined
      if (!format) return Response.json(plan)
      const key = version[4]!
      const blob = plan[format].id === key ? files.get(key) : null
      return blob ? new Response(blob) : missing()
    }
    return null
  }
}
