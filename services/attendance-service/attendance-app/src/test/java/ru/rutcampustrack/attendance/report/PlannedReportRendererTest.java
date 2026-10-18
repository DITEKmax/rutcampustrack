package ru.rutcampustrack.attendance.report;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import ru.rutcampustrack.attendance.contract.enums.*;
import ru.rutcampustrack.attendance.excuse.ExcuseRepository;
import ru.rutcampustrack.attendance.grpc.AcademicGrpcClient;
import ru.rutcampustrack.attendance.grpc.DocumentRendererGrpcClient;
import ru.rutcampustrack.attendance.grpc.ScheduleGrpcClient;
import ru.rutcampustrack.attendance.latecheckin.LateCheckinRepository;
import ru.rutcampustrack.attendance.report.planned.*;
import ru.rutcampustrack.attendance.report.teacher.TeacherAttendanceDocxRenderer;
import ru.rutcampustrack.attendance.security.RequestContext;
import ru.rutcampustrack.attendance.shared.port.*;
import ru.rutcampustrack.reportrendering.grpc.*;
import ru.rutcampustrack.teacher.grpc.TeacherAttendanceReportKind;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.time.*;
import java.util.*;
import java.util.stream.IntStream;
import java.util.zip.ZipInputStream;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;

class PlannedReportRendererTest {
    private static final String WORD =
            "http://schemas.openxmlformats.org/wordprocessingml/2006/main";
    private final AcademicGrpcClient academic = mock(AcademicGrpcClient.class);
    private final ScheduleGrpcClient schedule = mock(ScheduleGrpcClient.class);
    private final RequestContext caller = mock(RequestContext.class);
    private final AttendanceReadPort marks = mock(AttendanceReadPort.class);
    private final DocumentRendererGrpcClient converter = mock(DocumentRendererGrpcClient.class);
    private final ReportExecution retry = mock(ReportExecution.class);
    private PlannedReportRenderer renderer;
    private final Map<Long, LocalDate> markDates = new HashMap<>();

    @BeforeEach
    void setUp() {
        Clock clock =
                Clock.fixed(Instant.parse("2026-10-18T09:00:00Z"), ZoneId.of("Europe/Moscow"));
        var reports = new ReportService(marks, academic, schedule, null, caller, clock, null);
        var weekly =
                new HeadmanWeeklyReportService(
                        academic,
                        schedule,
                        marks,
                        new TeacherAttendanceDocxRenderer(),
                        new HeadmanWeeklyTabularRenderer(),
                        converter,
                        caller,
                        clock,
                        retry);
        var stats =
                new HeadmanStatsService(
                        academic,
                        schedule,
                        marks,
                        mock(ExcuseRepository.class),
                        mock(LateCheckinRepository.class),
                        new HeadmanStatsDocxRenderer(),
                        new HeadmanStatsTabularRenderer(),
                        converter,
                        caller,
                        clock);
        var composition =
                new HeadmanGroupCompositionService(
                        caller, academic, converter, new HeadmanGroupCompositionRenderer(), clock);
        renderer =
                new PlannedReportRenderer(
                        reports,
                        weekly,
                        stats,
                        composition,
                        new HeadmanStatsTrendRenderer(),
                        converter);
        when(marks.findByLessonId(anyLong()))
                .thenAnswer(
                        call ->
                                records(
                                        call.getArgument(0),
                                        markDates.getOrDefault(
                                                (Long) call.getArgument(0),
                                                LocalDate.of(2026, 10, 14))));
        when(marks.findByLessonIds(anyList()))
                .thenAnswer(
                        call ->
                                ((List<Long>) call.getArgument(0))
                                        .stream()
                                                .flatMap(
                                                        id ->
                                                                records(
                                                                        id,
                                                                        markDates.getOrDefault(
                                                                                id,
                                                                                LocalDate.of(
                                                                                        2026, 10,
                                                                                        14)))
                                                                        .stream())
                                                .toList());
        when(marks.findByGroupAndDateRange(anyLong(), any(), any()))
                .thenAnswer(
                        call -> {
                            LocalDate first = call.getArgument(1), last = call.getArgument(2);
                            if (!LocalDate.of(2026, 10, 14).isBefore(first)
                                    && !LocalDate.of(2026, 10, 14).isAfter(last))
                                return records(101L, LocalDate.of(2026, 10, 14));
                            return IntStream.range(0, 36)
                                    .mapToObj(
                                            i -> LocalDate.of(2026, 1, 5).plusWeeks(i).plusDays(2))
                                    .filter(d -> !d.isBefore(first) && !d.isAfter(last))
                                    .flatMap(
                                            d ->
                                                    records(
                                                            101L
                                                                    + java.time.temporal.ChronoUnit
                                                                            .WEEKS
                                                                            .between(
                                                                                    LocalDate.of(
                                                                                            2026, 1,
                                                                                            7),
                                                                                    d),
                                                            d)
                                                            .stream())
                                    .toList();
                        });
    }

