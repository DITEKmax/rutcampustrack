# L5A → union — frozen integration proposal, 2026-09-20

Status: inventory/contract only; root authorization required for isolated implementation. Risk S3. No checkout, merge, stage, source edit, build or runtime performed in this stage.

## 1. Goal

Integrate accepted L5A authority plus accepted H75 concurrency regression into a **new isolated checkout based on trusted union426**, preserving existing Maps/Access/Requests/Homework work. Keep immutable426 and its H57 artifacts available to Requests. No L5B physical lifecycle/closure activation and no frontend adaptation in this bounded integration.

## 2. Context/evidence

RULES SHA B256A175274987DA9710D804B3C050A5DBCB47D8448CC03644D74168B52A437A. Root packets LESSONS-L5A-UNION-INVENTORY, L5A-ACCEPTED, H75-ACCEPTED are authority. Source HEAD27860f24eb380c782311bbe0cbb65c3d2c75d85d → parentf59b3b9951c971bde42265ae67df8754dc594a58 → parentb8220ac92125a8afa37598b270aa4fab7aa1f470. Target426a15b6b42e816deaa3ca5c50437e0964aaf85e → parentd7ec16572db325d944f1a1fbd3b4960b827d09c3 → parentb822. Git merge-base is b822; it is **not** the immediate parent of426.

`path-inventory.json` is the complete machine-derived path authority: source unique46, target58 since common base, intersection2;44 source-only-importable,0 already-identical,2 independently modified. H75 changes an existing L5A test path; no47-path claim. Git blob IDs distinguish exact source versions independent of working-tree newline conventions. These are static source comparisons, not semantic integration proof.

Earlier current FK deadlock claim is withdrawn by actual H74 FOR NO KEY UPDATE; do not add a product lock-order correction. H75 wholeclass7/full independent test review PASS pertains to accepted source; counts across prior H60/H64/H69/H70/H75 overlap and are not summed. Existing source-stage tests remain valid evidence but do not prove the combined target wiring.

## 3. Relevant scope / sole writer

After explicit root GO, one fresh assigned Luna developer in a new isolated worktree rooted at exact426. No writer in accepted source, runtime-build-r2 or main/E. Exact change allowlist =46 paths in inventory;44 source-only paths must match accepted278 Git blobs. Two overlap files are composed as below. Own packet/evidence outside product allowlist. Any additional source/test path requires a concrete compile/integration defect and root-approved bounded extension; no automatic whole-directory import.

No tracked proto/generated/schema/lockfile/shared-security changes are authorized by this proposal. Retain target versions of all files outside46, including target-only56 paths. Existing untracked evidence is never staged/copied into product commits.

## 4. Required behavior / exact overlap resolution

### academic-app/build.gradle.kts

Compose the two independent additions: retain target `implementation("com.google.api.grpc:proto-google-common-protos:2.29.0")`; retain source PostgreSQL `compileOnly("org.postgresql:postgresql")` and `testCompileOnly("org.postgresql:postgresql")` alongside existing runtimeOnly. Do not substitute runtimeOnly for compile dependencies used by typed PostgreSQL handling. Preserve target plugins/tasks/dependencies and source integration-test setup; no version upgrades or lockfile regeneration.

### AcademicGrpcServiceImpl.java

Keep target complete map/projection/Homework/identity behavior and add accepted source assignment authority. One production `@Autowired` constructor must inject AssignmentRepository, SemesterRepository, UserRoleGrantRepository **and** StudentProjectionScopeService/CampusMapReadService, alongside existing read/user/group/subject/homework/completion/rate-limiter dependencies. No nullable required service in production. Preserve direct-test constructor signatures already used by source/target via unannotated delegating overloads if needed; do not select the legacy TSG constructor in Spring or restore TSG as runtime authority. Source's existing direct-test compatibility path remains test-only and fail-closed for missing assignment authority.

Preserve accepted source `getTeacherSubjects` current-effective interval, ACTIVE teacher grant, concrete effective end, deterministic ordering and assignment identity; preserve `getAssignmentsByIds` positive/missing-ID behavior and immutable snapshots. No invented policy expansion or cached live permissions.

Preserve target `getCampusMapManifest`, `getCampusFloorPlan`, `readCampusMapAsset` and helpers, exact signed `StudentHomeworkGrpcIdentity.requireClaims()` checks, cancellation-aware64KiB streaming, status mapping and manifest conversions. Preserve `resolveStudentProjectionScope`, CLAIMS context source, independent rank/membership fields and typed error mapping. Do not read actor/group from new client payloads or drop claimed service wiring while resolving constructor conflicts.

Direct-test call sites exist in AcademicAssignmentGrpcContractTest (source), CampusMapGrpcReadTest/StudentProjectionGrpcServiceTest (target) and StudentHomeworkGrpcIdentityInterceptorTest (both). Retaining their existing signatures avoids adding unrelated test paths merely to repair constructors; combined compilation and tests must prove overload resolution and production Spring wiring. A clean textual merge is insufficient.

