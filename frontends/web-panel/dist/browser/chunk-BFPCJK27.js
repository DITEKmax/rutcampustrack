import {
  HeadmanApiService
} from "./chunk-BU5FJGI6.js";
import {
  MatProgressSpinner,
  MatProgressSpinnerModule
} from "./chunk-BAW76DQ6.js";
import {
  MatSnackBar
} from "./chunk-53YESMZZ.js";
import {
  addDays,
  formatDate,
  getMonday,
  isSameWeek
} from "./chunk-7F5XKO6G.js";
import "./chunk-33KX5TJL.js";
import {
  animate,
  style,
  transition,
  trigger
} from "./chunk-JKCQIGK6.js";
import {
  AuthService
} from "./chunk-URGWZVMC.js";
import "./chunk-OIBNGD5S.js";
import "./chunk-PAS4QIDL.js";
import "./chunk-6JM2QIGP.js";
import "./chunk-75YPR2VK.js";
import "./chunk-2UA5NBNP.js";
import "./chunk-YIJEORIR.js";
import "./chunk-B3BDHDAM.js";
import {
  CommonModule
} from "./chunk-M5DKW26A.js";
import {
  ChangeDetectionStrategy,
  Component,
  catchError,
  computed,
  forkJoin,
  inject,
  of,
  setClassMetadata,
  signal,
  ɵsetClassDebugInfo,
  ɵɵadvance,
  ɵɵattribute,
  ɵɵclassProp,
  ɵɵconditional,
  ɵɵdeclareLet,
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
  ɵɵtextInterpolate1
} from "./chunk-MFRIGFR2.js";

