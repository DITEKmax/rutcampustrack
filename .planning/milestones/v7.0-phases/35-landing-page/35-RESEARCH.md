# Phase 35: Landing Page — Research

**Researched:** 2026-04-07
**Domain:** Статическая HTML/CSS страница, nginx:1.27-alpine, GSAP + ScrollTrigger, Tailwind CDN
**Confidence:** HIGH

---

## Summary

Phase 35 — создание маркетинговой landing page для RutCampusTrack. Страница полностью статическая (HTML + CSS + vanilla JS без фреймворков), обслуживается уже настроенным nginx-контейнером `landing-nginx` (порт 8081), scaffolded в Phase 33.

Инфраструктурная часть завершена: `docker-compose.yml` содержит `landing-nginx` (порт `8081:80`), `frontends/landing/nginx.conf` настроен на static-only с `=404` (без SPA fallback), `frontends/landing/dist/index.html` — placeholder, который Phase 35 заменяет реальной страницей.

Задача Phase 35 — написать `frontends/landing/dist/index.html` (или несколько файлов в `dist/`): hero-секция, features, обзор ролей, responsive layout 360px–1440px. Никаких JS-фреймворков (React, Angular), никаких API-вызовов.

Проектные дизайн-решения из `docs/design-decisions.md` прямо предписывают для лендинга: **GSAP + ScrollTrigger** для scroll-driven анимаций и **Aceternity UI / Magic UI** (copy-paste, React + Tailwind) как source of inspiration. Однако, так как страница должна быть полностью статической без React-зависимостей, используем GSAP (CDN) + Tailwind (CDN) с hand-crafted HTML.

**Primary recommendation:** Один файл `index.html` с Tailwind CSS (CDN), GSAP + ScrollTrigger (CDN), vanilla JS. Секции: nav, hero, features, roles overview, footer. Сохранить в `frontends/landing/dist/index.html`.

**Важное замечание по порту:** В phase description success criteria написано `http://localhost:8880`, но фактически `landing-nginx` в `docker-compose.yml` (verified) привязан к порту **8081**. URL для проверки: `http://localhost:8081`.

---

<phase_requirements>
## Phase Requirements

| ID | Description | Research Support |
|----|-------------|------------------|
| LAND-01 | Static HTML/CSS landing page with hero, features, role overview | Single `index.html` in `frontends/landing/dist/` replaces placeholder; sections defined below |
| LAND-02 | Mobile-responsive layout (360px–1440px) | Tailwind CDN с breakpoints sm/md/lg; no framework needed |
| LAND-03 | Served by dedicated nginx container | `landing-nginx` уже настроен в docker-compose (Phase 33); просто заменить `dist/index.html` |
</phase_requirements>

---

## Project Constraints (from CLAUDE.md)

- **Без JS-фреймворков** в landing: не React, не Angular — vanilla JS допустим только для анимаций и навигации
- **Дизайн-решения (docs/design-decisions.md):** для лендинга — GSAP + ScrollTrigger. Aceternity UI / Magic UI как источник вдохновения (copy-paste паттерны, но адаптированные под vanilla HTML/CSS)
- **Брендинг:** название «RutCampusTrack», короткое «RutTrack»; визуальный стиль единый с проектом (Phosphor Icons)
- **Иконки (design-decisions.md):** Phosphor Icons, для лендинга / dashboard context — `duotone` weight, 32px размер для hero элементов
- **nginx:** статический сервер, `=404` для отсутствующих файлов — без SPA fallback. Все файлы должны лежать в `frontends/landing/dist/`
- **Нет API-вызовов** — страница полностью офлайн-ready
- **Роли в системе:** ADMIN, TEACHER, STUDENT (+ is_headman), помощник старосты — все должны быть отражены в role overview секции

---

## Standard Stack

### Core

| Компонент | Версия | Назначение | Почему стандарт |
|-----------|--------|-----------|----------------|
| Tailwind CSS (CDN) | 3.x (play CDN) | Utility-first styling, responsive | Быстро, нет build step, проект уже использует для вдохновения из Aceternity UI |
| GSAP (CDN) | 3.12+ | Scroll-driven анимации, timeline | Прямо предписан в design-decisions.md |
| ScrollTrigger (CDN) | 3.12+ | Привязка анимаций к прокрутке | Входит в GSAP bundle, предписан в design-decisions.md |
| Phosphor Icons (CDN) | 2.x | Иконки для features и role cards | Единый стандарт иконок в проекте |

