# Limitations and handoff

- Frontend dependencies were installed once with the existing lockfile via
  `npm ci`; no package or lockfile changes were made. npm reported four
  existing audit vulnerabilities and no audit fix was run.
- Backend affected Gradle batch completed; no broader suite or Docker runtime
  was run.
- No maps import, deploy, push, merge, reset, clean, or foreign-file rollback was
  performed in this package.
- The shared port is deliberately limited to pair deletion and availability;
  request-owned attachment retention remains in `StudentRequestService`.
