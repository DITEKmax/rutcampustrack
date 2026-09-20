# Browser and mounted harness plan — 2026-09-08

Root's conditional GO for the exact `18110` lease is recorded. No devserver,
watcher or browser has been started by this leaf.

- harness root: `.agent/profile-ui/evidence/resume-2026-09-08/harness/`
- entry: `index.html` → `main.ts` → actual owned profile components
- proposed URL: `http://127.0.0.1:18110/`
- port lease: use exact `18110`; do not fall back to another port
- startup command: `node <verified D650 vite runtime> <harness>/serve.mjs`; the evidence-only launcher calls Vite `createServer({...config, configFile:false})` with `host:127.0.0.1`, `port:18110`, `strictPort:true`
- free-port check at `2026-09-08T08:22:52.3916809Z`: `.NET IPGlobalProperties.GetActiveTcpListeners()` found no active listener on exact `18110`; this is the accepted lease evidence. A prior `Get-NetTCPConnection` check was discarded because its `-ErrorAction SilentlyContinue` could hide `AccessDenied`.

## Flow and evidence

The harness will import all seven owned Vue components and exercise fixture
snapshots through the typed props/callbacks. It will cover dark and light at
390×844, narrow 320px, enlarged 200% root font, keyboard focus, long names and
metadata, loading/error/retry boundaries, offline role/password/logout gates,
role conflict/read-only cards, session current marker, history pagination, and
SecurityScreen invalidation while fields contain secrets. Screenshots and a
machine-readable run record stay under this evidence directory.

Before the SecurityScreen correction, the mounted scenario must show the
reproduction: changing `error` to an invalidating code clears no inputs and
still permits a submit callback. After correction it must clear all three input
values and prevent further submission while invalidation persists, while
ordinary current-password/policy/network errors retain input and permit retry.

The harness is local fixture evidence only; it does not prove durable backend
revocation, shared shell navigation, PWA service-worker behavior or live TMA.
