# Phase 50: baseHref Migration + Unified /login — Research

**Дата исследования:** 2026-04-09
**Домен:** Angular 18 SPA routing, nginx reverse proxy, JWT claims parsing
**Уверенность:** HIGH — все ключевые факты верифицированы через чтение реального кода репозитория

---

<user_constraints>
## User Constraints (from CONTEXT.md)

### Locked Decisions

- **D-01:** `nginx/conf.d/default.conf` — убрать блок `location /admin/` (~77–81). Добавить catch-all `location / { proxy_pass http://rct-web-panel-nginx:80/; proxy_set_header Host $host; }` в конце HTTPS-блока. Все более специфичные prefix-локации (`/api/`, `/app/`, `/presentation/`, `/mini-app/`, `/swagger-ui.html`, `/swagger-ui/`, `/v3/api-docs`, `/openapi/`, `/.well-known/acme-challenge/`) остаются ВЫШЕ fallback и побеждают по правилам nginx prefix-match.
- **D-02:** Блок Phase 49 `location = / { return 301 /login; }` сохраняется. Exact-match `= /` побеждает prefix `/`.
- **D-03:** Grep для `ruttrack.site/admin` и `href="/admin` в `frontends/landing/`, `docs/`, `.planning/`. Заменить внешние ссылки (landing HTML, docs .md) на `/login`. Внутренние Angular `routerLink="/admin/..."` не трогать.
- **D-04:** Нет явного 301 редиректа для `/admin/*` в nginx. SPA fallback автоматически отдаёт `/admin/dashboard` — Angular router матчит существующий `AdminDashboardComponent`.
- **D-05:** Единый `ShellComponent` хостит все 4 роли. `/student/*` и `/headman/*` — дочерние маршруты существующего shell.
- **D-06:** Placeholder-компоненты — лёгкие standalone компоненты с единственным сообщением: `"Кабинет [студента|старосты] появится в Фазе 51/54"`. Без записей в сайдбаре. Без стилей кроме базовой типографики.
- **D-07:** Маршруты Phase 50: `/student/dashboard`, `/student/schedule`, `/headman/dashboard` + `path: ''` redirectTo внутри каждой группы. Остальные `/student/*` и `/headman/*` маршруты добавляются в Phases 51-55.
- **D-08:** `guestGuard` (CanActivateFn) на маршруте `/login`. Если `isAuthenticated()` → `resolveDashboardFor(currentUser())` → `router.createUrlTree([dashboardPath])`.
- **D-09:** `AuthService.resolveDashboardFor(user: AuthUser | null): string` — единственный источник истины для post-login редиректов. Логика: `null` → `/login`, `ADMIN` → `/admin/dashboard`, `TEACHER` → `/teacher/dashboard`, `STUDENT && isHeadman` → `/headman/dashboard`, `STUDENT` → `/student/dashboard`.
- **D-10:** Полное unit-test покрытие: student.guard.spec.ts, headman.guard.spec.ts, guest.guard.spec.ts, расширить auth.service.spec.ts, обновить role.guard.spec.ts и login.component.spec.ts. 129 существующих тестов остаются green.

### Claude's Discretion

- Точные имена файлов/компонентов placeholder (например, `StudentDashboardPlaceholderComponent` vs общий `StudentPlaceholderComponent`).
- `is_headman` читается как `boolean` или `string "true"`/`"false"` — **ВЕРИФИЦИРОВАНО в данном исследовании** (см. раздел JWT Payload ниже).
- Детали стилизации placeholder (вариант Material typography, margin, иконка).
- `resolveDashboardFor` — plain method или `computed` signal.
- Порядок коммитов между изменениями `angular.json`, nginx и кода.

### Deferred Ideas (OUT OF SCOPE)

- Реальный контент `/student/*` (Phase 51-53)
- Реальный контент `/headman/*` (Phase 54-55)
- WPAN-13 backend AOP fix (Phase 54)
- PWA headman mode (Phase 56)
- Landing LAND-v9-05 multi-role description (Phase 57)
- `jwt-decode` library adoption
- ShellComponent sidebar registry refactor
- returnUrl pattern в authGuard
</user_constraints>

<phase_requirements>
## Phase Requirements

| ID | Description | Research Support |
|----|-------------|------------------|
| INFRA-v9-04 | Angular web-panel serves `/login`, `/admin/*`, `/teacher/*`, `/student/*`, `/headman/*` как единая SPA с `baseHref: /` | D-01: nginx catch-all, angular.json baseHref change. D-07: новые маршруты зарегистрированы. |
| AUTH-v9-01 | Пользователь входит на `/login` через `POST /api/auth/login`; токен в Angular signal (memory-only) | Уже реализован в login.component.ts + auth.service.ts. Не меняется. |
| AUTH-v9-02 | После успешного логина — маршрут по роли: ADMIN→`/admin/dashboard`, TEACHER→`/teacher/dashboard`, STUDENT→`/student/dashboard`, STUDENT+is_headman→`/headman/dashboard` | D-09: `resolveDashboardFor`. login.component.ts:50 — точка замены. |
| AUTH-v9-03 | `AuthService.currentUser` signal читает `role`, `is_headman`, `group_id` из JWT | Верифицировано: JWT содержит эти три claim. auth.service.ts нужно расширить AuthUser interface. |
| AUTH-v9-04 | `headmanGuard` — только `role=STUDENT && is_headman=true`; headman проходит все `/student/*` маршруты | Новый guard по образцу role.guard.ts. |
| AUTH-v9-05 | `studentGuard` — любой пользователь с `role=STUDENT` (включая headman) | Новый guard по образцу auth.guard.ts (простая функция без фабрики). |
| AUTH-v9-06 | Logout очищает токены, инвалидирует refresh на сервере, редирект → `/login` | Уже реализован в AuthService.logout(). Не меняется. |
| AUTH-v9-07 | 129 существующих web-panel vitest тестов продолжают проходить | Baseline: ~131 `it()` блок в 22 spec-файлах. Изменения auth.service.ts и role.guard.ts требуют обновления существующих spec. |
</phase_requirements>

---

## Summary

Phase 50 — чисто фронтендовая фаза без изменений бэкенда. Три несвязанных технических потока: (1) изменение `baseHref` в `angular.json` + соответствующий nginx catch-all в prod; (2) расширение `AuthService` и новые guards; (3) регистрация placeholder-маршрутов и внешних ссылок.

