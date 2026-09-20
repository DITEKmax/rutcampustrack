# RutCampusTrack — PWA: технические границы и UX-решения

**Срез:** 28.08.2026  
**Область:** мобильная PWA-поверхность и её различия с Telegram Mini App.  
**Не входит:** сборка Figma, редактирование кита, проектирование полного API.

## 0. Как читать документ

Метки доказательности:

- **A — первичный источник:** спецификация, документация или release notes производителя браузера/ОС.
- **A− — поддерживаемая справка совместимости:** MDN с browser-compatibility data.
- **B — инженерное руководство производителя:** web.dev / Chrome for Developers; надёжно для Chromium, но не следует автоматически переносить на WebKit.
- **C — воспроизводимый production issue:** полезен как свидетельство реального класса отказов, но не как универсальная статистика.
- **Проектное решение:** вывод для RutCampusTrack из нескольких источников. Это не утверждение источника.

Главное ограничение исследования: «PWA» — не один одинаковый рантайм. Опыт зависит как минимум от ОС, браузерного движка, способа запуска (`browser` / `standalone`) и разрешений. Поэтому ниже нет обещаний вида «PWA умеет X» без платформенной оговорки.

---

## 1. Короткий вывод для макетов и разработки

1. **RutCampusTrack должен оставаться полноценным в обычной вкладке.** Установка — усиление опыта, а не допуск к основным функциям. Даже установленный PWA может открыться во вкладке из-за пользовательской настройки, внешней ссылки или неподдерживаемого браузера. Chrome с версии 139 чаще захватывает ссылки установленным PWA, но решение всё равно включает пользовательскую настройку и browser fallback [S4].

2. **Основной режим установленного приложения — `standalone`.** `fullscreen` здесь вреден: скрывает системные ориентиры, но не даёт продукту полезного преимущества. `minimal-ui` и `window-controls-overlay` не являются мобильным кроссплатформенным контрактом. Manifest влияет на запуск установленной версии, но не на обычную вкладку [S2][S6].

3. **На iOS/iPadOS 26 изменилось само понятие установки:** любой сайт, добавленный на Home Screen, по умолчанию открывается как web app; manifest больше не условие installability. Пользователь может снять переключатель **Open as Web App**. Manifest всё равно нужен RutCampusTrack для `id`, имени, иконок, `start_url`, `scope`, цветов и ожидаемого режима [S1].

4. **Нельзя строить UX вокруг программной кнопки «Установить».** `beforeinstallprompt` не Baseline и не поддерживается на iOS [S2][S38]. В Chromium CTA показывается только после фактического события. На iOS — спокойная пошаговая подсказка Add to Home Screen, только после того как человек понял пользу установки. В in-app браузерах установка часто вообще недоступна [S37].

5. **Офлайн — “посмотреть последнее известное + сохранить явный черновик”, а не “всё работает как онлайн”.** Расписание, домашние задания, карта-каркас и последние сводки можно читать. Посещаемость старосты можно заполнять в локальный черновик, но статус «отправлено» появляется только после ответа сервера. Административные массовые операции, связывание аккаунта, смена ролей и окончательное проведение посещаемости — онлайн.

6. **Service Worker не является постоянно работающим фоном.** Браузер может остановить его через секунды простоя или прямо во время I/O; любой процесс должен быть коротким, идемпотентным и возобновляемым [S11][S19]. Background Sync не кроссбраузерный: Safari и Firefox его не поддерживают; fallback Workbox повторяет очередь лишь при следующем старте Service Worker [S17][S18].

7. **Web Push не равен Telegram-уведомлению.** На iPhone/iPad он доступен только Home Screen web app, после действия пользователя и системного разрешения [S20]. На Android и desktop установка не обязательна, но разрешение обязательно. Доставка, звук и показ зависят от Focus/Do Not Disturb, ОС и браузера. Push должен вести на устойчивый deep link, а не быть единственным местом, где находится информация.

8. **Badge и app shortcuts — только progressive enhancement.** На Android Badging API нет: launcher обычно показывает точку из непрочитанного уведомления, а не число [S27]. Manifest `shortcuts` не Baseline [S28]. Нельзя прятать в них основной путь и нельзя рассчитывать, что они мгновенно перестроятся после смены роли.

9. **Back-поведение обязано быть спроектировано отдельно.** Во вкладке есть browser Back; в `standalone` браузерной панели нет [S5]. Detail-экраны получают видимую стрелку. Если пользователь пришёл по deep link и внутренней истории нет, стрелка ведёт в логического родителя, а не вызывает слепой `history.back()`.

10. **PWA и Mini App могут разделять компоненты и информационную архитектуру, но не оболочку.** У PWA нет аналога нативного Telegram `MainButton`/`BackButton`, Telegram-шапки, Telegram-аккаунта и bot delivery. У Mini App нет переносимого эквивалента установленного app icon, OS shortcuts и надёжного PWA offline cache. Нужен один core и два platform adapters.

---

## 2. Матрица платформ

Обозначения: **да** — можно считать базовой возможностью; **частично** — только после установки/разрешения или с разным поведением; **нет/не контракт** — нельзя закладывать в обязательный flow.

