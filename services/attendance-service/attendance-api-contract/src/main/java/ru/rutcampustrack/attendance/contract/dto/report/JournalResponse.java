package ru.rutcampustrack.attendance.contract.dto.report;

import org.springframework.hateoas.RepresentationModel;

import java.util.List;

/**
 * Top-level response for journal grid (RPRT-02, D-07).
 * Columns are dates (List<String>), rows are students (List<JournalStudentRow>).
 * Extends RepresentationModel for HATEOAS _links support.
 * Plain Java class — no Lombok (contract module rule).
 */
public class JournalResponse extends RepresentationModel<JournalResponse> {

    private final Long groupId;
    private final Long subjectId;
    private final List<String> dates;
    private final List<JournalStudentRow> students;

    public JournalResponse(Long groupId, Long subjectId, List<String> dates, List<JournalStudentRow> students) {
        this.groupId = groupId;
        this.subjectId = subjectId;
        this.dates = dates;
        this.students = students;
    }

    public Long getGroupId() {
        return groupId;
    }

    public Long getSubjectId() {
        return subjectId;
    }

    public List<String> getDates() {
        return dates;
    }

    public List<JournalStudentRow> getStudents() {
        return students;
    }
}
