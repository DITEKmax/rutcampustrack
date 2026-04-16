import {
  MAT_DIALOG_DATA,
  MatDialogActions,
  MatDialogClose,
  MatDialogContent,
  MatDialogModule,
  MatDialogRef,
  MatDialogTitle
} from "./chunk-M24SPP55.js";
import {
  MatButton,
  MatButtonModule
} from "./chunk-PAS4QIDL.js";
import {
  ChangeDetectionStrategy,
  Component,
  inject,
  setClassMetadata,
  ɵsetClassDebugInfo,
  ɵɵadvance,
  ɵɵdefineComponent,
  ɵɵelementEnd,
  ɵɵelementStart,
  ɵɵproperty,
  ɵɵtext,
  ɵɵtextInterpolate,
  ɵɵtextInterpolate1
} from "./chunk-MFRIGFR2.js";

// src/app/shared/confirm-dialog/confirm-dialog.component.ts
var ConfirmDialogComponent = class _ConfirmDialogComponent {
  data = inject(MAT_DIALOG_DATA);
  dialogRef = inject(MatDialogRef);
  static \u0275fac = function ConfirmDialogComponent_Factory(__ngFactoryType__) {
    return new (__ngFactoryType__ || _ConfirmDialogComponent)();
  };
  static \u0275cmp = /* @__PURE__ */ \u0275\u0275defineComponent({ type: _ConfirmDialogComponent, selectors: [["app-confirm-dialog"]], decls: 10, vars: 6, consts: [["mat-dialog-title", ""], [2, "margin", "0", "color", "var(--text-primary)", "font-size", "0.9375rem", "line-height", "1.5"], ["align", "end"], ["mat-button", "", "mat-dialog-close", ""], ["mat-flat-button", "", "cdkFocusInitial", "", 3, "color", "mat-dialog-close"]], template: function ConfirmDialogComponent_Template(rf, ctx) {
    if (rf & 1) {
      \u0275\u0275elementStart(0, "h2", 0);
      \u0275\u0275text(1);
      \u0275\u0275elementEnd();
      \u0275\u0275elementStart(2, "mat-dialog-content")(3, "p", 1);
      \u0275\u0275text(4);
      \u0275\u0275elementEnd()();
      \u0275\u0275elementStart(5, "mat-dialog-actions", 2)(6, "button", 3);
      \u0275\u0275text(7);
      \u0275\u0275elementEnd();
      \u0275\u0275elementStart(8, "button", 4);
      \u0275\u0275text(9);
      \u0275\u0275elementEnd()();
    }
    if (rf & 2) {
      let tmp_2_0;
      let tmp_5_0;
      \u0275\u0275advance();
      \u0275\u0275textInterpolate(ctx.data.title);
      \u0275\u0275advance(3);
      \u0275\u0275textInterpolate1(" ", ctx.data.message, " ");
      \u0275\u0275advance(3);
      \u0275\u0275textInterpolate((tmp_2_0 = ctx.data.cancelLabel) !== null && tmp_2_0 !== void 0 ? tmp_2_0 : "\u041E\u0442\u043C\u0435\u043D\u0430");
      \u0275\u0275advance();
      \u0275\u0275property("color", ctx.data.destructive ? "warn" : "primary")("mat-dialog-close", true);
      \u0275\u0275advance();
      \u0275\u0275textInterpolate1(" ", (tmp_5_0 = ctx.data.confirmLabel) !== null && tmp_5_0 !== void 0 ? tmp_5_0 : "\u041F\u043E\u0434\u0442\u0432\u0435\u0440\u0434\u0438\u0442\u044C", " ");
    }
  }, dependencies: [MatDialogModule, MatDialogClose, MatDialogTitle, MatDialogActions, MatDialogContent, MatButtonModule, MatButton], encapsulation: 2, changeDetection: 0 });
};
(() => {
  (typeof ngDevMode === "undefined" || ngDevMode) && setClassMetadata(ConfirmDialogComponent, [{
    type: Component,
    args: [{
      selector: "app-confirm-dialog",
      standalone: true,
      changeDetection: ChangeDetectionStrategy.OnPush,
      imports: [MatDialogModule, MatButtonModule],
      template: `
    <h2 mat-dialog-title>{{ data.title }}</h2>
    <mat-dialog-content>
      <p style="margin: 0; color: var(--text-primary); font-size: 0.9375rem; line-height: 1.5;">
        {{ data.message }}
      </p>
    </mat-dialog-content>
    <mat-dialog-actions align="end">
      <button mat-button mat-dialog-close>{{ data.cancelLabel ?? '\u041E\u0442\u043C\u0435\u043D\u0430' }}</button>
      <button
        mat-flat-button
        [color]="data.destructive ? 'warn' : 'primary'"
        [mat-dialog-close]="true"
        cdkFocusInitial
      >
        {{ data.confirmLabel ?? '\u041F\u043E\u0434\u0442\u0432\u0435\u0440\u0434\u0438\u0442\u044C' }}
      </button>
    </mat-dialog-actions>
  `
    }]
  }], null, null);
})();
(() => {
  (typeof ngDevMode === "undefined" || ngDevMode) && \u0275setClassDebugInfo(ConfirmDialogComponent, { className: "ConfirmDialogComponent", filePath: "src/app/shared/confirm-dialog/confirm-dialog.component.ts", lineNumber: 58 });
})();

export {
  ConfirmDialogComponent
};
//# sourceMappingURL=chunk-KQCCUEDY.js.map
