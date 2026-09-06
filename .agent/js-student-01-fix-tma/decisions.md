# Decisions — JS-STUDENT-01 FIX-r2 TMA

## 2026-09-06 — official SDK bootstrap and canonical auth wire

The production entry now loads Telegram's official
`https://telegram.org/js/telegram-web-app.js` in `<head>` before the app module,
following the Telegram Mini Apps documentation. The server continues to receive
the raw signed `WebApp.initData` value, nested in the existing canonical
`TmaAuthRequest` JSON object.

## 2026-09-06 — one auth request path for production and fixtures

`src/tma-auth.ts` owns request serialization and response handling. Both the
production browser `fetch` and the explicit fixture transport call this helper,
so fixture checks exercise the same `{ initData }`, `application/json`,
`Accept`, and cookie credentials path. Fixture responses remain synthetic and do
not represent Telegram HMAC validation or an auth-service integration.

## 2026-09-06 — empty SDK shell in fixture mode

An SDK loaded in an ordinary browser can expose an empty `window.Telegram` shell.
The fixture installer therefore preserves a host only when `WebApp.initData` is
non-empty; it replaces an empty shell only when the caller explicitly enables
fixture mode. Production never calls the installer.

## 2026-09-06 — auth failure visibility

The Today loading predicate remains pending while an authenticated query is
loading, but stops treating a disabled query as pending after auth fails. This
allows the existing error state to render missing-host and 401 failures without
changing the screen or auth contract.