### Unchanged schema/proto / migration-history compatibility

Accepted L5A changes **no migration file and no proto file** versus b822. Academic migration Git tree is identical between278 and426, including existing V25 foundation and V26 map migration. Therefore this integration must add no version, rewrite no applied checksum and perform no repair/baseline operation. Compare migration inventory/checksums again at integrated freeze to prevent accidental overwrite.

On a fresh isolated PostgreSQL database, existing migrations and V25 provenance guards remain active; runtime tests validate entity/repository compatibility. On a database previously migrated from426, unchanged SQL should require no new Flyway DDL/checksum update, but this inventory did not inspect any actual schema-history/data state. Only an explicitly authorized later existing-DB validation could prove that environment; no production migration or data claim follows. Ambiguous legacy rows must not be seeded/deleted/backfilled to make tests pass.

Target `proto/academic.proto` adds projection contracts and removes Academic binding RPC/types now owned by `proto/schedule.proto`. Keep both target proto blobs unchanged; source assignment message/RPC tags already exist there. Generate/compile only through normal isolated build tasks; do not copy source generated classes or restore Academic binding ownership. Retain target reserved fields/numbers and typed projection errors.

## 5. Constraints

Source-only import means exact accepted version, not a new cleanup/refactor opportunity. Typed assignment closure remains409 not-ready. No Schedule fence/binding/one-off implementation, public mass-cancel feature work, SC02 data guess, new permission policy or frontend product completion. Source acceptance does not imply integrated product readiness. No changes to Requests immutable426 runtime inputs/H57 artifacts. Heavy checks only by root lease; no push/deploy/main merge/promotion.

## 6. Existing patterns / dependencies

Follow accepted union isolated single-writer/inventory workflow, root source acceptance and fresh FULL Sol integration review. Academic REST DTO/entity/repository/semester/exception paths plus Attendance ReportServiceTest are in exact46. No Attendance production file changes in source delta. Source typed PostgreSQL exception dependency and target google common protos support different compiled paths and both remain necessary. Auth/shared code lies outside changed pathset but signed identity behavior inside merged gRPC must be preserved.

## 7. Acceptance criteria

1. New integration baseline/root identity explicit; runtime-build-r2 remains426 and unchanged.
2. Diff against426 contains exactly approved46 paths,44 matching accepted source blobs; two composed files reviewed against **both** parent deltas. Any justified extension is separately frozen before changes.
3. Target-only56 paths, target proto and Academic migration tree remain blob-identical; no generated/checksum drift committed.
4. Production constructor wires assignment + map + projection together; signed authorization/stream cancellation/projection errors and accepted assignment invariants remain intact.
5. Applicable combined checks below pass with actual command/revision/exit/raw/XML/cleanup evidence, then fresh FULL Sol reviews the full integration delta. No source-stage PASS substituted for combined validation.

## 8. Verification proposal — future, NOT RUN

Stage0 static: verify ancestry/path classification,44 exact blobs, target-only preservation, migration/proto blob inventories, no conflict markers and scoped diff. Then compile Academic API/app and its tests plus affected Attendance test consumers on target proto/generated inputs through normal Gradle tasks. Compiler failures are evidence for bounded correction, not permission for broad API rewrites.

Bounded unit selection: six accepted L5A classes AssignmentAuthorityContractTest, AssignmentClosureContractTest, SubjectAssignmentAuthorityContractTest, SubjectTypeUserTypeTest, AcademicAssignmentGrpcContractTest, SemesterAssignmentLockContractTest; plus actual overlapping-handler consumers CampusMapGrpcReadTest, StudentProjectionGrpcServiceTest and StudentHomeworkGrpcIdentityInterceptorTest. These target constructor/handler/identity integration, not a repeat of all unrelated product tests. Attendance ReportServiceTest is the accepted changed consumer test and should run on combined baseline.

Root chooses one bounded real-PG combined union execution for six L5A IT classes: AssignmentAuthorityIT (includes accepted H75), AcademicAssignmentGrpcIT, SemesterAssignmentAuthorityIT, SubjectAssignmentAuthorityIT, SubjectServiceIT and AcademicGrpcIT. Their Spring contexts must exercise the combined gRPC bean and unchanged migrations. Do not additionally rerun H75 method/class immediately; it is covered in that union. Expand checks only for new failures or uncovered concrete integration risk. Maps/Projection handler unit checks above preserve directly merged logic; unrelated service/whole-product reruns are not inferred.

Build/public runtime activation beyond those checks is a separate root stage; no automatic rebuild of H57 artifacts. Record exact selectors/resources in execution packet after root approves this proposal. Root owns queue, actual evidence/cleanup and fresh independent FULL review. Current inventory runtime N/A.

## 9. Do not

No cherry-pick/checkout/integration in inventory stage; no E whole-import, source46 count inflation, destructive Git operation, source hash normalizing edit, stale proto restore, Flyway repair/checksum change, silent missing bean fallback, repeated accepted source audit, artificial deadlock fix, or claim that zero textual conflicts proves behavior.
