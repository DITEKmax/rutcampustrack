import {
  HeadmanApiService
} from "./chunk-ZC6IXG25.js";
import "./chunk-F5IUR55I.js";
import {
  MatIcon,
  MatIconModule
} from "./chunk-JUF5AB3M.js";
import {
  MatSnackBar
} from "./chunk-5Q6RVIG4.js";
import "./chunk-OYYF72SB.js";
import {
  animate,
  style,
  transition,
  trigger
} from "./chunk-JKCQIGK6.js";
import {
  AuthService
} from "./chunk-ID5GKTOO.js";
import {
  CdkCell,
  CdkCellDef,
  CdkColumnDef,
  CdkHeaderCell,
  CdkHeaderCellDef,
  CdkHeaderRow,
  CdkHeaderRowDef,
  CdkRow,
  CdkRowDef,
  CdkTable,
  CdkTableModule
} from "./chunk-JHAETIDC.js";
import "./chunk-OIBNGD5S.js";
import {
  MatFormField,
  MatFormFieldModule,
  MatLabel,
  MatSelect,
  MatSelectModule
} from "./chunk-JQW43QIT.js";
import {
  MatProgressBar,
  MatProgressBarModule
} from "./chunk-YZBQHHAR.js";
import "./chunk-GTDHNJL7.js";
import {
  MatOption
} from "./chunk-676FZPAG.js";
import {
  MatRippleModule
} from "./chunk-2WN7CIGJ.js";
import {
  MatRipple,
  _StructuralStylesLoader
} from "./chunk-ZEX6QU7O.js";
import {
  CdkScrollableModule,
  DomPortalOutlet,
  Overlay,
  OverlayConfig,
  OverlayModule,
  TemplatePortal
} from "./chunk-G4JX6AWT.js";
import "./chunk-YIJEORIR.js";
import {
  DOWN_ARROW,
  Directionality,
  ENTER,
  ESCAPE,
  FocusKeyManager,
  FocusMonitor,
  LEFT_ARROW,
  MatCommonModule,
  RIGHT_ARROW,
  SPACE,
  UP_ARROW,
  _CdkPrivateStyleLoader,
  _IdGenerator,
  _bindEventWithOptions,
  hasModifierKey,
  isFakeMousedownFromScreenReader,
  isFakeTouchstartFromScreenReader
} from "./chunk-FW2NJ6PM.js";
import {
  FormsModule,
  NgControlStatus,
  NgModel
} from "./chunk-HWHV5SCS.js";
import "./chunk-RWV7HGF7.js";
import "./chunk-FLE4DLW4.js";
import {
  DOCUMENT
} from "./chunk-CLRYRPPS.js";
import {
  ANIMATION_MODULE_TYPE,
  ApplicationRef,
  ChangeDetectionStrategy,
  ChangeDetectorRef,
  Component,
  ContentChild,
  ContentChildren,
  Directive,
  ElementRef,
  EventEmitter,
  InjectionToken,
  Injector,
  Input,
  NgModule,
  NgZone,
  Output,
  QueryList,
  Renderer2,
  Subject,
  Subscription,
  TemplateRef,
  ViewChild,
  ViewContainerRef,
  ViewEncapsulation,
  __spreadProps,
  __spreadValues,
  afterNextRender,
  booleanAttribute,
  catchError,
  computed,
  effect,
  filter,
  finalize,
  forkJoin,
  inject,
  merge,
  of,
  setClassMetadata,
  signal,
  startWith,
  switchMap,
  take,
  takeUntil,
  ɵsetClassDebugInfo,
  ɵɵNgOnChangesFeature,
  ɵɵProvidersFeature,
  ɵɵadvance,
  ɵɵattribute,
  ɵɵclassMap,
  ɵɵclassMapInterpolate1,
  ɵɵclassProp,
  ɵɵconditional,
  ɵɵcontentQuery,
  ɵɵdeclareLet,
  ɵɵdefineComponent,
  ɵɵdefineDirective,
  ɵɵdefineInjector,
  ɵɵdefineNgModule,
  ɵɵelement,
  ɵɵelementContainerEnd,
  ɵɵelementContainerStart,
  ɵɵelementEnd,
  ɵɵelementStart,
  ɵɵgetCurrentView,
  ɵɵlistener,
  ɵɵloadQuery,
  ɵɵnamespaceSVG,
  ɵɵnextContext,
  ɵɵprojection,
  ɵɵprojectionDef,
  ɵɵproperty,
  ɵɵpureFunction2,
  ɵɵqueryRefresh,
  ɵɵreadContextLet,
  ɵɵreference,
  ɵɵrepeater,
  ɵɵrepeaterCreate,
  ɵɵresetView,
  ɵɵrestoreView,
  ɵɵstoreLet,
  ɵɵstyleProp,
  ɵɵsyntheticHostProperty,
  ɵɵtemplate,
  ɵɵtext,
  ɵɵtextInterpolate,
  ɵɵtextInterpolate1,
  ɵɵviewQuery
} from "./chunk-4M4FDLBS.js";

