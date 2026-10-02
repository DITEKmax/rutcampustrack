# CURRENT — PRODUCT GO, 2026-10-02

Backend А–Ж → отдельный frontend/Figma → ручная PWA/TMA приёмка → отдельно разрешённый deploy. Полная цель не достигнута. Root — единственный main/shared-docs writer. Push/deploy не разрешены. RULES SHA A208AA4380B64376A4EAD645AA0731C9F574077107DC9C44356F5C04D80F28FA. Новые задания Sol6.1 high; отдельная runtime metadata модели недоступна.

## Принято в main
Main 7e1190a0. QR LOGIN 24e92335/evidence50c209a0; ONE_OFF lifecycle990cd74e/evidence4b01110c; canonical current flag a5ed1eed/evidencec1236ed3. DATE/edit/history и semester-delete приняты ранее; OTP28b1fd6f/evidence5b1faabb. Не повторять эти проверки без изменённого риска.
Отчёт старосты: adbdc668/49ca1f4d/evidence3d7788ea, выбранные фильтры/сортировка и целые строки, 120 студентов/9 страниц. Отчёт преподавателя: a199a668/evidence5c37b8e2, STUDENTS80/GROUPS60, 4+5 страниц. Source review и визуальная приёмка PASS; UI/HTTP скачивание не приписывать этим результатам.
Ж: directed token config dd837c04; recovery runbook c38786a3 (docs-only); quiet-hours4d1ba01e/evidence834199b4, pinned amtool PASS, provider delivery не проверялась.

## В — границы реальной приёмки
Evidence .agent/evidence/oneoff-transfer-bus-20261002/SUMMARY.md. R2 реальный Rabbit дал оба APPLIED ACK, точный повтор сохранил операцию/target; PG/Mongo сохранили identity ДЗ, completion timestamp, history и новую физическую генерацию. Общий probe остановился на дефекте current; исправление отдельно принято реальным API/PG методом27794 (exit0/66s, XML1 PASS). Fresh Homework/BFF/Attendance/oneoff API suffix НЕ выполнен; полный probe PASS не заявлен. Полный bus не повторяли. R1/R2 raw FAIL сохранены. Runtime14/14 и последующая PG/Ryuk проверка очищены, Java0.

## Активная работа
Постоянные настройки уведомлений интегрированы: product81848929, shared Mongo14f836e5, tests3400377e/53d500ac, evidencee7d3b054/7e1190a0. Независимые source reviews PASS. Финальный exec68736 exit0: shared5 и2MongoIT PASS, предыдущие7IT/66Python не повторялись; headman7 PASS. Exact product preferences diff accepted author→main пуст. Контейнеры/Java очищены, lease свободен.
- v_homework_schedule_1002: sole existing admin-group-promotion WT, готовит минимальный запуск оставшихся fresh API чтений после переноса. Прежние component/ACK проверки не повторять; heavy не выдан.
- v_homework_lifecycle_1002: sole existing map-usage WT, готовит bounded локальную recovery репетицию Redis/Rabbit по принятому runbook. До согласования ресурсов никаких destructive/runtime операций; heavy не выдан.
- Остальные авторы и reviewers завершили задания, idle. Main/shared docs пишет только root.

## Ресурсы и рабочая команда
Heavy lease свободен. Финальный exec68736 exit0; cleanup8bb1f4 exit0, собственные Mongo/Redis/Ryuk отсутствуют, Java0. Один heavy lease через root.
Для дальнейшей Gradle проверки использовать уже подтверждённые JDK21 C:/Users/maksd/.jdks/ms-21.0.10, --system-prop=org.gradle.java.compile-classpath-packaging=true, --no-problems-report, --no-daemon/--max-workers=1/--no-parallel. Cached JAR требуют exact require_escalated. Не повторять class-directory и problems-report ошибки: R3/R4 пропустили packaging flag, R5 sandbox AccessDenied, R6 compile tasks прошли, но diagnostic report copy дал FileAlreadyExists. IT11602 с корректными флагами реально запустился. Никаких clean/ACL/global config изменений.

## Остаток и ограничения
BACKEND-STAGE-20261001.md — общий остаток. Внешние Telegram/WebPush provider, actual DR/RPO/RTO/offsite/release не приняты. Доставка после исчерпания Java retry требует существующего manual DLQ replay; in-memory bot queue crash durability и атомарный lease с Academic rebind не заявлены. Неизвестный legacy tracker owner не переатрибутируется, сохраняется под прежним TTL с диагностикой. Shared WS мобильного клиента — REST invalidation, не отдельный внешний push.
Не создавать кампанию тестов/аудита. Готовность не повышать по количеству проверок. Все foreign dirty/protected files сохранены; никакого reset/stash/clean.


