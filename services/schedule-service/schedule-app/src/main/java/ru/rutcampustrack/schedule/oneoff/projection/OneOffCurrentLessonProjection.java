package ru.rutcampustrack.schedule.oneoff.projection;

import java.time.LocalDate;
import java.time.OffsetDateTime;

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
    OffsetDateTime getCreatedAt();
}
