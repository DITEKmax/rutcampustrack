package ru.rutcampustrack.attendance.report;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.support.StaticListableBeanFactory;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import ru.rutcampustrack.academic.grpc.GroupMembersResponse;
import ru.rutcampustrack.academic.grpc.StudentInfo;
import ru.rutcampustrack.attendance.contract.dto.report.HeadmanStatsExportRequest;
import ru.rutcampustrack.attendance.contract.dto.report.HeadmanStatsFilter;
import ru.rutcampustrack.attendance.contract.dto.report.HeadmanStatsQueryRequest;
import ru.rutcampustrack.attendance.contract.dto.report.HeadmanStatsResponse.EmptyState;
import ru.rutcampustrack.attendance.contract.dto.report.HeadmanStatsResponse.SummaryTickets;
import ru.rutcampustrack.attendance.contract.dto.report.HeadmanStatsResponse.TicketCounts;
import ru.rutcampustrack.attendance.contract.dto.report.HeadmanStatsSort;
import ru.rutcampustrack.attendance.contract.dto.report.HeadmanStatsTrendQueryRequest;
import ru.rutcampustrack.attendance.contract.enums.AttendanceSource;
import ru.rutcampustrack.attendance.contract.enums.AttendanceStatus;
import ru.rutcampustrack.attendance.contract.enums.ExcuseTicketStatus;
import ru.rutcampustrack.attendance.contract.enums.LateCheckinRequestOrigin;
import ru.rutcampustrack.attendance.contract.enums.LateCheckinRequestStatus;
import ru.rutcampustrack.attendance.exception.AccessDeniedException;
import ru.rutcampustrack.attendance.excuse.ExcuseRepository;
import ru.rutcampustrack.attendance.excuse.entity.ExcuseTicket;
import ru.rutcampustrack.attendance.grpc.AcademicGrpcClient;
import ru.rutcampustrack.attendance.grpc.DocumentRendererGrpcClient;
import ru.rutcampustrack.attendance.grpc.ScheduleGrpcClient;
import ru.rutcampustrack.attendance.latecheckin.LateCheckinRepository;
import ru.rutcampustrack.attendance.latecheckin.entity.LateCheckinRequest;
import ru.rutcampustrack.attendance.security.RequestContext;
import ru.rutcampustrack.attendance.shared.port.AttendanceReadPort;
import ru.rutcampustrack.attendance.shared.port.AttendanceRecord;
import ru.rutcampustrack.attendance.studentrequest.entity.StudentLessonSnapshotDocument;
import ru.rutcampustrack.schedule.grpc.LessonResponse;
import ru.rutcampustrack.schedule.grpc.LessonsResponse;
import ru.rutcampustrack.shared.web.time.EducationalTime;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;

@ExtendWith(MockitoExtension.class)
class HeadmanStatsServiceTest {
    private static final LocalDate FROM = LocalDate.of(2026, 4, 1);
    private static final LocalDate TO = LocalDate.of(2026, 4, 30);
    private static final Instant GENERATED_AT = Instant.parse("2026-04-30T12:00:00Z");
    private static final Clock CLOCK = Clock.fixed(GENERATED_AT, ZoneId.of("Europe/Moscow"));

    @Mock private AcademicGrpcClient academicGrpcClient;
    @Mock private ScheduleGrpcClient scheduleGrpcClient;
    @Mock private AttendanceReadPort attendanceReadPort;
    @Mock private ExcuseRepository excuseRepository;
    @Mock private LateCheckinRepository lateCheckinRepository;
    @Mock private DocumentRendererGrpcClient documentRendererGrpcClient;
    @Mock private RequestContext requestContext;

    private HeadmanStatsService service;

    @BeforeEach
    void setUp() {
        service =
                new HeadmanStatsService(
                        academicGrpcClient,
                        scheduleGrpcClient,
                        attendanceReadPort,
                        excuseRepository,
                        lateCheckinRepository,
                        new HeadmanStatsDocxRenderer(),
                        new HeadmanStatsTabularRenderer(),
                        documentRendererGrpcClient,
                        requestContext,
                        CLOCK,
                        new HeadmanApprovedTicketResolver(excuseRepository, lateCheckinRepository));
        when(requestContext.getGroupId()).thenReturn(10L);
    }

    @Test
    void productionBindingUsesEducationalTimeForClosedLessonRows() {
        when(academicGrpcClient.hasAssistantPermission(10L, "VIEW_STATS")).thenReturn(true);
        stubStatisticsData();
        Clock realBeforeLessons =
                Clock.fixed(Instant.parse("2026-03-31T05:00:00Z"), CLOCK.getZone());
        var educationalTime =
                new StaticListableBeanFactory(Map.of("educationalTime", new EducationalTime(CLOCK)))
                        .getBeanProvider(EducationalTime.class);
        var production =
                new HeadmanStatsService(
                        academicGrpcClient,
                        scheduleGrpcClient,
                        attendanceReadPort,
                        excuseRepository,
                        lateCheckinRepository,
                        new HeadmanStatsDocxRenderer(),
                        new HeadmanStatsTabularRenderer(),
                        documentRendererGrpcClient,
                        requestContext,
                        realBeforeLessons,
                        new HeadmanApprovedTicketResolver(excuseRepository, lateCheckinRepository),
                        educationalTime);

        var result =
                production.query(
                        new HeadmanStatsQueryRequest(null, List.of(), 0, 50, List.of(), List.of()));

        assertThat(result.rows().stream().anyMatch(row -> row.studentId() == 1L))
                .as("Educationally completed lessons retain the own roster student")
                .isTrue();
        assertThat(result.context().lessonsCount()).isEqualTo(2);
    }

    @Test
    void noSelectedSemesterHasUnavailableReasonsAndKnownEmptySemesterHasEmptyReasons() {
        when(academicGrpcClient.hasAssistantPermission(10L, "VIEW_STATS")).thenReturn(true);
        when(academicGrpcClient.getGroupMembers(10L))
                .thenReturn(GroupMembersResponse.getDefaultInstance());
        when(academicGrpcClient.resolveHeadmanStatsScope(10L, null))
                .thenReturn(
                        ru.rutcampustrack.academic.grpc.HeadmanStatsScopeResponse.newBuilder()
                                .setGroupId(10L)
                                .setGroupName("Own group")
                                .build());
        var query = new HeadmanStatsQueryRequest(null, List.of(), 0, 50, List.of(), List.of());
        var unavailable = service.query(query);
        assertThat(unavailable.emptyState()).isEqualTo(EmptyState.NO_ACTIVE_SEMESTER);
        assertThat(unavailable.reasonCounts()).isNull();
        org.mockito.Mockito.verifyNoInteractions(excuseRepository);

        when(academicGrpcClient.resolveHeadmanStatsScope(10L, null)).thenReturn(scope(TO));
        when(scheduleGrpcClient.getLessonsByGroup(10L, 1L, FROM.toString(), TO.toString()))
                .thenReturn(LessonsResponse.getDefaultInstance());
        when(excuseRepository.submittedReasonCounts(10L, 1L)).thenReturn(List.of());
        assertThat(service.query(query).reasonCounts()).isEmpty();
        verify(excuseRepository).submittedReasonCounts(10L, 1L);
    }

