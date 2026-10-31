# Независимое узкое review a2ef210623

Результат: **PASS source delta; подтверждённых P1/P2 нет**.

Scope: только три файла `cab5b8a2..a2ef210623541a1adb39253da096e06076e0430f`: scoped transport factory, ownership regression, hover specificity. Baseline/cab5 повторно целиком не оценивались. Reviewer read-only, без source edits/commits/push/install/heavy/CI. Запрошенная модельgpt-6.1-sol/high; runtime telemetry недоступна.

## Проверенное поведение

- createWebTransport захватывает authoritative identity один раз, сохраняет owner checks token getter/onUnauthorized изcab5, добавляет guard непосредственно перед native fetch и после Response. Bearer берётся из актуального token той же identity; credentials=include выставляется явно. createStudentApi использует этот же transport, поэтому старый401 owner fix и same-identity refresh сохраняются.
- Response guard отклоняет поздний ответA после logout/loginB до передачи consumer. Для изменения identity во время async decode API экспортирует assertCurrent; новый regression применяет его до изменения visible state. Текущий homework consumer также сохраняет generation/current guard после decoding, а mutationRevision не изменён этим diff.
- Проверен фактический consumer inventory через git grep наa2ef: единственный продуктовый consumer — useWebHomework→createStudentApi, чьи текущие запросы используют фиксированные внутренние /api paths; прямой createWebTransport использован ещё в regression. Подтверждённого external-origin/Request-input defect в текущем flow нет.
- Новый late-read regression проверяет visibleProfile=null послеA→B, а не только количество вызовов. Повтор PUT при неизвестном сетевом исходе factory не добавляет; StudentApi продолжает повторять только отвергнутый401 под той же identity.
- Hover selectors теперь сохраняют inverse palette выбранной navigation link и accent palette выбранной period tab с явным on-accent foreground. Source specificity соответствует назначению. Screenshot inspection/visual полноту reviewer не заявляет; root отдельно сообщил о targeted dark/light captures и исправленном контрасте.

## Дешёвые независимые проверки

`git diff --check a2ef210623^..a2ef210623` — PASS.

Однократный dependency-free V8/TypeScript causal harness использовал source ровноa2ef (session, factory, actual StudentApi), минимальные Vue/auth transport stubs, проверял изменённую request boundary:

| Сценарий | Результат | Наблюдаемое следствие |
| --- | --- | --- |
| A completion401 → held refresh → logout/loginB → late refresh | WebAuthorityChangedError | Только первоначальный PUT с bearerA; side effectB отсутствует |
| Same-identity401 → normal refresh | Успешный completion ACK | Retry с refreshed bearerA, credentials include; generation стабилен |
| Held profile readA → logout/loginB → late ResponseA | WebAuthorityChangedError | visible state остаётся null; Response не передан consumer |

Все bearer/user literals в harness вымышлены. Это проверка async causality/source, не настоящий backend/browser PASS. Авторские пять regressions/build/types/lint PASS переданы root, reviewer их не повторял. Общий auth original asset QENV002 и frozen gallery/full PR3 readiness этим узким gate не объявляются закрытыми.
