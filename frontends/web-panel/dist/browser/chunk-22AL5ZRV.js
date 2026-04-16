import {
  formatLoadError
} from "./chunk-OWQVFP43.js";
import {
  SubjectCacheService
} from "./chunk-QY7VTECN.js";
import {
  StudentApiService
} from "./chunk-ESVH3VU5.js";
import {
  StudentNotificationBadgeService
} from "./chunk-TWJXGMYI.js";
import {
  require_entry,
  require_stomp_umd
} from "./chunk-VAU65FPZ.js";
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
import {
  AsyncPipe
} from "./chunk-M5DKW26A.js";
import {
  ChangeDetectionStrategy,
  Component,
  DestroyRef,
  Injectable,
  Subject,
  __toESM,
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
  ɵɵconditional,
  ɵɵdeclareLet,
  ɵɵdefineComponent,
  ɵɵdefineInjectable,
  ɵɵelement,
  ɵɵelementEnd,
  ɵɵelementStart,
  ɵɵgetCurrentView,
  ɵɵlistener,
  ɵɵnextContext,
  ɵɵpipe,
  ɵɵpipeBind1,
  ɵɵproperty,
  ɵɵreadContextLet,
  ɵɵresetView,
  ɵɵrestoreView,
  ɵɵstoreLet,
  ɵɵtemplate,
  ɵɵtext,
  ɵɵtextInterpolate,
  ɵɵtextInterpolate1,
  ɵɵtextInterpolate2
} from "./chunk-MFRIGFR2.js";

// src/app/features/student/shared/student-stomp.service.ts
var import_stompjs = __toESM(require_stomp_umd());
var import_sockjs_client = __toESM(require_entry());
var STORED_TYPES = [
  "lesson.started",
  "lesson.cancelled",
  "homework.published",
  "homework.updated",
  "attendance.marked",
  "late_checkin.decided",
  "excuse.decided"
];
var StudentStompService = class _StudentStompService {
  client = null;
  currentGroupId = null;
  markedSubject = new Subject();
  onAnySubject = new Subject();
  badgeService = inject(StudentNotificationBadgeService);
  /** Reactive stream of attendance.marked payloads. */
  marked$ = this.markedSubject.asObservable();
  /** Reactive stream of ALL STOMP envelopes from the group topic. */
  onAnyEvent$ = this.onAnySubject.asObservable();
  /**
   * Connect to /api/ws for a given group. Idempotent per (groupId).
   * Switching groups tears down the prior client.
   */
  connect(groupId, getAccessToken) {
    if (this.currentGroupId === groupId && this.client !== null) {
      return;
    }
    if (this.client !== null) {
      this.disconnect();
    }
    this.currentGroupId = groupId;
    this.client = new import_stompjs.Client({
      webSocketFactory: () => new import_sockjs_client.default(`/api/ws?token=${getAccessToken() ?? ""}`),
      reconnectDelay: 1e3,
      onConnect: () => {
        this.client?.subscribe(`/topic/group/${groupId}`, (message) => {
          try {
            const envelope = JSON.parse(message.body);
            this.onAnySubject.next(envelope);
            if (STORED_TYPES.includes(envelope.type)) {
              this.badgeService.increment();
            }
            if (envelope.type === "attendance.marked") {
              this.markedSubject.next(envelope.payload);
            }
          } catch {
          }
        });
      },
      onStompError: (frame) => {
        console.error("STOMP error:", frame.headers["message"]);
      }
    });
    this.client.activate();
  }
  /** Disconnect and release the current client, if any. */
  disconnect() {
    if (this.client !== null) {
      this.client.deactivate();
      this.client = null;
      this.currentGroupId = null;
    }
  }
  static \u0275fac = function StudentStompService_Factory(__ngFactoryType__) {
    return new (__ngFactoryType__ || _StudentStompService)();
  };
  static \u0275prov = /* @__PURE__ */ \u0275\u0275defineInjectable({ token: _StudentStompService, factory: _StudentStompService.\u0275fac, providedIn: "root" });
};
(() => {
  (typeof ngDevMode === "undefined" || ngDevMode) && setClassMetadata(StudentStompService, [{
    type: Injectable,
    args: [{ providedIn: "root" }]
  }], null, null);
})();

// src/app/features/student/checkin/checkin-error-mapper.ts
function mapCheckinError(status) {
  switch (status) {
    case 403:
      return "\u0413\u0435\u043E\u043E\u0442\u043C\u0435\u0442\u043A\u0430 \u0437\u0430\u0431\u043B\u043E\u043A\u0438\u0440\u043E\u0432\u0430\u043D\u0430 \u043F\u0440\u0435\u043F\u043E\u0434\u0430\u0432\u0430\u0442\u0435\u043B\u0435\u043C.";
    case 404:
      return "\u0410\u043A\u0442\u0438\u0432\u043D\u043E\u0435 \u0437\u0430\u043D\u044F\u0442\u0438\u0435 \u043D\u0435 \u043D\u0430\u0439\u0434\u0435\u043D\u043E.";
    case 409:
      return "\u0412\u044B \u0443\u0436\u0435 \u043E\u0442\u043C\u0435\u0447\u0435\u043D\u044B \u043D\u0430 \u044D\u0442\u043E\u043C \u0437\u0430\u043D\u044F\u0442\u0438\u0438.";
    case 422:
      return "\u0412\u044B \u043D\u0430\u0445\u043E\u0434\u0438\u0442\u0435\u0441\u044C \u0441\u043B\u0438\u0448\u043A\u043E\u043C \u0434\u0430\u043B\u0435\u043A\u043E \u043E\u0442 \u043A\u0430\u043C\u043F\u0443\u0441\u0430. \u0413\u0435\u043E\u043E\u0442\u043C\u0435\u0442\u043A\u0430 \u043D\u0435\u0434\u043E\u0441\u0442\u0443\u043F\u043D\u0430.";
    case 429:
      return "\u0421\u043B\u0438\u0448\u043A\u043E\u043C \u043C\u043D\u043E\u0433\u043E \u043F\u043E\u043F\u044B\u0442\u043E\u043A. \u041F\u043E\u0434\u043E\u0436\u0434\u0438\u0442\u0435 \u043C\u0438\u043D\u0443\u0442\u0443 \u0438 \u043F\u043E\u043F\u0440\u043E\u0431\u0443\u0439\u0442\u0435 \u0441\u043D\u043E\u0432\u0430.";
    default:
      return "\u041D\u0435 \u0443\u0434\u0430\u043B\u043E\u0441\u044C \u043E\u0442\u043F\u0440\u0430\u0432\u0438\u0442\u044C \u043E\u0442\u043C\u0435\u0442\u043A\u0443. \u041F\u043E\u043F\u0440\u043E\u0431\u0443\u0439\u0442\u0435 \u0435\u0449\u0451 \u0440\u0430\u0437.";
  }
}
var GPS_DENIED_MESSAGE = "\u041D\u0435\u0442 \u0434\u043E\u0441\u0442\u0443\u043F\u0430 \u043A \u0433\u0435\u043E\u043B\u043E\u043A\u0430\u0446\u0438\u0438. \u0420\u0430\u0437\u0440\u0435\u0448\u0438\u0442\u0435 \u0434\u043E\u0441\u0442\u0443\u043F \u0432 \u043D\u0430\u0441\u0442\u0440\u043E\u0439\u043A\u0430\u0445 \u0431\u0440\u0430\u0443\u0437\u0435\u0440\u0430 \u0438 \u043F\u043E\u043F\u0440\u043E\u0431\u0443\u0439\u0442\u0435 \u0441\u043D\u043E\u0432\u0430.";
function mapRequestHeadmanError(status) {
  switch (status) {
    case 403:
      return "\u0421\u0442\u0430\u0440\u043E\u0441\u0442\u044B \u043E\u0442\u043C\u0435\u0447\u0430\u044E\u0442 \u0441\u0435\u0431\u044F \u0447\u0435\u0440\u0435\u0437 \u0436\u0443\u0440\u043D\u0430\u043B, \u0430 \u043D\u0435 \u0447\u0435\u0440\u0435\u0437 \u0437\u0430\u043F\u0440\u043E\u0441.";
    case 404:
      return "\u0417\u0430\u043D\u044F\u0442\u0438\u0435 \u043D\u0435 \u043D\u0430\u0439\u0434\u0435\u043D\u043E.";
    case 409:
      return "\u0417\u0430\u043F\u0440\u043E\u0441 \u043D\u0430 \u044D\u0442\u0443 \u043F\u0430\u0440\u0443 \u0443\u0436\u0435 \u043E\u0442\u043F\u0440\u0430\u0432\u043B\u0435\u043D \u0438\u043B\u0438 \u0432\u044B \u0443\u0436\u0435 \u043E\u0442\u043C\u0435\u0447\u0435\u043D\u044B.";
    case 400:
      return "\u0417\u0430\u043F\u0440\u043E\u0441 \u0434\u043E\u0441\u0442\u0443\u043F\u0435\u043D \u0442\u043E\u043B\u044C\u043A\u043E \u0432\u043E \u0432\u0440\u0435\u043C\u044F \u0430\u043A\u0442\u0438\u0432\u043D\u043E\u0439 \u043F\u0430\u0440\u044B.";
    default:
      return "\u041D\u0435 \u0443\u0434\u0430\u043B\u043E\u0441\u044C \u043E\u0442\u043F\u0440\u0430\u0432\u0438\u0442\u044C \u0437\u0430\u043F\u0440\u043E\u0441 \u0441\u0442\u0430\u0440\u043E\u0441\u0442\u0435. \u041F\u043E\u043F\u0440\u043E\u0431\u0443\u0439\u0442\u0435 \u0435\u0449\u0451 \u0440\u0430\u0437.";
  }
}