// node_modules/@angular/material/fesm2022/menu.mjs
var _c0 = ["mat-menu-item", ""];
var _c1 = [[["mat-icon"], ["", "matMenuItemIcon", ""]], "*"];
var _c2 = ["mat-icon, [matMenuItemIcon]", "*"];
function MatMenuItem_Conditional_4_Template(rf, ctx) {
  if (rf & 1) {
    \u0275\u0275namespaceSVG();
    \u0275\u0275elementStart(0, "svg", 2);
    \u0275\u0275element(1, "polygon", 3);
    \u0275\u0275elementEnd();
  }
}
var _c3 = ["*"];
function MatMenu_ng_template_0_Template(rf, ctx) {
  if (rf & 1) {
    const _r1 = \u0275\u0275getCurrentView();
    \u0275\u0275elementStart(0, "div", 0);
    \u0275\u0275listener("click", function MatMenu_ng_template_0_Template_div_click_0_listener() {
      \u0275\u0275restoreView(_r1);
      const ctx_r1 = \u0275\u0275nextContext();
      return \u0275\u0275resetView(ctx_r1.closed.emit("click"));
    })("animationstart", function MatMenu_ng_template_0_Template_div_animationstart_0_listener($event) {
      \u0275\u0275restoreView(_r1);
      const ctx_r1 = \u0275\u0275nextContext();
      return \u0275\u0275resetView(ctx_r1._onAnimationStart($event.animationName));
    })("animationend", function MatMenu_ng_template_0_Template_div_animationend_0_listener($event) {
      \u0275\u0275restoreView(_r1);
      const ctx_r1 = \u0275\u0275nextContext();
      return \u0275\u0275resetView(ctx_r1._onAnimationDone($event.animationName));
    })("animationcancel", function MatMenu_ng_template_0_Template_div_animationcancel_0_listener($event) {
      \u0275\u0275restoreView(_r1);
      const ctx_r1 = \u0275\u0275nextContext();
      return \u0275\u0275resetView(ctx_r1._onAnimationDone($event.animationName));
    });
    \u0275\u0275elementStart(1, "div", 1);
    \u0275\u0275projection(2);
    \u0275\u0275elementEnd()();
  }
  if (rf & 2) {
    const ctx_r1 = \u0275\u0275nextContext();
    \u0275\u0275classMap(ctx_r1._classList);
    \u0275\u0275classProp("mat-menu-panel-animations-disabled", ctx_r1._animationsDisabled)("mat-menu-panel-exit-animation", ctx_r1._panelAnimationState === "void")("mat-menu-panel-animating", ctx_r1._isAnimating);
    \u0275\u0275property("id", ctx_r1.panelId);
    \u0275\u0275attribute("aria-label", ctx_r1.ariaLabel || null)("aria-labelledby", ctx_r1.ariaLabelledby || null)("aria-describedby", ctx_r1.ariaDescribedby || null);
  }
}
var MAT_MENU_PANEL = new InjectionToken("MAT_MENU_PANEL");
var MatMenuItem = class _MatMenuItem {
  _elementRef = inject(ElementRef);
  _document = inject(DOCUMENT);
  _focusMonitor = inject(FocusMonitor);
  _parentMenu = inject(MAT_MENU_PANEL, {
    optional: true
  });
  _changeDetectorRef = inject(ChangeDetectorRef);
  /** ARIA role for the menu item. */
  role = "menuitem";
  /** Whether the menu item is disabled. */
  disabled = false;
  /** Whether ripples are disabled on the menu item. */
  disableRipple = false;
  /** Stream that emits when the menu item is hovered. */
  _hovered = new Subject();
  /** Stream that emits when the menu item is focused. */
  _focused = new Subject();
  /** Whether the menu item is highlighted. */
  _highlighted = false;
  /** Whether the menu item acts as a trigger for a sub-menu. */
  _triggersSubmenu = false;
  constructor() {
    inject(_CdkPrivateStyleLoader).load(_StructuralStylesLoader);
    this._parentMenu?.addItem?.(this);
  }
  /** Focuses the menu item. */
  focus(origin, options) {
    if (this._focusMonitor && origin) {
      this._focusMonitor.focusVia(this._getHostElement(), origin, options);
    } else {
      this._getHostElement().focus(options);
    }
    this._focused.next(this);
  }
  ngAfterViewInit() {
    if (this._focusMonitor) {
      this._focusMonitor.monitor(this._elementRef, false);
    }
  }
  ngOnDestroy() {
    if (this._focusMonitor) {
      this._focusMonitor.stopMonitoring(this._elementRef);
    }
    if (this._parentMenu && this._parentMenu.removeItem) {
      this._parentMenu.removeItem(this);
    }
    this._hovered.complete();
    this._focused.complete();
  }
  /** Used to set the `tabindex`. */
  _getTabIndex() {
    return this.disabled ? "-1" : "0";
  }
  /** Returns the host DOM element. */
  _getHostElement() {
    return this._elementRef.nativeElement;
  }
  /** Prevents the default element actions if it is disabled. */
  _checkDisabled(event) {
    if (this.disabled) {
      event.preventDefault();
      event.stopPropagation();
    }
  }
  /** Emits to the hover stream. */
  _handleMouseEnter() {
    this._hovered.next(this);
  }
  /** Gets the label to be used when determining whether the option should be focused. */
  getLabel() {
    const clone = this._elementRef.nativeElement.cloneNode(true);
    const icons = clone.querySelectorAll("mat-icon, .material-icons");
    for (let i = 0; i < icons.length; i++) {
      icons[i].remove();
    }
    return clone.textContent?.trim() || "";
  }
  _setHighlighted(isHighlighted) {
    this._highlighted = isHighlighted;
    this._changeDetectorRef.markForCheck();
  }
  _setTriggersSubmenu(triggersSubmenu) {
    this._triggersSubmenu = triggersSubmenu;
    this._changeDetectorRef.markForCheck();
  }
  _hasFocus() {
    return this._document && this._document.activeElement === this._getHostElement();
  }
  static \u0275fac = function MatMenuItem_Factory(__ngFactoryType__) {
    return new (__ngFactoryType__ || _MatMenuItem)();
  };
  static \u0275cmp = /* @__PURE__ */ \u0275\u0275defineComponent({
    type: _MatMenuItem,
    selectors: [["", "mat-menu-item", ""]],
    hostAttrs: [1, "mat-mdc-menu-item", "mat-focus-indicator"],
    hostVars: 8,
    hostBindings: function MatMenuItem_HostBindings(rf, ctx) {
      if (rf & 1) {
        \u0275\u0275listener("click", function MatMenuItem_click_HostBindingHandler($event) {
          return ctx._checkDisabled($event);
        })("mouseenter", function MatMenuItem_mouseenter_HostBindingHandler() {
          return ctx._handleMouseEnter();
        });
      }
      if (rf & 2) {
        \u0275\u0275attribute("role", ctx.role)("tabindex", ctx._getTabIndex())("aria-disabled", ctx.disabled)("disabled", ctx.disabled || null);
        \u0275\u0275classProp("mat-mdc-menu-item-highlighted", ctx._highlighted)("mat-mdc-menu-item-submenu-trigger", ctx._triggersSubmenu);
      }
    },
    inputs: {
      role: "role",
      disabled: [2, "disabled", "disabled", booleanAttribute],
      disableRipple: [2, "disableRipple", "disableRipple", booleanAttribute]
    },
    exportAs: ["matMenuItem"],
    attrs: _c0,
    ngContentSelectors: _c2,
    decls: 5,
    vars: 3,
    consts: [[1, "mat-mdc-menu-item-text"], ["matRipple", "", 1, "mat-mdc-menu-ripple", 3, "matRippleDisabled", "matRippleTrigger"], ["viewBox", "0 0 5 10", "focusable", "false", "aria-hidden", "true", 1, "mat-mdc-menu-submenu-icon"], ["points", "0,0 5,5 0,10"]],
    template: function MatMenuItem_Template(rf, ctx) {
      if (rf & 1) {
        \u0275\u0275projectionDef(_c1);
        \u0275\u0275projection(0);
        \u0275\u0275elementStart(1, "span", 0);
        \u0275\u0275projection(2, 1);
        \u0275\u0275elementEnd();
        \u0275\u0275element(3, "div", 1);
        \u0275\u0275template(4, MatMenuItem_Conditional_4_Template, 2, 0, ":svg:svg", 2);
      }
      if (rf & 2) {
        \u0275\u0275advance(3);
        \u0275\u0275property("matRippleDisabled", ctx.disableRipple || ctx.disabled)("matRippleTrigger", ctx._getHostElement());
        \u0275\u0275advance();
        \u0275\u0275conditional(ctx._triggersSubmenu ? 4 : -1);
      }
    },
    dependencies: [MatRipple],
    encapsulation: 2,
    changeDetection: 0
  });
};
(() => {
  (typeof ngDevMode === "undefined" || ngDevMode) && setClassMetadata(MatMenuItem, [{
    type: Component,
    args: [{
      selector: "[mat-menu-item]",
      exportAs: "matMenuItem",
      host: {
        "[attr.role]": "role",
        "class": "mat-mdc-menu-item mat-focus-indicator",
        "[class.mat-mdc-menu-item-highlighted]": "_highlighted",
        "[class.mat-mdc-menu-item-submenu-trigger]": "_triggersSubmenu",
        "[attr.tabindex]": "_getTabIndex()",
        "[attr.aria-disabled]": "disabled",
        "[attr.disabled]": "disabled || null",
        "(click)": "_checkDisabled($event)",
        "(mouseenter)": "_handleMouseEnter()"
      },
      changeDetection: ChangeDetectionStrategy.OnPush,
      encapsulation: ViewEncapsulation.None,
      imports: [MatRipple],
      template: '<ng-content select="mat-icon, [matMenuItemIcon]"></ng-content>\n<span class="mat-mdc-menu-item-text"><ng-content></ng-content></span>\n<div class="mat-mdc-menu-ripple" matRipple\n     [matRippleDisabled]="disableRipple || disabled"\n     [matRippleTrigger]="_getHostElement()">\n</div>\n\n@if (_triggersSubmenu) {\n     <svg\n       class="mat-mdc-menu-submenu-icon"\n       viewBox="0 0 5 10"\n       focusable="false"\n       aria-hidden="true"><polygon points="0,0 5,5 0,10"/></svg>\n}\n'
    }]
  }], () => [], {
    role: [{
      type: Input
    }],
    disabled: [{
      type: Input,
      args: [{
        transform: booleanAttribute
      }]
    }],
    disableRipple: [{
      type: Input,
      args: [{
        transform: booleanAttribute
      }]
    }]
  });
})();
function throwMatMenuInvalidPositionX() {
  throw Error(`xPosition value must be either 'before' or after'.
      Example: <mat-menu xPosition="before" #menu="matMenu"></mat-menu>`);
}
function throwMatMenuInvalidPositionY() {
  throw Error(`yPosition value must be either 'above' or below'.
      Example: <mat-menu yPosition="above" #menu="matMenu"></mat-menu>`);
}
function throwMatMenuRecursiveError() {
  throw Error(`matMenuTriggerFor: menu cannot contain its own trigger. Assign a menu that is not a parent of the trigger or move the trigger outside of the menu.`);
}
var MAT_MENU_CONTENT = new InjectionToken("MatMenuContent");
var MatMenuContent = class _MatMenuContent {
  _template = inject(TemplateRef);
  _appRef = inject(ApplicationRef);
  _injector = inject(Injector);
  _viewContainerRef = inject(ViewContainerRef);
  _document = inject(DOCUMENT);
  _changeDetectorRef = inject(ChangeDetectorRef);
  _portal;
  _outlet;
  /** Emits when the menu content has been attached. */
  _attached = new Subject();
  constructor() {
  }
  /**
   * Attaches the content with a particular context.
   * @docs-private
   */
  attach(context = {}) {
    if (!this._portal) {
      this._portal = new TemplatePortal(this._template, this._viewContainerRef);
    }
    this.detach();
    if (!this._outlet) {
      this._outlet = new DomPortalOutlet(this._document.createElement("div"), null, this._appRef, this._injector);
    }
    const element = this._template.elementRef.nativeElement;
    element.parentNode.insertBefore(this._outlet.outletElement, element);
    this._changeDetectorRef.markForCheck();
    this._portal.attach(this._outlet, context);
    this._attached.next();
  }
  /**
   * Detaches the content.
   * @docs-private
   */
  detach() {
    if (this._portal?.isAttached) {
      this._portal.detach();
    }
  }
  ngOnDestroy() {
    this.detach();
    this._outlet?.dispose();
  }
  static \u0275fac = function MatMenuContent_Factory(__ngFactoryType__) {
    return new (__ngFactoryType__ || _MatMenuContent)();
  };
  static \u0275dir = /* @__PURE__ */ \u0275\u0275defineDirective({
    type: _MatMenuContent,
    selectors: [["ng-template", "matMenuContent", ""]],
    features: [\u0275\u0275ProvidersFeature([{
      provide: MAT_MENU_CONTENT,
      useExisting: _MatMenuContent
    }])]
  });
};
(() => {
  (typeof ngDevMode === "undefined" || ngDevMode) && setClassMetadata(MatMenuContent, [{
    type: Directive,
    args: [{
      selector: "ng-template[matMenuContent]",
      providers: [{
        provide: MAT_MENU_CONTENT,
        useExisting: MatMenuContent
      }]
    }]
  }], () => [], null);
})();
var MAT_MENU_DEFAULT_OPTIONS = new InjectionToken("mat-menu-default-options", {
  providedIn: "root",
  factory: MAT_MENU_DEFAULT_OPTIONS_FACTORY
});
function MAT_MENU_DEFAULT_OPTIONS_FACTORY() {
  return {
    overlapTrigger: false,
    xPosition: "after",
    yPosition: "below",
    backdropClass: "cdk-overlay-transparent-backdrop"
  };
}
var ENTER_ANIMATION = "_mat-menu-enter";
var EXIT_ANIMATION = "_mat-menu-exit";
var MatMenu = class _MatMenu {
  _elementRef = inject(ElementRef);
  _changeDetectorRef = inject(ChangeDetectorRef);
  _injector = inject(Injector);
  _keyManager;
  _xPosition;
  _yPosition;
  _firstItemFocusRef;
  _exitFallbackTimeout;
  /** Whether animations are currently disabled. */
  _animationsDisabled;
  /** All items inside the menu. Includes items nested inside another menu. */
  _allItems;
  /** Only the direct descendant menu items. */
  _directDescendantItems = new QueryList();
  /** Classes to be applied to the menu panel. */
  _classList = {};
  /** Current state of the panel animation. */
  _panelAnimationState = "void";
  /** Emits whenever an animation on the menu completes. */
  _animationDone = new Subject();
  /** Whether the menu is animating. */
  _isAnimating = false;
  /** Parent menu of the current menu panel. */
  parentMenu;
  /** Layout direction of the menu. */
  direction;
  /** Class or list of classes to be added to the overlay panel. */
  overlayPanelClass;
  /** Class to be added to the backdrop element. */
  backdropClass;
  /** aria-label for the menu panel. */
  ariaLabel;
  /** aria-labelledby for the menu panel. */
  ariaLabelledby;
  /** aria-describedby for the menu panel. */
  ariaDescribedby;
  /** Position of the menu in the X axis. */
  get xPosition() {
    return this._xPosition;
  }
  set xPosition(value) {
    if (value !== "before" && value !== "after" && (typeof ngDevMode === "undefined" || ngDevMode)) {
      throwMatMenuInvalidPositionX();
    }
    this._xPosition = value;
    this.setPositionClasses();
  }
  /** Position of the menu in the Y axis. */
  get yPosition() {
    return this._yPosition;
  }
  set yPosition(value) {
    if (value !== "above" && value !== "below" && (typeof ngDevMode === "undefined" || ngDevMode)) {
      throwMatMenuInvalidPositionY();
    }
    this._yPosition = value;
    this.setPositionClasses();
  }
  /** @docs-private */
  templateRef;
  /**
   * List of the items inside of a menu.
   * @deprecated
   * @breaking-change 8.0.0
   */
  items;
  /**
   * Menu content that will be rendered lazily.
   * @docs-private
   */
  lazyContent;
  /** Whether the menu should overlap its trigger. */
  overlapTrigger;
  /** Whether the menu has a backdrop. */
  hasBackdrop;
  /**
   * This method takes classes set on the host mat-menu element and applies them on the
   * menu template that displays in the overlay container.  Otherwise, it's difficult
   * to style the containing menu from outside the component.
   * @param classes list of class names
   */
  set panelClass(classes) {
    const previousPanelClass = this._previousPanelClass;
    const newClassList = __spreadValues({}, this._classList);
    if (previousPanelClass && previousPanelClass.length) {
      previousPanelClass.split(" ").forEach((className) => {
        newClassList[className] = false;
      });
    }
    this._previousPanelClass = classes;
    if (classes && classes.length) {
      classes.split(" ").forEach((className) => {
        newClassList[className] = true;
      });
      this._elementRef.nativeElement.className = "";
    }
    this._classList = newClassList;
  }
  _previousPanelClass;
  /**
   * This method takes classes set on the host mat-menu element and applies them on the
   * menu template that displays in the overlay container.  Otherwise, it's difficult
   * to style the containing menu from outside the component.
   * @deprecated Use `panelClass` instead.
   * @breaking-change 8.0.0
   */
  get classList() {
    return this.panelClass;
  }
  set classList(classes) {
    this.panelClass = classes;
  }
  /** Event emitted when the menu is closed. */
  closed = new EventEmitter();
  /**
   * Event emitted when the menu is closed.
   * @deprecated Switch to `closed` instead
   * @breaking-change 8.0.0
   */
  close = this.closed;
  panelId = inject(_IdGenerator).getId("mat-menu-panel-");
  constructor() {
    const defaultOptions = inject(MAT_MENU_DEFAULT_OPTIONS);
    this.overlayPanelClass = defaultOptions.overlayPanelClass || "";
    this._xPosition = defaultOptions.xPosition;
    this._yPosition = defaultOptions.yPosition;
    this.backdropClass = defaultOptions.backdropClass;
    this.overlapTrigger = defaultOptions.overlapTrigger;
    this.hasBackdrop = defaultOptions.hasBackdrop;
    this._animationsDisabled = inject(ANIMATION_MODULE_TYPE, {
      optional: true
    }) === "NoopAnimations";
  }
  ngOnInit() {
    this.setPositionClasses();
  }
  ngAfterContentInit() {
    this._updateDirectDescendants();
    this._keyManager = new FocusKeyManager(this._directDescendantItems).withWrap().withTypeAhead().withHomeAndEnd();
    this._keyManager.tabOut.subscribe(() => this.closed.emit("tab"));
    this._directDescendantItems.changes.pipe(startWith(this._directDescendantItems), switchMap((items) => merge(...items.map((item) => item._focused)))).subscribe((focusedItem) => this._keyManager.updateActiveItem(focusedItem));
    this._directDescendantItems.changes.subscribe((itemsList) => {
      const manager = this._keyManager;
      if (this._panelAnimationState === "enter" && manager.activeItem?._hasFocus()) {
        const items = itemsList.toArray();
        const index = Math.max(0, Math.min(items.length - 1, manager.activeItemIndex || 0));
        if (items[index] && !items[index].disabled) {
          manager.setActiveItem(index);
        } else {
          manager.setNextItemActive();
        }
      }
    });
  }
  ngOnDestroy() {
    this._keyManager?.destroy();
    this._directDescendantItems.destroy();
    this.closed.complete();
    this._firstItemFocusRef?.destroy();
    clearTimeout(this._exitFallbackTimeout);
  }
  /** Stream that emits whenever the hovered menu item changes. */
  _hovered() {
    const itemChanges = this._directDescendantItems.changes;
    return itemChanges.pipe(startWith(this._directDescendantItems), switchMap((items) => merge(...items.map((item) => item._hovered))));
  }
  /*
   * Registers a menu item with the menu.
   * @docs-private
   * @deprecated No longer being used. To be removed.
   * @breaking-change 9.0.0
   */
  addItem(_item) {
  }
  /**
   * Removes an item from the menu.
   * @docs-private
   * @deprecated No longer being used. To be removed.
   * @breaking-change 9.0.0
   */
  removeItem(_item) {
  }
  /** Handle a keyboard event from the menu, delegating to the appropriate action. */
  _handleKeydown(event) {
    const keyCode = event.keyCode;
    const manager = this._keyManager;
    switch (keyCode) {
      case ESCAPE:
        if (!hasModifierKey(event)) {
          event.preventDefault();
          this.closed.emit("keydown");
        }
        break;
      case LEFT_ARROW:
        if (this.parentMenu && this.direction === "ltr") {
          this.closed.emit("keydown");
        }
        break;
      case RIGHT_ARROW:
        if (this.parentMenu && this.direction === "rtl") {
          this.closed.emit("keydown");
        }
        break;
      default:
        if (keyCode === UP_ARROW || keyCode === DOWN_ARROW) {
          manager.setFocusOrigin("keyboard");
        }
        manager.onKeydown(event);
        return;
    }
  }
  /**
   * Focus the first item in the menu.
   * @param origin Action from which the focus originated. Used to set the correct styling.
   */
  focusFirstItem(origin = "program") {
    this._firstItemFocusRef?.destroy();
    this._firstItemFocusRef = afterNextRender(() => {
      const menuPanel = this._resolvePanel();
      if (!menuPanel || !menuPanel.contains(document.activeElement)) {
        const manager = this._keyManager;
        manager.setFocusOrigin(origin).setFirstItemActive();
        if (!manager.activeItem && menuPanel) {
          menuPanel.focus();
        }
      }
    }, {
      injector: this._injector
    });
  }
  /**
   * Resets the active item in the menu. This is used when the menu is opened, allowing
   * the user to start from the first option when pressing the down arrow.
   */
  resetActiveItem() {
    this._keyManager.setActiveItem(-1);
  }
  /**
   * @deprecated No longer used and will be removed.
   * @breaking-change 21.0.0
   */
  setElevation(_depth) {
  }
  /**
   * Adds classes to the menu panel based on its position. Can be used by
   * consumers to add specific styling based on the position.
   * @param posX Position of the menu along the x axis.
   * @param posY Position of the menu along the y axis.
   * @docs-private
   */
  setPositionClasses(posX = this.xPosition, posY = this.yPosition) {
    this._classList = __spreadProps(__spreadValues({}, this._classList), {
      ["mat-menu-before"]: posX === "before",
      ["mat-menu-after"]: posX === "after",
      ["mat-menu-above"]: posY === "above",
      ["mat-menu-below"]: posY === "below"
    });
    this._changeDetectorRef.markForCheck();
  }
  /** Callback that is invoked when the panel animation completes. */
  _onAnimationDone(state) {
    const isExit = state === EXIT_ANIMATION;
    if (isExit || state === ENTER_ANIMATION) {
      if (isExit) {
        clearTimeout(this._exitFallbackTimeout);
        this._exitFallbackTimeout = void 0;
      }
      this._animationDone.next(isExit ? "void" : "enter");
      this._isAnimating = false;
    }
  }
  _onAnimationStart(state) {
    if (state === ENTER_ANIMATION || state === EXIT_ANIMATION) {
      this._isAnimating = true;
    }
  }
  _setIsOpen(isOpen) {
    this._panelAnimationState = isOpen ? "enter" : "void";
    if (isOpen) {
      if (this._keyManager.activeItemIndex === 0) {
        const menuPanel = this._resolvePanel();
        if (menuPanel) {
          menuPanel.scrollTop = 0;
        }
      }
    } else if (!this._animationsDisabled) {
      this._exitFallbackTimeout = setTimeout(() => this._onAnimationDone(EXIT_ANIMATION), 200);
    }
    if (this._animationsDisabled) {
      setTimeout(() => {
        this._onAnimationDone(isOpen ? ENTER_ANIMATION : EXIT_ANIMATION);
      });
    }
    this._changeDetectorRef.markForCheck();
  }
  /**
   * Sets up a stream that will keep track of any newly-added menu items and will update the list
   * of direct descendants. We collect the descendants this way, because `_allItems` can include
   * items that are part of child menus, and using a custom way of registering items is unreliable
   * when it comes to maintaining the item order.
   */
  _updateDirectDescendants() {
    this._allItems.changes.pipe(startWith(this._allItems)).subscribe((items) => {
      this._directDescendantItems.reset(items.filter((item) => item._parentMenu === this));
      this._directDescendantItems.notifyOnChanges();
    });
  }
  /** Gets the menu panel DOM node. */
  _resolvePanel() {
    let menuPanel = null;
    if (this._directDescendantItems.length) {
      menuPanel = this._directDescendantItems.first._getHostElement().closest('[role="menu"]');
    }
    return menuPanel;
  }
  static \u0275fac = function MatMenu_Factory(__ngFactoryType__) {
    return new (__ngFactoryType__ || _MatMenu)();
  };
  static \u0275cmp = /* @__PURE__ */ \u0275\u0275defineComponent({
    type: _MatMenu,
    selectors: [["mat-menu"]],
    contentQueries: function MatMenu_ContentQueries(rf, ctx, dirIndex) {
      if (rf & 1) {
        \u0275\u0275contentQuery(dirIndex, MAT_MENU_CONTENT, 5);
        \u0275\u0275contentQuery(dirIndex, MatMenuItem, 5);
        \u0275\u0275contentQuery(dirIndex, MatMenuItem, 4);
      }
      if (rf & 2) {
        let _t;
        \u0275\u0275queryRefresh(_t = \u0275\u0275loadQuery()) && (ctx.lazyContent = _t.first);
        \u0275\u0275queryRefresh(_t = \u0275\u0275loadQuery()) && (ctx._allItems = _t);
        \u0275\u0275queryRefresh(_t = \u0275\u0275loadQuery()) && (ctx.items = _t);
      }
    },
    viewQuery: function MatMenu_Query(rf, ctx) {
      if (rf & 1) {
        \u0275\u0275viewQuery(TemplateRef, 5);
      }
      if (rf & 2) {
        let _t;
        \u0275\u0275queryRefresh(_t = \u0275\u0275loadQuery()) && (ctx.templateRef = _t.first);
      }
    },
    hostVars: 3,
    hostBindings: function MatMenu_HostBindings(rf, ctx) {
      if (rf & 2) {
        \u0275\u0275attribute("aria-label", null)("aria-labelledby", null)("aria-describedby", null);
      }
    },
    inputs: {
      backdropClass: "backdropClass",
      ariaLabel: [0, "aria-label", "ariaLabel"],
      ariaLabelledby: [0, "aria-labelledby", "ariaLabelledby"],
      ariaDescribedby: [0, "aria-describedby", "ariaDescribedby"],
      xPosition: "xPosition",
      yPosition: "yPosition",
      overlapTrigger: [2, "overlapTrigger", "overlapTrigger", booleanAttribute],
      hasBackdrop: [2, "hasBackdrop", "hasBackdrop", (value) => value == null ? null : booleanAttribute(value)],
      panelClass: [0, "class", "panelClass"],
      classList: "classList"
    },
    outputs: {
      closed: "closed",
      close: "close"
    },
    exportAs: ["matMenu"],
    features: [\u0275\u0275ProvidersFeature([{
      provide: MAT_MENU_PANEL,
      useExisting: _MatMenu
    }])],
    ngContentSelectors: _c3,
    decls: 1,
    vars: 0,
    consts: [["tabindex", "-1", "role", "menu", 1, "mat-mdc-menu-panel", 3, "click", "animationstart", "animationend", "animationcancel", "id"], [1, "mat-mdc-menu-content"]],
    template: function MatMenu_Template(rf, ctx) {
      if (rf & 1) {
        \u0275\u0275projectionDef();
        \u0275\u0275template(0, MatMenu_ng_template_0_Template, 3, 12, "ng-template");
      }
    },
    styles: ['mat-menu{display:none}.mat-mdc-menu-content{margin:0;padding:8px 0;outline:0}.mat-mdc-menu-content,.mat-mdc-menu-content .mat-mdc-menu-item .mat-mdc-menu-item-text{-moz-osx-font-smoothing:grayscale;-webkit-font-smoothing:antialiased;flex:1;white-space:normal;font-family:var(--mat-menu-item-label-text-font, var(--mat-sys-label-large-font));line-height:var(--mat-menu-item-label-text-line-height, var(--mat-sys-label-large-line-height));font-size:var(--mat-menu-item-label-text-size, var(--mat-sys-label-large-size));letter-spacing:var(--mat-menu-item-label-text-tracking, var(--mat-sys-label-large-tracking));font-weight:var(--mat-menu-item-label-text-weight, var(--mat-sys-label-large-weight))}@keyframes _mat-menu-enter{from{opacity:0;transform:scale(0.8)}to{opacity:1;transform:none}}@keyframes _mat-menu-exit{from{opacity:1}to{opacity:0}}.mat-mdc-menu-panel{min-width:112px;max-width:280px;overflow:auto;box-sizing:border-box;outline:0;animation:_mat-menu-enter 120ms cubic-bezier(0, 0, 0.2, 1);border-radius:var(--mat-menu-container-shape, var(--mat-sys-corner-extra-small));background-color:var(--mat-menu-container-color, var(--mat-sys-surface-container));box-shadow:var(--mat-menu-container-elevation-shadow, 0px 3px 1px -2px rgba(0, 0, 0, 0.2), 0px 2px 2px 0px rgba(0, 0, 0, 0.14), 0px 1px 5px 0px rgba(0, 0, 0, 0.12));will-change:transform,opacity}.mat-mdc-menu-panel.mat-menu-panel-exit-animation{animation:_mat-menu-exit 100ms 25ms linear forwards}.mat-mdc-menu-panel.mat-menu-panel-animations-disabled{animation:none}.mat-mdc-menu-panel.mat-menu-panel-animating{pointer-events:none}.mat-mdc-menu-panel.mat-menu-panel-animating:has(.mat-mdc-menu-content:empty){display:none}@media(forced-colors: active){.mat-mdc-menu-panel{outline:solid 1px}}.mat-mdc-menu-panel .mat-divider{color:var(--mat-menu-divider-color, var(--mat-sys-surface-variant));margin-bottom:var(--mat-menu-divider-bottom-spacing, 8px);margin-top:var(--mat-menu-divider-top-spacing, 8px)}.mat-mdc-menu-item{display:flex;position:relative;align-items:center;justify-content:flex-start;overflow:hidden;padding:0;cursor:pointer;width:100%;text-align:left;box-sizing:border-box;color:inherit;font-size:inherit;background:none;text-decoration:none;margin:0;min-height:48px;padding-left:var(--mat-menu-item-leading-spacing, 12px);padding-right:var(--mat-menu-item-trailing-spacing, 12px);-webkit-user-select:none;user-select:none;cursor:pointer;outline:none;border:none;-webkit-tap-highlight-color:rgba(0,0,0,0)}.mat-mdc-menu-item::-moz-focus-inner{border:0}[dir=rtl] .mat-mdc-menu-item{padding-left:var(--mat-menu-item-trailing-spacing, 12px);padding-right:var(--mat-menu-item-leading-spacing, 12px)}.mat-mdc-menu-item:has(.material-icons,mat-icon,[matButtonIcon]){padding-left:var(--mat-menu-item-with-icon-leading-spacing, 12px);padding-right:var(--mat-menu-item-with-icon-trailing-spacing, 12px)}[dir=rtl] .mat-mdc-menu-item:has(.material-icons,mat-icon,[matButtonIcon]){padding-left:var(--mat-menu-item-with-icon-trailing-spacing, 12px);padding-right:var(--mat-menu-item-with-icon-leading-spacing, 12px)}.mat-mdc-menu-item,.mat-mdc-menu-item:visited,.mat-mdc-menu-item:link{color:var(--mat-menu-item-label-text-color, var(--mat-sys-on-surface))}.mat-mdc-menu-item .mat-icon-no-color,.mat-mdc-menu-item .mat-mdc-menu-submenu-icon{color:var(--mat-menu-item-icon-color, var(--mat-sys-on-surface-variant))}.mat-mdc-menu-item[disabled]{cursor:default;opacity:.38}.mat-mdc-menu-item[disabled]::after{display:block;position:absolute;content:"";top:0;left:0;bottom:0;right:0}.mat-mdc-menu-item:focus{outline:0}.mat-mdc-menu-item .mat-icon{flex-shrink:0;margin-right:var(--mat-menu-item-spacing, 12px);height:var(--mat-menu-item-icon-size, 24px);width:var(--mat-menu-item-icon-size, 24px)}[dir=rtl] .mat-mdc-menu-item{text-align:right}[dir=rtl] .mat-mdc-menu-item .mat-icon{margin-right:0;margin-left:var(--mat-menu-item-spacing, 12px)}.mat-mdc-menu-item:not([disabled]):hover{background-color:var(--mat-menu-item-hover-state-layer-color, color-mix(in srgb, var(--mat-sys-on-surface) calc(var(--mat-sys-hover-state-layer-opacity) * 100%), transparent))}.mat-mdc-menu-item:not([disabled]).cdk-program-focused,.mat-mdc-menu-item:not([disabled]).cdk-keyboard-focused,.mat-mdc-menu-item:not([disabled]).mat-mdc-menu-item-highlighted{background-color:var(--mat-menu-item-focus-state-layer-color, color-mix(in srgb, var(--mat-sys-on-surface) calc(var(--mat-sys-focus-state-layer-opacity) * 100%), transparent))}@media(forced-colors: active){.mat-mdc-menu-item{margin-top:1px}}.mat-mdc-menu-submenu-icon{width:var(--mat-menu-item-icon-size, 24px);height:10px;fill:currentColor;padding-left:var(--mat-menu-item-spacing, 12px)}[dir=rtl] .mat-mdc-menu-submenu-icon{padding-right:var(--mat-menu-item-spacing, 12px);padding-left:0}[dir=rtl] .mat-mdc-menu-submenu-icon polygon{transform:scaleX(-1);transform-origin:center}@media(forced-colors: active){.mat-mdc-menu-submenu-icon{fill:CanvasText}}.mat-mdc-menu-item .mat-mdc-menu-ripple{top:0;left:0;right:0;bottom:0;position:absolute;pointer-events:none}\n'],
    encapsulation: 2,
    changeDetection: 0
  });
};
(() => {
  (typeof ngDevMode === "undefined" || ngDevMode) && setClassMetadata(MatMenu, [{
    type: Component,
    args: [{
      selector: "mat-menu",
      changeDetection: ChangeDetectionStrategy.OnPush,
      encapsulation: ViewEncapsulation.None,
      exportAs: "matMenu",
      host: {
        "[attr.aria-label]": "null",
        "[attr.aria-labelledby]": "null",
        "[attr.aria-describedby]": "null"
      },
      providers: [{
        provide: MAT_MENU_PANEL,
        useExisting: MatMenu
      }],
      template: `<ng-template>
  <div
    class="mat-mdc-menu-panel"
    [id]="panelId"
    [class]="_classList"
    [class.mat-menu-panel-animations-disabled]="_animationsDisabled"
    [class.mat-menu-panel-exit-animation]="_panelAnimationState === 'void'"
    [class.mat-menu-panel-animating]="_isAnimating"
    (click)="closed.emit('click')"
    tabindex="-1"
    role="menu"
    (animationstart)="_onAnimationStart($event.animationName)"
    (animationend)="_onAnimationDone($event.animationName)"
    (animationcancel)="_onAnimationDone($event.animationName)"
    [attr.aria-label]="ariaLabel || null"
    [attr.aria-labelledby]="ariaLabelledby || null"
    [attr.aria-describedby]="ariaDescribedby || null">
    <div class="mat-mdc-menu-content">
      <ng-content></ng-content>
    </div>
  </div>
</ng-template>
`,
      styles: ['mat-menu{display:none}.mat-mdc-menu-content{margin:0;padding:8px 0;outline:0}.mat-mdc-menu-content,.mat-mdc-menu-content .mat-mdc-menu-item .mat-mdc-menu-item-text{-moz-osx-font-smoothing:grayscale;-webkit-font-smoothing:antialiased;flex:1;white-space:normal;font-family:var(--mat-menu-item-label-text-font, var(--mat-sys-label-large-font));line-height:var(--mat-menu-item-label-text-line-height, var(--mat-sys-label-large-line-height));font-size:var(--mat-menu-item-label-text-size, var(--mat-sys-label-large-size));letter-spacing:var(--mat-menu-item-label-text-tracking, var(--mat-sys-label-large-tracking));font-weight:var(--mat-menu-item-label-text-weight, var(--mat-sys-label-large-weight))}@keyframes _mat-menu-enter{from{opacity:0;transform:scale(0.8)}to{opacity:1;transform:none}}@keyframes _mat-menu-exit{from{opacity:1}to{opacity:0}}.mat-mdc-menu-panel{min-width:112px;max-width:280px;overflow:auto;box-sizing:border-box;outline:0;animation:_mat-menu-enter 120ms cubic-bezier(0, 0, 0.2, 1);border-radius:var(--mat-menu-container-shape, var(--mat-sys-corner-extra-small));background-color:var(--mat-menu-container-color, var(--mat-sys-surface-container));box-shadow:var(--mat-menu-container-elevation-shadow, 0px 3px 1px -2px rgba(0, 0, 0, 0.2), 0px 2px 2px 0px rgba(0, 0, 0, 0.14), 0px 1px 5px 0px rgba(0, 0, 0, 0.12));will-change:transform,opacity}.mat-mdc-menu-panel.mat-menu-panel-exit-animation{animation:_mat-menu-exit 100ms 25ms linear forwards}.mat-mdc-menu-panel.mat-menu-panel-animations-disabled{animation:none}.mat-mdc-menu-panel.mat-menu-panel-animating{pointer-events:none}.mat-mdc-menu-panel.mat-menu-panel-animating:has(.mat-mdc-menu-content:empty){display:none}@media(forced-colors: active){.mat-mdc-menu-panel{outline:solid 1px}}.mat-mdc-menu-panel .mat-divider{color:var(--mat-menu-divider-color, var(--mat-sys-surface-variant));margin-bottom:var(--mat-menu-divider-bottom-spacing, 8px);margin-top:var(--mat-menu-divider-top-spacing, 8px)}.mat-mdc-menu-item{display:flex;position:relative;align-items:center;justify-content:flex-start;overflow:hidden;padding:0;cursor:pointer;width:100%;text-align:left;box-sizing:border-box;color:inherit;font-size:inherit;background:none;text-decoration:none;margin:0;min-height:48px;padding-left:var(--mat-menu-item-leading-spacing, 12px);padding-right:var(--mat-menu-item-trailing-spacing, 12px);-webkit-user-select:none;user-select:none;cursor:pointer;outline:none;border:none;-webkit-tap-highlight-color:rgba(0,0,0,0)}.mat-mdc-menu-item::-moz-focus-inner{border:0}[dir=rtl] .mat-mdc-menu-item{padding-left:var(--mat-menu-item-trailing-spacing, 12px);padding-right:var(--mat-menu-item-leading-spacing, 12px)}.mat-mdc-menu-item:has(.material-icons,mat-icon,[matButtonIcon]){padding-left:var(--mat-menu-item-with-icon-leading-spacing, 12px);padding-right:var(--mat-menu-item-with-icon-trailing-spacing, 12px)}[dir=rtl] .mat-mdc-menu-item:has(.material-icons,mat-icon,[matButtonIcon]){padding-left:var(--mat-menu-item-with-icon-trailing-spacing, 12px);padding-right:var(--mat-menu-item-with-icon-leading-spacing, 12px)}.mat-mdc-menu-item,.mat-mdc-menu-item:visited,.mat-mdc-menu-item:link{color:var(--mat-menu-item-label-text-color, var(--mat-sys-on-surface))}.mat-mdc-menu-item .mat-icon-no-color,.mat-mdc-menu-item .mat-mdc-menu-submenu-icon{color:var(--mat-menu-item-icon-color, var(--mat-sys-on-surface-variant))}.mat-mdc-menu-item[disabled]{cursor:default;opacity:.38}.mat-mdc-menu-item[disabled]::after{display:block;position:absolute;content:"";top:0;left:0;bottom:0;right:0}.mat-mdc-menu-item:focus{outline:0}.mat-mdc-menu-item .mat-icon{flex-shrink:0;margin-right:var(--mat-menu-item-spacing, 12px);height:var(--mat-menu-item-icon-size, 24px);width:var(--mat-menu-item-icon-size, 24px)}[dir=rtl] .mat-mdc-menu-item{text-align:right}[dir=rtl] .mat-mdc-menu-item .mat-icon{margin-right:0;margin-left:var(--mat-menu-item-spacing, 12px)}.mat-mdc-menu-item:not([disabled]):hover{background-color:var(--mat-menu-item-hover-state-layer-color, color-mix(in srgb, var(--mat-sys-on-surface) calc(var(--mat-sys-hover-state-layer-opacity) * 100%), transparent))}.mat-mdc-menu-item:not([disabled]).cdk-program-focused,.mat-mdc-menu-item:not([disabled]).cdk-keyboard-focused,.mat-mdc-menu-item:not([disabled]).mat-mdc-menu-item-highlighted{background-color:var(--mat-menu-item-focus-state-layer-color, color-mix(in srgb, var(--mat-sys-on-surface) calc(var(--mat-sys-focus-state-layer-opacity) * 100%), transparent))}@media(forced-colors: active){.mat-mdc-menu-item{margin-top:1px}}.mat-mdc-menu-submenu-icon{width:var(--mat-menu-item-icon-size, 24px);height:10px;fill:currentColor;padding-left:var(--mat-menu-item-spacing, 12px)}[dir=rtl] .mat-mdc-menu-submenu-icon{padding-right:var(--mat-menu-item-spacing, 12px);padding-left:0}[dir=rtl] .mat-mdc-menu-submenu-icon polygon{transform:scaleX(-1);transform-origin:center}@media(forced-colors: active){.mat-mdc-menu-submenu-icon{fill:CanvasText}}.mat-mdc-menu-item .mat-mdc-menu-ripple{top:0;left:0;right:0;bottom:0;position:absolute;pointer-events:none}\n']
    }]
  }], () => [], {
    _allItems: [{
      type: ContentChildren,
      args: [MatMenuItem, {
        descendants: true
      }]
    }],
    backdropClass: [{
      type: Input
    }],
    ariaLabel: [{
      type: Input,
      args: ["aria-label"]
    }],
    ariaLabelledby: [{
      type: Input,
      args: ["aria-labelledby"]
    }],
    ariaDescribedby: [{
      type: Input,
      args: ["aria-describedby"]
    }],
    xPosition: [{
      type: Input
    }],
    yPosition: [{
      type: Input
    }],
    templateRef: [{
      type: ViewChild,
      args: [TemplateRef]
    }],
    items: [{
      type: ContentChildren,
      args: [MatMenuItem, {
        descendants: false
      }]
    }],
    lazyContent: [{
      type: ContentChild,
      args: [MAT_MENU_CONTENT]
    }],
    overlapTrigger: [{
      type: Input,
      args: [{
        transform: booleanAttribute
      }]
    }],
    hasBackdrop: [{
      type: Input,
      args: [{
        transform: (value) => value == null ? null : booleanAttribute(value)
      }]
    }],
    panelClass: [{
      type: Input,
      args: ["class"]
    }],
    classList: [{
      type: Input
    }],
    closed: [{
      type: Output
    }],
    close: [{
      type: Output
    }]
  });
})();
var MAT_MENU_SCROLL_STRATEGY = new InjectionToken("mat-menu-scroll-strategy", {
  providedIn: "root",
  factory: () => {
    const overlay = inject(Overlay);
    return () => overlay.scrollStrategies.reposition();
  }
});
function MAT_MENU_SCROLL_STRATEGY_FACTORY(overlay) {
  return () => overlay.scrollStrategies.reposition();
}
var MAT_MENU_SCROLL_STRATEGY_FACTORY_PROVIDER = {
  provide: MAT_MENU_SCROLL_STRATEGY,
  deps: [Overlay],
  useFactory: MAT_MENU_SCROLL_STRATEGY_FACTORY
};
var passiveEventListenerOptions = {
  passive: true
};
var PANELS_TO_TRIGGERS = /* @__PURE__ */ new WeakMap();
var MatMenuTrigger = class _MatMenuTrigger {
  _overlay = inject(Overlay);
  _element = inject(ElementRef);
  _viewContainerRef = inject(ViewContainerRef);
  _menuItemInstance = inject(MatMenuItem, {
    optional: true,
    self: true
  });
  _dir = inject(Directionality, {
    optional: true
  });
  _focusMonitor = inject(FocusMonitor);
  _ngZone = inject(NgZone);
  _scrollStrategy = inject(MAT_MENU_SCROLL_STRATEGY);
  _changeDetectorRef = inject(ChangeDetectorRef);
  _cleanupTouchstart;
  _portal;
  _overlayRef = null;
  _menuOpen = false;
  _closingActionsSubscription = Subscription.EMPTY;
  _hoverSubscription = Subscription.EMPTY;
  _menuCloseSubscription = Subscription.EMPTY;
  _pendingRemoval;
  /**
   * We're specifically looking for a `MatMenu` here since the generic `MatMenuPanel`
   * interface lacks some functionality around nested menus and animations.
   */
  _parentMaterialMenu;
  /**
   * Cached value of the padding of the parent menu panel.
   * Used to offset sub-menus to compensate for the padding.
   */
  _parentInnerPadding;
  // Tracking input type is necessary so it's possible to only auto-focus
  // the first item of the list when the menu is opened via the keyboard
  _openedBy = void 0;
  /**
   * @deprecated
   * @breaking-change 8.0.0
   */
  get _deprecatedMatMenuTriggerFor() {
    return this.menu;
  }
  set _deprecatedMatMenuTriggerFor(v) {
    this.menu = v;
  }
  /** References the menu instance that the trigger is associated with. */
  get menu() {
    return this._menu;
  }
  set menu(menu) {
    if (menu === this._menu) {
      return;
    }
    this._menu = menu;
    this._menuCloseSubscription.unsubscribe();
    if (menu) {
      if (menu === this._parentMaterialMenu && (typeof ngDevMode === "undefined" || ngDevMode)) {
        throwMatMenuRecursiveError();
      }
      this._menuCloseSubscription = menu.close.subscribe((reason) => {
        this._destroyMenu(reason);
        if ((reason === "click" || reason === "tab") && this._parentMaterialMenu) {
          this._parentMaterialMenu.closed.emit(reason);
        }
      });
    }
    this._menuItemInstance?._setTriggersSubmenu(this.triggersSubmenu());
  }
  _menu;
  /** Data to be passed along to any lazily-rendered content. */
  menuData;
  /**
   * Whether focus should be restored when the menu is closed.
   * Note that disabling this option can have accessibility implications
   * and it's up to you to manage focus, if you decide to turn it off.
   */
  restoreFocus = true;
  /** Event emitted when the associated menu is opened. */
  menuOpened = new EventEmitter();
  /**
   * Event emitted when the associated menu is opened.
   * @deprecated Switch to `menuOpened` instead
   * @breaking-change 8.0.0
   */
  // tslint:disable-next-line:no-output-on-prefix
  onMenuOpen = this.menuOpened;
  /** Event emitted when the associated menu is closed. */
  menuClosed = new EventEmitter();
  /**
   * Event emitted when the associated menu is closed.
   * @deprecated Switch to `menuClosed` instead
   * @breaking-change 8.0.0
   */
  // tslint:disable-next-line:no-output-on-prefix
  onMenuClose = this.menuClosed;
  constructor() {
    const parentMenu = inject(MAT_MENU_PANEL, {
      optional: true
    });
    const renderer = inject(Renderer2);
    this._parentMaterialMenu = parentMenu instanceof MatMenu ? parentMenu : void 0;
    this._cleanupTouchstart = _bindEventWithOptions(renderer, this._element.nativeElement, "touchstart", (event) => {
      if (!isFakeTouchstartFromScreenReader(event)) {
        this._openedBy = "touch";
      }
    }, passiveEventListenerOptions);
  }
  ngAfterContentInit() {
    this._handleHover();
  }
  ngOnDestroy() {
    if (this.menu && this._ownsMenu(this.menu)) {
      PANELS_TO_TRIGGERS.delete(this.menu);
    }
    this._cleanupTouchstart();
    this._pendingRemoval?.unsubscribe();
    this._menuCloseSubscription.unsubscribe();
    this._closingActionsSubscription.unsubscribe();
    this._hoverSubscription.unsubscribe();
    if (this._overlayRef) {
      this._overlayRef.dispose();
      this._overlayRef = null;
    }
  }
  /** Whether the menu is open. */
  get menuOpen() {
    return this._menuOpen;
  }
  /** The text direction of the containing app. */
  get dir() {
    return this._dir && this._dir.value === "rtl" ? "rtl" : "ltr";
  }
  /** Whether the menu triggers a sub-menu or a top-level one. */
  triggersSubmenu() {
    return !!(this._menuItemInstance && this._parentMaterialMenu && this.menu);
  }
  /** Toggles the menu between the open and closed states. */
  toggleMenu() {
    return this._menuOpen ? this.closeMenu() : this.openMenu();
  }
  /** Opens the menu. */
  openMenu() {
    const menu = this.menu;
    if (this._menuOpen || !menu) {
      return;
    }
    this._pendingRemoval?.unsubscribe();
    const previousTrigger = PANELS_TO_TRIGGERS.get(menu);
    PANELS_TO_TRIGGERS.set(menu, this);
    if (previousTrigger && previousTrigger !== this) {
      previousTrigger.closeMenu();
    }
    const overlayRef = this._createOverlay(menu);
    const overlayConfig = overlayRef.getConfig();
    const positionStrategy = overlayConfig.positionStrategy;
    this._setPosition(menu, positionStrategy);
    overlayConfig.hasBackdrop = menu.hasBackdrop == null ? !this.triggersSubmenu() : menu.hasBackdrop;
    if (!overlayRef.hasAttached()) {
      overlayRef.attach(this._getPortal(menu));
      menu.lazyContent?.attach(this.menuData);
    }
    this._closingActionsSubscription = this._menuClosingActions().subscribe(() => this.closeMenu());
    menu.parentMenu = this.triggersSubmenu() ? this._parentMaterialMenu : void 0;
    menu.direction = this.dir;
    menu.focusFirstItem(this._openedBy || "program");
    this._setIsMenuOpen(true);
    if (menu instanceof MatMenu) {
      menu._setIsOpen(true);
      menu._directDescendantItems.changes.pipe(takeUntil(menu.close)).subscribe(() => {
        positionStrategy.withLockedPosition(false).reapplyLastPosition();
        positionStrategy.withLockedPosition(true);
      });
    }
  }
  /** Closes the menu. */
  closeMenu() {
    this.menu?.close.emit();
  }
  /**
   * Focuses the menu trigger.
   * @param origin Source of the menu trigger's focus.
   */
  focus(origin, options) {
    if (this._focusMonitor && origin) {
      this._focusMonitor.focusVia(this._element, origin, options);
    } else {
      this._element.nativeElement.focus(options);
    }
  }
  /**
   * Updates the position of the menu to ensure that it fits all options within the viewport.
   */
  updatePosition() {
    this._overlayRef?.updatePosition();
  }
  /** Closes the menu and does the necessary cleanup. */
  _destroyMenu(reason) {
    const overlayRef = this._overlayRef;
    const menu = this._menu;
    if (!overlayRef || !this.menuOpen) {
      return;
    }
    this._closingActionsSubscription.unsubscribe();
    this._pendingRemoval?.unsubscribe();
    if (menu instanceof MatMenu && this._ownsMenu(menu)) {
      this._pendingRemoval = menu._animationDone.pipe(take(1)).subscribe(() => {
        overlayRef.detach();
        menu.lazyContent?.detach();
      });
      menu._setIsOpen(false);
    } else {
      overlayRef.detach();
      menu?.lazyContent?.detach();
    }
    if (menu && this._ownsMenu(menu)) {
      PANELS_TO_TRIGGERS.delete(menu);
    }
    if (this.restoreFocus && (reason === "keydown" || !this._openedBy || !this.triggersSubmenu())) {
      this.focus(this._openedBy);
    }
    this._openedBy = void 0;
    this._setIsMenuOpen(false);
  }
  // set state rather than toggle to support triggers sharing a menu
  _setIsMenuOpen(isOpen) {
    if (isOpen !== this._menuOpen) {
      this._menuOpen = isOpen;
      this._menuOpen ? this.menuOpened.emit() : this.menuClosed.emit();
      if (this.triggersSubmenu()) {
        this._menuItemInstance._setHighlighted(isOpen);
      }
      this._changeDetectorRef.markForCheck();
    }
  }
  /**
   * This method creates the overlay from the provided menu's template and saves its
   * OverlayRef so that it can be attached to the DOM when openMenu is called.
   */
  _createOverlay(menu) {
    if (!this._overlayRef) {
      const config = this._getOverlayConfig(menu);
      this._subscribeToPositions(menu, config.positionStrategy);
      this._overlayRef = this._overlay.create(config);
      this._overlayRef.keydownEvents().subscribe((event) => {
        if (this.menu instanceof MatMenu) {
          this.menu._handleKeydown(event);
        }
      });
    }
    return this._overlayRef;
  }
  /**
   * This method builds the configuration object needed to create the overlay, the OverlayState.
   * @returns OverlayConfig
   */
  _getOverlayConfig(menu) {
    return new OverlayConfig({
      positionStrategy: this._overlay.position().flexibleConnectedTo(this._element).withLockedPosition().withGrowAfterOpen().withTransformOriginOn(".mat-menu-panel, .mat-mdc-menu-panel"),
      backdropClass: menu.backdropClass || "cdk-overlay-transparent-backdrop",
      panelClass: menu.overlayPanelClass,
      scrollStrategy: this._scrollStrategy(),
      direction: this._dir || "ltr"
    });
  }
  /**
   * Listens to changes in the position of the overlay and sets the correct classes
   * on the menu based on the new position. This ensures the animation origin is always
   * correct, even if a fallback position is used for the overlay.
   */
  _subscribeToPositions(menu, position) {
    if (menu.setPositionClasses) {
      position.positionChanges.subscribe((change) => {
        this._ngZone.run(() => {
          const posX = change.connectionPair.overlayX === "start" ? "after" : "before";
          const posY = change.connectionPair.overlayY === "top" ? "below" : "above";
          menu.setPositionClasses(posX, posY);
        });
      });
    }
  }
  /**
   * Sets the appropriate positions on a position strategy
   * so the overlay connects with the trigger correctly.
   * @param positionStrategy Strategy whose position to update.
   */
  _setPosition(menu, positionStrategy) {
    let [originX, originFallbackX] = menu.xPosition === "before" ? ["end", "start"] : ["start", "end"];
    let [overlayY, overlayFallbackY] = menu.yPosition === "above" ? ["bottom", "top"] : ["top", "bottom"];
    let [originY, originFallbackY] = [overlayY, overlayFallbackY];
    let [overlayX, overlayFallbackX] = [originX, originFallbackX];
    let offsetY = 0;
    if (this.triggersSubmenu()) {
      overlayFallbackX = originX = menu.xPosition === "before" ? "start" : "end";
      originFallbackX = overlayX = originX === "end" ? "start" : "end";
      if (this._parentMaterialMenu) {
        if (this._parentInnerPadding == null) {
          const firstItem = this._parentMaterialMenu.items.first;
          this._parentInnerPadding = firstItem ? firstItem._getHostElement().offsetTop : 0;
        }
        offsetY = overlayY === "bottom" ? this._parentInnerPadding : -this._parentInnerPadding;
      }
    } else if (!menu.overlapTrigger) {
      originY = overlayY === "top" ? "bottom" : "top";
      originFallbackY = overlayFallbackY === "top" ? "bottom" : "top";
    }
    positionStrategy.withPositions([{
      originX,
      originY,
      overlayX,
      overlayY,
      offsetY
    }, {
      originX: originFallbackX,
      originY,
      overlayX: overlayFallbackX,
      overlayY,
      offsetY
    }, {
      originX,
      originY: originFallbackY,
      overlayX,
      overlayY: overlayFallbackY,
      offsetY: -offsetY
    }, {
      originX: originFallbackX,
      originY: originFallbackY,
      overlayX: overlayFallbackX,
      overlayY: overlayFallbackY,
      offsetY: -offsetY
    }]);
  }
  /** Returns a stream that emits whenever an action that should close the menu occurs. */
  _menuClosingActions() {
    const backdrop = this._overlayRef.backdropClick();
    const detachments = this._overlayRef.detachments();
    const parentClose = this._parentMaterialMenu ? this._parentMaterialMenu.closed : of();
    const hover = this._parentMaterialMenu ? this._parentMaterialMenu._hovered().pipe(filter((active) => this._menuOpen && active !== this._menuItemInstance)) : of();
    return merge(backdrop, parentClose, hover, detachments);
  }
  /** Handles mouse presses on the trigger. */
  _handleMousedown(event) {
    if (!isFakeMousedownFromScreenReader(event)) {
      this._openedBy = event.button === 0 ? "mouse" : void 0;
      if (this.triggersSubmenu()) {
        event.preventDefault();
      }
    }
  }
  /** Handles key presses on the trigger. */
  _handleKeydown(event) {
    const keyCode = event.keyCode;
    if (keyCode === ENTER || keyCode === SPACE) {
      this._openedBy = "keyboard";
    }
    if (this.triggersSubmenu() && (keyCode === RIGHT_ARROW && this.dir === "ltr" || keyCode === LEFT_ARROW && this.dir === "rtl")) {
      this._openedBy = "keyboard";
      this.openMenu();
    }
  }
  /** Handles click events on the trigger. */
  _handleClick(event) {
    if (this.triggersSubmenu()) {
      event.stopPropagation();
      this.openMenu();
    } else {
      this.toggleMenu();
    }
  }
  /** Handles the cases where the user hovers over the trigger. */
  _handleHover() {
    if (this.triggersSubmenu() && this._parentMaterialMenu) {
      this._hoverSubscription = this._parentMaterialMenu._hovered().subscribe((active) => {
        if (active === this._menuItemInstance && !active.disabled) {
          this._openedBy = "mouse";
          this.openMenu();
        }
      });
    }
  }
  /** Gets the portal that should be attached to the overlay. */
  _getPortal(menu) {
    if (!this._portal || this._portal.templateRef !== menu.templateRef) {
      this._portal = new TemplatePortal(menu.templateRef, this._viewContainerRef);
    }
    return this._portal;
  }
  /**
   * Determines whether the trigger owns a specific menu panel, at the current point in time.
   * This allows us to distinguish the case where the same panel is passed into multiple triggers
   * and multiple are open at a time.
   */
  _ownsMenu(menu) {
    return PANELS_TO_TRIGGERS.get(menu) === this;
  }
  static \u0275fac = function MatMenuTrigger_Factory(__ngFactoryType__) {
    return new (__ngFactoryType__ || _MatMenuTrigger)();
  };
  static \u0275dir = /* @__PURE__ */ \u0275\u0275defineDirective({
    type: _MatMenuTrigger,
    selectors: [["", "mat-menu-trigger-for", ""], ["", "matMenuTriggerFor", ""]],
    hostAttrs: [1, "mat-mdc-menu-trigger"],
    hostVars: 3,
    hostBindings: function MatMenuTrigger_HostBindings(rf, ctx) {
      if (rf & 1) {
        \u0275\u0275listener("click", function MatMenuTrigger_click_HostBindingHandler($event) {
          return ctx._handleClick($event);
        })("mousedown", function MatMenuTrigger_mousedown_HostBindingHandler($event) {
          return ctx._handleMousedown($event);
        })("keydown", function MatMenuTrigger_keydown_HostBindingHandler($event) {
          return ctx._handleKeydown($event);
        });
      }
      if (rf & 2) {
        \u0275\u0275attribute("aria-haspopup", ctx.menu ? "menu" : null)("aria-expanded", ctx.menuOpen)("aria-controls", ctx.menuOpen ? ctx.menu.panelId : null);
      }
    },
    inputs: {
      _deprecatedMatMenuTriggerFor: [0, "mat-menu-trigger-for", "_deprecatedMatMenuTriggerFor"],
      menu: [0, "matMenuTriggerFor", "menu"],
      menuData: [0, "matMenuTriggerData", "menuData"],
      restoreFocus: [0, "matMenuTriggerRestoreFocus", "restoreFocus"]
    },
    outputs: {
      menuOpened: "menuOpened",
      onMenuOpen: "onMenuOpen",
      menuClosed: "menuClosed",
      onMenuClose: "onMenuClose"
    },
    exportAs: ["matMenuTrigger"]
  });
};
(() => {
  (typeof ngDevMode === "undefined" || ngDevMode) && setClassMetadata(MatMenuTrigger, [{
    type: Directive,
    args: [{
      selector: `[mat-menu-trigger-for], [matMenuTriggerFor]`,
      host: {
        "class": "mat-mdc-menu-trigger",
        "[attr.aria-haspopup]": 'menu ? "menu" : null',
        "[attr.aria-expanded]": "menuOpen",
        "[attr.aria-controls]": "menuOpen ? menu.panelId : null",
        "(click)": "_handleClick($event)",
        "(mousedown)": "_handleMousedown($event)",
        "(keydown)": "_handleKeydown($event)"
      },
      exportAs: "matMenuTrigger"
    }]
  }], () => [], {
    _deprecatedMatMenuTriggerFor: [{
      type: Input,
      args: ["mat-menu-trigger-for"]
    }],
    menu: [{
      type: Input,
      args: ["matMenuTriggerFor"]
    }],
    menuData: [{
      type: Input,
      args: ["matMenuTriggerData"]
    }],
    restoreFocus: [{
      type: Input,
      args: ["matMenuTriggerRestoreFocus"]
    }],
    menuOpened: [{
      type: Output
    }],
    onMenuOpen: [{
      type: Output
    }],
    menuClosed: [{
      type: Output
    }],
    onMenuClose: [{
      type: Output
    }]
  });
})();
var MatMenuModule = class _MatMenuModule {
  static \u0275fac = function MatMenuModule_Factory(__ngFactoryType__) {
    return new (__ngFactoryType__ || _MatMenuModule)();
  };
  static \u0275mod = /* @__PURE__ */ \u0275\u0275defineNgModule({
    type: _MatMenuModule,
    imports: [MatRippleModule, MatCommonModule, OverlayModule, MatMenu, MatMenuItem, MatMenuContent, MatMenuTrigger],
    exports: [CdkScrollableModule, MatMenu, MatCommonModule, MatMenuItem, MatMenuContent, MatMenuTrigger]
  });
  static \u0275inj = /* @__PURE__ */ \u0275\u0275defineInjector({
    providers: [MAT_MENU_SCROLL_STRATEGY_FACTORY_PROVIDER],
    imports: [MatRippleModule, MatCommonModule, OverlayModule, CdkScrollableModule, MatCommonModule]
  });
};
(() => {
  (typeof ngDevMode === "undefined" || ngDevMode) && setClassMetadata(MatMenuModule, [{
    type: NgModule,
    args: [{
      imports: [MatRippleModule, MatCommonModule, OverlayModule, MatMenu, MatMenuItem, MatMenuContent, MatMenuTrigger],
      exports: [CdkScrollableModule, MatMenu, MatCommonModule, MatMenuItem, MatMenuContent, MatMenuTrigger],
      providers: [MAT_MENU_SCROLL_STRATEGY_FACTORY_PROVIDER]
    }]
  }], null, null);
})();
var matMenuAnimations = {
  // Represents:
  // trigger('transformMenu', [
  //   state(
  //     'void',
  //     style({
  //       opacity: 0,
  //       transform: 'scale(0.8)',
  //     }),
  //   ),
  //   transition(
  //     'void => enter',
  //     animate(
  //       '120ms cubic-bezier(0, 0, 0.2, 1)',
  //       style({
  //         opacity: 1,
  //         transform: 'scale(1)',
  //       }),
  //     ),
  //   ),
  //   transition('* => void', animate('100ms 25ms linear', style({opacity: 0}))),
  // ])
  /**
   * This animation controls the menu panel's entry and exit from the page.
   *
   * When the menu panel is added to the DOM, it scales in and fades in its border.
   *
   * When the menu panel is removed from the DOM, it simply fades out after a brief
   * delay to display the ripple.
   */
  transformMenu: {
    type: 7,
    name: "transformMenu",
    definitions: [{
      type: 0,
      name: "void",
      styles: {
        type: 6,
        styles: {
          opacity: 0,
          transform: "scale(0.8)"
        },
        offset: null
      }
    }, {
      type: 1,
      expr: "void => enter",
      animation: {
        type: 4,
        styles: {
          type: 6,
          styles: {
            opacity: 1,
            transform: "scale(1)"
          },
          offset: null
        },
        timings: "120ms cubic-bezier(0, 0, 0.2, 1)"
      },
      options: null
    }, {
      type: 1,
      expr: "* => void",
      animation: {
        type: 4,
        styles: {
          type: 6,
          styles: {
            opacity: 0
          },
          offset: null
        },
        timings: "100ms 25ms linear"
      },
      options: null
    }],
    options: {}
  },
  // Represents:
  // trigger('fadeInItems', [
  //   // TODO(crisbeto): this is inside the `transformMenu`
  //   // now. Remove next time we do breaking changes.
  //   state('showing', style({opacity: 1})),
  //   transition('void => *', [
  //     style({opacity: 0}),
  //     animate('400ms 100ms cubic-bezier(0.55, 0, 0.55, 0.2)'),
  //   ]),
  // ])
  /**
   * This animation fades in the background color and content of the menu panel
   * after its containing element is scaled in.
   */
  fadeInItems: {
    type: 7,
    name: "fadeInItems",
    definitions: [{
      type: 0,
      name: "showing",
      styles: {
        type: 6,
        styles: {
          opacity: 1
        },
        offset: null
      }
    }, {
      type: 1,
      expr: "void => *",
      animation: [{
        type: 6,
        styles: {
          opacity: 0
        },
        offset: null
      }, {
        type: 4,
        styles: null,
        timings: "400ms 100ms cubic-bezier(0.55, 0, 0.55, 0.2)"
      }],
      options: null
    }],
    options: {}
  }
};
var fadeInItems = matMenuAnimations.fadeInItems;
var transformMenu = matMenuAnimations.transformMenu;

