# Security invalidation repair — pre-state

- Recorded: 2026-09-08; nested checkout `C:/Users/maksd/.codex/worktrees/1456/rutcampustrack/.agent/worktrees/profile-ui`
- Scope: `frontends/mobile-core/src/features/profile/SecurityScreen.vue` only, plus this evidence directory.
- Risk: S3 auth/security UI boundary; no server, credential, session or production mutation.
- Base revision: `8002b9ea4356b10779c5bb9a6d99746d32d78ae2` (detached HEAD).
- Product target before correction: 10437 bytes; SHA-256 `BCC12C11D21BD9F0F13544C5FBA5E6B34BD8FD5EAFED6D61D2E0D8208C5139AD`.
- Pre-edit nested Git status: `?? .agent/profile-ui/`; `?? frontends/mobile-core/src/features/profile/`.
- Foreign work observed in the outer checkout: auth-service Java changes, outer `.agent/`, and outer `.agent/worktrees/`; these remain untouched.
- Existing mounted reproduction: `.agent/student-auth-a/profile/root-mounted-baseline.md` records `ACCOUNT_INVALIDATED` retaining entered values and allowing the password callback. The nested prior baseline is `evidence/resume-2026-09-08/security-browser-baseline.md`. No password values are copied here.
- Required correction: when external `props.error?.code` is `ACCOUNT_INVALIDATED`, clear all three fields, hide all reveals, reset `submitted` and local error, block submit independently and disable the button; retain the external error. Removing that external error must leave an empty usable form. Other errors retain input for retry. Existing success/unmount clearing remains.
- Source/contract note: the requested packet and checkpoint are authoritative at `.agent/profile-ui/packet.md` and `.agent/profile-ui/shutdown-checkpoint-2026-09-08.md` inside this nested checkout; the similarly named outer paths were absent/stale and were not modified.
