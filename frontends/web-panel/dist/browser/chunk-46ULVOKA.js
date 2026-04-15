import {
  HeadmanApiService
} from "./chunk-ZC6IXG25.js";
import {
  MatSnackBar,
  MatSnackBarModule
} from "./chunk-5Q6RVIG4.js";
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
import "./chunk-GTDHNJL7.js";
import "./chunk-2WN7CIGJ.js";
import "./chunk-ZEX6QU7O.js";
import "./chunk-G4JX6AWT.js";
import "./chunk-YIJEORIR.js";
import "./chunk-FW2NJ6PM.js";
import {
  DefaultValueAccessor,
  FormsModule,
  NgControlStatus,
  NgModel
} from "./chunk-HWHV5SCS.js";
import "./chunk-FLE4DLW4.js";
import {
  CommonModule,
  DatePipe
} from "./chunk-CLRYRPPS.js";
import {
  ChangeDetectionStrategy,
  Component,
  computed,
  inject,
  setClassMetadata,
  signal,
  ɵsetClassDebugInfo,
  ɵɵadvance,
  ɵɵattribute,
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
  ɵɵrepeater,
  ɵɵrepeaterCreate,
  ɵɵresetView,
  ɵɵrestoreView,
  ɵɵsyntheticHostProperty,
  ɵɵtemplate,
  ɵɵtext,
  ɵɵtextInterpolate,
  ɵɵtextInterpolate1,
  ɵɵtextInterpolate2
} from "./chunk-4M4FDLBS.js";

// src/app/features/headman/excuses/excuse.types.ts
var EXCUSE_TYPE_LABELS = {
  illness: "\u0411\u043E\u043B\u0435\u0437\u043D\u044C",
  summons: "\u041F\u043E\u0432\u0435\u0441\u0442\u043A\u0430",
  university_order: "\u041F\u0440\u0438\u043A\u0430\u0437 \u0443\u043D\u0438\u0432\u0435\u0440\u0441\u0438\u0442\u0435\u0442\u0430",
  exemption: "\u041E\u0441\u0432\u043E\u0431\u043E\u0436\u0434\u0435\u043D\u0438\u0435",
  free_attendance: "\u0421\u0432\u043E\u0431\u043E\u0434\u043D\u043E\u0435 \u043F\u043E\u0441\u0435\u0449\u0435\u043D\u0438\u0435",
  other: "\u0414\u0440\u0443\u0433\u043E\u0435"
};
var EXCUSE_STATUS_LABELS = {
  submitted: "\u041D\u0430 \u0440\u0430\u0441\u0441\u043C\u043E\u0442\u0440\u0435\u043D\u0438\u0438",
  approved: "\u041E\u0434\u043E\u0431\u0440\u0435\u043D\u043E",
  rejected: "\u041E\u0442\u043A\u043B\u043E\u043D\u0435\u043D\u043E",
  draft: "\u0427\u0435\u0440\u043D\u043E\u0432\u0438\u043A"
};

