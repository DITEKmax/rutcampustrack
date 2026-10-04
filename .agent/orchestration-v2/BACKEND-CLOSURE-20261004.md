# Backend closure — 2026-10-04

Известный серверный остаток А–Ж и постоянный локальный стенд приняты по frozen contract. Код и инструменты main42cbc547; последующий коммит общих документов фиксирует этот результат. Это завершение текущего серверного этапа, не обещание готовности всех147историй, интерфейса или production.

## Результат для пользователя

Стенд работает по https://127.0.0.1:18514/app/ с доверенным mkcert TLS. Студент входит, открывает выбор роли, возвращается на свою главную и переключается на старосту; староста загружает главную без404 и также открывает выбор роли. После штатного Stop/Start и обновления страницы та же сессия старосты восстановилась. Базы и настройки теперь сохраняются между такими перезапусками.

Через настоящие API созданы изолированные группа, предмет/назначение и четыре аккаунта для ADMIN, TEACHER, HEADMAN, STUDENT/помощника VIEW_STATS. Общая матрица загрузки ролей, зависимых запросов, authoritative смены роли/свежих ws-ticket и auth/admin отказов прошла. После restart свежий ADMIN прочитал ту же группу, четырёх пользователей и назначение старосты по прежним ID. Это проверка выбранных данных, не новый полный бизнес-прогон.

## Изменённые файлы — полный source inventory

Относительно checkpoint6a058770: 13 уникальных файлов. Создано6, изменено7, удалено0. Новых Java domain files нет. Generated dist не считается новой функцией.

| Вид | Файл | Назначение |
|---|---|---|
| Modified/product | C:/Users/maksd/IntelliJIDEA/rutcampustrack/frontends/mobile-core/src/features/today/TodayScreen.vue | Вход студента в существующий выбор роли |
| Modified/product | C:/Users/maksd/IntelliJIDEA/rutcampustrack/frontends/mobile-core/src/features/today/today-screen.pcss | Оформление/focus этого элемента |
| Modified/product | C:/Users/maksd/IntelliJIDEA/rutcampustrack/frontends/mobile-core/src/features/headman-home/HeadmanHomeScreen.vue | Вход старосты в выбор роли |
| Modified/product | C:/Users/maksd/IntelliJIDEA/rutcampustrack/frontends/mobile-core/src/features/schedule/HeadmanScheduleScreen.vue | Callback через существующего owner и guards |
| Modified/product | C:/Users/maksd/IntelliJIDEA/rutcampustrack/frontends/mobile-core/src/shared/components/StudentFeatureOwner.vue | Online/scope/disposal guards |
| Modified/product | C:/Users/maksd/IntelliJIDEA/rutcampustrack/frontends/mobile-core/src/shared/navigation.ts | Явное сохранение исходной главной для Back |
| Modified/existing test | C:/Users/maksd/IntelliJIDEA/rutcampustrack/frontends/mobile-core/src/shared/navigation.test.ts | Проверки изменённого правила истории |
| Created/config | C:/Users/maksd/IntelliJIDEA/rutcampustrack/infra/local-stand/compose.yml | 14services, 8persistentvolumes, толькоlocalhost, read-only private mounts |
| Created/config | C:/Users/maksd/IntelliJIDEA/rutcampustrack/infra/local-stand/nginx.conf | Полный Gateway /api и Vue /app/, /mini-app/ |
| Created/tooling | C:/Users/maksd/IntelliJIDEA/rutcampustrack/infra/local-stand/mongo-entrypoint.sh | Mongo key ownership внутри контейнера |
| Created/config | C:/Users/maksd/IntelliJIDEA/rutcampustrack/infra/local-stand/.gitattributes | LF для shell entrypoint |
| Created/tooling | C:/Users/maksd/IntelliJIDEA/rutcampustrack/scripts/local-stand.ps1 | Initialize/Check/Start/Status/Stop, защищённые настройки внеGit, постоянные identities |
| Created/acceptance tooling | C:/Users/maksd/IntelliJIDEA/rutcampustrack/scripts/local-stand-acceptance.mjs | Продуктовые API fixtures, одна ролевая матрица и точный readback послеrestart |

Основная правка навигации сохраняет историю только при явном запросе: переход из главной в chooser возвращается на исходную главную, обычные deeplinks продолжают использовать прежнее правило. Authorization/role ACK остаются у существующего session owner:

```ts
if (entries[entries.length - 1]!.root !== route.root && !options.preserveHistory) {
  entries.splice(0, entries.length, rootRoute(route.root), route)
} else {
  entries.push(route)
}
```

Общие документы root отдельно: modified CURRENT.md, SLOTS.md, BACKEND-STAGE-20261001.md, metrics/packages.csv; created LOCAL-STAND-CONTRACT-20261004.md, этот BACKEND-CLOSURE-20261004.md, UI-ACCEPTANCE.md и два снимка UI. Полные пути находятся под C:/Users/maksd/IntelliJIDEA/rutcampustrack/.agent/orchestration-v2/ и .agent/evidence/role-control-20261004/. Private credentials/input/journal/keys внеGit; значения не выводились.

## Достаточная приёмка

