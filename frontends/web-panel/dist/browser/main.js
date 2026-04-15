import {
  AuthApi
} from "./chunk-E6QM7VLR.js";
import {
  AuthService
} from "./chunk-ID5GKTOO.js";
import {
  provideNativeDateAdapter
} from "./chunk-5HQHZJ2I.js";
import "./chunk-ZMWXCRRI.js";
import "./chunk-LPDGA6NZ.js";
import {
  BarController,
  BarElement,
  CategoryScale,
  Chart,
  LinearScale,
  plugin_legend,
  plugin_tooltip
} from "./chunk-2KMXVJ2V.js";
import "./chunk-676FZPAG.js";
import "./chunk-2WN7CIGJ.js";
import "./chunk-ZEX6QU7O.js";
import "./chunk-YIJEORIR.js";
import "./chunk-FW2NJ6PM.js";
import {
  Router,
  RouterOutlet,
  provideRouter
} from "./chunk-BRINUWSL.js";
import {
  DomRendererFactory2,
  bootstrapApplication
} from "./chunk-RWV7HGF7.js";
import {
  provideHttpClient,
  withInterceptors
} from "./chunk-FLE4DLW4.js";
import {
  DOCUMENT
} from "./chunk-CLRYRPPS.js";
import {
  ANIMATION_MODULE_TYPE,
  ChangeDetectionScheduler,
  Component,
  Injectable,
  InjectionToken,
  Injector,
  NgZone,
  Observable,
  RendererFactory2,
  RuntimeError,
  catchError,
  inject,
  makeEnvironmentProviders,
  performanceMarkFeature,
  provideZoneChangeDetection,
  setClassMetadata,
  switchMap,
  throwError,
  ɵsetClassDebugInfo,
  ɵɵdefineComponent,
  ɵɵdefineInjectable,
  ɵɵelement,
  ɵɵinvalidFactory
} from "./chunk-4M4FDLBS.js";

