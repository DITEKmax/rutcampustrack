# 12. Frontend — Landing (`/presentation/`)

## Сводка

Лендинг — единый статический артефакт `frontends/landing/dist/index.html` (~1647 строк, 67 KB) + `favicon.svg` + nginx-конфиг. Никакого build pipeline: inline-CSS, inline-JS, внешние CDN для шрифтов, иконок и GSAP. Контракт страницы закреплён в `docs/phase-57-report.md` — static HTML остаётся «единственным артефактом» ради простоты доставки. Это разумный выбор для маркетинговой страницы, но он обнажает несколько проблем, которые на backend-стороне закрыты CSP/security-заголовками.

**Главная проблема.** Корневой reverse-proxy nginx (`nginx/conf.d/default.conf:40`) ставит CSP со строгим `default-src 'self'` и whitelisted только inline-стили/хешированный inline-скрипт. Но сам лендинг тянет всё с `api.fontshare.com`, `fonts.googleapis.com`, `fonts.gstatic.com`, `unpkg.com` и `cdn.jsdelivr.net`. В продакшене все эти ресурсы заблокируются браузером — страница останется без шрифтов, иконок и анимаций GSAP. Визуальный «эффект стантры» умрёт, JS `window.addEventListener('load', …)` упадёт на `typeof gsap === 'undefined'`-ветке, но hero-заголовок и `[data-count]` счётчики навсегда останутся скрыты / в начальном состоянии (`opacity:0`, `transform: translateY(110%)`). Это блокер релиза.

**Побочные проблемы.** OG/Twitter metadata неполные (`og:image` отсутствует — соцсети покажут пустой preview), нет `meta name="robots"` и `canonical`, нет `sitemap.xml`/`robots.txt`. CTA-кнопка «Открыть в Telegram» на самом деле ведёт на `/login` (веб-панель) — текст вводит в заблуждение, плюс клик со стороны пользователя, который реально пытается открыть бота, приведёт к форме OTP вместо deep-link. Инлайн-скрипт декларации темы выполняется до загрузки `data-theme` стилей — корректно для FOUC, но сбрасывает `crossorigin` на preconnect для `fonts.googleapis.com` (нужен для CORS). `theme-transitioning` добавляется как класс в JS, но соответствующего CSS-правила в inline-стиле нет — `theme-transitioning` мёртв. Отсутствует `meta http-equiv` для совместимости, нет `<link rel="alternate">` для EN-версии (сайт только на русском, но `lang="ru"` подтверждён).

**Счётчики:** P0=2, P1=6, P2=9, P3=7.

---

## Структура модуля

```
frontends/landing/
├── Dockerfile          ← FROM nginx:1.27-alpine, COPY dist, COPY nginx.conf
├── nginx.conf          ← inner nginx; cache-policy для /index.html и статики
└── dist/
    ├── index.html      ← ~1647 строк: head + inline style + body + inline JS
    └── favicon.svg     ← gradient R, 664 байта
```

Особенности:
- **Нет build-шага.** Все файлы в `dist/` коммитятся вручную. Нет bundler'а, нет минификации, нет PostCSS (token fallback — ручное копирование из `frontends/shared/styles/tokens.css`, расхождения не отслеживаются).
- **Inline всё.** Один HTML-файл содержит весь CSS (~1030 строк) и весь JS (~180 строк).
- **Внешние зависимости (CDN).** Fontshare (Clash Display, General Sans), Google Fonts (DM Sans, JetBrains Mono), Phosphor Icons 2.1.1, GSAP 3.12.5 + ScrollTrigger. Все — без SRI, без self-hosting, без fallback.
- **Контейнер.** Nginx 1.27-alpine, отдаёт статику, без security-заголовков (все ставит внешний reverse-proxy).

Нарушения CLAUDE.md-правил в этом модуле нет (правила касаются Java/контрактов).

---

## Критичные проблемы (P0)

### P0-1: 🔧 TO-FIX через self-host — CSP блокирует все внешние ресурсы лендинга
**Статус (2026-04-18):** будет закрыто фиксом C0-6 — все CDN-ресурсы (шрифты Fontshare/Google, иконки unpkg, GSAP jsdelivr) скачиваются в `dist/assets/`, отдаются с того же домена. CSP корневого nginx остаётся строгой. См. `OWNER-ANSWERS.md` 02-Q-csp-landing.



