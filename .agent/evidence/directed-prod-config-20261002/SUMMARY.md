# Directed prod credentials — source-ready S2

Оператор preflight теперь получает exit2, если не заполнил любой из двух обязательных directed credentials Academic ↔ Schedule; некорректный/неканонический формат возвращает exit3 с именем переменной, без её значения. Шаблон содержит оба CHANGE_ME и команду независимой генерации32byte unpadded base64url. Один токен направления используется у sender и receiver; направления не переставляются.

Изменённый продуктовый inventory:
- .env.prod.example — два placeholders и инструкции генерации/направления.
- scripts/validate-env-prod.sh — оба REQUIRED_VARS; отдельный pure Bash canonical helper и вызовы. Существующие проверки сохранены.

Точная логика: `^[A-Za-z0-9_-]{42}[AEIMQUYcgkosw048]$` в local LC_ALL=C. Последний символ имеет нулевые два padding-бита; это точный эквивалент DirectedServiceCredential.decodeCanonical для43ASCII/32bytes/decode-reencode, включая отказ неконанонических43символьных алиасов. Не применяется loose check_regex, печатающий начало значения. Серверного требования unequal tokens не найдено; запрет идентичных значений не добавлен. Шаблон рекомендует независимую генерацию каждого направления.

Baseline56f3dc52e0cb2d66241611f739ccd0a37e96ca74, branch codex/directed-prod-config-1002. Ordinary switch после clean tracked WT, без reset/stash/clean; foreign untracked evidence сохранено. Scoped Git metadata escalation accepted. RULES hash matches packet. Реальные .env и secrets не читались/не генерировались, WARN Git ignore permission unrelated, продукт на них не менялся.

Evidence: CONTRACT.md, checks.json, product.diff, before.log, after.log, syntax.log, probe.py. Before: baseline incorrectly returned0 for either missing token (2 cases, harness exit1). After:8synthetic cases PASS (harness exit0): valid separate canonical fixtures0; missing each/placeholder2; padded/noncanonical43/alphabet/length3; no token value in diagnostics. Bash syntax и scoped whitespace exit0. Один baseline и один post-fix запуск, без повторов PASS/массового suite; после проверки удалён только дублирующий comment label. Actual full validator runs, not service startup proof. Public deterministic synthetic fixtures removed.

Created evidence/harness inventory: .agent/evidence/directed-prod-config-20261002/{CONTRACT.md,SUMMARY.md,checks.json,probe.py,product.diff,before.log,after.log,syntax.log}. Product tests не созданы/изменены, deleted:none. No server build, containers/network/deploy/rotation/install. Source-ready: root принимает/review и интегрирует; production readiness не утверждается.

Root runbook delta: в deployment credential inventory и secret-rotation runbook добавить две направленные пары Academic sender ↔ Schedule receiver и Schedule sender ↔ Academic receiver; перед startup заполнять два independently generated32byte canonical unpadded base64url значения и запускать validate-env-prod.sh. Не путать их с GRPC_SECRET/INTERNAL_ISSUER_SECRET. Реальная rotation/deploy требует отдельной авторизации и не выполнялась. Shared runbooks/metrics в этом пакете не изменены.
