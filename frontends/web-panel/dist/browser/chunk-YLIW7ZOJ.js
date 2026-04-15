import {
  ChangeDetectionStrategy,
  Component,
  computed,
  input,
  setClassMetadata,
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
  ɵɵnamespaceSVG,
  ɵɵnextContext,
  ɵɵtemplate,
  ɵɵtext,
  ɵɵtextInterpolate,
  ɵɵtextInterpolate1
} from "./chunk-4M4FDLBS.js";

// src/app/features/admin/dashboard/stat-card/stat-card.component.ts
function StatCardComponent_Conditional_4_Template(rf, ctx) {
  if (rf & 1) {
    \u0275\u0275elementStart(0, "span", 4);
    \u0275\u0275element(1, "i");
    \u0275\u0275text(2);
    \u0275\u0275elementEnd();
  }
  if (rf & 2) {
    const ctx_r0 = \u0275\u0275nextContext();
    \u0275\u0275classProp("stat-card__trend--up", ctx_r0.trendPositive())("stat-card__trend--down", !ctx_r0.trendPositive());
    \u0275\u0275advance();
    \u0275\u0275classMap(ctx_r0.trendPositive() ? "ph ph-trend-up" : "ph ph-trend-down");
    \u0275\u0275advance();
    \u0275\u0275textInterpolate1(" ", ctx_r0.trendLabel(), " ");
  }
}
function StatCardComponent_Conditional_5_Template(rf, ctx) {
  if (rf & 1) {
    \u0275\u0275element(0, "div", 5)(1, "div", 6);
  }
}
function StatCardComponent_Conditional_6_Conditional_3_Template(rf, ctx) {
  if (rf & 1) {
    \u0275\u0275elementStart(0, "span", 9);
    \u0275\u0275text(1);
    \u0275\u0275elementEnd();
  }
  if (rf & 2) {
    const ctx_r0 = \u0275\u0275nextContext(2);
    \u0275\u0275advance();
    \u0275\u0275textInterpolate(ctx_r0.suffix());
  }
}
function StatCardComponent_Conditional_6_Conditional_6_Template(rf, ctx) {
  if (rf & 1) {
    \u0275\u0275namespaceSVG();
    \u0275\u0275elementStart(0, "svg", 11);
    \u0275\u0275element(1, "path", 12)(2, "path", 13);
    \u0275\u0275elementStart(3, "defs")(4, "linearGradient", 14);
    \u0275\u0275element(5, "stop", 15)(6, "stop", 16);
    \u0275\u0275elementEnd()()();
  }
  if (rf & 2) {
    const ctx_r0 = \u0275\u0275nextContext(2);
    \u0275\u0275attribute("viewBox", "0 0 100 32");
    \u0275\u0275advance();
    \u0275\u0275attribute("d", ctx_r0.sparkPath());
    \u0275\u0275advance();
    \u0275\u0275attribute("d", ctx_r0.sparkAreaPath());
  }
}
function StatCardComponent_Conditional_6_Template(rf, ctx) {
  if (rf & 1) {
    \u0275\u0275elementStart(0, "div", 7)(1, "span", 8);
    \u0275\u0275text(2);
    \u0275\u0275elementEnd();
    \u0275\u0275template(3, StatCardComponent_Conditional_6_Conditional_3_Template, 2, 1, "span", 9);
    \u0275\u0275elementEnd();
    \u0275\u0275elementStart(4, "div", 10);
    \u0275\u0275text(5);
    \u0275\u0275elementEnd();
    \u0275\u0275template(6, StatCardComponent_Conditional_6_Conditional_6_Template, 7, 3, ":svg:svg", 11);
  }
  if (rf & 2) {
    const ctx_r0 = \u0275\u0275nextContext();
    \u0275\u0275advance(2);
    \u0275\u0275textInterpolate(ctx_r0.value());
    \u0275\u0275advance();
    \u0275\u0275conditional(ctx_r0.suffix() ? 3 : -1);
    \u0275\u0275advance(2);
    \u0275\u0275textInterpolate(ctx_r0.label());
    \u0275\u0275advance();
    \u0275\u0275conditional(ctx_r0.sparkData() && ctx_r0.sparkData().length > 1 ? 6 : -1);
  }
}
var StatCardComponent = class _StatCardComponent {
  value = input.required();
  label = input.required();
  icon = input.required();
  /** Deprecated — color now comes from `accent` token. Kept for backwards compat. */
  iconColor = input("");
  loading = input(false);
  accent = input("primary");
  sparkData = input(null);
  trend = input(null);
  suffix = input("");
  trendPositive = computed(() => (this.trend() ?? 0) >= 0);
  trendLabel = computed(() => {
    const t = this.trend();
    if (t === null || t === void 0)
      return "";
    const sign = t > 0 ? "+" : "";
    return `${sign}${t}%`;
  });
  /** Normalize sparkline data into an SVG path `M` + `L` sequence. */
  sparkPath = computed(() => buildSparkPath(this.sparkData(), false));
  sparkAreaPath = computed(() => buildSparkPath(this.sparkData(), true));
  static \u0275fac = function StatCardComponent_Factory(__ngFactoryType__) {
    return new (__ngFactoryType__ || _StatCardComponent)();
  };
  static \u0275cmp = /* @__PURE__ */ \u0275\u0275defineComponent({ type: _StatCardComponent, selectors: [["app-stat-card"]], inputs: { value: [1, "value"], label: [1, "label"], icon: [1, "icon"], iconColor: [1, "iconColor"], loading: [1, "loading"], accent: [1, "accent"], sparkData: [1, "sparkData"], trend: [1, "trend"], suffix: [1, "suffix"] }, decls: 7, vars: 5, consts: [[1, "stat-card"], [1, "stat-card__head"], ["aria-hidden", "true", 1, "stat-card__icon"], [1, "stat-card__trend", 3, "stat-card__trend--up", "stat-card__trend--down"], [1, "stat-card__trend"], ["aria-hidden", "true", 1, "stat-card__skeleton"], ["aria-hidden", "true", 1, "stat-card__skeleton", "stat-card__skeleton--sm"], [1, "stat-card__value"], [1, "stat-card__number"], [1, "stat-card__suffix"], [1, "stat-card__label"], ["preserveAspectRatio", "none", "aria-hidden", "true", 1, "stat-card__spark"], ["fill", "none", "stroke", "currentColor", "stroke-width", "2", "stroke-linecap", "round", "stroke-linejoin", "round"], ["fill", "url(#sparkGrad)", "opacity", "0.18"], ["id", "sparkGrad", "x1", "0", "x2", "0", "y1", "0", "y2", "1"], ["offset", "0%", "stop-color", "currentColor"], ["offset", "100%", "stop-color", "currentColor", "stop-opacity", "0"]], template: function StatCardComponent_Template(rf, ctx) {
    if (rf & 1) {
      \u0275\u0275elementStart(0, "article", 0)(1, "div", 1)(2, "div", 2);
      \u0275\u0275element(3, "i");
      \u0275\u0275elementEnd();
      \u0275\u0275template(4, StatCardComponent_Conditional_4_Template, 3, 7, "span", 3);
      \u0275\u0275elementEnd();
      \u0275\u0275template(5, StatCardComponent_Conditional_5_Template, 2, 0)(6, StatCardComponent_Conditional_6_Template, 7, 4);
      \u0275\u0275elementEnd();
    }
    if (rf & 2) {
      \u0275\u0275attribute("data-accent", ctx.accent());
      \u0275\u0275advance(3);
      \u0275\u0275classMap(ctx.icon());
      \u0275\u0275advance();
      \u0275\u0275conditional(ctx.trend() !== null ? 4 : -1);
      \u0275\u0275advance();
      \u0275\u0275conditional(ctx.loading() ? 5 : 6);
    }
  }, styles: ['\n\n[_nghost-%COMP%] {\n  display: block;\n}\n.stat-card[_ngcontent-%COMP%] {\n  --stat-color: var(--accent-primary);\n  position: relative;\n  padding: var(--space-5);\n  background: var(--bg-secondary);\n  border: 1px solid var(--border-subtle);\n  border-radius: var(--radius-lg);\n  display: flex;\n  flex-direction: column;\n  gap: var(--space-3);\n  overflow: hidden;\n  isolation: isolate;\n  min-height: 168px;\n  transition:\n    transform var(--duration-slow) var(--ease-out),\n    border-color var(--duration-slow) var(--ease-out),\n    box-shadow var(--duration-slow) var(--ease-out),\n    background-color var(--duration-slow) var(--ease-out);\n}\n.stat-card[data-accent=secondary][_ngcontent-%COMP%] {\n  --stat-color: var(--accent-secondary);\n}\n.stat-card[data-accent=warning][_ngcontent-%COMP%] {\n  --stat-color: var(--accent-warning);\n}\n.stat-card[data-accent=info][_ngcontent-%COMP%] {\n  --stat-color: var(--accent-info);\n}\n.stat-card[data-accent=danger][_ngcontent-%COMP%] {\n  --stat-color: var(--accent-danger);\n}\n.stat-card[_ngcontent-%COMP%]::before {\n  content: "";\n  position: absolute;\n  top: -40%;\n  right: -30%;\n  width: 160px;\n  height: 160px;\n  background:\n    radial-gradient(\n      circle,\n      color-mix(in oklab, var(--stat-color) 22%, transparent),\n      transparent 70%);\n  pointer-events: none;\n  z-index: -1;\n  opacity: 0.7;\n  transition: opacity var(--duration-slow) var(--ease-out), transform var(--duration-slow) var(--ease-out);\n}\n.stat-card[_ngcontent-%COMP%]:hover {\n  transform: translateY(-3px);\n  border-color: color-mix(in oklab, var(--stat-color) 40%, transparent);\n  box-shadow: 0 0 24px color-mix(in oklab, var(--stat-color) 18%, transparent), var(--shadow-md);\n}\n.stat-card[_ngcontent-%COMP%]:hover::before {\n  opacity: 1;\n  transform: scale(1.05);\n}\n.stat-card__head[_ngcontent-%COMP%] {\n  display: flex;\n  align-items: center;\n  justify-content: space-between;\n  gap: var(--space-3);\n}\n.stat-card__icon[_ngcontent-%COMP%] {\n  width: 44px;\n  height: 44px;\n  border-radius: var(--radius-md);\n  display: grid;\n  place-items: center;\n  background: color-mix(in oklab, var(--stat-color) 14%, transparent);\n  border: 1px solid color-mix(in oklab, var(--stat-color) 32%, transparent);\n  color: var(--stat-color);\n  flex-shrink: 0;\n}\n.stat-card__icon[_ngcontent-%COMP%]   i[_ngcontent-%COMP%] {\n  font-size: 22px;\n  line-height: 1;\n}\n.stat-card__trend[_ngcontent-%COMP%] {\n  display: inline-flex;\n  align-items: center;\n  gap: 4px;\n  padding: 4px 10px;\n  border-radius: var(--radius-full);\n  font-family: var(--font-mono);\n  font-size: 0.6875rem;\n  font-weight: 500;\n  line-height: 1;\n}\n.stat-card__trend--up[_ngcontent-%COMP%] {\n  background: color-mix(in oklab, var(--accent-primary) 14%, transparent);\n  color: var(--accent-primary);\n}\n.stat-card__trend--down[_ngcontent-%COMP%] {\n  background: color-mix(in oklab, var(--accent-danger) 14%, transparent);\n  color: var(--accent-danger);\n}\n.stat-card__trend[_ngcontent-%COMP%]   i[_ngcontent-%COMP%] {\n  font-size: 12px;\n}\n.stat-card__value[_ngcontent-%COMP%] {\n  display: flex;\n  align-items: baseline;\n  gap: 4px;\n  margin-top: auto;\n}\n.stat-card__number[_ngcontent-%COMP%] {\n  font-family: var(--font-display);\n  font-weight: 700;\n  font-size: clamp(2rem, 2.4vw + 1rem, 2.75rem);\n  line-height: 1;\n  letter-spacing: -0.02em;\n  color: var(--text-primary);\n}\n.stat-card__suffix[_ngcontent-%COMP%] {\n  font-family: var(--font-display);\n  font-weight: 600;\n  font-size: 1.125rem;\n  color: var(--text-secondary);\n}\n.stat-card__label[_ngcontent-%COMP%] {\n  font-family: var(--font-mono);\n  font-size: 0.6875rem;\n  font-weight: 500;\n  letter-spacing: 0.06em;\n  text-transform: uppercase;\n  color: var(--text-muted);\n}\n.stat-card__spark[_ngcontent-%COMP%] {\n  height: 32px;\n  width: 100%;\n  margin-top: var(--space-2);\n  color: var(--stat-color);\n  overflow: visible;\n}\n.stat-card__skeleton[_ngcontent-%COMP%] {\n  height: 42px;\n  background: color-mix(in oklab, var(--text-primary) 6%, transparent);\n  border-radius: var(--radius-sm);\n  margin-top: auto;\n  animation: _ngcontent-%COMP%_stat-shimmer 1.4s ease-in-out infinite;\n}\n.stat-card__skeleton--sm[_ngcontent-%COMP%] {\n  height: 12px;\n  width: 60%;\n  margin-top: var(--space-2);\n}\n@keyframes _ngcontent-%COMP%_stat-shimmer {\n  0%, 100% {\n    opacity: 0.55;\n  }\n  50% {\n    opacity: 0.9;\n  }\n}\n@media (prefers-reduced-motion: reduce) {\n  .stat-card[_ngcontent-%COMP%], \n   .stat-card[_ngcontent-%COMP%]::before, \n   .stat-card__skeleton[_ngcontent-%COMP%] {\n    transition: none;\n    animation: none;\n  }\n}\n/*# sourceMappingURL=stat-card.component.css.map */'], changeDetection: 0 });
};
(() => {
  (typeof ngDevMode === "undefined" || ngDevMode) && setClassMetadata(StatCardComponent, [{
    type: Component,
    args: [{ selector: "app-stat-card", standalone: true, changeDetection: ChangeDetectionStrategy.OnPush, template: `
    <article class="stat-card" [attr.data-accent]="accent()">
      <div class="stat-card__head">
        <div class="stat-card__icon" aria-hidden="true">
          <i [class]="icon()"></i>
        </div>

        @if (trend() !== null) {
          <span
            class="stat-card__trend"
            [class.stat-card__trend--up]="trendPositive()"
            [class.stat-card__trend--down]="!trendPositive()"
          >
            <i [class]="trendPositive() ? 'ph ph-trend-up' : 'ph ph-trend-down'"></i>
            {{ trendLabel() }}
          </span>
        }
      </div>

      @if (loading()) {
        <div class="stat-card__skeleton" aria-hidden="true"></div>
        <div class="stat-card__skeleton stat-card__skeleton--sm" aria-hidden="true"></div>
      } @else {
        <div class="stat-card__value">
          <span class="stat-card__number">{{ value() }}</span>
          @if (suffix()) {
            <span class="stat-card__suffix">{{ suffix() }}</span>
          }
        </div>
        <div class="stat-card__label">{{ label() }}</div>

        @if (sparkData() && sparkData()!.length > 1) {
          <svg
            class="stat-card__spark"
            [attr.viewBox]="'0 0 100 32'"
            preserveAspectRatio="none"
            aria-hidden="true"
          >
            <path
              [attr.d]="sparkPath()"
              fill="none"
              stroke="currentColor"
              stroke-width="2"
              stroke-linecap="round"
              stroke-linejoin="round"
            />
            <path
              [attr.d]="sparkAreaPath()"
              fill="url(#sparkGrad)"
              opacity="0.18"
            />
            <defs>
              <linearGradient id="sparkGrad" x1="0" x2="0" y1="0" y2="1">
                <stop offset="0%" stop-color="currentColor" />
                <stop offset="100%" stop-color="currentColor" stop-opacity="0" />
              </linearGradient>
            </defs>
          </svg>
        }
      }
    </article>
  `, styles: ['/* src/app/features/admin/dashboard/stat-card/stat-card.component.css */\n:host {\n  display: block;\n}\n.stat-card {\n  --stat-color: var(--accent-primary);\n  position: relative;\n  padding: var(--space-5);\n  background: var(--bg-secondary);\n  border: 1px solid var(--border-subtle);\n  border-radius: var(--radius-lg);\n  display: flex;\n  flex-direction: column;\n  gap: var(--space-3);\n  overflow: hidden;\n  isolation: isolate;\n  min-height: 168px;\n  transition:\n    transform var(--duration-slow) var(--ease-out),\n    border-color var(--duration-slow) var(--ease-out),\n    box-shadow var(--duration-slow) var(--ease-out),\n    background-color var(--duration-slow) var(--ease-out);\n}\n.stat-card[data-accent=secondary] {\n  --stat-color: var(--accent-secondary);\n}\n.stat-card[data-accent=warning] {\n  --stat-color: var(--accent-warning);\n}\n.stat-card[data-accent=info] {\n  --stat-color: var(--accent-info);\n}\n.stat-card[data-accent=danger] {\n  --stat-color: var(--accent-danger);\n}\n.stat-card::before {\n  content: "";\n  position: absolute;\n  top: -40%;\n  right: -30%;\n  width: 160px;\n  height: 160px;\n  background:\n    radial-gradient(\n      circle,\n      color-mix(in oklab, var(--stat-color) 22%, transparent),\n      transparent 70%);\n  pointer-events: none;\n  z-index: -1;\n  opacity: 0.7;\n  transition: opacity var(--duration-slow) var(--ease-out), transform var(--duration-slow) var(--ease-out);\n}\n.stat-card:hover {\n  transform: translateY(-3px);\n  border-color: color-mix(in oklab, var(--stat-color) 40%, transparent);\n  box-shadow: 0 0 24px color-mix(in oklab, var(--stat-color) 18%, transparent), var(--shadow-md);\n}\n.stat-card:hover::before {\n  opacity: 1;\n  transform: scale(1.05);\n}\n.stat-card__head {\n  display: flex;\n  align-items: center;\n  justify-content: space-between;\n  gap: var(--space-3);\n}\n.stat-card__icon {\n  width: 44px;\n  height: 44px;\n  border-radius: var(--radius-md);\n  display: grid;\n  place-items: center;\n  background: color-mix(in oklab, var(--stat-color) 14%, transparent);\n  border: 1px solid color-mix(in oklab, var(--stat-color) 32%, transparent);\n  color: var(--stat-color);\n  flex-shrink: 0;\n}\n.stat-card__icon i {\n  font-size: 22px;\n  line-height: 1;\n}\n.stat-card__trend {\n  display: inline-flex;\n  align-items: center;\n  gap: 4px;\n  padding: 4px 10px;\n  border-radius: var(--radius-full);\n  font-family: var(--font-mono);\n  font-size: 0.6875rem;\n  font-weight: 500;\n  line-height: 1;\n}\n.stat-card__trend--up {\n  background: color-mix(in oklab, var(--accent-primary) 14%, transparent);\n  color: var(--accent-primary);\n}\n.stat-card__trend--down {\n  background: color-mix(in oklab, var(--accent-danger) 14%, transparent);\n  color: var(--accent-danger);\n}\n.stat-card__trend i {\n  font-size: 12px;\n}\n.stat-card__value {\n  display: flex;\n  align-items: baseline;\n  gap: 4px;\n  margin-top: auto;\n}\n.stat-card__number {\n  font-family: var(--font-display);\n  font-weight: 700;\n  font-size: clamp(2rem, 2.4vw + 1rem, 2.75rem);\n  line-height: 1;\n  letter-spacing: -0.02em;\n  color: var(--text-primary);\n}\n.stat-card__suffix {\n  font-family: var(--font-display);\n  font-weight: 600;\n  font-size: 1.125rem;\n  color: var(--text-secondary);\n}\n.stat-card__label {\n  font-family: var(--font-mono);\n  font-size: 0.6875rem;\n  font-weight: 500;\n  letter-spacing: 0.06em;\n  text-transform: uppercase;\n  color: var(--text-muted);\n}\n.stat-card__spark {\n  height: 32px;\n  width: 100%;\n  margin-top: var(--space-2);\n  color: var(--stat-color);\n  overflow: visible;\n}\n.stat-card__skeleton {\n  height: 42px;\n  background: color-mix(in oklab, var(--text-primary) 6%, transparent);\n  border-radius: var(--radius-sm);\n  margin-top: auto;\n  animation: stat-shimmer 1.4s ease-in-out infinite;\n}\n.stat-card__skeleton--sm {\n  height: 12px;\n  width: 60%;\n  margin-top: var(--space-2);\n}\n@keyframes stat-shimmer {\n  0%, 100% {\n    opacity: 0.55;\n  }\n  50% {\n    opacity: 0.9;\n  }\n}\n@media (prefers-reduced-motion: reduce) {\n  .stat-card,\n  .stat-card::before,\n  .stat-card__skeleton {\n    transition: none;\n    animation: none;\n  }\n}\n/*# sourceMappingURL=stat-card.component.css.map */\n'] }]
  }], null, null);
})();
(() => {
  (typeof ngDevMode === "undefined" || ngDevMode) && \u0275setClassDebugInfo(StatCardComponent, { className: "StatCardComponent", filePath: "src/app/features/admin/dashboard/stat-card/stat-card.component.ts", lineNumber: 83 });
})();
function buildSparkPath(data, asArea) {
  if (!data || data.length < 2)
    return "";
  const min = Math.min(...data);
  const max = Math.max(...data);
  const range = max - min || 1;
  const w = 100;
  const h = 32;
  const stepX = w / (data.length - 1);
  const points = data.map((v, i) => {
    const x = i * stepX;
    const y = h - (v - min) / range * (h - 4) - 2;
    return [x, y];
  });
  const line = points.map(([x, y], i) => `${i === 0 ? "M" : "L"}${x.toFixed(1)},${y.toFixed(1)}`).join(" ");
  if (!asArea)
    return line;
  const last = points[points.length - 1];
  return `${line} L${last[0].toFixed(1)},${h} L0,${h} Z`;
}

export {
  StatCardComponent
};
//# sourceMappingURL=chunk-YLIW7ZOJ.js.map
