import { describe, it, expect, vi, afterEach } from 'vitest';
import {
  addDays,
  formatDate,
  formatLessonTime,
  formatWeekRange,
  getMonday,
  getTodayDayIndex,
  isSameWeek,
  MONTH_ABBREV,
} from './week-utils';

describe('week-utils', () => {
  afterEach(() => {
    vi.useRealTimers();
  });

  describe('getMonday', () => {
    it('returns the Monday of the week containing a Thursday', () => {
      // Apr 9 2026 is a Thursday — Monday of that week is Apr 6 2026.
      const thu = new Date(2026, 3, 9, 12, 0, 0);
      const mon = getMonday(thu);
      expect(mon.getFullYear()).toBe(2026);
      expect(mon.getMonth()).toBe(3); // April
      expect(mon.getDate()).toBe(6);
      expect(mon.getHours()).toBe(0);
      expect(mon.getMinutes()).toBe(0);
      expect(mon.getSeconds()).toBe(0);
      expect(mon.getMilliseconds()).toBe(0);
    });

    it('snaps Sunday back to the previous Monday (not the next one)', () => {
      // Apr 12 2026 is Sunday — the previous Monday is Apr 6 2026.
      const sun = new Date(2026, 3, 12, 12, 0, 0);
      const mon = getMonday(sun);
      expect(mon.getFullYear()).toBe(2026);
      expect(mon.getMonth()).toBe(3);
      expect(mon.getDate()).toBe(6);
    });

    it('returns the same Monday when called with a Monday', () => {
      const mon = new Date(2026, 3, 6, 15, 30, 0);
      const result = getMonday(mon);
      expect(result.getDate()).toBe(6);
      expect(result.getHours()).toBe(0);
    });

    it('does not mutate the input date', () => {
      const thu = new Date(2026, 3, 9, 12, 0, 0);
      const before = thu.getTime();
      getMonday(thu);
      expect(thu.getTime()).toBe(before);
    });
  });

  describe('addDays', () => {
    it('adds a positive offset', () => {
      const monday = new Date(2026, 3, 6);
      const saturday = addDays(monday, 5);
      expect(saturday.getDate()).toBe(11);
      expect(saturday.getMonth()).toBe(3);
    });

    it('subtracts a negative offset', () => {
      const monday = new Date(2026, 3, 6);
      const prev = addDays(monday, -7);
      expect(prev.getDate()).toBe(30);
      expect(prev.getMonth()).toBe(2); // March
    });

    it('does not mutate the input date', () => {
      const d = new Date(2026, 3, 6);
      const before = d.getTime();
      addDays(d, 10);
      expect(d.getTime()).toBe(before);
    });
  });

  describe('formatDate', () => {
    it('returns zero-padded YYYY-MM-DD', () => {
      expect(formatDate(new Date(2026, 3, 6))).toBe('2026-04-06');
    });

    it('pads single-digit months and days', () => {
      expect(formatDate(new Date(2026, 0, 3))).toBe('2026-01-03');
    });
  });

  describe('formatWeekRange', () => {
    it('formats same-month range as `D1-D2 mmm`', () => {
      // Monday Apr 6 2026 → Saturday Apr 11 2026 (same month)
      const monday = new Date(2026, 3, 6);
      expect(formatWeekRange(monday)).toBe('6-11 апр');
    });

    it('formats across-months range as `D1 mmm1 - D2 mmm2`', () => {
      // Monday Mar 30 2026 → Saturday Apr 4 2026
      const monday = new Date(2026, 2, 30);
      expect(formatWeekRange(monday)).toBe('30 мар - 4 апр');
    });

    it('uses the short MONTH_ABBREV dictionary for every month', () => {
      expect(MONTH_ABBREV.length).toBe(12);
      expect(MONTH_ABBREV[0]).toBe('янв');
      expect(MONTH_ABBREV[11]).toBe('дек');
    });
  });

  describe('getTodayDayIndex', () => {
    it('returns 3 when today is a Thursday', () => {
      vi.useFakeTimers();
      vi.setSystemTime(new Date(2026, 3, 9, 12, 0, 0)); // Thursday
      expect(getTodayDayIndex()).toBe(3);
    });

    it('returns 5 (Сб) when today is a Sunday', () => {
      vi.useFakeTimers();
      vi.setSystemTime(new Date(2026, 3, 12, 12, 0, 0)); // Sunday
      expect(getTodayDayIndex()).toBe(5);
    });

    it('returns 0 when today is Monday', () => {
      vi.useFakeTimers();
      vi.setSystemTime(new Date(2026, 3, 6, 12, 0, 0)); // Monday
      expect(getTodayDayIndex()).toBe(0);
    });
  });

  describe('isSameWeek', () => {
    it('returns true for two dates in the same ISO week', () => {
      const monday = new Date(2026, 3, 6);
      expect(isSameWeek(monday, addDays(monday, 3))).toBe(true);
    });

    it('returns false for dates 8 days apart (different weeks)', () => {
      const monday = new Date(2026, 3, 6);
      expect(isSameWeek(monday, addDays(monday, 8))).toBe(false);
    });
  });

  describe('formatLessonTime', () => {
    it('strips seconds from HH:mm:ss', () => {
      expect(formatLessonTime('09:00:00')).toBe('09:00');
      expect(formatLessonTime('11:30:00')).toBe('11:30');
    });
  });
});
