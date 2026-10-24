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

- Пароль, LIMITED и whole-map delete/Auth — закрыты. Посты9598/c7ee подтверждают
  отзыв собственных сессий/временных полномочий; успешные операции не повторять.
- Group: status/fixtures исправлены,20 boundary cases закрыты. Live8e83 и matching
  Academic/Notification/BFF JAR приняты. Runtime12 v3180f и cohort a23logic review
  PASS; final source binding57e2d8f8 pins Root85c/productd69/558frontend inputs,
  узкий binding review PASS. Следующий результат — один UI delete с числовым
  preview/паролем/original UUID, шестью заранее открытыми WEB/PWA/TMA views,
  естественным отзывом целевого доступа и сохранением чужих аккаунтов/ролей/групп.
  Auth может оставить SID для другой роли; проверять grants и версию сессии.
- Q-ENV-010 pending: фактически5.38GB, нужен preflight7GiB и свежий Root GO.
  Запрошено минимум+2GiB с сохранением workspace/Docker-данных. До этого start,
  fixtures и delete0; ограничения и guards не ослабляются.
- Визуал: Journal skeleton/error/retry и WEB Teacher focused live/pagination
  исправлены и независимо приняты наd69; all3 Vue build/strict PASS переиспользуется.
  Student selection→readonly draft6 actual frames на85c закрыли локальную валидацию,
  пустой фильтр, выбор, focus и переход без отправки. Gallery267/portable267;
  PWA40 bounded observed consumers и10 not visually checked из50, это не полная
  готовность40 экранов. TMA controlled host/provider разделены. Следующий лёгкий
  пакет — недостающие readonly Teacher views; сначала existing evidence, затем PREP.
- После настоящего group gate: полное Angular removal по inventory и assembled
  common Nginx (deep links/refresh/static/API/realtime, /app/, /mini-app/,
  auth/updates/WebPush). Angular пока активен; build PASS этот gate не закрывает.

Новых продуктовых вопросов нет; ответ владельца нужен только по ресурсу Q-ENV-010.
Source-only recovery223044Z.zip:6482207B, SHA256b4262299…, snapshot источников/WIP
с manifest/CRC/bundle проверкой; секреты/DB/runtime исключены. Это точка во времени,
а актуальные продуктовые исправления уже опубликованы в существующей ветке PR3.
