package ru.rutcampustrack.attendance.report;

import lombok.RequiredArgsConstructor;
import org.springframework.hateoas.CollectionModel;
import org.springframework.hateoas.EntityModel;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;
import ru.rutcampustrack.attendance.contract.api.ReportApi;
import ru.rutcampustrack.attendance.contract.dto.report.AttendanceRecordEntry;
import ru.rutcampustrack.attendance.contract.dto.report.JournalResponse;
import ru.rutcampustrack.attendance.contract.dto.report.LessonAttendanceResponse;
import ru.rutcampustrack.attendance.contract.dto.report.StudentStatsResponse;

import java.time.LocalDate;
import java.util.List;

import static org.springframework.hateoas.server.mvc.WebMvcLinkBuilder.linkTo;
import static org.springframework.hateoas.server.mvc.WebMvcLinkBuilder.methodOn;

/**
 * REST controller for attendance reports (RPRT-01..04).
 * Implements ReportApi — all mappings and Swagger annotations live in the interface.
 *
 * Per CLAUDE.md: controller only delegates to ReportService and wraps in HATEOAS.
 * No business logic, no authorization, no exception handling — all in ReportService.
 */
@RestController
@RequiredArgsConstructor
public class ReportController implements ReportApi {

    private final ReportService reportService;

    @Override
    public ResponseEntity<EntityModel<LessonAttendanceResponse>> getLessonAttendance(Long lessonId) {
        LessonAttendanceResponse response = reportService.getLessonAttendance(lessonId);
        EntityModel<LessonAttendanceResponse> model = EntityModel.of(response,
                linkTo(methodOn(ReportController.class).getLessonAttendance(lessonId)).withSelfRel());
        return ResponseEntity.ok(model);
    }

    @Override
    public ResponseEntity<EntityModel<JournalResponse>> getJournal(
            Long groupId, Long subjectId, LocalDate dateFrom, LocalDate dateTo) {
        JournalResponse response = reportService.getJournal(groupId, subjectId, dateFrom, dateTo);
        EntityModel<JournalResponse> model = EntityModel.of(response,
                linkTo(methodOn(ReportController.class).getJournal(groupId, subjectId, dateFrom, dateTo))
                        .withSelfRel());
        return ResponseEntity.ok(model);
    }

    @Override
    public ResponseEntity<EntityModel<StudentStatsResponse>> getStudentStats() {
        StudentStatsResponse response = reportService.getStudentStats();
        EntityModel<StudentStatsResponse> model = EntityModel.of(response,
                linkTo(methodOn(ReportController.class).getStudentStats()).withSelfRel());
        return ResponseEntity.ok(model);
    }

    @Override
    public ResponseEntity<CollectionModel<EntityModel<AttendanceRecordEntry>>> getStudentRecords(Long subjectId) {
        List<AttendanceRecordEntry> entries = reportService.getStudentRecords(subjectId);
        List<EntityModel<AttendanceRecordEntry>> entityModels = entries.stream()
                .map(EntityModel::of)
                .toList();
        CollectionModel<EntityModel<AttendanceRecordEntry>> collectionModel = CollectionModel.of(
                entityModels,
                linkTo(methodOn(ReportController.class).getStudentRecords(subjectId)).withSelfRel());
        return ResponseEntity.ok(collectionModel);
    }
}
