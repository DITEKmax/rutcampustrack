# Ближайшие пакеты существующего Goal

Уточнение владельца2026-10-24. Ветка `codex/web-vue-redesign`, PR №3.
CI/Coverage/Security/OpenAPI PAUSED/manual; без merge/deploy/production.

| Пакет | Результат и критерий закрытия |
|---|---|
| Визуал параллельно ADMIN | Готовые STUDENT/HEADMAN/TEACHER и PWA/TMA проверяются до завершения group delete. Сначала воспроизвести, затем исправить. Конечный список:33 web-вью и50 мобильных Screen-компонентов; покрытие PWA/TMA отдельно. Статусы:готово/исправить/не проверено, без общего процента. |
| Уведомления и загрузка | Проверить4+ входящих уведомления: каждое живёт примерно10s, история через bell; устранить cap3, если runtime подтвердит вытеснение. Проверить skeleton, empty/error/retry/reconnect, focus/hover/press/disabled и применимые validation/saving/conflict/unknown. Обе темы,8 согласованных ширин. |
| Остаток ADMIN | LIMITED assign/change/revoke с фактическим доступом; user8 allowed/self/lostACK и обязательный cleanup временного FULL; завершить существенные Auth-проверки map. Q-ENV-003/007/008 разрешены. Успешное Q7 удаление не повторять; original operation recovery и реальные postguards сохраняются. |
| Group final delete | Backend и Vue параллельно по фиксированному контракту. Числовой preview, текущий пароль, exact digest/CAS/durable recovery; удалить только группу/членство/назначения/её полномочия, сохранить аккаунты и другие роли/группы. Отзыв уже открытого доступа. Source review, реальные PG/Mongo/HTTP и законченный runtime-сценарий. Q-PROD-004 решён. |
| Angular и сборка | После замены функций удалить активные Angular source/deps/lock/config/build и специфичные проверки исчезнувшего кода, сохранив полезные гарантии. Локальный assembled Vue через общий Nginx: deep links/refresh/static/API/realtime; PWA `/app/`, TMA `/mini-app/`, авторизация/обновления/WebPush. Без deploy. |

Перед пакетом фиксируются наблюдаемый результат и минимально достаточное
подтверждение. После PASS пакет закрывается. Повтор допускается из-за изменения
поведения/зависимости, ошибки, существенного непроверенного риска или обязательного
финального требования; причина записывается. При повторной неудаче той же причины
меняется гипотеза. Пригодные неизменённые артефакты переиспользуются.

Визуальные кадры имеют постоянные ID/screen/state/theme/width/revision;
синтетические причины помечаются. Отсутствие кадра не означает отсутствие кода.
Нет массовой E2E-кампании и тестов точных текстов/CSS/DOM ради визуала.
Тяжёлые проверки получают отдельные ресурсы/БД/очереди; стенд расширяется только
для конкретной зависимости текущего результата.

В PR отдельно: реализация117 active действий, проверенность поведения,
визуальная готовность, удаление Angular/сборка.116/117 — доля действий,
не99% всего PR. Кадры, тесты и assertions не увеличивают готовность.
Перед завершением обновляются галерея, карта, PROGRESS, API-CHANGES,
MOBILE-FOLLOWUPS и итог PR; PAUSED не объявляется PASS.

Текущие пакеты и зависимости:

- Пароль, LIMITED и whole-map delete/Auth закрыты; последние independent posts9598/
  c7ee подтверждают cleanup. Не повторять генерацию, выдачу прав или удаление.
- Group: status/fixtures исправлены,20 boundary cases и live8e83 закрыты. Matching
  JARs/1530 backend inputs прежние. Runtime12v3180f и cohorta23logic reviewed;
  historical57binding требует fresh frontend freeze/currentRootGO после1057.
  Следующий результат — один UI delete с числовым preview/current password/original
  UUID, шестью открытыми WEB/PWA/TMA views, естественным отзывом целевого доступа
  и сохранением чужих аккаунтов/ролей/групп. Полный SID может остаться для другой роли.
- Q-ENV-010 pending:≈5.36GB free против7GiB preflight. Прежние+2GiB уже впритык/
  недостаточны; практично добавить3GiB или обеспечить≥7GiB свободного с запасом.
  Источники/БД/Dockerданные сохраняются, guards не ослабляются. Start/fixtures/delete0.
- PWA hostlogout visualP3 закрыт: source343+actualafter2; gallery278 включает3 новых
  actual кадра (after2/WT04) и2 переиспользованных оригиналаAdminMore79e. Карта42
  bounded observed/8unchecked из50 (7activeconditional+1orphan); не процент готовности.
- WEB theme native device persistence подтверждён, oldrawFAIL/pageerrors сохранены.
  Реальный ownerloss bug reproduced/fixed1057/review+6tests+strictbuild PASS. Actual
  runtimefe795 показал native204/401/errors0, но testwait остановил guestoracle.
  Минимальный v2 исправляет наблюдение terminal/body, не повторяет theme4. Затем
  один ownHeadman history readonly packet; никакого нового grant/fixture/command.
- После group gate — полное удаление Angular по inventory и assembled commonNginx:
  deep links/refresh/static/API/realtime,/app/,/mini-app/,auth/updates/WebPush.
  Angular пока активен; предыдущие Vue build это gate не закрывают.

Новых продуктовых вопросов нет. Owner нужен только для ресурса Q-ENV-010.
Recovery snapshot223044Z сохраняется неизменным; новые source changes также
сохранены в Git/verified incremental bundles, без секретов/данных стенда.