    @ParameterizedTest
    @ValueSource(
            strings = {
                "TEACHER_JOURNAL",
                "TEACHER_STATS",
                "HEADMAN_WEEKLY_CURRENT",
                "HEADMAN_WEEKLY_SELECTED",
                "HEADMAN_STATS",
                "HEADMAN_STATS_TREND",
                "HEADMAN_GROUP_COMPOSITION",
                "HEADMAN_SUBJECT_JOURNAL"
            })
    void allEightKindsUseActualHtmlRenderersAndLocalMarksWithoutLiveReaders(String kind) {
        var result = renderer.render(admit(ReportPlanFixtures.plan(kind, "html")));
        String html = result.getBody().toStringUtf8();
        assertThat(html).contains("УИТ-311").doesNotContain("${");
        assertThat(result.getContentType()).contains("text/html");
        assertThat(result.getFilename()).endsWith(".html");
        verifyNoInteractions(academic, schedule, caller, converter, retry);
    }

    @Test
    void twoDistinctEighteenWeekJobsAndShortPeriodKeepActualDocxMatrices() throws Exception {
        List<String> first = weeks(0, 18), second = weeks(18, 18), shortPeriod = weeks(4, 3);
        List<byte[]> documents = new ArrayList<>();
        for (List<String> selected : List.of(first, second, shortPeriod)) {
            var plan =
                    semesterPlan("HEADMAN_WEEKLY_SELECTED", "docx").toBuilder()
                            .setJobId(UUID.randomUUID().toString())
                            .setLeaseId(UUID.randomUUID().toString())
                            .setWeekly(
                                    ReportWeeklySelector.newBuilder()
                                            .addAllWeekStarts(selected)
                                            .setFormat("docx"))
                            .build();
            var result = renderer.render(admit(plan));
            documents.add(result.getBody().toByteArray());
            String xml = entry(result.getBody().toByteArray(), "word/document.xml");
            var document = parse(xml);
            assertThat(document.getElementsByTagNameNS(WORD, "tbl").getLength())
                    .isEqualTo(selected.size());
            assertThat(document.getElementsByTagNameNS(WORD, "tblHeader").getLength())
                    .isEqualTo(selected.size());
            assertThat(xml)
                    .contains("Студент 1", "Студент 2", "Студент 3", "УИТ-311", "Математика")
                    .doesNotContain("${");
            for (String week : selected)
                assertThat(xml)
                        .contains(
                                LocalDate.parse(week)
                                        .format(
                                                java.time.format.DateTimeFormatter.ofPattern(
                                                        "dd.MM.yyyy")));
            var tables = document.getElementsByTagNameNS(WORD, "tbl");
            for (int index = 0; index < tables.getLength(); index++) {
                var table = (org.w3c.dom.Element) tables.item(index);
                assertThat(table.getElementsByTagNameNS(WORD, "tr").getLength()).isEqualTo(4);
                assertThat(table.getTextContent())
                        .contains("Студент 1", "Студент 2", "Студент 3", "+", "у");
            }
        }
        assertThat(documents.get(0)).isNotEqualTo(documents.get(1));
        verifyNoInteractions(academic, schedule, caller, converter, retry);
    }