    @Test
    void submittedReasonDistributionIsSemesterScopedUnpagedAndRejectsUnknownCodes() {
        when(academicGrpcClient.hasAssistantPermission(10L, "VIEW_STATS")).thenReturn(true);
        stubStatisticsData();
        when(excuseRepository.submittedReasonCounts(10L, 1L))
                .thenReturn(
                        List.of(
                                reasonCount("OTHER", 2L),
                                reasonCount("SUMMONS", 2L),
                                reasonCount("ILLNESS", 4L)));
        var query = new HeadmanStatsQueryRequest(null, List.of(), 8, 1, List.of(), List.of());
        var result = service.query(query);
        assertThat(result.rows()).isEmpty();
        assertThat(result.reasonCounts())
                .extracting(value -> value.code())
                .containsExactly("ILLNESS", "OTHER", "SUMMONS");
        assertThat(result.reasonCounts())
                .extracting(value -> value.count())
                .containsExactly(4L, 2L, 2L);
        when(excuseRepository.submittedReasonCounts(10L, 1L))
                .thenReturn(List.of(reasonCount("FUTURE_REASON", 1L)));
        assertThatThrownBy(() -> service.query(query))
                .isInstanceOf(
                        ru.rutcampustrack.attendance.exception.ReportExportUnavailableException
                                .class);
        when(academicGrpcClient.hasAssistantPermission(10L, "VIEW_STATS")).thenReturn(false);
        assertThatThrownBy(() -> service.query(query)).isInstanceOf(AccessDeniedException.class);
    }

    private static ExcuseRepository.SubmittedReasonCount reasonCount(String code, Long count) {
        return new ExcuseRepository.SubmittedReasonCount() {
            public String getCode() {
                return code;
            }

            public Long getCount() {
                return count;
            }
        };
    }

