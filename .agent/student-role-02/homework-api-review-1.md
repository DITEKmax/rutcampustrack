FAIL — четыре MEDIUM finding, Critical/High нет.

1. **MEDIUM — переполнение `id` превращает malformed input в 500.**
   `StudentApi.java:168`, `StudentQueryService.java:144`. Regex допускает строку цифр любой длины, после чего выполняется необработанный `Long.parseLong`. Влияние: нарушены 400/ProblemDetails contract и error handling. Воспроизведение: `PUT /api/v1/student/homework/9223372036854775808/completion` с валидным JWT и boolean body; ожидается `400 INVALID_REQUEST` без gRPC-вызова, фактически возникает немаппированный `NumberFormatException` и 500.

2. **MEDIUM — `completed` принимает не-boolean и может изменить данные.**
   `StudentApiModels.java:238`. `@NotNull Boolean` проверяет только null. BFF не задаёт строгую coercion policy; Jackson 2.18.2 использует `CoercionAction.TryConvert`, `ALLOW_COERCION_OF_SCALARS=true`, а integer `0/nonzero` преобразует в `false/true`. Влияние: запрос вне опубликованной схемы может записать completion. Воспроизведение: `{ "completed": 1 }` проходит как `true`, достигает gRPC и возвращает 200 вместо `400 INVALID_REQUEST`; строковые `"true"`/`"false"` также принимаются.

3. **MEDIUM — отсутствие активного семестра ошибочно мапится в 403.**
   `MobileAcademicClient.java:34-36,69-70`; подтверждающий domain intent — `AcademicGrpcServiceImpl.java:397-400`. `activeSemester()` использует generic mapping `NOT_FOUND → 403 OUT_OF_SCOPE`, поэтому GET и PUT Homework завершаются до domain mutation. Влияние: системное отсутствие активного семестра выдаётся за пользовательский authz failure вместо `503 DEPENDENCY_UNAVAILABLE`. Воспроизведение: валидный STUDENT JWT, local Academic `GetActiveSemester → NOT_FOUND`; оба Homework endpoint возвращают 403.

4. **MEDIUM — ProblemDetails не получают `Cache-Control: no-store`.**
   `MobileProblemHandler.java:34-42,76-79`, `MobileIdentityFilter.java:53-59`. No-store выставляется только на успешных ответах в `StudentApiController.java:51-59`; общего cache filter нет. Влияние: нарушение response contract и возможность stale/private caching для 400/401/403/404/503. Воспроизведение: invalid range, missing token или Academic NOT_FOUND; ProblemDetails приходит без `Cache-Control: no-store`. Текущий HTTP IT проверяет header только для 200.

Repair contract:

- **Defect/evidence:** четыре случая выше.
- **Correction:** безопасный bounded parse `id`; строгая type-scoped десериализация boolean без глобальной смены ObjectMapper; Homework-specific `NOT_FOUND active semester → 503`; no-store на всех Homework success/error paths, включая pre-auth 401.
- **Scope:** BFF Homework parsing/deserialization, Homework-specific Academic mapping, response cache policy, Java-first contract и соответствующие runtime tests. Не менять generic Today/schedule mapping без отдельного решения.
- **Verification:** HTTP cases для overflow, `1`, `"true"`, null/missing, настоящих true/false; подтверждение отсутствия gRPC-вызова на 400; active-semester NOT_FOUND → 503; no-store для 400/401/403/404/503; OpenAPI snapshot и generated TS drift; BFF compile/tests; соседние Today/check-in error regressions; scoped whitespace и security scan.
- После исправления обязательна свежая независимая recheck затронутого diff.

Принятое evidence: PostgreSQL concurrency 3/3 PASS, HTTP→gRPC 5/5 PASS, snapshot-without-update 4/4 PASS, прежние compiler/unit/TS/lint PASS, Gitleaks 22-file snapshot PASS, npm audit — 0 уязвимостей. Семантика прежнего OpenAPI после исключения новых Homework-узлов совпала с baseline. Финальная SHA-256 сверка manifest: 22/22 совпали; `git -c core.whitespace=cr-at-eol diff --check` exit 0. Обычный `diff --check` exit 2 относится только к CRLF generated OpenAPI и не является продуктовым finding. Trivy DB, browser/full-role и real Telegram остаются непокрытыми внешними/следующими gates.
