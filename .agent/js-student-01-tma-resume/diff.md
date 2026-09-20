# Resume diff evidence

Checked revision: `834f0ca7975914717bf77ea50c4469b92afcfb32`
Integration revision: `e583f05140c43311b547bbaeaa55c1a7bc3c9ce9`
Integration TMA pick: `23e153f1bc6db9c311c3aaf6351eb3395eb7bb3e`

The content comparison command

```powershell
git diff --exit-code 834f0ca7975914717bf77ea50c4469b92afcfb32 codex/js-student-01-integration -- frontends/tma-vue frontends/mobile-core
```

returned exit code `0` and no output. The integration branch therefore retains
the checked TMA code and the same shared mobile-core tree.

The TMA worktree was clean before this resume run. No product code, shared
component, generated file, lockfile, configuration, API contract or test was
changed during verification. The only new paths are the four files in
`.agent/js-student-01-tma-resume/`; they contain scope, checks, runtime evidence,
parity evidence and limitations.

The previous product diff remains the authored TMA fix commit
`834f0ca…` (integration equivalent `23e153f1…`): official SDK in the HTML head,
one JSON auth helper, explicit fixture transport reuse, and preserved auth
failure behavior. This resume adds no correction because no defect reproduced.
