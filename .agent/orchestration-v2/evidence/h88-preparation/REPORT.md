# Runtime r3 checkout and frontend dependency preparation

## Contract

- Worktree: `C:\Users\maksd\IntelliJIDEA\rutcampustrack\.agent\worktrees\v2-runtime-build-r3`
- Branch: `codex/v2-runtime-build-r3`
- Expected and actual HEAD: `13e5fd1985b798bbb61bcc85969e6a74b1fc6837`
- Parent: `ed9b63c9449b23fa3f5dbf2feb52cd8f9e9cfe83`
- Rules SHA-256: `B256A175274987DA9710D804B3C050A5DBCB47D8448CC03644D74168B52A437A`
- Preparation packet: `.agent/lessons-runtime-r3-preparation/PACKET.md`

The destination was absent and the branch was absent before creation. The worktree was created normally from the accepted mass-cancel commit. Existing r2 preparation and H42 evidence were read first.

## Dependency procedure and result

The accepted r2 procedure is the locked offline command from H42:

```text
npm ci --offline --no-audit --no-fund
```

It was run once from `r3/frontends`, where `package-lock.json` was present and `node_modules` was absent. Exit code: `0`. npm reported `added 222 packages in 12s`. No network install, package hunt, package/lock edit, cache copy, link, reparse workaround, or secret read was used.

The resulting `frontends/node_modules` is ignored dependency state for root's later producer. `frontends/package-lock.json` remains unchanged; its SHA-256 is `4AF978B91987A6A422732BF7598648521AD16A14632DEA85620C0307EED113F6`.

## Git handoff

Immediately after preparation, `git status --short --untracked-files=all` had no entries and `git diff --name-only` was empty. The dependency directory is ignored, so tracked source remains clean at the accepted HEAD. Root may use this worktree for H88; this leaf did not run the producer, Gradle, npm build, tests, Docker, services, browser, push, merge, or deploy.

## Evidence sources

- Original accepted H42 command/result: `.agent/orchestration-v2/evidence/h42-context.json`, `h42-exit.json`, `h42-stdout.log`, `h42-stderr.log`.
- H42 recorded the same command at exit `0` and `added 222 packages in 11s` in the prior clean r2 worktree. The r3 invocation independently returned exit `0` with the result above.
- No product readiness or build/runtime claim is made by this preparation.
