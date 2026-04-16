import {
  EXCUSE_TYPE_LABELS,
  ExcuseFormDialogComponent
} from "./chunk-WZUMQHNK.js";
import "./chunk-F5IUR55I.js";
import {
  MatSnackBar
} from "./chunk-53YESMZZ.js";
import {
  SubjectCacheService
} from "./chunk-QY7VTECN.js";
import "./chunk-MQ7WFICG.js";
import {
  StudentApiService
} from "./chunk-ESVH3VU5.js";
import {
  NotificationCenterService
} from "./chunk-PPDDTI5J.js";
import {
  MatDialog
} from "./chunk-M24SPP55.js";
import "./chunk-VAU65FPZ.js";
import {
  takeUntilDestroyed
} from "./chunk-JGPT6EAX.js";
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
import "./chunk-WQN3TC62.js";
import "./chunk-POR7KUSL.js";
import "./chunk-OIBNGD5S.js";
import "./chunk-Z3VV4J2X.js";
import "./chunk-PAS4QIDL.js";
import "./chunk-4RJPSRCU.js";
import "./chunk-6JM2QIGP.js";
import "./chunk-75YPR2VK.js";
import "./chunk-2UA5NBNP.js";
import "./chunk-YIJEORIR.js";
import "./chunk-B3BDHDAM.js";
import "./chunk-XAMIXVT7.js";
import {
  CommonModule,
  DatePipe,
  NgForOf
} from "./chunk-M5DKW26A.js";
import {
  ChangeDetectionStrategy,
  Component,
  DestroyRef,
  __spreadValues,
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
  ɵɵclassMap,
  ɵɵclassProp,
  ɵɵconditional,
  ɵɵdefineComponent,
  ɵɵelement,
  ɵɵelementEnd,
  ɵɵelementStart,
  ɵɵgetCurrentView,
  ɵɵlistener,
  ɵɵnextContext,
  ɵɵpipe,
  ɵɵpipeBind2,
  ɵɵproperty,
  ɵɵpureFunction0,
  ɵɵrepeater,
  ɵɵrepeaterCreate,
  ɵɵrepeaterTrackByIdentity,
  ɵɵresetView,
  ɵɵrestoreView,
  ɵɵtemplate,
  ɵɵtext,
  ɵɵtextInterpolate,
  ɵɵtextInterpolate1
} from "./chunk-MFRIGFR2.js";