### Подключение через CDN

```html
<!-- Tailwind CDN (play) -->
<script src="https://cdn.tailwindcss.com"></script>

<!-- Phosphor Icons -->
<script src="https://unpkg.com/@phosphor-icons/web@2.1.1/src/index.js"></script>

<!-- GSAP + ScrollTrigger -->
<script src="https://cdn.jsdelivr.net/npm/gsap@3.12.5/dist/gsap.min.js"></script>
<script src="https://cdn.jsdelivr.net/npm/gsap@3.12.5/dist/ScrollTrigger.min.js"></script>
```

**Версии (ASSUMED — необходима проверка на npm registry перед написанием кода):**
- GSAP: `npm view gsap version` — обновить перед использованием
- Phosphor Icons Web: `npm view @phosphor-icons/web version`

### Alternatives Considered

| Вместо | Можно | Tradeoff |
|--------|-------|----------|
| Tailwind CDN | Чистый CSS | Tailwind быстрее писать, нет build step — оптимально для статической страницы |
| GSAP ScrollTrigger | CSS scroll-driven animations (native) | GSAP предписан в дизайн-решениях; CSS scroll-driven — experimental, поддержка хуже |
| Phosphor Icons CDN | SVG inline | CDN проще, единый стиль с проектом |

---

## Architecture Patterns

### Рекомендуемая структура файлов

```
frontends/landing/
├── dist/
│   ├── index.html      ← ЕДИНСТВЕННЫЙ файл (все inline или через CDN)
│   ├── assets/         ← опционально: изображения/скриншоты если нужны
│   └── og-image.png    ← опционально: Open Graph preview
└── nginx.conf          ← УЖЕ СОЗДАН в Phase 33, не трогать
```

Предпочтительный подход: один `index.html` со всем встроенным. Nginx уже настроен на `root /usr/share/nginx/html`, что соответствует mount `./frontends/landing/dist:/usr/share/nginx/html:ro`.

### Pattern 1: Структура страницы

**Секции** (в порядке сверху вниз):

1. **`<nav>`** — Sticky navigation, лого RutCampusTrack, anchor links, CTA кнопка
2. **`<section id="hero">`** — Hero: headline, subheadline, описание системы, CTA
3. **`<section id="features">`** — Feature cards с иконками (геоотметка, расписание, статистика, уведомления, ДЗ-трекер)
4. **`<section id="roles">`** — Role overview: 4 карточки (Студент, Студент-Cтароста, Преподаватель, Администратор)
5. **`<footer>`** — Название, copyright, ссылки

### Pattern 2: Tailwind CDN конфигурация

```html
<script>
  tailwind.config = {
    darkMode: 'class',
    theme: {
      extend: {
        colors: {
          brand: {
            DEFAULT: '#2563EB',   // синий — основной
            dark: '#1D4ED8',
            light: '#3B82F6',
          },
          accent: '#10B981',      // зелёный — success/присутствие
        }
      }
    }
  }
</script>
```

### Pattern 3: GSAP ScrollTrigger — вход секций

Стандартный паттерн из skill gsap-scrolltrigger — batch-анимация карточек при входе в viewport:

```javascript
// Source: gsap-scrolltrigger SKILL.md
gsap.registerPlugin(ScrollTrigger);

// Fade-in + slide-up для feature карточек
ScrollTrigger.batch(".feature-card", {
  onEnter: (elements) => {
    gsap.to(elements, {
      autoAlpha: 1,
      y: 0,
      stagger: 0.12,
      duration: 0.6,
      ease: "power2.out"
    });
  },
  start: "top 85%",
  once: true
});

// Hero — immediate animation on load (без ScrollTrigger)
gsap.from(".hero-headline", { autoAlpha: 0, y: 30, duration: 0.8, ease: "power3.out" });
gsap.from(".hero-sub", { autoAlpha: 0, y: 20, duration: 0.8, delay: 0.15, ease: "power2.out" });
gsap.from(".hero-cta", { autoAlpha: 0, y: 15, duration: 0.6, delay: 0.3, ease: "power1.out" });
```

