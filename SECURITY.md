# Security Policy

Thanks for helping keep **RutCampusTrack** safe. Этот документ описывает
как сообщать об уязвимостях.

## Supported versions

| Version | Supported |
|---------|-----------|
| `v0.0.0-alpha.16` | ✅ Latest — first VPS deploy (M15) |
| `v0.0.0-alpha.13..15` | ✅ Pre-deploy hardening (M13-M14) |
| `< v0.0.0-alpha.13` | ❌ Pre-audit, не рекомендуется |

Первый стабильный релиз — `v0.0.0`. До его выхода security patches
выпускаются в рамках каждой `alpha.N`. Текущий статус: deployed на VPS
2026-04-27 (M15), M16 cleanup backlog в работе.

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
  Renovate (`docs/operations/deploy/ci-cd.md`) auto-bump'ит patch versions,
  Dependabot отправляет CVE-PR'ы. Отдельно не репортим.
- DoS на dev-инфраструктуре (docker-compose локально, Testcontainers).
- Issues в `docs/**` или `.planning/**` — не production code.
- Social engineering / phishing — не technical vuln.

## Supply-chain transparency

- **Trivy + Gitleaks** в CI (`.github/workflows/security.yml`) — scan
  на каждый PR и еженедельно.
- **cadvisor + promtail** digest-pinned (`docs/operations/deploy/container-trust.md`).
- **Renovate** auto-merge patch versions — минимизация CVE-exposure
  окна.
- **SBOM / cosign signing** — ✅ shipped (M08 + M14 G2 SHA-pin
  appleboy/ssh-action). All 13 production images digest-pinned, signed
  via cosign keyless OIDC, verified в deploy job. См.
  `docs/operations/runbooks/image-signing-verification.md`.

## Historic advisories

| Date | Severity | Summary | Fix |
|------|----------|---------|-----|
| — | — | Советы не публикуются до `v0.0.0` GA — pre-release audit замыкается внутри. Все findings в `docs/archive/report-before-v0.0.0/` (16 отчётов) + `docs/milestones/M13-pre-deploy-hardening/G27-cso-comprehensive-audit.md` (CSO audit) + `docs/milestones/M14-post-audit-fixes/` (закрытие блокеров). |

## Hall of thanks

Будет пополняться после `v0.0.0` публичного релиза.

---

_RutCampusTrack — микросервисная система учёта посещаемости РУТ МИИТ.
Проект вне юрисдикции РФ (meta-decision M1), персональных данных в
смысле 152-ФЗ не обрабатываем. Security hardening — M01-M14
(M06 supply-chain, M08 SBOM+cosign, M09 prod release blockers,
M13 pre-deploy hardening + IDOR×12, M14 post-audit fixes по 4 аудитам).
M15 — first VPS deploy 2026-04-27. См. `docs/milestones/`._