JWT payload верифицирован из исходного кода `JwtService.java:94-96`: `role` хранится как `user.getRole().name()` — то есть `UPPER_CASE` строка Java enum (`"ADMIN"`, `"TEACHER"`, `"STUDENT"`). `is_headman` — нативный Java `boolean`, что означает `true`/`false` в JSON без кавычек. `group_id` присутствует (`user.getGroupId()`), может быть `null` для пользователей без группы (ADMIN, TEACHER).

Существующий `auth.service.ts` уже нормализует роль через `.toUpperCase()`, поэтому для `role` нормализация не нужна. Для `is_headman` — читать как `!!payload.is_headman` (boolean-coercion, устойчив к отсутствию claim).

**Primary recommendation:** Начать с расширения `AuthService` и spec-файлов, затем новые guards с тестами, затем `app.routes.ts`, затем `angular.json` + nginx — в таком порядке тесты всегда можно запустить и проверить после каждого шага.

---

## Standard Stack

### Core (уже установлено в web-panel)

| Библиотека | Версия | Назначение | Комментарий |
|------------|--------|-----------|-------------|
| Angular | ~18 (из package.json) | SPA framework, router, signals | Уже используется [VERIFIED: codebase] |
| @angular/router | ~18 | CanActivateFn, Routes, Router | Guards используют inject() pattern [VERIFIED: codebase] |
| @analogjs/vite-plugin-angular | — | vitest ↔ Angular интеграция | Конфиг: vitest.config.ts [VERIFIED: codebase] |
| vitest | — | Test runner | setupFiles: src/test-setup.ts [VERIFIED: codebase] |
| @testing-library/angular | — | render(), screen, fireEvent | Используется в login.component.spec.ts [VERIFIED: codebase] |
| Angular Material | — | Mat-компоненты в UI | Используется везде [VERIFIED: codebase] |

### Без новых зависимостей

Phase 50 не требует установки новых npm пакетов. Все необходимые инструменты уже в `package.json`. [VERIFIED: codebase — все guard patterns используют только `@angular/router` и `@angular/core`]

---

## Architecture Patterns

### Рекомендуемая структура новых файлов

```
frontends/web-panel/src/app/
├── core/auth/
│   ├── auth.service.ts          ← ИЗМЕНИТЬ: AuthUser + resolveDashboardFor
│   ├── auth.service.spec.ts     ← ИЗМЕНИТЬ: добавить STUDENT/headman тесты
│   ├── auth.guard.ts            ← БЕЗ ИЗМЕНЕНИЙ
│   ├── auth.guard.spec.ts       ← БЕЗ ИЗМЕНЕНИЙ (или минимальные)
│   ├── role.guard.ts            ← ИЗМЕНИТЬ: fallback через resolveDashboardFor
│   ├── role.guard.spec.ts       ← ИЗМЕНИТЬ: добавить STUDENT сценарии
│   ├── student.guard.ts         ← НОВЫЙ
│   ├── student.guard.spec.ts    ← НОВЫЙ
│   ├── headman.guard.ts         ← НОВЫЙ
│   ├── headman.guard.spec.ts    ← НОВЫЙ
│   ├── guest.guard.ts           ← НОВЫЙ
│   └── guest.guard.spec.ts      ← НОВЫЙ
├── features/
│   ├── login/
│   │   ├── login.component.ts   ← ИЗМЕНИТЬ: строка 50, вызов resolveDashboardFor
│   │   └── login.component.spec.ts ← ИЗМЕНИТЬ: 4 redirect-сценария
│   ├── student/                 ← НОВАЯ ДИРЕКТОРИЯ
│   │   └── student-placeholder/
│   │       └── student-placeholder.component.ts
│   └── headman/                 ← НОВАЯ ДИРЕКТОРИЯ
│       └── headman-placeholder/
│           └── headman-placeholder.component.ts
└── app.routes.ts                ← ИЗМЕНИТЬ: новые маршруты + guestGuard на login
```

### Pattern 1: CanActivateFn (существующий образец для guard)

Все существующие guard'ы следуют единому образцу. Новые guards ДОЛЖНЫ следовать ему же.

```typescript
// Source: frontends/web-panel/src/app/core/auth/auth.guard.ts [VERIFIED]
import { CanActivateFn } from '@angular/router';
import { inject } from '@angular/core';
import { Router } from '@angular/router';
import { AuthService } from './auth.service';

export const authGuard: CanActivateFn = () => {
  const auth = inject(AuthService);
  const router = inject(Router);

  if (auth.isAuthenticated()) return true;
  return router.createUrlTree(['/login']);
};
```

**Правило:** `inject()` внутри функции, не в замыкании. Возврат `true | UrlTree`. Никакого императивного `router.navigate()`.

### Pattern 2: Guard factory (roleGuard — образец)

```typescript
// Source: frontends/web-panel/src/app/core/auth/role.guard.ts [VERIFIED]
export const roleGuard =
  (allowedRoles: string[]): CanActivateFn =>
  () => {
    const auth = inject(AuthService);
    const router = inject(Router);
    const user = auth.currentUser();
    if (!user) return router.createUrlTree(['/login']);
    if (!allowedRoles.includes(user.role)) {
      // ТЕКУЩИЙ hardcode — нужно заменить на resolveDashboardFor:
      const dashboard = user.role === 'ADMIN' ? '/admin/dashboard' : '/teacher/dashboard';
      return router.createUrlTree([dashboard]);
    }
    return true;
  };
```

`studentGuard` и `headmanGuard` — именованные функции (не фабрики), т.к. без параметров.

### Pattern 3: JWT payload parsing (AuthService computed signal)

```typescript
// Source: frontends/web-panel/src/app/core/auth/auth.service.ts:18-32 [VERIFIED]
readonly currentUser = computed((): AuthUser | null => {
  const token = this._accessToken();
  if (!token) return null;
  try {
    const parts = token.split('.');
    if (parts.length !== 3) return null;
    const payload = JSON.parse(atob(parts[1]));
    return {
      id: Number(payload.sub),
      role: (payload.role as string).toUpperCase() as 'TEACHER' | 'ADMIN',
    };
  } catch {
    return null;
  }
});
```

Расширение для STUDENT + isHeadman + groupId — добавить в return объект, не менять механику.

### Pattern 4: Guard unit-тест через TestBed.runInInjectionContext

```typescript
// Source: frontends/web-panel/src/app/core/auth/auth.guard.spec.ts [VERIFIED]
it('returns true when isAuthenticated() is true', () => {
  authService.setTokens(TEACHER_TOKEN, REFRESH_TOKEN);
  const result = TestBed.runInInjectionContext(() =>
    authGuard({} as any, {} as any)
  );
  expect(result).toBe(true);
});
```

