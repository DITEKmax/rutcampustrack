# RutCampusTrack — Брендбук и дизайн-система

> Этот документ — инструкция для Claude Code. Выполняй задачи последовательно, секция за секцией.

---

## 0. Контекст проекта

**Продукт:** RutCampusTrack — университетская система трекинга посещаемости студентов РУТ (МИИТ).

**Фронтенды и стек:**

| Приложение | Путь | Стек | Прод-URL |
|---|---|---|---|
| Landing page | `frontends/landing` | React/Vite | `ruttrack.site/landing/` |
| Admin dashboard | `frontends/web-panel` | Angular 19 | `ruttrack.site/admin/` |
| Telegram Mini App | `frontends/mini-app` | React/Vite | `ruttrack.site/mini-app/` |
| PWA (студенты) | `frontends/pwa` | React/Vite | `ruttrack.site/` |

**Аудитория:** студенты 18–25 (основная), преподаватели и администраторы 30–60 (вторичная).

**Тон бренда:** технологичный, энергичный, но не легкомысленный. Университетская серьёзность + современный tech-стартап.

---

## 1. Концепция: «Transit Grid»

Вдохновение — транспортная тематика РУТ МИИТ. Метафора: студент — это точка в движении по маршруту обучения, а посещаемость — это трекинг этого маршрута.

**Визуальные мотивы:**
- Сетки и линии маршрутов (как схема метро / железной дороги)
- Точки-станции как контрольные элементы (чекины, предметы, даты)
- Движение, динамика, пульс — через анимации
- Топографические / изометрические текстуры на фонах

**Настроение:** Тёмная тема — ночная карта города с подсвеченными маршрутами (неоновые акценты на тёмной базе). Светлая тема — чистый дневной чертёж маршрутной сети (чёткие линии на светлом фоне, акценты чуть глубже и насыщеннее). Обе темы должны ощущаться как единый бренд.

---

## 2. Цветовая палитра

Поддерживаются **две полноценные темы: тёмная и светлая**. Пользователь выбирает сам через toggle в header или настройках. Выбор сохраняется в localStorage. По умолчанию — определяется через `prefers-color-scheme`. Тёмная тема — основная при дизайне (проектируй сначала её), светлая — равноценная альтернатива, не «обесцвеченная версия тёмной». Обе темы должны выглядеть продуманно и завершённо.

Реализация: CSS-переменные, переключение через атрибут `[data-theme="dark"]` / `[data-theme="light"]` на `<html>`. Вставь токены в корневой `styles/tokens.css` (или эквивалент для Angular).

