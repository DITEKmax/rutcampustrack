# Diff record

- Product scope is exactly the frozen 46-path allowlist. The full path/hash record is [source-manifest.json](source-manifest.json).
- 44 paths are exact accepted source blobs from `27860f24eb380c782311bbe0cbb65c3d2c75d85d`.
- Composite 1: `services/academic-service/academic-app/build.gradle.kts`; target dependency `proto-google-common-protos:2.29.0` retained, source PostgreSQL compile/test classpath additions composed. Worktree blob: `c0fafd3106cffe7294b21a405a847ac1ae670903`.
- Composite 2: `services/academic-service/academic-app/src/main/java/ru/rutcampustrack/academic/grpc/AcademicGrpcServiceImpl.java`; target map/projection/Homework handlers retained and source assignment authority/constructors composed. Worktree blob: `05dc4d4748ef71df970a8b8a0d73dd7adf49e03e`.
- Evidence-only packet/report files remain untracked under `.agent/v2-integration-l5a`; they are outside the product allowlist.