Все новые guard spec-файлы ДОЛЖНЫ использовать `TestBed.runInInjectionContext()`. Router мокируется через `useValue: { createUrlTree: vi.fn(...) }`.

### Pattern 5: Sidebar role-filtering (существующий механизм)

```typescript
// Source: frontends/web-panel/src/app/layout/sidebar/sidebar.component.ts [VERIFIED]
interface NavItem {
  label: string;
  icon: string;
  route: string;
  roles: ('TEACHER' | 'ADMIN')[]; // ← тип нужно расширить до ('TEACHER' | 'ADMIN' | 'STUDENT')
}

readonly filteredNavItems = computed(() => {
  const user = this.currentUser();
  if (!user) return [];
  return this.allNavItems.filter((item) => item.roles.includes(user.role));
});
```

Per D-06 в Phase 50 нет новых записей в сайдбаре для STUDENT/HEADMAN. Но тип `NavItem.roles` придётся расширить при изменении `AuthUser.role` — иначе TypeScript не скомпилируется. Конкретно: тип `roles` изменится на `('TEACHER' | 'ADMIN' | 'STUDENT')[]`, и `sectionLabel computed` нужно будет покрыть STUDENT-case.

### Anti-Patterns to Avoid

- **Императивный `router.navigate()` в guard:** использовать только `router.createUrlTree()`, возвращая UrlTree.
- **localStorage для JWT токенов:** проект использует memory-only signals. Не добавлять localStorage/sessionStorage для токенов.
- **Новая зависимость `jwt-decode`:** деферирована. Продолжать ручной `atob()`.
- **Добавление `HEADMAN` в UserRole enum:** HEADMAN = `is_headman: boolean`, не enum value.
- **Изменение nginx внутри контейнера (`frontends/web-panel/nginx.conf`):** файл уже правильный (`try_files $uri $uri/ /index.html`), не требует изменений.

---

## Don't Hand-Roll

| Проблема | Не строить | Использовать | Почему |
|---------|-----------|-------------|--------|
| Хранение ролей в отдельном сервисе | Отдельный RoleService | `AuthService.currentUser()` signal | Уже централизовано, computed signal реактивен |
| JWT decode | Кастомный Base64 парсер | Существующий `atob(parts[1])` | Уже протестирован в 11 spec |
| Redirect логика в каждом компоненте | Хардкод `/admin/dashboard` в каждом месте | `AuthService.resolveDashboardFor()` | Единственный источник истины — D-09 |
| Отдельный layout для student/headman | Новый ShellComponent | Существующий `ShellComponent` | D-05: максимальное переиспользование |
| 301 редирект для `/admin/*` в nginx | `location /admin/ { return 301 /login; }` | SPA fallback | D-04: Angular router сам разберётся |

---

## Verified Findings (ответы на конкретные вопросы)

### Q1. JWT payload shape (КРИТИЧНО для AUTH-v9-02/03)

**Источник:** `JwtService.java:89-101` [VERIFIED: direct file read]

```java
return Jwts.builder()
    .claim("role", user.getRole().name())    // → "ADMIN" / "TEACHER" / "STUDENT" (UPPER_CASE)
    .claim("group_id", user.getGroupId())    // → Long | null
    .claim("is_headman", user.isHeadman())  // → boolean: true / false (нативный Java boolean)
    .subject(user.getId().toString())        // → числовая строка, e.g. "1"
    ...
```

**Точная форма payload в JSON:**
```json
{
  "sub": "42",
  "role": "STUDENT",
  "group_id": 7,
  "is_headman": true,
  "iss": "rutcampustrack-auth",
  "aud": ["rutcampustrack"],
  "iat": 1712345678,
  "exp": 1712349278
}
```

**Выводы для AuthService:**
- `role` — уже `UPPER_CASE` в JWT. Существующий `.toUpperCase()` — безвредная двойная нормализация, оставить.
- `is_headman` — нативный JSON `boolean`, **не** строка `"true"`. Читать: `payload.is_headman === true` или `!!payload.is_headman`.
- `group_id` — может быть `null` (у ADMIN, TEACHER). Читать с проверкой: `payload.group_id ?? null`.
- Ключи JWT: `sub`, `role`, `group_id`, `is_headman` (snake_case).

**Расширение `AuthUser` interface:**
```typescript
export interface AuthUser {
  id: number;
  role: 'TEACHER' | 'ADMIN' | 'STUDENT';
  isHeadman: boolean;
  groupId: number | null;
}
```

### Q2. Внутри-контейнерный nginx.conf

**Источник:** `frontends/web-panel/nginx.conf` [VERIFIED: direct file read]

```nginx
server {
    listen 80;
    root /usr/share/nginx/html;
    index index.html;

    location ~* \.(js|css|png|jpg|jpeg|svg|ico|woff|woff2|eot|ttf)$ {
        add_header Cache-Control "public, max-age=31536000, immutable";
    }

    location / {
        try_files $uri $uri/ /index.html;
    }
}
```

**Выводы:**
- `try_files $uri $uri/ /index.html` — присутствует. SPA fallback работает.
- Нет нигде хардкода `/admin/`. **Файл изменений не требует.**
- Dockerfile копирует `dist/browser` (а не `dist/`) — соответствует `@angular/build:application` output.
- После смены `baseHref: /admin/` → `/` собранный `index.html` будет содержать `<base href="/">` вместо `<base href="/admin/">`. Nginx это никак не интерпретирует — базовый тег только для Angular router.

### Q3. angular.json baseHref

**Источник:** `frontends/web-panel/angular.json:44,62` [VERIFIED: direct file read]

```json
"options": {
  "baseHref": "/admin/",   // ← строка 44, architect.build.options
  ...
},
"configurations": {
  "production": {
    "baseHref": "/admin/",  // ← строка 62, architect.build.configurations.production
    ...
  }
}
```

**Два вхождения, оба нужно изменить на `"/"`.**

- `architect.build.options.baseHref` — дефолтный (используется при `ng build` без конфигурации).
- `architect.build.configurations.production.baseHref` — используется при `ng build --configuration=production`.
- Dockerfile запускает `npm run build` → смотрит `package.json scripts.build` → вероятнее `ng build` (production — default configuration). Оба вхождения нужно поменять.
- В `serve` конфигурации нет `baseHref` — он наследует из `build`.

### Q4. Prod nginx (nginx/conf.d/default.conf)

