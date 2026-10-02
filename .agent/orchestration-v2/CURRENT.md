# CURRENT — PRODUCT GO, 2026-10-02

Фаза: функциональное подключение Vue PWA/TMA по новому GO владельца. Figma/redesign, настоящий Telegram и deploy остаются отдельными этапами. Offsite backup/RPO/RTO отложены до подготовки deploy. Полная цель не достигнута. Root sole main/shared-docs writer; переносы в main постоянно разрешены, push/deploy без отдельного задания запрещены.

RULES SHA A208AA4380B64376A4EAD645AA0731C9F574077107DC9C44356F5C04D80F28FA. Все новые задания Sol6.1 high; инструмент не предоставляет отдельную runtime metadata модели. Чужой dirty/protected WIP сохранять, reset/stash/clean запрещены.

## Main и текущие пакеты
Main a0821a8c. Интегрированы roster ticket 4a2a9114 + fixture/evidence5c674c2a и Vue roster b43ad9dc + evidencea0821a8c. Оба независимых source review PASS. Backend Auth/Gateway адресные проверки PASS: R1 один DOCX test deadline2s, R2 canonical30s пять форматов PASS, production bytes неизменны. Vue PWA/TMA typecheck, scopedlint и четыре targeted checks PASS; общий mobile-core tsc имеет ошибки в неизменённых чужих тестах, baseline-origin только inference. Root проверил отсутствие diff accepted sources→main. Не объявлять browser/Telegram скачивание принятым.

- g_backend_acceptance_1002: roster UI source28753877/evidence91ee42d5 интегрированы, idle. Требуется parent forwarding из ONE_OFF пакета.
- v_homework_lifecycle_1002: roster ticket source7815d55f/evidence2dd60d6d интегрированы, idle; heavy освобождён, Java EMPTY, Docker не запускался.
- v_homework_schedule_1002: source0df5fe7e ONE_OFF Vue + reportDownload forwarding, source-ready; reviewer review_academic_homework_1002 проверяет retry/session/canonical reads. Авторские targeted tests/typecheck/lint PASS.
- v_homework_bff_1002: DATE/archived/revision/retry/history Vue ДЗ, финализирует commit; targeted checks19 и bounded typecheck PASS; независимое review затем.
- review_homework_schedule_code_1002: roster source review PASS, доступен.
- e_homework_date_bot_1002: Telegram/WebPush wiring уже есть, fake auth не вводить; runtime reuse plan завершён, idle.

Frozen roster contract: HEADMAN_GROUP_COMPOSITION/headmanGroupComposition:{format}, без client groupId, server resolves own current group. Пять форматов, PNG ZIP. Сохранены существующие reusable60s/MAX20 ticket semantics, session/revocation и bindingHash; это не one-use ticket.

## Следующий шаг и runtime
Закончить review двух клиентских пакетов, исправить существенные findings, интегрировать. Один общий runtime после freeze. Existing clean holder v2-runtime-build-r2 пока7e1190a0. Rebuild Auth/Gateway/Notification (auth-api-contract dependency) и Vue; Academic/Schedule/Attendance/BFF/Renderer переиспользовать после manifest hash validation. Старый launch wrapper закреплён на7e1190a0: не запускать unchanged. Existing runner поддерживает ManualAcceptance и три DevTls параметра; mkcert cert/key/CA files существуют, browser trust ещё не проверен. Не читать/печатать ключи и synthetic credentials. TMA runner не обслуживает; реальный bot/initData отдельно.

Heavy FREE. Known Gradle JDK21 C:/Users/maksd/.jdks/ms-21.0.10; --system-prop=org.gradle.java.compile-classpath-packaging=true --no-problems-report --no-daemon --max-workers=1 --no-parallel. Сохранить чужой runner WIP и принятую3line BOT_TO_NOTIFICATION_SERVICE_TOKEN correction. Не повторять уже принятые backend проверки.

## Сохранённые результаты backend
BACKEND-STAGE-20261001.md содержит общий остаток. Durable prefs81848929/sharedMongo14f836e5/evidence7e1190a0; ONE_OFF fresh API0a4e982d; pending bot delivery069d0e0c/evidence71ce6255; native recoveryb1db7f28; backup runbook770b34c6; offsite deferralb7fa9059; roster scope996f590c. Их review/runtime не повторять без новой причины. Native drill containers/volumes остановлены и сохранены; не удалять. Внешние provider delivery, browser flows, app DR и публичный запуск не приняты. Проценты не повышать по коммитам или тестам.
