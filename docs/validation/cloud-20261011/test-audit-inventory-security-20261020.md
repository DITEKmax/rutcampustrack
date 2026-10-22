# Inventory checksum false positives — безопасная классификация

First audit HEAD `857841889860f3e943c8a02119f05ca270a06395` Security39502767346 failed Run Gitleaks; Trivy filesystem/config SUCCESS, built-image scan SKIPPED. Hosted SARIF contents не прочитаны: доступ к archive/code-scanning ограничен. Поэтому локальный source proof и фактический hosted failure учитываются раздельно.

Адресная локальная проверка использовала exact pinned Gitleaks8.24.3 archive checksum из неизменённого Security workflow и существующий metadata classifier/default policy. Synthetic local event ссылается на реальные Git commits; диапазон main..79caf3af, full-history/diff-merges сохранены. Classifier завершился успешно; native scan exit2,9.26s,peakRSS138043392B. Это не переигрывание hosted CI.

Обнаружены44 generic-api-key results, все в `docs/validation/cloud-20261011/test-audit-files-20261020.csv`, historical commit85784188. Каждый matched row имеет ровно schema path/action/scope/sha256. Для каждого checksum заново прочитан точный tracked test file этого коммита и независимо вычислен SHA256;44/44 совпали. Значения — публичные content digests, не credentials. Ни secret values/snippets, ни author metadata не включены в эту опубликованную проекцию.

Current CSV переставляет checksum первым столбцом, перед keyword-bearing путями;319 audit paths/actions/scopes/digests сохранены. Старые44 факта остаются в Git history, поэтому добавлены только exact historical commit:path:generic-api-key:line fingerprints в существующий `.gitleaksignore`. Никаких file/rule/path-prefix/history exclusions, detector/policy/workflow changes или history rewrite. Прежний точечный cookie Max-Age fingerprint сохранён.

Адресные native controls с той же policy и ignore file: current inventory exit0/findings0; отдельный unrelated synthetic credential CSV exit2/findings1. Таким образом, новый fingerprint набор не подавляет другое расположение/значение. Это собственная проверка исполнителя; [Отдельный свежий read-only reviewer](test-audit-review-inventory-security-20261020.md) независимо пересчитал44/44 checksums и проверил точное множество исключений: APPROVE, blocking findings0. Его source review отделён от выполненных root native checks.

Full-history scan окончательного correction commit и обычный hosted Security остаются отдельными обязательными проверками; до них PASS не заявляется. Настоящая утечка не квалифицируется как безвредная только из-за документа. Product bytes не меняются; Deploy остаётся disabled_manually.
