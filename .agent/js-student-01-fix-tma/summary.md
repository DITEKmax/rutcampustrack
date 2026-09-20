# Summary — JS-STUDENT-01 FIX-r2 TMA

Implemented the two production TMA corrections from FIX-r2. The entry now loads
Telegram's official WebApp SDK in `<head>` before the app module. Auth now has one
request helper that posts the existing `TmaAuthRequest` shape as JSON with
`application/json`, `Accept: application/json`, and `credentials: include`; both
production and explicit fixture mode use that path. Access tokens remain in the
existing in-memory Vue ref and the 401 `onUnauthorized` reauthentication callback
still uses fresh Telegram init data.

The fixture installer now handles an empty SDK shell without weakening production:
it replaces that shell only under explicit fixture mode and preserves a host with
non-empty init data. The existing Today error state also renders after auth fails
instead of being masked by a disabled query's pending flag.

Checks and runtime evidence are in `checks.json` and `evidence.json`. Regression
tests intentionally failed on the old state, then passed after the fix. Final
local checks: TMA regression 7 tests, strict typecheck, zero-warning lint,
production build with fixture mode unset, and `git diff --check` all exit 0.

Runtime passed on the synthetic fixture URL `http://127.0.0.1:5186/` (Today and
confirmed check-in) and production-like `http://127.0.0.1:5187/` (clean missing
host). `?fixtureApi=unauthorized` rendered the clean 401 state. These runs do not
claim Telegram signature validation or a real auth-service request; root owns that
remaining gate and the real host/session.