    @Test
    void currentViewStatsAssistantGetsFilteredCanonicalMetricsAndUnpagedExportUntilRevoked() {
        when(academicGrpcClient.hasAssistantPermission(10L, "VIEW_STATS"))
                .thenReturn(true, true, true, true, true, false, false);
        stubStatisticsData();
        HeadmanStatsQueryRequest query =
                new HeadmanStatsQueryRequest(
                        5L,
                        List.of("lecture"),
                        0,
                        1,
                        List.of(new HeadmanStatsSort("presentPercent", true)),
                        List.of(
                                new HeadmanStatsFilter(
                                        "presentPercent", null, BigDecimal.valueOf(50), null)));

        var result = service.query(query);

        assertThat(result.context().lessonsCount())
                .isEqualTo(2); // active, cancelled and deleted lessons are not counted as completed
        assertThat(result.filteredStudents()).isEqualTo(2);
        assertThat(result.rows()).extracting(row -> row.displayName()).containsExactly("Gamma");
        assertThat(result.rows().get(0).metrics().present())
                .satisfies(
                        metric -> {
                            assertThat(metric.numerator()).isEqualTo(2);
                            assertThat(metric.denominator()).isEqualTo(2);
                        });
        assertThat(result.summary().present().numerator()).isEqualTo(3);
        assertThat(result.summary().present().denominator()).isEqualTo(4);
        assertThat(result.summary().presentOrExcused().numerator()).isEqualTo(4);
        assertThat(result.summary().presentOrExcused().denominator()).isEqualTo(4);
        assertThat(result.summary().excused().numerator()).isEqualTo(1);
        assertThat(result.summaryTickets())
                .isEqualTo(
                        new SummaryTickets(new TicketCounts(1, 1, 0), new TicketCounts(1, 0, 0)));
        assertThat(result.hasNext()).isTrue();
        assertThat(result.totalElements()).isEqualTo(2);

        var alphaPage =
                service.query(
                        new HeadmanStatsQueryRequest(
                                5L,
                                List.of("lecture"),
                                1,
                                1,
                                List.of(new HeadmanStatsSort("presentPercent", true)),
                                List.of(
                                        new HeadmanStatsFilter(
                                                "presentPercent",
                                                null,
                                                BigDecimal.valueOf(50),
                                                null))));
        assertThat(alphaPage.rows()).extracting(row -> row.displayName()).containsExactly("Alpha");
        var lateCounts = alphaPage.rows().get(0).lateCheckin();
        assertThat(lateCounts.submitted())
                .isGreaterThanOrEqualTo(lateCounts.approved() + lateCounts.rejected());
        assertThat(lateCounts.submitted()).isEqualTo(1);
        assertThat(lateCounts.approved()).isEqualTo(1);
        assertThat(alphaPage.summaryTickets()).isEqualTo(result.summaryTickets());
        assertThat(alphaPage.hasPrevious()).isTrue();
        assertThat(alphaPage.hasNext()).isFalse();

        var betaOnly =
                service.query(
                        new HeadmanStatsQueryRequest(
                                5L,
                                List.of("lecture"),
                                0,
                                1,
                                List.of(),
                                List.of(
                                        new HeadmanStatsFilter(
                                                "displayName", "Beta", null, null))));
        assertThat(betaOnly.rows()).extracting(row -> row.displayName()).containsExactly("Beta");
        assertThat(betaOnly.summaryTickets())
                .isEqualTo(
                        new SummaryTickets(new TicketCounts(1, 0, 1), new TicketCounts(1, 1, 0)));

        var filteredEmpty =
                service.query(
                        new HeadmanStatsQueryRequest(
                                5L,
                                List.of("lecture"),
                                0,
                                1,
                                List.of(),
                                List.of(
                                        new HeadmanStatsFilter(
                                                "displayName", "Missing", null, null))));
        assertThat(filteredEmpty.rows()).isEmpty();
        assertThat(filteredEmpty.emptyState()).isEqualTo(EmptyState.FILTERED_EMPTY);
        assertThat(filteredEmpty.summaryTickets())
                .isEqualTo(
                        new SummaryTickets(new TicketCounts(0, 0, 0), new TicketCounts(0, 0, 0)));

        var exported =
                service.export(
                        new HeadmanStatsExportRequest(
                                5L,
                                List.of("lecture"),
                                List.of(new HeadmanStatsSort("presentPercent", true)),
                                List.of(
                                        new HeadmanStatsFilter(
                                                "presentPercent",
                                                null,
                                                BigDecimal.valueOf(50),
                                                null)),
                                "html"));
        String html = new String(exported.content(), StandardCharsets.UTF_8);
        assertThat(html)
                .contains("Alpha", "Gamma", "3</td><td>4</td><td>75.0%")
                .doesNotContain("Beta");
        assertThat(html)
                .contains(
                        "<dt>Фильтры</dt><dd>Процент «+»: от 50 (включительно)</dd>",
                        "<dt>Сортировка</dt><dd>1. Процент «+»: по убыванию</dd>");
        assertThat(exported.fileName()).endsWith("_математика.html");
        assertThat(exported.contentType()).isEqualTo("text/html; charset=UTF-8");

        assertThatThrownBy(() -> service.query(query)).isInstanceOf(AccessDeniedException.class);
        assertThatThrownBy(
                        () ->
                                service.export(
                                        new HeadmanStatsExportRequest(
                                                5L,
                                                List.of("lecture"),
                                                List.of(),
                                                List.of(),
                                                "html")))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void headmanFlagStillRequiresFreshActiveHeadmanGrantForReadAndExport() {
        lenient()
                .when(requestContext.isHeadman())
                .thenReturn(true); // A stale role flag must not bypass the current grant check.
        when(academicGrpcClient.hasAssistantPermission(10L, "VIEW_STATS"))
                .thenReturn(true, false, false);
        stubStatisticsData();
        HeadmanStatsQueryRequest query =
                new HeadmanStatsQueryRequest(null, List.of(), 0, 50, List.of(), List.of());

        assertThat(service.query(query).context().groupId()).isEqualTo(10L);
        assertThatThrownBy(() -> service.query(query)).isInstanceOf(AccessDeniedException.class);
        assertThatThrownBy(
                        () ->
                                service.export(
                                        new HeadmanStatsExportRequest(
                                                null, List.of(), List.of(), List.of(), "html")))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void trendUsesSummedHistoricalPairsSharedGridAndNullForEmptyBucketsAndDetailIsPersonal() {
        when(academicGrpcClient.hasAssistantPermission(10L, "VIEW_STATS"))
                .thenReturn(true, true, true, true, true);
        stubTrendData();

        var semester =
                service.trend(
                        new HeadmanStatsTrendQueryRequest(
                                HeadmanStatsTrendQueryRequest.Mode.SEMESTER,
                                null,
                                null,
                                List.of()));
        assertThat(semester.points()).hasSize(9);
        assertThat(semester.points().get(0).present())
                .satisfies(
                        metric -> {
                            assertThat(metric.numerator()).isEqualTo(1);
                            assertThat(metric.denominator()).isEqualTo(1);
                            assertThat(metric.percent()).isEqualTo(100.0);
                        });
        assertThat(semester.points().get(1).present())
                .satisfies(
                        metric -> {
                            assertThat(metric.numerator()).isZero();
                            assertThat(metric.denominator()).isEqualTo(2);
                            assertThat(metric.percent()).isZero();
                        });
        assertThat(semester.points().get(2).present().percent()).isNull();
        assertThat(semester.points().get(4).present().denominator()).isEqualTo(1);
        assertThat(semester.points().get(8).present().percent()).isNull();
        assertThat(semester.points().stream().mapToInt(point -> point.present().numerator()).sum())
                .isEqualTo(2);
        assertThat(
                        semester.points().stream()
                                .mapToInt(point -> point.present().denominator())
                                .sum())
                .isEqualTo(4);

        var bySubject =
                service.trend(
                        new HeadmanStatsTrendQueryRequest(
                                HeadmanStatsTrendQueryRequest.Mode.SUBJECT,
                                null,
                                5L,
                                List.of("lecture")));
        assertThat(bySubject.points()).hasSize(9);
        assertThat(bySubject.points().get(0).present().denominator()).isEqualTo(1);
        assertThat(bySubject.points().get(1).present().percent()).isNull();

        var byWeek =
                service.trend(
                        new HeadmanStatsTrendQueryRequest(
                                HeadmanStatsTrendQueryRequest.Mode.WEEK,
                                LocalDate.of(2026, 4, 6),
                                null,
                                List.of()));
        assertThat(byWeek.points()).hasSize(7);
        assertThat(byWeek.points().get(2).present().denominator()).isEqualTo(2);
        assertThat(byWeek.points().get(0).present().percent()).isNull();

        when(lateCheckinRepository.findByGroupIdAndSemesterIdAndStudentIdAndLessonIdInAndStatusIn(
                        eq(10L),
                        eq(1L),
                        eq(1L),
                        eq(List.of(100L, 101L, 102L, 103L, 104L)),
                        eq(
                                List.of(
                                        LateCheckinRequestStatus.PENDING,
                                        LateCheckinRequestStatus.APPROVED,
                                        LateCheckinRequestStatus.REJECTED,
                                        LateCheckinRequestStatus.CANCELLED)),
                        any(Pageable.class)))
                .thenReturn(
                        new PageImpl<>(
                                List.of(
                                        LateCheckinRequest.builder()
                                                .id("late-pending")
                                                .studentId(1L)
                                                .groupId(10L)
                                                .semesterId(1L)
                                                .lessonId(103L)
                                                .subjectId(5L)
                                                .subjectName("Математика")
                                                .subjectType("seminar")
                                                .lessonNumber(4)
                                                .lessonDate(LocalDate.of(2026, 4, 15))
                                                .status(LateCheckinRequestStatus.PENDING)
                                                .origin(LateCheckinRequestOrigin.MANUAL)
                                                .createdAt(GENERATED_AT.minusSeconds(120))
                                                .build(),
                                        LateCheckinRequest.builder()
                                                .id("late-cancelled")
                                                .studentId(1L)
                                                .groupId(10L)
                                                .semesterId(1L)
                                                .lessonId(104L)
                                                .subjectId(5L)
                                                .subjectName("Математика")
                                                .subjectType("practice")
                                                .lessonNumber(5)
                                                .lessonDate(LocalDate.of(2026, 4, 16))
                                                .status(LateCheckinRequestStatus.CANCELLED)
                                                .origin(LateCheckinRequestOrigin.AUTO_GEO_FAILURE)
                                                .createdAt(GENERATED_AT.minusSeconds(60))
                                                .build()),
                                PageRequest.of(0, 20),
                                2));
        when(excuseRepository.findByGroupIdAndSemesterIdAndStudentIdAndLessonIdsInAndStatusIn(
                        eq(10L),
                        eq(1L),
                        eq(1L),
                        eq(List.of(100L, 101L, 102L, 103L, 104L)),
                        eq(
                                List.of(
                                        ExcuseTicketStatus.SUBMITTED,
                                        ExcuseTicketStatus.APPROVED,
                                        ExcuseTicketStatus.REJECTED,
                                        ExcuseTicketStatus.CANCELLED)),
                        any(Pageable.class)))
                .thenReturn(
                        new PageImpl<>(
                                List.of(
                                        ExcuseTicket.builder()
                                                .id("excuse-cancelled")
                                                .studentId(1L)
                                                .groupId(10L)
                                                .semesterId(1L)
                                                .lessonIds(List.of(104L))
                                                .lessonSnapshots(
                                                        List.of(
                                                                StudentLessonSnapshotDocument
                                                                        .builder()
                                                                        .lessonId(104L)
                                                                        .groupId(10L)
                                                                        .subjectId(5L)
                                                                        .subjectName("Математика")
                                                                        .subjectType("practice")
                                                                        .semesterId(1L)
                                                                        .lessonNumber(5)
                                                                        .date(
                                                                                LocalDate.of(
                                                                                        2026, 4,
                                                                                        16))
                                                                        .status("cancelled")
                                                                        .build()))
                                                .status(ExcuseTicketStatus.CANCELLED)
                                                .createdAt(GENERATED_AT.minusSeconds(60))
                                                .decisionAt(GENERATED_AT)
                                                .build()),
                                PageRequest.of(0, 20),
                                1));

        when(lateCheckinRepository.findByGroupIdAndSemesterIdAndStatusIn(
                        10L,
                        1L,
                        List.of(
                                LateCheckinRequestStatus.PENDING,
                                LateCheckinRequestStatus.APPROVED,
                                LateCheckinRequestStatus.REJECTED)))
                .thenReturn(
                        List.of(
                                LateCheckinRequest.builder()
                                        .id("personal-approved")
                                        .studentId(1L)
                                        .groupId(10L)
                                        .semesterId(1L)
                                        .lessonId(102L)
                                        .status(LateCheckinRequestStatus.APPROVED)
                                        .origin(LateCheckinRequestOrigin.MANUAL)
                                        .build()));
        when(excuseRepository.findByLessonIdsInAndStatusIn(
                        List.of(100L, 101L, 102L),
                        List.of(
                                ExcuseTicketStatus.SUBMITTED,
                                ExcuseTicketStatus.APPROVED,
                                ExcuseTicketStatus.REJECTED)))
                .thenReturn(
                        List.of(
                                ExcuseTicket.builder()
                                        .id("personal-rejected")
                                        .studentId(1L)
                                        .groupId(10L)
                                        .semesterId(1L)
                                        .lessonIds(List.of(102L))
                                        .status(ExcuseTicketStatus.REJECTED)
                                        .build()));
        when(excuseRepository.findByLessonIdsInAndStatusIn(
                        List.of(100L, 101L),
                        List.of(
                                ExcuseTicketStatus.SUBMITTED,
                                ExcuseTicketStatus.APPROVED,
                                ExcuseTicketStatus.REJECTED)))
                .thenReturn(List.of());
        var filtered =
                service.query(
                        new HeadmanStatsQueryRequest(5L, List.of(), 0, 50, List.of(), List.of()));
        assertThat(
                        filtered.rows().stream()
                                .filter(row -> row.studentId() == 1L)
                                .findFirst()
                                .orElseThrow()
                                .lateCheckin()
                                .submitted())
                .isZero(); // request belongs to the other subject

        var detail = service.studentDetail(1L, 0, 0, 20);
        assertThat(detail.rank()).isNull(); // A legacy scope never invents a rank from row order.
        assertThat(detail.metrics().present().numerator()).isEqualTo(2);
        assertThat(detail.metrics().present().denominator()).isEqualTo(3);
        assertThat(detail.metrics().presentOrExcused().numerator()).isEqualTo(3);
        assertThat(detail.metrics().presentOrExcused().denominator()).isEqualTo(3);
        assertThat(detail.subjects()).hasSize(2);
        assertThat(detail.subjects().stream().filter(subject -> subject.subjectId() == 5L).toList())
                .singleElement()
                .satisfies(
                        subject -> {
                            assertThat(subject.lessonTypes())
                                    .extracting(type -> type.code())
                                    .containsExactly("lecture", "practice");
                        });
        assertThat(detail.sources().studentGeo()).isEqualTo(1);
        assertThat(detail.sources().headmanManual()).isEqualTo(1);
        assertThat(detail.lateCheckin().submitted()).isEqualTo(1);
        assertThat(detail.lateCheckin().approved()).isEqualTo(1);
        assertThat(detail.excuse().submitted()).isEqualTo(1);
        assertThat(detail.excuse().rejected()).isEqualTo(1);
        assertThat(detail.weeks()).hasSize(9);
        assertThat(detail.weeks().get(2).present().percent()).isNull();
        assertThat(detail.lateCheckins().totalElements()).isEqualTo(2);
        assertThat(detail.lateCheckins().items())
                .extracting(ticket -> ticket.status())
                .containsExactlyInAnyOrder(
                        LateCheckinRequestStatus.PENDING, LateCheckinRequestStatus.CANCELLED);
        assertThat(detail.lateCheckins().items())
                .extracting(ticket -> ticket.origin())
                .containsExactlyInAnyOrder(
                        LateCheckinRequestOrigin.MANUAL, LateCheckinRequestOrigin.AUTO_GEO_FAILURE);
        assertThat(detail.excuses().totalElements()).isEqualTo(1);
        assertThat(detail.excuses().items())
                .singleElement()
                .satisfies(
                        ticket -> {
                            assertThat(ticket.status()).isEqualTo(ExcuseTicketStatus.CANCELLED);
                            assertThat(ticket.lessons())
                                    .singleElement()
                                    .satisfies(
                                            lesson -> {
                                                assertThat(lesson.subjectName())
                                                        .isEqualTo("Математика");
                                                assertThat(lesson.lessonDate())
                                                        .isEqualTo(LocalDate.of(2026, 4, 16));
                                            });
                        });
        verify(lateCheckinRepository)
                .findByGroupIdAndSemesterIdAndStudentIdAndLessonIdInAndStatusIn(
                        eq(10L),
                        eq(1L),
                        eq(1L),
                        eq(List.of(100L, 101L, 102L, 103L, 104L)),
                        eq(
                                List.of(
                                        LateCheckinRequestStatus.PENDING,
                                        LateCheckinRequestStatus.APPROVED,
                                        LateCheckinRequestStatus.REJECTED,
                                        LateCheckinRequestStatus.CANCELLED)),
                        any(Pageable.class));
        verify(excuseRepository)
                .findByGroupIdAndSemesterIdAndStudentIdAndLessonIdsInAndStatusIn(
                        eq(10L),
                        eq(1L),
                        eq(1L),
                        eq(List.of(100L, 101L, 102L, 103L, 104L)),
                        eq(
                                List.of(
                                        ExcuseTicketStatus.SUBMITTED,
                                        ExcuseTicketStatus.APPROVED,
                                        ExcuseTicketStatus.REJECTED,
                                        ExcuseTicketStatus.CANCELLED)),
                        any(Pageable.class));

        var currentStudentWithoutPastLessons = service.studentDetail(3L, 0, 0, 20);
        assertThat(currentStudentWithoutPastLessons.emptyState())
                .isEqualTo(
                        ru.rutcampustrack.attendance.contract.dto.report
                                .HeadmanStatsStudentDetailResponse.EmptyState.NO_COMPLETED_LESSONS);
        assertThat(currentStudentWithoutPastLessons.weeks()).isEmpty();
    }

    @Test
    void studentDetailRequiresFreshViewStatsAndRejectsForeignStudentId() {
        when(academicGrpcClient.hasAssistantPermission(10L, "VIEW_STATS")).thenReturn(true, false);
        when(academicGrpcClient.resolveHeadmanStatsScope(10L, null, 999L))
                .thenThrow(
                        new ru.rutcampustrack.attendance.contract.exception
                                .ResourceNotFoundException("Student", "id", 999L));

        assertThatThrownBy(() -> service.studentDetail(999L, 0, 0, 20))
                .isInstanceOf(
                        ru.rutcampustrack.attendance.contract.exception.ResourceNotFoundException
                                .class);
        assertThatThrownBy(() -> service.studentDetail(1L, 0, 0, 20))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void targetRankMapsHiddenScopeAndRechecksAuthorityBeforeReturningDetail() {
        when(academicGrpcClient.hasAssistantPermission(10L, "VIEW_STATS")).thenReturn(true);
        when(academicGrpcClient.getGroupMembers(10L))
                .thenReturn(GroupMembersResponse.getDefaultInstance());
        when(scheduleGrpcClient.getLessonsByGroup(10L, 1L, FROM.toString(), TO.toString()))
                .thenReturn(LessonsResponse.getDefaultInstance());
        var hidden =
                ru.rutcampustrack.academic.grpc.StudentProjectionScopeResponse.newBuilder()
                        .setStudentId(1L)
                        .setSemesterId(1L)
                        .setRankVisibility(
                                ru.rutcampustrack.academic.grpc.StudentProjectionRankVisibility
                                        .STUDENT_PROJECTION_RANK_VISIBILITY_HIDDEN)
                        .build();
        var admitted = scope(TO).toBuilder().setStudentRankScope(hidden).build();
        when(academicGrpcClient.resolveHeadmanStatsScope(10L, null, 1L)).thenReturn(admitted);
        when(academicGrpcClient.resolveHeadmanStatsScope(10L, 1L, 1L)).thenReturn(admitted);
        service.configureStudentRank(
                new ru.rutcampustrack.attendance.report.studentprojection
                        .StudentAttendanceProjectionService(
                        attendanceReadPort, academicGrpcClient, scheduleGrpcClient, CLOCK));
        var detail = service.studentDetail(1L, 0, 0, 20);
        assertThat(detail.rank().visibility())
                .isEqualTo(
                        ru.rutcampustrack.attendance.contract.dto.report
                                .HeadmanStatsStudentDetailResponse.Visibility.HIDDEN);
        assertThat(detail.rank().position()).isNull();
        assertThat(detail.rank().participantCount()).isZero();
        when(academicGrpcClient.resolveHeadmanStatsScope(10L, 1L, 1L))
                .thenThrow(new AccessDeniedException("Current VIEW_STATS revoked"));
        assertThatThrownBy(() -> service.studentDetail(1L, 0, 0, 20))
                .isInstanceOf(AccessDeniedException.class);
        var foreign =
                hidden.toBuilder()
                        .setRankGroupId(20L)
                        .setRankEligible(true)
                        .setRankVisibility(
                                ru.rutcampustrack.academic.grpc.StudentProjectionRankVisibility
                                        .STUDENT_PROJECTION_RANK_VISIBILITY_VISIBLE)
                        .build();
        when(academicGrpcClient.resolveHeadmanStatsScope(10L, null, 1L))
                .thenReturn(scope(TO).toBuilder().setStudentRankScope(foreign).build());
        assertThatThrownBy(() -> service.studentDetail(1L, 0, 0, 20))
                .isInstanceOf(
                        ru.rutcampustrack.attendance.exception.ReportExportUnavailableException
                                .class);
    }

    private void stubTrendData() {
        LocalDate semesterTo = LocalDate.of(2026, 5, 31);
        when(academicGrpcClient.resolveHeadmanStatsScope(10L, null)).thenReturn(scope(semesterTo));
        when(academicGrpcClient.resolveHeadmanStatsScope(
                        eq(10L),
                        org.mockito.ArgumentMatchers.isNull(),
                        org.mockito.ArgumentMatchers.anyLong()))
                .thenReturn(scope(semesterTo));
        when(scheduleGrpcClient.getLessonsByGroup(10L, 1L, FROM.toString(), semesterTo.toString()))
                .thenReturn(
                        LessonsResponse.newBuilder()
                                .addLessons(lesson(100L, "2026-04-01", 1, "closed"))
                                .addLessons(
                                        lesson(101L, "2026-04-08", 2, "closed").toBuilder()
                                                .setLessonType("practice")
                                                .build())
                                .addLessons(
                                        lesson(102L, "2026-04-29", 3, "closed").toBuilder()
                                                .setSubjectId(6L)
                                                .setLessonType("lab")
                                                .build())
                                .addLessons(
                                        lesson(103L, "2026-04-15", 4, "active").toBuilder()
                                                .setLessonType("seminar")
                                                .build())
                                .addLessons(
                                        lesson(104L, "2026-04-16", 5, "cancelled").toBuilder()
                                                .setLessonType("practice")
                                                .build())
                                .build());
        when(academicGrpcClient.getSubjectDetailsByIds(List.of(5L, 6L)))
                .thenReturn(
                        Map.of(
                                5L,
                                new AcademicGrpcClient.SubjectDetails("Математика", "lecture"),
                                6L,
                                new AcademicGrpcClient.SubjectDetails("Физика", "lab")));
        when(academicGrpcClient.getGroupMembers(10L))
                .thenReturn(
                        GroupMembersResponse.newBuilder()
                                .addStudents(student(1L, "Alpha"))
                                .addStudents(student(2L, "Beta"))
                                .addStudents(student(3L, "Current member without past lessons"))
                                .build());
        when(academicGrpcClient.getGroupMembers(10L, LocalDate.of(2026, 4, 1), 1L))
                .thenReturn(roster(LocalDate.of(2026, 4, 1), List.of(student(1L, "Alpha"))));
        when(academicGrpcClient.getGroupMembers(10L, LocalDate.of(2026, 4, 8), 1L))
                .thenReturn(
                        roster(
                                LocalDate.of(2026, 4, 8),
                                List.of(student(1L, "Alpha"), student(2L, "Beta"))));
        when(academicGrpcClient.getGroupMembers(10L, LocalDate.of(2026, 4, 15), 1L))
                .thenReturn(roster(LocalDate.of(2026, 4, 15), List.of(student(1L, "Alpha"))));
        when(academicGrpcClient.getGroupMembers(10L, LocalDate.of(2026, 4, 16), 1L))
                .thenReturn(roster(LocalDate.of(2026, 4, 16), List.of(student(1L, "Alpha"))));
        when(academicGrpcClient.getGroupMembers(10L, LocalDate.of(2026, 4, 29), 1L))
                .thenReturn(roster(LocalDate.of(2026, 4, 29), List.of(student(1L, "Alpha"))));
        when(attendanceReadPort.findByGroupAndDateRange(10L, FROM, semesterTo))
                .thenReturn(
                        List.of(
                                attendance(
                                        100L,
                                        1L,
                                        LocalDate.of(2026, 4, 1),
                                        1,
                                        AttendanceStatus.PRESENT,
                                        AttendanceSource.STUDENT_GEO),
                                attendance(
                                        101L,
                                        1L,
                                        LocalDate.of(2026, 4, 8),
                                        2,
                                        AttendanceStatus.EXCUSED,
                                        AttendanceSource.HEADMAN),
                                attendance(
                                        101L,
                                        2L,
                                        LocalDate.of(2026, 4, 8),
                                        2,
                                        AttendanceStatus.ABSENT,
                                        AttendanceSource.HEADMAN),
                                new AttendanceRecord(
                                        102L,
                                        1L,
                                        10L,
                                        6L,
                                        LocalDate.of(2026, 4, 29),
                                        3,
                                        AttendanceStatus.PRESENT,
                                        AttendanceSource.HEADMAN,
                                        null)));
    }

    private void stubStatisticsData() {
        when(academicGrpcClient.resolveHeadmanStatsScope(10L, null)).thenReturn(scope(TO));
        when(scheduleGrpcClient.getLessonsByGroup(10L, 1L, FROM.toString(), TO.toString()))
                .thenReturn(
                        LessonsResponse.newBuilder()
                                .addLessons(lesson(100L, "2026-04-01", 1, "closed"))
                                .addLessons(lesson(101L, "2026-04-02", 2, "closed"))
                                .addLessons(lesson(102L, "2026-04-03", 3, "started"))
                                .addLessons(lesson(103L, "2026-04-04", 4, "cancelled"))
                                .addLessons(lesson(104L, "2026-04-05", 5, "deleted"))
                                .build());
        when(academicGrpcClient.getSubjectDetailsByIds(List.of(5L)))
                .thenReturn(
                        Map.of(5L, new AcademicGrpcClient.SubjectDetails("Математика", "lecture")));
        List<StudentInfo> students =
                List.of(student(1L, "Alpha"), student(2L, "Beta"), student(3L, "Gamma"));
        when(academicGrpcClient.getGroupMembers(10L))
                .thenReturn(GroupMembersResponse.newBuilder().addAllStudents(students).build());
        when(academicGrpcClient.getGroupMembers(10L, LocalDate.of(2026, 4, 1), 1L))
                .thenReturn(roster(LocalDate.of(2026, 4, 1), students));
        when(academicGrpcClient.getGroupMembers(10L, LocalDate.of(2026, 4, 2), 1L))
                .thenReturn(roster(LocalDate.of(2026, 4, 2), students));
        when(attendanceReadPort.findByGroupAndDateRange(10L, FROM, TO))
                .thenReturn(
                        List.of(
                                attendance(
                                        100L,
                                        1L,
                                        LocalDate.of(2026, 4, 1),
                                        1,
                                        AttendanceStatus.PRESENT,
                                        AttendanceSource.LATE_CHECKIN),
                                attendance(
                                        101L,
                                        1L,
                                        LocalDate.of(2026, 4, 2),
                                        2,
                                        AttendanceStatus.EXCUSED,
                                        AttendanceSource.HEADMAN),
                                attendance(
                                        100L,
                                        2L,
                                        LocalDate.of(2026, 4, 1),
                                        1,
                                        AttendanceStatus.ABSENT,
                                        AttendanceSource.HEADMAN),
                                attendance(
                                        101L,
                                        2L,
                                        LocalDate.of(2026, 4, 2),
                                        2,
                                        AttendanceStatus.ABSENT,
                                        AttendanceSource.HEADMAN),
                                attendance(
                                        100L,
                                        3L,
                                        LocalDate.of(2026, 4, 1),
                                        1,
                                        AttendanceStatus.PRESENT,
                                        AttendanceSource.STUDENT_GEO),
                                attendance(
                                        101L,
                                        3L,
                                        LocalDate.of(2026, 4, 2),
                                        2,
                                        AttendanceStatus.PRESENT,
                                        AttendanceSource.HEADMAN)));
        when(lateCheckinRepository.findByGroupIdAndSemesterIdAndStatusIn(
                        10L,
                        1L,
                        List.of(
                                LateCheckinRequestStatus.PENDING,
                                LateCheckinRequestStatus.APPROVED,
                                LateCheckinRequestStatus.REJECTED)))
                .thenReturn(
                        List.of(
                                LateCheckinRequest.builder()
                                        .id("late-1")
                                        .studentId(1L)
                                        .groupId(10L)
                                        .lessonId(100L)
                                        .subjectId(5L)
                                        .subjectName("Математика")
                                        .subjectType("lecture")
                                        .semesterId(1L)
                                        .lessonNumber(1)
                                        .lessonDate(LocalDate.of(2026, 4, 1))
                                        .status(LateCheckinRequestStatus.APPROVED)
                                        .origin(LateCheckinRequestOrigin.AUTO_GEO_FAILURE)
                                        .createdAt(GENERATED_AT.minusSeconds(60))
                                        .updatedAt(GENERATED_AT)
                                        .build(),
                                LateCheckinRequest.builder()
                                        .id("late-beta")
                                        .studentId(2L)
                                        .groupId(10L)
                                        .semesterId(1L)
                                        .lessonId(100L)
                                        .status(LateCheckinRequestStatus.REJECTED)
                                        .build()));
        when(excuseRepository.findByLessonIdsInAndStatusIn(
                        List.of(100L, 101L),
                        List.of(
                                ExcuseTicketStatus.SUBMITTED,
                                ExcuseTicketStatus.APPROVED,
                                ExcuseTicketStatus.REJECTED)))
                .thenReturn(
                        List.of(
                                ExcuseTicket.builder()
                                        .id("excuse-1")
                                        .studentId(1L)
                                        .groupId(10L)
                                        .semesterId(1L)
                                        .lessonIds(List.of(101L))
                                        .status(ExcuseTicketStatus.SUBMITTED)
                                        .build(),
                                ExcuseTicket.builder()
                                        .id("excuse-beta")
                                        .studentId(2L)
                                        .groupId(10L)
                                        .semesterId(1L)
                                        .lessonIds(List.of(100L, 101L))
                                        .status(ExcuseTicketStatus.APPROVED)
                                        .build()));
    }

    private static LessonResponse lesson(long id, String date, int number, String status) {
        return LessonResponse.newBuilder()
                .setId(id)
                .setGroupId(10L)
                .setSemesterId(1L)
                .setSubjectId(5L)
                .setDate(date)
                .setStartTime("09:00")
                .setEndTime("10:30")
                .setLessonNumber(number)
                .setLessonType("lecture")
                .setStatus(status)
                .build();
    }

    private static StudentInfo student(long id, String name) {
        return StudentInfo.newBuilder().setUserId(id).setDisplayName(name).build();
    }

    private static ru.rutcampustrack.academic.grpc.HeadmanStatsScopeResponse scope(LocalDate to) {
        var semester =
                ru.rutcampustrack.academic.grpc.HeadmanStatsSemester.newBuilder()
                        .setId(1L)
                        .setName("Spring 2026")
                        .setDateFrom(FROM.toString())
                        .setDateTo(to.toString())
                        .setActive(true)
                        .build();
        return ru.rutcampustrack.academic.grpc.HeadmanStatsScopeResponse.newBuilder()
                .setGroupId(10L)
                .setGroupName("UVPV511")
                .setActiveSemesterId(1L)
                .addSemesters(semester)
                .setSelectedSemester(semester)
                .setMembershipActive(3)
                .setMembershipTotal(4)
                .build();
    }

    @Test
    void explicitArchivedSemesterUsesFullDayGridAndPersonalHistoricalCohortWithoutActiveFallback() {
        when(academicGrpcClient.hasAssistantPermission(10L, "VIEW_STATS")).thenReturn(true);
        var archived =
                scope(TO).toBuilder()
                        .clearActiveSemesterId()
                        .setSelectedSemester(
                                scope(TO).getSelectedSemester().toBuilder()
                                        .setArchived(true)
                                        .setActive(false))
                        .build();
        when(academicGrpcClient.resolveHeadmanStatsScope(10L, 1L)).thenReturn(archived);
        when(academicGrpcClient.resolveHeadmanStatsScope(10L, 1L, 1L)).thenReturn(archived);
        when(academicGrpcClient.resolveHeadmanStatsScope(10L, 1L, 999L))
                .thenThrow(
                        new ru.rutcampustrack.attendance.contract.exception
                                .ResourceNotFoundException("Student", "id", 999L));
        when(academicGrpcClient.getGroupMembers(10L))
                .thenReturn(
                        GroupMembersResponse.newBuilder().addStudents(student(2L, "Beta")).build());
        when(academicGrpcClient.getGroupMembers(10L, FROM, 1L))
                .thenReturn(roster(FROM, List.of(student(1L, "Former"))));
        var secondDate = FROM.plusDays(1);
        when(academicGrpcClient.getGroupMembers(10L, secondDate, 1L))
                .thenReturn(roster(secondDate, List.of(student(2L, "Beta"))));
        when(academicGrpcClient.getGroupMembers(10L, TO, 1L))
                .thenReturn(roster(TO, List.of(student(2L, "Beta"))));
        when(scheduleGrpcClient.getLessonsByGroup(10L, 1L, FROM.toString(), TO.toString()))
                .thenReturn(
                        LessonsResponse.newBuilder()
                                .addLessons(lesson(100L, FROM.toString(), 1, "closed"))
                                .addLessons(lesson(101L, secondDate.toString(), 2, "closed"))
                                .addLessons(
                                        lesson(102L, TO.toString(), 3, "closed").toBuilder()
                                                .setStartTime("18:00")
                                                .build())
                                .build());
        when(academicGrpcClient.getSubjectDetailsByIds(List.of(5L)))
                .thenReturn(
                        Map.of(5L, new AcademicGrpcClient.SubjectDetails("Математика", "lecture")));
        when(attendanceReadPort.findByGroupAndDateRange(10L, FROM, TO))
                .thenReturn(
                        List.of(
                                attendance(
                                        100L,
                                        1L,
                                        FROM,
                                        1,
                                        AttendanceStatus.PRESENT,
                                        AttendanceSource.HEADMAN)));
        var request =
                new HeadmanStatsTrendQueryRequest(
                        HeadmanStatsTrendQueryRequest.Mode.SEMESTER,
                        null,
                        null,
                        List.of(),
                        1L,
                        HeadmanStatsTrendQueryRequest.BucketUnit.DAY);
        var group = service.trend(request);
        var personal = service.studentTrend(1L, request);
        assertThat(group.points()).hasSize(30);
        assertThat(group.points().get(1).present().denominator()).isEqualTo(1);
        assertThat(personal.points()).hasSize(30);
        assertThat(personal.points().getFirst().present().numerator()).isEqualTo(1);
        assertThat(personal.points().get(1).present().percent()).isNull();
        assertThat(personal.points().getLast().present().percent()).isNull();
        assertThatThrownBy(() -> service.studentTrend(999L, request))
                .isInstanceOf(
                        ru.rutcampustrack.attendance.contract.exception.ResourceNotFoundException
                                .class);
        when(academicGrpcClient.resolveHeadmanStatsScope(10L, 9L))
                .thenThrow(
                        new ru.rutcampustrack.attendance.contract.exception
                                .ResourceNotFoundException("Semester", "id", 9L));
        assertThatThrownBy(
                        () ->
                                service.trend(
                                        new HeadmanStatsTrendQueryRequest(
                                                HeadmanStatsTrendQueryRequest.Mode.SEMESTER,
                                                null,
                                                null,
                                                List.of(),
                                                9L,
                                                null)))
                .isInstanceOf(
                        ru.rutcampustrack.attendance.contract.exception.ResourceNotFoundException
                                .class);
    }

    @Test
    void overviewKeepsMembershipUnfilteredAndCountsOnlyStartedTodayPairs() {
        LocalDate today = LocalDate.of(2026, 4, 30);
        when(academicGrpcClient.hasAssistantPermission(10L, "VIEW_STATS")).thenReturn(true);
        when(academicGrpcClient.resolveHeadmanStatsScope(10L, null)).thenReturn(scope(TO));
        when(attendanceReadPort.findByGroupAndDateRange(10L, today, today))
                .thenReturn(
                        List.of(
                                attendance(
                                        100L,
                                        1L,
                                        today,
                                        1,
                                        AttendanceStatus.PRESENT,
                                        AttendanceSource.HEADMAN)));
        when(scheduleGrpcClient.getLessonsByGroup(10L, 1L, today.toString(), today.toString()))
                .thenReturn(
                        LessonsResponse.newBuilder()
                                .addLessons(lesson(100L, today.toString(), 1, "active"))
                                .addLessons(
                                        lesson(101L, today.toString(), 2, "closed").toBuilder()
                                                .setStartTime("18:00")
                                                .build())
                                .addLessons(lesson(102L, today.toString(), 3, "cancelled"))
                                .build());
        when(academicGrpcClient.getGroupMembers(10L, today, 1L))
                .thenReturn(roster(today, List.of(student(1L, "Alpha"), student(2L, "Beta"))));
        var overview = service.overview();
        assertThat(overview.membership().active()).isEqualTo(3);
        assertThat(overview.membership().total()).isEqualTo(4);
        assertThat(overview.today().date()).isEqualTo(today);
        assertThat(overview.today().present().numerator()).isEqualTo(1);
        assertThat(overview.today().present().denominator()).isEqualTo(2);
        assertThat(overview.today().present().percent()).isEqualTo(50.0);
    }

    @Test
    void authoritativeHistoricalMemberWithNoLessonsGetsEmptySelectedPeriodCalendar() {
        when(academicGrpcClient.hasAssistantPermission(10L, "VIEW_STATS")).thenReturn(true);
        when(academicGrpcClient.resolveHeadmanStatsScope(10L, 1L, 1L))
                .thenReturn(scope(TO).toBuilder().clearActiveSemesterId().build());
        when(academicGrpcClient.getGroupMembers(10L))
                .thenReturn(GroupMembersResponse.getDefaultInstance());
        when(scheduleGrpcClient.getLessonsByGroup(10L, 1L, FROM.toString(), TO.toString()))
                .thenReturn(LessonsResponse.getDefaultInstance());
        var result =
                service.studentTrend(
                        1L,
                        new HeadmanStatsTrendQueryRequest(
                                HeadmanStatsTrendQueryRequest.Mode.SEMESTER,
                                null,
                                null,
                                List.of(),
                                1L,
                                HeadmanStatsTrendQueryRequest.BucketUnit.DAY));
        assertThat(result.context().semesterId()).isEqualTo(1L);
        assertThat(result.points())
                .hasSize(30)
                .allMatch(point -> point.present().percent() == null);
    }

    @Test
    void clearedHistoryKeepsApprovedTicketAndServerLessonMetadataWhileArchiveBlocksWrites() {
        when(academicGrpcClient.hasAssistantPermission(10L, "VIEW_STATS")).thenReturn(true);
        when(academicGrpcClient.resolveHeadmanStatsScope(10L, 1L, 1L)).thenReturn(scope(TO));
        when(academicGrpcClient.getGroupMembers(10L))
                .thenReturn(
                        GroupMembersResponse.newBuilder()
                                .addStudents(student(1L, "Alpha"))
                                .build());
        when(academicGrpcClient.getGroupMembers(10L, FROM, 1L))
                .thenReturn(roster(FROM, List.of(student(1L, "Alpha"))));
        var lesson = lesson(100L, FROM.toString(), 1, "closed").toBuilder().setRoom("203").build();
        when(scheduleGrpcClient.getLessonsByGroup(10L, 1L, FROM.toString(), TO.toString()))
                .thenReturn(LessonsResponse.newBuilder().addLessons(lesson).build());
        when(scheduleGrpcClient.getLessonById(100L)).thenReturn(lesson);
        when(academicGrpcClient.getSubjectDetailsByIds(List.of(5L)))
                .thenReturn(
                        Map.of(5L, new AcademicGrpcClient.SubjectDetails("Математика", "lecture")));
        when(attendanceReadPort.findByGroupAndDateRange(10L, FROM, TO)).thenReturn(List.of());
        when(academicGrpcClient.getHeadmanJournalSemesterState(10L, 1L))
                .thenReturn(
                        ru.rutcampustrack.academic.grpc.SemesterStateResponse.newBuilder()
                                .setId(1L)
                                .setArchived(true)
                                .setWriteBlocked(true)
                                .build());
        when(excuseRepository.findByLessonIdsInAndStatusIn(
                        List.of(100L), List.of(ExcuseTicketStatus.APPROVED)))
                .thenReturn(
                        List.of(
                                ExcuseTicket.builder()
                                        .id("approved-excuse")
                                        .groupId(10L)
                                        .studentId(1L)
                                        .semesterId(1L)
                                        .lessonIds(List.of(100L))
                                        .status(ExcuseTicketStatus.APPROVED)
                                        .decisionAt(GENERATED_AT)
                                        .build()));
        when(lateCheckinRepository.findByGroupIdAndSemesterIdAndStatusIn(
                        10L, 1L, List.of(LateCheckinRequestStatus.APPROVED)))
                .thenReturn(List.of());
        var result = service.studentLessons(1L, 1L, 0, 50, null, List.of());
        assertThat(result.totalElements()).isEqualTo(1);
        var item = result.items().getFirst();
        assertThat(item.status()).isNull();
        assertThat(item.markRevision()).isNull();
        assertThat(item.approvedTicket().id()).isEqualTo("approved-excuse");
        assertThat(item.startsAt()).isEqualTo(Instant.parse("2026-04-01T06:00:00Z"));
        assertThat(item.endsAt()).isEqualTo(Instant.parse("2026-04-01T07:30:00Z"));
        assertThat(item.room()).isEqualTo("203");
        assertThat(item.editable()).isFalse();
        assertThat(item.blockedReason()).isEqualTo("Семестр архивирован");
    }

    private static GroupMembersResponse roster(LocalDate date, List<StudentInfo> students) {
        return GroupMembersResponse.newBuilder()
                .setAsOfDate(date.toString())
                .setSemesterId(1L)
                .addAllStudents(students)
                .build();
    }

    private static AttendanceRecord attendance(
            long lessonId,
            long userId,
            LocalDate date,
            int number,
            AttendanceStatus status,
            AttendanceSource source) {
        return new AttendanceRecord(lessonId, userId, 10L, 5L, date, number, status, source, null);
    }
}