| Возможность | Android, Chromium/Firefox | iOS/iPadOS 26, вкладка | iOS/iPadOS 26, Home Screen web app | Desktop Chrome/Edge | macOS Safari web app / Firefox Windows |
|---|---|---|---|---|---|
| Установка | Да; Chrome/Edge/Firefox/Samsung Internet поддерживают install/A2HS. В Chromium есть browser promotion при критериях [S2][S3] | Через Share → Add to Home Screen; `beforeinstallprompt` нет [S2][S38] | Любой добавленный сайт по умолчанию web app; пользователь может выбрать bookmark mode [S1] | Да; manifest-driven PWA и установка любого сайта как app [S2] | Safari 17+ Add to Dock для любого сайта; Firefox Windows 143+ умеет web-app window, но это не единый manifest-driven контракт на всех desktop ОС [S2][S40] |
| Программно вызвать install prompt | Частично: Chromium `beforeinstallprompt`; только когда событие реально пришло [S3][S38] | Нет | Не нужно внутри уже установленного app | Частично: Chromium | Не использовать как общий контракт |
| `display: standalone` | Да после установки | Не действует во вкладке | Да, но iOS 26 оставляет последнее слово пользователю [S1] | Да после установки | Safari web app — отдельное окно; Firefox Windows — отдельное app-like окно [S40] |
| Browser chrome / URL bar | Есть во вкладке; исчезает/сокращается в установленном режиме | Есть | Нет обычной browser navigation UI; системный status area остаётся | Есть во вкладке; в app window нет обычной вкладочной UI | Зависит от web-app реализации; основные маршруты не должны зависеть от chrome |
| Service Worker + Cache API | Да, HTTPS | Да, HTTPS | Да, HTTPS; установка на iOS не требует SW, но offline требует [S1][S11] | Да | Да в современных Safari/Firefox; проверять физическими устройствами [S11] |
| Полноценный offline после первого посещения | Частично; только заранее сохранённые данные | Частично; browser storage может быть вытеснен | Частично; installed container устойчивее, но не гарантирует данные, которые не были кешированы | Частично | Частично |
| One-shot Background Sync | Chromium — да; Firefox Android — не считать контрактом | Нет | Нет | Chromium — да | Safari/Firefox — нет [S17] |
| Periodic Background Sync | Только Chromium, установленная/запускавшаяся PWA и усмотрение браузера; это не точное расписание [S29] | Нет | Нет | Ограниченно Chromium | Нет как общий контракт |
| Web Push | Да после permission; установка обычно не обязательна [S22][S25] | На iOS — не для обычной вкладки | Да с iOS/iPadOS 16.4+, только после user gesture + permission [S20] | Да после permission | Safari 16.1+ на macOS; web apps поддерживают push. Firefox/Chromium — browser push [S20][S22] |
| App badge | Android: API нет, OS может дать notification dot [S27] | Нет app icon | Да, но значок виден только после notification permission; пользователь может отключить badge отдельно [S26] | Chrome/Edge Windows/macOS — да в установленном PWA [S27] | macOS Safari web app поддерживает badging; Firefox — не считать контрактом |
| Manifest app shortcuts | В установленном Chromium — да; в целом не Baseline [S28] | Нет надёжного общего поведения | Не закладывать | Установленный Chromium — да | Платформенно неоднородно; enhancement only |
| Safe area CSS | `env(safe-area-inset-*)`; обычно нули на прямоугольном viewport | Обязательно для notch/home indicator | Обязательно | Обычно нули, но API тот же | Обычно нули; desktop overlay — отдельная возможность, не нужна mobile UX [S7] |
| Стандартный выбор/загрузка файла | Да, `<input type=file>` | Да | Да | Да | Да [S30] |
| File System Access (`showOpenFilePicker`, writable handles) | Chromium — да; не кроссбраузерно | Нет как общий контракт | Нет как общий контракт | Chromium — да | Safari/Firefox — нет как общий контракт [S31] |
| Web Share | Частично, feature detect | Частично, feature detect | Частично, feature detect | Неоднородно | Неоднородно [S32] |

### Что следует из матрицы

- Макет нельзя подписывать просто **PWA mobile**. Для QA нужны как минимум состояния: `browser`, `standalone`, `offline`, `install available`, `install instructions iOS`, `push unavailable`, `push denied`, `update ready`.
- Нельзя скрывать нижнюю навигацию или критическую кнопку только потому, что есть app shortcut, badge или push.
- Файловый flow проектируется на стандартном `<input type=file>`; File System Access и Web Share — улучшение при feature detection.
- Реальный минимум поддержки задаётся не «версиями PWA», а тест-матрицей: Android Chrome/Firefox, iPhone Safari tab/Home Screen, desktop Chrome/Edge и Safari macOS, плюс Telegram клиенты в отдельной ветке.

---

## 3. Установка, запуск и manifest

### 3.1 Рекомендуемый контракт manifest

Это не готовый production manifest, а перечень обязательных решений:

```json
{
  "id": "/app",
  "name": "RutCampusTrack",
  "short_name": "CampusTrack",
  "start_url": "/app?source=pwa",
  "scope": "/",
  "display": "standalone",
  "theme_color": "<существующий theme token>",
  "background_color": "<существующий surface token>",
  "icons": [
    "192px PNG",
    "512px PNG",
    "maskable 512px"
  ]
}
```

**Проектные правила:**

- `start_url` не должен быть `/student` или `/headman`: одна установка переживает смену роли. `/app` восстанавливает сессию, определяет текущую роль на сервере и открывает её Главную.
- `id` должен оставаться стабильным при рефакторинге URL; иначе браузер может воспринять продукт как другое приложение.
- `scope` должен включать все продуктовые маршруты, но не внешние SSO/документацию. Выход за scope часто открывает browser/custom tab и визуально ломает непрерывность [S33].
- Нужны обычные и maskable-иконки. Значимые части логотипа удерживаются в безопасной зоне; не использовать произвольные новые цвета вне токенов.
- `display_override` не нужен для mobile MVP. `window-controls-overlay` — desktop-specific и создаёт дополнительные области, которые надо безопасно раскладывать; для RutCampusTrack это цена без мобильной пользы.

### 3.2 Когда предлагать установку

Не на первом экране и не модальным блокером. Предлагать после доказанной пользы:

- студент посмотрел расписание/домашнее задание второй или третий раз;
- староста завершил первую отметку посещаемости;
- пользователь включил напоминания;
- человек сам открыл «Установить приложение» в Профиле.

**Android/desktop Chromium:** скрытая по умолчанию строка/карточка становится доступной только после `beforeinstallprompt`; после отказа не показывается снова навязчиво. Chrome требует HTTPS, manifest и engagement criteria, прежде чем покажет promotion [S3].

**iOS/iPadOS:** отдельная короткая инструкция Share → Add to Home Screen → оставить **Open as Web App**. Нельзя обещать, что app сам откроет системный prompt. На iOS 16.4+ Add to Home Screen может быть доступен и из сторонних браузеров через Share [S2]; тексты не должны требовать именно Safari без feature/user-agent проверки.

**In-app browser:** если приложение открыто внутри мессенджера/соцсети, установка часто невозможна [S37]. Показать ненавязчивое «Открыть в браузере, чтобы установить», но только если платформа действительно позволяет открыть внешний браузер.

