# Goal
S3 bounded source resolution for retrospective lesson auto-ABSENT: identify existing authoritative group membership at lesson date and exact minimal Academic gRPC change. No code yet.
## Context/evidence
Owner explicitly wants initial recurring series fromsemesterstart and automatic Н/ABSENT forpastlessons. Existing Attendance LessonEventService.processLessonClosed uses GetGroupMembers currentroster+cachedactivesemester, which is unsafe historicalauthority. Root accepted3d4115f3 baseline; Lessons solewriter separate recurringworktree. Read RULES/CURRENT and L5B-RECURRING-WRITER-IMPLEMENT.md owneramendments.
## Relevant scope
Access lead owns docs .agent/l5b-historical-roster/. Slot2 atmost1freshLuna max readonly scout <=8directoriginals onstable v2-l5b-service-identity. Critical existing AcademicGrpcServiceImpl.getGroupMembers, membership entities/repo/migrations or JdbcStudentProjectionQueryAdapter using dateintervals, group/userrolegrant activityhistory, proto GroupMembersRequest/StudentInfo. Reuse existing Access source maps; no broadrepoaudit.
## Required behavior
Find exact authoritative groupmembership temporalinterval and role/user eligibility fordate. Map current GetGroupMembers behavior and consumers. Propose narrow compatible date-scoped GetGroupMembers request field or alreadyexisting RPC if available, exactvalidation/empty/outage semantics and focused tests. Root will own sharedproto contract and appoint one writer for crossservice integration. Distinguish lack of historical eligibilitydata from implementationgap; no guessed currentroster. Send compact originals/fields/query/test locations and anytrueproductgap.
## Constraints
RULES absolute C:/Users/maksd/IntelliJIDEA/rutcampustrack/.agent/orchestration-v2/RULES.md SHA B256A175274987DA9710D804B3C050A5DBCB47D8448CC03644D74168B52A437A. Astra low lead, freshLunamax/forknone scopedreadonly nochildren/Terra, noheavy/code/secrets. Preserveothers. No other Access tasks/configchanges.
## Existing patterns
Academic StudentProjectionScopeService/JdbcStudentProjectionQueryAdapter already expose membershipsegments for ownstudent projection; existing GroupMembers RPC used acrossservices. Exactphysical snapshot date/semester authoritative fromSchedule, noactive-semester guess.
## Acceptance criteria
One compact map + recommended boundedcontract, no full architecturecycle, scoutreleased. Enough rootfreeze for autoabsencehistorical integration independentofLessonswriter.
## Verification
Read-only source N/A runtime. Existingtestselectors and expected behavior only, no PASSclaim.
## Do not
No newauthplatform, bodytrustedactor, productmigration, guessedhistoricalroster, code/protoedits, currentmemberfallback, wholeAccessaudit or repeatedpastdefaultquestion.