// src/app/features/headman/journal/headman-journal-grid/headman-journal-grid.component.ts
var _c02 = (a0, a1) => ({ cell: a0, row: a1 });
var _c12 = (a0, a1) => ({ cell: null, row: a0, col: a1 });
var _forTrack0 = ($index, $item) => $item.id;
var _forTrack1 = ($index, $item) => $item.value;
function HeadmanJournalGridComponent_th_3_Template(rf, ctx) {
  if (rf & 1) {
    \u0275\u0275elementStart(0, "th", 14);
    \u0275\u0275text(1, "\u0421\u0442\u0443\u0434\u0435\u043D\u0442");
    \u0275\u0275elementEnd();
  }
}
function HeadmanJournalGridComponent_td_4_Template(rf, ctx) {
  if (rf & 1) {
    \u0275\u0275elementStart(0, "td", 15);
    \u0275\u0275text(1);
    \u0275\u0275elementEnd();
  }
  if (rf & 2) {
    const row_r1 = ctx.$implicit;
    \u0275\u0275property("title", row_r1.displayName);
    \u0275\u0275advance();
    \u0275\u0275textInterpolate1(" ", row_r1.displayName, " ");
  }
}
function HeadmanJournalGridComponent_For_6_th_1_For_18_Template(rf, ctx) {
  if (rf & 1) {
    const _r5 = \u0275\u0275getCurrentView();
    \u0275\u0275elementStart(0, "button", 27);
    \u0275\u0275listener("click", function HeadmanJournalGridComponent_For_6_th_1_For_18_Template_button_click_0_listener() {
      const extra_r6 = \u0275\u0275restoreView(_r5).$implicit;
      const col_r3 = \u0275\u0275nextContext(2).$implicit;
      const ctx_r3 = \u0275\u0275nextContext();
      return \u0275\u0275resetView(ctx_r3.bulkMark(col_r3, extra_r6.value));
    });
    \u0275\u0275text(1);
    \u0275\u0275elementEnd();
  }
  if (rf & 2) {
    const extra_r6 = ctx.$implicit;
    \u0275\u0275advance();
    \u0275\u0275textInterpolate1(" ", extra_r6.label, " ");
  }
}
function HeadmanJournalGridComponent_For_6_th_1_Template(rf, ctx) {
  if (rf & 1) {
    const _r2 = \u0275\u0275getCurrentView();
    \u0275\u0275elementStart(0, "th", 18)(1, "div", 19)(2, "span", 20);
    \u0275\u0275text(3);
    \u0275\u0275elementEnd();
    \u0275\u0275text(4);
    \u0275\u0275elementEnd();
    \u0275\u0275elementStart(5, "div", 21)(6, "button", 22);
    \u0275\u0275listener("click", function HeadmanJournalGridComponent_For_6_th_1_Template_button_click_6_listener() {
      \u0275\u0275restoreView(_r2);
      const col_r3 = \u0275\u0275nextContext().$implicit;
      const ctx_r3 = \u0275\u0275nextContext();
      return \u0275\u0275resetView(ctx_r3.bulkMark(col_r3, "present"));
    });
    \u0275\u0275text(7, "+");
    \u0275\u0275elementEnd();
    \u0275\u0275elementStart(8, "button", 23);
    \u0275\u0275listener("click", function HeadmanJournalGridComponent_For_6_th_1_Template_button_click_8_listener() {
      \u0275\u0275restoreView(_r2);
      const col_r3 = \u0275\u0275nextContext().$implicit;
      const ctx_r3 = \u0275\u0275nextContext();
      return \u0275\u0275resetView(ctx_r3.bulkMark(col_r3, "absent"));
    });
    \u0275\u0275text(9, "\u041D");
    \u0275\u0275elementEnd();
    \u0275\u0275elementStart(10, "button", 24);
    \u0275\u0275listener("click", function HeadmanJournalGridComponent_For_6_th_1_Template_button_click_10_listener() {
      \u0275\u0275restoreView(_r2);
      const col_r3 = \u0275\u0275nextContext().$implicit;
      const ctx_r3 = \u0275\u0275nextContext();
      return \u0275\u0275resetView(ctx_r3.bulkMark(col_r3, "excused"));
    });
    \u0275\u0275text(11, "\u0423");
    \u0275\u0275elementEnd();
    \u0275\u0275elementStart(12, "button", 25)(13, "mat-icon");
    \u0275\u0275text(14, "more_horiz");
    \u0275\u0275elementEnd()();
    \u0275\u0275elementStart(15, "mat-menu", null, 1);
    \u0275\u0275repeaterCreate(17, HeadmanJournalGridComponent_For_6_th_1_For_18_Template, 2, 1, "button", 26, _forTrack1);
    \u0275\u0275elementEnd()()();
  }
  if (rf & 2) {
    const extraMenu_r7 = \u0275\u0275reference(16);
    const col_r3 = \u0275\u0275nextContext().$implicit;
    const ctx_r3 = \u0275\u0275nextContext();
    \u0275\u0275classProp("today-column", col_r3.isToday);
    \u0275\u0275advance(3);
    \u0275\u0275textInterpolate(col_r3.weekday);
    \u0275\u0275advance();
    \u0275\u0275textInterpolate1(" ", col_r3.displayDate, " ");
    \u0275\u0275advance();
    \u0275\u0275attribute("aria-label", "\u041C\u0430\u0441\u0441\u043E\u0432\u0430\u044F \u043E\u0442\u043C\u0435\u0442\u043A\u0430 " + col_r3.weekday + " " + col_r3.displayDate);
    \u0275\u0275advance(7);
    \u0275\u0275property("matMenuTriggerFor", extraMenu_r7);
    \u0275\u0275advance(5);
    \u0275\u0275repeater(ctx_r3.extraStatuses);
  }
}
function HeadmanJournalGridComponent_For_6_td_2_Conditional_2_Conditional_0_Template(rf, ctx) {
  if (rf & 1) {
    \u0275\u0275elementStart(0, "span", 30);
    \u0275\u0275text(1);
    \u0275\u0275elementEnd();
  }
  if (rf & 2) {
    const row_r8 = \u0275\u0275nextContext(2).$implicit;
    const cell_r9 = \u0275\u0275readContextLet(1);
    const col_r3 = \u0275\u0275nextContext().$implicit;
    \u0275\u0275attribute("aria-label", "\u041E\u0442\u043C\u0435\u043D\u0435\u043D\u0430, " + row_r8.displayName + ", " + col_r3.displayDate);
    \u0275\u0275advance();
    \u0275\u0275textInterpolate(cell_r9.symbol);
  }
}
function HeadmanJournalGridComponent_For_6_td_2_Conditional_2_Conditional_1_Template(rf, ctx) {
  if (rf & 1) {
    \u0275\u0275elementStart(0, "button", 32);
    \u0275\u0275text(1);
    \u0275\u0275elementEnd();
  }
  if (rf & 2) {
    const row_r8 = \u0275\u0275nextContext(2).$implicit;
    const cell_r9 = \u0275\u0275readContextLet(1);
    const col_r3 = \u0275\u0275nextContext().$implicit;
    \u0275\u0275nextContext();
    const cellMenu_r10 = \u0275\u0275reference(13);
    \u0275\u0275classMapInterpolate1("status-btn status-chip--", cell_r9.status, "");
    \u0275\u0275property("matMenuTriggerFor", cellMenu_r10)("matMenuTriggerData", \u0275\u0275pureFunction2(7, _c02, cell_r9, row_r8));
    \u0275\u0275attribute("aria-label", "\u0421\u0442\u0430\u0442\u0443\u0441: " + cell_r9.symbol + ", " + row_r8.displayName + ", " + col_r3.displayDate);
    \u0275\u0275advance();
    \u0275\u0275textInterpolate(cell_r9.symbol);
  }
}
function HeadmanJournalGridComponent_For_6_td_2_Conditional_2_Template(rf, ctx) {
  if (rf & 1) {
    \u0275\u0275template(0, HeadmanJournalGridComponent_For_6_td_2_Conditional_2_Conditional_0_Template, 2, 2, "span", 30)(1, HeadmanJournalGridComponent_For_6_td_2_Conditional_2_Conditional_1_Template, 2, 10, "button", 31);
  }
  if (rf & 2) {
    \u0275\u0275nextContext();
    const cell_r9 = \u0275\u0275readContextLet(1);
    \u0275\u0275conditional(cell_r9.status === "cancelled" ? 0 : 1);
  }
}
function HeadmanJournalGridComponent_For_6_td_2_Conditional_3_Template(rf, ctx) {
  if (rf & 1) {
    \u0275\u0275elementStart(0, "button", 29);
    \u0275\u0275text(1, "\u2014");
    \u0275\u0275elementEnd();
  }
  if (rf & 2) {
    const row_r8 = \u0275\u0275nextContext().$implicit;
    const col_r3 = \u0275\u0275nextContext().$implicit;
    \u0275\u0275nextContext();
    const cellMenu_r10 = \u0275\u0275reference(13);
    \u0275\u0275property("matMenuTriggerFor", cellMenu_r10)("matMenuTriggerData", \u0275\u0275pureFunction2(3, _c12, row_r8, col_r3));
    \u0275\u0275attribute("aria-label", "\u041D\u0435\u0442 \u043E\u0442\u043C\u0435\u0442\u043A\u0438, " + row_r8.displayName + ", " + col_r3.displayDate);
  }
}
function HeadmanJournalGridComponent_For_6_td_2_Template(rf, ctx) {
  if (rf & 1) {
    \u0275\u0275elementStart(0, "td", 28);
    \u0275\u0275declareLet(1);
    \u0275\u0275template(2, HeadmanJournalGridComponent_For_6_td_2_Conditional_2_Template, 2, 1)(3, HeadmanJournalGridComponent_For_6_td_2_Conditional_3_Template, 2, 6, "button", 29);
    \u0275\u0275elementEnd();
  }
  if (rf & 2) {
    const row_r8 = ctx.$implicit;
    const col_r3 = \u0275\u0275nextContext().$implicit;
    \u0275\u0275advance();
    const cell_r11 = \u0275\u0275storeLet(\u0275\u0275nextContext().getCell(row_r8.userId, col_r3.id));
    \u0275\u0275advance();
    \u0275\u0275conditional(cell_r11 ? 2 : 3);
  }
}
function HeadmanJournalGridComponent_For_6_Template(rf, ctx) {
  if (rf & 1) {
    \u0275\u0275elementContainerStart(0, 7);
    \u0275\u0275template(1, HeadmanJournalGridComponent_For_6_th_1_Template, 19, 6, "th", 16)(2, HeadmanJournalGridComponent_For_6_td_2_Template, 4, 2, "td", 17);
    \u0275\u0275elementContainerEnd();
  }
  if (rf & 2) {
    const col_r3 = ctx.$implicit;
    \u0275\u0275property("cdkColumnDef", col_r3.id);
  }
}
function HeadmanJournalGridComponent_th_8_Template(rf, ctx) {
  if (rf & 1) {
    \u0275\u0275elementStart(0, "th", 33);
    \u0275\u0275text(1, "%");
    \u0275\u0275elementEnd();
  }
}
function HeadmanJournalGridComponent_td_9_Template(rf, ctx) {
  if (rf & 1) {
    \u0275\u0275elementStart(0, "td", 34);
    \u0275\u0275text(1);
    \u0275\u0275elementEnd();
  }
  if (rf & 2) {
    const row_r12 = ctx.$implicit;
    const ctx_r3 = \u0275\u0275nextContext();
    \u0275\u0275advance();
    \u0275\u0275textInterpolate1(" ", ctx_r3.getPercent(row_r12.userId), "% ");
  }
}
function HeadmanJournalGridComponent_tr_10_Template(rf, ctx) {
  if (rf & 1) {
    \u0275\u0275element(0, "tr", 35);
  }
}
function HeadmanJournalGridComponent_tr_11_Template(rf, ctx) {
  if (rf & 1) {
    \u0275\u0275element(0, "tr", 36);
  }
}
function HeadmanJournalGridComponent_ng_template_14_Template(rf, ctx) {
  if (rf & 1) {
    const _r13 = \u0275\u0275getCurrentView();
    \u0275\u0275elementStart(0, "button", 27);
    \u0275\u0275listener("click", function HeadmanJournalGridComponent_ng_template_14_Template_button_click_0_listener() {
      const ctx_r13 = \u0275\u0275restoreView(_r13);
      const cell_r15 = ctx_r13.cell;
      const row_r16 = ctx_r13.row;
      const col_r17 = ctx_r13.col;
      const ctx_r3 = \u0275\u0275nextContext();
      return \u0275\u0275resetView(ctx_r3.setStatus(cell_r15, row_r16, col_r17, "present"));
    });
    \u0275\u0275text(1, "\u041F\u0440\u0438\u0441\u0443\u0442\u0441\u0442\u0432\u043E\u0432\u0430\u043B");
    \u0275\u0275elementEnd();
    \u0275\u0275elementStart(2, "button", 27);
    \u0275\u0275listener("click", function HeadmanJournalGridComponent_ng_template_14_Template_button_click_2_listener() {
      const ctx_r17 = \u0275\u0275restoreView(_r13);
      const cell_r15 = ctx_r17.cell;
      const row_r16 = ctx_r17.row;
      const col_r17 = ctx_r17.col;
      const ctx_r3 = \u0275\u0275nextContext();
      return \u0275\u0275resetView(ctx_r3.setStatus(cell_r15, row_r16, col_r17, "absent"));
    });
    \u0275\u0275text(3, "\u041D\u0435 \u0431\u044B\u043B");
    \u0275\u0275elementEnd();
    \u0275\u0275elementStart(4, "button", 27);
    \u0275\u0275listener("click", function HeadmanJournalGridComponent_ng_template_14_Template_button_click_4_listener() {
      const ctx_r18 = \u0275\u0275restoreView(_r13);
      const cell_r15 = ctx_r18.cell;
      const row_r16 = ctx_r18.row;
      const col_r17 = ctx_r18.col;
      const ctx_r3 = \u0275\u0275nextContext();
      return \u0275\u0275resetView(ctx_r3.setStatus(cell_r15, row_r16, col_r17, "excused"));
    });
    \u0275\u0275text(5, "\u0423\u0432\u0430\u0436\u0438\u0442. \u043F\u0440\u0438\u0447\u0438\u043D\u0430");
    \u0275\u0275elementEnd();
    \u0275\u0275elementStart(6, "button", 27);
    \u0275\u0275listener("click", function HeadmanJournalGridComponent_ng_template_14_Template_button_click_6_listener() {
      const ctx_r19 = \u0275\u0275restoreView(_r13);
      const cell_r15 = ctx_r19.cell;
      const row_r16 = ctx_r19.row;
      const col_r17 = ctx_r19.col;
      const ctx_r3 = \u0275\u0275nextContext();
      return \u0275\u0275resetView(ctx_r3.setStatus(cell_r15, row_r16, col_r17, "free_attendance"));
    });
    \u0275\u0275text(7, "\u0421\u0432\u043E\u0431\u043E\u0434\u043D\u043E\u0435 \u043F\u043E\u0441\u0435\u0449\u0435\u043D\u0438\u0435");
    \u0275\u0275elementEnd();
  }
}
var STATUS_SYMBOLS = {
  present: "+",
  absent: "\u041D",
  excused: "\u0423",
  free_attendance: "\u0421\u041F",
  cancelled: "--"
};
var WEEKDAY_LABELS = ["\u0412\u0421", "\u041F\u041D", "\u0412\u0422", "\u0421\u0420", "\u0427\u0422", "\u041F\u0422", "\u0421\u0411"];
var EXTRA_STATUSES = [
  { value: "free_attendance", label: "\u0421\u0432\u043E\u0431\u043E\u0434\u043D\u043E\u0435 \u043F\u043E\u0441\u0435\u0449\u0435\u043D\u0438\u0435" },
  { value: "cancelled", label: "\u041E\u0442\u043C\u0435\u043D\u0435\u043D\u0430" }
];
var HeadmanJournalGridComponent = class _HeadmanJournalGridComponent {
  journalData;
  loading = false;
  headmanApi = inject(HeadmanApiService);
  snackBar = inject(MatSnackBar);
  today = (/* @__PURE__ */ new Date()).toISOString().slice(0, 10);
  extraStatuses = EXTRA_STATUSES;
  cellMap = signal(/* @__PURE__ */ new Map());
  columns = computed(() => {
    void this.cellMap();
    if (!this.journalData)
      return [];
    return this.buildColumns(this.journalData);
  });
  displayedColumns = computed(() => [
    "student",
    ...this.columns().map((c) => c.id),
    "percent"
  ]);
  dataSource = computed(() => {
    if (!this.journalData)
      return [];
    return [...this.journalData.students].sort((a, b) => a.displayName.localeCompare(b.displayName, "ru"));
  });
  ngOnChanges(changes) {
    if (changes["journalData"] && this.journalData) {
      this.cellMap.set(this.buildCellMap(this.journalData));
    }
  }
  buildColumns(journal) {
    const colSet = /* @__PURE__ */ new Map();
    journal.students.forEach((row) => row.records.forEach((cell) => {
      if (cell.date > this.today)
        return;
      const id = `${cell.date}_lesson${cell.lessonNumber}`;
      if (!colSet.has(id)) {
        const parts = cell.date.split("-");
        const dayIdx = (/* @__PURE__ */ new Date(cell.date + "T00:00:00")).getDay();
        colSet.set(id, {
          id,
          date: cell.date,
          lessonNumber: cell.lessonNumber,
          displayDate: `${parts[2]}.${parts[1]}`,
          isToday: cell.date === this.today,
          weekday: WEEKDAY_LABELS[dayIdx]
        });
      }
    }));
    return Array.from(colSet.values()).sort((a, b) => a.date.localeCompare(b.date) || a.lessonNumber - b.lessonNumber);
  }
  buildCellMap(journal) {
    const map = /* @__PURE__ */ new Map();
    journal.students.forEach((row) => row.records.forEach((cell) => {
      const colId = `${cell.date}_lesson${cell.lessonNumber}`;
      map.set(`${row.userId}_${colId}`, __spreadValues({}, cell));
    }));
    return map;
  }
  getCell(userId, colId) {
    return this.cellMap().get(`${userId}_${colId}`);
  }
  getPercent(userId) {
    const cols = this.columns();
    let counted = 0;
    let attended = 0;
    for (const col of cols) {
      const cell = this.cellMap().get(`${userId}_${col.id}`);
      if (!cell || cell.status === "cancelled")
        continue;
      counted++;
      if (cell.status === "present" || cell.status === "excused" || cell.status === "free_attendance") {
        attended++;
      }
    }
    if (counted === 0)
      return 0;
    return Math.round(attended / counted * 100);
  }
  setStatus(cell, row, col, next) {
    const resolvedCell = cell ?? (col ? this.getCell(row.userId, col.id) : void 0);
    if (!resolvedCell || !resolvedCell.lessonId)
      return;
    this.applyStatus(resolvedCell, row, next);
  }
  applyStatus(cell, row, next) {
    if (!cell.lessonId || cell.status === next)
      return;
    const prev = cell.status;
    cell.status = next;
    cell.symbol = STATUS_SYMBOLS[next];
    this.cellMap.set(new Map(this.cellMap()));
    this.headmanApi.markAttendance(cell.lessonId, row.userId, next).pipe(catchError(() => {
      cell.status = prev;
      cell.symbol = STATUS_SYMBOLS[prev];
      this.cellMap.set(new Map(this.cellMap()));
      this.snackBar.open("\u041D\u0435 \u0443\u0434\u0430\u043B\u043E\u0441\u044C \u0438\u0437\u043C\u0435\u043D\u0438\u0442\u044C \u0441\u0442\u0430\u0442\u0443\u0441. \u041F\u043E\u043F\u0440\u043E\u0431\u0443\u0439\u0442\u0435 \u0441\u043D\u043E\u0432\u0430.", void 0, {
        duration: 4e3
      });
      return of(null);
    })).subscribe();
  }
  bulkMark(col, status) {
    const targets = [];
    for (const row of this.journalData.students) {
      const cell = this.cellMap().get(`${row.userId}_${col.id}`);
      if (!cell || !cell.lessonId)
        continue;
      if (cell.status === "cancelled" && status !== "cancelled")
        continue;
      if (cell.status === status)
        continue;
      targets.push({ cell, row, prev: cell.status });
    }
    if (targets.length === 0)
      return;
    targets.forEach((t) => {
      t.cell.status = status;
      t.cell.symbol = STATUS_SYMBOLS[status];
    });
    this.cellMap.set(new Map(this.cellMap()));
    forkJoin(targets.map((t) => this.headmanApi.markAttendance(t.cell.lessonId, t.row.userId, status).pipe(catchError(() => {
      t.cell.status = t.prev;
      t.cell.symbol = STATUS_SYMBOLS[t.prev];
      return of({ failed: true });
    })))).subscribe((results) => {
      const failed = results.filter((r) => r?.failed).length;
      if (failed > 0) {
        this.cellMap.set(new Map(this.cellMap()));
        this.snackBar.open(`\u041D\u0435 \u0443\u0434\u0430\u043B\u043E\u0441\u044C \u043E\u0431\u043D\u043E\u0432\u0438\u0442\u044C ${failed} ${failed === 1 ? "\u0437\u0430\u043F\u0438\u0441\u044C" : "\u0437\u0430\u043F\u0438\u0441\u0435\u0439"}.`, void 0, { duration: 4e3 });
      }
    });
  }
  static \u0275fac = function HeadmanJournalGridComponent_Factory(__ngFactoryType__) {
    return new (__ngFactoryType__ || _HeadmanJournalGridComponent)();
  };
  static \u0275cmp = /* @__PURE__ */ \u0275\u0275defineComponent({ type: _HeadmanJournalGridComponent, selectors: [["app-headman-journal-grid"]], inputs: { journalData: "journalData", loading: "loading" }, features: [\u0275\u0275NgOnChangesFeature], decls: 15, vars: 7, consts: [["cellMenu", "matMenu"], ["extraMenu", "matMenu"], [1, "journal-grid-wrapper"], ["cdk-table", "", 3, "dataSource"], ["cdkColumnDef", "student", "sticky", ""], ["cdk-header-cell", "", "class", "student-header", 4, "cdkHeaderCellDef"], ["cdk-cell", "", "class", "student-cell", 3, "title", 4, "cdkCellDef"], [3, "cdkColumnDef"], ["cdkColumnDef", "percent", "stickyEnd", ""], ["cdk-header-cell", "", "class", "percent-header", 4, "cdkHeaderCellDef"], ["cdk-cell", "", "class", "percent-cell", 4, "cdkCellDef"], ["cdk-header-row", "", 4, "cdkHeaderRowDef", "cdkHeaderRowDefSticky"], ["cdk-row", "", 4, "cdkRowDef", "cdkRowDefColumns"], ["matMenuContent", ""], ["cdk-header-cell", "", 1, "student-header"], ["cdk-cell", "", 1, "student-cell", 3, "title"], ["cdk-header-cell", "", "class", "date-header", 3, "today-column", 4, "cdkHeaderCellDef"], ["cdk-cell", "", "class", "status-cell", 4, "cdkCellDef"], ["cdk-header-cell", "", 1, "date-header"], [1, "date-label"], [1, "date-label__weekday"], [1, "bulk-row"], ["type", "button", "title", "\u041E\u0442\u043C\u0435\u0442\u0438\u0442\u044C \u0432\u0441\u0435\u043C \xAB\u043F\u0440\u0438\u0441\u0443\u0442\u0441\u0442\u0432\u043E\u0432\u0430\u043B\xBB", 1, "bulk-btn", "bulk-btn--present", 3, "click"], ["type", "button", "title", "\u041E\u0442\u043C\u0435\u0442\u0438\u0442\u044C \u0432\u0441\u0435\u043C \xAB\u043D\u0435 \u0431\u044B\u043B\xBB", 1, "bulk-btn", "bulk-btn--absent", 3, "click"], ["type", "button", "title", "\u041E\u0442\u043C\u0435\u0442\u0438\u0442\u044C \u0432\u0441\u0435\u043C \xAB\u0443\u0432\u0430\u0436\u0438\u0442.\xBB", 1, "bulk-btn", "bulk-btn--excused", 3, "click"], ["type", "button", "title", "\u0414\u0440\u0443\u0433\u0438\u0435 \u0441\u0442\u0430\u0442\u0443\u0441\u044B", 1, "bulk-more", 3, "matMenuTriggerFor"], ["mat-menu-item", ""], ["mat-menu-item", "", 3, "click"], ["cdk-cell", "", 1, "status-cell"], [1, "status-empty", 3, "matMenuTriggerFor", "matMenuTriggerData"], ["aria-disabled", "true", "tabindex", "-1", 1, "status-cancelled"], [3, "class", "matMenuTriggerFor", "matMenuTriggerData"], [3, "matMenuTriggerFor", "matMenuTriggerData"], ["cdk-header-cell", "", 1, "percent-header"], ["cdk-cell", "", 1, "percent-cell"], ["cdk-header-row", ""], ["cdk-row", ""]], template: function HeadmanJournalGridComponent_Template(rf, ctx) {
    if (rf & 1) {
      \u0275\u0275elementStart(0, "div", 2)(1, "table", 3);
      \u0275\u0275elementContainerStart(2, 4);
      \u0275\u0275template(3, HeadmanJournalGridComponent_th_3_Template, 2, 0, "th", 5)(4, HeadmanJournalGridComponent_td_4_Template, 2, 2, "td", 6);
      \u0275\u0275elementContainerEnd();
      \u0275\u0275repeaterCreate(5, HeadmanJournalGridComponent_For_6_Template, 3, 1, "ng-container", 7, _forTrack0);
      \u0275\u0275elementContainerStart(7, 8);
      \u0275\u0275template(8, HeadmanJournalGridComponent_th_8_Template, 2, 0, "th", 9)(9, HeadmanJournalGridComponent_td_9_Template, 2, 1, "td", 10);
      \u0275\u0275elementContainerEnd();
      \u0275\u0275template(10, HeadmanJournalGridComponent_tr_10_Template, 1, 0, "tr", 11)(11, HeadmanJournalGridComponent_tr_11_Template, 1, 0, "tr", 12);
      \u0275\u0275elementEnd();
      \u0275\u0275elementStart(12, "mat-menu", null, 0);
      \u0275\u0275template(14, HeadmanJournalGridComponent_ng_template_14_Template, 8, 0, "ng-template", 13);
      \u0275\u0275elementEnd()();
    }
    if (rf & 2) {
      \u0275\u0275styleProp("pointer-events", ctx.loading ? "none" : "");
      \u0275\u0275attribute("aria-busy", ctx.loading);
      \u0275\u0275advance();
      \u0275\u0275property("dataSource", ctx.dataSource());
      \u0275\u0275advance(4);
      \u0275\u0275repeater(ctx.columns());
      \u0275\u0275advance(5);
      \u0275\u0275property("cdkHeaderRowDef", ctx.displayedColumns())("cdkHeaderRowDefSticky", true);
      \u0275\u0275advance();
      \u0275\u0275property("cdkRowDefColumns", ctx.displayedColumns());
    }
  }, dependencies: [CdkTableModule, CdkTable, CdkRowDef, CdkCellDef, CdkHeaderCellDef, CdkColumnDef, CdkCell, CdkRow, CdkHeaderCell, CdkHeaderRow, CdkHeaderRowDef, MatMenuModule, MatMenu, MatMenuItem, MatMenuContent, MatMenuTrigger, MatIconModule, MatIcon], styles: ["\n\n[_nghost-%COMP%] {\n  display: block;\n}\n.journal-grid-wrapper[_ngcontent-%COMP%] {\n  overflow-x: auto;\n  border: 1px solid var(--mat-sys-outline-variant);\n  border-radius: 8px;\n  background: var(--mat-sys-surface);\n}\ntable[_ngcontent-%COMP%] {\n  border-collapse: separate;\n  border-spacing: 0;\n  width: 100%;\n}\n[_nghost-%COMP%]     cdk-header-row, \n[_nghost-%COMP%]     cdk-row {\n  display: flex;\n  align-items: stretch;\n}\n.student-header[_ngcontent-%COMP%], \n.student-cell[_ngcontent-%COMP%] {\n  position: sticky;\n  left: 0;\n  z-index: 4;\n  width: 220px;\n  min-width: 220px;\n  max-width: 220px;\n  padding: 8px 12px;\n  background: var(--mat-sys-surface-container);\n  border-right: 1px solid var(--mat-sys-outline-variant);\n  white-space: nowrap;\n  overflow: hidden;\n  text-overflow: ellipsis;\n  font-size: 14px;\n  display: flex;\n  align-items: center;\n}\n.student-header[_ngcontent-%COMP%] {\n  font-weight: 600;\n  z-index: 6;\n}\n.percent-header[_ngcontent-%COMP%], \n.percent-cell[_ngcontent-%COMP%] {\n  position: sticky;\n  right: 0;\n  z-index: 4;\n  width: 72px;\n  min-width: 72px;\n  max-width: 72px;\n  padding: 8px;\n  background: var(--mat-sys-surface-container);\n  border-left: 1px solid var(--mat-sys-outline-variant);\n  text-align: center;\n  display: flex;\n  align-items: center;\n  justify-content: center;\n  font-weight: 600;\n  font-size: 13px;\n}\n.percent-header[_ngcontent-%COMP%] {\n  font-weight: 600;\n  z-index: 6;\n}\n.date-header[_ngcontent-%COMP%] {\n  width: 60px;\n  min-width: 60px;\n  max-width: 60px;\n  padding: 4px 2px;\n  background: var(--mat-sys-surface-container);\n  display: flex;\n  flex-direction: column;\n  align-items: center;\n  justify-content: flex-start;\n  font-size: 11px;\n  font-weight: 600;\n  border-right: 1px solid var(--mat-sys-outline-variant);\n  gap: 2px;\n}\n.date-header.today-column[_ngcontent-%COMP%] {\n  background: color-mix(in srgb, var(--mat-sys-primary) 12%, transparent);\n}\n.date-label[_ngcontent-%COMP%] {\n  line-height: 1.2;\n  letter-spacing: 0.3px;\n}\n.date-label__weekday[_ngcontent-%COMP%] {\n  opacity: 0.7;\n  font-weight: 500;\n}\n.bulk-row[_ngcontent-%COMP%] {\n  display: flex;\n  gap: 2px;\n  margin-top: 2px;\n  align-items: center;\n}\n.bulk-btn[_ngcontent-%COMP%] {\n  border: none;\n  background: transparent;\n  width: 16px;\n  height: 16px;\n  padding: 0;\n  font-size: 10px;\n  font-weight: 700;\n  border-radius: 3px;\n  cursor: pointer;\n  display: flex;\n  align-items: center;\n  justify-content: center;\n}\n.bulk-btn[_ngcontent-%COMP%]:hover {\n  background: var(--mat-sys-surface-variant);\n}\n.bulk-btn--present[_ngcontent-%COMP%] {\n  color: #2e7d32;\n}\n.bulk-btn--absent[_ngcontent-%COMP%] {\n  color: #c62828;\n}\n.bulk-btn--excused[_ngcontent-%COMP%] {\n  color: #f57f17;\n}\n.bulk-more[_ngcontent-%COMP%] {\n  border: none;\n  background: transparent;\n  width: 16px;\n  height: 16px;\n  padding: 0;\n  cursor: pointer;\n  border-radius: 3px;\n  display: flex;\n  align-items: center;\n  justify-content: center;\n  color: var(--mat-sys-on-surface-variant);\n}\n.bulk-more[_ngcontent-%COMP%]:hover {\n  background: var(--mat-sys-surface-variant);\n}\n.bulk-more[_ngcontent-%COMP%]   mat-icon[_ngcontent-%COMP%] {\n  font-size: 14px;\n  width: 14px;\n  height: 14px;\n}\n.status-cell[_ngcontent-%COMP%] {\n  width: 60px;\n  min-width: 60px;\n  max-width: 60px;\n  padding: 4px;\n  display: flex;\n  align-items: center;\n  justify-content: center;\n  border-right: 1px solid var(--mat-sys-outline-variant);\n}\n[_nghost-%COMP%]     cdk-row {\n  min-height: 44px;\n  border-bottom: 1px solid var(--mat-sys-outline-variant);\n}\n[_nghost-%COMP%]     cdk-row:hover {\n  background: color-mix(in srgb, var(--mat-sys-primary) 6%, transparent);\n}\n[_nghost-%COMP%]     cdk-row:hover .student-cell, \n[_nghost-%COMP%]     cdk-row:hover .percent-cell {\n  background: color-mix(in srgb, var(--mat-sys-primary) 8%, var(--mat-sys-surface-container));\n}\n.status-btn[_ngcontent-%COMP%], \n.status-empty[_ngcontent-%COMP%] {\n  width: 28px;\n  height: 28px;\n  border: none;\n  border-radius: 50%;\n  cursor: pointer;\n  font-size: 12px;\n  font-weight: 700;\n  display: flex;\n  align-items: center;\n  justify-content: center;\n  transition: transform 0.12s ease, box-shadow 0.12s ease;\n}\n.status-btn[_ngcontent-%COMP%]:hover {\n  transform: scale(1.1);\n  box-shadow: 0 2px 6px rgba(0, 0, 0, 0.15);\n}\n.status-chip--present[_ngcontent-%COMP%] {\n  background: #2e7d32;\n  color: #fff;\n}\n.status-chip--absent[_ngcontent-%COMP%] {\n  background: #c62828;\n  color: #fff;\n}\n.status-chip--excused[_ngcontent-%COMP%] {\n  background: #f9a825;\n  color: #fff;\n}\n.status-chip--free_attendance[_ngcontent-%COMP%] {\n  background: #1565c0;\n  color: #fff;\n}\n.status-empty[_ngcontent-%COMP%] {\n  background: transparent;\n  border: 1px dashed var(--mat-sys-outline);\n  color: var(--mat-sys-on-surface-variant);\n  cursor: pointer;\n}\n.status-empty[_ngcontent-%COMP%]:hover {\n  background: var(--mat-sys-surface-variant);\n}\n.status-cancelled[_ngcontent-%COMP%] {\n  width: 28px;\n  height: 28px;\n  font-size: 11px;\n  color: var(--mat-sys-on-surface);\n  opacity: 0.4;\n  display: flex;\n  align-items: center;\n  justify-content: center;\n  cursor: default;\n}\n[_nghost-%COMP%]     cdk-header-row {\n  position: sticky;\n  top: 0;\n  z-index: 5;\n  background: var(--mat-sys-surface-container);\n  border-bottom: 1px solid var(--mat-sys-outline-variant);\n  min-height: 56px;\n}\n/*# sourceMappingURL=headman-journal-grid.component.css.map */"], changeDetection: 0 });
};
(() => {
  (typeof ngDevMode === "undefined" || ngDevMode) && setClassMetadata(HeadmanJournalGridComponent, [{
    type: Component,
    args: [{ selector: "app-headman-journal-grid", standalone: true, changeDetection: ChangeDetectionStrategy.OnPush, imports: [CdkTableModule, MatMenuModule, MatIconModule], template: `
    <div class="journal-grid-wrapper"
         [attr.aria-busy]="loading"
         [style.pointer-events]="loading ? 'none' : ''">
      <table cdk-table [dataSource]="dataSource()">

        <!-- Sticky student name column -->
        <ng-container cdkColumnDef="student" sticky>
          <th cdk-header-cell *cdkHeaderCellDef class="student-header">\u0421\u0442\u0443\u0434\u0435\u043D\u0442</th>
          <td cdk-cell *cdkCellDef="let row" class="student-cell" [title]="row.displayName">
            {{ row.displayName }}
          </td>
        </ng-container>

        <!-- Dynamic date/lesson columns -->
        @for (col of columns(); track col.id) {
          <ng-container [cdkColumnDef]="col.id">
            <th cdk-header-cell *cdkHeaderCellDef class="date-header"
                [class.today-column]="col.isToday">
              <div class="date-label">
                <span class="date-label__weekday">{{ col.weekday }}</span>
                {{ col.displayDate }}
              </div>
              <div class="bulk-row" [attr.aria-label]="'\u041C\u0430\u0441\u0441\u043E\u0432\u0430\u044F \u043E\u0442\u043C\u0435\u0442\u043A\u0430 ' + col.weekday + ' ' + col.displayDate">
                <button type="button" class="bulk-btn bulk-btn--present"
                        (click)="bulkMark(col, 'present')"
                        title="\u041E\u0442\u043C\u0435\u0442\u0438\u0442\u044C \u0432\u0441\u0435\u043C \xAB\u043F\u0440\u0438\u0441\u0443\u0442\u0441\u0442\u0432\u043E\u0432\u0430\u043B\xBB">+</button>
                <button type="button" class="bulk-btn bulk-btn--absent"
                        (click)="bulkMark(col, 'absent')"
                        title="\u041E\u0442\u043C\u0435\u0442\u0438\u0442\u044C \u0432\u0441\u0435\u043C \xAB\u043D\u0435 \u0431\u044B\u043B\xBB">\u041D</button>
                <button type="button" class="bulk-btn bulk-btn--excused"
                        (click)="bulkMark(col, 'excused')"
                        title="\u041E\u0442\u043C\u0435\u0442\u0438\u0442\u044C \u0432\u0441\u0435\u043C \xAB\u0443\u0432\u0430\u0436\u0438\u0442.\xBB">\u0423</button>
                <button type="button" class="bulk-more"
                        [matMenuTriggerFor]="extraMenu"
                        title="\u0414\u0440\u0443\u0433\u0438\u0435 \u0441\u0442\u0430\u0442\u0443\u0441\u044B">
                  <mat-icon>more_horiz</mat-icon>
                </button>
                <mat-menu #extraMenu="matMenu">
                  @for (extra of extraStatuses; track extra.value) {
                    <button mat-menu-item (click)="bulkMark(col, extra.value)">
                      {{ extra.label }}
                    </button>
                  }
                </mat-menu>
              </div>
            </th>
            <td cdk-cell *cdkCellDef="let row" class="status-cell">
              @let cell = getCell(row.userId, col.id);
              @if (cell) {
                @if (cell.status === 'cancelled') {
                  <span
                    class="status-cancelled"
                    aria-disabled="true"
                    tabindex="-1"
                    [attr.aria-label]="'\u041E\u0442\u043C\u0435\u043D\u0435\u043D\u0430, ' + row.displayName + ', ' + col.displayDate"
                  >{{ cell.symbol }}</span>
                } @else {
                  <button
                    class="status-btn status-chip--{{ cell.status }}"
                    [matMenuTriggerFor]="cellMenu"
                    [matMenuTriggerData]="{ cell: cell, row: row }"
                    [attr.aria-label]="'\u0421\u0442\u0430\u0442\u0443\u0441: ' + cell.symbol + ', ' + row.displayName + ', ' + col.displayDate"
                  >{{ cell.symbol }}</button>
                }
              } @else {
                <button
                  class="status-empty"
                  [matMenuTriggerFor]="cellMenu"
                  [matMenuTriggerData]="{ cell: null, row: row, col: col }"
                  [attr.aria-label]="'\u041D\u0435\u0442 \u043E\u0442\u043C\u0435\u0442\u043A\u0438, ' + row.displayName + ', ' + col.displayDate"
                >\u2014</button>
              }
            </td>
          </ng-container>
        }

        <!-- Sticky right percentage column -->
        <ng-container cdkColumnDef="percent" stickyEnd>
          <th cdk-header-cell *cdkHeaderCellDef class="percent-header">%</th>
          <td cdk-cell *cdkCellDef="let row" class="percent-cell">
            {{ getPercent(row.userId) }}%
          </td>
        </ng-container>

        <tr cdk-header-row *cdkHeaderRowDef="displayedColumns(); sticky: true"></tr>
        <tr cdk-row *cdkRowDef="let row; columns: displayedColumns();"></tr>
      </table>

      <mat-menu #cellMenu="matMenu">
        <ng-template matMenuContent let-cell="cell" let-row="row" let-col="col">
          <button mat-menu-item (click)="setStatus(cell, row, col, 'present')">\u041F\u0440\u0438\u0441\u0443\u0442\u0441\u0442\u0432\u043E\u0432\u0430\u043B</button>
          <button mat-menu-item (click)="setStatus(cell, row, col, 'absent')">\u041D\u0435 \u0431\u044B\u043B</button>
          <button mat-menu-item (click)="setStatus(cell, row, col, 'excused')">\u0423\u0432\u0430\u0436\u0438\u0442. \u043F\u0440\u0438\u0447\u0438\u043D\u0430</button>
          <button mat-menu-item (click)="setStatus(cell, row, col, 'free_attendance')">\u0421\u0432\u043E\u0431\u043E\u0434\u043D\u043E\u0435 \u043F\u043E\u0441\u0435\u0449\u0435\u043D\u0438\u0435</button>
        </ng-template>
      </mat-menu>
    </div>
  `, styles: ["/* angular:styles/component:css;96d23ee4dadd0351173a2c24cacb4978d438399b4e876630f87d5f0bc401d343;C:/Users/maksd/IntelliJIDEA/rutcampustrack/frontends/web-panel/src/app/features/headman/journal/headman-journal-grid/headman-journal-grid.component.ts */\n:host {\n  display: block;\n}\n.journal-grid-wrapper {\n  overflow-x: auto;\n  border: 1px solid var(--mat-sys-outline-variant);\n  border-radius: 8px;\n  background: var(--mat-sys-surface);\n}\ntable {\n  border-collapse: separate;\n  border-spacing: 0;\n  width: 100%;\n}\n:host ::ng-deep cdk-header-row,\n:host ::ng-deep cdk-row {\n  display: flex;\n  align-items: stretch;\n}\n.student-header,\n.student-cell {\n  position: sticky;\n  left: 0;\n  z-index: 4;\n  width: 220px;\n  min-width: 220px;\n  max-width: 220px;\n  padding: 8px 12px;\n  background: var(--mat-sys-surface-container);\n  border-right: 1px solid var(--mat-sys-outline-variant);\n  white-space: nowrap;\n  overflow: hidden;\n  text-overflow: ellipsis;\n  font-size: 14px;\n  display: flex;\n  align-items: center;\n}\n.student-header {\n  font-weight: 600;\n  z-index: 6;\n}\n.percent-header,\n.percent-cell {\n  position: sticky;\n  right: 0;\n  z-index: 4;\n  width: 72px;\n  min-width: 72px;\n  max-width: 72px;\n  padding: 8px;\n  background: var(--mat-sys-surface-container);\n  border-left: 1px solid var(--mat-sys-outline-variant);\n  text-align: center;\n  display: flex;\n  align-items: center;\n  justify-content: center;\n  font-weight: 600;\n  font-size: 13px;\n}\n.percent-header {\n  font-weight: 600;\n  z-index: 6;\n}\n.date-header {\n  width: 60px;\n  min-width: 60px;\n  max-width: 60px;\n  padding: 4px 2px;\n  background: var(--mat-sys-surface-container);\n  display: flex;\n  flex-direction: column;\n  align-items: center;\n  justify-content: flex-start;\n  font-size: 11px;\n  font-weight: 600;\n  border-right: 1px solid var(--mat-sys-outline-variant);\n  gap: 2px;\n}\n.date-header.today-column {\n  background: color-mix(in srgb, var(--mat-sys-primary) 12%, transparent);\n}\n.date-label {\n  line-height: 1.2;\n  letter-spacing: 0.3px;\n}\n.date-label__weekday {\n  opacity: 0.7;\n  font-weight: 500;\n}\n.bulk-row {\n  display: flex;\n  gap: 2px;\n  margin-top: 2px;\n  align-items: center;\n}\n.bulk-btn {\n  border: none;\n  background: transparent;\n  width: 16px;\n  height: 16px;\n  padding: 0;\n  font-size: 10px;\n  font-weight: 700;\n  border-radius: 3px;\n  cursor: pointer;\n  display: flex;\n  align-items: center;\n  justify-content: center;\n}\n.bulk-btn:hover {\n  background: var(--mat-sys-surface-variant);\n}\n.bulk-btn--present {\n  color: #2e7d32;\n}\n.bulk-btn--absent {\n  color: #c62828;\n}\n.bulk-btn--excused {\n  color: #f57f17;\n}\n.bulk-more {\n  border: none;\n  background: transparent;\n  width: 16px;\n  height: 16px;\n  padding: 0;\n  cursor: pointer;\n  border-radius: 3px;\n  display: flex;\n  align-items: center;\n  justify-content: center;\n  color: var(--mat-sys-on-surface-variant);\n}\n.bulk-more:hover {\n  background: var(--mat-sys-surface-variant);\n}\n.bulk-more mat-icon {\n  font-size: 14px;\n  width: 14px;\n  height: 14px;\n}\n.status-cell {\n  width: 60px;\n  min-width: 60px;\n  max-width: 60px;\n  padding: 4px;\n  display: flex;\n  align-items: center;\n  justify-content: center;\n  border-right: 1px solid var(--mat-sys-outline-variant);\n}\n:host ::ng-deep cdk-row {\n  min-height: 44px;\n  border-bottom: 1px solid var(--mat-sys-outline-variant);\n}\n:host ::ng-deep cdk-row:hover {\n  background: color-mix(in srgb, var(--mat-sys-primary) 6%, transparent);\n}\n:host ::ng-deep cdk-row:hover .student-cell,\n:host ::ng-deep cdk-row:hover .percent-cell {\n  background: color-mix(in srgb, var(--mat-sys-primary) 8%, var(--mat-sys-surface-container));\n}\n.status-btn,\n.status-empty {\n  width: 28px;\n  height: 28px;\n  border: none;\n  border-radius: 50%;\n  cursor: pointer;\n  font-size: 12px;\n  font-weight: 700;\n  display: flex;\n  align-items: center;\n  justify-content: center;\n  transition: transform 0.12s ease, box-shadow 0.12s ease;\n}\n.status-btn:hover {\n  transform: scale(1.1);\n  box-shadow: 0 2px 6px rgba(0, 0, 0, 0.15);\n}\n.status-chip--present {\n  background: #2e7d32;\n  color: #fff;\n}\n.status-chip--absent {\n  background: #c62828;\n  color: #fff;\n}\n.status-chip--excused {\n  background: #f9a825;\n  color: #fff;\n}\n.status-chip--free_attendance {\n  background: #1565c0;\n  color: #fff;\n}\n.status-empty {\n  background: transparent;\n  border: 1px dashed var(--mat-sys-outline);\n  color: var(--mat-sys-on-surface-variant);\n  cursor: pointer;\n}\n.status-empty:hover {\n  background: var(--mat-sys-surface-variant);\n}\n.status-cancelled {\n  width: 28px;\n  height: 28px;\n  font-size: 11px;\n  color: var(--mat-sys-on-surface);\n  opacity: 0.4;\n  display: flex;\n  align-items: center;\n  justify-content: center;\n  cursor: default;\n}\n:host ::ng-deep cdk-header-row {\n  position: sticky;\n  top: 0;\n  z-index: 5;\n  background: var(--mat-sys-surface-container);\n  border-bottom: 1px solid var(--mat-sys-outline-variant);\n  min-height: 56px;\n}\n/*# sourceMappingURL=headman-journal-grid.component.css.map */\n"] }]
  }], null, { journalData: [{
    type: Input,
    args: [{ required: true }]
  }], loading: [{
    type: Input
  }] });
})();
(() => {
  (typeof ngDevMode === "undefined" || ngDevMode) && \u0275setClassDebugInfo(HeadmanJournalGridComponent, { className: "HeadmanJournalGridComponent", filePath: "src/app/features/headman/journal/headman-journal-grid/headman-journal-grid.component.ts", lineNumber: 278 });
})();

