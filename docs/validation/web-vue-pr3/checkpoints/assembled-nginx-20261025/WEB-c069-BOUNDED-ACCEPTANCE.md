# WEB c069 actual outcome — bounded independent qualification

Source/read-only qualification of the original Root run; reviewer Auth/API/browser/build/tests/replay0. WEB is not replayed.

Raw receipt `/workspace/rct-pr3-runtime/visual-readiness-20261025/nginx-auth-readonly-prep/run/web/receipt.json`, SHA256 `34f00f3d1b02c922407fa111f4aaa037886cbf93f9dbf0167187d0ad23c1b2fe`, case WEB/source240/runnerc069. Original EXIT1, completed=false, captureComplete=false and apiProof=null remain. Phase KNOWN_STOP_CLEANUP_QUALIFIED, unknownHold=false, sourceUnchanged=true. Failure is exactly NATIVE_KNOWN_TEACHER_CATALOG_SCOPE_ABSENT at the known-ID assertion.

Accepted bounded slices:

- Real normal login200 and browser canonical session GET200, with durable issued-own-handle capture. Reaching capture assertion409 after successful login/confirmOwner and passing that assertion establishes currentBearer/canonical actor2/ownSID/TEACHER at this boundary. Cleanup separately rechecks current exact owner before delete.
- Native catalog GET200 belongs to that canonical bearer. Reaching failure413 proves original response JSON parsed and passed411's object/groups/subjectOptions/taughtSubjectOptions-array shape. This is the actual top-level schema only, not full semantic validation of every field.
- Native same-origin SockJS socket1, ticket1, actual received CONNECTED1, sent CONNECT1/SUBSCRIBE1, expected transport path, socket errors0, page errors0, blocked0. Connection/handshake is observed; sent subscription is not delivery/long-duration/reconnect proof.
- One exact-current-own technical session DELETE204, same old Bearer401, no fallback, browser closed and no HOLD. This is not native UI logout PASS. PREF PUT0, domain writes0, screenshots0.

Known group1/subject1 content is NOTPROVEN by this run: the safe catalog projection was local-only and not persisted before failure, and strict numeric comparisons cannot match the BFF wire String IDs. `TeacherApiModels.java` declares CatalogGroup.id and StatsSubjectOption.groupId/subjectId as String; current teacher-client normalizeCatalog deliberately parses them into numbers for the UI. Therefore `group?.id === 1` / option numeric comparisons are a driver wire-adapter defect, not evidence that known scope is absent or that product normalization is broken. The receipt is not edited into success.

Any correction must preserve immutable c069/WEB evidence, compare exact wire strings, persist only safe counts/booleans before the assertion and apply only to the new PWA platform case after independent source/hash/final binding review and separate Root GO. HTTPS Auth/WSS, native TMA and external WebPush remain NOT_RUN. Do not repeat WEB for counts or silently generalize this bounded slice to full Auth/provider functionality.
