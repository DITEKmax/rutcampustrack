import {
  MatTooltip,
  MatTooltipModule
} from "./chunk-KXY4TZSE.js";
import {
  MatCell,
  MatCellDef,
  MatColumnDef,
  MatHeaderCell,
  MatHeaderCellDef,
  MatHeaderRow,
  MatHeaderRowDef,
  MatRow,
  MatRowDef,
  MatTable,
  MatTableModule
} from "./chunk-GRVREKGH.js";
import {
  fullName
} from "./chunk-OGMCFIRC.js";
import "./chunk-F5IUR55I.js";
import {
  MatSnackBar,
  MatSnackBarModule
} from "./chunk-53YESMZZ.js";
import {
  MatCheckbox,
  MatCheckboxModule
} from "./chunk-MQ7WFICG.js";
import {
  MAT_DIALOG_DATA,
  MatDialog,
  MatDialogActions,
  MatDialogClose,
  MatDialogContent,
  MatDialogModule,
  MatDialogRef,
  MatDialogTitle
} from "./chunk-M24SPP55.js";
import {
  takeUntilDestroyed
} from "./chunk-JGPT6EAX.js";
import "./chunk-SEQXD3QE.js";
import {
  MatInput,
  MatInputModule
} from "./chunk-WQN3TC62.js";
import "./chunk-POR7KUSL.js";
import "./chunk-OIBNGD5S.js";
import {
  MatError,
  MatFormField,
  MatFormFieldModule,
  MatHint,
  MatLabel,
  MatSelect,
  MatSelectModule
} from "./chunk-Z3VV4J2X.js";
import {
  MatButton,
  MatButtonModule,
  MatIconButton
} from "./chunk-PAS4QIDL.js";
import {
  MatOption
} from "./chunk-4RJPSRCU.js";
import "./chunk-6JM2QIGP.js";
import "./chunk-75YPR2VK.js";
import {
  MatProgressBar,
  MatProgressBarModule
} from "./chunk-PWYB6KY4.js";
import "./chunk-2UA5NBNP.js";
import "./chunk-YIJEORIR.js";
import {
  _IdGenerator
} from "./chunk-B3BDHDAM.js";
import {
  DefaultValueAccessor,
  FormControl,
  FormControlDirective,
  FormControlName,
  FormGroup,
  FormGroupDirective,
  NgControlStatus,
  NgControlStatusGroup,
  NumberValueAccessor,
  ReactiveFormsModule,
  RequiredValidator,
  Validators,
  ɵNgNoValidate
} from "./chunk-XAMIXVT7.js";
import {
  AdminApiService
} from "./chunk-LPTENPUF.js";
import {
  ActivatedRoute,
  Router
} from "./chunk-643P6YYN.js";
import "./chunk-7WMFDRFR.js";
import {
  CommonModule
} from "./chunk-M5DKW26A.js";
import {
  ChangeDetectionStrategy,
  ChangeDetectorRef,
  Component,
  DestroyRef,
  EventEmitter,
  Injectable,
  InjectionToken,
  Input,
  NgModule,
  Optional,
  Output,
  ReplaySubject,
  SkipSelf,
  Subject,
  ViewEncapsulation,
  __objRest,
  __spreadProps,
  __spreadValues,
  booleanAttribute,
  computed,
  debounceTime,
  distinctUntilChanged,
  inject,
  input,
  numberAttribute,
  setClassMetadata,
  signal,
  ɵsetClassDebugInfo,
  ɵɵadvance,
  ɵɵattribute,
  ɵɵclassMapInterpolate1,
  ɵɵclassProp,
  ɵɵconditional,
  ɵɵdefineComponent,
  ɵɵdefineInjectable,
  ɵɵdefineInjector,
  ɵɵdefineNgModule,
  ɵɵelement,
  ɵɵelementContainerEnd,
  ɵɵelementContainerStart,
  ɵɵelementEnd,
  ɵɵelementStart,
  ɵɵgetCurrentView,
  ɵɵlistener,
  ɵɵnamespaceHTML,
  ɵɵnamespaceSVG,
  ɵɵnextContext,
  ɵɵproperty,
  ɵɵpureFunction0,
  ɵɵreference,
  ɵɵrepeater,
  ɵɵrepeaterCreate,
  ɵɵrepeaterTrackByIdentity,
  ɵɵresetView,
  ɵɵrestoreView,
  ɵɵtemplate,
  ɵɵtext,
  ɵɵtextInterpolate,
  ɵɵtextInterpolate1,
  ɵɵtextInterpolate3
} from "./chunk-MFRIGFR2.js";

