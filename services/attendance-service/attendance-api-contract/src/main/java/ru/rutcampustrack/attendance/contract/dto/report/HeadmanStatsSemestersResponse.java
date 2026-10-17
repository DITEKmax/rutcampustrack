package ru.rutcampustrack.attendance.contract.dto.report;

import java.time.LocalDate;
import java.util.List;

public record HeadmanStatsSemestersResponse(
        long groupId, Long activeSemesterId, List<Semester> semesters) {
    public record Semester(
            long id,
            String name,
            LocalDate dateFrom,
            LocalDate dateTo,
            boolean active,
            boolean archived) {}
}
