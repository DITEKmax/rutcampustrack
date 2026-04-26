# Phase 57 Report: Landing Presentation Mode + Documentation

**Завершена:** 2026-04-13
**Milestone:** v9.0 Frontend Unification
**Requirements закрыты:** LAND-v9-02, LAND-v9-04, LAND-v9-05, DOCS-v9-01, DOCS-v9-02, DOCS-v9-03, DOCS-v9-04

## Цель

Добавить на лендинг `/presentation/` GSAP scroll-driven анимацию «Как работает система», отражающую путь данных git push → CI → backend → RabbitMQ → WebSocket/Push → устройство. Описать в ролях всех 4 участников (включая HEADMAN) с актуальными для v9.0 capabilities. Синхронизировать project documentation (CLAUDE.md, url-layout.md, job-stories.md, PROJECT.md) с v9.0 state.

## Результаты

### Landing (LAND-v9-02, LAND-v9-04, LAND-v9-05)

- Новая секция `#architecture-flow` в `frontends/landing/dist/index.html` — 6 шагов + 5 стрелок, Phosphor duotone иконки (`ph-git-branch`, `ph-gear`, `ph-cube`, `ph-paper-plane-tilt`, `ph-wifi-high`, `ph-device-mobile`), token-based CSS (`var(--bg-*)`, `var(--text-*)`, `var(--border-*)`, `var(--accent-*)`, `var(--gradient-*)`)
- GSAP timeline с `pin: true` + `scrub: 0.6` + `end: '+=500%'` на desktop ≥1024px (через `gsap.matchMedia`); `ScrollTrigger.batch` c stagger на mobile <1024px; статический fallback `gsap.set('.arch-step, .arch-arrow', { opacity: 1, y: 0, scaleX: 1 })` для `prefers-reduced-motion: reduce`
- Роль «Староста» обновлена 5 буллетами v9.0: журнал группы + массовая отметка (HEAD-WEB-05), управление предметами/преподавателями (HEAD-WEB-04), рассмотрение excuse-тикетов и late-check-in (HEAD-WEB-06/07), per-subject red-zone threshold (HEAD-WEB-08), web-кабинет `/headman/*` + вкладка «Группа» в PWA (PWA-HEAD-01)
- Все 4 роли (ADMIN, TEACHER, STUDENT, HEADMAN) визуально видны в `#roles`
- Dark/light темы работают через design tokens без хард-кодед hex-цветов
- Responsive поведение на 360px / 768px / 1440px подтверждено Human UAT

### Documentation (DOCS-v9-01..04)

- `CLAUDE.md`: статус-блок синхронизирован (v1.0-v8.0 ЗАВЕРШЕНА, v9.0 В РАБОТЕ фазы 49-57); добавлен раздел `### URL Layout (v9.0)` с 9-строчной таблицей production nginx-маршрутов
- `docs/product/url-layout.md`: добавлен раздел `## Production Path Routing (v9.0)` с 10-строчной таблицей маршрутов, маппингом роль→dashboard, списком deprecated путей (`/landing/`, root-PWA)
- `docs/product/job-stories.md`: добавлены 18 новых историй — JS-STUDENT-WEB-01..10 (10 историй web-кабинета студента, фазы 51-53) и JS-HEADMAN-WEB-01..08 (8 историй web-кабинета старосты, фазы 54-55), каждая с traceability-ссылкой `(STU-WEB-NN)` / `(HEAD-WEB-NN)` на родительский requirement
- `.planning/PROJECT.md`: добавлен статус `v9.0 ship-ready after Phase 57`, `### Recently Validated (v9.0)` расширен записями для фаз 50-57 (STU-WEB, HEAD-WEB, PWA-HEAD, LAND-v9, DOCS-v9)

## Ключевые решения

1. **GSAP без апгрейда стека** — использован уже подключённый GSAP 3.12.5 + ScrollTrigger через CDN. Нет перехода на build pipeline (контракт лендинга: static HTML, `frontends/landing/dist/index.html` — единственный артефакт).
2. **Pin + scrub только для desktop** — на mobile (<1024px) применён `ScrollTrigger.batch()` reveal, чтобы избежать perceived-DoS «страница зависла» при длинном pin на touch-устройствах.
3. **Reduced-motion fallback обязателен** — секция `#architecture-flow` получает статическое состояние при `prefers-reduced-motion: reduce` через существующий `mm.add` блок, без дублирующего `window.addEventListener('load', ...)`.
4. **v9.0 ship-ready, не shipped** — `.planning/PROJECT.md` помечает v9.0 как готовый к `/gsd-close-milestone`, но не перемещает его в `## Shipped Milestones` — это работа следующей команды после phase 57 verify-work.
5. **Headman card: 5 bullets вместо 4** — чтобы вместить все v9.0 HEAD-WEB-* capabilities без потери Journal/Subjects/Excuse/Threshold/PWA.

## Изменённые файлы

- `frontends/landing/dist/index.html` — HTML секции `#architecture-flow`, CSS `.arch-*` блок, GSAP таймлайн внутри существующего `mm.add` блока; обновлена карточка `data-role="headman"`
- `CLAUDE.md` — статус-блок + раздел URL Layout
- `docs/product/url-layout.md` — раздел Production Path Routing (append-only)
- `docs/product/job-stories.md` — 18 новых web-cabinet историй (append-only)
- `.planning/PROJECT.md` — ship-ready mark + Recently Validated расширен
- `docs/phase-reports/phase-57-report.md` — этот файл

## Верификация

- Все 5 автоматических структурных проверок из plan 57-03 Task 1 — зелёные (no hex colors in arch-* CSS, section balance 6/6, `@media (min-width: 1024px)` inside arch CSS, `gsap.set('.arch-step, ...')` inside reduced-motion block, 4 role cards по одному)
- Все grep-based smoke-тесты из plan 57-01 и plan 57-02 зелёные
- Human UAT на 360/768/1440 px + dark/light + reduced-motion + scroll animation: **approved**
- Scroll-driven анимация на desktop работает плавно, pin/unpin без layout-скачков
- Существующий landing-код (hero, features, how-it-works, CTA) не тронут — diff локализован в пределах одной новой секции, одного CSS-блока и одного нового sub-блока GSAP

## Nuances / gotchas

- Если какая-то Phosphor иконка из используемого набора в будущем исчезнет — заменить на ближайший duotone-аналог. В Phosphor 2.1.1 все 6 подтверждены.
- `end: '+=500%'` pin длительности — можно настроить по UX-ощущениям (research предлагал 500–600%). Текущее значение — 500%.
- CI path filters для image `rct-landing-nginx` не проверялись отдельной задачей (см. RESEARCH Q4). Если pipeline rebuild не запустится при изменении только `frontends/landing/dist/**` — потребуется фикс workflow в следующей фазе.
- Архив/ссылки на сами артефакты: `.planning/phases/57-landing-presentation-mode-documentation/57-01-SUMMARY.md`, `57-02-SUMMARY.md`, `57-03-SUMMARY.md`.

## Следующие шаги

- `/gsd-verify-work phase 57` — финальная goal-backward верификация всех 7 requirement IDs
- `/gsd-close-milestone v9.0` — перенос v9.0 в `## Shipped Milestones` секцию `.planning/PROJECT.md` и `ROADMAP.md`
- v10.0 planning (OTP UI, PWA TEACHER/ADMIN — см. REQUIREMENTS.md раздел «Future Requirements»)