### 3.3 Browser и standalone должны быть двумя вариантами одного продукта

| Элемент | Browser tab | Standalone PWA |
|---|---|---|
| Верх экрана | Не дублировать адресную строку; продуктовая шапка компактнее | Продуктовая шапка несёт название контекста и back |
| Back | Основной — browser/OS; внутренний back остаётся на detail, если это помогает ориентации | Видимый back обязателен на detail; на корне не показывается |
| Install CTA | Возможен по правилам выше | Никогда не показывать |
| Bottom nav | Та же информационная архитектура; учитывать меняющуюся browser toolbar | Та же панель; учитывать home indicator и safe area |
| Push onboarding | Можно, но на iOS сначала объяснить необходимость установки | Можно после value moment и user gesture |
| Offline state | Работает, если SW уже установился и данные были сохранены | То же; сама установка не «скачивает всю базу» |

Определять текущий режим через `@media (display-mode: standalone)` / `matchMedia`, а на старых iOS при необходимости через `navigator.standalone`. Это определяет **текущий способ запуска**, но не даёт универсального ответа «приложение когда-либо установлено» [S5][S6].

---

## 4. Viewport, safe area и клавиатура

### Обязательные правила

1. `<meta name="viewport" content="width=device-width, initial-scale=1, viewport-fit=cover">`.
2. Для всех фиксированных нижних элементов:

   ```css
   padding-bottom: calc(var(--space-bottom) + env(safe-area-inset-bottom));
   ```

3. Для верхней оболочки учитывать `env(safe-area-inset-top)`, но не добавлять inset дважды, если системная/browser UI уже ограничила content viewport.
4. Высота основной оболочки — `min-height: 100dvh`; `100vh` оставить fallback. `dvh` меняется вместе с появлением/скрытием browser controls, тогда как классический `vh` может оставлять контент под панелью [S8].
5. Нижняя навигация, sticky CTA и клавиатура не должны складываться друг на друга. При открытой клавиатуре контекстный CTA либо поднимается над visual viewport, либо превращается в inline action; пятислотовая навигация может временно скрываться только если форма сохраняет очевидный путь назад.
6. Проверять не только notch, но landscape, большой системный шрифт, zoom 200%, Android gesture navigation и iPhone Home Indicator.

`safe-area-inset-*` — динамические значения среды; на обычном прямоугольном desktop viewport они равны нулю [S7]. Это безопаснее, чем разные «магические» нижние padding для iPhone и Android.

---

## 5. History и Back: контракт маршрутов

### 5.1 Правила роутера

- Каждый самостоятельный экран и каждое состояние, на которое ведёт уведомление, имеет URL.
- Detail/редактирование добавляет history entry.
- Закрываемый sheet/dialog должен первым поглощать Back; повторный Back возвращает на предыдущий экран.
- Bottom-nav root — логический корень. На нём видимой стрелки назад нет.
- Deep link без внутренней истории: back ведёт в заранее определённого родителя (`attendance/session/:id` → `attendance`; `ticket/:id` → `tickets`), а не за пределы приложения.
- Не использовать `history.length > 1` как доказательство внутренней истории: туда входят страницы до RutCampusTrack.
- Хранить app-owned marker/entry id в `history.state`. Navigation API использовать только после feature detection; History API остаётся fallback. Release notes Safari 26.3 документируют `AbortSignal` для прерванной Navigation API-навигации [S9][S34].
- При смене роли заменить корневой route (`replace`), очистить role-local navigation stack и открыть Главную новой роли. Назад не должно возвращать в экран, к которому новая роль не имеет доступа.

### 5.2 Незавершённые формы

`beforeunload` ненадёжен на телефоне: он не вызывается, если пользователь переключился в другое приложение, а затем закрыл браузер из app manager [S10]. Поэтому:

- локально автосохранять черновик на `input/change` и `visibilitychange`;
- показывать статус `Сохранено на устройстве` / `Не отправлено`;
- использовать внутренний confirm только при уходе через контролируемый route;
- `beforeunload` подключать лишь как дополнительную защиту и только пока есть несохранённые изменения;
- после серверного ACK удалять локальный draft.

Это особенно важно для старосты: потеря отметок посещаемости из-за сворачивания приложения недопустима.

---

## 6. Service Worker, cache и offline

### 6.1 Что Service Worker действительно гарантирует

- Он перехватывает запросы только в своём scope и только после установки/активации.
- Самая первая загрузка не становится магически офлайн: пользователь должен хотя бы один раз загрузить shell и необходимые данные.
- Браузер может останавливать worker; фоновые процессы не должны зависеть от непрерывного JS execution [S11][S19].
- Cache API хранит HTTP request/response; IndexedDB лучше для нормализованных пользовательских данных, версий, timestamps, draft/outbox.
- Обновление SW имеет lifecycle `install → waiting → activate`. Принудительный `skipWaiting()` без координации может смешать старую страницу и новые chunks; обновление нельзя считать обычным hot reload [S12][S13].

### 6.2 Рекомендуемые стратегии RutCampusTrack

| Класс данных | Стратегия | Offline | Freshness/UX | Почему |
|---|---|---|---|---|
| Versioned JS/CSS/fonts/icons | Precache / Cache First для hash-файлов | Да | Меняются с build hash | Быстрый app shell; immutable assets безопасно кешировать |
| HTML/navigation shell | Network First с коротким timeout + offline shell | Да | При сети берём новую оболочку | Снижает риск старого `index.html`, который ссылается на уже удалённые chunks |
| Аватары/некритичные изображения | Stale While Revalidate, лимит записей/возраста | Да | Возможна старая картинка | Не блокирует основной экран |
| `GET` расписания | Network First; normalized snapshot в IndexedDB | Да, read-only | Всегда показывать `Обновлено …`; просрочка меняет акцент | Основной быстрый сценарий студента/преподавателя |
| `GET` домашних заданий | Network First + последние активные элементы | Да, read-only | Deadline и версия обязательны | Полезно в метро; старая дата должна быть видна |
| Посещаемость: список пары | Network First + snapshot | Да для чтения/черновика | Перед отправкой обязательна сверка версии | Критичный конфликтный ресурс |
| Изменения посещаемости | Network Only для commit; IndexedDB draft/outbox | Черновик да; commit нет | `Не отправлено` до ACK; conflict screen при stale version | Нельзя выдавать локальную запись за принятую сервером |
| Статистика | Network First; кеш только summary | Частично | Не кешировать бесконечные таблицы | На телефоне нужна сводка, не локальная копия всей аналитики |
| Пользователи/группы/семестры администратора | Network First; offline skeleton/последняя сводка | Массовые действия нет | Чётко `Только просмотр, данные от …` | Права и статусы быстро меняются; риск утечки на shared device |
| Карта | Cache shell + базовые данные кампуса; tiles по лимиту | Частично | Не обещать весь campus/off-campus offline | Размер, лицензии и eviction |
| Auth, role, permissions, CSRF, logout | Network Only | Нет | При недоступности — честный экран | Нельзя подменять авторизацию кешем |

