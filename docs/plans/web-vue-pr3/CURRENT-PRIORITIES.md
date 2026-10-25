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

Текущие пакеты и зависимости — checkpoint2026-10-25, product6219:

- Пароль, LIMITED и whole-map delete/Auth закрыты; independent posts9598/c7ee
  подтверждают cleanup. Не повторять generation/grants/delete по старымcheckpoint.
- Group status/fixtures и20boundary cases закрыты, live8e83 принят. MatchingJARs и
  1530backendinputs прежние. Runtime12v3180f/cohorta23logic reviewed; historical57
  binding требует freshfrontendfreeze после6219/currentRootGO. Следующий результат:
  один UIdelete/originalUUID, numericpreview/password/status и шесть заранееоткрытых
  WEB/PWA/TMA views, naturalwithdrawal/foreignpreservation. SID может жить для другойроли.
- Q-ENV-010: actual5,313,261,568B против7GiB preflight. Требуется около+3GiB или≥7GiB
  actualfree с запасом, сохранив workspace/Dockerdata. Guards неослаблены; start/
  fixtures/delete0. Этоединственный вопрос владельцу, не новыйпродуктовыйдопуск.
- Закрыты PWAhostlogout343/after2, WEBtheme4 и Studentownercancellation1057:
  6behaviorchecks/build/review плюс actualguest/privateabsent/errors0. Rawv2EXIT1/
  bodycompletionNOT_PROVEN сохранены; product-slice boundedaccepted, третийповтор
  не нужен. [Критерии](../../validation/web-vue-pr3/runtime-gallery/student-ownerloss-bounded-20261025/product-slice-accepted.json).
- Headmanhistory actual15events/Back/before2 закрыт. Source3371 исправляет только
  filterwrap/typeformatter; PWA/TMAstrictbuild/review и AFTER2PASS. ОбаP3закрыты
  при300light/root20 и768darkfocus. Новыефункциональныедействия0.
- Gallery287/portable287, finite45boundedobserved/5unchecked из50:4activeconditional+
  1orphan. Это не45fullyreadyscreens и неготовностьPR%. Original36/corrected37 preserved.
  [Before/after](../../validation/web-vue-pr3/runtime-gallery/headman-history-after-20261025/index.html).
- Завершён просмотр существующей одобренной справки преподавателем: обычный UI,
  настоящий GET вложения и возврат, twoPNG/EXIT0/errors0/owncleanup204+401. Готовая
  первая страница PDF не наблюдалась; loading/metadata не выдаются за previewPASS.
  На300/root20 найден узкий перенос даты/времени. TeacherCard CSS fix9e→6219
  прошёл независимый review/format/PostCSS и один matching PWA strict/build. Остался
  AFTER2 только для изменённого отображения. Допустимо одно bounded≤7s ожидание естественного PDFready
  для существенного ещё не проверенного риска; без retry/newAPI/newAuthloop.
- Завершён просмотр формы «Забыл отметиться»: fresh options200, ownlesson3, budget5/5,
  Continue disabled до выбора, два читаемых кадра с keyboardfocus. Submit/newrequest0,
  EXIT0/errors0/owncleanup204+401/browserclosed. Старый выбор типа и Excuse draft reused.
- Оставшиеся4activeconditional: два helper consumer без доказанных owncredentials,
  HeadmanGroup содержит секреты и не публикуется, TeacherManualExcuse отсутствовал
  в первой наблюдённой паре (не доказано глобальное отсутствие). Orphan отдельно.
  Не придумывать новые страницы, не подменять props/data и не менять права ради кадра.
- Послеgroupgate — полноеудалениеAngularпоinventory, затемassembledcommonNginx:
  deep links/refresh/static/API/realtime,/app/,/mini-app/,auth/updates/WebPush.
  Angularактивен; адресныеVuebuild этотgate незакрывают.

Source-only recovery004652Z.zip7,456,675B/SHA256
`0fdf5c3a2312352ecf3cb8c01acefaed6280a66f851a6cdea4725d6408aeda35`
доступен вчате.193commitsrelativecab3/12WT/133publicsources/199entries;
bundle/CRC/safepaths/manifest/sourcebytechecksPASS.193 — нечислоunpublishedcommits.
Private/secrets/DB/env/keys/JAR/deps исключены; nativeDIR1/25/history1/4 public
nonsecret findings классифицированы, unresolved0, rulesunchanged. СтарыйZIP сохранён.
[Receipt](../../validation/web-vue-pr3/checkpoints/remaining-admin-20261024/source-recovery-004652-final.json).
