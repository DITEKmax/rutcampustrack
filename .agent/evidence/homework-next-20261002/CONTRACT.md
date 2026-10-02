# JS-HEADMAN16 next homework lesson — frozen root ACK 2026-10-02

## Goal
Староста или helper выбирает предмет и тип, сервер возвращает ближайшую подходящую пару для привязки ДЗ.

## Context/evidence
Root лично открыл job-stories.md585–596. Main baseline cefd8fefc6076720175a35edb51727918283b7ed, leaf baseline ea49cefec9da209bc0e46a087b1599c802941f44; Schedule scoped diff пуст. Canonical RULES SHA A208AA4380B64376A4EAD645AA0731C9F574077107DC9C44356F5C04D80F28FA. Риск S3 (authz) по root/services AGENTS.

## Relevant scope
Schedule LessonApi, новый NextHomeworkLessonResponse, LessonController, LessonService, LessonRepository, новая NextHomeworkLessonProjection, один existing LessonApiIT method.

## Required behavior
GET /schedule/lessons/next: positive groupId,semesterId,subjectId; lessonType LECTURE/PRACTICE/LAB; ISO fromDate включительно. 200 physical selection {lessonId,occurrenceId,occurrenceRevision,groupId,subjectId,semesterId,lessonType,date,lessonNumber,startTime,endTime}; 204 отсутствие. Один MVCC query текущего physical pointer, exact scope/type, planned/active, now Moscow строго до date+end+5min, без unresolved transfer. Order date/startTime/lessonId, без lookahead. STUDENT своей группы и existing актуальный IsHeadman либо MANAGE_HOMEWORK helper check. ADMIN/TEACHER bypass отсутствует. Выбор не резервирует: запись повторно проверяет revision и действующие gates.

## Constraints
Не менять действующий lifecycle/end+5min policy, proto/generated, UI, другие сервисы. Сохранять foreign WIP. Один writer; no reset/clean/push. No heavy до independent review и root lease.

## Existing patterns
HTTP mapping на contract interface. Spring Data native projection рядом с LessonDetailsProjection. Clock из existing container. LessonService existing 8arg constructor сохранён и делегирует новому injectable 9arg constructor. HomeworkPlacementService current-pointer eligibility/cutoff retained.

## Acceptance criteria
Точный ближайший разрешённый ID включая gap>2weeks, recurring/oneoff physical snapshots. Current pointer и pending transfer исключают stale сторону. Own scope, revoked headman, denied helper не получают выбор. FromDate inclusive; exact type/semester; 204 отсутствие.

## Verification
Source diff check exit0. Planned ONE scoped compileJava/compileTestJava + LessonApiIT.nextHomeworkLesson_selectsCurrentEligibleSnapshotWithoutHorizonAndEnforcesScope. Method uses PostgreSQL and MockMvc, real transfer writer, current/expired/closed/cancelled rows, gap30days, oneoff physical snapshot/tie ID, inclusive date, absence/type/semester, foreign scope/revoked headman/helper, invalid params. No repeat accepted lifecycle tests. Heavy not launched.

## Do not
Не расширять oneoff creation: source подтверждает POST /schedule/one-off-lessons → OneOffLessonController → OneOffLessonService.createOneOffLesson saves только logicalrow (142) и event. DTO create не несёт assignment/type/time. Root получил blocker и назначает отдельный cohesive oneoff пакет. Этот lookup не materializes пары. No broad refactor/migration or external provider changes.
