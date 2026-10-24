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
