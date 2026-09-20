# r3 checks
Scope: services/attendance-service/attendance-app/src/test/java/ru/rutcampustrack/attendance/studentrequest/StudentRequestDomainIT.java only.
Criteria: raw BSON _id lookup uses ObjectId; no-payload and cancellation/attachment assertions remain unchanged; exactly one import and one query conversion.
Before SHA-256 (Get-FileHash, exit 0): 4AB8154262472792F051C0E15B742379A4D652C02430B2E1D3C9B54B93FE8BE2.
After SHA-256 (Get-FileHash, exit 0): BDA19E54D3B69BC6A7E87D7ABB7F3807335A469375CCE40707255658D5EF9C68.
Final shape evidence: import org.bson.types.ObjectId at line 8; raw query at line 529 uses new ObjectId(detail.summary().id()).
Diff evidence: two intended semantic changes only: one adjacent ObjectId import and one Filters.eq _id conversion; git diff -- target (exit 0) is empty because this producer path is untracked in the dirty worktree.
Runtime evidence: N/A by frozen contract; Gradle/Docker/runtime explicitly forbidden.
Checks: static SHA and targeted git diff completed with exit code 0; no Gradle/Docker run.
Limitations: root must coordinate the affected integration rerun; no runtime claim is made here.
