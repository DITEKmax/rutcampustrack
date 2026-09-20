# Independent S3 P1 review — FAIL

Reviewed frozen checkout: `C:/Users/maksd/.codex/worktrees/e31c/rutcampustrack/.agent/worktrees/requests-notification-authority`, HEAD `d3c31acb8cce53791a4981e5858a37d44fdc9a0e`.

## Finding 1 — MEDIUM — partial stored attachment lookup can silently truncate canonical inventory

**File:** `services/attendance-service/attendance-app/src/main/java/ru/rutcampustrack/attendance/studentrequest/StudentRequestService.java:1134-1141`.

**Evidence:** `resolveRequestNotification()` at lines 437-459 calls `toDetail(ticket)`. `toDetail` loads attachment documents, then, if that list is merely non-empty, discards the ticket's complete embedded `attachmentDescriptors` inventory and returns only the documents found. `headman_alerts.py:91-121` can only fetch descriptors present in that response and starts queueing at lines 135-182. The owner original `requests-transport-decision-result.md:131` and frozen resume contract lines 57-58 require expired, missing, or transient attachments to fail explicitly before any queue task and never be silently omitted. The 18-test Python evidence covers a descriptor whose Fetch RPC fails, but cannot detect a descriptor already dropped by the Java projection.

**Impact:** if a pending excuse has two canonical embedded descriptors but one `request_attachment` document is missing or corrupt, Resolve returns only the surviving descriptor. The bot fetches it successfully and queues actionable approval/rejection notifications without the missing evidence; the request may be decided on incomplete data.

**Reproduction:** persist or mock an `ExcuseTicket` with `attachmentDescriptors [A,B]`; make `findByRequestIdAndOwnerStudentIdOrderByPositionAsc(requestId, studentId)` return only document A; call `resolveRequestNotification(EXCUSE,id)`, then pass the response to `handle_headman_alert`. Current behavior exposes and fetches A, enqueues tasks, never attempts B, and raises no missing-attachment failure.

## Repair contract

- Defect: partial stored-attachment lookup truncates the canonical ticket inventory and bypasses the required fail-closed Fetch path.
- Evidence: `StudentRequestService.java:437-459,1134-1141`; `headman_alerts.py:91-121,135-182`; owner original line 131; resume packet lines 57-58.
- Correction: for notification resolution, reconcile the persisted ticket's embedded descriptor IDs and count against `request_attachment` documents and their request/owner binding, failing closed on missing, extra, duplicate, or mismatched entries. Project the matched stored documents so current retention state reaches the bot. Do not require mutable retention fields such as state or expiredAt to equal the embedded snapshot. Preserve expiry/actor/group checks and public detail behavior.
- Scope: `StudentRequestService.java`, one existing focused Java regression test, and evidence/hash manifests. No queue, public API, config, generated stub, Fetch-deadline, or `student_alerts` changes.
- Verification: focused Java test with `[A,B]` plus only A stored must prove no successful `NotificationResolution`; complete matched inventory succeeds; zero attachments stays valid. Retain existing selected Java 20/20, Python 18/18, package-generation equality, and 84-path source-freeze/hash guard. Require a fresh independent recheck.

## Reviewed evidence

Owner original SHA-256 `DF488B7D6012230AA6F772D9FC37ABCAAF186AD4951D5B1D8CCBDEB77C55B1F2`; immutable transport diff `4725C54BF3F6300557697C4A756ED1565D5CCD8005F229C6C60AE3250744B2C4`; repair manifest `BDDEC9DF3754E7AA149FAEF4A7BF2D2BAC34D41BFD90EEA1DECB3596764338F0`; current-path manifest `A01BC515F016D8CF90DA515D03906B1934CBF3960B888F6D974852EAFA955DD0`; checks `E8BE1A655A24DA454CD7F565D56AD4D7D4A31573242EBBEB0359171C0D79BC36`; generator evidence `9E9B434C479CE4918A07709169625D8688C6B2D22F18A4197C05C5FBDF230D60`.

The reviewer independently recomputed the 84-file union with zero mismatches. Python 18 tests passed in 4.33 seconds and py_compile exited 0. Corrected Gradle session 30926 exited 0 with 20 tests and no failures or errors. Generated outputs match. Real server, Mongo, Rabbit, Academic, and Telegram runtime remain untested as stated. The separately reserved four-file Fetch-deadline and `student_alerts` dependency-propagation repair remains open and did not cause this P1 finding.
