# Вложения заявок — source-ready, 2026-10-01

Назначенный sole writer: admin_recovery_sol61, Sol6.1/high, S3 access/lifecycle. WT admin-group-promotion-20260927; перед изменениями normal merge main a6fd83e8 выполнен в 2bba9585. Собственные и чужие evidence сохранены. Backend и API/права не изменены.

Принятые источники: JS-STUDENT-27 и JS-STUDENT-WEB-07 в `docs/research/reference-rutcampustrack-design/knowledge/job-stories.md`; AttachmentList в `docs/design/COMPONENT_REGISTRY.md`: имя/размер/«Открыть», ошибка внутри списка без исчезновения карточки. Авторский picker/preview/remove, проверка metadata limits, authenticated open и lifecycle уже существуют; их не переписывали.

## Конкретное исправление

Старосте доступны «Открыть» (существующий изолированный popup helper) и «Скачать» через существующий authenticated blob API. Поздний blob не публикуется после collapse, изменения списка/API/offline/permissions или unmount. Все созданные object URL имеют таймер 60 секунд и немедленную очистку при смене контекста/ошибке/teardown; ожидающий popup закрывается. Ошибки 401/403 запрещают повторные действия с файлами текущей заявки до обновления списка, 404/410 делают конкретное вложение недоступным. 5xx/сеть допускают повтор. EXPIRED/прошедший expiresAt показывает текст об истёкшем сроке. Ошибка details видна также в архиве. Cached details очищаются при загрузке нового списка; поздние details не могут восстановить их.

Права остаются серверными: client GET `/api/attendance/requests/{requestId}/attachments/{attachmentId}`; generation-bound client проверяет session owner после blob response. Headman authority на сервере — текущий староста своей группы либо помощник с MANAGE_EXCUSES. Frontend повторяет существующее условие MANAGE_EXCUSES для локального отключения и очистки; дополнительных прав не вводит.

Фрагмент download helper:

```ts
const blob = await deps.download()
if (!isCurrent()) return
objectUrl = deps.createObjectUrl(blob)
if (!isCurrent()) return
deps.save(objectUrl)
// URL либо передан владельцу bounded cleanup, либо освобождён в finally.
```

## Точный inventory

Изменены:
- `frontends/mobile-core/src/features/headman-requests/HeadmanRequestsScreen.vue`
- `frontends/mobile-core/src/features/requests/request-attachment-action.ts`
- `frontends/mobile-core/src/features/requests/request-attachment-action.test.ts`

Создан: `.agent/evidence/request-attachments-20261001.md`. Удалённых файлов нет. StudentFeatureOwner/App/auth/SW/offline/maps/contracts/generated/config/lockfiles/backend не изменены.

## Проверки

Из `frontends`:
- `node node_modules/vitest/vitest.mjs run mobile-core/src/features/requests/request-attachment-action.test.ts mobile-core/src/features/requests/request-attachment-preview.test.ts mobile-core/src/features/requests/attachment-validation.test.ts` — exit0, 43/43 PASS. Новые meaningful download cases: owner/generation/dispose при позднем ответе, invalidate при allocation, exception при save и release, 403/404/410/503, поздний denial. Существующие open/preview/validation cases PASS.
- `npm run typecheck --workspace @rct/pwa-vue` — exit0 после итогового изменения Vue.
- scoped ESLint всех трёх файлов с `--max-warnings=0` и отключением только трёх форматных правил `vue/max-attributes-per-line`, `vue/singleline-html-element-content-newline`, `vue/html-indent` — exit0. Полный строгий ESLint этих файлов FAIL: 141 warnings, 0 errors, включая старое форматирование всего HeadmanRequestsScreen. Форматирование всего экрана не входит в функциональную правку; semantic rules не отключались.
- `git diff --check -- <три продуктовых файла>` — exit0.

Docker/Gradle/общий build и серверные тесты не запускались. Независимое S3 review и browser/runtime acceptance выполняет root на общем стенде; source checks не считаются runtime PASS.

## Простой browser acceptance path