**Источник:** `nginx/conf.d/default.conf` [VERIFIED: direct file read]

**Текущие блоки (HTTPS server, порядок сверху вниз):**

```
/api/ws/         → rct-api-gateway:8080 (WebSocket, специальные заголовки)
/api/            → rct-api-gateway:8080
/presentation/   → rct-landing-nginx:80/
/app/            → rct-pwa-nginx:80/
/admin/          → rct-web-panel-nginx:80/    ← УДАЛИТЬ (D-01)
/mini-app/       → rct-mini-app-nginx:80/
/swagger-ui.html → rct-api-gateway:8080 (auth_basic)
/swagger-ui/     → rct-api-gateway:8080 (auth_basic)
/v3/api-docs     → rct-api-gateway:8080 (auth_basic)
/openapi/        → rct-api-gateway:8080 (auth_basic)
= /              → 301 /login               ← СОХРАНИТЬ (D-02)
```

**Целевая конфигурация после D-01/D-02 (добавить в конец HTTPS server блока перед закрывающей `}`):**

```nginx
    # --- Web Panel SPA — catch-all (INFRA-v9-04, Phase 50) ---
    # Должен быть ПОСЛЕДНИМ prefix-блоком; все более специфичные выше побеждают
    location / {
        proxy_pass http://rct-web-panel-nginx:80/;
        proxy_set_header Host $host;
    }
```

**Правила nginx prefix-match для понимания плановщика:**
- Exact match `= /` побеждает любой prefix `/` — `location = /` всё ещё редиректит на `/login`.
- Более длинный prefix `/api/` побеждает более короткий `/` — все API-запросы идут на gateway.
- `/mini-app/`, `/presentation/`, `/app/` аналогично.
- Новый `location /` получает только то, что не совпало с более специфичными блоками.

**ПРЕДУПРЕЖДЕНИЕ:** После удаления `location /admin/` и добавления `location /` — URL `https://ruttrack.site/admin/dashboard` будет обслуживаться через catch-all `/`, что направит его на web-panel контейнер. Angular router внутри SPA затем матчит `/admin/dashboard` → `AdminDashboardComponent`. Это и есть D-04 (graceful degradation без 301).

### Q5. AuthService текущая реализация

**Источник:** `frontends/web-panel/src/app/core/auth/auth.service.ts` [VERIFIED: direct file read]

- **Строка 6-8:** `AuthUser` interface: `{ id: number; role: 'TEACHER' | 'ADMIN' }` — нужно добавить `'STUDENT'` в union + `isHeadman: boolean` + `groupId: number | null`.
- **Строка 13:** `_accessToken = signal<string | null>(null)` — memory-only ✓
- **Строка 18-32:** `currentUser` = `computed(...)` — разбирает `atob(parts[1])`. Нормализация `.toUpperCase()` на строке 27.
- **Строка 48:** `logout(authApi, router)` — уже вызывает `authApi.logout(rt)` + `clearTokens()` + `router.navigate(['/login'])`. AUTH-v9-06 уже выполнен.

**Минимальный diff для расширения:**

```typescript
// Изменить interface:
export interface AuthUser {
  id: number;
  role: 'TEACHER' | 'ADMIN' | 'STUDENT';
  isHeadman: boolean;
  groupId: number | null;
}

// Изменить computed return:
return {
  id: Number(payload.sub),
  role: (payload.role as string).toUpperCase() as 'TEACHER' | 'ADMIN' | 'STUDENT',
  isHeadman: payload.is_headman === true,
  groupId: payload.group_id ?? null,
};

// Добавить новый метод:
resolveDashboardFor(user: AuthUser | null): string {
  if (!user) return '/login';
  if (user.role === 'ADMIN') return '/admin/dashboard';
  if (user.role === 'TEACHER') return '/teacher/dashboard';
  if (user.isHeadman) return '/headman/dashboard';
  return '/student/dashboard';
}
```

### Q6. role.guard.ts и auth.guard.ts

**Источник:** `frontends/web-panel/src/app/core/auth/role.guard.ts`, `auth.guard.ts` [VERIFIED]

- `authGuard`: именованная функция `CanActivateFn`, `inject()` внутри функции, возврат `true | UrlTree`.
- `roleGuard`: фабрика → возвращает `CanActivateFn`, `inject()` внутри возвращаемой функции.
- Fallback в `roleGuard` (строки 15-16): хардкод `user.role === 'ADMIN' ? '/admin/dashboard' : '/teacher/dashboard'` — **нужно заменить на `auth.resolveDashboardFor(user)`** чтобы покрыть STUDENT-случай.
- Новые `studentGuard` и `headmanGuard` — именованные функции (не фабрики), образец как `authGuard`.

### Q7. login.component.ts redirect logic

**Источник:** `frontends/web-panel/src/app/features/login/login.component.ts:49-51` [VERIFIED]

```typescript
// Текущая строка 50 (точное содержимое):
this.router.navigate([role === 'ADMIN' ? '/admin/dashboard' : '/teacher/dashboard']);
```

**Замена (D-09):**
```typescript
this.router.navigateByUrl(this.authService.resolveDashboardFor(this.authService.currentUser()));
```

Полный контекст: в `next` callback после `setTokens`:
```typescript
next: (tokens) => {
  this.authService.setTokens(tokens.accessToken, tokens.refreshToken);
  // строка 50 — ЗАМЕНИТЬ:
  this.router.navigateByUrl(this.authService.resolveDashboardFor(this.authService.currentUser()));
},
```

### Q8. app.routes.ts структура

**Источник:** `frontends/web-panel/src/app/app.routes.ts` [VERIFIED: direct file read]

**Текущая структура:**
```
/login                   → LoginComponent (lazy)
/ (shell, authGuard)
  /teacher (roleGuard(['TEACHER']))
    /teacher/dashboard
    /teacher/journal
    /teacher/stats
    path: '' → redirectTo: 'dashboard'
  /admin (roleGuard(['ADMIN']))
    /admin/dashboard
    /admin/users
    /admin/groups
    /admin/semesters
    path: '' → redirectTo: 'dashboard'
  path: '' → redirectTo: 'login'
** → redirectTo: 'login'
```

**Точки добавления Phase 50 (внутри shell children, после admin block, перед `{ path: '', redirectTo: 'login' }`):**

