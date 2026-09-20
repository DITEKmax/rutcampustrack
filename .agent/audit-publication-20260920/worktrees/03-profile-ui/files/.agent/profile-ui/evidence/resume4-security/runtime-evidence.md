# Runtime and port evidence

- revision: `8002b9ea4356b10779c5bb9a6d99746d32d78ae2`
- cwd: `C:\Users\maksd\.codex\worktrees\1456\rutcampustrack\.agent\worktrees\profile-ui`
- check started UTC: `2026-09-08T18:51:30.2050592Z`
- check ended UTC: `2026-09-08T18:53:15.9162192Z`
- exit code: `0`
- command: `$port = 18110; $listeners = [System.Net.NetworkInformation.IPGlobalProperties]::GetIPGlobalProperties().GetActiveTcpListeners() | Where-Object { $_.Port -eq $port }; [pscustomobject]@{ port = $port; free = (@($listeners).Count -eq 0); listeners = @($listeners | ForEach-Object { $_.ToString() }) } | ConvertTo-Json -Compress`
- exact output: `{"port":18110,"free":true,"listeners":[]}`

The leaf did not start a server or browser. Root owns the lease and mounted IAB
flow. After lease, the exact evidence-only startup cwd is
`.agent/profile-ui/evidence/resume-2026-09-08/harness` and the exact command is
`node .\serve.mjs`; its launcher is strict on `127.0.0.1:18110` and must be
cleaned up only by its owning process/PID.
