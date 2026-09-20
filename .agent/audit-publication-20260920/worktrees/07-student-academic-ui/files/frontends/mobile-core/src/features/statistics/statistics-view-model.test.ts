import { describe, expect, it } from 'vitest'
import {
  displayPercent,
  historyStatusAccessibleLabel,
  seriesForRange,
  toggleTypeSelection,
  typeSelectionContains,
  type StatisticsSubjectDetailData,
} from './statistics-view-model'

const detail = {
  subjectId: 'subject.opaque',
  name: 'Основы программирования',
  availableTypes: ['LECTURE', 'LAB'],
  selectedTypes: ['LECTURE', 'LAB'],
  selectedAggregate: {
    present: { count: 2, percent: 50 }, presentOrExcused: { count: 3, percent: 75 }, excused: { count: 1, percent: 25 }, absent: { count: 1, percent: 25 }, held: 4, planned: 6,
  },
  series: [
    { id: 'g:123', label: '1', dateFrom: '2026-09-01', dateTo: '2026-09-07', state: 'DATA', metrics: {
      present: { count: 1, percent: 100 }, presentOrExcused: { count: 1, percent: 100 }, excused: { count: 0, percent: 0 }, absent: { count: 0, percent: 0 }, held: 1, planned: 1,
    } },
    { id: 'days:2026-09-01', label: '1', dateFrom: '2026-09-01', dateTo: '2026-09-01', state: 'NO_DATA', metrics: {
      present: { count: 0, percent: null }, presentOrExcused: { count: 0, percent: null }, excused: { count: 0, percent: null }, absent: { count: 0, percent: null }, held: 0, planned: 0,
    } },
  ],
  typeCards: [],
} satisfies StatisticsSubjectDetailData

describe('statistics controlled projection helpers', () => {
  it('keeps null and zero display values distinct', () => {
    expect(displayPercent(null)).toBe('—')
    expect(displayPercent(0)).toBe('0%')
    expect(displayPercent(66.66)).toBe('67%')
  })

  it('preserves canonical type order and cannot produce an empty selection', () => {
    expect(toggleTypeSelection(['LAB', 'LECTURE'], 'LECTURE')).toEqual(['LAB'])
    expect(toggleTypeSelection(['LECTURE'], 'LECTURE')).toEqual(['LECTURE'])
    expect(toggleTypeSelection(['LECTURE'], 'PRACTICE')).toEqual(['LECTURE', 'PRACTICE'])
    expect(typeSelectionContains(detail.selectedTypes, 'LAB')).toBe(true)
  })

  it('selects the supplied server bucket without client aggregation', () => {
    expect(seriesForRange(detail, 'weeks')).toHaveLength(2)
    expect(seriesForRange(detail, 'days')).toHaveLength(2)
    expect(seriesForRange(detail, 'weeks')[0]?.id).toBe('g:123')
  })

  it('exposes full Russian labels for history segments', () => {
    expect(historyStatusAccessibleLabel('PRESENT')).toBe('присутствовал')
    expect(historyStatusAccessibleLabel('ABSENT')).toBe('отсутствовал')
    expect(historyStatusAccessibleLabel('EXCUSED')).toBe('уважительная причина')
    expect(historyStatusAccessibleLabel('FUTURE')).toBe('будет')
    expect(historyStatusAccessibleLabel('NO_DATA')).toBe('нет данных')
  })
})