- **Где:**
  - `frontends/landing/dist/index.html:33-41` — preconnect/stylesheet на `api.fontshare.com`, `fonts.googleapis.com`, `fonts.gstatic.com`, `unpkg.com`.
  - `frontends/landing/dist/index.html:1467-1468` — script на `cdn.jsdelivr.net` (GSAP + ScrollTrigger).
  - `nginx/conf.d/default.conf:40` — `Content-Security-Policy: default-src 'self'; script-src 'self' 'unsafe-hashes' 'sha256-…'; style-src 'self' 'unsafe-inline'; img-src 'self' data:; connect-src 'self' wss:; font-src 'self';`.
- **Что:** reverse-proxy добавляет CSP без whitelist'а внешних CDN. В результате:
  - `script-src 'self'` блокирует GSAP (`cdn.jsdelivr.net`) → `typeof gsap === 'undefined'` → ветка `mm.add('(prefers-reduced-motion: no-preference)', …)` не выполняется → hero-title остаётся с `transform: translateY(110%)` (строка 454), `.reveal` остаётся `opacity: 0` (строка 1072), `[data-count]` не анимируется. Страница **видимо сломана** — полупустой hero без заголовочного текста (он съехал вниз за overflow:hidden родителя `.line`).
  - `style-src 'self' 'unsafe-inline'` без whitelist'а блокирует внешние `<link rel="stylesheet">` на Fontshare, Google Fonts и Phosphor Icons → нет шрифтов (будет `system-ui` fallback по `var(--font-*)`), нет иконок (`<i class="ph …">` останется пустым `<i>`).
  - `font-src 'self'` блокирует `fonts.gstatic.com` — даже если бы `style-src` разрешил CSS-файл шрифта, сами font-файлы всё равно не подгрузятся.
  - `img-src 'self' data:` не влияет (иконки — SVG через CSS/webfont).
- **Риск:** блокер релиза. В проде страница `/presentation/` останется сломанной визуально: нет заголовка («Учёт посещаемости, который движется вместе с вами» — съехал за overflow), нет иконок возможностей, нет счётчиков, нет scroll-пина архитектуры. Deeplink из OG/поиска приведёт на разбитую страницу.
- **Как чинить:** два варианта, первый проще.
  1. **Self-host.** Сложить шрифты, иконки и GSAP в `frontends/landing/dist/assets/` (WOFF2 для font-family, CSS с `@font-face`, `gsap.min.js`). Убрать CDN-ссылки. Плюс: независимость, нет FOUT от удалённых CDN, CSP не надо трогать. Минус: +~400 KB статики, ручной апгрейд.
  2. **Расширить CSP.** В `nginx/conf.d/default.conf:40` добавить whitelist: `script-src 'self' 'unsafe-hashes' 'sha256-…' https://cdn.jsdelivr.net`, `style-src 'self' 'unsafe-inline' https://api.fontshare.com https://fonts.googleapis.com https://unpkg.com`, `font-src 'self' https://fonts.gstatic.com https://api.fontshare.com https://cdn.fontshare.com`. Дополнительно подумать про SRI и fallback на случай недоступности CDN.
- **Зависимости:** пересекается с 13-infra-docker-ci (централизованная политика CSP), 07-api-gateway (обсуждали реальный домен `ruttrack.site`).

### P0-2: «Открыть в Telegram» ведёт в web-login

- **Где:** `frontends/landing/dist/index.html:1104-1107` (header), `1182-1185` (hero CTA), `1428-1431` (footer CTA).
- **Что:** три места с кнопкой `<a href="/login" … ><i class="ph-duotone ph-telegram-logo"></i> Открыть в Telegram</a>`. `/login` — это web-panel SPA (см. `nginx/conf.d/default.conf:141-166`), не Telegram-бот. Пользователь, ожидающий уходящий deep-link на бота, получает форму ввода логина/пароля.
- **Риск:** грубое несоответствие текста и поведения. CTR от «Открыть в Telegram» падает: пользователь теряется, жмёт назад. Для преподавателей, у которых вообще нет Telegram (по CLAUDE.md — «TEACHER … БЕЗ Telegram»), текст прямо вводит в заблуждение.
- **Как чинить:** либо
  - заменить в этих трёх местах текст на нейтральное «Открыть» / «Войти»,
  - либо сделать две раздельные кнопки в hero и CTA:
    1. **Открыть в Telegram** → `https://t.me/<bot_username>?start=welcome` (deep-link в бота, `target="_blank" rel="noopener"`).
    2. **Войти в кабинет** → `/login` (для преподавателей/админов/web-панели старосты).
  Текущий Phosphor `ph-telegram-logo` оставить только на TG-кнопке. Для web-login использовать `ph-sign-in` / `ph-arrow-right`.