// node_modules/@angular/material/fesm2022/paginator.mjs
function MatPaginator_Conditional_2_Conditional_3_For_4_Template(rf, ctx) {
  if (rf & 1) {
    \u0275\u0275elementStart(0, "mat-option", 17);
    \u0275\u0275text(1);
    \u0275\u0275elementEnd();
  }
  if (rf & 2) {
    const pageSizeOption_r3 = ctx.$implicit;
    \u0275\u0275property("value", pageSizeOption_r3);
    \u0275\u0275advance();
    \u0275\u0275textInterpolate1(" ", pageSizeOption_r3, " ");
  }
}
function MatPaginator_Conditional_2_Conditional_3_Template(rf, ctx) {
  if (rf & 1) {
    const _r1 = \u0275\u0275getCurrentView();
    \u0275\u0275elementStart(0, "mat-form-field", 14)(1, "mat-select", 16, 0);
    \u0275\u0275listener("selectionChange", function MatPaginator_Conditional_2_Conditional_3_Template_mat_select_selectionChange_1_listener($event) {
      \u0275\u0275restoreView(_r1);
      const ctx_r1 = \u0275\u0275nextContext(2);
      return \u0275\u0275resetView(ctx_r1._changePageSize($event.value));
    });
    \u0275\u0275repeaterCreate(3, MatPaginator_Conditional_2_Conditional_3_For_4_Template, 2, 2, "mat-option", 17, \u0275\u0275repeaterTrackByIdentity);
    \u0275\u0275elementEnd();
    \u0275\u0275elementStart(5, "div", 18);
    \u0275\u0275listener("click", function MatPaginator_Conditional_2_Conditional_3_Template_div_click_5_listener() {
      \u0275\u0275restoreView(_r1);
      const selectRef_r4 = \u0275\u0275reference(2);
      return \u0275\u0275resetView(selectRef_r4.open());
    });
    \u0275\u0275elementEnd()();
  }
  if (rf & 2) {
    const ctx_r1 = \u0275\u0275nextContext(2);
    \u0275\u0275property("appearance", ctx_r1._formFieldAppearance)("color", ctx_r1.color);
    \u0275\u0275advance();
    \u0275\u0275property("value", ctx_r1.pageSize)("disabled", ctx_r1.disabled)("aria-labelledby", ctx_r1._pageSizeLabelId)("panelClass", ctx_r1.selectConfig.panelClass || "")("disableOptionCentering", ctx_r1.selectConfig.disableOptionCentering);
    \u0275\u0275advance(2);
    \u0275\u0275repeater(ctx_r1._displayedPageSizeOptions);
  }
}
function MatPaginator_Conditional_2_Conditional_4_Template(rf, ctx) {
  if (rf & 1) {
    \u0275\u0275elementStart(0, "div", 15);
    \u0275\u0275text(1);
    \u0275\u0275elementEnd();
  }
  if (rf & 2) {
    const ctx_r1 = \u0275\u0275nextContext(2);
    \u0275\u0275advance();
    \u0275\u0275textInterpolate(ctx_r1.pageSize);
  }
}
function MatPaginator_Conditional_2_Template(rf, ctx) {
  if (rf & 1) {
    \u0275\u0275elementStart(0, "div", 3)(1, "div", 13);
    \u0275\u0275text(2);
    \u0275\u0275elementEnd();
    \u0275\u0275template(3, MatPaginator_Conditional_2_Conditional_3_Template, 6, 7, "mat-form-field", 14)(4, MatPaginator_Conditional_2_Conditional_4_Template, 2, 1, "div", 15);
    \u0275\u0275elementEnd();
  }
  if (rf & 2) {
    const ctx_r1 = \u0275\u0275nextContext();
    \u0275\u0275advance();
    \u0275\u0275attribute("id", ctx_r1._pageSizeLabelId);
    \u0275\u0275advance();
    \u0275\u0275textInterpolate1(" ", ctx_r1._intl.itemsPerPageLabel, " ");
    \u0275\u0275advance();
    \u0275\u0275conditional(ctx_r1._displayedPageSizeOptions.length > 1 ? 3 : -1);
    \u0275\u0275advance();
    \u0275\u0275conditional(ctx_r1._displayedPageSizeOptions.length <= 1 ? 4 : -1);
  }
}
function MatPaginator_Conditional_6_Template(rf, ctx) {
  if (rf & 1) {
    const _r5 = \u0275\u0275getCurrentView();
    \u0275\u0275elementStart(0, "button", 19);
    \u0275\u0275listener("click", function MatPaginator_Conditional_6_Template_button_click_0_listener() {
      \u0275\u0275restoreView(_r5);
      const ctx_r1 = \u0275\u0275nextContext();
      return \u0275\u0275resetView(ctx_r1._buttonClicked(0, ctx_r1._previousButtonsDisabled()));
    });
    \u0275\u0275namespaceSVG();
    \u0275\u0275elementStart(1, "svg", 8);
    \u0275\u0275element(2, "path", 20);
    \u0275\u0275elementEnd()();
  }
  if (rf & 2) {
    const ctx_r1 = \u0275\u0275nextContext();
    \u0275\u0275property("matTooltip", ctx_r1._intl.firstPageLabel)("matTooltipDisabled", ctx_r1._previousButtonsDisabled())("disabled", ctx_r1._previousButtonsDisabled())("tabindex", ctx_r1._previousButtonsDisabled() ? -1 : null);
    \u0275\u0275attribute("aria-label", ctx_r1._intl.firstPageLabel);
  }
}
function MatPaginator_Conditional_13_Template(rf, ctx) {
  if (rf & 1) {
    const _r6 = \u0275\u0275getCurrentView();
    \u0275\u0275elementStart(0, "button", 21);
    \u0275\u0275listener("click", function MatPaginator_Conditional_13_Template_button_click_0_listener() {
      \u0275\u0275restoreView(_r6);
      const ctx_r1 = \u0275\u0275nextContext();
      return \u0275\u0275resetView(ctx_r1._buttonClicked(ctx_r1.getNumberOfPages() - 1, ctx_r1._nextButtonsDisabled()));
    });
    \u0275\u0275namespaceSVG();
    \u0275\u0275elementStart(1, "svg", 8);
    \u0275\u0275element(2, "path", 22);
    \u0275\u0275elementEnd()();
  }
  if (rf & 2) {
    const ctx_r1 = \u0275\u0275nextContext();
    \u0275\u0275property("matTooltip", ctx_r1._intl.lastPageLabel)("matTooltipDisabled", ctx_r1._nextButtonsDisabled())("disabled", ctx_r1._nextButtonsDisabled())("tabindex", ctx_r1._nextButtonsDisabled() ? -1 : null);
    \u0275\u0275attribute("aria-label", ctx_r1._intl.lastPageLabel);
  }
}
var MatPaginatorIntl = class _MatPaginatorIntl {
  /**
   * Stream to emit from when labels are changed. Use this to notify components when the labels have
   * changed after initialization.
   */
  changes = new Subject();
  /** A label for the page size selector. */
  itemsPerPageLabel = "Items per page:";
  /** A label for the button that increments the current page. */
  nextPageLabel = "Next page";
  /** A label for the button that decrements the current page. */
  previousPageLabel = "Previous page";
  /** A label for the button that moves to the first page. */
  firstPageLabel = "First page";
  /** A label for the button that moves to the last page. */
  lastPageLabel = "Last page";
  /** A label for the range of items within the current page and the length of the whole list. */
  getRangeLabel = (page, pageSize, length) => {
    if (length == 0 || pageSize == 0) {
      return `0 of ${length}`;
    }
    length = Math.max(length, 0);
    const startIndex = page * pageSize;
    const endIndex = startIndex < length ? Math.min(startIndex + pageSize, length) : startIndex + pageSize;
    return `${startIndex + 1} \u2013 ${endIndex} of ${length}`;
  };
  static \u0275fac = function MatPaginatorIntl_Factory(__ngFactoryType__) {
    return new (__ngFactoryType__ || _MatPaginatorIntl)();
  };
  static \u0275prov = /* @__PURE__ */ \u0275\u0275defineInjectable({
    token: _MatPaginatorIntl,
    factory: _MatPaginatorIntl.\u0275fac,
    providedIn: "root"
  });
};
(() => {
  (typeof ngDevMode === "undefined" || ngDevMode) && setClassMetadata(MatPaginatorIntl, [{
    type: Injectable,
    args: [{
      providedIn: "root"
    }]
  }], null, null);
})();
function MAT_PAGINATOR_INTL_PROVIDER_FACTORY(parentIntl) {
  return parentIntl || new MatPaginatorIntl();
}
var MAT_PAGINATOR_INTL_PROVIDER = {
  // If there is already an MatPaginatorIntl available, use that. Otherwise, provide a new one.
  provide: MatPaginatorIntl,
  deps: [[new Optional(), new SkipSelf(), MatPaginatorIntl]],
  useFactory: MAT_PAGINATOR_INTL_PROVIDER_FACTORY
};
var DEFAULT_PAGE_SIZE = 50;
var MAT_PAGINATOR_DEFAULT_OPTIONS = new InjectionToken("MAT_PAGINATOR_DEFAULT_OPTIONS");
var MatPaginator = class _MatPaginator {
  _intl = inject(MatPaginatorIntl);
  _changeDetectorRef = inject(ChangeDetectorRef);
  /** If set, styles the "page size" form field with the designated style. */
  _formFieldAppearance;
  /** ID for the DOM node containing the paginator's items per page label. */
  _pageSizeLabelId = inject(_IdGenerator).getId("mat-paginator-page-size-label-");
  _intlChanges;
  _isInitialized = false;
  _initializedStream = new ReplaySubject(1);
  /**
   * Theme color of the underlying form controls. This API is supported in M2
   * themes only,it has no effect in M3 themes. For color customization in M3, see https://material.angular.dev/components/paginator/styling.
   *
   * For information on applying color variants in M3, see
   * https://material.angular.dev/guide/material-2-theming#optional-add-backwards-compatibility-styles-for-color-variants
   */
  color;
  /** The zero-based page index of the displayed list of items. Defaulted to 0. */
  get pageIndex() {
    return this._pageIndex;
  }
  set pageIndex(value) {
    this._pageIndex = Math.max(value || 0, 0);
    this._changeDetectorRef.markForCheck();
  }
  _pageIndex = 0;
  /** The length of the total number of items that are being paginated. Defaulted to 0. */
  get length() {
    return this._length;
  }
  set length(value) {
    this._length = value || 0;
    this._changeDetectorRef.markForCheck();
  }
  _length = 0;
  /** Number of items to display on a page. By default set to 50. */
  get pageSize() {
    return this._pageSize;
  }
  set pageSize(value) {
    this._pageSize = Math.max(value || 0, 0);
    this._updateDisplayedPageSizeOptions();
  }
  _pageSize;
  /** The set of provided page size options to display to the user. */
  get pageSizeOptions() {
    return this._pageSizeOptions;
  }
  set pageSizeOptions(value) {
    this._pageSizeOptions = (value || []).map((p) => numberAttribute(p, 0));
    this._updateDisplayedPageSizeOptions();
  }
  _pageSizeOptions = [];
  /** Whether to hide the page size selection UI from the user. */
  hidePageSize = false;
  /** Whether to show the first/last buttons UI to the user. */
  showFirstLastButtons = false;
  /** Used to configure the underlying `MatSelect` inside the paginator. */
  selectConfig = {};
  /** Whether the paginator is disabled. */
  disabled = false;
  /** Event emitted when the paginator changes the page size or page index. */
  page = new EventEmitter();
  /** Displayed set of page size options. Will be sorted and include current page size. */
  _displayedPageSizeOptions;
  /** Emits when the paginator is initialized. */
  initialized = this._initializedStream;
  constructor() {
    const _intl = this._intl;
    const defaults = inject(MAT_PAGINATOR_DEFAULT_OPTIONS, {
      optional: true
    });
    this._intlChanges = _intl.changes.subscribe(() => this._changeDetectorRef.markForCheck());
    if (defaults) {
      const {
        pageSize,
        pageSizeOptions,
        hidePageSize,
        showFirstLastButtons
      } = defaults;
      if (pageSize != null) {
        this._pageSize = pageSize;
      }
      if (pageSizeOptions != null) {
        this._pageSizeOptions = pageSizeOptions;
      }
      if (hidePageSize != null) {
        this.hidePageSize = hidePageSize;
      }
      if (showFirstLastButtons != null) {
        this.showFirstLastButtons = showFirstLastButtons;
      }
    }
    this._formFieldAppearance = defaults?.formFieldAppearance || "outline";
  }
  ngOnInit() {
    this._isInitialized = true;
    this._updateDisplayedPageSizeOptions();
    this._initializedStream.next();
  }
  ngOnDestroy() {
    this._initializedStream.complete();
    this._intlChanges.unsubscribe();
  }
  /** Advances to the next page if it exists. */
  nextPage() {
    if (this.hasNextPage()) {
      this._navigate(this.pageIndex + 1);
    }
  }
  /** Move back to the previous page if it exists. */
  previousPage() {
    if (this.hasPreviousPage()) {
      this._navigate(this.pageIndex - 1);
    }
  }
  /** Move to the first page if not already there. */
  firstPage() {
    if (this.hasPreviousPage()) {
      this._navigate(0);
    }
  }
  /** Move to the last page if not already there. */
  lastPage() {
    if (this.hasNextPage()) {
      this._navigate(this.getNumberOfPages() - 1);
    }
  }
  /** Whether there is a previous page. */
  hasPreviousPage() {
    return this.pageIndex >= 1 && this.pageSize != 0;
  }
  /** Whether there is a next page. */
  hasNextPage() {
    const maxPageIndex = this.getNumberOfPages() - 1;
    return this.pageIndex < maxPageIndex && this.pageSize != 0;
  }
  /** Calculate the number of pages */
  getNumberOfPages() {
    if (!this.pageSize) {
      return 0;
    }
    return Math.ceil(this.length / this.pageSize);
  }
  /**
   * Changes the page size so that the first item displayed on the page will still be
   * displayed using the new page size.
   *
   * For example, if the page size is 10 and on the second page (items indexed 10-19) then
   * switching so that the page size is 5 will set the third page as the current page so
   * that the 10th item will still be displayed.
   */
  _changePageSize(pageSize) {
    const startIndex = this.pageIndex * this.pageSize;
    const previousPageIndex = this.pageIndex;
    this.pageIndex = Math.floor(startIndex / pageSize) || 0;
    this.pageSize = pageSize;
    this._emitPageEvent(previousPageIndex);
  }
  /** Checks whether the buttons for going forwards should be disabled. */
  _nextButtonsDisabled() {
    return this.disabled || !this.hasNextPage();
  }
  /** Checks whether the buttons for going backwards should be disabled. */
  _previousButtonsDisabled() {
    return this.disabled || !this.hasPreviousPage();
  }
  /**
   * Updates the list of page size options to display to the user. Includes making sure that
   * the page size is an option and that the list is sorted.
   */
  _updateDisplayedPageSizeOptions() {
    if (!this._isInitialized) {
      return;
    }
    if (!this.pageSize) {
      this._pageSize = this.pageSizeOptions.length != 0 ? this.pageSizeOptions[0] : DEFAULT_PAGE_SIZE;
    }
    this._displayedPageSizeOptions = this.pageSizeOptions.slice();
    if (this._displayedPageSizeOptions.indexOf(this.pageSize) === -1) {
      this._displayedPageSizeOptions.push(this.pageSize);
    }
    this._displayedPageSizeOptions.sort((a, b) => a - b);
    this._changeDetectorRef.markForCheck();
  }
  /** Emits an event notifying that a change of the paginator's properties has been triggered. */
  _emitPageEvent(previousPageIndex) {
    this.page.emit({
      previousPageIndex,
      pageIndex: this.pageIndex,
      pageSize: this.pageSize,
      length: this.length
    });
  }
  /** Navigates to a specific page index. */
  _navigate(index) {
    const previousIndex = this.pageIndex;
    if (index !== previousIndex) {
      this.pageIndex = index;
      this._emitPageEvent(previousIndex);
    }
  }
  /**
   * Callback invoked when one of the navigation buttons is called.
   * @param targetIndex Index to which the paginator should navigate.
   * @param isDisabled Whether the button is disabled.
   */
  _buttonClicked(targetIndex, isDisabled) {
    if (!isDisabled) {
      this._navigate(targetIndex);
    }
  }
  static \u0275fac = function MatPaginator_Factory(__ngFactoryType__) {
    return new (__ngFactoryType__ || _MatPaginator)();
  };
  static \u0275cmp = /* @__PURE__ */ \u0275\u0275defineComponent({
    type: _MatPaginator,
    selectors: [["mat-paginator"]],
    hostAttrs: ["role", "group", 1, "mat-mdc-paginator"],
    inputs: {
      color: "color",
      pageIndex: [2, "pageIndex", "pageIndex", numberAttribute],
      length: [2, "length", "length", numberAttribute],
      pageSize: [2, "pageSize", "pageSize", numberAttribute],
      pageSizeOptions: "pageSizeOptions",
      hidePageSize: [2, "hidePageSize", "hidePageSize", booleanAttribute],
      showFirstLastButtons: [2, "showFirstLastButtons", "showFirstLastButtons", booleanAttribute],
      selectConfig: "selectConfig",
      disabled: [2, "disabled", "disabled", booleanAttribute]
    },
    outputs: {
      page: "page"
    },
    exportAs: ["matPaginator"],
    decls: 14,
    vars: 14,
    consts: [["selectRef", ""], [1, "mat-mdc-paginator-outer-container"], [1, "mat-mdc-paginator-container"], [1, "mat-mdc-paginator-page-size"], [1, "mat-mdc-paginator-range-actions"], ["aria-live", "polite", 1, "mat-mdc-paginator-range-label"], ["mat-icon-button", "", "type", "button", "matTooltipPosition", "above", "disabledInteractive", "", 1, "mat-mdc-paginator-navigation-first", 3, "matTooltip", "matTooltipDisabled", "disabled", "tabindex"], ["mat-icon-button", "", "type", "button", "matTooltipPosition", "above", "disabledInteractive", "", 1, "mat-mdc-paginator-navigation-previous", 3, "click", "matTooltip", "matTooltipDisabled", "disabled", "tabindex"], ["viewBox", "0 0 24 24", "focusable", "false", "aria-hidden", "true", 1, "mat-mdc-paginator-icon"], ["d", "M15.41 7.41L14 6l-6 6 6 6 1.41-1.41L10.83 12z"], ["mat-icon-button", "", "type", "button", "matTooltipPosition", "above", "disabledInteractive", "", 1, "mat-mdc-paginator-navigation-next", 3, "click", "matTooltip", "matTooltipDisabled", "disabled", "tabindex"], ["d", "M10 6L8.59 7.41 13.17 12l-4.58 4.59L10 18l6-6z"], ["mat-icon-button", "", "type", "button", "matTooltipPosition", "above", "disabledInteractive", "", 1, "mat-mdc-paginator-navigation-last", 3, "matTooltip", "matTooltipDisabled", "disabled", "tabindex"], [1, "mat-mdc-paginator-page-size-label"], [1, "mat-mdc-paginator-page-size-select", 3, "appearance", "color"], [1, "mat-mdc-paginator-page-size-value"], ["hideSingleSelectionIndicator", "", 3, "selectionChange", "value", "disabled", "aria-labelledby", "panelClass", "disableOptionCentering"], [3, "value"], [1, "mat-mdc-paginator-touch-target", 3, "click"], ["mat-icon-button", "", "type", "button", "matTooltipPosition", "above", "disabledInteractive", "", 1, "mat-mdc-paginator-navigation-first", 3, "click", "matTooltip", "matTooltipDisabled", "disabled", "tabindex"], ["d", "M18.41 16.59L13.82 12l4.59-4.59L17 6l-6 6 6 6zM6 6h2v12H6z"], ["mat-icon-button", "", "type", "button", "matTooltipPosition", "above", "disabledInteractive", "", 1, "mat-mdc-paginator-navigation-last", 3, "click", "matTooltip", "matTooltipDisabled", "disabled", "tabindex"], ["d", "M5.59 7.41L10.18 12l-4.59 4.59L7 18l6-6-6-6zM16 6h2v12h-2z"]],
    template: function MatPaginator_Template(rf, ctx) {
      if (rf & 1) {
        \u0275\u0275elementStart(0, "div", 1)(1, "div", 2);
        \u0275\u0275template(2, MatPaginator_Conditional_2_Template, 5, 4, "div", 3);
        \u0275\u0275elementStart(3, "div", 4)(4, "div", 5);
        \u0275\u0275text(5);
        \u0275\u0275elementEnd();
        \u0275\u0275template(6, MatPaginator_Conditional_6_Template, 3, 5, "button", 6);
        \u0275\u0275elementStart(7, "button", 7);
        \u0275\u0275listener("click", function MatPaginator_Template_button_click_7_listener() {
          return ctx._buttonClicked(ctx.pageIndex - 1, ctx._previousButtonsDisabled());
        });
        \u0275\u0275namespaceSVG();
        \u0275\u0275elementStart(8, "svg", 8);
        \u0275\u0275element(9, "path", 9);
        \u0275\u0275elementEnd()();
        \u0275\u0275namespaceHTML();
        \u0275\u0275elementStart(10, "button", 10);
        \u0275\u0275listener("click", function MatPaginator_Template_button_click_10_listener() {
          return ctx._buttonClicked(ctx.pageIndex + 1, ctx._nextButtonsDisabled());
        });
        \u0275\u0275namespaceSVG();
        \u0275\u0275elementStart(11, "svg", 8);
        \u0275\u0275element(12, "path", 11);
        \u0275\u0275elementEnd()();
        \u0275\u0275template(13, MatPaginator_Conditional_13_Template, 3, 5, "button", 12);
        \u0275\u0275elementEnd()()();
      }
      if (rf & 2) {
        \u0275\u0275advance(2);
        \u0275\u0275conditional(!ctx.hidePageSize ? 2 : -1);
        \u0275\u0275advance(3);
        \u0275\u0275textInterpolate1(" ", ctx._intl.getRangeLabel(ctx.pageIndex, ctx.pageSize, ctx.length), " ");
        \u0275\u0275advance();
        \u0275\u0275conditional(ctx.showFirstLastButtons ? 6 : -1);
        \u0275\u0275advance();
        \u0275\u0275property("matTooltip", ctx._intl.previousPageLabel)("matTooltipDisabled", ctx._previousButtonsDisabled())("disabled", ctx._previousButtonsDisabled())("tabindex", ctx._previousButtonsDisabled() ? -1 : null);
        \u0275\u0275attribute("aria-label", ctx._intl.previousPageLabel);
        \u0275\u0275advance(3);
        \u0275\u0275property("matTooltip", ctx._intl.nextPageLabel)("matTooltipDisabled", ctx._nextButtonsDisabled())("disabled", ctx._nextButtonsDisabled())("tabindex", ctx._nextButtonsDisabled() ? -1 : null);
        \u0275\u0275attribute("aria-label", ctx._intl.nextPageLabel);
        \u0275\u0275advance(3);
        \u0275\u0275conditional(ctx.showFirstLastButtons ? 13 : -1);
      }
    },
    dependencies: [MatFormField, MatSelect, MatOption, MatIconButton, MatTooltip],
    styles: [".mat-mdc-paginator{display:block;-moz-osx-font-smoothing:grayscale;-webkit-font-smoothing:antialiased;color:var(--mat-paginator-container-text-color, var(--mat-sys-on-surface));background-color:var(--mat-paginator-container-background-color, var(--mat-sys-surface));font-family:var(--mat-paginator-container-text-font, var(--mat-sys-body-small-font));line-height:var(--mat-paginator-container-text-line-height, var(--mat-sys-body-small-line-height));font-size:var(--mat-paginator-container-text-size, var(--mat-sys-body-small-size));font-weight:var(--mat-paginator-container-text-weight, var(--mat-sys-body-small-weight));letter-spacing:var(--mat-paginator-container-text-tracking, var(--mat-sys-body-small-tracking));--mat-form-field-container-height:var(--mat-paginator-form-field-container-height, 40px);--mat-form-field-container-vertical-padding:var(--mat-paginator-form-field-container-vertical-padding, 8px)}.mat-mdc-paginator .mat-mdc-select-value{font-size:var(--mat-paginator-select-trigger-text-size, var(--mat-sys-body-small-size))}.mat-mdc-paginator .mat-mdc-form-field-subscript-wrapper{display:none}.mat-mdc-paginator .mat-mdc-select{line-height:1.5}.mat-mdc-paginator-outer-container{display:flex}.mat-mdc-paginator-container{display:flex;align-items:center;justify-content:flex-end;padding:0 8px;flex-wrap:wrap;width:100%;min-height:var(--mat-paginator-container-size, 56px)}.mat-mdc-paginator-page-size{display:flex;align-items:baseline;margin-right:8px}[dir=rtl] .mat-mdc-paginator-page-size{margin-right:0;margin-left:8px}.mat-mdc-paginator-page-size-label{margin:0 4px}.mat-mdc-paginator-page-size-select{margin:0 4px;width:84px}.mat-mdc-paginator-range-label{margin:0 32px 0 24px}.mat-mdc-paginator-range-actions{display:flex;align-items:center}.mat-mdc-paginator-icon{display:inline-block;width:28px;fill:var(--mat-paginator-enabled-icon-color, var(--mat-sys-on-surface-variant))}.mat-mdc-icon-button[aria-disabled] .mat-mdc-paginator-icon{fill:var(--mat-paginator-disabled-icon-color, color-mix(in srgb, var(--mat-sys-on-surface) 38%, transparent))}[dir=rtl] .mat-mdc-paginator-icon{transform:rotate(180deg)}@media(forced-colors: active){.mat-mdc-icon-button[aria-disabled] .mat-mdc-paginator-icon,.mat-mdc-paginator-icon{fill:currentColor}.mat-mdc-paginator-range-actions .mat-mdc-icon-button{outline:solid 1px}.mat-mdc-paginator-range-actions .mat-mdc-icon-button[aria-disabled]{color:GrayText}}.mat-mdc-paginator-touch-target{display:var(--mat-paginator-touch-target-display, block);position:absolute;top:50%;left:50%;width:84px;height:48px;background-color:rgba(0,0,0,0);transform:translate(-50%, -50%);cursor:pointer}\n"],
    encapsulation: 2,
    changeDetection: 0
  });
};
(() => {
  (typeof ngDevMode === "undefined" || ngDevMode) && setClassMetadata(MatPaginator, [{
    type: Component,
    args: [{
      selector: "mat-paginator",
      exportAs: "matPaginator",
      host: {
        "class": "mat-mdc-paginator",
        "role": "group"
      },
      changeDetection: ChangeDetectionStrategy.OnPush,
      encapsulation: ViewEncapsulation.None,
      imports: [MatFormField, MatSelect, MatOption, MatIconButton, MatTooltip],
      template: '<div class="mat-mdc-paginator-outer-container">\n  <div class="mat-mdc-paginator-container">\n    @if (!hidePageSize) {\n      <div class="mat-mdc-paginator-page-size">\n        <div class="mat-mdc-paginator-page-size-label" [attr.id]="_pageSizeLabelId">\n          {{_intl.itemsPerPageLabel}}\n        </div>\n\n        @if (_displayedPageSizeOptions.length > 1) {\n          <mat-form-field\n            [appearance]="_formFieldAppearance!"\n            [color]="color"\n            class="mat-mdc-paginator-page-size-select">\n            <mat-select\n              #selectRef\n              [value]="pageSize"\n              [disabled]="disabled"\n              [aria-labelledby]="_pageSizeLabelId"\n              [panelClass]="selectConfig.panelClass || \'\'"\n              [disableOptionCentering]="selectConfig.disableOptionCentering"\n              (selectionChange)="_changePageSize($event.value)"\n              hideSingleSelectionIndicator>\n              @for (pageSizeOption of _displayedPageSizeOptions; track pageSizeOption) {\n                <mat-option [value]="pageSizeOption">\n                  {{pageSizeOption}}\n                </mat-option>\n              }\n            </mat-select>\n          <div class="mat-mdc-paginator-touch-target" (click)="selectRef.open()"></div>\n          </mat-form-field>\n        }\n\n        @if (_displayedPageSizeOptions.length <= 1) {\n          <div class="mat-mdc-paginator-page-size-value">{{pageSize}}</div>\n        }\n      </div>\n    }\n\n    <div class="mat-mdc-paginator-range-actions">\n      <div class="mat-mdc-paginator-range-label" aria-live="polite">\n        {{_intl.getRangeLabel(pageIndex, pageSize, length)}}\n      </div>\n\n      <!--\n      The buttons use `disabledInteractive` so that they can retain focus if they become disabled,\n      otherwise focus is moved to the document body. However, users should not be able to navigate\n      into these buttons, so `tabindex` is set to -1 when disabled.\n      -->\n\n      @if (showFirstLastButtons) {\n        <button mat-icon-button type="button"\n                class="mat-mdc-paginator-navigation-first"\n                (click)="_buttonClicked(0, _previousButtonsDisabled())"\n                [attr.aria-label]="_intl.firstPageLabel"\n                [matTooltip]="_intl.firstPageLabel"\n                [matTooltipDisabled]="_previousButtonsDisabled()"\n                matTooltipPosition="above"\n                [disabled]="_previousButtonsDisabled()"\n                [tabindex]="_previousButtonsDisabled() ? -1 : null"\n                disabledInteractive>\n          <svg class="mat-mdc-paginator-icon"\n              viewBox="0 0 24 24"\n              focusable="false"\n              aria-hidden="true">\n            <path d="M18.41 16.59L13.82 12l4.59-4.59L17 6l-6 6 6 6zM6 6h2v12H6z"/>\n          </svg>\n        </button>\n      }\n      <button mat-icon-button type="button"\n              class="mat-mdc-paginator-navigation-previous"\n              (click)="_buttonClicked(pageIndex - 1, _previousButtonsDisabled())"\n              [attr.aria-label]="_intl.previousPageLabel"\n              [matTooltip]="_intl.previousPageLabel"\n              [matTooltipDisabled]="_previousButtonsDisabled()"\n              matTooltipPosition="above"\n              [disabled]="_previousButtonsDisabled()"\n              [tabindex]="_previousButtonsDisabled() ? -1 : null"\n              disabledInteractive>\n        <svg class="mat-mdc-paginator-icon"\n             viewBox="0 0 24 24"\n             focusable="false"\n             aria-hidden="true">\n          <path d="M15.41 7.41L14 6l-6 6 6 6 1.41-1.41L10.83 12z"/>\n        </svg>\n      </button>\n      <button mat-icon-button type="button"\n              class="mat-mdc-paginator-navigation-next"\n              (click)="_buttonClicked(pageIndex + 1, _nextButtonsDisabled())"\n              [attr.aria-label]="_intl.nextPageLabel"\n              [matTooltip]="_intl.nextPageLabel"\n              [matTooltipDisabled]="_nextButtonsDisabled()"\n              matTooltipPosition="above"\n              [disabled]="_nextButtonsDisabled()"\n              [tabindex]="_nextButtonsDisabled() ? -1 : null"\n              disabledInteractive>\n        <svg class="mat-mdc-paginator-icon"\n             viewBox="0 0 24 24"\n             focusable="false"\n             aria-hidden="true">\n          <path d="M10 6L8.59 7.41 13.17 12l-4.58 4.59L10 18l6-6z"/>\n        </svg>\n      </button>\n      @if (showFirstLastButtons) {\n        <button mat-icon-button type="button"\n                class="mat-mdc-paginator-navigation-last"\n                (click)="_buttonClicked(getNumberOfPages() - 1, _nextButtonsDisabled())"\n                [attr.aria-label]="_intl.lastPageLabel"\n                [matTooltip]="_intl.lastPageLabel"\n                [matTooltipDisabled]="_nextButtonsDisabled()"\n                matTooltipPosition="above"\n                [disabled]="_nextButtonsDisabled()"\n                [tabindex]="_nextButtonsDisabled() ? -1 : null"\n                disabledInteractive>\n          <svg class="mat-mdc-paginator-icon"\n              viewBox="0 0 24 24"\n              focusable="false"\n              aria-hidden="true">\n            <path d="M5.59 7.41L10.18 12l-4.59 4.59L7 18l6-6-6-6zM16 6h2v12h-2z"/>\n          </svg>\n        </button>\n      }\n    </div>\n  </div>\n</div>\n',
      styles: [".mat-mdc-paginator{display:block;-moz-osx-font-smoothing:grayscale;-webkit-font-smoothing:antialiased;color:var(--mat-paginator-container-text-color, var(--mat-sys-on-surface));background-color:var(--mat-paginator-container-background-color, var(--mat-sys-surface));font-family:var(--mat-paginator-container-text-font, var(--mat-sys-body-small-font));line-height:var(--mat-paginator-container-text-line-height, var(--mat-sys-body-small-line-height));font-size:var(--mat-paginator-container-text-size, var(--mat-sys-body-small-size));font-weight:var(--mat-paginator-container-text-weight, var(--mat-sys-body-small-weight));letter-spacing:var(--mat-paginator-container-text-tracking, var(--mat-sys-body-small-tracking));--mat-form-field-container-height:var(--mat-paginator-form-field-container-height, 40px);--mat-form-field-container-vertical-padding:var(--mat-paginator-form-field-container-vertical-padding, 8px)}.mat-mdc-paginator .mat-mdc-select-value{font-size:var(--mat-paginator-select-trigger-text-size, var(--mat-sys-body-small-size))}.mat-mdc-paginator .mat-mdc-form-field-subscript-wrapper{display:none}.mat-mdc-paginator .mat-mdc-select{line-height:1.5}.mat-mdc-paginator-outer-container{display:flex}.mat-mdc-paginator-container{display:flex;align-items:center;justify-content:flex-end;padding:0 8px;flex-wrap:wrap;width:100%;min-height:var(--mat-paginator-container-size, 56px)}.mat-mdc-paginator-page-size{display:flex;align-items:baseline;margin-right:8px}[dir=rtl] .mat-mdc-paginator-page-size{margin-right:0;margin-left:8px}.mat-mdc-paginator-page-size-label{margin:0 4px}.mat-mdc-paginator-page-size-select{margin:0 4px;width:84px}.mat-mdc-paginator-range-label{margin:0 32px 0 24px}.mat-mdc-paginator-range-actions{display:flex;align-items:center}.mat-mdc-paginator-icon{display:inline-block;width:28px;fill:var(--mat-paginator-enabled-icon-color, var(--mat-sys-on-surface-variant))}.mat-mdc-icon-button[aria-disabled] .mat-mdc-paginator-icon{fill:var(--mat-paginator-disabled-icon-color, color-mix(in srgb, var(--mat-sys-on-surface) 38%, transparent))}[dir=rtl] .mat-mdc-paginator-icon{transform:rotate(180deg)}@media(forced-colors: active){.mat-mdc-icon-button[aria-disabled] .mat-mdc-paginator-icon,.mat-mdc-paginator-icon{fill:currentColor}.mat-mdc-paginator-range-actions .mat-mdc-icon-button{outline:solid 1px}.mat-mdc-paginator-range-actions .mat-mdc-icon-button[aria-disabled]{color:GrayText}}.mat-mdc-paginator-touch-target{display:var(--mat-paginator-touch-target-display, block);position:absolute;top:50%;left:50%;width:84px;height:48px;background-color:rgba(0,0,0,0);transform:translate(-50%, -50%);cursor:pointer}\n"]
    }]
  }], () => [], {
    color: [{
      type: Input
    }],
    pageIndex: [{
      type: Input,
      args: [{
        transform: numberAttribute
      }]
    }],
    length: [{
      type: Input,
      args: [{
        transform: numberAttribute
      }]
    }],
    pageSize: [{
      type: Input,
      args: [{
        transform: numberAttribute
      }]
    }],
    pageSizeOptions: [{
      type: Input
    }],
    hidePageSize: [{
      type: Input,
      args: [{
        transform: booleanAttribute
      }]
    }],
    showFirstLastButtons: [{
      type: Input,
      args: [{
        transform: booleanAttribute
      }]
    }],
    selectConfig: [{
      type: Input
    }],
    disabled: [{
      type: Input,
      args: [{
        transform: booleanAttribute
      }]
    }],
    page: [{
      type: Output
    }]
  });
})();
var MatPaginatorModule = class _MatPaginatorModule {
  static \u0275fac = function MatPaginatorModule_Factory(__ngFactoryType__) {
    return new (__ngFactoryType__ || _MatPaginatorModule)();
  };
  static \u0275mod = /* @__PURE__ */ \u0275\u0275defineNgModule({
    type: _MatPaginatorModule,
    imports: [MatButtonModule, MatSelectModule, MatTooltipModule, MatPaginator],
    exports: [MatPaginator]
  });
  static \u0275inj = /* @__PURE__ */ \u0275\u0275defineInjector({
    providers: [MAT_PAGINATOR_INTL_PROVIDER],
    imports: [MatButtonModule, MatSelectModule, MatTooltipModule, MatPaginator]
  });
};
(() => {
  (typeof ngDevMode === "undefined" || ngDevMode) && setClassMetadata(MatPaginatorModule, [{
    type: NgModule,
    args: [{
      imports: [MatButtonModule, MatSelectModule, MatTooltipModule, MatPaginator],
      exports: [MatPaginator],
      providers: [MAT_PAGINATOR_INTL_PROVIDER]
    }]
  }], null, null);
})();

