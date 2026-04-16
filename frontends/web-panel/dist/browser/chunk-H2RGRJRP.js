import {
  HeadmanApiService
} from "./chunk-BU5FJGI6.js";
import {
  MatSnackBar,
  MatSnackBarModule
} from "./chunk-53YESMZZ.js";
import {
  SubjectCacheService
} from "./chunk-QY7VTECN.js";
import {
  NotificationCenterService
} from "./chunk-PPDDTI5J.js";
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
import "./chunk-OIBNGD5S.js";
import "./chunk-PAS4QIDL.js";
import "./chunk-6JM2QIGP.js";
import "./chunk-75YPR2VK.js";
import "./chunk-2UA5NBNP.js";
import "./chunk-YIJEORIR.js";
import "./chunk-B3BDHDAM.js";
import {
  DatePipe
} from "./chunk-M5DKW26A.js";
import {
  ChangeDetectionStrategy,
  Component,
  DestroyRef,
  Injectable,
  Subject,
  __spreadProps,
  __spreadValues,
  __toESM,
  computed,
  inject,
  setClassMetadata,
  signal,
  ɵsetClassDebugInfo,
  ɵɵadvance,
  ɵɵconditional,
  ɵɵdefineComponent,
  ɵɵdefineInjectable,
  ɵɵelement,
  ɵɵelementEnd,
  ɵɵelementStart,
  ɵɵgetCurrentView,
  ɵɵlistener,
  ɵɵnextContext,
  ɵɵpipe,
  ɵɵpipeBind2,
  ɵɵproperty,
  ɵɵrepeater,
  ɵɵrepeaterCreate,
  ɵɵresetView,
  ɵɵrestoreView,
  ɵɵsyntheticHostProperty,
  ɵɵtemplate,
  ɵɵtext,
  ɵɵtextInterpolate,
  ɵɵtextInterpolate1
} from "./chunk-MFRIGFR2.js";

