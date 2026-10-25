# V3 revision-2 — узкий recheck одного P2: PASS

**P2 из `REVIEW-v3-1fb560dc.md` закрыт в source. Новых подтверждённых blocking findings в correction и её влиянии нет.** Это PREP acceptance; actual v2 withdrawal FAIL сохраняется, actual v3 ещё не запускался.

Harness `/workspace/rct-pr3-runtime/admin-access-native-20261024/effective-access-withdrawal-v3-prep/revision-2/native-idle-admin-withdrawal-v3.cjs`, SHA256 `c65322d6c79b09c489303a65af47fc2a940816537401cec85702d43731a4fbde`; FREEZE SHA256 `391d070c8a6dd192aa97a953d49e9e2bf61cade69b94ec3ca464ef2e5c6c6943`.

## Closure и влияние

- `browserTimes` и native204 response listener используют browser request timing: фактический ACK=responseStart исходного captured DELETE. Отсутствующий timing остаётся unqualified; позднее время Node callback не подставляется вместо ACK.
- `idleVerdict` (`:332–347`) требует app session request **start и response** после ACK, response не позже ACK+10s и401 либо decoded RV9/nonADMIN200. Pre-ACK401 и запрос, начавшийся до ACK с поздним response, больше не проходят.
- `startIdleObservation` (`:349–407`) начинает пассивное sampling до native dispatch и работает одновременно с original-status/canonical reads. Timestamp первого post-ACK исчезновения берётся из browser DOM sample, в том числе из уже накопленных samples при поздней доставке ACK callback. Поздняя canonical qualification не меняет время первого withdrawal. Пятидесятисекундное no-ACK окно и ограниченные samples не расширяют заданный post-ACK10s criterion.
- До проверки verdict и любого failure cleanup `finish()` сохраняет в receipt ACK/first withdrawal/qualification times, native counters, timestamped DOM samples, safe HTTP/request-failed facts, observation errors и итоговую квалификацию. Missing/unknown signal, browser contamination и неподтверждённые timings получают TEST/ENV-unqualified. APPLICATION verdict требует подтверждённый current RV9 response и наблюдение protected DOM на/после deadline. Transient facts теперь остаются reviewable после reload/logout/browser close; секретные значения в новые observations не добавлены.
- Idle observer выполняет только passive DOM/counter/network reads. Native mutations, exact own-session DELETE/logout и Root proof controls не расширены. Адресное побайтное сравнение подтвердило неизменность восьми ранее принятых sections: admission, proofScope, source admission, write route guard, bounded mutate, own cleanup, Root correlation и Root controls. Предыдущий controls review переиспользован.

## Целостность и достаточные проверки

Пересчитаны все10 `revision-2/FREEZE.files` hashes, predecessor FREEZE и все его восемь artifacts: PASS, frozen1fb сохранён. Прочитаны correction patch, изменённые predicates/observer/cleanup call sites, публичные templates/readiness и offline-check source/results. Author syntax exit0 и18/18 pure offline timing/verdict checks приняты без повторного запуска; они покрывают pre-ACK start/response, late canonical qualification, contaminated browser events, missing timings, transport failures и разделение verdict.

Read-only Git consistency: current Root `888667e32f9ae0503fc3004fa280a96c72c2861a`; product diff к5e6 пуст, tracked product clean. Exact actual HEAD в свежем admission остаётся обязательным; это сравнение не заменяет свежие isolated baseline/artifact/TLS/runtime proofs.

Reviewer выполнил source/public-evidence/SHA/Git reads и чистое локальное сравнение bytes/JSON. Runtime/browser/API/DB/private values, harness/test/build/CI execution, product changes, main/merge/deploy и children:0. Создан только этот отчёт. Requested model/effort `gpt-6.1-sol/high`; served-model telemetry недоступна и не заявляется подтверждённой.

Следующий шаг: Root fresh readonly isolated baseline/admission и отдельно разрешённый один finite actual case — combined LIMITED CAS6→7 и revoke7→8, затем independent postguard. Старые Users-only/Groups permission results и source2d PASS переиспользуются; genuine Notification/STOMP qualification этим fallback не заявляется.