```css
:root {
  /* --- Base (тёмная тема — основная) --- */
  --bg-primary: #0A0E17;         /* глубокий тёмно-синий, почти чёрный */
  --bg-secondary: #111827;       /* карточки, панели */
  --bg-elevated: #1A2236;        /* модалки, выпадашки */
  --bg-surface: #0F1520;         /* input-поля, таблицы */

  /* --- Текст --- */
  --text-primary: #F0F2F5;
  --text-secondary: #8B95A8;
  --text-muted: #4A5568;

  /* --- Акценты --- */
  --accent-primary: #00E5A0;     /* неоново-зелёный — главный акцент, CTA */
  --accent-secondary: #3B82F6;   /* синий — ссылки, вторичные действия */
  --accent-warning: #F59E0B;     /* жёлтый — пропуски, предупреждения */
  --accent-danger: #EF4444;      /* красный — критические пропуски */
  --accent-info: #8B5CF6;        /* фиолетовый — информационные блоки */

  /* --- Градиенты (для фонов hero-блоков, карточек) --- */
  --gradient-brand: linear-gradient(135deg, #00E5A0 0%, #3B82F6 100%);
  --gradient-dark: linear-gradient(180deg, #0A0E17 0%, #111827 100%);
  --gradient-glow: radial-gradient(circle at 50% 50%, rgba(0,229,160,0.15) 0%, transparent 70%);

  /* --- Borders & Effects --- */
  --border-subtle: rgba(255,255,255,0.06);
  --border-accent: rgba(0,229,160,0.3);
  --glow-primary: 0 0 20px rgba(0,229,160,0.3);
  --glow-blue: 0 0 20px rgba(59,130,246,0.3);

  /* --- Radii --- */
  --radius-sm: 6px;
  --radius-md: 12px;
  --radius-lg: 20px;
  --radius-full: 9999px;

  /* --- Spacing scale (8px base) --- */
  --space-1: 4px;
  --space-2: 8px;
  --space-3: 12px;
  --space-4: 16px;
  --space-5: 24px;
  --space-6: 32px;
  --space-7: 48px;
  --space-8: 64px;
  --space-9: 96px;

}

/* ===== СВЕТЛАЯ ТЕМА (равноценная, не «урезанная») ===== */
[data-theme="light"] {
  --bg-primary: #F8FAFB;
  --bg-secondary: #FFFFFF;
  --bg-elevated: #FFFFFF;
  --bg-surface: #EEF1F5;
  --text-primary: #0A0E17;
  --text-secondary: #4A5568;
  --text-muted: #8B95A8;
  --accent-primary: #00B87A;       /* темнее для контраста на белом */
  --accent-secondary: #2563EB;     /* насыщеннее для читаемости */
  --accent-warning: #D97706;
  --accent-danger: #DC2626;
  --accent-info: #7C3AED;
  --gradient-brand: linear-gradient(135deg, #00B87A 0%, #2563EB 100%);
  --gradient-dark: linear-gradient(180deg, #F8FAFB 0%, #EEF1F5 100%);
  --gradient-glow: radial-gradient(circle at 50% 50%, rgba(0,184,122,0.08) 0%, transparent 70%);
  --border-subtle: rgba(0,0,0,0.08);
  --border-accent: rgba(0,184,122,0.25);
  --glow-primary: 0 0 16px rgba(0,184,122,0.15);
  --glow-blue: 0 0 16px rgba(37,99,235,0.15);
}

/* Для Telegram Mini App: синхронизация с темой Telegram */
/* В mini-app определяй тему через window.Telegram.WebApp.colorScheme */
```

---

## 3. Типографика

**Шрифты** (подключать через Google Fonts):

| Роль | Шрифт | Начертания | Использование |
|---|---|---|---|
| Display / H1-H2 | **Clash Display** (или Satoshi, если Clash недоступен — проверь fontsource) | 600, 700 | Заголовки hero, крупные числа |
| Headings / H3-H5 | **General Sans** | 500, 600 | Заголовки секций, карточек |
| Body | **DM Sans** | 400, 500 | Основной текст, параграфы |
| Mono / Data | **JetBrains Mono** | 400 | Коды занятий, ID, таблицы данных |

**Шкала размеров:**

```css
--text-xs: 0.75rem;    /* 12px — caption */
--text-sm: 0.875rem;   /* 14px — мелкий текст */
--text-base: 1rem;     /* 16px — body */
--text-lg: 1.125rem;   /* 18px — крупный body */
--text-xl: 1.25rem;    /* 20px — подзаголовки */
--text-2xl: 1.5rem;    /* 24px */
--text-3xl: 2rem;      /* 32px */
--text-4xl: 2.5rem;    /* 40px */
--text-5xl: 3.5rem;    /* 56px — hero */
--text-6xl: 4.5rem;    /* 72px — landing hero */
```

**Line-height:** 1.2 для display, 1.5 для body, 1.6 для длинных текстов.

---

## 4. Компоненты дизайн-системы

Создай общую библиотеку переиспользуемых компонентов. Для React-фронтендов — shared пакет. Для Angular — отдельный набор в `web-panel/src/shared/ui`.

### 4.1 Кнопки

```
[Primary]   — bg: --accent-primary, text: --bg-primary (чёрный на зелёном)
              hover: glow + scale(1.02), active: scale(0.98)
[Secondary] — bg: transparent, border: --border-accent, text: --accent-primary
              hover: bg rgba(0,229,160,0.1)
[Ghost]     — bg: transparent, text: --text-secondary
              hover: text --text-primary
[Danger]    — bg: --accent-danger
```