- Source independent reviews значимых boundaries и affected corrections PASS. Role source6cf3d7aa: обе Vue typecheck PASS; один grouped build, один Vite-only retry после sandboxEPERM; обе native сборки PASS. Manifest frontend-manifest-6cf3d7aa.json сохранён. Восемь неизменённых JAR переиспользованы, Java rebuild не было.
- Постоянный stand: Initialize/resume/Check, private file ownership/identities, Mongo auth/rs0 и READY PASS. Семь точных host файлов перенесены в ownvolume из-за Windows AppData file-bind ограничения; 15 потребителей имеют read-only file subpaths. Private helper stopped.
- Штатный Stop/Start/Status: exit0, 14running/13healthy+Nginx, те же15containers/8volumes/7hostfiles; старые65containers неизменны. Source guards проверяют sealed private files; JWT private content не читалось.
- All-role API matrix принята один раз. После исправления TMA asset parser выполнены только два оставшихся HTTP шага; полный набор не повторялся. Check-after exit0, exact-record readback PASS, private phasechecked-after/ACLtrue.
- Root actual PWA role-control/Back/reload PASS. Screenshot headman-home-after-restart.png. Нет Figma/device/offline/nativeTelegramBack или полной TMA приёмки.

Evidence: .agent/evidence/local-persistent-20261004/runtime-cycle-after.json; .agent/worktrees/v2-runtime-build-r3/.agent/evidence/local-api-20261004/runtime-final.json (SHA32960A62D568D4CE87464E83702C351F5489406D63CA490CFD45EF703898AE5F); .agent/evidence/role-control-20261004/UI-ACCEPTANCE.md. C1–C4 frozen local acceptance закрыты4/4. Это не числитель147stories.

## Пакеты и оставшаяся работа

Bounded сверка известных criteria/source/evidence не выявила нового конкретного backend implementation gap. Полный аудит147историй не проводился. Прежние смешанные инженерные ориентиры сохраняют тот же знаменатель; production процент по полному release checklist пока не рассчитан.

| Пакет | Ориентир | Серверный результат / отдельный остаток |
|---|---:|---|
| А Семестры | ≈95%, без изменения | Archive/restore/write barriers/delete/history/scheduler приняты; весь административный UI и ручные состояния отдельно |
| Б Доступ | ≈95%, без изменения | Session/roles/recovery/OTP/QR/restoreбезправ приняты; теперь также all-role localhost startup/ACK. Full account/session manual и настоящий all-role TMA отдельно |
| В Учебный процесс | ≈92%, без изменения | Transfer/cancel/restore/georules/заявки/ДЗ/history/replay приняты; полные клиентские пути, вложения и ручная приёмка отдельно |
| Г Расчёты/файлы | ≈92%, без изменения | Расчёты, контексты и пять форматов приняты; фактические device downloads/длинные периоды/клиентские фильтры отдельно |
| Д Карты | ≈92%, без изменения | Серверный lifecycle/access/views/finaldelete принят; полный административный и мобильный UI цикл отдельно |
| Е Уведомления | ≈92%, без изменения | Durablepreferences/history/recipients/retry и реальная Telegram/WebPush доставка приняты ранее; все типы/роли/reconnect/optout/accountswitch в ручной матрице отдельно |
| Ж Эксплуатация | ≈75%, было70 | Native/application recovery приняты ранее; теперь постоянный localhost stand/сохранность выбранных данных приняты. Offsite storage/RPO/RTO/monitoring/release/production/deploy отдельно |

Общая функциональная реализация остаётся около90% (ориентир85–95), не измеренная production готовность. Рост Ж обоснован новым наблюдаемым результатом; число тестов и смена backend/client знаменателя не повышают А–Е.

До полного продукта остаются: Figma-сверка и недостающие UI состояния всех ролей; полная ручная сквозная PWA матрица с аккаунтами/правами/вложениями/выгрузками; install/update/offline/network/cache separation; настоящий TMA для всех ролей и повторного открытия с новым разрешённым подключением; полная матрица уведомлений; отдельные production/offsite/восстановление/наблюдаемость/release настройки. Найденные на этом этапе дефекты исправляются адресно, существование неизвестного дефекта заранее не объявляется новой задачей.

## Что ускорило и что тормозило

Подтверждено: reuse8JAR; одна frontend сборка с Vite-only retry; одна общая API матрица; два tail шага вместо её повторения; один выбранный restart/readback; неизменённые provider/recovery сценарии повторно не запускались. Один Docker владелец исключил гонки, independent source reviews защищали права/private boundaries.

Основной расход сегодня — новая Windows/Docker/bootstrap обвязка, не доменная разработка. Исправлены конкретные ACLSID/ECCurve/CRLF/AppData-bind/absentbash/directed-token-format проблемы; две transient команды отказали до эффектов на точном path/template. API adapter отдельно исправлялся из-за neutralADMINbootstrap и внешнего script tag Telegram. Эти расходы не считаются продуктовым прогрессом. Метрики неполны; количественное ускорение от Sol6.1 против Luna не доказано.

Дальше переиспользуем готовую среду и fixture данные. Независимые frontend экраны могут двигаться параллельно в отдельных worktrees/devservers; session/navigation/sharedcomponents — один владелец, общая приёмка — один стенд. Ранние nonsecret probes реальных image tools/file transport и чтение backend validator до генерации конфига снизят повторение сегодняшних ошибок. Дополнительные агенты не ускоряют один зависимый Docker lifecycle; массовый аудит тестов не нужен.

Стенд оставлен running. Старые65containers и privatebackup сохранены, public tunnel/Bot/provider/deploy/push не запускались. Дальнейший этап начинается с CURRENT, без повторного Initialize или полного server rebuild.
