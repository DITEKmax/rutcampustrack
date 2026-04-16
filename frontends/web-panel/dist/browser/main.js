import {
  AuthApi
} from "./chunk-HQN62MRG.js";
import {
  AuthService
} from "./chunk-URGWZVMC.js";
import {
  provideNativeDateAdapter
} from "./chunk-2CP43WR6.js";
import "./chunk-6V3IFZC4.js";
import "./chunk-POR7KUSL.js";
import "./chunk-4RJPSRCU.js";
import "./chunk-6JM2QIGP.js";
import "./chunk-75YPR2VK.js";
import "./chunk-YIJEORIR.js";
import "./chunk-B3BDHDAM.js";
import {
  Router,
  RouterOutlet,
  provideRouter
} from "./chunk-643P6YYN.js";
import {
  DomRendererFactory2,
  bootstrapApplication
} from "./chunk-7WMFDRFR.js";
import {
  BarController,
  BarElement,
  CategoryScale,
  Chart,
  LineController,
  LineElement,
  LinearScale,
  PointElement,
  index,
  plugin_legend,
  plugin_tooltip
} from "./chunk-KLKVWNKW.js";
import {
  DOCUMENT,
  provideHttpClient,
  withInterceptors
} from "./chunk-M5DKW26A.js";
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
} from "./chunk-MFRIGFR2.js";

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
    const loadFn = () => this.moduleImpl ?? import("./chunk-3KR5A4FC.js").then((m) => m);
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
    loadComponent: () => import("./chunk-2O4DPRKE.js").then((m) => m.LoginComponent)
  },
  {
    path: "",
    loadComponent: () => import("./chunk-RIJ4INWF.js").then((m) => m.ShellComponent),
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
            loadComponent: () => import("./chunk-6CUNFU6G.js").then((m) => m.TeacherDashboardComponent),
            data: { title: "\u0414\u0430\u0448\u0431\u043E\u0440\u0434", eyebrow: "\u041F\u0440\u0435\u043F\u043E\u0434\u0430\u0432\u0430\u0442\u0435\u043B\u044C" }
          },
          {
            path: "journal",
            loadComponent: () => import("./chunk-YFWDJONW.js").then((m) => m.JournalPageComponent),
            data: { title: "\u0416\u0443\u0440\u043D\u0430\u043B \u043F\u043E\u0441\u0435\u0449\u0430\u0435\u043C\u043E\u0441\u0442\u0438", eyebrow: "\u041F\u0440\u0435\u043F\u043E\u0434\u0430\u0432\u0430\u0442\u0435\u043B\u044C" }
          },
          {
            path: "stats",
            loadComponent: () => import("./chunk-C4MNYPE2.js").then((m) => m.StatsPageComponent),
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
            loadComponent: () => import("./chunk-2FNPFSEC.js").then((m) => m.AdminDashboardComponent),
            data: { title: "\u0414\u0430\u0448\u0431\u043E\u0440\u0434", eyebrow: "\u0410\u0434\u043C\u0438\u043D\u0438\u0441\u0442\u0440\u0430\u0442\u043E\u0440" }
          },
          {
            path: "users",
            loadComponent: () => import("./chunk-DS5F6X5M.js").then((m) => m.UsersPageComponent),
            data: { title: "\u041F\u043E\u043B\u044C\u0437\u043E\u0432\u0430\u0442\u0435\u043B\u0438", eyebrow: "\u0410\u0434\u043C\u0438\u043D\u0438\u0441\u0442\u0440\u0430\u0442\u043E\u0440" }
          },
          {
            path: "groups",
            loadComponent: () => import("./chunk-VJB57OSV.js").then((m) => m.GroupsPageComponent),
            data: { title: "\u0413\u0440\u0443\u043F\u043F\u044B", eyebrow: "\u0410\u0434\u043C\u0438\u043D\u0438\u0441\u0442\u0440\u0430\u0442\u043E\u0440" }
          },
          {
            // BUG-006-6 / plan 58-08: read-only история архивной группы.
            path: "groups/:id/history",
            loadComponent: () => import("./chunk-6MK5SR4S.js").then((m) => m.GroupHistoryPageComponent),
            data: { title: "\u0418\u0441\u0442\u043E\u0440\u0438\u044F \u0433\u0440\u0443\u043F\u043F\u044B", eyebrow: "\u0410\u0434\u043C\u0438\u043D\u0438\u0441\u0442\u0440\u0430\u0442\u043E\u0440" }
          },
          {
            path: "semesters",
            loadComponent: () => import("./chunk-MRRPL5FT.js").then((m) => m.SemestersPageComponent),
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
            loadComponent: () => import("./chunk-BYBJRGTL.js").then((m) => m.StudentDashboardComponent),
            data: { title: "\u0413\u043B\u0430\u0432\u043D\u0430\u044F", eyebrow: "\u0421\u0442\u0443\u0434\u0435\u043D\u0442" }
          },
          {
            path: "schedule",
            loadComponent: () => import("./chunk-Z3SSJLAT.js").then((m) => m.StudentScheduleComponent),
            data: { title: "\u0420\u0430\u0441\u043F\u0438\u0441\u0430\u043D\u0438\u0435", eyebrow: "\u0421\u0442\u0443\u0434\u0435\u043D\u0442" }
          },
          {
            path: "checkin",
            loadComponent: () => import("./chunk-22AL5ZRV.js").then((m) => m.StudentCheckinComponent),
            data: { title: "\u041E\u0442\u043C\u0435\u0442\u0438\u0442\u044C\u0441\u044F", eyebrow: "\u0421\u0442\u0443\u0434\u0435\u043D\u0442" }
          },
          {
            path: "homework",
            loadComponent: () => import("./chunk-PBRFSSQX.js").then((m) => m.StudentHomeworkComponent),
            data: { title: "\u0414\u043E\u043C\u0430\u0448\u043D\u0438\u0435 \u0437\u0430\u0434\u0430\u043D\u0438\u044F", eyebrow: "\u0421\u0442\u0443\u0434\u0435\u043D\u0442" }
          },
          {
            path: "stats",
            loadComponent: () => import("./chunk-N4GLGDR3.js").then((m) => m.StudentStatsComponent),
            data: { title: "\u0421\u0442\u0430\u0442\u0438\u0441\u0442\u0438\u043A\u0430", eyebrow: "\u0421\u0442\u0443\u0434\u0435\u043D\u0442" }
          },
          {
            path: "notifications",
            loadComponent: () => import("./chunk-YEZXWOXK.js").then((m) => m.StudentNotificationsComponent),
            data: { title: "\u0423\u0432\u0435\u0434\u043E\u043C\u043B\u0435\u043D\u0438\u044F", eyebrow: "\u0421\u0442\u0443\u0434\u0435\u043D\u0442" }
          },
          {
            path: "profile",
            loadComponent: () => import("./chunk-NBQHNNYY.js").then((m) => m.StudentProfileComponent),
            data: { title: "\u041F\u0440\u043E\u0444\u0438\u043B\u044C", eyebrow: "\u0421\u0442\u0443\u0434\u0435\u043D\u0442" }
          },
          {
            path: "excuses",
            loadComponent: () => import("./chunk-4AYJKLCE.js").then((m) => m.StudentExcusesComponent),
            data: { title: "\u041F\u0440\u043E\u043F\u0443\u0441\u043A\u0438", eyebrow: "\u0421\u0442\u0443\u0434\u0435\u043D\u0442" }
          },
          {
            path: "late-checkin",
            loadComponent: () => import("./chunk-TVUE6ERH.js").then((m) => m.StudentLateCheckinComponent),
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
            loadComponent: () => import("./chunk-6EU5CYLO.js").then((m) => m.HeadmanDashboardComponent),
            data: { title: "\u041A\u0430\u0431\u0438\u043D\u0435\u0442 \u0441\u0442\u0430\u0440\u043E\u0441\u0442\u044B", eyebrow: "\u0421\u0442\u0430\u0440\u043E\u0441\u0442\u0430" }
          },
          {
            path: "group",
            canActivate: [headmanGuard],
            loadComponent: () => import("./chunk-7BXOD6B7.js").then((m) => m.HeadmanGroupComponent),
            data: { title: "\u0413\u0440\u0443\u043F\u043F\u0430", eyebrow: "\u0421\u0442\u0430\u0440\u043E\u0441\u0442\u0430" }
          },
          {
            path: "subjects",
            canActivate: [headmanGuard],
            loadComponent: () => import("./chunk-3DVFBTTC.js").then((m) => m.HeadmanSubjectsComponent),
            data: { title: "\u041F\u0440\u0435\u0434\u043C\u0435\u0442\u044B", eyebrow: "\u0421\u0442\u0430\u0440\u043E\u0441\u0442\u0430" }
          },
          {
            path: "homework",
            canActivate: [headmanGuard],
            loadComponent: () => import("./chunk-WVOXSGBA.js").then((m) => m.HeadmanHomeworkComponent),
            data: { title: "\u0414\u043E\u043C\u0430\u0448\u043D\u0438\u0435 \u0437\u0430\u0434\u0430\u043D\u0438\u044F", eyebrow: "\u0421\u0442\u0430\u0440\u043E\u0441\u0442\u0430" }
          },
          {
            path: "journal",
            canActivate: [headmanGuard],
            loadComponent: () => import("./chunk-KWLY5T5A.js").then((m) => m.HeadmanJournalPageComponent),
            data: { title: "\u0416\u0443\u0440\u043D\u0430\u043B", eyebrow: "\u0421\u0442\u0430\u0440\u043E\u0441\u0442\u0430" }
          },
          {
            path: "weekly-journal",
            canActivate: [headmanGuard],
            loadComponent: () => import("./chunk-BFPCJK27.js").then((m) => m.HeadmanWeeklyJournalComponent),
            data: { title: "\u0416\u0443\u0440\u043D\u0430\u043B \u043F\u043E\u0441\u0435\u0449\u0430\u0435\u043C\u043E\u0441\u0442\u0438", eyebrow: "\u0421\u0442\u0430\u0440\u043E\u0441\u0442\u0430" }
          },
          {
            path: "schedule",
            canActivate: [headmanGuard],
            loadComponent: () => import("./chunk-CZCWKEBY.js").then((m) => m.HeadmanScheduleComponent),
            data: { title: "\u0420\u0430\u0441\u043F\u0438\u0441\u0430\u043D\u0438\u0435", eyebrow: "\u0421\u0442\u0430\u0440\u043E\u0441\u0442\u0430" }
          },
          {
            path: "lessons",
            canActivate: [headmanGuard],
            loadComponent: () => import("./chunk-FLED4K3J.js").then((m) => m.HeadmanLessonsComponent),
            data: { title: "\u041F\u0430\u0440\u044B \u043D\u0430 2 \u043D\u0435\u0434\u0435\u043B\u0438", eyebrow: "\u0421\u0442\u0430\u0440\u043E\u0441\u0442\u0430" }
          },
          {
            path: "excuses",
            canActivate: [headmanGuard],
            loadComponent: () => import("./chunk-PDIVETNS.js").then((m) => m.HeadmanExcusesComponent),
            data: { title: "\u041F\u0440\u043E\u043F\u0443\u0441\u043A\u0438", eyebrow: "\u0421\u0442\u0430\u0440\u043E\u0441\u0442\u0430" }
          },
          {
            path: "late-checkin",
            canActivate: [headmanGuard],
            loadComponent: () => import("./chunk-H2RGRJRP.js").then((m) => m.HeadmanLateCheckinComponent),
            data: { title: "\u0417\u0430\u043F\u0440\u043E\u0441\u044B \u043E\u0442\u043C\u0435\u0442\u043A\u0438", eyebrow: "\u0421\u0442\u0430\u0440\u043E\u0441\u0442\u0430" }
          },
          {
            path: "stats",
            canActivate: [headmanGuard],
            loadComponent: () => import("./chunk-PSHLYTFX.js").then((m) => m.HeadmanStatsComponent),
            data: { title: "\u0421\u0442\u0430\u0442\u0438\u0441\u0442\u0438\u043A\u0430", eyebrow: "\u0421\u0442\u0430\u0440\u043E\u0441\u0442\u0430" }
          },
          { path: "", redirectTo: "dashboard", pathMatch: "full" }
        ]
      },
      { path: "", redirectTo: "login", pathMatch: "full" },
      // Authenticated 404: any unknown path under the shell shows NotFound
      // instead of silently bouncing the user back to login.
      {
        path: "**",
        loadComponent: () => import("./chunk-TOES5QDD.js").then((m) => m.NotFoundComponent),
        data: { title: "\u0421\u0442\u0440\u0430\u043D\u0438\u0446\u0430 \u043D\u0435 \u043D\u0430\u0439\u0434\u0435\u043D\u0430", eyebrow: "\u041E\u0448\u0438\u0431\u043A\u0430" }
      }
    ]
  }
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
Chart.register(BarController, LineController, CategoryScale, LinearScale, BarElement, LineElement, PointElement, index, plugin_legend, plugin_tooltip);
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
var RELOADED_KEY = "rct.chunk-reload.v1";
function recoverFromChunkError(err) {
  const msg = err instanceof Error ? `${err.name} ${err.message}` : String(err);
  const isChunkError = /ChunkLoadError/i.test(msg) || /Loading chunk [\w-]+ failed/i.test(msg) || /Failed to fetch dynamically imported module/i.test(msg);
  if (!isChunkError)
    return false;
  if (sessionStorage.getItem(RELOADED_KEY))
    return false;
  sessionStorage.setItem(RELOADED_KEY, "1");
  window.location.reload();
  return true;
}
window.addEventListener("error", (event) => {
  recoverFromChunkError(event.error ?? event.message);
});
window.addEventListener("unhandledrejection", (event) => {
  recoverFromChunkError(event.reason);
});
bootstrapApplication(AppComponent, appConfig).catch((err) => {
  if (recoverFromChunkError(err))
    return;
  console.error(err);
});
/*! Bundled license information:

@angular/platform-browser/fesm2022/animations/async.mjs:
  (**
   * @license Angular v19.2.20
   * (c) 2010-2025 Google LLC. https://angular.io/
   * License: MIT
   *)
*/
//# sourceMappingURL=main.js.map
