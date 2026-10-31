# Independent bounded canonical Vue spec migration review

Вердикт: **PASS по frozen source packet; подтверждённых P1/P2 нет**. Browser/backend acceptance не выполнена и не выводится из finite controls или test collection.

Дата: 2026-10-24. Frozen original: `e7bb0a7fd4d4ebd93be0c0f91bbdbb01dbac7641`, parent `6ff8874ad1417691c63e9c303c100ce87abc4721`. Scope — ровно9 test/config files: fixtures `users.ts`, `web-panel-route.ts`, `web-panel-route.control.mjs`; specs `auth.spec.ts`, `role-{student,headman,teacher,admin}.spec.ts`; `playwright.legacy-owned.config.ts`. Назначение — независимое Sol6.1/high review; runtime model telemetry недоступна.

## Role intent и denial boundaries

- Users fixture использует current STUDENT/TEACHER canonical homes; HEADMAN теперь отдельный explicit role intent того же synthetic identity, а не вывод из academic `isHeadman`. HEADMAN callers явно задают role и `/headman`. Accepted auth helper остаётся неизменным: required current grant/visible enabled selector, read-only/current role handling, pressed state и exact canonical landing сохранены. Новых accounts/grants/продуктовых bypass нет.
- Role specs наблюдают same-origin request events **до** denied navigation, используют актуальные target endpoint families и требуют canonical own home/owner плюс отсутствие чужих owners. Старый teacher assignments endpoint не используется как единственное свидетельство запрета; `/api/v1/teacher/*` и `/api/v1/student/*` наблюдаются непосредственно. STUDENT `/headman` — именно HEADMAN-only home, а не разрешённый delegated assistant subroute.
- Адресно исключён потенциальный ложный foreign-read matcher: разрешённый STUDENT bootstrap запрос `/api/academic/assistants/me/permissions` не попадает под ADMIN `admin-access|users|groups` selector. Текущее assistant nav permission refresh не превращается в denied-target read.
- Auth spec сохраняет реальный login/owned schedule/logout/deep-link denial, добавляет exact internal redirect и отсутствие private shell/owner. Invalid password требует server401/body finished, guest error и очищенный password. Удалённый legacy a11y skip заменён настоящим current HEADMAN data/a11y intent; zero CRITICAL/SERIOUS threshold не ослаблен.

## Data-to-DOM oracles

- Required read queue остаётся same-origin GET200 с обязательными finished/decode и object/array checks. Concurrent detail consumers заранее резервируют индексы и получают разные response objects; registered listener обеспечивает последовательный cursor. Pagination сохраняет successive responses, проверяет contiguous safe integer boundaries и client bound; collection shape не превращает неизвестные данные в пустой feature. Rendered count теперь должен быть non-negative safe integer.
- STUDENT schedule сверяет session role/current semester, выбранную неделю, количество cards/empty days, subject/time/room/cancelled state. Statistics сверяет server metrics/count denominators и subject rows с current number format/sort. Requests сверяет exact first OPEN page bounds, details identity/kind/status, pending cards, lesson expansion и readonly/budget-disabled actions. No-semester open-request intent явно отказывает; archive не подставляется вместо него.
- HEADMAN home сверяет оба independent queue counts, actual lesson/subject identities и attendance links, current lesson roster/metrics/actions и today's homework. Schedule сверяет actual semester selection/options, slots/policy, шестидневную matrix и assignment/subject/teacher/room cells. Эти carriers адресно сопоставлены с HomePage/SchedulePage и их shared typed clients.
- EXCUSE и LATE_CHECKIN сохраняются как **два отдельных queue сценария** `/headman/requests?type=...`: route query разрешает только эти значения; response matcher требует совпадающий type, OPEN bucket, page0/size20; DOM требует current type/bucket и соответствующие request kind/status. Counts на home отдельно требуют size1 для каждого type. Это не aliases двух старых сценариев на один общий shell.
- Existing ADMIN/TEACHER rendered oracles не заменены heading-only proof: current permissions, registry identities/counts, dashboard values, assigned group AND subject options, journal lesson/student identities и statistics percentages/denominators сохраняются. Main/current nav/loading/error/a11y postconditions остаются. Unsupported retired routes/extra query/hash/unknown intent дают `PENDING_VUE_ROUTE_ORACLE` до navigation/listener registration; Angular tables/retired APIs не возрождаются.

## Privacy/config preservation

Все пять migrated spec files используют existing `artifact-privacy` wrapper. Reporter/private-artifact validation выполняется до credentials; wrapper, safe reporter и их source не менялись.

Независимое in-memory сравнение parent/current config подтвердило: **только три testMatch regex replacements**, остальные bytes идентичны. Private reporter, trace/video/screenshot off, serial workers/parallel policy, inherited project/use/environment guard wiring сохранены. Pure сопоставление regex с текущими spec filenames даёт ровно7 desktop files (прежние2 плюс новые5) и3 mobile files (прежний1 плюс новые2), без случайного захвата соседних suites. Это не запуск Playwright collection/test.

## Evidence и ограничения

Прочитаны frozen nine-file diff, все изменённые specs/control/helper branches и релевантные actual Vue auth/shell/router, Student schedule/statistics/requests/cards/metrics/autopage, Headman home/schedule/requests/context и shared API normalizers. Accepted prior auth correction не переоткрывалась. Frozen author HEAD/parent и clean status совпали; `git diff --check e7bb0a7fd^ e7bb0a7fd` — exit0.

Author finite control ONE-run positive19/rejected47, `vueBrowserProof:false`, scoped TypeScript0 и list-only collection переданы в packet. Reviewer не повторял control/TS/list/browser/backend/build/CI. Эти числа относятся к adapter controls; их bodies во многих случаях synthetic и не доказывают actual DOM rendering, role admission или реальные API данные. Независимо выполнены только source reads, diff checks и pure config-regex/byte comparison. Initial scratch comparison ошибочно распознал reporter path как regex; после ограничения сравнения testMatch literals проверка прошла, продуктового изменения не потребовалось.

Фактический run migrated auth/five-role paths на owned real Vue edge остаётся pending. Старые отдельно выбранные `admin-create-user`/`student-excuse` cases этим девятифайловым packet не мигрированы; их известные legacy assumptions и другие global replacement gates остаются отдельным остатком. Source PASS не означает готовность всей legacy-owned config к зелёному runtime запуску или полную замену Angular.

Reviewer не менял продуктовые исходники, tests/config, credentials, БД, сеть или процессы; браузер/контейнер/remote/CI не запускал. Единственная запись — этот внешний immutable report.