// src/app/features/admin/shared/role-chip/role-chip.component.ts
var ROLE_LABELS = {
  ADMIN: "\u0410\u0434\u043C\u0438\u043D",
  TEACHER: "\u041F\u0440\u0435\u043F\u043E\u0434\u0430\u0432\u0430\u0442\u0435\u043B\u044C",
  STUDENT: "\u0421\u0442\u0443\u0434\u0435\u043D\u0442"
};
var RoleChipComponent = class _RoleChipComponent {
  role = input.required();
  roleLabel = computed(() => ROLE_LABELS[this.role()] ?? this.role());
  cssSuffix = computed(() => this.role().toLowerCase());
  static \u0275fac = function RoleChipComponent_Factory(__ngFactoryType__) {
    return new (__ngFactoryType__ || _RoleChipComponent)();
  };
  static \u0275cmp = /* @__PURE__ */ \u0275\u0275defineComponent({ type: _RoleChipComponent, selectors: [["app-role-chip"]], inputs: { role: [1, "role"] }, decls: 2, vars: 4, template: function RoleChipComponent_Template(rf, ctx) {
    if (rf & 1) {
      \u0275\u0275elementStart(0, "span");
      \u0275\u0275text(1);
      \u0275\u0275elementEnd();
    }
    if (rf & 2) {
      \u0275\u0275classMapInterpolate1("role-chip role-chip--", ctx.cssSuffix(), "");
      \u0275\u0275advance();
      \u0275\u0275textInterpolate(ctx.roleLabel());
    }
  }, encapsulation: 2 });
};
(() => {
  (typeof ngDevMode === "undefined" || ngDevMode) && setClassMetadata(RoleChipComponent, [{
    type: Component,
    args: [{
      selector: "app-role-chip",
      standalone: true,
      template: `<span class="role-chip role-chip--{{cssSuffix()}}">{{ roleLabel() }}</span>`
    }]
  }], null, null);
})();
(() => {
  (typeof ngDevMode === "undefined" || ngDevMode) && \u0275setClassDebugInfo(RoleChipComponent, { className: "RoleChipComponent", filePath: "src/app/features/admin/shared/role-chip/role-chip.component.ts", lineNumber: 15 });
})();

// src/app/features/admin/shared/status-chip/status-chip.component.ts
var STATUS_LABELS = {
  active: "\u0410\u043A\u0442\u0438\u0432\u0435\u043D",
  archived: "\u0410\u0440\u0445\u0438\u0432\u0438\u0440\u043E\u0432\u0430\u043D",
  expelled: "\u041E\u0442\u0447\u0438\u0441\u043B\u0435\u043D",
  suspended: "\u041F\u0440\u0438\u043E\u0441\u0442\u0430\u043D\u043E\u0432\u043B\u0435\u043D"
};
var StatusChipComponent = class _StatusChipComponent {
  status = input.required();
  statusLabel = computed(() => STATUS_LABELS[this.status()] ?? this.status());
  static \u0275fac = function StatusChipComponent_Factory(__ngFactoryType__) {
    return new (__ngFactoryType__ || _StatusChipComponent)();
  };
  static \u0275cmp = /* @__PURE__ */ \u0275\u0275defineComponent({ type: _StatusChipComponent, selectors: [["app-status-chip"]], inputs: { status: [1, "status"] }, decls: 2, vars: 4, template: function StatusChipComponent_Template(rf, ctx) {
    if (rf & 1) {
      \u0275\u0275elementStart(0, "span");
      \u0275\u0275text(1);
      \u0275\u0275elementEnd();
    }
    if (rf & 2) {
      \u0275\u0275classMapInterpolate1("status-chip-admin status-chip-admin--", ctx.status(), "");
      \u0275\u0275advance();
      \u0275\u0275textInterpolate(ctx.statusLabel());
    }
  }, encapsulation: 2 });
};
(() => {
  (typeof ngDevMode === "undefined" || ngDevMode) && setClassMetadata(StatusChipComponent, [{
    type: Component,
    args: [{
      selector: "app-status-chip",
      standalone: true,
      template: `<span class="status-chip-admin status-chip-admin--{{status()}}">{{ statusLabel() }}</span>`
    }]
  }], null, null);
})();
(() => {
  (typeof ngDevMode === "undefined" || ngDevMode) && \u0275setClassDebugInfo(StatusChipComponent, { className: "StatusChipComponent", filePath: "src/app/features/admin/shared/status-chip/status-chip.component.ts", lineNumber: 16 });
})();

