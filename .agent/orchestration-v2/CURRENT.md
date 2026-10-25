# CURRENT — существующий Goal PR3, 25 октября2026

Ветка codex/web-vue-redesign, PR3. Root — docs/Git/интеграция; sole writers в сохранённых
worktrees. Children gpt-6.1-sol/high, telemetry недоступна, fallback не заявлен.
CI/Coverage/Security/OpenAPI workflow_dispatch/PAUSED. Без merge/deploy/production,
main/reset/clean/forcepush/globalprune/новой среды/Goal/PR.

## Актуальное решение владельца

Полный group UI gate перенесён на локальную Ubuntu владельца. В облаке запрещены
его запуск, повторная выдача прав и новое удаление ради gate. Локальный результат
NOT_RUN до выполнения владельцем; Q-ENV-010 о расширении облачного диска superseded.
Angular source cleanup явно разрешён без ожидания этого gate. Старые записи о cloud
group/disk barrier ниже в исторических отчётах не являются действующими командами.

Angular433 tracked files удалены, source audit117/117 без утраты принятой функции,
независимый review PASS, Root WEB/TMA strict+Vite PASS. Прежний group action accepted
116/117, ADMIN33/34 до фактического six-view UI результата; source117 не overallPASS.
WEB artifact `/` переиспользуется; PWA/TMA default-base артефакты непригодны для
/app/,/mini-app/, поэтому только prefix builds повторяются. Общий Nginx source
review PASS; actual runtime впереди. Отдельный budget512MiB/128MiB container,
early2.5GiB/hard2GiB; group7GiB не применяется к лёгкому gate. ONEHEAVY Root.

Локальный handoff tests/e2e/pr3-local: отдельные ~/rutcampustrack-pr3 и namespace,
Ubuntu24/JDK21/Gradle8.12/Node24.14.0/npm11.9.0, Docker12 services +3 host Vites.
Старые ~/rutcampustrack-ci-diagnosis и ~/.local/share/rct-stand/activate не трогать.
Новые synthetic credentials/TLS/PKI локально, cloud DB/secrets/keys не экспортировать.
Final SIGTERM child-reap correction independent review; launchers публикуются в PR.

TeacherManual conditional actual source7ef/b0fb: один реестр7/ordinary1/manual0,
PNG0, rawEXIT1/false сохранён, ownDELETE204/oldBearer401/browserclosed/HOLDfalse.
Нет globalabsence и visualPASS. Helper credentials/grants не квалифицированы;
HeadmanGroup раскрывает credentials; orphan без новой страницы. Галерея289, mobile
45 boundedobserved/5unchecked, не45полностьюготовых. Новых продуктовых вопросов нет.

Cleanup109paths/3batches real+1286668288B (~1.20GiB), available6.08GiB до новых
сборок. Cleanup остановлена, source12WT/Git/archives/results/25JAR+3setupJAR,
30original containers/images/volumes/DB/keys сохранены. Recovery ZIP8784846B,
SHA00fb9f0158468e18acd134b7542c03906fd1e5daf10354f266a03e3a0fa58bd0 сохранён.

ПарольCLOSED post9598 MAINuser8 Cred7/RV8/Aca8/profile6unassigned, own3SID revoked.
LIMITEDCLOSED postc7ee ISOLATEDTeacher2 profile8unassigned/RV9/catalog22/Aca9,
own4SID revoked; naturalwithdraw3708ms oldADMIN403/Teacher200. MAINTeacher2 инойrealm.
Whole-map/Authcorrect204wrong403 CLOSED. Их/старую cleanup не replay без changedrisk.
Group20backend +live55client/6unit/2PG/1Mongo PASS reused, новый groupUI NOT_RUN.
Main13/admin7 running, clock5/shipping5 stopped; group app replacements0/fixture0/delete0.

Следующий пакет: matching prefix builds → один commonNginx static/API runtime →
reviewed serial native Auth/WS, если current Origin/CSP допускают. HTTPSAuth/WSS,
TMA provider, real WebPush и недостающие activation/update остаются отдельными local gates.
После source integration/publish обновить PR и дать владельцу точный полный SHA/команды.
