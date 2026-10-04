import { describe, expect, it } from 'vitest'
import { attendanceBounds, calendarWeek, graphPeriods, initialPeriodPage, periodState, serverDate } from './attendance-periods'
import type { AttendanceGraphPoint, AttendanceMetricSet, AttendanceViewModel } from './attendance-view-model'
import { attendanceFixture, createAttendanceReviewScenarios } from '../../dev/attendance-fixtures'

const metrics: AttendanceMetricSet = { present: { count: 1, percent: 25 }, presentOrExcused: { count: 2, percent: 50 }, excused: { count: 1, percent: 25 }, absent: { count: 2, percent: 50 }, held: 4, planned: 4 }
function point(date: string, values = metrics): AttendanceGraphPoint { return { id: date, label: date, dateFrom: date, dateTo: date, state: 'DATA', metrics: values } }
function data(): AttendanceViewModel { return { semester: { id: 's', name: 'Осень', dateFrom: '2026-09-01', dateTo: '2026-10-31' }, serverNow: '2026-10-04T10:00:00Z', metrics, days: [], subjects: [], graph: { days: [point('2026-09-01'), point('2026-09-03')], weeks: [{ ...point('2026-09-01'), dateTo: '2026-09-06' }] } } }

describe('attendance civil periods', () => {
  it('retains missing calendar dates and empty Monday–Saturday instead of compressing data', () => {
    const result = graphPeriods(data(), 'days')
    expect(result.slice(0, 7).map((item) => item.dateFrom)).toEqual(['2026-08-31', '2026-09-01', '2026-09-02', '2026-09-03', '2026-09-04', '2026-09-05', '2026-09-06'])
    expect(result[0]?.outsideSemester).toBe(true)
    expect(result[2]?.missing).toBe(true)
    expect(result[2]?.metrics.present.percent).toBeNull()
    expect(calendarWeek(data(), '2026-09-03').map((day) => day.date)).toEqual(result.slice(0, 7).map((item) => item.dateFrom))
  })
  it('uses server weighted weekly metrics in Monday–Sunday buckets', () => {
    const result = graphPeriods(data(), 'weeks')
    expect(result[0]?.dateFrom).toBe('2026-08-31')
    expect(result[0]?.dateTo).toBe('2026-09-06')
    expect(result[0]?.metrics).toBe(metrics)
    expect(result[0]?.metrics.present.percent).toBe(25)
  })
  it('keeps QA weekly weights and semester totals consistent with daily counts', () => {
    const fixture = attendanceFixture()
    for (const week of fixture.graph.weeks) {
      const days = fixture.graph.days.filter((day) => day.dateFrom >= week.dateFrom && day.dateFrom <= week.dateTo)
      expect(week.metrics.held).toBe(days.reduce((total, day) => total + day.metrics.held, 0))
      expect(week.metrics.present.count).toBe(days.reduce((total, day) => total + day.metrics.present.count, 0))
      if (week.metrics.held) expect(week.metrics.present.percent).toBeCloseTo(week.metrics.present.count / week.metrics.held * 100)
    }
    expect(fixture.metrics.held).toBe(fixture.graph.weeks.reduce((total, week) => total + week.metrics.held, 0))
    expect(fixture.metrics.present.count).toBe(fixture.graph.weeks.reduce((total, week) => total + week.metrics.present.count, 0))
    expect(fixture.metrics.excused.count).toBe(fixture.graph.weeks.reduce((total, week) => total + week.metrics.excused.count, 0))
    expect(fixture.metrics.absent.count).toBe(fixture.graph.weeks.reduce((total, week) => total + week.metrics.absent.count, 0))
    expect(fixture.metrics.planned).toBe(fixture.graph.weeks.reduce((total, week) => total + week.metrics.planned, 0))
    for (const scene of createAttendanceReviewScenarios().filter((item) => item.mode === 'graph')) {
      if (scene.state.status !== 'ready') continue
      const model = scene.state.data
      expect(model.metrics.held).toBe(model.graph.days.reduce((total, day) => total + day.metrics.held, 0))
      expect(model.metrics.present.count).toBe(model.graph.days.reduce((total, day) => total + day.metrics.present.count, 0))
    }
  })
  it('keeps real zero, held-zero, no marks, no considered classes and future distinct', () => {
    const zero = { ...metrics, present: { count: 0, percent: 0 } }
    expect(periodState(point('2026-09-01', zero))).toBe('Данные доступны')
    expect(periodState(point('2026-09-01', { ...metrics, held: 0 }))).toBe('Нет закрытых учитываемых пар')
    expect(periodState(point('2026-09-01', { ...metrics, present: { count: 0, percent: null } }))).toBe('Нет данных об отметках')
    expect(periodState(graphPeriods(data(), 'days')[2]!)).toBe('Нет учитываемых пар')
    expect(graphPeriods(data(), 'days').find((item) => item.dateFrom === '2026-10-05')?.state).toBe('FUTURE')
    const currentFuture = graphPeriods({ ...data(), graph: { days: [{ ...point('2026-10-04'), state: 'FUTURE' }], weeks: [] } }, 'days').find((item) => item.dateFrom === '2026-10-04')!
    expect(periodState(currentFuture)).toBe('Пары ещё не завершены')
  })
  it('opens nearest server-date page with Moscow civil-date rollover and preserves full-semester bounds', () => {
    expect(serverDate('2026-10-03T22:15:00Z')).toBe('2026-10-04')
    expect(initialPeriodPage(graphPeriods(data(), 'days'), 'days', data().serverNow)).toBe(4)
    expect(initialPeriodPage(graphPeriods(data(), 'weeks'), 'weeks', '2026-10-31T12:00:00Z')).toBe(1)
    expect(attendanceBounds(data())).toEqual({ from: '2026-09-01', to: '2026-10-31' })
    expect(graphPeriods({ metrics, days: [], subjects: [], graph: { days: [point('2026-09-03')], weeks: [] } }, 'days')).toHaveLength(7)
  })
})
