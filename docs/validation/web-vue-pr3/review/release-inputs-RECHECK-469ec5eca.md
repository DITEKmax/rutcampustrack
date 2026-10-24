# Independent DEV readability correction recheck

Вердикт: **PASS — единственный P2 из REVIEW-429a3a270.md закрыт; новых подтверждённых P1/P2 нет**. Пара429+469 source-ready в проверенном scope; full PROD/outer-edge/Angular replacement acceptance не следует из этого verdict.

Дата: 2026-10-24. Frozen correction `469ec5eca1b1f5802008f80abc84b506790e2c8c`, parent `429a3a27057065d564fc677092ec9214f0dd74ad`. Ровно `docker-compose.yml`: 11 добавлений/1 удаление. Author WT HEAD/parent соответствовали packet, status clean. Initial immutable report не переписывался. Назначение Sol6.1/high; runtime model telemetry недоступна.

## P2 CLOSED

`docker-compose.yml:337–348` сохраняет host dist read-only, теперь в `/opt/rct-web-dist`. Фиксированная startup command очищает/создаёт только внутренний `/usr/share/nginx/html`, копирует туда public input, применяет `chmod -R a+rX` только к внутренней копии и `exec nginx -g 'daemon off;'`. На host/input mount нет write/chmod, user/worker privileges не меняются. Destination больше не bind-mounted; фиксированная команда не содержит caller-controlled paths или env SQL/commands. Удаление внутреннего каталога убирает obsolete static files перед каждым snapshot startup.

Startup master сохраняет возможность читать restrictive readonly input, а nginx worker получает собственный readable public copy. Это устраняет exact0700/0600 mismatch, обнаруженный на c3 built dist. Image/config/service/container/ports/network/other lanes остаются прежними. Tradeoff явно принятого copy подхода: DEV обслуживает startup snapshot; новый host dist build требует restart данного static service, не нового build всего проекта.

## Independent source/provenance checks

- Frozen one-file diff/source inspection и `git diff --check 469ec5eca^ 469ec5eca` — exit0.
- Parsed old/new full DEV models после удаления только новой command и восстановления только mount destination равны. Дополнительных source/settings изменений нет.
- Exact command array из frozen compose совпал с `/tmp/rct-web-readable-command.json` и `WEB-DEV-READABILITY-429-PROBE-attempt3.json`. Прочитан внешний one-off probe source; reviewer его не запускал. Его fixed input — restrictive owned copy public dist с проверкой отсутствия symlinks и exact file hashes; actual ROOT dist/config монтируются read-only. Copy/chmod target не является этими mounts.
- Прочитаны author report `/workspace/rct-pr3-runtime/VUE-RELEASE-DEV-READABILITY-469ec5eca.md` и bounded receipts. First failed receipt сохранён byte-identical в attempt1; attempt1/2 имеют FAIL и0 HTTP checks. Их probe-only local-log compression/max-file incompatibility не представлена как application403 или HTTP PASS. Attempt3 меняет только probe logging `compress=false`, оставляя application command, input и assertions.

## Принятая targeted runtime evidence автора

Root-authorized tiny cached-nginx probe native exit0/attempt3 содержит6 actual static HTTP assertions: login/deep-link/index200 с exact reference HTML и no-store; actual hashed module200 с exact bytes/JS MIME/immutable; original PNG200 с canonical SHA256 `5ecad9bf5b38ddf9fe2114c5ab422cec78ea81c746fc2ee0b2ab5d9b3b6fff16`; missing asset404 без SPA fallback. Probe source действительно проверяет MIME/cache/bytes и canonical PNG hash.

Receipt фиксирует normal worker `nginx`, internal dir755/index644/PNG644,49 reference files/3971325 bytes, неизменность полных ROOT/fixture mode/owner/size/hash snapshots и read-only mounts. Exact owned container/temp удалены; lease освобождён. Это конкретное static-serving evidence correction, не browser execution, auth/domain или production acceptance.

## Остальные пределы сохранены

Reviewer не повторял HTTP/Docker/build/unit/control/browser/backend/CI и не менял host file permissions/продуктовые исходники/процессы. DEV Compose config exit0 передан автором. Initial changed-prod fragment PASS остаётся scoped; FULL PROD config по-прежнему не PASS из-за unchanged absent CAMPUS_MAP_USAGE_HMAC_KEY. Fake variable/secret не добавлялся.

429 PROD/deploy substitutions не менялись correction. Deploy сохраняет existing workflow_run(CI)+workflow_dispatch; четыре CI/Coverage/Security/OpenAPI files untouched/manual/PAUSED. Cutover/deploy/global runtime/QPROD004/Angular removal gates не закрыты. Единственная запись reviewer — этот внешний immutable recheck report.
