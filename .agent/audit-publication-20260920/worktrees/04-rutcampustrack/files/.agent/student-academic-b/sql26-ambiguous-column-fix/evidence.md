# Evidence

Status: `SOURCE_READY`.

## Guarded source mutation

The pre-write guard checked the exact V26 path against the frozen contract:

`$path = 'services/academic-service/academic-app/src/main/resources/db/migration/V26__campus_map.sql'; $hash = (Get-FileHash -Algorithm SHA256 -LiteralPath $path).Hash.ToUpperInvariant(); $bytes = (Get-Item -LiteralPath $path).Length; if ($hash -ne '4307D82508FB77784C47473FA8404AB7CFFBE944D41C695BF1297DD7D896CD50') { exit 1 }; if ($bytes -ne 17076) { exit 1 }`

Result: exit code `0`; pre-SHA256 `4307D82508FB77784C47473FA8404AB7CFFBE944D41C695BF1297DD7D896CD50`; pre-bytes `17076`.

## Source readback

The changed function read back as:

```sql
    IF NEW.state = 'READY' THEN
        SELECT asset.* INTO asset_record
        FROM campus_map_asset AS asset
        WHERE asset.id = NEW.asset_id
          AND asset.plan_version_id = NEW.plan_version_id
          AND asset.format = NEW.format;
```

This keeps the selected value assigned to `asset_record` and removes the collision between the local `plan_version_id` variable and the table column. The existing `asset_record.bytes`, `sha256`, `content_type`, `width`, and `height` comparison remains unchanged.

## Post-image

Post-SHA256: `21B3F522634BE7505FB73A613BF5BCB68C1584B7A1724BB45EBA5C767E4058A0`.

Post-bytes: `17109`.

The byte delta is `+33`, matching alias qualification in the single lookup hunk.

## Scope and runtime

The only product path mutated by this leaf is the V26 SQL file named in `packet.md`. Evidence files are confined to this directory. The V26 path was already untracked in the dirty checkout before this leaf; the exact source diff is therefore recorded in `diff.md` rather than inferred from a tracked Git diff.

Runtime evidence is `N/A by contract`: root explicitly prohibited SQL, Flyway, Gradle, Docker, Testcontainers, and product runtime for this leaf. The prior SQLSTATE 42702 reproduction is recorded in the frozen context; this leaf supplies source-level correction and static evidence only.
