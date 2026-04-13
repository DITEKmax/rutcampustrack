---
phase: 51
plan: 01
type: execute
wave: 1
depends_on: []
files_modified:
  - frontends/web-panel/package.json
  - frontends/web-panel/package-lock.json
  - frontends/web-panel/src/app/features/student/shared/student-schedule.types.ts
  - frontends/web-panel/src/app/features/student/shared/student-api.service.ts
  - frontends/web-panel/src/app/features/student/shared/student-api.service.spec.ts
  - frontends/web-panel/src/app/features/student/shared/subject-cache.service.ts
  - frontends/web-panel/src/app/features/student/shared/subject-cache.service.spec.ts
  - frontends/web-panel/src/app/features/student/shared/student-stomp.service.ts
  - frontends/web-panel/src/app/features/student/shared/student-stomp.service.spec.ts
  - frontends/web-panel/src/app/features/student/dashboard/student-dashboard.component.ts
  - frontends/web-panel/src/app/features/student/schedule/student-schedule.component.ts
  - frontends/web-panel/src/app/features/student/checkin/student-checkin.component.ts
  - frontends/web-panel/src/app/features/student/student-placeholder/student-placeholder.component.ts
  - frontends/web-panel/src/app/app.routes.ts
  - frontends/web-panel/src/app/layout/sidebar/sidebar.component.ts
  - frontends/web-panel/src/app/layout/sidebar/sidebar.component.html
  - frontends/web-panel/src/app/layout/sidebar/sidebar.component.spec.ts
autonomous: true
requirements: [STU-WEB-01, STU-WEB-02, STU-WEB-03]

must_haves:
  truths:
    - "@stomp/stompjs and sockjs-client are installed and importable in web-panel"
    - "Shared StudentApi service exposes getWeekLessons, getStudentStats, resolveThreshold, checkin methods targeting the existing backend REST paths (/api/schedule/groups/{id}/lessons, /api/attendance/reports/student/stats, /api/academic/thresholds/resolve, /api/attendance/checkin)"
    - "Shared SubjectCacheService resolves subject names by id via /api/academic/subjects/{id} with in-memory caching"
    - "Shared StudentStompService connects to /api/ws?token=... via SockJS, subscribes to /topic/group/{groupId}, and exposes an RxJS stream of attendance.marked envelopes"
    - "Three empty-shell components exist: StudentDashboardComponent, StudentScheduleComponent, StudentCheckinComponent — each importable and routable"
    - "app.routes.ts lazy-loads /student/dashboard, /student/schedule, and /student/checkin to the three real components (no placeholder references remain for these paths)"
    - "Sidebar renders three nav items for a STUDENT user: Главная, Расписание, Отметиться"
    - "Sidebar user-role chip renders 'Студент' when currentUser.role === 'STUDENT'"
    - "The existing 162 vitest tests continue to pass; the sidebar STUDENT test is updated to assert the new nav items are present"
  artifacts:
    - path: "frontends/web-panel/package.json"
      provides: "@stomp/stompjs, sockjs-client, @types/sockjs-client dependencies"
      contains: "@stomp/stompjs"
    - path: "frontends/web-panel/src/app/features/student/shared/student-schedule.types.ts"
      provides: "LessonResponse, LessonStatus, AttendanceStatus, SubjectResponse, StudentStatsResponse, SubjectStats, ResolvedThresholdResponse, CheckinRequest, CheckinResponse, AttendanceMarkedPayload types"
      exports: ["LessonResponse", "LessonStatus", "AttendanceStatus", "SubjectResponse", "StudentStatsResponse", "SubjectStats", "ResolvedThresholdResponse", "CheckinRequest", "CheckinResponse", "AttendanceMarkedPayload"]
    - path: "frontends/web-panel/src/app/features/student/shared/student-api.service.ts"
      provides: "Injectable HttpClient wrapper for schedule + attendance stats + thresholds + checkin"
      exports: ["StudentApiService"]
    - path: "frontends/web-panel/src/app/features/student/shared/subject-cache.service.ts"
      provides: "Caches subject names by id, returns Observable<string>"
      exports: ["SubjectCacheService"]
    - path: "frontends/web-panel/src/app/features/student/shared/student-stomp.service.ts"
      provides: "SockJS + STOMP client factory, subscribes to /topic/group/{groupId}, emits attendance.marked payloads"
      exports: ["StudentStompService"]
    - path: "frontends/web-panel/src/app/features/student/dashboard/student-dashboard.component.ts"
      provides: "Empty-shell StudentDashboardComponent — standalone, selector app-student-dashboard"
      exports: ["StudentDashboardComponent"]
    - path: "frontends/web-panel/src/app/features/student/schedule/student-schedule.component.ts"
      provides: "Empty-shell StudentScheduleComponent — standalone, selector app-student-schedule"
      exports: ["StudentScheduleComponent"]
    - path: "frontends/web-panel/src/app/features/student/checkin/student-checkin.component.ts"
      provides: "Empty-shell StudentCheckinComponent — standalone, selector app-student-checkin"
      exports: ["StudentCheckinComponent"]
  key_links:
    - from: "frontends/web-panel/src/app/app.routes.ts"
      to: "features/student/dashboard/student-dashboard.component.ts"
      via: "loadComponent"
      pattern: "student-dashboard\\.component.*StudentDashboardComponent"
    - from: "frontends/web-panel/src/app/app.routes.ts"
      to: "features/student/schedule/student-schedule.component.ts"
      via: "loadComponent"
      pattern: "student-schedule\\.component.*StudentScheduleComponent"
    - from: "frontends/web-panel/src/app/app.routes.ts"
      to: "features/student/checkin/student-checkin.component.ts"
      via: "loadComponent"
      pattern: "student-checkin\\.component.*StudentCheckinComponent"
    - from: "frontends/web-panel/src/app/layout/sidebar/sidebar.component.ts"
      to: "/student/dashboard, /student/schedule, /student/checkin"
      via: "primaryItems + allNavItems arrays with roles: ['STUDENT']"
      pattern: "route:\\s*'/student/(dashboard|schedule|checkin)'"
    - from: "frontends/web-panel/src/app/features/student/shared/student-stomp.service.ts"
      to: "/api/ws"
      via: "new SockJS with token query param"
      pattern: "new SockJS\\(.*\\?token="
