# Independent narrow role-home review — a075cc874

**PASS source review. Подтверждённых P1/P2 в заданном navigation glue не найдено.**

Scope: frozen `a075cc8742effac3f2a5a6496c304e28f4d1ccba`, семь файлов role-home/router/App/AuthMethods/ProfilePage notice/RoleLanding/tests. Полный AUTH/profile26 не переоткрывался. Author checkout позднее переместился наf823; scoped diff подтвердил отсутствие изменений рассматриваемых семи файлов относительно frozen a075. Источники и original commit проверены, unrelated student corrections не оценивались.

## Проверенные границы

- **Own-role entry.** chooseRoleHome фильтрует route name по собственному role prefix, requiredRoles, navHidden и parameterized details. STUDENT own home приоритетнее delegated HEADMAN links/shared map и порядка registry. HEADMAN/TEACHER аналогично используют canonical home; неизвестный/отсутствующий own route даёт settings fallback, не первый чужой раздел.
- **ADMIN permission mapping.** Existing GET me использует captured createWebTransport, assertCurrent после decode и явную проверку userId. Dashboard выбран только при DASHBOARD_VIEW/FULL assigned profile; иначе первая разрешённая собственная ADMIN page. Mapping USERS_VIEW/GROUPS_VIEW/SEMESTERS_VIEW/CAMPUS_MAP_MANAGE и DASHBOARD_VIEW сверено с actual assembled root route names; hidden attendance/detail routes не становятся home. Unknown/unassigned/no-permission не трактуются как grant и ведут в account settings с notice/retry.
- **Old async continuation.** Captured identity проверяется перед выбором home и settings fallback; чужой поздний Admin response не перенаправляет новую identity. Авторитетный transport сам также отбрасывает старого owner. Нет новых backend writes или permission cache, позволяющего принять старый profile.
- **Return targets.** internalReturnPath разрешает только scalar leading-single-slash app path без backslash/control/space. Router дополнительно resolve-проверяет matched internal non-guest/non-root/non-unavailable destination; последующая navigation снова проходит existing role/helper admission. Внешний origin/scheme/protocol-relative target не принят. Query/hash не реконструируются с потерей данных; login method tabs и App continuation передают их дальше.
- **Admission/role changes.** Allowed explicit protected routes после bootstrap не заменяются canonical home без причины. При изменении activeRole App отправляет на собственную role home. No activeRole сохраняет RoleLanding/существующий selector, не выбирает чужой feature. Settings fallback сохраняет свой notice, retry снова проверяет доступ; password recovery не вытесняется login redirect.

## Независимая адресная проверка

Один лёгкий Node/установленный TypeScript source harness исполнил точный router guard с real Vue refs и deferred AdminAccessClient GET stub: начать ADMIN home → сменить identity+snapshot на другого STUDENT → завершить старый getMe FULL profile. Получено `navigation=false`, `homeChoices=0`, `notice=''`: старый ответ не выбирает Admin page и не пишет fallback notice новой identity. Это async source-causality check, не browser/backend admission.

Собственное `git diff --check a075cc874^ a075cc874` — exit0. Четыре meaningful role-home choice/security tests прочитаны; повторно не запускались без отдельного дефекта. Product/source/server/dependencies/Git refs reviewer не менял, детей/heavy/Gradle/CI/широкого browser review не было; создан только этот immutable report. Запрошенная модель/effort gpt-6.1-sol/high, независимой runtime telemetry нет.

## Внешнее evidence и пределы

Прочитан `/workspace/rct-pr3-runtime/web-foundation/role-home-a075.json`, exact sourcea075: real protected path/query/hash через auth tabs+password admission, root STUDENT Today, allowed explicit schedule после bootstrap, реальные role PUT STUDENT→HEADMAN→STUDENT canonical homes, ignored external target, errors[]. Эти шесть checks — авторское runtime evidence, не собственный browser PASS reviewer.

Isolated author registry содержал старый ограниченный ADMIN inventory; поэтому actual ADMIN Dashboard UI/native full registry landing этим receipt **не подтверждены**. Проверка assembled root с настоящими ADMIN records/permissions остаётся назначенной runtime приёмкой root. Source mapping и stale async owner проверены независимо; отсутствие изолированного Dashboard не признано дефектом общего glue.

Новых source corrections по этому пакету не требуется. Итог не объявляет все AUTH/ADMIN/browser states проверенными и не расширяет исходный narrow scope.
