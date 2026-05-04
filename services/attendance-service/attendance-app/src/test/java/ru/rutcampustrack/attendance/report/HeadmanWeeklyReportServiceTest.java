package ru.rutcampustrack.attendance.report;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import ru.rutcampustrack.academic.grpc.GroupMembersResponse;
import ru.rutcampustrack.academic.grpc.GroupResponse;
import ru.rutcampustrack.academic.grpc.SemesterResponse;
import ru.rutcampustrack.academic.grpc.StudentInfo;
import ru.rutcampustrack.attendance.contract.enums.AttendanceSource;
import ru.rutcampustrack.attendance.contract.enums.AttendanceStatus;
import ru.rutcampustrack.attendance.exception.AccessDeniedException;
import ru.rutcampustrack.attendance.exception.BadRequestException;
import ru.rutcampustrack.attendance.exception.ReportValidationException;
import ru.rutcampustrack.attendance.grpc.AcademicGrpcClient;
import ru.rutcampustrack.attendance.grpc.DocumentRendererGrpcClient;
import ru.rutcampustrack.attendance.grpc.ScheduleGrpcClient;
import ru.rutcampustrack.attendance.security.RequestContext;
import ru.rutcampustrack.attendance.shared.port.AttendanceReadPort;
import ru.rutcampustrack.attendance.shared.port.AttendanceRecord;
import ru.rutcampustrack.documentrenderer.grpc.TargetFormat;
import ru.rutcampustrack.schedule.grpc.LessonResponse;
import ru.rutcampustrack.schedule.grpc.LessonsResponse;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.lenient;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class HeadmanWeeklyReportServiceTest {

    private static final Clock CLOCK = Clock.fixed(
            Instant.parse("2026-05-02T09:00:00Z"),
            ZoneId.of("Europe/Moscow"));

    @Mock
    private AcademicGrpcClient academicGrpcClient;

    @Mock
    private ScheduleGrpcClient scheduleGrpcClient;

    @Mock
    private AttendanceReadPort attendanceReadPort;

    @Mock
    private DocxRenderer docxRenderer;

    @Mock
    private DocumentRendererGrpcClient documentRendererGrpcClient;

    @Mock
    private RequestContext requestContext;

    private HeadmanWeeklyReportService service;

    @BeforeEach
    void setUp() {
        service = new HeadmanWeeklyReportService(
                academicGrpcClient,
                scheduleGrpcClient,
                attendanceReadPort,
                docxRenderer,
                documentRendererGrpcClient,
                requestContext,
                CLOCK);
        lenient().when(requestContext.isHeadman()).thenReturn(true);
        lenient().when(requestContext.getGroupId()).thenReturn(10L);
    }

    @Test
    void activeSemesterWeeks_startMidWeekUsesContainingMondayAndIncludesEndWeek() {
        when(academicGrpcClient.getActiveSemester()).thenReturn(semester("2026-04-29", "2026-05-12"));

        var response = service.getActiveSemesterWeeks();

        assertThat(response.getWeeks()).hasSize(3);

        var first = response.getWeeks().get(0);
        assertThat(first.getWeekOfSemester()).isEqualTo(1);
        assertThat(first.getLabel()).isEqualTo("\u041d1");
        assertThat(first.getWeekStart()).isEqualTo(LocalDate.of(2026, 4, 27));
        assertThat(first.getWeekEnd()).isEqualTo(LocalDate.of(2026, 5, 3));
        assertThat(first.isCurrent()).isTrue();

        var last = response.getWeeks().get(2);
        assertThat(last.getWeekOfSemester()).isEqualTo(3);
        assertThat(last.getWeekStart()).isEqualTo(LocalDate.of(2026, 5, 11));
        assertThat(last.getWeekEnd()).isEqualTo(LocalDate.of(2026, 5, 17));
    }

    @Test
    void activeSemesterWeeks_regularStudentDeniedBeforeGrpcCall() {
        when(requestContext.isHeadman()).thenReturn(false);

        assertThatThrownBy(() -> service.getActiveSemesterWeeks())
                .isInstanceOf(AccessDeniedException.class);
        verifyNoInteractions(academicGrpcClient);
    }

    @Test
    void exportSingleWeek_unknownFormatFailsAsBadRequest() {
        assertThatThrownBy(() -> service.exportSingleWeek(LocalDate.of(2026, 4, 27), "xlsx"))
                .isInstanceOf(BadRequestException.class);
        verifyNoInteractions(academicGrpcClient);
    }

    @Test
    void fileName_addsGapSuffixForNonConsecutiveWeeks() {
        String fileName = HeadmanWeeklyReportFiles.buildFileName(
                "uvpv511",
                List.of(LocalDate.of(2026, 4, 27), LocalDate.of(2026, 5, 11)),
                HeadmanWeeklyReportFormat.PDF);

        assertThat(fileName)
                .isEqualTo("UVPV511_27.04.2026_17.05.2026_\u0421_\u041f\u0420\u041e\u041f\u0423\u0421\u041a\u0410\u041c\u0418.pdf");
    }

    @Test
    void fileName_multiWeekPngUsesZipExtension() {
        String fileName = HeadmanWeeklyReportFiles.buildFileName(
                "UVPV511",
                List.of(LocalDate.of(2026, 4, 27), LocalDate.of(2026, 5, 4)),
                HeadmanWeeklyReportFormat.PNG);

        assertThat(fileName).isEqualTo("UVPV511_27.04.2026_10.05.2026_png.zip");
    }

    @Test
    void reportStatusSymbolsMatchM18TemplateRules() {
        assertThat(HeadmanWeeklyReportModel.symbolFor(AttendanceStatus.PRESENT)).isEqualTo("+");
        assertThat(HeadmanWeeklyReportModel.symbolFor(AttendanceStatus.ABSENT)).isEqualTo("\u043d");
        assertThat(HeadmanWeeklyReportModel.symbolFor(AttendanceStatus.EXCUSED)).isEqualTo("\u0443");
        assertThat(HeadmanWeeklyReportModel.symbolFor(AttendanceStatus.FREE_ATTENDANCE)).isEqualTo("\u0443");
        assertThat(HeadmanWeeklyReportModel.symbolFor(AttendanceStatus.CANCELLED)).isEmpty();
    }

    @Test
    void buildReportModels_compactsLessonSlotsNamesAndSubjectsForTemplate() {
        LocalDate weekStart = LocalDate.of(2026, 4, 27);
        stubAcademicForReport(semester("2026-04-27", "2026-05-31"),
                student(1L, "\u041f\u0435\u0442\u0440\u043e\u0432 \u041f\u0435\u0442\u0440 \u041f\u0430\u0432\u043b\u043e\u0432\u0438\u0447"));
        when(scheduleGrpcClient.getLessonsByGroup(10L, 1L, "2026-04-27", "2026-05-03"))
                .thenReturn(LessonsResponse.newBuilder()
                        .addLessons(lesson(100L, 5L, "2026-04-27", 3, "closed"))
                        .addLessons(lesson(101L, 6L, "2026-04-27", 5, "closed"))
                        .addLessons(lesson(102L, 7L, "2026-04-27", 6, "closed"))
                        .addLessons(lesson(103L, 8L, "2026-04-27", 8, "closed"))
                        .build());
        when(academicGrpcClient.getSubjectDetailsByIds(List.of(5L, 6L, 7L, 8L))).thenReturn(Map.of(
                5L, subjectDetails("\u0421\u0438\u0441\u0442\u0435\u043c\u044b \u0438\u0441\u043a\u0443\u0441\u0441\u0442\u0432\u0435\u043d\u043d\u043e\u0433\u043e \u0438\u043d\u0442\u0435\u043b\u043b\u0435\u043a\u0442\u0430 \u0438 \u043c\u0430\u0448\u0438\u043d\u043d\u043e\u0435 \u043e\u0431\u0443\u0447\u0435\u043d\u0438\u0435", "lecture"),
                6L, subjectDetails("\u0421\u0435\u0442\u0435\u0432\u044b\u0435 \u0442\u0435\u0445\u043d\u043e\u043b\u043e\u0433\u0438\u0438 \u0438 \u043f\u0440\u043e\u0442\u043e\u043a\u043e\u043b\u044b", "practice"),
                7L, subjectDetails("\u0421\u0435\u0440\u0432\u0438\u0441-\u043e\u0440\u0438\u0435\u043d\u0442\u0438\u0440\u043e\u0432\u0430\u043d\u043d\u043e\u0435 \u043f\u0440\u043e\u0433\u0440\u0430\u043c\u043c\u0438\u0440\u043e\u0432\u0430\u043d\u0438\u0435", "lab"),
                8L, subjectDetails("\u0422\u0435\u043e\u0440\u0435\u0442\u0438\u0447\u0435\u0441\u043a\u0438\u0435 \u043e\u0441\u043d\u043e\u0432\u044b \u043f\u043e\u0441\u0442\u0440\u043e\u0435\u043d\u0438\u044f \u0430\u0432\u0442\u043e\u043c\u0430\u0442\u0438\u0437\u0438\u0440\u043e\u0432\u0430\u043d\u043d\u044b\u0445 \u0438\u043d\u0444\u043e\u0440\u043c\u0430\u0446\u0438\u043e\u043d\u043d\u044b\u0445 \u0441\u0438\u0441\u0442\u0435\u043c", "unknown")));
        when(attendanceReadPort.findByGroupAndDateRange(10L, weekStart, LocalDate.of(2026, 5, 3)))
                .thenReturn(List.of(
                        record(100L, 1L, 5L, weekStart, 3, AttendanceStatus.PRESENT),
                        record(102L, 1L, 7L, weekStart, 6, AttendanceStatus.ABSENT)));

        HeadmanWeeklyReportModel model = service.buildReportModels(List.of(weekStart)).get(0);

        assertThat(model.days().get(0).lessons())
                .extracting(HeadmanWeeklyReportModel.LessonSlot::lessonNumber)
                .containsExactly(1, 2, 3, 4);
        assertThat(model.days().get(0).lessons())
                .extracting(HeadmanWeeklyReportModel.LessonSlot::subjectName)
                .containsExactly("\u0421\u0438\u0441\u0442. \u0418\u0418 \u0438 \u041c\u041e",
                        "\u0421\u0435\u0442\u0435\u0432\u044b\u0435 \u0442\u0435\u0445\u043d. \u0438 \u043f\u0440\u043e\u0442.",
                        "\u0421\u0435\u0440\u0432\u0438\u0441-\u043e\u0440\u0438\u0435\u043d\u0442. \u043f\u0440\u043e\u0433\u0440.",
                        "\u0422\u0435\u043e\u0440\u0435\u0442\u0438\u0447\u0435\u0441\u043a\u0438\u0435 \u041e.\u041f.\u0410.\u0418.\u0421.");
        assertThat(model.days().get(0).lessons())
                .extracting(HeadmanWeeklyReportModel.LessonSlot::lessonType)
                .containsExactly("\u041b\u041a", "\u041f\u0417", "\u041b\u0417", "");
        assertThat(model.students().get(0).displayName()).isEqualTo("\u041f\u0435\u0442\u0440\u043e\u0432 \u041f.\u041f.");
        assertThat(model.students().get(0).attendance())
                .extracting(HeadmanWeeklyReportModel.AttendanceMark::lessonNumber)
                .containsExactly(1, 2, 3, 4);
        assertThat(model.students().get(0).attendance())
                .extracting(HeadmanWeeklyReportModel.AttendanceMark::symbol)
                .containsExactly("+", "", "\u043d", "");
    }

    @Test
    void exportSingleWeek_docxReturnsFilenameContentTypeAndBytes() {
        LocalDate weekStart = LocalDate.of(2026, 4, 27);
        stubAcademicForReport(semester("2026-04-27", "2026-05-31"),
                student(1L, "Student"));
        when(scheduleGrpcClient.getLessonsByGroup(10L, 1L, "2026-04-27", "2026-05-03"))
                .thenReturn(LessonsResponse.getDefaultInstance());
        when(attendanceReadPort.findByGroupAndDateRange(10L, weekStart, LocalDate.of(2026, 5, 3)))
                .thenReturn(List.of());
        when(docxRenderer.render(any())).thenReturn(new byte[]{1, 2, 3});

        HeadmanWeeklyExportResult result = service.exportSingleWeek(weekStart, "docx");

        assertThat(result.fileName()).isEqualTo("UVPV511_27.04.2026_03.05.2026.docx");
        assertThat(result.contentType()).isEqualTo("application/vnd.openxmlformats-officedocument.wordprocessingml.document");
        assertThat(result.content()).containsExactly(1, 2, 3);
    }

    @Test
    void exportSingleWeek_pdfUsesRendererService() {
        LocalDate weekStart = LocalDate.of(2026, 4, 27);
        stubEmptyReportInputs(weekStart);
        when(docxRenderer.render(any())).thenReturn(new byte[]{1, 2, 3});
        when(documentRendererGrpcClient.convertDocx(any(byte[].class), eq(TargetFormat.PDF)))
                .thenReturn(new byte[]{4, 5});

        HeadmanWeeklyExportResult result = service.exportSingleWeek(weekStart, "pdf");

        assertThat(result.fileName()).isEqualTo("UVPV511_27.04.2026_03.05.2026.pdf");
        assertThat(result.contentType()).isEqualTo("application/pdf");
        assertThat(result.content()).containsExactly(4, 5);
        verify(documentRendererGrpcClient).convertDocx(any(byte[].class), eq(TargetFormat.PDF));
    }

    @Test
    void exportSingleWeek_pngUsesRendererService() {
        LocalDate weekStart = LocalDate.of(2026, 4, 27);
        stubEmptyReportInputs(weekStart);
        when(docxRenderer.render(any())).thenReturn(new byte[]{1, 2, 3});
        when(documentRendererGrpcClient.convertDocx(any(byte[].class), eq(TargetFormat.PNG)))
                .thenReturn(new byte[]{8, 9});

        HeadmanWeeklyExportResult result = service.exportSingleWeek(weekStart, "png");

        assertThat(result.fileName()).isEqualTo("UVPV511_27.04.2026_03.05.2026.png");
        assertThat(result.contentType()).isEqualTo("image/png");
        assertThat(result.content()).containsExactly(8, 9);
        verify(documentRendererGrpcClient).convertDocx(any(byte[].class), eq(TargetFormat.PNG));
    }

    @Test
    void exportSelectedWeeks_regularStudentDeniedBeforeGrpcCall() {
        when(requestContext.isHeadman()).thenReturn(false);

        assertThatThrownBy(() -> service.exportSelectedWeeks(
                new ru.rutcampustrack.attendance.contract.dto.report.HeadmanWeeklyExportRequest(
                        List.of(LocalDate.of(2026, 4, 27)),
                        "docx")))
                .isInstanceOf(AccessDeniedException.class);
        verifyNoInteractions(academicGrpcClient, scheduleGrpcClient, attendanceReadPort, docxRenderer, documentRendererGrpcClient);
    }

    @Test
    void exportMultiWeekDocxRendersOneDocument() {
        LocalDate firstWeek = LocalDate.of(2026, 4, 27);
        LocalDate secondWeek = LocalDate.of(2026, 5, 11);
        stubTwoEmptyWeeks(firstWeek, secondWeek);
        when(docxRenderer.render(any())).thenReturn(new byte[]{7, 7});

        HeadmanWeeklyExportResult result = service.exportSelectedWeeks(
                new ru.rutcampustrack.attendance.contract.dto.report.HeadmanWeeklyExportRequest(
                        List.of(firstWeek, secondWeek),
                        "docx"));

        assertThat(result.fileName())
                .isEqualTo("UVPV511_27.04.2026_17.05.2026_\u0421_\u041f\u0420\u041e\u041f\u0423\u0421\u041a\u0410\u041c\u0418.docx");
        assertThat(result.contentType()).isEqualTo("application/vnd.openxmlformats-officedocument.wordprocessingml.document");
        assertThat(result.content()).containsExactly(7, 7);
        verifyNoInteractions(documentRendererGrpcClient);
    }

    @Test
    void exportMultiWeekPdfRendersOnePdfDocument() {
        LocalDate firstWeek = LocalDate.of(2026, 4, 27);
        LocalDate secondWeek = LocalDate.of(2026, 5, 11);
        stubTwoEmptyWeeks(firstWeek, secondWeek);
        when(docxRenderer.render(any())).thenReturn(new byte[]{7, 7});
        when(documentRendererGrpcClient.convertDocx(any(byte[].class), eq(TargetFormat.PDF)))
                .thenReturn(new byte[]{6, 6});

        HeadmanWeeklyExportResult result = service.exportSelectedWeeks(
                new ru.rutcampustrack.attendance.contract.dto.report.HeadmanWeeklyExportRequest(
                        List.of(firstWeek, secondWeek),
                        "pdf"));

        assertThat(result.fileName())
                .isEqualTo("UVPV511_27.04.2026_17.05.2026_\u0421_\u041f\u0420\u041e\u041f\u0423\u0421\u041a\u0410\u041c\u0418.pdf");
        assertThat(result.contentType()).isEqualTo("application/pdf");
        assertThat(result.content()).containsExactly(6, 6);
        verify(documentRendererGrpcClient).convertDocx(any(byte[].class), eq(TargetFormat.PDF));
    }

    @Test
    void exportMultiWeekPngReturnsZipArchive() {
        LocalDate firstWeek = LocalDate.of(2026, 4, 27);
        LocalDate secondWeek = LocalDate.of(2026, 5, 11);
        stubAcademicForReport(semester("2026-04-27", "2026-05-31"), student(1L, "Student"));
        when(scheduleGrpcClient.getLessonsByGroup(10L, 1L, "2026-04-27", "2026-05-03"))
                .thenReturn(LessonsResponse.getDefaultInstance());
        when(scheduleGrpcClient.getLessonsByGroup(10L, 1L, "2026-05-11", "2026-05-17"))
                .thenReturn(LessonsResponse.getDefaultInstance());
        when(attendanceReadPort.findByGroupAndDateRange(10L, firstWeek, LocalDate.of(2026, 5, 3)))
                .thenReturn(List.of());
        when(attendanceReadPort.findByGroupAndDateRange(10L, secondWeek, LocalDate.of(2026, 5, 17)))
                .thenReturn(List.of());
        when(docxRenderer.render(any())).thenReturn(new byte[]{1});
        when(documentRendererGrpcClient.convertDocx(any(byte[].class), eq(TargetFormat.PNG)))
                .thenReturn(new byte[]{9});

        HeadmanWeeklyExportResult result = service.exportSelectedWeeks(
                new ru.rutcampustrack.attendance.contract.dto.report.HeadmanWeeklyExportRequest(
                        List.of(firstWeek, secondWeek),
                        "png"));

        assertThat(result.fileName())
                .isEqualTo("UVPV511_27.04.2026_17.05.2026_\u0421_\u041f\u0420\u041e\u041f\u0423\u0421\u041a\u0410\u041c\u0418_png.zip");
        assertThat(result.contentType()).isEqualTo("application/zip");
        assertThat(zipEntries(result.content()))
                .containsExactly("week-01_27.04.2026_03.05.2026.png", "week-03_11.05.2026_17.05.2026.png");
    }

    @Test
    void buildReportModels_collectsScheduleAttendanceAndSortsStudents() {
        LocalDate weekStart = LocalDate.of(2026, 4, 27);
        stubAcademicForReport(semester("2026-04-27", "2026-05-31"),
                student(2L, "\u0411\u0435\u0442\u0430"),
                student(1L, "\u0410\u043b\u044c\u0444\u0430"));
        when(scheduleGrpcClient.getLessonsByGroup(10L, 1L, "2026-04-27", "2026-05-03"))
                .thenReturn(LessonsResponse.newBuilder()
                        .addLessons(lesson(100L, 5L, "2026-04-27", 1, "closed"))
                        .addLessons(lesson(101L, 6L, "2026-04-27", 2, "cancelled"))
                        .addLessons(lesson(102L, 5L, "2026-04-28", 1, "closed"))
                        .build());
        when(academicGrpcClient.getSubjectDetailsByIds(List.of(5L))).thenReturn(Map.of(
                5L, subjectDetails("Math", "practice")));
        when(attendanceReadPort.findByGroupAndDateRange(10L, weekStart, LocalDate.of(2026, 5, 3)))
                .thenReturn(List.of(
                        record(100L, 1L, 5L, LocalDate.of(2026, 4, 27), 1, AttendanceStatus.PRESENT),
                        record(100L, 2L, 5L, LocalDate.of(2026, 4, 27), 1, AttendanceStatus.ABSENT),
                        record(102L, 1L, 5L, LocalDate.of(2026, 4, 28), 1, AttendanceStatus.EXCUSED)));

        List<HeadmanWeeklyReportModel> models = service.buildReportModels(List.of(weekStart));

        assertThat(models).hasSize(1);
        HeadmanWeeklyReportModel model = models.get(0);
        assertThat(model.groupId()).isEqualTo(10L);
        assertThat(model.groupCode()).isEqualTo("UVPV511");
        assertThat(model.weekOfSemester()).isEqualTo(1);
        assertThat(model.days()).hasSize(HeadmanWeeklyReportModel.TEMPLATE_DAYS);
        assertThat(model.days().get(0).lessons()).hasSize(1);
        assertThat(model.days().get(0).lessons().get(0).lessonId()).isEqualTo(100L);
        assertThat(model.days().get(0).lessons().get(0).subjectName()).isEqualTo("Math");
        assertThat(model.days().get(0).lessons().get(0).lessonType()).isEqualTo("\u041f\u0417");
        assertThat(model.days().get(1).lessons()).hasSize(1);
        assertThat(model.days().get(5).lessons()).isEmpty();

        assertThat(model.students()).extracting(HeadmanWeeklyReportModel.Student::displayName)
                .containsExactly("\u0410\u043b\u044c\u0444\u0430", "\u0411\u0435\u0442\u0430");
        assertThat(model.students().get(0).attendance()).extracting(HeadmanWeeklyReportModel.AttendanceMark::symbol)
                .containsExactly("+", "\u0443");
        assertThat(model.students().get(1).attendance()).extracting(HeadmanWeeklyReportModel.AttendanceMark::symbol)
                .containsExactly("\u043d", "");
    }

    @Test
    void buildReportModels_moreThanThirtyFiveStudentsThrows422() {
        StudentInfo[] students = java.util.stream.LongStream.rangeClosed(1, 36)
                .mapToObj(id -> student(id, "Student %02d".formatted(id)))
                .toArray(StudentInfo[]::new);
        stubAcademicForReport(semester("2026-04-27", "2026-05-31"), students);

        assertThatThrownBy(() -> service.buildReportModels(List.of(LocalDate.of(2026, 4, 27))))
                .isInstanceOf(ReportValidationException.class)
                .hasMessageContaining("35 students");
    }

    @Test
    void buildReportModels_weekOutsideActiveSemesterThrows422() {
        when(academicGrpcClient.getActiveSemester()).thenReturn(semester("2026-04-27", "2026-05-31"));

        assertThatThrownBy(() -> service.buildReportModels(List.of(LocalDate.of(2026, 6, 8))))
                .isInstanceOf(ReportValidationException.class)
                .hasMessageContaining("outside the active semester");
    }

    @Test
    void buildReportModels_moreThanFiveLessonsInDayThrows422() {
        LocalDate weekStart = LocalDate.of(2026, 4, 27);
        stubAcademicForReport(semester("2026-04-27", "2026-05-31"),
                student(1L, "Student"));
        when(scheduleGrpcClient.getLessonsByGroup(10L, 1L, "2026-04-27", "2026-05-03"))
                .thenReturn(LessonsResponse.newBuilder()
                        .addLessons(lesson(100L, 5L, "2026-04-27", 1, "closed"))
                        .addLessons(lesson(101L, 5L, "2026-04-27", 2, "closed"))
                        .addLessons(lesson(102L, 5L, "2026-04-27", 3, "closed"))
                        .addLessons(lesson(103L, 5L, "2026-04-27", 4, "closed"))
                        .addLessons(lesson(104L, 5L, "2026-04-27", 5, "closed"))
                        .addLessons(lesson(105L, 5L, "2026-04-27", 6, "closed"))
                        .build());

        assertThatThrownBy(() -> service.buildReportModels(List.of(weekStart)))
                .isInstanceOf(ReportValidationException.class)
                .hasMessageContaining("5 lessons");
    }

    private static SemesterResponse semester(String dateFrom, String dateTo) {
        return SemesterResponse.newBuilder()
                .setId(1L)
                .setName("Spring 2026")
                .setDateFrom(dateFrom)
                .setDateTo(dateTo)
                .build();
    }

    private void stubAcademicForReport(SemesterResponse semester, StudentInfo... students) {
        when(academicGrpcClient.getActiveSemester()).thenReturn(semester);
        when(academicGrpcClient.getGroup(10L)).thenReturn(GroupResponse.newBuilder()
                .setId(10L)
                .setName("UVPV511")
                .setIsActive(true)
                .build());
        GroupMembersResponse.Builder members = GroupMembersResponse.newBuilder();
        for (StudentInfo student : students) {
            members.addStudents(student);
        }
        when(academicGrpcClient.getGroupMembers(10L)).thenReturn(members.build());
    }

    private void stubEmptyReportInputs(LocalDate weekStart) {
        stubAcademicForReport(semester("2026-04-27", "2026-05-31"), student(1L, "Student"));
        when(scheduleGrpcClient.getLessonsByGroup(10L, 1L, weekStart.toString(), weekStart.plusDays(6).toString()))
                .thenReturn(LessonsResponse.getDefaultInstance());
        when(attendanceReadPort.findByGroupAndDateRange(10L, weekStart, weekStart.plusDays(6)))
                .thenReturn(List.of());
    }

    private void stubTwoEmptyWeeks(LocalDate firstWeek, LocalDate secondWeek) {
        stubAcademicForReport(semester("2026-04-27", "2026-05-31"), student(1L, "Student"));
        when(scheduleGrpcClient.getLessonsByGroup(10L, 1L, firstWeek.toString(), firstWeek.plusDays(6).toString()))
                .thenReturn(LessonsResponse.getDefaultInstance());
        when(scheduleGrpcClient.getLessonsByGroup(10L, 1L, secondWeek.toString(), secondWeek.plusDays(6).toString()))
                .thenReturn(LessonsResponse.getDefaultInstance());
        when(attendanceReadPort.findByGroupAndDateRange(10L, firstWeek, firstWeek.plusDays(6)))
                .thenReturn(List.of());
        when(attendanceReadPort.findByGroupAndDateRange(10L, secondWeek, secondWeek.plusDays(6)))
                .thenReturn(List.of());
    }

    private static List<String> zipEntries(byte[] zipBytes) {
        try (java.util.zip.ZipInputStream zip = new java.util.zip.ZipInputStream(
                new java.io.ByteArrayInputStream(zipBytes))) {
            java.util.ArrayList<String> entries = new java.util.ArrayList<>();
            java.util.zip.ZipEntry entry = zip.getNextEntry();
            while (entry != null) {
                entries.add(entry.getName());
                entry = zip.getNextEntry();
            }
            return entries;
        } catch (java.io.IOException ex) {
            throw new IllegalArgumentException(ex);
        }
    }

    private static AcademicGrpcClient.SubjectDetails subjectDetails(String name, String type) {
        return new AcademicGrpcClient.SubjectDetails(name, type);
    }

    private static StudentInfo student(Long id, String displayName) {
        return StudentInfo.newBuilder()
                .setUserId(id)
                .setDisplayName(displayName)
                .build();
    }

    private static LessonResponse lesson(Long id, Long subjectId, String date, int lessonNumber, String status) {
        return LessonResponse.newBuilder()
                .setId(id)
                .setGroupId(10L)
                .setSubjectId(subjectId)
                .setDate(date)
                .setLessonNumber(lessonNumber)
                .setStatus(status)
                .build();
    }

    private static AttendanceRecord record(Long lessonId,
                                           Long userId,
                                           Long subjectId,
                                           LocalDate lessonDate,
                                           Integer lessonNumber,
                                           AttendanceStatus status) {
        return new AttendanceRecord(
                lessonId,
                userId,
                10L,
                subjectId,
                lessonDate,
                lessonNumber,
                status,
                AttendanceSource.STUDENT_GEO,
                null);
    }
}
