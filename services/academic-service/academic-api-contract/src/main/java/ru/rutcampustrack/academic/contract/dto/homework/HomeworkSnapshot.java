package ru.rutcampustrack.academic.contract.dto.homework;

import java.time.LocalDate;
import ru.rutcampustrack.academic.contract.enums.HomeworkBindingMode;

/** Content and placement at one durable homework revision. */
public record HomeworkSnapshot(String title, String description, String link,
                               HomeworkBindingMode bindingMode, LocalDate lessonDate,
                               Integer lessonNumber) {}
