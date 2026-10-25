# Independent bounded Vue browser-adapter review

Вердикт: **CHANGES REQUIRED — два P2**. Original `0d30f35ce7be782f0dee4ab3783938e9b6e824ca` не следует считать готовым browser adapter до narrow corrections. Это findings в test helpers, не новые product defects.

Дата: 2026-10-24. Scope: только frozen parent diff четырёх файлов `tests/e2e/fixtures/{auth.ts,axe.ts,web-panel-route.ts,web-panel-route.control.mjs}` и адресная сверка accepted Vue counterparts. Назначение — независимое Sol6.1/high review; runtime model telemetry недоступна. Product/browser/DB/build/CI operations не выполнялись.

## P2-1 — exact password label не совпадает с текущей Vue label

**Путь:** `tests/e2e/fixtures/auth.ts:22`, также logout assertion `:53`.

`getByLabel('Пароль *', { exact: true })` ищет exact label text. Текущий `frontends/web-vue/src/auth/LoginPage.vue:44–70` содержит внутри одного `<label for="password">` не только `Пароль *`, но и nested password-toggle button с видимым текстом `Показать`/`Скрыть`. Playwright internal:label использует `getElementLabels(...)`, который для associated labels возвращает `elementText(...)`; exact internal matcher сравнивает normalized full text. Для исходного состояния получается `Пароль * Показать`, а не `Пароль *`.

**Causal case:** вызвать `loginAs` с любой допустимой canonical fixture intent. После успешного fill логина password locator не совпадает и helper не доходит до POST/login. После обычного logout тот же неверный exact locator ломает password-empty assertion. Finite control проверяет `webLoginIntent`, но не исполняет password locator.

**Влияние:** поддержанные helper login/logout paths блокируются корректной текущей формой. Исправление должно выбрать однозначный существующий password input/current carrier, сохранив парольный postcondition; product form ради legacy helper менять не требуется.

**Независимое доказательство:** прочитан installed Playwright selector source; выполнена только его чистая `elementText` function на минимальном DOM-shaped tree, соответствующем вложенности текущей label. Результат `label="Пароль * Показать", exactMatch=false, browserRun=false`. Это source-function reproduction, не browser run и не заявление о запущенном Vue flow.

## P2-2 — selection роли привязан к скрытому header на compact viewport

**Путь:** `tests/e2e/fixtures/auth.ts:32–42`.

Helper всегда ищет desired role в `.web-header-role`. Текущий `frontends/web-vue/src/shared/web.pcss:489` скрывает этот carrier в `rct-compact`; canonical mixin задаёт `width < 48rem`. Видимый responsive role carrier находится в `.web-nav-role` внутри раскрываемой кнопкой `Разделы` navigation (`AppShell.vue:93–105`).

**Causal case:** после исправления P2-1 использовать canonical STUDENT fixture, имеющую STUDENT+HEADMAN server grants, при Pixel7/другом viewport<48rem и `expectedLandingPath='/headman'`. `RoleSwitcher.vue` при нескольких ролях рендерит кнопки, поэтому `.web-role-label` отсутствует. Header buttons hidden; `getByRole` по умолчанию исключает hidden elements, `choice.count()` равен0. Helper выбирает fallback с несуществующим `.web-role-label` и не раскрывает реальный responsive selector. Аналогично ломается dual-role fixture с уже активной нужной ролью. Existing configured `mobile-chrome` project выбирает student/headman specs; перенос их canonical intent сделает этот mismatch достижимым.

**Влияние:** корректный mobile role carrier и server-granted HEADMAN selection недоступны общему helper. Исправление должно использовать видимый текущий carrier (при необходимости открыть `Разделы`), сохранить enabled/selectable constraint и exact `aria-pressed`/landing assertions, без вывода grants из `isHeadman`.

**Доказательство:** адресная source сверка helper, AppShell, RoleSwitcher, responsive PCSS/mixin и существующего Playwright project selection. Browser не запускался; case выведен из скрытия carrier и стандартной visibility semantics role locator.

## Остальной результат review

