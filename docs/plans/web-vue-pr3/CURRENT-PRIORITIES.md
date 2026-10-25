# Ближайшие пакеты существующего Goal PR3

25 октября2026. Ветка `codex/web-vue-redesign`, PR №3. CI/Coverage/Security/OpenAPI
остаются PAUSED/workflow_dispatch; без merge, deploy, production и новой среды.

| Ось готовности | Фактическое состояние |
|---|---|
| Реализация действий |116/117 active, ADMIN33/34; финальный group UI gate остаётся |
| Проверенное поведение |116/117; пройденные backend и закрытые административные пакеты переиспользуются |
| Визуальная часть |33 web-вью и50 mobile source consumers.45 mobile имеют ограниченные наблюдения,5 не проверены; это не45 полностью готовых экранов. Галерея289 PNG; PWA/TMA, темы, размеры и состояния квалифицируются отдельно |
| Angular / сборка |Angular433-file package активен; итоговый assembled commonNginx gate не пройден. Адресные Vue build его не заменяют |

## Текущая зависимость — место на диске

Разрешённая адресная очистка завершена:109 точных путей в3 пакетах. Реальный
available вырос5244747776→6531416064B, net+1286668288B (~1.20GiB). Это меньше
allocated1.64GB из инвентаризации. Source/12WT/Git/archives/results,25 protectedJARs
и3 legacysetupJARs, shared dependencies и все Docker/data ресурсы сохранены.
[Список, замеры и независимый postproof](../../validation/web-vue-pr3/checkpoints/disk-cleanup-20261025/README.md).

Сейчас~6.08GiB, минимум7GiB. Остаточный дефицит~0.92GiB без дополнительного запаса;
практически нужен ещё объём1.5–2GiB с сохранением workspace/Dockerdata. Безопасный
набор исчерпан, cleanup остановлена. Q-ENV-010 pending; hard2GiB/early2.5GiB/250ms
и preflight7GiB не менялись. Group start/fixtures/UIdelete0, это не resource PASS.

## Ближайшие результаты

1. **Удаление группы через UI.** Fresh source qualification559inputs/47eccb79 готова,
   backend1530 и matching JARs неизменны. После этого doccheckpoint — новый public
   cohort snapshot/exactHEAD binding и независимый review. ExecutionGO остаётся false
   до actualresourceREADY. Один originalUUID, numeric preview, текущий пароль,
   status исходной операции,6 открытых WEB/PWA/TMA views, естественный отзыв целевого
   доступа и сохранение других accounts/credentials/roles/groups. При unknown —
   только выяснение исходного результата, без нового удаления.
2. **Доступный визуальный остаток.** TeacherManual conditional PREP structuralPASS:
   один nativeTeacher2/Main lesson1/date24 roster; реальная manual строка даёт2кадра,
   её отсутствие — честный bounded outcome/0PNG, не globalabsence. FinalHEAD/recheck/
   RootGO впереди. HelperActions/Homework пока без доказанных existing helper
   credentials/grants; HeadmanGroup автоматически показывает passwords. Не выдавать
   права, не создавать данные и не подменять props/ответы ради картинки.
3. **После подтверждённого group gate** удалить активный Angular source/deps/lock/
   config/build и специфичные obsolete checks, сохранив полезные гарантии.
4. **Общий Nginx:** assembled Vue deep links/refresh/static/API/realtime; PWA`/app/`,
   TMA`/mini-app/`, auth/updates/WebPush. Это локальный gate, без deploy.
5. После пакетов обновлять карту117 active действий, PROGRESS, API-CHANGES,
   MOBILE-FOLLOWUPS, gallery и PR; исходники и уникальные результаты сохранять.

Пароль, LIMITED и whole-map deletion закрыты; старые cleanup/операции не replay.
До каждого пакета фиксируется пользовательский результат и достаточное подтверждение.
После PASS повторять только при изменённой зависимости/поведении, ошибке, существенном
непроверенном риске или обязательном финальном gate; причину повтора записывать.
Продуктовых вопросов нет, остаётся внешний ресурс Q-ENV-010.
