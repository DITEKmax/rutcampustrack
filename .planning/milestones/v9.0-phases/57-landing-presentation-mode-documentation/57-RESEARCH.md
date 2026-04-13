# Phase 57: Landing Presentation Mode + Documentation — Research

**Researched:** 2026-04-13
**Domain:** Static HTML landing (GSAP ScrollTrigger animation) + project documentation sync
**Confidence:** HIGH

## Summary

Phase 57 делится на два независимых трека:

1. **Landing trek** — добавить в `frontends/landing/dist/index.html` одну новую секцию «Как работает система» с GSAP ScrollTrigger-анимацией сквозного потока (git push → CI → backend microservices → RabbitMQ → WebSocket/Push → user device). Обновить раздел «Роли», если 4-й карточки HEADMAN нет. Убедиться, что всё работает в dark/light и на 360-1440px. **Критически важно:** лендинг уже содержит 4 полных ролевых карточки (Студент / Староста / Преподаватель / Администратор, строки 1247-1295) и уже грузит GSAP + ScrollTrigger. LAND-v9-05 формально уже выполнен; работа сводится к полировке (тексты закрыть capabilities HEADMAN из v9.0) и добавлению одной новой секции для LAND-v9-02. LAND-v9-04 — регрессия, а не новая функциональность.

2. **Docs trek** — синхронизация `CLAUDE.md`, `docs/url-layout.md`, `docs/job-stories.md`, `.planning/PROJECT.md`. Сюда не нужен новый стек — только правки Markdown.

**Primary recommendation:** Landing — одна новая `<section id="architecture-flow">` с 6-шаговой GSAP ScrollTrigger-анимацией (pin + scrub), встроенная между секциями `#how-it-works` и `#roles`. Использовать уже подключённые `gsap@3.12.5` + `ScrollTrigger` — не добавлять плагинов. Роль `HEADMAN` в карточках уже присутствует — только уточнить тексты про v9.0 web-кабинет. Docs — чисто текстовые патчи (5 файлов) с явными diff-картами в плане.

## User Constraints

> CONTEXT.md для фазы 57 не создан (не запускался `/gsd-discuss-phase`). Ограничения извлечены из ROADMAP.md (Notes) и CLAUDE.md.

### Locked Decisions (из ROADMAP + CLAUDE.md)

- Landing — **статический HTML** без build pipeline. Все стили inline, JS inline. Редактируем `frontends/landing/dist/index.html` (single file).
- **GSAP 3.12.5** уже подключён как CDN defer script (строки 1345-1346). ScrollTrigger зарегистрирован на `window.load`. **Не переходить на npm/bundler** — нарушит контракт "no build pipeline".
- **Язык user-facing**: русский (CLAUDE.md + `response_language: ru` в config.json).
- **Design tokens**: использовать переменные из `:root` / `[data-theme="dark"]` / `[data-theme="light"]` (строки 52-100 index.html). Не вводить хард-кодед цвета.
- **Phosphor Icons** уже подключены (`duotone` + `regular`) — использовать их, не `<svg>` вручную.
- **prefers-reduced-motion** обязателен: существующий `gsap.matchMedia()` блок (строки 1395-1484) — расширить, новая анимация должна уметь fallback в статику.
- **prefers-color-scheme: dark + light** оба работают через `data-theme` + localStorage. Новая секция должна рендериться корректно в обеих темах.
- **viewport 360-1440px** — регрессия существующих медиа-брейкпоинтов: 640px, 1024px.

### Claude's Discretion