// src/app/features/admin/users/user-dialog/user-dialog.component.ts
var _forTrack0 = ($index, $item) => $item.id;
function UserDialogComponent_Conditional_0_Template(rf, ctx) {
  if (rf & 1) {
    \u0275\u0275elementStart(0, "h2", 0);
    \u0275\u0275text(1, "\u041D\u043E\u0432\u044B\u0439 \u043F\u043E\u043B\u044C\u0437\u043E\u0432\u0430\u0442\u0435\u043B\u044C");
    \u0275\u0275elementEnd();
  }
}
function UserDialogComponent_Conditional_1_Template(rf, ctx) {
  if (rf & 1) {
    \u0275\u0275elementStart(0, "h2", 0);
    \u0275\u0275text(1, "\u0420\u0435\u0434\u0430\u043A\u0442\u0438\u0440\u043E\u0432\u0430\u0442\u044C \u043F\u043E\u043B\u044C\u0437\u043E\u0432\u0430\u0442\u0435\u043B\u044F");
    \u0275\u0275elementEnd();
  }
}
function UserDialogComponent_Conditional_3_Template(rf, ctx) {
  if (rf & 1) {
    \u0275\u0275elementStart(0, "div", 1)(1, "div", 5);
    \u0275\u0275element(2, "i", 6);
    \u0275\u0275elementEnd();
    \u0275\u0275elementStart(3, "p", 7);
    \u0275\u0275text(4, "\u041F\u043E\u043B\u044C\u0437\u043E\u0432\u0430\u0442\u0435\u043B\u044C \u0441\u043E\u0437\u0434\u0430\u043D");
    \u0275\u0275elementEnd();
    \u0275\u0275elementStart(5, "div", 8)(6, "div", 9)(7, "span", 10);
    \u0275\u0275text(8, "\u041B\u043E\u0433\u0438\u043D");
    \u0275\u0275elementEnd();
    \u0275\u0275elementStart(9, "code", 11);
    \u0275\u0275text(10);
    \u0275\u0275elementEnd()();
    \u0275\u0275elementStart(11, "div", 9)(12, "span", 10);
    \u0275\u0275text(13, "\u0412\u0440\u0435\u043C\u0435\u043D\u043D\u044B\u0439 \u043F\u0430\u0440\u043E\u043B\u044C");
    \u0275\u0275elementEnd();
    \u0275\u0275elementStart(14, "code", 11);
    \u0275\u0275text(15);
    \u0275\u0275elementEnd()()();
    \u0275\u0275elementStart(16, "p", 12);
    \u0275\u0275text(17, "\u0421\u043E\u0445\u0440\u0430\u043D\u0438\u0442\u0435 \u043F\u0430\u0440\u043E\u043B\u044C \u2014 \u043F\u043E\u0441\u043B\u0435 \u043F\u0435\u0440\u0432\u043E\u0433\u043E \u0432\u0445\u043E\u0434\u0430 \u043E\u043D \u0431\u0443\u0434\u0435\u0442 \u0441\u0431\u0440\u043E\u0448\u0435\u043D.");
    \u0275\u0275elementEnd()();
  }
  if (rf & 2) {
    const ctx_r0 = \u0275\u0275nextContext();
    \u0275\u0275advance(10);
    \u0275\u0275textInterpolate(ctx_r0.createdUser().login);
    \u0275\u0275advance(5);
    \u0275\u0275textInterpolate(ctx_r0.createdUser().initialPassword);
  }
}
function UserDialogComponent_Conditional_4_Conditional_6_Template(rf, ctx) {
  if (rf & 1) {
    \u0275\u0275elementStart(0, "mat-error");
    \u0275\u0275text(1, "\u0424\u0430\u043C\u0438\u043B\u0438\u044F \u043E\u0431\u044F\u0437\u0430\u0442\u0435\u043B\u044C\u043D\u0430");
    \u0275\u0275elementEnd();
  }
}
function UserDialogComponent_Conditional_4_Conditional_7_Template(rf, ctx) {
  if (rf & 1) {
    \u0275\u0275elementStart(0, "mat-error");
    \u0275\u0275text(1, "\u041C\u0438\u043D\u0438\u043C\u0443\u043C 2 \u0441\u0438\u043C\u0432\u043E\u043B\u0430");
    \u0275\u0275elementEnd();
  }
}
function UserDialogComponent_Conditional_4_Conditional_12_Template(rf, ctx) {
  if (rf & 1) {
    \u0275\u0275elementStart(0, "mat-error");
    \u0275\u0275text(1, "\u0418\u043C\u044F \u043E\u0431\u044F\u0437\u0430\u0442\u0435\u043B\u044C\u043D\u043E");
    \u0275\u0275elementEnd();
  }
}
function UserDialogComponent_Conditional_4_Conditional_13_Template(rf, ctx) {
  if (rf & 1) {
    \u0275\u0275elementStart(0, "mat-error");
    \u0275\u0275text(1, "\u041C\u0438\u043D\u0438\u043C\u0443\u043C 2 \u0441\u0438\u043C\u0432\u043E\u043B\u0430");
    \u0275\u0275elementEnd();
  }
}
function UserDialogComponent_Conditional_4_Conditional_31_Template(rf, ctx) {
  if (rf & 1) {
    \u0275\u0275elementStart(0, "mat-error");
    \u0275\u0275text(1, "\u0412\u044B\u0431\u0435\u0440\u0438\u0442\u0435 \u0440\u043E\u043B\u044C");
    \u0275\u0275elementEnd();
  }
}
function UserDialogComponent_Conditional_4_Conditional_32_Template(rf, ctx) {
  if (rf & 1) {
    \u0275\u0275elementStart(0, "p", 22);
    \u0275\u0275text(1, "\u041B\u043E\u0433\u0438\u043D \u0431\u0443\u0434\u0435\u0442 \u0441\u0433\u0435\u043D\u0435\u0440\u0438\u0440\u043E\u0432\u0430\u043D \u0430\u0432\u0442\u043E\u043C\u0430\u0442\u0438\u0447\u0435\u0441\u043A\u0438");
    \u0275\u0275elementEnd();
  }
}
function UserDialogComponent_Conditional_4_Conditional_33_Template(rf, ctx) {
  if (rf & 1) {
    \u0275\u0275elementStart(0, "mat-form-field", 23)(1, "mat-label");
    \u0275\u0275text(2, "\u041B\u043E\u0433\u0438\u043D");
    \u0275\u0275elementEnd();
    \u0275\u0275element(3, "input", 27);
    \u0275\u0275elementEnd();
  }
  if (rf & 2) {
    const ctx_r0 = \u0275\u0275nextContext(2);
    \u0275\u0275advance(3);
    \u0275\u0275property("value", ctx_r0.data.user.login);
  }
}
function UserDialogComponent_Conditional_4_Conditional_34_For_7_Template(rf, ctx) {
  if (rf & 1) {
    \u0275\u0275elementStart(0, "mat-option", 29);
    \u0275\u0275text(1);
    \u0275\u0275elementEnd();
  }
  if (rf & 2) {
    const g_r2 = ctx.$implicit;
    \u0275\u0275property("value", g_r2.id);
    \u0275\u0275advance();
    \u0275\u0275textInterpolate(g_r2.name);
  }
}
function UserDialogComponent_Conditional_4_Conditional_34_Template(rf, ctx) {
  if (rf & 1) {
    \u0275\u0275elementStart(0, "mat-form-field", 23)(1, "mat-label");
    \u0275\u0275text(2, "\u0423\u0447\u0435\u0431\u043D\u0430\u044F \u0433\u0440\u0443\u043F\u043F\u0430");
    \u0275\u0275elementEnd();
    \u0275\u0275elementStart(3, "mat-select", 28)(4, "mat-option", 29);
    \u0275\u0275text(5, "\u2014 \u043D\u0435 \u043D\u0430\u0437\u043D\u0430\u0447\u0435\u043D\u0430 \u2014");
    \u0275\u0275elementEnd();
    \u0275\u0275repeaterCreate(6, UserDialogComponent_Conditional_4_Conditional_34_For_7_Template, 2, 2, "mat-option", 29, _forTrack0);
    \u0275\u0275elementEnd()();
  }
  if (rf & 2) {
    const ctx_r0 = \u0275\u0275nextContext(2);
    \u0275\u0275advance(4);
    \u0275\u0275property("value", null);
    \u0275\u0275advance(2);
    \u0275\u0275repeater(ctx_r0.data.groups);
  }
}
function UserDialogComponent_Conditional_4_Conditional_35_Conditional_4_Template(rf, ctx) {
  if (rf & 1) {
    \u0275\u0275elementStart(0, "mat-error");
    \u0275\u0275text(1);
    \u0275\u0275elementEnd();
  }
  if (rf & 2) {
    let tmp_3_0;
    const ctx_r0 = \u0275\u0275nextContext(3);
    \u0275\u0275advance();
    \u0275\u0275textInterpolate((tmp_3_0 = ctx_r0.form.get("employeeNumber")) == null ? null : tmp_3_0.errors == null ? null : tmp_3_0.errors["conflict"]);
  }
}
function UserDialogComponent_Conditional_4_Conditional_35_Conditional_5_Template(rf, ctx) {
  if (rf & 1) {
    \u0275\u0275elementStart(0, "mat-hint");
    \u0275\u0275text(1, "\u041D\u0435\u043E\u0431\u044F\u0437\u0430\u0442\u0435\u043B\u044C\u043D\u043E \u2014 \u043C\u043E\u0436\u043D\u043E \u0434\u043E\u0431\u0430\u0432\u0438\u0442\u044C \u043F\u043E\u0437\u0436\u0435");
    \u0275\u0275elementEnd();
  }
}
function UserDialogComponent_Conditional_4_Conditional_35_Template(rf, ctx) {
  if (rf & 1) {
    \u0275\u0275elementStart(0, "mat-form-field", 23)(1, "mat-label");
    \u0275\u0275text(2, "\u0422\u0430\u0431\u0435\u043B\u044C\u043D\u044B\u0439 \u043D\u043E\u043C\u0435\u0440");
    \u0275\u0275elementEnd();
    \u0275\u0275element(3, "input", 30);
    \u0275\u0275template(4, UserDialogComponent_Conditional_4_Conditional_35_Conditional_4_Template, 2, 1, "mat-error")(5, UserDialogComponent_Conditional_4_Conditional_35_Conditional_5_Template, 2, 0, "mat-hint");
    \u0275\u0275elementEnd();
  }
  if (rf & 2) {
    let tmp_2_0;
    const ctx_r0 = \u0275\u0275nextContext(2);
    \u0275\u0275advance(4);
    \u0275\u0275conditional(((tmp_2_0 = ctx_r0.form.get("employeeNumber")) == null ? null : tmp_2_0.hasError("conflict")) ? 4 : 5);
  }
}
function UserDialogComponent_Conditional_4_Conditional_40_Template(rf, ctx) {
  if (rf & 1) {
    \u0275\u0275elementStart(0, "mat-error");
    \u0275\u0275text(1, "Telegram ID \u043E\u0431\u044F\u0437\u0430\u0442\u0435\u043B\u0435\u043D \u0434\u043B\u044F \u0441\u0442\u0443\u0434\u0435\u043D\u0442\u0430");
    \u0275\u0275elementEnd();
  }
}
function UserDialogComponent_Conditional_4_Conditional_41_Template(rf, ctx) {
  if (rf & 1) {
    \u0275\u0275elementStart(0, "mat-error");
    \u0275\u0275text(1);
    \u0275\u0275elementEnd();
  }
  if (rf & 2) {
    let tmp_2_0;
    const ctx_r0 = \u0275\u0275nextContext(2);
    \u0275\u0275advance();
    \u0275\u0275textInterpolate((tmp_2_0 = ctx_r0.form.get("telegramId")) == null ? null : tmp_2_0.errors == null ? null : tmp_2_0.errors["conflict"]);
  }
}
function UserDialogComponent_Conditional_4_Conditional_42_Template(rf, ctx) {
  if (rf & 1) {
    \u0275\u0275elementStart(0, "mat-hint");
    \u0275\u0275text(1, "\u0411\u0435\u0437 Telegram ID \u0441\u0442\u0443\u0434\u0435\u043D\u0442 \u043D\u0435 \u0441\u043C\u043E\u0436\u0435\u0442 \u043F\u043E\u043B\u0443\u0447\u0430\u0442\u044C \u0443\u0432\u0435\u0434\u043E\u043C\u043B\u0435\u043D\u0438\u044F \u0438 \u043F\u043E\u0434\u0442\u0432\u0435\u0440\u0436\u0434\u0430\u0442\u044C \u0447\u0435\u0440\u0435\u0437 \u0431\u043E\u0442\u0430");
    \u0275\u0275elementEnd();
  }
}
function UserDialogComponent_Conditional_4_Conditional_43_Template(rf, ctx) {
  if (rf & 1) {
    \u0275\u0275elementStart(0, "mat-hint");
    \u0275\u0275text(1, "\u041D\u0435\u043E\u0431\u044F\u0437\u0430\u0442\u0435\u043B\u044C\u043D\u043E \u2014 \u043C\u043E\u0436\u043D\u043E \u0434\u043E\u0431\u0430\u0432\u0438\u0442\u044C \u043F\u043E\u0437\u0436\u0435");
    \u0275\u0275elementEnd();
  }
}
function UserDialogComponent_Conditional_4_Conditional_44_Template(rf, ctx) {
  if (rf & 1) {
    \u0275\u0275elementStart(0, "mat-checkbox", 25);
    \u0275\u0275text(1, "\u042F\u0432\u043B\u044F\u0435\u0442\u0441\u044F \u0441\u0442\u0430\u0440\u043E\u0441\u0442\u043E\u0439 \u0433\u0440\u0443\u043F\u043F\u044B");
    \u0275\u0275elementEnd();
  }
}
function UserDialogComponent_Conditional_4_Conditional_45_Template(rf, ctx) {
  if (rf & 1) {
    \u0275\u0275elementStart(0, "div", 26);
    \u0275\u0275element(1, "i", 31);
    \u0275\u0275text(2);
    \u0275\u0275elementEnd();
  }
  if (rf & 2) {
    const ctx_r0 = \u0275\u0275nextContext(2);
    \u0275\u0275advance(2);
    \u0275\u0275textInterpolate1(" ", ctx_r0.submitError(), " ");
  }
}
function UserDialogComponent_Conditional_4_Conditional_46_Template(rf, ctx) {
  if (rf & 1) {
    \u0275\u0275elementStart(0, "div", 26);
    \u0275\u0275element(1, "i", 31);
    \u0275\u0275text(2, " \u041D\u0435 \u0443\u0434\u0430\u043B\u043E\u0441\u044C \u0441\u043E\u0445\u0440\u0430\u043D\u0438\u0442\u044C. \u041F\u0440\u043E\u0432\u0435\u0440\u044C\u0442\u0435 \u0434\u0430\u043D\u043D\u044B\u0435 \u0438 \u043F\u043E\u043F\u0440\u043E\u0431\u0443\u0439\u0442\u0435 \u0441\u043D\u043E\u0432\u0430. ");
    \u0275\u0275elementEnd();
  }
}
function UserDialogComponent_Conditional_4_Template(rf, ctx) {
  if (rf & 1) {
    \u0275\u0275elementStart(0, "form", 2)(1, "div", 13)(2, "mat-form-field", 14)(3, "mat-label");
    \u0275\u0275text(4, "\u0424\u0430\u043C\u0438\u043B\u0438\u044F");
    \u0275\u0275elementEnd();
    \u0275\u0275element(5, "input", 15);
    \u0275\u0275template(6, UserDialogComponent_Conditional_4_Conditional_6_Template, 2, 0, "mat-error")(7, UserDialogComponent_Conditional_4_Conditional_7_Template, 2, 0, "mat-error");
    \u0275\u0275elementEnd();
    \u0275\u0275elementStart(8, "mat-form-field", 14)(9, "mat-label");
    \u0275\u0275text(10, "\u0418\u043C\u044F");
    \u0275\u0275elementEnd();
    \u0275\u0275element(11, "input", 16);
    \u0275\u0275template(12, UserDialogComponent_Conditional_4_Conditional_12_Template, 2, 0, "mat-error")(13, UserDialogComponent_Conditional_4_Conditional_13_Template, 2, 0, "mat-error");
    \u0275\u0275elementEnd()();
    \u0275\u0275elementStart(14, "div", 13)(15, "mat-form-field", 14)(16, "mat-label");
    \u0275\u0275text(17, "\u041E\u0442\u0447\u0435\u0441\u0442\u0432\u043E");
    \u0275\u0275elementEnd();
    \u0275\u0275element(18, "input", 17);
    \u0275\u0275elementStart(19, "mat-hint");
    \u0275\u0275text(20, "\u041D\u0435\u043E\u0431\u044F\u0437\u0430\u0442\u0435\u043B\u044C\u043D\u043E");
    \u0275\u0275elementEnd()();
    \u0275\u0275elementStart(21, "mat-form-field", 14)(22, "mat-label");
    \u0275\u0275text(23, "\u0420\u043E\u043B\u044C");
    \u0275\u0275elementEnd();
    \u0275\u0275elementStart(24, "mat-select", 18)(25, "mat-option", 19);
    \u0275\u0275text(26, "\u0410\u0434\u043C\u0438\u043D\u0438\u0441\u0442\u0440\u0430\u0442\u043E\u0440");
    \u0275\u0275elementEnd();
    \u0275\u0275elementStart(27, "mat-option", 20);
    \u0275\u0275text(28, "\u041F\u0440\u0435\u043F\u043E\u0434\u0430\u0432\u0430\u0442\u0435\u043B\u044C");
    \u0275\u0275elementEnd();
    \u0275\u0275elementStart(29, "mat-option", 21);
    \u0275\u0275text(30, "\u0421\u0442\u0443\u0434\u0435\u043D\u0442");
    \u0275\u0275elementEnd()();
    \u0275\u0275template(31, UserDialogComponent_Conditional_4_Conditional_31_Template, 2, 0, "mat-error");
    \u0275\u0275elementEnd()();
    \u0275\u0275template(32, UserDialogComponent_Conditional_4_Conditional_32_Template, 2, 0, "p", 22)(33, UserDialogComponent_Conditional_4_Conditional_33_Template, 4, 1, "mat-form-field", 23)(34, UserDialogComponent_Conditional_4_Conditional_34_Template, 8, 1, "mat-form-field", 23)(35, UserDialogComponent_Conditional_4_Conditional_35_Template, 6, 1, "mat-form-field", 23);
    \u0275\u0275elementStart(36, "mat-form-field", 23)(37, "mat-label");
    \u0275\u0275text(38, "Telegram ID");
    \u0275\u0275elementEnd();
    \u0275\u0275element(39, "input", 24);
    \u0275\u0275template(40, UserDialogComponent_Conditional_4_Conditional_40_Template, 2, 0, "mat-error")(41, UserDialogComponent_Conditional_4_Conditional_41_Template, 2, 1, "mat-error")(42, UserDialogComponent_Conditional_4_Conditional_42_Template, 2, 0, "mat-hint")(43, UserDialogComponent_Conditional_4_Conditional_43_Template, 2, 0, "mat-hint");
    \u0275\u0275elementEnd();
    \u0275\u0275template(44, UserDialogComponent_Conditional_4_Conditional_44_Template, 2, 0, "mat-checkbox", 25)(45, UserDialogComponent_Conditional_4_Conditional_45_Template, 3, 1, "div", 26)(46, UserDialogComponent_Conditional_4_Conditional_46_Template, 3, 0, "div", 26);
    \u0275\u0275elementEnd();
  }
  if (rf & 2) {
    let tmp_2_0;
    let tmp_3_0;
    let tmp_4_0;
    let tmp_5_0;
    let tmp_6_0;
    let tmp_8_0;
    let tmp_9_0;
    let tmp_10_0;
    let tmp_11_0;
    let tmp_12_0;
    const ctx_r0 = \u0275\u0275nextContext();
    \u0275\u0275property("formGroup", ctx_r0.form);
    \u0275\u0275advance(6);
    \u0275\u0275conditional(((tmp_2_0 = ctx_r0.form.get("lastName")) == null ? null : tmp_2_0.hasError("required")) ? 6 : -1);
    \u0275\u0275advance();
    \u0275\u0275conditional(((tmp_3_0 = ctx_r0.form.get("lastName")) == null ? null : tmp_3_0.hasError("minlength")) ? 7 : -1);
    \u0275\u0275advance(5);
    \u0275\u0275conditional(((tmp_4_0 = ctx_r0.form.get("firstName")) == null ? null : tmp_4_0.hasError("required")) ? 12 : -1);
    \u0275\u0275advance();
    \u0275\u0275conditional(((tmp_5_0 = ctx_r0.form.get("firstName")) == null ? null : tmp_5_0.hasError("minlength")) ? 13 : -1);
    \u0275\u0275advance(18);
    \u0275\u0275conditional(((tmp_6_0 = ctx_r0.form.get("role")) == null ? null : tmp_6_0.hasError("required")) ? 31 : -1);
    \u0275\u0275advance();
    \u0275\u0275conditional(ctx_r0.data.mode === "create" ? 32 : 33);
    \u0275\u0275advance(2);
    \u0275\u0275conditional(((tmp_8_0 = ctx_r0.form.get("role")) == null ? null : tmp_8_0.value) === "STUDENT" ? 34 : -1);
    \u0275\u0275advance();
    \u0275\u0275conditional(((tmp_9_0 = ctx_r0.form.get("role")) == null ? null : tmp_9_0.value) === "TEACHER" ? 35 : -1);
    \u0275\u0275advance(4);
    \u0275\u0275property("required", ((tmp_10_0 = ctx_r0.form.get("role")) == null ? null : tmp_10_0.value) === "STUDENT");
    \u0275\u0275advance();
    \u0275\u0275conditional(((tmp_11_0 = ctx_r0.form.get("telegramId")) == null ? null : tmp_11_0.hasError("required")) ? 40 : ((tmp_11_0 = ctx_r0.form.get("telegramId")) == null ? null : tmp_11_0.hasError("conflict")) ? 41 : ((tmp_11_0 = ctx_r0.form.get("role")) == null ? null : tmp_11_0.value) === "STUDENT" ? 42 : 43);
    \u0275\u0275advance(4);
    \u0275\u0275conditional(((tmp_12_0 = ctx_r0.form.get("role")) == null ? null : tmp_12_0.value) === "STUDENT" ? 44 : -1);
    \u0275\u0275advance();
    \u0275\u0275conditional(ctx_r0.submitError() ? 45 : ctx_r0.apiError() ? 46 : -1);
  }
}
function UserDialogComponent_Conditional_6_Template(rf, ctx) {
  if (rf & 1) {
    \u0275\u0275elementStart(0, "button", 4);
    \u0275\u0275text(1, "\u0413\u043E\u0442\u043E\u0432\u043E");
    \u0275\u0275elementEnd();
  }
}
function UserDialogComponent_Conditional_7_Conditional_3_Template(rf, ctx) {
  if (rf & 1) {
    \u0275\u0275text(0, " \u0421\u043E\u0437\u0434\u0430\u0442\u044C \u043F\u043E\u043B\u044C\u0437\u043E\u0432\u0430\u0442\u0435\u043B\u044F ");
  }
}
function UserDialogComponent_Conditional_7_Conditional_4_Template(rf, ctx) {
  if (rf & 1) {
    \u0275\u0275text(0, " \u0421\u043E\u0445\u0440\u0430\u043D\u0438\u0442\u044C \u0438\u0437\u043C\u0435\u043D\u0435\u043D\u0438\u044F ");
  }
}
function UserDialogComponent_Conditional_7_Template(rf, ctx) {
  if (rf & 1) {
    const _r3 = \u0275\u0275getCurrentView();
    \u0275\u0275elementStart(0, "button", 32);
    \u0275\u0275text(1, "\u041E\u0442\u043C\u0435\u043D\u0430");
    \u0275\u0275elementEnd();
    \u0275\u0275elementStart(2, "button", 33);
    \u0275\u0275listener("click", function UserDialogComponent_Conditional_7_Template_button_click_2_listener() {
      \u0275\u0275restoreView(_r3);
      const ctx_r0 = \u0275\u0275nextContext();
      return \u0275\u0275resetView(ctx_r0.save());
    });
    \u0275\u0275template(3, UserDialogComponent_Conditional_7_Conditional_3_Template, 1, 0)(4, UserDialogComponent_Conditional_7_Conditional_4_Template, 1, 0);
    \u0275\u0275elementEnd();
  }
  if (rf & 2) {
    const ctx_r0 = \u0275\u0275nextContext();
    \u0275\u0275advance(2);
    \u0275\u0275property("disabled", ctx_r0.saving());
    \u0275\u0275advance();
    \u0275\u0275conditional(ctx_r0.data.mode === "create" ? 3 : 4);
  }
}
var UserDialogComponent = class _UserDialogComponent {
  adminApi = inject(AdminApiService);
  dialogRef = inject(MatDialogRef);
  data = inject(MAT_DIALOG_DATA);
  saving = signal(false);
  apiError = signal(false);
  /**
   * BUG-006-2 / AC-3: human-readable сообщение для текущей попытки сохранения.
   * null — ошибки нет; иначе покажет в template под формой (перекрывает
   * generic apiError-баннер, если тот выставлен).
   */
  submitError = signal(null);
  /** После успешного создания — здесь хранится ответ с initialPassword */
  createdUser = signal(null);
  /**
   * BUG-006-2: map backend field-names → localized сообщение. Ключи совпадают
   * с payload {@code ProblemDetail.field} из {@code GlobalExceptionHandler}.
   */
  static FIELD_MESSAGES = {
    login: "\u041B\u043E\u0433\u0438\u043D \u0443\u0436\u0435 \u0438\u0441\u043F\u043E\u043B\u044C\u0437\u0443\u0435\u0442\u0441\u044F. \u0412\u044B\u0431\u0435\u0440\u0438\u0442\u0435 \u0434\u0440\u0443\u0433\u043E\u0439",
    email: "Email \u0443\u0436\u0435 \u0437\u0430\u0440\u0435\u0433\u0438\u0441\u0442\u0440\u0438\u0440\u043E\u0432\u0430\u043D",
    telegramId: "Telegram ID \u0443\u0436\u0435 \u043F\u0440\u0438\u0432\u044F\u0437\u0430\u043D \u043A \u0434\u0440\u0443\u0433\u043E\u0439 \u0443\u0447\u0451\u0442\u043D\u043E\u0439 \u0437\u0430\u043F\u0438\u0441\u0438",
    employeeNumber: "\u0422\u0430\u0431\u0435\u043B\u044C\u043D\u044B\u0439 \u043D\u043E\u043C\u0435\u0440 \u0443\u0436\u0435 \u0438\u0441\u043F\u043E\u043B\u044C\u0437\u0443\u0435\u0442\u0441\u044F",
    name: "\u041D\u0430\u0437\u0432\u0430\u043D\u0438\u0435 \u0443\u0436\u0435 \u0438\u0441\u043F\u043E\u043B\u044C\u0437\u0443\u0435\u0442\u0441\u044F"
  };
  form = new FormGroup({
    lastName: new FormControl("", { nonNullable: true, validators: [Validators.required, Validators.minLength(2)] }),
    firstName: new FormControl("", { nonNullable: true, validators: [Validators.required, Validators.minLength(2)] }),
    middleName: new FormControl("", { nonNullable: true }),
    role: new FormControl("", { nonNullable: true, validators: [Validators.required] }),
    groupId: new FormControl(null),
    employeeNumber: new FormControl("", { nonNullable: true }),
    // BUG-006-3: optional by default; `Validators.required` is toggled by
    // `role.valueChanges` below — required for STUDENT, optional otherwise.
    telegramId: new FormControl(null),
    isHeadman: new FormControl(false, { nonNullable: true })
  });
  constructor() {
    if (this.data.mode === "edit" && this.data.user) {
      const u = this.data.user;
      this.form.patchValue({
        lastName: u.lastName,
        firstName: u.firstName,
        middleName: u.middleName ?? "",
        role: u.role,
        groupId: u.groupId,
        isHeadman: u.headman,
        employeeNumber: u.employeeNumber ?? "",
        telegramId: u.telegramId ?? null
      });
      this.form.get("role").disable();
    } else if (this.data.mode === "create" && this.data.presetRole) {
      const allowed = ["STUDENT", "TEACHER", "ADMIN"];
      const preset = this.data.presetRole.toUpperCase();
      if (allowed.includes(preset)) {
        this.form.patchValue({ role: preset });
      }
    }
    const roleCtrl = this.form.get("role");
    roleCtrl.valueChanges.pipe(takeUntilDestroyed(inject(DestroyRef))).subscribe((role) => this.applyTelegramRequiredForRole(role));
    this.applyTelegramRequiredForRole(roleCtrl.value);
  }
  /**
   * Toggles {@link Validators.required} on the telegramId control based on
   * the active role. Called on init and on every role change.
   */
  applyTelegramRequiredForRole(role) {
    const tg = this.form.get("telegramId");
    if (role === "STUDENT") {
      tg.addValidators(Validators.required);
    } else {
      tg.removeValidators(Validators.required);
    }
    tg.updateValueAndValidity({ emitEvent: false });
  }
  save() {
    this.form.markAllAsTouched();
    if (this.form.invalid)
      return;
    this.saving.set(true);
    this.apiError.set(false);
    if (this.data.mode === "create") {
      const raw = this.form.getRawValue();
      const req = {
        lastName: raw.lastName.trim(),
        firstName: raw.firstName.trim(),
        role: raw.role
      };
      if (raw.middleName.trim())
        req.middleName = raw.middleName.trim();
      if (raw.role === "STUDENT" && raw.groupId != null)
        req.groupId = raw.groupId;
      if (raw.role === "TEACHER" && raw.employeeNumber)
        req.employeeNumber = raw.employeeNumber;
      if (raw.telegramId != null)
        req.telegramId = raw.telegramId;
      this.adminApi.createUser(req).subscribe({
        next: (result) => {
          this.saving.set(false);
          this.createdUser.set(result);
          this.dialogRef.close(result);
        },
        error: (err) => {
          this.handleSaveError(err);
          this.saving.set(false);
        }
      });
    } else {
      const raw = this.form.getRawValue();
      const req = {};
      const u = this.data.user;
      if (raw.lastName.trim() !== u.lastName)
        req.lastName = raw.lastName.trim();
      if (raw.firstName.trim() !== u.firstName)
        req.firstName = raw.firstName.trim();
      if (raw.middleName.trim() !== (u.middleName ?? ""))
        req.middleName = raw.middleName.trim();
      if (raw.groupId !== u.groupId)
        req.groupId = raw.groupId ?? void 0;
      if (raw.isHeadman !== u.headman)
        req.isHeadman = raw.isHeadman;
      if (raw.employeeNumber !== (u.employeeNumber ?? ""))
        req.employeeNumber = raw.employeeNumber;
      if (raw.telegramId !== (u.telegramId ?? null)) {
        req.telegramId = raw.telegramId ?? void 0;
      }
      this.adminApi.patchUser(u.id, req).subscribe({
        next: () => {
          this.dialogRef.close(true);
        },
        error: (err) => {
          this.handleSaveError(err);
          this.saving.set(false);
        }
      });
    }
  }
  /**
   * BUG-006-2 / AC-3: translates HTTP error into field-level + form-level UI
   * state. 409 с payload {@code field} → маркирует конкретный control и
   * сохраняет локализованное сообщение в {@link submitError}. Остальные
   * ошибки — generic fallback.
   */
  handleSaveError(err) {
    const status = err.status;
    const field = err.error && typeof err.error === "object" && typeof err.error.field === "string" ? err.error.field : null;
    if (status === 409 && field) {
      const msg = _UserDialogComponent.FIELD_MESSAGES[field] ?? `\u041F\u043E\u043B\u0435 \xAB${field}\xBB \u0443\u0436\u0435 \u0438\u0441\u043F\u043E\u043B\u044C\u0437\u0443\u0435\u0442\u0441\u044F`;
      const control = this.form.get(field);
      if (control) {
        control.setErrors(__spreadProps(__spreadValues({}, control.errors ?? {}), { conflict: msg }));
        control.markAsTouched();
        const sub = control.valueChanges.subscribe(() => {
          const errors = control.errors;
          if (errors && "conflict" in errors) {
            const _a = errors, { conflict: _conflict } = _a, rest = __objRest(_a, ["conflict"]);
            control.setErrors(Object.keys(rest).length > 0 ? rest : null);
          }
          sub.unsubscribe();
        });
      }
      this.submitError.set(msg);
      this.apiError.set(false);
      return;
    }
    if (status >= 400 && status < 500) {
      this.submitError.set("\u041D\u0435 \u0443\u0434\u0430\u043B\u043E\u0441\u044C \u0441\u043E\u0445\u0440\u0430\u043D\u0438\u0442\u044C. \u041F\u0440\u043E\u0432\u0435\u0440\u044C\u0442\u0435 \u0432\u0432\u0435\u0434\u0451\u043D\u043D\u044B\u0435 \u0434\u0430\u043D\u043D\u044B\u0435");
    } else {
      this.submitError.set("\u041E\u0448\u0438\u0431\u043A\u0430 \u0441\u0435\u0440\u0432\u0435\u0440\u0430. \u041F\u043E\u043F\u0440\u043E\u0431\u0443\u0439\u0442\u0435 \u0435\u0449\u0451 \u0440\u0430\u0437");
    }
    this.apiError.set(true);
  }
  static \u0275fac = function UserDialogComponent_Factory(__ngFactoryType__) {
    return new (__ngFactoryType__ || _UserDialogComponent)();
  };
  static \u0275cmp = /* @__PURE__ */ \u0275\u0275defineComponent({ type: _UserDialogComponent, selectors: [["app-user-dialog"]], decls: 8, vars: 4, consts: [["mat-dialog-title", ""], [1, "ud-created"], [1, "ud-form", 3, "formGroup"], ["align", "end"], ["mat-flat-button", "", "color", "primary", "mat-dialog-close", ""], [1, "ud-created__icon"], ["aria-hidden", "true", 1, "ph-duotone", "ph-check-circle"], [1, "ud-created__title"], [1, "ud-created__creds"], [1, "ud-created__row"], [1, "ud-created__label"], [1, "ud-created__value"], [1, "ud-created__hint"], [1, "ud-row"], [1, "ud-field"], ["matInput", "", "formControlName", "lastName", "placeholder", "\u0418\u0432\u0430\u043D\u043E\u0432"], ["matInput", "", "formControlName", "firstName", "placeholder", "\u0418\u0432\u0430\u043D"], ["matInput", "", "formControlName", "middleName", "placeholder", "\u0418\u0432\u0430\u043D\u043E\u0432\u0438\u0447"], ["formControlName", "role"], ["value", "ADMIN"], ["value", "TEACHER"], ["value", "STUDENT"], [1, "ud-hint"], [1, "ud-field-full"], ["matInput", "", "type", "number", "formControlName", "telegramId", "placeholder", "123456789", 3, "required"], ["formControlName", "isHeadman"], ["role", "alert", 1, "ud-error"], ["matInput", "", "readonly", "", 3, "value"], ["formControlName", "groupId"], [3, "value"], ["matInput", "", "formControlName", "employeeNumber", "placeholder", "\u0422-00001"], ["aria-hidden", "true", 1, "ph", "ph-warning-circle"], ["mat-button", "", "mat-dialog-close", ""], ["mat-flat-button", "", "color", "primary", 3, "click", "disabled"]], template: function UserDialogComponent_Template(rf, ctx) {
    if (rf & 1) {
      \u0275\u0275template(0, UserDialogComponent_Conditional_0_Template, 2, 0, "h2", 0)(1, UserDialogComponent_Conditional_1_Template, 2, 0, "h2", 0);
      \u0275\u0275elementStart(2, "mat-dialog-content");
      \u0275\u0275template(3, UserDialogComponent_Conditional_3_Template, 18, 2, "div", 1)(4, UserDialogComponent_Conditional_4_Template, 47, 13, "form", 2);
      \u0275\u0275elementEnd();
      \u0275\u0275elementStart(5, "mat-dialog-actions", 3);
      \u0275\u0275template(6, UserDialogComponent_Conditional_6_Template, 2, 0, "button", 4)(7, UserDialogComponent_Conditional_7_Template, 5, 2);
      \u0275\u0275elementEnd();
    }
    if (rf & 2) {
      \u0275\u0275conditional(ctx.data.mode === "create" ? 0 : 1);
      \u0275\u0275advance(3);
      \u0275\u0275conditional(ctx.createdUser() ? 3 : -1);
      \u0275\u0275advance();
      \u0275\u0275conditional(!ctx.createdUser() ? 4 : -1);
      \u0275\u0275advance(2);
      \u0275\u0275conditional(ctx.createdUser() ? 6 : 7);
    }
  }, dependencies: [
    CommonModule,
    ReactiveFormsModule,
    \u0275NgNoValidate,
    DefaultValueAccessor,
    NumberValueAccessor,
    NgControlStatus,
    NgControlStatusGroup,
    RequiredValidator,
    FormGroupDirective,
    FormControlName,
    MatDialogModule,
    MatDialogClose,
    MatDialogTitle,
    MatDialogActions,
    MatDialogContent,
    MatFormFieldModule,
    MatFormField,
    MatLabel,
    MatHint,
    MatError,
    MatInputModule,
    MatInput,
    MatSelectModule,
    MatSelect,
    MatOption,
    MatButtonModule,
    MatButton,
    MatCheckboxModule,
    MatCheckbox
  ], styles: ["\n\n[_nghost-%COMP%] {\n  --mdc-dialog-container-color: var(--bg-elevated);\n  --mat-dialog-content-padding: 0 24px;\n}\n.ud-form[_ngcontent-%COMP%] {\n  display: flex;\n  flex-direction: column;\n  gap: var(--space-4);\n  padding: var(--space-2) 0;\n}\n.ud-row[_ngcontent-%COMP%] {\n  display: grid;\n  grid-template-columns: 1fr 1fr;\n  gap: var(--space-4);\n}\n@media (max-width: 480px) {\n  .ud-row[_ngcontent-%COMP%] {\n    grid-template-columns: 1fr;\n  }\n}\n.ud-field[_ngcontent-%COMP%], \n.ud-field-full[_ngcontent-%COMP%] {\n  width: 100%;\n}\n.ud-form[_ngcontent-%COMP%]   mat-form-field[_ngcontent-%COMP%] {\n  --mdc-outlined-text-field-container-shape: var(--radius-md);\n  --mdc-outlined-text-field-outline-color: var(--border-default);\n  --mdc-outlined-text-field-hover-outline-color: var(--border-accent);\n  --mdc-outlined-text-field-focus-outline-color: var(--accent-primary);\n  --mdc-outlined-text-field-input-text-color: var(--text-primary);\n  --mdc-outlined-text-field-label-text-color: var(--text-secondary);\n  --mdc-outlined-text-field-focus-label-text-color: var(--accent-primary);\n  --mat-select-trigger-text-color: var(--text-primary);\n  --mat-option-label-text-color: var(--text-primary);\n  --mat-select-panel-background-color: var(--bg-elevated);\n}\n.ud-hint[_ngcontent-%COMP%] {\n  font-size: 0.8125rem;\n  color: var(--text-muted);\n  margin: calc(-1 * var(--space-2)) 0 0;\n  padding-left: 2px;\n}\n.ud-error[_ngcontent-%COMP%] {\n  display: flex;\n  align-items: center;\n  gap: var(--space-2);\n  padding: var(--space-3) var(--space-4);\n  background: color-mix(in srgb, var(--accent-danger) 12%, transparent);\n  border: 1px solid color-mix(in srgb, var(--accent-danger) 28%, transparent);\n  border-radius: var(--radius-md);\n  color: var(--accent-danger);\n  font-size: 0.875rem;\n}\n.ud-error[_ngcontent-%COMP%]   i[_ngcontent-%COMP%] {\n  font-size: 18px;\n}\n.ud-created[_ngcontent-%COMP%] {\n  display: flex;\n  flex-direction: column;\n  align-items: center;\n  gap: var(--space-4);\n  padding: var(--space-4) 0 var(--space-2);\n  text-align: center;\n}\n.ud-created__icon[_ngcontent-%COMP%] {\n  width: 56px;\n  height: 56px;\n  border-radius: var(--radius-full);\n  background: color-mix(in oklab, var(--accent-primary) 15%, transparent);\n  border: 1px solid var(--border-accent);\n  display: grid;\n  place-items: center;\n  color: var(--accent-primary);\n}\n.ud-created__icon[_ngcontent-%COMP%]   i[_ngcontent-%COMP%] {\n  font-size: 30px;\n}\n.ud-created__title[_ngcontent-%COMP%] {\n  font-family: var(--font-heading);\n  font-weight: 600;\n  font-size: 1.0625rem;\n  color: var(--text-primary);\n  margin: 0;\n}\n.ud-created__creds[_ngcontent-%COMP%] {\n  width: 100%;\n  background: var(--bg-surface);\n  border: 1px solid var(--border-default);\n  border-radius: var(--radius-md);\n  overflow: hidden;\n}\n.ud-created__row[_ngcontent-%COMP%] {\n  display: flex;\n  align-items: center;\n  justify-content: space-between;\n  padding: var(--space-3) var(--space-4);\n  gap: var(--space-4);\n}\n.ud-created__row[_ngcontent-%COMP%]    + .ud-created__row[_ngcontent-%COMP%] {\n  border-top: 1px solid var(--border-subtle);\n}\n.ud-created__label[_ngcontent-%COMP%] {\n  font-size: 0.8125rem;\n  color: var(--text-secondary);\n  white-space: nowrap;\n}\n.ud-created__value[_ngcontent-%COMP%] {\n  font-family: var(--font-mono);\n  font-size: 0.9375rem;\n  color: var(--accent-primary);\n  background: color-mix(in oklab, var(--accent-primary) 10%, transparent);\n  padding: 2px 8px;\n  border-radius: var(--radius-sm);\n  word-break: break-all;\n  text-align: right;\n}\n.ud-created__hint[_ngcontent-%COMP%] {\n  font-size: 0.8125rem;\n  color: var(--text-muted);\n  margin: 0;\n}\n/*# sourceMappingURL=user-dialog.component.css.map */"] });
};
(() => {
  (typeof ngDevMode === "undefined" || ngDevMode) && setClassMetadata(UserDialogComponent, [{
    type: Component,
    args: [{ selector: "app-user-dialog", standalone: true, imports: [
      CommonModule,
      ReactiveFormsModule,
      MatDialogModule,
      MatFormFieldModule,
      MatInputModule,
      MatSelectModule,
      MatButtonModule,
      MatCheckboxModule
    ], template: `@if (data.mode === 'create') {\r
  <h2 mat-dialog-title>\u041D\u043E\u0432\u044B\u0439 \u043F\u043E\u043B\u044C\u0437\u043E\u0432\u0430\u0442\u0435\u043B\u044C</h2>\r
} @else {\r
  <h2 mat-dialog-title>\u0420\u0435\u0434\u0430\u043A\u0442\u0438\u0440\u043E\u0432\u0430\u0442\u044C \u043F\u043E\u043B\u044C\u0437\u043E\u0432\u0430\u0442\u0435\u043B\u044F</h2>\r
}\r
\r
<mat-dialog-content>\r
  <!-- \u0420\u0435\u0437\u0443\u043B\u044C\u0442\u0430\u0442 \u043F\u043E\u0441\u043B\u0435 \u0441\u043E\u0437\u0434\u0430\u043D\u0438\u044F: \u043F\u043E\u043A\u0430\u0437\u044B\u0432\u0430\u0435\u043C \u043B\u043E\u0433\u0438\u043D \u0438 \u043F\u0430\u0440\u043E\u043B\u044C -->\r
  @if (createdUser()) {\r
    <div class="ud-created">\r
      <div class="ud-created__icon">\r
        <i class="ph-duotone ph-check-circle" aria-hidden="true"></i>\r
      </div>\r
      <p class="ud-created__title">\u041F\u043E\u043B\u044C\u0437\u043E\u0432\u0430\u0442\u0435\u043B\u044C \u0441\u043E\u0437\u0434\u0430\u043D</p>\r
      <div class="ud-created__creds">\r
        <div class="ud-created__row">\r
          <span class="ud-created__label">\u041B\u043E\u0433\u0438\u043D</span>\r
          <code class="ud-created__value">{{ createdUser()!.login }}</code>\r
        </div>\r
        <div class="ud-created__row">\r
          <span class="ud-created__label">\u0412\u0440\u0435\u043C\u0435\u043D\u043D\u044B\u0439 \u043F\u0430\u0440\u043E\u043B\u044C</span>\r
          <code class="ud-created__value">{{ createdUser()!.initialPassword }}</code>\r
        </div>\r
      </div>\r
      <p class="ud-created__hint">\u0421\u043E\u0445\u0440\u0430\u043D\u0438\u0442\u0435 \u043F\u0430\u0440\u043E\u043B\u044C \u2014 \u043F\u043E\u0441\u043B\u0435 \u043F\u0435\u0440\u0432\u043E\u0433\u043E \u0432\u0445\u043E\u0434\u0430 \u043E\u043D \u0431\u0443\u0434\u0435\u0442 \u0441\u0431\u0440\u043E\u0448\u0435\u043D.</p>\r
    </div>\r
  }\r
\r
  <!-- \u0424\u043E\u0440\u043C\u0430 \u0441\u043E\u0437\u0434\u0430\u043D\u0438\u044F/\u0440\u0435\u0434\u0430\u043A\u0442\u0438\u0440\u043E\u0432\u0430\u043D\u0438\u044F -->\r
  @if (!createdUser()) {\r
    <form [formGroup]="form" class="ud-form">\r
      <div class="ud-row">\r
        <mat-form-field class="ud-field">\r
          <mat-label>\u0424\u0430\u043C\u0438\u043B\u0438\u044F</mat-label>\r
          <input matInput formControlName="lastName" placeholder="\u0418\u0432\u0430\u043D\u043E\u0432" />\r
          @if (form.get('lastName')?.hasError('required')) {\r
            <mat-error>\u0424\u0430\u043C\u0438\u043B\u0438\u044F \u043E\u0431\u044F\u0437\u0430\u0442\u0435\u043B\u044C\u043D\u0430</mat-error>\r
          }\r
          @if (form.get('lastName')?.hasError('minlength')) {\r
            <mat-error>\u041C\u0438\u043D\u0438\u043C\u0443\u043C 2 \u0441\u0438\u043C\u0432\u043E\u043B\u0430</mat-error>\r
          }\r
        </mat-form-field>\r
\r
        <mat-form-field class="ud-field">\r
          <mat-label>\u0418\u043C\u044F</mat-label>\r
          <input matInput formControlName="firstName" placeholder="\u0418\u0432\u0430\u043D" />\r
          @if (form.get('firstName')?.hasError('required')) {\r
            <mat-error>\u0418\u043C\u044F \u043E\u0431\u044F\u0437\u0430\u0442\u0435\u043B\u044C\u043D\u043E</mat-error>\r
          }\r
          @if (form.get('firstName')?.hasError('minlength')) {\r
            <mat-error>\u041C\u0438\u043D\u0438\u043C\u0443\u043C 2 \u0441\u0438\u043C\u0432\u043E\u043B\u0430</mat-error>\r
          }\r
        </mat-form-field>\r
      </div>\r
\r
      <div class="ud-row">\r
        <mat-form-field class="ud-field">\r
          <mat-label>\u041E\u0442\u0447\u0435\u0441\u0442\u0432\u043E</mat-label>\r
          <input matInput formControlName="middleName" placeholder="\u0418\u0432\u0430\u043D\u043E\u0432\u0438\u0447" />\r
          <mat-hint>\u041D\u0435\u043E\u0431\u044F\u0437\u0430\u0442\u0435\u043B\u044C\u043D\u043E</mat-hint>\r
        </mat-form-field>\r
\r
        <mat-form-field class="ud-field">\r
          <mat-label>\u0420\u043E\u043B\u044C</mat-label>\r
          <mat-select formControlName="role">\r
            <mat-option value="ADMIN">\u0410\u0434\u043C\u0438\u043D\u0438\u0441\u0442\u0440\u0430\u0442\u043E\u0440</mat-option>\r
            <mat-option value="TEACHER">\u041F\u0440\u0435\u043F\u043E\u0434\u0430\u0432\u0430\u0442\u0435\u043B\u044C</mat-option>\r
            <mat-option value="STUDENT">\u0421\u0442\u0443\u0434\u0435\u043D\u0442</mat-option>\r
          </mat-select>\r
          @if (form.get('role')?.hasError('required')) {\r
            <mat-error>\u0412\u044B\u0431\u0435\u0440\u0438\u0442\u0435 \u0440\u043E\u043B\u044C</mat-error>\r
          }\r
        </mat-form-field>\r
      </div>\r
\r
      @if (data.mode === 'create') {\r
        <p class="ud-hint">\u041B\u043E\u0433\u0438\u043D \u0431\u0443\u0434\u0435\u0442 \u0441\u0433\u0435\u043D\u0435\u0440\u0438\u0440\u043E\u0432\u0430\u043D \u0430\u0432\u0442\u043E\u043C\u0430\u0442\u0438\u0447\u0435\u0441\u043A\u0438</p>\r
      } @else {\r
        <mat-form-field class="ud-field-full">\r
          <mat-label>\u041B\u043E\u0433\u0438\u043D</mat-label>\r
          <input matInput [value]="data.user!.login" readonly />\r
        </mat-form-field>\r
      }\r
\r
      <!-- \u0413\u0440\u0443\u043F\u043F\u0430 \u0442\u043E\u043B\u044C\u043A\u043E \u0434\u043B\u044F \u0441\u0442\u0443\u0434\u0435\u043D\u0442\u043E\u0432 -->\r
      @if (form.get('role')?.value === 'STUDENT') {\r
        <mat-form-field class="ud-field-full">\r
          <mat-label>\u0423\u0447\u0435\u0431\u043D\u0430\u044F \u0433\u0440\u0443\u043F\u043F\u0430</mat-label>\r
          <mat-select formControlName="groupId">\r
            <mat-option [value]="null">\u2014 \u043D\u0435 \u043D\u0430\u0437\u043D\u0430\u0447\u0435\u043D\u0430 \u2014</mat-option>\r
            @for (g of data.groups; track g.id) {\r
              <mat-option [value]="g.id">{{ g.name }}</mat-option>\r
            }\r
          </mat-select>\r
        </mat-form-field>\r
      }\r
\r
      <!-- \u041F\u043E\u043B\u044F \u0442\u043E\u043B\u044C\u043A\u043E \u0434\u043B\u044F \u043F\u0440\u0435\u043F\u043E\u0434\u0430\u0432\u0430\u0442\u0435\u043B\u044F -->\r
      @if (form.get('role')?.value === 'TEACHER') {\r
        <mat-form-field class="ud-field-full">\r
          <mat-label>\u0422\u0430\u0431\u0435\u043B\u044C\u043D\u044B\u0439 \u043D\u043E\u043C\u0435\u0440</mat-label>\r
          <input matInput formControlName="employeeNumber" placeholder="\u0422-00001" />\r
          @if (form.get('employeeNumber')?.hasError('conflict')) {\r
            <mat-error>{{ form.get('employeeNumber')?.errors?.['conflict'] }}</mat-error>\r
          } @else {\r
            <mat-hint>\u041D\u0435\u043E\u0431\u044F\u0437\u0430\u0442\u0435\u043B\u044C\u043D\u043E \u2014 \u043C\u043E\u0436\u043D\u043E \u0434\u043E\u0431\u0430\u0432\u0438\u0442\u044C \u043F\u043E\u0437\u0436\u0435</mat-hint>\r
          }\r
        </mat-form-field>\r
      }\r
\r
      <!-- Telegram ID: required \u0434\u043B\u044F student, \u043E\u043F\u0446\u0438\u043E\u043D\u0430\u043B\u044C\u043D\u043E \u0438\u043D\u0430\u0447\u0435 (BUG-006-3) -->\r
      <mat-form-field class="ud-field-full">\r
        <mat-label>Telegram ID</mat-label>\r
        <input\r
          matInput\r
          type="number"\r
          formControlName="telegramId"\r
          placeholder="123456789"\r
          [required]="form.get('role')?.value === 'STUDENT'"\r
        />\r
        @if (form.get('telegramId')?.hasError('required')) {\r
          <mat-error>Telegram ID \u043E\u0431\u044F\u0437\u0430\u0442\u0435\u043B\u0435\u043D \u0434\u043B\u044F \u0441\u0442\u0443\u0434\u0435\u043D\u0442\u0430</mat-error>\r
        } @else if (form.get('telegramId')?.hasError('conflict')) {\r
          <mat-error>{{ form.get('telegramId')?.errors?.['conflict'] }}</mat-error>\r
        } @else if (form.get('role')?.value === 'STUDENT') {\r
          <mat-hint>\u0411\u0435\u0437 Telegram ID \u0441\u0442\u0443\u0434\u0435\u043D\u0442 \u043D\u0435 \u0441\u043C\u043E\u0436\u0435\u0442 \u043F\u043E\u043B\u0443\u0447\u0430\u0442\u044C \u0443\u0432\u0435\u0434\u043E\u043C\u043B\u0435\u043D\u0438\u044F \u0438 \u043F\u043E\u0434\u0442\u0432\u0435\u0440\u0436\u0434\u0430\u0442\u044C \u0447\u0435\u0440\u0435\u0437 \u0431\u043E\u0442\u0430</mat-hint>\r
        } @else {\r
          <mat-hint>\u041D\u0435\u043E\u0431\u044F\u0437\u0430\u0442\u0435\u043B\u044C\u043D\u043E \u2014 \u043C\u043E\u0436\u043D\u043E \u0434\u043E\u0431\u0430\u0432\u0438\u0442\u044C \u043F\u043E\u0437\u0436\u0435</mat-hint>\r
        }\r
      </mat-form-field>\r
\r
      @if (form.get('role')?.value === 'STUDENT') {\r
        <mat-checkbox formControlName="isHeadman">\u042F\u0432\u043B\u044F\u0435\u0442\u0441\u044F \u0441\u0442\u0430\u0440\u043E\u0441\u0442\u043E\u0439 \u0433\u0440\u0443\u043F\u043F\u044B</mat-checkbox>\r
      }\r
\r
      @if (submitError()) {\r
        <div class="ud-error" role="alert">\r
          <i class="ph ph-warning-circle" aria-hidden="true"></i>\r
          {{ submitError() }}\r
        </div>\r
      } @else if (apiError()) {\r
        <div class="ud-error" role="alert">\r
          <i class="ph ph-warning-circle" aria-hidden="true"></i>\r
          \u041D\u0435 \u0443\u0434\u0430\u043B\u043E\u0441\u044C \u0441\u043E\u0445\u0440\u0430\u043D\u0438\u0442\u044C. \u041F\u0440\u043E\u0432\u0435\u0440\u044C\u0442\u0435 \u0434\u0430\u043D\u043D\u044B\u0435 \u0438 \u043F\u043E\u043F\u0440\u043E\u0431\u0443\u0439\u0442\u0435 \u0441\u043D\u043E\u0432\u0430.\r
        </div>\r
      }\r
    </form>\r
  }\r
</mat-dialog-content>\r
\r
<mat-dialog-actions align="end">\r
  @if (createdUser()) {\r
    <button mat-flat-button color="primary" mat-dialog-close>\u0413\u043E\u0442\u043E\u0432\u043E</button>\r
  } @else {\r
    <button mat-button mat-dialog-close>\u041E\u0442\u043C\u0435\u043D\u0430</button>\r
    <button mat-flat-button color="primary" (click)="save()" [disabled]="saving()">\r
      @if (data.mode === 'create') {\r
        \u0421\u043E\u0437\u0434\u0430\u0442\u044C \u043F\u043E\u043B\u044C\u0437\u043E\u0432\u0430\u0442\u0435\u043B\u044F\r
      } @else {\r
        \u0421\u043E\u0445\u0440\u0430\u043D\u0438\u0442\u044C \u0438\u0437\u043C\u0435\u043D\u0435\u043D\u0438\u044F\r
      }\r
    </button>\r
  }\r
</mat-dialog-actions>\r
`, styles: ["/* src/app/features/admin/users/user-dialog/user-dialog.component.css */\n:host {\n  --mdc-dialog-container-color: var(--bg-elevated);\n  --mat-dialog-content-padding: 0 24px;\n}\n.ud-form {\n  display: flex;\n  flex-direction: column;\n  gap: var(--space-4);\n  padding: var(--space-2) 0;\n}\n.ud-row {\n  display: grid;\n  grid-template-columns: 1fr 1fr;\n  gap: var(--space-4);\n}\n@media (max-width: 480px) {\n  .ud-row {\n    grid-template-columns: 1fr;\n  }\n}\n.ud-field,\n.ud-field-full {\n  width: 100%;\n}\n.ud-form mat-form-field {\n  --mdc-outlined-text-field-container-shape: var(--radius-md);\n  --mdc-outlined-text-field-outline-color: var(--border-default);\n  --mdc-outlined-text-field-hover-outline-color: var(--border-accent);\n  --mdc-outlined-text-field-focus-outline-color: var(--accent-primary);\n  --mdc-outlined-text-field-input-text-color: var(--text-primary);\n  --mdc-outlined-text-field-label-text-color: var(--text-secondary);\n  --mdc-outlined-text-field-focus-label-text-color: var(--accent-primary);\n  --mat-select-trigger-text-color: var(--text-primary);\n  --mat-option-label-text-color: var(--text-primary);\n  --mat-select-panel-background-color: var(--bg-elevated);\n}\n.ud-hint {\n  font-size: 0.8125rem;\n  color: var(--text-muted);\n  margin: calc(-1 * var(--space-2)) 0 0;\n  padding-left: 2px;\n}\n.ud-error {\n  display: flex;\n  align-items: center;\n  gap: var(--space-2);\n  padding: var(--space-3) var(--space-4);\n  background: color-mix(in srgb, var(--accent-danger) 12%, transparent);\n  border: 1px solid color-mix(in srgb, var(--accent-danger) 28%, transparent);\n  border-radius: var(--radius-md);\n  color: var(--accent-danger);\n  font-size: 0.875rem;\n}\n.ud-error i {\n  font-size: 18px;\n}\n.ud-created {\n  display: flex;\n  flex-direction: column;\n  align-items: center;\n  gap: var(--space-4);\n  padding: var(--space-4) 0 var(--space-2);\n  text-align: center;\n}\n.ud-created__icon {\n  width: 56px;\n  height: 56px;\n  border-radius: var(--radius-full);\n  background: color-mix(in oklab, var(--accent-primary) 15%, transparent);\n  border: 1px solid var(--border-accent);\n  display: grid;\n  place-items: center;\n  color: var(--accent-primary);\n}\n.ud-created__icon i {\n  font-size: 30px;\n}\n.ud-created__title {\n  font-family: var(--font-heading);\n  font-weight: 600;\n  font-size: 1.0625rem;\n  color: var(--text-primary);\n  margin: 0;\n}\n.ud-created__creds {\n  width: 100%;\n  background: var(--bg-surface);\n  border: 1px solid var(--border-default);\n  border-radius: var(--radius-md);\n  overflow: hidden;\n}\n.ud-created__row {\n  display: flex;\n  align-items: center;\n  justify-content: space-between;\n  padding: var(--space-3) var(--space-4);\n  gap: var(--space-4);\n}\n.ud-created__row + .ud-created__row {\n  border-top: 1px solid var(--border-subtle);\n}\n.ud-created__label {\n  font-size: 0.8125rem;\n  color: var(--text-secondary);\n  white-space: nowrap;\n}\n.ud-created__value {\n  font-family: var(--font-mono);\n  font-size: 0.9375rem;\n  color: var(--accent-primary);\n  background: color-mix(in oklab, var(--accent-primary) 10%, transparent);\n  padding: 2px 8px;\n  border-radius: var(--radius-sm);\n  word-break: break-all;\n  text-align: right;\n}\n.ud-created__hint {\n  font-size: 0.8125rem;\n  color: var(--text-muted);\n  margin: 0;\n}\n/*# sourceMappingURL=user-dialog.component.css.map */\n"] }]
  }], () => [], null);
})();
(() => {
  (typeof ngDevMode === "undefined" || ngDevMode) && \u0275setClassDebugInfo(UserDialogComponent, { className: "UserDialogComponent", filePath: "src/app/features/admin/users/user-dialog/user-dialog.component.ts", lineNumber: 47 });
})();