// src/app/features/headman/shared/headman-stomp.service.ts
var import_stompjs = __toESM(require_stomp_umd());
var import_sockjs_client = __toESM(require_entry());
var HeadmanStompService = class _HeadmanStompService {
  client = null;
  currentGroupId = null;
  lateCheckinSubject = new Subject();
  lateCheckinRequested$ = this.lateCheckinSubject.asObservable();
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
        this.client?.subscribe(`/topic/group/${groupId}/headman`, (message) => {
          try {
            const envelope = JSON.parse(message.body);
            if (envelope.type === "late_checkin.requested") {
              this.lateCheckinSubject.next(envelope.payload);
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
  disconnect() {
    if (this.client !== null) {
      this.client.deactivate();
      this.client = null;
      this.currentGroupId = null;
    }
  }
  static \u0275fac = function HeadmanStompService_Factory(__ngFactoryType__) {
    return new (__ngFactoryType__ || _HeadmanStompService)();
  };
  static \u0275prov = /* @__PURE__ */ \u0275\u0275defineInjectable({ token: _HeadmanStompService, factory: _HeadmanStompService.\u0275fac, providedIn: "root" });
};
(() => {
  (typeof ngDevMode === "undefined" || ngDevMode) && setClassMetadata(HeadmanStompService, [{
    type: Injectable,
    args: [{ providedIn: "root" }]
  }], null, null);
})();

// src/app/features/headman/late-checkin/headman-late-checkin.component.ts
var _forTrack0 = ($index, $item) => $item.id;
function HeadmanLateCheckinComponent_Conditional_7_Template(rf, ctx) {
  if (rf & 1) {
    \u0275\u0275elementStart(0, "div", 4);
    \u0275\u0275element(1, "div", 5)(2, "div", 5);
    \u0275\u0275elementStart(3, "span", 6);
    \u0275\u0275text(4, "\u0417\u0430\u0433\u0440\u0443\u0437\u043A\u0430...");
    \u0275\u0275elementEnd()();
  }
}
function HeadmanLateCheckinComponent_Conditional_8_Template(rf, ctx) {
  if (rf & 1) {
    \u0275\u0275elementStart(0, "div", 4)(1, "div", 7);
    \u0275\u0275element(2, "i", 8);
    \u0275\u0275text(3);
    \u0275\u0275elementEnd()();
  }
  if (rf & 2) {
    const ctx_r0 = \u0275\u0275nextContext();
    \u0275\u0275advance(3);
    \u0275\u0275textInterpolate1(" ", ctx_r0.loadError(), " ");
  }
}
function HeadmanLateCheckinComponent_Conditional_9_Template(rf, ctx) {
  if (rf & 1) {
    \u0275\u0275elementStart(0, "div", 4)(1, "div", 9)(2, "div", 10);
    \u0275\u0275element(3, "i", 11);
    \u0275\u0275elementEnd();
    \u0275\u0275elementStart(4, "p", 12);
    \u0275\u0275text(5, "\u041D\u0435\u0442 \u0430\u043A\u0442\u0438\u0432\u043D\u044B\u0445 \u0437\u0430\u043F\u0440\u043E\u0441\u043E\u0432");
    \u0275\u0275elementEnd();
    \u0275\u0275elementStart(6, "p", 13);
    \u0275\u0275text(7, " \u0417\u0430\u044F\u0432\u043A\u0438 \u043F\u043E\u044F\u0432\u044F\u0442\u0441\u044F \u0437\u0434\u0435\u0441\u044C \u0430\u0432\u0442\u043E\u043C\u0430\u0442\u0438\u0447\u0435\u0441\u043A\u0438 \u2014 \u043A\u043E\u0433\u0434\u0430 \u0441\u0442\u0443\u0434\u0435\u043D\u0442 \u0437\u0430\u043F\u0440\u043E\u0441\u0438\u0442 \u043F\u043E\u0434\u0442\u0432\u0435\u0440\u0436\u0434\u0435\u043D\u0438\u0435. ");
    \u0275\u0275elementEnd()()();
  }
}
function HeadmanLateCheckinComponent_Conditional_10_For_7_Template(rf, ctx) {
  if (rf & 1) {
    const _r2 = \u0275\u0275getCurrentView();
    \u0275\u0275elementStart(0, "article", 16)(1, "header", 17)(2, "div", 18)(3, "strong", 19);
    \u0275\u0275text(4);
    \u0275\u0275elementEnd();
    \u0275\u0275elementStart(5, "span", 20);
    \u0275\u0275text(6);
    \u0275\u0275elementEnd()();
    \u0275\u0275elementStart(7, "span", 21);
    \u0275\u0275text(8);
    \u0275\u0275pipe(9, "date");
    \u0275\u0275elementEnd()();
    \u0275\u0275elementStart(10, "div", 22)(11, "button", 23);
    \u0275\u0275listener("click", function HeadmanLateCheckinComponent_Conditional_10_For_7_Template_button_click_11_listener() {
      const req_r3 = \u0275\u0275restoreView(_r2).$implicit;
      const ctx_r0 = \u0275\u0275nextContext(2);
      return \u0275\u0275resetView(ctx_r0.decide(req_r3.id, true));
    });
    \u0275\u0275element(12, "i", 24);
    \u0275\u0275text(13, " \u041F\u043E\u0434\u0442\u0432\u0435\u0440\u0434\u0438\u0442\u044C ");
    \u0275\u0275elementEnd();
    \u0275\u0275elementStart(14, "button", 25);
    \u0275\u0275listener("click", function HeadmanLateCheckinComponent_Conditional_10_For_7_Template_button_click_14_listener() {
      const req_r3 = \u0275\u0275restoreView(_r2).$implicit;
      const ctx_r0 = \u0275\u0275nextContext(2);
      return \u0275\u0275resetView(ctx_r0.decide(req_r3.id, false));
    });
    \u0275\u0275element(15, "i", 26);
    \u0275\u0275text(16, " \u041E\u0442\u043A\u043B\u043E\u043D\u0438\u0442\u044C ");
    \u0275\u0275elementEnd()()();
  }
  if (rf & 2) {
    const req_r3 = ctx.$implicit;
    const ctx_r0 = \u0275\u0275nextContext(2);
    \u0275\u0275advance(4);
    \u0275\u0275textInterpolate(req_r3.studentName);
    \u0275\u0275advance(2);
    \u0275\u0275textInterpolate(ctx_r0.formatLesson(req_r3));
    \u0275\u0275advance(2);
    \u0275\u0275textInterpolate1(" ", \u0275\u0275pipeBind2(9, 5, req_r3.createdAt, "dd.MM.yyyy HH:mm"), " ");
    \u0275\u0275advance(3);
    \u0275\u0275property("disabled", ctx_r0.busyId() === req_r3.id);
    \u0275\u0275advance(3);
    \u0275\u0275property("disabled", ctx_r0.busyId() === req_r3.id);
  }
}
function HeadmanLateCheckinComponent_Conditional_10_Template(rf, ctx) {
  if (rf & 1) {
    \u0275\u0275elementStart(0, "section", 4)(1, "div", 14)(2, "h2");
    \u0275\u0275text(3, "\u041D\u0430 \u0440\u0430\u0441\u0441\u043C\u043E\u0442\u0440\u0435\u043D\u0438\u0438");
    \u0275\u0275elementEnd();
    \u0275\u0275elementStart(4, "span", 15);
    \u0275\u0275text(5);
    \u0275\u0275elementEnd()();
    \u0275\u0275repeaterCreate(6, HeadmanLateCheckinComponent_Conditional_10_For_7_Template, 17, 8, "article", 16, _forTrack0);
    \u0275\u0275elementEnd();
  }
  if (rf & 2) {
    const ctx_r0 = \u0275\u0275nextContext();
    \u0275\u0275advance(5);
    \u0275\u0275textInterpolate(ctx_r0.requests().length);
    \u0275\u0275advance();
    \u0275\u0275repeater(ctx_r0.requests());
  }
}
var HeadmanLateCheckinComponent = class _HeadmanLateCheckinComponent {
  api = inject(HeadmanApiService);
  stomp = inject(HeadmanStompService);
  auth = inject(AuthService);
  snack = inject(MatSnackBar);
  subjectCache = inject(SubjectCacheService);
  center = inject(NotificationCenterService);
  destroyRef = inject(DestroyRef);
  requests = signal([]);
  loading = signal(true);
  loadError = signal(null);
  busyId = signal(null);
  noGroup = computed(() => this.auth.currentUser()?.groupId == null);
  realtimeSub = null;
  ruDays = ["\u0432\u0441", "\u043F\u043D", "\u0432\u0442", "\u0441\u0440", "\u0447\u0442", "\u043F\u0442", "\u0441\u0431"];
  ngOnInit() {
    const user = this.auth.currentUser();
    const groupId = user?.groupId ?? null;
    this.fetch(groupId);
    if (groupId != null) {
      this.stomp.connect(groupId, () => this.auth.accessToken());
      this.realtimeSub = this.stomp.lateCheckinRequested$.subscribe((payload) => {
        const existing = this.requests();
        if (existing.some((r) => r.id === payload.request_id))
          return;
        const enriched = {
          id: payload.request_id,
          studentId: payload.user_id,
          groupId: payload.group_id,
          lessonId: payload.lesson_id,
          studentName: payload.student_name,
          status: "pending",
          decisionBy: null,
          decisionAt: null,
          createdAt: (/* @__PURE__ */ new Date()).toISOString(),
          updatedAt: (/* @__PURE__ */ new Date()).toISOString(),
          lessonNumber: payload.lesson_number ?? null,
          lessonDate: payload.lesson_date ?? null,
          subjectId: payload.subject_id ?? null,
          subjectName: payload.subject_name ?? null
        };
        this.requests.set([...existing, enriched]);
      });
    }
    this.center.onEvent$.pipe(takeUntilDestroyed(this.destroyRef)).subscribe((envelope) => {
      if (envelope.type !== "late_checkin.decided")
        return;
      const requestId = envelope.payload?.["request_id"];
      if (typeof requestId !== "string")
        return;
      this.requests.set(this.requests().filter((r) => r.id !== requestId));
    });
  }
  ngOnDestroy() {
    this.realtimeSub?.unsubscribe();
    this.stomp.disconnect();
  }
  fetch(groupId) {
    this.loading.set(true);
    this.loadError.set(null);
    this.api.getPendingLateCheckins().subscribe({
      next: (list) => {
        const pending = list.filter((r) => r.status === "pending");
        this.requests.set(pending);
        this.loading.set(false);
        if (groupId != null && pending.length > 0) {
          this.enrichFromSchedule(groupId, pending);
        }
      },
      error: (err) => {
        this.loadError.set(this.describeLoadError(err));
        this.loading.set(false);
      }
    });
  }
  /**
   * Подтягиваем расписание группы за ±7 дней от сегодня и обогащаем карточки
   * деталями пар (subjectId, lessonNumber, lessonDate). Для subjectName
   * используем существующий {@link SubjectCacheService}, чтобы переиспользовать
   * кэш со страницами студента.
   *
   * Если какой-то lessonId не найден в окне дат — не страшно: в карточке
   * останется «Пара #{lessonId}» вместо обогащённой строки, и ошибки в UI нет.
   */
  enrichFromSchedule(groupId, list) {
    const today = /* @__PURE__ */ new Date();
    const from = new Date(today);
    from.setDate(from.getDate() - 7);
    const to = new Date(today);
    to.setDate(to.getDate() + 7);
    const dateFrom = from.toISOString().slice(0, 10);
    const dateTo = to.toISOString().slice(0, 10);
    this.api.getGroupLessons(groupId, dateFrom, dateTo).subscribe({
      next: (resp) => {
        const lessons = this.extractLessons(resp);
        const byId = /* @__PURE__ */ new Map();
        for (const l of lessons) {
          if (l && typeof l.id === "number") {
            byId.set(l.id, {
              lessonNumber: l.lessonNumber,
              date: l.date,
              subjectId: l.subjectId
            });
          }
        }
        if (byId.size === 0)
          return;
        const subjectIds = /* @__PURE__ */ new Set();
        for (const details of byId.values())
          subjectIds.add(details.subjectId);
        const subjectNames = /* @__PURE__ */ new Map();
        const pendingIds = [...subjectIds];
        const applyUpdate = () => {
          this.requests.update((curr) => curr.map((req) => {
            const details = byId.get(req.lessonId);
            if (!details)
              return req;
            return __spreadProps(__spreadValues({}, req), {
              lessonNumber: details.lessonNumber,
              lessonDate: details.date,
              subjectId: details.subjectId,
              subjectName: subjectNames.get(details.subjectId) ?? req.subjectName ?? null
            });
          }));
        };
        if (pendingIds.length === 0) {
          applyUpdate();
          return;
        }
        let resolved = 0;
        for (const sid of pendingIds) {
          this.subjectCache.getName(sid).subscribe((name) => {
            subjectNames.set(sid, name);
            resolved += 1;
            if (resolved === pendingIds.length)
              applyUpdate();
          });
        }
      },
      error: () => {
      }
    });
  }
  extractLessons(resp) {
    if (!resp || typeof resp !== "object")
      return [];
    const anyResp = resp;
    if (Array.isArray(anyResp))
      return anyResp;
    const embedded = anyResp["_embedded"];
    if (!embedded)
      return [];
    const firstKey = Object.keys(embedded)[0];
    return firstKey ? embedded[firstKey] : [];
  }
  decide(requestId, approved) {
    if (this.busyId() === requestId)
      return;
    this.busyId.set(requestId);
    this.api.decideLateCheckin(requestId, approved).subscribe({
      next: () => {
        this.requests.set(this.requests().filter((r) => r.id !== requestId));
        this.busyId.set(null);
        this.snack.open(approved ? "\u0417\u0430\u043F\u0440\u043E\u0441 \u043F\u043E\u0434\u0442\u0432\u0435\u0440\u0436\u0434\u0451\u043D" : "\u0417\u0430\u043F\u0440\u043E\u0441 \u043E\u0442\u043A\u043B\u043E\u043D\u0451\u043D", "OK", { duration: 2500 });
      },
      error: (err) => {
        this.busyId.set(null);
        this.snack.open(this.describeDecisionError(err), "OK", { duration: 4e3 });
      }
    });
  }
  /**
   * «№3 Матанализ, пн 14.04» — если есть хотя бы subjectName или дата.
   * Иначе fallback «Пара #123».
   */
  formatLesson(req) {
    const hasDetails = req.lessonNumber != null || req.subjectName || req.lessonDate;
    if (!hasDetails)
      return `\u041F\u0430\u0440\u0430 #${req.lessonId}`;
    const parts = [];
    if (req.lessonNumber != null)
      parts.push(`\u2116${req.lessonNumber}`);
    if (req.subjectName)
      parts.push(req.subjectName);
    const head = parts.join(" ").trim();
    const date = req.lessonDate ? this.formatRuDate(req.lessonDate) : "";
    return date ? `${head}, ${date}` : head || `\u041F\u0430\u0440\u0430 #${req.lessonId}`;
  }
  formatRuDate(iso) {
    const parsed = new Date(iso.length === 10 ? `${iso}T00:00:00` : iso);
    if (Number.isNaN(parsed.getTime()))
      return iso;
    const dow = this.ruDays[parsed.getDay()];
    const dd = String(parsed.getDate()).padStart(2, "0");
    const mm = String(parsed.getMonth() + 1).padStart(2, "0");
    return `${dow} ${dd}.${mm}`;
  }
  describeLoadError(err) {
    if (err.status === 403)
      return "\u041D\u0435\u0442 \u043F\u0440\u0430\u0432 \u043F\u0440\u043E\u0441\u043C\u043E\u0442\u0440\u0430 \u0441\u043F\u0438\u0441\u043A\u0430 \u0437\u0430\u043F\u0440\u043E\u0441\u043E\u0432.";
    if (err.status === 0)
      return "\u0421\u0435\u0440\u0432\u0435\u0440 \u043D\u0435\u0434\u043E\u0441\u0442\u0443\u043F\u0435\u043D. \u041F\u043E\u0432\u0442\u043E\u0440\u0438\u0442\u0435 \u043F\u043E\u0437\u0436\u0435.";
    return "\u041D\u0435 \u0443\u0434\u0430\u043B\u043E\u0441\u044C \u0437\u0430\u0433\u0440\u0443\u0437\u0438\u0442\u044C \u0441\u043F\u0438\u0441\u043E\u043A \u0437\u0430\u043F\u0440\u043E\u0441\u043E\u0432.";
  }
  describeDecisionError(err) {
    if (err.status === 403)
      return "\u041D\u0435\u0442 \u043F\u0440\u0430\u0432 \u043D\u0430 \u0440\u0435\u0448\u0435\u043D\u0438\u0435 \u044D\u0442\u043E\u0433\u043E \u0437\u0430\u043F\u0440\u043E\u0441\u0430.";
    if (err.status === 404)
      return "\u0417\u0430\u043F\u0440\u043E\u0441 \u043D\u0435 \u043D\u0430\u0439\u0434\u0435\u043D. \u041E\u0431\u043D\u043E\u0432\u0438\u0442\u0435 \u0441\u0442\u0440\u0430\u043D\u0438\u0446\u0443.";
    if (err.status === 400)
      return "\u041D\u0435\u043A\u043E\u0440\u0440\u0435\u043A\u0442\u043D\u043E\u0435 \u0442\u0435\u043B\u043E \u0437\u0430\u043F\u0440\u043E\u0441\u0430.";
    return "\u041D\u0435 \u0443\u0434\u0430\u043B\u043E\u0441\u044C \u043E\u0442\u043F\u0440\u0430\u0432\u0438\u0442\u044C \u0440\u0435\u0448\u0435\u043D\u0438\u0435. \u041F\u043E\u043F\u0440\u043E\u0431\u0443\u0439\u0442\u0435 \u0435\u0449\u0451 \u0440\u0430\u0437.";
  }
  static \u0275fac = function HeadmanLateCheckinComponent_Factory(__ngFactoryType__) {
    return new (__ngFactoryType__ || _HeadmanLateCheckinComponent)();
  };
  static \u0275cmp = /* @__PURE__ */ \u0275\u0275defineComponent({ type: _HeadmanLateCheckinComponent, selectors: [["app-headman-late-checkin"]], hostVars: 1, hostBindings: function HeadmanLateCheckinComponent_HostBindings(rf, ctx) {
    if (rf & 2) {
      \u0275\u0275syntheticHostProperty("@routeFade", void 0);
    }
  }, decls: 11, vars: 1, consts: [[1, "page-stack"], [1, "page-header"], [1, "page-eyebrow"], [1, "page-title"], [1, "page-card"], ["aria-hidden", "true", 1, "skeleton-row"], ["aria-live", "polite", 1, "sr-only"], ["role", "alert", 1, "page-error"], [1, "ph", "ph-warning-circle"], ["role", "status", "aria-live", "polite", 1, "page-empty"], [1, "page-empty__icon"], [1, "ph", "ph-clock-countdown"], [1, "page-empty__title"], [1, "page-empty__text"], [1, "page-card__header"], [1, "page-card__badge"], [1, "lcr-card"], [1, "lcr-card__head"], [1, "lcr-card__info"], [1, "lcr-card__student"], [1, "lcr-card__lesson"], [1, "lcr-card__meta"], [1, "lcr-card__actions"], ["type", "button", 1, "btn-brand", 3, "click", "disabled"], [1, "ph", "ph-check"], ["type", "button", 1, "btn-ghost", 3, "click", "disabled"], [1, "ph", "ph-x"]], template: function HeadmanLateCheckinComponent_Template(rf, ctx) {
    if (rf & 1) {
      \u0275\u0275elementStart(0, "div", 0)(1, "div", 1)(2, "div")(3, "span", 2);
      \u0275\u0275text(4, "\u0421\u0442\u0430\u0440\u043E\u0441\u0442\u0430\u0442");
      \u0275\u0275elementEnd();
      \u0275\u0275elementStart(5, "h1", 3);
      \u0275\u0275text(6, "\u0417\u0430\u043F\u0440\u043E\u0441\u044B \u043D\u0430 \u043E\u0442\u043C\u0435\u0442\u043A\u0443");
      \u0275\u0275elementEnd()()();
      \u0275\u0275template(7, HeadmanLateCheckinComponent_Conditional_7_Template, 5, 0, "div", 4)(8, HeadmanLateCheckinComponent_Conditional_8_Template, 4, 1, "div", 4)(9, HeadmanLateCheckinComponent_Conditional_9_Template, 8, 0, "div", 4)(10, HeadmanLateCheckinComponent_Conditional_10_Template, 8, 1, "section", 4);
      \u0275\u0275elementEnd();
    }
    if (rf & 2) {
      \u0275\u0275advance(7);
      \u0275\u0275conditional(ctx.loading() ? 7 : ctx.loadError() ? 8 : ctx.requests().length === 0 ? 9 : 10);
    }
  }, dependencies: [DatePipe, MatSnackBarModule], styles: ["\n\n.lcr-card[_ngcontent-%COMP%] {\n  border: 1px solid var(--border-subtle);\n  border-radius: var(--radius-lg);\n  padding: var(--space-4) var(--space-5);\n  margin-bottom: var(--space-3);\n  background: var(--bg-secondary);\n  transition: border-color var(--duration-base) var(--ease-out);\n}\n.lcr-card[_ngcontent-%COMP%]:hover {\n  border-color: var(--border-default);\n}\n.lcr-card__head[_ngcontent-%COMP%] {\n  display: flex;\n  justify-content: space-between;\n  align-items: baseline;\n  gap: var(--space-3);\n  flex-wrap: wrap;\n}\n.lcr-card__info[_ngcontent-%COMP%] {\n  display: flex;\n  flex-direction: column;\n  gap: 2px;\n}\n.lcr-card__student[_ngcontent-%COMP%] {\n  font-weight: 600;\n  color: var(--text-primary);\n}\n.lcr-card__lesson[_ngcontent-%COMP%] {\n  font-size: 0.875rem;\n  color: var(--text-secondary);\n}\n.lcr-card__meta[_ngcontent-%COMP%] {\n  font-family: var(--font-mono);\n  font-size: 0.8125rem;\n  color: var(--text-muted);\n}\n.lcr-card__actions[_ngcontent-%COMP%] {\n  display: flex;\n  gap: var(--space-2);\n  margin-top: var(--space-3);\n}\n/*# sourceMappingURL=headman-late-checkin.component.css.map */"], data: { animation: [
    trigger("routeFade", [
      transition(":enter", [
        style({ opacity: 0, transform: "translateY(8px)" }),
        animate("200ms cubic-bezier(0.16, 1, 0.3, 1)", style({ opacity: 1, transform: "translateY(0)" }))
      ])
    ])
  ] }, changeDetection: 0 });
};
(() => {
  (typeof ngDevMode === "undefined" || ngDevMode) && setClassMetadata(HeadmanLateCheckinComponent, [{
    type: Component,
    args: [{ selector: "app-headman-late-checkin", standalone: true, changeDetection: ChangeDetectionStrategy.OnPush, imports: [DatePipe, MatSnackBarModule], animations: [
      trigger("routeFade", [
        transition(":enter", [
          style({ opacity: 0, transform: "translateY(8px)" }),
          animate("200ms cubic-bezier(0.16, 1, 0.3, 1)", style({ opacity: 1, transform: "translateY(0)" }))
        ])
      ])
    ], host: { "[@routeFade]": "" }, template: `
    <div class="page-stack">
      <div class="page-header">
        <div>
          <span class="page-eyebrow">\u0421\u0442\u0430\u0440\u043E\u0441\u0442\u0430\u0442</span>
          <h1 class="page-title">\u0417\u0430\u043F\u0440\u043E\u0441\u044B \u043D\u0430 \u043E\u0442\u043C\u0435\u0442\u043A\u0443</h1>
        </div>
      </div>

      @if (loading()) {
        <div class="page-card">
          <div class="skeleton-row" aria-hidden="true"></div>
          <div class="skeleton-row" aria-hidden="true"></div>
          <span aria-live="polite" class="sr-only">\u0417\u0430\u0433\u0440\u0443\u0437\u043A\u0430...</span>
        </div>
      } @else if (loadError()) {
        <div class="page-card">
          <div class="page-error" role="alert">
            <i class="ph ph-warning-circle"></i>
            {{ loadError() }}
          </div>
        </div>
      } @else if (requests().length === 0) {
        <div class="page-card">
          <div class="page-empty" role="status" aria-live="polite">
            <div class="page-empty__icon"><i class="ph ph-clock-countdown"></i></div>
            <p class="page-empty__title">\u041D\u0435\u0442 \u0430\u043A\u0442\u0438\u0432\u043D\u044B\u0445 \u0437\u0430\u043F\u0440\u043E\u0441\u043E\u0432</p>
            <p class="page-empty__text">
              \u0417\u0430\u044F\u0432\u043A\u0438 \u043F\u043E\u044F\u0432\u044F\u0442\u0441\u044F \u0437\u0434\u0435\u0441\u044C \u0430\u0432\u0442\u043E\u043C\u0430\u0442\u0438\u0447\u0435\u0441\u043A\u0438 \u2014 \u043A\u043E\u0433\u0434\u0430 \u0441\u0442\u0443\u0434\u0435\u043D\u0442 \u0437\u0430\u043F\u0440\u043E\u0441\u0438\u0442 \u043F\u043E\u0434\u0442\u0432\u0435\u0440\u0436\u0434\u0435\u043D\u0438\u0435.
            </p>
          </div>
        </div>
      } @else {
        <section class="page-card">
          <div class="page-card__header">
            <h2>\u041D\u0430 \u0440\u0430\u0441\u0441\u043C\u043E\u0442\u0440\u0435\u043D\u0438\u0438</h2>
            <span class="page-card__badge">{{ requests().length }}</span>
          </div>

          @for (req of requests(); track req.id) {
            <article class="lcr-card">
              <header class="lcr-card__head">
                <div class="lcr-card__info">
                  <strong class="lcr-card__student">{{ req.studentName }}</strong>
                  <span class="lcr-card__lesson">{{ formatLesson(req) }}</span>
                </div>
                <span class="lcr-card__meta">
                  {{ req.createdAt | date: 'dd.MM.yyyy HH:mm' }}
                </span>
              </header>
              <div class="lcr-card__actions">
                <button
                  type="button"
                  class="btn-brand"
                  [disabled]="busyId() === req.id"
                  (click)="decide(req.id, true)"
                >
                  <i class="ph ph-check"></i> \u041F\u043E\u0434\u0442\u0432\u0435\u0440\u0434\u0438\u0442\u044C
                </button>
                <button
                  type="button"
                  class="btn-ghost"
                  [disabled]="busyId() === req.id"
                  (click)="decide(req.id, false)"
                >
                  <i class="ph ph-x"></i> \u041E\u0442\u043A\u043B\u043E\u043D\u0438\u0442\u044C
                </button>
              </div>
            </article>
          }
        </section>
      }
    </div>
  `, styles: ["/* angular:styles/component:css;ae9d4462d4ada2695eb1cb9eb6a8edce17c22c865e62e71bfd81bbb6af3994c0;C:/Users/maksd/IntelliJIDEA/rutcampustrack/frontends/web-panel/src/app/features/headman/late-checkin/headman-late-checkin.component.ts */\n.lcr-card {\n  border: 1px solid var(--border-subtle);\n  border-radius: var(--radius-lg);\n  padding: var(--space-4) var(--space-5);\n  margin-bottom: var(--space-3);\n  background: var(--bg-secondary);\n  transition: border-color var(--duration-base) var(--ease-out);\n}\n.lcr-card:hover {\n  border-color: var(--border-default);\n}\n.lcr-card__head {\n  display: flex;\n  justify-content: space-between;\n  align-items: baseline;\n  gap: var(--space-3);\n  flex-wrap: wrap;\n}\n.lcr-card__info {\n  display: flex;\n  flex-direction: column;\n  gap: 2px;\n}\n.lcr-card__student {\n  font-weight: 600;\n  color: var(--text-primary);\n}\n.lcr-card__lesson {\n  font-size: 0.875rem;\n  color: var(--text-secondary);\n}\n.lcr-card__meta {\n  font-family: var(--font-mono);\n  font-size: 0.8125rem;\n  color: var(--text-muted);\n}\n.lcr-card__actions {\n  display: flex;\n  gap: var(--space-2);\n  margin-top: var(--space-3);\n}\n/*# sourceMappingURL=headman-late-checkin.component.css.map */\n"] }]
  }], null, null);
})();
(() => {
  (typeof ngDevMode === "undefined" || ngDevMode) && \u0275setClassDebugInfo(HeadmanLateCheckinComponent, { className: "HeadmanLateCheckinComponent", filePath: "src/app/features/headman/late-checkin/headman-late-checkin.component.ts", lineNumber: 175 });
})();
export {
  HeadmanLateCheckinComponent
};
//# sourceMappingURL=chunk-H2RGRJRP.js.map
