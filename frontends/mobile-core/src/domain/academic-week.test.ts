import { describe, expect, it } from 'vitest'
import { academicDate, academicWeekOrdinal, normalizeAcademicWeekPolicy } from './academic-week'
const policy = {
  weekStart: '2026-10-05',
  weekEnd: '2026-10-11',
  isoWeek: 41,
  weekType: 'EVEN',
  parityNumber: 2,
  label: '2',
  weekTypes: [
    { weekType: 'ALL', parityNumber: null, label: 'Каждую неделю' },
    { weekType: 'ODD', parityNumber: 1, label: '1' },
    { weekType: 'EVEN', parityNumber: 2, label: '2' },
  ],
}
describe('authoritative academic week metadata and independent ordinal geometry', () => {
  it('retains authoritative labels without calculating or replacing server parity', () => {
    const authoritative = {
      ...policy,
      weekType: 'ODD',
      parityNumber: 1,
      label: 'Серверная подпись',
      weekTypes: policy.weekTypes.map((item) =>
        item.weekType === 'ODD' ? { ...item, label: 'Серверная подпись' } : item,
      ),
    }
    expect(normalizeAcademicWeekPolicy(authoritative, '2026-10-06').label).toBe('Серверная подпись')
  })
  it('rejects stale-week, malformed calendar and inconsistent catalog responses', () => {
    expect(() => normalizeAcademicWeekPolicy(policy, '2026-10-12')).toThrow()
    expect(() =>
      normalizeAcademicWeekPolicy({ ...policy, weekEnd: '2026-10-12' }, '2026-10-06'),
    ).toThrow()
    expect(() =>
      normalizeAcademicWeekPolicy({ ...policy, parityNumber: 1 }, '2026-10-06'),
    ).toThrow()
    expect(academicDate('2026-02-30')).toBeNull()
  })
  it('keeps partial first weeks and ISO-year boundaries separate from parity', () => {
    expect(academicWeekOrdinal('2026-09-01', '2026-09-01', '2027-01-31')).toBe(1)
    expect(academicWeekOrdinal('2026-09-06', '2026-09-01', '2027-01-31')).toBe(1)
    expect(academicWeekOrdinal('2026-09-07', '2026-09-01', '2027-01-31')).toBe(2)
    expect(academicWeekOrdinal('2021-01-01', '2020-12-29', '2021-06-30')).toBe(1)
    expect(academicWeekOrdinal('2021-01-04', '2020-12-29', '2021-06-30')).toBe(2)
    expect(academicWeekOrdinal('2026-08-31', '2026-09-01', '2027-01-31')).toBeNull()
    expect(academicWeekOrdinal('2027-02-01', '2026-09-01', '2027-01-31')).toBeNull()
  })
})
