# Handoff summary

`SOURCE_READY` for root integration.

Changed product path: `services/academic-service/academic-app/src/main/resources/db/migration/V26__campus_map.sql`.

Changed evidence paths: `packet.md`, `evidence.md`, `checks.md`, `diff.md`, and `summary.md` in this directory.

Preimage: SHA256 `4307D82508FB77784C47473FA8404AB7CFFBE944D41C695BF1297DD7D896CD50`, 17076 bytes.

Postimage: SHA256 `21B3F522634BE7505FB73A613BF5BCB68C1584B7A1724BB45EBA5C767E4058A0`, 17109 bytes.

Correction is one minimal alias hunk: `SELECT asset.* INTO asset_record` and qualified `asset.id`, `asset.plan_version_id`, `asset.format`. Static checks all exited 0. Runtime is N/A under the frozen contract; no SQL or heavy commands were run.

Limitations: the V26 source was pre-existing untracked work in the shared dirty checkout, so Git's tracked diff cannot display it; exact source diff and independent readback are recorded above. Root owns later leased runtime and independent recheck.
