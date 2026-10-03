import type { AdminMapBuildingResponse, AdminMapDeletionPreview, AdminMapDeletionTarget } from '../../api/types'

export interface AdminMapActionTarget {
  readonly type: AdminMapDeletionTarget
  readonly id: string
  readonly buildingId: string
  readonly label: string
}

export function captureAdminMapActionTarget(
  buildings: readonly AdminMapBuildingResponse[], type: AdminMapDeletionTarget,
  buildingId: string | null, floorId: string | null,
): AdminMapActionTarget | null {
  const building = buildings.find((item) => item.id === buildingId)
  if (!building) return null
  if (type === 'BUILDING') return Object.freeze({ type, id: building.id, buildingId: building.id, label: building.label })
  const floor = building.floors.find((item) => item.id === floorId && item.buildingId === building.id)
  return floor ? Object.freeze({ type, id: floor.id, buildingId: building.id, label: `${building.label} · ${floor.label}` }) : null
}

export function matchesAdminMapPreview(target: AdminMapActionTarget, preview: AdminMapDeletionPreview): boolean {
  return preview.targetType === target.type && preview.targetId === target.id
    && preview.buildingId === target.buildingId && /^[0-9a-f]{64}$/.test(preview.previewDigest)
    && (target.type !== 'BUILDING' || preview.remainingFloors === 0)
}

export interface AdminMapUploadTarget {
  readonly key: string
  readonly buildingId: string
  readonly buildingLabel: string
  readonly floorId: string
  readonly floorLabel: string
  readonly png?: File
  readonly svg?: File
}

export function adminMapSelectionKey(buildingId: string | null, floorId: string | null): string | null {
  return buildingId && floorId ? JSON.stringify([buildingId, floorId]) : null
}

export function captureAdminMapUploadTarget(
  buildings: readonly AdminMapBuildingResponse[],
  buildingId: string | null,
  floorId: string | null,
  draftKey: string | null,
  files: { png?: File; svg?: File },
): AdminMapUploadTarget | null {
  const key = adminMapSelectionKey(buildingId, floorId)
  if (!key || draftKey !== key || (!files.png && !files.svg)) return null

  const building = buildings.find((item) => item.id === buildingId)
  const floor = building?.floors.find((item) => item.id === floorId)
  if (!building || !floor) return null

  return {
    key,
    buildingId: building.id,
    buildingLabel: building.label,
    floorId: floor.id,
    floorLabel: floor.label,
    ...(files.png ? { png: files.png } : {}),
    ...(files.svg ? { svg: files.svg } : {}),
  }
}