```typescript
// student routes (D-07)
{
  path: 'student',
  canActivate: [studentGuard],
  data: { eyebrow: 'Студент' },
  children: [
    {
      path: 'dashboard',
      loadComponent: () =>
        import('./features/student/student-placeholder/student-placeholder.component')
          .then(m => m.StudentPlaceholderComponent),
      data: { title: 'Личный кабинет', eyebrow: 'Студент' },
    },
    {
      path: 'schedule',
      loadComponent: () =>
        import('./features/student/student-placeholder/student-placeholder.component')
          .then(m => m.StudentPlaceholderComponent),
      data: { title: 'Расписание', eyebrow: 'Студент' },
    },
    { path: '', redirectTo: 'dashboard', pathMatch: 'full' },
  ],
},
// headman routes (D-07)
{
  path: 'headman',
  canActivate: [headmanGuard],
  data: { eyebrow: 'Староста' },
  children: [
    {
      path: 'dashboard',
      loadComponent: () =>
        import('./features/headman/headman-placeholder/headman-placeholder.component')
          .then(m => m.HeadmanPlaceholderComponent),
      data: { title: 'Кабинет старосты', eyebrow: 'Староста' },
    },
    { path: '', redirectTo: 'dashboard', pathMatch: 'full' },
  ],
},
```

**Маршрут `/login` нужно добавить `canActivate: [guestGuard]`:**
```typescript
{
  path: 'login',
  canActivate: [guestGuard],
  loadComponent: () =>
    import('./features/login/login.component').then(m => m.LoginComponent),
},
```

### Q9. ShellComponent sidebar

**Источник:** `frontends/web-panel/src/app/layout/sidebar/sidebar.component.ts` [VERIFIED]

- `SidebarComponent` рендерит через `filteredPrimaryItems` и `filteredNavItems` — оба `computed()` signals фильтруют по `item.roles.includes(user.role)`.
- `NavItem.roles` тип: `('TEACHER' | 'ADMIN')[]` — **нужно расширить до `('TEACHER' | 'ADMIN' | 'STUDENT')[]`** из-за TypeScript, иначе `.includes(user.role)` не скомпилируется когда `user.role` может быть `'STUDENT'`.
- `sectionLabel` computed: `user.role === 'ADMIN' ? 'Администрирование' : 'Работа'` — нужно добавить STUDENT case.
- Per D-06: **никаких новых записей в `primaryItems` и `allNavItems` для STUDENT/HEADMAN в Phase 50**. Только расширение типов.
- `sidebar.component.spec.ts` использует `AuthUser` import — после изменения interface может потребовать обновления mock.

### Q10. Внешние `/admin/` ссылки

**Источник:** grep по `frontends/landing/`, `docs/`, `.planning/` [VERIFIED: bash search]

**Единственная внешняя ссылка, требующая изменения в Phase 50:**

| Файл | Строка | Содержимое | Действие |
|------|--------|-----------|---------|
| `frontends/landing/dist/index.html` | 1330 | `<a href="https://ruttrack.site/admin/">Панель администратора</a>` | Заменить на `<a href="/login">Войти</a>` |

**Остальные вхождения в `grep`-выводе** — в `.planning/` (history/plan документы — только читаемые, не редактируемые) или в Phase 49 планах (уже выполненных). Они **не требуют изменения**.

**Нет совпадений** в `docs/*.md` файлах проекта с `ruttrack.site/admin` или `href="/admin"` требующих замены в Phase 50.

### Q11. vitest conventions

**Источник:** `vitest.config.ts`, `auth.service.spec.ts`, `auth.guard.spec.ts` [VERIFIED]

```typescript
// vitest.config.ts — ключевые настройки:
environment: 'jsdom',
setupFiles: ['src/test-setup.ts'],   // zone.js + @angular/compiler + TestBed init
include: ['src/**/*.spec.ts'],
globals: true
```

**Стандартный шаблон для guard spec:**

```typescript
import { TestBed } from '@angular/core/testing';
import { describe, it, expect, beforeEach, vi } from 'vitest';
import { Router, UrlTree } from '@angular/router';
import { AuthService } from './auth.service';
import { studentGuard } from './student.guard';

const makeJwt = (payload: object): string => {
  const header = btoa(JSON.stringify({ alg: 'HS256', typ: 'JWT' }));
  const body = btoa(JSON.stringify(payload));
  return `${header}.${body}.signature`;
};

const STUDENT_TOKEN = makeJwt({ sub: '3', role: 'STUDENT', is_headman: false, group_id: 5, exp: 9999999999 });
const HEADMAN_TOKEN = makeJwt({ sub: '4', role: 'STUDENT', is_headman: true, group_id: 5, exp: 9999999999 });
const REFRESH_TOKEN = 'refresh-token-abc';

describe('studentGuard', () => {
  let authService: AuthService;
  let router: Router;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [
        AuthService,
        {
          provide: Router,
          useValue: {
            createUrlTree: vi.fn((commands: string[]) => ({ commands }) as unknown as UrlTree),
            navigate: vi.fn(),
          },
        },
      ],
    });
    authService = TestBed.inject(AuthService);
    router = TestBed.inject(Router);
  });

  it('allows STUDENT user through', () => {
    authService.setTokens(STUDENT_TOKEN, REFRESH_TOKEN);
    const result = TestBed.runInInjectionContext(() =>
      studentGuard({} as any, {} as any)
    );
    expect(result).toBe(true);
  });
  // ...
});
```

### Q12. Количество текущих тестов

**Источник:** Подсчёт `it(` в spec-файлах [VERIFIED: bash grep]

| Spec-файл | it() блоков |
|-----------|-------------|
| `auth.service.spec.ts` | 11 |
| `auth.guard.spec.ts` (включает roleGuard) | 6 |
| `auth.interceptor.spec.ts` | 6 |
| `login.component.spec.ts` | 10 |
| `sidebar.component.spec.ts` | 4 |
| `theme.service.spec.ts` | 8 |
| Teacher features (все spec) | 48 |
| Admin features (все spec) | 38 |
| **Итого** | **131** |

Расхождение с "129" в ROADMAP — вероятно, некоторые `it(` блоки находятся в `describe.skip` или параметрических тестах. Базовая линия для AUTH-v9-07: **все эти тесты должны быть green после Phase 50**.

**Спецификации которые потребуют изменений (обновление, не удаление):**
- `auth.service.spec.ts` — добавить тесты для STUDENT role + is_headman + resolveDashboardFor
- `auth.guard.spec.ts` / `role.guard.spec.ts` — добавить STUDENT + headman сценарии
- `login.component.spec.ts` — 2 существующих redirect-теста (TEACHER, ADMIN) остаются, добавить STUDENT + headman
- `sidebar.component.spec.ts` — `AuthUser` type изменится, mock может потребовать обновления