### 6.3 Offline state machine, которую надо нарисовать

```text
ONLINE_FRESH
  ├─ сеть пропала → OFFLINE_SNAPSHOT (есть кеш)
  └─ сеть пропала → OFFLINE_EMPTY (кеша нет)

OFFLINE_SNAPSHOT
  ├─ чтение → показать timestamp + offline badge
  ├─ редактирование допустимо → LOCAL_DRAFT
  └─ сеть вернулась → REVALIDATING

LOCAL_DRAFT
  ├─ пользователь нажал «Отправить», сети нет → QUEUED_EXPLICITLY
  └─ сеть есть → SENDING → ACK | CONFLICT | ERROR
```

В интерфейсе нельзя заменять все состояния одной красной плашкой «Нет интернета»:

- **есть данные:** «Офлайн · данные на 10:42»;
- **нет данных:** пустой offline экран с объяснением, что нужно открыть раздел онлайн хотя бы один раз;
- **есть draft:** постоянный локальный статус и отдельное действие `Отправить, когда появится сеть`;
- **идёт сверка:** skeleton/inline spinner только в изменившемся блоке;
- **конфликт:** показать, что изменилось на сервере, не перетирать молча.

### 6.4 Почему нельзя слепо включить Background Sync для посещаемости

Background Sync не Baseline [S17]. Workbox в неподдерживающих браузерах повторяет запрос, когда Service Worker снова стартует, а не гарантированно в момент появления сети [S18]. Для посещаемости это создаёт опасный разрыв: староста думает, что данные отправлены, а они лежат на устройстве.

Решение:

- хранить outbox независимо от поддержки Background Sync;
- показывать количество неотправленных изменений в самом приложении;
- при возврате online запускать явную сверку;
- Background Sync использовать только как ускоритель;
- request имеет idempotency key, user/role/lesson id и base revision;
- сервер отвечает ACK/conflict; только ACK меняет статус на «Отправлено».

### 6.5 Обновление приложения

Рекомендуемый UX:

- неблокирующая плашка `Доступно обновление`;
- для чтения — `Обновить` сейчас / позже;
- во время заполнения посещаемости или формы — не активировать новый SW автоматически; сначала сохранить draft, затем предложить reload;
- при несовместимом API/build — блокирующее обновление только после сохранения локального draft;
- error boundary для `ChunkLoadError` с одной безопасной перезагрузкой, а не белый экран.

Реальный failure mode: в Vite production issue пользователи с открытой старой сессией после deploy получали `Failed to fetch dynamically imported module` и пустой экран, потому что hash старого lazy chunk исчезал с сервера [S35]. Ранее тот же класс отказа многократно воспроизводился в CRA/service-worker cache [S36]. Следствия для deploy:

- старые hashed assets сохраняются хотя бы на период максимальной жизни старого client build;
- HTML не кешируется навсегда;
- build id логируется в error report;
- update flow проверяется с открытой вкладкой и активным draft.

---

## 7. Storage: объём, eviction и разделение аккаунтов

### 7.1 Факты платформы

- `localStorage + sessionStorage` ограничены примерно 10 MiB на origin; это не место для журналов и файлов [S14].
- IndexedDB, Cache API и OPFS живут в общей origin quota; запись может завершиться `QuotaExceededError`, который обязан обрабатываться [S14].
- По умолчанию storage — best effort. `navigator.storage.persist()` может запросить persistent mode, но Safari/Chromium принимают решение эвристически; пользовательского обещания «данные никогда не удалятся» нет [S14].
- Safari может проактивно удалить script-created data origin, с которым не было взаимодействия семь дней browser use; Home Screen web app первого домена явно исключён из этого ITP-механизма [S14][S15].
- Начиная с iOS 17/macOS Sonoma WebKit рассчитывает quota от диска; installed Home Screen/Dock app получает более высокий browser-app класс quota. Это не повод кешировать всю базу [S14][S16].
- Home Screen web app хранит website data отдельно от Safari [S15]. В UX следует быть готовым, что login/session и локальные настройки вкладки и установленного app не всегда ведут себя как одна копия.

### 7.2 Модель хранения RutCampusTrack

```text
Cache Storage
  app-shell:<build-id>
  public-media:<bounded-version>

IndexedDB
  snapshots { userId, roleId, semesterId, resource, revision, fetchedAt, payload }
  drafts    { userId, roleId, entityId, baseRevision, updatedAt, payload }
  outbox    { operationId, userId, roleId, entityId, createdAt, state, payload }
  prefs     { non-sensitive local UI preferences }
```

**Проектные требования безопасности:**

- caches/drafts namespace включает аккаунт и роль;
- logout, unlink, смена аккаунта очищают персональные Cache API, snapshots, drafts и outbox после подтверждения судьбы неотправленных данных;
- смена роли не показывает snapshot старой роли до повторной проверки permissions;
- access/refresh token не хранить в Cache API и по возможности не хранить в `localStorage`; предпочтительна защищённая cookie/session архитектура, если backend её поддерживает;
- offline snapshot содержит минимальный объём персональных данных;
- на shared university device — настройка `Не сохранять данные офлайн на этом устройстве`;
- каждый snapshot имеет TTL и видимый `fetchedAt`, но TTL не заменяет проверку прав.

`navigator.storage.estimate()` можно использовать для диагностики и мягкой очистки LRU-данных. Persistent storage запрашивать после того, как пользователь реально создал важный локальный draft, а не на onboarding.

