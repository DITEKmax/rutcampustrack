package ru.rutcampustrack.schedule.contract.api;

import io.swagger.v3.oas.annotations.Operation;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import ru.rutcampustrack.schedule.contract.dto.lesson.LessonSlotCatalogResponse;

public interface LessonSlotApi {
    @Operation(summary = "Получить время восьми пар по Москве")
    @GetMapping("/schedule/lesson-slots")
    ResponseEntity<LessonSlotCatalogResponse> lessonSlots();
}
