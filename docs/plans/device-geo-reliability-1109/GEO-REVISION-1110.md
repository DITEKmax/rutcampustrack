# Геоотметка — уточнение владельца 10.11.2026

## Goal
Студент нажимает «Отметиться», приложение автоматически проверяет позицию с максимум двумя повторами; в радиусе400м получает обычную отметку, при итоговом выходе/недоступности позиции — прежнюю автоматическую заявку старосте. Никакого нового ручного выбора качества.

## Context/evidence
Новый прямой запрос владельца10.11 заменяет прежние требования handoff о200м, пороге точности50м и выборе «повторить/продолжить». Это осознанное изменение продукта, а не ослабление тестов. Root первоначально реализовал лишнюю quality-policy; её необходимо убрать. Baseline интегрированного пакета aeaba0f19, root branch codex/device-geo-reliability-1109. Остальные A/B пункты PLAN остаются действующими.

## Relevant scope / ownership
- geo_backend_writer_1109, existing geo-backend-1109: Academic append-only migration, Attendance policy/Today protobuf, BFF HTTP/OpenAPI/generated, серверные доказательства. Единственный владелец контракта/generated.
- device_geo_frontend_writer_1109, existing device-access-1109: shared acquisition/permissions/platform adapters/consumers/tests; официальные backend commits переносит после freeze. Не редактирует generated вручную.
- Root: freeze, docs, integration, QA fixtures/browser; не пишет product code. Независимый geo reviewer проверяет только связанный delta. Один heavy lease; сейчас infra memory, далее backend.

## Required behavior
1. Канонический центр55.788328,37.606637, радиус400м с включённой границей. Academic хранит authoritative настройку. Для существующей БД новая миграция, старые применённые миграции не менять. Production применение отдельно, сейчас только source/local proof.
2. HTTP StudentToday получает optional nullable `checkinGeofence: {latitude:number, longitude:number, radiusMeters:number}`. Все числа конечные, корректные диапазоны/положительный радиус. Это подсказка для ограниченных повторов, окончательное решение только на сервере. Отсутствие policy/сбой чтения не ломают Today; legacy shape допустим.
3. При первой бизнес-попытке максимум3 свежих измерения: первоначальное +2 автоматических повтора при выходе из policy. Общий предел28с, никаких бесконечных циклов. Если свежая позиция внутри — сразу закончить получение. Погрешность GPS только диагностика, не блокирует отметку/не выбирает более близкий campus-point из истории.
4. При всех внешних позициях отправить последнюю свежую валидную точку. При отсутствии policy получить до3 ограниченных свежих измерений и отправить последнюю; не хардкодить клиентский campus-policy. При отсутствии координат — UNAVAILABLE. Существующий server business path сам создаёт PRESENT/PENDING_CONFIRMATION; не создавать промежуточную заявку для каждого измерения.
5. Transient timeout/unavailable можно повторить в общем бюджете. Denied/unsupported/app-disabled завершаются без повторных автоматических permission prompts и без нового ручного Continue: прежняя автоматическая заявка. Пользователь всё ещё может отменить до POST.
6. При задержке preflight устаревшую позицию не отправлять; общий retry budget не перезапускать бесконечно. Сохранить актуальные eligibility/server clock, owner/leave/off cancellation, late callbacks cleanup. После неопределённого POST immutable command/key повторяются, позиция не меняется.
7. В settings явное включение/проверка позволяет повтор после закрытого/неотвеченного запроса. Не кэшировать такой отказ навечно как запрет. Системный denied нельзя обходить: Telegram openSettings из user gesture; для браузера понятная инструкция по разрешению доступа и рабочая повторная проверка; возврат/focus/visibility обновляет статус. Нет ложного grant, бесконечного spinner или автовключения датчика при одном открытии страницы.
8. Для truthful diagnostics/reporting backend добавляет bounded enum reasons `WITHIN_GEOFENCE`, `ATTEMPTS_EXHAUSTED`; старые enum значения продолжают приниматься. Для отсутствия позиции сохраняются конкретные причины, для общего таймаута BUDGET_EXPIRED. Один terminal report на весь acquisition run, не каждый внутренний повтор. Settings probe0reports, retry unresolvedbusiness0newreports, никакие IDs/coordinates не входят в pre-POST report.

Compat уточнение после проверки consumers: при отсутствии/некорректной Today-policy клиент не знает, поддерживает ли сервер новые enum. В business diagnostics опускается только optional completionReason, если его новое значениеWITHIN_GEOFENCE/ATTEMPTS_EXHAUSTED; прочие bounded поля и прежние enum сохраняются. Не подменять reason ложнымQUALITY_REACHED. Best-effort terminal report остаётся правдивым; oldendpoint404 не мешает отметке. Это совместимость с metadata-capable BFF без policy, не обещание работы всех новых полей с любым историческим сервером; future delivery всё равно backend-first.

## Constraints
Временное окно, cooldown, RBAC, владельцы, audit/история/транзакции/receipt semantics сохраняются. Не менять принятый дизайн, кроме удаления лишнего quality chooser и необходимых существующих статусов/подсказок разрешений. Не применять migration на production, не push/deploy. Не менять другие роли/экраны.

## Existing patterns
GeofenceService получает Academic config; StudentToday существующий RPC/BFF; shared DeviceAccess/StudentCheckin pipeline с immutable retry; browser watchPosition и Telegram LocationManager. Перенести backend contract до окончательного wiring frontend. Функции probes отделены от business/acquisition telemetry.

## Acceptance criteria
- 300м теперь принимается, 400м включено, >400м в итоге получает одну автоматическую pending заявку. Миграция сохраняет центр и существующие учебные данные/receipt/history.
- outside→inside на2/3 попытке: один finalPOST PRESENT; outside×3: один finalPOST/одна pending; нет quality50-гейта или пользовательского Continue. Policy absent legacy/таймаут не зависают.
- Повтор настройки послеdismiss/закрытия не заблокирован старым app/native state; системный запрет честно восстанавливается через разрешённые settings. Probe не создаёт business side effects.
- Cancellation/owner drift/freshness/immutable ambiguous retry не регрессируют; один bounded terminal report/run.

## Verification
Адресные meaningful tests для retry-count/lastpoint/insideboundary/noqualitygate/timeout/cancellation/owner/immutable retry и permission dismissal/recovery; backend actualHTTP→gRPC→Mongo на400м и nativePostgreSQL migration proof. Сохранить исходные failed attempts и correction evidence, не перезапускать все ужеPASS пакеты. После scoped independent review root визуально проверяет реальные Vue PWA/TMA (synthetic sensors допустимы), screenshots обеих тем. Настоящие device prompts/GPS остаются отдельной ручной приёмкой.

## Do not
Не подменять старый command послеunknownPOST; не удалять реальные pending/отметки; не создавать2extraPOST до finalbusinessmutation; не принимать inaccurate какOUTSIDE толькоиз-за accuracy; не обещать принудительный повтор OS prompt послеpersistentdenied. Не выдавать local mocks за production acceptance.