- Структура GSAP-секции: рекомендую **pinned ScrollTrigger + scrub-таймлайн с 6 шагами** (см. Pattern 1 ниже). Альтернатива — batch reveals как в текущем `.reveal` паттерне (строка 1443). Выбор остаётся за планировщиком.
- Иконки для 6 шагов потока: `ph-git-branch` → `ph-gear` → `ph-cube` → `ph-paper-plane-tilt` (RabbitMQ) → `ph-wifi-high` (WebSocket) → `ph-device-mobile`. Все duotone, доступны в Phosphor 2.1.1.
- HEADMAN описание в роли «Староста»: обновить буллеты с учётом v9.0 (web-cabinet /headman/*, PWA Группа tab). Можно оставить 4 буллета или расширить до 5.

### Deferred Ideas (OUT OF SCOPE)

- **Переход landing на build pipeline (Vite/Astro)** — отдельная фаза, не в v9.0.
- **Lottie/Canvas animations** вместо GSAP — избыточно.
- **Интерактивная 3D-диаграмма архитектуры** — за рамки LAND-v9-02.
- **Sitemap.xml / SEO аудит** — не требуется.
- **Перевод лендинга на английский** — не требуется.
- **Новые роли / изменение enum UserRole** — явно out-of-scope в REQUIREMENTS.md.

## Phase Requirements

| ID | Description | Research Support |
|----|-------------|------------------|
| LAND-v9-02 | GSAP scroll-driven "Как работает система" секция (git push → CI → backend → RabbitMQ → WS/Push → device) | Pattern 1 (pinned timeline + scrub), GSAP уже подключён |
| LAND-v9-04 | Лендинг responsive 360-1440px + dark mode через `prefers-color-scheme` | Существующая система `data-theme` + 2 брейкпоинта; регрессия проверяется responsiveness-check skill |
| LAND-v9-05 | Описаны все 4 роли (ADMIN, TEACHER, STUDENT, HEADMAN) | Уже присутствует в index.html:1247-1295. Проверить тексты, обновить буллеты HEADMAN с учётом v9.0 web-кабинета |
| DOCS-v9-01 | `CLAUDE.md` статус отражает v9.0 phase 49-57 + URL layout | Стейл строка 14: "v6.0: В РАБОТЕ" — нужно заменить на v9.0 статус |
| DOCS-v9-02 | `docs/url-layout.md` — таблица `/`, `/login`, `/app/`, `/presentation/`, `/admin/*`, `/teacher/*`, `/student/*`, `/headman/*` | Текущий файл (39 строк) описывает только dev-порты и контейнеры — нужна новая секция "Production path routing" |
| DOCS-v9-03 | `docs/job-stories.md` — добавить JS-STUDENT-WEB-01..10 и JS-HEADMAN-WEB-01..08 | Существующий файл описывает 31 упоминание «староста», но в контексте Telegram/PWA. Нужны новые истории для web-кабинета |
| DOCS-v9-04 | `.planning/PROJECT.md` — v9.0 в Shipped Milestones | `Recently Validated (v9.0)` секция уже ведётся (строки 200-207) — продолжить и переместить v9.0 в «Shipped» после phase 57 |

## Project Constraints (from CLAUDE.md)

Все директивы CLAUDE.md, релевантные phase 57:

1. **Русский язык** для всех user-facing текстов (включая лендинг, докстроки, коммит-сообщения).
2. **Contract-first** — не применимо (нет бэкенд-работы).
3. **ddl-auto: validate** — не применимо.
4. **Статус проекта в CLAUDE.md** (строка 14): `v6.0: В РАБОТЕ` — **ЯВНО УСТАРЕЛ**. DOCS-v9-01 требует его исправить: v6.0-v8.0 — ЗАВЕРШЕНЫ, v9.0 — В РАБОТЕ (завершается phase 57).
5. **После завершения фазы — обновить** `docs/phase-{N}-report.md` и статус в CLAUDE.md (директива из раздела «Инструкция для Claude Code»). Это означает, что plan должен включать создание `docs/phase-57-report.md`.
6. **Структура репозитория** — не изменять layout (в CLAUDE.md явно нарисован, строки 74-103).

## Standard Stack

### Core (уже подключено, не менять версии)

| Library | Version | Purpose | Why Standard |
|---------|---------|---------|--------------|
| gsap | 3.12.5 | Core tweening | Уже в CDN, верифицировано [CITED: frontends/landing/dist/index.html:1345] |
| ScrollTrigger | 3.12.5 | Scroll-driven animation | Уже загружен, зарегистрирован `gsap.registerPlugin(ScrollTrigger)` [CITED: index.html:1393] |
| @phosphor-icons/web | 2.1.1 | Icons (duotone + regular) | Уже подключён [CITED: index.html:40-41] |
| Fontshare Clash Display | — | Headings | Уже подключён |
| Google DM Sans + JetBrains Mono | — | Body + mono | Уже подключён |

### Supporting

| Library | Version | Purpose | When to Use |
|---------|---------|---------|-------------|
| — | — | Никаких новых зависимостей | Фаза доставляется только через правки HTML/Markdown |

### Alternatives Considered

| Instead of | Could Use | Tradeoff |
|------------|-----------|----------|
| GSAP ScrollTrigger | IntersectionObserver + CSS `@keyframes` | Меньше веса, но не даёт scrub-таймлайн и pin. LAND-v9-02 явно требует "scroll-driven animation section" — ScrollTrigger — идиоматический выбор. |
| GSAP ScrollTrigger | Framer Motion `useScroll` | Framer Motion — React-only, landing — plain HTML. Не подходит. |
| GSAP ScrollTrigger | Lottie JSON animation | Подошло бы для предрендеренного motion, но не даёт связь scroll ↔ progress. LAND-v9-02 говорит про flow с 6 шагами — императивнее делается через GSAP timeline. |

**Installation:** не требуется. Стек заморожен.

**Version verification [VERIFIED: index.html CDN URLs]:** gsap@3.12.5 и ScrollTrigger@3.12.5 уже pinned в `<script src="https://cdn.jsdelivr.net/npm/gsap@3.12.5/...">`. GSAP 3.12 вышел в 2023, 3.12.5 — актуальная бесплатная версия без GSAP Business. [CITED: https://gsap.com/]

## Architecture Patterns

### Recommended Structure (single-file landing)

```
frontends/landing/dist/index.html
├── <head> (inline <style> tokens + component CSS) — уже существует
│   └── + CSS для .arch-flow, .arch-step, .arch-arrow (новое)
├── <body>
│   └── <main>
│       ├── #hero
│       ├── #features
│       ├── #how-it-works
│       ├── #architecture-flow  ← НОВАЯ СЕКЦИЯ (LAND-v9-02)
│       ├── #roles  ← правка буллетов HEADMAN
│       └── .cta
└── <script defer> (GSAP init) — расширить существующий mm.add block
```

### Pattern 1: Pinned scrub timeline с 6 шагами потока [CITED: .claude/skills/gsap-scrolltrigger/SKILL.md]

**What:** Секция пинится к вьюпорту на 600-800vh скролла; внутри — горизонтальная лента из 6 шагов; GSAP timeline привязан к `scrollTrigger.scrub: true`, каждый шаг получает timed entrance через `stagger`/`position parameter`.

**When to use:** Для длинной линейной цепочки из N шагов, которую надо «прокрутить» как историю. Идеально подходит под LAND-v9-02 (git → CI → backend → RabbitMQ → WS/Push → device).

**Example:**

```javascript
// Source: .claude/skills/gsap-scrolltrigger + index.html:1395 (existing matchMedia block)
mm.add('(prefers-reduced-motion: no-preference)', function () {
  // ... existing hero & reveal code ...

  // NEW: architecture flow — pinned timeline
  const archTL = gsap.timeline({
    scrollTrigger: {
      trigger: '#architecture-flow',
      start: 'top top',
      end: '+=500%',           // pin for 5 viewport heights
      scrub: 0.6,              // smooth catch-up, not instant
      pin: true,
      pinSpacing: true,
      anticipatePin: 1
    }
  });

  archTL
    .from('.arch-step[data-step="1"]', { opacity: 0, y: 40, duration: 1 })
    .from('.arch-arrow[data-to="2"]', { scaleX: 0, transformOrigin: 'left center', duration: 0.6 }, '-=0.3')
    .from('.arch-step[data-step="2"]', { opacity: 0, y: 40, duration: 1 })
    .from('.arch-arrow[data-to="3"]', { scaleX: 0, transformOrigin: 'left center', duration: 0.6 }, '-=0.3')
    // ... steps 3..6 ...
    ;
});

// Reduced-motion fallback — расширить существующий block (index.html:1481)
mm.add('(prefers-reduced-motion: reduce)', function () {
  gsap.set('.arch-step, .arch-arrow', { opacity: 1, y: 0, scaleX: 1 });
});
```

**Responsive consideration (<1024px):** pin + scrub на мобильном даёт плохой UX (долгий скролл на "залипшей" секции). Рекомендуется использовать `gsap.matchMedia()` с двумя брейкпоинтами: для desktop — pinned scrub; для mobile (<1024px) — обычный `ScrollTrigger.batch()` reveal как в уже существующем коде (строка 1443).

### Pattern 2: Responsive matchMedia для разных экранов [CITED: SKILL.md gsap-scrolltrigger]

```javascript
mm.add({
  isDesktop: '(min-width: 1024px) and (prefers-reduced-motion: no-preference)',
  isMobile: '(max-width: 1023px) and (prefers-reduced-motion: no-preference)',
  isReduced: '(prefers-reduced-motion: reduce)'
}, function (ctx) {
  const { isDesktop, isMobile, isReduced } = ctx.conditions;
  if (isDesktop) { /* pinned scrub timeline */ }
  if (isMobile)  { /* simple batch reveal */ }
  if (isReduced) { /* gsap.set static state */ }
});
```

### Pattern 3: Dark/light theme через CSS custom properties

Секция архитектуры использует только токены `--bg-elevated`, `--text-primary`, `--accent-primary`, `--border-subtle`, которые уже переопределены в `[data-theme="light"]` (строки 92-100). Не добавлять хард-кодед цвета — тема работает автоматически.

### Anti-Patterns to Avoid

- **Не использовать `window.onload = ...`** — уже навешан listener (строка 1391). Расширять существующий `mm.add()` блок, не создавать второй `load` handler.
- **Не регистрировать `ScrollTrigger` второй раз** — уже `gsap.registerPlugin(ScrollTrigger)` в строке 1393.
- **Не использовать `scrollTrigger: true` без `trigger`** — в GSAP это shorthand для `scrollTrigger: '.selector'`, не для boolean true.
- **Не анимировать пинимый элемент** — анимируем дочерние элементы (`.arch-step`), пинится контейнер (`#architecture-flow`). [CITED: SKILL.md gsap-scrolltrigger "Don't animate the pinned element itself"]
- **Не использовать `position: fixed`** для залипания — это сломает ScrollTrigger.pin. Доверять механике плагина.
- **Не оставлять `markers: true`** в production — только в dev.

