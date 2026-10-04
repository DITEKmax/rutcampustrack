package ru.rutcampustrack.schedule.lesson;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;
import ru.rutcampustrack.schedule.contract.api.LessonSlotApi;
import ru.rutcampustrack.schedule.contract.dto.lesson.LessonSlotCatalogResponse;
import ru.rutcampustrack.schedule.contract.enums.UserRole;
import ru.rutcampustrack.schedule.security.RequireRole;

@RestController
public class LessonSlotController implements LessonSlotApi {
    @Override
    @RequireRole({UserRole.ADMIN, UserRole.TEACHER, UserRole.STUDENT})
    public ResponseEntity<LessonSlotCatalogResponse> lessonSlots() {
        return ResponseEntity.ok(LessonSlotCatalogResponse.canonical());
    }
}
