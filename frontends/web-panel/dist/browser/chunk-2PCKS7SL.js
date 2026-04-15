import {
  HeadmanApiService
} from "./chunk-ZC6IXG25.js";
import {
  MatProgressSpinner,
  MatProgressSpinnerModule
} from "./chunk-VOF6QK53.js";
import {
  MatSnackBar
} from "./chunk-5Q6RVIG4.js";
import {
  addDays,
  formatDate
} from "./chunk-3SX4S54V.js";
import "./chunk-OYYF72SB.js";
import {
  animate,
  style,
  transition,
  trigger
} from "./chunk-JKCQIGK6.js";
import {
  AuthService
} from "./chunk-ID5GKTOO.js";
import "./chunk-OIBNGD5S.js";
import {
  MatButtonModule
} from "./chunk-GTDHNJL7.js";
import "./chunk-2WN7CIGJ.js";
import "./chunk-ZEX6QU7O.js";
import "./chunk-G4JX6AWT.js";
import "./chunk-YIJEORIR.js";
import "./chunk-FW2NJ6PM.js";
import "./chunk-FLE4DLW4.js";
import {
  CommonModule
} from "./chunk-CLRYRPPS.js";
import {
  ChangeDetectionStrategy,
  Component,
  __spreadProps,
  __spreadValues,
  computed,
  inject,
  setClassMetadata,
  signal,
  ɵsetClassDebugInfo,
  ɵɵadvance,
  ɵɵclassMapInterpolate1,
  ɵɵclassProp,
  ɵɵconditional,
  ɵɵdefineComponent,
  ɵɵelement,
  ɵɵelementEnd,
  ɵɵelementStart,
  ɵɵgetCurrentView,
  ɵɵlistener,
  ɵɵnextContext,
  ɵɵproperty,
  ɵɵrepeater,
  ɵɵrepeaterCreate,
  ɵɵresetView,
  ɵɵrestoreView,
  ɵɵtemplate,
  ɵɵtext,
  ɵɵtextInterpolate,
  ɵɵtextInterpolate1,
  ɵɵtextInterpolate2
} from "./chunk-4M4FDLBS.js";