## Don't Hand-Roll

| Problem | Don't Build | Use Instead | Why |
|---------|-------------|-------------|-----|
| Scroll-linked progress | `document.addEventListener('scroll', ...)` + math | GSAP ScrollTrigger scrub | ScrollTrigger делает rAF throttling, invalidation, resize handling, reduced-motion integration |
| Entrance reveals | Custom IntersectionObserver | `ScrollTrigger.batch()` уже есть в коде (строка 1443) | Уже работает, reuse |
| Icons | Inline `<svg>` | Phosphor `<i class="ph-duotone ph-...">` | Единообразие с остальным лендингом |
| Theme switching | Другой механизм | Существующий `data-theme` + localStorage | Работает, не трогать |
| Reduced-motion | Свой flag | `gsap.matchMedia('(prefers-reduced-motion: reduce)')` | Идиоматический способ |

**Key insight:** лендинг — «закрытая» codebase без npm. Любые хаки должны ложиться в уже существующие паттерны. Писать новый vanilla-JS scroll-логики вместо использования GSAP — против уклада файла.

## Common Pitfalls

### Pitfall 1: Pin ломает layout на коротких экранах
**What goes wrong:** Если высота секции с `pin: true` больше, чем пространство ниже до следующей секции, `pinSpacing` создаёт огромную «дырку».
**Why it happens:** `pinSpacing: true` (default) вставляет спейсер на высоту `end - start`.
**How to avoid:** Выставить `end: '+=500%'` — это значит 5 viewport height "прокрутки"; контент секции должен помещаться в 1vh (pinned). Не делать секцию высотой 500vh по CSS — это даст 2500vh общего скролла.
**Warning signs:** Видны две полосы белого пространства после секции.

