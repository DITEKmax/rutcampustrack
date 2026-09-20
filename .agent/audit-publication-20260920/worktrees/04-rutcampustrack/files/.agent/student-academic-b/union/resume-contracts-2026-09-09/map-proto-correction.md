# Campus Map proto correction evidence

Captured: 2026-09-09 after the exact Campus Map ledger readback. Risk S3. This correction is limited to the D proto ledger in proto/academic.proto, the academic line in file-sha256.md, and this evidence file.

## Authority and scope

- Frozen union contract SHA256: C9C4DC2C4BE2229AFF3CEDB27E0E43634329EBA5AC83CE42A889E34C33DAFC74.
- D original backend patch contract SHA256: F7DF5F4074AE4C205889A2EFCB4D3B8189ECCD7FCB8BE43A0C5AB1E4BE35D659.
- Product mutation: only proto/academic.proto. Evidence mutation: only the academic manifest line and this file. current-status.md was not changed.
- Preimage: SHA256 2B6206897E7145D3867D7DBA669765843BEFBA44966A4D4EC6110142100B7DBC, 8298 bytes; CampusMap count 0.

## Required Campus Map ledger

- AcademicGrpcService RPCs: GetCampusMapManifest(CampusMapManifestRequest) returns CampusMapManifestResponse; GetCampusFloorPlan(CampusMapFloorRequest) returns CampusMapPlanResponse; ReadCampusMapAsset(CampusMapAssetRequest) returns stream CampusMapAssetChunk; RecordCampusFloorOpen(CampusMapOpenRequest) returns CampusMapOpenAck.
- Manifest messages: CampusMapManifestRequest.known_revision int64 tag 1; CampusMapManifestUnchanged.revision int64 tag 1; CampusMapManifestResponse oneof unchanged tag 1 or manifest tag 2; CampusMapManifest schema_version int32 tag 1, validation_policy_version int32 tag 2, revision int64 tag 3, buildings repeated CampusMapBuilding tag 4.
- Hierarchy messages: CampusMapBuilding.id string 1, label string 2, floors repeated CampusMapFloor 3; CampusMapFloor.id string 1, label string 2, plan CampusMapPlan 3; CampusMapPlan.building_id string 1, floor_id string 2, version int64 3, label string 4, png/svg CampusMapFormatSlot 5/6.
- Format slot: CampusMapFormat format 1, CampusMapFormatState state 2, content_type string 3, optional asset_id string 4, bytes int64 5, optional sha256 string 6, optional width/height int32 7/8, view_box repeated double 9.
- Floor/asset/open messages: CampusMapFloorRequest building_id/floor_id string tags 1/2; CampusMapPlanResponse oneof plan CampusMapPlan tag 1 or no_plan Empty tag 2; CampusMapAssetRequest building_id string 1, floor_id string 2, version int64 3, format CampusMapFormat 4, asset_id string 5; CampusMapAssetChunk data bytes 1, offset int64 2; CampusMapOpenRequest building_id/floor_id/intent_id string tags 1/2/3; CampusMapOpenAck.accepted bool tag 1.
- Enums: CampusMapFormat values CAMPUS_MAP_FORMAT_UNSPECIFIED 0, CAMPUS_MAP_FORMAT_PNG 1, CAMPUS_MAP_FORMAT_SVG 2; CampusMapFormatState values CAMPUS_MAP_FORMAT_STATE_UNSPECIFIED 0, ABSENT 1, PROCESSING 2, READY 3, FAILED 4.

## Guard and verification evidence

- Guarded writer preconditions passed: exact prehash/length, existing homework_id tag, CampusMap absence, unique service marker, unique declaration marker, and inverse replacement preservation.
- The first writer process exited 1 after the atomic replace because its final startswith assertion incorrectly assumed the old prefix remains unchanged when RPCs are inserted before the declaration marker. No second product write was performed.
- Targeted final readback command exit 0: post SHA256 1A9FB1B357AC394AFC44906FE6888756DE1B1329E729F6BEB695B25534DB4B45, 10670 bytes, CampusMap count 37; all required RPC/message/field/enum probes passed.
- rg readback exit 0 found RPCs at lines 64-67 and CampusMap declarations at lines 319-419.
- git diff --check -- proto/academic.proto exit 0. Git emitted only its LF-to-CRLF normalization warning.
- git diff --numstat -- proto/academic.proto exit 0: 236 additions, 0 deletions.
- Manifest academic line was guarded by exact old-line count 1 and inverse byte preservation; the updated line is recorded in file-sha256.md.

## Runtime and limitations

- Runtime evidence: N/A. No Gradle, protoc, migration, database, generated-source, Java, service, or product runtime check was run in this correction.
- The correction verifies source shape and compatibility-preserving additive tags only. Root must decide whether to rerun the already-passed heavy compile/proto lease and route independent Sol review.
- No SQL, full B0, full-role, B1, frontend, OpenAPI, TypeScript, current-status, or foreign dirty work was changed or claimed.
- No Terra escalation gate was opened.
