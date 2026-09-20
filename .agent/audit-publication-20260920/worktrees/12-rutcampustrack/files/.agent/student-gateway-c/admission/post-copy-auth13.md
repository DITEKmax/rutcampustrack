# Auth13 destination verification

- Date: 2026-09-10 (Europe/Moscow)
- Revision: working tree on `8002b9ea4356b10779c5bb9a6d99746d32d78ae2`
- Command: `Get-FileHash -Algorithm SHA256 -LiteralPath <13 accepted auth paths>`
- Exit code: `0`
- Result: every destination hash equals the accepted B0 manifest and the
  source hashes recorded in `pre-copy.md`; no Auth13 path was edited after the
  guarded copy.

| Path | SHA-256 |
| --- | --- |
| `services/auth-service/auth-api-contract/src/main/java/ru/rutcampustrack/auth/api/AuthSessionApi.java` | `C0E33F5A93054B34DD9326E2CC865274384122D07B135457CCF1B2067954DEB2` |
| `services/auth-service/auth-api-contract/src/main/java/ru/rutcampustrack/auth/api/InternalSessionAdmissionApi.java` | `DB14CD24EB3448A12059A8D968A618E79EFF2B7629BDCC57215E4D3F51A92DD3` |
| `services/auth-service/auth-api-contract/src/main/java/ru/rutcampustrack/auth/dto/AccountHistoryEvent.java` | `3A745E2B1270F07E1F0523678D93BAB911AF0EBD1DCF4B4CB2EC9B4D66FDCD0F` |
| `services/auth-service/auth-api-contract/src/main/java/ru/rutcampustrack/auth/dto/AccountHistoryPage.java` | `593D09DEDFFD0F6B279594350B70088B76BD669B836BC071020E45A3135B9F9D` |
| `services/auth-service/auth-api-contract/src/main/java/ru/rutcampustrack/auth/dto/AuthAdmissionRequest.java` | `A874654D1E410590F43E59B11318F80C6C35A32AC9D0A32D940A53665A089452` |
| `services/auth-service/auth-api-contract/src/main/java/ru/rutcampustrack/auth/dto/AuthAdmissionResponse.java` | `1841B46A85ADB96212A9D167A9253A9CB9002021C96C6CEB0954DAD8C3E45912` |
| `services/auth-service/auth-api-contract/src/main/java/ru/rutcampustrack/auth/dto/AuthSessionSummary.java` | `38813017B0E10E4F6C9C1398EC8EB5D6546C0352151DA6E78362587B70BBD572` |
| `services/auth-service/auth-api-contract/src/main/java/ru/rutcampustrack/auth/dto/AuthSessionsPage.java` | `02AEFD0580AD2F1B939D4C5EACE3CCE17F06B66B440FF7AFA7273AE61B9A6990` |
| `services/auth-service/auth-api-contract/src/main/java/ru/rutcampustrack/auth/dto/CurrentSessionResponse.java` | `DA0899902573AB79D06C6713E44222322B96290F441DD7C4B913D809E965E9A1` |
| `services/auth-service/auth-api-contract/src/main/java/ru/rutcampustrack/auth/dto/PasswordPolicyResponse.java` | `5124D523F20AE93C4C3404D8419D37C8FB1B7C18A9198885E177121C3483BF7D` |
| `services/auth-service/auth-api-contract/src/main/java/ru/rutcampustrack/auth/dto/RoleGrantResponse.java` | `216D5BCE545AB120EECA05E14FB22E58085B407EB4CA9DBBDAB83DF076DABA24` |
| `services/auth-service/auth-api-contract/src/main/java/ru/rutcampustrack/auth/dto/SelectActiveRoleRequest.java` | `B62255C419C0BAD4F2854E6FAC4C358DC9E59AB1F9EC84617A6673E47B82AD56` |
| `services/auth-service/auth-api-contract/src/main/java/ru/rutcampustrack/auth/dto/SelectActiveRoleResponse.java` | `613957796DCA67F271ECB876D45791CECB7719C5DDE07A96C7D06893553777F2` |
