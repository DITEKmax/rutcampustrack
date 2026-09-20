# Statistics contract decision — consultation completed

07.09.2026. Bounded read-only Sol xhigh consultation completed. Final result is preserved in statistics-decision-result.md; root acceptance/corrections in statistics-root-decision.md supersede preliminary questions below. Numerical direction is a candidate; Subject/assignment/occurrence and profile/roster prerequisites prevent implementation dispatch. Earlier slot-limit attempt is historical, not current state.

## Goal
Choose a minimal implementable student statistics contract matching final five Figma states, without named peer ranking/export UI.

## Context/evidence
Final frames4603:142,4603:848696,4798:142,4798:200,4798:285, exact extracts in design-context. Root opened overview and subject variants. Final overview shows own rank and +/н/у percentages, stacked semester graph with future region, subject cards. Subject states support1/2/3 types with multiple selections. Earlier accepted source resolution says server4metrics4counts, final mobile subset only. backend-conflicts.md R4:127ff; old wireframe103-student-stats.md:89–105 identifies present/absent/excused/combined-present-excused counts and percentages and weekly series, but its named full ranking/group line/tab/export composition is superseded by final UI. job-stories.md JS-STUDENT07/13/14/18 remain source material.

## Relevant scope
Read ReportService and report DTOs, AttendanceReadPort, Academic Subject, schedule/academic clients/proto as necessary. No writes/children. Future domain implementation should avoid requestdomain files/MongoConfig and common proto/BFF/generated until integration.

## Required behavior / questions
Freeze exact denominator and closed-but-missing attendance behavior; weekly versus cumulative values and empty/future representation; grouping same subject with multiple types (Academic Subject currently has separate id,name,type,groupId per row with no common discipline ID); own-rank tie policy and participant set with no peer personal data. Define minimal domain DTOs and later BFF/gRPC/API shapes. Identify source facts versus bounded consultant inference.

## Constraints
Later owner sources/final Figma wins, read-only old materials, no compatibility layer or unrelated role/schema refactor. Student only own data and rank; no names/peerrecords in response. Four metrics/counts server authoritative, exclude cancelled; no invented UI.

## Existing patterns
ReportService.getStudentStats/buildOverall/buildWeekly currently attended=PRESENT+EXCUSED+FREE_ATTENDANCE and denominator=record count; this is not proof of the new formulas. AttendanceReadPort has own-user/semester and group/date reads. Subject stores type per row; root opened original. Reuse ports rather than import checkin repositories across report-domain boundary.

## Acceptance criteria
Decisive contract and ownership ready for a bounded developer, tests for zero denominator, future/cancelled/missing records, ownscope, multitype identity, ranking ties and stable dates. No outstanding owner question unless resolution truly changes owner decision.

## Verification
Consultant runtime N/A; cite exact critical original locations. Return nine concise sections, no exhaustive research or code.

## Do not
No file writes, children, changes to requests/Homework, new roles/fullleaderboard, deployment or claims of product PASS.

Additional root source facts: finaloverview72+18+10=100; subject72+8+20=100; lecturecardПар8/12 with75/8/17, labПар5/8 with60/20/20. This supports separate held/totalplanned counts and disjoint+/н/у denominator held; exact missing-record/ranking/grouping still decisionpending. SubjectRepository has group-list and id/group guards but no commondiscipline key or name/typeuniqueness; avoid claiming stable ID grouping by unverifiedname alone.