// src/app/features/headman/lessons/headman-lessons.component.ts
var _forTrack0 = ($index, $item) => $item.date;
var _forTrack1 = ($index, $item) => $item.id;
function HeadmanLessonsComponent_Conditional_7_Template(rf, ctx) {
  if (rf & 1) {
    \u0275\u0275elementStart(0, "div", 4);
    \u0275\u0275element(1, "mat-spinner", 7);
    \u0275\u0275elementEnd();
  }
}
function HeadmanLessonsComponent_Conditional_8_Template(rf, ctx) {
  if (rf & 1) {
    \u0275\u0275elementStart(0, "div", 5);
    \u0275\u0275element(1, "i", 8);
    \u0275\u0275text(2);
    \u0275\u0275elementEnd();
  }
  if (rf & 2) {
    const ctx_r0 = \u0275\u0275nextContext();
    \u0275\u0275advance(2);
    \u0275\u0275textInterpolate1(" ", ctx_r0.error(), "");
  }
}
function HeadmanLessonsComponent_Conditional_9_Template(rf, ctx) {
  if (rf & 1) {
    \u0275\u0275elementStart(0, "div", 6);
    \u0275\u0275element(1, "i", 9);
    \u0275\u0275elementStart(2, "h3");
    \u0275\u0275text(3, "\u041F\u0430\u0440 \u043D\u0435\u0442");
    \u0275\u0275elementEnd();
    \u0275\u0275elementStart(4, "p");
    \u0275\u0275text(5);
    \u0275\u0275elementEnd()();
  }
  if (rf & 2) {
    const ctx_r0 = \u0275\u0275nextContext();
    \u0275\u0275advance(5);
    \u0275\u0275textInterpolate1("\u041D\u0430 \u0431\u043B\u0438\u0436\u0430\u0439\u0448\u0438\u0435 ", ctx_r0.rangeDays, " \u0434\u043D. \u0432 \u0440\u0430\u0441\u043F\u0438\u0441\u0430\u043D\u0438\u0438 \u043D\u0435\u0442 \u043D\u0438 \u043E\u0434\u043D\u043E\u0439 \u043F\u0430\u0440\u044B.");
  }
}
function HeadmanLessonsComponent_Conditional_10_For_1_For_5_Conditional_9_Template(rf, ctx) {
  if (rf & 1) {
    \u0275\u0275elementStart(0, "span", 20);
    \u0275\u0275text(1);
    \u0275\u0275elementEnd();
  }
  if (rf & 2) {
    \u0275\u0275advance();
    \u0275\u0275textInterpolate1("\u2014 ", ctx, "");
  }
}
function HeadmanLessonsComponent_Conditional_10_For_1_For_5_Conditional_11_Template(rf, ctx) {
  if (rf & 1) {
    \u0275\u0275elementStart(0, "span");
    \u0275\u0275text(1);
    \u0275\u0275elementEnd();
  }
  if (rf & 2) {
    const lesson_r2 = \u0275\u0275nextContext().$implicit;
    \u0275\u0275advance();
    \u0275\u0275textInterpolate(lesson_r2.room);
  }
}
function HeadmanLessonsComponent_Conditional_10_For_1_For_5_Conditional_14_Template(rf, ctx) {
  if (rf & 1) {
    \u0275\u0275elementStart(0, "span", 22);
    \u0275\u0275text(1);
    \u0275\u0275elementEnd();
  }
  if (rf & 2) {
    const lesson_r2 = \u0275\u0275nextContext().$implicit;
    \u0275\u0275advance();
    \u0275\u0275textInterpolate1("\u2014 ", lesson_r2.cancelReason, "");
  }
}
function HeadmanLessonsComponent_Conditional_10_For_1_For_5_Conditional_16_Template(rf, ctx) {
  if (rf & 1) {
    const _r3 = \u0275\u0275getCurrentView();
    \u0275\u0275elementStart(0, "button", 26);
    \u0275\u0275listener("click", function HeadmanLessonsComponent_Conditional_10_For_1_For_5_Conditional_16_Template_button_click_0_listener() {
      \u0275\u0275restoreView(_r3);
      const lesson_r2 = \u0275\u0275nextContext().$implicit;
      const ctx_r0 = \u0275\u0275nextContext(3);
      return \u0275\u0275resetView(ctx_r0.onCancel(lesson_r2));
    });
    \u0275\u0275element(1, "i", 27);
    \u0275\u0275text(2, " \u041E\u0442\u043C\u0435\u043D\u0438\u0442\u044C ");
    \u0275\u0275elementEnd();
  }
  if (rf & 2) {
    const lesson_r2 = \u0275\u0275nextContext().$implicit;
    const ctx_r0 = \u0275\u0275nextContext(3);
    \u0275\u0275property("disabled", ctx_r0.busy() === lesson_r2.id)("title", ctx_r0.isClosed(lesson_r2) ? "\u041E\u0442\u043C\u0435\u043D\u0438\u0442\u044C \u0443\u0436\u0435 \u043F\u0440\u043E\u0448\u0435\u0434\u0448\u0443\u044E \u043F\u0430\u0440\u0443 (\u0434\u043B\u044F \u0438\u0441\u0442\u043E\u0440\u0438\u0447\u0435\u0441\u043A\u0438\u0445 \u0434\u0430\u043D\u043D\u044B\u0445)" : "\u041E\u0442\u043C\u0435\u043D\u0438\u0442\u044C \u043F\u0430\u0440\u0443");
  }
}
function HeadmanLessonsComponent_Conditional_10_For_1_For_5_Conditional_17_Template(rf, ctx) {
  if (rf & 1) {
    const _r4 = \u0275\u0275getCurrentView();
    \u0275\u0275elementStart(0, "button", 28);
    \u0275\u0275listener("click", function HeadmanLessonsComponent_Conditional_10_For_1_For_5_Conditional_17_Template_button_click_0_listener() {
      \u0275\u0275restoreView(_r4);
      const lesson_r2 = \u0275\u0275nextContext().$implicit;
      const ctx_r0 = \u0275\u0275nextContext(3);
      return \u0275\u0275resetView(ctx_r0.onRestore(lesson_r2));
    });
    \u0275\u0275element(1, "i", 29);
    \u0275\u0275text(2, " \u0412\u043E\u0441\u0441\u0442\u0430\u043D\u043E\u0432\u0438\u0442\u044C ");
    \u0275\u0275elementEnd();
  }
  if (rf & 2) {
    const lesson_r2 = \u0275\u0275nextContext().$implicit;
    const ctx_r0 = \u0275\u0275nextContext(3);
    \u0275\u0275property("disabled", ctx_r0.busy() === lesson_r2.id);
  }
}
function HeadmanLessonsComponent_Conditional_10_For_1_For_5_Template(rf, ctx) {
  if (rf & 1) {
    \u0275\u0275elementStart(0, "li", 14)(1, "div", 15)(2, "span", 16);
    \u0275\u0275text(3);
    \u0275\u0275elementEnd();
    \u0275\u0275elementStart(4, "span", 17);
    \u0275\u0275text(5);
    \u0275\u0275elementEnd()();
    \u0275\u0275elementStart(6, "div", 18)(7, "div", 19);
    \u0275\u0275text(8);
    \u0275\u0275template(9, HeadmanLessonsComponent_Conditional_10_For_1_For_5_Conditional_9_Template, 2, 1, "span", 20);
    \u0275\u0275elementEnd();
    \u0275\u0275elementStart(10, "div", 21);
    \u0275\u0275template(11, HeadmanLessonsComponent_Conditional_10_For_1_For_5_Conditional_11_Template, 2, 1, "span");
    \u0275\u0275elementStart(12, "span");
    \u0275\u0275text(13);
    \u0275\u0275elementEnd();
    \u0275\u0275template(14, HeadmanLessonsComponent_Conditional_10_For_1_For_5_Conditional_14_Template, 2, 1, "span", 22);
    \u0275\u0275elementEnd()();
    \u0275\u0275elementStart(15, "div", 23);
    \u0275\u0275template(16, HeadmanLessonsComponent_Conditional_10_For_1_For_5_Conditional_16_Template, 3, 2, "button", 24)(17, HeadmanLessonsComponent_Conditional_10_For_1_For_5_Conditional_17_Template, 3, 1, "button", 25);
    \u0275\u0275elementEnd()();
  }
  if (rf & 2) {
    let tmp_25_0;
    const lesson_r2 = ctx.$implicit;
    const ctx_r0 = \u0275\u0275nextContext(3);
    \u0275\u0275classProp("lesson-row--cancelled", ctx_r0.isCancelled(lesson_r2));
    \u0275\u0275advance(3);
    \u0275\u0275textInterpolate1("\u2116", lesson_r2.lessonNumber, "");
    \u0275\u0275advance(2);
    \u0275\u0275textInterpolate2("", ctx_r0.shortTime(lesson_r2.startTime), "\u2013", ctx_r0.shortTime(lesson_r2.endTime), "");
    \u0275\u0275advance(3);
    \u0275\u0275textInterpolate1(" ", ctx_r0.subjectName(lesson_r2.subjectId), " ");
    \u0275\u0275advance();
    \u0275\u0275conditional((tmp_25_0 = ctx_r0.subjectTypeLabel(lesson_r2.subjectId)) ? 9 : -1, tmp_25_0);
    \u0275\u0275advance(2);
    \u0275\u0275conditional(lesson_r2.room ? 11 : -1);
    \u0275\u0275advance();
    \u0275\u0275classMapInterpolate1("status status--", ctx_r0.statusKey(lesson_r2), "");
    \u0275\u0275advance();
    \u0275\u0275textInterpolate(ctx_r0.statusLabel(lesson_r2));
    \u0275\u0275advance();
    \u0275\u0275conditional(ctx_r0.isCancelled(lesson_r2) && lesson_r2.cancelReason ? 14 : -1);
    \u0275\u0275advance(2);
    \u0275\u0275conditional(!ctx_r0.isCancelled(lesson_r2) ? 16 : 17);
  }
}
function HeadmanLessonsComponent_Conditional_10_For_1_Template(rf, ctx) {
  if (rf & 1) {
    \u0275\u0275elementStart(0, "div", 10)(1, "h2", 11);
    \u0275\u0275text(2);
    \u0275\u0275elementEnd();
    \u0275\u0275elementStart(3, "ul", 12);
    \u0275\u0275repeaterCreate(4, HeadmanLessonsComponent_Conditional_10_For_1_For_5_Template, 18, 14, "li", 13, _forTrack1);
    \u0275\u0275elementEnd()();
  }
  if (rf & 2) {
    const day_r5 = ctx.$implicit;
    \u0275\u0275advance(2);
    \u0275\u0275textInterpolate(day_r5.label);
    \u0275\u0275advance(2);
    \u0275\u0275repeater(day_r5.lessons);
  }
}
function HeadmanLessonsComponent_Conditional_10_Template(rf, ctx) {
  if (rf & 1) {
    \u0275\u0275repeaterCreate(0, HeadmanLessonsComponent_Conditional_10_For_1_Template, 6, 1, "div", 10, _forTrack0);
  }
  if (rf & 2) {
    const ctx_r0 = \u0275\u0275nextContext();
    \u0275\u0275repeater(ctx_r0.dayGroups());
  }
}
var DAY_NAMES = ["\u0412\u043E\u0441\u043A\u0440\u0435\u0441\u0435\u043D\u044C\u0435", "\u041F\u043E\u043D\u0435\u0434\u0435\u043B\u044C\u043D\u0438\u043A", "\u0412\u0442\u043E\u0440\u043D\u0438\u043A", "\u0421\u0440\u0435\u0434\u0430", "\u0427\u0435\u0442\u0432\u0435\u0440\u0433", "\u041F\u044F\u0442\u043D\u0438\u0446\u0430", "\u0421\u0443\u0431\u0431\u043E\u0442\u0430"];
var MONTH_NAMES = [
  "\u044F\u043D\u0432\u0430\u0440\u044F",
  "\u0444\u0435\u0432\u0440\u0430\u043B\u044F",
  "\u043C\u0430\u0440\u0442\u0430",
  "\u0430\u043F\u0440\u0435\u043B\u044F",
  "\u043C\u0430\u044F",
  "\u0438\u044E\u043D\u044F",
  "\u0438\u044E\u043B\u044F",
  "\u0430\u0432\u0433\u0443\u0441\u0442\u0430",
  "\u0441\u0435\u043D\u0442\u044F\u0431\u0440\u044F",
  "\u043E\u043A\u0442\u044F\u0431\u0440\u044F",
  "\u043D\u043E\u044F\u0431\u0440\u044F",
  "\u0434\u0435\u043A\u0430\u0431\u0440\u044F"
];
var DEFAULT_RANGE_DAYS = 14;
var HeadmanLessonsComponent = class _HeadmanLessonsComponent {
  headmanApi = inject(HeadmanApiService);
  auth = inject(AuthService);
  snackBar = inject(MatSnackBar);
  rangeDays = DEFAULT_RANGE_DAYS;
  loading = signal(true);
  error = signal(null);
  lessons = signal([]);
  subjects = signal([]);
  /** id урока, для которого сейчас идёт PATCH (cancel/restore) — блокирует кнопки. */
  busy = signal(null);
  dayGroups = computed(() => {
    const map = /* @__PURE__ */ new Map();
    for (const l of this.lessons()) {
      const arr = map.get(l.date) ?? [];
      arr.push(l);
      map.set(l.date, arr);
    }
    const dates = Array.from(map.keys()).sort();
    return dates.map((date) => {
      const arr = (map.get(date) ?? []).slice().sort((a, b) => a.lessonNumber - b.lessonNumber);
      return { date, label: this.dayLabel(date), lessons: arr };
    });
  });
  ngOnInit() {
    const user = this.auth.currentUser();
    if (!user?.groupId) {
      this.error.set("\u041D\u0435 \u0443\u0434\u0430\u043B\u043E\u0441\u044C \u043E\u043F\u0440\u0435\u0434\u0435\u043B\u0438\u0442\u044C \u0433\u0440\u0443\u043F\u043F\u0443. \u041E\u0431\u0440\u0430\u0442\u0438\u0442\u0435\u0441\u044C \u043A \u0430\u0434\u043C\u0438\u043D\u0438\u0441\u0442\u0440\u0430\u0442\u043E\u0440\u0443.");
      this.loading.set(false);
      return;
    }
    this.load(user.groupId);
  }
  load(groupId) {
    this.loading.set(true);
    this.error.set(null);
    const today = /* @__PURE__ */ new Date();
    const dateFrom = formatDate(today);
    const dateTo = formatDate(addDays(today, this.rangeDays - 1));
    this.headmanApi.getGroupLessons(groupId, dateFrom, dateTo).subscribe({
      next: (resp) => {
        const embedded = resp?._embedded;
        const list = embedded ? Object.values(embedded)[0] ?? [] : [];
        const normalised = list.map((entry) => {
          const item = entry?.content ?? entry;
          return item;
        });
        this.lessons.set(normalised.filter(Boolean));
        this.loading.set(false);
      },
      error: () => {
        this.error.set("\u041D\u0435 \u0443\u0434\u0430\u043B\u043E\u0441\u044C \u0437\u0430\u0433\u0440\u0443\u0437\u0438\u0442\u044C \u0441\u043F\u0438\u0441\u043E\u043A \u043F\u0430\u0440.");
        this.loading.set(false);
      }
    });
    this.headmanApi.listSubjects().subscribe({
      next: (resp) => {
        const embedded = resp?._embedded;
        const list = embedded ? Object.values(embedded)[0] ?? [] : [];
        this.subjects.set(list);
      }
    });
  }
  subjectName(id) {
    return this.subjects().find((s) => s.id === id)?.name ?? `\u041F\u0440\u0435\u0434\u043C\u0435\u0442 #${id}`;
  }
  /** Полное название типа занятия для рендера рядом с предметом. */
  subjectTypeLabel(id) {
    const t = this.subjects().find((s) => s.id === id)?.type;
    switch (t) {
      case "LECTURE":
        return "\u041B\u0435\u043A\u0446\u0438\u044F";
      case "PRACTICE":
        return "\u041F\u0440\u0430\u043A\u0442\u0438\u043A\u0430";
      case "LAB":
        return "\u041B\u0430\u0431\u043E\u0440\u0430\u0442\u043E\u0440\u043D\u0430\u044F";
      default:
        return "";
    }
  }
  shortTime(t) {
    return t?.length >= 5 ? t.slice(0, 5) : t;
  }
  dayLabel(date) {
    const d = /* @__PURE__ */ new Date(date + "T00:00:00");
    const dow = d.getDay();
    return `${DAY_NAMES[dow]}, ${d.getDate()} ${MONTH_NAMES[d.getMonth()]}`;
  }
  isCancelled(l) {
    return (l.status ?? "").toUpperCase() === "CANCELLED";
  }
  isClosed(l) {
    return (l.status ?? "").toUpperCase() === "CLOSED";
  }
  statusKey(l) {
    return (l.status ?? "").toLowerCase();
  }
  statusLabel(l) {
    switch ((l.status ?? "").toUpperCase()) {
      case "PLANNED":
        return "\u0417\u0430\u043F\u043B\u0430\u043D\u0438\u0440\u043E\u0432\u0430\u043D\u0430";
      case "ACTIVE":
        return "\u0418\u0434\u0451\u0442";
      case "CLOSED":
        return "\u0417\u0430\u0432\u0435\u0440\u0448\u0435\u043D\u0430";
      case "CANCELLED":
        return "\u041E\u0442\u043C\u0435\u043D\u0435\u043D\u0430";
      default:
        return l.status ?? "";
    }
  }
  onCancel(lesson) {
    const reason = window.prompt(`\u041F\u0440\u0438\u0447\u0438\u043D\u0430 \u043E\u0442\u043C\u0435\u043D\u044B \u043F\u0430\u0440\u044B \xAB${this.subjectName(lesson.subjectId)}\xBB (${this.dayLabel(lesson.date)}, \u2116 ${lesson.lessonNumber}):`);
    if (reason === null)
      return;
    const trimmed = reason.trim();
    if (!trimmed) {
      this.snackBar.open("\u041F\u0440\u0438\u0447\u0438\u043D\u0430 \u043E\u0442\u043C\u0435\u043D\u044B \u043D\u0435 \u043C\u043E\u0436\u0435\u0442 \u0431\u044B\u0442\u044C \u043F\u0443\u0441\u0442\u043E\u0439.", void 0, { duration: 4e3 });
      return;
    }
    if (trimmed.length > 512) {
      this.snackBar.open("\u041F\u0440\u0438\u0447\u0438\u043D\u0430 \u043D\u0435 \u0434\u043E\u043B\u0436\u043D\u0430 \u043F\u0440\u0435\u0432\u044B\u0448\u0430\u0442\u044C 512 \u0441\u0438\u043C\u0432\u043E\u043B\u043E\u0432.", void 0, { duration: 4e3 });
      return;
    }
    this.busy.set(lesson.id);
    this.headmanApi.cancelLesson(lesson.id, trimmed).subscribe({
      next: () => {
        this.busy.set(null);
        this.snackBar.open("\u041F\u0430\u0440\u0430 \u043E\u0442\u043C\u0435\u043D\u0435\u043D\u0430.", void 0, { duration: 3e3 });
        this.applyLocalUpdate(lesson.id, (l) => __spreadProps(__spreadValues({}, l), { status: "CANCELLED", cancelReason: trimmed }));
      },
      error: (err) => {
        this.busy.set(null);
        this.snackBar.open(this.errorMessage(err, "\u041D\u0435 \u0443\u0434\u0430\u043B\u043E\u0441\u044C \u043E\u0442\u043C\u0435\u043D\u0438\u0442\u044C \u043F\u0430\u0440\u0443."), void 0, { duration: 5e3 });
      }
    });
  }
  onRestore(lesson) {
    if (!window.confirm(`\u0412\u043E\u0441\u0441\u0442\u0430\u043D\u043E\u0432\u0438\u0442\u044C \u043F\u0430\u0440\u0443 \xAB${this.subjectName(lesson.subjectId)}\xBB?`))
      return;
    this.busy.set(lesson.id);
    this.headmanApi.restoreLesson(lesson.id).subscribe({
      next: () => {
        this.busy.set(null);
        this.snackBar.open("\u041F\u0430\u0440\u0430 \u0432\u043E\u0441\u0441\u0442\u0430\u043D\u043E\u0432\u043B\u0435\u043D\u0430.", void 0, { duration: 3e3 });
        this.applyLocalUpdate(lesson.id, (l) => __spreadProps(__spreadValues({}, l), { status: "PLANNED", cancelReason: void 0 }));
      },
      error: (err) => {
        this.busy.set(null);
        this.snackBar.open(this.errorMessage(err, "\u041D\u0435 \u0443\u0434\u0430\u043B\u043E\u0441\u044C \u0432\u043E\u0441\u0441\u0442\u0430\u043D\u043E\u0432\u0438\u0442\u044C \u043F\u0430\u0440\u0443."), void 0, { duration: 5e3 });
      }
    });
  }
  applyLocalUpdate(id, fn) {
    this.lessons.update((arr) => arr.map((l) => l.id === id ? fn(l) : l));
  }
  errorMessage(err, fallback) {
    if (err?.status === 403)
      return "\u041D\u0435\u0434\u043E\u0441\u0442\u0430\u0442\u043E\u0447\u043D\u043E \u043F\u0440\u0430\u0432 \u0434\u043B\u044F \u044D\u0442\u043E\u0433\u043E \u0434\u0435\u0439\u0441\u0442\u0432\u0438\u044F.";
    if (err?.status === 404)
      return "\u041F\u0430\u0440\u0430 \u043D\u0435 \u043D\u0430\u0439\u0434\u0435\u043D\u0430 \u2014 \u0432\u043E\u0437\u043C\u043E\u0436\u043D\u043E \u0443\u0436\u0435 \u0438\u0437\u043C\u0435\u043D\u0435\u043D\u0430.";
    if (err?.status === 422)
      return "\u041D\u0435\u0434\u043E\u043F\u0443\u0441\u0442\u0438\u043C\u044B\u0439 \u043F\u0435\u0440\u0435\u0445\u043E\u0434 \u0441\u0442\u0430\u0442\u0443\u0441\u0430 \u0434\u043B\u044F \u044D\u0442\u043E\u0439 \u043F\u0430\u0440\u044B.";
    return fallback;
  }
  static \u0275fac = function HeadmanLessonsComponent_Factory(__ngFactoryType__) {
    return new (__ngFactoryType__ || _HeadmanLessonsComponent)();
  };
  static \u0275cmp = /* @__PURE__ */ \u0275\u0275defineComponent({ type: _HeadmanLessonsComponent, selectors: [["app-headman-lessons"]], decls: 11, vars: 2, consts: [[1, "page-stack"], [1, "page-header"], [1, "page-eyebrow"], [1, "page-title"], [1, "page-card"], [1, "page-error"], [1, "page-empty"], ["diameter", "32"], [1, "ph", "ph-warning-circle"], [1, "ph-duotone", "ph-calendar-blank"], [1, "page-card", "day-card"], [1, "day-heading"], [1, "lesson-list"], [1, "lesson-row", 3, "lesson-row--cancelled"], [1, "lesson-row"], [1, "lesson-row__time"], [1, "lesson-num"], [1, "lesson-time"], [1, "lesson-row__main"], [1, "lesson-subject"], [1, "lesson-type"], [1, "lesson-meta"], [1, "cancel-reason"], [1, "lesson-row__actions"], ["type", "button", 1, "btn-stroke", "btn-stroke--danger", 3, "disabled", "title"], ["type", "button", 1, "btn-stroke", 3, "disabled"], ["type", "button", 1, "btn-stroke", "btn-stroke--danger", 3, "click", "disabled", "title"], [1, "ph", "ph-x-circle"], ["type", "button", 1, "btn-stroke", 3, "click", "disabled"], [1, "ph", "ph-arrow-counter-clockwise"]], template: function HeadmanLessonsComponent_Template(rf, ctx) {
    if (rf & 1) {
      \u0275\u0275elementStart(0, "div", 0)(1, "div", 1)(2, "div")(3, "span", 2);
      \u0275\u0275text(4, "\u0421\u0442\u0430\u0440\u043E\u0441\u0442\u0430\u0442");
      \u0275\u0275elementEnd();
      \u0275\u0275elementStart(5, "h1", 3);
      \u0275\u0275text(6, "\u041F\u0430\u0440\u044B \u043D\u0430 \u0431\u043B\u0438\u0436\u0430\u0439\u0448\u0438\u0435 2 \u043D\u0435\u0434\u0435\u043B\u0438");
      \u0275\u0275elementEnd()()();
      \u0275\u0275template(7, HeadmanLessonsComponent_Conditional_7_Template, 2, 0, "div", 4)(8, HeadmanLessonsComponent_Conditional_8_Template, 3, 1, "div", 5)(9, HeadmanLessonsComponent_Conditional_9_Template, 6, 1, "div", 6)(10, HeadmanLessonsComponent_Conditional_10_Template, 2, 0);
      \u0275\u0275elementEnd();
    }
    if (rf & 2) {
      \u0275\u0275property("@routeFade", void 0);
      \u0275\u0275advance(7);
      \u0275\u0275conditional(ctx.loading() ? 7 : ctx.error() ? 8 : ctx.dayGroups().length === 0 ? 9 : 10);
    }
  }, dependencies: [CommonModule, MatButtonModule, MatProgressSpinnerModule, MatProgressSpinner], styles: ["\n\n.day-card[_ngcontent-%COMP%] {\n  padding: 16px;\n}\n.day-heading[_ngcontent-%COMP%] {\n  font-size: 1rem;\n  font-weight: 600;\n  margin: 0 0 12px;\n  color: var(--text-primary);\n}\n.lesson-list[_ngcontent-%COMP%] {\n  list-style: none;\n  padding: 0;\n  margin: 0;\n  display: flex;\n  flex-direction: column;\n  gap: 8px;\n}\n.lesson-row[_ngcontent-%COMP%] {\n  display: grid;\n  grid-template-columns: 110px 1fr auto;\n  gap: 12px;\n  align-items: center;\n  padding: 10px 12px;\n  border: 1px solid var(--border-subtle, #e0e0e0);\n  border-radius: 8px;\n  background: var(--bg-elevated, #fafafa);\n}\n.lesson-row--cancelled[_ngcontent-%COMP%] {\n  opacity: 0.6;\n  background: #fafafa;\n}\n.lesson-row__time[_ngcontent-%COMP%] {\n  display: flex;\n  flex-direction: column;\n  gap: 2px;\n}\n.lesson-num[_ngcontent-%COMP%] {\n  font-size: 0.75rem;\n  color: var(--text-muted);\n}\n.lesson-time[_ngcontent-%COMP%] {\n  font-family: var(--font-mono, monospace);\n  font-variant-numeric: tabular-nums;\n  font-size: 0.85rem;\n}\n.lesson-subject[_ngcontent-%COMP%] {\n  font-weight: 600;\n}\n.lesson-type[_ngcontent-%COMP%] {\n  font-weight: 500;\n  color: var(--text-muted);\n  margin-left: 4px;\n  font-size: 0.9em;\n}\n.lesson-meta[_ngcontent-%COMP%] {\n  display: flex;\n  gap: 8px;\n  align-items: center;\n  flex-wrap: wrap;\n  font-size: 0.8rem;\n  color: var(--text-muted);\n  margin-top: 2px;\n}\n.status[_ngcontent-%COMP%] {\n  display: inline-block;\n  padding: 1px 8px;\n  border-radius: 10px;\n  font-size: 0.75rem;\n  font-weight: 500;\n}\n.status--planned[_ngcontent-%COMP%] {\n  background: #e3f2fd;\n  color: #1565c0;\n}\n.status--active[_ngcontent-%COMP%] {\n  background: #e8f5e9;\n  color: #2e7d32;\n}\n.status--closed[_ngcontent-%COMP%] {\n  background: #f5f5f5;\n  color: #616161;\n}\n.status--cancelled[_ngcontent-%COMP%] {\n  background: #ffebee;\n  color: #c62828;\n}\n.cancel-reason[_ngcontent-%COMP%] {\n  font-style: italic;\n}\n.btn-stroke[_ngcontent-%COMP%] {\n  padding: 6px 12px;\n  border-radius: 6px;\n  border: 1px solid var(--border-subtle);\n  background: transparent;\n  cursor: pointer;\n  font-size: 0.85rem;\n  display: inline-flex;\n  align-items: center;\n  gap: 4px;\n  transition: background 150ms ease;\n}\n.btn-stroke[_ngcontent-%COMP%]:hover:not([disabled]) {\n  background: var(--bg-hover, #eee);\n}\n.btn-stroke[disabled][_ngcontent-%COMP%] {\n  opacity: 0.5;\n  cursor: not-allowed;\n}\n.btn-stroke--danger[_ngcontent-%COMP%] {\n  color: #c62828;\n  border-color: #ef9a9a;\n}\n.btn-stroke--danger[_ngcontent-%COMP%]:hover:not([disabled]) {\n  background: #ffebee;\n}\n/*# sourceMappingURL=headman-lessons.component.css.map */"], data: { animation: [
    trigger("routeFade", [
      transition(":enter", [
        style({ opacity: 0, transform: "translateY(8px)" }),
        animate("200ms cubic-bezier(0.16, 1, 0.3, 1)", style({ opacity: 1, transform: "translateY(0)" }))
      ])
    ])
  ] }, changeDetection: 0 });
};
(() => {
  (typeof ngDevMode === "undefined" || ngDevMode) && setClassMetadata(HeadmanLessonsComponent, [{
    type: Component,
    args: [{ selector: "app-headman-lessons", standalone: true, changeDetection: ChangeDetectionStrategy.OnPush, imports: [CommonModule, MatButtonModule, MatProgressSpinnerModule], animations: [
      trigger("routeFade", [
        transition(":enter", [
          style({ opacity: 0, transform: "translateY(8px)" }),
          animate("200ms cubic-bezier(0.16, 1, 0.3, 1)", style({ opacity: 1, transform: "translateY(0)" }))
        ])
      ])
    ], template: `
    <div class="page-stack" [@routeFade]>
      <div class="page-header">
        <div>
          <span class="page-eyebrow">\u0421\u0442\u0430\u0440\u043E\u0441\u0442\u0430\u0442</span>
          <h1 class="page-title">\u041F\u0430\u0440\u044B \u043D\u0430 \u0431\u043B\u0438\u0436\u0430\u0439\u0448\u0438\u0435 2 \u043D\u0435\u0434\u0435\u043B\u0438</h1>
        </div>
      </div>

      @if (loading()) {
        <div class="page-card"><mat-spinner diameter="32"></mat-spinner></div>
      } @else if (error()) {
        <div class="page-error"><i class="ph ph-warning-circle"></i> {{ error() }}</div>
      } @else if (dayGroups().length === 0) {
        <div class="page-empty">
          <i class="ph-duotone ph-calendar-blank"></i>
          <h3>\u041F\u0430\u0440 \u043D\u0435\u0442</h3>
          <p>\u041D\u0430 \u0431\u043B\u0438\u0436\u0430\u0439\u0448\u0438\u0435 {{ rangeDays }} \u0434\u043D. \u0432 \u0440\u0430\u0441\u043F\u0438\u0441\u0430\u043D\u0438\u0438 \u043D\u0435\u0442 \u043D\u0438 \u043E\u0434\u043D\u043E\u0439 \u043F\u0430\u0440\u044B.</p>
        </div>
      } @else {
        @for (day of dayGroups(); track day.date) {
          <div class="page-card day-card">
            <h2 class="day-heading">{{ day.label }}</h2>
            <ul class="lesson-list">
              @for (lesson of day.lessons; track lesson.id) {
                <li class="lesson-row" [class.lesson-row--cancelled]="isCancelled(lesson)">
                  <div class="lesson-row__time">
                    <span class="lesson-num">\u2116{{ lesson.lessonNumber }}</span>
                    <span class="lesson-time">{{ shortTime(lesson.startTime) }}\u2013{{ shortTime(lesson.endTime) }}</span>
                  </div>
                  <div class="lesson-row__main">
                    <div class="lesson-subject">
                      {{ subjectName(lesson.subjectId) }}
                      @if (subjectTypeLabel(lesson.subjectId); as t) {
                        <span class="lesson-type">\u2014 {{ t }}</span>
                      }
                    </div>
                    <div class="lesson-meta">
                      @if (lesson.room) { <span>{{ lesson.room }}</span> }
                      <span class="status status--{{ statusKey(lesson) }}">{{ statusLabel(lesson) }}</span>
                      @if (isCancelled(lesson) && lesson.cancelReason) {
                        <span class="cancel-reason">\u2014 {{ lesson.cancelReason }}</span>
                      }
                    </div>
                  </div>
                  <div class="lesson-row__actions">
                    @if (!isCancelled(lesson)) {
                      <button class="btn-stroke btn-stroke--danger" type="button"
                              [disabled]="busy() === lesson.id"
                              [title]="isClosed(lesson) ? '\u041E\u0442\u043C\u0435\u043D\u0438\u0442\u044C \u0443\u0436\u0435 \u043F\u0440\u043E\u0448\u0435\u0434\u0448\u0443\u044E \u043F\u0430\u0440\u0443 (\u0434\u043B\u044F \u0438\u0441\u0442\u043E\u0440\u0438\u0447\u0435\u0441\u043A\u0438\u0445 \u0434\u0430\u043D\u043D\u044B\u0445)' : '\u041E\u0442\u043C\u0435\u043D\u0438\u0442\u044C \u043F\u0430\u0440\u0443'"
                              (click)="onCancel(lesson)">
                        <i class="ph ph-x-circle"></i> \u041E\u0442\u043C\u0435\u043D\u0438\u0442\u044C
                      </button>
                    } @else {
                      <button class="btn-stroke" type="button"
                              [disabled]="busy() === lesson.id"
                              (click)="onRestore(lesson)">
                        <i class="ph ph-arrow-counter-clockwise"></i> \u0412\u043E\u0441\u0441\u0442\u0430\u043D\u043E\u0432\u0438\u0442\u044C
                      </button>
                    }
                  </div>
                </li>
              }
            </ul>
          </div>
        }
      }
    </div>
  `, styles: ["/* angular:styles/component:css;7a2dc523652b4ea00af1cfbf886cf11d80590cdd64db9c2a217aa29337737e49;C:/Users/maksd/IntelliJIDEA/rutcampustrack/frontends/web-panel/src/app/features/headman/lessons/headman-lessons.component.ts */\n.day-card {\n  padding: 16px;\n}\n.day-heading {\n  font-size: 1rem;\n  font-weight: 600;\n  margin: 0 0 12px;\n  color: var(--text-primary);\n}\n.lesson-list {\n  list-style: none;\n  padding: 0;\n  margin: 0;\n  display: flex;\n  flex-direction: column;\n  gap: 8px;\n}\n.lesson-row {\n  display: grid;\n  grid-template-columns: 110px 1fr auto;\n  gap: 12px;\n  align-items: center;\n  padding: 10px 12px;\n  border: 1px solid var(--border-subtle, #e0e0e0);\n  border-radius: 8px;\n  background: var(--bg-elevated, #fafafa);\n}\n.lesson-row--cancelled {\n  opacity: 0.6;\n  background: #fafafa;\n}\n.lesson-row__time {\n  display: flex;\n  flex-direction: column;\n  gap: 2px;\n}\n.lesson-num {\n  font-size: 0.75rem;\n  color: var(--text-muted);\n}\n.lesson-time {\n  font-family: var(--font-mono, monospace);\n  font-variant-numeric: tabular-nums;\n  font-size: 0.85rem;\n}\n.lesson-subject {\n  font-weight: 600;\n}\n.lesson-type {\n  font-weight: 500;\n  color: var(--text-muted);\n  margin-left: 4px;\n  font-size: 0.9em;\n}\n.lesson-meta {\n  display: flex;\n  gap: 8px;\n  align-items: center;\n  flex-wrap: wrap;\n  font-size: 0.8rem;\n  color: var(--text-muted);\n  margin-top: 2px;\n}\n.status {\n  display: inline-block;\n  padding: 1px 8px;\n  border-radius: 10px;\n  font-size: 0.75rem;\n  font-weight: 500;\n}\n.status--planned {\n  background: #e3f2fd;\n  color: #1565c0;\n}\n.status--active {\n  background: #e8f5e9;\n  color: #2e7d32;\n}\n.status--closed {\n  background: #f5f5f5;\n  color: #616161;\n}\n.status--cancelled {\n  background: #ffebee;\n  color: #c62828;\n}\n.cancel-reason {\n  font-style: italic;\n}\n.btn-stroke {\n  padding: 6px 12px;\n  border-radius: 6px;\n  border: 1px solid var(--border-subtle);\n  background: transparent;\n  cursor: pointer;\n  font-size: 0.85rem;\n  display: inline-flex;\n  align-items: center;\n  gap: 4px;\n  transition: background 150ms ease;\n}\n.btn-stroke:hover:not([disabled]) {\n  background: var(--bg-hover, #eee);\n}\n.btn-stroke[disabled] {\n  opacity: 0.5;\n  cursor: not-allowed;\n}\n.btn-stroke--danger {\n  color: #c62828;\n  border-color: #ef9a9a;\n}\n.btn-stroke--danger:hover:not([disabled]) {\n  background: #ffebee;\n}\n/*# sourceMappingURL=headman-lessons.component.css.map */\n"] }]
  }], null, null);
})();
(() => {
  (typeof ngDevMode === "undefined" || ngDevMode) && \u0275setClassDebugInfo(HeadmanLessonsComponent, { className: "HeadmanLessonsComponent", filePath: "src/app/features/headman/lessons/headman-lessons.component.ts", lineNumber: 201 });
})();
export {
  HeadmanLessonsComponent
};
//# sourceMappingURL=chunk-2PCKS7SL.js.map
