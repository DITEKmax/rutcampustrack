# Homework heading — consultant result received at pause

07.09.2026. Fresh Sol xhigh readonly homework_heading_decision; runtime N/A. Root freeze deferred until resume.
Conclusion: «Выполнено сегодня» refers to Moscow completion-action date. Final4601:142 puts subjects on September1 and September2, while4601:848636 groups both under completed-today. Boolean/lessonDate cannot establish that after reload. Existing HomeworkCompletion.completedAt is preserved by repeated true INSERT ON CONFLICT DO NOTHING; false removes record.

Proposed minimal delta (consultant inference, not yet implemented): nullable server completedAt in HomeworkInfo/BFF HomeworkItem/PUT response. Current feed union lessonDate[from,to] plus own active-semester assignments completed current Moscow day, dedup byid. Other items retain chronological lessonDate groups. Header predicate MoscowDate(completedAt)==MoscowDate(serverNow). No device-time authority/client history.

Required cases: past/future completed today; yesterday; reload; repeatedtrue timestamp unchanged; undo removes timestamp/todaygroup; redo newtimestamp; Moscow midnight. Timestamp wire representation choose from existing conventions.
Sources: design-context/4601-142.txt,4601-848636-refreshed-2026-09-07.txt,4788-146.txt,4601-848562.txt,4922-343.txt; HomeworkCompletion.java:25,HomeworkCompletionRepository.java:24,academic.proto:188,StudentApiModels.java:212 in homework-api worktree. No consultant file/Figma writes.
