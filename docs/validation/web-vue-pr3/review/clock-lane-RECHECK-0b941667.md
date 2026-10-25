# Mongo CPU-only source recheck — 0b941667

Verdict: **PASS for the bounded same-CID CPU-only proposal**. No confirmed P1/P2 blocker found in this corrective packet. This is not an executed update, readiness result or technical GO.

Read MONGO-CPU-CORRECTION.md, FACTS/PLAN/VALIDATION, mongo_cpu_adjustment.py and the small wrapper/compose/resource/recipe delta against archived23d. Recomputed package digest exactly `0b94166733d9266d81e3825b48a90e4f973dd41a05520f27a6da09c49d272b87`; archived23d remains unchanged. Plan SHA256 matches `cb42bb1d56e92381f074db75e3b0b28da0b825e447fb160b79ca65ab08bc75e8`.

The proposal targets only replacement Mongo CID `6d4fee38782c610ea586f93ddfc6fb4376fc68b7527cba26b86e8b44bc5261f9`, NanoCpus100000000→500000000. Its sole direct Docker action is `update --cpus=0.5` on the plan's fixed CID. There is no deletion, reset, restart/start/stop, credential/environment/data write or automatic mutation retry.

Before apply, the script requires exact package/image admission, unmodified memory budget guard, snapshot=false/no clone artifacts, unchanged prior completed-repair receipt SHA and private env SHA, unchanged main13 baseline, and exactly the five recorded lane CIDs/images/creation times/mounts/network names/running non-OOM states. It fingerprints complete Config/HostConfig for the other four containers; only Mongo NanoCpus is excluded and separately checked against the exact old/new values. Mongo's memory, health configuration, environment and volume therefore remain covered.

Apply requires the additional exact-plan CPU approval. An exclusive NOFOLLOW0600 receipt is created before update, and completion is recorded only after the exact post-state guard. A partial/inconclusive operation cannot silently replay. Wait requires completed update receipt, rechecks the exact five/main13 and unchanged configuration, and uses a real monotonic300-second deadline; it performs reads only and fails without quota/health/reset fallback. Existing infra-up remains no-recreate and uses the new exact-five guard; invoking it is not required for this update. Snapshot/apps/browser stages remain separately blocked.

Pure checks passed: exact five service/id set and fixed Mongo id/old-new CPU; only semantic Compose change is Mongo cpus.1→.5; health command/5-second timeout/memory are identical; guard.py and prior repair script are unchanged; resource sum6976MiB/2.55CPU; ten Python AST parses; AST confirms one direct Docker call in the new helper, the fixed CPU update.

FACTS records main Mongo as **uncapped**, with five successful0.413–0.772-second probes, versus the new lane's running/non-OOM .1CPU Mongo with five5.12–5.50-second timeouts and heavy throttling. This supports testing a resource prerequisite; it does not prove .5CPU will pass. Prior repair/config native0 and retained readiness1 are supplied public evidence, not executions repeated by this reviewer. Corrected configuration/health remains a mandatory actual result after root's separate technical quota GO. A failed wait must stop/report.

No Docker, DB, Gradle, browser, network, private-file read, runtime action, product source write or child agent was used. Only reads, pure hashes/JSON/YAML/AST checks and this immutable report write occurred. Requested model remains `gpt-6.1-sol/high`; runtime telemetry is unavailable and no fallback/telemetry claim is made.
