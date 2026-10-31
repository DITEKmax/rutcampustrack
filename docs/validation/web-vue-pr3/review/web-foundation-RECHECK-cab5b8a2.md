# Независимая узкая recheck webcab5b8a2

Результат: **PASS source correction; late401 owner P2 закрыт, новых подтверждённых P1/P2 нет**.

Scope: только четыре файла `5ab473df..cab5b8a2f9149cf9da22da0d411bf37739dd9746` и необходимые async boundaries существующего StudentApi. Предыдущие четыре исправления5ab повторно целиком не оценивались. Reviewer read-only, без source edits/commits/push/install/heavy/CI. Запрошенная модельgpt-6.1-sol/high; runtime telemetry недоступна.

## Закрытие замечания и влияние

- `createStudentApi()` захватывает immutable owner identity при создании feature. Token getter проверяет owner перед первоначальной отправкой и перед StudentApi retry; onUnauthorized проверяет owner также до/после ожидаемого refresh. Поэтому obsolete refresh, завершившийся без применения результата, больше не разрешает повтор старой команды с bearer новой identity.
- Owner включает userId/sessionId/sessionVersion/rolesVersion/activeRole/readOnly. Смена аккаунта/роли/сессии/authority version запрещает старому adapter дальнейшие запросы. useWebHomework создаёт scoped adapter на instance, глобальный studentApi удалён.
- Ordinary refresh той же authority меняет token, но сохраняет identity и generation: исходный feature/pending state остаётся владельцем completion и принимает ACK. Logout всё ещё очищает snapshot и увеличивает generation. Role selection всё ещё сразу очищает snapshot/увеличивает generation; принятие новой authority сохраняет предыдущий guard старых callbacks. Authority failure также сохраняет snapshot=null/generation change. Существующие request/mutationRevision guards не ослаблены.
- Новые negative/positive regression tests проверяют отсутствие побочного эффектаB и успешный same-session retry на actual adapters. Четыре авторских regressions/typecheck/build/lint PASS сообщены root; reviewer их заново не запускал.

## Независимые дешёвые проверки

`git diff --check cab5b8a2^..cab5b8a2` — PASS.

Однократный dependency-free V8/TypeScript causal harness использовал исходники ровноcab5 (session.ts, app/api.ts, actual StudentApi), минимальные Vue/auth transport stubs и четыре целевых последовательности:

| Сценарий | Результат старого completion | Повтор PUT | Generation |
| --- | --- | --- | --- |
| A401 → held refresh → logoutA/loginB → late refresh | WebAuthorityChangedError | Нет, только bearerA первоначально | 1→3 |
| A401 → обычный refreshA | completed ACK | Да, bearer refreshedA | 1→1 |
| A401 → held refresh → role HEADMAN ACK → late refresh | WebAuthorityChangedError | Нет | 1→3 |
| A401 → held refresh → logout ACK → late refresh | WebAuthorityChangedError | Нет | 1→2 |

В negative account/role/logout сценариях adapter не выполнил server-side completion; в positive normal scenario выполнил completion с новым token той же identity. Все bearer literals — вымышленные. Проверка подтверждает async ownership и generation, не настоящий HTTP/browser/runtime/visual PASS.

Новых blocking findings нет. Ранее известные visual/background/frozen gallery и общий PR3 acceptance scope этим узким source отчётом не объявляются выполненными.
