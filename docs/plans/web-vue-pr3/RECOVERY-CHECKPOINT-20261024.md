# Восстановление shell и сохранность PR3

Проверены существующая ветка и local77fbd7a5e, все11 worktrees,63 staged
файла Root и source WIP. Потерь не обнаружено. Все46 файлов group SOURCEv7
совпали с immutable manifest; credential handoff3файла и ACL-v2 сохранены.
Worker absent paths соответствуют sparse skip-worktree, это не потеря файлов.
Последний unit after-navigation.log содержит только RUN; PASS не заявляется.

Переносимая source-only копия: /workspace/rct-pr3-recovery/20261024T171044Z.zip,
6256924bytes, SHA256 d349768413f03487fcf7e1a18133191dc43b9d0c1b9561394bcb331fba8bdd43.
Git bundle165 unpublished commits verifyPASS, раздельные staged/unstaged patches
и новые исходники11worktrees,13 внешних source/prep files, manifest/hashes,
ZIP CRC и безопасные пути PASS. Секреты/private runtime receipts/DB/keys/env,
JAR/dependencies/caches не включены. Четыре historical scan matches — UUID
константы unit-test запросов, не credentials; история и правила не переписаны.

Три staged Gitleaks matches в map metadata — public Git revision и два
evidence SHA256. Значения сверены с Git object/реальными файлами. Поля
losslessly renamed в публичной проекции; inverse mapping равен исходному
receipt. Raw original сохранён; scanner rules/ignores не менялись. Новый
staged scan PASS. [Проекция](../../validation/web-vue-pr3/checkpoints/admin-visual-20261024/publication-provenance.json).

Последняя подтверждённая очистка Q3 — independent19PASS, proof498dc5e7:
user8 ACTIVE/STUDENT/group1, RV6/Cred5/profile4unassigned, обе его сессии
и собственные seed-сессии revoked, остальные17actors/20grants не менялись.
Старый external checkpoint сохранён отдельно; по его pendingFULL операции
повторять нельзя. Новая проверка очистки без изменившейся причины не запускалась.

Следующие пакеты после публикации: закончить self-credential handoff и
изоляцию successor login; исправить group status binding и две test fixtures,
повторить8failed случаев с переиспользованием5PASS; закончить LIMITED target
access/openpage withdrawal. Визуальный finite mobile/theme/state список
идёт параллельно. Затем Angular removal и assembled commonNginx runtime.
CI/Coverage/Security/OpenAPI PAUSED, без merge/deploy/production.