// src/app/features/student/excuses/student-excuses.component.ts
var _c0 = () => [1, 2, 3];
var _forTrack0 = ($index, $item) => $item.id;
function StudentExcusesComponent_Conditional_20_div_1_Template(rf, ctx) {
  if (rf & 1) {
    \u0275\u0275element(0, "div", 14);
  }
}
function StudentExcusesComponent_Conditional_20_Template(rf, ctx) {
  if (rf & 1) {
    \u0275\u0275elementStart(0, "div", 10);
    \u0275\u0275template(1, StudentExcusesComponent_Conditional_20_div_1_Template, 1, 0, "div", 13);
    \u0275\u0275elementEnd();
  }
  if (rf & 2) {
    \u0275\u0275advance();
    \u0275\u0275property("ngForOf", \u0275\u0275pureFunction0(1, _c0));
  }
}
function StudentExcusesComponent_Conditional_21_Template(rf, ctx) {
  if (rf & 1) {
    \u0275\u0275elementStart(0, "div", 11);
    \u0275\u0275element(1, "i", 15);
    \u0275\u0275elementStart(2, "h2", 16);
    \u0275\u0275text(3);
    \u0275\u0275elementEnd();
    \u0275\u0275elementStart(4, "p", 17);
    \u0275\u0275text(5);
    \u0275\u0275elementEnd()();
  }
  if (rf & 2) {
    const ctx_r0 = \u0275\u0275nextContext();
    \u0275\u0275advance(3);
    \u0275\u0275textInterpolate1(" ", ctx_r0.currentTab() === "active" ? "\u041D\u0435\u0442 \u0430\u043A\u0442\u0438\u0432\u043D\u044B\u0445 \u0442\u0438\u043A\u0435\u0442\u043E\u0432" : "\u0410\u0440\u0445\u0438\u0432 \u043F\u0443\u0441\u0442", " ");
    \u0275\u0275advance(2);
    \u0275\u0275textInterpolate1(" ", ctx_r0.currentTab() === "active" ? "\u041F\u043E\u0434\u0430\u0439\u0442\u0435 \u0442\u0438\u043A\u0435\u0442, \u0435\u0441\u043B\u0438 \u043F\u0440\u043E\u043F\u0443\u0441\u0442\u0438\u043B\u0438 \u0437\u0430\u043D\u044F\u0442\u0438\u0435 \u043F\u043E \u0443\u0432\u0430\u0436\u0438\u0442\u0435\u043B\u044C\u043D\u043E\u0439 \u043F\u0440\u0438\u0447\u0438\u043D\u0435." : "\u0417\u0434\u0435\u0441\u044C \u043F\u043E\u044F\u0432\u043B\u044F\u044E\u0442\u0441\u044F \u043E\u0434\u043E\u0431\u0440\u0435\u043D\u043D\u044B\u0435 \u0438 \u043E\u0442\u043A\u043B\u043E\u043D\u0451\u043D\u043D\u044B\u0435 \u0442\u0438\u043A\u0435\u0442\u044B.", " ");
  }
}
function StudentExcusesComponent_Conditional_22_For_12_Conditional_10_Conditional_19_Template(rf, ctx) {
  if (rf & 1) {
    \u0275\u0275elementStart(0, "div")(1, "dt");
    \u0275\u0275text(2, "\u0420\u0435\u0448\u0435\u043D\u0438\u0435");
    \u0275\u0275elementEnd();
    \u0275\u0275elementStart(3, "dd");
    \u0275\u0275text(4);
    \u0275\u0275pipe(5, "date");
    \u0275\u0275elementEnd()();
  }
  if (rf & 2) {
    const ticket_r3 = \u0275\u0275nextContext(2).$implicit;
    \u0275\u0275advance(4);
    \u0275\u0275textInterpolate(\u0275\u0275pipeBind2(5, 1, ticket_r3.decisionAt, "dd.MM.yyyy HH:mm"));
  }
}
function StudentExcusesComponent_Conditional_22_For_12_Conditional_10_Conditional_20_Template(rf, ctx) {
  if (rf & 1) {
    \u0275\u0275elementStart(0, "section", 28)(1, "h4", 29);
    \u0275\u0275text(2, "\u041A\u043E\u043C\u043C\u0435\u043D\u0442\u0430\u0440\u0438\u0439 \u0441\u0442\u0443\u0434\u0435\u043D\u0442\u0430");
    \u0275\u0275elementEnd();
    \u0275\u0275elementStart(3, "p", 31);
    \u0275\u0275text(4);
    \u0275\u0275elementEnd()();
  }
  if (rf & 2) {
    const ticket_r3 = \u0275\u0275nextContext(2).$implicit;
    \u0275\u0275advance(4);
    \u0275\u0275textInterpolate(ticket_r3.comment);
  }
}
function StudentExcusesComponent_Conditional_22_For_12_Conditional_10_Conditional_21_Template(rf, ctx) {
  if (rf & 1) {
    \u0275\u0275elementStart(0, "section", 28)(1, "h4", 29);
    \u0275\u0275text(2, "\u041A\u043E\u043C\u043C\u0435\u043D\u0442\u0430\u0440\u0438\u0439 \u0441\u0442\u0430\u0440\u043E\u0441\u0442\u044B");
    \u0275\u0275elementEnd();
    \u0275\u0275elementStart(3, "p", 31);
    \u0275\u0275text(4);
    \u0275\u0275elementEnd()();
  }
  if (rf & 2) {
    const ticket_r3 = \u0275\u0275nextContext(2).$implicit;
    \u0275\u0275advance(4);
    \u0275\u0275textInterpolate(ticket_r3.decisionComment);
  }
}
function StudentExcusesComponent_Conditional_22_For_12_Conditional_10_For_27_Template(rf, ctx) {
  if (rf & 1) {
    \u0275\u0275elementStart(0, "li");
    \u0275\u0275text(1);
    \u0275\u0275elementEnd();
  }
  if (rf & 2) {
    const lid_r4 = ctx.$implicit;
    const ctx_r0 = \u0275\u0275nextContext(4);
    \u0275\u0275advance();
    \u0275\u0275textInterpolate(ctx_r0.lessonLabel(lid_r4));
  }
}
function StudentExcusesComponent_Conditional_22_For_12_Conditional_10_Template(rf, ctx) {
  if (rf & 1) {
    \u0275\u0275elementStart(0, "div", 26)(1, "dl", 27)(2, "div")(3, "dt");
    \u0275\u0275text(4, "\u041F\u0440\u0438\u0447\u0438\u043D\u0430");
    \u0275\u0275elementEnd();
    \u0275\u0275elementStart(5, "dd");
    \u0275\u0275text(6);
    \u0275\u0275elementEnd()();
    \u0275\u0275elementStart(7, "div")(8, "dt");
    \u0275\u0275text(9, "\u0421\u0442\u0430\u0442\u0443\u0441");
    \u0275\u0275elementEnd();
    \u0275\u0275elementStart(10, "dd")(11, "span", 25);
    \u0275\u0275text(12);
    \u0275\u0275elementEnd()()();
    \u0275\u0275elementStart(13, "div")(14, "dt");
    \u0275\u0275text(15, "\u041F\u043E\u0434\u0430\u043D");
    \u0275\u0275elementEnd();
    \u0275\u0275elementStart(16, "dd");
    \u0275\u0275text(17);
    \u0275\u0275pipe(18, "date");
    \u0275\u0275elementEnd()();
    \u0275\u0275template(19, StudentExcusesComponent_Conditional_22_For_12_Conditional_10_Conditional_19_Template, 6, 4, "div");
    \u0275\u0275elementEnd();
    \u0275\u0275template(20, StudentExcusesComponent_Conditional_22_For_12_Conditional_10_Conditional_20_Template, 5, 1, "section", 28)(21, StudentExcusesComponent_Conditional_22_For_12_Conditional_10_Conditional_21_Template, 5, 1, "section", 28);
    \u0275\u0275elementStart(22, "section", 28)(23, "h4", 29);
    \u0275\u0275text(24);
    \u0275\u0275elementEnd();
    \u0275\u0275elementStart(25, "ul", 30);
    \u0275\u0275repeaterCreate(26, StudentExcusesComponent_Conditional_22_For_12_Conditional_10_For_27_Template, 2, 1, "li", null, \u0275\u0275repeaterTrackByIdentity);
    \u0275\u0275elementEnd()()();
  }
  if (rf & 2) {
    const ticket_r3 = \u0275\u0275nextContext().$implicit;
    const ctx_r0 = \u0275\u0275nextContext(2);
    \u0275\u0275attribute("aria-label", "\u0414\u0435\u0442\u0430\u043B\u0438 \u0442\u0438\u043A\u0435\u0442\u0430 " + ticket_r3.id);
    \u0275\u0275advance(6);
    \u0275\u0275textInterpolate(ctx_r0.labelForExcuseType(ticket_r3.excuseType));
    \u0275\u0275advance(5);
    \u0275\u0275classMap("status-chip--" + ticket_r3.status);
    \u0275\u0275advance();
    \u0275\u0275textInterpolate1(" ", ctx_r0.statusLabels[ticket_r3.status], " ");
    \u0275\u0275advance(5);
    \u0275\u0275textInterpolate(\u0275\u0275pipeBind2(18, 10, ticket_r3.createdAt, "dd.MM.yyyy HH:mm"));
    \u0275\u0275advance(2);
    \u0275\u0275conditional(ticket_r3.decisionAt ? 19 : -1);
    \u0275\u0275advance();
    \u0275\u0275conditional(ticket_r3.comment ? 20 : -1);
    \u0275\u0275advance();
    \u0275\u0275conditional(ticket_r3.decisionComment ? 21 : -1);
    \u0275\u0275advance(3);
    \u0275\u0275textInterpolate1("\u041F\u0430\u0440\u044B (", ticket_r3.lessonIds.length, ")");
    \u0275\u0275advance(2);
    \u0275\u0275repeater(ticket_r3.lessonIds);
  }
}
function StudentExcusesComponent_Conditional_22_For_12_Template(rf, ctx) {
  if (rf & 1) {
    const _r2 = \u0275\u0275getCurrentView();
    \u0275\u0275elementStart(0, "button", 21);
    \u0275\u0275listener("click", function StudentExcusesComponent_Conditional_22_For_12_Template_button_click_0_listener() {
      const ticket_r3 = \u0275\u0275restoreView(_r2).$implicit;
      const ctx_r0 = \u0275\u0275nextContext(2);
      return \u0275\u0275resetView(ctx_r0.toggleTicket(ticket_r3));
    });
    \u0275\u0275elementStart(1, "span", 22);
    \u0275\u0275text(2);
    \u0275\u0275pipe(3, "date");
    \u0275\u0275elementEnd();
    \u0275\u0275elementStart(4, "span", 23);
    \u0275\u0275text(5);
    \u0275\u0275elementEnd();
    \u0275\u0275elementStart(6, "span", 24);
    \u0275\u0275text(7);
    \u0275\u0275elementEnd();
    \u0275\u0275elementStart(8, "span", 25);
    \u0275\u0275text(9);
    \u0275\u0275elementEnd()();
    \u0275\u0275template(10, StudentExcusesComponent_Conditional_22_For_12_Conditional_10_Template, 28, 13, "div", 26);
  }
  if (rf & 2) {
    const ticket_r3 = ctx.$implicit;
    const ctx_r0 = \u0275\u0275nextContext(2);
    \u0275\u0275classProp("excuse-table__row--expanded", ctx_r0.isExpanded(ticket_r3.id));
    \u0275\u0275attribute("aria-expanded", ctx_r0.isExpanded(ticket_r3.id));
    \u0275\u0275advance(2);
    \u0275\u0275textInterpolate(\u0275\u0275pipeBind2(3, 11, ticket_r3.createdAt, "dd.MM.yyyy"));
    \u0275\u0275advance(3);
    \u0275\u0275textInterpolate(ctx_r0.labelForExcuseType(ticket_r3.excuseType));
    \u0275\u0275advance(2);
    \u0275\u0275textInterpolate(ticket_r3.lessonIds.length);
    \u0275\u0275advance();
    \u0275\u0275classMap("status-chip--" + ticket_r3.status);
    \u0275\u0275attribute("aria-label", ctx_r0.statusLabels[ticket_r3.status]);
    \u0275\u0275advance();
    \u0275\u0275textInterpolate(ctx_r0.statusLabels[ticket_r3.status]);
    \u0275\u0275advance();
    \u0275\u0275conditional(ctx_r0.isExpanded(ticket_r3.id) ? 10 : -1);
  }
}
function StudentExcusesComponent_Conditional_22_Template(rf, ctx) {
  if (rf & 1) {
    \u0275\u0275elementStart(0, "div", 12)(1, "div", 18)(2, "div", 19)(3, "span", 20);
    \u0275\u0275text(4, "\u0414\u0430\u0442\u0430");
    \u0275\u0275elementEnd();
    \u0275\u0275elementStart(5, "span", 20);
    \u0275\u0275text(6, "\u041F\u0440\u0438\u0447\u0438\u043D\u0430");
    \u0275\u0275elementEnd();
    \u0275\u0275elementStart(7, "span", 20);
    \u0275\u0275text(8, "\u0417\u0430\u043D\u044F\u0442\u0438\u0439");
    \u0275\u0275elementEnd();
    \u0275\u0275elementStart(9, "span", 20);
    \u0275\u0275text(10, "\u0421\u0442\u0430\u0442\u0443\u0441");
    \u0275\u0275elementEnd()();
    \u0275\u0275repeaterCreate(11, StudentExcusesComponent_Conditional_22_For_12_Template, 11, 14, null, null, _forTrack0);
    \u0275\u0275elementEnd()();
  }
  if (rf & 2) {
    const ctx_r0 = \u0275\u0275nextContext();
    \u0275\u0275advance(11);
    \u0275\u0275repeater(ctx_r0.visibleTickets());
  }
}
var ACTIVE_STATUSES = ["draft", "submitted"];
var ARCHIVE_STATUSES = ["approved", "rejected"];
var StudentExcusesComponent = class _StudentExcusesComponent {
  apiService = inject(StudentApiService);
  subjectCache = inject(SubjectCacheService);
  auth = inject(AuthService);
  dialog = inject(MatDialog);
  snackBar = inject(MatSnackBar);
  center = inject(NotificationCenterService);
  destroyRef = inject(DestroyRef);
  loading = signal(false);
  error = signal(null);
  tickets = signal([]);
  currentTab = signal("active");
  expandedTicketId = signal(null);
  /** lesson cache — keyed by lessonId, lazy-loaded once per ticket expand. */
  lessonCache = signal({});
  subjectNameCache = signal({});
  /** Partition tickets by lifecycle bucket so each tab renders instantly. */
  activeTickets = computed(() => this.tickets().filter((t) => ACTIVE_STATUSES.includes(t.status)));
  archiveTickets = computed(() => this.tickets().filter((t) => ARCHIVE_STATUSES.includes(t.status)));
  visibleTickets = computed(() => this.currentTab() === "active" ? this.activeTickets() : this.archiveTickets());
  statusLabels = {
    draft: "\u0427\u0435\u0440\u043D\u043E\u0432\u0438\u043A",
    submitted: "\u041D\u0430 \u0440\u0430\u0441\u0441\u043C\u043E\u0442\u0440\u0435\u043D\u0438\u0438",
    approved: "\u041E\u0434\u043E\u0431\u0440\u0435\u043D\u043E",
    rejected: "\u041E\u0442\u043A\u043B\u043E\u043D\u0435\u043D\u043E"
  };
  excuseTypeLabels = EXCUSE_TYPE_LABELS;
  labelForExcuseType(type) {
    return this.excuseTypeLabels[type] ?? type;
  }
  ngOnInit() {
    this.loadTickets();
    this.center.onEvent$.pipe(takeUntilDestroyed(this.destroyRef)).subscribe((envelope) => {
      if (envelope.type === "excuse.decided") {
        this.loadTickets();
      }
    });
  }
  switchTab(tab) {
    this.currentTab.set(tab);
    this.expandedTicketId.set(null);
  }
  toggleTicket(ticket) {
    const current = this.expandedTicketId();
    if (current === ticket.id) {
      this.expandedTicketId.set(null);
      return;
    }
    this.expandedTicketId.set(ticket.id);
    this.ensureLessonsLoaded(ticket);
  }
  isExpanded(ticketId) {
    return this.expandedTicketId() === ticketId;
  }
  /**
   * Fetch schedule info for each lesson in the ticket. We pull only the missing
   * lessons so a user toggling several tickets doesn't refetch the same lesson
   * twice. Lessons are pulled via the existing week endpoint, filtered client-side
   * to the requested IDs.
   */
  ensureLessonsLoaded(ticket) {
    const missing = ticket.lessonIds.filter((id) => !this.lessonCache()[id]);
    if (missing.length === 0) {
      this.ensureSubjectsLoaded(ticket);
      return;
    }
    const groupId = this.auth.currentUser()?.groupId ?? ticket.groupId;
    if (!groupId) {
      this.ensureSubjectsLoaded(ticket);
      return;
    }
    const today = /* @__PURE__ */ new Date();
    const from = new Date(today.getTime() - 60 * 24 * 60 * 60 * 1e3);
    const to = new Date(today.getTime() + 30 * 24 * 60 * 60 * 1e3);
    const fmt = (d) => {
      const y = d.getFullYear();
      const m = String(d.getMonth() + 1).padStart(2, "0");
      const day = String(d.getDate()).padStart(2, "0");
      return `${y}-${m}-${day}`;
    };
    this.apiService.getWeekLessons(groupId, fmt(from), fmt(to)).pipe(catchError(() => of([]))).subscribe((lessons) => {
      const updates = {};
      for (const l of lessons) {
        if (ticket.lessonIds.includes(l.id))
          updates[l.id] = l;
      }
      this.lessonCache.update((prev) => __spreadValues(__spreadValues({}, prev), updates));
      this.ensureSubjectsLoaded(ticket);
    });
  }
  ensureSubjectsLoaded(ticket) {
    const cached = this.subjectNameCache();
    const subjectIds = /* @__PURE__ */ new Set();
    for (const id of ticket.lessonIds) {
      const lesson = this.lessonCache()[id];
      if (lesson && !cached[lesson.subjectId])
        subjectIds.add(lesson.subjectId);
    }
    if (subjectIds.size === 0)
      return;
    const calls = Array.from(subjectIds).map((sid) => this.subjectCache.getName(sid).pipe(catchError(() => of("\u041F\u0440\u0435\u0434\u043C\u0435\u0442"))));
    if (calls.length === 0)
      return;
    forkJoin(calls).subscribe((names) => {
      const ids = Array.from(subjectIds);
      const updates = {};
      ids.forEach((sid, i) => {
        updates[sid] = names[i];
      });
      this.subjectNameCache.update((prev) => __spreadValues(__spreadValues({}, prev), updates));
    });
  }
  lessonLabel(lessonId) {
    const lesson = this.lessonCache()[lessonId];
    if (!lesson)
      return `\u041F\u0430\u0440\u0430 \u2116${lessonId}`;
    const subject = this.subjectNameCache()[lesson.subjectId] ?? "\u041F\u0440\u0435\u0434\u043C\u0435\u0442";
    const date = lesson.date ?? "";
    const num = lesson.lessonNumber ? `\u2116${lesson.lessonNumber}` : "";
    return `${date} ${num} ${subject}`.trim();
  }
  loadTickets() {
    this.loading.set(true);
    this.error.set(null);
    this.apiService.getExcuseTickets().subscribe({
      next: (tickets) => {
        this.tickets.set(tickets);
        this.loading.set(false);
      },
      error: () => {
        this.tickets.set([]);
        this.loading.set(false);
      }
    });
  }
  openExcuseForm() {
    this.apiService.getStudentRecords().subscribe({
      next: (records) => {
        const ref = this.dialog.open(ExcuseFormDialogComponent, {
          width: "560px",
          maxWidth: "100vw",
          maxHeight: "80vh",
          ariaLabel: "\u041D\u043E\u0432\u044B\u0439 \u0442\u0438\u043A\u0435\u0442 \u043E \u043F\u0440\u043E\u043F\u0443\u0441\u043A\u0435",
          data: { lessons: records }
        });
        ref.afterClosed().subscribe((submitted) => {
          if (submitted)
            this.loadTickets();
        });
      },
      error: () => {
        const ref = this.dialog.open(ExcuseFormDialogComponent, {
          width: "560px",
          maxWidth: "100vw",
          maxHeight: "80vh",
          ariaLabel: "\u041D\u043E\u0432\u044B\u0439 \u0442\u0438\u043A\u0435\u0442 \u043E \u043F\u0440\u043E\u043F\u0443\u0441\u043A\u0435",
          data: { lessons: [] }
        });
        ref.afterClosed().subscribe((submitted) => {
          if (submitted)
            this.loadTickets();
        });
      }
    });
  }
  static \u0275fac = function StudentExcusesComponent_Factory(__ngFactoryType__) {
    return new (__ngFactoryType__ || _StudentExcusesComponent)();
  };
  static \u0275cmp = /* @__PURE__ */ \u0275\u0275defineComponent({ type: _StudentExcusesComponent, selectors: [["app-student-excuses"]], decls: 23, vars: 10, consts: [[1, "page-stack"], [1, "page-header"], [1, "page-header__eyebrow"], [1, "page-header__title"], [1, "page-header__actions"], [1, "btn-brand", 3, "click"], [1, "ph", "ph-plus"], ["role", "tablist", "aria-label", "\u0424\u0438\u043B\u044C\u0442\u0440 \u0442\u0438\u043A\u0435\u0442\u043E\u0432", 1, "excuse-tabs"], ["type", "button", "role", "tab", 1, "excuse-tabs__btn", 3, "click"], [1, "excuse-tabs__count"], [1, "page-card"], [1, "page-empty"], [1, "page-card", "page-card--flush"], ["class", "skeleton-row", 4, "ngFor", "ngForOf"], [1, "skeleton-row"], [1, "ph-duotone", "ph-file-text", "page-empty__icon"], [1, "page-empty__heading"], [1, "page-empty__body"], ["role", "table", "aria-label", "\u0422\u0438\u043A\u0435\u0442\u044B \u043E \u043F\u0440\u043E\u043F\u0443\u0441\u043A\u0430\u0445", 1, "excuse-table"], ["role", "row", 1, "excuse-table__header"], ["role", "columnheader"], ["type", "button", 1, "excuse-table__row", "excuse-table__row--clickable", 3, "click"], [1, "excuse-date"], [1, "excuse-subjects"], [1, "excuse-count"], [1, "status-chip"], ["role", "region", 1, "excuse-detail"], [1, "excuse-detail__facts"], [1, "excuse-detail__section"], [1, "excuse-detail__heading"], [1, "excuse-detail__lessons"], [1, "excuse-detail__text"]], template: function StudentExcusesComponent_Template(rf, ctx) {
    if (rf & 1) {
      \u0275\u0275elementStart(0, "div", 0)(1, "div", 1)(2, "div")(3, "p", 2);
      \u0275\u0275text(4, "\u0421\u0442\u0443\u0434\u0435\u043D\u0442");
      \u0275\u0275elementEnd();
      \u0275\u0275elementStart(5, "h1", 3);
      \u0275\u0275text(6, "\u041F\u0440\u043E\u043F\u0443\u0441\u043A\u0438");
      \u0275\u0275elementEnd()();
      \u0275\u0275elementStart(7, "div", 4)(8, "button", 5);
      \u0275\u0275listener("click", function StudentExcusesComponent_Template_button_click_8_listener() {
        return ctx.openExcuseForm();
      });
      \u0275\u0275element(9, "i", 6);
      \u0275\u0275text(10, " \u041F\u043E\u0434\u0430\u0442\u044C \u0442\u0438\u043A\u0435\u0442 ");
      \u0275\u0275elementEnd()()();
      \u0275\u0275elementStart(11, "div", 7)(12, "button", 8);
      \u0275\u0275listener("click", function StudentExcusesComponent_Template_button_click_12_listener() {
        return ctx.switchTab("active");
      });
      \u0275\u0275text(13, " \u0410\u043A\u0442\u0438\u0432\u043D\u044B\u0435 ");
      \u0275\u0275elementStart(14, "span", 9);
      \u0275\u0275text(15);
      \u0275\u0275elementEnd()();
      \u0275\u0275elementStart(16, "button", 8);
      \u0275\u0275listener("click", function StudentExcusesComponent_Template_button_click_16_listener() {
        return ctx.switchTab("archive");
      });
      \u0275\u0275text(17, " \u0410\u0440\u0445\u0438\u0432 ");
      \u0275\u0275elementStart(18, "span", 9);
      \u0275\u0275text(19);
      \u0275\u0275elementEnd()()();
      \u0275\u0275template(20, StudentExcusesComponent_Conditional_20_Template, 2, 2, "div", 10)(21, StudentExcusesComponent_Conditional_21_Template, 6, 2, "div", 11)(22, StudentExcusesComponent_Conditional_22_Template, 13, 0, "div", 12);
      \u0275\u0275elementEnd();
    }
    if (rf & 2) {
      \u0275\u0275property("@routeFade", void 0);
      \u0275\u0275advance(12);
      \u0275\u0275classProp("excuse-tabs__btn--active", ctx.currentTab() === "active");
      \u0275\u0275attribute("aria-selected", ctx.currentTab() === "active");
      \u0275\u0275advance(3);
      \u0275\u0275textInterpolate(ctx.activeTickets().length);
      \u0275\u0275advance();
      \u0275\u0275classProp("excuse-tabs__btn--active", ctx.currentTab() === "archive");
      \u0275\u0275attribute("aria-selected", ctx.currentTab() === "archive");
      \u0275\u0275advance(3);
      \u0275\u0275textInterpolate(ctx.archiveTickets().length);
      \u0275\u0275advance();
      \u0275\u0275conditional(ctx.loading() ? 20 : ctx.visibleTickets().length === 0 ? 21 : 22);
    }
  }, dependencies: [CommonModule, NgForOf, DatePipe], styles: ["\n\n.excuse-tabs[_ngcontent-%COMP%] {\n  display: inline-flex;\n  gap: var(--space-2);\n  padding: 4px;\n  background: var(--bg-surface);\n  border-radius: var(--radius-pill, 9999px);\n  border: 1px solid var(--border-subtle);\n  margin-bottom: var(--space-4);\n}\n.excuse-tabs__btn[_ngcontent-%COMP%] {\n  display: inline-flex;\n  align-items: center;\n  gap: var(--space-1);\n  padding: var(--space-2) var(--space-4);\n  border: none;\n  background: transparent;\n  color: var(--text-secondary);\n  font-size: var(--text-sm);\n  font-weight: 600;\n  border-radius: var(--radius-pill, 9999px);\n  cursor: pointer;\n  transition: background 150ms ease, color 150ms ease;\n}\n.excuse-tabs__btn[_ngcontent-%COMP%]:hover:not(.excuse-tabs__btn--active) {\n  color: var(--text-primary);\n}\n.excuse-tabs__btn--active[_ngcontent-%COMP%] {\n  background: var(--bg-elevated);\n  color: var(--text-primary);\n  box-shadow: var(--shadow-sm);\n}\n.excuse-tabs__count[_ngcontent-%COMP%] {\n  display: inline-flex;\n  align-items: center;\n  justify-content: center;\n  min-width: 22px;\n  padding: 0 6px;\n  height: 18px;\n  border-radius: var(--radius-pill, 9999px);\n  background: var(--bg-primary);\n  color: var(--text-muted);\n  font-size: 11px;\n}\n.excuse-tabs__btn--active[_ngcontent-%COMP%]   .excuse-tabs__count[_ngcontent-%COMP%] {\n  background: var(--accent-primary);\n  color: var(--accent-primary-contrast);\n}\n.excuse-table[_ngcontent-%COMP%] {\n  width: 100%;\n}\n.excuse-table__row--clickable[_ngcontent-%COMP%] {\n  width: 100%;\n  border: none;\n  border-bottom: 1px solid var(--border-subtle);\n  background: transparent;\n  color: inherit;\n  cursor: pointer;\n  text-align: left;\n  font: inherit;\n}\n.excuse-table__row--clickable[_ngcontent-%COMP%]:hover {\n  background: color-mix(in srgb, var(--accent-primary) 6%, transparent);\n}\n.excuse-table__row--expanded[_ngcontent-%COMP%] {\n  background: var(--bg-elevated);\n}\n.excuse-detail[_ngcontent-%COMP%] {\n  padding: var(--space-5) var(--space-5) var(--space-6);\n  border-bottom: 1px solid var(--border-subtle);\n  background: var(--bg-surface);\n}\n.excuse-detail__facts[_ngcontent-%COMP%] {\n  display: grid;\n  grid-template-columns: repeat(auto-fit, minmax(180px, 1fr));\n  gap: var(--space-3) var(--space-5);\n  margin: 0 0 var(--space-4);\n}\n.excuse-detail__facts[_ngcontent-%COMP%]   div[_ngcontent-%COMP%] {\n  display: flex;\n  flex-direction: column;\n  gap: 2px;\n}\n.excuse-detail__facts[_ngcontent-%COMP%]   dt[_ngcontent-%COMP%] {\n  font-size: var(--text-xs);\n  color: var(--text-muted);\n  text-transform: uppercase;\n  letter-spacing: 0.04em;\n}\n.excuse-detail__facts[_ngcontent-%COMP%]   dd[_ngcontent-%COMP%] {\n  margin: 0;\n  font-size: var(--text-sm);\n  color: var(--text-primary);\n}\n.excuse-detail__section[_ngcontent-%COMP%] {\n  margin-top: var(--space-4);\n}\n.excuse-detail__heading[_ngcontent-%COMP%] {\n  margin: 0 0 var(--space-2);\n  font-size: var(--text-sm);\n  font-weight: 600;\n  color: var(--text-primary);\n}\n.excuse-detail__text[_ngcontent-%COMP%] {\n  margin: 0;\n  font-size: var(--text-sm);\n  line-height: var(--leading-body);\n  color: var(--text-secondary);\n  white-space: pre-wrap;\n}\n.excuse-detail__lessons[_ngcontent-%COMP%] {\n  list-style: disc inside;\n  padding: 0;\n  margin: 0;\n  display: flex;\n  flex-direction: column;\n  gap: 4px;\n  font-size: var(--text-sm);\n  color: var(--text-secondary);\n}\n.excuse-table__header[_ngcontent-%COMP%] {\n  display: grid;\n  grid-template-columns: 120px 1fr 80px 140px;\n  padding: var(--space-2) var(--space-4);\n  font-size: var(--text-xs);\n  font-weight: 600;\n  color: var(--text-muted);\n  border-bottom: 1px solid var(--border-subtle);\n  text-transform: uppercase;\n  letter-spacing: 0.05em;\n}\n.excuse-table__row[_ngcontent-%COMP%] {\n  display: grid;\n  grid-template-columns: 120px 1fr 80px 140px;\n  padding: var(--space-4);\n  min-height: 52px;\n  align-items: center;\n  border-bottom: 1px solid var(--border-subtle);\n  gap: var(--space-4);\n}\n.excuse-table__row[_ngcontent-%COMP%]:last-child {\n  border-bottom: none;\n}\n.excuse-date[_ngcontent-%COMP%] {\n  font-size: var(--text-xs);\n  font-family: var(--font-mono);\n  color: var(--text-muted);\n}\n.excuse-subjects[_ngcontent-%COMP%] {\n  font-size: var(--text-sm);\n  color: var(--text-primary);\n}\n.status-chip--submitted[_ngcontent-%COMP%], \n.status-chip--pending[_ngcontent-%COMP%] {\n  background-color: color-mix(in srgb, var(--accent-warning) 15%, transparent);\n  color: var(--accent-warning);\n  border: 1px solid var(--accent-warning);\n}\n.status-chip--draft[_ngcontent-%COMP%] {\n  background-color: color-mix(in srgb, var(--text-muted) 15%, transparent);\n  color: var(--text-muted);\n  border: 1px solid var(--border-default);\n}\n.excuse-count[_ngcontent-%COMP%] {\n  font-size: var(--text-sm);\n  font-family: var(--font-mono);\n  color: var(--text-secondary);\n  text-align: center;\n}\n.status-chip--approved[_ngcontent-%COMP%] {\n  background-color: color-mix(in srgb, var(--accent-info) 15%, transparent);\n  color: var(--accent-info);\n  border: 1px solid var(--accent-info);\n}\n.status-chip--rejected[_ngcontent-%COMP%] {\n  background-color: color-mix(in srgb, var(--accent-danger) 15%, transparent);\n  color: var(--accent-danger);\n  border: 1px solid var(--accent-danger);\n}\n.status-chip--cancelled[_ngcontent-%COMP%] {\n  background-color: color-mix(in srgb, var(--text-muted) 15%, transparent);\n  color: var(--text-muted);\n  border: 1px solid var(--border-default);\n}\n.skeleton-row[_ngcontent-%COMP%] {\n  height: 52px;\n  border-radius: var(--radius-md);\n  background: var(--bg-elevated);\n  margin-bottom: var(--space-2);\n  animation: dashboard-shimmer 1.5s infinite;\n}\n@media (max-width: 600px) {\n  .excuse-table__header[_ngcontent-%COMP%], \n   .excuse-table__row[_ngcontent-%COMP%] {\n    grid-template-columns: 1fr 60px 140px;\n  }\n  .excuse-date[_ngcontent-%COMP%] {\n    display: none;\n  }\n}\n/*# sourceMappingURL=student-excuses.component.css.map */"], data: { animation: [
    trigger("routeFade", [
      transition(":enter", [
        style({ opacity: 0, transform: "translateY(8px)" }),
        animate("200ms cubic-bezier(0.16, 1, 0.3, 1)", style({ opacity: 1, transform: "translateY(0)" }))
      ])
    ])
  ] }, changeDetection: 0 });
};
(() => {
  (typeof ngDevMode === "undefined" || ngDevMode) && setClassMetadata(StudentExcusesComponent, [{
    type: Component,
    args: [{ selector: "app-student-excuses", standalone: true, changeDetection: ChangeDetectionStrategy.OnPush, imports: [CommonModule], animations: [
      trigger("routeFade", [
        transition(":enter", [
          style({ opacity: 0, transform: "translateY(8px)" }),
          animate("200ms cubic-bezier(0.16, 1, 0.3, 1)", style({ opacity: 1, transform: "translateY(0)" }))
        ])
      ])
    ], template: `<div class="page-stack" @routeFade>
  <div class="page-header">
    <div>
      <p class="page-header__eyebrow">\u0421\u0442\u0443\u0434\u0435\u043D\u0442</p>
      <h1 class="page-header__title">\u041F\u0440\u043E\u043F\u0443\u0441\u043A\u0438</h1>
    </div>
    <div class="page-header__actions">
      <button class="btn-brand" (click)="openExcuseForm()">
        <i class="ph ph-plus"></i>
        \u041F\u043E\u0434\u0430\u0442\u044C \u0442\u0438\u043A\u0435\u0442
      </button>
    </div>
  </div>

  <div class="excuse-tabs" role="tablist" aria-label="\u0424\u0438\u043B\u044C\u0442\u0440 \u0442\u0438\u043A\u0435\u0442\u043E\u0432">
    <button
      type="button"
      role="tab"
      class="excuse-tabs__btn"
      [class.excuse-tabs__btn--active]="currentTab() === 'active'"
      [attr.aria-selected]="currentTab() === 'active'"
      (click)="switchTab('active')"
    >
      \u0410\u043A\u0442\u0438\u0432\u043D\u044B\u0435
      <span class="excuse-tabs__count">{{ activeTickets().length }}</span>
    </button>
    <button
      type="button"
      role="tab"
      class="excuse-tabs__btn"
      [class.excuse-tabs__btn--active]="currentTab() === 'archive'"
      [attr.aria-selected]="currentTab() === 'archive'"
      (click)="switchTab('archive')"
    >
      \u0410\u0440\u0445\u0438\u0432
      <span class="excuse-tabs__count">{{ archiveTickets().length }}</span>
    </button>
  </div>

  @if (loading()) {
    <div class="page-card">
      <div class="skeleton-row" *ngFor="let i of [1,2,3]"></div>
    </div>
  } @else if (visibleTickets().length === 0) {
    <div class="page-empty">
      <i class="ph-duotone ph-file-text page-empty__icon"></i>
      <h2 class="page-empty__heading">
        {{ currentTab() === 'active' ? '\u041D\u0435\u0442 \u0430\u043A\u0442\u0438\u0432\u043D\u044B\u0445 \u0442\u0438\u043A\u0435\u0442\u043E\u0432' : '\u0410\u0440\u0445\u0438\u0432 \u043F\u0443\u0441\u0442' }}
      </h2>
      <p class="page-empty__body">
        {{ currentTab() === 'active'
          ? '\u041F\u043E\u0434\u0430\u0439\u0442\u0435 \u0442\u0438\u043A\u0435\u0442, \u0435\u0441\u043B\u0438 \u043F\u0440\u043E\u043F\u0443\u0441\u0442\u0438\u043B\u0438 \u0437\u0430\u043D\u044F\u0442\u0438\u0435 \u043F\u043E \u0443\u0432\u0430\u0436\u0438\u0442\u0435\u043B\u044C\u043D\u043E\u0439 \u043F\u0440\u0438\u0447\u0438\u043D\u0435.'
          : '\u0417\u0434\u0435\u0441\u044C \u043F\u043E\u044F\u0432\u043B\u044F\u044E\u0442\u0441\u044F \u043E\u0434\u043E\u0431\u0440\u0435\u043D\u043D\u044B\u0435 \u0438 \u043E\u0442\u043A\u043B\u043E\u043D\u0451\u043D\u043D\u044B\u0435 \u0442\u0438\u043A\u0435\u0442\u044B.' }}
      </p>
    </div>
  } @else {
    <div class="page-card page-card--flush">
      <div class="excuse-table" role="table" aria-label="\u0422\u0438\u043A\u0435\u0442\u044B \u043E \u043F\u0440\u043E\u043F\u0443\u0441\u043A\u0430\u0445">
        <div class="excuse-table__header" role="row">
          <span role="columnheader">\u0414\u0430\u0442\u0430</span>
          <span role="columnheader">\u041F\u0440\u0438\u0447\u0438\u043D\u0430</span>
          <span role="columnheader">\u0417\u0430\u043D\u044F\u0442\u0438\u0439</span>
          <span role="columnheader">\u0421\u0442\u0430\u0442\u0443\u0441</span>
        </div>
        @for (ticket of visibleTickets(); track ticket.id) {
          <button
            type="button"
            class="excuse-table__row excuse-table__row--clickable"
            [class.excuse-table__row--expanded]="isExpanded(ticket.id)"
            (click)="toggleTicket(ticket)"
            [attr.aria-expanded]="isExpanded(ticket.id)"
          >
            <span class="excuse-date">{{ ticket.createdAt | date:'dd.MM.yyyy' }}</span>
            <span class="excuse-subjects">{{ labelForExcuseType(ticket.excuseType) }}</span>
            <span class="excuse-count">{{ ticket.lessonIds.length }}</span>
            <span
              class="status-chip"
              [class]="'status-chip--' + ticket.status"
              [attr.aria-label]="statusLabels[ticket.status]"
            >{{ statusLabels[ticket.status] }}</span>
          </button>
          @if (isExpanded(ticket.id)) {
            <div class="excuse-detail" role="region" [attr.aria-label]="'\u0414\u0435\u0442\u0430\u043B\u0438 \u0442\u0438\u043A\u0435\u0442\u0430 ' + ticket.id">
              <dl class="excuse-detail__facts">
                <div>
                  <dt>\u041F\u0440\u0438\u0447\u0438\u043D\u0430</dt>
                  <dd>{{ labelForExcuseType(ticket.excuseType) }}</dd>
                </div>
                <div>
                  <dt>\u0421\u0442\u0430\u0442\u0443\u0441</dt>
                  <dd>
                    <span class="status-chip" [class]="'status-chip--' + ticket.status">
                      {{ statusLabels[ticket.status] }}
                    </span>
                  </dd>
                </div>
                <div>
                  <dt>\u041F\u043E\u0434\u0430\u043D</dt>
                  <dd>{{ ticket.createdAt | date:'dd.MM.yyyy HH:mm' }}</dd>
                </div>
                @if (ticket.decisionAt) {
                  <div>
                    <dt>\u0420\u0435\u0448\u0435\u043D\u0438\u0435</dt>
                    <dd>{{ ticket.decisionAt | date:'dd.MM.yyyy HH:mm' }}</dd>
                  </div>
                }
              </dl>

              @if (ticket.comment) {
                <section class="excuse-detail__section">
                  <h4 class="excuse-detail__heading">\u041A\u043E\u043C\u043C\u0435\u043D\u0442\u0430\u0440\u0438\u0439 \u0441\u0442\u0443\u0434\u0435\u043D\u0442\u0430</h4>
                  <p class="excuse-detail__text">{{ ticket.comment }}</p>
                </section>
              }

              @if (ticket.decisionComment) {
                <section class="excuse-detail__section">
                  <h4 class="excuse-detail__heading">\u041A\u043E\u043C\u043C\u0435\u043D\u0442\u0430\u0440\u0438\u0439 \u0441\u0442\u0430\u0440\u043E\u0441\u0442\u044B</h4>
                  <p class="excuse-detail__text">{{ ticket.decisionComment }}</p>
                </section>
              }

              <section class="excuse-detail__section">
                <h4 class="excuse-detail__heading">\u041F\u0430\u0440\u044B ({{ ticket.lessonIds.length }})</h4>
                <ul class="excuse-detail__lessons">
                  @for (lid of ticket.lessonIds; track lid) {
                    <li>{{ lessonLabel(lid) }}</li>
                  }
                </ul>
              </section>
            </div>
          }
        }
      </div>
    </div>
  }
</div>
`, styles: ["/* src/app/features/student/excuses/student-excuses.component.css */\n.excuse-tabs {\n  display: inline-flex;\n  gap: var(--space-2);\n  padding: 4px;\n  background: var(--bg-surface);\n  border-radius: var(--radius-pill, 9999px);\n  border: 1px solid var(--border-subtle);\n  margin-bottom: var(--space-4);\n}\n.excuse-tabs__btn {\n  display: inline-flex;\n  align-items: center;\n  gap: var(--space-1);\n  padding: var(--space-2) var(--space-4);\n  border: none;\n  background: transparent;\n  color: var(--text-secondary);\n  font-size: var(--text-sm);\n  font-weight: 600;\n  border-radius: var(--radius-pill, 9999px);\n  cursor: pointer;\n  transition: background 150ms ease, color 150ms ease;\n}\n.excuse-tabs__btn:hover:not(.excuse-tabs__btn--active) {\n  color: var(--text-primary);\n}\n.excuse-tabs__btn--active {\n  background: var(--bg-elevated);\n  color: var(--text-primary);\n  box-shadow: var(--shadow-sm);\n}\n.excuse-tabs__count {\n  display: inline-flex;\n  align-items: center;\n  justify-content: center;\n  min-width: 22px;\n  padding: 0 6px;\n  height: 18px;\n  border-radius: var(--radius-pill, 9999px);\n  background: var(--bg-primary);\n  color: var(--text-muted);\n  font-size: 11px;\n}\n.excuse-tabs__btn--active .excuse-tabs__count {\n  background: var(--accent-primary);\n  color: var(--accent-primary-contrast);\n}\n.excuse-table {\n  width: 100%;\n}\n.excuse-table__row--clickable {\n  width: 100%;\n  border: none;\n  border-bottom: 1px solid var(--border-subtle);\n  background: transparent;\n  color: inherit;\n  cursor: pointer;\n  text-align: left;\n  font: inherit;\n}\n.excuse-table__row--clickable:hover {\n  background: color-mix(in srgb, var(--accent-primary) 6%, transparent);\n}\n.excuse-table__row--expanded {\n  background: var(--bg-elevated);\n}\n.excuse-detail {\n  padding: var(--space-5) var(--space-5) var(--space-6);\n  border-bottom: 1px solid var(--border-subtle);\n  background: var(--bg-surface);\n}\n.excuse-detail__facts {\n  display: grid;\n  grid-template-columns: repeat(auto-fit, minmax(180px, 1fr));\n  gap: var(--space-3) var(--space-5);\n  margin: 0 0 var(--space-4);\n}\n.excuse-detail__facts div {\n  display: flex;\n  flex-direction: column;\n  gap: 2px;\n}\n.excuse-detail__facts dt {\n  font-size: var(--text-xs);\n  color: var(--text-muted);\n  text-transform: uppercase;\n  letter-spacing: 0.04em;\n}\n.excuse-detail__facts dd {\n  margin: 0;\n  font-size: var(--text-sm);\n  color: var(--text-primary);\n}\n.excuse-detail__section {\n  margin-top: var(--space-4);\n}\n.excuse-detail__heading {\n  margin: 0 0 var(--space-2);\n  font-size: var(--text-sm);\n  font-weight: 600;\n  color: var(--text-primary);\n}\n.excuse-detail__text {\n  margin: 0;\n  font-size: var(--text-sm);\n  line-height: var(--leading-body);\n  color: var(--text-secondary);\n  white-space: pre-wrap;\n}\n.excuse-detail__lessons {\n  list-style: disc inside;\n  padding: 0;\n  margin: 0;\n  display: flex;\n  flex-direction: column;\n  gap: 4px;\n  font-size: var(--text-sm);\n  color: var(--text-secondary);\n}\n.excuse-table__header {\n  display: grid;\n  grid-template-columns: 120px 1fr 80px 140px;\n  padding: var(--space-2) var(--space-4);\n  font-size: var(--text-xs);\n  font-weight: 600;\n  color: var(--text-muted);\n  border-bottom: 1px solid var(--border-subtle);\n  text-transform: uppercase;\n  letter-spacing: 0.05em;\n}\n.excuse-table__row {\n  display: grid;\n  grid-template-columns: 120px 1fr 80px 140px;\n  padding: var(--space-4);\n  min-height: 52px;\n  align-items: center;\n  border-bottom: 1px solid var(--border-subtle);\n  gap: var(--space-4);\n}\n.excuse-table__row:last-child {\n  border-bottom: none;\n}\n.excuse-date {\n  font-size: var(--text-xs);\n  font-family: var(--font-mono);\n  color: var(--text-muted);\n}\n.excuse-subjects {\n  font-size: var(--text-sm);\n  color: var(--text-primary);\n}\n.status-chip--submitted,\n.status-chip--pending {\n  background-color: color-mix(in srgb, var(--accent-warning) 15%, transparent);\n  color: var(--accent-warning);\n  border: 1px solid var(--accent-warning);\n}\n.status-chip--draft {\n  background-color: color-mix(in srgb, var(--text-muted) 15%, transparent);\n  color: var(--text-muted);\n  border: 1px solid var(--border-default);\n}\n.excuse-count {\n  font-size: var(--text-sm);\n  font-family: var(--font-mono);\n  color: var(--text-secondary);\n  text-align: center;\n}\n.status-chip--approved {\n  background-color: color-mix(in srgb, var(--accent-info) 15%, transparent);\n  color: var(--accent-info);\n  border: 1px solid var(--accent-info);\n}\n.status-chip--rejected {\n  background-color: color-mix(in srgb, var(--accent-danger) 15%, transparent);\n  color: var(--accent-danger);\n  border: 1px solid var(--accent-danger);\n}\n.status-chip--cancelled {\n  background-color: color-mix(in srgb, var(--text-muted) 15%, transparent);\n  color: var(--text-muted);\n  border: 1px solid var(--border-default);\n}\n.skeleton-row {\n  height: 52px;\n  border-radius: var(--radius-md);\n  background: var(--bg-elevated);\n  margin-bottom: var(--space-2);\n  animation: dashboard-shimmer 1.5s infinite;\n}\n@media (max-width: 600px) {\n  .excuse-table__header,\n  .excuse-table__row {\n    grid-template-columns: 1fr 60px 140px;\n  }\n  .excuse-date {\n    display: none;\n  }\n}\n/*# sourceMappingURL=student-excuses.component.css.map */\n"] }]
  }], null, null);
})();
(() => {
  (typeof ngDevMode === "undefined" || ngDevMode) && \u0275setClassDebugInfo(StudentExcusesComponent, { className: "StudentExcusesComponent", filePath: "src/app/features/student/excuses/student-excuses.component.ts", lineNumber: 41 });
})();
export {
  StudentExcusesComponent
};
//# sourceMappingURL=chunk-4AYJKLCE.js.map