---

<objective>
Phase 51 foundation: install the STOMP/SockJS toolchain, build the shared student domain services (REST API client, subject-name cache, STOMP subscription service), create empty-shell components for the three student pages, wire all three routes in `app.routes.ts` (replacing the Phase 50 placeholder), and extend the sidebar so STUDENT users see the "Главная / Расписание / Отметиться" navigation with the correct role chip label.

Purpose: Establish the single source of truth for all shared student-cabinet concerns (types, API paths, STOMP lifecycle, nav) so Plans 02-04 can each build exactly one page in parallel without touching the foundation or each other. No page logic yet — only plumbing.

Output: Working web-panel build where navigating to `/student/dashboard`, `/student/schedule`, and `/student/checkin` renders three empty shells (each shows nothing but the route-data title in the header), the sidebar shows three STUDENT nav items, and existing 162 vitest tests continue to pass plus the new shared-service specs.
</objective>

<execution_context>
@$HOME/.claude/get-shit-done/workflows/execute-plan.md
@$HOME/.claude/get-shit-done/templates/summary.md
</execution_context>

<context>
@CLAUDE.md
@.planning/ROADMAP.md
@.planning/REQUIREMENTS.md
@.planning/phases/51-student-web-cabinet-shell-schedule-check-in/51-UI-SPEC.md
@docs/design-decisions.md
@frontends/web-panel/package.json
@frontends/web-panel/src/app/app.routes.ts
@frontends/web-panel/src/app/core/auth/auth.service.ts
@frontends/web-panel/src/app/layout/sidebar/sidebar.component.ts
@frontends/web-panel/src/app/layout/sidebar/sidebar.component.html
@frontends/web-panel/src/app/layout/sidebar/sidebar.component.spec.ts
@frontends/web-panel/src/app/features/student/student-placeholder/student-placeholder.component.ts
@frontends/web-panel/src/app/features/teacher/journal/journal-api.service.ts
@frontends/web-panel/src/app/features/teacher/journal/types.ts
@frontends/pwa/src/features/schedule/types.ts
@frontends/pwa/src/features/schedule/api.ts
@frontends/pwa/src/features/checkin/StompProvider.tsx
@frontends/pwa/src/features/checkin/useStompCheckin.ts
@frontends/pwa/src/features/checkin/api.ts
@frontends/pwa/src/features/checkin/types.ts

<interfaces>
<!-- These are the exact types, contracts and tokens downstream plans consume. -->
<!-- Copy them directly from the sources below — do NOT re-derive from scratch. -->

From frontends/web-panel/src/app/core/auth/auth.service.ts (already shipped in Phase 50):

```typescript
export interface AuthUser {
  id: number;
  role: 'TEACHER' | 'ADMIN' | 'STUDENT';
  isHeadman: boolean;
  groupId: number | null;
}

@Injectable({ providedIn: 'root' })
export class AuthService {
  readonly accessToken = this._accessToken.asReadonly();   // Signal<string | null>
  readonly isAuthenticated = computed(() => ...);
  readonly currentUser = computed((): AuthUser | null => ...); // reads sub, role, is_headman, group_id
  resolveDashboardFor(user: AuthUser | null): string;
}
```

From frontends/pwa/src/features/schedule/types.ts (canonical DTO shapes returned by the backend — replicate these in student-schedule.types.ts):

```typescript
export interface LessonResponse {
  id: number
  scheduleItemId: number
  groupId: number
  subjectId: number
  teacherId: number
  date: string           // 'YYYY-MM-DD'
  status: 'PLANNED' | 'ACTIVE' | 'CLOSED' | 'CANCELLED'
  dayOfWeek: number      // 1=Mon..7=Sun
  lessonNumber: number
  startTime: string      // 'HH:mm:ss'
  endTime: string        // 'HH:mm:ss'
  weekType: 'NUMERATOR' | 'DENOMINATOR' | 'BOTH'
  room: string
  geoBlocked: boolean
  cancelReason: string | null
  createdAt: string
}
export type LessonStatus = LessonResponse['status']
export type AttendanceStatus = 'present' | 'absent' | 'excused' | 'free_attendance'
export interface SubjectResponse { id: number; name: string }
```

From frontends/pwa/src/features/checkin/types.ts (check-in DTOs — backend CheckinRequest record is {lat: Double, lng: Double}):

```typescript
export interface CheckinRequest { lat: number; lng: number }
export interface AttendanceMarkedPayload {
  lesson_id: number
  user_id: number
  group_id: number
  status: string
  marked_by: string
}
```

From services/attendance-service/attendance-api-contract (StudentStatsResponse shape — replicate as TS interface):

```java
public class StudentStatsResponse {
  private final List<SubjectStats> subjects;
  private final OverallStats overall;
}
public class SubjectStats {
  private final Long subjectId;
  private final String subjectName;
  private final int total;
  private final int attended;
  private final int absent;
  private final int excused;
  private final double percentage;  // 0..100
}
```

From services/academic-service ThresholdApi.java — GET /academic/thresholds/resolve?groupId&subjectId returns
`EntityModel<ResolvedThresholdResponse>` with an effective threshold percentage (replicate ResolvedThresholdResponse
as `{ groupId: number | null, subjectId: number | null, percentage: number, level: 'global' | 'group' | 'subject' }`).

Backend REST paths to call through the web-panel's HttpClient (baseURL is empty — use full `/api/…` paths, same as
JournalApiService at journal-api.service.ts:27):

- `GET  /api/schedule/groups/{groupId}/lessons?dateFrom=YYYY-MM-DD&dateTo=YYYY-MM-DD&size=100`
    → `PagedResponse<LessonResponse>` (`_embedded.lessonResponseList`)
