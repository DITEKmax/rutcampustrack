# Independent fingerprint review — APPROVE

**APPROVE** `2a1c1f82b3b2a06daf1f666478d796e0a55e3775`, parent `eec3e0a864626160d33ae29da2cf3b0640e30caa`. Блокирующих findings нет. Scoped correction добавляет только `.gitleaksignore`: comment и один exact historical fingerprint. Это разрешает игнорировать установленное совпадение архитектурной прозы, сохраняя обнаружение нового совпадения в том же месте. **Remote Security38184430147 остаётся FAIL до фактических новых current gate receipts.** Project security/DONE здесь не объявляется.

Reviewer независим от автора. Полные governing inputs и актуальные decisions/questions/resources восстановлены; TASK-CONTRACT SHA256 `8cbd27d431815647886b2f39592905d3f1d4cb985643e5d9fa3f3f669b4df4a5`. Requested retained Sol6.1/xhigh — только scoped packet metadata, actual UNKNOWN. Все записи созданы только в `/workspace/scratch/rct-validation/fingerprint_review`; чужие worktrees/configs/source/docs и прежний завершённый NTR package не менялись. Children/heavy/runtime apps/Java/Gradle/Docker/browser/mutations/push — NONE.

## Узкое исключение и historical identity

Current `.gitleaksignore` отдельно открыт и сравнен с final Git blob. Его единственная noncomment строка:

`63647fc9b63175e1e32b0773d57affc637ea1065:docs/validation/cloud-20261011/packet-export-backend-security-review.md:generic-api-key:12`

SHA256 ignore bytes `b151fa8f6efc1340a37d1dc616aae0c803e5d2404ed063381f7128a5e03a1ba8`, Git mode100644; фактический filesystem mode0600 сохраняет author umask. Независимо открыта именно historical Git636 строка12. Это известная architecture prose о транспортных/доменных границах; credential assignment отсутствует. Blob SHA256 `041b38046f426ff860d8625c1029549d674e96521243fa102bfccdb489c27bb7`, line SHA256 `8d45ff494f4991e99c94509811935a760c82693d23a3eeb1773b41a6cf99e719` совпадают с original safe records. Сам текст найденного scanner value не повторяется в новом evidence.

Native root safe job metadata38184430147/job114603624624: scanner8.24.3,26 commits, PR head636, actual Security merge checkout `c3d12f3313a95f25162a1503be624f3b0617f8c0`; selected Fingerprint точно совпадает с ignore. Actual checkout других jobs не выводится из этого receipt.

Нет regex/path-wide/rule-wide/global allowlist, entropy/config/scanner version change, trigger weakening или history rewrite. Security workflow и local hook configuration побайтно unchanged. Другие fingerprints не добавлены.

## Official scanner и meaningful sensitivity

Author-recorded official release archive/checksum origins — `github.com/gitleaks/gitleaks/releases/download/v8.24.3`. Независимые local byte checks подтвердили checksum archive `9991e0b2903da4c8f6122b5c3186448b927a5da4deef1fe45271c3793f4ee29c`; extracted archive binary точно равен используемому binary SHA256 `e18325d568268b6efb6cd0cc721f8bea5667f4cd1c8d03618ab61987f41269ad`. Actual reviewer `gitleaks version` вернул8.24.3. Official provenance download URL сохранён автором; reviewer network redownload/signature verification не выполнял.

Original safe red/green/sensitivity command/exit/count/fingerprint records независимо разобраны; все12 author publishable file hashes verified. Выбранный historical range независимо содержит26 first-parent nonmerge commits.

| Control | Exact scanner result | Что доказано |
| --- | --- | --- |
| Historical range, empty ignore | native exit1, one exact636 historical fingerprint | Scanner действительно обнаруживает установленное совпадение |
| Same range and binary, exact corrected ignore | native exit0, zero findings | Указанное точное исключение работает |
| Separate synthetic NEW commit, same path/rule/line12, same exact ignore | native exit1, one distinct1901133 fingerprint | Исключение не прячет новое совпадение в том же месте |

Synthetic commit `1901133a3df2da743e32c6b54d965b51a992a5f5` independently inspected: parent eec3, только замена строки12 synthetic random48hex assignment и добавление exact ignore. Значение synthetic assignment не выводилось и не передавалось. Controlled repo имеет свой object store с read-only alternates к исходным historical objects; новый object существует только там и отсутствует product object store. Это релевантный regression control, а не синтаксическая/infra ошибка и не exhaustive certification всех secret rules.

Private author reports/logs/Finding/Secret values не открывались, не печатались и не копировались. Original safe projections сохраняют report hashes; совпадение hashes с private report bytes reviewer не утверждает. Recorded native exits относятся к actual author subprocess receipts. Reviewer history scanner/mutation replay NOT_RUN: duplicate history checks не потребовались.

## Source bridge, hooks и ограничения

Все **6985** original tracked SHA256 и filesystem modes независимо сверены с current. Для каждого current file также вычислен Git blob ID и сопоставлен с BASE и final2a1c trees. Все existing blobs/Git modes неизменны; final добавляет только exact `.gitleaksignore`, +2/−0, Git100644. Worktree clean; diffcheckPASS.

Авторские historical/sensitivity runs выполнялись при HEAD eec3 с untracked exact ignore, после чего file был committed2a1c. Source identity связывает proof с final correction; выполнения scanner при HEAD2a1c задним числом не утверждается. Original safe author evidence сохранено без нормализации.

На author commit installed hook отсутствовал — hookPASS не заявлен. Safe author receipt показывает actual8.24.3 staged guard exit0/findings0 на sole staged ignore плюс size/EOF/trailing whitespace/diffcheck; YAML/JSON N/A. Reviewer повторно проверил applicable file predicates. Pinned local hook config v8.22.1 unchanged;8.24.3 staged guard соответствует actual native CI scanner, но не выдан за запуск pinned local hook. Root сохраняет mandatory integration-hook и remote gate ownership.

Предыдущие25 NTR outputs побайтно проверены по их завершённому transfer manifest и оставлены неизменными. NP01/P2 и остальные product/client acceptance gaps этим scoped review не закрываются. Нового production/security policy и функций не вводилось.

Созданы только собственные review/hash/provenance/safe publication receipts, product/config edits0. Own audit первый запуск остановился на ошибочно предположенном filesystem0644 для нового ignore; действительный0600 соответствует author umask, Git mode остаётся100644. Исправлен только own helper, повторный полный audit native0. Это не reviewed product/scanner control failure.

Дополнительно actual official8.24.3 `gitleaks dir` проверил только собственные candidate publishable review receipts с empty ignore: native exit0/findings0. Это проверка публикации evidence, не повторная repository history или product security приёмка. Raw redacted report остаётся private и исключён из transfer; передан только safe metadata receipt.

Completion и точный safely publishable inventory находятся в `completion.json` и `transfer-manifest.json`; immutable payload hashes/ZIP сопровождают review. Следующий шаг — root интегрирует sole ignorefile и получает actual current Security вместе с другими обязательными gate receipts.