// src/app/features/headman/weekly-journal/headman-weekly-journal.component.ts
var _forTrack0 = ($index, $item) => $item.date;
var _forTrack1 = ($index, $item) => $item.userId;
var _forTrack2 = ($index, $item) => $item.lessonId;
function HeadmanWeeklyJournalComponent_Conditional_17_Template(rf, ctx) {
  if (rf & 1) {
    \u0275\u0275elementStart(0, "div", 12);
    \u0275\u0275element(1, "mat-spinner", 16);
    \u0275\u0275elementEnd();
  }
}
function HeadmanWeeklyJournalComponent_Conditional_18_Template(rf, ctx) {
  if (rf & 1) {
    \u0275\u0275elementStart(0, "div", 13);
    \u0275\u0275element(1, "i", 17);
    \u0275\u0275text(2);
    \u0275\u0275elementEnd();
  }
  if (rf & 2) {
    const ctx_r0 = \u0275\u0275nextContext();
    \u0275\u0275advance(2);
    \u0275\u0275textInterpolate1(" ", ctx_r0.error(), "");
  }
}
function HeadmanWeeklyJournalComponent_Conditional_19_Template(rf, ctx) {
  if (rf & 1) {
    \u0275\u0275elementStart(0, "div", 14);
    \u0275\u0275element(1, "i", 18);
    \u0275\u0275elementStart(2, "h3");
    \u0275\u0275text(3, "\u041D\u0430 \u044D\u0442\u043E\u0439 \u043D\u0435\u0434\u0435\u043B\u0435 \u043D\u0435\u0442 \u0437\u0430\u043D\u044F\u0442\u0438\u0439");
    \u0275\u0275elementEnd();
    \u0275\u0275elementStart(4, "p");
    \u0275\u0275text(5, "\u041F\u0435\u0440\u0435\u0439\u0434\u0438\u0442\u0435 \u043A \u0434\u0440\u0443\u0433\u043E\u0439 \u043D\u0435\u0434\u0435\u043B\u0435, \u0447\u0442\u043E\u0431\u044B \u043F\u0440\u043E\u0434\u043E\u043B\u0436\u0438\u0442\u044C \u0440\u0430\u0431\u043E\u0442\u0443 \u0441 \u0436\u0443\u0440\u043D\u0430\u043B\u043E\u043C.");
    \u0275\u0275elementEnd()();
  }
}
function HeadmanWeeklyJournalComponent_Conditional_20_For_7_Template(rf, ctx) {
  if (rf & 1) {
    \u0275\u0275elementStart(0, "th", 25)(1, "span", 26);
    \u0275\u0275text(2);
    \u0275\u0275elementEnd();
    \u0275\u0275elementStart(3, "span", 27);
    \u0275\u0275text(4);
    \u0275\u0275elementEnd()();
  }
  if (rf & 2) {
    const day_r2 = ctx.$implicit;
    const \u0275$index_65_r3 = ctx.$index;
    const \u0275$count_65_r4 = ctx.$count;
    \u0275\u0275classProp("col-day--last", !(\u0275$index_65_r3 === \u0275$count_65_r4 - 1));
    \u0275\u0275attribute("colspan", day_r2.columns.length * 3);
    \u0275\u0275advance(2);
    \u0275\u0275textInterpolate(day_r2.weekday);
    \u0275\u0275advance(2);
    \u0275\u0275textInterpolate(day_r2.displayDate);
  }
}
function HeadmanWeeklyJournalComponent_Conditional_20_For_10_For_1_Template(rf, ctx) {
  if (rf & 1) {
    \u0275\u0275elementStart(0, "th", 29)(1, "div", 30)(2, "span", 31);
    \u0275\u0275text(3);
    \u0275\u0275elementEnd()()();
  }
  if (rf & 2) {
    const col_r5 = ctx.$implicit;
    const \u0275$index_77_r6 = ctx.$index;
    const \u0275$count_77_r7 = ctx.$count;
    const ctx_r7 = \u0275\u0275nextContext();
    const \u0275$index_76_r9 = ctx_r7.$index;
    const \u0275$count_76_r10 = ctx_r7.$count;
    \u0275\u0275classProp("col-subject--day-end", \u0275$index_77_r6 === \u0275$count_77_r7 - 1 && !(\u0275$index_76_r9 === \u0275$count_76_r10 - 1))("col-subject--day-inner", !(\u0275$index_77_r6 === \u0275$count_77_r7 - 1));
    \u0275\u0275advance();
    \u0275\u0275property("title", col_r5.subjectName);
    \u0275\u0275advance(2);
    \u0275\u0275textInterpolate(col_r5.subjectName);
  }
}
function HeadmanWeeklyJournalComponent_Conditional_20_For_10_Template(rf, ctx) {
  if (rf & 1) {
    \u0275\u0275repeaterCreate(0, HeadmanWeeklyJournalComponent_Conditional_20_For_10_For_1_Template, 4, 6, "th", 28, _forTrack2);
  }
  if (rf & 2) {
    const day_r11 = ctx.$implicit;
    \u0275\u0275repeater(day_r11.columns);
  }
}
function HeadmanWeeklyJournalComponent_Conditional_20_For_13_For_4_For_1_Template(rf, ctx) {
  if (rf & 1) {
    const _r12 = \u0275\u0275getCurrentView();
    \u0275\u0275declareLet(0)(1)(2);
    \u0275\u0275elementStart(3, "td", 33)(4, "button", 34);
    \u0275\u0275listener("click", function HeadmanWeeklyJournalComponent_Conditional_20_For_13_For_4_For_1_Template_button_click_4_listener() {
      const col_r13 = \u0275\u0275restoreView(_r12).$implicit;
      const student_r14 = \u0275\u0275nextContext(2).$implicit;
      const ctx_r0 = \u0275\u0275nextContext(2);
      return \u0275\u0275resetView(ctx_r0.onStatusClick(col_r13.lessonId, student_r14.userId, "present"));
    });
    \u0275\u0275text(5, "+");
    \u0275\u0275elementEnd()();
    \u0275\u0275elementStart(6, "td", 33)(7, "button", 35);
    \u0275\u0275listener("click", function HeadmanWeeklyJournalComponent_Conditional_20_For_13_For_4_For_1_Template_button_click_7_listener() {
      const col_r13 = \u0275\u0275restoreView(_r12).$implicit;
      const student_r14 = \u0275\u0275nextContext(2).$implicit;
      const ctx_r0 = \u0275\u0275nextContext(2);
      return \u0275\u0275resetView(ctx_r0.onStatusClick(col_r13.lessonId, student_r14.userId, "absent"));
    });
    \u0275\u0275text(8, "\u043D");
    \u0275\u0275elementEnd()();
    \u0275\u0275elementStart(9, "td", 33);
    \u0275\u0275declareLet(10);
    \u0275\u0275elementStart(11, "button", 36);
    \u0275\u0275listener("click", function HeadmanWeeklyJournalComponent_Conditional_20_For_13_For_4_For_1_Template_button_click_11_listener() {
      const col_r13 = \u0275\u0275restoreView(_r12).$implicit;
      const student_r14 = \u0275\u0275nextContext(2).$implicit;
      const ctx_r0 = \u0275\u0275nextContext(2);
      return \u0275\u0275resetView(ctx_r0.onStatusClick(col_r13.lessonId, student_r14.userId, "excused"));
    });
    \u0275\u0275text(12, "\u0443");
    \u0275\u0275elementEnd()();
  }
  if (rf & 2) {
    const col_r13 = ctx.$implicit;
    const \u0275$index_94_r15 = ctx.$index;
    const \u0275$count_94_r16 = ctx.$count;
    const ctx_r16 = \u0275\u0275nextContext();
    const \u0275$index_93_r18 = ctx_r16.$index;
    const \u0275$count_93_r19 = ctx_r16.$count;
    const student_r14 = \u0275\u0275nextContext().$implicit;
    const ctx_r0 = \u0275\u0275nextContext(2);
    const status_r20 = ctx_r0.getStatus(col_r13.lessonId, student_r14.userId);
    const saving_r21 = ctx_r0.isSaving(col_r13.lessonId, student_r14.userId);
    const flashing_r22 = ctx_r0.isFlashing(col_r13.lessonId, student_r14.userId);
    \u0275\u0275advance(3);
    \u0275\u0275classProp("cell--saving", saving_r21)("cell--flash", flashing_r22);
    \u0275\u0275advance();
    \u0275\u0275classProp("dot--active", status_r20 === "present");
    \u0275\u0275attribute("aria-label", "\u041F\u0440\u0438\u0441\u0443\u0442\u0441\u0442\u0432\u043E\u0432\u0430\u043B \u2014 " + student_r14.displayName)("aria-pressed", status_r20 === "present");
    \u0275\u0275advance(2);
    \u0275\u0275classProp("cell--saving", saving_r21)("cell--flash", flashing_r22);
    \u0275\u0275advance();
    \u0275\u0275classProp("dot--active", status_r20 === "absent");
    \u0275\u0275attribute("aria-label", "\u041D\u0435 \u0431\u044B\u043B \u2014 " + student_r14.displayName)("aria-pressed", status_r20 === "absent");
    \u0275\u0275advance(2);
    \u0275\u0275classProp("cell--saving", saving_r21)("cell--flash", flashing_r22)("cell--day-end", \u0275$index_94_r15 === \u0275$count_94_r16 - 1 && !(\u0275$index_93_r18 === \u0275$count_93_r19 - 1))("cell--subject-end", !(\u0275$index_94_r15 === \u0275$count_94_r16 - 1));
    const reason_r23 = ctx_r0.getReason(col_r13.lessonId, student_r14.userId);
    \u0275\u0275advance(2);
    \u0275\u0275classProp("dot--active", status_r20 === "excused" || status_r20 === "free_attendance");
    \u0275\u0275attribute("aria-label", "\u0423\u0432\u0430\u0436\u0438\u0442. \u043F\u0440\u0438\u0447\u0438\u043D\u0430 \u2014 " + student_r14.displayName + (reason_r23 ? " \xB7 " + reason_r23 : ""))("aria-pressed", status_r20 === "excused" || status_r20 === "free_attendance")("title", reason_r23);
  }
}
function HeadmanWeeklyJournalComponent_Conditional_20_For_13_For_4_Template(rf, ctx) {
  if (rf & 1) {
    \u0275\u0275repeaterCreate(0, HeadmanWeeklyJournalComponent_Conditional_20_For_13_For_4_For_1_Template, 13, 29, null, null, _forTrack2);
  }
  if (rf & 2) {
    const day_r24 = ctx.$implicit;
    \u0275\u0275repeater(day_r24.columns);
  }
}
function HeadmanWeeklyJournalComponent_Conditional_20_For_13_Template(rf, ctx) {
  if (rf & 1) {
    \u0275\u0275elementStart(0, "tr", 24)(1, "td", 32);
    \u0275\u0275text(2);
    \u0275\u0275elementEnd();
    \u0275\u0275repeaterCreate(3, HeadmanWeeklyJournalComponent_Conditional_20_For_13_For_4_Template, 2, 0, null, null, _forTrack0);
    \u0275\u0275elementEnd();
  }
  if (rf & 2) {
    const student_r14 = ctx.$implicit;
    const ctx_r0 = \u0275\u0275nextContext(2);
    \u0275\u0275advance();
    \u0275\u0275property("title", student_r14.displayName);
    \u0275\u0275advance();
    \u0275\u0275textInterpolate(student_r14.displayName);
    \u0275\u0275advance();
    \u0275\u0275repeater(ctx_r0.dayGroups());
  }
}
function HeadmanWeeklyJournalComponent_Conditional_20_Template(rf, ctx) {
  if (rf & 1) {
    \u0275\u0275elementStart(0, "div", 15)(1, "table", 19)(2, "thead")(3, "tr", 20)(4, "th", 21);
    \u0275\u0275text(5, "\u0421\u0442\u0443\u0434\u0435\u043D\u0442");
    \u0275\u0275elementEnd();
    \u0275\u0275repeaterCreate(6, HeadmanWeeklyJournalComponent_Conditional_20_For_7_Template, 5, 5, "th", 22, _forTrack0);
    \u0275\u0275elementEnd();
    \u0275\u0275elementStart(8, "tr", 23);
    \u0275\u0275repeaterCreate(9, HeadmanWeeklyJournalComponent_Conditional_20_For_10_Template, 2, 0, null, null, _forTrack0);
    \u0275\u0275elementEnd()();
    \u0275\u0275elementStart(11, "tbody");
    \u0275\u0275repeaterCreate(12, HeadmanWeeklyJournalComponent_Conditional_20_For_13_Template, 5, 2, "tr", 24, _forTrack1);
    \u0275\u0275elementEnd()()();
  }
  if (rf & 2) {
    const ctx_r0 = \u0275\u0275nextContext();
    \u0275\u0275advance(6);
    \u0275\u0275repeater(ctx_r0.dayGroups());
    \u0275\u0275advance(3);
    \u0275\u0275repeater(ctx_r0.dayGroups());
    \u0275\u0275advance(3);
    \u0275\u0275repeater(ctx_r0.sortedStudents());
  }
}
var WEEKDAY_LABELS = ["\u0412\u0421", "\u041F\u041D", "\u0412\u0422", "\u0421\u0420", "\u0427\u0422", "\u041F\u0422", "\u0421\u0411"];
var HeadmanWeeklyJournalComponent = class _HeadmanWeeklyJournalComponent {
  headmanApi = inject(HeadmanApiService);
  auth = inject(AuthService);
  snackBar = inject(MatSnackBar);
  loading = signal(true);
  error = signal(null);
  monday = signal(getMonday(/* @__PURE__ */ new Date()));
  students = signal([]);
  lessons = signal([]);
  subjects = signal(/* @__PURE__ */ new Map());
  /** key = `${lessonId}_${userId}` -> status */
  statusMap = signal(/* @__PURE__ */ new Map());
  reasonMap = signal(/* @__PURE__ */ new Map());
  /** cells currently saving (visual feedback) */
  savingCells = signal(/* @__PURE__ */ new Set());
  /** cells that just saved successfully — flash indicator */
  flashCells = signal(/* @__PURE__ */ new Set());
  weekRange = computed(() => {
    const mon = this.monday();
    const sun = addDays(mon, 6);
    const fmt = (d) => `${String(d.getDate()).padStart(2, "0")}.${String(d.getMonth() + 1).padStart(2, "0")}`;
    return `${fmt(mon)} \u2014 ${fmt(sun)}`;
  });
  isCurrentWeek = computed(() => isSameWeek(this.monday(), /* @__PURE__ */ new Date()));
  dayGroups = computed(() => {
    const monday = this.monday();
    const subjects = this.subjects();
    const byDate = /* @__PURE__ */ new Map();
    for (const l of this.lessons()) {
      const arr = byDate.get(l.date) ?? [];
      arr.push(l);
      byDate.set(l.date, arr);
    }
    const groups = [];
    for (let i = 0; i < 7; i++) {
      const d = addDays(monday, i);
      const iso = formatDate(d);
      const dayLessons = byDate.get(iso);
      if (!dayLessons || dayLessons.length === 0)
        continue;
      const sorted = dayLessons.slice().sort((a, b) => {
        if (a.lessonNumber !== b.lessonNumber)
          return a.lessonNumber - b.lessonNumber;
        return a.startTime.localeCompare(b.startTime);
      });
      const columns = sorted.map((l) => ({
        lessonId: l.id,
        subjectId: l.subjectId,
        subjectName: subjects.get(l.subjectId) ?? `\u041F\u0440\u0435\u0434\u043C\u0435\u0442 #${l.subjectId}`,
        lessonNumber: l.lessonNumber,
        startTime: (l.startTime ?? "").slice(0, 5),
        date: l.date,
        header: `${(subjects.get(l.subjectId) ?? `#${l.subjectId}`).toUpperCase()} \xA7 ${l.lessonNumber} ${(l.startTime ?? "").slice(0, 5)}`
      }));
      groups.push({
        date: iso,
        weekday: WEEKDAY_LABELS[d.getDay()],
        displayDate: `${String(d.getDate()).padStart(2, "0")}.${String(d.getMonth() + 1).padStart(2, "0")}`,
        columns
      });
    }
    return groups;
  });
  sortedStudents = computed(() => {
    return this.students().slice().sort((a, b) => a.displayName.localeCompare(b.displayName, "ru"));
  });
  hasLessons = computed(() => this.dayGroups().length > 0);
  ngOnInit() {
    const user = this.auth.currentUser();
    if (!user?.groupId) {
      this.error.set("\u041D\u0435 \u0443\u0434\u0430\u043B\u043E\u0441\u044C \u043E\u043F\u0440\u0435\u0434\u0435\u043B\u0438\u0442\u044C \u0433\u0440\u0443\u043F\u043F\u0443. \u041E\u0431\u0440\u0430\u0442\u0438\u0442\u0435\u0441\u044C \u043A \u0430\u0434\u043C\u0438\u043D\u0438\u0441\u0442\u0440\u0430\u0442\u043E\u0440\u0443.");
      this.loading.set(false);
      return;
    }
    this.loadSubjects();
    this.loadWeek();
  }
  loadSubjects() {
    this.headmanApi.listSubjects(0, 200).pipe(catchError(() => of(null))).subscribe((resp) => {
      if (!resp?._embedded)
        return;
      const list = Object.values(resp._embedded)[0] ?? [];
      const map = /* @__PURE__ */ new Map();
      for (const s of list)
        map.set(s.id, s.name);
      this.subjects.set(map);
    });
  }
  loadWeek() {
    const user = this.auth.currentUser();
    if (!user?.groupId)
      return;
    const groupId = user.groupId;
    this.loading.set(true);
    this.error.set(null);
    const mon = this.monday();
    const sun = addDays(mon, 6);
    const dateFrom = formatDate(mon);
    const dateTo = formatDate(sun);
    forkJoin({
      members: this.headmanApi.getGroupMembers(0, 200).pipe(catchError(() => of(null))),
      lessons: this.headmanApi.getGroupLessons(groupId, dateFrom, dateTo).pipe(catchError(() => of(null)))
    }).subscribe(({ members, lessons }) => {
      if (members?._embedded) {
        const list = Object.values(members._embedded)[0] ?? [];
        this.students.set(list.map((m) => ({
          userId: m.userId ?? m.id,
          displayName: m.fullName ?? m.displayName ?? `#${m.userId ?? m.id}`
        })));
      } else {
        this.students.set([]);
      }
      const embedded = lessons?._embedded;
      const rawLessons = embedded ? Object.values(embedded)[0] ?? [] : [];
      const normalised = rawLessons.map((entry) => entry?.content ?? entry).filter(Boolean);
      this.lessons.set(normalised);
      if (normalised.length === 0) {
        this.statusMap.set(/* @__PURE__ */ new Map());
        this.loading.set(false);
        return;
      }
      forkJoin(normalised.map((l) => this.headmanApi.getLessonAttendance(l.id).pipe(catchError(() => of(null))))).subscribe((results) => {
        const map = /* @__PURE__ */ new Map();
        const reasons = /* @__PURE__ */ new Map();
        for (let i = 0; i < normalised.length; i++) {
          const lesson = normalised[i];
          const resp = results[i];
          const payload = resp?.content ?? resp;
          const entries = payload?.entries ?? [];
          for (const e of entries) {
            const status = (e.status ?? "").toLowerCase();
            if (!status)
              continue;
            const key = `${lesson.id}_${e.userId}`;
            map.set(key, status);
            if (e.excuseReason) {
              reasons.set(key, e.excuseReason);
            }
          }
        }
        this.statusMap.set(map);
        this.reasonMap.set(reasons);
        this.loading.set(false);
      });
    });
  }
  prevWeek() {
    this.monday.set(addDays(this.monday(), -7));
    this.loadWeek();
  }
  nextWeek() {
    if (this.isCurrentWeek())
      return;
    const next = addDays(this.monday(), 7);
    const currentMonday = getMonday(/* @__PURE__ */ new Date());
    if (next.getTime() > currentMonday.getTime()) {
      this.monday.set(currentMonday);
    } else {
      this.monday.set(next);
    }
    this.loadWeek();
  }
  getStatus(lessonId, userId) {
    return this.statusMap().get(`${lessonId}_${userId}`);
  }
  getReason(lessonId, userId) {
    return this.reasonMap().get(`${lessonId}_${userId}`) ?? null;
  }
  isSaving(lessonId, userId) {
    return this.savingCells().has(`${lessonId}_${userId}`);
  }
  isFlashing(lessonId, userId) {
    return this.flashCells().has(`${lessonId}_${userId}`);
  }
  onStatusClick(lessonId, userId, next) {
    const key = `${lessonId}_${userId}`;
    const current = this.statusMap().get(key);
    if (current === next)
      return;
    const prev = current;
    const map = new Map(this.statusMap());
    map.set(key, next);
    this.statusMap.set(map);
    const saving = new Set(this.savingCells());
    saving.add(key);
    this.savingCells.set(saving);
    this.headmanApi.markAttendance(lessonId, userId, next).pipe(catchError(() => {
      const rollback = new Map(this.statusMap());
      if (prev)
        rollback.set(key, prev);
      else
        rollback.delete(key);
      this.statusMap.set(rollback);
      this.snackBar.open("\u041D\u0435 \u0443\u0434\u0430\u043B\u043E\u0441\u044C \u0441\u043E\u0445\u0440\u0430\u043D\u0438\u0442\u044C. \u041F\u043E\u0432\u0442\u043E\u0440\u0438\u0442\u0435.", void 0, { duration: 4e3 });
      return of(null);
    })).subscribe((result) => {
      const s = new Set(this.savingCells());
      s.delete(key);
      this.savingCells.set(s);
      if (result !== null) {
        const f = new Set(this.flashCells());
        f.add(key);
        this.flashCells.set(f);
        setTimeout(() => {
          const f2 = new Set(this.flashCells());
          f2.delete(key);
          this.flashCells.set(f2);
        }, 600);
      }
    });
  }
  static \u0275fac = function HeadmanWeeklyJournalComponent_Factory(__ngFactoryType__) {
    return new (__ngFactoryType__ || _HeadmanWeeklyJournalComponent)();
  };
  static \u0275cmp = /* @__PURE__ */ \u0275\u0275defineComponent({ type: _HeadmanWeeklyJournalComponent, selectors: [["app-headman-weekly-journal"]], decls: 21, vars: 5, consts: [[1, "page-stack"], [1, "page-header"], [1, "page-eyebrow"], [1, "page-title"], [1, "page-subtitle"], ["role", "navigation", "aria-label", "\u041D\u0430\u0432\u0438\u0433\u0430\u0446\u0438\u044F \u043F\u043E \u043D\u0435\u0434\u0435\u043B\u044F\u043C", 1, "week-nav"], ["type", "button", "aria-label", "\u041F\u0440\u0435\u0434\u044B\u0434\u0443\u0449\u0430\u044F \u043D\u0435\u0434\u0435\u043B\u044F", 1, "week-nav__btn", 3, "click", "disabled"], [1, "ph", "ph-caret-left"], ["aria-live", "polite", 1, "week-nav__range"], ["type", "button", "aria-label", "\u0421\u043B\u0435\u0434\u0443\u044E\u0449\u0430\u044F \u043D\u0435\u0434\u0435\u043B\u044F", 1, "week-nav__btn", 3, "click", "disabled"], [1, "ph", "ph-caret-right"], [1, "page-card", "page-card--flush"], [1, "loader"], [1, "page-error"], [1, "page-empty"], [1, "grid-wrapper"], ["diameter", "32"], [1, "ph", "ph-warning-circle"], [1, "ph-duotone", "ph-calendar-blank"], [1, "journal-table"], [1, "row-day-header"], ["rowspan", "2", 1, "col-student", "col-student--head"], [1, "col-day", 3, "col-day--last"], [1, "row-subject-header"], [1, "row-student"], [1, "col-day"], [1, "col-day__weekday"], [1, "col-day__date"], ["colspan", "3", 1, "col-subject", 3, "col-subject--day-end", "col-subject--day-inner"], ["colspan", "3", 1, "col-subject"], [1, "col-subject__text", 3, "title"], [1, "col-subject__name"], [1, "col-student", 3, "title"], [1, "cell"], ["type", "button", 1, "dot", "dot--present", 3, "click"], ["type", "button", 1, "dot", "dot--absent", 3, "click"], ["type", "button", 1, "dot", "dot--excused", 3, "click"]], template: function HeadmanWeeklyJournalComponent_Template(rf, ctx) {
    if (rf & 1) {
      \u0275\u0275elementStart(0, "div", 0)(1, "div", 1)(2, "div")(3, "span", 2);
      \u0275\u0275text(4, "\u0421\u0442\u0430\u0440\u043E\u0441\u0442\u0430\u0442");
      \u0275\u0275elementEnd();
      \u0275\u0275elementStart(5, "h1", 3);
      \u0275\u0275text(6, "\u0416\u0443\u0440\u043D\u0430\u043B \u043F\u043E\u0441\u0435\u0449\u0430\u0435\u043C\u043E\u0441\u0442\u0438");
      \u0275\u0275elementEnd();
      \u0275\u0275elementStart(7, "p", 4);
      \u0275\u0275text(8, "\u041E\u0442\u043C\u0435\u0442\u044C\u0442\u0435 \u043F\u0440\u0438\u0441\u0443\u0442\u0441\u0442\u0432\u0438\u0435 \u0441\u0442\u0443\u0434\u0435\u043D\u0442\u043E\u0432");
      \u0275\u0275elementEnd()()();
      \u0275\u0275elementStart(9, "div", 5)(10, "button", 6);
      \u0275\u0275listener("click", function HeadmanWeeklyJournalComponent_Template_button_click_10_listener() {
        return ctx.prevWeek();
      });
      \u0275\u0275element(11, "i", 7);
      \u0275\u0275elementEnd();
      \u0275\u0275elementStart(12, "span", 8);
      \u0275\u0275text(13);
      \u0275\u0275elementEnd();
      \u0275\u0275elementStart(14, "button", 9);
      \u0275\u0275listener("click", function HeadmanWeeklyJournalComponent_Template_button_click_14_listener() {
        return ctx.nextWeek();
      });
      \u0275\u0275element(15, "i", 10);
      \u0275\u0275elementEnd()();
      \u0275\u0275elementStart(16, "div", 11);
      \u0275\u0275template(17, HeadmanWeeklyJournalComponent_Conditional_17_Template, 2, 0, "div", 12)(18, HeadmanWeeklyJournalComponent_Conditional_18_Template, 3, 1, "div", 13)(19, HeadmanWeeklyJournalComponent_Conditional_19_Template, 6, 0, "div", 14)(20, HeadmanWeeklyJournalComponent_Conditional_20_Template, 14, 0, "div", 15);
      \u0275\u0275elementEnd()();
    }
    if (rf & 2) {
      \u0275\u0275property("@routeFade", void 0);
      \u0275\u0275advance(10);
      \u0275\u0275property("disabled", ctx.loading());
      \u0275\u0275advance(3);
      \u0275\u0275textInterpolate(ctx.weekRange());
      \u0275\u0275advance();
      \u0275\u0275property("disabled", ctx.loading() || ctx.isCurrentWeek());
      \u0275\u0275advance(3);
      \u0275\u0275conditional(ctx.loading() ? 17 : ctx.error() ? 18 : !ctx.hasLessons() ? 19 : 20);
    }
  }, dependencies: [CommonModule, MatProgressSpinnerModule, MatProgressSpinner], styles: ["\n\n[_nghost-%COMP%] {\n  display: block;\n  width: 100%;\n}\n.page-subtitle[_ngcontent-%COMP%] {\n  margin: 4px 0 0;\n  color: var(--mat-sys-on-surface-variant);\n  font-size: 14px;\n}\n.week-nav[_ngcontent-%COMP%] {\n  display: flex;\n  align-items: center;\n  justify-content: center;\n  gap: 16px;\n  padding: 8px 12px;\n  background: var(--mat-sys-surface-container);\n  border: 1px solid var(--mat-sys-outline-variant);\n  border-radius: 12px;\n  align-self: center;\n  width: fit-content;\n  margin: 0 auto;\n}\n.week-nav__btn[_ngcontent-%COMP%] {\n  border: 1px solid var(--mat-sys-outline-variant);\n  background: var(--mat-sys-surface);\n  width: 36px;\n  height: 36px;\n  border-radius: 50%;\n  cursor: pointer;\n  display: flex;\n  align-items: center;\n  justify-content: center;\n  color: var(--mat-sys-on-surface);\n  transition: background 0.12s ease;\n}\n.week-nav__btn[_ngcontent-%COMP%]:hover:not(:disabled) {\n  background: var(--mat-sys-surface-variant);\n}\n.week-nav__btn[_ngcontent-%COMP%]:disabled {\n  opacity: 0.4;\n  cursor: not-allowed;\n}\n.week-nav__range[_ngcontent-%COMP%] {\n  font-weight: 600;\n  font-size: 15px;\n  min-width: 140px;\n  text-align: center;\n  color: var(--mat-sys-on-surface);\n}\n.loader[_ngcontent-%COMP%] {\n  display: flex;\n  justify-content: center;\n  padding: 48px 16px;\n}\n.grid-wrapper[_ngcontent-%COMP%] {\n  overflow-x: auto;\n  overflow-y: visible;\n  border-radius: 8px;\n  position: relative;\n}\n.journal-table[_ngcontent-%COMP%] {\n  border-collapse: separate;\n  border-spacing: 0;\n  width: max-content;\n  min-width: 100%;\n  font-size: 13px;\n}\n.journal-table[_ngcontent-%COMP%] {\n  --subject-divider: var(--border-subtle, var(--mat-sys-outline-variant));\n  --day-divider: var(--subject-divider);\n  --day-divider-width: 20px;\n}\n.col-student[_ngcontent-%COMP%] {\n  position: sticky;\n  left: 0;\n  z-index: 3;\n  width: 220px;\n  min-width: 220px;\n  max-width: 220px;\n  padding: 10px 14px;\n  background: var(--mat-sys-surface);\n  border-right: 2px solid var(--mat-sys-outline-variant);\n  border-bottom: 1px solid var(--subject-divider);\n  text-align: left;\n  white-space: normal;\n  word-break: break-word;\n  vertical-align: middle;\n  line-height: 1.3;\n  font-size: 13px;\n}\n.col-student--head[_ngcontent-%COMP%] {\n  background: var(--bg-elevated, var(--mat-sys-surface-container-high));\n  z-index: 6;\n  font-weight: 700;\n  font-size: var(--text-lg, 16px);\n}\n.col-day[_ngcontent-%COMP%] {\n  padding: 12px 8px;\n  font-size: var(--text-lg, 16px);\n  font-weight: 700;\n  text-align: center;\n  border-bottom: 1px solid var(--mat-sys-outline-variant);\n  border-right: var(--day-divider-width) solid var(--day-divider);\n  color: var(--mat-sys-on-surface);\n  background: var(--bg-elevated, var(--mat-sys-surface-container-high));\n  letter-spacing: 0.3px;\n}\n.col-day[_ngcontent-%COMP%]:last-child {\n  border-right: none;\n}\n.col-day__weekday[_ngcontent-%COMP%] {\n  display: inline-block;\n  margin-right: 8px;\n  opacity: 0.8;\n  font-weight: 600;\n}\n.col-day__date[_ngcontent-%COMP%] {\n  font-weight: 700;\n}\n.col-subject[_ngcontent-%COMP%] {\n  padding: 8px 2px;\n  min-width: 78px;\n  width: 78px;\n  background: var(--mat-sys-surface-container);\n  border-bottom: 1px solid var(--subject-divider);\n  vertical-align: bottom;\n  height: 160px;\n}\n.col-subject--day-inner[_ngcontent-%COMP%] {\n  border-right: 1px solid var(--subject-divider);\n}\n.col-subject--day-end[_ngcontent-%COMP%] {\n  border-right: var(--day-divider-width) solid var(--day-divider);\n}\n.col-subject__text[_ngcontent-%COMP%] {\n  writing-mode: vertical-rl;\n  transform: rotate(180deg);\n  display: flex;\n  align-items: center;\n  justify-content: flex-start;\n  font-size: 12px;\n  line-height: 1.2;\n  text-align: left;\n  max-height: 148px;\n  margin: 0 auto;\n  padding: 4px 2px;\n  overflow: hidden;\n}\n.col-subject__name[_ngcontent-%COMP%] {\n  font-weight: 600;\n  letter-spacing: 0.2px;\n  white-space: normal;\n  word-break: break-word;\n  overflow-wrap: anywhere;\n  max-width: 148px;\n  display: -webkit-box;\n  -webkit-line-clamp: 4;\n  line-clamp: 4;\n  -webkit-box-orient: vertical;\n  overflow: hidden;\n  text-overflow: ellipsis;\n}\n.row-student[_ngcontent-%COMP%] {\n  transition: background 0.1s ease;\n}\n.row-student[_ngcontent-%COMP%]:hover {\n  background: color-mix(in srgb, var(--mat-sys-primary) 5%, transparent);\n}\n.row-student[_ngcontent-%COMP%]:hover   .col-student[_ngcontent-%COMP%] {\n  background: color-mix(in srgb, var(--mat-sys-primary) 7%, var(--mat-sys-surface));\n}\n.cell[_ngcontent-%COMP%] {\n  width: 26px;\n  min-width: 26px;\n  max-width: 26px;\n  padding: 3px 0;\n  text-align: center;\n  border-bottom: 1px solid var(--subject-divider);\n  vertical-align: middle;\n}\n.cell--subject-end[_ngcontent-%COMP%] {\n  border-right: 1px solid var(--subject-divider);\n}\n.cell--day-end[_ngcontent-%COMP%] {\n  border-right: var(--day-divider-width) solid var(--day-divider);\n}\n.cell--saving[_ngcontent-%COMP%] {\n  opacity: 0.6;\n}\n.dot[_ngcontent-%COMP%] {\n  width: 22px;\n  height: 22px;\n  border-radius: 50%;\n  border: 1px solid var(--mat-sys-outline-variant);\n  background: transparent;\n  color: var(--mat-sys-on-surface-variant);\n  font-size: 11px;\n  font-weight: 700;\n  cursor: pointer;\n  display: inline-flex;\n  align-items: center;\n  justify-content: center;\n  opacity: 0.45;\n  transition:\n    transform 0.1s ease,\n    opacity 0.15s ease,\n    background 0.15s ease;\n  padding: 0;\n  outline: none;\n  box-shadow: none;\n  -webkit-tap-highlight-color: transparent;\n}\n.dot[_ngcontent-%COMP%]:focus, \n.dot[_ngcontent-%COMP%]:focus-visible, \n.dot[_ngcontent-%COMP%]:active {\n  outline: none;\n  box-shadow: none;\n}\n.dot[_ngcontent-%COMP%]    + .dot[_ngcontent-%COMP%] {\n  margin-left: 2px;\n}\n.dot[_ngcontent-%COMP%]:hover {\n  opacity: 1;\n  transform: scale(1.1);\n}\n.dot--active[_ngcontent-%COMP%] {\n  opacity: 1;\n  color: var(--accent-primary-contrast, #fff);\n  border-color: transparent;\n  box-shadow: none;\n}\n.dot--present.dot--active[_ngcontent-%COMP%] {\n  background: var(--status-present);\n}\n.dot--absent.dot--active[_ngcontent-%COMP%] {\n  background: var(--status-absent);\n}\n.dot--excused.dot--active[_ngcontent-%COMP%] {\n  background: var(--status-excused);\n}\n/*# sourceMappingURL=headman-weekly-journal.component.css.map */"], data: { animation: [
    trigger("routeFade", [
      transition(":enter", [
        style({ opacity: 0, transform: "translateY(8px)" }),
        animate("200ms cubic-bezier(0.16, 1, 0.3, 1)", style({ opacity: 1, transform: "translateY(0)" }))
      ])
    ])
  ] }, changeDetection: 0 });
};
(() => {
  (typeof ngDevMode === "undefined" || ngDevMode) && setClassMetadata(HeadmanWeeklyJournalComponent, [{
    type: Component,
    args: [{ selector: "app-headman-weekly-journal", standalone: true, changeDetection: ChangeDetectionStrategy.OnPush, imports: [CommonModule, MatProgressSpinnerModule], animations: [
      trigger("routeFade", [
        transition(":enter", [
          style({ opacity: 0, transform: "translateY(8px)" }),
          animate("200ms cubic-bezier(0.16, 1, 0.3, 1)", style({ opacity: 1, transform: "translateY(0)" }))
        ])
      ])
    ], template: `<div class="page-stack" [@routeFade]>
  <div class="page-header">
    <div>
      <span class="page-eyebrow">\u0421\u0442\u0430\u0440\u043E\u0441\u0442\u0430\u0442</span>
      <h1 class="page-title">\u0416\u0443\u0440\u043D\u0430\u043B \u043F\u043E\u0441\u0435\u0449\u0430\u0435\u043C\u043E\u0441\u0442\u0438</h1>
      <p class="page-subtitle">\u041E\u0442\u043C\u0435\u0442\u044C\u0442\u0435 \u043F\u0440\u0438\u0441\u0443\u0442\u0441\u0442\u0432\u0438\u0435 \u0441\u0442\u0443\u0434\u0435\u043D\u0442\u043E\u0432</p>
    </div>
  </div>

  <!-- Week navigation -->
  <div class="week-nav" role="navigation" aria-label="\u041D\u0430\u0432\u0438\u0433\u0430\u0446\u0438\u044F \u043F\u043E \u043D\u0435\u0434\u0435\u043B\u044F\u043C">
    <button
      type="button"
      class="week-nav__btn"
      (click)="prevWeek()"
      [disabled]="loading()"
      aria-label="\u041F\u0440\u0435\u0434\u044B\u0434\u0443\u0449\u0430\u044F \u043D\u0435\u0434\u0435\u043B\u044F"
    >
      <i class="ph ph-caret-left"></i>
    </button>
    <span class="week-nav__range" aria-live="polite">{{ weekRange() }}</span>
    <button
      type="button"
      class="week-nav__btn"
      (click)="nextWeek()"
      [disabled]="loading() || isCurrentWeek()"
      aria-label="\u0421\u043B\u0435\u0434\u0443\u044E\u0449\u0430\u044F \u043D\u0435\u0434\u0435\u043B\u044F"
    >
      <i class="ph ph-caret-right"></i>
    </button>
  </div>

  <div class="page-card page-card--flush">
    @if (loading()) {
      <div class="loader">
        <mat-spinner diameter="32"></mat-spinner>
      </div>
    } @else if (error()) {
      <div class="page-error"><i class="ph ph-warning-circle"></i> {{ error() }}</div>
    } @else if (!hasLessons()) {
      <div class="page-empty">
        <i class="ph-duotone ph-calendar-blank"></i>
        <h3>\u041D\u0430 \u044D\u0442\u043E\u0439 \u043D\u0435\u0434\u0435\u043B\u0435 \u043D\u0435\u0442 \u0437\u0430\u043D\u044F\u0442\u0438\u0439</h3>
        <p>\u041F\u0435\u0440\u0435\u0439\u0434\u0438\u0442\u0435 \u043A \u0434\u0440\u0443\u0433\u043E\u0439 \u043D\u0435\u0434\u0435\u043B\u0435, \u0447\u0442\u043E\u0431\u044B \u043F\u0440\u043E\u0434\u043E\u043B\u0436\u0438\u0442\u044C \u0440\u0430\u0431\u043E\u0442\u0443 \u0441 \u0436\u0443\u0440\u043D\u0430\u043B\u043E\u043C.</p>
      </div>
    } @else {
      <div class="grid-wrapper">
        <table class="journal-table">
          <thead>
            <!-- Day headers row -->
            <tr class="row-day-header">
              <th class="col-student col-student--head" rowspan="2">\u0421\u0442\u0443\u0434\u0435\u043D\u0442</th>
              @for (day of dayGroups(); track day.date; let last = $last) {
                <th
                  class="col-day"
                  [class.col-day--last]="!last"
                  [attr.colspan]="day.columns.length * 3"
                >
                  <span class="col-day__weekday">{{ day.weekday }}</span>
                  <span class="col-day__date">{{ day.displayDate }}</span>
                </th>
              }
            </tr>
            <!-- Subject headers -->
            <tr class="row-subject-header">
              @for (day of dayGroups(); track day.date; let dayLast = $last) {
                @for (col of day.columns; track col.lessonId; let colLast = $last) {
                  <th
                    class="col-subject"
                    [class.col-subject--day-end]="colLast && !dayLast"
                    [class.col-subject--day-inner]="!colLast"
                    colspan="3"
                  >
                    <div class="col-subject__text" [title]="col.subjectName">
                      <span class="col-subject__name">{{ col.subjectName }}</span>
                    </div>
                  </th>
                }
              }
            </tr>
          </thead>
          <tbody>
            @for (student of sortedStudents(); track student.userId) {
              <tr class="row-student">
                <td class="col-student" [title]="student.displayName">{{ student.displayName }}</td>
                @for (day of dayGroups(); track day.date; let dayLast = $last) {
                  @for (col of day.columns; track col.lessonId; let colLast = $last) {
                    @let status = getStatus(col.lessonId, student.userId);
                    @let saving = isSaving(col.lessonId, student.userId);
                    @let flashing = isFlashing(col.lessonId, student.userId);
                    <td class="cell" [class.cell--saving]="saving" [class.cell--flash]="flashing">
                      <button
                        type="button"
                        class="dot dot--present"
                        [class.dot--active]="status === 'present'"
                        (click)="onStatusClick(col.lessonId, student.userId, 'present')"
                        [attr.aria-label]="'\u041F\u0440\u0438\u0441\u0443\u0442\u0441\u0442\u0432\u043E\u0432\u0430\u043B \u2014 ' + student.displayName"
                        [attr.aria-pressed]="status === 'present'"
                      >+</button>
                    </td>
                    <td class="cell" [class.cell--saving]="saving" [class.cell--flash]="flashing">
                      <button
                        type="button"
                        class="dot dot--absent"
                        [class.dot--active]="status === 'absent'"
                        (click)="onStatusClick(col.lessonId, student.userId, 'absent')"
                        [attr.aria-label]="'\u041D\u0435 \u0431\u044B\u043B \u2014 ' + student.displayName"
                        [attr.aria-pressed]="status === 'absent'"
                      >\u043D</button>
                    </td>
                    <td
                      class="cell"
                      [class.cell--saving]="saving"
                      [class.cell--flash]="flashing"
                      [class.cell--day-end]="colLast && !dayLast"
                      [class.cell--subject-end]="!colLast"
                    >
                      @let reason = getReason(col.lessonId, student.userId);
                      <button
                        type="button"
                        class="dot dot--excused"
                        [class.dot--active]="status === 'excused' || status === 'free_attendance'"
                        (click)="onStatusClick(col.lessonId, student.userId, 'excused')"
                        [attr.aria-label]="'\u0423\u0432\u0430\u0436\u0438\u0442. \u043F\u0440\u0438\u0447\u0438\u043D\u0430 \u2014 ' + student.displayName + (reason ? (' \xB7 ' + reason) : '')"
                        [attr.aria-pressed]="status === 'excused' || status === 'free_attendance'"
                        [attr.title]="reason"
                      >\u0443</button>
                    </td>
                  }
                }
              </tr>
            }
          </tbody>
        </table>
      </div>
    }
  </div>
</div>
`, styles: ["/* src/app/features/headman/weekly-journal/headman-weekly-journal.component.css */\n:host {\n  display: block;\n  width: 100%;\n}\n.page-subtitle {\n  margin: 4px 0 0;\n  color: var(--mat-sys-on-surface-variant);\n  font-size: 14px;\n}\n.week-nav {\n  display: flex;\n  align-items: center;\n  justify-content: center;\n  gap: 16px;\n  padding: 8px 12px;\n  background: var(--mat-sys-surface-container);\n  border: 1px solid var(--mat-sys-outline-variant);\n  border-radius: 12px;\n  align-self: center;\n  width: fit-content;\n  margin: 0 auto;\n}\n.week-nav__btn {\n  border: 1px solid var(--mat-sys-outline-variant);\n  background: var(--mat-sys-surface);\n  width: 36px;\n  height: 36px;\n  border-radius: 50%;\n  cursor: pointer;\n  display: flex;\n  align-items: center;\n  justify-content: center;\n  color: var(--mat-sys-on-surface);\n  transition: background 0.12s ease;\n}\n.week-nav__btn:hover:not(:disabled) {\n  background: var(--mat-sys-surface-variant);\n}\n.week-nav__btn:disabled {\n  opacity: 0.4;\n  cursor: not-allowed;\n}\n.week-nav__range {\n  font-weight: 600;\n  font-size: 15px;\n  min-width: 140px;\n  text-align: center;\n  color: var(--mat-sys-on-surface);\n}\n.loader {\n  display: flex;\n  justify-content: center;\n  padding: 48px 16px;\n}\n.grid-wrapper {\n  overflow-x: auto;\n  overflow-y: visible;\n  border-radius: 8px;\n  position: relative;\n}\n.journal-table {\n  border-collapse: separate;\n  border-spacing: 0;\n  width: max-content;\n  min-width: 100%;\n  font-size: 13px;\n}\n.journal-table {\n  --subject-divider: var(--border-subtle, var(--mat-sys-outline-variant));\n  --day-divider: var(--subject-divider);\n  --day-divider-width: 20px;\n}\n.col-student {\n  position: sticky;\n  left: 0;\n  z-index: 3;\n  width: 220px;\n  min-width: 220px;\n  max-width: 220px;\n  padding: 10px 14px;\n  background: var(--mat-sys-surface);\n  border-right: 2px solid var(--mat-sys-outline-variant);\n  border-bottom: 1px solid var(--subject-divider);\n  text-align: left;\n  white-space: normal;\n  word-break: break-word;\n  vertical-align: middle;\n  line-height: 1.3;\n  font-size: 13px;\n}\n.col-student--head {\n  background: var(--bg-elevated, var(--mat-sys-surface-container-high));\n  z-index: 6;\n  font-weight: 700;\n  font-size: var(--text-lg, 16px);\n}\n.col-day {\n  padding: 12px 8px;\n  font-size: var(--text-lg, 16px);\n  font-weight: 700;\n  text-align: center;\n  border-bottom: 1px solid var(--mat-sys-outline-variant);\n  border-right: var(--day-divider-width) solid var(--day-divider);\n  color: var(--mat-sys-on-surface);\n  background: var(--bg-elevated, var(--mat-sys-surface-container-high));\n  letter-spacing: 0.3px;\n}\n.col-day:last-child {\n  border-right: none;\n}\n.col-day__weekday {\n  display: inline-block;\n  margin-right: 8px;\n  opacity: 0.8;\n  font-weight: 600;\n}\n.col-day__date {\n  font-weight: 700;\n}\n.col-subject {\n  padding: 8px 2px;\n  min-width: 78px;\n  width: 78px;\n  background: var(--mat-sys-surface-container);\n  border-bottom: 1px solid var(--subject-divider);\n  vertical-align: bottom;\n  height: 160px;\n}\n.col-subject--day-inner {\n  border-right: 1px solid var(--subject-divider);\n}\n.col-subject--day-end {\n  border-right: var(--day-divider-width) solid var(--day-divider);\n}\n.col-subject__text {\n  writing-mode: vertical-rl;\n  transform: rotate(180deg);\n  display: flex;\n  align-items: center;\n  justify-content: flex-start;\n  font-size: 12px;\n  line-height: 1.2;\n  text-align: left;\n  max-height: 148px;\n  margin: 0 auto;\n  padding: 4px 2px;\n  overflow: hidden;\n}\n.col-subject__name {\n  font-weight: 600;\n  letter-spacing: 0.2px;\n  white-space: normal;\n  word-break: break-word;\n  overflow-wrap: anywhere;\n  max-width: 148px;\n  display: -webkit-box;\n  -webkit-line-clamp: 4;\n  line-clamp: 4;\n  -webkit-box-orient: vertical;\n  overflow: hidden;\n  text-overflow: ellipsis;\n}\n.row-student {\n  transition: background 0.1s ease;\n}\n.row-student:hover {\n  background: color-mix(in srgb, var(--mat-sys-primary) 5%, transparent);\n}\n.row-student:hover .col-student {\n  background: color-mix(in srgb, var(--mat-sys-primary) 7%, var(--mat-sys-surface));\n}\n.cell {\n  width: 26px;\n  min-width: 26px;\n  max-width: 26px;\n  padding: 3px 0;\n  text-align: center;\n  border-bottom: 1px solid var(--subject-divider);\n  vertical-align: middle;\n}\n.cell--subject-end {\n  border-right: 1px solid var(--subject-divider);\n}\n.cell--day-end {\n  border-right: var(--day-divider-width) solid var(--day-divider);\n}\n.cell--saving {\n  opacity: 0.6;\n}\n.dot {\n  width: 22px;\n  height: 22px;\n  border-radius: 50%;\n  border: 1px solid var(--mat-sys-outline-variant);\n  background: transparent;\n  color: var(--mat-sys-on-surface-variant);\n  font-size: 11px;\n  font-weight: 700;\n  cursor: pointer;\n  display: inline-flex;\n  align-items: center;\n  justify-content: center;\n  opacity: 0.45;\n  transition:\n    transform 0.1s ease,\n    opacity 0.15s ease,\n    background 0.15s ease;\n  padding: 0;\n  outline: none;\n  box-shadow: none;\n  -webkit-tap-highlight-color: transparent;\n}\n.dot:focus,\n.dot:focus-visible,\n.dot:active {\n  outline: none;\n  box-shadow: none;\n}\n.dot + .dot {\n  margin-left: 2px;\n}\n.dot:hover {\n  opacity: 1;\n  transform: scale(1.1);\n}\n.dot--active {\n  opacity: 1;\n  color: var(--accent-primary-contrast, #fff);\n  border-color: transparent;\n  box-shadow: none;\n}\n.dot--present.dot--active {\n  background: var(--status-present);\n}\n.dot--absent.dot--active {\n  background: var(--status-absent);\n}\n.dot--excused.dot--active {\n  background: var(--status-excused);\n}\n/*# sourceMappingURL=headman-weekly-journal.component.css.map */\n"] }]
  }], null, null);
})();
(() => {
  (typeof ngDevMode === "undefined" || ngDevMode) && \u0275setClassDebugInfo(HeadmanWeeklyJournalComponent, { className: "HeadmanWeeklyJournalComponent", filePath: "src/app/features/headman/weekly-journal/headman-weekly-journal.component.ts", lineNumber: 73 });
})();
export {
  HeadmanWeeklyJournalComponent
};
//# sourceMappingURL=chunk-BFPCJK27.js.map
