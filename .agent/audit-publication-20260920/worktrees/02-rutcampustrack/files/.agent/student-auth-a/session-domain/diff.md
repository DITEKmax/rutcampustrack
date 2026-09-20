# Scoped diff

Owned additions:

- `services/auth-service/auth-app/src/main/java/ru/rutcampustrack/auth/session/model/` — 8 immutable domain model files;
- `services/auth-service/auth-app/src/main/java/ru/rutcampustrack/auth/session/port/` — 2 atomic port contracts;
- `services/auth-service/auth-app/src/main/java/ru/rutcampustrack/auth/session/` — `ActiveRolePolicy`, `PasswordPolicy`, `SessionLifecycleService`;
- `services/auth-service/auth-app/src/test/java/ru/rutcampustrack/auth/session/` — 3 focused unit test classes;
- `.agent/student-auth-a/session-domain/` — packet-adjacent evidence and status files, with the frozen `packet.md` unchanged.

No existing hot auth files, JPA/controller/DTO/admission code, migrations,
proto, Gateway/shared security, Redis, build/config/lockfiles, or generated
outputs were edited. Pre-existing purpose4 changes and the independent UI
worktree/files remain outside this scope and were preserved.
