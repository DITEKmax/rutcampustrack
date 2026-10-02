# Ж Redis/Rabbit recovery — docs source-ready

Оператор теперь получает конкретные currentvolume/nodename/version/persistence
discovery-команды, обязательные STOP условия и порядок nativeRedis/coldRabbit
checkpoint/изолированного восстановления поверх принятого PG/Mongo/files bundle.
Новый согласованныйcheckpoint отличается от восстановления после утраты источника:
после катастрофы используется только ранее сохранённый checkpoint.

Modified docs: docs/operations/runbooks/backup-restore.md (+124/-1).
Redis table уточнён: это не disposablecache, preferences безTTL/missing=>enabled
отделены от ephemeral capabilities/rate/dedup. Путь Redis: отдельно разрешённый
nativeRDB capture с completion/проверкойLASTSAVE; фактическийAOF требует complete
native set. Rabbit: cold completevolume с прежней identity/version/cookie,
definitions не заменяют сообщения. Restoreorder: изоляция → authorityconfig/keys
→ acceptedDBbundle → Redis/Rabbitstate → infrastructurereconciliation → отдельное
разрешение auth/domain/consumers/ingress. Rollback не обещает возврат утраченного
источника и не включает очистку/перезапись.

Точные актуальные ограничения root/owner: acceptableRedisloss/preferences,
ephemeralrollback/replay иdedup, brokerloss/replay, checkpointcadence/RPO/RTO,
offsiteprovider/шифрование/доступ/retention, disastersecrets/keyrecovery. Они не
выбраны этим документом. Отсутствующие policy/identity/version/evidence — STOP,
а не фиктивныйPASS. Actual productionDR не подтверждён.

Baseline178c196a13f28732ed854411e0d451346ffd9806, branchcodex/recovery-policy-1002.
Обычныйswitch после trackedclean сохранил foreignuntrackedWIP; noreset/stash/clean.
Root принимает документацию/интегрируетmain; CURRENT/SLOTS/metrics untouched.
Genericpreflight draft отклонён root и удалён доcommit, новой обвязки runtime нет.
Accepted backup.sh/restore.sh/test-restore.sh/recovery.py/test-recovery.py и
Compose/env/scripts остались byte-identical baseline (scoped emptydiff exit0).

Checks:6relative links + новыйdiscoveryblock Bash-n PASS(exit0), scopedwhitespace0.
Первыйdocchecker1 был Windows raw ../ longpath, normalized actualpath exists;
минимально исправлен только .agent checker, исходные ссылки не изменены.
Логи сохранены, послеPASS повторов нет. RuntimeN/A: docs-only; discovery,
BGSAVE/stop/export/restore не выполнялись. NoDocker/heavy/secret/envread/network/
install/production/rotation/offsite/deletion/push; nochildren/Terra.

Created evidence/harness exactfiles: .agent/evidence/recovery-policy-20261002/
{CONTRACT.md,SUMMARY.md,checks.json,check-docs.py,discovery-syntax.sh,
doc-check.log,doc-check-normalized.log,product.diff}. Productcode/tests Created/
Modified/Deleted:none. Existing foreignwork preserved.

Существенные оригиналы открыты: prodCompose Redis/Rabbit; currentrunbook and
acceptedrecovery verify_bundle/restore/dryrun; OtpService/LoginRateLimiter,
WsTicketService/RedisReportDownloadTicketStore, JwtService init;
NotificationPreferencesService and bot notification_prefs/idempotency_guard/
event_consumer/event_publisher/send_queue; OutboxPublisherJob(PENDING→SENT).
Их существующие limitations не превращены в unassignedcodefixes.
