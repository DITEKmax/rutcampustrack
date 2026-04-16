import {
  AuthService
} from "./chunk-URGWZVMC.js";
import {
  RouterLink
} from "./chunk-643P6YYN.js";
import "./chunk-7WMFDRFR.js";
import "./chunk-M5DKW26A.js";
import {
  ChangeDetectionStrategy,
  Component,
  computed,
  inject,
  setClassMetadata,
  ɵsetClassDebugInfo,
  ɵɵadvance,
  ɵɵdefineComponent,
  ɵɵelement,
  ɵɵelementEnd,
  ɵɵelementStart,
  ɵɵproperty,
  ɵɵtext
} from "./chunk-MFRIGFR2.js";

// src/app/features/not-found/not-found.component.ts
var NotFoundComponent = class _NotFoundComponent {
  auth = inject(AuthService);
  dashboardLink = computed(() => {
    const user = this.auth.currentUser();
    return user ? this.auth.resolveDashboardFor(user) : "/login";
  });
  static \u0275fac = function NotFoundComponent_Factory(__ngFactoryType__) {
    return new (__ngFactoryType__ || _NotFoundComponent)();
  };
  static \u0275cmp = /* @__PURE__ */ \u0275\u0275defineComponent({ type: _NotFoundComponent, selectors: [["app-not-found"]], decls: 12, vars: 1, consts: [[1, "page-stack", 2, "max-width", "640px", "margin", "0 auto", "padding-top", "var(--space-7)"], [1, "page-card"], [1, "page-empty"], ["aria-hidden", "true", 1, "page-empty__icon"], [1, "ph-duotone", "ph-compass"], [1, "page-empty__title"], [1, "page-empty__text"], [1, "btn-brand", 2, "margin-top", "var(--space-4)", 3, "routerLink"], ["aria-hidden", "true", 1, "ph", "ph-arrow-left"]], template: function NotFoundComponent_Template(rf, ctx) {
    if (rf & 1) {
      \u0275\u0275elementStart(0, "section", 0)(1, "div", 1)(2, "div", 2)(3, "div", 3);
      \u0275\u0275element(4, "i", 4);
      \u0275\u0275elementEnd();
      \u0275\u0275elementStart(5, "h1", 5);
      \u0275\u0275text(6, "\u0421\u0442\u0440\u0430\u043D\u0438\u0446\u0430 \u043D\u0435 \u043D\u0430\u0439\u0434\u0435\u043D\u0430");
      \u0275\u0275elementEnd();
      \u0275\u0275elementStart(7, "p", 6);
      \u0275\u0275text(8, " \u0410\u0434\u0440\u0435\u0441, \u043F\u043E \u043A\u043E\u0442\u043E\u0440\u043E\u043C\u0443 \u0432\u044B \u043F\u0435\u0440\u0435\u0448\u043B\u0438, \u043D\u0435 \u0441\u0443\u0449\u0435\u0441\u0442\u0432\u0443\u0435\u0442 \u0438\u043B\u0438 \u0431\u044B\u043B \u043F\u0435\u0440\u0435\u043C\u0435\u0449\u0451\u043D. \u041F\u0440\u043E\u0432\u0435\u0440\u044C\u0442\u0435 \u0441\u0441\u044B\u043B\u043A\u0443 \u0438\u043B\u0438 \u0432\u0435\u0440\u043D\u0438\u0442\u0435\u0441\u044C \u043D\u0430 \u0433\u043B\u0430\u0432\u043D\u0443\u044E. ");
      \u0275\u0275elementEnd();
      \u0275\u0275elementStart(9, "a", 7);
      \u0275\u0275element(10, "i", 8);
      \u0275\u0275text(11, " \u041D\u0430 \u0433\u043B\u0430\u0432\u043D\u0443\u044E ");
      \u0275\u0275elementEnd()()()();
    }
    if (rf & 2) {
      \u0275\u0275advance(9);
      \u0275\u0275property("routerLink", ctx.dashboardLink());
    }
  }, dependencies: [RouterLink], encapsulation: 2, changeDetection: 0 });
};
(() => {
  (typeof ngDevMode === "undefined" || ngDevMode) && setClassMetadata(NotFoundComponent, [{
    type: Component,
    args: [{
      selector: "app-not-found",
      standalone: true,
      changeDetection: ChangeDetectionStrategy.OnPush,
      imports: [RouterLink],
      template: `
    <section class="page-stack" style="max-width: 640px; margin: 0 auto; padding-top: var(--space-7);">
      <div class="page-card">
        <div class="page-empty">
          <div class="page-empty__icon" aria-hidden="true">
            <i class="ph-duotone ph-compass"></i>
          </div>
          <h1 class="page-empty__title">\u0421\u0442\u0440\u0430\u043D\u0438\u0446\u0430 \u043D\u0435 \u043D\u0430\u0439\u0434\u0435\u043D\u0430</h1>
          <p class="page-empty__text">
            \u0410\u0434\u0440\u0435\u0441, \u043F\u043E \u043A\u043E\u0442\u043E\u0440\u043E\u043C\u0443 \u0432\u044B \u043F\u0435\u0440\u0435\u0448\u043B\u0438, \u043D\u0435 \u0441\u0443\u0449\u0435\u0441\u0442\u0432\u0443\u0435\u0442 \u0438\u043B\u0438 \u0431\u044B\u043B \u043F\u0435\u0440\u0435\u043C\u0435\u0449\u0451\u043D.
            \u041F\u0440\u043E\u0432\u0435\u0440\u044C\u0442\u0435 \u0441\u0441\u044B\u043B\u043A\u0443 \u0438\u043B\u0438 \u0432\u0435\u0440\u043D\u0438\u0442\u0435\u0441\u044C \u043D\u0430 \u0433\u043B\u0430\u0432\u043D\u0443\u044E.
          </p>
          <a class="btn-brand" [routerLink]="dashboardLink()" style="margin-top: var(--space-4);">
            <i class="ph ph-arrow-left" aria-hidden="true"></i>
            \u041D\u0430 \u0433\u043B\u0430\u0432\u043D\u0443\u044E
          </a>
        </div>
      </div>
    </section>
  `
    }]
  }], null, null);
})();
(() => {
  (typeof ngDevMode === "undefined" || ngDevMode) && \u0275setClassDebugInfo(NotFoundComponent, { className: "NotFoundComponent", filePath: "src/app/features/not-found/not-found.component.ts", lineNumber: 40 });
})();
export {
  NotFoundComponent
};
//# sourceMappingURL=chunk-TOES5QDD.js.map
