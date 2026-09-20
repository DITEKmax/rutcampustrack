# Resume 4 mounted profile QA

Date: 2026-09-08. Risk S3. Revision: `8002b9ea4356b10779c5bb9a6d99746d32d78ae2`.

## Runtime

- CWD: `C:/Users/maksd/.codex/worktrees/1456/rutcampustrack/.agent/worktrees/profile-ui/.agent/profile-ui/evidence/resume-2026-09-08/harness`
- Command: `node .\\serve.mjs`
- Server PID: `46404` (`C:/Program Files/nodejs/node.exe`)
- Bind: `127.0.0.1:18110`, strict port
- HTTP probe: `curl.exe -sS http://127.0.0.1:18110/`, exit 0; returned the Vite fixture HTML.
- Browser: Codex in-app browser, mounted actual Vue fixture at `http://127.0.0.1:18110/`.
- Source manifest: nested `.agent/profile-ui/evidence/resume4-security/source-manifest.json`, SHA256 `E594C4B422253BD0B9F8A2DE4659F5AB58475413C85DB58866961F5498931254`.
- Checks manifest: nested `.agent/profile-ui/evidence/resume4-security/checks.json`, SHA256 `B7DDA706A053EDF91D4DDF56810F5BAB3FCCD47F321AD05B7E3E28D5044EF0ED`.

## Mounted observations

PASS — Security invalidation:

- Filled all three synthetic password fields and enabled all three reveal controls. Values are intentionally omitted from this artifact.
- Triggered external `ACCOUNT_INVALIDATED` through the fixture control.
- All three rendered fields became empty; all three reveal controls returned to the hidden state.
- External message `Аккаунт больше недоступен` remained visible.
- `Сменить пароль` reported `isEnabled() === false`.
- Fixture callback count remained `Отправок: 0`.

PASS — recovery after invalidation:

- Cleared the external invalidation, entered a fresh policy-valid synthetic input and used the real form submit path.
- Callback count changed `0 -> 1`; fixture reported `Последний результат: получен`.
- The component cleared the three fields after successful callback completion.

PASS — ordinary error retry:

- Triggered external `CURRENT_PASSWORD_INVALID` after entering synthetic values and enabling reveal controls.
- All three visible values remained rendered, the ordinary field error remained visible, and `Сменить пароль` reported `isEnabled() === true`.
- Static stable source confines the immediate watcher to `props.error?.code === 'ACCOUNT_INVALIDATED'`; other external error codes do not invoke `clearSensitiveForm`.

PASS — mounted profile surface:

- Mounted fixture routes `Профиль`, `Смена роли`, `Оформление`, `Безопасность`, `Сеансы`, `История`, and the three sourced `Ещё` variants. Their real component headings were respectively present (`Профиль`, `Сменить роль`, `Оформление`, `Безопасность`, `Активные сеансы`, `История аккаунта`, `Ещё`).
- Switched the fixture between dark and light surfaces and selected the real `Светлая` appearance radio; its controlled selected state updated.
- At 390x844 the profile with the long-name fixture remained legible and all six profile rows were reachable.
- At width 320 with the fixture 200% text mode, the profile main element had `clientWidth === scrollWidth === 320`; no descendant exceeded the component bounds. The desktop fixture shell occupied the full CSS viewport while the browser's persistent vertical scrollbar reduced `documentElement.clientWidth` to 305, so the observed 15 px document-level scrollbar belongs to the fixture shell, not the clipped profile component.
- Keyboard Tab traversal reached the fixture controls and then the component in DOM order: back, current-password field/reveal, new-password field/reveal. No focus trap was observed.
- Browser console warning/error query returned an empty list after the route/theme/responsive flows.

## Verdict and limits

Mounted QA: PASS for the resume-4 acceptance criteria. No real Auth/BFF/PWA/TMA service was contacted. Network/policy error retention is supported by the exact invalidation-only watcher and the mounted ordinary-error proof; the fixture has no separate network/policy injection buttons. Server cleanup and an independent post-cleanup port check are recorded separately after the review handoff.
