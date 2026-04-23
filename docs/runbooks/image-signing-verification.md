# Image signing verification (cosign keyless) — NEW-165

M08 Группа 11. Runbook для release-engineer'а и оператора VPS — как проверить
подпись GHCR-образа, как диагностировать failure, как rotate trust policy.

Ссылки:
- M08 DECISIONS D4 — выбор keyless через Fulcio/Rekor.
- `.github/workflows/deploy.yml` — job `sbom-sign` + verify step.
- `docker-compose.prod.yml` — digest-pinned base images (M08 G11).

## TL;DR — быстрая команда

```bash
cosign verify \
  --certificate-identity-regexp='^https://github\.com/ditekmax/rutcampustrack/\.github/workflows/deploy\.yml@.*' \
  --certificate-oidc-issuer='https://token.actions.githubusercontent.com' \
  ghcr.io/ditekmax/rutcampustrack/api-gateway:<SHA>
```

Выход `Verification for ... -- The signatures were verified against the specified public key` + JSON с subject/issuer → **OK**.

Любая ошибка (`no matching signatures`, `certificate subject matched neither of...`, `no entries found in the Rekor log`) → **не катим релиз**.

## Полный verify (11 images)

В CI этот блок исполняется в `deploy.yml:deploy.Verify signatures` перед SSH-deploy. Для manual-run:

```bash
#!/usr/bin/env bash
set -euo pipefail

SHA="${1:?usage: verify-all.sh <commit-sha>}"
IDENTITY='^https://github\.com/ditekmax/rutcampustrack/\.github/workflows/deploy\.yml@.*'
ISSUER='https://token.actions.githubusercontent.com'

IMAGES=(api-gateway auth-service academic-service schedule-service
        attendance-service notification-web notification-bot
        pwa-nginx mini-app-nginx web-panel-nginx landing-nginx)

for img in "${IMAGES[@]}"; do
  echo "--- $img ---"
  cosign verify \
    --certificate-identity-regexp="$IDENTITY" \
    --certificate-oidc-issuer="$ISSUER" \
    "ghcr.io/ditekmax/rutcampustrack/${img}:${SHA}"
done
echo "✓ All 11 images verified"
```

## Верификация SBOM attestation

SBOM прикреплён к image через `cosign attest --type spdxjson`:

```bash
cosign verify-attestation \
  --type spdxjson \
  --certificate-identity-regexp='^https://github\.com/ditekmax/rutcampustrack/\.github/workflows/deploy\.yml@.*' \
  --certificate-oidc-issuer='https://token.actions.githubusercontent.com' \
  ghcr.io/ditekmax/rutcampustrack/api-gateway:<SHA> \
  | jq -r '.payload' | base64 -d | jq '.predicate.packages[] | {name, versionInfo}' \
  | head -30
```

Вывод — список SPDX packages (apt/apk installed + Java/Node deps).

## Установка cosign

**Linux / macOS:**
```bash
# Latest release — см. https://github.com/sigstore/cosign/releases
COSIGN_VERSION=v2.4.1
curl -sSfL "https://github.com/sigstore/cosign/releases/download/${COSIGN_VERSION}/cosign-linux-amd64" -o /usr/local/bin/cosign
chmod +x /usr/local/bin/cosign
cosign version
```

**Windows (PowerShell):**
```powershell
winget install sigstore.cosign
```

**Docker (isolated):**
```bash
docker run --rm gcr.io/projectsigstore/cosign:v2.4.1 version
```

## Troubleshooting

### `no matching signatures`

Причины:
1. Image ещё не подписан. Зайди в `https://github.com/ditekmax/rutcampustrack/actions/workflows/deploy.yml`, проверь что `sbom-sign` job завершился успешно для этого commit SHA.
2. Image tag не соответствует commit SHA (использовали `:latest` вместо `:<sha>`). `:latest` — mutable, может уже быть перезаписан следующим deploy.

**Fix:** use immutable SHA tag, пересканируй workflow logs.

### `certificate subject matched neither of "..."`

Подпись есть, но identity не совпадает с expected regex. Причины:
1. Подпись сделана другим workflow (например `coverage.yml` случайно запустил `cosign sign`).
2. Repo был forked → `--certificate-identity-regexp` не матчит `fork-org/...`.
3. Workflow был переименован (`deploy.yml` → `release.yml`) без обновления runbook.

**Fix:** сравни expected identity из runbook с actual из error message:
```
Actual: "https://github.com/someone/rutcampustrack/.github/workflows/foo.yml@refs/heads/main"
Expected: "https://github.com/ditekmax/rutcampustrack/.github/workflows/deploy.yml@..."
```
При legitimate переименовании — обнови regex в `deploy.yml:Verify signatures` + этом runbook + notify команду.

### `no entries found in the Rekor log`

Причины:
1. Rekor transparency log временно недоступен (редко, но бывает outage sigstore.dev).
2. Подпись создана не через public Rekor instance (e.g., private sigstore deployment).

**Fix:**
- Проверь https://status.sigstore.dev/ — если outage, подожди.
- Retry с `--rekor-url=https://rekor.sigstore.dev` (default, но явно указать).
- В emergency: `cosign verify --insecure-ignore-tlog ...` **только** с письменного согласия команды (запись в MEMORY + NOTES).

### Emergency: deploy без verify

Если Fulcio/Rekor completely down > 2h и production down → manual override:

```bash
# workflow_dispatch с флагом
# (добавится в M09 emergency-flow)
gh workflow run deploy.yml \
  -f commit_sha=<SHA> \
  -f reason="fulcio outage 2026-04-XX — verified manually via digest comparison" \
  -f allow_unsigned_deploy=true
```

До реализации флага — временно отредактируй `deploy.yml:Verify signatures` шаг, обернув в `continue-on-error: true`, сделай deploy, **немедленно** верни обратно после incident.

## Rotation / revocation

Keyless certs короткоживущие (10 min TTL), revocation не нужна.
Если обнаружена malicious подпись в Rekor:

1. Зафиксируй Rekor entry ID (`rekor-cli search --sha <image-sha>`).
2. Обнови `certificate-identity-regexp` чтобы исключить compromised workflow (если возможно).
3. Rotate GITHUB_TOKEN на уровне repo settings (invalidates future OIDC).
4. Открой incident ticket + GH security advisory.

## CI/local параллель

| Среда     | Когда verify                | Команда |
|-----------|-----------------------------|---------|
| CI deploy | automatic pre-SSH           | `deploy.yml:deploy.Verify signatures` |
| VPS       | первая pull после ручного rollback | см. TL;DR выше |
| Dev       | не требуется                | skip |

## Связь с SBOM

SBOM (`artifact-name: sbom-<service>.spdx.json`) уходит в GitHub Actions artifact — retention 90d. Для long-term archive используем `cosign attest` + Rekor (immutable).

Fetch SBOM из конкретного image:
```bash
cosign download attestation \
  ghcr.io/ditekmax/rutcampustrack/api-gateway:<SHA> \
  | jq -r '.payload' | base64 -d | jq . > sbom-api-gateway.json
```