### Pattern 4: prefers-reduced-motion через gsap.matchMedia()

```javascript
// Source: gsap-core SKILL.md — accessibility requirement
const mm = gsap.matchMedia();
mm.add(
  { reduceMotion: "(prefers-reduced-motion: reduce)" },
  (context) => {
    const { reduceMotion } = context.conditions;
    if (!reduceMotion) {
      // запускать анимации только когда motion не отключён
      initAnimations();
    }
  }
);
```

### Pattern 5: Responsive layout Tailwind

```html
<!-- Feature grid: 1 col mobile, 2 col tablet, 3 col desktop -->
<div class="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-3 gap-6">
  <!-- feature cards -->
</div>

<!-- Role overview: 1 col mobile, 2 col tablet, 4 col desktop -->
<div class="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-4 gap-6">
  <!-- role cards -->
</div>
```

### Anti-Patterns

- **Не использовать React/Angular** — страница должна быть plain HTML, иначе нарушается LAND-01 ("no JS framework")
- **Не делать API-вызовы** — страница полностью статическая (LAND-01)
- **Не вкладывать ScrollTrigger в child tween** внутри timeline — только на top-level tweens (правило из gsap-scrolltrigger SKILL.md)
- **Не оставлять `markers: true`** в production ScrollTrigger
- **Не использовать `scrub` и `toggleActions` одновременно** на одном ScrollTrigger

---

## Don't Hand-Roll

| Проблема | Не строить | Использовать | Почему |
|---------|------------|--------------|--------|
| Scroll-driven анимации | Кастомный IntersectionObserver + CSS | GSAP ScrollTrigger (CDN) | Предписан в дизайн-решениях, батч-API проще IO |
| Responsive breakpoints | Кастомный CSS с media queries | Tailwind CDN utility классы | Скорость написания, нет build step |
| Иконки | SVG спрайты / кастомные | Phosphor Icons Web CDN | Единый стиль с проектом |
| Hamburger меню | Кастомная анимация JS | Простой CSS toggle (`peer` Tailwind) или 5 строк JS | Нет зависимостей |

---

## Common Pitfalls

### Pitfall 1: Файлы вне `dist/` не будут видны nginx

**Что идёт не так:** Разработчик создаёт `frontends/landing/index.html` вместо `frontends/landing/dist/index.html`.
**Почему:** Volume mount в docker-compose: `./frontends/landing/dist:/usr/share/nginx/html:ro` — nginx видит только `dist/`.
**Как избежать:** Все файлы лендинга — только в `frontends/landing/dist/`.
**Признак проблемы:** nginx возвращает 403 или placeholder вместо реальной страницы.

### Pitfall 2: `autoAlpha: 0` на элементах по умолчанию блокирует видимость при отключённом JS

**Что идёт не так:** Если JS не загрузился или пользователь с отключённым JS, элементы остаются `visibility: hidden`.
**Как избежать:** Начальное состояние `autoAlpha: 0` устанавливать только через `gsap.set()` после DOMContentLoaded, или задать через CSS `opacity: 0; visibility: hidden` только для JS-enhanced elements.

### Pitfall 3: ScrollTrigger.batch() не вызывает `onEnter` для элементов выше fold при первой загрузке

**Что идёт не так:** Элементы hero видны без прокрутки, batch `onEnter` не срабатывает → они остаются невидимыми.
**Как избежать:** Для элементов выше fold (hero секция) использовать обычный `gsap.from()` без ScrollTrigger, только для ниже-fold элементов — `ScrollTrigger.batch()` с `once: true`.

### Pitfall 4: Tailwind CDN play vs production

**Что идёт не так:** `https://cdn.tailwindcss.com` — dev-версия с JIT, большой bundle (~300KB gzip). Для статической страницы это нормально, но важно не путать с production-build командой.
**Как избежать:** Для Phase 35 (простая статическая страница) CDN достаточен. Помнить, что Tailwind CDN не поддерживает `@apply` в `<style>` тегах — использовать только utility классы.

### Pitfall 5: Порт 8081 vs 8880