// node_modules/@angular/platform-browser/fesm2022/animations/async.mjs
var ANIMATION_PREFIX = "@";
var AsyncAnimationRendererFactory = class _AsyncAnimationRendererFactory {
  doc;
  delegate;
  zone;
  animationType;
  moduleImpl;
  _rendererFactoryPromise = null;
  scheduler = null;
  injector = inject(Injector);
  loadingSchedulerFn = inject(\u0275ASYNC_ANIMATION_LOADING_SCHEDULER_FN, {
    optional: true
  });
  _engine;
  /**
   *
   * @param moduleImpl allows to provide a mock implmentation (or will load the animation module)
   */
  constructor(doc, delegate, zone, animationType, moduleImpl) {
    this.doc = doc;
    this.delegate = delegate;
    this.zone = zone;
    this.animationType = animationType;
    this.moduleImpl = moduleImpl;
  }
  /** @docs-private */
  ngOnDestroy() {
    this._engine?.flush();
  }
  /**
   * @internal
   */
  loadImpl() {
    const loadFn = () => this.moduleImpl ?? import("./chunk-GN64MO42.js").then((m) => m);
    let moduleImplPromise;
    if (this.loadingSchedulerFn) {
      moduleImplPromise = this.loadingSchedulerFn(loadFn);
    } else {
      moduleImplPromise = loadFn();
    }
    return moduleImplPromise.catch((e) => {
      throw new RuntimeError(5300, (typeof ngDevMode === "undefined" || ngDevMode) && "Async loading for animations package was enabled, but loading failed. Angular falls back to using regular rendering. No animations will be displayed and their styles won't be applied.");
    }).then(({
      \u0275createEngine,
      \u0275AnimationRendererFactory
    }) => {
      this._engine = \u0275createEngine(this.animationType, this.doc);
      const rendererFactory = new \u0275AnimationRendererFactory(this.delegate, this._engine, this.zone);
      this.delegate = rendererFactory;
      return rendererFactory;
    });
  }
  /**
   * This method is delegating the renderer creation to the factories.
   * It uses default factory while the animation factory isn't loaded
   * and will rely on the animation factory once it is loaded.
   *
   * Calling this method will trigger as side effect the loading of the animation module
   * if the renderered component uses animations.
   */
  createRenderer(hostElement, rendererType) {
    const renderer = this.delegate.createRenderer(hostElement, rendererType);
    if (renderer.\u0275type === 0) {
      return renderer;
    }
    if (typeof renderer.throwOnSyntheticProps === "boolean") {
      renderer.throwOnSyntheticProps = false;
    }
    const dynamicRenderer = new DynamicDelegationRenderer(renderer);
    if (rendererType?.data?.["animation"] && !this._rendererFactoryPromise) {
      this._rendererFactoryPromise = this.loadImpl();
    }
    this._rendererFactoryPromise?.then((animationRendererFactory) => {
      const animationRenderer = animationRendererFactory.createRenderer(hostElement, rendererType);
      dynamicRenderer.use(animationRenderer);
      this.scheduler ??= this.injector.get(ChangeDetectionScheduler, null, {
        optional: true
      });
      this.scheduler?.notify(
        10
        /* NotificationSource.AsyncAnimationsLoaded */
      );
    }).catch((e) => {
      dynamicRenderer.use(renderer);
    });
    return dynamicRenderer;
  }
  begin() {
    this.delegate.begin?.();
  }
  end() {
    this.delegate.end?.();
  }
  whenRenderingDone() {
    return this.delegate.whenRenderingDone?.() ?? Promise.resolve();
  }
  /**
   * Used during HMR to clear any cached data about a component.
   * @param componentId ID of the component that is being replaced.
   */
  componentReplaced(componentId) {
    this._engine?.flush();
    this.delegate.componentReplaced?.(componentId);
  }
  static \u0275fac = function AsyncAnimationRendererFactory_Factory(__ngFactoryType__) {
    \u0275\u0275invalidFactory();
  };
  static \u0275prov = /* @__PURE__ */ \u0275\u0275defineInjectable({
    token: _AsyncAnimationRendererFactory,
    factory: _AsyncAnimationRendererFactory.\u0275fac
  });
};
(() => {
  (typeof ngDevMode === "undefined" || ngDevMode) && setClassMetadata(AsyncAnimationRendererFactory, [{
    type: Injectable
  }], () => [{
    type: Document
  }, {
    type: RendererFactory2
  }, {
    type: NgZone
  }, {
    type: void 0
  }, {
    type: Promise
  }], null);
})();
var DynamicDelegationRenderer = class {
  delegate;
  // List of callbacks that need to be replayed on the animation renderer once its loaded
  replay = [];
  \u0275type = 1;
  constructor(delegate) {
    this.delegate = delegate;
  }
  use(impl) {
    this.delegate = impl;
    if (this.replay !== null) {
      for (const fn of this.replay) {
        fn(impl);
      }
      this.replay = null;
    }
  }
  get data() {
    return this.delegate.data;
  }
  destroy() {
    this.replay = null;
    this.delegate.destroy();
  }
  createElement(name, namespace) {
    return this.delegate.createElement(name, namespace);
  }
  createComment(value) {
    return this.delegate.createComment(value);
  }
  createText(value) {
    return this.delegate.createText(value);
  }
  get destroyNode() {
    return this.delegate.destroyNode;
  }
  appendChild(parent, newChild) {
    this.delegate.appendChild(parent, newChild);
  }
  insertBefore(parent, newChild, refChild, isMove) {
    this.delegate.insertBefore(parent, newChild, refChild, isMove);
  }
  removeChild(parent, oldChild, isHostElement) {
    this.delegate.removeChild(parent, oldChild, isHostElement);
  }
  selectRootElement(selectorOrNode, preserveContent) {
    return this.delegate.selectRootElement(selectorOrNode, preserveContent);
  }
  parentNode(node) {
    return this.delegate.parentNode(node);
  }
  nextSibling(node) {
    return this.delegate.nextSibling(node);
  }
  setAttribute(el, name, value, namespace) {
    this.delegate.setAttribute(el, name, value, namespace);
  }
  removeAttribute(el, name, namespace) {
    this.delegate.removeAttribute(el, name, namespace);
  }
  addClass(el, name) {
    this.delegate.addClass(el, name);
  }
  removeClass(el, name) {
    this.delegate.removeClass(el, name);
  }
  setStyle(el, style, value, flags) {
    this.delegate.setStyle(el, style, value, flags);
  }
  removeStyle(el, style, flags) {
    this.delegate.removeStyle(el, style, flags);
  }
  setProperty(el, name, value) {
    if (this.shouldReplay(name)) {
      this.replay.push((renderer) => renderer.setProperty(el, name, value));
    }
    this.delegate.setProperty(el, name, value);
  }
  setValue(node, value) {
    this.delegate.setValue(node, value);
  }
  listen(target, eventName, callback, options) {
    if (this.shouldReplay(eventName)) {
      this.replay.push((renderer) => renderer.listen(target, eventName, callback, options));
    }
    return this.delegate.listen(target, eventName, callback, options);
  }
  shouldReplay(propOrEventName) {
    return this.replay !== null && propOrEventName.startsWith(ANIMATION_PREFIX);
  }
};
var \u0275ASYNC_ANIMATION_LOADING_SCHEDULER_FN = new InjectionToken(ngDevMode ? "async_animation_loading_scheduler_fn" : "");
function provideAnimationsAsync(type = "animations") {
  performanceMarkFeature("NgAsyncAnimations");
  if (false) {
    type = "noop";
  }
  return makeEnvironmentProviders([{
    provide: RendererFactory2,
    useFactory: (doc, renderer, zone) => {
      return new AsyncAnimationRendererFactory(doc, renderer, zone, type);
    },
    deps: [DOCUMENT, DomRendererFactory2, NgZone]
  }, {
    provide: ANIMATION_MODULE_TYPE,
    useValue: type === "noop" ? "NoopAnimations" : "BrowserAnimations"
  }]);
}

