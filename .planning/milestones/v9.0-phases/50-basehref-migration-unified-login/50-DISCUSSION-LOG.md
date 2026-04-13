# Phase 50: baseHref Migration + Unified /login — Discussion Log

> **Audit trail only.** Do not use as input to planning, research, or execution agents.
> Decisions captured in `50-CONTEXT.md` — this log preserves the full Q&A.

**Date:** 2026-04-09
**Phase:** 50-basehref-migration-unified-login
**Mode:** discuss (interactive, response_language=ru)
**Areas discussed:** Nginx routing (prod), Совместимость старых /admin/*, Placeholder-шеллы + layout, /login для залогиненных + тесты

## Prior Context Loaded

- `.planning/PROJECT.md` — v9.0 milestone, D-05/D-06 (memory-only tokens)
- `.planning/REQUIREMENTS.md` — INFRA-v9-04, AUTH-v9-01..07 requirements
- `.planning/ROADMAP.md` — Phase 50 goal, 6 success criteria, implementation notes
- `.planning/STATE.md` — v9.0 architecture decisions (HEADMAN model, unified /login, headmanGuard)
- `.planning/codebase/*.md` — STRUCTURE, CONVENTIONS, ARCHITECTURE, TESTING maps available but not deep-read
- No prior CONTEXT.md files from earlier phases
- `todo match-phase 50` → 0 matches

## Codebase Scout Highlights

- `nginx/conf.d/default.conf:77-81` — existing `location /admin/` proxies to `rct-web-panel-nginx:80`
- `nginx/conf.d/default.conf:130-135` — Phase 49 added `location = / { return 301 /login; }`
- `frontends/web-panel/angular.json:44,62` — `"baseHref": "/admin/"` at two build configurations
- `frontends/web-panel/nginx.conf` — in-container SPA already uses `try_files $uri $uri/ /index.html;`
- `frontends/web-panel/src/app/app.routes.ts` — admin/teacher children under shared `ShellComponent` + `authGuard`
- `frontends/web-panel/src/app/core/auth/auth.service.ts:6-9` — `AuthUser { id, role: 'TEACHER'|'ADMIN' }` — must extend to STUDENT + isHeadman
- `frontends/web-panel/src/app/core/auth/role.guard.ts:16` — hard-coded fallback `'/admin/dashboard' : '/teacher/dashboard'` — needs replacement
- `frontends/web-panel/src/app/features/login/login.component.ts:50` — inline role switch, matches ROADMAP note
- `frontends/landing/dist/index.html:1330` — live `https://ruttrack.site/admin/` reference (ROADMAP risk callout confirmed)

## Questions Asked & Answers

### Area Selection
| Question | Options Presented | User Selection |
|---|---|---|
| Какие области обсуждаем для Фазы 50? (multiSelect) | Nginx routing (prod); Совместимость старых /admin/*; Глубина placeholder-шеллов + layout; /login для залогиненных + тесты | All four selected |

### Area 1: Nginx Routing (prod)
| Question | Options Presented | User Selection |
|---|---|---|
| Как перестроить `nginx/conf.d/default.conf` для web-panel на корне? | (a) location / к web-panel + удалить /admin/ [Recommended]; (b) location / + оставить /admin/; (c) rewrite /admin/(.*) → /$1 | (a) location / + удалить /admin/ |
| В Фазе 49 был добавлен `location = / { return 301 /login; }`. Что с этим? | (a) Оставить 301-редирект [Recommended]; (b) Убрать, пусть Angular сам редиректит | (a) Оставить 301 |

### Area 2: Совместимость старых `/admin/*`
| Question | Options Presented | User Selection |
|---|---|---|
| Как поступаем со ссылками на /admin/ в коде/доках? | (a) Grep + правка всех найденных [Recommended]; (b) Только landing и docs, ignore внутренние; (c) Отложить в фазу 57 | (a) Grep + правка всех |
| Добавить 301 server-side redirect для внешних старых закладок? | (a) Не добавлять [Recommended]; (b) location = /admin/ → 301 /login; (c) returnUrl паттерн в authGuard | (a) Не добавлять |

### Area 3: Placeholder-шеллы + Layout
| Question | Options Presented | User Selection |
|---|---|---|
| Какой layout используем для /student/* и /headman/*? | (a) Общий ShellComponent для всех 4 ролей [Recommended]; (b) Отдельные StudentShell и HeadmanShell; (c) ShellComponent для admin/teacher + плейн для student/headman | (a) Общий ShellComponent |
| Что внутри placeholder-компонентов /student/dashboard и /headman/dashboard? | (a) Заглушка 'Скоро…' + имя роли [Recommended]; (b) Пустой `<router-outlet>`; (c) Фулл-shell с sidebar + заглушка | (a) Заглушка 'Скоро…' |
| Какие маршруты регистрируем в `app.routes.ts` для /student/* и /headman/* сейчас? | (a) Только dashboard + wildcard редирект [Recommended]; (b) dashboard + все будущие паты как placeholders; (c) dashboard + schedule | (a) Только dashboard + wildcard |
| Критерий 4 из ROADMAP требует `/student/schedule`. Выбираем: | (a) Добавить второй placeholder /student/schedule [Recommended]; (b) Два placeholder /student/* + два /headman/*; (c) Оставить только dashboard, criterion 4 в deferred UAT | (a) Добавить /student/schedule |

**Note:** The criterion 4 follow-up corrected the initial "only dashboard" answer — user explicitly chose to cover ROADMAP success criterion 4 in this phase rather than defer it.

### Area 4: `/login` для залогиненных + тесты
| Question | Options Presented | User Selection |
|---|---|---|
| Поведение /login когда пользователь уже авторизован? | (a) guestGuard: авто-редирект на дашборд [Recommended]; (b) Показывать форму + кнопку 'Выйти'; (c) Ничего, показывать форму | (a) guestGuard |
| Где живёт логика `resolvePostLoginDashboard(user)`? | (a) Метод в AuthService [Recommended]; (b) Свободная функция в core/auth/dashboard-redirect.ts; (c) Дублировать в двух местах | (a) Метод в AuthService |
| Глубина новых тестов в этой фазе? | (a) Unit для всех новых кусков [Recommended]; (b) Только критичные: guards + login redirect; (c) Только зелёные 129 (AUTH-v9-07) | (a) Unit для всех новых кусков |

### Wrap-up
| Question | Options Presented | User Selection |
|---|---|---|
| Что дальше? | (a) Готов к фиксации CONTEXT.md [Recommended]; (b) Обсудить Dockerfile/build; (c) Обсудить JWT parsing; (d) Обсудить меню ShellComponent | (a) Готов к CONTEXT.md |

## Corrections Made

None — all recommended defaults were accepted. The criterion-4 follow-up was not a correction but a clarification triggered by Claude noticing a conflict between the user's initial "only dashboard" answer and ROADMAP success criterion 4. User agreed to add `/student/schedule` as a second placeholder route.

## Deferred Ideas (captured during discussion)

- Dockerfile / in-container nginx deep review (not chosen as a discussion area; acknowledged as low-risk, planning agent to verify)
- `jwt-decode` library adoption + stricter JWT typing (tech-debt, future phase)
- ShellComponent sidebar registry refactor (not needed for this phase)
- `returnUrl` pattern in `authGuard` (UX improvement, deferred)
- Full `/student/*` and `/headman/*` route tree (Phases 51-55)
- Landing `LAND-v9-05` multi-role description (Phase 57)

## Auto-Resolved

Not applicable — interactive mode, no `--auto` flag.
