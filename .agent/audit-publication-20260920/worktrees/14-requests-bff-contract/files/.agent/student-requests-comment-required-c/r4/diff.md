# Diff r4

The r4 leaf introduces evidence files only under
.agent/student-requests-comment-required-c/r4/**; it makes no product or
generated-file change.

The final producer chain represented by the manifest is:

StudentRequestService.commentRequired -> domain ReasonOption ->
StudentRequestGrpcMapper -> comment_required = 3 in Attendance proto ->
StudentRequestFacade -> required BFF JSON commentRequired.

The final union carries the accepted P1 service attachment reconciliation and
byte-exact authorization test, accepted P2 paths, and the explicit DTO
supersession. r2 restores only NotificationResolution in
StudentRequestModels.java; r3 changes only the raw BSON ObjectId lookup in
StudentRequestDomainIT.java. The r1 DTO producer path remains at its recorded
final hash. No redesign, product decision or scope delta is present.
