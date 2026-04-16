import {
  SubjectCacheService
} from "./chunk-QY7VTECN.js";
import {
  NotificationCenterService
} from "./chunk-PPDDTI5J.js";
import "./chunk-VAU65FPZ.js";
import "./chunk-33KX5TJL.js";
import {
  animate,
  style,
  transition,
  trigger
} from "./chunk-JKCQIGK6.js";
import "./chunk-URGWZVMC.js";
import {
  AsyncPipe,
  CommonModule,
  NgClass
} from "./chunk-M5DKW26A.js";
import {
  ChangeDetectionStrategy,
  Component,
  Input,
  computed,
  inject,
  of,
  setClassMetadata,
  ɵsetClassDebugInfo,
  ɵɵNgOnChangesFeature,
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
  ɵɵpipe,
  ɵɵpipeBind1,
  ɵɵproperty,
  ɵɵrepeater,
  ɵɵrepeaterCreate,
  ɵɵtemplate,
  ɵɵtext,
  ɵɵtextInterpolate
} from "./chunk-MFRIGFR2.js";

// src/app/features/student/notifications/notification-item/notification-item.component.ts
var NotificationItemComponent = class _NotificationItemComponent {
  subjectCache = inject(SubjectCacheService);
  item;
  subjectName$ = of("");
  ngOnChanges(changes) {
    if (changes["item"]) {
      this.subjectName$ = this.resolveSubjectName$(this.item);
    }
  }
  resolveSubjectName$(item) {
    const p = item.payload ?? {};
    const inline = p["subject_name"] ?? p["subjectName"];
    if (typeof inline === "string" && inline.trim() !== "") {
      return of(inline);
    }
    const rawId = p["subject_id"] ?? p["subjectId"];
    const subjectId = typeof rawId === "number" ? rawId : Number(rawId);
    if (!Number.isFinite(subjectId) || subjectId <= 0) {
      return of("");
    }
    return this.subjectCache.getName(subjectId);
  }
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
      case "late_checkin.decided":
        return this.isApproved ? "\u041E\u0442\u043C\u0435\u0442\u043A\u0430 \u043F\u043E\u0434\u0442\u0432\u0435\u0440\u0436\u0434\u0435\u043D\u0430" : "\u041E\u0442\u043C\u0435\u0442\u043A\u0430 \u043E\u0442\u043A\u043B\u043E\u043D\u0435\u043D\u0430";
      case "excuse.decided":
        return this.isApproved ? "\u0423\u0432\u0430\u0436. \u043F\u0440\u0438\u0447\u0438\u043D\u0430 \u043E\u0434\u043E\u0431\u0440\u0435\u043D\u0430" : "\u0423\u0432\u0430\u0436. \u043F\u0440\u0438\u0447\u0438\u043D\u0430 \u043E\u0442\u043A\u043B\u043E\u043D\u0435\u043D\u0430";
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
      case "late_checkin.decided":
      case "excuse.decided":
        return this.isApproved ? "ph-check-circle ph-fill" : "ph-x-circle ph-fill";
      default:
        return "ph-bell";
    }
  }
  get iconColorClass() {
    switch (this.item.type) {
      case "lesson.started":
        return "icon-secondary";
      case "lesson.cancelled":
        return "icon-danger";
      case "homework.published":
        return "icon-info";
      case "homework.updated":
        return "icon-warning";
      case "attendance.marked":
        return "icon-primary";
      case "late_checkin.decided":
      case "excuse.decided":
        return this.isApproved ? "icon-primary" : "icon-warning";
      default:
        return "icon-muted";
    }
  }
  getBodyText(resolvedSubjectName) {
    const p = this.item.payload ?? {};
    const subjectName = resolvedSubjectName && resolvedSubjectName.trim() || "\u043F\u0430\u0440\u044B";
    const title = p["title"] ?? "";
    const lessonNumber = p["lesson_number"] ?? p["lessonNumber"];
    const lessonDate = p["lesson_date"] ?? p["lessonDate"] ?? "";
    const dateText = lessonDate ? this.formatRuDate(lessonDate) : "";
    const comment = p["decision_comment"] ?? p["decisionComment"] ?? "";
    switch (this.item.type) {
      case "lesson.started":
        return `${this.capitalize(subjectName)} \u043D\u0430\u0447\u0430\u043B\u0430\u0441\u044C \u2014 \u043D\u0435 \u0437\u0430\u0431\u0443\u0434\u044C\u0442\u0435 \u043E\u0442\u043C\u0435\u0442\u0438\u0442\u044C\u0441\u044F.`;
      case "lesson.cancelled": {
        const d = dateText ? ` ${dateText}` : "";
        return `\u041F\u0430\u0440\u0430 \xAB${subjectName}\xBB${d} \u043E\u0442\u043C\u0435\u043D\u0435\u043D\u0430.`;
      }
      case "homework.published":
        return title ? `\u0417\u0430\u0434\u0430\u043D\u0438\u0435 \u043F\u043E ${subjectName}: \xAB${title}\xBB.` : `\u041E\u043F\u0443\u0431\u043B\u0438\u043A\u043E\u0432\u0430\u043D\u043E \u043D\u043E\u0432\u043E\u0435 \u0437\u0430\u0434\u0430\u043D\u0438\u0435 \u043F\u043E ${subjectName}.`;
      case "homework.updated":
        return title ? `\u0417\u0430\u0434\u0430\u043D\u0438\u0435 \u043F\u043E ${subjectName} \u043E\u0431\u043D\u043E\u0432\u043B\u0435\u043D\u043E: \xAB${title}\xBB.` : `\u0417\u0430\u0434\u0430\u043D\u0438\u0435 \u043F\u043E ${subjectName} \u043E\u0431\u043D\u043E\u0432\u043B\u0435\u043D\u043E.`;
      case "attendance.marked": {
        const num = lessonNumber ? ` \u2116${lessonNumber}` : "";
        return `\u0412\u044B \u043E\u0442\u043C\u0435\u0447\u0435\u043D\u044B \u043D\u0430 \u043F\u0430\u0440\u0435${num}.`;
      }
      case "late_checkin.decided": {
        const verdict = this.isApproved ? "\u043F\u043E\u0434\u0442\u0432\u0435\u0440\u0434\u0438\u043B \u0432\u0430\u0448\u0443 \u043E\u0442\u043C\u0435\u0442\u043A\u0443" : "\u043E\u0442\u043A\u043B\u043E\u043D\u0438\u043B \u0437\u0430\u043F\u0440\u043E\u0441 \u043D\u0430 \u043E\u0442\u043C\u0435\u0442\u043A\u0443";
        const lessonPart = lessonNumber ? ` \u2116${lessonNumber}` : "";
        const datePart = dateText ? ` (${dateText})` : "";
        return `\u0421\u0442\u0430\u0440\u043E\u0441\u0442\u0430 ${verdict} \u043F\u043E \xAB${subjectName}\xBB${lessonPart}${datePart}.`;
      }
      case "excuse.decided": {
        const verdict = this.isApproved ? "\u043E\u0434\u043E\u0431\u0440\u0438\u043B" : "\u043E\u0442\u043A\u043B\u043E\u043D\u0438\u043B";
        const tail = comment ? ` \u041A\u043E\u043C\u043C\u0435\u043D\u0442\u0430\u0440\u0438\u0439: \xAB${comment}\xBB.` : "";
        return `\u0421\u0442\u0430\u0440\u043E\u0441\u0442\u0430 ${verdict} \u0437\u0430\u044F\u0432\u043A\u0443 \u043E\u0431 \u0443\u0432\u0430\u0436\u0438\u0442\u0435\u043B\u044C\u043D\u043E\u0439 \u043F\u0440\u0438\u0447\u0438\u043D\u0435.${tail}`;
      }
      default:
        return "";
    }
  }
  capitalize(value) {
    if (!value)
      return value;
    return value.charAt(0).toUpperCase() + value.slice(1);
  }
  formatRuDate(iso) {
    const parsed = new Date(iso.length === 10 ? `${iso}T00:00:00` : iso);
    if (Number.isNaN(parsed.getTime()))
      return iso;
    return parsed.toLocaleDateString("ru-RU", { day: "numeric", month: "short" });
  }
  get isApproved() {
    const raw = this.item.payload?.["status"];
    return typeof raw === "string" && raw.toLowerCase() === "approved";
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
  static \u0275cmp = /* @__PURE__ */ \u0275\u0275defineComponent({ type: _NotificationItemComponent, selectors: [["app-notification-item"]], inputs: { item: "item" }, features: [\u0275\u0275NgOnChangesFeature], decls: 12, vars: 11, consts: [["role", "listitem", 1, "notification-item"], [1, "notification-item__icon", 3, "ngClass"], [1, "notification-item__body"], [1, "notification-item__header"], [1, "notification-item__heading"], [1, "notification-item__time"], [1, "notification-item__text"]], template: function NotificationItemComponent_Template(rf, ctx) {
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
      \u0275\u0275pipe(11, "async");
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
      \u0275\u0275textInterpolate(ctx.getBodyText(\u0275\u0275pipeBind1(11, 9, ctx.subjectName$)));
    }
  }, dependencies: [CommonModule, NgClass, AsyncPipe], styles: ["\n\n.notification-item[_ngcontent-%COMP%] {\n  display: flex;\n  gap: var(--space-3, 12px);\n  align-items: flex-start;\n  background: var(--bg-secondary);\n  border-radius: var(--radius-md);\n  padding: var(--space-4) var(--space-5);\n  min-height: 64px;\n  border-left: 3px solid transparent;\n}\n.notification-item.is-unread[_ngcontent-%COMP%] {\n  border-left-color: var(--border-accent, var(--accent-primary));\n}\n.notification-item__icon[_ngcontent-%COMP%] {\n  flex-shrink: 0;\n  padding-top: 2px;\n}\n.notification-item__body[_ngcontent-%COMP%] {\n  flex: 1;\n  min-width: 0;\n}\n.notification-item__header[_ngcontent-%COMP%] {\n  display: flex;\n  justify-content: space-between;\n  align-items: baseline;\n  gap: var(--space-2);\n}\n.notification-item__heading[_ngcontent-%COMP%] {\n  font-size: var(--text-base);\n  font-family: var(--font-heading);\n  font-weight: 600;\n}\n.notification-item__time[_ngcontent-%COMP%] {\n  font-size: var(--text-xs);\n  font-family: var(--font-mono);\n  color: var(--text-muted);\n  white-space: nowrap;\n  flex-shrink: 0;\n}\n.notification-item__text[_ngcontent-%COMP%] {\n  font-size: var(--text-sm);\n  line-height: var(--leading-body);\n  color: var(--text-secondary);\n  margin-top: var(--space-1);\n  white-space: normal;\n}\n.icon-primary[_ngcontent-%COMP%] {\n  color: var(--accent-primary);\n}\n.icon-info[_ngcontent-%COMP%] {\n  color: var(--accent-info);\n}\n.icon-secondary[_ngcontent-%COMP%] {\n  color: var(--accent-secondary);\n}\n.icon-warning[_ngcontent-%COMP%] {\n  color: var(--accent-warning);\n}\n.icon-danger[_ngcontent-%COMP%] {\n  color: var(--accent-danger);\n}\n.icon-muted[_ngcontent-%COMP%] {\n  color: var(--text-muted);\n}\n/*# sourceMappingURL=notification-item.component.css.map */"], changeDetection: 0 });
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
        <p class="notification-item__text">{{ getBodyText(subjectName$ | async) }}</p>
      </div>
    </div>
  `, styles: ["/* angular:styles/component:css;f28992505965c2434fbfc69efdb0bd2c35684e4b89cd9db56027798d673b9c4d;C:/Users/maksd/IntelliJIDEA/rutcampustrack/frontends/web-panel/src/app/features/student/notifications/notification-item/notification-item.component.ts */\n.notification-item {\n  display: flex;\n  gap: var(--space-3, 12px);\n  align-items: flex-start;\n  background: var(--bg-secondary);\n  border-radius: var(--radius-md);\n  padding: var(--space-4) var(--space-5);\n  min-height: 64px;\n  border-left: 3px solid transparent;\n}\n.notification-item.is-unread {\n  border-left-color: var(--border-accent, var(--accent-primary));\n}\n.notification-item__icon {\n  flex-shrink: 0;\n  padding-top: 2px;\n}\n.notification-item__body {\n  flex: 1;\n  min-width: 0;\n}\n.notification-item__header {\n  display: flex;\n  justify-content: space-between;\n  align-items: baseline;\n  gap: var(--space-2);\n}\n.notification-item__heading {\n  font-size: var(--text-base);\n  font-family: var(--font-heading);\n  font-weight: 600;\n}\n.notification-item__time {\n  font-size: var(--text-xs);\n  font-family: var(--font-mono);\n  color: var(--text-muted);\n  white-space: nowrap;\n  flex-shrink: 0;\n}\n.notification-item__text {\n  font-size: var(--text-sm);\n  line-height: var(--leading-body);\n  color: var(--text-secondary);\n  margin-top: var(--space-1);\n  white-space: normal;\n}\n.icon-primary {\n  color: var(--accent-primary);\n}\n.icon-info {\n  color: var(--accent-info);\n}\n.icon-secondary {\n  color: var(--accent-secondary);\n}\n.icon-warning {\n  color: var(--accent-warning);\n}\n.icon-danger {\n  color: var(--accent-danger);\n}\n.icon-muted {\n  color: var(--text-muted);\n}\n/*# sourceMappingURL=notification-item.component.css.map */\n"] }]
  }], null, { item: [{
    type: Input,
    args: [{ required: true }]
  }] });
})();
(() => {
  (typeof ngDevMode === "undefined" || ngDevMode) && \u0275setClassDebugInfo(NotificationItemComponent, { className: "NotificationItemComponent", filePath: "src/app/features/student/notifications/notification-item/notification-item.component.ts", lineNumber: 58 });
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
var StudentNotificationsComponent = class _StudentNotificationsComponent {
  center = inject(NotificationCenterService);
  /** Прокси к {@link NotificationCenterService.items} для существующего шаблона. */
  items = this.center.items;
  /**
   * Адаптер: глобальный сервис хранит receivedAt как ISO-строку (иначе
   * sessionStorage теряет Date при (de)serialization), а существующий
   * {@link NotificationItemComponent} ждёт {@link NotificationItem} с Date.
   */
  sortedItems = computed(() => {
    return this.center.items().map(toItem).sort((a, b) => b.receivedAt.getTime() - a.receivedAt.getTime());
  });
  allRead = computed(() => this.center.items().length > 0 && this.center.unreadCount() === 0);
  ngOnInit() {
    this.center.markAllRead();
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
        animate("200ms cubic-bezier(0.16, 1, 0.3, 1)", style({ opacity: 1, transform: "translateY(0)" }))
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
          animate("200ms cubic-bezier(0.16, 1, 0.3, 1)", style({ opacity: 1, transform: "translateY(0)" }))
        ])
      ])
    ], template: '<div class="page-stack" [@routeFade]>\n  <header class="page-header">\n    <p class="page-header__eyebrow">\u0421\u0442\u0443\u0434\u0435\u043D\u0442</p>\n    <h1 class="page-header__title">\u0423\u0432\u0435\u0434\u043E\u043C\u043B\u0435\u043D\u0438\u044F</h1>\n  </header>\n\n  @if (items().length === 0) {\n    <div class="page-empty">\n      <i class="ph-bell-slash ph-duotone"></i>\n      <h2>\u0423\u0432\u0435\u0434\u043E\u043C\u043B\u0435\u043D\u0438\u0439 \u043D\u0435\u0442</h2>\n      <p>\u0417\u0434\u0435\u0441\u044C \u0431\u0443\u0434\u0443\u0442 \u043F\u043E\u044F\u0432\u043B\u044F\u0442\u044C\u0441\u044F \u0443\u0432\u0435\u0434\u043E\u043C\u043B\u0435\u043D\u0438\u044F \u043E \u0437\u0430\u043D\u044F\u0442\u0438\u044F\u0445, \u0437\u0430\u0434\u0430\u043D\u0438\u044F\u0445 \u0438 \u043F\u043E\u0441\u0435\u0449\u0430\u0435\u043C\u043E\u0441\u0442\u0438.</p>\n    </div>\n  }\n\n  @if (items().length > 0) {\n    @if (allRead()) {\n      <div class="all-read-pill">\n        <i class="ph-check-circle ph-fill"></i>\n        <span>\u0412\u0441\u0435 \u043F\u0440\u043E\u0447\u0438\u0442\u0430\u043D\u043E</span>\n      </div>\n    }\n    <div class="notifications-list" role="list">\n      @for (item of sortedItems(); track item.id) {\n        <app-notification-item [item]="item"></app-notification-item>\n      }\n    </div>\n  }\n</div>\n', styles: ["/* src/app/features/student/notifications/student-notifications.component.css */\n.notifications-list {\n  display: flex;\n  flex-direction: column;\n  gap: var(--space-2, 8px);\n}\n.all-read-pill {\n  display: inline-flex;\n  align-items: center;\n  gap: var(--space-1, 4px);\n  font-size: var(--text-xs);\n  color: var(--text-muted);\n  background: color-mix(in oklab, var(--text-muted) 10%, transparent);\n  border-radius: var(--radius-full, 9999px);\n  padding: 4px 10px;\n  margin-bottom: var(--space-3, 12px);\n  width: fit-content;\n}\n/*# sourceMappingURL=student-notifications.component.css.map */\n"] }]
  }], null, null);
})();
(() => {
  (typeof ngDevMode === "undefined" || ngDevMode) && \u0275setClassDebugInfo(StudentNotificationsComponent, { className: "StudentNotificationsComponent", filePath: "src/app/features/student/notifications/student-notifications.component.ts", lineNumber: 38 });
})();
function toItem(record) {
  return {
    id: record.id,
    type: record.type,
    payload: record.payload,
    receivedAt: new Date(record.receivedAt),
    read: record.read
  };
}
export {
  StudentNotificationsComponent
};
//# sourceMappingURL=chunk-YEZXWOXK.js.map
