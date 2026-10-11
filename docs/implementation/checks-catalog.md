# Каталог проверок подготовки

Команды взяты из текущих package scripts, Gradle wrapper, CI и runbooks. Статус
`not-run` не равен PASS; для этой документационной партии нужны синтаксис, coverage
и provenance, а не повторный runtime старого продукта.

| Scope | Команда | Назначение | Статус |
|---|---|---|---|
| Registry | `powershell.exe -NoProfile -File .agent/migration/generate-preparation-registry.ps1` | 147 IDs, JSON syntax | observed PASS |
| Backend delta | `powershell.exe -NoProfile -File .agent/migration/generate-backend-delta.ps1` | 175 IDs, JSON syntax | observed PASS |
| PWA | `npm test`, `npm run build` в `frontends/pwa` | future legacy/replacement change | not-run; `test` uses `--passWithNoTests`, so it is not a Vue acceptance gate |
| TMA | `npm test`, `npm run build` в `frontends/mini-app` | future legacy/replacement change | not-run; `test` uses `--passWithNoTests`, so it is not a Vue acceptance gate |
| web-panel | `npm test`, `npm run build` в `frontends/web-panel` | legacy/replacement changes | not-run, no code change |
| Backend | `.\gradlew.bat :services:auth-service:auth-app:check` | registered module check; `settings.gradle.kts:28`, `build.gradle.kts:51-92` | not-run, no code change |
| Backend IT | `.\gradlew.bat :services:auth-service:auth-app:integrationTest --tests "*AuthIT"` | registered targeted auth integration task | not-run, no code change |
| E2E | `npm run test:smoke` в `tests/e2e` | integrated PWA/TMA/browser flow | not-run; needs isolated running stack |
| Runtime | compose + targeted service + browser/Telegram host | endpoint/host evidence | not-run; no behavior changed |

For a future auth/session story, add negative authz, role-scope, token/session lifecycle,
contract and PWA/TMA host evidence. A mock or static path never closes integration.