**Что идёт не так:** В phase description success criteria написано `http://localhost:8880`, но реально `landing-nginx` слушает на **8081** (verified в docker-compose.yml и Phase 33 summary).
**Как избежать:** Всегда проверять на `http://localhost:8081`. Это не ошибка в коде, только ошибка в описании фазы.

### Pitfall 6: nginx `=404` и опечатки в именах файлов

**Что идёт не так:** `frontends/landing/nginx.conf` использует `try_files $uri $uri/ =404` — нет fallback к `index.html`. Любая опечатка в имени файла (например `index.htm` вместо `index.html`) → 404.
**Как избежать:** Один файл — `index.html` (точно такое имя). `nginx.conf` уже настроен на `index index.html`.

---

## Code Examples

### Базовая структура HTML

```html
<!DOCTYPE html>
<html lang="ru" class="scroll-smooth">
<head>
  <meta charset="UTF-8">
  <meta name="viewport" content="width=device-width, initial-scale=1.0">
  <title>RutCampusTrack — Система учёта посещаемости РУТ МИИТ</title>
  <meta name="description" content="Автоматизированная система геоотметки и учёта посещаемости для студентов и преподавателей">

  <!-- Tailwind CDN -->
  <script src="https://cdn.tailwindcss.com"></script>
  <script>
    tailwind.config = { darkMode: 'class', theme: { extend: { colors: { brand: '#2563EB' } } } }
  </script>

  <!-- Phosphor Icons -->
  <script src="https://unpkg.com/@phosphor-icons/web@2.1.1/src/index.js"></script>
</head>
<body class="bg-white text-gray-900">

  <!-- nav -->
  <header class="sticky top-0 z-50 bg-white/90 backdrop-blur border-b border-gray-100">
    <nav class="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8 h-16 flex items-center justify-between">
      <span class="font-bold text-xl text-brand">RutCampusTrack</span>
      <a href="#features" class="hidden sm:block text-sm text-gray-600 hover:text-brand transition-colors">Возможности</a>
    </nav>
  </header>

  <!-- hero -->
  <section id="hero" class="min-h-screen flex items-center">
    <div class="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8 py-24">
      <h1 class="hero-headline text-4xl sm:text-5xl lg:text-6xl font-bold tracking-tight">
        Учёт посещаемости<br class="hidden sm:block"> для РУТ МИИТ
      </h1>
      <p class="hero-sub mt-6 text-lg sm:text-xl text-gray-600 max-w-2xl">
        Геоотметка за 10 секунд, расписание, статистика и уведомления — в одном месте.
        Для студентов, старост и преподавателей.
      </p>
    </div>
  </section>

  <!-- GSAP + ScrollTrigger -->
  <script src="https://cdn.jsdelivr.net/npm/gsap@3.12.5/dist/gsap.min.js"></script>
  <script src="https://cdn.jsdelivr.net/npm/gsap@3.12.5/dist/ScrollTrigger.min.js"></script>
  <script>
    gsap.registerPlugin(ScrollTrigger);
    // Hero анимации — без ScrollTrigger (элементы сразу видны)
    gsap.from(".hero-headline", { autoAlpha: 0, y: 30, duration: 0.8, ease: "power3.out" });
    gsap.from(".hero-sub",      { autoAlpha: 0, y: 20, duration: 0.8, delay: 0.15, ease: "power2.out" });
    // Scroll-triggered анимации для нижних секций
    ScrollTrigger.batch(".feature-card", {
      onEnter: (els) => gsap.to(els, { autoAlpha: 1, y: 0, stagger: 0.1, duration: 0.6, ease: "power2.out" }),
      start: "top 85%",
      once: true
    });
  </script>
</body>
</html>
```

### Feature card с Phosphor Icons (duotone, 32px)

```html
<div class="feature-card opacity-0 translate-y-6 rounded-2xl border border-gray-100 p-6 shadow-sm">
  <i class="ph-duotone ph-map-pin text-brand" style="font-size: 32px;"></i>
  <h3 class="mt-4 text-lg font-semibold">Геоотметка</h3>
  <p class="mt-2 text-gray-600 text-sm">Отметьтесь на паре одним нажатием — GPS подтверждает ваше присутствие на кампусе.</p>
</div>
```

**Примечание:** `opacity-0 translate-y-6` — начальное состояние для GSAP анимации. GSAP переведёт в `autoAlpha: 1, y: 0`.

