import {
  require_entry,
  require_stomp_umd
} from "./chunk-VAU65FPZ.js";
import {
  AuthService
} from "./chunk-URGWZVMC.js";
import {
  Injectable,
  Subject,
  __spreadProps,
  __spreadValues,
  __toESM,
  computed,
  effect,
  inject,
  setClassMetadata,
  signal,
  ɵɵdefineInjectable
} from "./chunk-MFRIGFR2.js";

// src/app/core/notifications/notification-center.service.ts
var import_stompjs = __toESM(require_stomp_umd());
var import_sockjs_client = __toESM(require_entry());
var STORED_TYPES = /* @__PURE__ */ new Set([
  "lesson.started",
  "lesson.cancelled",
  "homework.published",
  "homework.updated",
  "attendance.marked",
  "late_checkin.requested",
  "late_checkin.decided",
  "excuse.requested",
  "excuse.decided"
]);
var USER_SCOPED_TYPES = /* @__PURE__ */ new Set([
  "late_checkin.decided",
  "excuse.decided"
]);
var STORAGE_KEY = "rct-global-notifications";
var MAX_ITEMS = 200;
var NotificationCenterService = class _NotificationCenterService {
  auth = inject(AuthService);
  client = null;
  connectedKey = null;
  // `${userId}:${groupId}:${isHeadman}`
  _items = signal(this.loadFromStorage());
  items = this._items.asReadonly();
  unreadCount = computed(() => this._items().filter((i) => !i.read).length);
  eventSubject = new Subject();
  /** Поток всех принятых событий (для компонентов, слушающих конкретные типы). */
  onEvent$ = this.eventSubject.asObservable();
  constructor() {
    effect(() => {
      const user = this.auth.currentUser();
      if (!user) {
        this.disconnect();
        return;
      }
      if (user.groupId == null) {
        this.disconnect();
        return;
      }
      this.connectFor(user.id, user.groupId, user.isHeadman);
    });
  }
  /** Пометить всё как прочитанное (вызывает колокольчик / страница уведомлений). */
  markAllRead() {
    this._items.update((list) => list.map((i) => i.read ? i : __spreadProps(__spreadValues({}, i), { read: true })));
    this.persist();
  }
  /** Очистить историю целиком (не используется в UI пока, но пригодится). */
  clear() {
    this._items.set([]);
    this.persist();
  }
  connectFor(userId, groupId, isHeadman) {
    const key = `${userId}:${groupId}:${isHeadman}`;
    if (this.connectedKey === key && this.client !== null)
      return;
    this.disconnect();
    this.connectedKey = key;
    const token = this.auth.accessToken() ?? "";
    this.client = new import_stompjs.Client({
      webSocketFactory: () => new import_sockjs_client.default(`/api/ws?token=${token}`),
      reconnectDelay: 2e3,
      onConnect: () => {
        this.client?.subscribe(`/topic/group/${groupId}`, (message) => this.handleFrame(message.body, userId));
        if (isHeadman) {
          this.client?.subscribe(`/topic/group/${groupId}/headman`, (message) => this.handleFrame(message.body, userId));
        }
      },
      onStompError: (frame) => {
        console.error("[notification-center] STOMP error:", frame.headers["message"]);
      }
    });
    this.client.activate();
  }
  disconnect() {
    if (this.client !== null) {
      this.client.deactivate();
      this.client = null;
    }
    this.connectedKey = null;
  }
  handleFrame(body, currentUserId) {
    let envelope;
    try {
      envelope = JSON.parse(body);
    } catch {
      return;
    }
    if (!envelope.type)
      return;
    if (USER_SCOPED_TYPES.has(envelope.type)) {
      const payloadUserId = envelope.payload?.["user_id"];
      if (typeof payloadUserId !== "number" || payloadUserId !== currentUserId) {
        return;
      }
    }
    this.eventSubject.next(envelope);
    if (!STORED_TYPES.has(envelope.type))
      return;
    const record = {
      id: crypto.randomUUID(),
      type: envelope.type,
      payload: envelope.payload ?? {},
      receivedAt: (/* @__PURE__ */ new Date()).toISOString(),
      read: false
    };
    this._items.update((list) => {
      const next = [record, ...list];
      return next.length > MAX_ITEMS ? next.slice(0, MAX_ITEMS) : next;
    });
    this.persist();
  }
  loadFromStorage() {
    try {
      if (typeof sessionStorage === "undefined")
        return [];
      const raw = sessionStorage.getItem(STORAGE_KEY);
      if (!raw)
        return [];
      const parsed = JSON.parse(raw);
      return Array.isArray(parsed) ? parsed : [];
    } catch {
      return [];
    }
  }
  persist() {
    try {
      sessionStorage.setItem(STORAGE_KEY, JSON.stringify(this._items()));
    } catch {
    }
  }
  static \u0275fac = function NotificationCenterService_Factory(__ngFactoryType__) {
    return new (__ngFactoryType__ || _NotificationCenterService)();
  };
  static \u0275prov = /* @__PURE__ */ \u0275\u0275defineInjectable({ token: _NotificationCenterService, factory: _NotificationCenterService.\u0275fac, providedIn: "root" });
};
(() => {
  (typeof ngDevMode === "undefined" || ngDevMode) && setClassMetadata(NotificationCenterService, [{
    type: Injectable,
    args: [{ providedIn: "root" }]
  }], () => [], null);
})();

export {
  NotificationCenterService
};
//# sourceMappingURL=chunk-PPDDTI5J.js.map
