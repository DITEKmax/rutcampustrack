# H95 bounded compiler correction and date-unit gate

## Goal
Correct actual H94 compiler failures and reach compile plus RecurringDateCalculatorTest PASS; then freeze. Do not resume broad implementation.
## Context/evidence
Root H94 compileJava exit1; tests NOTRUN, zero source drift. Evidence C:/Users/maksd/IntelliJIDEA/rutcampustrack/.agent/orchestration-v2/evidence/h94-context.json and h94-stderr.log. Observed defect: missing java.time.LocalDate import in ScheduleItem.java fields90/94, six diagnostics including Lombok. RULES C:/Users/maksd/IntelliJIDEA/rutcampustrack/.agent/orchestration-v2/RULES.md SHA B256A175274987DA9710D804B3C050A5DBCB47D8448CC03644D74168B52A437A. Governing product packet C:/Users/maksd/IntelliJIDEA/rutcampustrack/.agent/orchestration-v2/L5B-RECURRING-WRITER-IMPLEMENT.md remains applicable; this amendment narrows current execution to actual compiler correction.
## Relevant scope
Same Luna author, sole writer C:/Users/maksd/IntelliJIDEA/rutcampustrack/.agent/worktrees/v2-l5b-recurring-writer, base3d4115f3a4c4ddba473689e27ac1a0efb519a202 plus preserved31-path WIP. Only fixes directly supported by compiler output, including necessary contract call sites. Own untracked h95 evidence allowed. No other checkout writes.
## Required behavior
Acknowledge packet and H95 lease before edit/run. Fix missing import; execute the exact requested test command sequentially. Correct additional actual compiler defects only between finished attempts. Stop immediately on compile/date-unit PASS, or report a concrete noncompiler blocking defect without broad redesign. Preserve all prior evidence and WIP.
## Constraints
Root explicitly grants exclusive HEAVY LEASE H95 to this author for this command only. No parallel heavy, PG, Docker, generation, unrelated semantic rewrites, full review or commit. Luna max/Terra NEVER/no children; preserve others. Use absolute edit paths. Report actual lease start and completion promptly. Real scoped escalation if required, no permission bypass.
## Existing patterns
Use established Java21/Gradle8.12 execution context from H94 evidence, login:false. Do not print secrets. Same bounded source corrections, not unbounded original planning.
## Acceptance criteria
Actual command exits0 and fresh RecurringDateCalculatorTest XML confirms tests executed without failure/error/skip. Source frozen at returned hashes. Otherwise exact compiler/blocker evidence and honest NOTRUN/FAIL status. No claim that broader product scope is done.
## Verification
Before EACH attempt save exact source inventory/raw SHA256 including new files, command/start/environment under unique own h95-01/h95-02 directories. Capture distinct stdout/stderr/exit/end and fresh XML copies; never overwrite prior attempts. Source edits only between attempts. Command from worktree: .\gradlew.bat :services:schedule-service:schedule-app:test --tests *RecurringDateCalculatorTest --continue --no-daemon --no-parallel --max-workers=1 --no-problems-report. Record needed established environment from H94 without exposing secrets. Notify lead on first actual execution start and final result.
## Do not
No broad completion/refactor, changing assertions to obtain PASS, source edits during execution, other selectors, repeated accepted tests, prototype PG runs, installs, new agents or automatic next stage.