// src/app/features/headman/journal/headman-journal-page.component.ts
var _forTrack02 = ($index, $item) => $item.id;
function HeadmanJournalPageComponent_For_12_Template(rf, ctx) {
  if (rf & 1) {
    \u0275\u0275elementStart(0, "mat-option", 6);
    \u0275\u0275text(1);
    \u0275\u0275elementEnd();
  }
  if (rf & 2) {
    const s_r1 = ctx.$implicit;
    \u0275\u0275property("value", s_r1.id);
    \u0275\u0275advance();
    \u0275\u0275textInterpolate(s_r1.name);
  }
}
function HeadmanJournalPageComponent_Conditional_13_Template(rf, ctx) {
  if (rf & 1) {
    \u0275\u0275elementStart(0, "p", 7);
    \u0275\u0275text(1, "\u041D\u0430\u0436\u043C\u0438\u0442\u0435 \u043D\u0430 \u044F\u0447\u0435\u0439\u043A\u0443 \u0434\u043B\u044F \u0438\u0437\u043C\u0435\u043D\u0435\u043D\u0438\u044F \u0441\u0442\u0430\u0442\u0443\u0441\u0430.");
    \u0275\u0275elementEnd();
  }
}
function HeadmanJournalPageComponent_Conditional_14_Template(rf, ctx) {
  if (rf & 1) {
    \u0275\u0275element(0, "mat-progress-bar", 8);
  }
}
function HeadmanJournalPageComponent_Conditional_15_Template(rf, ctx) {
  if (rf & 1) {
    \u0275\u0275elementStart(0, "div", 9);
    \u0275\u0275element(1, "i", 12);
    \u0275\u0275text(2);
    \u0275\u0275elementEnd();
  }
  if (rf & 2) {
    const ctx_r1 = \u0275\u0275nextContext();
    \u0275\u0275advance(2);
    \u0275\u0275textInterpolate1(" ", ctx_r1.error(), " ");
  }
}
function HeadmanJournalPageComponent_Conditional_16_Template(rf, ctx) {
  if (rf & 1) {
    \u0275\u0275elementStart(0, "div", 10)(1, "div", 13);
    \u0275\u0275element(2, "i", 14);
    \u0275\u0275elementEnd();
    \u0275\u0275elementStart(3, "p", 15);
    \u0275\u0275text(4, "\u0412\u044B\u0431\u0435\u0440\u0438\u0442\u0435 \u043F\u0440\u0435\u0434\u043C\u0435\u0442");
    \u0275\u0275elementEnd();
    \u0275\u0275elementStart(5, "p", 16);
    \u0275\u0275text(6, " \u0412\u044B\u0431\u0435\u0440\u0438\u0442\u0435 \u043F\u0440\u0435\u0434\u043C\u0435\u0442 \u0438\u0437 \u0432\u044B\u043F\u0430\u0434\u0430\u044E\u0449\u0435\u0433\u043E \u0441\u043F\u0438\u0441\u043A\u0430, \u0447\u0442\u043E\u0431\u044B \u0430\u0432\u0442\u043E\u043C\u0430\u0442\u0438\u0447\u0435\u0441\u043A\u0438 \u0437\u0430\u0433\u0440\u0443\u0437\u0438\u0442\u044C \u0436\u0443\u0440\u043D\u0430\u043B. ");
    \u0275\u0275elementEnd()();
  }
}
function HeadmanJournalPageComponent_Conditional_17_Template(rf, ctx) {
  if (rf & 1) {
    \u0275\u0275element(0, "app-headman-journal-grid", 11);
  }
  if (rf & 2) {
    const ctx_r1 = \u0275\u0275nextContext();
    \u0275\u0275property("journalData", ctx_r1.journalData())("loading", ctx_r1.loading());
  }
}
var HeadmanJournalPageComponent = class _HeadmanJournalPageComponent {
  headmanApi = inject(HeadmanApiService);
  authService = inject(AuthService);
  subjects = signal([]);
  selectedSubjectId = signal(null);
  journalData = signal(null);
  loading = signal(false);
  error = signal(null);
  constructor() {
    effect(() => {
      const id = this.selectedSubjectId();
      if (id != null) {
        this.loadJournal();
      }
    });
  }
  getToday() {
    return (/* @__PURE__ */ new Date()).toISOString().split("T")[0];
  }
  /** Backend expects an explicit range — use a wide past window; future lessons are excluded by dateTo=today. */
  getRangeStart() {
    const d = /* @__PURE__ */ new Date();
    d.setFullYear(d.getFullYear() - 1);
    return d.toISOString().split("T")[0];
  }
  ngOnInit() {
    this.headmanApi.listSubjects(0, 100).pipe(catchError(() => of(null))).subscribe((res) => {
      if (res?._embedded) {
        const key = Object.keys(res._embedded)[0];
        const raw = res._embedded[key] ?? [];
        this.subjects.set(raw.map((s) => ({ id: s.id, name: s.name })));
      }
    });
  }
  onSubjectChange(id) {
    this.selectedSubjectId.set(id);
  }
  loadJournal() {
    const subjectId = this.selectedSubjectId();
    const groupId = this.authService.currentUser()?.groupId;
    if (!subjectId || !groupId)
      return;
    this.loading.set(true);
    this.error.set(null);
    this.journalData.set(null);
    this.headmanApi.getJournal(groupId, subjectId, this.getRangeStart(), this.getToday()).pipe(catchError(() => {
      this.error.set("\u041D\u0435 \u0443\u0434\u0430\u043B\u043E\u0441\u044C \u0437\u0430\u0433\u0440\u0443\u0437\u0438\u0442\u044C \u0434\u0430\u043D\u043D\u044B\u0435. \u041E\u0431\u043D\u043E\u0432\u0438\u0442\u0435 \u0441\u0442\u0440\u0430\u043D\u0438\u0446\u0443 \u0438\u043B\u0438 \u043F\u043E\u043F\u0440\u043E\u0431\u0443\u0439\u0442\u0435 \u043F\u043E\u0437\u0436\u0435.");
      return of(null);
    }), finalize(() => this.loading.set(false))).subscribe((data) => {
      this.journalData.set(data);
    });
  }
  static \u0275fac = function HeadmanJournalPageComponent_Factory(__ngFactoryType__) {
    return new (__ngFactoryType__ || _HeadmanJournalPageComponent)();
  };
  static \u0275cmp = /* @__PURE__ */ \u0275\u0275defineComponent({ type: _HeadmanJournalPageComponent, selectors: [["app-headman-journal-page"]], hostVars: 1, hostBindings: function HeadmanJournalPageComponent_HostBindings(rf, ctx) {
    if (rf & 2) {
      \u0275\u0275syntheticHostProperty("@routeFade", void 0);
    }
  }, decls: 18, vars: 4, consts: [[1, "page-header"], [1, "page-header__eyebrow"], [1, "page-card"], [1, "page-filters"], ["appearance", "outline"], ["placeholder", "\u0412\u044B\u0431\u0435\u0440\u0438\u0442\u0435 \u043F\u0440\u0435\u0434\u043C\u0435\u0442", 3, "ngModelChange", "ngModel"], [3, "value"], [1, "journal-hint"], ["mode", "indeterminate", "aria-label", "\u0417\u0430\u0433\u0440\u0443\u0437\u043A\u0430 \u0436\u0443\u0440\u043D\u0430\u043B\u0430"], ["role", "alert", 1, "page-error"], ["role", "status", "aria-live", "polite", 1, "page-empty"], [3, "journalData", "loading"], [1, "ph", "ph-warning-circle"], [1, "page-empty__icon"], [1, "ph", "ph-table"], [1, "page-empty__title"], [1, "page-empty__text"]], template: function HeadmanJournalPageComponent_Template(rf, ctx) {
    if (rf & 1) {
      \u0275\u0275elementStart(0, "div", 0)(1, "h1");
      \u0275\u0275text(2, "\u0416\u0443\u0440\u043D\u0430\u043B \u043F\u043E\u0441\u0435\u0449\u0430\u0435\u043C\u043E\u0441\u0442\u0438");
      \u0275\u0275elementEnd();
      \u0275\u0275elementStart(3, "span", 1);
      \u0275\u0275text(4, "\u0421\u0442\u0430\u0440\u043E\u0441\u0442\u0430");
      \u0275\u0275elementEnd()();
      \u0275\u0275elementStart(5, "div", 2)(6, "div", 3)(7, "mat-form-field", 4)(8, "mat-label");
      \u0275\u0275text(9, "\u041F\u0440\u0435\u0434\u043C\u0435\u0442");
      \u0275\u0275elementEnd();
      \u0275\u0275elementStart(10, "mat-select", 5);
      \u0275\u0275listener("ngModelChange", function HeadmanJournalPageComponent_Template_mat_select_ngModelChange_10_listener($event) {
        return ctx.onSubjectChange($event);
      });
      \u0275\u0275repeaterCreate(11, HeadmanJournalPageComponent_For_12_Template, 2, 2, "mat-option", 6, _forTrack02);
      \u0275\u0275elementEnd()()();
      \u0275\u0275template(13, HeadmanJournalPageComponent_Conditional_13_Template, 2, 0, "p", 7)(14, HeadmanJournalPageComponent_Conditional_14_Template, 1, 0, "mat-progress-bar", 8)(15, HeadmanJournalPageComponent_Conditional_15_Template, 3, 1, "div", 9)(16, HeadmanJournalPageComponent_Conditional_16_Template, 7, 0, "div", 10)(17, HeadmanJournalPageComponent_Conditional_17_Template, 1, 2, "app-headman-journal-grid", 11);
      \u0275\u0275elementEnd();
    }
    if (rf & 2) {
      \u0275\u0275advance(10);
      \u0275\u0275property("ngModel", ctx.selectedSubjectId());
      \u0275\u0275advance();
      \u0275\u0275repeater(ctx.subjects());
      \u0275\u0275advance(2);
      \u0275\u0275conditional(ctx.selectedSubjectId() && !ctx.loading() && !ctx.error() ? 13 : -1);
      \u0275\u0275advance();
      \u0275\u0275conditional(ctx.loading() ? 14 : -1);
      \u0275\u0275advance();
      \u0275\u0275conditional(ctx.error() ? 15 : !ctx.journalData() ? 16 : 17);
    }
  }, dependencies: [
    FormsModule,
    NgControlStatus,
    NgModel,
    MatSelectModule,
    MatFormField,
    MatLabel,
    MatSelect,
    MatOption,
    MatFormFieldModule,
    MatProgressBarModule,
    MatProgressBar,
    HeadmanJournalGridComponent
  ], encapsulation: 2, data: { animation: [
    trigger("routeFade", [
      transition(":enter", [
        style({ opacity: 0, transform: "translateY(8px)" }),
        animate("200ms cubic-bezier(0.16, 1, 0.3, 1)", style({ opacity: 1, transform: "translateY(0)" }))
      ])
    ])
  ] }, changeDetection: 0 });
};
(() => {
  (typeof ngDevMode === "undefined" || ngDevMode) && setClassMetadata(HeadmanJournalPageComponent, [{
    type: Component,
    args: [{ selector: "app-headman-journal-page", standalone: true, changeDetection: ChangeDetectionStrategy.OnPush, imports: [
      FormsModule,
      MatSelectModule,
      MatFormFieldModule,
      MatProgressBarModule,
      HeadmanJournalGridComponent
    ], animations: [
      trigger("routeFade", [
        transition(":enter", [
          style({ opacity: 0, transform: "translateY(8px)" }),
          animate("200ms cubic-bezier(0.16, 1, 0.3, 1)", style({ opacity: 1, transform: "translateY(0)" }))
        ])
      ])
    ], host: { "[@routeFade]": "" }, template: '<div class="page-header">\n  <h1>\u0416\u0443\u0440\u043D\u0430\u043B \u043F\u043E\u0441\u0435\u0449\u0430\u0435\u043C\u043E\u0441\u0442\u0438</h1>\n  <span class="page-header__eyebrow">\u0421\u0442\u0430\u0440\u043E\u0441\u0442\u0430</span>\n</div>\n\n<div class="page-card">\n  <div class="page-filters">\n    <mat-form-field appearance="outline">\n      <mat-label>\u041F\u0440\u0435\u0434\u043C\u0435\u0442</mat-label>\n      <mat-select\n        [ngModel]="selectedSubjectId()"\n        (ngModelChange)="onSubjectChange($event)"\n        placeholder="\u0412\u044B\u0431\u0435\u0440\u0438\u0442\u0435 \u043F\u0440\u0435\u0434\u043C\u0435\u0442"\n      >\n        @for (s of subjects(); track s.id) {\n          <mat-option [value]="s.id">{{ s.name }}</mat-option>\n        }\n      </mat-select>\n    </mat-form-field>\n  </div>\n\n  @if (selectedSubjectId() && !loading() && !error()) {\n    <p class="journal-hint">\u041D\u0430\u0436\u043C\u0438\u0442\u0435 \u043D\u0430 \u044F\u0447\u0435\u0439\u043A\u0443 \u0434\u043B\u044F \u0438\u0437\u043C\u0435\u043D\u0435\u043D\u0438\u044F \u0441\u0442\u0430\u0442\u0443\u0441\u0430.</p>\n  }\n\n  @if (loading()) {\n    <mat-progress-bar mode="indeterminate" aria-label="\u0417\u0430\u0433\u0440\u0443\u0437\u043A\u0430 \u0436\u0443\u0440\u043D\u0430\u043B\u0430"></mat-progress-bar>\n  }\n\n  @if (error()) {\n    <div class="page-error" role="alert">\n      <i class="ph ph-warning-circle"></i>\n      {{ error() }}\n    </div>\n  } @else if (!journalData()) {\n    <div class="page-empty" role="status" aria-live="polite">\n      <div class="page-empty__icon"><i class="ph ph-table"></i></div>\n      <p class="page-empty__title">\u0412\u044B\u0431\u0435\u0440\u0438\u0442\u0435 \u043F\u0440\u0435\u0434\u043C\u0435\u0442</p>\n      <p class="page-empty__text">\n        \u0412\u044B\u0431\u0435\u0440\u0438\u0442\u0435 \u043F\u0440\u0435\u0434\u043C\u0435\u0442 \u0438\u0437 \u0432\u044B\u043F\u0430\u0434\u0430\u044E\u0449\u0435\u0433\u043E \u0441\u043F\u0438\u0441\u043A\u0430, \u0447\u0442\u043E\u0431\u044B \u0430\u0432\u0442\u043E\u043C\u0430\u0442\u0438\u0447\u0435\u0441\u043A\u0438 \u0437\u0430\u0433\u0440\u0443\u0437\u0438\u0442\u044C \u0436\u0443\u0440\u043D\u0430\u043B.\n      </p>\n    </div>\n  } @else {\n    <app-headman-journal-grid\n      [journalData]="journalData()!"\n      [loading]="loading()"\n    ></app-headman-journal-grid>\n  }\n</div>\n' }]
  }], () => [], null);
})();
(() => {
  (typeof ngDevMode === "undefined" || ngDevMode) && \u0275setClassDebugInfo(HeadmanJournalPageComponent, { className: "HeadmanJournalPageComponent", filePath: "src/app/features/headman/journal/headman-journal-page.component.ts", lineNumber: 45 });
})();
export {
  HeadmanJournalPageComponent
};
//# sourceMappingURL=chunk-5GSR4UZE.js.map
