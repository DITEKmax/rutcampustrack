# Local TMA run — FIX-r2

The TMA frontend runs locally at:

`http://127.0.0.1:5186/`

Start the synthetic fixture host on an isolated port from `frontends/`:

```powershell
$env:VITE_MOBILE_FIXTURE_MODE='true'
npm run dev --workspace @rct/tma-vue -- --host 127.0.0.1 --port 5186
```

Useful fixture URLs:

- `http://127.0.0.1:5186/?fixtureToday=default` — Today rows and synthetic
  geolocation check-in.
- `http://127.0.0.1:5186/?fixtureApi=unauthorized` — clean auth 401 state.

For a production-like browser bootstrap with no synthetic host, omit
`VITE_MOBILE_FIXTURE_MODE` and use another isolated port, for example
`http://127.0.0.1:5187/`. It should show the existing missing-host error until
opened by Telegram.

The auth-service validates Telegram `initData` with the backend variable
`TMA_BOT_TOKEN` (`services/auth-service/auth-app/src/main/resources/application.yml`).
Keep that value out of the frontend and browser. If a local auth stack needs a
file-backed value, use the Git-ignored repository-root `.env.local` path (the
`.gitignore` rule is `*.env.local`) or an equivalent local secret store, and
export only `TMA_BOT_TOKEN` to the auth-service process. Do not create, print, or
commit the secret in this TMA worktree.

The local fixture and production-like URLs are browser checks only. A real
Telegram host and a real auth-service request still require root's separate
runtime gate; the fixture token is not signed Telegram data.