    @Test
    void teacherJournalActualDocxSegmentsCoverSelectedEighteenWeeksAndShortRange()
            throws Exception {
        for (int[] range : List.of(new int[] {0, 18}, new int[] {18, 18}, new int[] {4, 3})) {
            var base = semesterPlan("TEACHER_JOURNAL", "docx").toBuilder();
            var selector =
                    base.getTeacherJournal().toBuilder()
                            .setDateFrom(LocalDate.of(2026, 1, 5).plusWeeks(range[0]).toString())
                            .setDateTo(
                                    LocalDate.of(2026, 1, 5)
                                            .plusWeeks(range[0] + range[1])
                                            .minusDays(1)
                                            .toString())
                            .setReportKind(
                                    TeacherAttendanceReportKind
                                            .TEACHER_ATTENDANCE_REPORT_KIND_SUBJECT_JOURNAL);
            base.getLessonsList()
                    .forEach(
                            lesson ->
                                    markDates.put(
                                            lesson.getId(), LocalDate.parse(lesson.getDate())));
            var result =
                    renderer.render(
                            admit(
                                    base.setJobId(UUID.randomUUID().toString())
                                            .setTeacherJournal(selector)
                                            .build()));
            String xml = entry(result.getBody().toByteArray(), "word/document.xml");
            var document = parse(xml);
            assertThat(document.getElementsByTagNameNS(WORD, "tbl").getLength())
                    .isEqualTo((range[1] + 5) / 6 + 1);
            assertThat(xml)
                    .contains("Студент 1", "Студент 2", "Студент 3", "Математика")
                    .doesNotContain("${");
            for (int i = range[0]; i < range[0] + range[1]; i++)
                assertThat(xml)
                        .contains(
                                LocalDate.of(2026, 1, 7)
                                        .plusWeeks(i)
                                        .format(
                                                java.time.format.DateTimeFormatter.ofPattern(
                                                        "dd.MM")));
        }
        verifyNoInteractions(academic, schedule, caller, converter, retry);
    }

    @ParameterizedTest
    @ValueSource(
            strings = {
                "TEACHER_JOURNAL",
                "TEACHER_STATS",
                "HEADMAN_WEEKLY_SELECTED",
                "HEADMAN_STATS",
                "HEADMAN_GROUP_COMPOSITION",
                "HEADMAN_SUBJECT_JOURNAL"
            })
    void nativeXlsxKeepsRosterAsText(String kind) throws Exception {
        var result = renderer.render(admit(ReportPlanFixtures.plan(kind, "xlsx")));
        assertThat(entry(result.getBody().toByteArray(), "xl/worksheets/sheet1.xml"))
                .contains("Студент 1")
                .doesNotContain("<f>");
        verifyNoInteractions(academic, schedule, caller, converter, retry);
    }

    @ParameterizedTest
    @ValueSource(
            strings = {
                "TEACHER_JOURNAL",
                "TEACHER_STATS",
                "HEADMAN_WEEKLY_SELECTED",
                "HEADMAN_STATS",
                "HEADMAN_GROUP_COMPOSITION",
                "HEADMAN_SUBJECT_JOURNAL"
            })
    void pdfAndPngDelegateActualCanonicalDocxToExistingConversionBoundary(String kind)
            throws Exception {
        for (String format : List.of("pdf", "png")) {
            reset(converter);
            var target =
                    format.equals("pdf")
                            ? ru.rutcampustrack.documentrenderer.grpc.TargetFormat.PDF
                            : ru.rutcampustrack.documentrenderer.grpc.TargetFormat.PNG_PAGES_ZIP;
            var input = new java.util.concurrent.atomic.AtomicReference<byte[]>();
            org.mockito.stubbing.Answer<byte[]> converted =
                    call -> {
                        input.set(call.getArgument(0));
                        return new byte[] {1, 2, 3};
                    };
            if (kind.startsWith("TEACHER_"))
                when(converter.convertDocxForTeacherExport(any(), eq(target)))
                        .thenAnswer(converted);
            else
                when(converter.convertDocxForHeadmanWeeklyExport(any(), eq(target)))
                        .thenAnswer(converted);
            var result = renderer.render(admit(ReportPlanFixtures.plan(kind, format)));
            assertThat(result.getBody().toByteArray()).containsExactly(1, 2, 3);
            assertThat(entry(input.get(), "word/document.xml"))
                    .contains("Студент 1", "УИТ-311")
                    .doesNotContain("${");
            assertThat(result.getFilename()).endsWith(format.equals("pdf") ? ".pdf" : ".zip");
            assertThat(result.getContentType())
                    .isEqualTo(format.equals("pdf") ? "application/pdf" : "application/zip");
        }
        verifyNoInteractions(academic, schedule, caller, retry);
    }

