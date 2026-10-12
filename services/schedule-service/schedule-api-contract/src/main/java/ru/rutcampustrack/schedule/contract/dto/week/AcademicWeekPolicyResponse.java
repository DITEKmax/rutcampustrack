package ru.rutcampustrack.schedule.contract.dto.week;

import java.time.LocalDate;
import java.util.List;
import ru.rutcampustrack.schedule.contract.enums.WeekType;

public record AcademicWeekPolicyResponse(
        LocalDate weekStart,
        LocalDate weekEnd,
        int isoWeek,
        WeekType weekType,
        int parityNumber,
        String label,
        List<WeekTypeOption> weekTypes) {
    public AcademicWeekPolicyResponse {
        weekTypes = List.copyOf(weekTypes);
    }

    public record WeekTypeOption(WeekType weekType, Integer parityNumber, String label) {}
}