---

## 8. Web Push и Notifications

### 8.1 Платформенный контракт

**iOS/iPadOS:** Web Push с 16.4 доступен Home Screen web apps. Запрос permission должен следовать за прямым пользовательским действием. Уведомления попадают в Lock Screen, Notification Center и paired Apple Watch, подчиняются Focus. Apple Developer Program для standards-based Web Push не требуется [S20]. В iOS/iPadOS 18.4 появился Declarative Web Push без обязательного Service Worker; это полезная WebKit-оптимизация, но не общий кроссбраузерный контракт [S21].

**Android/desktop:** Push API работает через service worker и browser push service; установка PWA обычно не обязательна [S22]. Chrome может автоматически снять notification permission у давно не посещавшегося или disruptive сайта [S25].

**Все платформы:** permission не означает гарантированную немедленную доставку/звук. Подписка привязана к конкретному browser profile/device. Push endpoint может протухнуть: `404`/`410` означает удалить subscription на backend и ждать новой подписки [S24]. Минимально гарантированный payload мал (4 KiB); уведомление передаёт id/deep link и короткий текст, а свежие данные приложение получает после открытия [S24].

### 8.2 UX разрешения

Плохой flow:

```text
Первый запуск → системный prompt «Разрешить уведомления?»
```

Рекомендуемый flow:

```text
Пользователь увидел ценность
  → экран «Какие уведомления нужны?»
  → выбрал конкретные категории
  → нажал «Включить»
  → системный permission prompt
  → подтверждение + ссылка на настройки
```

Категории для продукта:

- изменение/отмена ближайшей пары;
- новый дедлайн домашнего задания;
- старосте — незавершённая или конфликтная ведомость;
- преподавателю — значимое изменение по его паре;
- администратору — только события, требующие действия, без потока системного шума;
- ответы по заявке/тикету.

Не включать всё по умолчанию. Не просить push только ради badge.

### 8.3 Дизайн самого уведомления

- один primary tap открывает устойчивый in-scope deep link;
- если объект удалён или права изменились — понятный destination fallback, а не 404;
- не полагаться на notification actions, image, vibration или `requireInteraction`: представление различается между ОС;
- `tag/topic` может заменять устаревшие уведомления, например показывать только последнее изменение пары;
- не помещать чувствительные оценки/детали посещаемости в lock-screen текст; показывать нейтрально `Обновилась информация по занятию`;
- unread/actionable state хранится на сервере. Уведомление — доставка, не источник истины;
- при открытом приложении предпочтительнее in-app toast/refresh, чем дублирующее системное уведомление.

### 8.4 Backend notes

- хранить несколько push subscriptions на аккаунт, с platform/browser hints только для диагностики;
- привязывать preferences к типам событий, а не к одной общей галочке;
- удалять endpoints при `404/410`, соблюдать `429 Retry-After` [S24];
- строить deep link из entity id и текущей роли, но проверять permissions после открытия;
- для iOS не хардкодить FCM: endpoint выдаёт браузер; iOS использует APNs за стандартным Web Push endpoint [S20];
- push может инвалидировать snapshot, но не должен тихо кешировать чувствительные данные без открытия приложения.

---

## 9. Badge и App Shortcuts

### 9.1 Badge

Badge — вторичный сигнал, не основной navigation item.

- iOS/iPadOS: `setAppBadge` работает у Home Screen web app; число появится только после notification permission. Пользователь может оставить notifications, но выключить badges — сайт не узнает эту настройку [S26].
- Chrome/Edge desktop: badge доступен установленному PWA на Windows/macOS [S27].
- Android: Badging API не поддерживается; система может показать точку при непрочитанном notification [S27].

**Для RutCampusTrack:** badge означает число **действий, ожидающих пользователя**, а не все новые записи. При нескольких ролях — агрегированное число; внутри Профиля/role switch можно раскрыть по ролям. Нельзя показывать число только текущей роли на общем app icon без ясной семантики.

### 9.2 App Shortcuts

Manifest shortcuts открываются long press/right click на иконке установленного приложения, но feature не Baseline [S28]. Возможные role-neutral варианты:

- `Сегодня` → role-aware Today/Home;
- `Карта`;
- `Заявки` → если доступно роли, иначе корректный fallback;
- `Быстрое действие` не добавлять, если оно меняется по роли.

Почему не стоит делать `Отметить посещаемость` главным shortcut:

- он неприменим студенту/администратору;
- manifest metadata не является мгновенно обновляемой навигацией после role switch;
- платформа может обрезать список или вовсе не показать shortcuts [S28].

Если shortcut всё же ведёт в role-specific сценарий, URL сначала проверяет активную роль и показывает безопасный альтернативный экран.

---

## 10. Файлы, downloads, share и внешние ссылки

### 10.1 Базовый файловый flow

Кроссбраузерная основа — `<input type="file">` + File API [S30].

- `accept` — подсказка chooser, а не валидация; MIME/extension/размер проверяются сервером;
- `capture="environment"` можно предложить для фото документа/аудитории, но оставить выбор существующего файла;
- до загрузки показать имя, размер, тип, preview и удаление;
- upload имеет прогресс, cancel и retry;
- при offline файл остаётся локальным draft только после явного согласия; показать размер локального хранения;
- крупные файлы не отправлять через Background Sync: worker может быть остановлен, а Background Fetch не кроссбраузерный.

### 10.2 Не делать File System Access обязательным

`showOpenFilePicker/showSaveFilePicker` и writable handles хорошо поддержаны Chromium, но отсутствуют как общий Safari/Firefox контракт [S31]. Использовать только как enhancement для desktop export. На телефоне — стандартный chooser, server-generated download и Web Share при наличии.

### 10.3 Web Share

`navigator.share()` требует user activation, HTTPS и feature detection; поддержка файлов/targets зависит от ОС [S32]. Паттерн:

```text
if canShare(file/url) → системный Share
else if download supported → Скачать
else → Копировать ссылку
```

Не обозначать кнопку только иконкой share, если её результат на платформе меняется; подпись может быть `Поделиться` с fallback sheet.

### 10.4 External links и SSO

