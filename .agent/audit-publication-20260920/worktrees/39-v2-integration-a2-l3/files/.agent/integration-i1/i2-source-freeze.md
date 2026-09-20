# I2 source freeze — accepted R5 over preserved I1

Freeze date: 2026-09-15 (Europe/Moscow)

Target: `codex/v2-integration-a2-l3`,
`C:/Users/maksd/IntelliJIDEA/rutcampustrack/.agent/worktrees/v2-integration-a2-l3`

Source: `codex/v2-requests-contract`,
`C:/Users/maksd/IntelliJIDEA/rutcampustrack/.agent/worktrees/v2-requests-contract`

Base revision for both: `b8220ac92125a8afa37598b270aa4fab7aa1f470`

Rules SHA256: `B256A175274987DA9710D804B3C050A5DBCB47D8448CC03644D74168B52A437A`

## Frozen source evidence

- Accepted R5 manifest SHA256: `ED35EDC347D30D610F7530DA767C90674A6F1853E4C76331535D8098FE3992F6`.
- Preserved pre-I1 18-row union SHA256: `EB831B86E3E61559B4B9B3755AD36F69F582039CB6D460F88B003544E210DC85`.
- Final 34-row union manifest: `i2-union-manifest.sha256`, SHA256
  `32031090D593BBC8B53869590F14D90386A97B1AD4786CDC7B6C16586C8CC266`.
- R5 snapshot source and target SHA256: `D4F97E0476CD681A9460247D9F904C7901241269B73221A097EC6BE45132CDA0`.
- R5 generated TypeScript source and target SHA256:
  `5D697D0D0615BB5BB93F0C4735B090D3420112B7B97F4FA6C3598CC31B2CA66E`.

The source branch remained at E with its pre-existing dirty 17-path product
diff plus `.agent/requests-contract-r5` metadata. It was not modified by this
transfer. The target's pre-existing I1 product and metadata were preserved.

## R5 exact tracked rows

The following 16 non-overlap rows matched the accepted R5 source SHA256 before
transfer and the target SHA256 after transfer:

| Path | SHA256 |
| --- | --- |
| `.env.prod.example` | `1D77EDA0ADB35354E61D1F44743876E94D143EC37E1C51BE9DD4E6F9354C8C8C` |
| `.github/workflows/ci.yml` | `1D6ED2F8EB9ACD7FF9E2D928B9A3C773DC909FB19CC752FECA38839A356774DF` |
| `README.md` | `816136C47D608C590DDF63B9C16E32451A4A1C82ED6AE1FBE16CD99E5AC99CC3` |
| `docker-compose.e2e.yml` | `4D840B4CA3CCCF66D0E1EA4C795154F8DF140DD3A575C65C92EF779608272232` |
| `docs/openapi/mobile-bff.json` | `D4F97E0476CD681A9460247D9F904C7901241269B73221A097EC6BE45132CDA0` |
| `docs/operations/deploy/prod-deploy-checklist.md` | `0B9CAD94786B06169C1D605714F3A37BBE4DCE5A918B97508EA4A193BF7CF74C` |
| `docs/operations/runbooks/backup-restore.md` | `574B2CCE59EAD4192238187C330E184C6C2657D1FCBBAC5B5510AFD6C77F02B4` |
| `docs/operations/runbooks/cert-renewal.md` | `EE919BD62D227BDC0741C491DF0F31798CD5012309ABC24070D870D17D93F974` |
| `docs/operations/runbooks/loki-major-upgrade.md` | `9CD74B21022DEBC3F0172E780EB1FB69D07E92514385835C7A4327CA18115C46` |
| `docs/testing/M16-vps-verify.md` | `4D4CC4B95B33ED8DD0AEF6CDF85EECCAAC0477F89646FD18B6558B39E8585F10` |
| `frontends/mobile-core/src/api/generated/mobile-bff.ts` | `5D697D0D0615BB5BB93F0C4735B090D3420112B7B97F4FA6C3598CC31B2CA66E` |
| `nginx/scripts/init-letsencrypt.sh` | `19953F6895AF52E61E26DD97B8CB72435E3ADB7D978E370946D35529D94F5258` |
| `scripts/preflight-deploy.sh` | `E940074644C99297FEA88B415B6FC9DCCE203EF7B80ECC71D1DC3E17F3950C4A` |
| `scripts/validate-env-prod.sh` | `7FA889714468B3081D090F906D91C992272C679B8068440D9C3F35961D53BAD8` |
| `services/mobile-bff/mobile-bff-api-contract/src/main/java/ru/rutcampustrack/mobilebff/contract/api/StudentApi.java` | `B8061B3E0F824210D338F9BA7FB08B33A3F1F331D07ABD81E5B3EE2513B90B1E` |
| `tests/e2e/.env.ci` | `2317922EEEB11A57539C42C19F49A208D508DE03AB4F7F21EBB0D2AF158711DB` |

The `.env.ci` value was transferred as an opaque accepted tracked row. Its
content was not opened, logged, or printed; the checksum is recorded only for
exact-copy verification.

## Critical overlap

`OpenApiSnapshotIT.java` was the only overlapping product path. The bounded
pre-transfer no-index diff had exactly two added assertion lines at the existing
I1 request path. Applying those lines produced target SHA256
`D0D715C22A77CBADFE61177454428B606A5F0476359C2B8FD879573A4A221C25`, equal to
the accepted R5 source. The I1 UUID import and nine-argument JWT fixture remain
present; no fixture or user/group assertion was replaced.

The seven bounded OpenAPI semantic deltas are the required request body plus
the six known I1 metadata changes: `LessonSchedule.startsAt` example removed,
`LessonSchedule.endsAt` example removed, `StudentRequestLesson.startsAt`
example removed, `StudentRequestLesson.endsAt` example removed,
`StudentRequestLesson.startsAt` nullable added, and
`StudentRequestLesson.endsAt` nullable added. JSON object-key ordering is
non-semantic. No snapshot regeneration or blind update ran in I2.

## Transfer boundary

Only the 17 accepted R5 tracked paths were transferred. No R5 evidence,
validator fixture, preflight mock, source AGENTS pointer, build/cache output,
secret value, or unrelated source was copied. The existing I1 source-freeze,
union manifest, historical checks and status remain available for provenance;
I1's `ERRORED_CAPACITY_METADATA_CHECKPOINT` is retained as historical state and
is not converted into an author PASS.
