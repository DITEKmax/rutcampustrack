# Runtime evidence r4

Runtime evidence was supplied by root after the separate heavy runtime GO.
This leaf did not start Gradle, Docker, Testcontainers, a network call or a
product process. The commands below are copied exactly from the frozen
contract.

## Command 1 — historical PASS

~~~powershell
.\gradlew.bat :services:attendance-service:attendance-app:test --tests ru.rutcampustrack.attendance.grpc.StudentRequestGrpcMapperTest --tests ru.rutcampustrack.attendance.studentrequest.StudentRequestServiceAuthorizationTest --no-daemon --no-parallel --max-workers=1 --console=plain --rerun-tasks
~~~

Result: PASS, exit 0, historical run on 2026-09-09 near 19:03+03.
Fresh XML evidence is mapper1 (1) and auth18 (18), both with
failures/errors/skips 0.

## Command 2 — current PASS

~~~powershell
.\gradlew.bat :services:attendance-service:attendance-app:integrationTest --tests ru.rutcampustrack.attendance.studentrequest.StudentRequestDomainIT --no-daemon --no-parallel --max-workers=1 --console=plain --rerun-tasks
~~~

Result: PASS, exit 0, elapsed 3m48s. Fresh XML evidence is domain20
(20 tests; failures/errors/skips 0).

## Command 3 — current PASS

~~~powershell
.\gradlew.bat :services:mobile-bff:mobile-bff-app:test --tests ru.rutcampustrack.mobilebff.grpc.MobileAttendanceClientErrorTest --tests ru.rutcampustrack.mobilebff.contract.StudentRequestDetailJsonTest --tests ru.rutcampustrack.mobilebff.student.StudentRequestFacadeOptionsTest --tests ru.rutcampustrack.mobilebff.contract.StudentRequestOptionsJsonTest --no-daemon --no-parallel --max-workers=1 --console=plain --rerun-tasks
~~~

Result: PASS, exit 0, elapsed 1m56s. Fresh XML evidence is Detail3,
OptionsJson1, MobileAttendance23 and Facade1; all have failures/errors/skips 0.

The seven XML roots total 67 tests with zero failures, errors and skips. These
Gradle tests establish the producer-chain and regression evidence; they do not
claim live HTTP/gRPC behavior.
