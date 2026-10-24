# Profile cleanup HOLD correction + TMA-only derivative — narrow recheck

**PASS по closure единственного P2 helper cleanup и по TMA-only derivative scope. Новых blocking source findings нет.** Это две PREP qualifications; никаких cleanup/Auth/browser действий reviewer не выполнял. Root GO остаётся отдельным и последовательным: helper → independent PG post → known terminal close исходного92079 → remaining TMA.

## Cleanup3f39 — F1 закрыт

Runner SHA256 `3f399b97e87db32eb2caee285bfe7dd3ae51cbd3fb58c78caaf72845d3dc9676` совпал с PREP.json. Exact `hold-P2.patch` обратным применением в памяти восстанавливает original5e6a checksum; изменения ограничены unknown handling/intent durability и их влиянием. Старый `REVIEW-profile3-own-cleanup.md` CHANGES REQUIRED сохранён.

Перед каждой mutation forward теперь fsync сохраняет one-shot counter и kind/time/PENDING attempt. Transport uncertainty либо unexpected mutation status фиксируется до propagation; неуспех DELETE/logout не повторяется. При uncertain forward finally больше не отправляет helper logout. Accepted login с плохим/unobserved body, captured SID без successful canonical qualification, неизвестный login response и unconfirmed helper revocation попадают в **реальный process/RAM HOLD**, а не завершают promise/process. Hold не выполняет HTTP/cleanup/retry, сохраняет token/cookies только в RAM и durable nonsensitive handle/attempt facts.

Definite401/403 login rejection без новой session либо known helper logout204+same-bearer401 допускают безопасный STOP/exit. SIGTERM в hold только записывает terminatedFromUnknownHold и завершает процесс с исходным failure; terminal qualification не фабрикуется. Root должен применять этот close лишь после независимого resolution, как задано packet.

Ранее принятые exact actor3/current helper SID, frozen old noncurrent SID/time-window, own-list absence/other-handle preservation, один old DELETE и один ordinary cookie-only helper logout не ослаблены. Новых auth/role/admin/business writes, fallback, private token persistence или extraction из original driver нет.

## TMA-only06f954 — PASS

SHA256 `06f954be739a74662677a4af4cf0b4eb7674e29ef70b1202f14693863e0c5c6c` пересчитан. Exact derivative patch обратно восстанавливает reviewed8b38 checksum. Delta только: отдельные receipt/once-guard paths, удалён main PWA invocation, expected sample count2→1 и уточнён PREP comment. Только `runConsumer('TMA')` достижим из entrypoint; исходный PWA/screenshot не повторяется. Старые guards не сбрасываются.

Canonical owner/SID checks, closed errors, immutable bearer/native one-forward204+same-bearer401, actual held GET/release200 и unknown HOLD byte-identical принятому8b38 TMA path. Отдельно проверен **реальный TMA logout contract**: `tma-session.ts.logoutCurrent` отправляет Authorization Bearer; прежний PWA cookie-only mismatch к этой derivative не переносится. Source basea851 и expected Profile hashes сохранены; relevant working diff к базе пуст на текущем Root. Loaded reuse не становится новым runtime image.

TMA запуск допустим только после successful helper cleanup/independent PG qualification и закрытия original92079; этот report не заменяет эти факты и не снимает отдельный Root browser GO. Никаких новых public/runtime checks или успешной тестовой кампании для narrow source closure не требуется.

Создан только этот отчёт. Reviewer public source/PREP/hash/patch и existing TMA contract reads=only; private inputs/handles, Auth/API/browser/DB/tests/runtime=0. Original driver/archive/rawFAIL не изменены. Requested Sol6.1/high; served-model telemetry недоступна.
