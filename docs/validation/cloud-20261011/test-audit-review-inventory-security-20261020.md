# Независимое review точных Gitleaks inventory fingerprints

**APPROVE** для узкой correction: перестановка столбцов текущего inventory и добавление ровно44 исторических fingerprints. Blocking findings:0. Это source-review конкретного WIP, не подтверждение зелёного CI и не одобрение всех изменений PR2.

Дата UTC: 2026-10-20T22:00:46.746579+00:00. Repository: `/workspace/work/pr2-review/repo`. Проверенный HEAD: `79caf3af68bbd7e008be61fae31ba7b7a7e54fe4`. Опубликованный исходный checkpoint: `857841889860f3e943c8a02119f05ca270a06395`. Запрошенный маршрут reviewer: Sol6.1/high; actual runtime model/effort metadata UNKNOWN. Reviewer не автор correction; исходники, policy и ignore не редактировались. Единственный созданный файл этим reviewer — этот отчёт. Дочерних агентов нет.

## Решение и риск

Каждое исключение относится к одному неизменяемому commit, одному точному пути CSV, одному rule `generic-api-key` и одной конкретной строке. Все44 значения доказаны как SHA256 полных tracked test/testFixtures файлов соответствующего commit. Я независимо извлёк исторический CSV через `git show commit:path`, разобрал строго4 поля `path,action,scope,sha256`, прочитал bytes соответствующего Git blob и вычислил `hashlib.sha256`; совпадение44/44. Флаги `checksumVerified`/`allOriginsVerifiedTrackedFileSHA256` в safe projections не использовались как доказательство: результат пересчитан заново. Четыре поля каждой строки содержат путь, действие, scope и воспроизводимый public-file checksum; credential-bearing поле отсутствует. Это доказывает происхождение именно исключаемого значения, а не отсутствие секретов внутри всего репозитория или самих test blobs.

Точный fingerprint на историческую строку оправдан сохраняющейся историей: исправление текущего порядка столбцов не удаляет CSV из commit857. Новых исключений detector/file/rule/history и broad allowlist нет. Новая находка другого commit/path/rule/line этим новым набором не подавляется. Остаточный риск ограничен точными historical findings, содержание которых полностью разобрано. Никакой actual secret на основании docs-only предположений не классифицировался и не исключался.

## Независимые проверки

- `.gitleaksignore`: исходные bytes HEAD сохранены как точный prefix WIP; добавлены пустая строка, один комментарий и44 уникальные entries. Удалений/изменений существующих исключений нет (`git diff --numstat`:46 additions/0 deletions). Множество entries точно равно множеству44 fingerprints safe findings, missing/extra/duplicates0.
- `.gitleaks.toml` и `.github/workflows/security.yml`: bytes WIP равны и HEAD79, и опубликованному857. Default rules, существующий dynamic metadata policy, limits/thresholds и scan command не менялись этим исправлением. В policy сохранено `useDefault = true`; присутствующее узкое старое rule allowlist не расширено.
- CSV: заголовок `sha256,action,scope,path`, строго4 столбца; исходные317 путей сохранены.316 записей после нормализации названий колонок полностью неизменны. Единственная изменённая прежняя запись — checksum `.github/workflows/ci.yml`; два новых пути — `tests/e2e/fixtures/http500-diagnostic-contract.py` и `tests/e2e/fixtures/mobile-dataset.ts`. Именно эти3 source paths отличаются между857 и79; все3 актуальных checksum совпали с HEAD blobs. Сейчас319 уникальных путей, потерь полей/entries нет.
- Безопасные projections прочитаны только из `findings.safe.json` и `classification.safe.json`. Raw SARIF, private logs и author metadata не читались.

Проверки выполнены read-only командами `git status`, `git diff`, `git show`, `git rev-parse`, `csv.reader`/`csv.DictReader`, `hashlib.sha256` и assertions; итог всех assertions — exit0. Сканер, native/runtime, tests/build/CI, Mongo/Chromium, network writes, history rewrite, external messaging/reviews/deploy не выполнялись. B013–15/frozen experiments не затронуты.