- Внешние/out-of-scope ссылки могут открыть browser/custom tab и вывести из standalone [S33].
- SSO callback должен возвращаться на in-scope HTTPS route; весь OAuth flow тестируется отдельно в browser и installed mode.
- Не предполагать, что localStorage/cookie state обычной вкладки и installed container полностью общий; WebKit изолирует Home Screen data [S15].
- Перед внешним переходом из незавершённой формы сохранить draft.
- PDF/экспорт не должен оставлять человека в navigation dead-end: после генерации сохранить экран результата с `Открыть`, `Поделиться`, `Вернуться`.

---

## 11. Что одинаково с Telegram Mini App, а что обязано расходиться

| Слой | Можно уравнять | Обязательное различие |
|---|---|---|
| Доменные экраны | Карточки, сводки, фильтры, таблица→карточки, empty/error/loading, role permissions | Telegram viewport/host chrome может менять доступную высоту; PWA browser и standalone тоже различаются |
| Информационная архитектура | Те же названия разделов и один primary route на экран | Частота entry points может отличаться: TMA часто открывают из конкретного сообщения/deep link, PWA — с app icon/shortcut/push |
| Bottom nav | Визуально близкая пятислотовая панель | В PWA это единственная app nav; в TMA рядом есть Telegram controls. Insets и Back обрабатывает adapter |
| Back | Одна логика parent routes/history | PWA browser использует browser Back, standalone — собственный control + OS gesture; TMA — Telegram `BackButton` |
| Primary action | Одинаковая команда и label | PWA рисует собственный sticky/FAB/inline CTA; TMA может делегировать в `MainButton`. Нельзя резервировать одинаковую физическую высоту |
| Theme/tokens | Семантика RutCampusTrack, состояния, контраст | PWA следует своей теме + `prefers-color-scheme`; TMA получает `themeParams`. Платформенные цвета проходят через mapping, а не заменяют продуктовые токены |
| Offline | Одинаковые тексты stale/draft/conflict | PWA имеет Service Worker/Cache/IndexedDB. Mini App нельзя считать эквивалентной offline PWA без отдельной подтверждённой реализации |
| Storage | Общая схема данных/draft/outbox | Реализация и quota разные; PWA IndexedDB/Cache, TMA CloudStorage/WebView adapters |
| Notifications | Одинаковые event categories и deep links | PWA — Web Push + OS permission; TMA — Telegram/bot delivery и настройки чата. Permission/отписка/атрибуция разные |
| Badge/shortcut | Общая семантика unread/actionable | PWA может иметь app icon badge/manifest shortcuts; Mini App не владеет отдельной OS app icon |
| Auth | Одна серверная учётная запись и role model | PWA начинает с web session; TMA получает Telegram init data и требует account linking. Смена роли одинакова доменно, вход разный |
| Files/share | Одинаковые server validation и attachment model | PWA использует browser File/Web Share; Telegram может дать свои host методы/ограничения |

### Предлагаемый platform adapter

```text
AppCore
  routeTo(screen)
  loadSnapshot(resource)
  saveDraft(entity)
  submit(operation)

SurfaceAdapter
  mode = pwa-browser | pwa-standalone | telegram
  insets()
  viewport()
  back.show/hide/subscribe()
  primaryAction.set/hide()
  share()
  notifyCapability()
  storageCapability()
```

Это позволяет сохранить один визуальный язык и доменную логику, не имитируя Telegram `MainButton` в PWA и не притворяясь, что Telegram WebView — установленный PWA.

---

## 12. Конкретные PWA-решения по ролям

Это не полный user-flow документ, а технические PWA entry points и offline/push implications.

### Студент

- `start_url`/app icon → Главная/Сегодня с last-known расписанием.
- Offline: расписание, активная домашка, campus map snapshot; всё с timestamp.
- Push: изменение пары, дедлайн, ответ по тикету.
- Shortcut enhancement: `Сегодня`, `Карта`.
- Файл: attachment к заявке/домашке через стандартный file input.

### Староста

- Возврат приложения должен восстанавливать незавершённую ведомость **как draft**, не как server state.
- При уходе в background — autosave по `visibilitychange`; `beforeunload` только дополнительная сетка [S10].
- Offline: список текущей пары + локальные отметки; commit только после ACK/revision check.
- Push: конфликт/неотправленная ведомость, изменение состава или пары. Не спамить каждым изменением студента.
- Не полагаться на shortcut `Отметить`: быстрый путь лучше дать на Главной по текущей/ближайшей паре, он работает на всех платформах.

### Преподаватель

- Offline: ближайшие пары и последняя summary-статистика read-only.
- Push: отмена/перенос пары, событие, требующее подтверждения.
- Большие статистические выгрузки — online; download/share progressive enhancement.

### Администратор

- PWA mobile — мониторинг и точечное действие, не перенос desktop registry целиком.
- Offline: только dashboard summary и последние просмотренные entities с заметным stale state.
- Создание/массовое редактирование пользователей, групп, семестров — online; для опасных действий fresh permission check.
- Badge — только actionable incidents, не общий объём изменений.

---

## 13. Реальные failure modes и чему они учат

| Наблюдение | Источник / доверие | Вывод для RutCampusTrack |
|---|---|---|
| После deploy старый client запрашивает удалённый hash lazy chunk; результат — blank page / `Failed to fetch dynamically imported module` | Vite issue с production reports [S35], **C** | Хранить предыдущие assets, иметь ChunkLoad recovery, не кешировать HTML бесконечно, тестировать upgrade при открытом app |
| Service Worker может быть прекращён во время I/O | Google PWA engineering article [S19], **B**, конкретный production design constraint | Очереди и миграции должны быть resumable; никакой длинной транзакции «в фоне» |
| Safari 26.6 исправлял регистрации SW с отсутствующим main/imported script, которые мешали новой регистрации | WebKit 26.6 release notes [S39], **A** | Тестировать обновление/удаление старых SW на реальных iOS; иметь экран recovery/clear local app data, не обвинять только backend |
| Chrome может удалить permission у давно неиспользуемого или disruptive notification origin | Chrome Help [S25], **A** | Permission status перепроверять; не считать `granted` вечным; просить разрешение после value moment и не спамить |
| iOS install UI нельзя вызвать программно; Add to Home Screen остаётся системным действием | MDN/WebKit [S1][S2][S38], **A/A−** | Не проектировать единую кнопку установки; отдельный iOS education flow |
| Home Screen storage в WebKit изолирован от Safari и исключён из семидневной ITP-очистки первого домена | WebKit [S15], **A** | Установка полезна для offline persistence, но login/settings между tab и app нельзя считать одной копией |