- **Зависимости:** требует узнать реальное имя бота в проде. Пересекается с 06-notification-bot (там TG bot endpoint).

---

## Серьёзные (P1)

### P1-1: отсутствует `og:image` и Twitter Card

- **Где:** `frontends/landing/dist/index.html:12-17` — секция OpenGraph объявляет только `og:type`, `og:title`, `og:description`, `og:url`, `og:locale`. Нет `og:image`, `og:image:alt`, `og:site_name`, `og:image:width/height`. Twitter-карточки не объявлены вообще (`twitter:card`, `twitter:title`, `twitter:description`, `twitter:image`).
- **Что:** поделиться ссылкой в Telegram/Slack/Twitter — превью будет либо без картинки, либо с дефолтным favicon, либо клиенты просто не покажут preview.
- **Риск:** потеря viral-распространения для маркетинговой страницы.
- **Как чинить:** нарисовать `og-image.png` (1200×630) с гранд-дизайном hero (лучше статично, не копия hero-device), положить в `dist/og-image.png`, добавить:
  ```html
  <meta property="og:image" content="https://ruttrack.site/presentation/og-image.png" />
  <meta property="og:image:width" content="1200" />
  <meta property="og:image:height" content="630" />
  <meta property="og:image:alt" content="RutTrack — учёт посещаемости РУТ МИИТ" />
  <meta property="og:site_name" content="RutTrack" />
  <meta name="twitter:card" content="summary_large_image" />
  <meta name="twitter:title" content="RutTrack — Учёт посещаемости РУТ МИИТ" />
  <meta name="twitter:description" content="Геоотметка за 10 секунд, расписание, статистика и уведомления в Telegram." />
  <meta name="twitter:image" content="https://ruttrack.site/presentation/og-image.png" />
  ```

### P1-2: нет `robots` и `canonical` meta

- **Где:** `frontends/landing/dist/index.html:8-17` — head-блок.
- **Что:** ни `<meta name="robots">`, ни `<link rel="canonical">`. Также в репозитории нет `frontends/landing/dist/robots.txt` и `sitemap.xml`.
- **Риск:** поисковики (Yandex/Google) получают сырую индексацию. Отсутствие `canonical` позволяет одинаково индексировать `https://ruttrack.site/presentation/` и (если когда-то появится) `https://ruttrack.site/presentation/index.html` — дубликат.
- **Как чинить:**
  ```html
  <meta name="robots" content="index, follow" />
  <link rel="canonical" href="https://ruttrack.site/presentation/" />
  ```
  Добавить `robots.txt` с `User-agent: * \n Allow: /presentation/ \n Disallow: /api/ \n Sitemap: https://ruttrack.site/presentation/sitemap.xml`. Sitemap — одна страница, статический.

### P1-3: в head нет preload приоритетных ассетов, но есть preconnect к заблокированным CDN

- **Где:** `frontends/landing/dist/index.html:33-37`.
- **Что:** `<link rel="preconnect" href="https://api.fontshare.com" crossorigin />` / `fonts.googleapis.com` / `fonts.gstatic.com` — три preconnect'а к доменам, на которые CSP запрещает обращаться (см. P0-1). Если мигрировать на self-host, надо:
  - `<link rel="preload" as="font" href="/presentation/assets/fonts/ClashDisplay-Bold.woff2" type="font/woff2" crossorigin>` для критичных начертаний.
- **Риск:** без preload'а WOFF2 h1 будет рендериться с системным fallback ~150 мс (FOUT).
- **Как чинить:** после миграции на self-host добавить preload только для Clash Display 700 (hero title) и DM Sans 400 (body). Остальное LCP не затрагивает.

### P1-4: нет SRI на CDN-скриптах/стилях

- **Где:** `frontends/landing/dist/index.html:36`, `40-41`, `1467-1468`.
- **Что:** `<link rel="stylesheet" href="https://unpkg.com/@phosphor-icons/web@2.1.1/…">` и `<script src="https://cdn.jsdelivr.net/npm/gsap@3.12.5/dist/gsap.min.js" defer>` без `integrity=` и `crossorigin=`. Компрометация CDN = внедрение произвольного JS/CSS в лендинг.
- **Риск:** supply-chain инъекция — особенно критично для страницы, на которую пользователи приходят до логина (XSS в логин-форме через DOM, стилинг фишинговой надстройки).
- **Как чинить:** либо self-host (решает сразу P0-1 и P1-4), либо добавить `integrity="sha384-…"` и `crossorigin="anonymous"` на каждый CDN-линк/скрипт. Хеши пересчитывать при каждом апгрейде (неудобно; self-host в итоге проще).

