import {
  StudentStompService
} from "./chunk-YQ3GL6QE.js";
import {
  takeUntilDestroyed
} from "./chunk-5AGNK6YK.js";
import {
  StudentNotificationBadgeService
} from "./chunk-5SWBTVWA.js";
import "./chunk-3OOB2NIX.js";
import {
  animate,
  style,
  transition,
  trigger
} from "./chunk-JKCQIGK6.js";
import {
  CommonModule,
  NgClass
} from "./chunk-L2FQVI4C.js";
import {
  ChangeDetectionStrategy,
  Component,
  DestroyRef,
  Input,
  __spreadProps,
  __spreadValues,
  computed,
  inject,
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
  ɵɵnextContext,
  ɵɵproperty,
  ɵɵrepeater,
  ɵɵrepeaterCreate,
  ɵɵtemplate,
  ɵɵtext,
  ɵɵtextInterpolate
} from "./chunk-WGFJ2PWY.js";

// src/app/features/student/notifications/notification-item/notification-item.component.ts
var NotificationItemComponent = class _NotificationItemComponent {
  item;
  get heading() {
    switch (this.item.type) {
      case "lesson.started":
        return "\u041F\u0430\u0440\u0430 \u043D\u0430\u0447\u0430\u043B\u0430\u0441\u044C";
      case "lesson.cancelled":
        return "\u041F\u0430\u0440\u0430 \u043E\u0442\u043C\u0435\u043D\u0435\u043D\u0430";
      case "homework.published":
        return "\u041D\u043E\u0432\u043E\u0435 \u0437\u0430\u0434\u0430\u043D\u0438\u0435";
      case "homework.updated":
        return "\u0417\u0430\u0434\u0430\u043D\u0438\u0435 \u043E\u0431\u043D\u043E\u0432\u043B\u0435\u043D\u043E";
      case "attendance.marked":
        return "\u041F\u043E\u0441\u0435\u0449\u0430\u0435\u043C\u043E\u0441\u0442\u044C \u043F\u043E\u0434\u0442\u0432\u0435\u0440\u0436\u0434\u0435\u043D\u0430";
      default:
        return "\u0423\u0432\u0435\u0434\u043E\u043C\u043B\u0435\u043D\u0438\u0435";
    }
  }
  get iconClass() {
    switch (this.item.type) {
      case "lesson.started":
        return "ph-play-circle ph-fill";
      case "lesson.cancelled":
        return "ph-x-circle ph-fill";
      case "homework.published":
        return "ph-notebook ph-fill";
      case "homework.updated":
        return "ph-pencil-simple ph-fill";
      case "attendance.marked":
        return "ph-check-circle ph-fill";
      default:
        return "ph-bell";
    }
  }
  get iconColorClass() {
    switch (this.item.type) {
      case "lesson.started":
        return "icon-primary";
      case "lesson.cancelled":
        return "icon-info";
      case "homework.published":
        return "icon-secondary";
      case "homework.updated":
        return "icon-warning";
      case "attendance.marked":
        return "icon-primary";
      default:
        return "icon-muted";
    }
  }
  get bodyText() {
    const p = this.item.payload;
    const subjectName = p["subject_name"] ?? p["subjectName"] ?? "\u041F\u0430\u0440\u0430";
    const title = p["title"] ?? "";
    switch (this.item.type) {
      case "lesson.started":
        return `${subjectName} \u2014 \u043E\u0442\u043C\u0435\u0442\u044C\u0442\u0435\u0441\u044C!`;
      case "lesson.cancelled":
        return `${subjectName} \u2014 \u043F\u0430\u0440\u0430 \u043E\u0442\u043C\u0435\u043D\u0435\u043D\u0430`;
      case "homework.published":
        return `\u041D\u043E\u0432\u043E\u0435 \u0414\u0417 \u043F\u043E ${subjectName}: ${title}`;
      case "homework.updated":
        return `\u0414\u0417 \u043F\u043E ${subjectName} \u043E\u0431\u043D\u043E\u0432\u043B\u0435\u043D\u043E: ${title}`;
      case "attendance.marked":
        return "";
      default:
        return "";
    }
  }
  get relativeTime() {
    const diff = Date.now() - this.item.receivedAt.getTime();
    const minutes = Math.floor(diff / 6e4);
    if (minutes < 1)
      return "\u0442\u043E\u043B\u044C\u043A\u043E \u0447\u0442\u043E";
    if (minutes < 60)
      return `${minutes} \u043C\u0438\u043D \u043D\u0430\u0437\u0430\u0434`;
    const hours = Math.floor(minutes / 60);
    if (hours < 24)
      return `${hours} \u0447 \u043D\u0430\u0437\u0430\u0434`;
    return new Date(this.item.receivedAt).toLocaleDateString("ru-RU", { day: "numeric", month: "short" });
  }
  static \u0275fac = function NotificationItemComponent_Factory(__ngFactoryType__) {
    return new (__ngFactoryType__ || _NotificationItemComponent)();
  };
  static \u0275cmp = /* @__PURE__ */ \u0275\u0275defineComponent({ type: _NotificationItemComponent, selectors: [["app-notification-item"]], inputs: { item: "item" }, decls: 11, vars: 9, consts: [["role", "listitem", 1, "notification-item"], [1, "notification-item__icon", 3, "ngClass"], [1, "notification-item__body"], [1, "notification-item__header"], [1, "notification-item__heading"], [1, "notification-item__time"], [1, "notification-item__text"]], template: function NotificationItemComponent_Template(rf, ctx) {
    if (rf & 1) {
      \u0275\u0275elementStart(0, "div", 0)(1, "div", 1);
      \u0275\u0275element(2, "i");
      \u0275\u0275elementEnd();
      \u0275\u0275elementStart(3, "div", 2)(4, "div", 3)(5, "span", 4);
      \u0275\u0275text(6);
      \u0275\u0275elementEnd();
      \u0275\u0275elementStart(7, "span", 5);
      \u0275\u0275text(8);
      \u0275\u0275elementEnd()();
      \u0275\u0275elementStart(9, "p", 6);
      \u0275\u0275text(10);
      \u0275\u0275elementEnd()()();
    }
    if (rf & 2) {
      \u0275\u0275classProp("is-unread", !ctx.item.read);
      \u0275\u0275attribute("aria-label", "\u0423\u0432\u0435\u0434\u043E\u043C\u043B\u0435\u043D\u0438\u0435: " + ctx.heading + ", " + ctx.relativeTime);
      \u0275\u0275advance();
      \u0275\u0275property("ngClass", ctx.iconColorClass);
      \u0275\u0275advance();
      \u0275\u0275classMap(ctx.iconClass);
      \u0275\u0275advance(4);
      \u0275\u0275textInterpolate(ctx.heading);
      \u0275\u0275advance(2);
      \u0275\u0275textInterpolate(ctx.relativeTime);
      \u0275\u0275advance(2);
      \u0275\u0275textInterpolate(ctx.bodyText);
    }
  }, dependencies: [CommonModule, NgClass], styles: ["\n\n.notification-item[_ngcontent-%COMP%] {\n  display: flex;\n  gap: var(--space-3, 12px);\n  align-items: flex-start;\n  background: var(--bg-secondary);\n  border-radius: var(--radius-md);\n  padding: var(--space-4) var(--space-5);\n  min-height: 64px;\n  border-left: 3px solid transparent;\n}\n.notification-item.is-unread[_ngcontent-%COMP%] {\n  border-left-color: var(--border-accent, var(--accent-primary));\n}\n.notification-item__icon[_ngcontent-%COMP%] {\n  flex-shrink: 0;\n  padding-top: 2px;\n}\n.notification-item__body[_ngcontent-%COMP%] {\n  flex: 1;\n  min-width: 0;\n}\n.notification-item__header[_ngcontent-%COMP%] {\n  display: flex;\n  justify-content: space-between;\n  align-items: baseline;\n  gap: var(--space-2);\n}\n.notification-item__heading[_ngcontent-%COMP%] {\n  font-size: var(--text-base);\n  font-family: var(--font-heading);\n  font-weight: 600;\n}\n.notification-item__time[_ngcontent-%COMP%] {\n  font-size: var(--text-xs);\n  font-family: var(--font-mono);\n  color: var(--text-muted);\n  white-space: nowrap;\n  flex-shrink: 0;\n}\n.notification-item__text[_ngcontent-%COMP%] {\n  font-size: var(--text-sm);\n  line-height: var(--leading-body);\n  color: var(--text-secondary);\n  margin-top: var(--space-1);\n  white-space: nowrap;\n  overflow: hidden;\n  text-overflow: ellipsis;\n}\n.icon-primary[_ngcontent-%COMP%] {\n  color: var(--accent-primary);\n}\n.icon-info[_ngcontent-%COMP%] {\n  color: var(--accent-info);\n}\n.icon-secondary[_ngcontent-%COMP%] {\n  color: var(--accent-secondary);\n}\n.icon-warning[_ngcontent-%COMP%] {\n  color: var(--accent-warning);\n}\n.icon-muted[_ngcontent-%COMP%] {\n  color: var(--text-muted);\n}\n/*# sourceMappingURL=notification-item.component.css.map */"], changeDetection: 0 });
};
(() => {
  (typeof ngDevMode === "undefined" || ngDevMode) && setClassMetadata(NotificationItemComponent, [{
    type: Component,
    args: [{ selector: "app-notification-item", standalone: true, changeDetection: ChangeDetectionStrategy.OnPush, imports: [CommonModule], template: `
    <div
      class="notification-item"
      [class.is-unread]="!item.read"
      role="listitem"
      [attr.aria-label]="'\u0423\u0432\u0435\u0434\u043E\u043C\u043B\u0435\u043D\u0438\u0435: ' + heading + ', ' + relativeTime">

      <div class="notification-item__icon" [ngClass]="iconColorClass">
        <i [class]="iconClass"></i>
      </div>

      <div class="notification-item__body">
        <div class="notification-item__header">
          <span class="notification-item__heading">{{ heading }}</span>
          <span class="notification-item__time">{{ relativeTime }}</span>
        </div>
        <p class="notification-item__text">{{ bodyText }}</p>
      </div>
    </div>
  `, styles: ["/* angular:styles/component:css;114e016abddeadb2e328eda47bcb85bedb155d0057aeb7a0cab5b61b5e8c5503;C:/Users/maksd/IntelliJIDEA/rutcampustrack/frontends/web-panel/src/app/features/student/notifications/notification-item/notification-item.component.ts */\n.notification-item {\n  display: flex;\n  gap: var(--space-3, 12px);\n  align-items: flex-start;\n  background: var(--bg-secondary);\n  border-radius: var(--radius-md);\n  padding: var(--space-4) var(--space-5);\n  min-height: 64px;\n  border-left: 3px solid transparent;\n}\n.notification-item.is-unread {\n  border-left-color: var(--border-accent, var(--accent-primary));\n}\n.notification-item__icon {\n  flex-shrink: 0;\n  padding-top: 2px;\n}\n.notification-item__body {\n  flex: 1;\n  min-width: 0;\n}\n.notification-item__header {\n  display: flex;\n  justify-content: space-between;\n  align-items: baseline;\n  gap: var(--space-2);\n}\n.notification-item__heading {\n  font-size: var(--text-base);\n  font-family: var(--font-heading);\n  font-weight: 600;\n}\n.notification-item__time {\n  font-size: var(--text-xs);\n  font-family: var(--font-mono);\n  color: var(--text-muted);\n  white-space: nowrap;\n  flex-shrink: 0;\n}\n.notification-item__text {\n  font-size: var(--text-sm);\n  line-height: var(--leading-body);\n  color: var(--text-secondary);\n  margin-top: var(--space-1);\n  white-space: nowrap;\n  overflow: hidden;\n  text-overflow: ellipsis;\n}\n.icon-primary {\n  color: var(--accent-primary);\n}\n.icon-info {\n  color: var(--accent-info);\n}\n.icon-secondary {\n  color: var(--accent-secondary);\n}\n.icon-warning {\n  color: var(--accent-warning);\n}\n.icon-muted {\n  color: var(--text-muted);\n}\n/*# sourceMappingURL=notification-item.component.css.map */\n"] }]
  }], null, { item: [{
    type: Input,
    args: [{ required: true }]
  }] });
})();
(() => {
  (typeof ngDevMode === "undefined" || ngDevMode) && \u0275setClassDebugInfo(NotificationItemComponent, { className: "NotificationItemComponent", filePath: "src/app/features/student/notifications/notification-item/notification-item.component.ts", lineNumber: 55 });
})();