    @Test
    void realCompositionFilenameUsesUtf8ByteBudgetBeforeReturningReportEnvelope() {
        var base = ReportPlanFixtures.plan("HEADMAN_GROUP_COMPOSITION", "html");
        var oversized =
                base.toBuilder()
                        .setGroupComposition(
                                base.getGroupComposition().toBuilder()
                                        .setGroupName("я".repeat(512)))
                        .build();
        assertThatThrownBy(() -> renderer.render(admit(oversized)))
                .isInstanceOf(
                        ru.rutcampustrack.attendance.exception.ReportExportTooLargeException.class);
        verifyNoInteractions(academic, schedule, caller, converter, retry);
    }

    @Test
    void twentyMiBBodyAndBoundedMetadataFitDirectedTwentyOneMiBProtobufEnvelope() {
        var composition = mock(HeadmanGroupCompositionService.class);
        when(composition.forPlan(any())).thenReturn(composition);
        byte[] body = new byte[AdmittedReportPlan.MAX_BYTES];
        when(composition.export("html"))
                .thenReturn(
                        new HeadmanGroupCompositionExportResult(
                                "я".repeat(512), "t".repeat(256), body));
        var boundary = new PlannedReportRenderer(null, null, null, composition, null, null);
        var result =
                boundary.render(
                        admit(ReportPlanFixtures.plan("HEADMAN_GROUP_COMPOSITION", "html")));
        assertThat(result.getBody().size()).isEqualTo(AdmittedReportPlan.MAX_BYTES);
        assertThat(result.getSerializedSize()).isLessThan(21 * 1024 * 1024);
        when(composition.export("html"))
                .thenReturn(
                        new HeadmanGroupCompositionExportResult(
                                "report.html", "t".repeat(257), new byte[] {1}));
        assertThatThrownBy(
                        () ->
                                boundary.render(
                                        admit(
                                                ReportPlanFixtures.plan(
                                                        "HEADMAN_GROUP_COMPOSITION", "html"))))
                .isInstanceOf(
                        ru.rutcampustrack.attendance.exception.ReportExportTooLargeException.class);
    }

    @Test
    void trendPngIsActualDecodableImage() throws Exception {
        var result = renderer.render(admit(ReportPlanFixtures.plan("HEADMAN_STATS_TREND", "png")));
        var image =
                javax.imageio.ImageIO.read(
                        new ByteArrayInputStream(result.getBody().toByteArray()));
        assertThat(image).isNotNull();
        assertThat(image.getWidth()).isGreaterThan(100);
        verifyNoInteractions(converter, retry, academic, schedule, caller);
    }

    @Test
    void plannedWeeklyFailureIsNotRetriedByLegacyReportExecution() {
        reset(marks);
        when(marks.findByGroupAndDateRange(anyLong(), any(), any()))
                .thenThrow(
                        new org.springframework.dao.TransientDataAccessResourceException(
                                "synthetic local outage"));
        assertThatThrownBy(
                        () ->
                                renderer.render(
                                        admit(
                                                ReportPlanFixtures.plan(
                                                        "HEADMAN_WEEKLY_SELECTED", "docx"))))
                .isInstanceOf(org.springframework.dao.TransientDataAccessResourceException.class);
        verify(marks)
                .findByGroupAndDateRange(
                        33L, LocalDate.of(2026, 10, 12), LocalDate.of(2026, 10, 18));
        verifyNoInteractions(retry, academic, schedule, caller, converter);
    }