## Custody проверенного diff и набора

Все значения ниже SHA256 bytes. Для sorted fingerprints используется лексикографическая сортировка полных fingerprints, LF между строками и завершающий LF.

| Объект | SHA256 |
| --- | --- |
| safe findings projection | `b2dba3e69748346d734718d7585a8251a580294131e7f70e61939d41cc0eb348` |
| safe classification projection | `91e45790863570c43ba6cf965bbce245b8d1c18e431751cd2d590d240205013d` |
| HEAD `.gitleaksignore` | `16044190723ccb6840b090eaf689487dbe893163cd6a6555edf3748650847462` |
| WIP `.gitleaksignore` | `5f20ba1a2c9f231453605e27464fba4a921b43cf7352081d3cb68d5fa9756d49` |
| exact `git diff -- .gitleaksignore` | `e9911284d87b3dc039acf6e260c0dde6a0ff238c2c11f2965067704f67681942` |
| sorted44 new fingerprints | `2ead16957c15e03e034f640cfc7b5d2b06abb1c52083bcc2431b4455abeb77e6` |
| historical CSV at857 | `e11d807c87cb13e1ef0f78bf1cb535b670c68cc6b8b3143674ec6fb1798a5bbb` |
| WIP CSV | `0565a76ec57f6a536286dc05257f8a8e53872ddef669ba193b0a68a7cf1f1f1e` |
| unchanged `.gitleaks.toml` | `d9d402888344e6cc1a2e94e0558d31cbee8d42a46c70d892bec6433e8265edfa` |
| unchanged Security workflow | `c73439248d9a8983a3427553dbc298b0c74f75ee0a26f7f7f2443ec10df6e586` |

Точные исторические строки:144–146,186–210,244–249,251–252,280,284–290. Набор fingerprints:

```text
857841889860f3e943c8a02119f05ca270a06395:docs/validation/cloud-20261011/test-audit-files-20261020.csv:generic-api-key:144
857841889860f3e943c8a02119f05ca270a06395:docs/validation/cloud-20261011/test-audit-files-20261020.csv:generic-api-key:145
857841889860f3e943c8a02119f05ca270a06395:docs/validation/cloud-20261011/test-audit-files-20261020.csv:generic-api-key:146
857841889860f3e943c8a02119f05ca270a06395:docs/validation/cloud-20261011/test-audit-files-20261020.csv:generic-api-key:186
857841889860f3e943c8a02119f05ca270a06395:docs/validation/cloud-20261011/test-audit-files-20261020.csv:generic-api-key:187
857841889860f3e943c8a02119f05ca270a06395:docs/validation/cloud-20261011/test-audit-files-20261020.csv:generic-api-key:188
857841889860f3e943c8a02119f05ca270a06395:docs/validation/cloud-20261011/test-audit-files-20261020.csv:generic-api-key:189
857841889860f3e943c8a02119f05ca270a06395:docs/validation/cloud-20261011/test-audit-files-20261020.csv:generic-api-key:190
857841889860f3e943c8a02119f05ca270a06395:docs/validation/cloud-20261011/test-audit-files-20261020.csv:generic-api-key:191
857841889860f3e943c8a02119f05ca270a06395:docs/validation/cloud-20261011/test-audit-files-20261020.csv:generic-api-key:192
857841889860f3e943c8a02119f05ca270a06395:docs/validation/cloud-20261011/test-audit-files-20261020.csv:generic-api-key:193
857841889860f3e943c8a02119f05ca270a06395:docs/validation/cloud-20261011/test-audit-files-20261020.csv:generic-api-key:194
857841889860f3e943c8a02119f05ca270a06395:docs/validation/cloud-20261011/test-audit-files-20261020.csv:generic-api-key:195
857841889860f3e943c8a02119f05ca270a06395:docs/validation/cloud-20261011/test-audit-files-20261020.csv:generic-api-key:196
857841889860f3e943c8a02119f05ca270a06395:docs/validation/cloud-20261011/test-audit-files-20261020.csv:generic-api-key:197
857841889860f3e943c8a02119f05ca270a06395:docs/validation/cloud-20261011/test-audit-files-20261020.csv:generic-api-key:198
857841889860f3e943c8a02119f05ca270a06395:docs/validation/cloud-20261011/test-audit-files-20261020.csv:generic-api-key:199
857841889860f3e943c8a02119f05ca270a06395:docs/validation/cloud-20261011/test-audit-files-20261020.csv:generic-api-key:200
857841889860f3e943c8a02119f05ca270a06395:docs/validation/cloud-20261011/test-audit-files-20261020.csv:generic-api-key:201
857841889860f3e943c8a02119f05ca270a06395:docs/validation/cloud-20261011/test-audit-files-20261020.csv:generic-api-key:202
857841889860f3e943c8a02119f05ca270a06395:docs/validation/cloud-20261011/test-audit-files-20261020.csv:generic-api-key:203
857841889860f3e943c8a02119f05ca270a06395:docs/validation/cloud-20261011/test-audit-files-20261020.csv:generic-api-key:204
857841889860f3e943c8a02119f05ca270a06395:docs/validation/cloud-20261011/test-audit-files-20261020.csv:generic-api-key:205
857841889860f3e943c8a02119f05ca270a06395:docs/validation/cloud-20261011/test-audit-files-20261020.csv:generic-api-key:206
857841889860f3e943c8a02119f05ca270a06395:docs/validation/cloud-20261011/test-audit-files-20261020.csv:generic-api-key:207
857841889860f3e943c8a02119f05ca270a06395:docs/validation/cloud-20261011/test-audit-files-20261020.csv:generic-api-key:208
857841889860f3e943c8a02119f05ca270a06395:docs/validation/cloud-20261011/test-audit-files-20261020.csv:generic-api-key:209
857841889860f3e943c8a02119f05ca270a06395:docs/validation/cloud-20261011/test-audit-files-20261020.csv:generic-api-key:210
857841889860f3e943c8a02119f05ca270a06395:docs/validation/cloud-20261011/test-audit-files-20261020.csv:generic-api-key:244
857841889860f3e943c8a02119f05ca270a06395:docs/validation/cloud-20261011/test-audit-files-20261020.csv:generic-api-key:245
857841889860f3e943c8a02119f05ca270a06395:docs/validation/cloud-20261011/test-audit-files-20261020.csv:generic-api-key:246
857841889860f3e943c8a02119f05ca270a06395:docs/validation/cloud-20261011/test-audit-files-20261020.csv:generic-api-key:247
857841889860f3e943c8a02119f05ca270a06395:docs/validation/cloud-20261011/test-audit-files-20261020.csv:generic-api-key:248
857841889860f3e943c8a02119f05ca270a06395:docs/validation/cloud-20261011/test-audit-files-20261020.csv:generic-api-key:249
857841889860f3e943c8a02119f05ca270a06395:docs/validation/cloud-20261011/test-audit-files-20261020.csv:generic-api-key:251
857841889860f3e943c8a02119f05ca270a06395:docs/validation/cloud-20261011/test-audit-files-20261020.csv:generic-api-key:252
857841889860f3e943c8a02119f05ca270a06395:docs/validation/cloud-20261011/test-audit-files-20261020.csv:generic-api-key:280
857841889860f3e943c8a02119f05ca270a06395:docs/validation/cloud-20261011/test-audit-files-20261020.csv:generic-api-key:284
857841889860f3e943c8a02119f05ca270a06395:docs/validation/cloud-20261011/test-audit-files-20261020.csv:generic-api-key:285
857841889860f3e943c8a02119f05ca270a06395:docs/validation/cloud-20261011/test-audit-files-20261020.csv:generic-api-key:286
857841889860f3e943c8a02119f05ca270a06395:docs/validation/cloud-20261011/test-audit-files-20261020.csv:generic-api-key:287
857841889860f3e943c8a02119f05ca270a06395:docs/validation/cloud-20261011/test-audit-files-20261020.csv:generic-api-key:288
857841889860f3e943c8a02119f05ca270a06395:docs/validation/cloud-20261011/test-audit-files-20261020.csv:generic-api-key:289
857841889860f3e943c8a02119f05ca270a06395:docs/validation/cloud-20261011/test-audit-files-20261020.csv:generic-api-key:290
```

