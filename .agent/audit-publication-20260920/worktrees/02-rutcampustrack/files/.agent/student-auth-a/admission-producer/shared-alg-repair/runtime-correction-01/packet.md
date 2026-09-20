# Runtime correction 01 — compact packet

Status: `SOURCE_READY / RELEASE`

Baseline revision: `8002b9ea4356b10779c5bb9a6d99746d32d78ae2`
Risk: `S3` (граница аутентификации и frozen JWT contract)
Assignment: fresh bounded implementation leaf, `gpt-5.6-luna / max` по dispatch root
Writer: `shared-alg-runtime-fix`
Environment: Windows PowerShell checkout; source-only lease; no Gradle/Docker/runtime

## 1. Goal

Устранить подтверждённый runtime defect в `InternalJwtValidator`: убрать только
неисполняемую промежуточную мутацию JJWT signature registry и выпустить source для
root verification.

## 2. Context / evidence

Immutable failure record: `../runtime-failure-01/failure.md`. Focused Gradle run
compiled source but exited `1`: 22 tests, 16 passed, 6 failed. Все шесть отказов
вызваны `DefaultJwtParserBuilder$4.changed` на `InternalJwtValidator.java:64`:
`.sig().clear()` немедленно отклоняет пустую коллекцию до `.add(RS256)`.

## 3. Relevant scope

Только `services/shared/shared-security/src/main/java/ru/rutcampustrack/shared/security/InternalJwtValidator.java`.
Новая evidence-папка — `runtime-correction-01/`. Остальные tracked/untracked
изменения checkout принадлежат другим работам и сохранены.

## 4. Required behavior

Сохранить strict raw JOSE parse до JJWT parse: exact plain `alg=RS256`, required
nonblank `kid`, optional exact `typ=JWT`, unknown/missing/duplicate/wrong-type
rejection. Сохранить `.verifyWith(publicKeyProvider.getPublicKey())` и все
существующие claim, issuer, audience, purpose, skew и identity проверки.

## 5. Constraints

Удалить только `.sig().clear().add(Jwts.SIG.RS256).and()`. Не менять тесты,
producer, `InternalJwtClaims`, header schema, skew, `isHeadman()` или любой другой
код. Не запускать Gradle, Docker, product runtime, commit, reset, staging или deploy.

## 6. Existing patterns

Текущий validator выполняет bounded raw JOSE/JSON validation перед JJWT и
криптографической проверкой; JJWT остаётся проверяющим подпись RSA public key.

## 7. Acceptance criteria

- source delta ровно одна удалённая builder mutation;
- raw algorithm gate вызывается до `.verifyWith(...)` и остаётся exact `RS256`;
- все прочие source/test bytes в repair slice не изменены;
- evidence содержит pre/post hash и bytes, immutable failure references и exit codes;
- runtime post-correction не заявляется этим leaf.

## 8. Verification

Static-only: revision, target hash/bytes, scoped `diff --check`, line-order grep,
negative grep for removed mutation, unchanged test/fixture hashes and clean scoped
diff. Focused Gradle/runtime verification переданы root.

## 9. Do not

Не ослаблять raw header/signature checks, не добавлять альтернативный allowlist,
не исправлять соседние WARN/ERROR без reproduction и root decision, не затрагивать
чужой dirty state.
