# RutCampusTrack — Реестр Claude Code Skills

Установленные skills для использования в разработке. Группировка по категориям.

---

## Backend / Code Quality

| Skill | Расположение | Фазы | Описание |
|-------|-------------|------|----------|
| systematic-debugging | `.claude/skills/` | Все | Систематический дебаг: перед предложением фиксов |
| test-driven-development | `.claude/skills/` | Все | TDD: тесты перед реализацией |
| code-review | `.agents/skills/` | Все | Code review по практикам Sentry: безопасность, производительность, тесты |
| find-bugs | `.agents/skills/` | Все | Поиск багов и уязвимостей в изменениях на текущей ветке |
| review-local-changes | `.claude/skills/` | Все | Комплексный review локальных изменений с агентами |
| review-pr | `.claude/skills/` | Все | Комплексный review pull request с агентами |
| setup-code-formating | `.claude/skills/` | 0 | Настройка правил форматирования в CLAUDE.md |

## Security

| Skill | Расположение | Фазы | Описание |
|-------|-------------|------|----------|
| cso | `.claude/skills/` | Все | Chief Security Officer: аудит инфраструктуры, OWASP Top 10, STRIDE |
| vibesec | `.claude/skills/` | Все | Аудит безопасности веб-приложений |

## Angular (Веб-панель, Фаза 8)

| Skill | Расположение | Описание |
|-------|-------------|----------|
| angular | `.claude/skills/` | Angular v20+ эксперт: Signals, Standalone, Zoneless, SSR |
| angular-component | `.claude/skills/` | Standalone компоненты с signal inputs/outputs, OnPush |
| angular-di | `.claude/skills/` | Dependency injection: inject(), tokens, providers |
| angular-directives | `.claude/skills/` | Custom directives для DOM-манипуляций |
| angular-forms | `.claude/skills/` | Signal-based формы (Angular v21+ Signal Forms API) |
| angular-http | `.claude/skills/` | HTTP: resource(), httpResource(), HttpClient, interceptors |
| angular-routing | `.claude/skills/` | Routing: lazy loading, guards, resolvers |
| angular-signals | `.claude/skills/` | Signals: signal(), computed(), linkedSignal(), effect() |
| angular-ssr | `.claude/skills/` | SSR и hydration через @angular/ssr |
| angular-testing | `.claude/skills/` | Тесты: Vitest/Jasmine + TestBed, modern patterns |
| angular-tooling | `.claude/skills/` | Angular CLI: генерация, сборка, конфигурация |

## React (Mini App + PWA, Фазы 7–8)

| Skill | Расположение | Описание |
|-------|-------------|----------|
| vercel-react-best-practices | `.agents/skills/` | React/Next.js оптимизация от Vercel Engineering (69 правил) |
| react-patterns | `.agents/skills/` | React 19 паттерны и композиция (50+ правил) |
| motion | `.claude/skills/` | Motion (Framer Motion): drag-and-drop, gestures, scroll animations |

## Animation (Лендинг + фронтенды)

| Skill | Расположение | Описание |
|-------|-------------|----------|
| gsap-core | `.agents/skills/` | GSAP core API: to(), from(), easing, stagger |
| gsap-react | `.agents/skills/` | GSAP + React: useGSAP, refs, context, cleanup |
| gsap-scrolltrigger | `.agents/skills/` | ScrollTrigger: scroll-linked, pinning, scrub, параллакс |
| gsap-timeline | `.agents/skills/` | Timelines: sequencing, position parameter, nesting |
| gsap-plugins | `.agents/skills/` | Plugins: Flip, Draggable, SplitText, ScrollSmoother |
| gsap-performance | `.agents/skills/` | Performance: transforms, 60fps, will-change |
| gsap-frameworks | `.agents/skills/` | GSAP + Vue/Svelte: lifecycle, cleanup |
| gsap-utils | `.agents/skills/` | gsap.utils: clamp, mapRange, snap, toArray |
| aceternity-ui | `.claude/skills/` | 100+ animated React компонентов для Next.js + Tailwind |
| animated-component-libraries | `.claude/skills/` | Magic UI + React Bits: ready-made animated компоненты |
| lottie-animations | `.claude/skills/` | Lottie: After Effects JSON анимации для веба |

## Design / UI

| Skill | Расположение | Описание |
|-------|-------------|----------|
| frontend-design | `.agents/skills/` | Production-grade интерфейсы с высоким качеством дизайна |
| taste-skill | `.claude/skills/` | Senior UI/UX: metric-based правила, CSS hardware acceleration |
| minimalist-skill | `.claude/skills/` | Editorial-style: monochrome, typographic contrast, bento grids |
| baseline-ui | `.claude/skills/` | Валидация анимаций, типографики, accessibility в Tailwind |
| design-system | `.agents/skills/` | Извлечение design system из существующих сайтов |
| platform-design-web | `.claude/skills/` | Web design guidelines + accessibility (WCAG) |
| fixing-accessibility | `.claude/skills/` | Аудит и фикс ARIA, keyboard navigation, color contrast |
| fixing-metadata | `.claude/skills/` | Аудит SEO: meta tags, Open Graph, JSON-LD |
| fixing-motion-performance | `.claude/skills/` | Аудит производительности анимаций |
| ux-audit | `.agents/skills/` | Dogfood: тестирование UX как реальный пользователь |
| responsiveness-check | `.agents/skills/` | Тест адаптивности по viewport breakpoints |
| landing-page | `.agents/skills/` | Генерация лендинга как single HTML file |

## PWA / Mobile (Фаза 7)

| Skill | Расположение | Описание |
|-------|-------------|----------|
| progressive-web-app | `.claude/skills/` | PWA: Service Worker, manifest, offline, install prompt |
| push-notification-setup | `.claude/skills/` | Push: Firebase Cloud Messaging, Web Push, iOS/Android |

## Workflow

| Skill | Расположение | Описание |
|-------|-------------|----------|
| ship | `.claude/skills/` | Ship: tests → review → bump VERSION → commit → push → PR |
| git-workflow | `.agents/skills/` | Git: prepare PR, clean branches, resolve conflicts |
