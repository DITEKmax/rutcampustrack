import { describe, expect, it } from 'vitest'
import type { AdminMapBuildingResponse, AdminMapDeletionPreview } from '../../api/types'
import { adminMapSelectionKey, captureAdminMapActionTarget, captureAdminMapUploadTarget, matchesAdminMapPreview } from './admin-map-state'

const buildings: readonly AdminMapBuildingResponse[] = [
  {
    id: 'building-a',
    code: '10',
    label: 'Корпус 10',
    floors: [{
      id: 'floor-a',
      buildingId: 'building-a',
      code: '1',
      label: 'Этаж 1',
      currentPlan: null,
      openCount: 0,
      openCountPeriod: 'all_time',
    }],
  },
  {
    id: 'building-b',
    code: '20',
    label: 'Корпус 20',
    floors: [{
      id: 'floor-b',
      buildingId: 'building-b',
      code: '1',
      label: 'Этаж 1',
      currentPlan: null,
      openCount: 0,
      openCountPeriod: 'all_time',
    }],
  },
]

describe('admin map upload target', () => {
  it('rejects an old floor or file draft after the selected building changes', () => {
    const file = new File(['map'], 'plan.png', { type: 'image/png' })
    const draftKey = adminMapSelectionKey('building-a', 'floor-a')

    expect(captureAdminMapUploadTarget(buildings, 'building-b', 'floor-a', draftKey, { png: file })).toBeNull()
    expect(captureAdminMapUploadTarget(buildings, 'building-b', 'floor-b', draftKey, { png: file })).toBeNull()
  })

  it('captures the exact selected floor and files for the in-flight upload', () => {
    const file = new File(['map'], 'plan.svg', { type: 'image/svg+xml' })
    const target = captureAdminMapUploadTarget(
      buildings,
      'building-a',
      'floor-a',
      adminMapSelectionKey('building-a', 'floor-a'),
      { svg: file },
    )

    expect(target).toMatchObject({
      buildingId: 'building-a',
      buildingLabel: 'Корпус 10',
      floorId: 'floor-a',
      floorLabel: 'Этаж 1',
      svg: file,
    })
  })
})

describe('admin map deletion target', () => {
  it('captures an immutable target and rejects a preview for a different entity', () => {
    const target = captureAdminMapActionTarget(buildings, 'FLOOR', 'building-a', 'floor-a')!
    const preview: AdminMapDeletionPreview = {
      targetType: 'FLOOR', targetId: 'floor-a', buildingId: 'building-a', label: 'Этаж 1',
      versions: 3, assets: 5, bytes: 100, openCount: 10, remainingFloors: 0, previewDigest: 'a'.repeat(64),
    }
    expect(Object.isFrozen(target)).toBe(true)
    expect(matchesAdminMapPreview(target, preview)).toBe(true)
    expect(matchesAdminMapPreview(target, { ...preview, targetId: 'floor-b' })).toBe(false)
    expect(matchesAdminMapPreview(target, { ...preview, buildingId: 'building-b' })).toBe(false)
    expect(matchesAdminMapPreview(target, { ...preview, targetType: 'BUILDING' })).toBe(false)
    expect(captureAdminMapActionTarget(buildings, 'FLOOR', 'building-b', 'floor-a')).toBeNull()
  })

  it('rejects a building deletion preview with remaining floors', () => {
    const target = captureAdminMapActionTarget(buildings, 'BUILDING', 'building-a', null)!
    expect(matchesAdminMapPreview(target, {
      targetType: 'BUILDING', targetId: 'building-a', buildingId: 'building-a', label: 'Корпус 10',
      versions: 0, assets: 0, bytes: 0, openCount: 0, remainingFloors: 1, previewDigest: 'a'.repeat(64),
    })).toBe(false)
  })
})