// src/app/features/student/checkin/student-checkin.component.ts
function StudentCheckinComponent_Conditional_4_Template(rf, ctx) {
  if (rf & 1) {
    \u0275\u0275element(0, "div", 2);
  }
}
function StudentCheckinComponent_Conditional_5_Template(rf, ctx) {
  if (rf & 1) {
    \u0275\u0275elementStart(0, "p", 3);
    \u0275\u0275text(1);
    \u0275\u0275elementEnd();
  }
  if (rf & 2) {
    const ctx_r0 = \u0275\u0275nextContext();
    \u0275\u0275advance();
    \u0275\u0275textInterpolate(ctx_r0.fetchError());
  }
}
function StudentCheckinComponent_Conditional_6_Conditional_17_Template(rf, ctx) {
  if (rf & 1) {
    \u0275\u0275elementStart(0, "div", 16);
    \u0275\u0275element(1, "i", 21);
    \u0275\u0275text(2, " \u0412\u044B \u043E\u0442\u043C\u0435\u0442\u0438\u043B\u0438\u0441\u044C ");
    \u0275\u0275elementEnd();
  }
}
function StudentCheckinComponent_Conditional_6_Conditional_18_Template(rf, ctx) {
  if (rf & 1) {
    const _r2 = \u0275\u0275getCurrentView();
    \u0275\u0275elementStart(0, "button", 22);
    \u0275\u0275listener("click", function StudentCheckinComponent_Conditional_6_Conditional_18_Template_button_click_0_listener() {
      \u0275\u0275restoreView(_r2);
      const ctx_r0 = \u0275\u0275nextContext(2);
      return \u0275\u0275resetView(ctx_r0.onCheckinClick());
    });
    \u0275\u0275text(1);
    \u0275\u0275elementEnd();
  }
  if (rf & 2) {
    const ctx_r0 = \u0275\u0275nextContext(2);
    \u0275\u0275property("disabled", ctx_r0.buttonDisabled());
    \u0275\u0275attribute("aria-label", ctx_r0.buttonAriaLabel())("aria-busy", ctx_r0.isGpsPending() || ctx_r0.isSubmitting());
    \u0275\u0275advance();
    \u0275\u0275textInterpolate1(" ", ctx_r0.buttonLabel(), " ");
  }
}
function StudentCheckinComponent_Conditional_6_Conditional_22_Template(rf, ctx) {
  if (rf & 1) {
    const _r3 = \u0275\u0275getCurrentView();
    \u0275\u0275elementStart(0, "p", 23);
    \u0275\u0275text(1);
    \u0275\u0275elementEnd();
    \u0275\u0275elementStart(2, "button", 24);
    \u0275\u0275listener("click", function StudentCheckinComponent_Conditional_6_Conditional_22_Template_button_click_2_listener() {
      \u0275\u0275restoreView(_r3);
      const ctx_r0 = \u0275\u0275nextContext(2);
      return \u0275\u0275resetView(ctx_r0.onRequestHeadmanClick());
    });
    \u0275\u0275text(3);
    \u0275\u0275elementEnd();
  }
  if (rf & 2) {
    const ctx_r0 = \u0275\u0275nextContext(2);
    \u0275\u0275advance();
    \u0275\u0275textInterpolate(ctx_r0.errorMessage());
    \u0275\u0275advance();
    \u0275\u0275property("disabled", ctx_r0.requestButtonDisabled());
    \u0275\u0275advance();
    \u0275\u0275textInterpolate1(" ", ctx_r0.requestButtonLabel(), " ");
  }
}
function StudentCheckinComponent_Conditional_6_Conditional_23_Template(rf, ctx) {
  if (rf & 1) {
    \u0275\u0275elementStart(0, "p", 20);
    \u0275\u0275element(1, "i", 25);
    \u0275\u0275text(2, " \u0417\u0430\u043F\u0440\u043E\u0441 \u043E\u0442\u043F\u0440\u0430\u0432\u043B\u0435\u043D \u0441\u0442\u0430\u0440\u043E\u0441\u0442\u0435. \u041E\u0436\u0438\u0434\u0430\u0439\u0442\u0435 \u043E\u0442\u0432\u0435\u0442\u0430 \u0432 Telegram. ");
    \u0275\u0275elementEnd();
  }
}
function StudentCheckinComponent_Conditional_6_Template(rf, ctx) {
  if (rf & 1) {
    \u0275\u0275elementStart(0, "div", 4)(1, "div", 6);
    \u0275\u0275element(2, "span", 7);
    \u0275\u0275elementStart(3, "span", 8);
    \u0275\u0275text(4, "\u0418\u0434\u0451\u0442 \u0441\u0435\u0439\u0447\u0430\u0441");
    \u0275\u0275elementEnd()();
    \u0275\u0275elementStart(5, "h2", 9);
    \u0275\u0275text(6);
    \u0275\u0275pipe(7, "async");
    \u0275\u0275elementEnd();
    \u0275\u0275elementStart(8, "p", 10);
    \u0275\u0275element(9, "i", 11);
    \u0275\u0275elementStart(10, "span", 12);
    \u0275\u0275text(11);
    \u0275\u0275elementEnd();
    \u0275\u0275element(12, "span", 13)(13, "i", 14);
    \u0275\u0275elementStart(14, "span");
    \u0275\u0275text(15);
    \u0275\u0275elementEnd()();
    \u0275\u0275elementStart(16, "div", 15);
    \u0275\u0275template(17, StudentCheckinComponent_Conditional_6_Conditional_17_Template, 3, 0, "div", 16)(18, StudentCheckinComponent_Conditional_6_Conditional_18_Template, 2, 4, "button", 17);
    \u0275\u0275elementStart(19, "p", 18);
    \u0275\u0275element(20, "i", 19);
    \u0275\u0275text(21);
    \u0275\u0275elementEnd()();
    \u0275\u0275template(22, StudentCheckinComponent_Conditional_6_Conditional_22_Template, 4, 3)(23, StudentCheckinComponent_Conditional_6_Conditional_23_Template, 3, 0, "p", 20);
    \u0275\u0275elementEnd();
  }
  if (rf & 2) {
    let tmp_3_0;
    const ctx_r0 = \u0275\u0275nextContext();
    const active_r4 = \u0275\u0275readContextLet(2);
    \u0275\u0275advance(6);
    \u0275\u0275textInterpolate1(" ", (tmp_3_0 = \u0275\u0275pipeBind1(7, 8, ctx_r0.getSubjectName$(active_r4.subjectId))) !== null && tmp_3_0 !== void 0 ? tmp_3_0 : "\u041F\u0440\u0435\u0434\u043C\u0435\u0442", " ");
    \u0275\u0275advance(5);
    \u0275\u0275textInterpolate2(" ", ctx_r0.formatTime(active_r4.startTime), "\u2013", ctx_r0.formatTime(active_r4.endTime), " ");
    \u0275\u0275advance(4);
    \u0275\u0275textInterpolate1("\u0410\u0443\u0434. ", active_r4.room, "");
    \u0275\u0275advance(2);
    \u0275\u0275conditional(ctx_r0.isConfirmed() ? 17 : 18);
    \u0275\u0275advance(4);
    \u0275\u0275textInterpolate1(" ", ctx_r0.attendeeCount(), " \u043E\u0442\u043C\u0435\u0442\u0438\u043B\u043E\u0441\u044C ");
    \u0275\u0275advance();
    \u0275\u0275conditional(ctx_r0.isError() ? 22 : -1);
    \u0275\u0275advance();
    \u0275\u0275conditional(ctx_r0.isRequestPending() ? 23 : -1);
  }
}
function StudentCheckinComponent_Conditional_7_Conditional_7_Template(rf, ctx) {
  if (rf & 1) {
    \u0275\u0275elementStart(0, "div", 30)(1, "span", 32);
    \u0275\u0275text(2, "\u0421\u043B\u0435\u0434\u0443\u044E\u0449\u0430\u044F \u043F\u0430\u0440\u0430");
    \u0275\u0275elementEnd();
    \u0275\u0275elementStart(3, "span", 33);
    \u0275\u0275text(4);
    \u0275\u0275pipe(5, "async");
    \u0275\u0275elementEnd();
    \u0275\u0275elementStart(6, "span", 34);
    \u0275\u0275text(7);
    \u0275\u0275elementEnd()();
  }
  if (rf & 2) {
    let tmp_4_0;
    const ctx_r0 = \u0275\u0275nextContext(2);
    const nextPlanned_r5 = \u0275\u0275readContextLet(3);
    \u0275\u0275advance(4);
    \u0275\u0275textInterpolate1(" ", (tmp_4_0 = \u0275\u0275pipeBind1(5, 2, ctx_r0.getSubjectName$(nextPlanned_r5.subjectId))) !== null && tmp_4_0 !== void 0 ? tmp_4_0 : "\u041F\u0440\u0435\u0434\u043C\u0435\u0442", " ");
    \u0275\u0275advance(3);
    \u0275\u0275textInterpolate1(" \u043D\u0430\u0447\u0430\u043B\u043E \u0432 ", ctx_r0.formatTime(nextPlanned_r5.startTime), " ");
  }
}
function StudentCheckinComponent_Conditional_7_Conditional_8_Template(rf, ctx) {
  if (rf & 1) {
    \u0275\u0275elementStart(0, "p", 31);
    \u0275\u0275text(1, "\u041D\u0430 \u0441\u0435\u0433\u043E\u0434\u043D\u044F \u043F\u0430\u0440 \u0431\u043E\u043B\u044C\u0448\u0435 \u043D\u0435\u0442");
    \u0275\u0275elementEnd();
  }
}
function StudentCheckinComponent_Conditional_7_Template(rf, ctx) {
  if (rf & 1) {
    \u0275\u0275elementStart(0, "div", 5)(1, "div", 26);
    \u0275\u0275element(2, "i", 27);
    \u0275\u0275elementEnd();
    \u0275\u0275elementStart(3, "h2", 28);
    \u0275\u0275text(4, "\u041D\u0435\u0442 \u0430\u043A\u0442\u0438\u0432\u043D\u043E\u0439 \u043F\u0430\u0440\u044B");
    \u0275\u0275elementEnd();
    \u0275\u0275elementStart(5, "p", 29);
    \u0275\u0275text(6, " \u041E\u0442\u043C\u0435\u0442\u043A\u0430 \u0441\u0442\u0430\u043D\u0435\u0442 \u0434\u043E\u0441\u0442\u0443\u043F\u043D\u0430 \u0437\u0430 5 \u043C\u0438\u043D\u0443\u0442 \u0434\u043E \u043D\u0430\u0447\u0430\u043B\u0430 \u0441\u043B\u0435\u0434\u0443\u044E\u0449\u0435\u0433\u043E \u0437\u0430\u043D\u044F\u0442\u0438\u044F. ");
    \u0275\u0275elementEnd();
    \u0275\u0275template(7, StudentCheckinComponent_Conditional_7_Conditional_7_Template, 8, 4, "div", 30)(8, StudentCheckinComponent_Conditional_7_Conditional_8_Template, 2, 0, "p", 31);
    \u0275\u0275elementEnd();
  }
  if (rf & 2) {
    \u0275\u0275nextContext();
    const nextPlanned_r5 = \u0275\u0275readContextLet(3);
    \u0275\u0275advance(7);
    \u0275\u0275conditional(nextPlanned_r5 ? 7 : 8);
  }
}
function todayDateString() {
  const d = /* @__PURE__ */ new Date();
  const y = d.getFullYear();
  const m = String(d.getMonth() + 1).padStart(2, "0");
  const day = String(d.getDate()).padStart(2, "0");
  return `${y}-${m}-${day}`;
}
function formatHhMm(time) {
  return time.slice(0, 5);
}
var StudentCheckinComponent = class _StudentCheckinComponent {
  studentApi = inject(StudentApiService);
  subjectCache = inject(SubjectCacheService);
  stomp = inject(StudentStompService);
  auth = inject(AuthService);
  destroyRef = inject(DestroyRef);
  lessons = signal([]);
  loading = signal(false);
  fetchError = signal(null);
  state = signal({ kind: "idle" });
  attendeeCount = signal(0);
  activeLesson = computed(() => {
    return this.lessons().find((l) => l.status === "ACTIVE") ?? null;
  });
  nextPlannedLesson = computed(() => {
    if (this.activeLesson()) {
      return null;
    }
    return this.lessons().filter((l) => l.status === "PLANNED").sort((a, b) => a.startTime.localeCompare(b.startTime))[0] ?? null;
  });
  /** Template helper for mono time rendering. */
  formatTime(time) {
    return formatHhMm(time);
  }
  /** Template helper — delegates to SubjectCacheService. */
  getSubjectName$(subjectId) {
    return this.subjectCache.getName(subjectId ?? null);
  }
  ngOnInit() {
    const user = this.auth.currentUser();
    const groupId = user?.groupId ?? null;
    if (!groupId) {
      this.fetchError.set("\u041D\u0435 \u0443\u0434\u0430\u043B\u043E\u0441\u044C \u043E\u043F\u0440\u0435\u0434\u0435\u043B\u0438\u0442\u044C \u0433\u0440\u0443\u043F\u043F\u0443 \u043F\u043E\u043B\u044C\u0437\u043E\u0432\u0430\u0442\u0435\u043B\u044F.");
      return;
    }
    this.fetchToday(groupId);
    this.stomp.connect(groupId, () => this.auth.accessToken());
    this.stomp.marked$.pipe(takeUntilDestroyed(this.destroyRef)).subscribe({
      next: (payload) => {
        const active = this.activeLesson();
        if (active && payload.lesson_id === active.id) {
          this.attendeeCount.update((n) => n + 1);
          if (user && payload.user_id === user.id) {
            this.state.set({ kind: "confirmed" });
          }
        }
      },
      error: (err) => {
        console.error("[student-checkin] STOMP subscription error", err);
      }
    });
  }
  ngOnDestroy() {
    this.stomp.disconnect();
  }
  fetchToday(groupId) {
    const today = todayDateString();
    this.loading.set(true);
    forkJoin({
      lessons: this.studentApi.getWeekLessons(groupId, today, today),
      records: this.studentApi.getStudentRecords().pipe(catchError(() => of([])))
    }).subscribe({
      next: ({ lessons, records }) => {
        this.lessons.set(lessons);
        const active = lessons.find((l) => l.status === "ACTIVE");
        if (!active) {
          this.state.set({ kind: "idle" });
        } else {
          const own = records.find((r) => r.lessonId === active.id);
          this.state.set(own?.status === "present" ? { kind: "confirmed" } : { kind: "ready" });
        }
        this.loading.set(false);
      },
      error: (err) => {
        console.error("[student-checkin] failed to load lessons", err);
        this.fetchError.set(formatLoadError(err, "\u041D\u0435 \u0443\u0434\u0430\u043B\u043E\u0441\u044C \u0437\u0430\u0433\u0440\u0443\u0437\u0438\u0442\u044C \u0434\u0430\u043D\u043D\u044B\u0435."));
        this.loading.set(false);
      }
    });
  }
  onCheckinClick() {
    const current = this.state();
    if (current.kind !== "ready" && current.kind !== "error") {
      return;
    }
    const active = this.activeLesson();
    if (!active) {
      return;
    }
    this.state.set({ kind: "gps_pending" });
    const user = this.auth.currentUser();
    if (user?.isHeadman) {
      this.state.set({ kind: "submitting" });
      this.studentApi.checkin({ lat: 0, lng: 0 }).subscribe({
        next: () => this.state.set({ kind: "confirmed" }),
        error: (err) => {
          const status = err?.status ?? 0;
          this.state.set({ kind: "error", message: mapCheckinError(status) });
        }
      });
      return;
    }
    if (!navigator.geolocation) {
      this.state.set({ kind: "error", message: GPS_DENIED_MESSAGE });
      return;
    }
    navigator.geolocation.getCurrentPosition((position) => {
      this.state.set({ kind: "submitting" });
      this.studentApi.checkin({
        lat: position.coords.latitude,
        lng: position.coords.longitude
      }).subscribe({
        next: () => this.state.set({ kind: "confirmed" }),
        error: (err) => {
          const status = err?.status ?? 0;
          this.state.set({
            kind: "error",
            message: mapCheckinError(status)
          });
        }
      });
    }, () => {
      this.state.set({ kind: "error", message: GPS_DENIED_MESSAGE });
    }, { timeout: 1e4, maximumAge: 3e4 });
  }
  /**
   * Send a "please mark me present" request to the group's headman.
   * Fires only from `error` / `request_error` states — invalid transitions are ignored.
   */
  onRequestHeadmanClick() {
    const current = this.state();
    if (current.kind !== "error" && current.kind !== "request_error") {
      return;
    }
    const active = this.activeLesson();
    if (!active) {
      return;
    }
    this.state.set({ kind: "request_submitting" });
    this.studentApi.requestLateCheckin(active.id).subscribe({
      next: () => this.state.set({ kind: "request_pending" }),
      error: (err) => {
        const status = err?.status ?? 0;
        this.state.set({
          kind: "request_error",
          message: mapRequestHeadmanError(status)
        });
      }
    });
  }
  // ── Template helpers ───────────────────────────────────────────────
  isIdle() {
    return this.state().kind === "idle";
  }
  isReady() {
    return this.state().kind === "ready";
  }
  isGpsPending() {
    return this.state().kind === "gps_pending";
  }
  isSubmitting() {
    return this.state().kind === "submitting";
  }
  isConfirmed() {
    return this.state().kind === "confirmed";
  }
  isError() {
    const k = this.state().kind;
    return k === "error" || k === "request_error";
  }
  isRequestSubmitting() {
    return this.state().kind === "request_submitting";
  }
  isRequestPending() {
    return this.state().kind === "request_pending";
  }
  errorMessage() {
    const s = this.state();
    if (s.kind === "error")
      return s.message;
    if (s.kind === "request_error")
      return s.message;
    return null;
  }
  requestButtonLabel() {
    return this.isRequestSubmitting() ? "\u041E\u0442\u043F\u0440\u0430\u0432\u043B\u044F\u0435\u043C\u2026" : "\u041F\u043E\u043F\u0440\u043E\u0441\u0438\u0442\u044C \u0441\u0442\u0430\u0440\u043E\u0441\u0442\u0443 \u043E\u0442\u043C\u0435\u0442\u0438\u0442\u044C";
  }
  requestButtonDisabled() {
    return this.isRequestSubmitting();
  }
  buttonLabel() {
    switch (this.state().kind) {
      case "gps_pending":
        return "\u041E\u043F\u0440\u0435\u0434\u0435\u043B\u044F\u0435\u043C \u043A\u043E\u043E\u0440\u0434\u0438\u043D\u0430\u0442\u044B\u2026";
      case "submitting":
        return "\u041E\u0442\u043F\u0440\u0430\u0432\u043B\u044F\u0435\u043C \u043E\u0442\u043C\u0435\u0442\u043A\u0443\u2026";
      case "confirmed":
        return "\u0412\u044B \u043E\u0442\u043C\u0435\u0442\u0438\u043B\u0438\u0441\u044C";
      default:
        return "\u041E\u0442\u043C\u0435\u0442\u0438\u0442\u044C\u0441\u044F";
    }
  }
  buttonAriaLabel() {
    return this.isConfirmed() ? "\u0412\u044B \u0443\u0436\u0435 \u043E\u0442\u043C\u0435\u0442\u0438\u043B\u0438\u0441\u044C" : "\u041E\u0442\u043C\u0435\u0442\u0438\u0442\u044C\u0441\u044F \u043D\u0430 \u0442\u0435\u043A\u0443\u0449\u0435\u0439 \u043F\u0430\u0440\u0435";
  }
  buttonDisabled() {
    return this.isGpsPending() || this.isSubmitting() || this.isConfirmed();
  }
  static \u0275fac = function StudentCheckinComponent_Factory(__ngFactoryType__) {
    return new (__ngFactoryType__ || _StudentCheckinComponent)();
  };
  static \u0275cmp = /* @__PURE__ */ \u0275\u0275defineComponent({ type: _StudentCheckinComponent, selectors: [["app-student-checkin"]], decls: 8, vars: 4, consts: [[1, "checkin"], [1, "checkin__frame"], ["aria-busy", "true", "aria-label", "\u0417\u0430\u0433\u0440\u0443\u0437\u043A\u0430\u2026", 1, "checkin__skeleton"], ["role", "alert", 1, "checkin__fetch-error"], ["data-testid", "checkin-active-hero", 1, "checkin__hero"], ["data-testid", "checkin-empty", 1, "checkin__empty"], ["aria-hidden", "true", 1, "checkin__live"], [1, "checkin__live-dot"], [1, "checkin__live-label"], [1, "checkin__subject"], [1, "checkin__meta"], ["aria-hidden", "true", 1, "ph-fill", "ph-clock"], [1, "checkin__meta-time"], ["aria-hidden", "true", 1, "checkin__meta-sep"], ["aria-hidden", "true", 1, "ph-fill", "ph-map-pin"], [1, "checkin__cta-wrap"], ["role", "status", 1, "checkin__confirmed"], ["type", "button", 1, "checkin__cta", 3, "disabled"], ["aria-live", "polite", 1, "checkin__counter"], ["aria-hidden", "true", 1, "ph-fill", "ph-users"], ["role", "status", 1, "checkin__pending"], ["aria-hidden", "true", 1, "ph-fill", "ph-check-circle"], ["type", "button", 1, "checkin__cta", 3, "click", "disabled"], ["role", "alert", 1, "checkin__error"], ["type", "button", 1, "checkin__request-headman", 3, "click", "disabled"], ["aria-hidden", "true", 1, "ph-fill", "ph-hourglass"], ["aria-hidden", "true", 1, "checkin__empty-icon"], [1, "ph-duotone", "ph-clock"], [1, "checkin__empty-title"], [1, "checkin__empty-text"], ["data-testid", "checkin-next-hint", 1, "checkin__next-hint"], [1, "checkin__no-more"], [1, "checkin__next-label"], [1, "checkin__next-subject"], [1, "checkin__next-time"]], template: function StudentCheckinComponent_Template(rf, ctx) {
    if (rf & 1) {
      \u0275\u0275elementStart(0, "section", 0)(1, "div", 1);
      \u0275\u0275declareLet(2)(3);
      \u0275\u0275template(4, StudentCheckinComponent_Conditional_4_Template, 1, 0, "div", 2)(5, StudentCheckinComponent_Conditional_5_Template, 2, 1, "p", 3)(6, StudentCheckinComponent_Conditional_6_Template, 24, 10, "div", 4)(7, StudentCheckinComponent_Conditional_7_Template, 9, 1, "div", 5);
      \u0275\u0275elementEnd()();
    }
    if (rf & 2) {
      \u0275\u0275property("@routeFade", void 0);
      \u0275\u0275advance(2);
      const active_r6 = \u0275\u0275storeLet(ctx.activeLesson());
      \u0275\u0275advance();
      \u0275\u0275storeLet(ctx.nextPlannedLesson());
      \u0275\u0275advance();
      \u0275\u0275conditional(ctx.loading() ? 4 : ctx.fetchError() ? 5 : active_r6 ? 6 : 7);
    }
  }, dependencies: [AsyncPipe], styles: ["\n\n[_nghost-%COMP%] {\n  display: block;\n  min-height: 100%;\n}\n.checkin[_ngcontent-%COMP%] {\n  display: flex;\n  justify-content: center;\n  align-items: center;\n  min-height: 100%;\n  padding: var(--space-8) var(--space-5);\n}\n.checkin__frame[_ngcontent-%COMP%] {\n  width: 100%;\n  max-width: 480px;\n  display: flex;\n  justify-content: center;\n  align-items: center;\n}\n.checkin__skeleton[_ngcontent-%COMP%] {\n  width: 100%;\n  height: 200px;\n  border-radius: var(--radius-lg);\n  background:\n    linear-gradient(\n      90deg,\n      var(--bg-elevated) 25%,\n      color-mix(in oklab, var(--text-primary) 8%, transparent) 50%,\n      var(--bg-elevated) 75%);\n  background-size: 200% 100%;\n  animation: _ngcontent-%COMP%_checkin-shimmer 1.5s linear infinite;\n}\n@keyframes _ngcontent-%COMP%_checkin-shimmer {\n  0% {\n    background-position: 200% 0;\n  }\n  100% {\n    background-position: -200% 0;\n  }\n}\n.checkin__fetch-error[_ngcontent-%COMP%] {\n  padding: var(--space-4);\n  border-radius: var(--radius-md);\n  background: color-mix(in srgb, var(--accent-danger) 12%, transparent);\n  border: 1px solid color-mix(in srgb, var(--accent-danger) 28%, transparent);\n  color: var(--accent-danger);\n  font-size: var(--text-sm);\n}\n.checkin__hero[_ngcontent-%COMP%] {\n  width: 100%;\n  display: flex;\n  flex-direction: column;\n  align-items: center;\n  gap: var(--space-5);\n  padding: var(--space-6);\n  border-radius: var(--radius-lg);\n  background: var(--bg-secondary);\n  border: 1px solid var(--border-accent);\n  box-shadow: var(--glow-primary);\n  text-align: center;\n}\n.checkin__live[_ngcontent-%COMP%] {\n  display: inline-flex;\n  align-items: center;\n  gap: var(--space-2);\n  padding: 4px 12px;\n  border-radius: var(--radius-full);\n  background: color-mix(in oklab, var(--accent-primary) 12%, transparent);\n  border: 1px solid var(--border-accent);\n  color: var(--accent-primary);\n  font-size: var(--text-xs);\n  font-weight: 600;\n  text-transform: uppercase;\n  letter-spacing: var(--tracking-wide);\n}\n.checkin__live-dot[_ngcontent-%COMP%] {\n  width: 6px;\n  height: 6px;\n  border-radius: 50%;\n  background: var(--accent-primary);\n  box-shadow: 0 0 6px var(--accent-primary);\n  animation: _ngcontent-%COMP%_checkin-live-blink 1.6s ease-in-out infinite;\n}\n@keyframes _ngcontent-%COMP%_checkin-live-blink {\n  0%, 100% {\n    opacity: 1;\n  }\n  50% {\n    opacity: 0.35;\n  }\n}\n.checkin__subject[_ngcontent-%COMP%] {\n  margin: 0;\n  font-family: var(--font-display);\n  font-size: var(--text-2xl);\n  font-weight: 600;\n  line-height: var(--leading-display);\n  color: var(--text-primary);\n}\n.checkin__meta[_ngcontent-%COMP%] {\n  margin: 0;\n  display: inline-flex;\n  align-items: center;\n  gap: var(--space-2);\n  color: var(--text-secondary);\n  font-size: var(--text-sm);\n  font-variant-numeric: tabular-nums;\n}\n.checkin__meta[_ngcontent-%COMP%]   i[_ngcontent-%COMP%] {\n  font-size: 14px;\n}\n.checkin__meta-time[_ngcontent-%COMP%] {\n  font-family: var(--font-mono);\n  font-weight: 600;\n}\n.checkin__meta-sep[_ngcontent-%COMP%] {\n  width: 4px;\n  height: 4px;\n  border-radius: 50%;\n  background: var(--text-muted);\n  display: inline-block;\n}\n.checkin__cta-wrap[_ngcontent-%COMP%] {\n  display: flex;\n  flex-direction: column;\n  align-items: center;\n  gap: var(--space-3);\n  padding-top: var(--space-2);\n}\n.checkin__cta[_ngcontent-%COMP%] {\n  min-height: 48px;\n  min-width: 180px;\n  padding: 0 var(--space-6);\n  border-radius: var(--radius-full);\n  border: 0;\n  background: var(--accent-primary);\n  color: var(--accent-primary-contrast);\n  font-family: var(--font-heading);\n  font-size: var(--text-sm);\n  font-weight: 600;\n  cursor: pointer;\n  transition:\n    filter var(--duration-base) var(--ease-out),\n    transform var(--duration-base) var(--ease-out),\n    opacity var(--duration-base) var(--ease-out);\n}\n.checkin__cta[_ngcontent-%COMP%]:hover:not(:disabled) {\n  filter: brightness(1.08);\n}\n.checkin__cta[_ngcontent-%COMP%]:active:not(:disabled) {\n  transform: scale(0.97);\n}\n.checkin__cta[_ngcontent-%COMP%]:disabled {\n  opacity: 0.55;\n  cursor: not-allowed;\n}\n.checkin__cta[_ngcontent-%COMP%]:focus-visible {\n  outline: 2px solid var(--accent-primary);\n  outline-offset: 2px;\n}\n.checkin__confirmed[_ngcontent-%COMP%] {\n  min-height: 48px;\n  padding: 0 var(--space-5);\n  border-radius: var(--radius-full);\n  background: color-mix(in oklab, var(--accent-primary) 14%, transparent);\n  border: 1px solid var(--border-accent);\n  color: var(--accent-primary);\n  font-family: var(--font-heading);\n  font-size: var(--text-sm);\n  font-weight: 600;\n  display: inline-flex;\n  align-items: center;\n  gap: var(--space-2);\n}\n.checkin__confirmed[_ngcontent-%COMP%]   i[_ngcontent-%COMP%] {\n  font-size: 18px;\n}\n.checkin__counter[_ngcontent-%COMP%] {\n  margin: 0;\n  display: inline-flex;\n  align-items: center;\n  gap: var(--space-1);\n  font-size: var(--text-xs);\n  color: var(--text-muted);\n  font-variant-numeric: tabular-nums;\n}\n.checkin__counter[_ngcontent-%COMP%]   i[_ngcontent-%COMP%] {\n  font-size: 12px;\n}\n.checkin__error[_ngcontent-%COMP%] {\n  margin: 0;\n  color: var(--accent-danger);\n  font-size: var(--text-sm);\n  text-align: center;\n}\n.checkin__request-headman[_ngcontent-%COMP%] {\n  margin-top: var(--space-2);\n  min-height: 40px;\n  padding: 0 var(--space-5);\n  border-radius: var(--radius-full);\n  background: transparent;\n  border: 1px solid var(--border-accent);\n  color: var(--accent-primary);\n  font-family: var(--font-heading);\n  font-size: var(--text-sm);\n  font-weight: 600;\n  cursor: pointer;\n  transition: background var(--duration-base) var(--ease-out), opacity var(--duration-base) var(--ease-out);\n}\n.checkin__request-headman[_ngcontent-%COMP%]:hover:not(:disabled) {\n  background: color-mix(in oklab, var(--accent-primary) 10%, transparent);\n}\n.checkin__request-headman[_ngcontent-%COMP%]:disabled {\n  opacity: 0.55;\n  cursor: not-allowed;\n}\n.checkin__request-headman[_ngcontent-%COMP%]:focus-visible {\n  outline: 2px solid var(--accent-primary);\n  outline-offset: 2px;\n}\n.checkin__pending[_ngcontent-%COMP%] {\n  margin: var(--space-2) 0 0;\n  display: inline-flex;\n  align-items: center;\n  gap: var(--space-2);\n  padding: var(--space-2) var(--space-4);\n  border-radius: var(--radius-full);\n  background: color-mix(in oklab, var(--accent-primary) 10%, transparent);\n  border: 1px solid var(--border-accent);\n  color: var(--accent-primary);\n  font-size: var(--text-sm);\n  text-align: center;\n}\n.checkin__pending[_ngcontent-%COMP%]   i[_ngcontent-%COMP%] {\n  font-size: 16px;\n}\n.checkin__empty[_ngcontent-%COMP%] {\n  display: flex;\n  flex-direction: column;\n  align-items: center;\n  gap: var(--space-5);\n  text-align: center;\n  padding: var(--space-7);\n}\n.checkin__empty-icon[_ngcontent-%COMP%] {\n  width: 80px;\n  height: 80px;\n  border-radius: var(--radius-full);\n  background: var(--bg-secondary);\n  border: 1px solid var(--border-subtle);\n  color: var(--text-muted);\n  display: grid;\n  place-items: center;\n}\n.checkin__empty-icon[_ngcontent-%COMP%]   i[_ngcontent-%COMP%] {\n  font-size: 36px;\n}\n.checkin__empty-title[_ngcontent-%COMP%] {\n  margin: 0;\n  font-family: var(--font-display);\n  font-size: var(--text-2xl);\n  font-weight: 600;\n  color: var(--text-primary);\n}\n.checkin__empty-text[_ngcontent-%COMP%] {\n  margin: 0;\n  font-size: var(--text-sm);\n  color: var(--text-secondary);\n  max-width: 300px;\n}\n.checkin__next-hint[_ngcontent-%COMP%] {\n  margin-top: var(--space-3);\n  padding: var(--space-4) var(--space-5);\n  background: var(--bg-secondary);\n  border: 1px solid var(--border-subtle);\n  border-radius: var(--radius-lg);\n  display: flex;\n  flex-direction: column;\n  align-items: center;\n  gap: var(--space-1);\n}\n.checkin__next-label[_ngcontent-%COMP%] {\n  font-size: var(--text-xs);\n  color: var(--text-muted);\n  text-transform: uppercase;\n  letter-spacing: var(--tracking-wide);\n  font-weight: 600;\n}\n.checkin__next-subject[_ngcontent-%COMP%] {\n  font-family: var(--font-heading);\n  font-size: var(--text-base);\n  font-weight: 600;\n  color: var(--text-primary);\n}\n.checkin__next-time[_ngcontent-%COMP%] {\n  font-family: var(--font-mono);\n  font-size: var(--text-sm);\n  color: var(--text-secondary);\n  font-variant-numeric: tabular-nums;\n}\n.checkin__no-more[_ngcontent-%COMP%] {\n  margin: 0;\n  font-size: var(--text-xs);\n  color: var(--text-muted);\n}\n@media (prefers-reduced-motion: reduce) {\n  .checkin__live-dot[_ngcontent-%COMP%] {\n    animation: none;\n  }\n  .checkin__skeleton[_ngcontent-%COMP%] {\n    animation: none;\n  }\n  .checkin__cta[_ngcontent-%COMP%] {\n    transition: none;\n  }\n}\n/*# sourceMappingURL=student-checkin.component.css.map */"], data: { animation: [
    trigger("routeFade", [
      transition(":enter", [
        style({ opacity: 0, transform: "translateY(8px)" }),
        animate("200ms cubic-bezier(0.16, 1, 0.3, 1)", style({ opacity: 1, transform: "translateY(0)" }))
      ])
    ])
  ] }, changeDetection: 0 });
};
(() => {
  (typeof ngDevMode === "undefined" || ngDevMode) && setClassMetadata(StudentCheckinComponent, [{
    type: Component,
    args: [{ selector: "app-student-checkin", standalone: true, changeDetection: ChangeDetectionStrategy.OnPush, imports: [AsyncPipe], animations: [
      trigger("routeFade", [
        transition(":enter", [
          style({ opacity: 0, transform: "translateY(8px)" }),
          animate("200ms cubic-bezier(0.16, 1, 0.3, 1)", style({ opacity: 1, transform: "translateY(0)" }))
        ])
      ])
    ], template: `<section class="checkin" [@routeFade]>\r
  <div class="checkin__frame">\r
    @let active = activeLesson();\r
    @let nextPlanned = nextPlannedLesson();\r
\r
    @if (loading()) {\r
      <div class="checkin__skeleton" aria-busy="true" aria-label="\u0417\u0430\u0433\u0440\u0443\u0437\u043A\u0430\u2026"></div>\r
    } @else if (fetchError()) {\r
      <p class="checkin__fetch-error" role="alert">{{ fetchError() }}</p>\r
    } @else if (active) {\r
      <!-- Active lesson hero -->\r
      <div class="checkin__hero" data-testid="checkin-active-hero">\r
        <div class="checkin__live" aria-hidden="true">\r
          <span class="checkin__live-dot"></span>\r
          <span class="checkin__live-label">\u0418\u0434\u0451\u0442 \u0441\u0435\u0439\u0447\u0430\u0441</span>\r
        </div>\r
\r
        <h2 class="checkin__subject">\r
          {{ (getSubjectName$(active.subjectId) | async) ?? '\u041F\u0440\u0435\u0434\u043C\u0435\u0442' }}\r
        </h2>\r
\r
        <p class="checkin__meta">\r
          <i class="ph-fill ph-clock" aria-hidden="true"></i>\r
          <span class="checkin__meta-time">\r
            {{ formatTime(active.startTime) }}\u2013{{ formatTime(active.endTime) }}\r
          </span>\r
          <span class="checkin__meta-sep" aria-hidden="true"></span>\r
          <i class="ph-fill ph-map-pin" aria-hidden="true"></i>\r
          <span>\u0410\u0443\u0434. {{ active.room }}</span>\r
        </p>\r
\r
        <div class="checkin__cta-wrap">\r
          @if (isConfirmed()) {\r
            <div class="checkin__confirmed" role="status">\r
              <i class="ph-fill ph-check-circle" aria-hidden="true"></i>\r
              \u0412\u044B \u043E\u0442\u043C\u0435\u0442\u0438\u043B\u0438\u0441\u044C\r
            </div>\r
          } @else {\r
            <button\r
              type="button"\r
              class="checkin__cta"\r
              [attr.aria-label]="buttonAriaLabel()"\r
              [attr.aria-busy]="isGpsPending() || isSubmitting()"\r
              [disabled]="buttonDisabled()"\r
              (click)="onCheckinClick()"\r
            >\r
              {{ buttonLabel() }}\r
            </button>\r
          }\r
\r
          <p class="checkin__counter" aria-live="polite">\r
            <i class="ph-fill ph-users" aria-hidden="true"></i>\r
            {{ attendeeCount() }} \u043E\u0442\u043C\u0435\u0442\u0438\u043B\u043E\u0441\u044C\r
          </p>\r
        </div>\r
\r
        @if (isError()) {\r
          <p class="checkin__error" role="alert">{{ errorMessage() }}</p>\r
          <button\r
            type="button"\r
            class="checkin__request-headman"\r
            [disabled]="requestButtonDisabled()"\r
            (click)="onRequestHeadmanClick()"\r
          >\r
            {{ requestButtonLabel() }}\r
          </button>\r
        }\r
\r
        @if (isRequestPending()) {\r
          <p class="checkin__pending" role="status">\r
            <i class="ph-fill ph-hourglass" aria-hidden="true"></i>\r
            \u0417\u0430\u043F\u0440\u043E\u0441 \u043E\u0442\u043F\u0440\u0430\u0432\u043B\u0435\u043D \u0441\u0442\u0430\u0440\u043E\u0441\u0442\u0435. \u041E\u0436\u0438\u0434\u0430\u0439\u0442\u0435 \u043E\u0442\u0432\u0435\u0442\u0430 \u0432 Telegram.\r
          </p>\r
        }\r
      </div>\r
    } @else {\r
      <!-- Idle empty state -->\r
      <div class="checkin__empty" data-testid="checkin-empty">\r
        <div class="checkin__empty-icon" aria-hidden="true">\r
          <i class="ph-duotone ph-clock"></i>\r
        </div>\r
        <h2 class="checkin__empty-title">\u041D\u0435\u0442 \u0430\u043A\u0442\u0438\u0432\u043D\u043E\u0439 \u043F\u0430\u0440\u044B</h2>\r
        <p class="checkin__empty-text">\r
          \u041E\u0442\u043C\u0435\u0442\u043A\u0430 \u0441\u0442\u0430\u043D\u0435\u0442 \u0434\u043E\u0441\u0442\u0443\u043F\u043D\u0430 \u0437\u0430 5 \u043C\u0438\u043D\u0443\u0442 \u0434\u043E \u043D\u0430\u0447\u0430\u043B\u0430 \u0441\u043B\u0435\u0434\u0443\u044E\u0449\u0435\u0433\u043E \u0437\u0430\u043D\u044F\u0442\u0438\u044F.\r
        </p>\r
\r
        @if (nextPlanned) {\r
          <div class="checkin__next-hint" data-testid="checkin-next-hint">\r
            <span class="checkin__next-label">\u0421\u043B\u0435\u0434\u0443\u044E\u0449\u0430\u044F \u043F\u0430\u0440\u0430</span>\r
            <span class="checkin__next-subject">\r
              {{ (getSubjectName$(nextPlanned.subjectId) | async) ?? '\u041F\u0440\u0435\u0434\u043C\u0435\u0442' }}\r
            </span>\r
            <span class="checkin__next-time">\r
              \u043D\u0430\u0447\u0430\u043B\u043E \u0432 {{ formatTime(nextPlanned.startTime) }}\r
            </span>\r
          </div>\r
        } @else {\r
          <p class="checkin__no-more">\u041D\u0430 \u0441\u0435\u0433\u043E\u0434\u043D\u044F \u043F\u0430\u0440 \u0431\u043E\u043B\u044C\u0448\u0435 \u043D\u0435\u0442</p>\r
        }\r
      </div>\r
    }\r
  </div>\r
</section>\r
`, styles: ["/* src/app/features/student/checkin/student-checkin.component.css */\n:host {\n  display: block;\n  min-height: 100%;\n}\n.checkin {\n  display: flex;\n  justify-content: center;\n  align-items: center;\n  min-height: 100%;\n  padding: var(--space-8) var(--space-5);\n}\n.checkin__frame {\n  width: 100%;\n  max-width: 480px;\n  display: flex;\n  justify-content: center;\n  align-items: center;\n}\n.checkin__skeleton {\n  width: 100%;\n  height: 200px;\n  border-radius: var(--radius-lg);\n  background:\n    linear-gradient(\n      90deg,\n      var(--bg-elevated) 25%,\n      color-mix(in oklab, var(--text-primary) 8%, transparent) 50%,\n      var(--bg-elevated) 75%);\n  background-size: 200% 100%;\n  animation: checkin-shimmer 1.5s linear infinite;\n}\n@keyframes checkin-shimmer {\n  0% {\n    background-position: 200% 0;\n  }\n  100% {\n    background-position: -200% 0;\n  }\n}\n.checkin__fetch-error {\n  padding: var(--space-4);\n  border-radius: var(--radius-md);\n  background: color-mix(in srgb, var(--accent-danger) 12%, transparent);\n  border: 1px solid color-mix(in srgb, var(--accent-danger) 28%, transparent);\n  color: var(--accent-danger);\n  font-size: var(--text-sm);\n}\n.checkin__hero {\n  width: 100%;\n  display: flex;\n  flex-direction: column;\n  align-items: center;\n  gap: var(--space-5);\n  padding: var(--space-6);\n  border-radius: var(--radius-lg);\n  background: var(--bg-secondary);\n  border: 1px solid var(--border-accent);\n  box-shadow: var(--glow-primary);\n  text-align: center;\n}\n.checkin__live {\n  display: inline-flex;\n  align-items: center;\n  gap: var(--space-2);\n  padding: 4px 12px;\n  border-radius: var(--radius-full);\n  background: color-mix(in oklab, var(--accent-primary) 12%, transparent);\n  border: 1px solid var(--border-accent);\n  color: var(--accent-primary);\n  font-size: var(--text-xs);\n  font-weight: 600;\n  text-transform: uppercase;\n  letter-spacing: var(--tracking-wide);\n}\n.checkin__live-dot {\n  width: 6px;\n  height: 6px;\n  border-radius: 50%;\n  background: var(--accent-primary);\n  box-shadow: 0 0 6px var(--accent-primary);\n  animation: checkin-live-blink 1.6s ease-in-out infinite;\n}\n@keyframes checkin-live-blink {\n  0%, 100% {\n    opacity: 1;\n  }\n  50% {\n    opacity: 0.35;\n  }\n}\n.checkin__subject {\n  margin: 0;\n  font-family: var(--font-display);\n  font-size: var(--text-2xl);\n  font-weight: 600;\n  line-height: var(--leading-display);\n  color: var(--text-primary);\n}\n.checkin__meta {\n  margin: 0;\n  display: inline-flex;\n  align-items: center;\n  gap: var(--space-2);\n  color: var(--text-secondary);\n  font-size: var(--text-sm);\n  font-variant-numeric: tabular-nums;\n}\n.checkin__meta i {\n  font-size: 14px;\n}\n.checkin__meta-time {\n  font-family: var(--font-mono);\n  font-weight: 600;\n}\n.checkin__meta-sep {\n  width: 4px;\n  height: 4px;\n  border-radius: 50%;\n  background: var(--text-muted);\n  display: inline-block;\n}\n.checkin__cta-wrap {\n  display: flex;\n  flex-direction: column;\n  align-items: center;\n  gap: var(--space-3);\n  padding-top: var(--space-2);\n}\n.checkin__cta {\n  min-height: 48px;\n  min-width: 180px;\n  padding: 0 var(--space-6);\n  border-radius: var(--radius-full);\n  border: 0;\n  background: var(--accent-primary);\n  color: var(--accent-primary-contrast);\n  font-family: var(--font-heading);\n  font-size: var(--text-sm);\n  font-weight: 600;\n  cursor: pointer;\n  transition:\n    filter var(--duration-base) var(--ease-out),\n    transform var(--duration-base) var(--ease-out),\n    opacity var(--duration-base) var(--ease-out);\n}\n.checkin__cta:hover:not(:disabled) {\n  filter: brightness(1.08);\n}\n.checkin__cta:active:not(:disabled) {\n  transform: scale(0.97);\n}\n.checkin__cta:disabled {\n  opacity: 0.55;\n  cursor: not-allowed;\n}\n.checkin__cta:focus-visible {\n  outline: 2px solid var(--accent-primary);\n  outline-offset: 2px;\n}\n.checkin__confirmed {\n  min-height: 48px;\n  padding: 0 var(--space-5);\n  border-radius: var(--radius-full);\n  background: color-mix(in oklab, var(--accent-primary) 14%, transparent);\n  border: 1px solid var(--border-accent);\n  color: var(--accent-primary);\n  font-family: var(--font-heading);\n  font-size: var(--text-sm);\n  font-weight: 600;\n  display: inline-flex;\n  align-items: center;\n  gap: var(--space-2);\n}\n.checkin__confirmed i {\n  font-size: 18px;\n}\n.checkin__counter {\n  margin: 0;\n  display: inline-flex;\n  align-items: center;\n  gap: var(--space-1);\n  font-size: var(--text-xs);\n  color: var(--text-muted);\n  font-variant-numeric: tabular-nums;\n}\n.checkin__counter i {\n  font-size: 12px;\n}\n.checkin__error {\n  margin: 0;\n  color: var(--accent-danger);\n  font-size: var(--text-sm);\n  text-align: center;\n}\n.checkin__request-headman {\n  margin-top: var(--space-2);\n  min-height: 40px;\n  padding: 0 var(--space-5);\n  border-radius: var(--radius-full);\n  background: transparent;\n  border: 1px solid var(--border-accent);\n  color: var(--accent-primary);\n  font-family: var(--font-heading);\n  font-size: var(--text-sm);\n  font-weight: 600;\n  cursor: pointer;\n  transition: background var(--duration-base) var(--ease-out), opacity var(--duration-base) var(--ease-out);\n}\n.checkin__request-headman:hover:not(:disabled) {\n  background: color-mix(in oklab, var(--accent-primary) 10%, transparent);\n}\n.checkin__request-headman:disabled {\n  opacity: 0.55;\n  cursor: not-allowed;\n}\n.checkin__request-headman:focus-visible {\n  outline: 2px solid var(--accent-primary);\n  outline-offset: 2px;\n}\n.checkin__pending {\n  margin: var(--space-2) 0 0;\n  display: inline-flex;\n  align-items: center;\n  gap: var(--space-2);\n  padding: var(--space-2) var(--space-4);\n  border-radius: var(--radius-full);\n  background: color-mix(in oklab, var(--accent-primary) 10%, transparent);\n  border: 1px solid var(--border-accent);\n  color: var(--accent-primary);\n  font-size: var(--text-sm);\n  text-align: center;\n}\n.checkin__pending i {\n  font-size: 16px;\n}\n.checkin__empty {\n  display: flex;\n  flex-direction: column;\n  align-items: center;\n  gap: var(--space-5);\n  text-align: center;\n  padding: var(--space-7);\n}\n.checkin__empty-icon {\n  width: 80px;\n  height: 80px;\n  border-radius: var(--radius-full);\n  background: var(--bg-secondary);\n  border: 1px solid var(--border-subtle);\n  color: var(--text-muted);\n  display: grid;\n  place-items: center;\n}\n.checkin__empty-icon i {\n  font-size: 36px;\n}\n.checkin__empty-title {\n  margin: 0;\n  font-family: var(--font-display);\n  font-size: var(--text-2xl);\n  font-weight: 600;\n  color: var(--text-primary);\n}\n.checkin__empty-text {\n  margin: 0;\n  font-size: var(--text-sm);\n  color: var(--text-secondary);\n  max-width: 300px;\n}\n.checkin__next-hint {\n  margin-top: var(--space-3);\n  padding: var(--space-4) var(--space-5);\n  background: var(--bg-secondary);\n  border: 1px solid var(--border-subtle);\n  border-radius: var(--radius-lg);\n  display: flex;\n  flex-direction: column;\n  align-items: center;\n  gap: var(--space-1);\n}\n.checkin__next-label {\n  font-size: var(--text-xs);\n  color: var(--text-muted);\n  text-transform: uppercase;\n  letter-spacing: var(--tracking-wide);\n  font-weight: 600;\n}\n.checkin__next-subject {\n  font-family: var(--font-heading);\n  font-size: var(--text-base);\n  font-weight: 600;\n  color: var(--text-primary);\n}\n.checkin__next-time {\n  font-family: var(--font-mono);\n  font-size: var(--text-sm);\n  color: var(--text-secondary);\n  font-variant-numeric: tabular-nums;\n}\n.checkin__no-more {\n  margin: 0;\n  font-size: var(--text-xs);\n  color: var(--text-muted);\n}\n@media (prefers-reduced-motion: reduce) {\n  .checkin__live-dot {\n    animation: none;\n  }\n  .checkin__skeleton {\n    animation: none;\n  }\n  .checkin__cta {\n    transition: none;\n  }\n}\n/*# sourceMappingURL=student-checkin.component.css.map */\n"] }]
  }], null, null);
})();
(() => {
  (typeof ngDevMode === "undefined" || ngDevMode) && \u0275setClassDebugInfo(StudentCheckinComponent, { className: "StudentCheckinComponent", filePath: "src/app/features/student/checkin/student-checkin.component.ts", lineNumber: 104 });
})();
export {
  StudentCheckinComponent
};
//# sourceMappingURL=chunk-22AL5ZRV.js.map