// src/app/features/student/notifications/student-notifications.component.ts
var _forTrack0 = ($index, $item) => $item.id;
function StudentNotificationsComponent_Conditional_6_Template(rf, ctx) {
  if (rf & 1) {
    \u0275\u0275elementStart(0, "div", 4);
    \u0275\u0275element(1, "i", 5);
    \u0275\u0275elementStart(2, "h2");
    \u0275\u0275text(3, "\u0423\u0432\u0435\u0434\u043E\u043C\u043B\u0435\u043D\u0438\u0439 \u043D\u0435\u0442");
    \u0275\u0275elementEnd();
    \u0275\u0275elementStart(4, "p");
    \u0275\u0275text(5, "\u0417\u0434\u0435\u0441\u044C \u0431\u0443\u0434\u0443\u0442 \u043F\u043E\u044F\u0432\u043B\u044F\u0442\u044C\u0441\u044F \u0443\u0432\u0435\u0434\u043E\u043C\u043B\u0435\u043D\u0438\u044F \u043E \u0437\u0430\u043D\u044F\u0442\u0438\u044F\u0445, \u0437\u0430\u0434\u0430\u043D\u0438\u044F\u0445 \u0438 \u043F\u043E\u0441\u0435\u0449\u0430\u0435\u043C\u043E\u0441\u0442\u0438.");
    \u0275\u0275elementEnd()();
  }
}
function StudentNotificationsComponent_Conditional_7_Conditional_0_Template(rf, ctx) {
  if (rf & 1) {
    \u0275\u0275elementStart(0, "div", 6);
    \u0275\u0275element(1, "i", 9);
    \u0275\u0275elementStart(2, "span");
    \u0275\u0275text(3, "\u0412\u0441\u0435 \u043F\u0440\u043E\u0447\u0438\u0442\u0430\u043D\u043E");
    \u0275\u0275elementEnd()();
  }
}
function StudentNotificationsComponent_Conditional_7_For_3_Template(rf, ctx) {
  if (rf & 1) {
    \u0275\u0275element(0, "app-notification-item", 8);
  }
  if (rf & 2) {
    const item_r1 = ctx.$implicit;
    \u0275\u0275property("item", item_r1);
  }
}
function StudentNotificationsComponent_Conditional_7_Template(rf, ctx) {
  if (rf & 1) {
    \u0275\u0275template(0, StudentNotificationsComponent_Conditional_7_Conditional_0_Template, 4, 0, "div", 6);
    \u0275\u0275elementStart(1, "div", 7);
    \u0275\u0275repeaterCreate(2, StudentNotificationsComponent_Conditional_7_For_3_Template, 1, 1, "app-notification-item", 8, _forTrack0);
    \u0275\u0275elementEnd();
  }
  if (rf & 2) {
    const ctx_r1 = \u0275\u0275nextContext();
    \u0275\u0275conditional(ctx_r1.allRead() ? 0 : -1);
    \u0275\u0275advance(2);
    \u0275\u0275repeater(ctx_r1.sortedItems());
  }
}
var STORAGE_KEY = "rct-notifications";
var MAX_ITEMS = 100;
var STORED_TYPES = ["lesson.started", "lesson.cancelled", "homework.published", "homework.updated", "attendance.marked"];
var StudentNotificationsComponent = class _StudentNotificationsComponent {
  stompService = inject(StudentStompService);
  badgeService = inject(StudentNotificationBadgeService);
  destroyRef = inject(DestroyRef);
  items = signal(this.loadFromStorage());
  sortedItems = computed(() => [...this.items()].sort((a, b) => b.receivedAt.getTime() - a.receivedAt.getTime()));
  allRead = computed(() => this.items().length > 0 && this.items().every((i) => i.read));
  ngOnInit() {
    this.items.update((list) => list.map((i) => __spreadProps(__spreadValues({}, i), { read: true })));
    this.persistToStorage();
    this.badgeService.reset();
    this.stompService.onAnyEvent$.pipe(takeUntilDestroyed(this.destroyRef)).subscribe((envelope) => {
      if (!STORED_TYPES.includes(envelope.type))
        return;
      const newItem = {
        id: crypto.randomUUID(),
        type: envelope.type,
        payload: envelope.payload,
        receivedAt: /* @__PURE__ */ new Date(),
        read: true
        // already on the page — immediately read
      };
      this.items.update((list) => {
        const updated = [newItem, ...list];
        return updated.length > MAX_ITEMS ? updated.slice(0, MAX_ITEMS) : updated;
      });
      this.persistToStorage();
    });
  }
  loadFromStorage() {
    try {
      const raw = sessionStorage.getItem(STORAGE_KEY);
      if (!raw)
        return [];
      const parsed = JSON.parse(raw);
      return parsed.map((i) => __spreadProps(__spreadValues({}, i), { receivedAt: new Date(i.receivedAt) }));
    } catch {
      return [];
    }
  }
  persistToStorage() {
    try {
      sessionStorage.setItem(STORAGE_KEY, JSON.stringify(this.items()));
    } catch {
    }
  }
  static \u0275fac = function StudentNotificationsComponent_Factory(__ngFactoryType__) {
    return new (__ngFactoryType__ || _StudentNotificationsComponent)();
  };
  static \u0275cmp = /* @__PURE__ */ \u0275\u0275defineComponent({ type: _StudentNotificationsComponent, selectors: [["app-student-notifications"]], decls: 8, vars: 3, consts: [[1, "page-stack"], [1, "page-header"], [1, "page-header__eyebrow"], [1, "page-header__title"], [1, "page-empty"], [1, "ph-bell-slash", "ph-duotone"], [1, "all-read-pill"], ["role", "list", 1, "notifications-list"], [3, "item"], [1, "ph-check-circle", "ph-fill"]], template: function StudentNotificationsComponent_Template(rf, ctx) {
    if (rf & 1) {
      \u0275\u0275elementStart(0, "div", 0)(1, "header", 1)(2, "p", 2);
      \u0275\u0275text(3, "\u0421\u0442\u0443\u0434\u0435\u043D\u0442");
      \u0275\u0275elementEnd();
      \u0275\u0275elementStart(4, "h1", 3);
      \u0275\u0275text(5, "\u0423\u0432\u0435\u0434\u043E\u043C\u043B\u0435\u043D\u0438\u044F");
      \u0275\u0275elementEnd()();
      \u0275\u0275template(6, StudentNotificationsComponent_Conditional_6_Template, 6, 0, "div", 4)(7, StudentNotificationsComponent_Conditional_7_Template, 4, 1);
      \u0275\u0275elementEnd();
    }
    if (rf & 2) {
      \u0275\u0275property("@routeFade", void 0);
      \u0275\u0275advance(6);
      \u0275\u0275conditional(ctx.items().length === 0 ? 6 : -1);
      \u0275\u0275advance();
      \u0275\u0275conditional(ctx.items().length > 0 ? 7 : -1);
    }
  }, dependencies: [CommonModule, NotificationItemComponent], styles: ["\n\n.notifications-list[_ngcontent-%COMP%] {\n  display: flex;\n  flex-direction: column;\n  gap: var(--space-2, 8px);\n}\n.all-read-pill[_ngcontent-%COMP%] {\n  display: inline-flex;\n  align-items: center;\n  gap: var(--space-1, 4px);\n  font-size: var(--text-xs);\n  color: var(--text-muted);\n  background: color-mix(in oklab, var(--text-muted) 10%, transparent);\n  border-radius: var(--radius-full, 9999px);\n  padding: 4px 10px;\n  margin-bottom: var(--space-3, 12px);\n  width: fit-content;\n}\n/*# sourceMappingURL=student-notifications.component.css.map */"], data: { animation: [
    trigger("routeFade", [
      transition(":enter", [
        style({ opacity: 0, transform: "translateY(8px)" }),
        animate("200ms var(--ease-out, ease-out)", style({ opacity: 1, transform: "translateY(0)" }))
      ])
    ])
  ] }, changeDetection: 0 });
};
(() => {
  (typeof ngDevMode === "undefined" || ngDevMode) && setClassMetadata(StudentNotificationsComponent, [{
    type: Component,
    args: [{ selector: "app-student-notifications", standalone: true, changeDetection: ChangeDetectionStrategy.OnPush, imports: [CommonModule, NotificationItemComponent], animations: [
      trigger("routeFade", [
        transition(":enter", [
          style({ opacity: 0, transform: "translateY(8px)" }),
          animate("200ms var(--ease-out, ease-out)", style({ opacity: 1, transform: "translateY(0)" }))
        ])
      ])
    ], template: '<div class="page-stack" [@routeFade]>\n  <header class="page-header">\n    <p class="page-header__eyebrow">\u0421\u0442\u0443\u0434\u0435\u043D\u0442</p>\n    <h1 class="page-header__title">\u0423\u0432\u0435\u0434\u043E\u043C\u043B\u0435\u043D\u0438\u044F</h1>\n  </header>\n\n  @if (items().length === 0) {\n    <div class="page-empty">\n      <i class="ph-bell-slash ph-duotone"></i>\n      <h2>\u0423\u0432\u0435\u0434\u043E\u043C\u043B\u0435\u043D\u0438\u0439 \u043D\u0435\u0442</h2>\n      <p>\u0417\u0434\u0435\u0441\u044C \u0431\u0443\u0434\u0443\u0442 \u043F\u043E\u044F\u0432\u043B\u044F\u0442\u044C\u0441\u044F \u0443\u0432\u0435\u0434\u043E\u043C\u043B\u0435\u043D\u0438\u044F \u043E \u0437\u0430\u043D\u044F\u0442\u0438\u044F\u0445, \u0437\u0430\u0434\u0430\u043D\u0438\u044F\u0445 \u0438 \u043F\u043E\u0441\u0435\u0449\u0430\u0435\u043C\u043E\u0441\u0442\u0438.</p>\n    </div>\n  }\n\n  @if (items().length > 0) {\n    @if (allRead()) {\n      <div class="all-read-pill">\n        <i class="ph-check-circle ph-fill"></i>\n        <span>\u0412\u0441\u0435 \u043F\u0440\u043E\u0447\u0438\u0442\u0430\u043D\u043E</span>\n      </div>\n    }\n    <div class="notifications-list" role="list">\n      @for (item of sortedItems(); track item.id) {\n        <app-notification-item [item]="item"></app-notification-item>\n      }\n    </div>\n  }\n</div>\n', styles: ["/* src/app/features/student/notifications/student-notifications.component.css */\n.notifications-list {\n  display: flex;\n  flex-direction: column;\n  gap: var(--space-2, 8px);\n}\n.all-read-pill {\n  display: inline-flex;\n  align-items: center;\n  gap: var(--space-1, 4px);\n  font-size: var(--text-xs);\n  color: var(--text-muted);\n  background: color-mix(in oklab, var(--text-muted) 10%, transparent);\n  border-radius: var(--radius-full, 9999px);\n  padding: 4px 10px;\n  margin-bottom: var(--space-3, 12px);\n  width: fit-content;\n}\n/*# sourceMappingURL=student-notifications.component.css.map */\n"] }]
  }], null, null);
})();
(() => {
  (typeof ngDevMode === "undefined" || ngDevMode) && \u0275setClassDebugInfo(StudentNotificationsComponent, { className: "StudentNotificationsComponent", filePath: "src/app/features/student/notifications/student-notifications.component.ts", lineNumber: 32 });
})();
export {
  StudentNotificationsComponent
};
//# sourceMappingURL=chunk-ECFOFS6O.js.map
