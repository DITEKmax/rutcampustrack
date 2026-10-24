# Profile after harness — узкий F1/F2 recheck

**PASS по source closure обоих P2 и их влиянию. Новых blocking findings нет.** Actual screenshots/Auth/cleanup не запускались. Предыдущий `REVIEW-after-harness.md` CHANGES REQUIRED сохранён.

Runner SHA256 `8b387328e56c8d219d89c4b895f27ec5c9dea6e5fbac57b0787a3f1335e9828f` совпал с prepared JSON. Прочитан exact `profile-loading-after-P2.patch`; reverse reconstruction подтверждает прежний458b checksum, то есть delta ограничен указанными corrections. Basea851 сохраняется; relevant working product/helper diff к базе пуст на текущем Root. Profile expected hashes/source freeze не ослаблены.

**F1 закрыт.** Public case/packet failures теперь содержат только фиксированные stage/code и allowlisted error names; pageerror/native-cleanup names также проходят allowlist. Raw driver message/stack отсутствуют. Unexpected actor/SID diagnostics сохраняются только в private0600 observedHandles, без bearer/password в durable output. Неизвестные error names превращаются в Error.

**F2 закрыт.** Каждый observed actor/SID сохраняется до проверки mismatch; mismatch flags не сбрасываются последующим корректным ответом. До native logout immutable RAM bearer проходит реальный canonical `/auth/session` GET: expected actor, captured SID и отсутствие owner/session mismatch обязательны. Затем page route разрешает ровно один native logout с **тем же** Authorization и admitted SID; иной bearer/повтор/изменившийся owner блокируется и оставляет cleanup unqualified.

Native response waiter коррелирован с тем же bearer; qualification требует именно его204 и последующий401 read с тем же immutable bearer, а также все owner/SID/correlation flags. Прежняя generic logoutStatus fallback больше не используется как proof. Неуспех current read либо любой mismatch удерживает own browser/module/diagnostics для Root resolution, не запускает следующий consumer. API logout fallback, новый login/TMA retry, token installation или business mutation не добавлены.

Остальной уже рассмотренный flow сохранён: два once-owned contexts, held actual profile GET с явной synthetic PNG qualification, release unchanged GET→real200/loaded card, native-only cleanup; старые guards не сбрасываются. Loaded1440 остаётся source-qualified reuse. Node syntax evidence автора принято, reviewer ничего не исполнял; дополнительная тестовая кампания для этих predicates не требуется.

Создан только этот отчёт. Reviewer source/SHA/patch reads=only; browser/Auth/API/DB/private handle reads/tests/runtime=0. Root отдельно выдаёт bounded browser GO. Requested Sol6.1/high; served-model telemetry недоступна.
