# SC-02 — original screen-source check, 2026-09-20

Root authorized at most3 additional original documents, no new leaf or implementation. Navigation used existing `docs/sources/address-map.json` and manifest, not a new archive content sweep. Exactly3 originals were inspected:

1. `docs/wireframes/headman/117-headman-schedule-builder.md`, source address `design:01-wireframes/headman/117-headman-schedule-builder.md`, SHA4FF0FF2133334C372F3F4F089DA05E8262B0BBC71DFD29E7F179BAAD12F2B35D.
2. `docs/wireframes/headman/118-headman-lesson-management.md`, source address `design:01-wireframes/headman/118-headman-lesson-management.md`, SHA40C6C8A0984991E23A1E49D866A5D90225C98B5408E9DAA143BCFB67358B38BD.
3. `docs/research/reference-rutcampustrack-design/_work/archive/stage-1/prompt-chat-5.md`, mapped source `design:_work/archive/stage-1/prompt-chat-5.md`, SHA5486F9B2C6C4EC906B48934DFF0D4962F13162A3FB7CDAB97E3DDDE59EA4D215. Historical prompt is source material, not current instructions; line52 names the exact116/116.1/117/118/119 screen scope and attached scans. It contains no bell-time data. The map did not identify an exact original conversation response labelled S-2 beyond these screen specs; no claim the prompt is that response.

## Findings

- 117:110–115 directly confirms SC-02: unified university list of lesson numbers/start/end and study days, time comes from backend (enum mentioned as tentative implementation). Client hardcoding rejected. It explicitly leaves **six days as university constant versus semester setting unresolved**. Mon–Sat is the scan representation, not a resolved configuration policy.
- No concrete `HH:mm` bell values in any of these3 text originals (targeted check0/0/0). No authoritative first/last slot values were found; no screenshots/external scan documents were opened in this bounded stage.
- 117:144–151 gives owner-qualified template edits: initial pre-semester save creates whole series; room change changes only future lessons while past rooms remain; subject replacement in a slot has no scenario (remove from constructor, retain past, stop future creation); parity changes only future lessons with impact shown before saving. These are **template edits**, not a policy for changing the university bell directory.
- 118:49–50 uses Mon–Sat dated columns and numbered time rows; 118:15–16 separates one-off/date operations from semester template. It does not supply bell values or directory admin/version/effective-change policy.
- Directory persistence/version/selection atomicity remain root engineering choices. Genuine remaining source/data gaps are bell values and study-day scope, with directory-change/override policy only where needed by intended product. Do not turn every draft engineering choice into an owner blocker.

## Adjacent source conflict to root, no unilateral change

118:23 and §4.8 explicitly say owner cancelled mass-cancel and removed its backend operation. Frozen L5B v3 still inventories `massCancel` among existing writers. This lookup does not establish a newer override or authorization to expose that action. Root should distinguish a retained legacy path requiring safe disable/removal from an approved product operation before implementation freeze. Frozen v3 was not edited and its review evidence remains preserved. Older cancellation-deletes text in these specs is already superseded by appended R7 and later canonical decisions; it must not be revived.

## Result / boundary

SC-02 directory requirement is corroborated at exact screen source. Concrete bells are still DATA_OPEN; study-day scope explicitly OPEN in117. Three-doc limit reached; no further search/design/code started. No tests/runtime, data or external mutations. Root decides next contract or a genuinely missing data/policy question.