### Pitfall 2: Pin на мобильном = плохой UX
**What goes wrong:** На мобильном «залипание» на несколько скроллов подряд воспринимается как «страница зависла».
**Why it happens:** Мобильные пользователи привыкли к непрерывному потоку.
**How to avoid:** Использовать `matchMedia` breakpoint 1024px (совпадает с существующим `@media (min-width: 1024px)`). Ниже — простой `.batch()` reveal.

### Pitfall 3: GSAP CDN + defer + `window.load`
**What goes wrong:** Скрипты загружены `defer`, код inline-скрипта привязан к `window.addEventListener('load', ...)`. Если внести код в другой event (DOMContentLoaded), GSAP может быть ещё не загружен.
**Why it happens:** `defer` гарантирует порядок, но `load` ждёт всех ресурсов.
**How to avoid:** Расширять **тот же** блок `window.addEventListener('load', ...)` (строка 1391), не вводить новый handler. Текущая проверка `if (typeof gsap === 'undefined') return;` — корректная защита.

### Pitfall 4: Light theme потерян после добавления секции
**What goes wrong:** Новая секция использует фоновый градиент с хард-кодед цветами — ломается light mode.
**Why it happens:** Разработчик копирует hex из dark темы.
**How to avoid:** Использовать только токены `--bg-*`, `--text-*`, `--accent-*`, `--border-*`. Проверять в DevTools с `data-theme="light"` на корне.
**Warning signs:** Секция выглядит тёмной на белой странице.

### Pitfall 5: Стейл статус в CLAUDE.md
**What goes wrong:** CLAUDE.md строка 14 `v6.0: В РАБОТЕ` — v6.0 был завершён 2026-04-06, но статус не обновлялся.
**Why it happens:** Обновление файла не включено в commit-флоу предыдущих фаз.
**How to avoid:** DOCS-v9-01 требует аудит **всех** строк «В РАБОТЕ» в CLAUDE.md. Plan должен включать explicit task: заменить весь статус-блок строк 12-16 одним актуальным блоком v1.0-v9.0.

### Pitfall 6: Git/CI/RabbitMQ — технически некорректные иконки
**What goes wrong:** Illustration показывает поток «git push → CI → ...», но для пользователя-студента это абстракция — если нарисовать слишком технично (логотипы GitHub / RabbitMQ / Docker), лендинг теряет доступность.
**How to avoid:** Использовать нейтральные Phosphor иконки (`ph-git-branch`, `ph-gear`, `ph-cube`, `ph-paper-plane-tilt`, `ph-wifi-high`, `ph-device-mobile`), подписи на русском: «Коммит кода» / «CI/CD сборка» / «Backend микросервисы» / «Очередь сообщений» / «Push / WebSocket» / «Ваше устройство».