### Role card пример

```html
<!-- Студент -->
<div class="role-card rounded-2xl bg-blue-50 p-6">
  <i class="ph-duotone ph-student text-brand" style="font-size: 32px;"></i>
  <h3 class="mt-4 font-semibold">Студент</h3>
  <ul class="mt-3 text-sm text-gray-600 space-y-1">
    <li>Геоотметка на парах</li>
    <li>Расписание на день и неделю</li>
    <li>Статистика посещаемости</li>
    <li>Трекер домашних заданий</li>
  </ul>
</div>
```

---

## Environment Availability

Это чисто frontend-фаза, никаких внешних зависимостей кроме уже запущенного docker-compose.

| Зависимость | Требуется для | Доступна | Версия | Fallback |
|-------------|---------------|----------|--------|---------|
| `landing-nginx` docker container | LAND-03 | ✓ (Phase 33, port 8081) | nginx:1.27-alpine | — |
| `frontends/landing/dist/` | LAND-03 | ✓ (placeholder exists) | — | — |
| Tailwind CDN | LAND-01, LAND-02 | ✓ (internet access) | 3.x play CDN | Inline CSS |
| GSAP CDN | LAND-01 анимации | ✓ (internet access) | 3.12.5 | CSS transitions |
| Phosphor Icons CDN | Иконки | ✓ (internet access) | 2.1.1 | SVG inline |

**Отсутствующие зависимости:** Нет. Все CDN-ресурсы доступны. `landing-nginx` уже настроен.

**Fallback при отсутствии CDN:** При разработке без интернета — скачать `.min.js` файлы и положить в `frontends/landing/dist/assets/`. Для production это рекомендуется в любом случае (но не является требованием для данной фазы).

---

## Validation Architecture

`workflow.nyquist_validation` отсутствует в `.planning/config.json` — treated as enabled.

### Test Framework

| Property | Value |
|----------|-------|
| Framework | Manual browser / curl smoke test |
| Config file | нет (static HTML) |
| Quick run command | `curl -s -o /dev/null -w "%{http_code}" http://localhost:8081/` |
| Full suite command | Browser viewport test 360px / 768px / 1440px + scrolling |

### Phase Requirements → Test Map

| Req ID | Поведение | Тип | Команда | Файл |
|--------|-----------|-----|---------|------|
| LAND-01 | `http://localhost:8081` отдаёт HTML с hero, features, roles | smoke | `curl -s http://localhost:8081/ \| grep -c "hero\|features\|roles"` | ✓ `dist/index.html` |
| LAND-01 | Страница без JS фреймворков и API-вызовов | code review | `grep -c "react\|angular\|fetch\|axios" dist/index.html` → 0 | ✓ |
| LAND-02 | Responsive на 360px — viewport meta + grid | code review | `grep "viewport" dist/index.html` → наличие meta | ✓ |
| LAND-02 | Визуальный layout на 360px/768px/1440px | manual | DevTools responsive mode | manual |
| LAND-03 | nginx container up, 200 ответ | smoke | `docker compose ps rct-landing-nginx` + `curl` | ✓ |

### Wave 0 Gaps

Нет — тесты не требуют test files. Только `dist/index.html` (создаётся в этой фазе) и уже запущенный `landing-nginx`.

---

## Security Domain

### Applicable ASVS Categories

| ASVS Category | Applies | Standard Control |
|---------------|---------|-----------------|
| V2 Authentication | no | — |
| V3 Session Management | no | — |
| V4 Access Control | no | — |
| V5 Input Validation | no | Нет форм, нет пользовательского ввода |
| V6 Cryptography | no | — |

Лендинг — публичная статическая страница без форм, без авторизации, без API. Минимальный threat surface.

### Known Threat Patterns для статического nginx

| Pattern | STRIDE | Standard Mitigation |
|---------|--------|---------------------|
| Directory traversal | Tampering | nginx `root` + `try_files $uri $uri/ =404` — уже настроено (Phase 33) |
| Inline JS XSS через CDN | Spoofing | CDN GSAP/Tailwind/Phosphor — доверенные источники; нет user input |
| Clickjacking | Spoofing | Добавить `X-Frame-Options: DENY` в nginx.conf если требуется (опционально) |