- `webLoginIntent` требует canonical explicit role/landing и не превращает `isHeadman` в granted role. Invalid legacy intent бросает `PENDING_VUE_AUTH_FIXTURE` до goto/fill/submit. Выбор actual role требует enabled server-rendered choice, существующую current read-only role не заставляет становиться selectable. Same-origin POST/login200 и exact landing checks сохраняют admission boundary.
- Route helper поддерживает только шесть явно названных current ADMIN/TEACHER маршрутов. Неизвестный legacy/other intent бросает `PENDING_VUE_ROUTE_ORACLE` до navigation/listeners; alias, silent skip или browser PASS не добавлены. Existing users/spec migration остаётся отдельным pending scope.
- Current API paths, ADMIN permission codes, root/main/owners/table captions, teacher group+subject catalogs, current lesson links и statistics metric rendering адресно сверены с Vue/client source. FULL profile wire permissions действительно содержат expanded permissions; проверка required permission не конфликтует с действующим admin client. Семестровые responses сохраняются по порядку и с contiguous pages.
- Required reads остаются same-origin GET200, дожидаются finished body и JSON object/array; unrelated response не подменяет обязательный companion. Rendered registry identities/counts и dashboard number text сравниваются с загруженными данными; teacher journal/stats не принимаются только по shell heading. Listener снимается через finally. Zero CRITICAL/SERIOUS axe threshold/tags сохранены; main landmark теперь должен быть единственным.

## Независимые проверки и точная команда source-function harness

Прочитаны frozen four-file diff и `/workspace/rct-pr3-runtime/VUE-BROWSER-ADAPTERS-0d30f35ce.md`. HEAD/status соответствовали frozen clean worktree. `git diff --check 0d30f35ce^ 0d30f35ce` — exit0. Авторские finite positive10/rejected22 и scoped strict TS PASS прочитаны как переданная evidence, reviewer их не повторял. `vueBrowserProof:false` остаётся точным пределом этих controls; они не проверяют найденные password/responsive selectors.

Для конкретного P2-1 выполнена следующая единственная pure source-function проверка (cwd `/workspace`, exit0); новых test files не создано:

```sh
node <<'JS'
const {source}=require('/workspace/rutcampustrack/tests/e2e/node_modules/playwright-core/lib/generated/injectedScriptSource.js');
const lines=source.split('\n');
for(let i=0;i<lines.length;i++)if(lines[i].includes('getElementLabels(')&&!lines[i].startsWith('function'))console.log(lines.slice(Math.max(0,i-5),i+5).join('\n'));
const start=source.indexOf('function elementText('),end=source.indexOf('\nfunction elementMatchesText(',start);
const run=new Function('HTMLInputElement','Node','normalizeWhiteSpace','shouldSkipForTextMatching',source.slice(start,end)+'; return elementText');
class Input {}
const text=value=>({nodeType:3,nodeValue:value,nextSibling:null});
const el=(...children)=>{for(let i=0;i<children.length;i++)children[i].nextSibling=children[i+1]??null;return {nodeType:1,firstChild:children[0]??null,shadowRoot:null,nextSibling:null}};
const input=Object.assign(new Input(),el(),{type:'password'});
const label=el(el(text('Пароль *')),text(' '),el(input,el(text('Показать'))));
const value=run(Input,{TEXT_NODE:3,COMMENT_NODE:8,ELEMENT_NODE:1},s=>s.replace(/\s+/g,' ').trim(),()=>false)(new Map(),label);
console.log(JSON.stringify({pureInstalledPlaywrightElementText:true,label:value.normalized,exactExpected:'Пароль *',exactMatch:value.normalized==='Пароль *',browserRun:false}));
JS
```

Здесь ordinary-node traversal и normalize-whitespace использованы для source-shaped tree; не выполнялись DOM browser queries, приложение, login POST или тестовый control suite. Browser/DB/HTTP/Docker/build/CI, широкие suites и source edits отсутствуют. Полное migrated users/spec inventory, unsupported legacy scenarios и фактический Vue browser proof остаются pending. Единственная запись reviewer — этот внешний immutable report.