1. На disposable fixture студент открывает «Заявки» → новая уважительная заявка для доступной пары → JPEG/PNG/PDF. Проверить имя/размер/preview, удалить и снова выбрать файл; отправить. Автор раскрывает созданную заявку и открывает вложение; сравнить содержимое.
2. Текущий староста той же группы открывает «Заявки группы» → созданная заявка → детали → «Открыть», затем «Скачать». Содержимое и имя скачанного файла соответствуют выбранному студентом. Повторить через помощника с MANAGE_EXCUSES; помощник без права не получает файл.
3. При замедленном ответе нажать действие и сразу скрыть детали, сменить вкладку/страницу, выйти с экрана либо сменить API/session owner: старый response не открывает/не скачивает файл; ожидающее окно закрывается. На смене permissions/offline старые details/URL очищаются.
4. Для expired descriptor увидеть текст истёкшего срока без действий. Для реального 404/410/403 при получении файла увидеть понятный alert в его строке, старый URL очищен, карточка не исчезает; 403 блокирует файлы заявки до refresh. Для сети/5xx остаётся повторная попытка. После refresh результат перечитывается через API.

Для негативных HTTP UI состояний допустим bounded browser response interception; это не доказательство серверной авторизации. Серверное denial проверять настоящим пользователем/правами общего disposable fixture. Нельзя менять пароли/права реальных учётных записей. Никакой ticket/token/credential/file payload не хранить в evidence.

## Correction после независимого review 891c60e1

Review: два P2. Lifecycle reset при toggle стирал 404/410 и ошибки, хотя cached details оставались; второй denial тоже стирал первый. Нереактивный Date.now оставлял визуально активную кнопку после expiry и давал silent click. Helper blob fencing/cleanup прошёл review и не изменён.

Correction: `cancelAttachmentActions` отменяет pending, закрывает ожидающие popup и очищает URL, сохраняет ошибки/known-unavailable. Отказы объединяются. Только `resetAttachments` на реальном list refresh/context change снимает server-state вместе с cached details. Reactive clock + ближайший expiry timeout обновляет русский expired state без click; delayed/throttled click синхронно обновляет clock и очищает старые resources. Expiry timer очищается при reset/unmount.

Точный correction inventory: изменены `HeadmanRequestsScreen.vue`, существующий `request-attachment-action.test.ts` и этот evidence. `request-attachment-action.ts` product helper после 891c60e1 не менялся. Остальной запрещённый scope/backend без изменений.

Affected verification, без повтора исходных 43/helper cases:
- `node node_modules/vitest/vitest.mjs run --config pwa-vue/vite.config.ts mobile-core/src/features/requests/request-attachment-action.test.ts -t 'headman attachment availability transitions'` — exit0, **4/4 PASS**, 22 других теста намеренно исключены selector. Тесты выполняют настоящие события и рендер текущего HeadmanRequestsScreen, используя существующий Vue custom renderer pattern; client template компилируется из неизменённого SFC source, т.к. существующий config импортирует SSR setup в Node. Native v-model host surface минимальная, сторонних DOM packages/config/platform не добавлено.
  - 410 → collapse/reopen cached details → другая карточка → 404 второго файла: оба alert и disabled сохраняются; API refresh повторно получает detail и снимает known denial.
  - Таймер наступления expiry убирает действия и показывает русский текст без click.
  - Clock jump с задержанным таймером: click показывает expired и не вызывает download.
  - Ещё pending expiry timeout очищается на list refresh и unmount.
- Финальный `npm run typecheck --workspace @rct/pwa-vue` — exit0.
- Scoped ESLint двух correction source/test файлов с теми же только тремя отключёнными форматными правилами — exit0; остальные правила активны.
- Scoped `git diff --check` — exit0.

Промежуточные проверки тестовой обвязки: sandbox блокировал esbuild config read; targeted require_escalated разрешён. Затем SSR setup без SSR context и native v-model host surface давали тестовый FAIL; исправлено в test-only fixture, финальные четыре перехода PASS без unhandled errors. Эти FAIL не являются дополнительными продуктовыми дефектами. Docker/Gradle/build/backend не запускались. Требуется affected independent recheck перед интеграцией исходного и correction commit.

## Runtime correction: период заявки и русская причина

Root real-API evidence `request-date-diagnosis-20261001.json`: synthetic request `6abe3d5253205ba7b5617f6e`, list `coverageStart/End=2026-09-30`, `reason=other`; detail lessonId3 `date=2026-10-01`. API period — LocalDate, поэтому frontend timezone correction неприменим. Raw aggregation reader HeadmanRequestService конвертировал Mongo Date через UTC, тогда как repository detail использует Mongo LocalDate converter. BSON-полуночь MSK — предыдущий UTC-день. Enum raw Mongo хранится lowercase, detail API выводит canonical enum.name.

