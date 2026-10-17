Static-source addendum only. Current seven public source files exactly match frozen Root 18451c9dacc08625f038313ef2d62e36b3ba6423; full SHA256/bytes are in source-mapping.safe.json.

Attendance Academic getSemesterState/getGroupMembers use the same `Academic Service unavailable: ` prefix plus dynamic gRPC Status; no unique operation follows from this prefix. Schedule getLessonById uses `Schedule Service unavailable: ` plus Status. Fixed messages: `Mismatched semester state`, `Schedule returned no lesson snapshot`, `groupId, asOfDate and semesterId are required`. Transfer rejection also embeds dynamic state/operationId.

Attendance handler503: type `https://api.rutcampustrack.ru/problems/service-unavailable`, title `Сервис недоступен`, detail = exception message. No code/causeGrpcCode/grpcCode field; detail can contain gRPC Status text. Instance/timestamp/optional traceId are other fields and are not diagnostic-safe raw output.

Gateway filter own503: fixed status/title/detail/code = 503 / `Service Unavailable` / `Dependency unavailable` / `DEPENDENCY_UNAVAILABLE`. No type or gRPC cause field. Internal `Auth authority unavailable` is not serialized. Downstream failures propagate separately.

No native runtime/API/private material or response bodies read. No classifier implementation/design or confirmed product defect/runtime cause.