- `GET  /api/academic/subjects/{subjectId}` → `SubjectResponse`
- `GET  /api/attendance/reports/student/stats` → `EntityModel<StudentStatsResponse>`
- `GET  /api/academic/thresholds/resolve?groupId=X&subjectId=Y` → `EntityModel<ResolvedThresholdResponse>`
    (calling with no params returns the effective global threshold)
- `POST /api/attendance/checkin` body `CheckinRequest` → 201 `EntityModel<CheckinResponse>`
- `WebSocket /api/ws?token=<access-token>` with SockJS transport, STOMP broker, subscribe to `/topic/group/{groupId}`

STOMP envelope shape (from useStompCheckin.ts):

```json
{ "type": "attendance.marked", "payload": { "lesson_id": 12, "user_id": 34, "group_id": 5, "status": "present", "marked_by": "self" } }
```
</interfaces>
</context>

<tasks>

<task type="auto" tdd="true">
  <name>Task 1: Install STOMP deps + build shared student domain services (types, API, subject cache, STOMP)</name>
  <files>frontends/web-panel/package.json, frontends/web-panel/package-lock.json, frontends/web-panel/src/app/features/student/shared/student-schedule.types.ts, frontends/web-panel/src/app/features/student/shared/student-api.service.ts, frontends/web-panel/src/app/features/student/shared/student-api.service.spec.ts, frontends/web-panel/src/app/features/student/shared/subject-cache.service.ts, frontends/web-panel/src/app/features/student/shared/subject-cache.service.spec.ts, frontends/web-panel/src/app/features/student/shared/student-stomp.service.ts, frontends/web-panel/src/app/features/student/shared/student-stomp.service.spec.ts</files>
  <read_first>
    - frontends/web-panel/package.json (current deps — confirm @stomp/stompjs and sockjs-client are NOT yet present)
    - frontends/web-panel/src/app/features/teacher/journal/journal-api.service.ts (canonical HttpClient pattern for this codebase — inject(HttpClient), Observable<T>, /api/ prefix, pipe(map(resp => ...)))
    - frontends/web-panel/src/app/features/teacher/journal/journal-api.service.spec.ts (canonical HttpTestingController + provideHttpClient + provideHttpClientTesting spec pattern)
    - frontends/web-panel/src/app/features/teacher/journal/types.ts (canonical PagedResponse<T> shape with _embedded: Record<string, T[]>)
    - frontends/pwa/src/features/schedule/types.ts (exact DTO field names to copy)
    - frontends/pwa/src/features/schedule/api.ts (exact query params for /schedule/groups/{id}/lessons)
    - frontends/pwa/src/features/checkin/api.ts + types.ts (checkin POST body and success/error shape)
    - frontends/pwa/src/features/checkin/StompProvider.tsx + useStompCheckin.ts (STOMP client lifecycle — new Client({ webSocketFactory, reconnectDelay, onConnect }), subscribe to /topic/group/{groupId}, parse envelope.type === 'attendance.marked')
    - services/attendance-service/attendance-api-contract/src/main/java/ru/rutcampustrack/attendance/contract/dto/report/StudentStatsResponse.java + SubjectStats.java
    - services/academic-service/academic-api-contract/src/main/java/ru/rutcampustrack/academic/contract/api/ThresholdApi.java (GET /academic/thresholds/resolve signature)
    - services/notification-service/notification-app/src/main/java/ru/rutcampustrack/notification/config/WebSocketConfig.java (confirms SockJS transport — web-panel MUST use sockjs-client, not native WebSocket)
  </read_first>
  <behavior>
    # student-api.service.spec.ts — MUST cover (using HttpTestingController pattern from journal-api.service.spec.ts):
    - `getWeekLessons(groupId=5, dateFrom='2026-04-06', dateTo='2026-04-11')` issues `GET /api/schedule/groups/5/lessons?dateFrom=2026-04-06&dateTo=2026-04-11&size=100` and returns `LessonResponse[]` by unwrapping `_embedded.lessonResponseList` (assert empty array when body omits _embedded)
    - `getStudentStats()` issues `GET /api/attendance/reports/student/stats` and returns `StudentStatsResponse` (assert parsed subjects array)
    - `resolveGlobalThreshold()` issues `GET /api/academic/thresholds/resolve` with NO query params and returns the `ResolvedThresholdResponse` (percentage number)
    - `checkin({lat: 55.1, lng: 37.2})` issues `POST /api/attendance/checkin` with body `{lat: 55.1, lng: 37.2}` and returns the response body on 201
    - afterEach httpMock.verify() passes (no outstanding requests)

    # subject-cache.service.spec.ts — MUST cover:
    - `getName(42)` issues `GET /api/academic/subjects/42` on first call and returns Observable emitting the name
    - A second `getName(42)` call does NOT hit the network — it returns the cached Observable/name (assert httpMock.expectNone or single request across two subscribes)
    - `getName(0)` or `getName(undefined)` returns an Observable of 'Предмет' fallback without network call

    # student-stomp.service.spec.ts — MUST cover (mock @stomp/stompjs Client and SockJS constructor using vi.mock):
    - `connect(groupId=5, () => 'fake-token')` calls new Client({ webSocketFactory: fn, reconnectDelay: 1000, onConnect: fn, ... })
    - the webSocketFactory returns a new SockJS instance with URL '/api/ws?token=fake-token'
    - on simulated onConnect, the service calls client.subscribe('/topic/group/5', callback)
    - when the subscribe callback receives `{ body: JSON.stringify({type: 'attendance.marked', payload: {...}}) }`, the service emits a next value on its observable with the payload
    - `disconnect()` calls client.deactivate()
    - multiple `connect()` calls for the same groupId do not create multiple clients (idempotent)
  </behavior>
  <action>
Step 1 — install NPM deps into the web-panel project (run from repo root):

```bash
cd frontends/web-panel && npm install --save @stomp/stompjs@^7.1.0 sockjs-client@^1.6.1 && npm install --save-dev @types/sockjs-client@^1.5.4
```

