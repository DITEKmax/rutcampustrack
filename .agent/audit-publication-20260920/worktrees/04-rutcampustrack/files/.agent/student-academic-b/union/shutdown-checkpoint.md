# Shutdown checkpoint — B0 shared implementation

Captured: `2026-09-08T03:31:12.2015724+03:00`
Exact cwd: `C:\Users\maksd\.codex\worktrees\34a5\rutcampustrack`
HEAD: `8002b9ea4356b10779c5bb9a6d99746d32d78ae2`

## Dirty paths at capture

Git status command: `git status --porcelain=v1 --untracked-files=all` (exit code 0); 116 entries captured.

- ` M` `build.gradle.kts` — SHA256 `c3a3e9ba5ddd96069877f5d4ef70fcc73f012d1e53d30d0b84d731eba09bee72` — bytes `17498
- ` M` `docs/openapi/academic.json` — SHA256 `44bafccaeb77ee671605285b470739f5046205c86447f48b8fa85062fd990d0c` — bytes `216691
- ` M` `docs/openapi/attendance.json` — SHA256 `df11209cc3234ac0310cd082f86a0e5f10953d77ac4726b96d81ffcd4c12aa3c` — bytes `104545
- ` M` `docs/openapi/auth.json` — SHA256 `ad8eeb87b1aea632e232a0687812d5489ca7ac8057a5a591e77515b8b7826532` — bytes `36691
- ` M` `docs/openapi/mobile-bff.json` — SHA256 `e991067b2f7b5fecda5bb1542f34586537e12771a83444733fb5d8f841b62b43` — bytes `34865
- ` M` `docs/openapi/notification.json` — SHA256 `6bf02ec71ae915206467982fd0a44f3354f3fa3ca61bf44346e0014a43879152` — bytes `40359
- ` M` `docs/openapi/schedule.json` — SHA256 `b6ed18e882fb6ac82a62d4c3f693bb288f36dfd15cd7328e8462276d545e6d61` — bytes `73495
- ` M` `event-schemas/excuse.decided.json` — SHA256 `e427d64c0c6cbc0950f41bf0431399a2951494ee0e5d5830080583ef0addca4a` — bytes `2483
- ` M` `event-schemas/excuse.requested.json` — SHA256 `2880ec5dbb29055e01ea2bd829278c9bce54fefbb44a9229e145e5d86eae1ba4` — bytes `4260
- ` M` `event-schemas/late_checkin.decided.json` — SHA256 `a6ac22666974e17472635712d75a334512f351c76936764bee3d0b1dd8fa3021` — bytes `2732
- ` M` `frontends/mobile-core/src/api/generated/mobile-bff.ts` — SHA256 `3219b05a681f84191d8c882bc30c2076354ec7b7693512c844ad70be0726b86d` — bytes `26096
- ` M` `gradle/libs.versions.toml` — SHA256 `d8900ad47ed968f0a831d7227b10067a0065abebc604255a09e6281de88ba83a` — bytes `2219
- ` M` `services/academic-service/academic-api-contract/build.gradle.kts` — SHA256 `45d1dc9c8531db1c2ae539629dc8919e2950f3067bad1cfbd20beed04f68471f` — bytes `1027
- ` M` `services/academic-service/academic-app/build.gradle.kts` — SHA256 `20f4d9564ca08556481ddc2b6d411647da7077c85887469d80b3e1f42aaf32cf` — bytes `5588
- ` M` `services/academic-service/academic-app/src/test/java/ru/rutcampustrack/academic/integration/OpenApiSnapshotIT.java` — SHA256 `3aea5e66bd3981a39708af93916303ce9bb7906d4008ec7487589c252cc2bafe` — bytes `4186
- ` M` `services/api-gateway/build.gradle.kts` — SHA256 `f22d83cea3a18d420be810541064aea26fe42f6db5b807ce6804c6ad3c268a18` — bytes `3042
- ` M` `services/api-gateway/src/main/resources/application-prod.yml` — SHA256 `ff0142ab2339c02265c09d98ab2645601b4169d648de2f418c737dd3aa987449` — bytes `1142
- ` M` `services/api-gateway/src/main/resources/application.yml` — SHA256 `6815a39ecf1f63b2094138b7171262744654b16cc13e1c8f7d77b044ab5ccb74` — bytes `19018
- ` M` `services/attendance-service/attendance-api-contract/build.gradle.kts` — SHA256 `21c4de9115e93aafb93d75a8ac7b4c081bf1e308430264a28ddf8fc394c1f37b` — bytes `631
- ` M` `services/attendance-service/attendance-api-contract/src/main/java/ru/rutcampustrack/attendance/contract/enums/ExcuseTicketStatus.java` — SHA256 `a0d8adc463e6cb7ea8c36a451193019d2c449802089265f66b66c064a0f54c2a` — bytes `161
- ` M` `services/attendance-service/attendance-api-contract/src/main/java/ru/rutcampustrack/attendance/contract/enums/ExcuseType.java` — SHA256 `59c4e01cf39cf79fbacaba196c9bdfb102e59a98558ece209ac7dc91d5842a1a` — bytes `263
- ` M` `services/attendance-service/attendance-api-contract/src/main/java/ru/rutcampustrack/attendance/contract/enums/LateCheckinResolutionReason.java` — SHA256 `2106d010925c292e74659d10674f1b2c8217b0c9bbf7425c4ac686df2ca98410` — bytes `211
- ` M` `services/attendance-service/attendance-app/build.gradle.kts` — SHA256 `6c040bb7fde9936287ee91609809e1281c04016c6c58c05e6595e553e293376c` — bytes `5880
- ` M` `services/attendance-service/attendance-app/src/main/java/ru/rutcampustrack/attendance/config/MongoConfig.java` — SHA256 `e18f5b7e1a4c485f1f2b23fd326e7f7d14a75f8ff3f40acd405f659683657edf` — bytes `7849
- ` M` `services/attendance-service/attendance-app/src/main/java/ru/rutcampustrack/attendance/excuse/ExcuseEventPublisher.java` — SHA256 `f358683d0bce41b28fa92bc366f2eca044245bd691628b1095e6af79ad1270ee` — bytes `9201
- ` M` `services/attendance-service/attendance-app/src/main/java/ru/rutcampustrack/attendance/excuse/entity/ExcuseTicket.java` — SHA256 `8b14ebe8dc16f34e6422bf7aa06325fc10cd8129943094d49d1f594e228abfc3` — bytes `2316
- ` M` `services/attendance-service/attendance-app/src/main/java/ru/rutcampustrack/attendance/grpc/AttendanceStudentGrpcServiceImpl.java` — SHA256 `24b43a4f8ee6cb2fc79f51391250f8a36f1f74a01c183d984fadcae55e6de4e3` — bytes `14256
- ` M` `services/attendance-service/attendance-app/src/main/java/ru/rutcampustrack/attendance/latecheckin/LateCheckinEventPublisher.java` — SHA256 `94efd22554a5ff3acdec684ac1b56c07f03259ef1e5ca914c5e84dfb17f0e50d` — bytes `4528
- ` M` `services/attendance-service/attendance-app/src/main/java/ru/rutcampustrack/attendance/latecheckin/entity/LateCheckinRequest.java` — SHA256 `5b8777bcdf922e058e1331f04bb0a033917a8251650df9e3dabcbbbe8a2a0cb7` — bytes `2321
- ` M` `services/attendance-service/attendance-app/src/test/java/ru/rutcampustrack/attendance/excuse/ExcuseEventPublisherTest.java` — SHA256 `4ca61bbb52ba3f5193f653b542ba59f54d97c5bcdd20fba849f54ec77c376e8e` — bytes `8682
- ` M` `services/attendance-service/attendance-app/src/test/java/ru/rutcampustrack/attendance/integration/OpenApiSnapshotIT.java` — SHA256 `4db3f4fdf66caa14cbf48d2c6fe539a566076b043ce724ee80c2dde96536808d` — bytes `3304
- ` M` `services/attendance-service/attendance-app/src/test/java/ru/rutcampustrack/attendance/latecheckin/LateCheckinEventContractTest.java` — SHA256 `6e0ae3179a52ee9482b87ef3a5180b6b1b563475cd018615d1d590c5c91c6480` — bytes `11274
- ` M` `services/auth-service/auth-api-contract/build.gradle.kts` — SHA256 `d4efdef2383741841191e23ac01d0c8fced32c7da354d38c12d09c6ffe65aa62` — bytes `1211
- ` M` `services/auth-service/auth-app/build.gradle.kts` — SHA256 `eb21a7b6b99ce9926e3d5e4d8a6a53dc78ec4fb9a43aa53a2eac0625f2da83a7` — bytes `3569
- ` M` `services/auth-service/auth-app/src/main/java/ru/rutcampustrack/auth/config/JwtAuthenticationFilter.java` — SHA256 `a321630875c98b40de20d30d8d220885c80ab35b871ee9207c2f10da2eb864fa` — bytes `2513
- ` M` `services/auth-service/auth-app/src/main/java/ru/rutcampustrack/auth/service/JwtService.java` — SHA256 `81429c4219ec00fd2207e11460c8ab03314787c7cdfb45ea28b3a18a72e1bd5d` — bytes `16164
- ` M` `services/auth-service/auth-app/src/test/java/ru/rutcampustrack/auth/integration/OpenApiSnapshotIT.java` — SHA256 `968232f3ac9b1ae3ced60e58120b6bf60b1a05f9d1da5c0f4293af63960b59c9` — bytes `4083
- ` M` `services/document-renderer-service/document-renderer-app/build.gradle.kts` — SHA256 `a0f107ead238d5b2d9c2e7d1ed7089c96e342b16f0af585a368f77782cd92306` — bytes `1814
- ` M` `services/mobile-bff/mobile-bff-api-contract/build.gradle.kts` — SHA256 `7faa27265b65a0df257b912ff76a5b0d49c648f5577911556491980f60f902c4` — bytes `343
- ` M` `services/mobile-bff/mobile-bff-app/build.gradle.kts` — SHA256 `694ef2c3664f68f7fc1130250e01e250dcb30b1adfbb9f25cb4dd85197cfd9ea` — bytes `2177
- ` M` `services/mobile-bff/mobile-bff-app/src/test/java/ru/rutcampustrack/mobilebff/contractexport/OpenApiSnapshotIT.java` — SHA256 `1bec56ea17cc141a7815b8aa2a455bfdb009d5d17eb85793198b58f28fa0b518` — bytes `11811
- ` M` `services/mobile-bff/mobile-bff-app/src/test/java/ru/rutcampustrack/mobilebff/runtime/StudentHttpGrpcAuthIT.java` — SHA256 `8f631e24c0e68603fc65383479ff431d79e169b0563644880e205f0fd242c56a` — bytes `14894
- ` M` `services/notification-service/notification-api-contract/build.gradle.kts` — SHA256 `9fc584e7edbbe1deaeb0a8bcb8f2217004dd3f09ef3187674d28c3fa7d795a12` — bytes `538
- ` M` `services/notification-service/notification-app/build.gradle.kts` — SHA256 `76140b801eae7679d45edaf1d2e1797bb0cb6c25c9a43afb4c1a9efd472d3734` — bytes `5213
- ` M` `services/notification-service/notification-app/src/test/java/ru/rutcampustrack/notification/OpenApiSnapshotIT.java` — SHA256 `479fcd2d8ddb547bd3f12f1877886a80188bfabf0645ddea8e7332cf5541a2fc` — bytes `4829
- ` M` `services/schedule-service/schedule-api-contract/build.gradle.kts` — SHA256 `7aedb5b3803ef2b6e9e969046ea57ae5c131600de79c61f669977ce84c7e86ef` — bytes `699
- ` M` `services/schedule-service/schedule-app/build.gradle.kts` — SHA256 `5e0736df4d0eb6ad5a0d2c6b95c1f0e0ff4360cb7db7d2691c08e0a9bdc55368` — bytes `4631
- ` M` `services/schedule-service/schedule-app/src/test/java/ru/rutcampustrack/schedule/integration/OpenApiSnapshotIT.java` — SHA256 `cce161a34896c03bf1bd1d560ede10030dc27645205cb45ad4ad40135bc720a6` — bytes `3138
- ` M` `services/shared/shared-events/build.gradle.kts` — SHA256 `2735def6aed78e8f229eeccb7d850ff4bc761fe3db0f74641f392652befb3cf9` — bytes `1456
- ` M` `services/shared/shared-events/src/test/java/ru/rutcampustrack/shared/events/EventSchemaCoverageTest.java` — SHA256 `5b95a560c28fb81ffdf8ed16c59114988c7dccaa7fa669612f78abe294beaff2` — bytes `12364
- ` M` `services/shared/shared-logback/build.gradle.kts` — SHA256 `775b239ec1764085ec2802a023bdbc88e82a81e466733df5dd08b7165e42b7cb` — bytes `991
- ` M` `services/shared/shared-observability/build.gradle.kts` — SHA256 `a41caf0ce9d18f139eea1950f2c3d22d1136b6ef3dc42aaf05b47ee13ca6d33e` — bytes `3165
- ` M` `services/shared/shared-outbox/build.gradle.kts` — SHA256 `1ea28921b809d4fd4ba93ff388e2b1559ee8dece9e85b71ab334a0efd7f143ba` — bytes `2986
- ` M` `services/shared/shared-security/build.gradle.kts` — SHA256 `974805d75c9adaf181e0f0c6731eda5c5a5e11dee570bb74cc6d2275a76ea1e8` — bytes `2588
- ` M` `services/shared/shared-test-containers/build.gradle.kts` — SHA256 `9bdebae64f6bf9fcc9f4e3fde997f9c7c9d3dffbcbf01f6e08f56a141d931d5a` — bytes `2323
- ` M` `services/shared/shared-web-api/build.gradle.kts` — SHA256 `6ce0cfe6393939a9b744767c0b734e61aea288c1e11b35594884bcd0dd932da9` — bytes `1132
- ` M` `services/shared/shared-web/build.gradle.kts` — SHA256 `044e1ebf395f0259910fcfed61796359d4c3b308d0034b1500260a602a496995` — bytes `3707
- `??` `.agent/student-academic-b/checks.json` — SHA256 `49b70070b2af7ecf4c298e9ddc296cf4b0324d511076dee6e02303ac56bf15ef` — bytes `8578
- `??` `.agent/student-academic-b/contract.md` — SHA256 `cb6ed68243bffafa0b385bab7f4c40ff01e61753c1926a935f0ce6b7f6abf298` — bytes `6704
- `??` `.agent/student-academic-b/evidence/AttendanceMetricCalculatorTest.xml` — SHA256 `ea8a9454cdce6aa6aa1a24b7f9adf89a43b5e177916df2f102f9a52f4ac2f974` — bytes `2312
- `??` `.agent/student-academic-b/evidence/GrpcClientDeadlineTest.xml` — SHA256 `e935e5d5a7719819856c73806d66c4d5777594366e1e122e9cdabeea10f8868b` — bytes `575
- `??` `.agent/student-academic-b/evidence/OwnRankCalculatorTest.xml` — SHA256 `87486f2c6344ce1bc44a71a6bc5f84dae56c29cc45cf3e20c7cbb8ecae1c5884` — bytes `1756
- `??` `.agent/student-academic-b/evidence/ReportDomainIsolationTest.xml` — SHA256 `1d05afd61f527b1f98132600395ee44b7c8c59722ab7ed1421760ce002a130aa` — bytes `482
- `??` `.agent/student-academic-b/evidence/ScheduledMustHaveSchedulerLockTest.xml` — SHA256 `8511506c80a180143ccdf7309f051a84233cc29255ca9b64caeb53848d28c298` — bytes `514
- `??` `.agent/student-academic-b/evidence/gradle-leased-rerun.json` — SHA256 `ced31ac5a2e778287abc7e1c3329495841c5c23012da0867c0e4d4808c673d89` — bytes `426
- `??` `.agent/student-academic-b/evidence/ui-relocation.json` — SHA256 `b73d1d90a9df8bed66756514d1611a52f002526e4b8cb275080f06125bdaf377` — bytes `2688
- `??` `.agent/student-academic-b/foundation-decision-result.md` — SHA256 `00b22ec98ba8117e92e4f145deba5d1b2fedc2b59d41b14644f5184a280fdde2` — bytes `14983
- `??` `.agent/student-academic-b/manifest.sha256` — SHA256 `aa6b28b284e5d0e43feb6215c13197044aece879378b013e4854aea496029362` — bytes `2619
- `??` `.agent/student-academic-b/repair-packet.md` — SHA256 `62dc665c25285790b9e0b684838a6e59586454607c4f1a9473463a183fd31aaa` — bytes `4136
- `??` `.agent/student-academic-b/review-1-result.md` — SHA256 `aab1fbca1921250044af499a5011d0fd8a77e104d1659eb863ed361a36943fa0` — bytes `4467
- `??` `.agent/student-academic-b/review-2-recheck-result.md` — SHA256 `2f750299bd3a6f395d3838de9225fc5b4a586eb415b8e9e12fb10121b4b8e255` — bytes `1251
- `??` `.agent/student-academic-b/review-2-result.md` — SHA256 `ea11179d96cc8352fc53457096f5ae7af8c36d341a0c5089afcff41836a9c445` — bytes `2347
- `??` `.agent/student-academic-b/summary.md` — SHA256 `98e57410a4e1a91a782dccc98eb8bf65385afab7064a094a96ffa9dad12b4ec3` — bytes `3865
- `??` `.agent/student-academic-b/union/contract.md` — SHA256 `c9c4dc2c4be2229aff3cedb27e0e43634329eba5ac83ce42a889e34c33dafc74` — bytes `13852
- `??` `.agent/student-academic-b/union/implementation-addendum.md` — SHA256 `ff05b4ed18f0e230b0543d756f49262956f08938a4980a7dd7067161b4ca450b` — bytes `3740
- `??` `.agent/student-academic-b/union/implementation-packet.md` — SHA256 `85387cedc6b11a03ecb7301c10e5d437798a6dd1fbfc61a83abb6e4de4513d6f` — bytes `6382
- `??` `.agent/student-academic-b/union/manifest.sha256` — SHA256 `78e7d99a98a109369fc68b51a41020c72923f2f631bfdde2a87168dfaf914d9f` — bytes `457
- `??` `.agent/student-academic-b/union/optionalchecks.json` — SHA256 `88aaacc28b53fb17a4c581421a6b05f7a1a4398d022725d5d41bfef7cbb49103` — bytes `6031
- `??` `.agent/student-academic-b/union/paths.json` — SHA256 `14219d88304a419e4fbdbb4fc62bbccc190d34a3e78ed79eb6e331b6987e40ec` — bytes `193109
- `??` `.agent/student-academic-b/union/source-layer-gate.md` — SHA256 `eaf987dac8675ad3ce685c58d071c60a0faebc7cd389cb7ec78d558bf354e121` — bytes `1780
- `??` `.agent/student-academic-b/union/source-preflight.json` — SHA256 `d373c1646663da8f8bbd5fa7a9fda94eb950bbff0086c04f0c42e1dd15e04239` — bytes `186333
- `??` `.agent/student-academic-b/union/sql-correction-gate.md` — SHA256 `ec79519be66237915118438c10dbdeedf5e05c32308bbd76ad48d321ee8b2249` — bytes `3043
- `??` `.agent/worktrees/student-academic-ui/` — SHA256 `<deleted-or-unavailable>` — bytes `-
- `??` `services/academic-service/academic-app/src/main/resources/db/migration/V24__auth_session_authority.sql` — SHA256 `7b35f18418dfe860a04be469040f5de277b5a6b517cc721048fe7df80c0264aa` — bytes `7242
- `??` `services/academic-service/academic-app/src/main/resources/db/migration/V25__student_subject_homework_foundation.sql` — SHA256 `2c378f0ed6ef41a47421602421e8ddfcbb73289d281fee4d6bde3b194d1e3311` — bytes `7463
- `??` `services/academic-service/academic-app/src/main/resources/db/migration/V26__campus_map.sql` — SHA256 `4a846e63df1a9e890def336bc97b71f17e37c503f745785d68dbdf4155f0f7ae` — bytes `14315
- `??` `services/academic-service/academic-app/src/test/java/ru/rutcampustrack/academic/events/HomeworkNotificationContractIT.java` — SHA256 `e034ce3333bc3de17afd6149b7581c2a9dc7ba6921ef178458c2be84bf7e6afb` — bytes `10993
- `??` `services/attendance-service/attendance-api-contract/src/main/java/ru/rutcampustrack/attendance/contract/enums/StudentRequestKind.java` — SHA256 `1edaf447f5dc3dc24702edd089aa48b4681c0b1e8760065a692d83953e7db4f6` — bytes `191
- `??` `services/attendance-service/attendance-api-contract/src/main/java/ru/rutcampustrack/attendance/contract/enums/StudentRequestOrigin.java` — SHA256 `aa71ab2a1d63760af98041fcbe62f776f19e7f8dcc496be4894d68445bd449fe` — bytes `210
- `??` `services/attendance-service/attendance-api-contract/src/main/java/ru/rutcampustrack/attendance/contract/enums/StudentRequestStatus.java` — SHA256 `71840e8d3f097c870b41b2b343dccab07cc55934491c3ad2aec1d2a3054c2b8a` — bytes `214
- `??` `services/attendance-service/attendance-app/src/main/java/ru/rutcampustrack/attendance/report/studentprojection/AttendanceMetricCalculator.java` — SHA256 `555393833a0bf598beedecd165ea5778bf06cf5d6ad6d65cee9e39c67ec57268` — bytes `14768
- `??` `services/attendance-service/attendance-app/src/main/java/ru/rutcampustrack/attendance/report/studentprojection/OwnRankCalculator.java` — SHA256 `78ce5a5949c673e303adfa13fbde6bbf3c4d0bafa4263e5b6cf73499a3eaab15` — bytes `6756
- `??` `services/attendance-service/attendance-app/src/main/java/ru/rutcampustrack/attendance/report/studentprojection/StudentProjectionException.java` — SHA256 `2a7929e7a48234b7bcca956822fb6e59f61d1676ac6c26f377bf4405baca1261` — bytes `2623
- `??` `services/attendance-service/attendance-app/src/main/java/ru/rutcampustrack/attendance/studentrequest/AttachmentState.java` — SHA256 `c1b9880413cdfbbe20ca3dec8d577e9c424f9d658cd8739ad4d2d8c8b853724c` — bytes `189
- `??` `services/attendance-service/attendance-app/src/main/java/ru/rutcampustrack/attendance/studentrequest/RequestAttachmentRepository.java` — SHA256 `5823836ad0b68fd591480e6fcab148c1eb07fdcab54c3a3f8db139a7861e6931` — bytes `481
- `??` `services/attendance-service/attendance-app/src/main/java/ru/rutcampustrack/attendance/studentrequest/RequestAttachmentRetentionJob.java` — SHA256 `ea02be8110064978d3db12fafd1c6e4c0d96f4433fa3fea7d3e29532b79c6eb8` — bytes `897
- `??` `services/attendance-service/attendance-app/src/main/java/ru/rutcampustrack/attendance/studentrequest/RequestBucket.java` — SHA256 `3c1aae15a5dd7d7c62d81d0b5ebe1fb0eebde34276a0592616b49803ea4f1e1c` — bytes `163
- `??` `services/attendance-service/attendance-app/src/main/java/ru/rutcampustrack/attendance/studentrequest/StudentLateCheckinBudgetRepository.java` — SHA256 `98c0e67dec2e78e729928b0e44a373d5f0d20f5404d31dfedfd5afbf0e7747b2` — bytes `484
- `??` `services/attendance-service/attendance-app/src/main/java/ru/rutcampustrack/attendance/studentrequest/StudentRequestModels.java` — SHA256 `defca9a5dc3d03f96a8e184b7040638cd04418dc014ab1da01528ee7f09b25c2` — bytes `6615
- `??` `services/attendance-service/attendance-app/src/main/java/ru/rutcampustrack/attendance/studentrequest/StudentRequestReceiptRepository.java` — SHA256 `7c6adbf803ac9226caf050498ad612992d9b3acf1acbc48022b4638309261b76` — bytes `529
- `??` `services/attendance-service/attendance-app/src/main/java/ru/rutcampustrack/attendance/studentrequest/StudentRequestService.java` — SHA256 `5095c2a63edf7e6561f6c08e2071c97270489a5c1cda1b73ef64507b3132a662` — bytes `91310
- `??` `services/attendance-service/attendance-app/src/main/java/ru/rutcampustrack/attendance/studentrequest/entity/RequestAttachmentDescriptorDocument.java` — SHA256 `15f372f6201cc7ea8f118d67cda149fa22cb704b5dc944c9e0219a4ed09d7c0e` — bytes `956
- `??` `services/attendance-service/attendance-app/src/main/java/ru/rutcampustrack/attendance/studentrequest/entity/RequestAttachmentDocument.java` — SHA256 `a4f991c7ad4dac3f8c96ca1341f86d996d72dffed699ebd13a0694edcefd4b95` — bytes `1467
- `??` `services/attendance-service/attendance-app/src/main/java/ru/rutcampustrack/attendance/studentrequest/entity/StudentLateCheckinBudgetDocument.java` — SHA256 `4a05fe8ab665fee1206b3633208258457de9fe9efd806cfe08b7976de9f9186b` — bytes `883
- `??` `services/attendance-service/attendance-app/src/main/java/ru/rutcampustrack/attendance/studentrequest/entity/StudentLessonSnapshotDocument.java` — SHA256 `ac4748e5306fcd9ef5e56e45348225af0930fc96bb7f2fcd1ac955abe07f29ec` — bytes `1112
- `??` `services/attendance-service/attendance-app/src/main/java/ru/rutcampustrack/attendance/studentrequest/entity/StudentRequestReceiptDocument.java` — SHA256 `85c71600c938c7fa556799110ee7936b3a6e42d77f1670aad22f8c0e9ae47cd0` — bytes `966
- `??` `services/attendance-service/attendance-app/src/test/java/ru/rutcampustrack/attendance/report/studentprojection/AttendanceMetricCalculatorTest.java` — SHA256 `d41927321ba948a28c0e9c40574e80d0a75d8b2f0a5d52eea7799199b5ec346c` — bytes `12391
- `??` `services/attendance-service/attendance-app/src/test/java/ru/rutcampustrack/attendance/report/studentprojection/OwnRankCalculatorTest.java` — SHA256 `1c00579efb4dda0e183b6f1bbc3c6e8ca066af2e2cec197c5bd0035686d9ace5` — bytes `6183
- `??` `services/attendance-service/attendance-app/src/test/java/ru/rutcampustrack/attendance/studentrequest/StudentRequestDomainIT.java` — SHA256 `968f4a4967d0ad2fdb0055c647d709f6a0a020b59ddb3396c64007e57a150de6` — bytes `46822
- `??` `services/attendance-service/attendance-app/src/test/java/ru/rutcampustrack/attendance/studentrequest/StudentRequestServiceAuthorizationTest.java` — SHA256 `7f96615f5a43bc8bd020bc51a4f3951340c3f9088363a56abe8ef95cc373c322` — bytes `9379
- `??` `services/auth-service/auth-app/src/test/java/ru/rutcampustrack/auth/config/JwtAuthenticationFilterPurposeTest.java` — SHA256 `f313eaf985473ac7205d21eb850014f4e4d6c762b9994f0285b361e25db2f574` — bytes `6224
- `??` `services/auth-service/auth-app/src/test/java/ru/rutcampustrack/auth/service/JwtTokenPurposeTest.java` — SHA256 `61e4db92a8376452787c8467ae61fc8cee75de539c226c04bd5d558646867855` — bytes `9229
- `??` `services/document-renderer-service/document-renderer-app/src/test/java/ru/rutcampustrack/documentrenderer/render/OfficeDocumentConverterTest.java` — SHA256 `1ae1df61678ad4e588deeb33bc7663de1b1a04be643e2cda3e5644d48ac25165` — bytes `7123
- `??` `services/mobile-bff/mobile-bff-app/src/test/java/ru/rutcampustrack/mobilebff/runtime/StudentHomeworkHttpGrpcIT.java` — SHA256 `01bf3a9cfa02bd050ece2e66f250e56333f1b626f1e6b7b6e647d38e78a8b1aa` — bytes `24422
- `??` `services/notification-service/notification-app/src/test/java/ru/rutcampustrack/notification/push/PushLibraryCompatibilityTest.java` — SHA256 `4a5fad1b8887a1709fdde9484c4bfbbc5e927afbcf9eee7ab0039df2a91fef49` — bytes `7539
- `??` `services/schedule-service/schedule-app/src/main/resources/db/migration/V17__student_occurrence_homework_binding.sql` — SHA256 `e7cc49423a57d7de377f49157b573d2d2ee0d36379bbf5de1b9e7d26d5b2dabb` — bytes `14052

