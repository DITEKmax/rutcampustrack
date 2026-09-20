# Campus floor plans — staged bounded decision

07.09.2026. S2 data/read subsystem; fresh Sol xhigh read-only consultation under the owner's substantive-decision routing. Not dispatched. No writes or children.

## Goal
Freeze the minimal truthful campus/building/floor/plan read contract for final student map frame 4603:848832 in both shells.

## Context/evidence
Read active-contract.md, map-source-note.md, the final frame extract and source screenshot. Root reopened backend-conflicts R5:134 and R30:480. R5 excludes a room registry and textual floor-plan alternative. R30 requires one record with independent PNG/SVG states, preserved prior versions and server-owned demand counts. Current CampusSetting.java and AcademicReadService.fetchCampusGeofence expose only geofence row1; no verified building/floor catalog or plan subsystem exists. Mock building5/floor3 is illustrative and must not seed asserted real campus facts. Final empty viewer is supported, but an empty backend is not proof of functional selectors or a populated production catalog.

## Relevant scope
Read actual Academic campus entity/repository/gRPC, migrations, proto/academic.proto, integrated BFF query/contract patterns at d3c31acb and canonical map viewer/picker references. Identify exact minimal new data/read paths. No implementation or external data writes. Academic/proto/migrations and BFF generated contracts require sequential ownership with profile/subject/requests work; reserve actual migration numbers only at dispatch.

## Required decision
Choose authoritative building/floor catalog and versioned plan storage/read model, independent PNG/SVG state and fallback behavior, typed empty versus unavailable responses, authenticated safe asset delivery and bounded server demand accounting. Explain how data can be populated through a defined trusted write boundary without building admin-role UI or falsely treating test fixtures as live data. Missing factual campus data is an explicit limitation, never an invented list. Define deterministic default selection, missing selected floor, no buildings/no floors, ready/loading/empty/error and zoom/reset. Keep existing geofence semantics independent unless primary evidence proves shared identity. Do not infer floors from radius or IDs.

## Constraints
No room registry, map-download feature, external navigation, data scraping or admin UI. No active SVG script/foreign content exposure: safe delivery/rendering and external-reference policy must be concrete. Do not silently delete/replace prior versions or use filenames as authorization. Demand must exclude meaningless retry/prefetch inflation under a documented bounded rule; do not invent analytics PII collection. No production migration, seed writes, deploy, secrets or Figma changes.

## Existing patterns
Academic ID/FK storage, Java-first BFF DTO/OpenAPI/generated TS, signed internal identity and shared StudentApi. Existing floorplan viewer/picker tokens and zoom controls are canonical references. PWA offline scope currently schedule/homework only; map stays online and must not silently enlarge cache policy.

## Acceptance criteria
Return a finite nine-section developer packet with exact API/ownership, data invariants, source references, fixture/live distinction and integration dependencies. Include empty catalog, no floors, absent plan, independent format failure, version replacement with retained old asset, foreign/invalid asset request, SVG active-content rejection and correct demand semantics. Flag actual missing owner data separately from routine engineering decisions.

## Verification
Consultation runtime N/A. Later real PostgreSQL service/gRPC/BFF tests, safe-image delivery tests, both shell visual/keyboard/zoom/error scenarios and independent Sol high review. Fixtures prove behavior, not real campus inventory.

## Do not
No implementation, write/spawn, invented campus labels/floors/plans, whole-role PASS, broad asset platform, copy-pasted legacy commands or permissions escalation for this read-only consultation.