Versions chosen to match frontends/pwa/package.json (proven-working combination shipped in Phase 30). Do NOT pull `@stomp/rx-stomp`; we replicate the same stompjs + SockJS pattern the PWA uses so Plan 03 can wire it identically. Commit package.json + package-lock.json.

Step 2 — create `frontends/web-panel/src/app/features/student/shared/student-schedule.types.ts` with the following exports (field names MUST match backend DTOs verbatim):

```typescript
export type LessonStatus = 'PLANNED' | 'ACTIVE' | 'CLOSED' | 'CANCELLED';
export type WeekType = 'NUMERATOR' | 'DENOMINATOR' | 'BOTH';
export type AttendanceStatus = 'present' | 'absent' | 'excused' | 'free_attendance';

export interface LessonResponse {
  id: number;
  scheduleItemId: number;
  groupId: number;
  subjectId: number;
  teacherId: number;
  date: string;          // YYYY-MM-DD
  status: LessonStatus;
  dayOfWeek: number;     // 1=Mon..7=Sun
  lessonNumber: number;
  startTime: string;     // HH:mm:ss
  endTime: string;       // HH:mm:ss
  weekType: WeekType;
  room: string;
  geoBlocked: boolean;
  cancelReason: string | null;
  createdAt: string;
}

export interface SubjectResponse {
  id: number;
  name: string;
}

export interface SubjectStats {
  subjectId: number;
  subjectName: string;
  total: number;
  attended: number;
  absent: number;
  excused: number;
  percentage: number; // 0..100
}

export interface OverallStats {
  total: number;
  attended: number;
  absent: number;
  excused: number;
  percentage: number;
}

export interface StudentStatsResponse {
  subjects: SubjectStats[];
  overall: OverallStats;
}

export interface ResolvedThresholdResponse {
  groupId: number | null;
  subjectId: number | null;
  percentage: number;
  level: 'global' | 'group' | 'subject';
}

export interface CheckinRequest {
  lat: number;
  lng: number;
}

export interface CheckinResponse {
  status: AttendanceStatus;
  lessonId: number;
  timestamp: string;
}

export interface AttendanceMarkedPayload {
  lesson_id: number;
  user_id: number;
  group_id: number;
  status: string;
  marked_by: string;
}

export interface StompEnvelope<T> {
  type: string;
  payload: T;
}

export interface PagedResponse<T> {
  _embedded?: Record<string, T[]>;
  page?: { totalElements: number; totalPages: number; size: number; number: number };
}
```

Step 3 — create `frontends/web-panel/src/app/features/student/shared/student-api.service.ts`:

- `@Injectable({ providedIn: 'root' })` class `StudentApiService`
- `private readonly http = inject(HttpClient)`
- `getWeekLessons(groupId: number, dateFrom: string, dateTo: string): Observable<LessonResponse[]>` — constructs `HttpParams` with `dateFrom`, `dateTo`, `size=100`; calls `this.http.get<PagedResponse<LessonResponse>>('/api/schedule/groups/' + groupId + '/lessons', { params }).pipe(map(resp => resp._embedded?.['lessonResponseList'] ?? []))`
- `getStudentStats(): Observable<StudentStatsResponse>` — `this.http.get<StudentStatsResponse>('/api/attendance/reports/student/stats')`
- `resolveGlobalThreshold(): Observable<ResolvedThresholdResponse>` — `this.http.get<ResolvedThresholdResponse>('/api/academic/thresholds/resolve')` (no params → global)
- `resolveGroupThreshold(groupId: number): Observable<ResolvedThresholdResponse>` — same URL with `HttpParams().set('groupId', groupId)`
- `checkin(coords: CheckinRequest): Observable<CheckinResponse>` — `this.http.post<CheckinResponse>('/api/attendance/checkin', coords)`

No HATEOAS _links handling; Angular HttpClient transparently parses the JSON body. The authInterceptor already adds the Bearer header (auth.interceptor.ts is provided globally in app.config.ts line 16) — no manual token handling needed here.

Step 4 — create `frontends/web-panel/src/app/features/student/shared/subject-cache.service.ts`:

- `@Injectable({ providedIn: 'root' })` class `SubjectCacheService`
- `private readonly http = inject(HttpClient)`
- `private readonly cache = new Map<number, Observable<string>>()`
- `getName(subjectId: number | null | undefined): Observable<string>`:
  - if `!subjectId` return `of('Предмет')`
  - if `this.cache.has(subjectId)` return the cached Observable
  - otherwise `const req$ = this.http.get<SubjectResponse>('/api/academic/subjects/' + subjectId).pipe(map(r => r.name), shareReplay(1), catchError(() => of('Предмет')))`; cache it; return it

Use `shareReplay(1)` so multiple subscribers share the single HTTP call.

Step 5 — create `frontends/web-panel/src/app/features/student/shared/student-stomp.service.ts`:

- `@Injectable({ providedIn: 'root' })` class `StudentStompService`
- Uses `import { Client } from '@stomp/stompjs'` and `import SockJS from 'sockjs-client'`
- Private state: `client: Client | null = null`, `currentGroupId: number | null = null`, `markedSubject = new Subject<AttendanceMarkedPayload>()`
- Public `readonly marked$: Observable<AttendanceMarkedPayload> = this.markedSubject.asObservable()`
- Public `connect(groupId: number, getAccessToken: () => string | null): void`:
  - if `this.currentGroupId === groupId && this.client?.active` return (idempotent)
  - if `this.client` is not null → `this.disconnect()`
  - `this.currentGroupId = groupId`
  - `this.client = new Client({ webSocketFactory: () => new SockJS('/api/ws?token=' + (getAccessToken() ?? '')), reconnectDelay: 1000, onConnect: () => { this.client!.subscribe('/topic/group/' + groupId, (msg) => { try { const env = JSON.parse(msg.body) as StompEnvelope<AttendanceMarkedPayload>; if (env.type === 'attendance.marked') this.markedSubject.next(env.payload); } catch { /* ignore */ } }); }, onStompError: (frame) => { console.error('STOMP error:', frame.headers['message']); } })`
  - `this.client.activate()`
