# OpenAPI export command-parsing failure 94003

Status: `PRESERVED_SEPARATELY`.

The first requested export command was run exactly as supplied, but Windows
Gradle argument transport split the unquoted project property. The command
was:

`.\gradlew.bat :services:auth-service:auth-app:integrationTest --tests ru.rutcampustrack.auth.integration.OpenApiSnapshotIT -Popenapi.snapshot.update=true --no-daemon --no-parallel --max-workers=1 --console=plain`

- Session: `94003`
- Exit code: `1`
- Result: `BUILD FAILED in 15s`
- Exact Gradle error: `Task '.snapshot.update=true' not found in root project 'rutcampustrack' and its subprojects.`

No test task ran and no JSON or JUnit artifact was produced by this attempt.
The pre-export `docs/openapi/auth.json` was `37,654` bytes with SHA-256
`5B97E363B891AE0A4B9B4148992E05FD474527C3896D2998780AF60818C1D35E`.

Root authorized the routine transport correction: retry the same export with
`"-Popenapi.snapshot.update=true"` as one quoted token. The successful retry
and the subsequent compare are recorded in `../../openapi-runtime.md`.