## State and ownership

- Direct owner command: STOP all development until tomorrow. This checkpoint is the only permitted write.
- WIP is preserved in place. No reset, clean, rollback, commit, source recovery, SQL/proto/DTO edits, tests, runtime launch, or review was performed after STOP.
- This leaf remains the paused B0 sole writer for the shared implementation slice. Existing foreign changes were preserved byte-for-byte; no foreign Java was touched.
- Numeric slice: ACCEPTED 18-file manifest, 21 XML snapshots, and fresh focused PASS are complete and must not be changed.
- B0 contract: exact contract SHA `C9C4DC2C4BE2229AFF3CEDB27E0E43634329EBA5AC83CE42A889E34C33DAFC74`; paths SHA `14219D88304A419E4FBDBB4FC62BBCCC190D34A3E78ED79EB6E331B6987E40EC`.
- Accepted d3 source layer: exact revision `d3c31acb8cce53791a4981e5858a37d44fdc9a0e`; 39 non-overlap paths remain pending recovery. Three overlap paths remain dependency-winner bytes: `docs/openapi/mobile-bff.json`, `frontends/mobile-core/src/api/generated/mobile-bff.ts`, and `services/mobile-bff/mobile-bff-app/src/test/java/ru/rutcampustrack/mobilebff/runtime/StudentHomeworkHttpGrpcIT.java`.
- Source-layer gate is recorded in `source-layer-gate.md`; recovery was not started/completed. SQL V24–V26 and V17 remain WIP; V17 still needs the root-requested durable `payload_hash BYTEA` correction/test.
- B1 Academic/Schedule/Homework/lifecycle plus Attendance generation/events and B2 own/group/semester projections remain OPEN. A auth and D map domain integration remain after B0 acceptance.
- UI is a separate nested worktree/leaf and remains paused; its files were preserved.