Все кнопки: `border-radius: var(--radius-full)`, padding `12px 24px`, transition `all 0.2s ease`.

### 4.2 Карточки

- Фон: `var(--bg-secondary)`
- Border: `1px solid var(--border-subtle)`
- Radius: `var(--radius-lg)`
- Hover: поднятие тени `0 8px 32px rgba(0,0,0,0.3)` + border переходит в `var(--border-accent)`
- Внутри: паддинг `var(--space-5)` или `var(--space-6)`

### 4.3 Статистические виджеты (dashboard)

Числовые карточки со значением посещаемости:
- Крупное число шрифтом Clash Display 700, размер `--text-4xl`
- Подпись `--text-sm` цвет `--text-secondary`
- Слева или сверху — иконка в круге с gradient-brand фоном
- Sparkline-график внизу (маленький line chart за 7 дней)
- При hover — мягкий glow акцентным цветом

### 4.4 Таблицы (dashboard)

- Заголовки: uppercase, `--text-xs`, letter-spacing 0.05em, цвет `--text-muted`
- Строки: border-bottom `var(--border-subtle)`, hover — bg `var(--bg-elevated)`
- Ячейки статуса: pill-бейджи (зелёный = был, красный = пропуск, жёлтый = опоздание)
- Sticky header при скролле

### 4.5 Input-поля

- Фон: `var(--bg-surface)`
- Border: `1px solid var(--border-subtle)`
- Focus: border `var(--accent-primary)`, box-shadow `var(--glow-primary)`
- Radius: `var(--radius-md)`
- Label сверху, мелким шрифтом

### 4.6 Навигация

**Sidebar (web-panel):**
- Ширина 260px, коллапсируется до 72px (иконки)
- Фон: `var(--bg-primary)` с лёгким noise-texture
- Активный пункт: bg `rgba(0,229,160,0.1)`, left-border 3px `var(--accent-primary)`
- Иконки: Lucide или Phosphor icons

**Bottom Tab Bar (PWA, mini-app):**
- 4-5 табов, иконки + текст
- Активный: цвет `var(--accent-primary)`, иконка с fill
- Backdrop-filter blur на фоне
- Safe area inset для мобильных

### 4.7 Графики и визуализация данных

- Библиотека: Recharts (React) / ng2-charts (Angular)
- Цвета линий/баров: `--accent-primary`, `--accent-secondary`, `--accent-info`
- Фон графика: прозрачный
- Grid-линии: `var(--border-subtle)`
- Tooltip: `var(--bg-elevated)` с `var(--glow-primary)` тенью

---

## 5. Анимации и motion-дизайн

Используй GSAP (уже доступен). Подключи через CDN или npm.

### 5.1 Принципы

- **Entrance:** элементы появляются снизу с opacity 0 → 1, y: 20 → 0, stagger 0.08s
- **Page transitions:** fade + subtle slide (200-300ms)
- **Hover:** scale, glow, color shift — быстро (150-200ms)
- **Числа:** countUp анимация для статистики (GSAP или countUp.js)
- **Фоны:** subtle floating particles или animated gradient mesh (только landing)

### 5.2 Landing page (тяжёлые анимации)

- Hero: текст появляется посимвольно или построчно с GSAP SplitText-эффектом
- ScrollTrigger: секции появляются при скролле с parallax-эффектами
- Анимированная «карта маршрутов» как фоновая SVG-иллюстрация с движущимися точками
- Интерактивные hover-эффекты на feature-карточках
- Smooth scroll между секциями

### 5.3 Dashboard (лёгкие анимации)

- Staggered fade-in при загрузке страницы
- Числа: countUp при появлении
- Графики: draw-in анимация линий
- Sidebar: smooth expand/collapse
- Таблицы: row enter/exit анимации

### 5.4 PWA / Mini-App (средние анимации)