---

## Common Pitfalls

### Pitfall 1: `<base href>` и `/login` недоступен без trailing slash

**Что идёт не так:** После смены `baseHref: /admin/` → `/` Angular генерирует `<base href="/">`. Но если nginx отдаёт SPA для `/login` — Angular router корректно матчит. Проблемы нет.

**Почему возникает:** Прежний `baseHref: /admin/` означал, что все assets грузились по `/admin/main.xxx.js`. После смены они грузятся по `/main.xxx.js`. Если prod nginx кешировал старые пути — нужен `docker compose pull && docker compose up -d --force-recreate`.

**Как избежать:** Перестраивать Docker образ (`npm run build` → новый dist с `<base href="/">`), затем обновлять контейнер.

### Pitfall 2: `roleGuard` fallback hardcode ломает STUDENT

**Что идёт не так:** Текущий `role.guard.ts:16` — хардкод `user.role === 'ADMIN' ? '/admin/dashboard' : '/teacher/dashboard'`. Если STUDENT попадёт в `roleGuard(['ADMIN'])` — будет отправлен на `/teacher/dashboard` вместо `/student/dashboard`.

**Как избежать:** Заменить fallback на `auth.resolveDashboardFor(user)` как часть Phase 50 — это уже в D-09.

### Pitfall 3: TypeScript ошибки компиляции из-за расширения AuthUser

**Что идёт не так:** `sidebar.component.ts` типизирует `NavItem.roles` как `('TEACHER' | 'ADMIN')[]`. После добавления `'STUDENT'` в `AuthUser.role` union — TypeScript выдаст ошибку на `item.roles.includes(user.role)` поскольку `'STUDENT'` не входит в тип `roles`.

**Признаки:** `ng build` завершается с error, vitest тоже может провалиться с compilation error.

**Как избежать:** Одновременно с изменением `AuthUser.role` обновить:
1. `NavItem.roles` тип в `sidebar.component.ts`
2. `sectionLabel` computed (добавить STUDENT case)
3. `sidebar.component.spec.ts` mock (убедиться что `AuthUser` mock включает новые поля)

### Pitfall 4: login.component.spec.ts — существующие тесты сломаются

**Что идёт не так:** `login.component.spec.ts` тестирует строку 50 напрямую — mock `currentUser` возвращает `{ id: 1, role: 'TEACHER' }`, тест ожидает `router.navigate(['/teacher/dashboard'])`. После смены на `resolveDashboardFor` — API вызова `router.navigate` не будет, будет `router.navigateByUrl(string)`.

**Как избежать:** Обновить spec вместе с компонентом. Изменить mock `mockRouter` — добавить `navigateByUrl: vi.fn()`. Изменить assertions — проверять `navigateByUrl` вместо `navigate`.

### Pitfall 5: is_headman отсутствует в JWT у существующих TEACHER/ADMIN пользователей

**Что идёт не так:** JWT токены для TEACHER и ADMIN **тоже содержат** `"is_headman": false` (Java `user.isHeadman()` всегда возвращает значение поля, которое по умолчанию `false`). Но если по какой-то причине claim отсутствует — `payload.is_headman` будет `undefined`. `!!undefined === false`, так что `isHeadman` корректно будет `false`.

**Как избежать:** Читать `isHeadman: payload.is_headman === true` (не `!!payload.is_headman`) для строгой проверки. Тест с JWT без `is_headman` claim должен вернуть `isHeadman: false`.

### Pitfall 6: nginx catch-all должен быть ПОСЛЕДНИМ prefix-блоком

**Что идёт не так:** Если добавить `location / { ... }` перед `/api/`, `/mini-app/` etc. — все запросы пойдут на web-panel.

**Как избежать:** Добавить `location /` в конец HTTPS server блока (как указано в D-01), после всех специфичных prefix блоков, непосредственно перед закрывающей `}`.

### Pitfall 7: Забыть guestGuard на /login — ADMIN/TEACHER попадут на пустую форму

**Что идёт не так:** Без `guestGuard` уже аутентифицированный пользователь, открывая `/login`, увидит форму входа вместо редиректа на дашборд.

**Как избежать:** D-08 требует `canActivate: [guestGuard]` на маршруте `/login` в `app.routes.ts`.

---

## Code Examples

### Новый studentGuard

```typescript
// frontends/web-panel/src/app/core/auth/student.guard.ts
import { CanActivateFn } from '@angular/router';
import { inject } from '@angular/core';
import { Router } from '@angular/router';
import { AuthService } from './auth.service';

export const studentGuard: CanActivateFn = () => {
  const auth = inject(AuthService);
  const router = inject(Router);

  const user = auth.currentUser();
  if (!user) return router.createUrlTree(['/login']);
  if (user.role === 'STUDENT') return true;
  return router.createUrlTree([auth.resolveDashboardFor(user)]);
};
```

### Новый headmanGuard

```typescript
// frontends/web-panel/src/app/core/auth/headman.guard.ts
import { CanActivateFn } from '@angular/router';
import { inject } from '@angular/core';
import { Router } from '@angular/router';
import { AuthService } from './auth.service';

export const headmanGuard: CanActivateFn = () => {
  const auth = inject(AuthService);
  const router = inject(Router);

  const user = auth.currentUser();
  if (!user) return router.createUrlTree(['/login']);
  if (user.role === 'STUDENT' && user.isHeadman) return true;
  return router.createUrlTree([auth.resolveDashboardFor(user)]);
};
```

### Новый guestGuard

```typescript
// frontends/web-panel/src/app/core/auth/guest.guard.ts
import { CanActivateFn } from '@angular/router';
import { inject } from '@angular/core';
import { Router } from '@angular/router';
import { AuthService } from './auth.service';

export const guestGuard: CanActivateFn = () => {
  const auth = inject(AuthService);
  const router = inject(Router);

  if (!auth.isAuthenticated()) return true;
  return router.createUrlTree([auth.resolveDashboardFor(auth.currentUser())]);
};
```

### Placeholder компонент (пример для student)

```typescript
// frontends/web-panel/src/app/features/student/student-placeholder/student-placeholder.component.ts
import { Component } from '@angular/core';
import { MatCardModule } from '@angular/material/card';

@Component({
  selector: 'app-student-placeholder',
  standalone: true,
  imports: [MatCardModule],
  template: `
    <div style="display: flex; justify-content: center; padding: 48px 16px;">
      <mat-card style="max-width: 480px; width: 100%; text-align: center; padding: 32px;">
        <p class="mat-body-1" style="color: var(--mat-sys-on-surface-variant);">
          Кабинет студента появится в Фазе 51
        </p>
      </mat-card>
    </div>
  `,
})
export class StudentPlaceholderComponent {}
```