- Public `disconnect(): void` — if `this.client` then `this.client.deactivate(); this.client = null; this.currentGroupId = null;`
- SECURITY: the access token arrives as `?token=` query param on the upgrade request — do NOT log the URL anywhere, do NOT write it to localStorage, do NOT echo it in error messages. Per threat T-51-01 document inline: the token is scoped to the upgrade handshake; the backend's JwtHandshakeInterceptor validates it and immediately discards it per WebSocketConfig.java:44. The log line in `onStompError` MUST NOT include the client URL or headers.

Step 6 — write specs:

- `student-api.service.spec.ts`: use `TestBed.configureTestingModule({ providers: [provideHttpClient(), provideHttpClientTesting()] })`; inject `StudentApiService` and `HttpTestingController`; assert the request URL, method, and params for each method per the behavior block above; use `req.flush(mockPayload)` to return bodies; cover the empty `_embedded` case for `getWeekLessons`.
- `subject-cache.service.spec.ts`: assert single network call across two subscribers to `getName(42)`; assert fallback path for `getName(0)` (no network, emits 'Предмет').
- `student-stomp.service.spec.ts`: `vi.mock('@stomp/stompjs', () => ({ Client: vi.fn().mockImplementation((config) => ({ activate: vi.fn(), deactivate: vi.fn(), subscribe: vi.fn((dest, cb) => { /* store cb for test driver */ }), active: false })) }))` and `vi.mock('sockjs-client', () => ({ default: vi.fn(() => ({})) }))`; drive the mock's `onConnect` from the test; assert the URL passed to SockJS matches `/api/ws?token=fake-token`; simulate an incoming message by calling the stored subscribe callback with `{ body: JSON.stringify({type: 'attendance.marked', payload: {...}}) }`; assert the service's `marked$` observable emits the payload; assert `disconnect()` calls `deactivate()`.

