# Homework same-tick retry crosses owner — confirmed

07.09.2026. Root isolated verification; no product edits. Exact frozen UI15 composable SHA03C72E19A41901A6C77F19E7D1C43EEA9CF384BBAE7CDEF1F6BCC61A8EA6BE8E matches the tested snapshot. Domain helper snapshot/hash is in manifest.json. Node24.14.0, actual installed Vue/Vue Query bundled by existing esbuild; no fake Vue internals or HTTP calls.

Reproduction: mount actual useHomework with its VueQueryPlugin and a minimal Vue renderer; student A's completion fails and leaves its retry intent. Replace the reactive scope with student B (same group/semester/homework ID, increased resetGeneration), and immediately call retryCompletion before nextTick. The actual composable invokes the fake transport for B using A's desired state:

```
{"retryResult":"promise","calls":[{"userId":"student-a","id":"shared-homework-id","completed":true},{"userId":"student-b","id":"shared-homework-id","completed":true}]}
Prior owner retry must not issue a command for the new owner
PROBE_PROCESS_EXIT=1
```

Severity: MEDIUM (unwanted mutation under the replacement owner). Location: use-homework.ts:291–294 reads stored input but drops its scope when calling submitCompletion; cleanup at308 is an asynchronous watcher. Scope validation inside mutationFn sees the newly captured B scope and therefore accepts the old intent. This is a cross-owner unwanted command, not proof of unauthorized cross-user server data access. In a shared-group session change, the new owner could receive an unintended completion on its own record.

Commands: node build.cjs; node probe.cjs. Initial sandbox esbuild failed Access denied before executing behavior. The approved scoped escalated command built successfully and the actual security assertion exited1; exact result.log retained. Product source and writer build caches are untouched.

Root correction gate: current active mobile_theme_repair_fresh remains sole homework-ui writer and additionally owns only use-homework.ts and its existing focused test for this evidenced defect. This is a bounded extension of current Homework repair before review, not reuse of a completed agent. Validate saved command ownership synchronously before retry can capture current scope; preserve same-owner desired-state retry, explicit fresh-owner submissions, offline/pending guards and stale response rejection. Add a regression retaining a FAILED A intent and switching to B then retrying in the same tick; do not empty the retry map with a successful A retry before the identity switch. Check role/group/semester/resetGeneration scope changes as applicable. No API/domain contract change or broad query refactor. The separate obsolete-query-key concern remains unconfirmed and is not part of this correction.

Verification: new regression must fail on frozen snapshot and pass after correction; existing composable/domain tests, strict frontend checks and independent final Sol high review remain required. Root will rerun this isolated fixture on a new named snapshot after handoff.
