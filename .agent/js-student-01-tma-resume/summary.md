# Summary — JS-STUDENT-01 TMA resume verification

The assigned TMA worktree was clean at `834f0ca797…`. Integration HEAD
`e583f05140…` has no content difference from that revision for
`frontends/tma-vue` or `frontends/mobile-core`; its TMA change is the equivalent
cherry-pick `23e153f1bc…`.

The new meaningful auth check used the project's `integrationTest` task, not
the unit `test` task that excludes `*IT`: `TmaIT` passed 8/8 with zero failures.
It proves the backend's test-generated signed-initData boundary and negative
signature/auth cases without a live Telegram session. The earlier `test
--tests TmaIT` command exited 0 with zero matching tests and is recorded as
`NOT_EVIDENCE` in `checks.json`.

The local fixture command launched on port 5186. Today rendered, the available
check-in action changed to `Отмечено`, and the lesson list showed
`Отметка подтверждена`; a screenshot was emitted after the confirmed state.
The preview stopped with exit 1 because the existing dependency junction caused
Vite fs-allow warnings for font requests. The page and flow had already rendered;
this is retained as an environment limitation, with no product change. Ports
5186/5187 were free afterward.

The prior seven frontend checks, including SDK order, JSON request, 401 and
missing-host behavior, typecheck, lint, build and previous fixture browser
evidence remain preserved because the product tree is unchanged. There is no
claim of a real Telegram host launch or a live auth-service request from
Telegram. No defect or complexity gate was recorded, so no Terra escalation or
code correction was needed.
