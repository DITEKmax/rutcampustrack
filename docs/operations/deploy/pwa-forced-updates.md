# PWA Forced Updates

RutTrack PWA использует обязательное обновление на каждый production deploy.
Цель: после зелёного деплоя пользователь не продолжает работать на старой
сборке, а видит hard-block модалку без действия "позже".

## Release marker

Production release marker = `1-<DEPLOY_SHA>` из `.github/workflows/deploy.yml`.
Префикс `1-` — policy epoch: он делает marker численно выше старого
`0.0.0`, чтобы уже установленные старые клиенты тоже увидели forced update
через прежний SemVer-only checker.

Deploy workflow передаёт его в Docker build PWA:

- `VITE_APP_VERSION=1-<DEPLOY_SHA>` — вшивается в JS bundle как `APP_VERSION`.
- `VITE_MIN_SUPPORTED_VERSION=1-<DEPLOY_SHA>` — попадает в `version.json`.
- `VITE_FORCE_UPDATE=true` — включает exact-match policy.

`frontends/pwa/scripts/write-version-policy.mjs` генерирует
`public/version.json` перед `npm run build`. В итоговом контейнере файл
отдаётся как `/app/version.json` через main nginx и как `/version.json` внутри
PWA nginx container.

## Client behavior

PWA проверяет `/app/version.json`:

- при старте приложения;
- при возврате вкладки/PWA в foreground;
- при восстановлении online;
- каждые 60 секунд.

Запрос идёт с `cache: "no-store"` и cache-busting query. Nginx также отдаёт
`version.json`, `sw.js` и `index.html` с `Cache-Control: no-store`.

Если `force=true`, клиент обязан иметь `APP_VERSION === latest`. Для git SHA
это важнее, чем SemVer comparison: любая старая сборка получает модалку
"Версия больше не поддерживается". Модалка не имеет close/postpone action.

Кнопка "Обновить приложение":

1. вызывает `ServiceWorkerRegistration.update()`;
2. вызывает PWA `updateSW(true)`;
3. делает reload fallback.

## API fallback

Каждый PWA API-запрос отправляет `X-PWA-Version: <APP_VERSION>`.

API Gateway фильтр `PwaVersionPolicyFilter` включён в production через
`docker-compose.prod.yml`:

- `PWA_VERSION_POLICY_ENABLED=true`
- `PWA_VERSION_POLICY_LATEST=1-${IMAGE_TAG}`
- `PWA_VERSION_POLICY_FORCE=true`

Если старый PWA делает `/api/**` запрос, gateway отвечает:

- HTTP `426 Upgrade Required`;
- `Content-Type: application/problem+json`;
- `X-PWA-Latest-Version`;
- `X-PWA-Minimum-Supported-Version`;
- Problem Details body.

Frontend axios interceptor ловит `426` и открывает тот же hard-block update
modal. Это закрывает сценарий, когда polling ещё не успел сработать.

## Rollback

Rollback через `IMAGE_TAG=<old_sha> docker compose ... up -d` также откатывает
PWA latest policy на `1-<old_sha>`, потому что gateway выводит marker из
`IMAGE_TAG`.
Пользователи с более новой сборкой увидят forced update назад к rollback SHA.

Это ожидаемое поведение для production rollback: активная версия ровно одна.

## Limits

Нельзя принудительно обновить пользователя, который офлайн или не открыл PWA.
Как только приложение получает сеть и открывает `/app/version.json` или делает
API-запрос, hard-block срабатывает.
