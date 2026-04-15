import "./chunk-OYYF72SB.js";
import {
  animate,
  style,
  transition,
  trigger
} from "./chunk-JKCQIGK6.js";
import "./chunk-CLRYRPPS.js";
import {
  ChangeDetectionStrategy,
  Component,
  setClassMetadata,
  ɵsetClassDebugInfo,
  ɵɵdefineComponent,
  ɵɵelement,
  ɵɵelementEnd,
  ɵɵelementStart,
  ɵɵsyntheticHostProperty,
  ɵɵtext
} from "./chunk-4M4FDLBS.js";

// src/app/features/headman/late-checkin/headman-late-checkin.component.ts
var HeadmanLateCheckinComponent = class _HeadmanLateCheckinComponent {
  static \u0275fac = function HeadmanLateCheckinComponent_Factory(__ngFactoryType__) {
    return new (__ngFactoryType__ || _HeadmanLateCheckinComponent)();
  };
  static \u0275cmp = /* @__PURE__ */ \u0275\u0275defineComponent({ type: _HeadmanLateCheckinComponent, selectors: [["app-headman-late-checkin"]], hostVars: 1, hostBindings: function HeadmanLateCheckinComponent_HostBindings(rf, ctx) {
    if (rf & 2) {
      \u0275\u0275syntheticHostProperty("@routeFade", void 0);
    }
  }, decls: 13, vars: 0, consts: [[1, "page-header"], [1, "page-header__eyebrow"], [1, "page-card"], ["role", "status", "aria-live", "polite", 1, "page-empty"], [1, "page-empty__icon"], [1, "ph", "ph-clock-countdown"], [1, "page-empty__title"], [1, "page-empty__text"]], template: function HeadmanLateCheckinComponent_Template(rf, ctx) {
    if (rf & 1) {
      \u0275\u0275elementStart(0, "div", 0)(1, "h1");
      \u0275\u0275text(2, "\u0417\u0430\u043F\u0440\u043E\u0441\u044B \u043E\u0442\u043C\u0435\u0442\u043A\u0438");
      \u0275\u0275elementEnd();
      \u0275\u0275elementStart(3, "span", 1);
      \u0275\u0275text(4, "\u0421\u0442\u0430\u0440\u043E\u0441\u0442\u0430");
      \u0275\u0275elementEnd()();
      \u0275\u0275elementStart(5, "div", 2)(6, "div", 3)(7, "div", 4);
      \u0275\u0275element(8, "i", 5);
      \u0275\u0275elementEnd();
      \u0275\u0275elementStart(9, "p", 6);
      \u0275\u0275text(10, "\u041D\u0435\u0442 \u0430\u043A\u0442\u0438\u0432\u043D\u044B\u0445 \u0437\u0430\u043F\u0440\u043E\u0441\u043E\u0432");
      \u0275\u0275elementEnd();
      \u0275\u0275elementStart(11, "p", 7);
      \u0275\u0275text(12, "\u0424\u0443\u043D\u043A\u0446\u0438\u044F \u043D\u0430\u0445\u043E\u0434\u0438\u0442\u0441\u044F \u0432 \u0440\u0430\u0437\u0440\u0430\u0431\u043E\u0442\u043A\u0435. \u0417\u0430\u044F\u0432\u043A\u0438 \u043F\u043E\u044F\u0432\u044F\u0442\u0441\u044F \u0437\u0434\u0435\u0441\u044C \u0430\u0432\u0442\u043E\u043C\u0430\u0442\u0438\u0447\u0435\u0441\u043A\u0438.");
      \u0275\u0275elementEnd()()();
    }
  }, encapsulation: 2, data: { animation: [
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
    args: [{
      selector: "app-headman-late-checkin",
      standalone: true,
      changeDetection: ChangeDetectionStrategy.OnPush,
      imports: [],
      animations: [
        trigger("routeFade", [
          transition(":enter", [
            style({ opacity: 0, transform: "translateY(8px)" }),
            animate("200ms cubic-bezier(0.16, 1, 0.3, 1)", style({ opacity: 1, transform: "translateY(0)" }))
          ])
        ])
      ],
      host: { "[@routeFade]": "" },
      template: `
    <div class="page-header">
      <h1>\u0417\u0430\u043F\u0440\u043E\u0441\u044B \u043E\u0442\u043C\u0435\u0442\u043A\u0438</h1>
      <span class="page-header__eyebrow">\u0421\u0442\u0430\u0440\u043E\u0441\u0442\u0430</span>
    </div>
    <div class="page-card">
      <div class="page-empty" role="status" aria-live="polite">
        <div class="page-empty__icon"><i class="ph ph-clock-countdown"></i></div>
        <p class="page-empty__title">\u041D\u0435\u0442 \u0430\u043A\u0442\u0438\u0432\u043D\u044B\u0445 \u0437\u0430\u043F\u0440\u043E\u0441\u043E\u0432</p>
        <p class="page-empty__text">\u0424\u0443\u043D\u043A\u0446\u0438\u044F \u043D\u0430\u0445\u043E\u0434\u0438\u0442\u0441\u044F \u0432 \u0440\u0430\u0437\u0440\u0430\u0431\u043E\u0442\u043A\u0435. \u0417\u0430\u044F\u0432\u043A\u0438 \u043F\u043E\u044F\u0432\u044F\u0442\u0441\u044F \u0437\u0434\u0435\u0441\u044C \u0430\u0432\u0442\u043E\u043C\u0430\u0442\u0438\u0447\u0435\u0441\u043A\u0438.</p>
      </div>
    </div>
  `
    }]
  }], null, null);
})();
(() => {
  (typeof ngDevMode === "undefined" || ngDevMode) && \u0275setClassDebugInfo(HeadmanLateCheckinComponent, { className: "HeadmanLateCheckinComponent", filePath: "src/app/features/headman/late-checkin/headman-late-checkin.component.ts", lineNumber: 32 });
})();
export {
  HeadmanLateCheckinComponent
};
//# sourceMappingURL=chunk-KJOUKWRD.js.map
