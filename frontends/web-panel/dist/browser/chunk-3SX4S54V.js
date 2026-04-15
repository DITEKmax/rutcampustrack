// src/app/features/student/schedule/week-utils.ts
var MONTH_ABBREV = [
  "\u044F\u043D\u0432",
  "\u0444\u0435\u0432",
  "\u043C\u0430\u0440",
  "\u0430\u043F\u0440",
  "\u043C\u0430\u044F",
  "\u0438\u044E\u043D",
  "\u0438\u044E\u043B",
  "\u0430\u0432\u0433",
  "\u0441\u0435\u043D",
  "\u043E\u043A\u0442",
  "\u043D\u043E\u044F",
  "\u0434\u0435\u043A"
];
function getMonday(date) {
  const d = new Date(date);
  const day = d.getDay();
  const diff = day === 0 ? -6 : 1 - day;
  d.setDate(d.getDate() + diff);
  d.setHours(0, 0, 0, 0);
  return d;
}
function addDays(date, days) {
  const d = new Date(date);
  d.setDate(d.getDate() + days);
  return d;
}
function formatDate(date) {
  const y = date.getFullYear();
  const m = String(date.getMonth() + 1).padStart(2, "0");
  const d = String(date.getDate()).padStart(2, "0");
  return `${y}-${m}-${d}`;
}
function getTodayDayIndex(now = /* @__PURE__ */ new Date()) {
  const dow = now.getDay();
  if (dow === 0)
    return 5;
  return dow - 1;
}
function formatWeekRange(monday) {
  const saturday = addDays(monday, 5);
  const startDay = monday.getDate();
  const endDay = saturday.getDate();
  if (monday.getMonth() === saturday.getMonth()) {
    return `${startDay}-${endDay} ${MONTH_ABBREV[saturday.getMonth()]}`;
  }
  return `${startDay} ${MONTH_ABBREV[monday.getMonth()]} - ${endDay} ${MONTH_ABBREV[saturday.getMonth()]}`;
}
function isSameWeek(a, b) {
  return getMonday(a).getTime() === getMonday(b).getTime();
}
function formatLessonTime(time) {
  return time.slice(0, 5);
}

export {
  getMonday,
  addDays,
  formatDate,
  getTodayDayIndex,
  formatWeekRange,
  isSameWeek,
  formatLessonTime
};
//# sourceMappingURL=chunk-3SX4S54V.js.map
