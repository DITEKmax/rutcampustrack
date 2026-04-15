import {
  MatIconModule
} from "./chunk-JUF5AB3M.js";
import {
  fullName
} from "./chunk-OGMCFIRC.js";
import {
  MatSnackBar
} from "./chunk-5Q6RVIG4.js";
import "./chunk-OIBNGD5S.js";
import {
  MatProgressBar,
  MatProgressBarModule
} from "./chunk-YZBQHHAR.js";
import {
  MatAnchor,
  MatButtonModule
} from "./chunk-GTDHNJL7.js";
import "./chunk-2WN7CIGJ.js";
import "./chunk-ZEX6QU7O.js";
import "./chunk-G4JX6AWT.js";
import "./chunk-YIJEORIR.js";
import "./chunk-FW2NJ6PM.js";
import {
  AdminApiService
} from "./chunk-4Y37KGBC.js";
import {
  ActivatedRoute,
  Router,
  RouterLink
} from "./chunk-BRINUWSL.js";
import "./chunk-RWV7HGF7.js";
import "./chunk-FLE4DLW4.js";
import {
  CommonModule,
  DatePipe
} from "./chunk-CLRYRPPS.js";
import {
  Component,
  catchError,
  forkJoin,
  inject,
  of,
  setClassMetadata,
  signal,
  ɵsetClassDebugInfo,
  ɵɵadvance,
  ɵɵconditional,
  ɵɵdefineComponent,
  ɵɵelement,
  ɵɵelementEnd,
  ɵɵelementStart,
  ɵɵnextContext,
  ɵɵpipe,
  ɵɵpipeBind2,
  ɵɵproperty,
  ɵɵpureFunction0,
  ɵɵpureFunction1,
  ɵɵrepeater,
  ɵɵrepeaterCreate,
  ɵɵtemplate,
  ɵɵtext,
  ɵɵtextInterpolate,
  ɵɵtextInterpolate1
} from "./chunk-4M4FDLBS.js";