### P1-5: внутренний nginx лендинга не выставляет no-cache на index.html, если сервер блокирует `expires 0`

- **Где:** `frontends/landing/nginx.conf:7-11`.
- **Что:** конфиг `location = /index.html { add_header Cache-Control "no-cache, no-store, must-revalidate"; add_header Pragma "no-cache"; expires 0; }` корректен, но корневой proxy вызывает `/presentation/ { proxy_pass http://rct-landing-nginx:80/ }` с `proxy_pass` **без** `proxy_hide_header`/`proxy_pass_header`. По умолчанию nginx передаёт все заголовки, но из-за порядка location-блоков корневого конфига (см. `default.conf:70-74`) заголовки лендинга не теряются. Проблема в другом: `add_header Cache-Control` внутреннего конфига перекрывает корневой уровень только для `location = /index.html`, но при запросе `GET /presentation/` (без слэша в конце проходит через `location / ` внутреннего nginx → `try_files $uri $uri/ =404;` → резолвится на `/index.html`) **не попадает** в `location = /index.html` — он реагирует только на точное `/index.html`. Итог: когда пользователь приходит на `/presentation/`, index.html отдаётся без Cache-Control, и следующий деплой не увидит новых assets (404 на новый hash-файл).
- **Риск:** после деплоя новой версии часть пользователей увидят старый index.html (из `stale-while-revalidate` browser/CDN кеша) и сломанные ссылки. Для лендинга это не катастрофа, но симптом «после деплоя у меня ничего не обновляется» уже был в проекте.
- **Как чинить:**
  ```nginx
  location = / {
      add_header Cache-Control "no-cache, no-store, must-revalidate" always;
      add_header Pragma "no-cache" always;
      expires 0;
      try_files /index.html =404;
  }
  location = /index.html {
      add_header Cache-Control "no-cache, no-store, must-revalidate" always;
      add_header Pragma "no-cache" always;
      expires 0;
  }
  ```
  Или, проще, навесить `Cache-Control` на `location /` без регекса и положиться на то, что `favicon.svg` получит `no-cache` (не критично — ~600 байт).

### P1-6: текст карточки «Excuse-тикеты и помощники» расходится с v9.0-реальностью

- **Где:** `frontends/landing/dist/index.html:1279-1283`.
- **Что:** «Студент прикладывает файлы — они пересылаются старосте в Telegram. Староста может делегировать права помощнику.» Согласно отчётам 05 P0 и 06 P1, в v9.0 исповеди (excuse tickets) **переведены на backend** (Phase 59, см. CLAUDE.md). Файлы больше не пересылаются через Telegram — они теперь хранятся в системе. Помощники старосты (`headman_assistants`) — отдельный flow, который в лендинге не раскрыт.
- **Риск:** маркетинговая страница обещает flow, которого больше нет в системе. Пользователи, ожидающие «TG-пересылку», столкнутся с web-интерфейсом и наоборот.
- **Как чинить:** переписать карточку на v9.0-реальность: «Студент создаёт excuse-тикет в PWA или боте, прикрепляет фото. Староста одобряет в web-панели или TG.» CLAUDE.md раздел «Excuse-тикет» описывает flow — сверить тексты и подтянуть.

---

## Средние (P2)

### P2-1: `lang="ru"` правильно выставлен, но нет `hreflang`

- **Где:** `frontends/landing/dist/index.html:2`, head.
- **Что:** язык «ru» корректно, но если когда-то появится `/presentation/en/`, `<link rel="alternate" hreflang="ru" href="https://ruttrack.site/presentation/">` облегчит индексацию. Можно отложить до MVP англ. версии.
- **Риск:** нулевой сейчас.
- **Как чинить:** добавить, когда будет перевод.

### P2-2: `preconnect` к `fonts.googleapis.com` без `crossorigin`

- **Где:** `frontends/landing/dist/index.html:34`.
- **Что:** `<link rel="preconnect" href="https://fonts.googleapis.com" />` без атрибута `crossorigin` — preconnect не поднимет CORS handshake для шрифтов. Должно быть как строка 35 с `fonts.gstatic.com`.
- **Риск:** теряется 100-200 мс в DNS+TCP+TLS, которые preconnect должен был сэкономить.
- **Как чинить:** добавить `crossorigin` (или удалить preconnect, если перейти на self-host).

