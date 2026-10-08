import type { AdminMapBuildingResponse, AdminMapFloorResponse } from '../api/types'
import { AdminMapClient } from '../api/map-client'
import type { AdminAccessProfile } from '../features/admin-access/admin-access-client'

export const adminMapStates = ['full', 'limited-map', 'no-delete', 'target-switch', 'floating-file', 'combined-new', 'combined-existing', 'combined-pending', 'partial-failure', 'unknown', 'unknown-building', 'delete', 'proof-revoked', 'statistics', 'statistics-zero', 'statistics-empty', 'statistics-error', 'statistics-filter'] as const

export function createAdminMapFixtures(state: string) {
  const floor = (id: string, buildingId: string, code: string, openCount: number): AdminMapFloorResponse => ({ id, buildingId, code, label: `Прежнее название этажа ${code}`, currentPlan: null, openCount, openCountPeriod: 'all_time' })
  let inventory: AdminMapBuildingResponse[] = state === 'statistics-empty' ? [] : [
    { id: '20', code: '1', label: 'Прежнее название корпуса', floors: [floor('200', '20', '1', state === 'statistics-zero' ? 0 : 1248), floor('201', '20', '3', state === 'statistics-zero' ? 0 : 742)] },
    { id: '30', code: '2', label: 'Прежнее название второго корпуса', floors: [floor('300', '30', '1', state === 'statistics-zero' ? 0 : 395)] },
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
    if (path === '/api/academic/map/buildings' && method === 'POST') {
      const body = JSON.parse(String(init?.body)) as { code: string }
      const building = { id: '90', code: body.code, label: body.code, floors: [] }
      inventory = [...inventory, building]
      if (state === 'unknown-building') throw new TypeError('Synthetic connection loss after ACK')
      return Response.json(building)
    }
    if (path === '/api/academic/map/floors' && method === 'POST') {
      if (state === 'combined-pending') return new Promise<Response>(() => {})
      if (state === 'partial-failure') return Response.json({ detail: 'Номер этажа занят. Укажи другой номер.' }, { status: 409 })
      const body = JSON.parse(String(init?.body)) as { code: string; buildingId: string }
      const created = floor('900', body.buildingId, body.code, 0)
      inventory = inventory.map(building => building.id === body.buildingId ? { ...building, floors: [...building.floors, created] } : building)
      if (state === 'unknown') throw new TypeError('Synthetic connection loss after ACK')
      return Response.json(created)
    }
    if (path === '/api/academic/map/floors/200/deletion-preview') return Response.json({ targetType: 'FLOOR', targetId: '200', buildingId: '20', label: 'Прежнее название', versions: 3, assets: 5, bytes: 246312, openCount: 1248, remainingFloors: 1, previewDigest: 'a'.repeat(64) })
    if (path === '/api/academic/map/floors/200/deletion') return Response.json({ code: 'CURRENT_PASSWORD_INVALID' }, { status: 403 })
    throw new Error(`Unexpected synthetic map API: ${method} ${path}`)
  } })
  return { client, authority }
}
