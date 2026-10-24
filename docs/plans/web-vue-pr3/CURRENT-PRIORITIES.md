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

Текущие пакеты после опубликованного888 checkpoint:

- Password CLOSED: native self-known5→6 /lostACK6→7 и successor isolation PASS;
  independentpost9598 подтверждает cleanup. Не повторять генерацию/старую очистку.
- LIMITED CLOSED: natural visible-page withdrawal3708ms, oldADMIN/session403,
  Teacher/profile200. Raw driver exit1 сохранён как TEST enum gap. Independent
  postc7ee: profile8unassigned, все4 собственные сессии revoked, чужие данные целы.
- Group status и fixtures исправлены,20 boundary cases CLOSED.64f/backend,
  ae546/client, f67/UI и authority-retirement3e174 интегрированы. Найден APP gap
  адресной доставки удаления на открытые WEB/PWA/TMA;8e83 исправляет hidden hint
  и active projection refresh, review/F1recheck/55client/6unit/2PG/1Mongo PASS,
  интегрированb039. Следующий результат: matching JARs + real same-realm Notification
  и один UI delete с числовым preview/паролем/original receipt, отзывом открытого
  доступа и независимым сохранением чужих аккаунтов/ролей/групп. До запуска —
  qualification shared schema/JAR consumers и ресурсный admission.20PASS не повторять.
- Визуал параллельно:249 actual gallery PNG с отдельными qualifications, TMA
  controlled host отделён от native provider. Compact navigation исправлена и
  проверена300/root16/root20 +widecontrol. Profile loading skeletonc35 reviewed
  и integrateda851;2 held-read after кадра next, loaded1440 reuse если пригоден.
  Остальные экран/тема/адаптив/состояния — конечный список, без общего процента.

После закрытия единственного оставшегося ADMIN action gate — полное Angular
removal по inventory и assembled common-Nginx flow. Никакой промежуточный PASS
не закрывает эти gates. Вопросов, ожидающих владельца, сейчас нет.
