package ru.rutcampustrack.academic.contract.dto.homework;

import java.time.LocalDate;
import ru.rutcampustrack.academic.contract.enums.HomeworkBindingMode;
import ru.rutcampustrack.academic.contract.enums.SubjectType;

/** Content and placement at one durable homework revision. */
public record HomeworkSnapshot(String title, String description, String link,
                               HomeworkBindingMode bindingMode, LocalDate lessonDate,
                               Integer lessonNumber, @com.fasterxml.jackson.annotation.JsonInclude(com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL) SubjectType lessonType) {
    public HomeworkSnapshot(String title, String description, String link, HomeworkBindingMode bindingMode,
                            LocalDate lessonDate, Integer lessonNumber) {
        this(title, description, link, bindingMode, lessonDate, lessonNumber, null);
    }
}