    private static AdmittedReportPlan admit(ReportRenderPlan plan) {
        return AdmittedReportPlan.admit(
                plan,
                ReportPlanFixtures.actor(
                        plan.getReportKind().startsWith("TEACHER_"),
                        plan.getReportKind().equals("HEADMAN_GROUP_COMPOSITION")));
    }

    private static List<String> weeks(int offset, int count) {
        return IntStream.range(offset, offset + count)
                .mapToObj(i -> LocalDate.of(2026, 1, 5).plusWeeks(i).toString())
                .toList();
    }

    private static ReportRenderPlan semesterPlan(String kind, String format) {
        var builder =
                ReportPlanFixtures.plan(kind, format).toBuilder().clearLessons().clearRosters();
        builder.setSemester(
                builder.getSemester().toBuilder()
                        .setDateFrom("2026-01-05")
                        .setDateTo("2026-09-13"));
        builder.addRosters(
                ReportGroupRoster.newBuilder()
                        .setGroupId(33)
                        .setRoster(ReportPlanFixtures.roster(false)));
        for (int i = 0; i < 36; i++) {
            String date = LocalDate.of(2026, 1, 7).plusWeeks(i).toString();
            builder.addLessons(
                    ReportPlanFixtures.lesson().toBuilder()
                            .setId(101 + i)
                            .setDate(date)
                            .setOccurrenceId(101 + i));
            builder.addRosters(
                    ReportGroupRoster.newBuilder()
                            .setGroupId(33)
                            .setRoster(
                                    ReportPlanFixtures.roster(true).toBuilder().setAsOfDate(date)));
        }
        if (kind.startsWith("TEACHER_")) {
            builder.setTeacherSubjects(
                    builder.getTeacherSubjects().toBuilder()
                            .setSubjects(
                                    0,
                                    builder.getTeacherSubjects().getSubjects(0).toBuilder()
                                            .setValidFrom("2026-01-05")
                                            .setValidUntilExclusive("2026-09-14")));
            builder.setTeacherAssignments(
                    builder.getTeacherAssignments().toBuilder()
                            .setSemesterDateFrom("2026-01-05")
                            .setSemesterDateTo("2026-09-13")
                            .setAssignments(
                                    0,
                                    builder.getTeacherAssignments().getAssignments(0).toBuilder()
                                            .setValidFrom("2026-01-05")
                                            .setValidUntilExclusive("2026-09-14")));
        }
        return builder.build();
    }

    private static List<AttendanceRecord> records(long id, LocalDate date) {
        return List.of(
                new AttendanceRecord(
                        id,
                        1L,
                        33L,
                        22L,
                        date,
                        1,
                        AttendanceStatus.PRESENT,
                        AttendanceSource.HEADMAN,
                        null),
                new AttendanceRecord(
                        id,
                        2L,
                        33L,
                        22L,
                        date,
                        1,
                        AttendanceStatus.EXCUSED,
                        AttendanceSource.HEADMAN,
                        null));
    }

    private static String entry(byte[] bytes, String path) throws Exception {
        try (var zip = new ZipInputStream(new ByteArrayInputStream(bytes))) {
            for (var e = zip.getNextEntry(); e != null; e = zip.getNextEntry())
                if (e.getName().equals(path))
                    return new String(zip.readAllBytes(), StandardCharsets.UTF_8);
        }
        throw new AssertionError("Missing rendered document part " + path);
    }

    private static org.w3c.dom.Document parse(String xml) throws Exception {
        var factory = DocumentBuilderFactory.newInstance();
        factory.setNamespaceAware(true);
        factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
        factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, "");
        factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "");
        return factory.newDocumentBuilder()
                .parse(new ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8)));
    }
}
