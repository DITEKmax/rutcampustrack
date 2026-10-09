import type { AdminMapBuildingResponse, AdminMapFloorResponse, AdminMapPlan } from '../api/types'
import { AdminMapClient } from '../api/map-client'
import type { AdminAccessProfile } from '../features/admin-access/admin-access-client'

export const adminMapStates = ['full', 'limited-map', 'no-delete', 'target-switch', 'floating-file', 'combined-new', 'combined-existing', 'combined-pending', 'partial-failure', 'unknown', 'unknown-building', 'delete', 'proof-revoked', 'statistics', 'statistics-zero', 'statistics-empty', 'statistics-error', 'statistics-filter', 'replace', 'browse', 'unknown-reconciled', 'more'] as const
const svg = '<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 320 180"><rect width="320" height="180" fill="#f7f5fc"/><path d="M20 20h280v140H20z M20 90h280 M100 20v140 M220 20v140" fill="none" stroke="#684db1" stroke-width="3"/><g fill="#34244f" font-family="sans-serif" font-size="16"><text x="42" y="58">101</text><text x="145" y="58">102</text><text x="244" y="58">103</text><text x="42" y="135">104</text><text x="145" y="135">105</text></g></svg>'
const png = Uint8Array.from(atob('iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mP8/x8AAusB9Wl6PCEAAAAASUVORK5CYII='), character => character.charCodeAt(0))
function plan(buildingId: string, floorId: string, version = '1'): AdminMapPlan {
  const slot = (format: 'png' | 'svg') => ({ format, state: 'ready' as const, contentType: format === 'png' ? 'image/png' as const : 'image/svg+xml' as const, id: `asset-${format}-${floorId}-${version}`, bytes: format === 'png' ? png.length : svg.length, sha256: 'a'.repeat(64), width: 320, height: 180, viewBox: format === 'svg' ? [0, 0, 320, 180] : null })
  return { buildingId, floorId, version, label: 'Синтетическая схема', catalogRevision: `revision-${version}`, png: slot('png'), svg: slot('svg') }
}
export function createAdminMapFixtures(state: string) {
  const floor = (id: string, buildingId: string, code: string, openCount: number): AdminMapFloorResponse => ({ id, buildingId, code, label: `Прежнее название этажа ${code}`, currentPlan: plan(buildingId, id), openCount, openCountPeriod: 'all_time' })
  let inventory: AdminMapBuildingResponse[] = state === 'statistics-empty' ? [] : [
    { id: '20', code: '1', label: 'Прежнее название корпуса', floors: [floor('200', '20', '1', state === 'statistics-zero' ? 0 : 1248), floor('201', '20', '3', 0)] },
    { id: '30', code: '2', label: 'Прежнее название второго корпуса', floors: [floor('300', '30', '1', state === 'statistics-zero' ? 0 : 395), floor('301', '30', '2', state === 'statistics-zero' ? 0 : 742)] },
  ]
  const limited = ['limited-map', 'no-delete', 'proof-revoked'].includes(state)
  const authority: AdminAccessProfile = { userId: 1, assigned: true, mode: limited ? 'LIMITED' : 'FULL', permissions: ['CAMPUS_MAP_MANAGE', ...(!['limited-map', 'no-delete'].includes(state) ? ['DATA_DELETE'] : [])], version: 1 }
  let failRead = state === 'statistics-error'
  const client = new AdminMapClient({ accessToken: () => null, currentGeneration: () => 1, fetcher: async (input, init) => {
    const path = String(input)
    const method = init?.method ?? 'GET'
    if (path === '/api/academic/map/buildings' && method === 'GET') {
      if (failRead) { failRead = false; return Response.json({ title: 'Синтетическая ошибка чтения' }, { status: 503 }) }
      return Response.json(inventory)
    }
    if (path === '/api/academic/map/plans' && method === 'POST') {
      if (state === 'combined-pending') return new Promise<Response>(() => {})
      if (state === 'partial-failure') return Response.json({ detail: 'Файл схемы отклонён. Проверь формат.' }, { status: 400 })
      const body = init?.body as FormData
      const buildingCode = String(body.get('buildingCode'))
      const floorCode = String(body.get('floorCode'))
      const previousBuilding = inventory.find(item => item.code === buildingCode)
      const previousFloor = previousBuilding?.floors.find(item => item.code === floorCode)
      const buildingId = previousBuilding?.id ?? '90'
      const floorId = previousFloor?.id ?? '900'
      const published = plan(buildingId, floorId, previousFloor?.currentPlan ? '2' : '1')
      const createdFloor = { ...floor(floorId, buildingId, floorCode, previousFloor?.openCount ?? 0), currentPlan: published }
      const building = { id: buildingId, code: buildingCode, label: previousBuilding?.label ?? `Корпус ${buildingCode}`, floors: [...(previousBuilding?.floors.filter(item => item.id !== floorId) ?? []), createdFloor] }
      inventory = [...inventory.filter(item => item.id !== buildingId), building]
      if (state.startsWith('unknown')) throw new TypeError('Synthetic connection loss after publication')
      return Response.json({ status: previousFloor?.currentPlan ? 'replaced' : 'created', buildingCreated: !previousBuilding, floorCreated: !previousFloor, plan: published }, { status: previousFloor?.currentPlan ? 200 : 201 })
    }
    if (/\/assets\/svg\//.test(path)) return new Response(svg, { headers: { 'Content-Type': 'image/svg+xml' } })
    if (/\/assets\/png\//.test(path)) return new Response(png, { headers: { 'Content-Type': 'image/png' } })
    if (path === '/api/academic/map/floors/200/deletion-preview') return Response.json({ targetType: 'FLOOR', targetId: '200', buildingId: '20', label: 'Прежнее название', versions: 3, assets: 5, bytes: 246312, openCount: 1248, remainingFloors: 1, previewDigest: 'a'.repeat(64) })
    if (path === '/api/academic/map/floors/200/deletion') return Response.json({ code: 'CURRENT_PASSWORD_INVALID' }, { status: 403 })
    throw new Error(`Unexpected synthetic map API: ${method} ${path}`)
  } })
  return { client, authority }
}
