# Независимая узкая recheck web5ab473df

Результат: **четыре исходных P2 закрыты; обнаружен ещё один P2 на границе late identity/retry**.

Scope: diff только `9c61f0f7d..5ab473df0cb92d7c0444cb1ca394c780c509ccc3` и необходимое влияние исправлений на исходный web adapter. Dependency copy9c61/shared6de повторно не оценивались. Reviewer read-only, продуктовых правок/commits/push/install/тяжёлых запусков нет. Запрошенная модельgpt-6.1-sol/high; runtime telemetry недоступна.

## Исправления исходных findings

1. **Obsolete refresh coalescing — PASS.** refreshOwner различает operation; ожидающий reconciliation после obsolete flight запускает authority refresh для актуальной operation. Исходный deterministic repro теперь получает ready/STUDENT/current token, refreshCalls=2, busy=false. Поздний refresh после role ACK сохраняет HEADMAN/headman token; после logout сохраняет guest/null snapshot/null token.
2. **Old GET после completion ACK — PASS.** GET captures mutationRevision; mutation start/end меняют revision, поздний старый снимок не применяется и инициируется новое чтение. Исходный repro теперь оставляет completed=true/error=null/pending=false. Failed mutation readback сливает только affected item, не заменяет ACK других карточек.
3. **Длинное описание — PASS source.** Реальный overflow измеряется ResizeObserver, появляется button с aria-expanded и доступным названием; expanded display:block снимает line clamp. Изменение description сбрасывает раскрытие и повторяет measurement. Browser layout reviewer не запускал.
4. **Ready→ready role route — PASS source.** Watcher зависит также от authoritative activeRole; после role change пересматривает разрешённость route. Nonselectable active grant остаётся видимым, но не выбирается; pointer/focus interaction разделены.

Новые два regression tests проверяют пользовательское состояние при реальных adapter async boundaries, а не call counts/DOM shape. Их источник прочитан; авторский PASS2/2 заново не запускался.

## Новое подтверждённое замечание

**P2 — старый completion retry может исполниться от нового аккаунта.** Удержанный исходным пакетом `frontends/web-vue/src/app/api.ts:4–6` связывает StudentApi с изменяемым глобальным webSession token и refresh. Существующий `frontends/mobile-core/src/api/student-client.ts:315–317` после401 и ожидания onUnauthorized повторяет запрос, заново читая token. Operation guard обновлённого session.ts правильно отбрасывает старый refresh result, но успешно завершающийся obsolete callback всё равно разрешает transport retry; composable identity/current проверяется только после server sideeffect.

Конкретный repro: аккаунтA со STUDENT отправляет completion PUT → получает401 и ждёт refresh → пользователь выходитA и входитB → старый refresh body наконец разрешается и игнорируется как obsolete → StudentApi автоматически повторяет тот же PUT с bearerB. Для A/B из одной группы этот homework доступен обоим, поэтому backend сохраняет отметкуB по действию, совершённомуA. Сам backend не нарушает authz; клиент переносит команду между identity. Такое же окно есть при role/session switch. Нужен identity guard до повторного отправления; обычный refresh той же identity должен оставаться работоспособным. Простое сравнение generation без различения token refresh непригодно, потому что acceptSession увеличивает generation и при обычном refresh.

Dependency-free source harness на ровно5ab + actual StudentApi подтвердил вывод:

`{"scenario":"old completion401 retry after account switch","currentUser":"B","calls":[{"path":"/api/v1/student/homework/h1/completion","authorization":"Bearer token-A","method":"PUT"},{"path":"/api/v1/student/homework/h1/completion","authorization":"Bearer token-B","method":"PUT"}]}`

Все транспортные tokens в harness — вымышленные literals. Микро-проверка использовала установленные TypeScript/V8, отдельные Vue/transport stubs и Git source; это подтверждение причинности async callbacks, не настоящий HTTP/browser PASS. Findings переданы root и автору одним пакетом.

## Прочее и пределы

`git diff --check 5ab473df^..5ab473df` — PASS. Device mirror wiring передаёт controller/preference и обновляет session context по token + generation/userId/sessionId; при logout context становится null, online вызывает reconcile. Shared mirror implementation этим scope не переоткрывалась. SVG change только использует уже рассмотренный компонент.

Root передал авторские typecheck/build/lint, две regressions и real role/lost-response/logout smoke PASS; reviewer их не повторял. Frozen screenshots и auth rollout ожидаются, visual completeness/background Q-ENV002 не объявлены PASS. Требуется узкая correction/recheck identity retry до окончательной приёмки web пакета.
