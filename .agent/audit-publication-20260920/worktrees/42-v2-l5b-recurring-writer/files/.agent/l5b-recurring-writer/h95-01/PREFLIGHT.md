# H95-01 preflight

- Lease: root exclusive H95; scope is the actual H94 compiler defect only.
- Worktree: `C:/Users/maksd/IntelliJIDEA/rutcampustrack/.agent/worktrees/v2-l5b-recurring-writer`
- HEAD: `3d4115f3a4c4ddba473689e27ac1a0efb519a202`
- Governing packet SHA256: `58540BB77D1DDB03F22740996DC25675287A7FED01E577D8724347C07FD0BDFC`
- RULES SHA256: `B256A175274987DA9710D804B3C050A5DBCB47D8448CC03644D74168B52A437A`
- H95 packet SHA256: `1FC6ACF6444B574E702352BFEC777D4230D005A95F63AED2ABE256C99D946BFB`
- Preflight recorded: `2026-09-20T17:06:25.5863779+03:00`.
- Exact command: `./gradlew.bat :services:schedule-service:schedule-app:test --tests *RecurringDateCalculatorTest --continue --no-daemon --no-parallel --max-workers=1 --no-problems-report`
- Environment: OpenJDK 21.0.10 Microsoft-13106404; PowerShell 7.6.5; testcontainersReuse=false; login=false; no PG/Docker/generation.
- Source correction before this preflight: add `java.time.LocalDate` import to `schedule/item/entity/ScheduleItem.java`, directly supported by H94 diagnostics. No assertion or semantic change.

## Source inventory and SHA256 before H95-01

