# TeacherManualExcuse — structural review and final binding

**PASS. Blocking P1/P2 findings: 0.** Independent source review only; actual visual acceptance and Root execution GO are separate.

Final Root HEAD: `7ef451a6cfd7f0e0134816eb4ea90ca290829668`; product base: `6219ca60a322d29921575535eccbf12efa76e304`.

Final runner: `/workspace/rct-pr3-runtime/visual-readiness-20261025/teacher-manual-conditional/runner.cjs`; SHA256 `b0fb96d1e54abb8520c1944e42ede2dddc45445fd52f15bfb0aa423825603aa0`.

## Structural review reused

Reviewed structural runner `eec968ad543aa60eb2a619718c8ed28ae20b5cfc7b0541c58a697ccb350ac35a` against accepted Teacher lesson metadata runner `da0d114c5cd60b718ea534ed7fdee2ccef02558b95d82eb8f1d2be2e3b1dfa16`. The old structural package is preserved under `prep-revisions/eec-head-pending/`.

The native adapter uses one existing Teacher2 roster: lesson1/date2026-10-24/group1/subject1, with assignment1/semester2 qualification. Catalog/feed and roster must settle before any absence conclusion. Its `recordPresent && !ticketId && (status.toLowerCase() === 'excused' || symbol === 'у')` predicate matches the current product helper. The first native manual control follows the same roster order; the real detail reloads the same lesson and requalifies student identity and the no-ticket predicate. No Vue state/props or API responses are fabricated.

If no manual row exists, the runner records only this roster's bounded absence with `globalAbsenceClaimed=false`, zero PNG and unchanged `captureComplete=false` / raw EXIT1. It does not search other lessons or open an ordinary ticket. If a row exists, the limit remains two original full-page PNG: 300/light/root20 and 768/dark/root16, with Back keyboard focus only. Author and Root must inspect the originals before publication; unexpected private content does not gain publication approval from this source review.

The accepted durable intent/capture barriers, canonical owner qualification, strict late body validators, one normal Teacher2 login and optional TEACHER selection remain intact. Attachment and ordinary excuse routes now fail before forwarding; the special attachment fetch/fulfill and PDF observer were removed. Non-read, PREF and unexpected Auth gates remain. Cleanup retains one exact current own SID DELETE204 plus the same old Bearer401; unknown outcomes retain bounded transport quiesce and exact fresh Root terminal proof, without retry or replacement authentication. The Root proof format is renamed for this case; scope/epoch/freshness checks are preserved.

## Final binding recheck

Independent byte comparison confirms that final runner differs from the preserved structural runner only by replacing the single `expectedRevision = 'ROOT_HEAD_FREEZE_PENDING'` literal with the final Root HEAD. No lifecycle or conditional outcome logic changed.

`PREP.json`, the runner literal and `source-pins.json` agree on the exact HEAD; PREP and CRITICAL-DELTA candidate hashes match the actual final runner. Source-pins SHA256 is `f9289804395d2a44fcffc82fe6e762d87aa9ae6df113b959ac86e1001d99f0f9`. All 32 pinned current source hashes match. Root HEAD is exact and its full Git status is clean. Relevant product paths have an empty diff from product base6219 to final HEAD. `node --check runner.cjs` exits0. `run/` is absent at recheck.

These observations qualify this final source package for Root's separately authorized one attempt. The capture budget remains40s, two PNG maximum, followed by bounded cleanup/HOLD. They do not establish a runtime result or approve another attempt after any unknown or completed outcome.

The owner's local Ubuntu group UI gate is separate and remains NOT_RUN in cloud. This review authorizes no group start, fixture/grant/password/clock/data mutation, group deletion, service change, or broad test campaign. The group seven-GiB floor is not imposed on this separately budgeted light visual case.

Reviewer actions: public source/metadata/Git reads, hashes and Node syntax check only; Auth/API/browser/DB/build/service/CI execution0. The reviewer created only this external review report for this recheck. Requested model `gpt-6.1-sol`, effort `high`; runtime model telemetry unavailable, no fallback claimed.