// src/app/features/admin/groups/group-history/group-history-page.component.ts
var _c0 = () => ["/admin/dashboard"];
var _c1 = (a0) => ({ groupId: a0 });
var _c2 = (a0) => ({ groupId: a0, view: "attendance" });
var _forTrack0 = ($index, $item) => $item.id;
function GroupHistoryPageComponent_Conditional_6_Template(rf, ctx) {
  if (rf & 1) {
    \u0275\u0275element(0, "mat-progress-bar", 5);
  }
}
function GroupHistoryPageComponent_Conditional_7_Template(rf, ctx) {
  if (rf & 1) {
    \u0275\u0275elementStart(0, "div", 6)(1, "div", 8)(2, "div", 9);
    \u0275\u0275element(3, "i", 10);
    \u0275\u0275elementEnd();
    \u0275\u0275elementStart(4, "h3", 11);
    \u0275\u0275text(5, "\u0413\u0440\u0443\u043F\u043F\u0430 \u043D\u0435 \u043D\u0430\u0439\u0434\u0435\u043D\u0430");
    \u0275\u0275elementEnd();
    \u0275\u0275elementStart(6, "p", 12);
    \u0275\u0275text(7, "\u0412\u043E\u0437\u043C\u043E\u0436\u043D\u043E, \u0433\u0440\u0443\u043F\u043F\u0430 \u0431\u044B\u043B\u0430 \u0443\u0434\u0430\u043B\u0435\u043D\u0430.");
    \u0275\u0275elementEnd()()();
  }
}
function GroupHistoryPageComponent_Conditional_8_Conditional_4_Template(rf, ctx) {
  if (rf & 1) {
    \u0275\u0275elementStart(0, "span", 15);
    \u0275\u0275text(1);
    \u0275\u0275elementEnd();
  }
  if (rf & 2) {
    \u0275\u0275advance();
    \u0275\u0275textInterpolate1("\u0432\u044B\u043F\u0443\u0441\u043A ", ctx, "");
  }
}
function GroupHistoryPageComponent_Conditional_8_Conditional_13_Template(rf, ctx) {
  if (rf & 1) {
    \u0275\u0275elementStart(0, "div")(1, "dt");
    \u0275\u0275text(2, "\u0410\u0440\u0445\u0438\u0432\u0438\u0440\u043E\u0432\u0430\u043D\u0430");
    \u0275\u0275elementEnd();
    \u0275\u0275elementStart(3, "dd");
    \u0275\u0275text(4);
    \u0275\u0275pipe(5, "date");
    \u0275\u0275elementEnd()();
  }
  if (rf & 2) {
    const g_r1 = \u0275\u0275nextContext();
    \u0275\u0275advance(4);
    \u0275\u0275textInterpolate(\u0275\u0275pipeBind2(5, 1, g_r1.archivedAt, "dd.MM.yyyy HH:mm"));
  }
}
function GroupHistoryPageComponent_Conditional_8_Conditional_28_Template(rf, ctx) {
  if (rf & 1) {
    \u0275\u0275elementStart(0, "p", 19);
    \u0275\u0275text(1, "\u041D\u0435\u0442 \u0441\u0442\u0443\u0434\u0435\u043D\u0442\u043E\u0432, \u0437\u0430\u043A\u0440\u0435\u043F\u043B\u0451\u043D\u043D\u044B\u0445 \u0437\u0430 \u044D\u0442\u043E\u0439 \u0433\u0440\u0443\u043F\u043F\u043E\u0439.");
    \u0275\u0275elementEnd();
  }
}
function GroupHistoryPageComponent_Conditional_8_Conditional_29_For_2_Template(rf, ctx) {
  if (rf & 1) {
    \u0275\u0275elementStart(0, "li");
    \u0275\u0275text(1);
    \u0275\u0275elementEnd();
  }
  if (rf & 2) {
    const s_r2 = ctx.$implicit;
    const ctx_r2 = \u0275\u0275nextContext(3);
    \u0275\u0275advance();
    \u0275\u0275textInterpolate(ctx_r2.displayName(s_r2));
  }
}
function GroupHistoryPageComponent_Conditional_8_Conditional_29_Template(rf, ctx) {
  if (rf & 1) {
    \u0275\u0275elementStart(0, "ul", 20);
    \u0275\u0275repeaterCreate(1, GroupHistoryPageComponent_Conditional_8_Conditional_29_For_2_Template, 2, 1, "li", null, _forTrack0);
    \u0275\u0275elementEnd();
  }
  if (rf & 2) {
    const ctx_r2 = \u0275\u0275nextContext(2);
    \u0275\u0275advance();
    \u0275\u0275repeater(ctx_r2.students());
  }
}
function GroupHistoryPageComponent_Conditional_8_Template(rf, ctx) {
  if (rf & 1) {
    \u0275\u0275elementStart(0, "div", 7)(1, "div", 13)(2, "h1", 14);
    \u0275\u0275text(3);
    \u0275\u0275elementEnd();
    \u0275\u0275template(4, GroupHistoryPageComponent_Conditional_8_Conditional_4_Template, 2, 1, "span", 15);
    \u0275\u0275elementStart(5, "span", 16);
    \u0275\u0275text(6, "\u0410\u0440\u0445\u0438\u0432");
    \u0275\u0275elementEnd()();
    \u0275\u0275elementStart(7, "dl", 17)(8, "div")(9, "dt");
    \u0275\u0275text(10, "\u041F\u043E\u043B\u043D\u043E\u0435 \u043D\u0430\u0437\u0432\u0430\u043D\u0438\u0435");
    \u0275\u0275elementEnd();
    \u0275\u0275elementStart(11, "dd");
    \u0275\u0275text(12);
    \u0275\u0275elementEnd()();
    \u0275\u0275template(13, GroupHistoryPageComponent_Conditional_8_Conditional_13_Template, 6, 4, "div");
    \u0275\u0275elementStart(14, "div")(15, "dt");
    \u0275\u0275text(16, "\u0421\u043E\u0437\u0434\u0430\u043D\u0430");
    \u0275\u0275elementEnd();
    \u0275\u0275elementStart(17, "dd");
    \u0275\u0275text(18);
    \u0275\u0275pipe(19, "date");
    \u0275\u0275elementEnd()();
    \u0275\u0275elementStart(20, "div")(21, "dt");
    \u0275\u0275text(22, "\u0421\u0442\u0443\u0434\u0435\u043D\u0442\u043E\u0432");
    \u0275\u0275elementEnd();
    \u0275\u0275elementStart(23, "dd");
    \u0275\u0275text(24);
    \u0275\u0275elementEnd()()();
    \u0275\u0275elementStart(25, "section", 18)(26, "h2");
    \u0275\u0275text(27, "\u0421\u0442\u0443\u0434\u0435\u043D\u0442\u044B");
    \u0275\u0275elementEnd();
    \u0275\u0275template(28, GroupHistoryPageComponent_Conditional_8_Conditional_28_Template, 2, 0, "p", 19)(29, GroupHistoryPageComponent_Conditional_8_Conditional_29_Template, 3, 0, "ul", 20);
    \u0275\u0275elementEnd();
    \u0275\u0275elementStart(30, "section", 21)(31, "h2");
    \u0275\u0275text(32, "\u0421\u0432\u044F\u0437\u0430\u043D\u043D\u044B\u0435 \u0440\u0430\u0437\u0434\u0435\u043B\u044B");
    \u0275\u0275elementEnd();
    \u0275\u0275elementStart(33, "div", 22)(34, "a", 23);
    \u0275\u0275element(35, "i", 24);
    \u0275\u0275text(36, " \u0416\u0443\u0440\u043D\u0430\u043B \u0433\u0440\u0443\u043F\u043F\u044B ");
    \u0275\u0275elementEnd();
    \u0275\u0275elementStart(37, "a", 25);
    \u0275\u0275element(38, "i", 26);
    \u0275\u0275text(39, " \u041F\u043E\u0441\u0435\u0449\u0430\u0435\u043C\u043E\u0441\u0442\u044C ");
    \u0275\u0275elementEnd()()()();
  }
  if (rf & 2) {
    let tmp_3_0;
    const g_r1 = ctx;
    const ctx_r2 = \u0275\u0275nextContext();
    \u0275\u0275advance(3);
    \u0275\u0275textInterpolate(ctx_r2.activeName());
    \u0275\u0275advance();
    \u0275\u0275conditional((tmp_3_0 = ctx_r2.graduationYear()) ? 4 : -1, tmp_3_0);
    \u0275\u0275advance(8);
    \u0275\u0275textInterpolate(g_r1.name);
    \u0275\u0275advance();
    \u0275\u0275conditional(g_r1.archivedAt ? 13 : -1);
    \u0275\u0275advance(5);
    \u0275\u0275textInterpolate(\u0275\u0275pipeBind2(19, 11, g_r1.createdAt, "dd.MM.yyyy"));
    \u0275\u0275advance(6);
    \u0275\u0275textInterpolate(ctx_r2.students().length);
    \u0275\u0275advance(4);
    \u0275\u0275conditional(ctx_r2.students().length === 0 ? 28 : 29);
    \u0275\u0275advance(6);
    \u0275\u0275property("routerLink", \u0275\u0275pureFunction0(14, _c0))("queryParams", \u0275\u0275pureFunction1(15, _c1, g_r1.id));
    \u0275\u0275advance(3);
    \u0275\u0275property("routerLink", \u0275\u0275pureFunction0(17, _c0))("queryParams", \u0275\u0275pureFunction1(18, _c2, g_r1.id));
  }
}
var GroupHistoryPageComponent = class _GroupHistoryPageComponent {
  api = inject(AdminApiService);
  route = inject(ActivatedRoute);
  router = inject(Router);
  snackBar = inject(MatSnackBar);
  loading = signal(true);
  group = signal(null);
  students = signal([]);
  notFound = signal(false);
  ngOnInit() {
    const idParam = this.route.snapshot.paramMap.get("id");
    const id = idParam ? Number(idParam) : NaN;
    if (!Number.isFinite(id)) {
      this.notFound.set(true);
      this.loading.set(false);
      return;
    }
    forkJoin({
      group: this.api.getGroup(id).pipe(catchError(() => of(null))),
      students: this.api.listUsers({ role: "STUDENT", size: 500 }).pipe(catchError(() => of({ items: [], total: 0 })))
    }).subscribe(({ group, students }) => {
      if (!group) {
        this.notFound.set(true);
        this.loading.set(false);
        return;
      }
      if (group.active) {
        this.snackBar.open("\u042D\u0442\u0430 \u0433\u0440\u0443\u043F\u043F\u0430 \u0430\u043A\u0442\u0438\u0432\u043D\u0430, \u0438\u0441\u0442\u043E\u0440\u0438\u044F \u043D\u0435\u0434\u043E\u0441\u0442\u0443\u043F\u043D\u0430.", void 0, { duration: 5e3 });
        this.router.navigate(["/admin/groups"]);
        return;
      }
      this.group.set(group);
      this.students.set(students.items.filter((u) => u.groupId === group.id));
      this.loading.set(false);
    });
  }
  /** Год выпуска извлекается из суффикса "(выпуск YYYY)" или archivedAt. */
  graduationYear() {
    const g = this.group();
    if (!g)
      return null;
    const match = /\(выпуск (\d{4})\)/.exec(g.name);
    if (match)
      return Number(match[1]);
    if (g.archivedAt)
      return new Date(g.archivedAt).getFullYear();
    return null;
  }
  /** Отображаемое имя без суффикса "(выпуск YYYY)" — для отдельного заголовка. */
  activeName() {
    const g = this.group();
    if (!g)
      return "";
    return g.name.replace(/ \(выпуск \d{4}\)$/, "");
  }
  displayName(u) {
    return fullName(u);
  }
  static \u0275fac = function GroupHistoryPageComponent_Factory(__ngFactoryType__) {
    return new (__ngFactoryType__ || _GroupHistoryPageComponent)();
  };
  static \u0275cmp = /* @__PURE__ */ \u0275\u0275defineComponent({ type: _GroupHistoryPageComponent, selectors: [["app-group-history-page"]], decls: 9, vars: 3, consts: [[1, "page-stack"], [1, "page-header"], [1, "page-header__title"], ["routerLink", "/admin/groups", 1, "page-header__back"], ["aria-hidden", "true", 1, "ph", "ph-arrow-left"], ["mode", "indeterminate"], [1, "page-card"], [1, "page-card", "group-history"], [1, "page-empty"], ["aria-hidden", "true", 1, "page-empty__icon"], [1, "ph-duotone", "ph-smiley-x-eyes"], [1, "page-empty__title"], [1, "page-empty__text"], [1, "group-history__head"], [1, "group-history__name"], [1, "group-history__year"], [1, "group-history__badge"], [1, "group-history__meta"], [1, "group-history__section"], [1, "group-history__empty"], [1, "group-history__students"], [1, "group-history__section", "group-history__links"], [1, "group-history__link-row"], ["mat-stroked-button", "", "data-testid", "journal-link", 3, "routerLink", "queryParams"], ["aria-hidden", "true", 1, "ph", "ph-clipboard-text"], ["mat-stroked-button", "", "data-testid", "attendance-link", 3, "routerLink", "queryParams"], ["aria-hidden", "true", 1, "ph", "ph-chart-bar"]], template: function GroupHistoryPageComponent_Template(rf, ctx) {
    if (rf & 1) {
      \u0275\u0275elementStart(0, "div", 0)(1, "header", 1)(2, "div", 2)(3, "a", 3);
      \u0275\u0275element(4, "i", 4);
      \u0275\u0275text(5, " \u041A \u0441\u043F\u0438\u0441\u043A\u0443 \u0433\u0440\u0443\u043F\u043F ");
      \u0275\u0275elementEnd()()();
      \u0275\u0275template(6, GroupHistoryPageComponent_Conditional_6_Template, 1, 0, "mat-progress-bar", 5)(7, GroupHistoryPageComponent_Conditional_7_Template, 8, 0, "div", 6)(8, GroupHistoryPageComponent_Conditional_8_Template, 40, 20, "div", 7);
      \u0275\u0275elementEnd();
    }
    if (rf & 2) {
      let tmp_2_0;
      \u0275\u0275advance(6);
      \u0275\u0275conditional(ctx.loading() ? 6 : -1);
      \u0275\u0275advance();
      \u0275\u0275conditional(ctx.notFound() ? 7 : -1);
      \u0275\u0275advance();
      \u0275\u0275conditional((tmp_2_0 = !ctx.loading() && !ctx.notFound() && ctx.group()) ? 8 : -1, tmp_2_0);
    }
  }, dependencies: [CommonModule, DatePipe, RouterLink, MatButtonModule, MatAnchor, MatIconModule, MatProgressBarModule, MatProgressBar], styles: ["\n\n.group-history[_ngcontent-%COMP%] {\n  padding: 1.5rem;\n  display: flex;\n  flex-direction: column;\n  gap: 1.5rem;\n}\n.group-history__head[_ngcontent-%COMP%] {\n  display: flex;\n  align-items: center;\n  gap: 0.75rem;\n  flex-wrap: wrap;\n}\n.group-history__name[_ngcontent-%COMP%] {\n  margin: 0;\n  font-size: 1.75rem;\n  font-weight: 600;\n}\n.group-history__year[_ngcontent-%COMP%] {\n  color: rgba(0, 0, 0, 0.6);\n  font-size: 1.1rem;\n}\n.group-history__badge[_ngcontent-%COMP%] {\n  padding: 0.15rem 0.6rem;\n  border-radius: 12px;\n  background: #e5e7eb;\n  color: #374151;\n  font-size: 0.8rem;\n  font-weight: 500;\n  letter-spacing: 0.02em;\n}\n.group-history__meta[_ngcontent-%COMP%] {\n  display: grid;\n  grid-template-columns: repeat(auto-fit, minmax(180px, 1fr));\n  gap: 1rem;\n  margin: 0;\n}\n.group-history__meta[_ngcontent-%COMP%]   dt[_ngcontent-%COMP%] {\n  font-size: 0.8rem;\n  color: rgba(0, 0, 0, 0.6);\n  margin-bottom: 0.25rem;\n}\n.group-history__meta[_ngcontent-%COMP%]   dd[_ngcontent-%COMP%] {\n  margin: 0;\n  font-weight: 500;\n}\n.group-history__section[_ngcontent-%COMP%]   h2[_ngcontent-%COMP%] {\n  font-size: 1.1rem;\n  font-weight: 600;\n  margin: 0 0 0.5rem;\n}\n.group-history__empty[_ngcontent-%COMP%] {\n  color: rgba(0, 0, 0, 0.6);\n}\n.group-history__students[_ngcontent-%COMP%] {\n  margin: 0;\n  padding-left: 1.25rem;\n  line-height: 1.7;\n}\n.group-history__link-row[_ngcontent-%COMP%] {\n  display: flex;\n  gap: 0.75rem;\n  flex-wrap: wrap;\n}\n/*# sourceMappingURL=group-history-page.component.css.map */"] });
};
(() => {
  (typeof ngDevMode === "undefined" || ngDevMode) && setClassMetadata(GroupHistoryPageComponent, [{
    type: Component,
    args: [{ selector: "app-group-history-page", standalone: true, imports: [CommonModule, RouterLink, MatButtonModule, MatIconModule, MatProgressBarModule], template: `<div class="page-stack">
  <header class="page-header">
    <div class="page-header__title">
      <a routerLink="/admin/groups" class="page-header__back">
        <i class="ph ph-arrow-left" aria-hidden="true"></i>
        \u041A \u0441\u043F\u0438\u0441\u043A\u0443 \u0433\u0440\u0443\u043F\u043F
      </a>
    </div>
  </header>

  @if (loading()) {
    <mat-progress-bar mode="indeterminate"></mat-progress-bar>
  }

  @if (notFound()) {
    <div class="page-card">
      <div class="page-empty">
        <div class="page-empty__icon" aria-hidden="true">
          <i class="ph-duotone ph-smiley-x-eyes"></i>
        </div>
        <h3 class="page-empty__title">\u0413\u0440\u0443\u043F\u043F\u0430 \u043D\u0435 \u043D\u0430\u0439\u0434\u0435\u043D\u0430</h3>
        <p class="page-empty__text">\u0412\u043E\u0437\u043C\u043E\u0436\u043D\u043E, \u0433\u0440\u0443\u043F\u043F\u0430 \u0431\u044B\u043B\u0430 \u0443\u0434\u0430\u043B\u0435\u043D\u0430.</p>
      </div>
    </div>
  }

  @if (!loading() && !notFound() && group(); as g) {
    <div class="page-card group-history">
      <div class="group-history__head">
        <h1 class="group-history__name">{{ activeName() }}</h1>
        @if (graduationYear(); as year) {
          <span class="group-history__year">\u0432\u044B\u043F\u0443\u0441\u043A {{ year }}</span>
        }
        <span class="group-history__badge">\u0410\u0440\u0445\u0438\u0432</span>
      </div>

      <dl class="group-history__meta">
        <div>
          <dt>\u041F\u043E\u043B\u043D\u043E\u0435 \u043D\u0430\u0437\u0432\u0430\u043D\u0438\u0435</dt>
          <dd>{{ g.name }}</dd>
        </div>
        @if (g.archivedAt) {
          <div>
            <dt>\u0410\u0440\u0445\u0438\u0432\u0438\u0440\u043E\u0432\u0430\u043D\u0430</dt>
            <dd>{{ g.archivedAt | date:'dd.MM.yyyy HH:mm' }}</dd>
          </div>
        }
        <div>
          <dt>\u0421\u043E\u0437\u0434\u0430\u043D\u0430</dt>
          <dd>{{ g.createdAt | date:'dd.MM.yyyy' }}</dd>
        </div>
        <div>
          <dt>\u0421\u0442\u0443\u0434\u0435\u043D\u0442\u043E\u0432</dt>
          <dd>{{ students().length }}</dd>
        </div>
      </dl>

      <section class="group-history__section">
        <h2>\u0421\u0442\u0443\u0434\u0435\u043D\u0442\u044B</h2>
        @if (students().length === 0) {
          <p class="group-history__empty">\u041D\u0435\u0442 \u0441\u0442\u0443\u0434\u0435\u043D\u0442\u043E\u0432, \u0437\u0430\u043A\u0440\u0435\u043F\u043B\u0451\u043D\u043D\u044B\u0445 \u0437\u0430 \u044D\u0442\u043E\u0439 \u0433\u0440\u0443\u043F\u043F\u043E\u0439.</p>
        } @else {
          <ul class="group-history__students">
            @for (s of students(); track s.id) {
              <li>{{ displayName(s) }}</li>
            }
          </ul>
        }
      </section>

      <section class="group-history__section group-history__links">
        <h2>\u0421\u0432\u044F\u0437\u0430\u043D\u043D\u044B\u0435 \u0440\u0430\u0437\u0434\u0435\u043B\u044B</h2>
        <div class="group-history__link-row">
          <a
            mat-stroked-button
            [routerLink]="['/admin/dashboard']"
            [queryParams]="{ groupId: g.id }"
            data-testid="journal-link">
            <i class="ph ph-clipboard-text" aria-hidden="true"></i>
            \u0416\u0443\u0440\u043D\u0430\u043B \u0433\u0440\u0443\u043F\u043F\u044B
          </a>
          <a
            mat-stroked-button
            [routerLink]="['/admin/dashboard']"
            [queryParams]="{ groupId: g.id, view: 'attendance' }"
            data-testid="attendance-link">
            <i class="ph ph-chart-bar" aria-hidden="true"></i>
            \u041F\u043E\u0441\u0435\u0449\u0430\u0435\u043C\u043E\u0441\u0442\u044C
          </a>
        </div>
      </section>
    </div>
  }
</div>
`, styles: ["/* src/app/features/admin/groups/group-history/group-history-page.component.scss */\n.group-history {\n  padding: 1.5rem;\n  display: flex;\n  flex-direction: column;\n  gap: 1.5rem;\n}\n.group-history__head {\n  display: flex;\n  align-items: center;\n  gap: 0.75rem;\n  flex-wrap: wrap;\n}\n.group-history__name {\n  margin: 0;\n  font-size: 1.75rem;\n  font-weight: 600;\n}\n.group-history__year {\n  color: rgba(0, 0, 0, 0.6);\n  font-size: 1.1rem;\n}\n.group-history__badge {\n  padding: 0.15rem 0.6rem;\n  border-radius: 12px;\n  background: #e5e7eb;\n  color: #374151;\n  font-size: 0.8rem;\n  font-weight: 500;\n  letter-spacing: 0.02em;\n}\n.group-history__meta {\n  display: grid;\n  grid-template-columns: repeat(auto-fit, minmax(180px, 1fr));\n  gap: 1rem;\n  margin: 0;\n}\n.group-history__meta dt {\n  font-size: 0.8rem;\n  color: rgba(0, 0, 0, 0.6);\n  margin-bottom: 0.25rem;\n}\n.group-history__meta dd {\n  margin: 0;\n  font-weight: 500;\n}\n.group-history__section h2 {\n  font-size: 1.1rem;\n  font-weight: 600;\n  margin: 0 0 0.5rem;\n}\n.group-history__empty {\n  color: rgba(0, 0, 0, 0.6);\n}\n.group-history__students {\n  margin: 0;\n  padding-left: 1.25rem;\n  line-height: 1.7;\n}\n.group-history__link-row {\n  display: flex;\n  gap: 0.75rem;\n  flex-wrap: wrap;\n}\n/*# sourceMappingURL=group-history-page.component.css.map */\n"] }]
  }], null, null);
})();
(() => {
  (typeof ngDevMode === "undefined" || ngDevMode) && \u0275setClassDebugInfo(GroupHistoryPageComponent, { className: "GroupHistoryPageComponent", filePath: "src/app/features/admin/groups/group-history/group-history-page.component.ts", lineNumber: 28 });
})();
export {
  GroupHistoryPageComponent
};
//# sourceMappingURL=chunk-QXW3MA3N.js.map
