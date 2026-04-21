# Security Policy

Thanks for helping keep **RutCampusTrack** safe. Этот документ описывает
как сообщать об уязвимостях.

## Supported versions

| Version | Supported |
|---------|-----------|
| `v0.0.0-alpha.*` | ✅ (pre-release hardening) |
| `< v0.0.0-alpha.2` | ❌ (pre-audit, не рекомендуется) |

Первый стабильный релиз — `v0.0.0`. До его выхода security patches
выпускаются в рамках каждой `alpha.N`.

## Reporting a vulnerability

Если вы обнаружили уязвимость, пожалуйста, **не создавайте публичный
GitHub Issue**. Вместо этого:

### Primary: private email

Напишите на **melrichards496937@gazeta.pl** с темой `[SECURITY]
RutCampusTrack <краткое описание>`.

Include:
- Описание уязвимости (какая атака, какой impact).
- Шаги воспроизведения (PoC, если возможно).
- Affected endpoint / service / file:line.
- Suggested fix (если есть).
- Ваш контакт (email, Telegram, GitHub handle) для follow-up.

### Alternative: Telegram

Если email недоступен — `@ditekmax` в Telegram (проверяйте username
по upstream repo).

### What happens next

- **24 часа:** acknowledgement receipt.
- **7 дней:** первичная оценка severity (CVSS 3.1) + tentative timeline.
- **30 дней:** fix в main branch (для HIGH/CRITICAL) или обоснование
  acceptance (для LOW/MEDIUM).
- **90 дней:** публикация CVE + blog-post (если релевантно) с credit'ом
  reporter'а.

## Out of scope

- Vulnerabilities в third-party dependencies без PoC на **RutCampusTrack**.
  Renovate (docs/ci-cd.md) auto-bump'ит patch versions, Dependabot
  отправляет CVE-PR'ы. Отдельно не репортим.
- DoS на dev-инфраструктуре (docker-compose локально, Testcontainers).
- Issues в `docs/**` или `.planning/**` — не production code.
- Social engineering / phishing — не technical vuln.

## Supply-chain transparency

- **Trivy + Gitleaks** в CI (`.github/workflows/security.yml`) — scan
  на каждый PR и еженедельно.
- **cadvisor + promtail** digest-pinned (`docs/infra/container-trust.md`).
- **Renovate** auto-merge patch versions — минимизация CVE-exposure
  окна.
- **SBOM / cosign signing** — планируется в M08 (CI hardening).

## Historic advisories

| Date | Severity | Summary | Fix |
|------|----------|---------|-----|
| — | — | Советы не публиковались до `v0.0.0` — pre-release audit замыкается внутри, все findings в `docs/report-before-v0.0.0/`. |

## Hall of thanks

Будет пополняться после `v0.0.0` публичного релиза.

---

_RutCampusTrack — микросервисная система учёта посещаемости РУТ МИИТ.
Проект вне юрисдикции РФ (meta-decision M1), персональных данных в
смысле 152-ФЗ не обрабатываем. Security hardening — M01-M09 (M06
supply-chain, M09 prod release blockers, см. `docs/milestones/`)._