// src/app/features/admin/users/archive-user-dialog/archive-user-dialog.component.ts
var ArchiveUserDialogComponent = class _ArchiveUserDialogComponent {
  data = inject(MAT_DIALOG_DATA);
  fullName = fullName;
  static \u0275fac = function ArchiveUserDialogComponent_Factory(__ngFactoryType__) {
    return new (__ngFactoryType__ || _ArchiveUserDialogComponent)();
  };
  static \u0275cmp = /* @__PURE__ */ \u0275\u0275defineComponent({ type: _ArchiveUserDialogComponent, selectors: [["app-archive-user-dialog"]], decls: 10, vars: 2, consts: [["mat-dialog-title", ""], ["align", "end"], ["mat-button", "", "mat-dialog-close", ""], ["mat-flat-button", "", "color", "warn", 3, "mat-dialog-close"]], template: function ArchiveUserDialogComponent_Template(rf, ctx) {
    if (rf & 1) {
      \u0275\u0275elementStart(0, "h2", 0);
      \u0275\u0275text(1, "\u0410\u0440\u0445\u0438\u0432\u0438\u0440\u043E\u0432\u0430\u0442\u044C \u043F\u043E\u043B\u044C\u0437\u043E\u0432\u0430\u0442\u0435\u043B\u044F?");
      \u0275\u0275elementEnd();
      \u0275\u0275elementStart(2, "mat-dialog-content")(3, "p");
      \u0275\u0275text(4);
      \u0275\u0275elementEnd()();
      \u0275\u0275elementStart(5, "mat-dialog-actions", 1)(6, "button", 2);
      \u0275\u0275text(7, "\u041E\u0442\u043C\u0435\u043D\u0430");
      \u0275\u0275elementEnd();
      \u0275\u0275elementStart(8, "button", 3);
      \u0275\u0275text(9, "\u0410\u0440\u0445\u0438\u0432\u0438\u0440\u043E\u0432\u0430\u0442\u044C");
      \u0275\u0275elementEnd()();
    }
    if (rf & 2) {
      \u0275\u0275advance(4);
      \u0275\u0275textInterpolate1("\u041F\u043E\u043B\u044C\u0437\u043E\u0432\u0430\u0442\u0435\u043B\u044C ", ctx.fullName(ctx.data.user), " \u0431\u0443\u0434\u0435\u0442 \u0434\u0435\u0430\u043A\u0442\u0438\u0432\u0438\u0440\u043E\u0432\u0430\u043D. \u0412\u043E\u0439\u0442\u0438 \u0432 \u0441\u0438\u0441\u0442\u0435\u043C\u0443 \u043E\u043D \u043D\u0435 \u0441\u043C\u043E\u0436\u0435\u0442.");
      \u0275\u0275advance(4);
      \u0275\u0275property("mat-dialog-close", true);
    }
  }, dependencies: [MatDialogModule, MatDialogClose, MatDialogTitle, MatDialogActions, MatDialogContent, MatButtonModule, MatButton], encapsulation: 2 });
};
(() => {
  (typeof ngDevMode === "undefined" || ngDevMode) && setClassMetadata(ArchiveUserDialogComponent, [{
    type: Component,
    args: [{
      selector: "app-archive-user-dialog",
      standalone: true,
      imports: [MatDialogModule, MatButtonModule],
      template: `
    <h2 mat-dialog-title>\u0410\u0440\u0445\u0438\u0432\u0438\u0440\u043E\u0432\u0430\u0442\u044C \u043F\u043E\u043B\u044C\u0437\u043E\u0432\u0430\u0442\u0435\u043B\u044F?</h2>
    <mat-dialog-content>
      <p>\u041F\u043E\u043B\u044C\u0437\u043E\u0432\u0430\u0442\u0435\u043B\u044C {{ fullName(data.user) }} \u0431\u0443\u0434\u0435\u0442 \u0434\u0435\u0430\u043A\u0442\u0438\u0432\u0438\u0440\u043E\u0432\u0430\u043D. \u0412\u043E\u0439\u0442\u0438 \u0432 \u0441\u0438\u0441\u0442\u0435\u043C\u0443 \u043E\u043D \u043D\u0435 \u0441\u043C\u043E\u0436\u0435\u0442.</p>
    </mat-dialog-content>
    <mat-dialog-actions align="end">
      <button mat-button mat-dialog-close>\u041E\u0442\u043C\u0435\u043D\u0430</button>
      <button mat-flat-button color="warn" [mat-dialog-close]="true">\u0410\u0440\u0445\u0438\u0432\u0438\u0440\u043E\u0432\u0430\u0442\u044C</button>
    </mat-dialog-actions>
  `
    }]
  }], null, null);
})();
(() => {
  (typeof ngDevMode === "undefined" || ngDevMode) && \u0275setClassDebugInfo(ArchiveUserDialogComponent, { className: "ArchiveUserDialogComponent", filePath: "src/app/features/admin/users/archive-user-dialog/archive-user-dialog.component.ts", lineNumber: 21 });
})();