Run the new spec files via `npm test` and fix any failures before moving on.
  </action>
  <verify>
    <automated>cd frontends/web-panel && npm test -- --run src/app/features/student/shared 2>&1 | tail -40</automated>
  </verify>
  <acceptance_criteria>
    - grep `@stomp/stompjs` in frontends/web-panel/package.json matches at least one "dependencies" entry
    - grep `sockjs-client` in frontends/web-panel/package.json matches at least one "dependencies" entry
    - grep `@types/sockjs-client` in frontends/web-panel/package.json matches at least one "devDependencies" entry
    - frontends/web-panel/src/app/features/student/shared/student-schedule.types.ts exists and contains all of: `export type LessonStatus`, `export interface LessonResponse`, `export interface SubjectStats`, `export interface StudentStatsResponse`, `export interface ResolvedThresholdResponse`, `export interface CheckinRequest`, `export interface AttendanceMarkedPayload`, `export interface StompEnvelope`, `export interface PagedResponse`
    - frontends/web-panel/src/app/features/student/shared/student-api.service.ts contains `export class StudentApiService` AND the string `/api/schedule/groups/` AND `/api/attendance/reports/student/stats` AND `/api/academic/thresholds/resolve` AND `/api/attendance/checkin`
    - frontends/web-panel/src/app/features/student/shared/subject-cache.service.ts contains `export class SubjectCacheService` AND `shareReplay` AND the string `/api/academic/subjects/`
    - frontends/web-panel/src/app/features/student/shared/student-stomp.service.ts contains `export class StudentStompService` AND `import { Client } from '@stomp/stompjs'` AND `import SockJS from 'sockjs-client'` AND the literal `'/topic/group/'` AND the literal `'/api/ws?token='`
    - No file in the student/shared directory logs `getAccessToken`, `accessToken`, or the connection URL (grep for `console.log.*token|console.log.*ws\?token` returns nothing)
    - `cd frontends/web-panel && npm test -- --run src/app/features/student/shared` exits 0 with all new specs green
    - `cd frontends/web-panel && npm test` exits 0 (full regression — all prior 162 tests still pass; the sidebar spec is still asserting the OLD "no items for STUDENT" behavior at this task boundary because Task 3 updates it — so this task's full-suite check runs AFTER Task 3; if running Task 1 in isolation, scope to the student/shared folder only)
  </acceptance_criteria>
  <done>
Student domain foundation is installed and tested: @stomp/stompjs + sockjs-client + types are in package.json; StudentApiService, SubjectCacheService, StudentStompService exist with unit tests that pass; no secrets are logged; both dev and test tsc compile clean.
  </done>
</task>

<task type="auto">
  <name>Task 2: Create empty-shell components + register routes + delete placeholder</name>
  <files>frontends/web-panel/src/app/features/student/dashboard/student-dashboard.component.ts, frontends/web-panel/src/app/features/student/schedule/student-schedule.component.ts, frontends/web-panel/src/app/features/student/checkin/student-checkin.component.ts, frontends/web-panel/src/app/features/student/student-placeholder/student-placeholder.component.ts, frontends/web-panel/src/app/app.routes.ts</files>
  <read_first>
    - frontends/web-panel/src/app/app.routes.ts (the whole file — existing admin/teacher route data pattern with title + eyebrow; note the current student block at lines 89-113 referencing student-placeholder)
    - frontends/web-panel/src/app/features/student/student-placeholder/student-placeholder.component.ts (to be deleted — confirm no other file imports it before deleting)
    - frontends/web-panel/src/app/features/teacher/dashboard/teacher-dashboard.component.ts (canonical empty-dashboard shell pattern — standalone, changeDetection OnPush, selector app-teacher-dashboard)
    - .planning/phases/51-student-web-cabinet-shell-schedule-check-in/51-UI-SPEC.md section "Layout Shell Integration" (lines 335-362 — route data table)
  </read_first>
  <action>
Step 1 — verify nothing else imports the placeholder:

```bash
grep -rn "student-placeholder" frontends/web-panel/src --include="*.ts" --include="*.html"
```

The only hits should be inside `app.routes.ts` (to be rewritten below) and the placeholder file itself. If any other file imports `StudentPlaceholderComponent`, stop and surface the finding.

Step 2 — create `frontends/web-panel/src/app/features/student/dashboard/student-dashboard.component.ts` as an empty shell. Plan 04 will replace the template and logic. Content:

```typescript
import { ChangeDetectionStrategy, Component } from '@angular/core';

/**
 * Student dashboard (Phase 51 — Plan 04 fills this).
 *
 * Empty shell committed in Plan 01 so the route resolves immediately and
 * the other student page plans can run in parallel without colliding on
 * this file. Selector matches the naming convention of other dashboards.
 */
@Component({
  selector: 'app-student-dashboard',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `<section class="dashboard page-stack" data-testid="student-dashboard-shell"></section>`,
  styles: [`:host { display: block; }`],
})
export class StudentDashboardComponent {}
```

Step 3 — create `frontends/web-panel/src/app/features/student/schedule/student-schedule.component.ts` with the same pattern:

```typescript
import { ChangeDetectionStrategy, Component } from '@angular/core';

/**
 * Student schedule page (Phase 51 — Plan 02 fills this).
 */
@Component({
  selector: 'app-student-schedule',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `<section class="page-stack" data-testid="student-schedule-shell"></section>`,
  styles: [`:host { display: block; }`],
})
export class StudentScheduleComponent {}
```

Step 4 — create `frontends/web-panel/src/app/features/student/checkin/student-checkin.component.ts`:

```typescript
import { ChangeDetectionStrategy, Component } from '@angular/core';

/**
 * Student check-in page (Phase 51 — Plan 03 fills this).
 */
@Component({
  selector: 'app-student-checkin',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `<section class="page-stack" data-testid="student-checkin-shell"></section>`,
  styles: [`:host { display: block; }`],
})
export class StudentCheckinComponent {}
```

Step 5 — rewrite the `/student` block in `frontends/web-panel/src/app/app.routes.ts` (lines 89-113). Replace it with:

```typescript
      // Student routes — Phase 51 (STU-WEB-01..03)
      {
        path: 'student',
        canActivate: [studentGuard],
        data: { eyebrow: 'Студент' },
        children: [
          {
            path: 'dashboard',
            loadComponent: () =>
              import('./features/student/dashboard/student-dashboard.component').then(
                m => m.StudentDashboardComponent,
              ),
            data: { title: 'Главная', eyebrow: 'Студент' },
          },
          {
            path: 'schedule',
            loadComponent: () =>
              import('./features/student/schedule/student-schedule.component').then(
                m => m.StudentScheduleComponent,
              ),
            data: { title: 'Расписание', eyebrow: 'Студент' },
          },
          {
            path: 'checkin',
            loadComponent: () =>
              import('./features/student/checkin/student-checkin.component').then(
                m => m.StudentCheckinComponent,
              ),
            data: { title: 'Отметиться', eyebrow: 'Студент' },
          },
          { path: '', redirectTo: 'dashboard', pathMatch: 'full' },
        ],
      },
```

Note the exact route-data values: `title` changes from the Phase 50 placeholder `'Личный кабинет'` to `'Главная'` per UI-SPEC lines 358-362. The schedule title stays `'Расписание'`. The checkin route is NEW — it did not exist in Phase 50 placeholder routes.

Step 6 — delete the placeholder file:

```bash
rm frontends/web-panel/src/app/features/student/student-placeholder/student-placeholder.component.ts && rmdir frontends/web-panel/src/app/features/student/student-placeholder 2>/dev/null || true
```

Do NOT remove the whole `features/student/` directory — the new subdirectories (dashboard/, schedule/, checkin/, shared/) live inside it.

Step 7 — run a production build to confirm the route graph compiles:

```bash
cd frontends/web-panel && npm run build 2>&1 | tail -30
```

Must exit 0 with no TypeScript errors.
  </action>
  <verify>
    <automated>cd frontends/web-panel && npm run build 2>&1 | tail -30</automated>
  </verify>
  <acceptance_criteria>
    - frontends/web-panel/src/app/features/student/dashboard/student-dashboard.component.ts exists and contains `export class StudentDashboardComponent` AND `selector: 'app-student-dashboard'` AND `standalone: true`
    - frontends/web-panel/src/app/features/student/schedule/student-schedule.component.ts exists and contains `export class StudentScheduleComponent` AND `selector: 'app-student-schedule'` AND `standalone: true`
    - frontends/web-panel/src/app/features/student/checkin/student-checkin.component.ts exists and contains `export class StudentCheckinComponent` AND `selector: 'app-student-checkin'` AND `standalone: true`
    - frontends/web-panel/src/app/features/student/student-placeholder/student-placeholder.component.ts does NOT exist (file deleted)
    - grep `student-placeholder` in frontends/web-panel/src returns zero matches
    - frontends/web-panel/src/app/app.routes.ts contains the literal strings `'./features/student/dashboard/student-dashboard.component'`, `'./features/student/schedule/student-schedule.component'`, `'./features/student/checkin/student-checkin.component'`
    - frontends/web-panel/src/app/app.routes.ts contains `title: 'Главная'` within the student dashboard route entry
    - frontends/web-panel/src/app/app.routes.ts contains `title: 'Отметиться'` within a new student checkin route entry
    - frontends/web-panel/src/app/app.routes.ts contains `path: 'checkin'` nested under the student path
    - `cd frontends/web-panel && npm run build` exits 0 with no TypeScript errors
  </acceptance_criteria>
  <done>
Route graph compiles against three real empty-shell components. Placeholder is gone. Navigating to `/student/dashboard`, `/student/schedule`, and `/student/checkin` at runtime renders empty `<section>` placeholders inside the existing shell, header eyebrow reads "Студент", header title reads "Главная" / "Расписание" / "Отметиться" respectively.
  </done>
</task>

<task type="auto" tdd="true">
  <name>Task 3: Extend sidebar with STUDENT nav items and update the sidebar role-label + test</name>
  <files>frontends/web-panel/src/app/layout/sidebar/sidebar.component.ts, frontends/web-panel/src/app/layout/sidebar/sidebar.component.html, frontends/web-panel/src/app/layout/sidebar/sidebar.component.spec.ts</files>
  <read_first>
    - frontends/web-panel/src/app/layout/sidebar/sidebar.component.ts (the whole file — especially primaryItems, allNavItems, sectionLabel)
    - frontends/web-panel/src/app/layout/sidebar/sidebar.component.html (the whole file — especially line 79 role chip switch)
    - frontends/web-panel/src/app/layout/sidebar/sidebar.component.spec.ts (the whole file — especially the test at lines 94-114 "renders no nav items for plain STUDENT role (placeholder phase — D-06)" which MUST be inverted in this phase)
    - .planning/phases/51-student-web-cabinet-shell-schedule-check-in/51-UI-SPEC.md lines 336-353 (exact sidebar entries and role-label requirement)
    - docs/design-decisions.md §1 (icon weight rules: desktop uses `regular` / `fill` — sidebar already follows this via Phosphor classes)
  </read_first>
  <behavior>
    # Updated sidebar.component.spec.ts MUST cover:
    - "renders filtered nav items for STUDENT role" — given `AuthUser { id: 3, role: 'STUDENT', isHeadman: false, groupId: 5 }`, renders `Главная`, `Расписание`, `Отметиться` AND does NOT render `Журнал посещаемости`, `Пользователи`, `Группы`, `Семестры`, `Статистика`
    - The TEACHER and ADMIN tests at lines 51-92 continue to pass unchanged (STUDENT items must not leak into their render output — i.e. the teacher render does NOT show 'Главная' with route /student/dashboard)
    - Either add a new test OR assert inline: user-role chip shows `Студент` when STUDENT user is active (query by text 'Студент' inside the `.sidebar__user-meta` container; use the existing role check helper style)
  </behavior>
  <action>
Step 1 — edit `frontends/web-panel/src/app/layout/sidebar/sidebar.component.ts` in two places:

(a) Add a new entry to `primaryItems` array (after the admin entry) so the STUDENT primary dashboard link appears first:

```typescript
    {
      label: 'Главная',
      icon: 'ph-squares-four',
      route: '/student/dashboard',
      roles: ['STUDENT'],
    },
```

(b) Add two new entries to `allNavItems` (after the admin entries) for the secondary student nav. Do NOT touch the existing TEACHER/ADMIN entries:

```typescript
    // Student items
    {
      label: 'Расписание',
      icon: 'ph-calendar-dots',
      route: '/student/schedule',
      roles: ['STUDENT'],
    },
    {
      label: 'Отметиться',
      icon: 'ph-map-pin',
      route: '/student/checkin',
      roles: ['STUDENT'],
    },
```

No other changes to the component class. `sectionLabel()` already returns `'Учёба'` for STUDENT (line 128) and `filteredNavItems` / `filteredPrimaryItems` already filter by `item.roles.includes(user.role)` — both work unchanged because `NavItem['roles']` is already typed `('TEACHER' | 'ADMIN' | 'STUDENT')[]` (line 27).

Step 2 — edit `frontends/web-panel/src/app/layout/sidebar/sidebar.component.html`. Replace line 79 (the hard-coded role label) from:

```html
              {{ user.role === 'ADMIN' ? 'Администратор' : 'Преподаватель' }}
```

to:

```html
              @switch (user.role) {
                @case ('ADMIN') { Администратор }
                @case ('TEACHER') { Преподаватель }
                @case ('STUDENT') { Студент }
              }
```

This uses the existing Angular 19 control-flow `@switch` block (the file already uses `@for`/`@if`, so the syntax is consistent — see lines 16, 70, 83).

Step 3 — update `frontends/web-panel/src/app/layout/sidebar/sidebar.component.spec.ts`. Replace the test named `"renders no nav items for plain STUDENT role (placeholder phase — D-06)"` (lines 94-114) with a new test that asserts the Phase 51 behavior. The ENTIRE replacement block:

```typescript
  it('renders filtered nav items for plain STUDENT role (Phase 51 — STU-WEB-01..03)', async () => {
    const studentUser: AuthUser = { id: 3, role: 'STUDENT', isHeadman: false, groupId: 5 };
    const authServiceMock = makeAuthServiceMock(studentUser);

    await render(SidebarComponent, {
      providers: [
        provideRouter([]),
        provideNoopAnimations(),
        { provide: AuthService, useValue: authServiceMock },
        { provide: AuthApi, useValue: mockAuthApi },
        { provide: ThemeService, useValue: mockThemeService },
      ],
    });

    // STUDENT should see three nav items (Главная primary, Расписание + Отметиться secondary)
    expect(screen.getByText('Главная')).toBeTruthy();
    expect(screen.getByText('Расписание')).toBeTruthy();
    expect(screen.getByText('Отметиться')).toBeTruthy();
    // Role chip reads "Студент"
    expect(screen.getByText('Студент')).toBeTruthy();
    // Teacher/Admin items must NOT leak into STUDENT view
    expect(screen.queryByText('Журнал посещаемости')).toBeNull();
    expect(screen.queryByText('Пользователи')).toBeNull();
    expect(screen.queryByText('Группы')).toBeNull();
    expect(screen.queryByText('Семестры')).toBeNull();
  });
```

Step 4 — check that the TEACHER and ADMIN tests (lines 51-92 in the original file) do NOT accidentally break. They assert absence of their counter-role labels but do NOT assert absence of the new STUDENT labels. If they happen to now conflict (e.g. if `Статистика` is ever reused for a STUDENT nav label — it is not), update them. As written they should remain green.

Step 5 — run the full vitest suite and confirm exit 0:

```bash
cd frontends/web-panel && npm test 2>&1 | tail -20
```

All prior tests (162 baseline) + the new 5 shared-service specs from Task 1 + the updated sidebar spec should all pass. Target: green across the board.
  </action>
  <verify>
    <automated>cd frontends/web-panel && npm test 2>&1 | tail -20</automated>
  </verify>
  <acceptance_criteria>
    - grep `label: 'Главная'` in sidebar.component.ts matches at least one line
    - grep `label: 'Расписание'` in sidebar.component.ts matches at least one line
    - grep `label: 'Отметиться'` in sidebar.component.ts matches at least one line
    - grep `'/student/dashboard'` in sidebar.component.ts matches at least one line
    - grep `'/student/schedule'` in sidebar.component.ts matches at least one line
    - grep `'/student/checkin'` in sidebar.component.ts matches at least one line
    - grep `ph-calendar-dots` in sidebar.component.ts matches at least one line
    - grep `ph-map-pin` in sidebar.component.ts matches at least one line
    - sidebar.component.html contains the literal string `Студент` AND does NOT contain the string `? 'Администратор' : 'Преподаватель'` (the old hardcoded ternary)
    - sidebar.component.spec.ts contains the literal string `renders filtered nav items for plain STUDENT role` AND `expect(screen.getByText('Главная'))`
    - `cd frontends/web-panel && npm test` exits 0 with zero failing tests
    - `cd frontends/web-panel && npm run build` exits 0
  </acceptance_criteria>
  <done>
Sidebar renders three STUDENT entries (Главная / Расписание / Отметиться) with Phosphor icons `ph-squares-four` / `ph-calendar-dots` / `ph-map-pin`, the section label reads "Учёба" (already shipped in Phase 50), the footer role chip reads "Студент" for STUDENT users, and the full vitest suite is green.
  </done>
</task>

</tasks>

<threat_model>
## Trust Boundaries

| Boundary | Description |
|----------|-------------|
| browser → `/api/*` REST | JWT bearer token added by authInterceptor; backend validates via API Gateway |
| browser → `/api/ws` STOMP upgrade | Access token passed as `?token=` query param; notification-service JwtHandshakeInterceptor validates at upgrade |
| external deps → web-panel bundle | npm registry (@stomp/stompjs, sockjs-client) — supply-chain trust |

## STRIDE Threat Register

| Threat ID | Category | Component | Disposition | Mitigation Plan |
|-----------|----------|-----------|-------------|-----------------|
| T-51-01 | Information Disclosure | student-stomp.service.ts | mitigate | Access token is passed as `?token=` on the WebSocket upgrade URL. Service MUST NOT log the full URL, the accessToken value, or the SockJS constructor arguments. `onStompError` logs `frame.headers['message']` only — never the client URL. No localStorage writes of the token. |
| T-51-02 | Tampering | student-api.service.ts (`/api/attendance/checkin`) | mitigate | CheckinRequest body is `{lat, lng}` only. Server resolves the lesson_id from the student's group + current time; the client cannot select which lesson to mark. Server-side RFC 7807 error responses are mapped by Plan 03; no client-side authorization logic. |
| T-51-03 | Elevation of Privilege | app.routes.ts student subtree | mitigate | All three student routes are registered under a parent `path: 'student'` guarded by `studentGuard` (already shipped in Phase 50). studentGuard.ts:12 allows only `role === 'STUDENT'` which includes headman per D-06. Role-guard bypass is prevented by the parent route canActivate; child routes inherit it. |
| T-51-04 | Spoofing (supply chain) | @stomp/stompjs, sockjs-client, @types/sockjs-client | accept | Standard npm packages with large user bases; versions pinned to the exact releases already shipped in frontends/pwa (Phase 30) and passing 63 PWA tests. No private registry / no vetting gate triggered per UI-SPEC Registry Safety section. |
| T-51-05 | Information Disclosure | browser console logs | mitigate | Neither student-api.service nor student-stomp.service calls `console.log` with token, URL, coords, user id, or group id. `console.error` is allowed only for STOMP frame error messages and HttpClient error responses already stripped by the interceptor. |
| T-51-06 | Repudiation | checkin flow | accept | Server already logs every checkin attempt (Phase 17) with timestamp, user id, lat/lng, and IP. Client does not need a local audit trail. |
| T-51-07 | Denial of Service | STOMP reconnection | mitigate | `reconnectDelay: 1000` (matches PWA). No exponential backoff escalation path that could amplify load. Connection is created once per mount in Plan 03 and torn down in ngOnDestroy. |
</threat_model>

<verification>
Automated gates that must pass before this plan is considered complete:

1. `cd frontends/web-panel && npm test -- --run src/app/features/student/shared` exits 0 (Task 1 unit tests)
2. `cd frontends/web-panel && npm run build` exits 0 (Task 2 route graph compiles)
3. `cd frontends/web-panel && npm test` exits 0 (Task 3 full regression green — 162 prior + 5 new shared-service + updated sidebar)
4. No file under `frontends/web-panel/src/app/features/student` logs the access token or the STOMP upgrade URL: `grep -rnE "console\.log.*(token|ws\?|accessToken)" frontends/web-panel/src/app/features/student` returns zero matches
5. No orphan import of the deleted placeholder component: `grep -rn "student-placeholder" frontends/web-panel/src` returns zero matches
</verification>

<success_criteria>
- STOMP toolchain installed (@stomp/stompjs, sockjs-client, @types/sockjs-client) matching PWA versions
- Shared student domain layer in place (types, API service, subject cache, STOMP service) with full unit-test coverage
- Three empty-shell components exist at `features/student/{dashboard,schedule,checkin}/` and compile clean
- `app.routes.ts` lazy-loads the three real components with the correct `title` + `eyebrow` route data (per UI-SPEC Layout Shell Integration)
- Phase 50 student-placeholder is fully removed
- Sidebar renders three STUDENT nav items with Phosphor icons and the correct `Студент` role chip
- Full web-panel vitest suite green; prod build green
- No secrets or tokens leaked via console or localStorage
</success_criteria>

<output>
After completion, create `.planning/phases/51-student-web-cabinet-shell-schedule-check-in/51-01-SUMMARY.md` capturing: installed deps (with versions), shared service API surface, list of files created/modified/deleted, test counts before/after, any deviations from this plan.
</output>