---

## 14. QA-чеклист перед передачей в production

### Режимы запуска

- Android Chrome: browser → install → standalone → external deep link.
- Android Firefox: install/A2HS и fallback поведения.
- iPhone iOS 26.6: Safari tab → Add to Home Screen с **Open as Web App** on/off → standalone.
- iPhone: Home Screen web app без push permission / denied / granted / Focus enabled.
- Desktop Chrome/Edge: tab/installed; Safari macOS: tab/Add to Dock.
- Firefox Windows 143+ web app как дополнительный вариант, но не единственная desktop acceptance platform [S40].

### Viewport

- portrait/landscape;
- notch/home indicator;
- browser toolbar expanded/collapsed;
- keyboard open on длинной форме;
- zoom и большой системный font;
- bottom nav + sticky CTA + toast одновременно.

### Offline/update

- первый запуск сразу offline;
- повторный запуск с snapshot;
- offline draft → закрыть app → открыть → отправить;
- conflict после восстановления сети;
- quota/write error;
- logout с draft/outbox;
- deploy новой версии, пока старая вкладка открыта на lazy route;
- update while attendance form has unsaved data;
- очистка browser storage/OS storage pressure.

### Push

- несколько устройств/браузеров одного аккаунта;
- role switch;
- permission denied/revoked;
- expired subscription `404/410`;
- deep link к удалённому/запрещённому объекту;
- уведомление при открытом app;
- отсутствие сети при tap на notification.

### Files

- camera/file chooser;
- wrong MIME despite `accept`;
- oversized file;
- upload interrupted/backgrounded;
- download/PDF из standalone;
- `navigator.share` absent or `canShare(file) === false`.

---

## 15. Открытые вопросы владельцу

1. Какой минимальный browser/OS support: допускается ли требовать iOS 16.4+ для push, и какой процент аудитории на более старых версиях?
2. Должен ли староста уметь **создавать offline draft**, или даже локальное редактирование посещаемости запрещено политикой данных?
3. Что считается server ACK посещаемости: вся ведомость атомарно или отдельные студенты? Это меняет conflict UX.
4. Разрешено ли хранить персональные данные/ФИО/посещаемость локально на личном устройстве? Каков TTL и требуется ли opt-in?
5. Какой механизм web auth: HttpOnly cookie/session, OAuth/OIDC, access token в JS? На каких origin живут IdP и API?
6. Нужны ли PWA push в дополнение к Telegram notifications или пользователь выбирает один канал, чтобы избежать дублей?
7. Что именно означает badge: unread, просроченные задачи или действия, ожидающие подтверждения?
8. Какие attachments реально нужны: фото, PDF, Office, несколько файлов, максимальный размер?
9. Нужно ли offline-покрытие campus map, и кому принадлежат tiles/лицензия?
10. Допустим ли общий PWA app icon для нескольких ролей, или нужны независимые установки/аккаунты? Рекомендация исследования — один app и role-aware start route.

---

## 16. Реестр источников

| ID | Источник | Дата/версия | Доверие | Для чего использован |
|---|---|---:|:---:|---|
| [S1] | WebKit — Safari 26.0, every site can be a web app | 15.09.2025; iOS/iPadOS 26 | A | Новая installability модель iOS 26, user toggle |
| [S2] | MDN — Making PWAs installable | обновлено 30.11.2025 | A− | Browser/platform install support, iOS 16.4 third-party A2HS, display |
| [S3] | web.dev — Chrome install criteria | обновлено 19.09.2024 | B | HTTPS/manifest/engagement для promotion |
| [S4] | Chrome Developers — PWA navigation management | 19.08.2025; Chrome 139+ | A/B | Link capturing, user preference, app vs tab |
| [S5] | web.dev — PWA app design | обновлено 20.09.2024 | B | Standalone без browser nav UI, собственный back |
| [S6] | MDN — Web App Manifest `display` | обновлено 08.08.2025 | A− | Display modes и fallback |
| [S7] | MDN — CSS `env()` | обновлено 04.08.2026 | A− | Safe area variables |
| [S8] | web.dev — CSS sizing / dynamic viewport units | обновлено 24.11.2025 | B | `dvh` против mobile toolbar |
| [S9] | MDN — Working with History API | обновлено 01.08.2025 | A− | SPA back/forward contract |
| [S10] | MDN — `beforeunload` | обновлено 21.08.2026 | A− | Mobile unreliability, `visibilitychange` |
| [S11] | MDN — Service Worker API | обновлено 15.08.2026 | A− | Scope, offline proxy, lifecycle |
| [S12] | web.dev — Updating a PWA | 10.03.2022 | B | Cache/update timing и live-update risk |
| [S13] | Chrome Workbox — `workbox-window` lifecycle | текущая документация | B | waiting/controlling/update UX |
| [S14] | MDN — Storage quotas and eviction | обновлено 05.01.2026 | A− | Quota, persistence, Safari proactive eviction |
| [S15] | WebKit — Home Screen domain exempt from ITP | 12.11.2020 | A | Isolated Home Screen storage и 7-day exemption |
| [S16] | WebKit — Safari 17 storage policy | 06.06.2023 / Safari 17 | A | Disk-based quota, Storage API persistence |
| [S17] | MDN — Background Synchronization API | обновлено 22.04.2024 | A− | Limited availability; Safari/Firefox gap |
| [S18] | Chrome Workbox — Background Sync | текущая документация | B | Replay on SW start without native Sync |
| [S19] | web.dev — Building a PWA at Google | 29.07.2020 | B | Worker termination during I/O, resumability |
| [S20] | WebKit — Web Push on iOS/iPadOS | 16.02.2023; 16.4+ | A | Home Screen-only push, gesture, Focus, APNs |
| [S21] | WebKit — Safari 18.4 Declarative Web Push | 31.03.2025; iOS/iPadOS 18.4 | A | Declarative push without mandatory SW |
| [S22] | MDN — Push API | обновлено 28.05.2025 | A− | Cross-browser Push API + service worker |
| [S23] | MDN — Notifications API | обновлено 25.05.2026 | A− | Persistent notifications и platform variance |
| [S24] | web.dev — Web Push Protocol | обновлено 20.09.2024 | B | VAPID, `404/410`, 4 KiB minimum payload support |
| [S25] | Google Chrome Help — site notifications | проверено 28.08.2026 | A | Permission auto-removal / disruptive origins |
| [S26] | WebKit — Badging for Home Screen web apps | 25.04.2023; iOS/iPadOS 16.4+ | A | Permission tie, user control, worker use |
| [S27] | Chrome Developers — Badging API | текущая документация | A/B | Desktop support, Android notification dot |
| [S28] | MDN — manifest `shortcuts` | обновлено 30.11.2025 | A− | Limited availability и OS discretion |
| [S29] | Chrome Developers — Periodic Background Sync | проверено 28.08.2026 | B | Chromium-only, engagement/browser discretion, не точное расписание |
| [S30] | MDN — `<input type=file>` / File API | проверено 28.08.2026 | A− | Кроссбраузерный file picker и server validation |
| [S31] | Chrome Developers — File System Access API | обновлено 19.08.2024 | B | Chromium-only advanced file access |
| [S32] | MDN — Web Share API | обновлено 13.03.2025 | A− | User activation, feature detection, files |
| [S33] | web.dev — Multi-origin PWAs | 19.08.2019 | B | Out-of-scope navigation и login flow |
| [S34] | WebKit — Safari 26.3 Navigation API | 11.02.2026 | A | AbortSignal при прерванной SPA navigation |
| [S35] | Vite issue #11804 | открыт 24.01.2023; production reports | C | Old lazy chunk → blank page после deploy |
| [S36] | CRA issue #3613 | открыт 16.12.2017; reproducible | C | SW + code splitting stale chunk failure |
| [S37] | web.dev — Learn PWA: in-app browser install warning | обновлено 20.09.2024 | B | Install недоступен во многих in-app browsers |
| [S38] | MDN — `beforeinstallprompt` | обновлено 28.07.2026 | A− | Non-Baseline, platform-gated install prompt |
| [S39] | WebKit — Safari 26.6 release notes | 27.07.2026 | A | Актуальные Service Worker bug fixes |
| [S40] | Mozilla Support — Firefox Web Apps for Windows | обновлено 24.06.2026; Firefox 143+ | A | Desktop Firefox Windows-specific web-app window |

