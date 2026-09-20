# FE13 decisions and bounded corrections

- The frozen contract keeps server ownership of attendance/statistics projections. Components remain controlled Vue compositions and emit typed events.
- Requests authority is represented exactly as EXCUSE and LATE_CHECKIN. Eligibility comes only from the supplied option projection.
- Statistics series IDs are opaque. seriesForRange returns the supplied server series without parsing IDs, bucketing, aggregating, ranking or deriving percentages.
- Statistics type filters emit the next selectedTypes projection. The harness host replaces selectedTypes and does not recompute the aggregate.
- Terminal/read states preserve readable navigation and filters where the contract allows them; request actions remain disabled when terminal or when the server option is disabled.
- Status badges preserve their status tone. Long neutral labels have an auto-width rem pill, and non-interactive statuses expose full Russian accessible names.
- The request form stays a named scoped slot with data-slot-state=OPEN fallback. The fallback uses user-facing copy and does not implement reason, comment, file, upload or submit behavior.
- Focus ownership stays in AttendanceScreen: opening a controlled request transition focuses Back; closing returns focus to the originating request trigger.
- Source geometry correction keeps Attendance view controls as separate rounded pills and fills each day item surface; all authored PCSS dimensions remain rem-based.

