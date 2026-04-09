/**
 * Pure date helpers for the student schedule page (Phase 51 — Plan 02).
 *
 * Mirrors `frontends/pwa/src/features/schedule/SchedulePage.tsx` lines 15-60
 * so the web-panel schedule view stays byte-for-byte aligned with the PWA
 * contract. No Angular imports — this module can be consumed by any Angular
 * component, service, or spec.
 *
 * Week model:
 * - ISO week — Monday is the first day of the week.
 * - Sunday (`getDay() === 0`) snaps back to the previous Monday.
 * - Only 6 days are visible in the UI (Пн..Сб); Sunday collapses into Sb.
 *
 * Timezone: all helpers operate on the host's local timezone. The user's
 * device (Moscow or otherwise) is trusted — matches the PWA behavior.
 */

export const MONTH_ABBREV = [
  'янв',
  'фев',
  'мар',
  'апр',
  'мая',
  'июн',
  'июл',
  'авг',
  'сен',
  'окт',
  'ноя',
  'дек',
] as const;

/**
 * Return the Monday of the ISO week containing `date`.
 * Clamps the time to 00:00:00.000 local.
 */
export function getMonday(date: Date): Date {
  const d = new Date(date);
  const day = d.getDay(); // 0=Sun..6=Sat
  const diff = day === 0 ? -6 : 1 - day; // shift to ISO Monday
  d.setDate(d.getDate() + diff);
  d.setHours(0, 0, 0, 0);
  return d;
}

/** Non-mutating addition of `days` to `date`. */
export function addDays(date: Date, days: number): Date {
  const d = new Date(date);
  d.setDate(d.getDate() + days);
  return d;
}

/** Zero-padded `YYYY-MM-DD` in local time. Used as backend query param. */
export function formatDate(date: Date): string {
  const y = date.getFullYear();
  const m = String(date.getMonth() + 1).padStart(2, '0');
  const d = String(date.getDate()).padStart(2, '0');
  return `${y}-${m}-${d}`;
}

/**
 * Today's index in the UI day-tab bar (0=Пн..5=Сб).
 * Sunday is collapsed into Сб (index 5) since the schedule never shows Вс.
 */
export function getTodayDayIndex(now: Date = new Date()): number {
  const dow = now.getDay();
  if (dow === 0) return 5; // Sun -> Sb
  return dow - 1; // Mon=0..Sat=5
}

/**
 * Human-readable week-range label for the week-nav strip.
 *
 * - Same month:  `6-11 апр`
 * - Across months: `30 мар - 4 апр`
 */
export function formatWeekRange(monday: Date): string {
  const saturday = addDays(monday, 5);
  const startDay = monday.getDate();
  const endDay = saturday.getDate();
  if (monday.getMonth() === saturday.getMonth()) {
    return `${startDay}-${endDay} ${MONTH_ABBREV[saturday.getMonth()]}`;
  }
  return `${startDay} ${MONTH_ABBREV[monday.getMonth()]} - ${endDay} ${MONTH_ABBREV[saturday.getMonth()]}`;
}

/** Two dates fall in the same ISO week if they share a Monday. */
export function isSameWeek(a: Date, b: Date): boolean {
  return getMonday(a).getTime() === getMonday(b).getTime();
}

/** Backend sends `HH:mm:ss`; the UI only displays `HH:mm`. */
export function formatLessonTime(time: string): string {
  return time.slice(0, 5);
}
