package ru.rutcampustrack.attendance.contract.api;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import ru.rutcampustrack.attendance.contract.dto.report.LessonActionsResponse;

@RequestMapping("/attendance/reports")
public interface LessonActionsApi {
    @GetMapping("/lesson/{lessonId}/actions")
    LessonActionsResponse actions(
            @PathVariable Long lessonId,
            @RequestParam(required = false) String cursor,
            @RequestParam(defaultValue = "50") int limit);
}
