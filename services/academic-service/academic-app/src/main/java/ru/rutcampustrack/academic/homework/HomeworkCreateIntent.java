package ru.rutcampustrack.academic.homework;

import ru.rutcampustrack.academic.contract.dto.homework.CreateHomeworkRequest;
import ru.rutcampustrack.academic.contract.enums.HomeworkBindingMode;
import java.time.LocalDate;

/** Immutable request payload used for CREATE replay after later content/placement changes. */
public record HomeworkCreateIntent(Long groupId, Long subjectId, Long semesterId,
                                   String title, String description, String link,
                                   HomeworkBindingMode bindingMode,
                                   LocalDate lessonDate, Integer lessonNumber) {
    public static HomeworkCreateIntent from(CreateHomeworkRequest request) {
        return new HomeworkCreateIntent(request.groupId(), request.subjectId(), request.semesterId(),
                request.title(), request.description(), request.link(), request.bindingMode(),
                request.lessonDate(), request.lessonNumber());
    }
}