## Last actual checks/evidence (recorded before STOP; no new checks run for shutdown)

- `.agent/student-academic-b/checks.json`: frozen revision `8002b9ea4356b10779c5bb9a6d99746d32d78ae2`; recorded checks have exit code 0, including focused Gradle PASS (21 tests), `git diff --check` PASS, evidence JSON/manifest/domain-boundary checks PASS. Product runtime for numeric pure calculators is recorded N/A.
- Union import preflight recorded 81/81 source manifest hashes and 81/81 destination raw preimages matching before product writes.
- Last source-layer inspection confirmed each of the 39 non-overlap d3 targets was still at the 8002 baseline or absent; no d3 bytes were imported.
- No owned dev server, watcher, or test process was started by this leaf for the paused work; no process was stopped and no data/volume was removed. The prior numeric Gradle lease was already released; no active owned process remains.

## Resume gate and next concrete step

- Resume only after a new direct owner GO.
- On resume: ownership check → guarded exact d3 39-file source recovery and verification → V17/V24–V26 SQL correction/tests under lease (including durable binding payload hash) → proto/DTO checks → fresh independent Sol review → B1/B2.
- No API/runtime/full-role PASS is claimed by this checkpoint.

## Limits

- This file records state only. It does not authorize source recovery, schema edits, transport edits, runtime, deployment, migration, cleanup, or review.