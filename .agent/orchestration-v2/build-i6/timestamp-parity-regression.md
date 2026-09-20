# Recorded producer serializer defect and correction

## Request and reproduction

The producer must match the current `Resolve-TrustedBuildArtifacts` canonical
serializer byte-for-byte. The first pure run used the normal PowerShell
`DateTimeOffset.ToString('o')` value
`2026-09-16T00:01:00.0000000+00:00` in a synthetic command entry. The check
exited `1`. PowerShell `ConvertFrom-Json` coerced that value to `DateTime`, so
the accepted serializer emitted `09/16/2026 03:01:00`, while producer output
retained the ISO text. The mismatch was observed before any build or artifact
operation.

## New evidence and correction

The producer now formats command timestamps as
`yyyy-MM-dd HH:mm:ss.fffffff+00:00`. `DateTimeOffset.Parse` accepts the value,
the JSON reader preserves it as a string, and the accepted serializer returns
identical canonical JSON. The correction is confined to
`producer.ps1`/`producer-pure-check.ps1`; no product or R3 harness source was
changed. The recheck exits `0` and asserts the wire timestamp type is `String`.

## Failure-report path correction

An independent negative preflight with a custom `-ManifestPath` reproduced a
second producer defect: the top-level PowerShell parameter and internal
`$script:manifestPath` differed only by case, so initialization reset the
parameter and the failure report incorrectly named the default manifest path.
The correction snapshots the public `ManifestPath` and `RunsRoot` values before
initializing internal state, then uses those snapshots for path resolution.
The final negative run `20260915T213650170Z-27ac301f7496486c98a2f3c0ad267ad5`
exited `1` on the expected revision mismatch before any build command;
`run-report.json` records the requested `negative-failure-final.json` path and
that manifest is absent. Raw top-level stderr is in
`negative-final.stderr.log` (stdout is empty).

## Decision and limit

This is a bounded producer compatibility correction required by the accepted
consumer contract. It does not authorize H41 or imply build/runtime evidence.
