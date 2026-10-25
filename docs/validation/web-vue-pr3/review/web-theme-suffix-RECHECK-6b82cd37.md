# WEB relogin suffix — narrow recheck

**FAIL — один P2 в late-body influence нового read classifier.** Actual suffix GO не выдавать до узкой correction/recheck. Runtime/Auth/browser/tests reviewer не выполнял.

Reviewed runner SHA256 `6b82cd37cbefba8ce863efc8cb3ab5f4c2fbbd09c0a6ad322d535f4df88459e6`; exact Root `7d664ce89cff5813772d1923e310ad74c2706c87`; product base `d69ac9159e3bfff169c4b2bd25adbcc7d9def771`. Scope: classifier + request-start/slot/phase GET causality + минимальный suffix flow относительно [ASSESS-0583-actual-transport.md](ASSESS-0583-actual-transport.md). Старые F1/F2, accepted HTTP6 и завершённый исходный Root terminal proof заново не проверялись.

## Findings — один набор

**S1 / P2 — late body после diagnostic timeout обходит critical schema/token guard.** `suffix-prep/runner.cjs:112`–`128`, влияние `218`–`246`.

Новый classifier разрешает known-own read body timeout как diagnostic/incomplete: `readingBody && knownRead(meta)` в строках244–245 не устанавливает global capture failure. Это допустимо лишь при сохранении critical проверки окончательного body/error. Обычный timely response path запрещает `accessToken` в read payload (строка219) и не downgrades `SyntaxError` (строка244).

Однако raw `response.json()` продолжает выполняться после3s timeout. При его позднем успехе `lateBody()` не использует request meta/метод и не выполняет те же payload/schema checks: `body.accessToken` передаётся в `issuedToken(..., late=true)` независимо от pathname (строка115). Если токен содержит тот же уже captured SID/user3, ownerMismatch и число handles не меняются, поэтому seal/HOLD не возникает. При позднем JSON parse rejection handler в строке126 вообще отбрасывает error и лишь пишет `lateBodyRejected`; eventual `SyntaxError` также не становится critical.

Получается конкретное timing-dependent различие: один и тот же unexpected read-token/invalid-JSON payload до3s — critical HOLD, после3s known-read timeout — остаётся diagnostic. Новый read-timeout exception впервые делает этот ранее унаследованный late callback достижимым без уже установленного global seal. Это противоречит заявленному suffix contract «unexpected read-issued token/schema failure remains critical» и может позволить дальнейший suffix/cleanup как при обычном incomplete read, если другой required relogin GET успел пройти. Не утверждается, что valid product API фактически вернул такой payload: finding относится к корректности нового failure classifier.

Минимальная correction: передать request meta/path/метод в eventual raw-body callbacks и применить там те же различения. Unexpected token в read, invalid JSON/schema, foreign/new handle и evidence/fsync failure должны вызывать captureFailure; допустимый transport rejection уже known-own read может оставаться diagnostic. Поздний read body не должен становиться relogin proof или менять текущий bearer. Не исправлять finding blanket HOLD на каждый read timeout либо подавлением всех late errors: это вернёт исходный TEST gap или ослабит critical guards.

## Остальные изменённые границы

- ACK classifier теперь сначала ждёт опубликованный bounded capture и сохраняет `ACK_CAPTURED_DURABLE`/canonical ACK при последующем transport terminal event. Unresolved mutation и initial issuance/canonical failures остаются critical. Read exception проверяет canonical-at-forward stage; schema/owner/fsync errors вне body-await не поглощаются.
- Safe diagnostics записывают request correlation ID/method/path-or-hash, request-start phase/slot, observed status/category/stage/outcome; raw URL query/bearer/cookie/error text не сохраняются. Диагностический read не выдаётся за успешный200.
- Request slot связывается с уже captured SID обычного bearer, иначе с текущим login slot; response использует request-start metadata. Поздний slot0/prelogout GET не становится slot1 relogin proof. Relogin evidence требует slot1 GET, начатый в LOGIN_1/STUDENT_ADMITTED_1, dark/revision1; old-slot snapshot не перезаписывает current bearer.
- Minimal fresh-device flow соответствует принятому scope: initial null0 → один native dark CAS/ACK1 → native logout204 и exact old bearer401 → второй normal login на том же ещё живом browser/device → real dark GET той же revision1/no PUT после logout → один WT04 → final canonical exact-own selfDELETE204/same bearer401. Старые light/system/live-OS три PNG не повторяются. Mutation count/phase guards, one-shot reservation и no unknown Auth retry/helper сохранены.
- Релогин/PNG/cleanup ещё не выполнены; первоначальный0583 raw FAIL не переписывается. Его Root proof/SQL state принято как уже закрытый Root input, reviewer не обращался к БД/private state.

Binding проверен независимо: actual runner SHA совпадает с PREP.sha256 и command; HEAD exact7d; relevant productd69→7d diff и tracked working status пусты; все14 source hashes byte-equal прежним PREP/current files; suffix `run/` отсутствует. Source pins не маскируют исполнение: actual suffix count0.

Создан только этот review report. Public source/diff/Git/hash reads only; private values, runtime/browser/Auth/API/network/DB/tests —0. Product/repo/shared docs/runtime source не менялись. QENV010/runtime12 STOP и CI/manual PAUSED сохраняются. Requested gpt-6.1-sol/high; actual model telemetry unavailable. No children.
