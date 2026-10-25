# Assembled Vue Nginx — nonroot permission correction

**PASS for `28e9f985eaa5703a1a6a4336854f757e350dd78b`. Blocking P1/P2 findings:0.** Narrow read-only review of the two-file delta from accepted666280a2 in `/workspace/rct-worktrees/headman-web`. All prior source, producer, ownership, process cleanup, listener and budget reviews are reused.

Root's concrete input was host UID:GID1000:1000 with new artifact directories0700 and TLS directory0700/key0600. The former default-root/cap-drop configuration could not establish worker access to those private host-owned inputs. The correction uses the host's numeric nonroot UID:GID as the Compose user and records it in pins; `prepare` rejects UID0 before outputs. Nginx's nonroot master/workers retain that identity, so the known private artifacts/key can be read without widening their modes or adding DAC capabilities. This is a source-level assessment against Root's reported stat facts, not an observed Nginx startup result.

The runtime inspector now requires exact `.Config.User`, pinned/prepared user agreement, no supplementary groups, empty added capabilities and `cap_drop=ALL`. It retains no-new-privileges, read-only root, full read-only bind identity,128MiB memory/swap,0.5CPU and32PID bounds. Each of cache/run/log/tmp has the original finite size plus explicit matching UID/GID and mode0700; actual HostConfig tmpfs options must match. No shared path/artifact/key chmod or ownership mutation is added.

The unpublished source HTTP redirect listener is moved from80 to8080 while preserving its redirect route. Together with inner8081–8084 and common14240/14443, every listener is now nonprivileged; the correction does not depend on Docker's unprivileged-port sysctl. Only the same two loopback14240/14443 publications remain exposed. Common routes, CSP, Origin forwarding and the protocol-specific proof boundaries are unchanged. No Main container/configuration restart, backend mutation or additional container is included.

The extra ownership options do not expand the disk/memory forecast. Previously accepted producer settings, one retained Nginx image, serial1536MiB Node builds,512MiB disk forecast/growth ceiling,180s/250ms monitor and early2.5GiB/hard2GiB thresholds remain. Root must observe the selected container's actual admission and `nginx -t` before treating permissions/startup as runtime PASS. No new dependency installation, image build/pull or WEB rebuild is required by this fix; fresh matching PWA/TMA prefix artifacts remain the planned work.

Exact final SHA256:

- `gate.py`: `3e05096d592d31efad44a1e4313efe7ac6723e535758e0a4216b165caaef164d`
- unchanged `build.py`: `1e5ca3ebcf0ebf635890e6e77566cd6042cd0ce2b1827bfec372bfacc51127c8`
- `README.md`: `bbd5bee93a1fe30ca74eb58a481aab0c992a55203655ba244d4d8dadc6a1bc67`

The two source paths were clean at the reviewed commit. Author reports eight focused negative permission/user/group/capability/tmpfs controls, private-mode preservation and high-port checks plus AST/diff checks. Reviewer inspected the exact source delta and hashes; controls were not rerun. Reviewer Docker/build/sockets/HTTP/Auth/browser/service/owner-local operations:0. The only reviewer write is this external report. Actual HTTP Auth/SockJS remains separate from HTTPS static evidence; TLS Auth/WSS and the full group UI gate remain owner-local NOT_RUN until their own evidence.

Requested model `gpt-6.1-sol`, effort `high`; runtime telemetry unavailable, no fallback claimed. Root retains final integration/source-freeze/resource admission and the decision to start its one owned Nginx.