### Pitfall 7: 1488-строчный файл + Edit tool
**What goes wrong:** Файл index.html уже 1488 строк. Редактирование может не попасть с точной match-строкой.
**Why it happens:** Inline CSS и большие блоки, повторяющиеся селекторы.
**How to avoid:** Делать Edit через уникальные якоря (комментарии `<!-- ============ ARCHITECTURE ============ -->`, полные имена классов). Для CSS — вставлять блок с явным маркер-комментарием перед анимационным блоком (строка 995).

## Code Examples

### Example 1: HTML секции (тело)

```html
<!-- Source: pattern derived from existing #how-it-works section (index.html:1214-1238) -->
<section id="architecture-flow" class="section arch-flow">
  <div class="arch-flow__stage">
    <p class="section__eyebrow">Как работает система</p>
    <h2 class="section__title">От коммита до вашего&nbsp;устройства</h2>
    <p class="section__subtitle">Каждая отметка — это путь через контейнеры, очереди и&nbsp;сокеты. Вот как он выглядит.</p>

    <ol class="arch-flow__track">
      <li class="arch-step" data-step="1">
        <div class="arch-step__icon"><i class="ph-duotone ph-git-branch"></i></div>
        <h3>Коммит в&nbsp;репозиторий</h3>
        <p>Разработчик пушит изменения на&nbsp;GitHub.</p>
      </li>
      <span class="arch-arrow" data-to="2" aria-hidden="true"></span>
      <li class="arch-step" data-step="2">
        <div class="arch-step__icon"><i class="ph-duotone ph-gear"></i></div>
        <h3>CI/CD пайплайн</h3>
        <p>GitHub Actions собирает образы и&nbsp;раскатывает их.</p>
      </li>
      <!-- ...steps 3..6 with arrows between... -->
    </ol>
  </div>
</section>
```

### Example 2: CSS (token-based, theme-safe)

```css
/* Source: existing token palette at index.html:52-100 */
.arch-flow {
  background: var(--bg-secondary);
  border-top: 1px solid var(--border-subtle);
  border-bottom: 1px solid var(--border-subtle);
}

.arch-flow__stage {
  min-height: 100vh;            /* pinned content fills viewport */
  display: flex;
  flex-direction: column;
  justify-content: center;
  padding: var(--space-12) 0;
}

.arch-flow__track {
  display: grid;
  grid-template-columns: 1fr;
  gap: var(--space-6);
  margin-top: var(--space-8);
}

@media (min-width: 1024px) {
  .arch-flow__track {
    grid-template-columns: repeat(11, 1fr); /* 6 steps + 5 arrows */
    gap: 0;
    align-items: center;
  }
}

.arch-step {
  background: var(--bg-elevated);
  border: 1px solid var(--border-subtle);
  border-radius: 16px;
  padding: var(--space-4);
  text-align: center;
}

.arch-arrow {
  height: 2px;
  background: var(--gradient-brand);
  transform-origin: left center;
  /* GSAP animates scaleX: 0 → 1 */
}
```

### Example 3: GSAP timeline (extending existing mm block)

```javascript
// Source: extension of existing gsap.matchMedia block at index.html:1395
// Insert inside the existing `mm.add('(prefers-reduced-motion: no-preference)', function () { ... })`

var isDesktop = window.matchMedia('(min-width: 1024px)').matches;

if (isDesktop) {
  gsap.set('.arch-step', { opacity: 0, y: 40 });
  gsap.set('.arch-arrow', { scaleX: 0 });

  var archTL = gsap.timeline({
    scrollTrigger: {
      trigger: '#architecture-flow',
      start: 'top top',
      end: '+=500%',
      scrub: 0.6,
      pin: true,
      pinSpacing: true,
      anticipatePin: 1
    }
  });

  for (var i = 1; i <= 6; i++) {
    archTL.to('.arch-step[data-step="' + i + '"]', { opacity: 1, y: 0, duration: 1 });
    if (i < 6) {
      archTL.to('.arch-arrow[data-to="' + (i + 1) + '"]', { scaleX: 1, duration: 0.6 }, '-=0.3');
    }
  }
} else {
  // Mobile: simple batch reveal (reuse existing pattern from index.html:1443)
  ScrollTrigger.batch('.arch-step', {
    start: 'top 85%', once: true,
    onEnter: function (els) {
      gsap.to(els, { opacity: 1, y: 0, stagger: 0.1, duration: 0.6, ease: 'power3.out' });
    }
  });
  gsap.set('.arch-arrow', { scaleX: 1 });
  gsap.set('.arch-step', { opacity: 0, y: 24 });
}
```

### Example 4: url-layout.md — v9.0 production routing table

