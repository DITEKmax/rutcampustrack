# Evidence

## Scope and criteria

The bounded S2 scope is the three production files and two exact focused test
files named in `resume-packet.md`. The implementation classifies raw and typed
attendance gRPC failures into the public BFF status/code contract, protects the
generic 500 message from server/local diagnostics, and keeps nullable request
detail properties present in JSON. Inherited transport paths and the two
accepted IT repairs remain untouched by this leaf.

## Static evidence

- Base revision: `d3c31acb8cce53791a4981e5858a37d44fdc9a0e`.
- Immutable import guard: exit `0`,
  `IMPORT_VERIFY_PASS productPaths=82 repairs=2`.
- `git diff --check`: exit `0`; the only output was expected line-ending
  warnings for imported working-tree files.
- Scoped production paths:
  - `services/mobile-bff/mobile-bff-api-contract/src/main/java/ru/rutcampustrack/mobilebff/contract/model/StudentApiModels.java`
  - `services/mobile-bff/mobile-bff-api-contract/src/main/java/ru/rutcampustrack/mobilebff/contract/model/StudentRequestApiModels.java`
  - `services/mobile-bff/mobile-bff-app/src/main/java/ru/rutcampustrack/mobilebff/grpc/MobileAttendanceClient.java`
- Scoped test paths:
  - `services/mobile-bff/mobile-bff-app/src/test/java/ru/rutcampustrack/mobilebff/grpc/MobileAttendanceClientErrorTest.java`
  - `services/mobile-bff/mobile-bff-app/src/test/java/ru/rutcampustrack/mobilebff/contract/StudentRequestDetailJsonTest.java`

SHA-256 of the five scoped files at release:

```text
6D4F1AAA69A2112358459041BD80C1917755476707E118E9EDE6F5DED0AF91C1  StudentApiModels.java
651B83E0E7B629F2750EE51231915B9A55D072E3F04FB31EDD6C0D4864198286  StudentRequestApiModels.java
450845E563AC7A37091A027DC5E717D0B19331327DA75E7473CF25261A05CDAC  MobileAttendanceClient.java
6471F65AB71698F27B2B7FDD2A9E79741EB7E558F4DF833B2613B9BBAFDB2C40  MobileAttendanceClientErrorTest.java
3F6027AE9E51D4EF743FEE0ACCF20B1CFC7AFCC32FE0B7B67765A9307B44D2C9  StudentRequestDetailJsonTest.java
```

## Focused runtime evidence

The single lease-authorized command and its exit code are recorded in
`checks.json` and `runtime.md`: exit `0`, BUILD SUCCESSFUL. XML reports contain
17/0/0/0 and 2/0/0/0 for tests/failures/errors/skipped respectively. The test
matrix covers raw internal/dependency/auth/scope statuses, typed unknown or
malformed details, invalid and valid cooldown timestamps, request 404/409,
local failure handling, and null/populated JSON fields.

## Limitations

This evidence does not claim a live HTTP-to-gRPC path, gateway, Nginx, Redis,
Mongo, Testcontainers, or external service behavior. Independent Sol review of
the stable diff remains root-owned.
