import { describe, expect, it } from 'vitest'
import {
  displayPercent,
  historyStatusAccessibleLabel,
  seriesForRange,
  statisticsRankingName,
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
    present: { count: 2, percent: 50 },
    presentOrExcused: { count: 3, percent: 75 },
    excused: { count: 1, percent: 25 },
    absent: { count: 1, percent: 25 },
    held: 4,
    planned: 6,
  },
  series: [
    {
      id: 'g:123',
      label: '1',
      dateFrom: '2026-09-01',
      dateTo: '2026-09-07',
      state: 'DATA',
      metrics: {
        present: { count: 1, percent: 100 },
        presentOrExcused: { count: 1, percent: 100 },
        excused: { count: 0, percent: 0 },
        absent: { count: 0, percent: 0 },
        held: 1,
        planned: 1,
      },
    },
    {
      id: 'days:2026-09-01',
      label: '1',
      dateFrom: '2026-09-01',
      dateTo: '2026-09-01',
      state: 'NO_DATA',
      metrics: {
        present: { count: 0, percent: null },
        presentOrExcused: { count: 0, percent: null },
        excused: { count: 0, percent: null },
        absent: { count: 0, percent: null },
        held: 0,
        planned: 0,
      },
    },
  ],
  typeCards: [],
} satisfies StatisticsSubjectDetailData

describe('statistics controlled projection helpers', () => {
  it('keeps null and zero display values distinct', () => {
    expect(displayPercent(null)).toBe('—')
    expect(displayPercent(0)).toBe('0%')
    expect(displayPercent(66.66)).toBe('67%')
  })

  it('preserves canonical type order and allows deselecting the last type', () => {
    expect(toggleTypeSelection(['LAB', 'LECTURE'], 'LECTURE')).toEqual(['LAB'])
    expect(toggleTypeSelection(['LECTURE'], 'LECTURE')).toEqual([])
    expect(toggleTypeSelection(['LECTURE'], 'PRACTICE')).toEqual(['LECTURE', 'PRACTICE'])
    expect(typeSelectionContains(detail.selectedTypes, 'LAB')).toBe(true)
  })

  it('keeps weeks unchanged and drops only authoritative zero-schedule days', () => {
    const scheduledUnknown = {
      ...detail.series[1]!,
      id: 'unknown-with-lesson',
      metrics: { ...detail.series[1]!.metrics, planned: 1 },
    }
    const future = { ...scheduledUnknown, id: 'future-with-lesson', state: 'FUTURE' as const }
    const source = {
      ...detail,
      series: [detail.series[0]!, detail.series[1]!, scheduledUnknown, future],
    }
    expect(seriesForRange(source, 'weeks')).toBe(source.series)
    expect(seriesForRange(source, 'days')).toEqual([detail.series[0]!, scheduledUnknown, future])
    expect(seriesForRange(source, 'days')[1]!.metrics.present.percent).toBeNull()
    expect(seriesForRange(source, 'days')[2]!.state).toBe('FUTURE')
    expect(source.series).toHaveLength(4)
  })

  it('formats surname and initials without losing a hyphenated surname or Unicode initial', () => {
    expect(statisticsRankingName('  Иванов-Петров  Иван   Олегович ')).toBe('Иванов-Петров И. О.')
    expect(statisticsRankingName('Ли Я')).toBe('Ли Я.')
    expect(statisticsRankingName('Сингл')).toBe('Сингл')
    expect(statisticsRankingName('')).toBe('')
  })

  it('exposes full Russian labels for history segments', () => {
    expect(historyStatusAccessibleLabel('PRESENT')).toBe('присутствовал')
    expect(historyStatusAccessibleLabel('ABSENT')).toBe('отсутствовал')
    expect(historyStatusAccessibleLabel('EXCUSED')).toBe('уважительная причина')
    expect(historyStatusAccessibleLabel('FUTURE')).toBe('будет')
    expect(historyStatusAccessibleLabel('NO_DATA')).toBe('нет данных')
  })
})
