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

Текущий пакет2026-10-24 после опубликованного checkpoint7ff:

- Password fix c586 интегрирован7ad6; source review/10 mounted cases и Root WEB build PASS. Native повтор ограничен изменёнными self-known/lostACK; новый harness исправляет три независимых cleanup/proof findings до GO. Старые генерации и очистка не повторяются.
- Group status endpoint и два дефекта fixtures исправлены: все13 Academic/Auth cases закрыты (8 новыхPASS+5 пригодныхPASS). Следующая зависимость — actual SchedulePG/AttendanceMongo, затем четыре сервиса и UI/open access.
- LIMITED actual Users200/Groups403→200 и revoke deny подтверждены. Open-page withdrawal FAIL; четыре собственные сессии независимо revoked, временного ADMIN нет. Причина сужена до отсутствия invalidation: bounded visibleADMIN reconciliation в отдельном WT; повтор только combinedgrant/revoke.
- Визуал параллельно:12 новых Today PWA кадров проверены, raw locator failure сохранён. Повтор только4 notification кадра после доказанной ошибки locator. Темы/адаптив/состояния не получают общего процента по количеству кадров.

Критерий закрытия ADMIN-пакета: исправленный self-password native исход без утечки
следующему входу, group preview/confirm/delete+withdrawal с сохранением чужих данных,
LIMITED open-page withdrawal с реальным изменением authority; успешные неизменённые
части переиспользуются. После этих зависимостей начинается полный Angular cleanup
и собранный common-Nginx сценарий. Ни один промежуточный PASS не включает эти gates.
