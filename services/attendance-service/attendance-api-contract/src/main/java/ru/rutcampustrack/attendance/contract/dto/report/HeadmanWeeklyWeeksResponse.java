package ru.rutcampustrack.attendance.contract.dto.report;

import io.swagger.v3.oas.annotations.media.Schema;
import org.springframework.hateoas.RepresentationModel;

import java.time.LocalDate;
import java.util.List;

@Schema(description = "Weeks of the active semester available for headman weekly report export")
public class HeadmanWeeklyWeeksResponse extends RepresentationModel<HeadmanWeeklyWeeksResponse> {

    private final Long semesterId;
    private final String semesterName;
    private final LocalDate semesterDateFrom;
    private final LocalDate semesterDateTo;
    private final List<HeadmanWeeklyWeekOption> weeks;

    public HeadmanWeeklyWeeksResponse(Long semesterId,
                                      String semesterName,
                                      LocalDate semesterDateFrom,
                                      LocalDate semesterDateTo,
                                      List<HeadmanWeeklyWeekOption> weeks) {
        this.semesterId = semesterId;
        this.semesterName = semesterName;
        this.semesterDateFrom = semesterDateFrom;
        this.semesterDateTo = semesterDateTo;
        this.weeks = List.copyOf(weeks);
    }

    public Long getSemesterId() {
        return semesterId;
    }

    public String getSemesterName() {
        return semesterName;
    }

    public LocalDate getSemesterDateFrom() {
        return semesterDateFrom;
    }

    public LocalDate getSemesterDateTo() {
        return semesterDateTo;
    }

    public List<HeadmanWeeklyWeekOption> getWeeks() {
        return weeks;
    }
}
