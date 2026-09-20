# L5A union implementation scope

- Risk: S3 bounded backend integration; no production/runtime mutation.
- Base: `426a15b6b42e816deaa3ca5c50437e0964aaf85e`.
- Source: accepted `27860f24eb380c782311bbe0cbb65c3d2c75d85d`.
- Worktree/branch: `v2-integration-l5a` / `codex/l5a-union-20260920`.
- Sole writer: this fresh Luna max leaf; root owns heavy checks and integration acceptance.
- Product allowlist: the exact 46 paths in frozen `path-inventory.json`; 44 exact source blobs plus the two composites `academic-app/build.gradle.kts` and `AcademicGrpcServiceImpl.java`.
- Preserved scope: target Maps, projection, signed Homework identity, cancellation-aware asset streaming, proto/generated inputs, migrations and target-only files.
- Excluded: frontend, L5B lifecycle/closure/binding, auth expansion, proto/migration changes, source/runtime/main writes, deployment, push and merge.