---

## Assumptions Log

| # | Утверждение | Раздел | Риск при ошибке |
|---|-------------|--------|-----------------|
| A1 | GSAP `3.12.5` — текущая версия | Standard Stack | Устаревший URL CDN; проверить `npm view gsap version` |
| A2 | Phosphor Icons Web `2.1.1` — текущая версия | Standard Stack | Устаревший CDN; проверить `npm view @phosphor-icons/web version` |
| A3 | `landing-nginx` запускается командой `docker compose up` без изменений | Environment | Phase 33 завершена и verified — LOW риск |
| A4 | Цветовая палитра (синий brand, зелёный accent) — не задана явно | Code Examples | Стиль может расходиться с будущей design system; LOW риск для лендинга |

---

## Open Questions

1. **Порт 8081 vs 8880 в phase description**
   - Что известно: `docker-compose.yml` (verified) — `landing-nginx` порт `8081:80`. Phase 33 summary подтверждает 8081.
   - Что неясно: success criteria в phase description написаны `http://localhost:8880` — возможно опечатка
   - Рекомендация: Использовать **8081** (verified из docker-compose). Если планировщик или пользователь подтвердит 8880 — нужно изменить `docker-compose.yml` (но это меняет Phase 33 инфраструктуру).

2. **Скриншоты / мокапы приложения в hero-секции**
   - Что известно: phase description упоминает "screenshots" как возможный элемент
   - Что неясно: есть ли реальные скриншоты или нужны заглушки
   - Рекомендация: Использовать placeholder-блоки (CSS gradient rectangles) вместо реальных скриншотов. Добавить TODO-комментарий.

3. **Темная тема**
   - Что известно: landing-page SKILL.md рекомендует dark mode toggle
   - Что неясно: нужен ли dark mode для данной страницы (нет явного требования)
   - Рекомендация: Реализовать prefers-color-scheme CSS + Tailwind `dark:` классы без интерактивного toggle (упрощает разработку, соответствует accessibility требованиям).

---

## Sources

### Primary (HIGH confidence)

- `docker-compose.yml` (VERIFIED) — `landing-nginx` service, порт 8081, volume mounts
- `frontends/landing/nginx.conf` (VERIFIED) — static config с `=404`, `root /usr/share/nginx/html`
- `frontends/landing/dist/index.html` (VERIFIED) — placeholder, фаза 35 его заменяет
- `.planning/phases/33-infrastructure/33-01-SUMMARY.md` (VERIFIED) — порт 8081 подтверждён, url-layout.md создан
- `docs/design-decisions.md` (VERIFIED) — GSAP + ScrollTrigger для лендинга; Phosphor Icons стандарт
- `.claude/skills/landing-page/SKILL.md` (VERIFIED) — структура страницы, Tailwind CDN паттерн
- `.claude/skills/gsap-core/SKILL.md` (VERIFIED) — GSAP API, matchMedia, autoAlpha, best practices
- `.claude/skills/gsap-scrolltrigger/SKILL.md` (VERIFIED) — ScrollTrigger.batch(), once, anti-patterns
- `.claude/skills/frontend-design/SKILL.md` (VERIFIED) — design quality guidelines

### Secondary (MEDIUM confidence)

- Phosphor Icons web CDN — стандарт проекта [CITED: docs/design-decisions.md + skills inventory]
- Tailwind CDN play — подходит для static page без build step [CITED: landing-page SKILL.md]

### Tertiary (LOW confidence)

- Точные версии GSAP 3.12.5 и Phosphor Icons 2.1.1 [ASSUMED — проверить перед написанием кода]

---

## Metadata

**Confidence breakdown:**
- Инфраструктура (LAND-03): HIGH — Phase 33 verified, контейнер работает
- Standard stack (HTML/CSS/JS): HIGH — skills подтверждают подход
- GSAP паттерны: HIGH — verified из SKILL.md
- Версии CDN: LOW — assumed, требуют проверки
- Цветовая палитра: LOW — assumed (нет locked design tokens)

**Research date:** 2026-04-07
**Valid until:** Stable — nginx/static HTML паттерны не меняются. GSAP CDN URL — проверить версию перед началом.
