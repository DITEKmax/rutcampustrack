Goal: locate a finite reported boot boundary in the existing failed E2E job without rerunning tests.

Context/evidence: PR head1742d1e806faa5c39c6d182a3554daa8f4239801, run38387263916/attempt1, job115262001797, step9 “Boot isolated Vue auth stack” failed. Existing terminal metadata says steps10–14 skipped. Actual E2E checkout and boot cause remain UNKNOWN. Auth P10 diagnosis belongs to its separate author.

Relevant scope: sole new checking writer /workspace/scratch/rct-validation/current1742_e2e_boot_diagnostic_source. Repo/workflow/compose, old seals, two current JARs685a/412c, stand data160/key/five volumes are read-only. No children.

Required behavior: Root first reuses/fetches only normal run+job metadata to bind head/run/attempt/job/step and exact started_at/completed_at. Existing SAFE artifacts have no boot cause. Check annotations only through the same closed metadata boundary if already available; annotations are not downloaded by this package. If metadata still lacks cause, one exact normal GH job-log GET can run after fresh privacy review/grant. capture-job-private.py exec-replaces itself with /usr/bin/gh; fixed argv has no credentials. stdout is only new mode600 job.log.private, stderr/stdin DEVNULL, hard RLIMIT_FSIZE32MiB. b78 owns the unchanged exec PID. No automatic retry or rerun.

Constraints: current package is SOURCE_ONLY. Future GET requires normal platform approval and Root fresh120s grant with45s phase, at least30s cleanup, workspace4GiB floor, shared15GiB stop with1GiB reserve, sampled reader RSS512MiB and disk256MiB ceiling reused from existing collection envelope. This single log is hard capped32MiB, private file plus tiny safe receipts; sampling is not hard memory isolation. Root creates no new credentials and uses only existing normal GH auth/proxy/CA context supplied privately to b78's explicit environment. No os.environ copy, ENV dump/hash, signed redirect output or token in argv. Failure/oversize/unsupported format remains UNKNOWN, no cap increase or fallback.

Existing patterns: b78 run_owned import API, SHA b78c61293568d9941692e0b92ee34fd0b84db6d532b4a2c9a5ad42ccbf2204a0; existing current1742 SAFE reader has32MiB/30s request/45s phase/512MiB RSS/256MiB disk. Reuse Root's ordinary inline owned-observe/status wrapper; no new manager/caller framework. GitHub connector fetch_workflow_job_logs returns whole decoded output without an exposed stream-size bound, so it was discovered but NOT called and is not the selected capture transport.

Acceptance criteria: exact metadata identity before read; private parent FD identity/nofollow, O_EXCL600 leaf, no overwritten file; settled owned exec tree before one RAM read; strict UTF8, fixed current Compose16 services/status/code enums only. Unknown lines are discarded; malformed input/unknown captured enums deny. No raw messages, XML, stack, URI, value hashes or userdata in receipts. Fixed log hints are untrusted reported context, never confirmed application/user cause. Original smoke/FIRST3 NOT_RUN remains explicit.

Verification: parser and grant/fresh resource predicates tested with public synthetic RAM only. Selected controls include malformedUTF8/control/ANSI/longline, private canary, false head/run/attempt/job/step, unknown enum, expired/delayed clock-LAST/floor/shared gates. Actual log/API/child/capture remains NOT_RUN. Independent privacy review and Root fresh native grant required.

Do not: replay tests, run Compose/stand/JAR build/STAGE, modify workflow/product/locks, inspect credentials/private ENV or retained DB/key values, read foreign logs, attempt B013, lower caps, issue arbitrary gh argv, preserve/publish raw capture. Exact capture cleanup occurs only after b78 settled; consume_private denies foreign mode/owner/alias/replacement and removes only its validated own inode. A failed/truncated capture must be disposed by Root's exact-owned-file cleanup after settlement, never parsed as a successful full log.

Effective future caller (existing inline Root pattern; NOT executed):

```python
# 1. Root verifies this sealed manifest, b78 and gh/python public hashes,
#    fresh normal-context private directory FD identity and Root600 grant.
# 2. Root normal metadata read provides binding.template.safe.json fields;
#    metadata_verified must be true, real step times replace UNKNOWN.
# 3. Root's existing sampled observe callback checks floors/shared/RSS/file
#    allocation and cleanup UTC LAST; closed status retains PID/birth only.
result = guard.run_owned(
    ['/usr/bin/python3', SOURCE + '/capture-job-private.py', '--grant',
     SOURCE + '/private/read-grant.json'],
    cwd=SOURCE, env=Root_existing_private_normal_GH_environment,
    deadline_seconds=45, term_seconds=5, kill_seconds=5,
    observe=Root_existing_resource_callback, status=Root_existing_closed_status)
# 4. Record terminal result and fresh birth absence; only phase_success +
#    all_owned_processes_reaped allows private parser. No raw stdout.
# 5. With exact pinned private directory FD, read once into bounded RAM:
projection = parser.consume_private(private_directory_fd, verified_binding)
# 6. Pre-serialize only projection + fixed binding; fresh resources/UTC LAST
#    immediately before gated unbuffered safe publication. Root records
#    UNKNOWN on capture/format/parse failure and safely removes own private
#    log after settlement. Close FDs and verify exact private log absent.
```

Job log content length/effective gh proxy behavior and actual line grammar are UNKNOWN until that one approved read. Job-level transport binds immutable jobID; public metadata joins head/run/attempt. Step time filtering is qualified because timestamps have finite precision; hints do not authenticate log text. No live source/VM dependency change is needed for this proposal.