// src/app/core/auth/auth.guard.ts
var authGuard = () => {
  const auth = inject(AuthService);
  const router = inject(Router);
  if (auth.isAuthenticated())
    return true;
  return router.createUrlTree(["/login"]);
};

// src/app/core/auth/role.guard.ts
var roleGuard = (allowedRoles) => () => {
  const auth = inject(AuthService);
  const router = inject(Router);
  const user = auth.currentUser();
  if (!user)
    return router.createUrlTree(["/login"]);
  if (!allowedRoles.includes(user.role)) {
    return router.createUrlTree([auth.resolveDashboardFor(user)]);
  }
  return true;
};

// src/app/core/auth/student.guard.ts
var studentGuard = () => {
  const auth = inject(AuthService);
  const router = inject(Router);
  const user = auth.currentUser();
  if (!user)
    return router.createUrlTree(["/login"]);
  if (user.role === "STUDENT")
    return true;
  return router.createUrlTree([auth.resolveDashboardFor(user)]);
};

// src/app/core/auth/headman.guard.ts
var headmanGuard = () => {
  const auth = inject(AuthService);
  const router = inject(Router);
  const user = auth.currentUser();
  if (!user)
    return router.createUrlTree(["/login"]);
  if (user.role === "STUDENT" && user.isHeadman)
    return true;
  return router.createUrlTree([auth.resolveDashboardFor(user)]);
};

// src/app/core/auth/guest.guard.ts
var guestGuard = () => {
  const auth = inject(AuthService);
  const router = inject(Router);
  if (!auth.isAuthenticated())
    return true;
  return router.createUrlTree([auth.resolveDashboardFor(auth.currentUser())]);
};

