package ru.rutcampustrack.schedule.oneoff.projection;

import java.time.Instant;
import java.time.LocalDate;

/** Public placement and origin identity from one current physical snapshot. */
public interface OneOffCurrentLessonProjection {
    Long getId();

    Long getPhysicalLessonId();

    Long getGroupId();

    Long getSubjectId();

    Long getSemesterId();

    LocalDate getDate();

    Short getLessonNumber();

    String getClassroom();

    Long getCreatedBy();

    Instant getCreatedAt();
}
