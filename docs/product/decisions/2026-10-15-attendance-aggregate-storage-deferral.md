# D028 — Q027: Retain authoritative attendance calculation; defer stored incremental counters

Recorded UTC 2026-10-15T19:59:41.591452+00:00 after the owner reply in the main Cloud chat.

Owner exact answer: «Сохранить расчёт при запросе; отдельные счётчики отложить (рекомендую)».

For this current acceptance retain authoritative server calculation from persisted attendance data on requests. Separate stored counters maintained incrementally are deferred until measured load establishes a need and a separately agreed implementation is prepared. This supersedes only the explicit storage-strategy clause of canonical JS-SYSTEM-16:899 (reference job-stories.md SHA87af4670d5d8f996d940de9a56815cacc4befeaf90e5ea406a60871329bd4981). It does not defer correct server-owned percentages/counts, common denominator rules, current authorization, historical membership, exclusion of canceled/future lessons, null versus zero, events/atomicity or any confirmed user-visible performance defect.

Continue the compatible Teacher journal Table/Chart correction: metrics come from the authoritative server, rather than client division, with existing scope/paging/selection/privacy controls. Do not add a new aggregate store, cache architecture, queue or migration merely to satisfy the deferred storage mechanism. This decision changes neither production/merge/deploy prohibitions nor the other task criteria.
