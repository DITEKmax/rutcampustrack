# Восстановление shell и переносимая копия PR3

Сохранность local77fbd7a5e, всех11 исходных worktrees,63 staged файлов Root,
source WIP и новых файлов проверена: потерь продуктовых исходников не найдено.
Sparse skip-worktree paths не являются потерей. Два старых /tmp native scripts
не пережили restart; product/source/receipts сохранены, новые harnesses в /workspace.

Последняя переданная source-only копия:
`/workspace/rct-pr3-recovery/20261024T185650Z.zip`,5552353bytes,
SHA256 `5f065b81185d995f039e0ebf79db1c325db41928853f96b3f0065dd9970d228f`.
168 unpublished commits относительно888,12 worktrees staged/unstaged/new source,
48 public source/prep files. Git bundle verify, ZIP CRC/safe paths/SHA manifest,
неизменность исходного WIP PASS. Секреты/private runtime receipts/DB/keys/env,
JAR/dependencies/cache исключены. Это снимок18:56Z; более поздние интегрированные
коммиты публикуются в существующую ветку PR3, архив не является latest HEAD.

Первый архив171044Z (6256924B/d3497684,165commits/11WT) сохранён. Три staged
Gitleaks map matches классифицированы как публичные Git/evidence hashes;
lossless metadata projection и staged scan PASS, правила/ignores не менялись.
В новой source pack native scan exit1 с24 matches сохранён:11 public Git IDs,
11 AuthJAR checksums и2 public harness hashes независимо сверены как nonsecret.
SCAN-CLASSIFICATION.md включён в архив; native scanner PASS не заявляется,
история/источники/правила не переписаны для зелёного результата.

Security состояние принимать из последних независимых postread, не из архива:
password post9598 — Cred7/RV8/Aca8/profile6unassigned, STUDENTgroup1 preserved,
все3 текущие own sessions revoked; LIMITED postc7ee — Teacher2profile8unassigned,
RV9/catalog22/Aca9/Cred1/Binding1, все4 own sessions revoked, чужие данные целы.
Пакеты закрыты. Старые Cred5/profile4/profile6 ACL baseline и pending cleanup
исторические. Ни генерацию, ни выдачу прав, ни cleanup по ним не повторять.

Продолжается group final delete UI/open access и finite visual остаток; затем
Angular removal/commonNginx. CI/Coverage/Security/OpenAPI PAUSED, без merge/deploy/
production. [Актуальные приоритеты](CURRENT-PRIORITIES.md).


## Свежий переносимый снимок22:30Z

`/workspace/rct-pr3-recovery/20261024T223044Z.zip`,6482207bytes,
SHA256 `b4262299dbb27220d12447c2d0a3d83293e59574ead89135e940bec7533f123d`.
185commits относительно publishedcab3,12worktrees и122public source/prep files;
source/WIP unchanged, bundleverify/ZIPCRC/safe paths/allSHA256manifest PASS.
Включены новые UIветки и готовые runtime/cohort sourcepreps. Privatecredentials,
Bearer/cookies/sessionhandles/generatedpasswords/DB/env/runtimekeys/JAR/deps/cache
не включены. Старые185650Z/171044Z копии сохранены.

Current-code scan cab3..d69 native0/0findings. Sourcepack DIR native1/25findings
сохранён:24 прежних publicGit/AuthJAR/harnesshashes и1public phase label
ACKNOWLEDGED_204. Все exact matched values проверены по исходникам/прежней
independentclassification; siblingtemplate metadata updates отмечены отдельно,
whole-file equality не предполагалась. SCAN-CLASSIFICATION.json иprior24report
включены; rules/ignores/history не менялись, nativeFAIL не названPASS.

Текущие ownvisualcleanup дополнительно закрыты: Mainactor3Profilepost95d933,
MainTeacher2Journalpostf137 — exact2ownrevoked/foreignmetadata unchanged каждое;
92079exit2/64379exit1 закрытыknownproof, rawFAIL сохранён. Journalafterd69technical
ownselfDELETE204/samebearer401/browserclosed. Не повторять эти cleanup/логины.
Runtime12 stillPREP/noactualwrites: QENV010 awaitsactual7GiB, минимум+2GiB requested.
Новый снимок не является разрешением запускать старые one-shot скрипты.