```markdown
## Production Path Routing (v9.0)

Decided: Phase 49-50 (v9.0). Nginx reverse proxy at `https://ruttrack.site`.

| Path | Served By | Notes |
|------|-----------|-------|
| `/` | 301 redirect → `/login` | INFRA-v9-01 |
| `/login` | web-panel SPA (Angular) | AUTH-v9-01 |
| `/admin/*` | web-panel SPA (Angular, lazy feature) | ADMIN role |
| `/teacher/*` | web-panel SPA (Angular, lazy feature) | TEACHER role |
| `/student/*` | web-panel SPA (Angular, lazy feature) | STUDENT role (incl. headman) |
| `/headman/*` | web-panel SPA (Angular, lazy feature) | STUDENT + is_headman=true |
| `/app/` | PWA (React + Vite) | INFRA-v9-03 |
| `/presentation/` | Landing (static HTML) | INFRA-v9-02, LAND-v9-01 |
| `/api/*` | API Gateway (port 8080) | Proxied via nginx |
```

## Runtime State Inventory

> Rename/refactor inventory required because DOCS-v9-01 updates CLAUDE.md status block — касается strings в уже-commited doc, not runtime state.

| Category | Items Found | Action Required |
|----------|-------------|------------------|
| Stored data | None — phase touches only static HTML and Markdown. No databases, collections, or user_ids changed. | none |
| Live service config | None — nginx routing уже finalized в phase 49. Datadog/Tailscale/Cloudflare не затронуты. | none |
| OS-registered state | None — no daemons, no task registrations, no pm2. | none |
| Secrets/env vars | None — no secrets touched. | none |
| Build artifacts | `frontends/landing/dist/index.html` редактируется — Docker image `rct-landing-nginx` нужно будет пересобрать через CI, но это уже делает существующий pipeline на push в main. **Нужно подтвердить**, что CI ребилдит landing image при изменении только `dist/index.html` (не только source). | Verify CI workflow in plan (GitHub Actions должен тригернуться по path filter или без filter) |

**Критический вопрос для планировщика:** `.github/workflows/*` — path filters? Если landing image пересобирается только при изменении `frontends/landing/**`, то правки в `dist/index.html` подхватятся. Проверить в 57-01-PLAN.

## Environment Availability

| Dependency | Required By | Available | Version | Fallback |
|------------|------------|-----------|---------|----------|
| GSAP CDN (jsdelivr) | LAND-v9-02 | ✓ (verified в уже работающем landing) | 3.12.5 | — |
| Phosphor Icons CDN (unpkg) | Все изменения секций | ✓ | 2.1.1 | — |
| Fontshare/Google Fonts | Все секции | ✓ | — | — |
| Docker CI build | Новый landing image | ✓ (v8.0 pipeline) | — | — |
| Browser DevTools с Lighthouse | LAND-v9-04 responsive audit | ✓ | — | — |

**Нет блокирующих зависимостей.** Работа не требует нового софта.

## State of the Art

| Old Approach | Current Approach | When Changed | Impact |
|--------------|------------------|--------------|--------|
| v6.0/v5.0 статусы в CLAUDE.md помечены «В РАБОТЕ» | Актуализация под v9.0 | Phase 57 (DOCS-v9-01) | `v1.0-v8.0: ЗАВЕРШЕНЫ`, `v9.0: В РАБОТЕ (завершается фазой 57)` |
| Landing accessible at `/landing/` | `/presentation/` | Phase 49 (INFRA-v9-02) | `og:url` уже `/presentation/` (index.html:16) |
| 3 роли в карточках (без HEADMAN) | 4 роли including HEADMAN | Уже present в dist/index.html:1260 | LAND-v9-05 в основном done; уточнить только v9.0-specific буллеты |
| Landing → Telegram t.me links | → /login | Phase 49 (LAND-v9-03) | Уже done, `<a href="/login">` (index.html:1029,1306) |

**Deprecated/outdated:**
- **Линк `/landing/`** в документации или nginx — удалён в phase 49, проверить что нигде не остался.
- **`v6.0: В РАБОТЕ`** в CLAUDE.md:14 — стейл, v6.0 shipped 2026-04-06.

## Assumptions Log

| # | Claim | Section | Risk if Wrong |
|---|-------|---------|---------------|
| A1 | GSAP 3.12.5 free license покрывает ScrollTrigger без Business тира | Standard Stack | [VERIFIED via gsap.com] ScrollTrigger is free; только SplitText/MorphSVG требуют Club GreenSock. Актуализировано. |
| A2 | Landing image в CI пересобирается при правках `dist/index.html` (не только source) | Runtime State Inventory | [ASSUMED] Если CI имеет path filter `frontends/landing/src/**`, правки в `dist/` могут не триггернуть build. Плановщик ОБЯЗАН проверить `.github/workflows/*.yml` до старта работы. |
| A3 | Роль HEADMAN в card «Староста» (строки 1260-1270) достаточна как "описывает capabilities HEADMAN" | Phase Requirements (LAND-v9-05) | [ASSUMED] Текущие 4 буллета описывают v1.0-v5.0 capabilities (журнал группы, excuse, помощники). v9.0 добавил `/headman/subjects`, `/headman/journal`, `/headman/stats`, `/headman/excuses`, `/headman/late-checkin`, PWA Группа tab. **Рекомендуется заменить/расширить** буллеты на v9.0 state. Если user хочет минимальные изменения — оставить as-is. Требует явного подтверждения в /gsd-discuss-phase или по умолчанию в plan. |
| A4 | `docs/phase-57-report.md` должен быть создан (директива CLAUDE.md) | Project Constraints | [CITED: CLAUDE.md раздел «Инструкция для Claude Code» пункт 6] — обязательно. |
| A5 | JS-STUDENT-WEB-01..10 и JS-HEADMAN-WEB-01..08 = 10 + 8 = 18 новых историй в job-stories.md | DOCS-v9-03 | [CITED: ROADMAP Notes + REQUIREMENTS.md mapping STU-WEB-01..10 = 10 items, HEAD-WEB-01..08 = 8 items] — ровно 18. |
| A6 | `.planning/PROJECT.md` нужно не переписать, а добавить v9.0 в Shipped секции после phase 57 полностью | DOCS-v9-04 | [CITED: REQUIREMENTS.md "v9.0 moves to Shipped Milestones section after completion"] — движение, не добавление. |

## Open Questions

1. **Высота pinned секции и продолжительность scroll-durability**
   - What we know: рекомендуемая `end: '+=500%'` даёт 5vh скролла на 6-шаговую анимацию.
   - What's unclear: 500% или 600%? Решается в UX-тестировании.
   - Recommendation: начать с 500%, финально полировать на ux-audit stage плана.

2. **Должна ли секция поддерживать keyboard navigation / links вокруг каждого шага?**
   - What we know: Lendings обычно не делают шаги кликабельными.
   - What's unclear: accessibility. Лучше шаги оставить как `<li>` с визуальным выделением через `:focus-visible` не требуется, но рекомендуется `prefers-reduced-motion` fallback без pin.
   - Recommendation: статическая версия (reduced-motion) должна быть полностью видна и читаема без скролла.

3. **Нужна ли кнопка «Подробнее про архитектуру» → ссылка на docs/architecture.md?**
   - What we know: не указано в requirements.
   - What's unclear: вне scope; решение за планом.
   - Recommendation: не добавлять, не тратить scope.

4. **CI path filter для rct-landing-nginx image**
   - What we know: CI pipeline shipped в v8.0.
   - What's unclear: есть ли path filter.
   - Recommendation: Plan task — grep `.github/workflows/*.yml` на `landing`, подтвердить, что правки `dist/` триггернут билд. Если path filter слишком узкий, расширить.

## Validation Architecture

**Skipped** — config.json не содержит `workflow.nyquist_validation` ни true, ни false; по умолчанию включён. Однако phase 57 — static HTML + Markdown. Автоматические тесты применимы ограниченно.

### Test Framework
| Property | Value |
|----------|-------|
| Framework | Нет unit-test framework для лендинга (static HTML, нет build). Для web-panel vitest уже есть (162 теста в v9.0 phase 50). |
| Config file | — (landing); `frontends/web-panel/vitest.config.ts` (regression reference) |
| Quick run command | Browser manual + Lighthouse |
| Full suite command | `cd frontends/web-panel && npm test` (регрессия, не зависит от phase 57 но проверяется) |

### Phase Requirements → Test Map

| Req ID | Behavior | Test Type | Automated Command | File Exists? |
|--------|----------|-----------|-------------------|-------------|
| LAND-v9-02 | GSAP секция рендерится и анимируется на скролл | manual-only | Browser + DevTools Performance | N/A |
| LAND-v9-04 | Responsive 360-1440px + dark mode | manual-only | Browser DevTools responsive mode + `responsiveness-check` skill agent | N/A |
| LAND-v9-05 | 4 роли видны в `#roles` | smoke | `curl -s https://ruttrack.site/presentation/ \| grep -c 'data-role='` → expect `4` | N/A (CI probe) |
| DOCS-v9-01 | CLAUDE.md содержит v9.0 статус | smoke | `grep -c "v9.0" CLAUDE.md` → expect `>= 3` | N/A |
| DOCS-v9-02 | url-layout.md имеет Production Routing таблицу | smoke | `grep -q "## Production Path Routing" docs/url-layout.md` | N/A |
| DOCS-v9-03 | job-stories.md содержит JS-STUDENT-WEB-* и JS-HEADMAN-WEB-* | smoke | `grep -cE "JS-(STUDENT\|HEADMAN)-WEB-" docs/job-stories.md` → expect `>= 18` | N/A |
| DOCS-v9-04 | PROJECT.md имеет v9.0 в Shipped | smoke | `grep -q "v9.0.*shipped" .planning/PROJECT.md` | N/A |

### Sampling Rate
- **Per task commit:** grep smoke tests (see above).
- **Per wave merge:** Lighthouse + manual dark/light toggle + 360/768/1440px responsive check.
- **Phase gate:** все smoke greps pass + Chrome DevTools flow manual check + `cd frontends/web-panel && npm test` = 162/162 green (регрессия).

### Wave 0 Gaps
- None — phase 57 doesn't need new test infrastructure. Landing не имеет CI-тестов и не должен получать их в этой фазе (out-of-scope).

## Security Domain

> `security_enforcement` не задан в config.json; по умолчанию включён. Однако phase 57 — контент-only, без auth/authz/crypto/DB.

### Applicable ASVS Categories

| ASVS Category | Applies | Standard Control |
|---------------|---------|-----------------|
| V2 Authentication | no | Нет auth flow в лендинге |
| V3 Session Management | no | Static page |
| V4 Access Control | no | — |
| V5 Input Validation | no | Нет пользовательского input |
| V6 Cryptography | no | — |
| V14 Configuration | yes | CSP headers в nginx.conf (уже настроены в phase 49); CDN SRI hashes — opportunity |

### Known Threat Patterns for static landing

| Pattern | STRIDE | Standard Mitigation |
|---------|--------|---------------------|
| CDN tampering (GSAP/Phosphor/fonts подменены) | Tampering | [ASSUMED gap] CDN `<script>` тэги **не имеют SRI** (integrity= attribute). Это не регрессия от phase 57, но если плановщик хочет улучшить — можно добавить `integrity="sha384-..."` к GSAP CDN. Не обязательно для LAND-v9-02, но good practice. |
| XSS через inline script | Tampering | Лендинг — контролируемый статический HTML, нет user input. Низкий риск. |
| Clickjacking | Tampering | `X-Frame-Options` должен быть установлен в nginx.conf (проверить — уже настроено в phase 44 SSL config). |

**Security conclusion:** phase 57 не вносит новых security-рисков. SRI для CDN — nice-to-have, но не в scope LAND-v9-02.

## Sources

### Primary (HIGH confidence)
- `frontends/landing/dist/index.html` — полный существующий код (1488 строк, изучено секциями)
- `.planning/REQUIREMENTS.md` — requirement IDs LAND-v9-02, 04, 05, DOCS-v9-01..04
- `.planning/ROADMAP.md` — phase 57 Notes + Success Criteria
- `.planning/STATE.md` — accumulated context из phase 49-56
- `.planning/PROJECT.md` — текущий статус проекта
- `CLAUDE.md` — project rules + устаревший статус-блок (строки 12-16)
- `docs/url-layout.md` — текущее состояние
- `docs/job-stories.md` — формат существующих историй (JS-ADMIN-XX, JS-TEACHER-XX)
- `docs/skills-inventory.md` — каталог GSAP skills
- `docs/design-decisions.md` — лендинг → GSAP + ScrollTrigger (секция 2)
- `.claude/skills/gsap-scrolltrigger/SKILL.md` — паттерны pin + scrub + matchMedia

### Secondary (MEDIUM confidence)
- GSAP official docs: https://gsap.com/docs/v3/Plugins/ScrollTrigger/ — pin behavior, matchMedia (cited via SKILL.md, not fetched in this session)

### Tertiary (LOW confidence)
- None — phase 57 работает с уже существующим стеком и файлами.

## Metadata

**Confidence breakdown:**
- Standard stack: HIGH — стек зафиксирован в уже-работающем файле, версии verified в CDN URLs
- Architecture: HIGH — паттерн извлечён из official GSAP SKILL.md + аналогия с существующим `mm.add` блоком
- Pitfalls: HIGH — перечислены реальные проблемы, которые видны в существующем коде (pinSpacing, reduced-motion, CLAUDE.md stale lines)
- Runtime state: HIGH — docs-only phase, нечего мигрировать
- Environment: HIGH — CDN доступен, verified
- Security: HIGH — phase не вводит attack surface
- Open questions: MEDIUM — есть assumptions про CI path filter и про v9.0 буллеты HEADMAN; требуют подтверждения в /gsd-discuss-phase или явного решения планировщика

**Research date:** 2026-04-13
**Valid until:** 2026-05-13 (30 дней — GSAP 3.12.5 stable, Phosphor 2.1.1 stable, phase scope узкий)