```text
services/schedule-service/schedule-api-contract/src/main/java/ru/rutcampustrack/schedule/contract/api/ScheduleItemApi.java 4F268533B3CC003006A7A459F98A482F5AF9A2A5CECA27FD568285F723811C7A
services/schedule-service/schedule-api-contract/src/main/java/ru/rutcampustrack/schedule/contract/dto/item/CreateScheduleItemRequest.java FABADD7312C0C39971E01E293DAC56305CDE2FC9EBF66BAE1EC5B399B820FF44
services/schedule-service/schedule-api-contract/src/main/java/ru/rutcampustrack/schedule/contract/dto/item/ScheduleItemResponse.java CE680E4D4528CE17FADAB3771F1517C05C31163672DA81F31F6D332B216DEEEE
services/schedule-service/schedule-api-contract/src/main/java/ru/rutcampustrack/schedule/contract/dto/lesson/LessonResponse.java 382BEC87F11B00CFDFE520584AE2F56DAEB8417194C3B82C12ABF22A5361E756
services/schedule-service/schedule-app/src/main/java/ru/rutcampustrack/schedule/exception/GlobalExceptionHandler.java 65A4287D71E82579A5FA81D06018AA0185E5EA5BFAC38B849AEE4479A29033D6
services/schedule-service/schedule-app/src/main/java/ru/rutcampustrack/schedule/exception/RecurringLifecycleNotReadyException.java 98161AC36B943F580C87FFE6575B83D6C363506F5A7703587CE677287AB22552
services/schedule-service/schedule-app/src/main/java/ru/rutcampustrack/schedule/exception/RecurringProtocolConflictException.java 4E68431D8CAB0641134CD7E1C181A35619A5EDC42E8DF368C0CA93AD2D287238
services/schedule-service/schedule-app/src/main/java/ru/rutcampustrack/schedule/grpc/AcademicGrpcClient.java 3EA5261AD49BB50CF33A557C1FA8FBC8E0E019C70A3A6E2DB00DC7270AB4010C
services/schedule-service/schedule-app/src/main/java/ru/rutcampustrack/schedule/grpc/ScheduleGrpcServiceImpl.java 6DDABB2B5E31873A3133C070AB27D4C2AA86369557C74A01B9EF7C92BBF9B2E7
services/schedule-service/schedule-app/src/main/java/ru/rutcampustrack/schedule/item/entity/ScheduleItem.java 5948937907C34DE16AC42846223861EB586D57CE4CBE5F7EDBC5D83D0747F3B2
services/schedule-service/schedule-app/src/main/java/ru/rutcampustrack/schedule/item/ScheduleItemAssembler.java 4D3C00AD2CA6D631213EC7253CC098848D8C98F8926C2B7E87FB20D598CE3804
services/schedule-service/schedule-app/src/main/java/ru/rutcampustrack/schedule/item/ScheduleItemController.java 64E60C2EB28D2BC0685C64F326E69613F7C0993F56EA5AB1FD29601D42335BDD
services/schedule-service/schedule-app/src/main/java/ru/rutcampustrack/schedule/item/ScheduleItemService.java A8A3C7D14BF16B0A165362A38618451AF0E62FC9B7D5A94EBFBB5B106DDA241D
services/schedule-service/schedule-app/src/main/java/ru/rutcampustrack/schedule/lesson/entity/Lesson.java 9DC2AC95D0524238CF1FE02BB47F8F9E11D86B495A82C71FFF141ABC88FEE575
services/schedule-service/schedule-app/src/main/java/ru/rutcampustrack/schedule/lesson/IsoParityReconciler.java B083D637ABBA18C69654C004E61D059AC057646303A0F4802C4BB8B8E970ABBC
services/schedule-service/schedule-app/src/main/java/ru/rutcampustrack/schedule/lesson/LessonAssembler.java 60EE7167659DEABED20CDAD22E7F8047D7770041ACDD99587E69D977CA066525
services/schedule-service/schedule-app/src/main/java/ru/rutcampustrack/schedule/lesson/LessonGenerationService.java 2FA1A537A8C116442FBAA3EF62188F8A99B5E6952D6A762EAD7B9C15E5582A4C
services/schedule-service/schedule-app/src/main/java/ru/rutcampustrack/schedule/lesson/LessonService.java 08FF056D392A91103401EFF54780858BC6114E15F0C0C52EA0AD4653CD0FA534
services/schedule-service/schedule-app/src/main/java/ru/rutcampustrack/schedule/lesson/LessonStatusTransitionJob.java 3620B9AA06BF933DEA1EDD74CBF69FA4E8DDB9C28DBB9FCAA876C9BDBDCF15E2
services/schedule-service/schedule-app/src/main/java/ru/rutcampustrack/schedule/lesson/repository/LessonRepository.java 438A282D5A97BAA6A3A13E0BE875E22BBB9D768C3500AB1CB043A0F7D3500AFE
services/schedule-service/schedule-app/src/main/java/ru/rutcampustrack/schedule/recurring/RecurringAssignmentAuthority.java BF5AF2DF1EF1EEE698A05B19D1EB868BD5CD7D61DDC40C0AAF15B27BA8A4ACF2
services/schedule-service/schedule-app/src/main/java/ru/rutcampustrack/schedule/recurring/RecurringCreateResult.java 3C30488D2F40E044E21D1AB2B3314EFF318619FCC389CD1601C5E6688DA8EDD9
services/schedule-service/schedule-app/src/main/java/ru/rutcampustrack/schedule/recurring/RecurringDateCalculator.java 049C5BBB4995AEEF72775F491A4DA706A11790AF4E28C1CE50E45A7F0B574304
services/schedule-service/schedule-app/src/main/java/ru/rutcampustrack/schedule/recurring/RecurringScheduleItemCoordinator.java 9076F3A7120A2EF9DDBED0D0F7AB23FBB2101E79416DA552A20A8C1B870FA7AA
services/schedule-service/schedule-app/src/main/java/ru/rutcampustrack/schedule/recurring/RecurringScheduleItemWriter.java 1F3DF400521389F7279D4DEF5A99922F766067C3CBA565A3892F59DB5298CF24
services/schedule-service/schedule-app/src/main/java/ru/rutcampustrack/schedule/subject/SubjectDeletedCascadeService.java A89B0A5AD3BFAD09D60229EBB873A36D59389A44E55329EBFB2E41897165C8C5
services/schedule-service/schedule-app/src/main/resources/db/migration/V18__recurring_assignment_fence.sql 747FEA00E9E2309B86B2D14B44D41607EB333F60CD829DAFCDB937E1C1E429D7
services/schedule-service/schedule-app/src/test/java/ru/rutcampustrack/schedule/integration/LessonGenerationIT.java 3CA4A9979FAAD295D1D62911876ED7B6094A81DC48A9B4A61D66D992E4C71B7B
services/schedule-service/schedule-app/src/test/java/ru/rutcampustrack/schedule/integration/ScheduleItemApiIT.java EBBF0F8BAC8A9BEF536FE6BA0FE35865AAE565D19A1ADCBEA1CDBB36BFE4AB57
services/schedule-service/schedule-app/src/test/java/ru/rutcampustrack/schedule/item/ScheduleItemSecurityTest.java FC7B2FA26FA096C962B9E822421850C0DC43FF666007DB59C91CA59C79B0C1F2
services/schedule-service/schedule-app/src/test/java/ru/rutcampustrack/schedule/recurring/RecurringDateCalculatorTest.java E261F5923D6429F496B8022BC6024A5E13970562C0CF2A80796B5DFE921B4969
