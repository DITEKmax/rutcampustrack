package ru.rutcampustrack.attendance.report;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.w3c.dom.Document;
import org.w3c.dom.NodeList;
import ru.rutcampustrack.academic.grpc.GroupMembersResponse;
import ru.rutcampustrack.academic.grpc.GroupResponse;
import ru.rutcampustrack.academic.grpc.SemesterResponse;
import ru.rutcampustrack.academic.grpc.StudentInfo;
import ru.rutcampustrack.attendance.contract.dto.report.HeadmanWeeklyExportRequest;
import ru.rutcampustrack.attendance.contract.enums.AttendanceSource;
import ru.rutcampustrack.attendance.contract.enums.AttendanceStatus;
import ru.rutcampustrack.attendance.exception.AccessDeniedException;
import ru.rutcampustrack.attendance.exception.ReportExportTooLargeException;
import ru.rutcampustrack.attendance.exception.ReportValidationException;
import ru.rutcampustrack.attendance.grpc.AcademicGrpcClient;
import ru.rutcampustrack.attendance.grpc.DocumentRendererGrpcClient;
import ru.rutcampustrack.attendance.grpc.ScheduleGrpcClient;
import ru.rutcampustrack.attendance.report.teacher.TeacherAttendanceDocxRenderer;
import ru.rutcampustrack.attendance.report.teacher.TeacherAttendanceExportModel;
import ru.rutcampustrack.attendance.security.RequestContext;
import ru.rutcampustrack.attendance.shared.port.AttendanceReadPort;
import ru.rutcampustrack.attendance.shared.port.AttendanceRecord;
import ru.rutcampustrack.documentrenderer.grpc.TargetFormat;
import ru.rutcampustrack.schedule.grpc.LessonResponse;
import ru.rutcampustrack.schedule.grpc.LessonsResponse;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.IntStream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class HeadmanWeeklyReportServiceTest {

    private static final String WORD_NS = "http://schemas.openxmlformats.org/wordprocessingml/2006/main";
    private static final String SHEET_NS = "http://schemas.openxmlformats.org/spreadsheetml/2006/main";
    private static final Clock CLOCK = Clock.fixed(
            Instant.parse("2026-05-02T09:00:00Z"), ZoneId.of("Europe/Moscow"));
    private static final LocalDate WEEK_START = LocalDate.of(2026, 4, 27);

    @Mock private AcademicGrpcClient academicGrpcClient;
    @Mock private ScheduleGrpcClient scheduleGrpcClient;
    @Mock private AttendanceReadPort attendanceReadPort;
    @Mock private DocumentRendererGrpcClient documentRendererGrpcClient;
    @Mock private RequestContext requestContext;

    private HeadmanWeeklyReportService service;

    @BeforeEach
    void setUp() {
        service = new HeadmanWeeklyReportService(
                academicGrpcClient,
                scheduleGrpcClient,
                attendanceReadPort,
                new TeacherAttendanceDocxRenderer(),
                new HeadmanWeeklyTabularRenderer(),
                documentRendererGrpcClient,
                requestContext,
                CLOCK);
        lenient().when(requestContext.isHeadman()).thenReturn(true);
        lenient().when(requestContext.getGroupId()).thenReturn(10L);
    }

    @Test
    void catalogueIsServerSuppliedAndOnlyIncludesWeeksStartedByToday() {
        when(academicGrpcClient.getActiveSemester()).thenReturn(semester("2026-04-01", "2026-05-31"));

        var response = service.getActiveSemesterWeeks();

        assertThat(response.getWeeks()).extracting(option -> option.getWeekStart().toString())
                .containsExactly("2026-03-30", "2026-04-06", "2026-04-13", "2026-04-20", "2026-04-27");
        assertThat(response.getWeeks().get(4).isCurrent()).isTrue();
        assertThat(response.getFormats()).extracting(option -> option.code())
                .containsExactly("docx", "pdf", "png", "html", "xlsx");
        assertThat(response.getFormats().get(2).contentType()).isEqualTo("application/zip");
        assertThat(response.getFormats().get(2).extension()).isEqualTo("zip");
    }

    @Test
    void activeAssistantMustHaveLiveViewStatsForEveryExportRequest() {
        when(requestContext.isHeadman()).thenReturn(false);
        when(academicGrpcClient.hasAssistantPermission(10L, "VIEW_STATS")).thenReturn(true, false);
        when(academicGrpcClient.getActiveSemester()).thenReturn(semester("2026-04-01", "2026-05-31"));

        assertThat(service.getActiveSemesterWeeks().getWeeks()).isNotEmpty();
        assertThatThrownBy(() -> service.getActiveSemesterWeeks()).isInstanceOf(AccessDeniedException.class);
        verify(academicGrpcClient, org.mockito.Mockito.times(2)).hasAssistantPermission(10L, "VIEW_STATS");
    }

    @Test
    void weeklyMatrixUsesDatedRosterAndKeepsFutureAndMissingMarksBlankWithoutRosterCap() {
        stubBase(semester("2026-04-01", "2026-05-31"));
        List<StudentInfo> mondayRoster = IntStream.rangeClosed(1, 40)
                .mapToObj(id -> student(id, id == 1 ? "Alpha <script>" : "Student " + id))
                .toList();
        List<StudentInfo> saturdayRoster = mondayRoster.subList(0, 39);
        when(scheduleGrpcClient.getLessonsByGroup(10L, 1L, "2026-04-27", "2026-05-03"))
                .thenReturn(LessonsResponse.newBuilder()
                        .addLessons(lesson(100L, "2026-04-27", "09:00", 1, "closed"))
                        .addLessons(lesson(101L, "2026-05-02", "13:00", 2, "planned"))
                        .addLessons(lesson(102L, "2026-05-02", "14:00", 3, "cancelled"))
                        .build());
        when(academicGrpcClient.getSubjectDetailsByIds(List.of(5L)))
                .thenReturn(Map.of(5L, new AcademicGrpcClient.SubjectDetails("Math", "lecture")));
        when(academicGrpcClient.getGroupMembers(10L, LocalDate.of(2026, 4, 27), 1L))
                .thenReturn(roster(LocalDate.of(2026, 4, 27), mondayRoster));
        when(academicGrpcClient.getGroupMembers(10L, LocalDate.of(2026, 5, 2), 1L))
                .thenReturn(roster(LocalDate.of(2026, 5, 2), saturdayRoster));
        when(attendanceReadPort.findByGroupAndDateRange(10L, WEEK_START, WEEK_START.plusDays(6)))
                .thenReturn(List.of(
                        attendance(100L, 1L, LocalDate.of(2026, 4, 27), 1, AttendanceStatus.PRESENT),
                        attendance(100L, 2L, LocalDate.of(2026, 4, 27), 1, AttendanceStatus.ABSENT),
                        attendance(101L, 1L, LocalDate.of(2026, 5, 2), 2, AttendanceStatus.ABSENT)));

        TeacherAttendanceExportModel model = service.buildReportModels(List.of(WEEK_START)).get(0);

        assertThat(model.columns()).extracting(TeacherAttendanceExportModel.Column::lessonId)
                .containsExactly(100L, 101L);
        assertThat(model.rows()).hasSize(40);
        TeacherAttendanceExportModel.Row alpha = model.rows().stream().filter(row -> row.studentId() == 1L).findFirst().orElseThrow();
        assertThat(alpha.cellsByLessonId()).containsOnlyKeys(100L);
        assertThat(alpha.cellsByLessonId().get(100L).symbol()).isEqualTo("+");
        TeacherAttendanceExportModel.Row beta = model.rows().stream().filter(row -> row.studentId() == 2L).findFirst().orElseThrow();
        assertThat(beta.cellsByLessonId().get(100L).symbol()).isEqualTo("н");
        TeacherAttendanceExportModel.Row departed = model.rows().stream().filter(row -> row.studentId() == 40L).findFirst().orElseThrow();
        // There is no attendance record for this student/lesson, so do not infer an absence.
        assertThat(departed.cellsByLessonId()).isEmpty();
        assertThat(alpha.metrics()).isEmpty();
        verify(academicGrpcClient).getGroupMembers(10L, LocalDate.of(2026, 4, 27), 1L);
        verify(academicGrpcClient).getGroupMembers(10L, LocalDate.of(2026, 5, 2), 1L);
    }

    @Test
    void htmlAndXlsxKeepTheSameEscapedMatrixAndReadableFrozenHeaders() throws Exception {
        TeacherAttendanceExportModel model = oneWeekModel();
        HeadmanWeeklyTabularRenderer renderer = new HeadmanWeeklyTabularRenderer();

        String html = new String(renderer.renderHtml(List.of(model)), StandardCharsets.UTF_8);
        String sheet = zipText(renderer.renderXlsx(List.of(model)), "xl/worksheets/sheet1.xml");
        String styles = zipText(renderer.renderXlsx(List.of(model)), "xl/styles.xml");

        assertThat(html).contains("<th scope=\"row\">Alpha &lt;script&gt;</th>")
                .contains("border-collapse:collapse")
                .contains("overflow-x:auto");
        assertThat(sheet).contains("xSplit=\"1\" ySplit=\"7\"")
                .contains("width=\"34\"")
                .contains("width=\"24\"")
                .contains("Alpha &lt;script&gt;")
                .contains("s=\"1\"");
        assertThat(styles).contains("wrapText=\"1\"").contains("FFEAF1F7");
    }

    @Test
    void xmlRenderersRemoveXml10ForbiddenRosterControlsAndPreserveTheOtherText() throws Exception {
        stubBase(semester("2026-04-01", "2026-05-31"));
        String rosterName = "А" + (char) 0x0B + "Б";
        when(scheduleGrpcClient.getLessonsByGroup(10L, 1L, "2026-04-27", "2026-05-03"))
                .thenReturn(LessonsResponse.newBuilder()
                        .addLessons(lesson(100L, "2026-04-27", "09:00", 1, "closed"))
                        .build());
        when(academicGrpcClient.getSubjectDetailsByIds(List.of(5L)))
                .thenReturn(Map.of(5L, new AcademicGrpcClient.SubjectDetails("Math", "lecture")));
        when(academicGrpcClient.getGroupMembers(10L, WEEK_START, 1L))
                .thenReturn(roster(WEEK_START, List.of(student(1L, rosterName))));
        when(attendanceReadPort.findByGroupAndDateRange(10L, WEEK_START, WEEK_START.plusDays(6)))
                .thenReturn(List.of());

        TeacherAttendanceExportModel model = service.buildReportModels(List.of(WEEK_START)).get(0);
        String wordXml = zipText(new TeacherAttendanceDocxRenderer().render(model), "word/document.xml");
        String sheetXml = zipText(new HeadmanWeeklyTabularRenderer().renderXlsx(List.of(model)),
                "xl/worksheets/sheet1.xml");

        assertThat(model.rows()).hasSize(1);
        assertThat(model.rows().get(0).displayName()).isEqualTo(rosterName);
        assertThat(textValues(parseXml(wordXml), WORD_NS)).contains("АБ").noneMatch(value -> value.contains(rosterName));
        assertThat(textValues(parseXml(sheetXml), SHEET_NS)).contains("АБ").noneMatch(value -> value.contains(rosterName));
    }

    @Test
    void singleWeekPngReturnsCompletePageArchiveMimeAndFilename() {
        stubBase(semester("2026-04-01", "2026-05-31"));
        when(scheduleGrpcClient.getLessonsByGroup(10L, 1L, "2026-04-27", "2026-05-03"))
                .thenReturn(LessonsResponse.getDefaultInstance());
        when(attendanceReadPort.findByGroupAndDateRange(10L, WEEK_START, WEEK_START.plusDays(6))).thenReturn(List.of());
        when(documentRendererGrpcClient.convertDocxForHeadmanWeeklyExport(any(byte[].class), eq(TargetFormat.PNG_PAGES_ZIP)))
                .thenReturn(new byte[]{1, 2, 3});

        HeadmanWeeklyExportResult result = service.exportSingleWeek(WEEK_START, "png");

        assertThat(result.fileName()).isEqualTo("UVPV511_27.04.2026_03.05.2026_png.zip");
        assertThat(result.contentType()).isEqualTo("application/zip");
        assertThat(result.content()).containsExactly(1, 2, 3);
        verify(documentRendererGrpcClient).convertDocxForHeadmanWeeklyExport(any(byte[].class), eq(TargetFormat.PNG_PAGES_ZIP));
    }

    @Test
    void rendererSizeLimitBecomesExplicitTooLargeExportError() {
        stubBase(semester("2026-04-01", "2026-05-31"));
        when(scheduleGrpcClient.getLessonsByGroup(10L, 1L, "2026-04-27", "2026-05-03"))
                .thenReturn(LessonsResponse.getDefaultInstance());
        when(attendanceReadPort.findByGroupAndDateRange(10L, WEEK_START, WEEK_START.plusDays(6))).thenReturn(List.of());
        when(documentRendererGrpcClient.convertDocxForHeadmanWeeklyExport(any(byte[].class), eq(TargetFormat.PDF)))
                .thenThrow(io.grpc.Status.RESOURCE_EXHAUSTED.asRuntimeException());

        assertThatThrownBy(() -> service.exportSingleWeek(WEEK_START, "pdf"))
                .isInstanceOf(ReportExportTooLargeException.class)
                .hasMessageContaining("меньше недель");
    }

    @Test
    void requestCannotSelectFutureOrForeignSemesterWeek() {
        when(academicGrpcClient.getActiveSemester()).thenReturn(semester("2026-04-01", "2026-05-31"));

        assertThatThrownBy(() -> service.exportSelectedWeeks(new HeadmanWeeklyExportRequest(
                List.of(LocalDate.of(2026, 5, 4)), "docx")))
                .isInstanceOf(ReportValidationException.class);
        verifyNoInteractions(scheduleGrpcClient, attendanceReadPort, documentRendererGrpcClient);
    }

    private TeacherAttendanceExportModel oneWeekModel() {
        stubBase(semester("2026-04-01", "2026-05-31"));
        when(scheduleGrpcClient.getLessonsByGroup(10L, 1L, "2026-04-27", "2026-05-03"))
                .thenReturn(LessonsResponse.newBuilder()
                        .addLessons(lesson(100L, "2026-04-27", "09:00", 1, "closed"))
                        .build());
        when(academicGrpcClient.getSubjectDetailsByIds(List.of(5L)))
                .thenReturn(Map.of(5L, new AcademicGrpcClient.SubjectDetails("Math", "lecture")));
        when(academicGrpcClient.getGroupMembers(10L, LocalDate.of(2026, 4, 27), 1L))
                .thenReturn(roster(LocalDate.of(2026, 4, 27), List.of(student(1, "Alpha <script>"))));
        when(attendanceReadPort.findByGroupAndDateRange(10L, WEEK_START, WEEK_START.plusDays(6)))
                .thenReturn(List.of(attendance(100L, 1L, WEEK_START, 1, AttendanceStatus.PRESENT)));
        return service.buildReportModels(List.of(WEEK_START)).get(0);
    }

    private void stubBase(SemesterResponse semester) {
        when(academicGrpcClient.getActiveSemester()).thenReturn(semester);
        when(academicGrpcClient.getGroup(10L)).thenReturn(GroupResponse.newBuilder()
                .setId(10L).setName("UVPV511").setIsActive(true).build());
    }

    private static SemesterResponse semester(String dateFrom, String dateTo) {
        return SemesterResponse.newBuilder().setId(1L).setName("Spring 2026")
                .setDateFrom(dateFrom).setDateTo(dateTo).build();
    }

    private static GroupMembersResponse roster(LocalDate date, List<StudentInfo> students) {
        return GroupMembersResponse.newBuilder().setAsOfDate(date.toString()).setSemesterId(1L)
                .addAllStudents(students).build();
    }

    private static StudentInfo student(long id, String name) {
        return StudentInfo.newBuilder().setUserId(id).setDisplayName(name).build();
    }

    private static LessonResponse lesson(long id, String date, String startTime, int number, String status) {
        return LessonResponse.newBuilder().setId(id).setGroupId(10L).setSemesterId(1L)
                .setSubjectId(5L).setDate(date).setStartTime(startTime).setLessonNumber(number)
                .setLessonType("lecture").setStatus(status).build();
    }

    private static AttendanceRecord attendance(long lessonId, long userId, LocalDate date,
                                               int lessonNumber, AttendanceStatus status) {
        return new AttendanceRecord(lessonId, userId, 10L, 5L, date, lessonNumber,
                status, AttendanceSource.HEADMAN, null);
    }

    private static String zipText(byte[] bytes, String name) throws Exception {
        try (ZipInputStream zip = new ZipInputStream(new ByteArrayInputStream(bytes), StandardCharsets.UTF_8)) {
            ZipEntry entry;
            while ((entry = zip.getNextEntry()) != null) {
                if (entry.getName().equals(name)) return new String(zip.readAllBytes(), StandardCharsets.UTF_8);
            }
        }
        throw new IllegalArgumentException("Missing ZIP entry: " + name);
    }

    private static Document parseXml(String xml) throws Exception {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setNamespaceAware(true);
        factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
        return factory.newDocumentBuilder().parse(
                new ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8)));
    }

    private static List<String> textValues(Document document, String namespace) {
        NodeList elements = document.getElementsByTagNameNS(namespace, "t");
        return IntStream.range(0, elements.getLength())
                .mapToObj(index -> elements.item(index).getTextContent())
                .toList();
    }
}
