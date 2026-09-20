# Import guard

Дата: 2026-09-08. Worktree: `requests-notification-authority`, baseline
`d3c31acb8cce53791a4981e5858a37d44fdc9a0e`.

## Source and destination verification

- Transport `diff.json` source SHA-256:
  `4725C54BF3F6300557697C4A756ED1565D5CCD8005F229C6C60AE3250744B2C4`.
- Transport product paths: `82`; all source hashes matched; destination guard
  passed for `80` transport-only paths.
- Repair `repair-manifest.json` source SHA-256:
  `7B64350F73272787BD383E89611B75BE10959B02B4A0342695E45C46769462F3`.
- Repair overrides: exactly `2`; destination hashes matched
  `EventConsumerIT.java` → `1FE05ED7FA57A2DC3CFA1EA77635B414D94B986D7DFFEAFC78EA544D3485FF32`
  and `RabbitDecisionRetryIT.java` →
  `95BFF3CE808623BFCD370499410A33BFC3C38465AF9097FE3016F80D49E51EDA`.

- Accepted runtime evidence source hashes verified: 2 XML artifacts; copies are
  under `imported-repair-evidence/`.
- `services/notification-bot/uv.lock` was copied only as the immutable baseline
  guard and remains SHA-256
  `C59E3D361F8F175C3D661018029AEB9DF00761B74D70F79D6D1E3971FCC59082`.
  It is excluded from the accepted product union and must not change.

## Guard result

`GUARD_OK`: source hashes, transport-only destination hashes, two repair
destination hashes and two runtime-evidence hashes all matched. The two repair
files are the only expected overrides of the imported transport snapshot; all
other imported product files retain their released hashes.
