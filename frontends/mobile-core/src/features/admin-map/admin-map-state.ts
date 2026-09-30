import type { AdminMapBuildingResponse } from '../../api/types'

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
