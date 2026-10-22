# Subject deletion — owner decisions 2026-10-15

## D029 — Q025: Subject and lesson identity is local to a semester

Recorded UTC 2026-10-15T21:25:11.147945+00:00. Owner exact answer: «Ну вообще у нас староста каждый семестр создаёт новый id у предмета. между семестрами нет в целом никакой связи (кроме как у групп и преподов, остальное всё по id новое и привязано к семестру) у нас пока нет межсеместрового взаимодействия да и по предметной области никак пары не связать из разных семестров».

Accepted domain rule: a headman creates a new subject ID for each semester. Subjects, lessons and their related semester data do not share identity or interaction across semesters; groups and teachers can persist. Full subject deletion must target the exact subject and its own semester data, never match other semesters by subject name, group or teacher. This answer resolves the premise that one intended subject spans semesters; it does not authorize purging another archived semester or production data. Preserve historical data outside the exact accepted target. A legacy or inconsistent cross-semester reference must be detected and handled safely rather than silently broadening the destructive scope. Assess current schema/creation paths against this rule before choosing a compatible implementation or migration; do not claim current enforcement or runtime PASS.

Supersedes Q025 unresolved domain-identity premise and the assumption that cross-semester subject sharing is intended. The existing password plus purpose-specific bot-code, current headman authority, exact preview/confirmation, durable completion/retry and independent review criteria remain. No new mobile destructive screen is authorized solely by this answer.

## D030 — Q026: Preserve mixed-subject EXCUSE tickets when deleting a subject

Recorded UTC 2026-10-15T21:25:11.147945+00:00. Owner exact answer: «Сохранить заявку, убрать только пары удаляемого предмета».

For an EXCUSE ticket covering lessons of several subjects, keep the same ticket and the other subjects' lesson coverage; remove only the lessons belonging to the deleted subject and record the change in history. Preserve the accepted single ticket and single decision, existing attribution and remaining attachments under their current ownership rules. Do not delete the whole mixed ticket, split its decision, reset unrelated quotas or remove other-subject data. Sole-subject request deletion remains the separate accepted full-subject cascade; this decision specifically resolves the mixed-subject branch.

Supersedes Q026 unresolved mixed-ticket deletion policy. Implement only through the reviewed subject-scoped durable deletion protocol with current authority, password and purpose-specific bot proof. Prove exact remaining coverage/history and replay/concurrency behavior with real data; Source readiness is not acceptance. No production deletion, deploy, main merge or Angular/new-web work.