### Целевой nginx/conf.d/default.conf (HTTPS блок, только изменённая часть)

```nginx
    # УДАЛИТЬ:
    # location /admin/ {
    #     proxy_pass http://rct-web-panel-nginx:80/;
    #     proxy_set_header Host $host;
    # }

    # ... /mini-app/, /swagger-ui.html, /swagger-ui/, /v3/api-docs, /openapi/ остаются ...

    # СОХРАНИТЬ (Phase 49):
    location = / {
        return 301 /login;
    }

    # ДОБАВИТЬ В КОНЕЦ (D-01, Phase 50):
    location / {
        proxy_pass http://rct-web-panel-nginx:80/;
        proxy_set_header Host $host;
    }
```

---

## State of the Art

| Старый подход | Текущий подход | Изменение | Влияние |
|--------------|----------------|-----------|---------|
| `baseHref: /admin/` → SPA на `/admin/*` | `baseHref: /` → SPA на всём пространстве URLs | Phase 50 | web-panel обслуживает `/login`, `/student/*`, `/headman/*` |
| Хардкод `role === 'ADMIN' ? ... : ...` в каждой точке | `resolveDashboardFor()` — единый метод | Phase 50 | Все 4 роли обрабатываются детерминировано |
| `AuthUser` без `isHeadman`/`groupId` | `AuthUser` c полным набором JWT claims | Phase 50 | Phase 51-55 могут читать groupId без доп. запросов |

---

## Runtime State Inventory

Фаза не является rename/refactor/migration фазой в смысле переименования хранимых данных. Никаких runtime-состояний, требующих миграции, не выявлено.

- **Stored data:** Нет. JWT токены хранятся memory-only в Angular signals, не в БД и не в localStorage.
- **Live service config:** Нет. nginx prod конфиг — файл в git (`nginx/conf.d/default.conf`), применяется при перезапуске контейнера.
- **OS-registered state:** Нет.
- **Secrets/env vars:** Нет. JWT ключи не меняются.
- **Build artifacts:** После смены `baseHref` нужен rebuild Docker образа `rct-web-panel-nginx`. Это стандартный деплой, не миграция.

---

## Environment Availability

Фаза — фронтендовые изменения + nginx конфиг. Новых внешних зависимостей нет.

| Зависимость | Требуется для | Доступна | Комментарий |
|------------|--------------|---------|-------------|
| Node.js / npm | `npm run build`, `npm run test` | ✓ [ASSUMED: CI pipeline из v8.0 работает] | Dockerfile использует `node:22-alpine` |
| nginx | Prod reverse proxy | ✓ [VERIFIED: nginx/conf.d/default.conf существует] | В Docker compose |
| Angular CLI | `ng build` (через npm script) | ✓ [VERIFIED: angular.json существует, Dockerfile использует npm run build] | Внутри Docker builder stage |
| vitest | Запуск тестов | ✓ [VERIFIED: vitest.config.ts существует] | `npm run test` |

**Missing dependencies with no fallback:** Нет.

---

## Validation Architecture

Nyquist validation включён (ключ `workflow.nyquist_validation` отсутствует в config.json, трактуется как enabled).

### Test Framework

| Свойство | Значение |
|---------|---------|
| Framework | Vitest + @analogjs/vite-plugin-angular |
| Config file | `frontends/web-panel/vitest.config.ts` |
| Quick run command | `cd frontends/web-panel && npm run test` |
| Full suite command | `cd frontends/web-panel && npm run test` (нет отдельного watch/full split) |

### Phase Requirements → Test Map

| REQ-ID | Поведение | Тип теста | Команда | Файл существует? |
|--------|----------|----------|---------|-----------------|
| INFRA-v9-04 | web-panel обслуживает `/login`, `/student/*`, `/headman/*` | smoke manual | `curl https://ruttrack.site/login` → 200 | — (ручная проверка) |
| AUTH-v9-01 | Логин через `/login` работает | unit + manual smoke | `npm run test` + ручной логин | ✅ login.component.spec.ts |
| AUTH-v9-02 | 4 post-login редиректа по роли | unit | `npm run test` | ❌ Wave 0: расширить login.component.spec.ts |
| AUTH-v9-03 | currentUser() парсит role, is_headman, group_id | unit | `npm run test` | ❌ Wave 0: расширить auth.service.spec.ts |
| AUTH-v9-04 | headmanGuard: STUDENT+isHeadman=true проходит | unit | `npm run test` | ❌ Wave 0: headman.guard.spec.ts |
| AUTH-v9-05 | studentGuard: любой STUDENT проходит | unit | `npm run test` | ❌ Wave 0: student.guard.spec.ts |
| AUTH-v9-06 | Logout очищает токены + редирект /login | unit (уже есть) | `npm run test` | ✅ auth.service.spec.ts |
| AUTH-v9-07 | 129 существующих тестов green | unit regression | `npm run test` | ✅ все 22 spec-файла |

### Sampling Rate

- **После каждого изменяемого файла:** `cd frontends/web-panel && npm run test`
- **Per wave merge:** `cd frontends/web-panel && npm run test`
- **Phase gate:** полный suite green перед `/gsd-verify-work`

### Wave 0 Gaps (новые файлы, которые нужно создать)

- [ ] `src/app/core/auth/student.guard.ts` + `student.guard.spec.ts` — покрывает AUTH-v9-05
- [ ] `src/app/core/auth/headman.guard.ts` + `headman.guard.spec.ts` — покрывает AUTH-v9-04
- [ ] `src/app/core/auth/guest.guard.ts` + `guest.guard.spec.ts` — покрывает D-08
- [ ] `src/app/features/student/student-placeholder/student-placeholder.component.ts` — покрывает INFRA-v9-04
- [ ] `src/app/features/headman/headman-placeholder/headman-placeholder.component.ts` — покрывает INFRA-v9-04

**Обновления существующих файлов:**
- [ ] `auth.service.spec.ts` — добавить STUDENT/headman/resolveDashboardFor тесты
- [ ] `auth.guard.spec.ts` — добавить STUDENT + headman сценарии (role.guard раздел)
- [ ] `login.component.spec.ts` — обновить redirect тесты (navigateByUrl вместо navigate), добавить STUDENT + headman

