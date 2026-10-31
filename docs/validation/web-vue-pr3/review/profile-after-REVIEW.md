# Profile after screenshots — independent harness review

**CHANGES REQUIRED — два узких P2 в failure-output и exact cleanup qualification; source freeze также ещё не установлен.** Browser/Auth/API reviewer не запускал. Проверены весь172-line public runner и prepared JSON; SHA256 runner `3b972d9f840125111fc82b3413fc6f9e03b49222d98d4bf7d06a7e95331ac6c1` совпал. Requested Sol6.1/high, served-model telemetry недоступна.

## F1 — P2: raw Playwright failure message попадает в public receipt

`profile-loading-after.cjs:140,170` сохраняет произвольный `e.message.slice(0,180)` в public receipt. В этом же try выполняются native login/password fill и TMA host/navigation. Playwright error message может содержать call log с аргументом fill и прочими данными выполненного действия; ограничение180 символами не является redaction. Таким образом error path не обеспечивает заявленный no-private-exfil boundary даже при корректном secret-free success output.

Минимальная correction: public failure — closed stage/code + безопасное error name; не сохранять driver message/calllog. Собственные заранее заданные harness messages можно заменить фиксированными кодами. Новых auth requests, private reads или тестовой кампании для correction не требуется.

## F2 — P2: detected session/owner mismatch не запрещает successful cleanup claim

`ownSnapshot` (`:40–46`) при новом SID лишь ставит `unexpectedSessionChange` и оставляет старый sid. При чужом userId сохраняет sid с `ownerMatches=false`. Основной flow отклоняет эти условия (`:110`), но finally всё равно пытается native logout, а `clean.confirmed` (`:156`) требует только любого captured sid + любого native logout204 + bearer session401. Ни matching expected actor, ни absence of session change, ни correlation logout bearer с exact captured SID в этом predicate нет.

Если observed authority действительно сменилась, native logout может завершить текущую другую session, а401 старого bearer не доказывает отзыв сохранённого SID. Текущий error flow тогда пометит cleanup confirmed и закроет context вместо UNKNOWN hold. Это не утверждение, что смена уже происходила; код содержит специальную обнаруживающую ветку, которая сейчас не участвует в cleanup admission.

Минимальная correction: при owner/session mismatch не квалифицировать native cleanup и удерживать exact handles для Root resolution. Перед native logout зафиксировать canonical own current snapshot/bearer correlation с ожидаемым actor/SID; подтверждение204/401 применять только к этому же handle. Не вводить API logout fallback, повторный login/TMA или blind refresh. Новые unexpected handles не следует молча терять из durable diagnosis.

## Source freeze readiness

На проверенном SHA `base` всё ещё `PENDING_ROOT_PROFILE_FREEZE` (`:5`), prepared JSON также pending. Regex guard корректно остановит execute до browser/Auth; поэтому этот runner сейчас **не исполним**. Actual Root уже `a851d5cf780c965513e79c2334a924eaaac72cdc`. Автор должен обновить exact reviewed integration base/prepared checksum и сохранить freeze перед Root GO. Это отдельный PREP blocker, не разрешение обходить guard. После corrections достаточно narrow delta/hash recheck, не нового полного review.

## Остальной bounded flow

План ограничен PWA actor3 и controlled-host TMA actor4, двумя sequential contexts/300light+300dark PNG. Dedicated wx0600 guards новые; старые TMA guards не читаются/не сбрасываются. Auth route gate допускает один forward соответствующего login/TMA и один PWA startup refresh; повтор/другие business mutations блокируются. Существующий public Telegram helper моделирует только host/initData; native provider claim явно false, API/Auth responses не подменяются.

Profile loading создаётся удержанием **реального GET** `/api/academic/users/me`, ожидается actual route hit и `.profile-loading`; PNG явно помечается synthetic held-read. Hold releases в finally, настоящий unchanged GET должен200 и loading исчезнуть до native cleanup. No API logout fallback, token retry или fake response. Rejected/unknown auth либо unconfirmed native204/guest401 не запускают следующий consumer; unknown удерживает browser/process и приватный handle. Source hash checks/changed relevant-tree gate предусмотрены до capture; screenshot capture не выдаётся за natural latency или Telegram provider proof. Loaded1440 reuse отдельно обозначен прежним evidence, не новым runtime PASS.

Создан только этот отчёт; product/Git/browser/runtime/private handles не изменены и private files не читались. Прочитан только публичный helper source. Reviewer tests/browser/API/DB/runtime runs=0;55 live checks не повторялись.

## Узкий final-freeze recheck —458b2a99

Проверен финальный runner SHA256 `458b2a995eff98a2a0b568ae9f956e93e26313f2661c6fe5f691e58633e82c4f` и prepared JSON: checksum совпал, base теперь `a851d5cf780c965513e79c2334a924eaaac72cdc`. Actual Rootcab3 имеет пустой relevant product/helper diff к этой базе. **PREP source-freeze blocker закрыт.** Loaded-source-reuse qualification подтверждает script и normalized loaded/error/logout suffix equality; прежний loaded1440 остаётся reuse, не новым runtime image.

**F1 и F2 остаются открытыми без изменений:** public raw `e.message.slice` всё ещё на140/170; cleanup predicate156 всё ещё не требует expected owner/absence of unexpected SID/current exact logout correlation. Freeze update этих failure paths не исправляет. Итог для458b — **CHANGES REQUIRED**, те же два P2, без новых findings и без требования нового полного review. Runtime/private операции не выполнялись.