// src/app/app.routes.ts
var routes = [
  {
    path: "login",
    canActivate: [guestGuard],
    loadComponent: () => import("./chunk-HTKKVKVY.js").then((m) => m.LoginComponent)
  },
  {
    path: "",
    loadComponent: () => import("./chunk-IQM2FRC2.js").then((m) => m.ShellComponent),
    canActivate: [authGuard],
    children: [
      // Teacher routes
      {
        path: "teacher",
        canActivate: [roleGuard(["TEACHER"])],
        data: { eyebrow: "\u041F\u0440\u0435\u043F\u043E\u0434\u0430\u0432\u0430\u0442\u0435\u043B\u044C" },
        children: [
          {
            path: "dashboard",
            loadComponent: () => import("./chunk-DRZ3CLIP.js").then((m) => m.TeacherDashboardComponent),
            data: { title: "\u0414\u0430\u0448\u0431\u043E\u0440\u0434", eyebrow: "\u041F\u0440\u0435\u043F\u043E\u0434\u0430\u0432\u0430\u0442\u0435\u043B\u044C" }
          },
          {
            path: "journal",
            loadComponent: () => import("./chunk-DBXS6FQQ.js").then((m) => m.JournalPageComponent),
            data: { title: "\u0416\u0443\u0440\u043D\u0430\u043B \u043F\u043E\u0441\u0435\u0449\u0430\u0435\u043C\u043E\u0441\u0442\u0438", eyebrow: "\u041F\u0440\u0435\u043F\u043E\u0434\u0430\u0432\u0430\u0442\u0435\u043B\u044C" }
          },
          {
            path: "stats",
            loadComponent: () => import("./chunk-SEY3MXRK.js").then((m) => m.StatsPageComponent),
            data: { title: "\u0421\u0442\u0430\u0442\u0438\u0441\u0442\u0438\u043A\u0430", eyebrow: "\u041F\u0440\u0435\u043F\u043E\u0434\u0430\u0432\u0430\u0442\u0435\u043B\u044C" }
          },
          { path: "", redirectTo: "dashboard", pathMatch: "full" }
        ]
      },
      // Admin routes
      {
        path: "admin",
        canActivate: [roleGuard(["ADMIN"])],
        data: { eyebrow: "\u0410\u0434\u043C\u0438\u043D\u0438\u0441\u0442\u0440\u0430\u0442\u043E\u0440" },
        children: [
          {
            path: "dashboard",
            loadComponent: () => import("./chunk-SG22DCTG.js").then((m) => m.AdminDashboardComponent),
            data: { title: "\u0414\u0430\u0448\u0431\u043E\u0440\u0434", eyebrow: "\u0410\u0434\u043C\u0438\u043D\u0438\u0441\u0442\u0440\u0430\u0442\u043E\u0440" }
          },
          {
            path: "users",
            loadComponent: () => import("./chunk-HCIN3DSS.js").then((m) => m.UsersPageComponent),
            data: { title: "\u041F\u043E\u043B\u044C\u0437\u043E\u0432\u0430\u0442\u0435\u043B\u0438", eyebrow: "\u0410\u0434\u043C\u0438\u043D\u0438\u0441\u0442\u0440\u0430\u0442\u043E\u0440" }
          },
          {
            path: "groups",
            loadComponent: () => import("./chunk-FPJ5UGFM.js").then((m) => m.GroupsPageComponent),
            data: { title: "\u0413\u0440\u0443\u043F\u043F\u044B", eyebrow: "\u0410\u0434\u043C\u0438\u043D\u0438\u0441\u0442\u0440\u0430\u0442\u043E\u0440" }
          },
          {
            // BUG-006-6 / plan 58-08: read-only история архивной группы.
            path: "groups/:id/history",
            loadComponent: () => import("./chunk-QXW3MA3N.js").then((m) => m.GroupHistoryPageComponent),
            data: { title: "\u0418\u0441\u0442\u043E\u0440\u0438\u044F \u0433\u0440\u0443\u043F\u043F\u044B", eyebrow: "\u0410\u0434\u043C\u0438\u043D\u0438\u0441\u0442\u0440\u0430\u0442\u043E\u0440" }
          },
          {
            path: "semesters",
            loadComponent: () => import("./chunk-7QBEEJSH.js").then((m) => m.SemestersPageComponent),
            data: { title: "\u0421\u0435\u043C\u0435\u0441\u0442\u0440\u044B", eyebrow: "\u0410\u0434\u043C\u0438\u043D\u0438\u0441\u0442\u0440\u0430\u0442\u043E\u0440" }
          },
          { path: "", redirectTo: "dashboard", pathMatch: "full" }
        ]
      },
      // Student routes — Phase 51 (STU-WEB-01..03)
      {
        path: "student",
        canActivate: [studentGuard],
        data: { eyebrow: "\u0421\u0442\u0443\u0434\u0435\u043D\u0442" },
        children: [
          {
            path: "dashboard",
            loadComponent: () => import("./chunk-FKU6IXVX.js").then((m) => m.StudentDashboardComponent),
            data: { title: "\u0413\u043B\u0430\u0432\u043D\u0430\u044F", eyebrow: "\u0421\u0442\u0443\u0434\u0435\u043D\u0442" }
          },
          {
            path: "schedule",
            loadComponent: () => import("./chunk-FLCL3KV3.js").then((m) => m.StudentScheduleComponent),
            data: { title: "\u0420\u0430\u0441\u043F\u0438\u0441\u0430\u043D\u0438\u0435", eyebrow: "\u0421\u0442\u0443\u0434\u0435\u043D\u0442" }
          },
          {
            path: "checkin",
            loadComponent: () => import("./chunk-JMODROSD.js").then((m) => m.StudentCheckinComponent),
            data: { title: "\u041E\u0442\u043C\u0435\u0442\u0438\u0442\u044C\u0441\u044F", eyebrow: "\u0421\u0442\u0443\u0434\u0435\u043D\u0442" }
          },
          {
            path: "homework",
            loadComponent: () => import("./chunk-YV4SUYVU.js").then((m) => m.StudentHomeworkComponent),
            data: { title: "\u0414\u043E\u043C\u0430\u0448\u043D\u0438\u0435 \u0437\u0430\u0434\u0430\u043D\u0438\u044F", eyebrow: "\u0421\u0442\u0443\u0434\u0435\u043D\u0442" }
          },
          {
            path: "stats",
            loadComponent: () => import("./chunk-SKUTW32K.js").then((m) => m.StudentStatsComponent),
            data: { title: "\u0421\u0442\u0430\u0442\u0438\u0441\u0442\u0438\u043A\u0430", eyebrow: "\u0421\u0442\u0443\u0434\u0435\u043D\u0442" }
          },
          {
            path: "notifications",
            loadComponent: () => import("./chunk-XXX6ADOD.js").then((m) => m.StudentNotificationsComponent),
            data: { title: "\u0423\u0432\u0435\u0434\u043E\u043C\u043B\u0435\u043D\u0438\u044F", eyebrow: "\u0421\u0442\u0443\u0434\u0435\u043D\u0442" }
          },
          {
            path: "profile",
            loadComponent: () => import("./chunk-NONRDNDT.js").then((m) => m.StudentProfileComponent),
            data: { title: "\u041F\u0440\u043E\u0444\u0438\u043B\u044C", eyebrow: "\u0421\u0442\u0443\u0434\u0435\u043D\u0442" }
          },
          {
            path: "excuses",
            loadComponent: () => import("./chunk-WVY3VPMI.js").then((m) => m.StudentExcusesComponent),
            data: { title: "\u041F\u0440\u043E\u043F\u0443\u0441\u043A\u0438", eyebrow: "\u0421\u0442\u0443\u0434\u0435\u043D\u0442" }
          },
          {
            path: "late-checkin",
            loadComponent: () => import("./chunk-63H455IG.js").then((m) => m.StudentLateCheckinComponent),
            data: { title: "\u0417\u0430\u043F\u0440\u043E\u0441 \u043E\u0442\u043C\u0435\u0442\u043A\u0438", eyebrow: "\u0421\u0442\u0443\u0434\u0435\u043D\u0442" }
          },
          { path: "", redirectTo: "dashboard", pathMatch: "full" }
        ]
      },
      // Headman routes (D-07 — Phase 54)
      {
        path: "headman",
        canActivate: [headmanGuard],
        data: { eyebrow: "\u0421\u0442\u0430\u0440\u043E\u0441\u0442\u0430" },
        children: [
          {
            path: "dashboard",
            canActivate: [headmanGuard],
            loadComponent: () => import("./chunk-XPIZTY32.js").then((m) => m.HeadmanDashboardComponent),
            data: { title: "\u041A\u0430\u0431\u0438\u043D\u0435\u0442 \u0441\u0442\u0430\u0440\u043E\u0441\u0442\u044B", eyebrow: "\u0421\u0442\u0430\u0440\u043E\u0441\u0442\u0430" }
          },
          {
            path: "group",
            canActivate: [headmanGuard],
            loadComponent: () => import("./chunk-5URQZUXV.js").then((m) => m.HeadmanGroupComponent),
            data: { title: "\u0413\u0440\u0443\u043F\u043F\u0430", eyebrow: "\u0421\u0442\u0430\u0440\u043E\u0441\u0442\u0430" }
          },
          {
            path: "subjects",
            canActivate: [headmanGuard],
            loadComponent: () => import("./chunk-MZVN4L7Z.js").then((m) => m.HeadmanSubjectsComponent),
            data: { title: "\u041F\u0440\u0435\u0434\u043C\u0435\u0442\u044B", eyebrow: "\u0421\u0442\u0430\u0440\u043E\u0441\u0442\u0430" }
          },
          {
            path: "journal",
            canActivate: [headmanGuard],
            loadComponent: () => import("./chunk-5GSR4UZE.js").then((m) => m.HeadmanJournalPageComponent),
            data: { title: "\u0416\u0443\u0440\u043D\u0430\u043B", eyebrow: "\u0421\u0442\u0430\u0440\u043E\u0441\u0442\u0430" }
          },
          {
            path: "weekly-journal",
            canActivate: [headmanGuard],
            loadComponent: () => import("./chunk-HZGMFRCL.js").then((m) => m.HeadmanWeeklyJournalComponent),
            data: { title: "\u0416\u0443\u0440\u043D\u0430\u043B \u043F\u043E\u0441\u0435\u0449\u0430\u0435\u043C\u043E\u0441\u0442\u0438", eyebrow: "\u0421\u0442\u0430\u0440\u043E\u0441\u0442\u0430" }
          },
          {
            path: "schedule",
            canActivate: [headmanGuard],
            loadComponent: () => import("./chunk-3XKGGH5O.js").then((m) => m.HeadmanScheduleComponent),
            data: { title: "\u0420\u0430\u0441\u043F\u0438\u0441\u0430\u043D\u0438\u0435", eyebrow: "\u0421\u0442\u0430\u0440\u043E\u0441\u0442\u0430" }
          },
          {
            path: "lessons",
            canActivate: [headmanGuard],
            loadComponent: () => import("./chunk-2PCKS7SL.js").then((m) => m.HeadmanLessonsComponent),
            data: { title: "\u041F\u0430\u0440\u044B \u043D\u0430 2 \u043D\u0435\u0434\u0435\u043B\u0438", eyebrow: "\u0421\u0442\u0430\u0440\u043E\u0441\u0442\u0430" }
          },
          {
            path: "excuses",
            canActivate: [headmanGuard],
            loadComponent: () => import("./chunk-46ULVOKA.js").then((m) => m.HeadmanExcusesComponent),
            data: { title: "\u041F\u0440\u043E\u043F\u0443\u0441\u043A\u0438", eyebrow: "\u0421\u0442\u0430\u0440\u043E\u0441\u0442\u0430" }
          },
          {
            path: "late-checkin",
            canActivate: [headmanGuard],
            loadComponent: () => import("./chunk-KJOUKWRD.js").then((m) => m.HeadmanLateCheckinComponent),
            data: { title: "\u0417\u0430\u043F\u0440\u043E\u0441\u044B \u043E\u0442\u043C\u0435\u0442\u043A\u0438", eyebrow: "\u0421\u0442\u0430\u0440\u043E\u0441\u0442\u0430" }
          },
          {
            path: "stats",
            canActivate: [headmanGuard],
            loadComponent: () => import("./chunk-QQXAGGTM.js").then((m) => m.HeadmanStatsComponent),
            data: { title: "\u0421\u0442\u0430\u0442\u0438\u0441\u0442\u0438\u043A\u0430", eyebrow: "\u0421\u0442\u0430\u0440\u043E\u0441\u0442\u0430" }
          },
          { path: "", redirectTo: "dashboard", pathMatch: "full" }
        ]
      },
      { path: "", redirectTo: "login", pathMatch: "full" }
    ]
  },
  { path: "**", redirectTo: "login" }
];