---

## Security Domain

Security enforcement включён.

### Applicable ASVS Categories

| ASVS Category | Applies | Standard Control |
|---------------|---------|-----------------|
| V2 Authentication | yes | Без изменений — `POST /api/auth/login` уже реализован |
| V3 Session Management | yes | Memory-only токены (signals) — соответствует D-06 PROJECT.md |
| V4 Access Control | yes | `authGuard` + `roleGuard` + новые guards (studentGuard, headmanGuard, guestGuard) |
| V5 Input Validation | no | Только маршрутизация в этой фазе, без новых форм |
| V6 Cryptography | no | JWT ключи не меняются |

### Known Threat Patterns

| Pattern | STRIDE | Standard Mitigation |
|---------|--------|---------------------|
| Privilege escalation (STUDENT → ADMIN) | Elevation of Privilege | `headmanGuard` и `roleGuard` — сервер-side validation в API Gateway остаётся неизменной |
| Session fixation через /login без guestGuard | Spoofing | `guestGuard` редиректит аутентифицированных пользователей |
| Доступ к `/headman/*` plain STUDENT | Elevation of Privilege | `headmanGuard` проверяет `user.role === 'STUDENT' && user.isHeadman` |

**Примечание:** Все Angular guards — клиентские проверки. Серверная авторизация обеспечивается API Gateway JWT filter (неизменен). Guards защищают UX, не данные.

---

## Assumptions Log

| # | Claim | Section | Risk if Wrong |
|---|-------|---------|---------------|
| A1 | `user.isHeadman()` у TEACHER/ADMIN возвращает `false` (не бросает NPE) | Q1/JWT payload | Если null — атрибут отсутствует в JWT для этих ролей; `!!payload.is_headman` покрывает оба случая |
| A2 | CI/CD (GitHub Actions) использует `npm run build` без явной передачи `--base-href` аргумента | Q3/Dockerfile | Если базовый href передаётся CLI аргументом в Dockerfile/CI — нужно обновить и его |
| A3 | `user.getGroupId()` возвращает `null` для ADMIN и TEACHER (нет группы) | Q1/JWT | Если поле `group_id` отсутствует в entity — `payload.group_id` = `undefined`, `?? null` обрабатывает корректно |

**A2 уточнение:** Dockerfile читается как `RUN npm run build` без аргументов [VERIFIED]. `package.json` scripts.build нужно проверить — вероятно `"build": "ng build"`. baseHref будет читаться из `angular.json`. Риск A2 низкий.

---

## Open Questions

1. **`package.json scripts.build` — есть ли флаг `--base-href`?**
   - Что известно: Dockerfile запускает `npm run build`. `angular.json` содержит `baseHref` в options.
   - Неясно: Не читали `package.json`. Если там `"build": "ng build --base-href /admin/"` — это дополнительная точка изменения.
   - Рекомендация: Исполнитель должен прочитать `frontends/web-panel/package.json` в начале работы.

2. **`proxy.conf.json` — есть ли `/admin/` в dev-proxy?**
   - Что известно: `angular.json` serve options ссылается на `proxy.conf.json`.
   - Неясно: Содержимое этого файла не читалось. Если там `/admin/` как prefix для API-запросов — нужно обновить для local dev.
   - Рекомендация: Исполнитель читает `frontends/web-panel/proxy.conf.json` в начале.

3. **`auth.interceptor.ts` — не зависит ли он от baseHref `/admin/`?**
   - Что известно: `auth.interceptor.spec.ts` — 6 тестов. Файл interceptor не читался в данном исследовании.
   - Неясно: Interceptor может иметь URL whitelist с `/admin/` prefix.
   - Рекомендация: Исполнитель читает `auth.interceptor.ts` и убеждается, что нет хардкода `/admin/`.

---

## Sources

### Primary (HIGH confidence)
- `services/auth-service/src/main/java/ru/rutcampustrack/auth/service/JwtService.java` — JWT payload shape (строки 94-96)
- `frontends/web-panel/src/app/core/auth/auth.service.ts` — AuthUser interface, currentUser computed, logout
- `frontends/web-panel/src/app/core/auth/auth.guard.ts` — guard pattern (CanActivateFn, inject, UrlTree)
- `frontends/web-panel/src/app/core/auth/role.guard.ts` — factory guard pattern, fallback hardcode
- `frontends/web-panel/src/app/app.routes.ts` — полная маршрутная структура
- `frontends/web-panel/src/app/features/login/login.component.ts` — строка 50, redirect логика
- `frontends/web-panel/src/app/layout/sidebar/sidebar.component.ts` — NavItem type, role filtering
- `frontends/web-panel/angular.json` — оба вхождения baseHref (строки 44, 62)
- `frontends/web-panel/nginx.conf` — SPA fallback, отсутствие `/admin/` хардкода
- `nginx/conf.d/default.conf` — prod nginx, все location блоки
- `frontends/web-panel/Dockerfile` — build команда, dist/browser path
- `frontends/web-panel/vitest.config.ts` — test configuration
- `frontends/web-panel/src/app/core/auth/auth.service.spec.ts` — test pattern, makeJwt helper
- `frontends/web-panel/src/app/core/auth/auth.guard.spec.ts` — TestBed.runInInjectionContext pattern
- `frontends/web-panel/src/app/features/login/login.component.spec.ts` — render/screen/userEvent pattern
- `frontends/web-panel/src/app/layout/sidebar/sidebar.component.spec.ts` — component render pattern

### Secondary (MEDIUM confidence)
- `.planning/codebase/TESTING.md` — Angular testing patterns (makeJwt, TestBed conventions)
- `.planning/codebase/CONVENTIONS.md` — file naming, Angular component structure
- `.planning/phases/50-basehref-migration-unified-login/50-CONTEXT.md` — D-01..D-10 decisions

### Tertiary (LOW confidence)
- Нет LOW confidence источников в данном исследовании.

---

## Metadata

**Confidence breakdown:**
- Standard stack: HIGH — все библиотеки верифицированы из реального кода
- Architecture patterns: HIGH — guards, routes, signals — всё из актуального кода
- JWT payload shape: HIGH — прямое чтение JwtService.java:89-101
- nginx конфиг: HIGH — прямое чтение обоих файлов
- Pitfalls: HIGH — выведены из конкретных несовместимостей между существующим кодом и требуемыми изменениями

**Research date:** 2026-04-09
**Valid until:** 2026-05-09 (стабильный стек, Angular 18 + nginx)