## Полная независимая checksum сверка44/44

Для каждой строки ниже action/scope разобраны из исходного CSV; scope всех44 — `auth_shared`, actions только `MODIFY`/`CREATE`. SHA256 заново вычислен по полному tracked blob в commit857 и точно равен полю CSV.

| CSV line | Tracked test path | Action | Independently matched SHA256 |
| --- | --- | --- | --- |
| 144 | `services/api-gateway/src/test/java/ru/rutcampustrack/gateway/filter/JwtAuthenticationFilterTest.java` | MODIFY | `8bb1cbf966e88a95490d4f3b5c395e58fdd4ac74cf7abcc0bec66979f96c0128` |
| 145 | `services/api-gateway/src/test/java/ru/rutcampustrack/gateway/ratelimit/CompositeLoginKeyResolverIT.java` | MODIFY | `143bbc18f256bb6b69d4b47199086483b1141dbf1173f10691ff7a21eae5badc` |
| 146 | `services/api-gateway/src/test/java/ru/rutcampustrack/gateway/security/InternalJwtIssuerIT.java` | MODIFY | `764bb4c3d6e6ad8cd97b12411028e3fb8063fcc6bdabd1abd56ee82dc2375dd1` |
| 186 | `services/auth-service/auth-app/src/test/java/ru/rutcampustrack/auth/authority/AuthReportCapabilityServiceTest.java` | MODIFY | `f61c6f2502935ac2acf15f6ff3fbc9f3be3939c9db938c8dda6f93946685720f` |
| 187 | `services/auth-service/auth-app/src/test/java/ru/rutcampustrack/auth/config/JwtAuthenticationFilterPurposeTest.java` | MODIFY | `ed94ff9837b1eec732630734c8e517b3015b8284a244cf9601c52c203aca92c5` |
| 188 | `services/auth-service/auth-app/src/test/java/ru/rutcampustrack/auth/contract/AuthTokenDtoRedactionTest.java` | MODIFY | `7fcff0f953299ef197277093f7dff359989646f93c5117cfe5b0fd0f1a91ec60` |
| 189 | `services/auth-service/auth-app/src/test/java/ru/rutcampustrack/auth/controller/AuthSessionControllerTest.java` | MODIFY | `c80c413650eff9e6fa22561b3f53bb3e5f589e729c6a876a694a5baa1d0ea2df` |
| 190 | `services/auth-service/auth-app/src/test/java/ru/rutcampustrack/auth/integration/ActuatorIT.java` | MODIFY | `87717ce1b9fbdc07151ea3ad8bb05c0e0f03e9a5998dabfb5d44dfc78dd9a49d` |
| 191 | `services/auth-service/auth-app/src/test/java/ru/rutcampustrack/auth/integration/AuthIT.java` | MODIFY | `d8d4b223d0d1b3b612ede17d8ca08224601cf615db69ff989ac019e5065f9c98` |
| 192 | `services/auth-service/auth-app/src/test/java/ru/rutcampustrack/auth/integration/BcryptDoSMitigationIT.java` | MODIFY | `d81dced9c9eb527d1ddb5f987b7973c1b7884f1fc5820d0edcabb684ad9f43ff` |
| 193 | `services/auth-service/auth-app/src/test/java/ru/rutcampustrack/auth/integration/InternalSessionAdmissionIT.java` | MODIFY | `2b5f314b7c2714a14cbb0b06f77b3ccb837ee2c4b9da4c3b3e3b1783f9f93ff6` |
| 194 | `services/auth-service/auth-app/src/test/java/ru/rutcampustrack/auth/integration/OpenApiSnapshotIT.java` | MODIFY | `16090a1d73a07c056cde8855fe08d3586030d9bda77198cab78866623d8af52e` |
| 195 | `services/auth-service/auth-app/src/test/java/ru/rutcampustrack/auth/integration/OtpIT.java` | MODIFY | `1f613fca92ec96ea42f5aa39d1146f69efba20a8cc5963b8ae639c16d4beacfd` |
| 196 | `services/auth-service/auth-app/src/test/java/ru/rutcampustrack/auth/integration/ReportCapabilityCountersIT.java` | CREATE | `ce0f02bf3e344eefb8e7ec00c71d6c9087e3f0f9c1dc65ccac01d1f639b3a236` |
| 197 | `services/auth-service/auth-app/src/test/java/ru/rutcampustrack/auth/integration/SameSiteCookieContractIT.java` | MODIFY | `43f9a93c1b31f12bb14739e90cc0ab20fa85565df6fb82124a20f771a6af1a9f` |
| 198 | `services/auth-service/auth-app/src/test/java/ru/rutcampustrack/auth/integration/SessionAuthFlowIT.java` | MODIFY | `a96227fb55d78100383b502c3dc6de7bcca35b32569f5d617e680e966b7b3dfd` |
| 199 | `services/auth-service/auth-app/src/test/java/ru/rutcampustrack/auth/integration/SubjectDeletionProofHttpIT.java` | MODIFY | `1f14a56d7cb038d5d79498efd3dd66d502cc6ffb493b9cc6d79b54bc4394b2b4` |
| 200 | `services/auth-service/auth-app/src/test/java/ru/rutcampustrack/auth/integration/WsTicketIT.java` | MODIFY | `4e5bffb565cd3adafcccf86cbff8b4c9520253b58ec2289e03cc7296e1aab67b` |
| 201 | `services/auth-service/auth-app/src/test/java/ru/rutcampustrack/auth/service/JwtTokenPurposeTest.java` | MODIFY | `19c327084eb6a8924762c11723a75d342a4e0ef26afd54fbfafb29352d446a1a` |
| 202 | `services/auth-service/auth-app/src/test/java/ru/rutcampustrack/auth/service/OtpServiceTest.java` | MODIFY | `14c47cfcd2dc27626410e7c9371c522f216b4088ba5f4a84eca9a76026979e49` |
| 203 | `services/auth-service/auth-app/src/test/java/ru/rutcampustrack/auth/service/TmaServiceTest.java` | MODIFY | `063cb8aa5bc2d4c80c57fbfb45a95e2fe34a578828b3ab20175e7d5b91bec5d1` |
| 204 | `services/auth-service/auth-app/src/test/java/ru/rutcampustrack/auth/session/SessionLifecycleServiceTest.java` | MODIFY | `731d4751cd38c4c32d0af411cffe18a6b5069ee0b4c81b8b6b9046766669a96c` |
| 205 | `services/auth-service/auth-app/src/test/java/ru/rutcampustrack/auth/session/jdbc/JdbcSessionAuthorityIT.java` | MODIFY | `42374171aa628f8e7b967f4a99f5eb1166121cb7de2e42a67250dc2980703679` |
| 206 | `services/auth-service/auth-app/src/test/java/ru/rutcampustrack/auth/support/TestJwtKeys.java` | CREATE | `75892d2c83c8272d1290ce35d039bec7f271666c52b2d7743b1eed3f5d76deb8` |
| 207 | `services/document-renderer-service/document-renderer-app/src/test/java/ru/rutcampustrack/documentrenderer/render/OfficeDocumentConverterTest.java` | MODIFY | `97588374d445c3ded4e22d6055715530d1d01a8626ae73bed5c58fec839f8f69` |
| 208 | `services/mobile-bff/mobile-bff-app/src/test/java/ru/rutcampustrack/mobilebff/map/MapQueryFacadeTest.java` | MODIFY | `a426cd009ea8d3f45c5b4dc5fcd3d0bfb255a20b55b55ccc11e990fdc3bb5269` |
| 209 | `services/mobile-bff/mobile-bff-app/src/test/java/ru/rutcampustrack/mobilebff/runtime/MapHttpGrpcIT.java` | CREATE | `1c8e3cefdfe15807260fe263c04ac67f57d887dd4936029b8f6185212cde1407` |
| 210 | `services/mobile-bff/mobile-bff-app/src/test/java/ru/rutcampustrack/mobilebff/student/StudentSessionProjectionTest.java` | MODIFY | `e1249298618913b47501ad7c542d1c2287ab736347991d8195417be46b56fcac` |
| 244 | `services/notification-service/notification-app/src/test/java/ru/rutcampustrack/notification/config/WebSocketConfigTest.java` | MODIFY | `7e9f6fa9090f6e3f694dc938058e9b6eefa5b152c9600b926d0f03c38a45786b` |
| 245 | `services/notification-service/notification-app/src/test/java/ru/rutcampustrack/notification/csp/CspReportIT.java` | MODIFY | `02695176023cab1f480d0e67f3939789c405fc2e215b4c6a8af9f788b9dcf214` |
| 246 | `services/notification-service/notification-app/src/test/java/ru/rutcampustrack/notification/exception/NotificationErrorHandlingIT.java` | MODIFY | `6a39a6771f1023ae558613adafdb76499f3676cf27f39f2d3d2ba4f202a755d7` |
| 247 | `services/notification-service/notification-app/src/test/java/ru/rutcampustrack/notification/history/NotificationHistoryConsumerIT.java` | MODIFY | `d283790f53ec3a505d55fa085351882f286fdbf794114106a38f40b10e31487e` |
| 248 | `services/notification-service/notification-app/src/test/java/ru/rutcampustrack/notification/push/PushSubscriptionCleanupJobIT.java` | MODIFY | `976f6759704f209d3a93978b69704eb748c30caf9e3075a9ed50cdf90430d6fa` |
| 249 | `services/notification-service/notification-app/src/test/java/ru/rutcampustrack/notification/push/PushSubscriptionRepositoryIT.java` | CREATE | `973d825bd0b43f99e1c8ddb97c0b39a2488ff63160dd57e9341e80e57317e98d` |
| 251 | `services/notification-service/notification-app/src/test/java/ru/rutcampustrack/notification/security/SecurityIdorIT.java` | MODIFY | `8ea3aba45789f09c17ce793d8df9a3de795979c54a18eb54a4f1db291430fca6` |
| 252 | `services/notification-service/notification-app/src/test/java/ru/rutcampustrack/notification/ws/StompIntegrationIT.java` | MODIFY | `e2ce46154c2ea0cdffb1bbc1911279a2c0513933bfb38d9945cb8b2546c45166` |
| 280 | `services/shared/shared-events/src/test/java/ru/rutcampustrack/shared/events/IdempotencyGuardTest.java` | MODIFY | `21b39f1334721aa29706bd4dfb142ff1d70a49f1a156c319059a2ad4639203db` |
| 284 | `services/shared/shared-observability/src/test/java/ru/rutcampustrack/shared/observability/GrpcDeadlineCallOptionsTest.java` | CREATE | `520e56304fc22ad8e9c889d5ddf8af218aad3506d0cbf98fd66c1906353b20b7` |
| 285 | `services/shared/shared-observability/src/testFixtures/java/ru/rutcampustrack/shared/observability/GrpcDeadlineArchRules.java` | MODIFY | `c403e086d283bde8f48c4367bd7a181dc983f7bfc1db30f103383291f9b369c6` |
| 286 | `services/shared/shared-outbox/src/test/java/ru/rutcampustrack/shared/outbox/OutboxPublisherJobTest.java` | MODIFY | `5e241e56fed72f549f4165aaf96797fd6ac43e22b8a6dd875576fe4f479a3181` |
| 287 | `services/shared/shared-security/src/test/java/ru/rutcampustrack/shared/security/grpc/ServiceIdentityServerInterceptorTest.java` | MODIFY | `9371ba9733c0f33526c1dd56592aa68a403fc3edf427df499662244ba8efee23` |
| 288 | `services/shared/shared-test-containers/src/testFixtures/java/ru/rutcampustrack/shared/testcontainers/ContainerTestBase.java` | MODIFY | `fb9a9375135c656149249acd7513ca1293477d90cea1ef7a6452172868f3d37b` |
| 289 | `services/shared/shared-web/src/test/java/ru/rutcampustrack/shared/web/audit/AdminActionAspectTest.java` | MODIFY | `201e1bfd6a8cd5847f159d22a97270c285bfc58ffb4615808470425c144a3624` |
| 290 | `services/shared/shared-web/src/test/java/ru/rutcampustrack/shared/web/config/GlobalErrorResponsesCustomizerTest.java` | MODIFY | `8e79327c47e461a2176fc52d14927e8ab17e035b3824b43b6e992f4cd1d8e10e` |