// src/app/core/auth/auth.interceptor.ts
var isRefreshing = false;
var pendingRequests = [];
var AUTH_ENDPOINTS = ["/api/auth/login", "/api/auth/refresh", "/api/auth/logout"];
var authInterceptor = (req, next) => {
  const authService = inject(AuthService);
  const authApi = inject(AuthApi);
  const router = inject(Router);
  const isAuthEndpoint = AUTH_ENDPOINTS.some((ep) => req.url.includes(ep));
  const token = authService.accessToken();
  const authedReq = token && !isAuthEndpoint ? req.clone({ setHeaders: { Authorization: `Bearer ${token}` } }) : req;
  return next(authedReq).pipe(catchError((error) => {
    if (error.status !== 401 || isAuthEndpoint) {
      return throwError(() => error);
    }
    if (isRefreshing) {
      return new Observable((observer) => {
        pendingRequests.push({
          resolve: (newToken) => {
            const retried = req.clone({
              setHeaders: { Authorization: `Bearer ${newToken}` }
            });
            next(retried).subscribe(observer);
          },
          reject: (err) => observer.error(err)
        });
      });
    }
    isRefreshing = true;
    const refreshToken = authService.getRefreshToken();
    if (!refreshToken) {
      isRefreshing = false;
      authService.clearTokens();
      router.navigate(["/login"]);
      return throwError(() => error);
    }
    return authApi.refresh(refreshToken).pipe(switchMap((tokens) => {
      authService.setTokens(tokens.accessToken, tokens.refreshToken);
      isRefreshing = false;
      pendingRequests.forEach((p) => p.resolve(tokens.accessToken));
      pendingRequests = [];
      const retried = req.clone({
        setHeaders: { Authorization: `Bearer ${tokens.accessToken}` }
      });
      return next(retried);
    }), catchError((refreshError) => {
      isRefreshing = false;
      pendingRequests.forEach((p) => p.reject(refreshError));
      pendingRequests = [];
      authService.clearTokens();
      router.navigate(["/login"]);
      return throwError(() => refreshError);
    }));
  }));
};

