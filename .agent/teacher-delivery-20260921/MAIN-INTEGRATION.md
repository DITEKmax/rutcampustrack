# MAIN integration — teacher reads

Scope: S3 additive integration of the accepted teacher read source into MAIN. The user-visible flow is an authenticated ACTIVE `TEACHER` session in PWA or TMA, followed by a generation-bound `TeacherApi.semester()` bootstrap and the teacher home/day, concrete lesson, group×subject×type semester journal, excuse detail, and attachment read surfaces.

## Contract and criteria

- `GetTeacherActiveSemester` is a dedicated additive Academic RPC. It requires signed internal claims, role `TEACHER`, an active teacher grant, and an active semester with both date bounds.
- BFF `GET /api/v1/teacher/semester` maps that response; the remaining teacher routes stay read-only and are scoped by the validated server identity. The Gateway forwards `/api/v1/teacher/**` to the existing Mobile BFF URI with the existing global JWT/internal admission chain and 600 req/min IP limiter.
- PWA and TMA create generation-bound teacher clients. Role activation clears prior owners and late responses; the owner renders teacher home and keeps detail screens on their own Back path.
- Home queries actual semester assignments and the selected day. Journal navigation carries group, subject, type, and semester context; the server discovers concrete semester lessons and applies per-lesson historical authorization. No student writes, personal student detail, or stats are exposed.

## Exact owned files

- `proto/teacher_reads.proto`
- `frontends/mobile-core/src/features/teacher/teacher-client.ts`, `teacher-screen.pcss`, `TeacherHomeScreen.vue`, `TeacherLessonScreen.vue`, `TeacherJournalScreen.vue`, `TeacherExcuseScreen.vue`
- `frontends/mobile-core/src/shared/components/TeacherFeatureOwner.vue`, `frontends/mobile-core/src/index.ts`
- `frontends/pwa-vue/src/App.vue`, `src/auth.ts`, `src/role-flow.ts`
- `frontends/tma-vue/src/App.vue`, `src/tma-session.ts`
- `services/api-gateway/src/main/resources/application.yml`
- Academic teacher identity/interceptor/service and focused test under `services/academic-service/academic-app/src/{main,test}/.../academic/grpc/`
- Attendance teacher identity/interceptor/service and focused test under `services/attendance-service/attendance-app/src/{main,test}/.../attendance/grpc/`, plus `ExcuseRepository.java` and `ReportService.java`
- BFF contract `TeacherApi.java`, `TeacherApiModels.java`; BFF teacher controller/facade; `MobileAcademicClient.java`, `MobileAttendanceClient.java`, `MobileIdentityFilter.java`; focused `TeacherReadFacadeTest.java`

Foreign MAIN changes and accepted Journal/Maps/HW/DNS/TLS work remain outside this inventory.

## Checks and evidence

- `npm run typecheck --workspaces --if-present` in `frontends`: exit 0.
- Targeted teacher/App ESLint with `--max-warnings=0`: exit 0.
- Gradle session `32383`, lease `TEACHER-MAIN-INTEGRATION-20260921`, exact Academic compile + BFF contract/app compile: exit 0 in 36s.
- Gateway `application.yml` PyYAML parse: exit 0.
- Owned diff whitespace check: exit 0 after the Gateway route addition and before the snapshot-only run.
- Existing BFF `OpenApiSnapshotIT` updated `docs/openapi/mobile-bff.json`; lease `TEACHER-BFF-OPENAPI-20260921`, session `1644`, exit 0 in 1m14s. The snapshot now reflects the current BFF contract, including TeacherApi paths. No full suite or unchanged source unit rerun was used.
- Runtime evidence is owned by root; this integration was not claimed as full-stack teacher runtime acceptance.

## Limitations

The final HTTP/gRPC role admission, historical data, fixture availability, and browser/TMA acceptance still require root's runtime against the integrated assembly. No teacher runtime acceptance was claimed by this integration.

