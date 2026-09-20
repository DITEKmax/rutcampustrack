from pathlib import Path
import hashlib
import os
import uuid

target = Path('proto/academic.proto')
expected_sha = '2B6206897E7145D3867D7DBA669765843BEFBA44966A4D4EC6110142100B7DBC'
raw = target.read_bytes()
actual_sha = hashlib.sha256(raw).hexdigest().upper()
assert actual_sha == expected_sha, f'prehash mismatch: {actual_sha}'
assert len(raw) == 8298, f'prelength mismatch: {len(raw)}'
assert raw.endswith(b'\n') and not raw.endswith(b'\n\n'), 'unexpected EOF shape'
assert b'optional int64 homework_id = 4;' in raw, 'existing academic correction missing'
assert raw.count(b'CampusMap') == 0, 'CampusMap already present'
service_marker = b'  rpc GetHomeworkBindings (HomeworkBindingsRequest) returns (HomeworkBindingsResponse);\n}'
declaration_marker = b'message HomeworkBindingsResponse {\n  repeated HomeworkBindingResponse bindings = 1;\n}\r\n'
assert raw.count(service_marker) == 1, f'service marker count: {raw.count(service_marker)}'
assert raw.count(declaration_marker) == 1, f'declaration marker count: {raw.count(declaration_marker)}'
rpc_insert = (
    b'  rpc GetHomeworkBindings (HomeworkBindingsRequest) returns (HomeworkBindingsResponse);\n'
    b'\n'
    b'  rpc GetCampusMapManifest (CampusMapManifestRequest) returns (CampusMapManifestResponse);\n'
    b'  rpc GetCampusFloorPlan (CampusMapFloorRequest) returns (CampusMapPlanResponse);\n'
    b'  rpc ReadCampusMapAsset (CampusMapAssetRequest) returns (stream CampusMapAssetChunk);\n'
    b'  rpc RecordCampusFloorOpen (CampusMapOpenRequest) returns (CampusMapOpenAck);\n'
)
service_replacement = rpc_insert + b'}'
map_declarations = (
    b'\n'
    b'enum CampusMapFormat {\n'
    b'  CAMPUS_MAP_FORMAT_UNSPECIFIED = 0;\n'
    b'  CAMPUS_MAP_FORMAT_PNG = 1;\n'
    b'  CAMPUS_MAP_FORMAT_SVG = 2;\n'
    b'}\n'
    b'\n'
    b'enum CampusMapFormatState {\n'
    b'  CAMPUS_MAP_FORMAT_STATE_UNSPECIFIED = 0;\n'
    b'  CAMPUS_MAP_FORMAT_STATE_ABSENT = 1;\n'
    b'  CAMPUS_MAP_FORMAT_STATE_PROCESSING = 2;\n'
    b'  CAMPUS_MAP_FORMAT_STATE_READY = 3;\n'
    b'  CAMPUS_MAP_FORMAT_STATE_FAILED = 4;\n'
    b'}\n'
    b'\n'
    b'message CampusMapManifestRequest {\n'
    b'  int64 known_revision = 1;\n'
    b'}\n'
    b'\n'
    b'message CampusMapManifestUnchanged {\n'
    b'  int64 revision = 1;\n'
    b'}\n'
    b'\n'
    b'message CampusMapManifestResponse {\n'
    b'  oneof result {\n'
    b'    CampusMapManifestUnchanged unchanged = 1;\n'
    b'    CampusMapManifest manifest = 2;\n'
    b'  }\n'
    b'}\n'
    b'\n'
    b'message CampusMapManifest {\n'
    b'  int32 schema_version = 1;\n'
    b'  int32 validation_policy_version = 2;\n'
    b'  int64 revision = 3;\n'
    b'  repeated CampusMapBuilding buildings = 4;\n'
    b'}\n'
    b'\n'
    b'message CampusMapBuilding {\n'
    b'  string id = 1;\n'
    b'  string label = 2;\n'
    b'  repeated CampusMapFloor floors = 3;\n'
    b'}\n'
    b'\n'
    b'message CampusMapFloor {\n'
    b'  string id = 1;\n'
    b'  string label = 2;\n'
    b'  CampusMapPlan plan = 3;\n'
    b'}\n'
    b'\n'
    b'message CampusMapPlan {\n'
    b'  string building_id = 1;\n'
    b'  string floor_id = 2;\n'
    b'  int64 version = 3;\n'
    b'  string label = 4;\n'
    b'  CampusMapFormatSlot png = 5;\n'
    b'  CampusMapFormatSlot svg = 6;\n'
    b'}\n'
    b'\n'
    b'message CampusMapFormatSlot {\n'
    b'  CampusMapFormat format = 1;\n'
    b'  CampusMapFormatState state = 2;\n'
    b'  string content_type = 3;\n'
    b'  optional string asset_id = 4;\n'
    b'  int64 bytes = 5;\n'
    b'  optional string sha256 = 6;\n'
    b'  optional int32 width = 7;\n'
    b'  optional int32 height = 8;\n'
    b'  repeated double view_box = 9;\n'
    b'}\n'
    b'\n'
    b'message CampusMapFloorRequest {\n'
    b'  string building_id = 1;\n'
    b'  string floor_id = 2;\n'
    b'}\n'
    b'\n'
    b'message CampusMapPlanResponse {\n'
    b'  oneof result {\n'
    b'    CampusMapPlan plan = 1;\n'
    b'    Empty no_plan = 2;\n'
    b'  }\n'
    b'}\n'
    b'\n'
    b'message CampusMapAssetRequest {\n'
    b'  string building_id = 1;\n'
    b'  string floor_id = 2;\n'
    b'  int64 version = 3;\n'
    b'  CampusMapFormat format = 4;\n'
    b'  string asset_id = 5;\n'
    b'}\n'
    b'\n'
    b'message CampusMapAssetChunk {\n'
    b'  bytes data = 1;\n'
    b'  int64 offset = 2;\n'
    b'}\n'
    b'\n'
    b'message CampusMapOpenRequest {\n'
    b'  string building_id = 1;\n'
    b'  string floor_id = 2;\n'
    b'  string intent_id = 3;\n'
    b'}\n'
    b'\n'
    b'message CampusMapOpenAck {\n'
    b'  bool accepted = 1;\n'
    b'}\n'
)
new_raw = raw.replace(service_marker, service_replacement, 1)
new_raw = new_raw.replace(declaration_marker, declaration_marker + map_declarations, 1)
assert new_raw.count(b'CampusMap') == 37, f'CampusMap declaration count: {new_raw.count(b"CampusMap")}'
restored = new_raw.replace(declaration_marker + map_declarations, declaration_marker, 1)
restored = restored.replace(service_replacement, service_marker, 1)
assert restored == raw, 'existing bytes were not preserved by inverse replacement'
tmp = target.with_name(target.name + '.map-correction-' + uuid.uuid4().hex + '.tmp')
try:
    with tmp.open('wb') as handle:
        handle.write(new_raw)
        handle.flush()
        os.fsync(handle.fileno())
    os.replace(tmp, target)
finally:
    if tmp.exists():
        tmp.unlink()
post_raw = target.read_bytes()
post_sha = hashlib.sha256(post_raw).hexdigest().upper()
assert post_raw.startswith(raw[:raw.index(declaration_marker)])
assert post_sha != expected_sha
print(f'READY pre_sha={actual_sha} post_sha={post_sha} pre_bytes={len(raw)} post_bytes={len(post_raw)} campus_map_count={post_raw.count(b"CampusMap")}')