### P2-3: hero-hardcoded дата и число

- **Где:** `frontends/landing/dist/index.html:1204` (`ЧТ · 8 АПР`), `1208` (`94`), `1191-1194` (`10 сек`, `5 ролей`, `24/7`).
- **Что:** в hero-mockup отображается статичная дата «ЧТ · 8 АПР», статичный процент «94%» и три цифры в hero__meta. Это декоративно, но:
  - «8 АПР» устареет (сегодня 2026-04-18, на странице «8 АПР» — ровно текущая неделя, но через день это даст эффект «лендинг неживой»).
  - «94%» — выдуманный показатель.
- **Риск:** ощущение «лендинг не поддерживается». Это типичный Phase-57 (демо-данные) вопрос.
- **Как чинить:** заменить дату на относительную («сегодня») или убрать. «94%» оставить как пример — это OK для маркетингового mockup'а, но добавить `aria-label="пример: 94%"` или явную подпись «демонстрация».

### P2-4: `href="#hero"` в брендовом линке не работает гладко на странице без `#hero`

- **Где:** `frontends/landing/dist/index.html:1088`, `1444`.
- **Что:** `<a href="#hero" class="brand">` — клик по лого должен возвращать на hero. Это работает (у hero есть `id="hero"` на строке 1114), но при клике в footer'е происходит `scroll-behavior: smooth` на всю страницу. С учётом `<main id="main">` + sticky header — подойдёт.
- **Риск:** минимальный. Но `brand` в footer'е дублирует `<a href="#hero">` в header'е и duplicate landmark — оба `<a>` с aria-label `"RutTrack — на главную"` (но в footer'е aria-label только у header'ного, footer'ный без). NVDA может их смешать.
- **Как чинить:** `<a href="#hero" class="brand" aria-label="RutTrack — наверх страницы">` в footer'е, либо убрать `brand` из footer'а.

### P2-5: `aria-hidden="true"` на декоративных hero-элементах правильно, но `<svg class="hero__routes">` с `<animateMotion>` скрыт от a11y — а для motion-чувствительных пользователей не остановлен через `prefers-reduced-motion`

- **Где:** `frontends/landing/dist/index.html:1118-1165`.
- **Что:** SVG transit routes с `<animateMotion dur="9s" repeatCount="indefinite">` — чистый SMIL. CSS-правило `@media (prefers-reduced-motion: reduce) { html { scroll-behavior: auto; } }` (строка 169) не затрагивает SMIL-анимации в SVG.
- **Риск:** пользователи с вестибулярными нарушениями / эпилепсией увидят бесконечное движение dot'ов, даже если у них OS-level reduced-motion.
- **Как чинить:** добавить CSS:
  ```css
  @media (prefers-reduced-motion: reduce) {
    .hero__routes animateMotion,
    .hero__routes .pulse-dot { display: none !important; }
  }
  ```
  Или генерировать SVG через JS с уважением к `window.matchMedia('(prefers-reduced-motion: reduce)')` и не вставлять `<animateMotion>`.

### P2-6: `rel="noopener"` без `target="_blank"` — бессмысленный

- **Где:** `frontends/landing/dist/index.html:1104`, `1182`, `1428`, `1432`.
- **Что:** атрибут `rel="noopener"` не имеет эффекта без `target="_blank"` (и современные браузеры автоматически применяют `noopener` даже без явного указания). Это cargo-cult.
- **Риск:** ноль (просто мусор в HTML).
- **Как чинить:** убрать `rel="noopener"` из всех четырёх мест (оставив только там, где реально `target="_blank"`, если такое появится). Или, если планируется внешний deep-link `https://t.me/bot` (см. P0-2), то там — `target="_blank" rel="noopener noreferrer"`.

### P2-7: `<button type="button">` theme-toggle не имеет `aria-pressed`/`aria-live` для отображения текущей темы

- **Где:** `frontends/landing/dist/index.html:1100-1103`.
- **Что:** switcher меняет иконку (moon ↔ sun) через CSS `[data-theme="light"] .theme-toggle__sun { display: inline-block; }`, но для screen-reader'ов кнопка остаётся `aria-label="Переключить тему"` без индикации текущей темы. Нажатие → нет announcement'а.
- **Риск:** a11y-шероховатость, не блокер.
- **Как чинить:** обновлять `aria-pressed` в JS при клике: `btn.setAttribute('aria-pressed', next === 'dark' ? 'false' : 'true')` + `aria-label="Текущая тема: dark. Переключить на light"`.

### P2-8: scroll-pinned `#architecture-flow` на мобильных деградирует правильно, но hero на `(min-width: 1024px)` получает `grid-template-columns: 1.15fr 0.85fr` без `min-width: 0` на колонке, что приводит к горизонтальному overflow на планшете 1024-1280px

- **Где:** `frontends/landing/dist/index.html:406-408`.
- **Что:** `.hero__inner { grid-template-columns: 1.15fr 0.85fr }` без `min-width: 0` на колонке hero-текста. Если в h1 попадёт длинное неразрывное слово (URL, `&nbsp;`-склейка «вместе с&nbsp;вами»), текст пушит колонку и либо уходит за viewport, либо «толкает» .hero__visual. Визуально не критично при текущем тексте, но хрупко.
- **Риск:** перевод на другой язык — ломается вёрстка.
- **Как чинить:** `.hero__inner > *:first-child { min-width: 0; }` или `word-break: normal; overflow-wrap: break-word;` на h1.

### P2-9: `box-shadow` с `0 0 8px rgba(255,255,255,0.8)` на brand::after сработает как ореол в light-theme

- **Где:** `frontends/landing/dist/index.html:274-278`.
- **Что:** hardcoded белый glow на точке в логотипе. В light-theme (`bg-primary: #F8FAFB`) белый glow невидим.
- **Риск:** нулевой (ореол всё равно декоративный), но small visual nit.
- **Как чинить:** использовать `var(--accent-primary-contrast)` или token'изировать.

---

## Мелкие и nit (P3)

### P3-1: `theme-transitioning` класс добавляется, но CSS-правила нет

- **Где:** `frontends/landing/dist/index.html:1488`, `1494`.
- **Что:** `root.classList.add('theme-transitioning')` и через 320ms `remove()` — но в inline-стиле (`:root`, `[data-theme]`, `body`) нет ни одного селектора `.theme-transitioning`. То есть класс вхолостую добавляется и убирается.
- **Риск:** мёртвый код. Безвреден.
- **Как чинить:** либо добавить CSS-правило (например, disable всех transitions на `.theme-transitioning *` и re-enable), либо убрать из JS (строки 1488, 1494).

### P3-2: `setTimeout(() => { root.classList.remove(...); }, 320)` — жёстко зашитое число без comments

- **Где:** `frontends/landing/dist/index.html:1494`.
- **Что:** 320 — предположительно `--duration-slow * 1.07`, но без коммента непонятно почему. Если мигрировать `--duration-slow` в 400ms, таймер отстанет.
- **Как чинить:** либо использовать `parseFloat(getComputedStyle(root).getPropertyValue('--duration-slow'))`, либо коммент `// --duration-slow + buffer`.

### P3-3: `<script>...</script>` перед `<link rel="stylesheet">` блокирует параллельную загрузку CSS

- **Где:** `frontends/landing/dist/index.html:20-30`.
- **Что:** inline-скрипт выбора темы до первого `<link rel="stylesheet">` — корректно (предотвращает FOUC), но тормозит HTML-parser на ~5ms. С учётом `defer` на внешних скриптах ниже — это OK, но маленькая оптимизация — вынести скрипт после critical preconnect'ов.
- **Риск:** ноль (малозначимый nit).
- **Как чинить:** оставить как есть; inline-скрипт в head перед стилями — стандартный паттерн для theme-flicker prevention.

### P3-4: `<svg class="hero__routes">` с `var()` внутри `<stop stop-color="var(--accent-primary)">` — в Firefox 120- CSS-переменные в SVG `stop-color` работают только на HTML-inline SVG, но не через `<img src=".svg">` или background

- **Где:** `frontends/landing/dist/index.html:1121`, `1126`, `1131`.
- **Что:** технически OK — SVG inline. Но если когда-то вынести в отдельный файл `routes.svg`, CSS-переменные сломаются.
- **Риск:** ноль сейчас; маленькая ловушка на будущее.
- **Как чинить:** коммент `<!-- NB: CSS vars work only because this SVG is inline -->`.

### P3-5: `clip: rect(0, 0, 0, 0)` в `.sr-only` — устарел

- **Где:** `frontends/landing/dist/index.html:207-214`.
- **Что:** старый способ скрытия от зрячих пользователей. Современный — `clip-path: inset(50%)` или известный паттерн от Tailwind/Bootstrap. Оба паттерна ~эквивалентны в 2026.
- **Риск:** ноль.
- **Как чинить:** обновить на современный паттерн:
  ```css
  .sr-only {
    position: absolute;
    width: 1px; height: 1px;
    padding: 0; margin: -1px;
    overflow: hidden;
    clip-path: inset(50%);
    white-space: nowrap;
    border: 0;
  }
  ```

### P3-6: в `<h4>` device-header используется без `<h5>` подрядов (в структуре h1→h4 пропущены h2, h3 выше)

- **Где:** `frontends/landing/dist/index.html:1202-1205`, vs. `section__title` h2 (строка 1245).
- **Что:** в hero-mockup `<h4>Сегодня</h4>` — это декоративный элемент внутри device-card, идёт до первого `<h2>` на странице (h2 в `#features` строка 1245). NVDA/JAWS обнаружат скачок в hierarchy.
- **Риск:** a11y-nit.
- **Как чинить:** использовать `<div class="device__title">` или `<p class="device__title">` вместо h4 (это mockup, не структурный элемент документа).

### P3-7: footer'ный `<a href="/login">Войти</a>` ведёт на web-panel — корректно, но в контексте «Telegram-first» страницы запутывает

- **Где:** `frontends/landing/dist/index.html:1452`.
- **Что:** все три hero/header CTA говорят «Открыть в Telegram» (хоть и ведут на `/login`), а footer'ный — «Войти». Есть рассинхрон; либо все «Открыть в Telegram», либо все «Войти». С учётом P0-2 предложенного раскладывания на 2 кнопки — согласовать с footer'ом тоже.
- **Риск:** когнитивный диссонанс, не блокер.
- **Как чинить:** после разделения CTA (P0-2): header — «Открыть», hero — «Открыть в Telegram» + «Войти в кабинет», CTA-section — зеркально, footer — «Войти в кабинет».

---

## Мёртвый код

- `frontends/landing/dist/index.html:1488, 1494` — `theme-transitioning` класс (см. P3-1).
- `frontends/landing/dist/index.html:34` — `preconnect` к `fonts.googleapis.com` без `crossorigin` (см. P2-2), не выполняет роль.
- Корневой файл `frontends/landing/Dockerfile` — нет проблем, но без `HEALTHCHECK` (nit, см. 13-infra).

---

## Костыли и TODO/FIXME

Нет явных `TODO/FIXME/HACK` маркеров в `index.html`, `nginx.conf`, `Dockerfile`. Код чистый. Скрытый костыль:

- `frontends/landing/dist/index.html:1073-1077`
  `.js .hero__eyebrow, .js .hero__subtitle, .js .hero__cta, .js .hero__meta, .js .hero__visual { opacity: 0; }` — это «обратный FOUC»: JS включается флагом `body.className = 'js'`, без JS (noscript) — всё видно, с JS (`js`) — скрыто до GSAP. Если CDN GSAP заблокирован CSP (P0-1), элементы навсегда остаются скрытыми. Это **подтверждает**, что P0-1 — блокер.

---

## Тесты

### Что покрыто хорошо

Ничего. Лендинг **без тестов**. Это приемлемо для статической маркетинговой страницы, но:

### Что покрыто плохо / не покрыто

- Нет e2e-смок-теста «`GET /presentation/` возвращает 200, содержит `<h1>Учёт посещаемости`».
- Нет проверки, что CSP не блокирует загрузку ассетов (LLR — большинство в проде упадут в console error).
- Нет Lighthouse-CI / axe-core проверки в CI (кросс-сервисно — см. 14-tests-audit).
- Нет проверки совместимости шрифтов с `lang="ru"` (Clash Display поддерживает Cyrillic — подтверждено, но закрепить regression-check не мешало бы).

### Некорректные/подозрительные тесты

Неприменимо.

### Кандидаты на удаление/рефакторинг

- Добавить smoke-тест в `.github/workflows/*.yml` по деплою `/presentation/` (см. 13-infra-docker-ci).
- Добавить Pa11y / axe-core в nightly (см. 14-tests-audit — кросс-срез).

---

## Соответствие CLAUDE.md

| Правило | Статус | Комментарий |
|--|--|--|
| Contract-first | неприменимо | лендинг не имеет контрактных модулей (пустая страница) |
| Enum lowercase | неприменимо | нет БД |
| Request/Response DTO | неприменимо | нет API |
| Lombok запрет в `*-api-contract` | неприменимо | нет Java |
| HATEOAS/RFC 7807 | неприменимо | нет REST |
| Flyway migrations | неприменимо | нет БД |
| Package isolation | неприменимо | один файл |
| REST пути | ⚠ | `/presentation/` зарегистрирован в `docs/product/url-layout.md` — OK, но в `frontends/landing/dist/index.html:16` `og:url` захардкожен — придётся сверять при переезде |
| Общая дизайн-система (docs/product/design-decisions.md) | ✅ | токены `--bg-*`, `--text-*`, `--accent-*` скопированы из `frontends/shared/styles/tokens.css` (коммент в строке 49 это подтверждает); но расхождение с tokens.css не отслеживается автоматически — см. 15-cross-cutting |

---

## Зависимости между проблемами

1. **P0-1 (CSP) блокирует выполнение JS-блоков**, от которых зависит весь UX:
   - P3-1 `theme-transitioning` — не страшно, он мёртв в любом случае.
   - P2-3 `[data-count]` CountUp-анимация — не выполнится.
   - «GSAP reveal» для `.hero__*` — скроет hero навсегда (см. «Костыли»).
   → фикс P0-1 **обязан** быть сделан до всех frontend-P1/P2 работ. Без этого лендинг в проде сломан.

2. **P0-2 (кнопка TG)** — blocks маркетинговый запуск. Решение независимо от P0-1, но требует знания реального имени бота (координация с 06-notification-bot).

3. **P1-1 (og:image)** нужно синхронизировать с Phase 57 — если маркетинговая картинка генерируется из hero-device mockup'а, то картинка зависит от P2-3 (чтобы процент «94» не менять после того, как OG-картинка уже в проде).

4. **P1-5 (cache-control)** — пересекается с 13-infra-docker-ci (политика кеша во всех внутренних nginx должна быть единой).

---

## Вопросы к владельцу проекта

1. ✅ **Self-host vs CSP-whitelist.** Я рекомендую вариант 1 P0-1 (self-host шрифты/иконки/GSAP в `dist/assets/`). Плюс: независимость от CDN uptime и санкций, уменьшение supply-chain. Минус: +~350 KB статики. Согласны? Если нет — пойдём через CSP-whitelist, но тогда надо сразу добавить SRI (P1-4).
   → **ACCEPTED BY OWNER (2026-04-18)**: **(a) Self-host**. P1-4 (SRI) → ❌ ОТКЛОНЁН (не нужен — нет CDN). См. `OWNER-ANSWERS.md` 02-Q-csp-landing.

2. **CTA-кнопка: куда она ведёт?** Сейчас `/login` — логин web-panel'и, но текст «Открыть в Telegram» (P0-2). Три варианта:
   - (a) Оставить `/login` + поменять текст на «Войти».
   - (b) Две кнопки: «Открыть в Telegram» (`https://t.me/<bot>` в новой вкладке) + «Войти в кабинет» (`/login`).
   - (c) Автодетект: если user-agent — TG WebView, deep-link в бота; иначе `/login`.
   Я рекомендую (b).

3. **OG-картинка (P1-1).** Нужна от вас (кто-то из дизайнеров/маркетинга) либо можно сгенерировать из hero-device — какой путь предпочитаете?

4. **Excuse-flow на лендинге (P1-6).** Текст описывает старый flow «файлы через Telegram». В v9.0 Phase 59 — это теперь backend. Какой marketing-tagline хотите для v9.0?

5. ✅ **Добавить страницу публичной политики конфиденциальности / пользовательского соглашения?** Для геоотметки и Telegram-login в РФ это почти обязательно (152-ФЗ). Сейчас лендинг ни на что не ссылается. Если да — отдельный лендинг-раздел (e.g. `/presentation/privacy/`).
   → **AUTO-RESOLVED (2026-04-18)**: M1 — проект вне юрисдикции РФ, 152-ФЗ не применяется. Privacy-страница НЕ требуется для v0.0.0. См. `OWNER-ANSWERS.md` 12-Q5.

6. **Ценность `/presentation/` как URL.** Это «внутренняя» страница (префикс `/presentation/`). Для SEO и social-share более привычный был бы корень `/`. Сейчас корень делает 301 на `/login` (`nginx/conf.d/default.conf:144-146`). Оставить так или сделать `/` = лендинг + `/login` отдельный?

7. **Lighthouse / Pa11y в CI.** Запускать на PR или только в nightly на проде?

8. **Favicon.** Сейчас только `favicon.svg` (664 байта). Нет `.ico`-fallback'а для старых Edge/Outlook; нет `apple-touch-icon.png` для iOS. Нужно?