Correction использует текущий Mongo conversion service для raw Date → LocalDate; write/filter `mongoDate` уже использовал `convertToMongoType` и не меняется. Нет `+1 day`, fixed-zone workaround, изменений исторических данных, timezone/config/schema. List reason нормализуется в canonical uppercase code; API по-прежнему отдаёт enum-код. UI отображает принятые пять student reason labels и LATE_CHECKIN по-русски; `OTHER/other` → «Другое». Date formatter UI не меняется.

Exact inventory (перед изменениями согласован с root):
- `services/attendance-service/attendance-app/src/main/java/ru/rutcampustrack/attendance/studentrequest/HeadmanRequestService.java`
- `services/attendance-service/attendance-app/src/test/java/ru/rutcampustrack/attendance/studentrequest/StudentRequestDomainIT.java`
- `frontends/mobile-core/src/features/headman-requests/HeadmanRequestsScreen.vue`
- `frontends/mobile-core/src/features/requests/request-attachment-action.test.ts`
- этот own evidence.

Meaningful Mongo regression `headmanMongoDatesMatchDetailAndInclusiveCoverageInMoscow` сохраняет ticket/snapshot через native repository под Europe/Moscow, проверяет actual BSON `2026-09-30T21:00:00Z` и lowercase enum, затем совпадение list/detail на LocalDate2026-10-01, canonical OTHER, inclusive same-day filter и отсутствие match на соседние дни. JVM timezone восстанавливается в finally.

Affected UI check: `node node_modules/vitest/vitest.mjs run --config pwa-vue/vite.config.ts mobile-core/src/features/requests/request-attachment-action.test.ts -t 'renders Russian reason'` — exit0, 2/2 PASS, остальные26 намеренно исключены. Финальный PWA Vue typecheck и scoped semantic ESLint — exit0. Исходные helper/expiry cases повторно не запускались.

Root granted sole HEAVY after stand88216 cleanup. First invocation6911, log `request-date-it-20261001-141140.log` SHA256 `774AB7B61B4A874ED4B44F903B95EAD368043CE300573A9A1DE7B201ABA8202D`: compileJava/compileTestJava Attendance PASS, BUILD SUCCESSFUL63s/exit0. Но task `test` исключает *IT и допускает zero matching; свежий report **0 tests**. Это tooling invocation defect, **не Mongo test PASS**. Старый integrationTest XML00:09 mixedOwner не относится к этому запуску. Testcontainers label inventory пуст.

Root разрешил corrected targeted task в том же lease. Command: `:services:attendance-service:attendance-app:integrationTest --tests ru.rutcampustrack.attendance.studentrequest.StudentRequestDomainIT.headmanMongoDatesMatchDetailAndInclusiveCoverageInMoscow`, JDK `C:/Users/maksd/.jdks/ms-21.0.10`, `--no-daemon --no-parallel --max-workers=1 --no-problems-report --console=plain --system-prop=org.gradle.java.compile-classpath-packaging=true`. Handle36813 terminal exit0, BUILD SUCCESSFUL80s. Log `request-date-it-r2-20261001-141740.log`, SHA256 `BBCA1EA22D6DDFE06445C239B1118369613B0853B78C38C84D27E0EB42A0E232`. Fresh14:19 XML `attendance-app/build/test-results/integrationTest/TEST-ru.rutcampustrack.attendance.studentrequest.StudentRequestDomainIT.xml`: **1 testcase `headmanMongoDatesMatchDetailAndInclusiveCoverageInMoscow()`, 0 failures, 0 errors, 0 skipped**. Actual Mongo regression PASS. Docker `ps -a --filter label=org.testcontainers` EMPTY; HEAVY возвращён root. Дополнительные запуски не нужны.

Новое решение root/владельца: backend-first, UI/manual/deploy позже. Backend freeze содержит ровно три файла: HeadmanRequestService.java, StudentRequestDomainIT.java, этот own evidence. Уже готовые HeadmanRequestsScreen.vue и request-attachment-action.test.ts сохраняются в отдельном UI commit и не переносятся сейчас. Никаких новых UI работ после решения не проводилось. Original backend goal: list/detail показывают одну и ту же LocalDate занятия, coverage filter включительно совпадает с native persisted LocalDate, API сохраняет canonical reason enum. Независимый reviewer проверяет native codec compatibility и filter boundaries; root runtime acceptance после интеграции отдельно от targeted Mongo PASS.