// src/app/features/admin/users/users-page.component.ts
var _c0 = () => [20, 50, 100];
function UsersPageComponent_Conditional_11_Template(rf, ctx) {
  if (rf & 1) {
    \u0275\u0275element(0, "mat-progress-bar", 7);
  }
}
function UsersPageComponent_Conditional_40_Template(rf, ctx) {
  if (rf & 1) {
    \u0275\u0275elementStart(0, "div", 20);
    \u0275\u0275element(1, "i", 22);
    \u0275\u0275text(2);
    \u0275\u0275elementEnd();
  }
  if (rf & 2) {
    const ctx_r0 = \u0275\u0275nextContext();
    \u0275\u0275advance(2);
    \u0275\u0275textInterpolate1(" ", ctx_r0.error(), " ");
  }
}
function UsersPageComponent_Conditional_41_Template(rf, ctx) {
  if (rf & 1) {
    \u0275\u0275elementStart(0, "div", 8)(1, "div", 23)(2, "div", 24);
    \u0275\u0275element(3, "i", 25);
    \u0275\u0275elementEnd();
    \u0275\u0275elementStart(4, "h3", 26);
    \u0275\u0275text(5, "\u041F\u043E\u043B\u044C\u0437\u043E\u0432\u0430\u0442\u0435\u043B\u0438 \u043D\u0435 \u043D\u0430\u0439\u0434\u0435\u043D\u044B");
    \u0275\u0275elementEnd();
    \u0275\u0275elementStart(6, "p", 27);
    \u0275\u0275text(7, " \u0418\u0437\u043C\u0435\u043D\u0438\u0442\u0435 \u0444\u0438\u043B\u044C\u0442\u0440\u044B \u0438\u043B\u0438 \u0441\u043E\u0437\u0434\u0430\u0439\u0442\u0435 \u043F\u0435\u0440\u0432\u043E\u0433\u043E \u043F\u043E\u043B\u044C\u0437\u043E\u0432\u0430\u0442\u0435\u043B\u044F \u0447\u0435\u0440\u0435\u0437 \u043A\u043D\u043E\u043F\u043A\u0443 \u0432\u044B\u0448\u0435. ");
    \u0275\u0275elementEnd()()();
  }
}
function UsersPageComponent_Conditional_42_mat_header_cell_3_Template(rf, ctx) {
  if (rf & 1) {
    \u0275\u0275elementStart(0, "mat-header-cell");
    \u0275\u0275text(1, "\u041B\u043E\u0433\u0438\u043D");
    \u0275\u0275elementEnd();
  }
}
function UsersPageComponent_Conditional_42_mat_cell_4_Template(rf, ctx) {
  if (rf & 1) {
    \u0275\u0275elementStart(0, "mat-cell");
    \u0275\u0275text(1);
    \u0275\u0275elementEnd();
  }
  if (rf & 2) {
    const row_r3 = ctx.$implicit;
    \u0275\u0275advance();
    \u0275\u0275textInterpolate(row_r3.login);
  }
}
function UsersPageComponent_Conditional_42_mat_header_cell_6_Template(rf, ctx) {
  if (rf & 1) {
    \u0275\u0275elementStart(0, "mat-header-cell");
    \u0275\u0275text(1, "\u0424\u0418\u041E");
    \u0275\u0275elementEnd();
  }
}
function UsersPageComponent_Conditional_42_mat_cell_7_Template(rf, ctx) {
  if (rf & 1) {
    \u0275\u0275elementStart(0, "mat-cell");
    \u0275\u0275text(1);
    \u0275\u0275elementEnd();
  }
  if (rf & 2) {
    const row_r4 = ctx.$implicit;
    \u0275\u0275advance();
    \u0275\u0275textInterpolate3("", row_r4.lastName, " ", row_r4.firstName, "", row_r4.middleName ? " " + row_r4.middleName : "", "");
  }
}
function UsersPageComponent_Conditional_42_mat_header_cell_9_Template(rf, ctx) {
  if (rf & 1) {
    \u0275\u0275elementStart(0, "mat-header-cell");
    \u0275\u0275text(1, "\u0420\u043E\u043B\u044C");
    \u0275\u0275elementEnd();
  }
}
function UsersPageComponent_Conditional_42_mat_cell_10_Template(rf, ctx) {
  if (rf & 1) {
    \u0275\u0275elementStart(0, "mat-cell");
    \u0275\u0275element(1, "app-role-chip", 41);
    \u0275\u0275elementEnd();
  }
  if (rf & 2) {
    const row_r5 = ctx.$implicit;
    \u0275\u0275advance();
    \u0275\u0275property("role", row_r5.role);
  }
}
function UsersPageComponent_Conditional_42_mat_header_cell_12_Template(rf, ctx) {
  if (rf & 1) {
    \u0275\u0275elementStart(0, "mat-header-cell");
    \u0275\u0275text(1, "\u0413\u0440\u0443\u043F\u043F\u0430");
    \u0275\u0275elementEnd();
  }
}
function UsersPageComponent_Conditional_42_mat_cell_13_Template(rf, ctx) {
  if (rf & 1) {
    \u0275\u0275elementStart(0, "mat-cell");
    \u0275\u0275text(1);
    \u0275\u0275elementEnd();
  }
  if (rf & 2) {
    const row_r6 = ctx.$implicit;
    const ctx_r0 = \u0275\u0275nextContext(2);
    \u0275\u0275advance();
    \u0275\u0275textInterpolate(ctx_r0.getGroupName(row_r6.groupId));
  }
}
function UsersPageComponent_Conditional_42_mat_header_cell_15_Template(rf, ctx) {
  if (rf & 1) {
    \u0275\u0275elementStart(0, "mat-header-cell");
    \u0275\u0275text(1, "\u0421\u0442\u0430\u0442\u0443\u0441");
    \u0275\u0275elementEnd();
  }
}
function UsersPageComponent_Conditional_42_mat_cell_16_Template(rf, ctx) {
  if (rf & 1) {
    \u0275\u0275elementStart(0, "mat-cell");
    \u0275\u0275element(1, "app-status-chip", 42);
    \u0275\u0275elementEnd();
  }
  if (rf & 2) {
    const row_r7 = ctx.$implicit;
    \u0275\u0275advance();
    \u0275\u0275property("status", row_r7.status);
  }
}
function UsersPageComponent_Conditional_42_Conditional_17_mat_header_cell_1_Template(rf, ctx) {
  if (rf & 1) {
    \u0275\u0275elementStart(0, "mat-header-cell");
    \u0275\u0275text(1, "\u041D\u0430\u0447\u0430\u043B\u044C\u043D\u044B\u0439 \u043F\u0430\u0440\u043E\u043B\u044C");
    \u0275\u0275elementEnd();
  }
}
function UsersPageComponent_Conditional_42_Conditional_17_mat_cell_2_Conditional_1_Template(rf, ctx) {
  if (rf & 1) {
    const _r8 = \u0275\u0275getCurrentView();
    \u0275\u0275elementStart(0, "span", 43)(1, "code", 45);
    \u0275\u0275text(2);
    \u0275\u0275elementEnd();
    \u0275\u0275elementStart(3, "button", 46);
    \u0275\u0275listener("click", function UsersPageComponent_Conditional_42_Conditional_17_mat_cell_2_Conditional_1_Template_button_click_3_listener($event) {
      \u0275\u0275restoreView(_r8);
      const row_r9 = \u0275\u0275nextContext().$implicit;
      const ctx_r0 = \u0275\u0275nextContext(3);
      ctx_r0.copyPassword(row_r9.initialPassword);
      return \u0275\u0275resetView($event.stopPropagation());
    });
    \u0275\u0275element(4, "i", 47);
    \u0275\u0275elementEnd()();
  }
  if (rf & 2) {
    const row_r9 = \u0275\u0275nextContext().$implicit;
    \u0275\u0275advance(2);
    \u0275\u0275textInterpolate(row_r9.initialPassword);
  }
}
function UsersPageComponent_Conditional_42_Conditional_17_mat_cell_2_Conditional_2_Template(rf, ctx) {
  if (rf & 1) {
    \u0275\u0275elementStart(0, "span", 44);
    \u0275\u0275text(1, "\u2014");
    \u0275\u0275elementEnd();
  }
}
function UsersPageComponent_Conditional_42_Conditional_17_mat_cell_2_Template(rf, ctx) {
  if (rf & 1) {
    \u0275\u0275elementStart(0, "mat-cell");
    \u0275\u0275template(1, UsersPageComponent_Conditional_42_Conditional_17_mat_cell_2_Conditional_1_Template, 5, 1, "span", 43)(2, UsersPageComponent_Conditional_42_Conditional_17_mat_cell_2_Conditional_2_Template, 2, 0, "span", 44);
    \u0275\u0275elementEnd();
  }
  if (rf & 2) {
    const row_r9 = ctx.$implicit;
    \u0275\u0275advance();
    \u0275\u0275conditional(row_r9.initialPassword ? 1 : 2);
  }
}
function UsersPageComponent_Conditional_42_Conditional_17_Template(rf, ctx) {
  if (rf & 1) {
    \u0275\u0275elementContainerStart(0, 36);
    \u0275\u0275template(1, UsersPageComponent_Conditional_42_Conditional_17_mat_header_cell_1_Template, 2, 0, "mat-header-cell", 30)(2, UsersPageComponent_Conditional_42_Conditional_17_mat_cell_2_Template, 3, 1, "mat-cell", 31);
    \u0275\u0275elementContainerEnd();
  }
}
function UsersPageComponent_Conditional_42_mat_header_cell_19_Template(rf, ctx) {
  if (rf & 1) {
    \u0275\u0275element(0, "mat-header-cell");
  }
}
function UsersPageComponent_Conditional_42_mat_cell_20_Conditional_3_Template(rf, ctx) {
  if (rf & 1) {
    const _r12 = \u0275\u0275getCurrentView();
    \u0275\u0275elementStart(0, "button", 52);
    \u0275\u0275listener("click", function UsersPageComponent_Conditional_42_mat_cell_20_Conditional_3_Template_button_click_0_listener() {
      \u0275\u0275restoreView(_r12);
      const row_r11 = \u0275\u0275nextContext().$implicit;
      const ctx_r0 = \u0275\u0275nextContext(2);
      return \u0275\u0275resetView(ctx_r0.archiveUser(row_r11));
    });
    \u0275\u0275element(1, "i", 53);
    \u0275\u0275elementEnd();
  }
}
function UsersPageComponent_Conditional_42_mat_cell_20_Conditional_4_Template(rf, ctx) {
  if (rf & 1) {
    const _r13 = \u0275\u0275getCurrentView();
    \u0275\u0275elementStart(0, "button", 54);
    \u0275\u0275listener("click", function UsersPageComponent_Conditional_42_mat_cell_20_Conditional_4_Template_button_click_0_listener() {
      \u0275\u0275restoreView(_r13);
      const row_r11 = \u0275\u0275nextContext().$implicit;
      const ctx_r0 = \u0275\u0275nextContext(2);
      return \u0275\u0275resetView(ctx_r0.restoreUser(row_r11));
    });
    \u0275\u0275element(1, "i", 55);
    \u0275\u0275elementEnd();
  }
}
function UsersPageComponent_Conditional_42_mat_cell_20_Template(rf, ctx) {
  if (rf & 1) {
    const _r10 = \u0275\u0275getCurrentView();
    \u0275\u0275elementStart(0, "mat-cell")(1, "button", 48);
    \u0275\u0275listener("click", function UsersPageComponent_Conditional_42_mat_cell_20_Template_button_click_1_listener() {
      const row_r11 = \u0275\u0275restoreView(_r10).$implicit;
      const ctx_r0 = \u0275\u0275nextContext(2);
      return \u0275\u0275resetView(ctx_r0.openEditDialog(row_r11));
    });
    \u0275\u0275element(2, "i", 49);
    \u0275\u0275elementEnd();
    \u0275\u0275template(3, UsersPageComponent_Conditional_42_mat_cell_20_Conditional_3_Template, 2, 0, "button", 50)(4, UsersPageComponent_Conditional_42_mat_cell_20_Conditional_4_Template, 2, 0, "button", 51);
    \u0275\u0275elementEnd();
  }
  if (rf & 2) {
    const row_r11 = ctx.$implicit;
    \u0275\u0275advance(3);
    \u0275\u0275conditional(row_r11.status !== "archived" ? 3 : 4);
  }
}
function UsersPageComponent_Conditional_42_mat_header_row_21_Template(rf, ctx) {
  if (rf & 1) {
    \u0275\u0275element(0, "mat-header-row");
  }
}
function UsersPageComponent_Conditional_42_mat_row_22_Template(rf, ctx) {
  if (rf & 1) {
    \u0275\u0275element(0, "mat-row");
  }
  if (rf & 2) {
    const row_r14 = ctx.$implicit;
    \u0275\u0275classProp("opacity-60", row_r14.status === "archived");
  }
}
function UsersPageComponent_Conditional_42_Template(rf, ctx) {
  if (rf & 1) {
    const _r2 = \u0275\u0275getCurrentView();
    \u0275\u0275elementStart(0, "div", 21)(1, "mat-table", 28);
    \u0275\u0275elementContainerStart(2, 29);
    \u0275\u0275template(3, UsersPageComponent_Conditional_42_mat_header_cell_3_Template, 2, 0, "mat-header-cell", 30)(4, UsersPageComponent_Conditional_42_mat_cell_4_Template, 2, 1, "mat-cell", 31);
    \u0275\u0275elementContainerEnd();
    \u0275\u0275elementContainerStart(5, 32);
    \u0275\u0275template(6, UsersPageComponent_Conditional_42_mat_header_cell_6_Template, 2, 0, "mat-header-cell", 30)(7, UsersPageComponent_Conditional_42_mat_cell_7_Template, 2, 3, "mat-cell", 31);
    \u0275\u0275elementContainerEnd();
    \u0275\u0275elementContainerStart(8, 33);
    \u0275\u0275template(9, UsersPageComponent_Conditional_42_mat_header_cell_9_Template, 2, 0, "mat-header-cell", 30)(10, UsersPageComponent_Conditional_42_mat_cell_10_Template, 2, 1, "mat-cell", 31);
    \u0275\u0275elementContainerEnd();
    \u0275\u0275elementContainerStart(11, 34);
    \u0275\u0275template(12, UsersPageComponent_Conditional_42_mat_header_cell_12_Template, 2, 0, "mat-header-cell", 30)(13, UsersPageComponent_Conditional_42_mat_cell_13_Template, 2, 1, "mat-cell", 31);
    \u0275\u0275elementContainerEnd();
    \u0275\u0275elementContainerStart(14, 35);
    \u0275\u0275template(15, UsersPageComponent_Conditional_42_mat_header_cell_15_Template, 2, 0, "mat-header-cell", 30)(16, UsersPageComponent_Conditional_42_mat_cell_16_Template, 2, 1, "mat-cell", 31);
    \u0275\u0275elementContainerEnd();
    \u0275\u0275template(17, UsersPageComponent_Conditional_42_Conditional_17_Template, 3, 0, "ng-container", 36);
    \u0275\u0275elementContainerStart(18, 37);
    \u0275\u0275template(19, UsersPageComponent_Conditional_42_mat_header_cell_19_Template, 1, 0, "mat-header-cell", 30)(20, UsersPageComponent_Conditional_42_mat_cell_20_Template, 5, 1, "mat-cell", 31);
    \u0275\u0275elementContainerEnd();
    \u0275\u0275template(21, UsersPageComponent_Conditional_42_mat_header_row_21_Template, 1, 0, "mat-header-row", 38)(22, UsersPageComponent_Conditional_42_mat_row_22_Template, 1, 2, "mat-row", 39);
    \u0275\u0275elementEnd();
    \u0275\u0275elementStart(23, "mat-paginator", 40);
    \u0275\u0275listener("page", function UsersPageComponent_Conditional_42_Template_mat_paginator_page_23_listener($event) {
      \u0275\u0275restoreView(_r2);
      const ctx_r0 = \u0275\u0275nextContext();
      return \u0275\u0275resetView(ctx_r0.onPageChange($event));
    });
    \u0275\u0275elementEnd()();
  }
  if (rf & 2) {
    const ctx_r0 = \u0275\u0275nextContext();
    \u0275\u0275advance();
    \u0275\u0275property("dataSource", ctx_r0.users());
    \u0275\u0275advance(16);
    \u0275\u0275conditional(ctx_r0.showInitialPasswordColumn() ? 17 : -1);
    \u0275\u0275advance(4);
    \u0275\u0275property("matHeaderRowDef", ctx_r0.displayedColumns());
    \u0275\u0275advance();
    \u0275\u0275property("matRowDefColumns", ctx_r0.displayedColumns());
    \u0275\u0275advance();
    \u0275\u0275property("length", ctx_r0.totalElements())("pageSize", ctx_r0.pageSize())("pageIndex", ctx_r0.pageIndex())("pageSizeOptions", \u0275\u0275pureFunction0(8, _c0));
  }
}
var UsersPageComponent = class _UsersPageComponent {
  adminApi = inject(AdminApiService);
  dialog = inject(MatDialog);
  snackBar = inject(MatSnackBar);
  destroyRef = inject(DestroyRef);
  route = inject(ActivatedRoute);
  router = inject(Router);
  users = signal([]);
  loading = signal(false);
  error = signal(null);
  totalElements = signal(0);
  pageIndex = signal(0);
  pageSize = signal(20);
  groups = signal([]);
  searchControl = new FormControl("");
  roleFilter = signal("");
  statusFilter = signal("");
  /**
   * BUG-006-4 / D-14: колонка «Начальный пароль» показывается только если
   * хотя бы у одного пользователя в текущей выдаче есть видимый initialPassword
   * (backend отдаёт его только пока {@code passwordChanged = false}).
   */
  showInitialPasswordColumn = computed(() => this.users().some((u) => !!u.initialPassword));
  displayedColumns = computed(() => {
    const base = ["login", "displayName", "role", "group", "status"];
    if (this.showInitialPasswordColumn()) {
      base.push("initialPassword");
    }
    base.push("actions");
    return base;
  });
  ngOnInit() {
    this.adminApi.listGroups().subscribe({
      next: (groups) => this.groups.set(groups)
    });
    const snapshot = this.route.snapshot.queryParamMap;
    const initialRole = snapshot.get("role");
    const initialStatus = snapshot.get("status");
    const initialAction = snapshot.get("action");
    if (initialRole)
      this.roleFilter.set(initialRole.toUpperCase());
    if (initialStatus)
      this.statusFilter.set(initialStatus);
    this.loadUsers();
    if (initialAction === "create") {
      queueMicrotask(() => {
        this.openCreateDialog(initialRole ?? void 0);
        this.router.navigate([], {
          relativeTo: this.route,
          queryParams: { action: null },
          queryParamsHandling: "merge",
          replaceUrl: true
        });
      });
    }
    this.searchControl.valueChanges.pipe(debounceTime(300), distinctUntilChanged(), takeUntilDestroyed(this.destroyRef)).subscribe(() => {
      this.pageIndex.set(0);
      this.loadUsers();
    });
  }
  loadUsers() {
    this.loading.set(true);
    this.error.set(null);
    this.adminApi.listUsers({
      page: this.pageIndex(),
      size: this.pageSize(),
      role: this.roleFilter() || void 0,
      status: this.statusFilter() || void 0,
      search: this.searchControl.value || void 0
    }).subscribe({
      next: (result) => {
        this.users.set(result.items);
        this.totalElements.set(result.total);
        this.loading.set(false);
      },
      error: () => {
        this.error.set("\u041D\u0435 \u0443\u0434\u0430\u043B\u043E\u0441\u044C \u0437\u0430\u0433\u0440\u0443\u0437\u0438\u0442\u044C \u043F\u043E\u043B\u044C\u0437\u043E\u0432\u0430\u0442\u0435\u043B\u0435\u0439.");
        this.loading.set(false);
      }
    });
  }
  onPageChange(event) {
    this.pageIndex.set(event.pageIndex);
    this.pageSize.set(event.pageSize);
    this.loadUsers();
  }
  onRoleFilterChange(role) {
    this.roleFilter.set(role);
    this.pageIndex.set(0);
    this.loadUsers();
  }
  onStatusFilterChange(status) {
    this.statusFilter.set(status);
    this.pageIndex.set(0);
    this.loadUsers();
  }
  getGroupName(groupId) {
    if (groupId === null)
      return "\u2014";
    return this.groups().find((g) => g.id === groupId)?.name ?? "\u2014";
  }
  openCreateDialog(presetRole) {
    const dialogRef = this.dialog.open(UserDialogComponent, {
      width: "520px",
      data: { mode: "create", groups: this.groups(), presetRole }
    });
    dialogRef.afterClosed().subscribe((result) => {
      this.loadUsers();
      if (result?.login) {
        this.snackBar.open(`\u041F\u043E\u043B\u044C\u0437\u043E\u0432\u0430\u0442\u0435\u043B\u044C \u0441\u043E\u0437\u0434\u0430\u043D: ${result.login}`, void 0, {
          duration: 4e3
        });
      }
    });
  }
  openEditDialog(user) {
    const dialogRef = this.dialog.open(UserDialogComponent, {
      width: "520px",
      data: { mode: "edit", user, groups: this.groups() }
    });
    dialogRef.afterClosed().subscribe((result) => {
      if (result) {
        this.snackBar.open("\u0414\u0430\u043D\u043D\u044B\u0435 \u043F\u043E\u043B\u044C\u0437\u043E\u0432\u0430\u0442\u0435\u043B\u044F \u043E\u0431\u043D\u043E\u0432\u043B\u0435\u043D\u044B.", void 0, {
          duration: 4e3
        });
        this.loadUsers();
      }
    });
  }
  archiveUser(user) {
    const dialogRef = this.dialog.open(ArchiveUserDialogComponent, {
      width: "480px",
      data: { user }
    });
    dialogRef.afterClosed().subscribe((confirmed) => {
      if (confirmed) {
        this.adminApi.patchUser(user.id, { status: "archived" }).subscribe({
          next: () => {
            this.snackBar.open("\u041F\u043E\u043B\u044C\u0437\u043E\u0432\u0430\u0442\u0435\u043B\u044C \u0430\u0440\u0445\u0438\u0432\u0438\u0440\u043E\u0432\u0430\u043D.", void 0, {
              duration: 4e3
            });
            this.loadUsers();
          }
        });
      }
    });
  }
  /**
   * BUG-006-4 / T-58-02-04: копирует начальный пароль в буфер обмена.
   * Вызывается только по user-gesture (click), поэтому clipboard API
   * всегда доступен; ошибки (например SecurityError в устаревших браузерах)
   * сообщаем пользователю через snackbar без утечек пароля в логи.
   */
  copyPassword(password) {
    if (!password)
      return;
    const clipboard = typeof navigator !== "undefined" ? navigator.clipboard : void 0;
    if (!clipboard) {
      this.snackBar.open("\u0411\u0443\u0444\u0435\u0440 \u043E\u0431\u043C\u0435\u043D\u0430 \u043D\u0435\u0434\u043E\u0441\u0442\u0443\u043F\u0435\u043D \u0432 \u044D\u0442\u043E\u043C \u0431\u0440\u0430\u0443\u0437\u0435\u0440\u0435", void 0, { duration: 3e3 });
      return;
    }
    clipboard.writeText(password).then(() => {
      this.snackBar.open("\u041D\u0430\u0447\u0430\u043B\u044C\u043D\u044B\u0439 \u043F\u0430\u0440\u043E\u043B\u044C \u0441\u043A\u043E\u043F\u0438\u0440\u043E\u0432\u0430\u043D", void 0, { duration: 2500 });
    }, () => {
      this.snackBar.open("\u041D\u0435 \u0443\u0434\u0430\u043B\u043E\u0441\u044C \u0441\u043A\u043E\u043F\u0438\u0440\u043E\u0432\u0430\u0442\u044C \u043F\u0430\u0440\u043E\u043B\u044C", void 0, { duration: 3e3 });
    });
  }
  restoreUser(user) {
    this.adminApi.patchUser(user.id, { status: "active" }).subscribe({
      next: () => {
        this.snackBar.open("\u041F\u043E\u043B\u044C\u0437\u043E\u0432\u0430\u0442\u0435\u043B\u044C \u0432\u043E\u0441\u0441\u0442\u0430\u043D\u043E\u0432\u043B\u0435\u043D.", void 0, {
          duration: 4e3
        });
        this.loadUsers();
      }
    });
  }
  static \u0275fac = function UsersPageComponent_Factory(__ngFactoryType__) {
    return new (__ngFactoryType__ || _UsersPageComponent)();
  };
  static \u0275cmp = /* @__PURE__ */ \u0275\u0275defineComponent({ type: _UsersPageComponent, selectors: [["app-users-page"]], decls: 43, vars: 6, consts: [[1, "page-stack"], [1, "page-header"], [1, "page-header__title"], [1, "page-header__subtitle"], [1, "page-header__actions"], ["type", "button", 1, "btn-brand", 3, "click"], ["aria-hidden", "true", 1, "ph", "ph-plus"], ["mode", "indeterminate"], [1, "page-card"], [1, "page-filters"], [1, "flex-1", "min-w-[220px]"], ["matInput", "", "placeholder", "\u041F\u043E\u0438\u0441\u043A \u043F\u043E \u0438\u043C\u0435\u043D\u0438 \u0438\u043B\u0438 \u043B\u043E\u0433\u0438\u043D\u0443\u2026", 3, "formControl"], [1, "w-[200px]"], [3, "selectionChange", "value"], ["value", ""], ["value", "ADMIN"], ["value", "TEACHER"], ["value", "STUDENT"], ["value", "active"], ["value", "archived"], ["role", "alert", 1, "page-error"], [1, "page-card", "page-card--flush"], ["aria-hidden", "true", 1, "ph", "ph-warning-circle"], [1, "page-empty"], ["aria-hidden", "true", 1, "page-empty__icon"], [1, "ph-duotone", "ph-users"], [1, "page-empty__title"], [1, "page-empty__text"], [3, "dataSource"], ["matColumnDef", "login"], [4, "matHeaderCellDef"], [4, "matCellDef"], ["matColumnDef", "displayName"], ["matColumnDef", "role"], ["matColumnDef", "group"], ["matColumnDef", "status"], ["matColumnDef", "initialPassword"], ["matColumnDef", "actions"], [4, "matHeaderRowDef"], [3, "opacity-60", 4, "matRowDef", "matRowDefColumns"], [3, "page", "length", "pageSize", "pageIndex", "pageSizeOptions"], [3, "role"], [3, "status"], [1, "init-password__wrap"], [1, "init-password__muted"], [1, "init-password"], ["mat-icon-button", "", "matTooltip", "\u0421\u043A\u043E\u043F\u0438\u0440\u043E\u0432\u0430\u0442\u044C", "aria-label", "\u0421\u043A\u043E\u043F\u0438\u0440\u043E\u0432\u0430\u0442\u044C \u043D\u0430\u0447\u0430\u043B\u044C\u043D\u044B\u0439 \u043F\u0430\u0440\u043E\u043B\u044C", 3, "click"], ["aria-hidden", "true", 1, "ph", "ph-copy"], ["mat-icon-button", "", "matTooltip", "\u0420\u0435\u0434\u0430\u043A\u0442\u0438\u0440\u043E\u0432\u0430\u0442\u044C", "aria-label", "\u0420\u0435\u0434\u0430\u043A\u0442\u0438\u0440\u043E\u0432\u0430\u0442\u044C", 3, "click"], ["aria-hidden", "true", 1, "ph", "ph-pencil-simple"], ["mat-icon-button", "", "matTooltip", "\u0410\u0440\u0445\u0438\u0432\u0438\u0440\u043E\u0432\u0430\u0442\u044C", "aria-label", "\u0410\u0440\u0445\u0438\u0432\u0438\u0440\u043E\u0432\u0430\u0442\u044C"], ["mat-icon-button", "", "matTooltip", "\u0412\u043E\u0441\u0441\u0442\u0430\u043D\u043E\u0432\u0438\u0442\u044C", "aria-label", "\u0412\u043E\u0441\u0441\u0442\u0430\u043D\u043E\u0432\u0438\u0442\u044C"], ["mat-icon-button", "", "matTooltip", "\u0410\u0440\u0445\u0438\u0432\u0438\u0440\u043E\u0432\u0430\u0442\u044C", "aria-label", "\u0410\u0440\u0445\u0438\u0432\u0438\u0440\u043E\u0432\u0430\u0442\u044C", 3, "click"], ["aria-hidden", "true", 1, "ph", "ph-archive"], ["mat-icon-button", "", "matTooltip", "\u0412\u043E\u0441\u0441\u0442\u0430\u043D\u043E\u0432\u0438\u0442\u044C", "aria-label", "\u0412\u043E\u0441\u0441\u0442\u0430\u043D\u043E\u0432\u0438\u0442\u044C", 3, "click"], ["aria-hidden", "true", 1, "ph", "ph-arrow-counter-clockwise"]], template: function UsersPageComponent_Template(rf, ctx) {
    if (rf & 1) {
      \u0275\u0275elementStart(0, "div", 0)(1, "header", 1)(2, "div", 2)(3, "h1");
      \u0275\u0275text(4, "\u041F\u043E\u043B\u044C\u0437\u043E\u0432\u0430\u0442\u0435\u043B\u0438");
      \u0275\u0275elementEnd();
      \u0275\u0275elementStart(5, "p", 3);
      \u0275\u0275text(6, " \u0423\u043F\u0440\u0430\u0432\u043B\u0435\u043D\u0438\u0435 \u0443\u0447\u0451\u0442\u043D\u044B\u043C\u0438 \u0437\u0430\u043F\u0438\u0441\u044F\u043C\u0438 \u0441\u0442\u0443\u0434\u0435\u043D\u0442\u043E\u0432, \u043F\u0440\u0435\u043F\u043E\u0434\u0430\u0432\u0430\u0442\u0435\u043B\u0435\u0439 \u0438 \u0430\u0434\u043C\u0438\u043D\u0438\u0441\u0442\u0440\u0430\u0442\u043E\u0440\u043E\u0432. ");
      \u0275\u0275elementEnd()();
      \u0275\u0275elementStart(7, "div", 4)(8, "button", 5);
      \u0275\u0275listener("click", function UsersPageComponent_Template_button_click_8_listener() {
        return ctx.openCreateDialog();
      });
      \u0275\u0275element(9, "i", 6);
      \u0275\u0275text(10, " \u0421\u043E\u0437\u0434\u0430\u0442\u044C \u043F\u043E\u043B\u044C\u0437\u043E\u0432\u0430\u0442\u0435\u043B\u044F ");
      \u0275\u0275elementEnd()()();
      \u0275\u0275template(11, UsersPageComponent_Conditional_11_Template, 1, 0, "mat-progress-bar", 7);
      \u0275\u0275elementStart(12, "div", 8)(13, "div", 9)(14, "mat-form-field", 10)(15, "mat-label");
      \u0275\u0275text(16, "\u041F\u043E\u0438\u0441\u043A");
      \u0275\u0275elementEnd();
      \u0275\u0275element(17, "input", 11);
      \u0275\u0275elementEnd();
      \u0275\u0275elementStart(18, "mat-form-field", 12)(19, "mat-label");
      \u0275\u0275text(20, "\u0420\u043E\u043B\u044C");
      \u0275\u0275elementEnd();
      \u0275\u0275elementStart(21, "mat-select", 13);
      \u0275\u0275listener("selectionChange", function UsersPageComponent_Template_mat_select_selectionChange_21_listener($event) {
        return ctx.onRoleFilterChange($event.value);
      });
      \u0275\u0275elementStart(22, "mat-option", 14);
      \u0275\u0275text(23, "\u0412\u0441\u0435 \u0440\u043E\u043B\u0438");
      \u0275\u0275elementEnd();
      \u0275\u0275elementStart(24, "mat-option", 15);
      \u0275\u0275text(25, "\u0410\u0434\u043C\u0438\u043D");
      \u0275\u0275elementEnd();
      \u0275\u0275elementStart(26, "mat-option", 16);
      \u0275\u0275text(27, "\u041F\u0440\u0435\u043F\u043E\u0434\u0430\u0432\u0430\u0442\u0435\u043B\u044C");
      \u0275\u0275elementEnd();
      \u0275\u0275elementStart(28, "mat-option", 17);
      \u0275\u0275text(29, "\u0421\u0442\u0443\u0434\u0435\u043D\u0442");
      \u0275\u0275elementEnd()()();
      \u0275\u0275elementStart(30, "mat-form-field", 12)(31, "mat-label");
      \u0275\u0275text(32, "\u0421\u0442\u0430\u0442\u0443\u0441");
      \u0275\u0275elementEnd();
      \u0275\u0275elementStart(33, "mat-select", 13);
      \u0275\u0275listener("selectionChange", function UsersPageComponent_Template_mat_select_selectionChange_33_listener($event) {
        return ctx.onStatusFilterChange($event.value);
      });
      \u0275\u0275elementStart(34, "mat-option", 14);
      \u0275\u0275text(35, "\u0412\u0441\u0435");
      \u0275\u0275elementEnd();
      \u0275\u0275elementStart(36, "mat-option", 18);
      \u0275\u0275text(37, "\u0410\u043A\u0442\u0438\u0432\u043D\u044B\u0435");
      \u0275\u0275elementEnd();
      \u0275\u0275elementStart(38, "mat-option", 19);
      \u0275\u0275text(39, "\u0410\u0440\u0445\u0438\u0432\u043D\u044B\u0435");
      \u0275\u0275elementEnd()()()()();
      \u0275\u0275template(40, UsersPageComponent_Conditional_40_Template, 3, 1, "div", 20)(41, UsersPageComponent_Conditional_41_Template, 8, 0, "div", 8)(42, UsersPageComponent_Conditional_42_Template, 24, 9, "div", 21);
      \u0275\u0275elementEnd();
    }
    if (rf & 2) {
      \u0275\u0275advance(11);
      \u0275\u0275conditional(ctx.loading() ? 11 : -1);
      \u0275\u0275advance(6);
      \u0275\u0275property("formControl", ctx.searchControl);
      \u0275\u0275advance(4);
      \u0275\u0275property("value", ctx.roleFilter());
      \u0275\u0275advance(12);
      \u0275\u0275property("value", ctx.statusFilter());
      \u0275\u0275advance(7);
      \u0275\u0275conditional(ctx.error() ? 40 : -1);
      \u0275\u0275advance();
      \u0275\u0275conditional(ctx.users().length === 0 && !ctx.loading() ? 41 : 42);
    }
  }, dependencies: [
    CommonModule,
    ReactiveFormsModule,
    DefaultValueAccessor,
    NgControlStatus,
    FormControlDirective,
    MatTableModule,
    MatTable,
    MatHeaderCellDef,
    MatHeaderRowDef,
    MatColumnDef,
    MatCellDef,
    MatRowDef,
    MatHeaderCell,
    MatCell,
    MatHeaderRow,
    MatRow,
    MatPaginatorModule,
    MatPaginator,
    MatFormFieldModule,
    MatFormField,
    MatLabel,
    MatInputModule,
    MatInput,
    MatSelectModule,
    MatSelect,
    MatOption,
    MatButtonModule,
    MatIconButton,
    MatProgressBarModule,
    MatProgressBar,
    MatTooltipModule,
    MatTooltip,
    MatSnackBarModule,
    MatDialogModule,
    RoleChipComponent,
    StatusChipComponent
  ], styles: ['\n\n.init-password__wrap[_ngcontent-%COMP%] {\n  display: inline-flex;\n  align-items: center;\n  gap: 4px;\n}\n.init-password[_ngcontent-%COMP%] {\n  font-family:\n    "Fira Code",\n    "JetBrains Mono",\n    Consolas,\n    monospace;\n  font-size: 0.875rem;\n  background: var(--mat-sys-surface-variant, rgba(0, 0, 0, 0.06));\n  color: var(--mat-sys-on-surface-variant, inherit);\n  padding: 2px 6px;\n  border-radius: 4px;\n  letter-spacing: 0.5px;\n  -webkit-user-select: all;\n  user-select: all;\n}\n.init-password__muted[_ngcontent-%COMP%] {\n  color: var(--mat-sys-outline, #9e9e9e);\n}\n/*# sourceMappingURL=users-page.component.css.map */'] });
};
(() => {
  (typeof ngDevMode === "undefined" || ngDevMode) && setClassMetadata(UsersPageComponent, [{
    type: Component,
    args: [{ selector: "app-users-page", standalone: true, imports: [
      CommonModule,
      ReactiveFormsModule,
      MatTableModule,
      MatPaginatorModule,
      MatFormFieldModule,
      MatInputModule,
      MatSelectModule,
      MatButtonModule,
      MatProgressBarModule,
      MatTooltipModule,
      MatSnackBarModule,
      MatDialogModule,
      RoleChipComponent,
      StatusChipComponent
    ], template: `<div class="page-stack">\r
  <header class="page-header">\r
    <div class="page-header__title">\r
      <h1>\u041F\u043E\u043B\u044C\u0437\u043E\u0432\u0430\u0442\u0435\u043B\u0438</h1>\r
      <p class="page-header__subtitle">\r
        \u0423\u043F\u0440\u0430\u0432\u043B\u0435\u043D\u0438\u0435 \u0443\u0447\u0451\u0442\u043D\u044B\u043C\u0438 \u0437\u0430\u043F\u0438\u0441\u044F\u043C\u0438 \u0441\u0442\u0443\u0434\u0435\u043D\u0442\u043E\u0432, \u043F\u0440\u0435\u043F\u043E\u0434\u0430\u0432\u0430\u0442\u0435\u043B\u0435\u0439 \u0438 \u0430\u0434\u043C\u0438\u043D\u0438\u0441\u0442\u0440\u0430\u0442\u043E\u0440\u043E\u0432.\r
      </p>\r
    </div>\r
    <div class="page-header__actions">\r
      <button type="button" class="btn-brand" (click)="openCreateDialog()">\r
        <i class="ph ph-plus" aria-hidden="true"></i>\r
        \u0421\u043E\u0437\u0434\u0430\u0442\u044C \u043F\u043E\u043B\u044C\u0437\u043E\u0432\u0430\u0442\u0435\u043B\u044F\r
      </button>\r
    </div>\r
  </header>\r
\r
  @if (loading()) {\r
    <mat-progress-bar mode="indeterminate"></mat-progress-bar>\r
  }\r
\r
  <div class="page-card">\r
    <div class="page-filters">\r
      <mat-form-field class="flex-1 min-w-[220px]">\r
        <mat-label>\u041F\u043E\u0438\u0441\u043A</mat-label>\r
        <input matInput placeholder="\u041F\u043E\u0438\u0441\u043A \u043F\u043E \u0438\u043C\u0435\u043D\u0438 \u0438\u043B\u0438 \u043B\u043E\u0433\u0438\u043D\u0443\u2026" [formControl]="searchControl" />\r
      </mat-form-field>\r
\r
      <mat-form-field class="w-[200px]">\r
        <mat-label>\u0420\u043E\u043B\u044C</mat-label>\r
        <mat-select [value]="roleFilter()" (selectionChange)="onRoleFilterChange($event.value)">\r
          <mat-option value="">\u0412\u0441\u0435 \u0440\u043E\u043B\u0438</mat-option>\r
          <mat-option value="ADMIN">\u0410\u0434\u043C\u0438\u043D</mat-option>\r
          <mat-option value="TEACHER">\u041F\u0440\u0435\u043F\u043E\u0434\u0430\u0432\u0430\u0442\u0435\u043B\u044C</mat-option>\r
          <mat-option value="STUDENT">\u0421\u0442\u0443\u0434\u0435\u043D\u0442</mat-option>\r
        </mat-select>\r
      </mat-form-field>\r
\r
      <mat-form-field class="w-[200px]">\r
        <mat-label>\u0421\u0442\u0430\u0442\u0443\u0441</mat-label>\r
        <mat-select [value]="statusFilter()" (selectionChange)="onStatusFilterChange($event.value)">\r
          <mat-option value="">\u0412\u0441\u0435</mat-option>\r
          <mat-option value="active">\u0410\u043A\u0442\u0438\u0432\u043D\u044B\u0435</mat-option>\r
          <mat-option value="archived">\u0410\u0440\u0445\u0438\u0432\u043D\u044B\u0435</mat-option>\r
        </mat-select>\r
      </mat-form-field>\r
    </div>\r
  </div>\r
\r
  @if (error()) {\r
    <div class="page-error" role="alert">\r
      <i class="ph ph-warning-circle" aria-hidden="true"></i>\r
      {{ error() }}\r
    </div>\r
  }\r
\r
  @if (users().length === 0 && !loading()) {\r
    <div class="page-card">\r
      <div class="page-empty">\r
        <div class="page-empty__icon" aria-hidden="true">\r
          <i class="ph-duotone ph-users"></i>\r
        </div>\r
        <h3 class="page-empty__title">\u041F\u043E\u043B\u044C\u0437\u043E\u0432\u0430\u0442\u0435\u043B\u0438 \u043D\u0435 \u043D\u0430\u0439\u0434\u0435\u043D\u044B</h3>\r
        <p class="page-empty__text">\r
          \u0418\u0437\u043C\u0435\u043D\u0438\u0442\u0435 \u0444\u0438\u043B\u044C\u0442\u0440\u044B \u0438\u043B\u0438 \u0441\u043E\u0437\u0434\u0430\u0439\u0442\u0435 \u043F\u0435\u0440\u0432\u043E\u0433\u043E \u043F\u043E\u043B\u044C\u0437\u043E\u0432\u0430\u0442\u0435\u043B\u044F \u0447\u0435\u0440\u0435\u0437 \u043A\u043D\u043E\u043F\u043A\u0443 \u0432\u044B\u0448\u0435.\r
        </p>\r
      </div>\r
    </div>\r
  } @else {\r
    <div class="page-card page-card--flush">\r
      <mat-table [dataSource]="users()">\r
        <ng-container matColumnDef="login">\r
          <mat-header-cell *matHeaderCellDef>\u041B\u043E\u0433\u0438\u043D</mat-header-cell>\r
          <mat-cell *matCellDef="let row">{{ row.login }}</mat-cell>\r
        </ng-container>\r
\r
        <ng-container matColumnDef="displayName">\r
          <mat-header-cell *matHeaderCellDef>\u0424\u0418\u041E</mat-header-cell>\r
          <mat-cell *matCellDef="let row">{{ row.lastName }} {{ row.firstName }}{{ row.middleName ? ' ' + row.middleName : '' }}</mat-cell>\r
        </ng-container>\r
\r
        <ng-container matColumnDef="role">\r
          <mat-header-cell *matHeaderCellDef>\u0420\u043E\u043B\u044C</mat-header-cell>\r
          <mat-cell *matCellDef="let row">\r
            <app-role-chip [role]="row.role" />\r
          </mat-cell>\r
        </ng-container>\r
\r
        <ng-container matColumnDef="group">\r
          <mat-header-cell *matHeaderCellDef>\u0413\u0440\u0443\u043F\u043F\u0430</mat-header-cell>\r
          <mat-cell *matCellDef="let row">{{ getGroupName(row.groupId) }}</mat-cell>\r
        </ng-container>\r
\r
        <ng-container matColumnDef="status">\r
          <mat-header-cell *matHeaderCellDef>\u0421\u0442\u0430\u0442\u0443\u0441</mat-header-cell>\r
          <mat-cell *matCellDef="let row">\r
            <app-status-chip [status]="row.status" />\r
          </mat-cell>\r
        </ng-container>\r
\r
        @if (showInitialPasswordColumn()) {\r
          <ng-container matColumnDef="initialPassword">\r
            <mat-header-cell *matHeaderCellDef>\u041D\u0430\u0447\u0430\u043B\u044C\u043D\u044B\u0439 \u043F\u0430\u0440\u043E\u043B\u044C</mat-header-cell>\r
            <mat-cell *matCellDef="let row">\r
              @if (row.initialPassword) {\r
                <span class="init-password__wrap">\r
                  <code class="init-password">{{ row.initialPassword }}</code>\r
                  <button\r
                    mat-icon-button\r
                    matTooltip="\u0421\u043A\u043E\u043F\u0438\u0440\u043E\u0432\u0430\u0442\u044C"\r
                    aria-label="\u0421\u043A\u043E\u043F\u0438\u0440\u043E\u0432\u0430\u0442\u044C \u043D\u0430\u0447\u0430\u043B\u044C\u043D\u044B\u0439 \u043F\u0430\u0440\u043E\u043B\u044C"\r
                    (click)="copyPassword(row.initialPassword); $event.stopPropagation()"\r
                  >\r
                    <i class="ph ph-copy" aria-hidden="true"></i>\r
                  </button>\r
                </span>\r
              } @else {\r
                <span class="init-password__muted">\u2014</span>\r
              }\r
            </mat-cell>\r
          </ng-container>\r
        }\r
\r
        <ng-container matColumnDef="actions">\r
          <mat-header-cell *matHeaderCellDef></mat-header-cell>\r
          <mat-cell *matCellDef="let row">\r
            <button mat-icon-button matTooltip="\u0420\u0435\u0434\u0430\u043A\u0442\u0438\u0440\u043E\u0432\u0430\u0442\u044C" aria-label="\u0420\u0435\u0434\u0430\u043A\u0442\u0438\u0440\u043E\u0432\u0430\u0442\u044C" (click)="openEditDialog(row)">\r
              <i class="ph ph-pencil-simple" aria-hidden="true"></i>\r
            </button>\r
            @if (row.status !== 'archived') {\r
              <button mat-icon-button matTooltip="\u0410\u0440\u0445\u0438\u0432\u0438\u0440\u043E\u0432\u0430\u0442\u044C" aria-label="\u0410\u0440\u0445\u0438\u0432\u0438\u0440\u043E\u0432\u0430\u0442\u044C" (click)="archiveUser(row)">\r
                <i class="ph ph-archive" aria-hidden="true"></i>\r
              </button>\r
            } @else {\r
              <button mat-icon-button matTooltip="\u0412\u043E\u0441\u0441\u0442\u0430\u043D\u043E\u0432\u0438\u0442\u044C" aria-label="\u0412\u043E\u0441\u0441\u0442\u0430\u043D\u043E\u0432\u0438\u0442\u044C" (click)="restoreUser(row)">\r
                <i class="ph ph-arrow-counter-clockwise" aria-hidden="true"></i>\r
              </button>\r
            }\r
          </mat-cell>\r
        </ng-container>\r
\r
        <mat-header-row *matHeaderRowDef="displayedColumns()"></mat-header-row>\r
        <mat-row\r
          *matRowDef="let row; columns: displayedColumns()"\r
          [class.opacity-60]="row.status === 'archived'"\r
        ></mat-row>\r
      </mat-table>\r
\r
      <mat-paginator\r
        [length]="totalElements()"\r
        [pageSize]="pageSize()"\r
        [pageIndex]="pageIndex()"\r
        [pageSizeOptions]="[20, 50, 100]"\r
        (page)="onPageChange($event)"\r
      ></mat-paginator>\r
    </div>\r
  }\r
</div>\r
`, styles: ['/* src/app/features/admin/users/users-page.component.scss */\n.init-password__wrap {\n  display: inline-flex;\n  align-items: center;\n  gap: 4px;\n}\n.init-password {\n  font-family:\n    "Fira Code",\n    "JetBrains Mono",\n    Consolas,\n    monospace;\n  font-size: 0.875rem;\n  background: var(--mat-sys-surface-variant, rgba(0, 0, 0, 0.06));\n  color: var(--mat-sys-on-surface-variant, inherit);\n  padding: 2px 6px;\n  border-radius: 4px;\n  letter-spacing: 0.5px;\n  -webkit-user-select: all;\n  user-select: all;\n}\n.init-password__muted {\n  color: var(--mat-sys-outline, #9e9e9e);\n}\n/*# sourceMappingURL=users-page.component.css.map */\n'] }]
  }], null, null);
})();
(() => {
  (typeof ngDevMode === "undefined" || ngDevMode) && \u0275setClassDebugInfo(UsersPageComponent, { className: "UsersPageComponent", filePath: "src/app/features/admin/users/users-page.component.ts", lineNumber: 47 });
})();
export {
  UsersPageComponent
};
//# sourceMappingURL=chunk-DS5F6X5M.js.map
