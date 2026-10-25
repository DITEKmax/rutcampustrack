# GEO-only clock lane review — 849f149b

Verdict: **BLOCKED as frozen: two concrete P2 corrections required before a launch GO**. Preparation remains read-only/unlaunched; this report is not permission to build, pull, clone, start or mutate.

Reviewed `/workspace/rct-pr3-runtime/clock-lane/RECIPE.md` and every listed executable script/config, plus its manifests and the supplied feasibility history. Current GEO64-only recipe supersedes the older two-case/advance/new-actor proposals in that history. The accepted initializer/library discovery evidence was not treated as pending merely because older prose said so.

## Required corrections

### P2 — Twenty-three TLS bind specifications are invalid

Location: `compose.yml`, e.g. auth-service lines192–194, repeated across Academic, Schedule, Attendance, BFF and Notification TLS mounts.

These strings retain a fragment of an old shell-style default after the new source path:

```text
${LANE_PRIVATE}/tls/auth-server.crt:-./tests/e2e/infra/certs}/auth-server.crt:/run/rct-auth-tls/auth-server.crt:ro
```

After the normal LANE_PRIVATE substitution, this has four colon-separated fields rather than a valid source:target[:mode] short bind specification. YAML parsing accepts it as an ordinary string, but it is not a valid Compose volume specification. The package cannot progress through Compose configuration/start with these mounts.

A pure YAML/string check identified **23** such mounts in six services. Replace them with the exact owned source, container target and read-only mode. Seal the corrected package under a new digest; this finding does not justify bypassing TLS or mounting the old shared certificate directory. No Docker/config command was executed by this reviewer.

### P2 — The native UI action is not guarded to target only lesson64

Location: `cases.cjs:4`, at the Today eligibility poll and the subsequent generic “Отметиться на паре” click. Relevant frozen frontend: `StudentHomePage.vue`, hero selection at pinned source `3d6cf10a`.

The guard proves that Today contains lesson64 with ACTIVE/allowed eligibility. It then clicks the Home page's generic check-in button. Home binds that button to the **first ACTIVE** lesson (or first PLANNED lesson), not specifically to64. A Today projection containing an earlier ACTIVE entry plus eligible64 therefore passes the guard while the click posts the other lesson. Waiting for a response whose path contains64 only observes traffic after the action; it times out after the out-of-scope write instead of preventing it.

The lane must fail closed unless the actual current UI target is64 and its expected ready state has been reconciled immediately before the native click. Keep this a real UI/API assertion without interception, fabricated replies, Date manipulation or a different lesson. This is a source-level scope counterexample, not a claim that another active lesson was observed in the current clone; no clone exists yet.

## Verified preparation boundaries

- Recomputed the package digest using the manifest's filename/NUL/content/NUL algorithm: exact `849f149bf37316daea9142eba31d5a8e90dcc241b744b889974bfd3a5a99512e` match. All twelve pinned source files matched their hashes. Stream-hashed all seven artifacts actually used by this compose; every SHA256 matched. Academic's embedded-source qualification and the older operational receipts for other JARs remain as documented, not invented embedded metadata.
- Thirteen separately labelled services use a dedicated bridge/IPAM and project-scoped volumes. Compose declares no host network, shared external volume or production build. Published backend/edge ports bind loopback. Resource declarations total6592MiB and2CPU, consistent with the resource manifest. Actual second-lane readiness/peak usage is not measured here.
- Only Academic/Schedule/Attendance/BFF receive the external educational fixture/classpath and read-only clock mount. Auth, Gateway, TLS, database time, ordinary OS/JVM clocks and TTL/security timers are not intentionally pinned. Build commands target only the accepted external fixture/control, offline with one worker; source/hash drift is rejected.
- Capture requires a distinct snapshot approval and refuses while the seven named main backend writers are running. The package itself cannot stop or restart main. Root must own the graceful pause, SAME-container recovery and healthy/config/ID verification even if capture fails. Dumps are private/exclusive and restore writes target only the named new-project infrastructure. Three PG application DBs and Attendance Mongo are copied; Redis/Rabbit and Notification DB remain fresh. Mongo tool availability and PG ACL/owner restore are explicitly still real preflight gates.
- The frontend proxy retains the browser's exact HTTP loopback Origin and uses strict HTTPS8443 upstream. The preflight checks CA validation, loopback peer IP and the exact owned certificate. The inherited Node trust bundle is extended locally; no global trust import or HTTPS-ignore fallback appears in the reviewed code. Browser secure context is asserted by the future case.
- The finite case uses normal login15, server Auth identity/role, real Today data and a native browser geolocation action. It contains no Date pin, route interception, fake API response, CI metadata impersonation, phase advance, newslot or Late action. Credentials remain in private input/JS memory; the exported receipt is sanitized.
- Startup has bounded compose wait times and no implicit pull/build. Stop targets only the labelled new project or the exact recorded Vite PID/start-ticks/config. No prune, global kill, down-v or main-stop command appears in the reviewed package.

## Checks and remaining launch gates

Allowed independent checks only: file reads, pure hashes, eight Python AST parses, YAML parsing/string structure, `node --check` for the two CJS scripts and `bash -n` for init-roles. These syntax checks passed but did not validate Compose mount semantics. The runtime directory is absent and the private directory has zero entries at review, consistent with preparation-only status.

Even after the two corrections, the recipe's existing explicit gates remain: separate exact-digest technical GO/HEAVY lease; approved missing nginx image acquisition plus exact image ID; actual external fixture/control and official recursive five-input isolation verification; separately approved main-writer pause and private consistent snapshots; successful isolated restore/own15/group4/semester2/lesson64 authority readback; frontend14250 handoff and real strict TLS/secure-context admission. These are finite execution gates, not claimed failures or completed runtime evidence. No denied FULL/map operation was reviewed or authorized.

No Docker, Gradle, browser, build, network, dump/restore, key generation, runtime start, product write or child agent was used. Only this immutable review report was written. Requested model remains `gpt-6.1-sol/high`; runtime telemetry is unavailable and no fallback/telemetry claim is made.
