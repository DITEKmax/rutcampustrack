# Fresh ADMIN role home — one actual after case

Result: PASS for fresh native role selection and guarded canonical navigation. Source revision `0421d344c9697d58efc8f039b74c5bdf594c6eb4` was checked immediately before and after the case. ROOT frontend tracked diff is empty; unrelated documentation WIP is outside product source. Actual App SHA256 `b257165eec7b9adf4c0348eab0df7c6c62ca5005b9363dcd2095f5fa67217446`. This includes original229 and correction756, independently reviewed PASS, integrated as90e69c419/0421. No author source changes during validation.

ROOT explicitly authorized one bounded fresh guest UI case on isolated http://127.0.0.1:14250 and its existing strict TLS backend18443. Existing Vite process/imports were used without restart. Native public synthetic admin/password login, then visible ADMIN role selection; no direct role API call, injected token, route injection, fake STOMP, domain action or navigation bypass. Registered `admin-dashboard` path `/admin/dashboard` and existing permission-safe chooser were read before running; the test did not click a feature link to bypass role-home admission.

Native command: `node /tmp/rct-fresh-role-home.cjs after`, managed additional network permission enabled for Browser IPC and loopback, exit0. This was exactly one after login; no repeat. The previous before observer path error was corrected to actual `/api/academic/admin-access/me` before invocation.

Observed sequence:

- Login POST200, current-session GET200; initial post-login URL `/`.
- Native active-role PUT200, role label `Администратор`.
- Two actual UI `GET /api/academic/admin-access/me` responses200 (canonical admission and page authorization).
- Final URL `/admin/dashboard`, heading `Обзор системы`, no `Страница недоступна` element.
- Non-auth domain writes0, page errors0.
- Normal own UI logout POST204, guest current-session GET401, context and browser closed. No cleanup error; sole browser lease released immediately.

The screenshot was captured after successful admission with empty login fields no longer present, before logout. It contains synthetic aggregate dashboard data only, no password/token/header/session capability. It was visually inspected. The dashboard shows truthful temporarily unavailable metrics; this bounded case does NOT establish dashboard metric/source acceptance, other ADMIN operations, different access modes/roles, focus preservation in actual DOM during authority churn, full auth suite or release readiness. The memory authority-race probe remains source/harness evidence rather than backend/SUT churn proof.

Evidence files:

- `after.json`: SHA256 `e868b90133a464b2ff9238e0e48cfbe60e16f235a0b334b6cc427bc38058c4cf`.
- `after.png`: unedited1440×1038, SHA256 `8021bf3ffe36b0636424b5b1204fe7cb03ec4239f3be9cacf09d7251eb322c1e`.

Both reside in `/workspace/rct-pr3-runtime/fresh-role-home/`. Earlier before.json/before.png, original229 review CHANGES REQUIRED, correction756 report and independent narrow PASS remain separate historical evidence. No main14210, backend/container/JVM, API/domain/grant/self credential write, CI/deploy/push or source mutation occurred. Auth login/role/logout are only the explicitly authorized own-session lifecycle actions. The separate native ADMIN harness still requires its explicit revision repin/GO; this case does not run or accept it.
