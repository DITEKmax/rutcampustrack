import { describe, it, expect } from 'vitest';
import { deriveStudentChartData, deriveOverallStats } from './stats-utils';
import type { JournalResponse } from '../journal/types';

function makeJournal(overrides: Partial<JournalResponse> = {}): JournalResponse {
  return {
    groupId: 1,
    subjectId: 10,
    dates: ['2026-03-01', '2026-03-02', '2026-03-03'],
    students: [],
    ...overrides,
  };
}

describe('deriveStudentChartData', () => {
  it('returns correct counts per student with 2 students, 3 records each', () => {
    const journal = makeJournal({
      students: [
        {
          userId: 1,
          displayName: 'Иванов Иван',
          records: [
            { date: '2026-03-01', lessonNumber: 1, status: 'present', symbol: 'б' },
            { date: '2026-03-02', lessonNumber: 1, status: 'absent', symbol: 'н' },
            { date: '2026-03-03', lessonNumber: 1, status: 'excused', symbol: 'у' },
          ],
        },
        {
          userId: 2,
          displayName: 'Петров Пётр',
          records: [
            { date: '2026-03-01', lessonNumber: 1, status: 'present', symbol: 'б' },
            { date: '2026-03-02', lessonNumber: 1, status: 'free_attendance', symbol: 'сп' },
            { date: '2026-03-03', lessonNumber: 1, status: 'absent', symbol: 'н' },
          ],
        },
      ],
    });
    const result = deriveStudentChartData(journal);
    expect(result).toHaveLength(2);
    expect(result[0]).toMatchObject({ present: 1, absent: 1, excused: 1, freeAttendance: 0 });
    expect(result[1]).toMatchObject({ present: 1, absent: 1, excused: 0, freeAttendance: 1 });
  });

  it('excludes cancelled records from counts', () => {
    const journal = makeJournal({
      students: [
        {
          userId: 1,
          displayName: 'Иванов Иван',
          records: [
            { date: '2026-03-01', lessonNumber: 1, status: 'present', symbol: 'б' },
            { date: '2026-03-02', lessonNumber: 1, status: 'cancelled', symbol: '—' },
          ],
        },
      ],
    });
    const result = deriveStudentChartData(journal);
    expect(result[0]).toMatchObject({ present: 1, absent: 0, excused: 0, freeAttendance: 0 });
  });

  it('truncates displayName at 16 chars with ellipsis', () => {
    const journal = makeJournal({
      students: [
        {
          userId: 1,
          displayName: 'Очень Длинное Имя Студента',
          records: [],
        },
      ],
    });
    const result = deriveStudentChartData(journal);
    expect(result[0].name).toBe('Очень Длинное Им...');
    expect(result[0].name.length).toBeLessThanOrEqual(19); // 16 + 3 dots
  });

  it('does not truncate names of 16 chars or fewer', () => {
    const journal = makeJournal({
      students: [
        {
          userId: 1,
          displayName: 'Иванов Иван',
          records: [],
        },
      ],
    });
    const result = deriveStudentChartData(journal);
    expect(result[0].name).toBe('Иванов Иван');
  });

  it('returns empty array for empty students', () => {
    const journal = makeJournal({ students: [] });
    const result = deriveStudentChartData(journal);
    expect(result).toEqual([]);
  });
});

describe('deriveOverallStats', () => {
  it('returns total unique lessons and attendance percentage', () => {
    const journal = makeJournal({
      students: [
        {
          userId: 1,
          displayName: 'Иванов',
          records: [
            { date: '2026-03-01', lessonNumber: 1, status: 'present', symbol: 'б' },
            { date: '2026-03-02', lessonNumber: 1, status: 'absent', symbol: 'н' },
          ],
        },
        {
          userId: 2,
          displayName: 'Петров',
          records: [
            { date: '2026-03-01', lessonNumber: 1, status: 'present', symbol: 'б' },
            { date: '2026-03-02', lessonNumber: 1, status: 'present', symbol: 'б' },
          ],
        },
      ],
    });
    const result = deriveOverallStats(journal);
    // 2 unique date+lessonNumber pairs
    expect(result.totalLessons).toBe(2);
    // 3 present out of 4 total = 75%
    expect(result.attendanceRate).toBe(75);
  });

  it('returns 100% when all students are present', () => {
    const journal = makeJournal({
      students: [
        {
          userId: 1,
          displayName: 'Иванов',
          records: [
            { date: '2026-03-01', lessonNumber: 1, status: 'present', symbol: 'б' },
          ],
        },
      ],
    });
    const result = deriveOverallStats(journal);
    expect(result.attendanceRate).toBe(100);
  });

  it('returns { totalLessons: 0, attendanceRate: 0 } for empty students array', () => {
    const journal = makeJournal({ students: [] });
    const result = deriveOverallStats(journal);
    expect(result).toEqual({ totalLessons: 0, attendanceRate: 0 });
  });

  it('excludes cancelled lessons from unique lesson count', () => {
    const journal = makeJournal({
      students: [
        {
          userId: 1,
          displayName: 'Иванов',
          records: [
            { date: '2026-03-01', lessonNumber: 1, status: 'present', symbol: 'б' },
            { date: '2026-03-02', lessonNumber: 1, status: 'cancelled', symbol: '—' },
          ],
        },
      ],
    });
    const result = deriveOverallStats(journal);
    expect(result.totalLessons).toBe(1); // only the non-cancelled one
  });
});