### URL-адреса

[S1]: https://webkit.org/blog/17333/webkit-features-in-safari-26-0/
[S2]: https://developer.mozilla.org/en-US/docs/Web/Progressive_web_apps/Guides/Making_PWAs_installable
[S3]: https://web.dev/articles/install-criteria
[S4]: https://developer.chrome.com/docs/capabilities/pwa-navigation-management
[S5]: https://web.dev/learn/pwa/app-design
[S6]: https://developer.mozilla.org/en-US/docs/Web/Progressive_web_apps/Manifest/Reference/display
[S7]: https://developer.mozilla.org/en-US/docs/Web/CSS/Reference/Values/env
[S8]: https://web.dev/learn/css/sizing
[S9]: https://developer.mozilla.org/en-US/docs/Web/API/History_API/Working_with_the_History_API
[S10]: https://developer.mozilla.org/en-US/docs/Web/API/Window/beforeunload_event
[S11]: https://developer.mozilla.org/en-US/docs/Web/API/Service_Worker_API
[S12]: https://web.dev/learn/pwa/update
[S13]: https://developer.chrome.com/docs/workbox/modules/workbox-window
[S14]: https://developer.mozilla.org/en-US/docs/Web/API/Storage_API/Storage_quotas_and_eviction_criteria
[S15]: https://webkit.org/blog/11338/cname-cloaking-and-bounce-tracking-defense/
[S16]: https://webkit.org/blog/14205/news-from-wwdc23-webkit-features-in-safari-17-beta/
[S17]: https://developer.mozilla.org/en-US/docs/Web/API/Background_Synchronization_API
[S18]: https://developer.chrome.com/docs/workbox/modules/workbox-background-sync
[S19]: https://web.dev/articles/building-a-pwa-at-google-part-1
[S20]: https://webkit.org/blog/13878/web-push-for-web-apps-on-ios-and-ipados/
[S21]: https://webkit.org/blog/16574/webkit-features-in-safari-18-4/
[S22]: https://developer.mozilla.org/en-US/docs/Web/API/Push_API
[S23]: https://developer.mozilla.org/en-US/docs/Web/API/Notifications_API
[S24]: https://web.dev/articles/push-notifications-web-push-protocol
[S25]: https://support.google.com/chrome/answer/3220216?co=GENIE.Platform%3DAndroid&hl=en
[S26]: https://webkit.org/blog/14112/badging-for-home-screen-web-apps/
[S27]: https://developer.chrome.com/docs/capabilities/web-apis/badging-api
[S28]: https://developer.mozilla.org/en-US/docs/Web/Progressive_web_apps/Manifest/Reference/shortcuts
[S29]: https://developer.chrome.com/docs/capabilities/periodic-background-sync
[S30]: https://developer.mozilla.org/en-US/docs/Web/HTML/Reference/Elements/input/file
[S31]: https://developer.chrome.com/docs/capabilities/web-apis/file-system-access
[S32]: https://developer.mozilla.org/en-US/docs/Web/API/Web_Share_API
[S33]: https://web.dev/articles/multi-origin-pwas
[S34]: https://webkit.org/blog/17798/webkit-features-for-safari-26-3/
[S35]: https://github.com/vitejs/vite/issues/11804
[S36]: https://github.com/facebook/create-react-app/issues/3613
[S37]: https://web.dev/learn/pwa/progressive-web-apps
[S38]: https://developer.mozilla.org/en-US/docs/Web/API/Window/beforeinstallprompt_event
[S39]: https://webkit.org/blog/18178/webkit-features-for-safari-26-6/
[S40]: https://support.mozilla.org/en-US/kb/web-apps-firefox-windows
