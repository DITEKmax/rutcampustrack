package ru.rutcampustrack.attendance.audit;

import org.springframework.web.bind.annotation.RestController;
import ru.rutcampustrack.attendance.contract.api.LessonActionsApi;
import ru.rutcampustrack.attendance.contract.dto.report.LessonActionsResponse;

@RestController
public class LessonActionController implements LessonActionsApi {
    private final LessonActionReadService service;

    public LessonActionController(LessonActionReadService service) {
        this.service = service;
    }

    public LessonActionsResponse actions(Long lessonId, String cursor, int limit) {
        return service.read(lessonId, cursor, limit);
    }
}
