# IN_PROGRESS — объединённая цель 17–18.10.2026

Восстановление: [TASK18](TASK-CONTRACT-20261018.md), [TASK17](TASK-CONTRACT-20261017.md),
[async](TASK-ADDENDUM-ASYNC-20261017.md), этот файл, [ресурсы](resources.md).
Q031/D037: TASK-CONTRACT/CURRENT — принятая сохранённая цель ACTIVE; отдельного Goal API нет.
[Матрица v1](selected-results-20261018.csv)/[веса](selected-results-20261018.meta.json),
[решения](decisions.md), [вопросы](questions.md), [evidence](attempts.ndjson),
[единственный HANDOFF](HANDOFF-20261017.md). Историю заново не исследовать.

## Revision / принятие

Ветка codex/server-validation-20261011, [draft PR2](https://github.com/DITEKmax/rutcampustrack/pull/2), main не merged.
Подтверждённый local=remote=PR head **0d663fe1d5c0d29a64026383baa66e667c5f9788**.
Stage17 base753173688d633ca07991f4dd73bdcd2698f45d96; received18 basee66b1711b3d93faee625b50a2e2f785020a0f43a.
На f2 опубликован U14 coherent63 +8 contract/diagnostic/CI source paths; review1f9f21eb.
На 0d только 19 CI scalar bindings после U14; assertions/restoration/gates сохранены,
review1f895e43; пять применимых hooks PASS, JSON неприменим. Новое WIP: canonical
E2E Flyway password target c1ccc28d; Data15+Infra2 corrections ниже frozen/compilePASS/reviewbb0161b8; normalpublishpending.
Чужие25untracked, старыеWT/WIP/stand/volumes/keys сохранены.

Матрица **v1-КТ1 FROZEN: 22/25, вес105/123=85.37% принятого плана**, DONE_CHANGED19,
ALREADY_SATISFIED3. Состав/веса не менялись; это не production readiness.
U12 actual62c Auth200IT/148unit/generator/TS приняты9f691350;
U13 actualf2 все6 native snapshots + неизменённые prefix/4TS/inventory приняты7e64bfb6.
Публичные232paths/267ops +2directedinternal/3ops = прежний inventory234/270, без нового scope.
U16/U20 actual903 cache/principals/menu/TODAY-WEEK приняты7b580917/dfed6ec6;
U17 actual62c restored bytes/currentgrantdeny принятыe11ae909;
U21 actual62c browser size1→backend HALnext→GET200→две карточки принятf104287a,
пять файлов неизменны через0d. U19/U22 notification/Alloy приняты ранее; не переоткрывать.
U24 два k6 scripts/40portable controls принятыbe3a402a/f5025c2a; realk6/load/capacity NOT_RUN.

Прежние **9/9 функционально DONE_CHANGED9, ALREADY_SATISFIED0**; исходные6/9 в истории.
Это не завершение расширенного Goal и не отмена прежних manual/environment ограничений.

| ID | Статус | Достаточное evidence |
| --- | --- | --- |
| A01 | DONE_CHANGED | BCrypt/DI/credential lifecycle +realVue create→login12114507172/12114133029/review. |
| A02 | DONE_CHANGED | Текстовые enum/JPA/JDBC/REST/gRPC unknown/null12114232912/review; wire frozen. |
| A03 | DONE_CHANGED | Redaction/RS256/CI privacy,145unit+200IT12114133029/review. |
| A04 | DONE_CHANGED | RealSQL currentpage/order/student scope: subject2/HW1 enrichment12114232912/review. |
| M01 | DONE_CHANGED | Gateway→BFF→gRPC24spans; Rabbit retry/dedup10spans→Tempo/Loki12114439186/review. |
| M02 | DONE_CHANGED | 9a2DOCX exact+2job/reportdeltas/8apps/3117series12120205958/review85f1ca36. |
| M03 | DONE_CHANGED | 426livequeries/idle/noevent/insufficient30d +logs↔traces12120205958/review. |
| M04 | DONE_CHANGED | 9a correct2x18weekDOCX/navigation/lost202/reconnect/currentrights +PG/broker/recovery proofs/review85f1ca36. |
| M05 | DONE_CHANGED | e66PG/Mongo/Redis rights/exporters/Rabbit/privateaccess/recreate12118842862/review. |

C01 actualPG oldstaleRED→afterCommit commit/rollback2GREEN12113673595;
C02 immutable93publishedmigrations/28controls; C03 runtime/migrator/monitor M05.
Это не новое владение фактами U14.

## Конечный исполнимый остаток

| ID | Статус / достаточное и отсутствующее |
| --- | --- |
| U14 | REVIEW. f2 actual Auth200IT/148unit, Academic401ITPASS84FAIL/506unitPASS; оба privateAuthTLS→Python/broker native paths PASS. Data исправляет доказанные fixtures/новую SQL-неоднозначность; actual whole lifecycle/ordinaryCI ещё нужен. |
| U15 | IN_PROGRESS. f2 Flyway4 PASS: fresh/61→62, новые principals/ACL/atomicSQL/repeatstartup. После NEWV63 нужны соответствующие fresh/upgrade/repeat и успешный обычный startup; publishedSQL не переписывать. |
| U25 | IN_PROGRESS. Итоговый changed-risk review, совместимые текущие CI/Coverage/Security/OpenAPI, local=remote=PRhead, Q009 source-equivalence/final-doc SHA и handoff. |

Критический пакет: Infra исправил ТОЛЬКО E2E secret target SPRING_FLYWAY_PASSWORD→
spring.flyway.password, как в prod. Boot3.5.16 ConfigTree/Binder: старое имя NOTbound,
новое bound expected synthetic; 1.591s/RSS94944KiB/reaped. Actual startup после правки NOT_RUN.
На f2 оба browser jobs остановились при Academic exit1 до сценариев; appcause отсутствует.
Data11 fixtures: currentprincipal/auth_api вместо устаревшей authority схемы, sameDS rollback,
валидный group110..119, JDBC fixture seed readonlyAuthfields. ReviewReports R1 нашёл две
currentMap plan-target проверки ещё наV54; автор перевёл ТОЛЬКО их наcurrentfullchain.
Auth RCA оставшихся12: Event6 missing genuineadminclaims/setup; Dashboard1 barrier после
глобального TXlock; SubjectDeletion5 конфликт sid parameter/column в PLpgSQL.
V62 dd498250 сохраняется byteexact; NEWV63 квалифицирует параметр без изменения predicates.
Actual old42702→new safe invalid/полныйHTTP результат ещё не доказан; existingFlyway case
и достаточные native checks запланированы. Data soleauthor; ONEcompilePASS50.05s/RSS854144KiB/reaped, actualSQL/testsNOT_RUN.
ReviewReports coherent17 bb0161b8 accepted/R1closed; SQLReviewAuth d40ff838 linked.
CI audit3scalar05179faf currentfixtureSHA/frame1286, gates/oracles/identity preserved.
Старые62c D002 refresh/sessiontimeout и ADMIN_UI FAIL causeUNKNOWN остаются до нового
достаточного browser результата; U21 собственный HW PASS независим.

Последний snapshot02:52UTC: 0d CI39090969593/Coverage39090969574 IN_PROGRESS,
Security39090969552/OpenAPI39090969541 SUCCESS. AcademicCI вновь FAIL доordinaryIT,
SAFE12123011925: phaseXML_RULE, primary+additional expectedassertions proved; auditstate absent
causeUNKNOWN, exactsourcesrestored. Nativefixture repairs pending. AuthCI SUCCESS. Monitoring/browser идут.
f2 CI39089909475/Coverage39089909428 FAIL сохранены; никаких общегоPASS/таймаутослабления.
Продуктовых вопросов владельцу нет; следующий шаг — coherent corrections→compile→
независимое review→normal push/CI, actual runtime, затем финальная приёмка U14/U15/U25.

## Owners / ресурсы / границы

Root soleGit/taskdocs; /tmp/rct-s18-{auth,data,infra,reports}, известнаяbase9a и явные
readonly crossinputs. Data soleV62/commands/entities/fixtures+NEWV63; Auth API/generated/
privateTLS bridges; Infra configs/CI/bootstrap; Reports Python/UI/files; reviewers независимы.
Sol6.1/high/forknone, actualUNKNOWN — принятое Cloud-исключение. Nativecapacity7inclRoot.
Data CPU1/cache /tmp/rct-stage17-gradle-home; Auth CPU0/отдельныйcache. TWOheavy только
при доказанной вместимости, workers1/fork1/≤2.5GiBRSS/≤512MiBnewTMP/floor4GiB.
Локальный Docker не запускать: workspace почти наfloor; actualDB/broker/browser вordinaryCI.
Rootown1309objects pack5c439ead SHA196283b9, checkpoint/tmp/rct-s18-domain-checkpoint;
всеrefs/index/WIP сохранены, noGC/prune: auxiliaryWTs используют readonlyRootobjects.
No main/merge/deploy/productiongrants/reset/volumedelete/secrets/Angular/newweb/redesign/
projectdocs refactor/secondCLI. B013/B014/B015 FROZEN, no equivalents.
DEFERRED_OWNER: realTelegram/push/install/Safari, alertbot, offhostprobe при deploy,
прежняя manual приёмка. Исходный syncDOCX crashcause UNKNOWN. D032–D037 действуют.
