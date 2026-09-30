import { describe, expect, it } from 'vitest'
import type { AdminMapBuildingResponse } from '../../api/types'
import { adminMapSelectionKey, captureAdminMapUploadTarget } from './admin-map-state'

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