// src/app/app.config.ts
Chart.register(BarController, CategoryScale, LinearScale, BarElement, plugin_legend, plugin_tooltip);
var appConfig = {
  providers: [
    provideZoneChangeDetection({ eventCoalescing: true }),
    provideRouter(routes),
    provideHttpClient(withInterceptors([authInterceptor])),
    provideAnimationsAsync(),
    provideNativeDateAdapter()
  ]
};

// src/app/app.component.ts
var AppComponent = class _AppComponent {
  static \u0275fac = function AppComponent_Factory(__ngFactoryType__) {
    return new (__ngFactoryType__ || _AppComponent)();
  };
  static \u0275cmp = /* @__PURE__ */ \u0275\u0275defineComponent({ type: _AppComponent, selectors: [["app-root"]], decls: 1, vars: 0, template: function AppComponent_Template(rf, ctx) {
    if (rf & 1) {
      \u0275\u0275element(0, "router-outlet");
    }
  }, dependencies: [RouterOutlet], encapsulation: 2 });
};
(() => {
  (typeof ngDevMode === "undefined" || ngDevMode) && setClassMetadata(AppComponent, [{
    type: Component,
    args: [{
      selector: "app-root",
      standalone: true,
      imports: [RouterOutlet],
      template: "<router-outlet />"
    }]
  }], null, null);
})();
(() => {
  (typeof ngDevMode === "undefined" || ngDevMode) && \u0275setClassDebugInfo(AppComponent, { className: "AppComponent", filePath: "src/app/app.component.ts", lineNumber: 10 });
})();

// src/main.ts
window.global = window;
bootstrapApplication(AppComponent, appConfig).catch((err) => console.error(err));
/*! Bundled license information:

@angular/platform-browser/fesm2022/animations/async.mjs:
  (**
   * @license Angular v19.2.20
   * (c) 2010-2025 Google LLC. https://angular.io/
   * License: MIT
   *)
*/
//# sourceMappingURL=main.js.map
