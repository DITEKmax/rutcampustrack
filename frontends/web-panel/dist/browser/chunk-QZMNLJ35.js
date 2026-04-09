import {
  AdminApiService
} from "./chunk-W5VNZ3AB.js";
import {
  RouterLink
} from "./chunk-LV56B2AI.js";
import "./chunk-LGL42SQS.js";
import "./chunk-T4R75CF5.js";
import "./chunk-L2FQVI4C.js";
import {
  ChangeDetectionStrategy,
  Component,
  DestroyRef,
  computed,
  inject,
  input,
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
  ɵɵnamespaceSVG,
  ɵɵnextContext,
  ɵɵproperty,
  ɵɵpropertyInterpolate,
  ɵɵtemplate,
  ɵɵtext,
  ɵɵtextInterpolate,
  ɵɵtextInterpolate1
} from "./chunk-WGFJ2PWY.js";

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

// src/app/features/admin/dashboard/admin-dashboard.component.ts
function AdminDashboardComponent_Conditional_22_Template(rf, ctx) {
  if (rf & 1) {
    \u0275\u0275elementStart(0, "div", 15);
    \u0275\u0275element(1, "i", 32);
    \u0275\u0275text(2);
    \u0275\u0275elementEnd();
  }
  if (rf & 2) {
    const ctx_r0 = \u0275\u0275nextContext();
    \u0275\u0275advance(2);
    \u0275\u0275textInterpolate1(" ", ctx_r0.error(), " ");
  }
}
var AdminDashboardComponent = class _AdminDashboardComponent {
  adminApi = inject(AdminApiService);
  destroyRef = inject(DestroyRef);
  stats = signal(null);
  loading = signal(false);
  error = signal(null);
  /** Live clock — refreshed once a minute. */
  _now = signal(/* @__PURE__ */ new Date());
  timeLabel = computed(() => this._now().toLocaleTimeString("ru-RU", { hour: "2-digit", minute: "2-digit" }));
  dateLabel = computed(() => this._now().toLocaleDateString("ru-RU", {
    weekday: "long",
    day: "numeric",
    month: "long"
  }));
  greeting = computed(() => {
    const hour = this._now().getHours();
    if (hour < 6)
      return "\u0414\u043E\u0431\u0440\u043E\u0439 \u043D\u043E\u0447\u0438";
    if (hour < 12)
      return "\u0414\u043E\u0431\u0440\u043E\u0435 \u0443\u0442\u0440\u043E";
    if (hour < 18)
      return "\u0414\u043E\u0431\u0440\u044B\u0439 \u0434\u0435\u043D\u044C";
    return "\u0414\u043E\u0431\u0440\u044B\u0439 \u0432\u0435\u0447\u0435\u0440";
  });
  /** Share of groups that are active (0-100). */
  activeGroupsPct = computed(() => {
    const s = this.stats();
    if (!s || s.totalGroups === 0)
      return 0;
    return Math.round(s.activeGroups / s.totalGroups * 100);
  });
  ngOnInit() {
    this.loading.set(true);
    this.adminApi.getDashboardStats().subscribe({
      next: (data) => {
        this.stats.set(data);
        this.loading.set(false);
      },
      error: () => {
        this.error.set("\u041D\u0435 \u0443\u0434\u0430\u043B\u043E\u0441\u044C \u0437\u0430\u0433\u0440\u0443\u0437\u0438\u0442\u044C \u0441\u0432\u043E\u0434\u043A\u0443. \u041F\u043E\u043F\u0440\u043E\u0431\u0443\u0439\u0442\u0435 \u043F\u043E\u0437\u0436\u0435.");
        this.loading.set(false);
      }
    });
    const tick = setInterval(() => this._now.set(/* @__PURE__ */ new Date()), 6e4);
    this.destroyRef.onDestroy(() => clearInterval(tick));
  }
  static \u0275fac = function AdminDashboardComponent_Factory(__ngFactoryType__) {
    return new (__ngFactoryType__ || _AdminDashboardComponent)();
  };
  static \u0275cmp = /* @__PURE__ */ \u0275\u0275defineComponent({ type: _AdminDashboardComponent, selectors: [["app-admin-dashboard"]], decls: 57, vars: 14, consts: [[1, "dashboard"], [1, "dashboard__hero"], [1, "dashboard__greeting"], [1, "dashboard__eyebrow"], ["aria-hidden", "true", 1, "dashboard__pulse"], [1, "dashboard__title"], [1, "dashboard__subtitle"], ["aria-hidden", "true", 1, "dashboard__clock"], [1, "dashboard__clock-value"], [1, "dashboard__clock-label"], [1, "dashboard__grid"], ["accent", "primary", "icon", "ph-duotone ph-students", "label", "\u0421\u0442\u0443\u0434\u0435\u043D\u0442\u043E\u0432", 3, "value", "loading"], ["accent", "secondary", "icon", "ph-duotone ph-chalkboard-teacher", "label", "\u041F\u0440\u0435\u043F\u043E\u0434\u0430\u0432\u0430\u0442\u0435\u043B\u0435\u0439", 3, "value", "loading"], ["accent", "warning", "icon", "ph-duotone ph-users-three", "label", "\u0413\u0440\u0443\u043F\u043F \u0432\u0441\u0435\u0433\u043E", 3, "value", "loading"], ["accent", "info", "icon", "ph-duotone ph-buildings", "label", "\u0410\u043A\u0442\u0438\u0432\u043D\u044B\u0445 \u0433\u0440\u0443\u043F\u043F", 3, "value", "suffix", "loading"], ["role", "alert", 1, "dashboard__error"], [1, "dashboard__section"], [1, "dashboard__section-head"], [1, "dashboard__section-title"], [1, "dashboard__section-sub"], [1, "dashboard__actions"], ["routerLink", "/admin/users", "data-accent", "primary", 1, "action-card"], ["aria-hidden", "true", 1, "action-card__icon"], [1, "ph-duotone", "ph-users"], [1, "action-card__body"], [1, "action-card__title"], [1, "action-card__text"], ["aria-hidden", "true", 1, "ph", "ph-arrow-right", "action-card__arrow"], ["routerLink", "/admin/groups", "data-accent", "warning", 1, "action-card"], [1, "ph-duotone", "ph-users-three"], ["routerLink", "/admin/semesters", "data-accent", "info", 1, "action-card"], [1, "ph-duotone", "ph-calendar"], ["aria-hidden", "true", 1, "ph", "ph-warning-circle"]], template: function AdminDashboardComponent_Template(rf, ctx) {
    if (rf & 1) {
      \u0275\u0275elementStart(0, "section", 0)(1, "header", 1)(2, "div", 2)(3, "p", 3);
      \u0275\u0275element(4, "span", 4);
      \u0275\u0275text(5, " \u0421\u0438\u0441\u0442\u0435\u043C\u0430 \u0430\u043A\u0442\u0438\u0432\u043D\u0430 ");
      \u0275\u0275elementEnd();
      \u0275\u0275elementStart(6, "h2", 5);
      \u0275\u0275text(7);
      \u0275\u0275elementEnd();
      \u0275\u0275elementStart(8, "p", 6);
      \u0275\u0275text(9);
      \u0275\u0275elementStart(10, "strong");
      \u0275\u0275text(11);
      \u0275\u0275elementEnd()()();
      \u0275\u0275elementStart(12, "div", 7)(13, "span", 8);
      \u0275\u0275text(14);
      \u0275\u0275elementEnd();
      \u0275\u0275elementStart(15, "span", 9);
      \u0275\u0275text(16, "\u043F\u043E \u041C\u043E\u0441\u043A\u0432\u0435");
      \u0275\u0275elementEnd()()();
      \u0275\u0275elementStart(17, "div", 10);
      \u0275\u0275element(18, "app-stat-card", 11)(19, "app-stat-card", 12)(20, "app-stat-card", 13)(21, "app-stat-card", 14);
      \u0275\u0275elementEnd();
      \u0275\u0275template(22, AdminDashboardComponent_Conditional_22_Template, 3, 1, "div", 15);
      \u0275\u0275elementStart(23, "div", 16)(24, "div", 17)(25, "h3", 18);
      \u0275\u0275text(26, "\u0411\u044B\u0441\u0442\u0440\u044B\u0435 \u0434\u0435\u0439\u0441\u0442\u0432\u0438\u044F");
      \u0275\u0275elementEnd();
      \u0275\u0275elementStart(27, "p", 19);
      \u0275\u0275text(28, "\u0427\u0430\u0441\u0442\u043E \u0438\u0441\u043F\u043E\u043B\u044C\u0437\u0443\u0435\u043C\u044B\u0435 \u0440\u0430\u0437\u0434\u0435\u043B\u044B \u043F\u0430\u043D\u0435\u043B\u0438");
      \u0275\u0275elementEnd()();
      \u0275\u0275elementStart(29, "div", 20)(30, "a", 21)(31, "div", 22);
      \u0275\u0275element(32, "i", 23);
      \u0275\u0275elementEnd();
      \u0275\u0275elementStart(33, "div", 24)(34, "h4", 25);
      \u0275\u0275text(35, "\u041F\u043E\u043B\u044C\u0437\u043E\u0432\u0430\u0442\u0435\u043B\u0438");
      \u0275\u0275elementEnd();
      \u0275\u0275elementStart(36, "p", 26);
      \u0275\u0275text(37, "\u0421\u043E\u0437\u0434\u0430\u0442\u044C, \u0440\u0435\u0434\u0430\u043A\u0442\u0438\u0440\u043E\u0432\u0430\u0442\u044C, \u0430\u0440\u0445\u0438\u0432\u0438\u0440\u043E\u0432\u0430\u0442\u044C \u0443\u0447\u0451\u0442\u043D\u044B\u0435 \u0437\u0430\u043F\u0438\u0441\u0438");
      \u0275\u0275elementEnd()();
      \u0275\u0275element(38, "i", 27);
      \u0275\u0275elementEnd();
      \u0275\u0275elementStart(39, "a", 28)(40, "div", 22);
      \u0275\u0275element(41, "i", 29);
      \u0275\u0275elementEnd();
      \u0275\u0275elementStart(42, "div", 24)(43, "h4", 25);
      \u0275\u0275text(44, "\u0413\u0440\u0443\u043F\u043F\u044B");
      \u0275\u0275elementEnd();
      \u0275\u0275elementStart(45, "p", 26);
      \u0275\u0275text(46, "\u0423\u0447\u0435\u0431\u043D\u044B\u0435 \u0433\u0440\u0443\u043F\u043F\u044B \u0438 \u0438\u0445 \u0441\u043E\u0441\u0442\u0430\u0432");
      \u0275\u0275elementEnd()();
      \u0275\u0275element(47, "i", 27);
      \u0275\u0275elementEnd();
      \u0275\u0275elementStart(48, "a", 30)(49, "div", 22);
      \u0275\u0275element(50, "i", 31);
      \u0275\u0275elementEnd();
      \u0275\u0275elementStart(51, "div", 24)(52, "h4", 25);
      \u0275\u0275text(53, "\u0421\u0435\u043C\u0435\u0441\u0442\u0440\u044B");
      \u0275\u0275elementEnd();
      \u0275\u0275elementStart(54, "p", 26);
      \u0275\u0275text(55, "\u0420\u0430\u0441\u043F\u0438\u0441\u0430\u043D\u0438\u0435 \u0443\u0447\u0435\u0431\u043D\u044B\u0445 \u043F\u0435\u0440\u0438\u043E\u0434\u043E\u0432");
      \u0275\u0275elementEnd()();
      \u0275\u0275element(56, "i", 27);
      \u0275\u0275elementEnd()()()();
    }
    if (rf & 2) {
      let tmp_2_0;
      let tmp_4_0;
      let tmp_6_0;
      let tmp_8_0;
      let tmp_11_0;
      \u0275\u0275advance(7);
      \u0275\u0275textInterpolate1("", ctx.greeting(), ", \u0430\u0434\u043C\u0438\u043D\u0438\u0441\u0442\u0440\u0430\u0442\u043E\u0440");
      \u0275\u0275advance(2);
      \u0275\u0275textInterpolate1(" ", ctx.dateLabel(), " \xB7 \u0442\u0435\u043A\u0443\u0449\u0438\u0439 \u0441\u0435\u043C\u0435\u0441\u0442\u0440 ");
      \u0275\u0275advance(2);
      \u0275\u0275textInterpolate((tmp_2_0 = (tmp_2_0 = ctx.stats()) == null ? null : tmp_2_0.activeSemesterName) !== null && tmp_2_0 !== void 0 ? tmp_2_0 : "\u2014");
      \u0275\u0275advance(3);
      \u0275\u0275textInterpolate(ctx.timeLabel());
      \u0275\u0275advance(4);
      \u0275\u0275property("value", (tmp_4_0 = (tmp_4_0 = ctx.stats()) == null ? null : tmp_4_0.totalStudents) !== null && tmp_4_0 !== void 0 ? tmp_4_0 : 0)("loading", ctx.loading());
      \u0275\u0275advance();
      \u0275\u0275property("value", (tmp_6_0 = (tmp_6_0 = ctx.stats()) == null ? null : tmp_6_0.totalTeachers) !== null && tmp_6_0 !== void 0 ? tmp_6_0 : 0)("loading", ctx.loading());
      \u0275\u0275advance();
      \u0275\u0275property("value", (tmp_8_0 = (tmp_8_0 = ctx.stats()) == null ? null : tmp_8_0.totalGroups) !== null && tmp_8_0 !== void 0 ? tmp_8_0 : 0)("loading", ctx.loading());
      \u0275\u0275advance();
      \u0275\u0275propertyInterpolate("suffix", ctx.activeGroupsPct() ? ctx.activeGroupsPct() + "%" : "");
      \u0275\u0275property("value", (tmp_11_0 = (tmp_11_0 = ctx.stats()) == null ? null : tmp_11_0.activeGroups) !== null && tmp_11_0 !== void 0 ? tmp_11_0 : 0)("loading", ctx.loading());
      \u0275\u0275advance();
      \u0275\u0275conditional(ctx.error() ? 22 : -1);
    }
  }, dependencies: [StatCardComponent, RouterLink], styles: ['\n\n[_nghost-%COMP%] {\n  display: block;\n}\n.dashboard[_ngcontent-%COMP%] {\n  display: flex;\n  flex-direction: column;\n  gap: var(--space-7);\n  animation: _ngcontent-%COMP%_dashboard-in 0.6s var(--ease-out) both;\n}\n@keyframes _ngcontent-%COMP%_dashboard-in {\n  from {\n    opacity: 0;\n    transform: translateY(12px);\n  }\n  to {\n    opacity: 1;\n    transform: translateY(0);\n  }\n}\n.dashboard__hero[_ngcontent-%COMP%] {\n  display: flex;\n  align-items: flex-start;\n  justify-content: space-between;\n  gap: var(--space-5);\n  padding: var(--space-6);\n  border-radius: var(--radius-xl);\n  background:\n    radial-gradient(\n      ellipse at 15% 20%,\n      color-mix(in oklab, var(--accent-primary) 16%, transparent),\n      transparent 55%),\n    radial-gradient(\n      ellipse at 90% 80%,\n      color-mix(in oklab, var(--accent-secondary) 18%, transparent),\n      transparent 55%),\n    var(--bg-secondary);\n  border: 1px solid var(--border-default);\n  overflow: hidden;\n  position: relative;\n  isolation: isolate;\n}\n.dashboard__hero[_ngcontent-%COMP%]::before {\n  content: "";\n  position: absolute;\n  inset: 0;\n  background-image:\n    linear-gradient(var(--border-subtle) 1px, transparent 1px),\n    linear-gradient(\n      90deg,\n      var(--border-subtle) 1px,\n      transparent 1px);\n  background-size: 48px 48px;\n  -webkit-mask-image:\n    radial-gradient(\n      ellipse 70% 80% at 50% 50%,\n      #000 25%,\n      transparent 75%);\n  mask-image:\n    radial-gradient(\n      ellipse 70% 80% at 50% 50%,\n      #000 25%,\n      transparent 75%);\n  z-index: -1;\n  opacity: 0.6;\n}\n.dashboard__greeting[_ngcontent-%COMP%] {\n  min-width: 0;\n  flex: 1;\n}\n.dashboard__eyebrow[_ngcontent-%COMP%] {\n  display: inline-flex;\n  align-items: center;\n  gap: var(--space-2);\n  padding: 4px 12px;\n  border-radius: var(--radius-full);\n  background: color-mix(in oklab, var(--accent-primary) 12%, transparent);\n  border: 1px solid color-mix(in oklab, var(--accent-primary) 30%, transparent);\n  color: var(--accent-primary);\n  font-family: var(--font-mono);\n  font-size: 0.6875rem;\n  font-weight: 500;\n  letter-spacing: 0.06em;\n  text-transform: uppercase;\n  margin: 0 0 var(--space-4);\n}\n.dashboard__pulse[_ngcontent-%COMP%] {\n  width: 6px;\n  height: 6px;\n  border-radius: 50%;\n  background: var(--accent-primary);\n  box-shadow: 0 0 8px var(--accent-primary);\n  animation: _ngcontent-%COMP%_dashboard-pulse 2.2s ease-in-out infinite;\n}\n@keyframes _ngcontent-%COMP%_dashboard-pulse {\n  0%, 100% {\n    opacity: 1;\n    transform: scale(1);\n  }\n  50% {\n    opacity: 0.5;\n    transform: scale(1.4);\n  }\n}\n.dashboard__title[_ngcontent-%COMP%] {\n  font-family: var(--font-display);\n  font-weight: 700;\n  font-size: clamp(1.75rem, 2.4vw + 0.5rem, 2.5rem);\n  line-height: 1.1;\n  letter-spacing: -0.02em;\n  color: var(--text-primary);\n  margin: 0 0 var(--space-3);\n}\n.dashboard__subtitle[_ngcontent-%COMP%] {\n  font-size: 0.9375rem;\n  color: var(--text-secondary);\n  margin: 0;\n  line-height: 1.5;\n}\n.dashboard__subtitle[_ngcontent-%COMP%]   strong[_ngcontent-%COMP%] {\n  color: var(--text-primary);\n  font-weight: 600;\n}\n.dashboard__clock[_ngcontent-%COMP%] {\n  display: none;\n  flex-direction: column;\n  align-items: flex-end;\n  padding: var(--space-4) var(--space-5);\n  border-radius: var(--radius-lg);\n  background: color-mix(in oklab, var(--bg-primary) 60%, transparent);\n  border: 1px solid var(--border-subtle);\n  flex-shrink: 0;\n}\n@media (min-width: 640px) {\n  .dashboard__clock[_ngcontent-%COMP%] {\n    display: flex;\n  }\n}\n.dashboard__clock-value[_ngcontent-%COMP%] {\n  font-family: var(--font-display);\n  font-weight: 700;\n  font-size: 2rem;\n  line-height: 1;\n  color: var(--text-primary);\n  letter-spacing: -0.02em;\n  font-variant-numeric: tabular-nums;\n}\n.dashboard__clock-label[_ngcontent-%COMP%] {\n  font-family: var(--font-mono);\n  font-size: 0.6875rem;\n  color: var(--text-muted);\n  letter-spacing: 0.06em;\n  text-transform: uppercase;\n  margin-top: 4px;\n}\n.dashboard__grid[_ngcontent-%COMP%] {\n  display: grid;\n  gap: var(--space-4);\n  grid-template-columns: repeat(auto-fit, minmax(240px, 1fr));\n}\n.dashboard__grid[_ngcontent-%COMP%]    > app-stat-card[_ngcontent-%COMP%] {\n  animation: _ngcontent-%COMP%_card-in 0.5s var(--ease-out) both;\n}\n.dashboard__grid[_ngcontent-%COMP%]    > app-stat-card[_ngcontent-%COMP%]:nth-child(1) {\n  animation-delay: 0.05s;\n}\n.dashboard__grid[_ngcontent-%COMP%]    > app-stat-card[_ngcontent-%COMP%]:nth-child(2) {\n  animation-delay: 0.12s;\n}\n.dashboard__grid[_ngcontent-%COMP%]    > app-stat-card[_ngcontent-%COMP%]:nth-child(3) {\n  animation-delay: 0.19s;\n}\n.dashboard__grid[_ngcontent-%COMP%]    > app-stat-card[_ngcontent-%COMP%]:nth-child(4) {\n  animation-delay: 0.26s;\n}\n.dashboard__grid[_ngcontent-%COMP%]    > app-stat-card[_ngcontent-%COMP%]:nth-child(5) {\n  animation-delay: 0.33s;\n}\n@keyframes _ngcontent-%COMP%_card-in {\n  from {\n    opacity: 0;\n    transform: translateY(14px);\n  }\n  to {\n    opacity: 1;\n    transform: translateY(0);\n  }\n}\n.dashboard__error[_ngcontent-%COMP%] {\n  display: inline-flex;\n  align-items: center;\n  gap: var(--space-3);\n  padding: var(--space-4) var(--space-5);\n  border-radius: var(--radius-md);\n  background: color-mix(in oklab, var(--accent-danger) 12%, transparent);\n  border: 1px solid color-mix(in oklab, var(--accent-danger) 30%, transparent);\n  color: var(--accent-danger);\n  font-size: 0.875rem;\n}\n.dashboard__error[_ngcontent-%COMP%]   i[_ngcontent-%COMP%] {\n  font-size: 20px;\n}\n.dashboard__section[_ngcontent-%COMP%] {\n  display: flex;\n  flex-direction: column;\n  gap: var(--space-5);\n}\n.dashboard__section-head[_ngcontent-%COMP%] {\n  display: flex;\n  flex-direction: column;\n  gap: 4px;\n}\n.dashboard__section-title[_ngcontent-%COMP%] {\n  font-family: var(--font-heading);\n  font-weight: 600;\n  font-size: 1.25rem;\n  color: var(--text-primary);\n  margin: 0;\n  letter-spacing: -0.01em;\n}\n.dashboard__section-sub[_ngcontent-%COMP%] {\n  font-size: 0.875rem;\n  color: var(--text-muted);\n  margin: 0;\n}\n.dashboard__actions[_ngcontent-%COMP%] {\n  display: grid;\n  gap: var(--space-4);\n  grid-template-columns: repeat(auto-fit, minmax(280px, 1fr));\n}\n.action-card[_ngcontent-%COMP%] {\n  --stat-color: var(--accent-primary);\n  display: flex;\n  align-items: center;\n  gap: var(--space-4);\n  padding: var(--space-5);\n  border-radius: var(--radius-lg);\n  background: var(--bg-secondary);\n  border: 1px solid var(--border-subtle);\n  text-decoration: none;\n  color: inherit;\n  position: relative;\n  overflow: hidden;\n  transition:\n    transform var(--duration-slow) var(--ease-out),\n    border-color var(--duration-slow) var(--ease-out),\n    box-shadow var(--duration-slow) var(--ease-out);\n}\n.action-card[data-accent=warning][_ngcontent-%COMP%] {\n  --stat-color: var(--accent-warning);\n}\n.action-card[data-accent=info][_ngcontent-%COMP%] {\n  --stat-color: var(--accent-info);\n}\n.action-card[_ngcontent-%COMP%]:hover {\n  transform: translateY(-2px);\n  border-color: color-mix(in oklab, var(--stat-color) 40%, transparent);\n  box-shadow: 0 0 24px color-mix(in oklab, var(--stat-color) 14%, transparent), var(--shadow-md);\n}\n.action-card[_ngcontent-%COMP%]:hover   .action-card__arrow[_ngcontent-%COMP%] {\n  transform: translateX(4px);\n  color: var(--stat-color);\n}\n.action-card__icon[_ngcontent-%COMP%] {\n  width: 52px;\n  height: 52px;\n  border-radius: var(--radius-md);\n  display: grid;\n  place-items: center;\n  background: color-mix(in oklab, var(--stat-color) 14%, transparent);\n  border: 1px solid color-mix(in oklab, var(--stat-color) 28%, transparent);\n  color: var(--stat-color);\n  flex-shrink: 0;\n}\n.action-card__icon[_ngcontent-%COMP%]   i[_ngcontent-%COMP%] {\n  font-size: 24px;\n}\n.action-card__body[_ngcontent-%COMP%] {\n  flex: 1;\n  min-width: 0;\n}\n.action-card__title[_ngcontent-%COMP%] {\n  font-family: var(--font-heading);\n  font-weight: 600;\n  font-size: 1rem;\n  margin: 0 0 4px;\n  color: var(--text-primary);\n}\n.action-card__text[_ngcontent-%COMP%] {\n  font-size: 0.8125rem;\n  color: var(--text-secondary);\n  margin: 0;\n  line-height: 1.4;\n}\n.action-card__arrow[_ngcontent-%COMP%] {\n  color: var(--text-muted);\n  font-size: 20px;\n  transition: transform var(--duration-base) var(--ease-out), color var(--duration-base) var(--ease-out);\n}\n@media (prefers-reduced-motion: reduce) {\n  .dashboard[_ngcontent-%COMP%], \n   .dashboard__grid[_ngcontent-%COMP%]    > app-stat-card[_ngcontent-%COMP%] {\n    animation: none;\n  }\n  .dashboard__pulse[_ngcontent-%COMP%] {\n    animation: none;\n  }\n}\n/*# sourceMappingURL=admin-dashboard.component.css.map */'], changeDetection: 0 });
};
(() => {
  (typeof ngDevMode === "undefined" || ngDevMode) && setClassMetadata(AdminDashboardComponent, [{
    type: Component,
    args: [{ selector: "app-admin-dashboard", standalone: true, changeDetection: ChangeDetectionStrategy.OnPush, imports: [StatCardComponent, RouterLink], template: `<section class="dashboard">
  <!-- Greeting + live clock -->
  <header class="dashboard__hero">
    <div class="dashboard__greeting">
      <p class="dashboard__eyebrow">
        <span class="dashboard__pulse" aria-hidden="true"></span>
        \u0421\u0438\u0441\u0442\u0435\u043C\u0430 \u0430\u043A\u0442\u0438\u0432\u043D\u0430
      </p>
      <h2 class="dashboard__title">{{ greeting() }}, \u0430\u0434\u043C\u0438\u043D\u0438\u0441\u0442\u0440\u0430\u0442\u043E\u0440</h2>
      <p class="dashboard__subtitle">
        {{ dateLabel() }} \xB7 \u0442\u0435\u043A\u0443\u0449\u0438\u0439 \u0441\u0435\u043C\u0435\u0441\u0442\u0440
        <strong>{{ stats()?.activeSemesterName ?? '\u2014' }}</strong>
      </p>
    </div>

    <div class="dashboard__clock" aria-hidden="true">
      <span class="dashboard__clock-value">{{ timeLabel() }}</span>
      <span class="dashboard__clock-label">\u043F\u043E \u041C\u043E\u0441\u043A\u0432\u0435</span>
    </div>
  </header>

  <!-- Stat grid -->
  <div class="dashboard__grid">
    <app-stat-card
      accent="primary"
      icon="ph-duotone ph-students"
      [value]="stats()?.totalStudents ?? 0"
      label="\u0421\u0442\u0443\u0434\u0435\u043D\u0442\u043E\u0432"
      [loading]="loading()"
    />
    <app-stat-card
      accent="secondary"
      icon="ph-duotone ph-chalkboard-teacher"
      [value]="stats()?.totalTeachers ?? 0"
      label="\u041F\u0440\u0435\u043F\u043E\u0434\u0430\u0432\u0430\u0442\u0435\u043B\u0435\u0439"
      [loading]="loading()"
    />
    <app-stat-card
      accent="warning"
      icon="ph-duotone ph-users-three"
      [value]="stats()?.totalGroups ?? 0"
      label="\u0413\u0440\u0443\u043F\u043F \u0432\u0441\u0435\u0433\u043E"
      [loading]="loading()"
    />
    <app-stat-card
      accent="info"
      icon="ph-duotone ph-buildings"
      [value]="stats()?.activeGroups ?? 0"
      label="\u0410\u043A\u0442\u0438\u0432\u043D\u044B\u0445 \u0433\u0440\u0443\u043F\u043F"
      suffix="{{ activeGroupsPct() ? activeGroupsPct() + '%' : '' }}"
      [loading]="loading()"
    />
  </div>

  @if (error()) {
    <div class="dashboard__error" role="alert">
      <i class="ph ph-warning-circle" aria-hidden="true"></i>
      {{ error() }}
    </div>
  }

  <!-- Quick actions -->
  <div class="dashboard__section">
    <div class="dashboard__section-head">
      <h3 class="dashboard__section-title">\u0411\u044B\u0441\u0442\u0440\u044B\u0435 \u0434\u0435\u0439\u0441\u0442\u0432\u0438\u044F</h3>
      <p class="dashboard__section-sub">\u0427\u0430\u0441\u0442\u043E \u0438\u0441\u043F\u043E\u043B\u044C\u0437\u0443\u0435\u043C\u044B\u0435 \u0440\u0430\u0437\u0434\u0435\u043B\u044B \u043F\u0430\u043D\u0435\u043B\u0438</p>
    </div>

    <div class="dashboard__actions">
      <a routerLink="/admin/users" class="action-card" data-accent="primary">
        <div class="action-card__icon" aria-hidden="true">
          <i class="ph-duotone ph-users"></i>
        </div>
        <div class="action-card__body">
          <h4 class="action-card__title">\u041F\u043E\u043B\u044C\u0437\u043E\u0432\u0430\u0442\u0435\u043B\u0438</h4>
          <p class="action-card__text">\u0421\u043E\u0437\u0434\u0430\u0442\u044C, \u0440\u0435\u0434\u0430\u043A\u0442\u0438\u0440\u043E\u0432\u0430\u0442\u044C, \u0430\u0440\u0445\u0438\u0432\u0438\u0440\u043E\u0432\u0430\u0442\u044C \u0443\u0447\u0451\u0442\u043D\u044B\u0435 \u0437\u0430\u043F\u0438\u0441\u0438</p>
        </div>
        <i class="ph ph-arrow-right action-card__arrow" aria-hidden="true"></i>
      </a>

      <a routerLink="/admin/groups" class="action-card" data-accent="warning">
        <div class="action-card__icon" aria-hidden="true">
          <i class="ph-duotone ph-users-three"></i>
        </div>
        <div class="action-card__body">
          <h4 class="action-card__title">\u0413\u0440\u0443\u043F\u043F\u044B</h4>
          <p class="action-card__text">\u0423\u0447\u0435\u0431\u043D\u044B\u0435 \u0433\u0440\u0443\u043F\u043F\u044B \u0438 \u0438\u0445 \u0441\u043E\u0441\u0442\u0430\u0432</p>
        </div>
        <i class="ph ph-arrow-right action-card__arrow" aria-hidden="true"></i>
      </a>

      <a routerLink="/admin/semesters" class="action-card" data-accent="info">
        <div class="action-card__icon" aria-hidden="true">
          <i class="ph-duotone ph-calendar"></i>
        </div>
        <div class="action-card__body">
          <h4 class="action-card__title">\u0421\u0435\u043C\u0435\u0441\u0442\u0440\u044B</h4>
          <p class="action-card__text">\u0420\u0430\u0441\u043F\u0438\u0441\u0430\u043D\u0438\u0435 \u0443\u0447\u0435\u0431\u043D\u044B\u0445 \u043F\u0435\u0440\u0438\u043E\u0434\u043E\u0432</p>
        </div>
        <i class="ph ph-arrow-right action-card__arrow" aria-hidden="true"></i>
      </a>
    </div>
  </div>
</section>
`, styles: ['/* src/app/features/admin/dashboard/admin-dashboard.component.css */\n:host {\n  display: block;\n}\n.dashboard {\n  display: flex;\n  flex-direction: column;\n  gap: var(--space-7);\n  animation: dashboard-in 0.6s var(--ease-out) both;\n}\n@keyframes dashboard-in {\n  from {\n    opacity: 0;\n    transform: translateY(12px);\n  }\n  to {\n    opacity: 1;\n    transform: translateY(0);\n  }\n}\n.dashboard__hero {\n  display: flex;\n  align-items: flex-start;\n  justify-content: space-between;\n  gap: var(--space-5);\n  padding: var(--space-6);\n  border-radius: var(--radius-xl);\n  background:\n    radial-gradient(\n      ellipse at 15% 20%,\n      color-mix(in oklab, var(--accent-primary) 16%, transparent),\n      transparent 55%),\n    radial-gradient(\n      ellipse at 90% 80%,\n      color-mix(in oklab, var(--accent-secondary) 18%, transparent),\n      transparent 55%),\n    var(--bg-secondary);\n  border: 1px solid var(--border-default);\n  overflow: hidden;\n  position: relative;\n  isolation: isolate;\n}\n.dashboard__hero::before {\n  content: "";\n  position: absolute;\n  inset: 0;\n  background-image:\n    linear-gradient(var(--border-subtle) 1px, transparent 1px),\n    linear-gradient(\n      90deg,\n      var(--border-subtle) 1px,\n      transparent 1px);\n  background-size: 48px 48px;\n  -webkit-mask-image:\n    radial-gradient(\n      ellipse 70% 80% at 50% 50%,\n      #000 25%,\n      transparent 75%);\n  mask-image:\n    radial-gradient(\n      ellipse 70% 80% at 50% 50%,\n      #000 25%,\n      transparent 75%);\n  z-index: -1;\n  opacity: 0.6;\n}\n.dashboard__greeting {\n  min-width: 0;\n  flex: 1;\n}\n.dashboard__eyebrow {\n  display: inline-flex;\n  align-items: center;\n  gap: var(--space-2);\n  padding: 4px 12px;\n  border-radius: var(--radius-full);\n  background: color-mix(in oklab, var(--accent-primary) 12%, transparent);\n  border: 1px solid color-mix(in oklab, var(--accent-primary) 30%, transparent);\n  color: var(--accent-primary);\n  font-family: var(--font-mono);\n  font-size: 0.6875rem;\n  font-weight: 500;\n  letter-spacing: 0.06em;\n  text-transform: uppercase;\n  margin: 0 0 var(--space-4);\n}\n.dashboard__pulse {\n  width: 6px;\n  height: 6px;\n  border-radius: 50%;\n  background: var(--accent-primary);\n  box-shadow: 0 0 8px var(--accent-primary);\n  animation: dashboard-pulse 2.2s ease-in-out infinite;\n}\n@keyframes dashboard-pulse {\n  0%, 100% {\n    opacity: 1;\n    transform: scale(1);\n  }\n  50% {\n    opacity: 0.5;\n    transform: scale(1.4);\n  }\n}\n.dashboard__title {\n  font-family: var(--font-display);\n  font-weight: 700;\n  font-size: clamp(1.75rem, 2.4vw + 0.5rem, 2.5rem);\n  line-height: 1.1;\n  letter-spacing: -0.02em;\n  color: var(--text-primary);\n  margin: 0 0 var(--space-3);\n}\n.dashboard__subtitle {\n  font-size: 0.9375rem;\n  color: var(--text-secondary);\n  margin: 0;\n  line-height: 1.5;\n}\n.dashboard__subtitle strong {\n  color: var(--text-primary);\n  font-weight: 600;\n}\n.dashboard__clock {\n  display: none;\n  flex-direction: column;\n  align-items: flex-end;\n  padding: var(--space-4) var(--space-5);\n  border-radius: var(--radius-lg);\n  background: color-mix(in oklab, var(--bg-primary) 60%, transparent);\n  border: 1px solid var(--border-subtle);\n  flex-shrink: 0;\n}\n@media (min-width: 640px) {\n  .dashboard__clock {\n    display: flex;\n  }\n}\n.dashboard__clock-value {\n  font-family: var(--font-display);\n  font-weight: 700;\n  font-size: 2rem;\n  line-height: 1;\n  color: var(--text-primary);\n  letter-spacing: -0.02em;\n  font-variant-numeric: tabular-nums;\n}\n.dashboard__clock-label {\n  font-family: var(--font-mono);\n  font-size: 0.6875rem;\n  color: var(--text-muted);\n  letter-spacing: 0.06em;\n  text-transform: uppercase;\n  margin-top: 4px;\n}\n.dashboard__grid {\n  display: grid;\n  gap: var(--space-4);\n  grid-template-columns: repeat(auto-fit, minmax(240px, 1fr));\n}\n.dashboard__grid > app-stat-card {\n  animation: card-in 0.5s var(--ease-out) both;\n}\n.dashboard__grid > app-stat-card:nth-child(1) {\n  animation-delay: 0.05s;\n}\n.dashboard__grid > app-stat-card:nth-child(2) {\n  animation-delay: 0.12s;\n}\n.dashboard__grid > app-stat-card:nth-child(3) {\n  animation-delay: 0.19s;\n}\n.dashboard__grid > app-stat-card:nth-child(4) {\n  animation-delay: 0.26s;\n}\n.dashboard__grid > app-stat-card:nth-child(5) {\n  animation-delay: 0.33s;\n}\n@keyframes card-in {\n  from {\n    opacity: 0;\n    transform: translateY(14px);\n  }\n  to {\n    opacity: 1;\n    transform: translateY(0);\n  }\n}\n.dashboard__error {\n  display: inline-flex;\n  align-items: center;\n  gap: var(--space-3);\n  padding: var(--space-4) var(--space-5);\n  border-radius: var(--radius-md);\n  background: color-mix(in oklab, var(--accent-danger) 12%, transparent);\n  border: 1px solid color-mix(in oklab, var(--accent-danger) 30%, transparent);\n  color: var(--accent-danger);\n  font-size: 0.875rem;\n}\n.dashboard__error i {\n  font-size: 20px;\n}\n.dashboard__section {\n  display: flex;\n  flex-direction: column;\n  gap: var(--space-5);\n}\n.dashboard__section-head {\n  display: flex;\n  flex-direction: column;\n  gap: 4px;\n}\n.dashboard__section-title {\n  font-family: var(--font-heading);\n  font-weight: 600;\n  font-size: 1.25rem;\n  color: var(--text-primary);\n  margin: 0;\n  letter-spacing: -0.01em;\n}\n.dashboard__section-sub {\n  font-size: 0.875rem;\n  color: var(--text-muted);\n  margin: 0;\n}\n.dashboard__actions {\n  display: grid;\n  gap: var(--space-4);\n  grid-template-columns: repeat(auto-fit, minmax(280px, 1fr));\n}\n.action-card {\n  --stat-color: var(--accent-primary);\n  display: flex;\n  align-items: center;\n  gap: var(--space-4);\n  padding: var(--space-5);\n  border-radius: var(--radius-lg);\n  background: var(--bg-secondary);\n  border: 1px solid var(--border-subtle);\n  text-decoration: none;\n  color: inherit;\n  position: relative;\n  overflow: hidden;\n  transition:\n    transform var(--duration-slow) var(--ease-out),\n    border-color var(--duration-slow) var(--ease-out),\n    box-shadow var(--duration-slow) var(--ease-out);\n}\n.action-card[data-accent=warning] {\n  --stat-color: var(--accent-warning);\n}\n.action-card[data-accent=info] {\n  --stat-color: var(--accent-info);\n}\n.action-card:hover {\n  transform: translateY(-2px);\n  border-color: color-mix(in oklab, var(--stat-color) 40%, transparent);\n  box-shadow: 0 0 24px color-mix(in oklab, var(--stat-color) 14%, transparent), var(--shadow-md);\n}\n.action-card:hover .action-card__arrow {\n  transform: translateX(4px);\n  color: var(--stat-color);\n}\n.action-card__icon {\n  width: 52px;\n  height: 52px;\n  border-radius: var(--radius-md);\n  display: grid;\n  place-items: center;\n  background: color-mix(in oklab, var(--stat-color) 14%, transparent);\n  border: 1px solid color-mix(in oklab, var(--stat-color) 28%, transparent);\n  color: var(--stat-color);\n  flex-shrink: 0;\n}\n.action-card__icon i {\n  font-size: 24px;\n}\n.action-card__body {\n  flex: 1;\n  min-width: 0;\n}\n.action-card__title {\n  font-family: var(--font-heading);\n  font-weight: 600;\n  font-size: 1rem;\n  margin: 0 0 4px;\n  color: var(--text-primary);\n}\n.action-card__text {\n  font-size: 0.8125rem;\n  color: var(--text-secondary);\n  margin: 0;\n  line-height: 1.4;\n}\n.action-card__arrow {\n  color: var(--text-muted);\n  font-size: 20px;\n  transition: transform var(--duration-base) var(--ease-out), color var(--duration-base) var(--ease-out);\n}\n@media (prefers-reduced-motion: reduce) {\n  .dashboard,\n  .dashboard__grid > app-stat-card {\n    animation: none;\n  }\n  .dashboard__pulse {\n    animation: none;\n  }\n}\n/*# sourceMappingURL=admin-dashboard.component.css.map */\n'] }]
  }], null, null);
})();
(() => {
  (typeof ngDevMode === "undefined" || ngDevMode) && \u0275setClassDebugInfo(AdminDashboardComponent, { className: "AdminDashboardComponent", filePath: "src/app/features/admin/dashboard/admin-dashboard.component.ts", lineNumber: 31 });
})();
export {
  AdminDashboardComponent
};
//# sourceMappingURL=chunk-QZMNLJ35.js.map