- Переходы между экранами: slide left/right
- Pull-to-refresh с кастомной анимацией
- Skeleton-загрузка вместо спиннеров
- Micro-interactions: успешный чекин — pulse + confetti-burst
- Swipe-жесты с пружинной физикой

---

## 6. Иконография и иллюстрации

- **Иконки:** Phosphor Icons (стиль: regular, weight 1.5). Единый набор для всех фронтов.
- **Иллюстрации:** минималистичные line-art в стиле маршрутной карты. Используй SVG.
- **Эмодзи:** не использовать в UI. Только в уведомлениях Telegram при необходимости.
- **Favicon:** стилизованная буква «R» с точкой-маркером маршрута, цвет `--accent-primary` на тёмном фоне.

### 4.8 Theme Toggle

Компонент переключения темы. Присутствует во всех 4 фронтендах:
- **web-panel:** в header справа, рядом с профилем
- **PWA / mini-app:** в настройках профиля + опционально в header
- **Landing:** в header

Реализация:
- Toggle-переключатель (sun/moon иконки) с плавной CSS-transition на `background-color`, `color`, `border-color` (300ms ease)
- При переключении — устанавливать `data-theme` на `<html>` и сохранять в `localStorage('theme')`
- При загрузке: читать `localStorage`, если нет — `prefers-color-scheme`
- В mini-app: по умолчанию синхронизировать с `window.Telegram.WebApp.colorScheme`, но дать возможность переопределить
- Все компоненты должны использовать **только CSS-переменные** для цветов — никаких хардкод-значений

---

## 7. Сетка и layout

- **Desktop (web-panel):** sidebar 260px + content area. Content: max-width 1280px, padding 32px.
- **Landing:** full-width секции, content max-width 1200px, centered.
- **Mobile (PWA, mini-app):** single column, padding 16px. Bottom bar 64px + safe area.
- **Grid:** 12 колонок, gap 16px (desktop) / 12px (mobile).
- **Breakpoints:** 480 / 768 / 1024 / 1280 / 1536.

---

## 8. Порядок выполнения редизайна

Выполняй строго в этом порядке. Каждый шаг — отдельный промпт.

### Шаг 1: Токены и shared-стили
Создай файлы дизайн-токенов (CSS-переменные для обеих тем, шрифты) и подключи их во все 4 фронтенда. Реализуй компонент Theme Toggle (см. 4.8) и утилиту определения/сохранения темы. Убедись что переключение dark↔light работает плавно и все цвета меняются корректно.

### Шаг 2: Landing page
Полный редизайн `frontends/landing`. Hero с анимированным заголовком (GSAP), секции features/how-it-works/CTA. ScrollTrigger-анимации. Фоновая SVG-иллюстрация маршрутов. Mobile responsive.

### Шаг 3: Web-panel — Layout и навигация
Редизайн layout оболочки `frontends/web-panel`: sidebar, header, routing-outlet. Не трогай содержимое страниц пока.

### Шаг 4: Web-panel — Dashboard страница
Редизайн главной страницы дашборда: статистические карточки, графики посещаемости, таблица последних занятий. CountUp, staggered animations.

### Шаг 5: Web-panel — Остальные страницы
Редизайн оставшихся страниц (список студентов, расписание, настройки и т.д.) в соответствии с дизайн-системой.

### Шаг 6: PWA — Layout и навигация
Редизайн `frontends/pwa`: bottom tab bar, header, page transitions. Skeleton-загрузка.

### Шаг 7: PWA — Экраны
Редизайн всех экранов PWA: профиль, расписание, история посещений, чекин. Micro-interactions для чекина.

### Шаг 8: Mini-App — Адаптация
Редизайн `frontends/mini-app` с учётом Telegram WebApp API (тема, safe areas, haptic feedback). Компактный UI. Light/dark theme из Telegram.

### Шаг 9: Полировка
Финальный проход: проверка консистентности между фронтендами, accessibility (контраст, focus states, aria-labels), performance (lazy load, code split).

---

## 9. Доступные Claude Code skills

