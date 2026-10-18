# ACTIVE — separate DB + apps / 18.10.2026

Persisted Goal Q031/D037: [GO](TASK-ADDENDUM-SPLIT-APPS-20261018.md),
[TASK18](TASK-CONTRACT-20261018.md), [TASK17](TASK-CONTRACT-20261017.md),
[ASYNC](TASK-ADDENDUM-ASYNC-20261017.md). Goal API нет; falsecomplete нет.
Root sole Git/taskdocs; codex/server-validation-20261011,
[draft PR2](https://github.com/DITEKmax/rutcampustrack/pull/2), no main/merge/deploy.
Starting **26cbe5912f6683a81780a831609dbddbc26feba4**; published/remote/PR
**ac9eaec52fca99d5ce97b2c72123d9cb15522e3d**. Checkout **50b87c8b…**:
safe coverage metadata+GitHubcompare подтверждают0changedfiles противac9.

## Конечный план

[Матрица v2 FROZEN](selected-results-20261018-v2.csv)/[веса](selected-results-20261018-v2.meta.json):
**23/38,109/188=57.98% плана**, не production readiness.
[v1](selected-results-20261018.csv):23/25,110/123=89.43% в прежней применимости;
U15 sameDB не двеDB/VPS. TASK17 прежние9/9 сохраняются в исходных границах.

| КТ | Закрыто / всего | Вес / всего | % плана |
| --- | --- | --- | --- |
| КТ2 boundary/AAA/contracts/persistence | 8/13 | 32/63 | 50.79 |
| КТ3 bot/apps | 2/9 | 7/37 | 18.92 |
| КТ4 changedchains/monitoring/jobs | 13/15 | 70/83 | 84.34 |
| КТ5 finalgates/review/handoff | 0/1 | 0/5 | 0 |

| Остаток | Конкретное недостающее действие / зависимость |
| --- | --- |
| U14.1/.2/.3/.5 | Actual twoDB/minrights; create/initialcredentials/grants/transfer/archive/proofs/currentrights/unknownACK/recovery через реальные SQL+AuthRPC. |
| U14.4/.6 | Actual Academic11jobs/singlequeue/2DOCX+короткий период/уход/lost202/reconnect/currentrights; official runtime snapshots→scoped TS generation. |
| U15.1 | Fresh/repeat двухDB сохраняют IDs/credentials/grants/history/sessions/jobs, включая additiveV10; no reset. |
| U20.1/.2/.3 | Actual private synthetic bot common4/studentHW/headmanroster/teacherToday, currentbinding/mixed/revoked new APIs. |
| U23.1/.2/.3/.4 | Actual GlitchTipVueJava/Pyro/protectedHomepage + measured combinedresources после networkfix; PMM assessment U23.5 отдельно закрыт. |
| U25 | Current final CI/Coverage/Security/OpenAPI/monitoring, independent review, exact source/executable/PR handoff. |

## Факты / классификация

ac9: CI39138304815/Coverage39138304856 FAIL; Security39138304884 и
OpenAPI39138304798 SUCCESS (offline snapshots, не новые runtime contracts).
Gateway/BFF IT SUCCESS; Authunit166PASS; Academicunit650PASS/1ordinalFAIL.
AuthIT105PASS95FAIL; AcademicIT89PASS409FAIL. Counts не доказывают общий RCA.
Safe primary единожды: .agent/evidence/cloud-20261011/current-wip/ci-ac9-primary/.

Текущий проверяемый пакет поверхac9:
- Fixtures: exact Auth V11 helper EXECUTE; manualAuthEMF publicschema при safe
  searchpath. Missingtable branch подтверждена cachedHibernate6.6.53/CI sourceframe;
  весь эффект на IT ещё не проверен. ACL/validation не расширены/не отключены.
- Scheduler guard: retained explicit nonblankreason для шести singleworker/local jobs;
  runtime CAS/leases/bodies неизменны, unmarked/blank всё ещё отказ. Review APPROVE.
- Exporter fixture: реальные ownDB права/foreignDB и private запреты вместо sharedDB;
  production ACL неизменны. Review APPROVE; actualDB runtime pending.
- Apps: standardDjango bootstrap исправляет source-proven modulepath defect;
  actual exit1 причина UNKNOWN, не OOM. Review APPROVE; fullruntime/resources pending.
- Product packaging: actual Dockerbuild AcademicAPI не видит AuthAPI DTOs;
  добавлены только два необходимых COPY. Browser до UI/login не дошёл.

Native current11: Auth/Academic fixture compile PASS; exact scheduler guard
JUnit1+ArchTest1 PASS,108.80s/RSS1,513,558,016B. Старые PASS не повторялись.
Source ed609daa/2261 неизменён, процессов0/lease RELEASED; actualDB не проверена.
Presplit485PASS/createбез500/2DOCX/b9reconciliation только прежняя применимость.
ИсторическийWORD incident/первыйSQLSTATE UNKNOWN.

## Исполнение / следующий шаг

[Граница](BOUNDARY-PROTOCOL-20261018.md), [owners/resources](resources.md).
Следующий шаг: normalpush связного source-reviewed/native-checked пакета,
actualCI/newDB paths. Готовые PASS не повторять без причины.
Official Auth snapshot получен; Academic отсутствует. Один type-only report client WIP
сохранён до snapshots+existing generators, без ручных spec/generated edits.
Native heavy lease RELEASED/processes0. Local Docker/DB/imagepull NOT_RUN:
workspace ниже4GiB reserve, DockerVFS на том же filesystem; no cleanup/limit lowering.
Q032 ANSWERED; текущие разрешённые isolated checks выполнены без обходов auto-review.

[История/evidence](attempts.ndjson), [решения](decisions.md), [вопросы](questions.md),
[HANDOFF](HANDOFF-20261017.md); freshest ignored checkpoint current-wip/latest-checkpoint.safe.json.
B013/B014/B015 FROZEN без эквивалентов. DEFERRED_LOCAL/OWNER:
realTelegram/push/install/Safari/alertbot/offhostprobe/ownerstand/realdeploy/backup/VPS/multiworker.
No reset/secrets/Angular retirement/newweb.
