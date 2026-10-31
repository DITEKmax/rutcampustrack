# Source-only архив восстановления08e08aedc

Архив `pr3-source-only-08e08aedc.zip` доступен для скачивания в итоговом сообщении
этого чата; бинарныйZIP не добавлен вGit. Snapshot published
`08e08aedc00dd7a6eae173a9766dbfcc55c1ba8c` в существующей веткеPR3.
Последующие изменения этого отчёта не меняют product/config/launchers source.

Размер11130527байт, SHA256:
`caa5b61406f5bd6a37f78169159fef73cf512b58c1e8cadd8ed1245e66d8f82c`.
[Независимая проверка](VERIFY.md): CRC, безопасные пути,0symlinks,
все3666sourcefiles SHA/size/mode/Gitblobbytes PASS. Всего3668entries;
sourcepayload38492611B, ZIPuncompressed39633076B сmanifest/restore.
Включены public sourceassets, Gradlewrapper и27canonicallanding/dist входов
(DockerCOPY, не результатVueсборки). Нет cloudruntime/privateconfigs/DB/keys/
sessionhandles/installeddeps/compiledbuilds/screenshots/Githistory.
PublicCIenv2 исключены; freshlocalsecrets генерируются отдельно.

NativeGitleaks полногоpayload: **EXIT1/12findings**, raw не превращён вEXIT0.
Все12 независимо [квалифицированы](findings-qualified.json):9publictestUUID,
2TypeScript typeannotations,1mockpublicVAPIDresponse. Реальных/privatecredentials
не найдено, unresolved0. Значения не брались изREDACTED. Source/rules/ignore/history
не изменялись ради сканера. [Receipt](receipt.json), [integrity](integrity.json).

Архив — source snapshot, не полный offlineGit/stand backup. Для запуска нужен
freshclone точного опубликованногоSHA и новый ~/rutcampustrack-pr3; guards требуютGit.
Старый ~/rutcampustrack-ci-diagnosis/activate не трогать. Launchers/configtemplates
уже вPR: [локальные команды](../../../../plans/web-vue-pr3/LOCAL-EXECUTION.md).
Старый immutableWIPbackup8784846B/SHA00fb9f0158468e18acd134b7542c03906fd1e5daf10354f266a03e3a0fa58bd0
сохранён отдельно, не заменён этимsourcecheckpoint. Group ownerlocalNOT_RUN;
CI-familyPAUSED, без merge/deploy/production.