// src/app/features/headman/excuses/headman-excuses.component.ts
var _forTrack0 = ($index, $item) => $item.id;
function HeadmanExcusesComponent_Conditional_7_Template(rf, ctx) {
  if (rf & 1) {
    \u0275\u0275elementStart(0, "div", 4)(1, "div", 5)(2, "div", 6);
    \u0275\u0275element(3, "i", 7);
    \u0275\u0275elementEnd();
    \u0275\u0275elementStart(4, "p", 8);
    \u0275\u0275text(5, "\u0413\u0440\u0443\u043F\u043F\u0430 \u043D\u0435 \u043E\u043F\u0440\u0435\u0434\u0435\u043B\u0435\u043D\u0430");
    \u0275\u0275elementEnd();
    \u0275\u0275elementStart(6, "p", 9);
    \u0275\u0275text(7, " \u0412\u0430\u0448 \u043F\u0440\u043E\u0444\u0438\u043B\u044C \u043D\u0435 \u043F\u0440\u0438\u0432\u044F\u0437\u0430\u043D \u043A \u0433\u0440\u0443\u043F\u043F\u0435 \u2014 \u0444\u0443\u043D\u043A\u0446\u0438\u044F \u043D\u0435\u0434\u043E\u0441\u0442\u0443\u043F\u043D\u043E. \u041E\u0431\u0440\u0430\u0442\u0438\u0442\u0435\u0441\u044C \u043A \u0430\u0434\u043C\u0438\u043D\u0438\u0441\u0442\u0440\u0430\u0442\u043E\u0440\u0443. ");
    \u0275\u0275elementEnd()()();
  }
}
function HeadmanExcusesComponent_Conditional_8_Template(rf, ctx) {
  if (rf & 1) {
    \u0275\u0275elementStart(0, "div", 4);
    \u0275\u0275element(1, "div", 10)(2, "div", 10);
    \u0275\u0275elementStart(3, "span", 11);
    \u0275\u0275text(4, "\u0417\u0430\u0433\u0440\u0443\u0437\u043A\u0430...");
    \u0275\u0275elementEnd()();
  }
}
function HeadmanExcusesComponent_Conditional_9_Template(rf, ctx) {
  if (rf & 1) {
    \u0275\u0275elementStart(0, "div", 4)(1, "div", 12);
    \u0275\u0275element(2, "i", 13);
    \u0275\u0275text(3);
    \u0275\u0275elementEnd()();
  }
  if (rf & 2) {
    const ctx_r0 = \u0275\u0275nextContext();
    \u0275\u0275advance(3);
    \u0275\u0275textInterpolate1(" ", ctx_r0.loadError(), " ");
  }
}
function HeadmanExcusesComponent_Conditional_10_Conditional_6_Template(rf, ctx) {
  if (rf & 1) {
    \u0275\u0275elementStart(0, "div", 16)(1, "p", 8);
    \u0275\u0275text(2, "\u041D\u0435\u0442 \u0437\u0430\u044F\u0432\u043E\u043A \u043D\u0430 \u0440\u0430\u0441\u0441\u043C\u043E\u0442\u0440\u0435\u043D\u0438\u0438");
    \u0275\u0275elementEnd();
    \u0275\u0275elementStart(3, "p", 9);
    \u0275\u0275text(4, "\u041D\u043E\u0432\u044B\u0435 \u0437\u0430\u044F\u0432\u043A\u0438 \u0441\u0442\u0443\u0434\u0435\u043D\u0442\u043E\u0432 \u043F\u043E\u044F\u0432\u044F\u0442\u0441\u044F \u0437\u0434\u0435\u0441\u044C \u0430\u0432\u0442\u043E\u043C\u0430\u0442\u0438\u0447\u0435\u0441\u043A\u0438.");
    \u0275\u0275elementEnd()();
  }
}
function HeadmanExcusesComponent_Conditional_10_Conditional_7_For_1_Conditional_10_Template(rf, ctx) {
  if (rf & 1) {
    \u0275\u0275elementStart(0, "p", 22);
    \u0275\u0275text(1);
    \u0275\u0275elementEnd();
  }
  if (rf & 2) {
    const ticket_r2 = \u0275\u0275nextContext().$implicit;
    \u0275\u0275advance();
    \u0275\u0275textInterpolate(ticket_r2.comment);
  }
}
function HeadmanExcusesComponent_Conditional_10_Conditional_7_For_1_Conditional_12_Template(rf, ctx) {
  if (rf & 1) {
    const _r3 = \u0275\u0275getCurrentView();
    \u0275\u0275elementStart(0, "button", 25);
    \u0275\u0275listener("click", function HeadmanExcusesComponent_Conditional_10_Conditional_7_For_1_Conditional_12_Template_button_click_0_listener() {
      \u0275\u0275restoreView(_r3);
      const ticket_r2 = \u0275\u0275nextContext().$implicit;
      const ctx_r0 = \u0275\u0275nextContext(3);
      return \u0275\u0275resetView(ctx_r0.approve(ticket_r2.id));
    });
    \u0275\u0275element(1, "i", 26);
    \u0275\u0275text(2, " \u041E\u0434\u043E\u0431\u0440\u0438\u0442\u044C ");
    \u0275\u0275elementEnd();
    \u0275\u0275elementStart(3, "button", 27);
    \u0275\u0275listener("click", function HeadmanExcusesComponent_Conditional_10_Conditional_7_For_1_Conditional_12_Template_button_click_3_listener() {
      \u0275\u0275restoreView(_r3);
      const ticket_r2 = \u0275\u0275nextContext().$implicit;
      const ctx_r0 = \u0275\u0275nextContext(3);
      return \u0275\u0275resetView(ctx_r0.startReject(ticket_r2.id));
    });
    \u0275\u0275element(4, "i", 28);
    \u0275\u0275text(5, " \u041E\u0442\u043A\u043B\u043E\u043D\u0438\u0442\u044C ");
    \u0275\u0275elementEnd();
  }
  if (rf & 2) {
    const ticket_r2 = \u0275\u0275nextContext().$implicit;
    const ctx_r0 = \u0275\u0275nextContext(3);
    \u0275\u0275property("disabled", ctx_r0.busyId() === ticket_r2.id);
    \u0275\u0275advance(3);
    \u0275\u0275property("disabled", ctx_r0.busyId() === ticket_r2.id);
  }
}
function HeadmanExcusesComponent_Conditional_10_Conditional_7_For_1_Conditional_13_Conditional_5_Template(rf, ctx) {
  if (rf & 1) {
    \u0275\u0275elementStart(0, "p", 31);
    \u0275\u0275text(1);
    \u0275\u0275elementEnd();
  }
  if (rf & 2) {
    const ctx_r0 = \u0275\u0275nextContext(5);
    \u0275\u0275advance();
    \u0275\u0275textInterpolate(ctx_r0.validationError());
  }
}
function HeadmanExcusesComponent_Conditional_10_Conditional_7_For_1_Conditional_13_Template(rf, ctx) {
  if (rf & 1) {
    const _r4 = \u0275\u0275getCurrentView();
    \u0275\u0275elementStart(0, "div", 24)(1, "label")(2, "span", 29);
    \u0275\u0275text(3, "\u041A\u043E\u043C\u043C\u0435\u043D\u0442\u0430\u0440\u0438\u0439 \u043A \u043E\u0442\u043A\u043B\u043E\u043D\u0435\u043D\u0438\u044E");
    \u0275\u0275elementEnd();
    \u0275\u0275elementStart(4, "input", 30);
    \u0275\u0275listener("ngModelChange", function HeadmanExcusesComponent_Conditional_10_Conditional_7_For_1_Conditional_13_Template_input_ngModelChange_4_listener($event) {
      \u0275\u0275restoreView(_r4);
      const ctx_r0 = \u0275\u0275nextContext(4);
      return \u0275\u0275resetView(ctx_r0.rejectComment.set($event));
    });
    \u0275\u0275elementEnd()();
    \u0275\u0275template(5, HeadmanExcusesComponent_Conditional_10_Conditional_7_For_1_Conditional_13_Conditional_5_Template, 2, 1, "p", 31);
    \u0275\u0275elementStart(6, "div", 32)(7, "button", 25);
    \u0275\u0275listener("click", function HeadmanExcusesComponent_Conditional_10_Conditional_7_For_1_Conditional_13_Template_button_click_7_listener() {
      \u0275\u0275restoreView(_r4);
      const ticket_r2 = \u0275\u0275nextContext().$implicit;
      const ctx_r0 = \u0275\u0275nextContext(3);
      return \u0275\u0275resetView(ctx_r0.confirmReject(ticket_r2.id));
    });
    \u0275\u0275text(8, " \u041F\u043E\u0434\u0442\u0432\u0435\u0440\u0434\u0438\u0442\u044C \u043E\u0442\u043A\u043B\u043E\u043D\u0435\u043D\u0438\u0435 ");
    \u0275\u0275elementEnd();
    \u0275\u0275elementStart(9, "button", 33);
    \u0275\u0275listener("click", function HeadmanExcusesComponent_Conditional_10_Conditional_7_For_1_Conditional_13_Template_button_click_9_listener() {
      \u0275\u0275restoreView(_r4);
      const ctx_r0 = \u0275\u0275nextContext(4);
      return \u0275\u0275resetView(ctx_r0.cancelReject());
    });
    \u0275\u0275text(10, " \u041E\u0442\u043C\u0435\u043D\u0430 ");
    \u0275\u0275elementEnd()()();
  }
  if (rf & 2) {
    const ticket_r2 = \u0275\u0275nextContext().$implicit;
    const ctx_r0 = \u0275\u0275nextContext(3);
    \u0275\u0275advance(4);
    \u0275\u0275property("ngModel", ctx_r0.rejectComment());
    \u0275\u0275attribute("aria-invalid", ctx_r0.validationError() ? "true" : null);
    \u0275\u0275advance();
    \u0275\u0275conditional(ctx_r0.validationError() ? 5 : -1);
    \u0275\u0275advance(2);
    \u0275\u0275property("disabled", ctx_r0.busyId() === ticket_r2.id);
  }
}
function HeadmanExcusesComponent_Conditional_10_Conditional_7_For_1_Template(rf, ctx) {
  if (rf & 1) {
    \u0275\u0275elementStart(0, "article", 17)(1, "header", 18)(2, "div")(3, "strong", 19);
    \u0275\u0275text(4);
    \u0275\u0275elementEnd();
    \u0275\u0275elementStart(5, "span", 20);
    \u0275\u0275text(6);
    \u0275\u0275elementEnd()();
    \u0275\u0275elementStart(7, "span", 21);
    \u0275\u0275text(8);
    \u0275\u0275pipe(9, "date");
    \u0275\u0275elementEnd()();
    \u0275\u0275template(10, HeadmanExcusesComponent_Conditional_10_Conditional_7_For_1_Conditional_10_Template, 2, 1, "p", 22);
    \u0275\u0275elementStart(11, "div", 23);
    \u0275\u0275template(12, HeadmanExcusesComponent_Conditional_10_Conditional_7_For_1_Conditional_12_Template, 6, 2)(13, HeadmanExcusesComponent_Conditional_10_Conditional_7_For_1_Conditional_13_Template, 11, 4, "div", 24);
    \u0275\u0275elementEnd()();
  }
  if (rf & 2) {
    const ticket_r2 = ctx.$implicit;
    const ctx_r0 = \u0275\u0275nextContext(3);
    \u0275\u0275advance(4);
    \u0275\u0275textInterpolate(ticket_r2.studentName);
    \u0275\u0275advance(2);
    \u0275\u0275textInterpolate(ctx_r0.typeLabel(ticket_r2.excuseType));
    \u0275\u0275advance(2);
    \u0275\u0275textInterpolate2(" ", ticket_r2.lessonIds.length, " \u0443\u0440\u043E\u043A(\u043E\u0432) \xB7 ", \u0275\u0275pipeBind2(9, 6, ticket_r2.createdAt, "dd.MM.yyyy HH:mm"), " ");
    \u0275\u0275advance(2);
    \u0275\u0275conditional(ticket_r2.comment ? 10 : -1);
    \u0275\u0275advance(2);
    \u0275\u0275conditional(ctx_r0.rejectingId() !== ticket_r2.id ? 12 : 13);
  }
}
function HeadmanExcusesComponent_Conditional_10_Conditional_7_Template(rf, ctx) {
  if (rf & 1) {
    \u0275\u0275repeaterCreate(0, HeadmanExcusesComponent_Conditional_10_Conditional_7_For_1_Template, 14, 9, "article", 17, _forTrack0);
  }
  if (rf & 2) {
    const ctx_r0 = \u0275\u0275nextContext(2);
    \u0275\u0275repeater(ctx_r0.pendingTickets());
  }
}
function HeadmanExcusesComponent_Conditional_10_Conditional_8_For_7_Conditional_9_Template(rf, ctx) {
  if (rf & 1) {
    \u0275\u0275elementStart(0, "p", 22);
    \u0275\u0275text(1);
    \u0275\u0275elementEnd();
  }
  if (rf & 2) {
    const ticket_r5 = \u0275\u0275nextContext().$implicit;
    \u0275\u0275advance();
    \u0275\u0275textInterpolate(ticket_r5.decisionComment);
  }
}
function HeadmanExcusesComponent_Conditional_10_Conditional_8_For_7_Template(rf, ctx) {
  if (rf & 1) {
    \u0275\u0275elementStart(0, "article", 34)(1, "header", 18)(2, "div")(3, "strong", 19);
    \u0275\u0275text(4);
    \u0275\u0275elementEnd();
    \u0275\u0275elementStart(5, "span", 20);
    \u0275\u0275text(6);
    \u0275\u0275elementEnd()();
    \u0275\u0275elementStart(7, "span", 35);
    \u0275\u0275text(8);
    \u0275\u0275elementEnd()();
    \u0275\u0275template(9, HeadmanExcusesComponent_Conditional_10_Conditional_8_For_7_Conditional_9_Template, 2, 1, "p", 22);
    \u0275\u0275elementEnd();
  }
  if (rf & 2) {
    const ticket_r5 = ctx.$implicit;
    const ctx_r0 = \u0275\u0275nextContext(3);
    \u0275\u0275advance(4);
    \u0275\u0275textInterpolate(ticket_r5.studentName);
    \u0275\u0275advance(2);
    \u0275\u0275textInterpolate(ctx_r0.typeLabel(ticket_r5.excuseType));
    \u0275\u0275advance();
    \u0275\u0275classProp("excuse-card__status--approved", ticket_r5.status === "approved")("excuse-card__status--rejected", ticket_r5.status === "rejected");
    \u0275\u0275advance();
    \u0275\u0275textInterpolate1(" ", ctx_r0.statusLabel(ticket_r5.status), " ");
    \u0275\u0275advance();
    \u0275\u0275conditional(ticket_r5.decisionComment ? 9 : -1);
  }
}
function HeadmanExcusesComponent_Conditional_10_Conditional_8_Template(rf, ctx) {
  if (rf & 1) {
    \u0275\u0275elementStart(0, "section", 4)(1, "div", 14)(2, "h2");
    \u0275\u0275text(3, "\u0420\u0435\u0448\u0435\u043D\u0438\u044F");
    \u0275\u0275elementEnd();
    \u0275\u0275elementStart(4, "span", 15);
    \u0275\u0275text(5);
    \u0275\u0275elementEnd()();
    \u0275\u0275repeaterCreate(6, HeadmanExcusesComponent_Conditional_10_Conditional_8_For_7_Template, 10, 8, "article", 34, _forTrack0);
    \u0275\u0275elementEnd();
  }
  if (rf & 2) {
    const ctx_r0 = \u0275\u0275nextContext(2);
    \u0275\u0275advance(5);
    \u0275\u0275textInterpolate(ctx_r0.resolvedTickets().length);
    \u0275\u0275advance();
    \u0275\u0275repeater(ctx_r0.resolvedTickets());
  }
}
function HeadmanExcusesComponent_Conditional_10_Template(rf, ctx) {
  if (rf & 1) {
    \u0275\u0275elementStart(0, "section", 4)(1, "div", 14)(2, "h2");
    \u0275\u0275text(3, "\u041D\u0430 \u0440\u0430\u0441\u0441\u043C\u043E\u0442\u0440\u0435\u043D\u0438\u0438");
    \u0275\u0275elementEnd();
    \u0275\u0275elementStart(4, "span", 15);
    \u0275\u0275text(5);
    \u0275\u0275elementEnd()();
    \u0275\u0275template(6, HeadmanExcusesComponent_Conditional_10_Conditional_6_Template, 5, 0, "div", 16)(7, HeadmanExcusesComponent_Conditional_10_Conditional_7_Template, 2, 0);
    \u0275\u0275elementEnd();
    \u0275\u0275template(8, HeadmanExcusesComponent_Conditional_10_Conditional_8_Template, 8, 1, "section", 4);
  }
  if (rf & 2) {
    const ctx_r0 = \u0275\u0275nextContext();
    \u0275\u0275advance(5);
    \u0275\u0275textInterpolate(ctx_r0.pendingTickets().length);
    \u0275\u0275advance();
    \u0275\u0275conditional(ctx_r0.pendingTickets().length === 0 ? 6 : 7);
    \u0275\u0275advance(2);
    \u0275\u0275conditional(ctx_r0.resolvedTickets().length > 0 ? 8 : -1);
  }
}
var HeadmanExcusesComponent = class _HeadmanExcusesComponent {
  headmanApi = inject(HeadmanApiService);
  auth = inject(AuthService);
  snackBar = inject(MatSnackBar);
  loading = signal(false);
  loadError = signal(null);
  tickets = signal([]);
  /** id of the ticket for which the inline reject form is open. */
  rejectingId = signal(null);
  rejectComment = signal("");
  validationError = signal(null);
  /** id of the ticket currently hitting the network (disables its buttons). */
  busyId = signal(null);
  pendingTickets = computed(() => this.tickets().filter((t) => t.status === "submitted"));
  resolvedTickets = computed(() => this.tickets().filter((t) => t.status !== "submitted"));
  noGroup = computed(() => this.auth.currentUser()?.groupId == null);
  ngOnInit() {
    this.loadTickets();
  }
  loadTickets() {
    const groupId = this.auth.currentUser()?.groupId;
    if (groupId == null) {
      this.tickets.set([]);
      return;
    }
    this.loading.set(true);
    this.loadError.set(null);
    this.headmanApi.getGroupExcuses(groupId).subscribe({
      next: (list) => {
        this.tickets.set(list);
        this.loading.set(false);
      },
      error: (err) => {
        this.loading.set(false);
        this.loadError.set(err.status === 403 ? "\u0414\u043E\u0441\u0442\u0443\u043F \u0437\u0430\u043F\u0440\u0435\u0449\u0451\u043D \u2014 \u0442\u043E\u043B\u044C\u043A\u043E \u0441\u0442\u0430\u0440\u043E\u0441\u0442\u0430 \u0433\u0440\u0443\u043F\u043F\u044B \u043C\u043E\u0436\u0435\u0442 \u043F\u0440\u043E\u0441\u043C\u0430\u0442\u0440\u0438\u0432\u0430\u0442\u044C \u0437\u0430\u044F\u0432\u043A\u0438." : "\u041D\u0435 \u0443\u0434\u0430\u043B\u043E\u0441\u044C \u0437\u0430\u0433\u0440\u0443\u0437\u0438\u0442\u044C \u0437\u0430\u044F\u0432\u043A\u0438. \u041F\u043E\u043F\u0440\u043E\u0431\u0443\u0439\u0442\u0435 \u043F\u043E\u0437\u0436\u0435.");
      }
    });
  }
  approve(id) {
    this.busyId.set(id);
    this.headmanApi.approveExcuse(id, null).subscribe({
      next: () => {
        this.busyId.set(null);
        this.snackBar.open("\u0417\u0430\u044F\u0432\u043A\u0430 \u043E\u0434\u043E\u0431\u0440\u0435\u043D\u0430", "OK", { duration: 3e3 });
        this.loadTickets();
      },
      error: (err) => {
        this.busyId.set(null);
        this.snackBar.open(this.errorMessage(err, "approve"), "OK", { duration: 5e3 });
      }
    });
  }
  startReject(id) {
    this.rejectingId.set(id);
    this.rejectComment.set("");
    this.validationError.set(null);
  }
  cancelReject() {
    this.rejectingId.set(null);
    this.rejectComment.set("");
    this.validationError.set(null);
  }
  confirmReject(id) {
    const comment = this.rejectComment().trim();
    if (!comment) {
      this.validationError.set("\u0412\u0432\u0435\u0434\u0438\u0442\u0435 \u043A\u043E\u043C\u043C\u0435\u043D\u0442\u0430\u0440\u0438\u0439 \u0434\u043B\u044F \u043E\u0442\u043A\u043B\u043E\u043D\u0435\u043D\u0438\u044F");
      return;
    }
    this.validationError.set(null);
    this.busyId.set(id);
    this.headmanApi.rejectExcuse(id, comment).subscribe({
      next: () => {
        this.busyId.set(null);
        this.rejectingId.set(null);
        this.rejectComment.set("");
        this.snackBar.open("\u0417\u0430\u044F\u0432\u043A\u0430 \u043E\u0442\u043A\u043B\u043E\u043D\u0435\u043D\u0430", "OK", { duration: 3e3 });
        this.loadTickets();
      },
      error: (err) => {
        this.busyId.set(null);
        this.snackBar.open(this.errorMessage(err, "reject"), "OK", { duration: 5e3 });
      }
    });
  }
  typeLabel(type) {
    return EXCUSE_TYPE_LABELS[type] ?? type;
  }
  statusLabel(status) {
    return EXCUSE_STATUS_LABELS[status] ?? status;
  }
  errorMessage(err, action) {
    if (err.status === 409) {
      return action === "approve" ? "\u041D\u0435\u0432\u043E\u0437\u043C\u043E\u0436\u043D\u043E \u043E\u0434\u043E\u0431\u0440\u0438\u0442\u044C: \u0440\u0435\u0448\u0435\u043D\u0438\u0435 \u0443\u0436\u0435 \u043F\u0440\u0438\u043D\u044F\u0442\u043E \u0438\u043B\u0438 \u0437\u0430\u044F\u0432\u043A\u0430 \u043D\u0435\u0434\u043E\u0441\u0442\u0443\u043F\u043D\u0430 \u0434\u043B\u044F \u043E\u0434\u043E\u0431\u0440\u0435\u043D\u0438\u044F." : "\u041D\u0435\u0432\u043E\u0437\u043C\u043E\u0436\u043D\u043E \u043E\u0442\u043A\u043B\u043E\u043D\u0438\u0442\u044C: \u0440\u0435\u0448\u0435\u043D\u0438\u0435 \u0443\u0436\u0435 \u043F\u0440\u0438\u043D\u044F\u0442\u043E \u0438\u043B\u0438 \u0437\u0430\u044F\u0432\u043A\u0430 \u043D\u0435\u0434\u043E\u0441\u0442\u0443\u043F\u043D\u0430 \u0434\u043B\u044F \u043E\u0442\u043A\u043B\u043E\u043D\u0435\u043D\u0438\u044F.";
    }
    if (err.status === 403) {
      return "\u041D\u0435\u0434\u043E\u0441\u0442\u0430\u0442\u043E\u0447\u043D\u043E \u043F\u0440\u0430\u0432 \u2014 \u044D\u0442\u0430 \u0437\u0430\u044F\u0432\u043A\u0430 \u043F\u0440\u0438\u043D\u0430\u0434\u043B\u0435\u0436\u0438\u0442 \u0434\u0440\u0443\u0433\u043E\u0439 \u0433\u0440\u0443\u043F\u043F\u0435.";
    }
    if (err.status === 404) {
      return "\u0417\u0430\u044F\u0432\u043A\u0430 \u043D\u0435 \u043D\u0430\u0439\u0434\u0435\u043D\u0430 \u2014 \u0432\u043E\u0437\u043C\u043E\u0436\u043D\u043E, \u043E\u043D\u0430 \u0431\u044B\u043B\u0430 \u0443\u0434\u0430\u043B\u0435\u043D\u0430.";
    }
    return action === "approve" ? "\u041D\u0435 \u0443\u0434\u0430\u043B\u043E\u0441\u044C \u043E\u0434\u043E\u0431\u0440\u0438\u0442\u044C \u0437\u0430\u044F\u0432\u043A\u0443. \u041F\u043E\u043F\u0440\u043E\u0431\u0443\u0439\u0442\u0435 \u043F\u043E\u0437\u0436\u0435." : "\u041D\u0435 \u0443\u0434\u0430\u043B\u043E\u0441\u044C \u043E\u0442\u043A\u043B\u043E\u043D\u0438\u0442\u044C \u0437\u0430\u044F\u0432\u043A\u0443. \u041F\u043E\u043F\u0440\u043E\u0431\u0443\u0439\u0442\u0435 \u043F\u043E\u0437\u0436\u0435.";
  }
  static \u0275fac = function HeadmanExcusesComponent_Factory(__ngFactoryType__) {
    return new (__ngFactoryType__ || _HeadmanExcusesComponent)();
  };
  static \u0275cmp = /* @__PURE__ */ \u0275\u0275defineComponent({ type: _HeadmanExcusesComponent, selectors: [["app-headman-excuses"]], hostVars: 1, hostBindings: function HeadmanExcusesComponent_HostBindings(rf, ctx) {
    if (rf & 2) {
      \u0275\u0275syntheticHostProperty("@routeFade", void 0);
    }
  }, decls: 11, vars: 1, consts: [[1, "page-stack"], [1, "page-header"], [1, "page-eyebrow"], [1, "page-title"], [1, "page-card"], ["role", "status", 1, "page-empty"], [1, "page-empty__icon"], [1, "ph", "ph-users"], [1, "page-empty__title"], [1, "page-empty__text"], ["aria-hidden", "true", 1, "skeleton-row"], ["aria-live", "polite", 1, "sr-only"], ["role", "alert", 1, "page-error"], [1, "ph", "ph-warning-circle"], [1, "page-card__header"], [1, "page-card__badge"], [1, "page-empty"], [1, "excuse-card"], [1, "excuse-card__head"], [1, "excuse-card__student"], [1, "excuse-card__type"], [1, "excuse-card__meta"], [1, "excuse-card__comment"], [1, "excuse-card__actions"], [1, "excuse-card__reject"], ["type", "button", 1, "btn-brand", 3, "click", "disabled"], [1, "ph", "ph-check"], ["type", "button", 1, "btn-ghost", 3, "click", "disabled"], [1, "ph", "ph-x"], [1, "sr-only"], ["type", "text", "placeholder", "\u0423\u043A\u0430\u0436\u0438\u0442\u0435 \u043F\u0440\u0438\u0447\u0438\u043D\u0443 \u043E\u0442\u043A\u043B\u043E\u043D\u0435\u043D\u0438\u044F", 1, "form-input", 3, "ngModelChange", "ngModel"], ["role", "alert", 1, "form-error"], [1, "excuse-card__reject-actions"], ["type", "button", 1, "btn-ghost", 3, "click"], [1, "excuse-card", "excuse-card--resolved"], [1, "excuse-card__status"]], template: function HeadmanExcusesComponent_Template(rf, ctx) {
    if (rf & 1) {
      \u0275\u0275elementStart(0, "div", 0)(1, "div", 1)(2, "div")(3, "span", 2);
      \u0275\u0275text(4, "\u0421\u0442\u0430\u0440\u043E\u0441\u0442\u0430\u0442");
      \u0275\u0275elementEnd();
      \u0275\u0275elementStart(5, "h1", 3);
      \u0275\u0275text(6, "\u041F\u0440\u043E\u043F\u0443\u0441\u043A\u0438 \u0433\u0440\u0443\u043F\u043F\u044B");
      \u0275\u0275elementEnd()()();
      \u0275\u0275template(7, HeadmanExcusesComponent_Conditional_7_Template, 8, 0, "div", 4)(8, HeadmanExcusesComponent_Conditional_8_Template, 5, 0, "div", 4)(9, HeadmanExcusesComponent_Conditional_9_Template, 4, 1, "div", 4)(10, HeadmanExcusesComponent_Conditional_10_Template, 9, 3);
      \u0275\u0275elementEnd();
    }
    if (rf & 2) {
      \u0275\u0275advance(7);
      \u0275\u0275conditional(ctx.noGroup() ? 7 : ctx.loading() ? 8 : ctx.loadError() ? 9 : 10);
    }
  }, dependencies: [CommonModule, DatePipe, FormsModule, DefaultValueAccessor, NgControlStatus, NgModel, MatSnackBarModule], styles: ["\n\n.excuse-card[_ngcontent-%COMP%] {\n  border: 1px solid var(--color-border, #e5e7eb);\n  border-radius: 8px;\n  padding: 12px 16px;\n  margin-bottom: 10px;\n  background: var(--color-surface, #fff);\n}\n.excuse-card--resolved[_ngcontent-%COMP%] {\n  opacity: 0.85;\n}\n.excuse-card__head[_ngcontent-%COMP%] {\n  display: flex;\n  justify-content: space-between;\n  align-items: baseline;\n  gap: 12px;\n  flex-wrap: wrap;\n}\n.excuse-card__student[_ngcontent-%COMP%] {\n  font-weight: 600;\n  margin-right: 10px;\n}\n.excuse-card__type[_ngcontent-%COMP%] {\n  font-size: 0.9em;\n  color: var(--color-text-muted, #64748b);\n}\n.excuse-card__meta[_ngcontent-%COMP%] {\n  font-size: 0.85em;\n  color: var(--color-text-muted, #64748b);\n}\n.excuse-card__comment[_ngcontent-%COMP%] {\n  margin: 8px 0;\n  color: var(--color-text, #111827);\n}\n.excuse-card__actions[_ngcontent-%COMP%] {\n  display: flex;\n  gap: 8px;\n  margin-top: 8px;\n  flex-wrap: wrap;\n}\n.excuse-card__reject[_ngcontent-%COMP%] {\n  flex: 1;\n  display: flex;\n  flex-direction: column;\n  gap: 6px;\n}\n.excuse-card__reject-actions[_ngcontent-%COMP%] {\n  display: flex;\n  gap: 8px;\n}\n.excuse-card__status[_ngcontent-%COMP%] {\n  font-size: 0.85em;\n  padding: 2px 8px;\n  border-radius: 999px;\n  background: var(--color-surface-2, #f1f5f9);\n}\n.excuse-card__status--approved[_ngcontent-%COMP%] {\n  background: #dcfce7;\n  color: #14532d;\n}\n.excuse-card__status--rejected[_ngcontent-%COMP%] {\n  background: #fee2e2;\n  color: #7f1d1d;\n}\n.form-input[_ngcontent-%COMP%] {\n  width: 100%;\n  padding: 6px 10px;\n  border: 1px solid var(--color-border, #e5e7eb);\n  border-radius: 6px;\n}\n.form-error[_ngcontent-%COMP%] {\n  color: #b91c1c;\n  font-size: 0.85em;\n  margin: 0;\n}\n.sr-only[_ngcontent-%COMP%] {\n  position: absolute;\n  width: 1px;\n  height: 1px;\n  padding: 0;\n  margin: -1px;\n  overflow: hidden;\n  clip: rect(0, 0, 0, 0);\n  white-space: nowrap;\n  border: 0;\n}\n.page-card__badge[_ngcontent-%COMP%] {\n  background: var(--color-surface-2, #f1f5f9);\n  padding: 2px 8px;\n  border-radius: 999px;\n  font-size: 0.85em;\n  margin-left: 8px;\n}\n/*# sourceMappingURL=headman-excuses.component.css.map */"], data: { animation: [
    trigger("routeFade", [
      transition(":enter", [
        style({ opacity: 0, transform: "translateY(8px)" }),
        animate("200ms cubic-bezier(0.16, 1, 0.3, 1)", style({ opacity: 1, transform: "translateY(0)" }))
      ])
    ])
  ] }, changeDetection: 0 });
};
(() => {
  (typeof ngDevMode === "undefined" || ngDevMode) && setClassMetadata(HeadmanExcusesComponent, [{
    type: Component,
    args: [{ selector: "app-headman-excuses", standalone: true, changeDetection: ChangeDetectionStrategy.OnPush, imports: [CommonModule, FormsModule, MatSnackBarModule], animations: [
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
          <h1 class="page-title">\u041F\u0440\u043E\u043F\u0443\u0441\u043A\u0438 \u0433\u0440\u0443\u043F\u043F\u044B</h1>
        </div>
      </div>

      @if (noGroup()) {
        <div class="page-card">
          <div class="page-empty" role="status">
            <div class="page-empty__icon"><i class="ph ph-users"></i></div>
            <p class="page-empty__title">\u0413\u0440\u0443\u043F\u043F\u0430 \u043D\u0435 \u043E\u043F\u0440\u0435\u0434\u0435\u043B\u0435\u043D\u0430</p>
            <p class="page-empty__text">
              \u0412\u0430\u0448 \u043F\u0440\u043E\u0444\u0438\u043B\u044C \u043D\u0435 \u043F\u0440\u0438\u0432\u044F\u0437\u0430\u043D \u043A \u0433\u0440\u0443\u043F\u043F\u0435 \u2014 \u0444\u0443\u043D\u043A\u0446\u0438\u044F \u043D\u0435\u0434\u043E\u0441\u0442\u0443\u043F\u043D\u043E. \u041E\u0431\u0440\u0430\u0442\u0438\u0442\u0435\u0441\u044C \u043A \u0430\u0434\u043C\u0438\u043D\u0438\u0441\u0442\u0440\u0430\u0442\u043E\u0440\u0443.
            </p>
          </div>
        </div>
      } @else if (loading()) {
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
      } @else {
        <!-- Pending section -->
        <section class="page-card">
          <div class="page-card__header">
            <h2>\u041D\u0430 \u0440\u0430\u0441\u0441\u043C\u043E\u0442\u0440\u0435\u043D\u0438\u0438</h2>
            <span class="page-card__badge">{{ pendingTickets().length }}</span>
          </div>

          @if (pendingTickets().length === 0) {
            <div class="page-empty">
              <p class="page-empty__title">\u041D\u0435\u0442 \u0437\u0430\u044F\u0432\u043E\u043A \u043D\u0430 \u0440\u0430\u0441\u0441\u043C\u043E\u0442\u0440\u0435\u043D\u0438\u0438</p>
              <p class="page-empty__text">\u041D\u043E\u0432\u044B\u0435 \u0437\u0430\u044F\u0432\u043A\u0438 \u0441\u0442\u0443\u0434\u0435\u043D\u0442\u043E\u0432 \u043F\u043E\u044F\u0432\u044F\u0442\u0441\u044F \u0437\u0434\u0435\u0441\u044C \u0430\u0432\u0442\u043E\u043C\u0430\u0442\u0438\u0447\u0435\u0441\u043A\u0438.</p>
            </div>
          } @else {
            @for (ticket of pendingTickets(); track ticket.id) {
              <article class="excuse-card">
                <header class="excuse-card__head">
                  <div>
                    <strong class="excuse-card__student">{{ ticket.studentName }}</strong>
                    <span class="excuse-card__type">{{ typeLabel(ticket.excuseType) }}</span>
                  </div>
                  <span class="excuse-card__meta">
                    {{ ticket.lessonIds.length }} \u0443\u0440\u043E\u043A(\u043E\u0432) \xB7 {{ ticket.createdAt | date:'dd.MM.yyyy HH:mm' }}
                  </span>
                </header>
                @if (ticket.comment) {
                  <p class="excuse-card__comment">{{ ticket.comment }}</p>
                }
                <div class="excuse-card__actions">
                  @if (rejectingId() !== ticket.id) {
                    <button
                      type="button"
                      class="btn-brand"
                      [disabled]="busyId() === ticket.id"
                      (click)="approve(ticket.id)"
                    >
                      <i class="ph ph-check"></i> \u041E\u0434\u043E\u0431\u0440\u0438\u0442\u044C
                    </button>
                    <button
                      type="button"
                      class="btn-ghost"
                      [disabled]="busyId() === ticket.id"
                      (click)="startReject(ticket.id)"
                    >
                      <i class="ph ph-x"></i> \u041E\u0442\u043A\u043B\u043E\u043D\u0438\u0442\u044C
                    </button>
                  } @else {
                    <div class="excuse-card__reject">
                      <label>
                        <span class="sr-only">\u041A\u043E\u043C\u043C\u0435\u043D\u0442\u0430\u0440\u0438\u0439 \u043A \u043E\u0442\u043A\u043B\u043E\u043D\u0435\u043D\u0438\u044E</span>
                        <input
                          type="text"
                          class="form-input"
                          placeholder="\u0423\u043A\u0430\u0436\u0438\u0442\u0435 \u043F\u0440\u0438\u0447\u0438\u043D\u0443 \u043E\u0442\u043A\u043B\u043E\u043D\u0435\u043D\u0438\u044F"
                          [ngModel]="rejectComment()"
                          (ngModelChange)="rejectComment.set($event)"
                          [attr.aria-invalid]="validationError() ? 'true' : null"
                        />
                      </label>
                      @if (validationError()) {
                        <p class="form-error" role="alert">{{ validationError() }}</p>
                      }
                      <div class="excuse-card__reject-actions">
                        <button
                          type="button"
                          class="btn-brand"
                          [disabled]="busyId() === ticket.id"
                          (click)="confirmReject(ticket.id)"
                        >
                          \u041F\u043E\u0434\u0442\u0432\u0435\u0440\u0434\u0438\u0442\u044C \u043E\u0442\u043A\u043B\u043E\u043D\u0435\u043D\u0438\u0435
                        </button>
                        <button type="button" class="btn-ghost" (click)="cancelReject()">
                          \u041E\u0442\u043C\u0435\u043D\u0430
                        </button>
                      </div>
                    </div>
                  }
                </div>
              </article>
            }
          }
        </section>

        <!-- Resolved section -->
        @if (resolvedTickets().length > 0) {
          <section class="page-card">
            <div class="page-card__header">
              <h2>\u0420\u0435\u0448\u0435\u043D\u0438\u044F</h2>
              <span class="page-card__badge">{{ resolvedTickets().length }}</span>
            </div>
            @for (ticket of resolvedTickets(); track ticket.id) {
              <article class="excuse-card excuse-card--resolved">
                <header class="excuse-card__head">
                  <div>
                    <strong class="excuse-card__student">{{ ticket.studentName }}</strong>
                    <span class="excuse-card__type">{{ typeLabel(ticket.excuseType) }}</span>
                  </div>
                  <span
                    class="excuse-card__status"
                    [class.excuse-card__status--approved]="ticket.status === 'approved'"
                    [class.excuse-card__status--rejected]="ticket.status === 'rejected'"
                  >
                    {{ statusLabel(ticket.status) }}
                  </span>
                </header>
                @if (ticket.decisionComment) {
                  <p class="excuse-card__comment">{{ ticket.decisionComment }}</p>
                }
              </article>
            }
          </section>
        }
      }
    </div>
  `, styles: ["/* angular:styles/component:css;df8aefea522135e8a40e8bb99a03301ca23b1d4ee70899070636987bf8d7866a;C:/Users/maksd/IntelliJIDEA/rutcampustrack/frontends/web-panel/src/app/features/headman/excuses/headman-excuses.component.ts */\n.excuse-card {\n  border: 1px solid var(--color-border, #e5e7eb);\n  border-radius: 8px;\n  padding: 12px 16px;\n  margin-bottom: 10px;\n  background: var(--color-surface, #fff);\n}\n.excuse-card--resolved {\n  opacity: 0.85;\n}\n.excuse-card__head {\n  display: flex;\n  justify-content: space-between;\n  align-items: baseline;\n  gap: 12px;\n  flex-wrap: wrap;\n}\n.excuse-card__student {\n  font-weight: 600;\n  margin-right: 10px;\n}\n.excuse-card__type {\n  font-size: 0.9em;\n  color: var(--color-text-muted, #64748b);\n}\n.excuse-card__meta {\n  font-size: 0.85em;\n  color: var(--color-text-muted, #64748b);\n}\n.excuse-card__comment {\n  margin: 8px 0;\n  color: var(--color-text, #111827);\n}\n.excuse-card__actions {\n  display: flex;\n  gap: 8px;\n  margin-top: 8px;\n  flex-wrap: wrap;\n}\n.excuse-card__reject {\n  flex: 1;\n  display: flex;\n  flex-direction: column;\n  gap: 6px;\n}\n.excuse-card__reject-actions {\n  display: flex;\n  gap: 8px;\n}\n.excuse-card__status {\n  font-size: 0.85em;\n  padding: 2px 8px;\n  border-radius: 999px;\n  background: var(--color-surface-2, #f1f5f9);\n}\n.excuse-card__status--approved {\n  background: #dcfce7;\n  color: #14532d;\n}\n.excuse-card__status--rejected {\n  background: #fee2e2;\n  color: #7f1d1d;\n}\n.form-input {\n  width: 100%;\n  padding: 6px 10px;\n  border: 1px solid var(--color-border, #e5e7eb);\n  border-radius: 6px;\n}\n.form-error {\n  color: #b91c1c;\n  font-size: 0.85em;\n  margin: 0;\n}\n.sr-only {\n  position: absolute;\n  width: 1px;\n  height: 1px;\n  padding: 0;\n  margin: -1px;\n  overflow: hidden;\n  clip: rect(0, 0, 0, 0);\n  white-space: nowrap;\n  border: 0;\n}\n.page-card__badge {\n  background: var(--color-surface-2, #f1f5f9);\n  padding: 2px 8px;\n  border-radius: 999px;\n  font-size: 0.85em;\n  margin-left: 8px;\n}\n/*# sourceMappingURL=headman-excuses.component.css.map */\n"] }]
  }], null, null);
})();
(() => {
  (typeof ngDevMode === "undefined" || ngDevMode) && \u0275setClassDebugInfo(HeadmanExcusesComponent, { className: "HeadmanExcusesComponent", filePath: "src/app/features/headman/excuses/headman-excuses.component.ts", lineNumber: 273 });
})();
export {
  HeadmanExcusesComponent
};
//# sourceMappingURL=chunk-46ULVOKA.js.map
