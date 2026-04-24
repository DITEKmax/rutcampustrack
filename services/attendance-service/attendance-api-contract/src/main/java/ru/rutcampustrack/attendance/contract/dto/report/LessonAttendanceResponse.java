package ru.rutcampustrack.attendance.contract.dto.report;

import io.swagger.v3.oas.annotations.media.Schema;
import org.springframework.hateoas.RepresentationModel;

import java.util.List;

/**
 * Top-level response for lesson attendance list (RPRT-01).
 * Extends RepresentationModel for HATEOAS _links support.
 * Plain Java class — no Lombok (contract module rule).
 */
@Schema(description = "Список посещаемости конкретной пары: ID пары, группа, предмет, дата, записи по студентам")
public class LessonAttendanceResponse extends RepresentationModel<LessonAttendanceResponse> {

    private final Long lessonId;
    private final Long groupId;
    private final Long subjectId;
    private final String lessonDate;
    private final List<StudentAttendanceEntry> entries;

    public LessonAttendanceResponse(Long lessonId, Long groupId, Long subjectId, String lessonDate,
                                     List<StudentAttendanceEntry> entries) {
        this.lessonId = lessonId;
        this.groupId = groupId;
        this.subjectId = subjectId;
        this.lessonDate = lessonDate;
        this.entries = entries;
    }

    public Long getLessonId() {
        return lessonId;
    }

    public Long getGroupId() {
        return groupId;
    }

    public Long getSubjectId() {
        return subjectId;
    }

    public String getLessonDate() {
        return lessonDate;
    }

    public List<StudentAttendanceEntry> getEntries() {
        return entries;
    }
}