## Пределы заключения

Исходный bounded native scan с44 findings/exit2 известен только из packet root; reviewer его не запускал и raw output не читал. Повторный same-policy bounded scan и отдельный finite synthetic refusal control ещё не представлены этому review, поэтому их PASS не заявляется. Для публикации/root приёмки нужны их фактические результаты, а обязательные hosted gates остаются отдельным условием. Одобрение относится только к зафиксированным выше bytes/set и не заменяет review отдельной fixture correction79, общий аудит PR2 или К4 readback. При изменении ignore/fingerprint набора либо policy требуется адресная повторная сверка.

## Адресное дополнение по evidence refusal controls

Дата UTC: 2026-10-20T22:02:00.926546+00:00. **APPROVE сохраняется**, blocking findings0. Это дополнение обновляет только временное ограничение предыдущего раздела: закрытая сводка controls теперь представлена и прочитана reviewer. Исходники повторно не аудировались; `.gitleaksignore` и текущий inventory CSV повторно сверены только по уже зафиксированным SHA256 — оба побайтно неизменны.

Прочитан исключительно `/workspace/work/test-audit-implementation/security-scan/refusal-control.safe.json`, SHA256 `f38c1ab4587033258331f9a8fa557385e97474bcebd94eed73252a60d3663df2`:

| Control | Exit | Findings |
| --- | --- | --- |
| currentInventory | 0 | 0 |
| unrelatedSyntheticCredential | 2 | 1 |

Эта закрытая сводка подтверждает, что текущий CSV прошёл адресный control, а отдельный unrelated synthetic credential был обнаружен с отказным exit2. Root сообщает, что оба запуска выполнены pinned native Gitleaks8.24.3 с одной и той же generated default policy и новым exact ignore, без ослабления flags/rules/defaults. Сам summary содержит только control/exit/findings; версия, команда и идентичность generated policy не устанавливаются его собственными полями. Поэтому provenance запуска остаётся свидетельством автора root, отдельно от моей независимой44/44 checksum/fingerprint сверки. Reviewer эти scanner/native controls не запускал, raw reports/private logs не читал.

Root также сообщает о no-git scan новых unpublished documentation bytes с exit0/findings0, включая предыдущую версию этого отчёта. Это отдельно сообщённый root результат, не собственный запуск reviewer; закрытого артефакта этого дополнительного scan в scope supplement не предъявлено. Изменённая этим дополнением версия отчёта не считается автоматически покрытой тем более ранним scan. Итоговый history scan после final commit и обязательные hosted gates всё ещё отдельные условия; их PASS это review не заявляет. Новую tracked explanation и прочие source/docs bytes этим узким evidence supplement не переаудировал.