При выполнении шагов используй эти skills:
- `frontend-design` — для всех UI-задач
- `gsap-core`, `gsap-scrolltrigger`, `gsap-react`, `gsap-timeline`, `gsap-plugins` — для анимаций
- `animated-component-libraries` — для готовых анимированных компонентов
- `landing-page` — для шага 2
- `platform-design-web` — для web-panel
- `baseline-ui` — для базовых компонентов
- `design-system` — для шага 1
- `motion` — для transition-анимаций
- `lottie-animations` — если нужны сложные иллюстративные анимации

---

## 10. Важные ограничения

- **Две темы обязательны.** Каждый компонент и каждая страница должны корректно выглядеть в обеих темах. Используй только CSS-переменные для цветов, теней и бордеров — никаких хардкод-значений. Тестируй оба варианта.
- **Не ломай функциональность.** Редизайн — только визуальный слой. Логика, API-вызовы, роутинг должны остаться рабочими.
- **Angular !== React.** В web-panel используй Angular-подход (компоненты, сервисы, Angular Animations или GSAP через директивы).
- **Telegram Mini App:** учитывай `window.Telegram.WebApp` API — цвета темы, кнопка Back, MainButton, haptic feedback.
- **Accessibility:** минимум AA контраст (4.5:1 для текста). Focus-visible стили. Semantic HTML.
- **Performance:** lazy-load изображения, code-split роуты, не грузи все шрифты сразу (font-display: swap).


----------------------
● All test suites green:
- web-panel: 131/131
- pwa: 46/46
- mini-app: 35/35

● Step 1 complete

What was delivered (brandbook §8 Step 1):

Design tokens — frontends/shared/styles/{tokens.css,fonts.css} as canonical source, mirrored into src/styles/ of each of the 3 source-based apps. Covers both themes with all     
Transit Grid colors, typography scale, spacing, radii, shadows, motion, layout, z-index per brandbook §2-3, §7. Includes .theme-transitioning class for smooth switches and       
prefers-reduced-motion override.

Fonts — Clash Display + General Sans (Fontshare), DM Sans + JetBrains Mono (Google Fonts), loaded with display=swap per §10.

Tailwind v4 bridges (pwa, mini-app) — @theme inline routes every shadcn semantic token (--color-background, --color-primary, etc.) to Transit Grid vars, so existing shadcn       
components automatically inherit the brand palette without rewrites.

Angular Material bridge (web-panel) — --mat-sys-* tokens routed to Transit Grid vars, re-theming every Material component. Legacy .dark class preserved alongside new [data-theme]
attribute for backwards compat with status/role chips.

Theme toggle utility:
- React: frontends/pwa/src/shared/theme/{ThemeProvider,ThemeToggle}.tsx (mirrored into mini-app). Context-based, persists to localStorage['ruttrack.theme'], falls back to
  prefers-color-scheme, follows OS changes until user overrides, applies brief transition class on switch.
- Angular: frontends/web-panel/src/app/core/theme/{theme.service.ts,theme-toggle.component.ts}. Signal-based service upgraded from .dark-class-only to dual data-theme + .dark,
  with legacy web-panel.theme localStorage key migration.

Telegram integration — TelegramThemeProvider rewritten: no longer clobbers brand colors with Telegram's raw bg_color/text_color. Instead, reads miniApp.isDark once and seeds
ThemeProvider.initialTheme. User toggle still wins and persists.

Toggle placement (verifiable for Step 1):
- pwa: in ProfilePage with a "Тема" card.
- mini-app: floating top-right compact button (interim, to be relocated during Step 8).
- web-panel: existing sidebar footer toggle already calls themeService.toggle() — automatically works with new dual-attribute approach.

Verification:
- Type-check: pwa + mini-app ✓
- Build: web-panel ng build ✓ (only pre-existing unrelated warning)
- Tests: web-panel 131/131, pwa 46/46, mini-app 35/35 ✓

Files untouched (by design — frontends/landing is a pre-built static HTML artifact with no source; the brandbook schedules its full rewrite in Step 2).

Ready for Step 2 (landing page redesign) whenever you want to proceed.