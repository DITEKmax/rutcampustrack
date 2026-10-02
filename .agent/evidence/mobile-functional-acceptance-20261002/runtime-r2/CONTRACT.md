# BFF diagnostic artifact preparation

Goal: obtain a build of the accepted safe gRPC operation/status logging to identify the real bootstrap failure with minimum additional runtime cost.
Context/evidence: root a93f4265 freeze contains accepted one-file MobileAcademicClient diagnostic change9450d6fe; R1 bootstrap UNACCEPTED and all owned resources removed. RULES SHA A208AA4380B64376A4EAD645AA0731C9F574077107DC9C44356F5C04D80F28FA.
Relevant scope: sole runtimeholder v2-runtime-build-r2 and assigned external main evidence runtime-r2/ only. Existing foreign runner WIP and previous R1 evidence untouched.
Required behavior: ordinary safe clean switch to exact a93f4265a2ad397d60d3758ea57116b8616916d0; only Mobile BFF bootJar; reuse seven JAR and both Vue dist pins; canonical manifest source/BFF metadata update with actual clean proof.
Constraints: no reset/clean/install/product or config edits, tests/rebuilds of accepted artifacts, manual generator invocation, new helper/tunnel, runtime launch, secret output, main commit or foreign writes.
Existing patterns: known JDK21 and packaging/no-problems-report/no-daemon/no-parallel/maxworkers1; previous trusted manifest and exact original runner canonical utilities.
Acceptance: target build terminal0, seven JAR and PWA/TMA byte hashes preserved, canonical roundtrip0, full holder status empty and Java empty. This stage does not prove the runtime root cause.
Verification: source/reuse648b06 exit0; build42119 final5feb29 exit0/35s; manifest proof9dfbe0 exit0. Exact invocation, raw log and pins retained.
Do not: modify auth/deadlines, rerun tests/old probes, start stand before separate root GO, claim bootstrap or product acceptance from build readiness.
