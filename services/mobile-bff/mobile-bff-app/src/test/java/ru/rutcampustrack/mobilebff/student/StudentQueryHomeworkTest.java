package ru.rutcampustrack.mobilebff.student;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import ru.rutcampustrack.academic.grpc.HomeworkInfo;
import ru.rutcampustrack.academic.grpc.HomeworksForWeekResponse;
import ru.rutcampustrack.academic.grpc.SemesterResponse;
import ru.rutcampustrack.academic.grpc.SetHomeworkCompletionResponse;
import ru.rutcampustrack.attendance.grpc.StudentAttendanceRankingResponse;
import ru.rutcampustrack.attendance.grpc.StudentAttendanceRankingRow;
import ru.rutcampustrack.mobilebff.contract.model.StudentApiModels.HomeworkCompletionRequest;
import ru.rutcampustrack.mobilebff.contract.model.StudentApiModels.HomeworkItem;
import ru.rutcampustrack.mobilebff.contract.model.StudentApiModels.ProblemCode;
import ru.rutcampustrack.mobilebff.contract.model.StudentApiModels.StudentStatisticsRankingRow;
import ru.rutcampustrack.mobilebff.error.MobileBffException;
import ru.rutcampustrack.mobilebff.grpc.MobileAcademicClient;
import ru.rutcampustrack.mobilebff.grpc.MobileAttendanceClient;
import ru.rutcampustrack.mobilebff.grpc.MobileScheduleClient;
import ru.rutcampustrack.mobilebff.security.MobileRequestContext;
import ru.rutcampustrack.shared.security.InternalJwtClaims;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class StudentQueryHomeworkTest {
    private static final UUID SESSION_ID = UUID.fromString("11111111-1111-4111-8111-111111111111");

    @Mock private MobileRequestContext requestContext;
    @Mock private MobileAcademicClient academic;
    @Mock private MobileScheduleClient schedule;
    @Mock private MobileAttendanceClient attendance;

    private StudentQueryService service;

    @BeforeEach
    void setUp() {
        service = new StudentQueryService(
                requestContext, academic, schedule, attendance,
                Clock.fixed(Instant.parse("2026-09-07T10:00:00Z"), ZoneOffset.UTC));
        when(requestContext.claims()).thenReturn(
                new InternalJwtClaims(100L, SESSION_ID, 1L, 1L,
                        "STUDENT", "ACTIVE", 10L, false, false));
        lenient().when(academic.activeSemesterForHomework()).thenReturn(SemesterResponse.newBuilder()
                .setId(5L).setName("Осень 2026")
                .setDateFrom("2026-09-01").setDateTo("2026-09-30").build());
    }

    @Test
    void defaultRangeUsesMoscowTodayAndDeterministicUncompletedFirstOrder() {
        when(academic.homeworks(anyLong(), anyLong(), anyLong(), anyString(), anyString(),
                anyBoolean(), anyString(), anyString()))
                .thenReturn(HomeworksForWeekResponse.newBuilder()
                        .addAllHomeworks(List.of(
                                homework(3L, "2026-09-02", 1, true, "https://example.test/3"),
                                homework(2L, "2026-09-01", 2, true, ""),
                                homework(1L, "2026-09-01", 5, false, ""),
                                homework(4L, "2026-09-01", 2, false, "https://example.test/4")))
                        .build());

        var result = service.homework(null, null);

        assertThat(result.from()).isEqualTo(LocalDate.of(2026, 9, 7));
        assertThat(result.to()).isEqualTo(LocalDate.of(2026, 9, 30));
        assertThat(result.serverNow()).isEqualTo(Instant.parse("2026-09-07T10:00:00Z"));
        assertThat(result.items()).extracting(HomeworkItem::id)
                .containsExactly("4", "1", "2", "3");
        assertThat(result.items().get(0).link()).isEqualTo("https://example.test/4");
        assertThat(result.items().get(2).link()).isNull();
        assertThat(result.items().get(2).completedAt()).isNotNull();
        verify(academic).homeworks(10L, 5L, 100L, "2026-09-07", "2026-09-30",
                true, "2026-09-06T21:00:00Z", "2026-09-07T21:00:00Z");
    }

    @Test
    void statisticsRankingMapsOnlyThePagedAttendanceRankingFields() {
        when(academic.activeSemester()).thenReturn(SemesterResponse.newBuilder()
                .setId(5L).setName("Осень 2026")
                .setDateFrom("2026-09-01").setDateTo("2026-09-30").build());
        when(attendance.ranking(5L, null, 20)).thenReturn(StudentAttendanceRankingResponse.newBuilder()
                .setAvailable(true)
                .setPage(2)
                .setSize(20)
                .setTotal(45)
                .setOwnPosition(37)
                .addRows(StudentAttendanceRankingRow.newBuilder()
                        .setStudentId(100L)
                        .setDisplayName("Я")
                        .setPosition(37)
                        .setPercentage(83.33)
                        .setIsSelf(true))
                .build());

        var result = service.statisticsRanking(5L, null, 20);

        assertThat(result.available()).isTrue();
        assertThat(result.page()).isEqualTo(2);
        assertThat(result.size()).isEqualTo(20);
        assertThat(result.total()).isEqualTo(45);
        assertThat(result.ownPosition()).isEqualTo(37);
        assertThat(result.rows()).containsExactly(
                new StudentStatisticsRankingRow("100", "Я", 37, 83.33, true));
        verify(attendance).ranking(5L, null, 20);
        verifyNoInteractions(schedule);
    }

    @Test
    void defaultFromClampsBeforeAndAfterActiveSemester() {
        when(academic.homeworks(anyLong(), anyLong(), anyLong(), anyString(), anyString()))
                .thenReturn(HomeworksForWeekResponse.getDefaultInstance());

        var before = new StudentQueryService(
                requestContext, academic, schedule, attendance,
                Clock.fixed(Instant.parse("2026-08-01T10:00:00Z"), ZoneOffset.UTC))
                .homework(null, null);
        var after = new StudentQueryService(
                requestContext, academic, schedule, attendance,
                Clock.fixed(Instant.parse("2026-10-15T10:00:00Z"), ZoneOffset.UTC))
                .homework(null, null);

        assertThat(before.from()).isEqualTo(LocalDate.of(2026, 9, 1));
        assertThat(after.from()).isEqualTo(LocalDate.of(2026, 9, 30));
    }

    @Test
    void historicalRangeKeepsRangeOnlySemantics() {
        when(academic.homeworks(anyLong(), anyLong(), anyLong(), anyString(), anyString()))
                .thenReturn(HomeworksForWeekResponse.getDefaultInstance());

        service.homework("2026-09-01", "2026-09-05");

        verify(academic).homeworks(10L, 5L, 100L, "2026-09-01", "2026-09-05");
    }

    @Test
    void completedWithoutServerTimestampFailsClosed() {
        when(academic.homeworks(anyLong(), anyLong(), anyLong(), anyString(), anyString(),
                anyBoolean(), anyString(), anyString()))
                .thenReturn(HomeworksForWeekResponse.newBuilder()
                        .addHomeworks(HomeworkInfo.newBuilder()
                                .setHomeworkId(8L).setSubjectId(11L).setSubjectName("Математика")
                                .setTitle("Задание 8").setDescription("Описание")
                                .setLessonDate("2026-09-02").setLessonNumber(1).setCompleted(true)
                                .build())
                        .build());

        assertThatThrownBy(() -> service.homework(null, null))
                .isInstanceOf(MobileBffException.class)
                .satisfies(error -> assertThat(((MobileBffException) error).status().value()).isEqualTo(503));
    }

    @Test
    void completionMutationCarriesStoredTimestampAndRejectsMissingTimestamp() {
        when(academic.setHomeworkCompletion(77L, 5L, true))
                .thenReturn(SetHomeworkCompletionResponse.newBuilder()
                        .setHomeworkId(77L).setCompleted(true)
                        .setCompletedAt("2026-09-07T09:30:00Z").build());

        var result = service.setHomeworkCompletion("77", new HomeworkCompletionRequest(true));

        assertThat(result.completedAt()).isEqualTo(Instant.parse("2026-09-07T09:30:00Z"));

        when(academic.setHomeworkCompletion(78L, 5L, true))
                .thenReturn(SetHomeworkCompletionResponse.newBuilder()
                        .setHomeworkId(78L).setCompleted(true).build());
        assertThatThrownBy(() -> service.setHomeworkCompletion("78", new HomeworkCompletionRequest(true)))
                .isInstanceOf(MobileBffException.class)
                .satisfies(error -> assertThat(((MobileBffException) error).status().value()).isEqualTo(503));
    }

    @Test
    void explicitOutOfSemesterRangeIsBadRequest() {
        assertThatThrownBy(() -> service.homework("2026-08-31", "2026-09-10"))
                .isInstanceOf(MobileBffException.class)
                .satisfies(error -> assertThat(((MobileBffException) error).status().value()).isEqualTo(400));
    }

    @Test
    void readOnlyCompletionIsRejectedBeforeParsingOrAcademicDependency() {
        when(requestContext.claims()).thenReturn(new InternalJwtClaims(
                100L, SESSION_ID, 1L, 1L, "STUDENT", "EXPELLED", 10L, false, true));

        assertThatThrownBy(() -> service.setHomeworkCompletion(
                "not-a-number", new HomeworkCompletionRequest(null)))
                .isInstanceOfSatisfying(MobileBffException.class, error -> {
                    assertThat(error.status().value()).isEqualTo(403);
                    assertThat(error.code()).isEqualTo(ProblemCode.ROLE_READ_ONLY);
                });
        verifyNoInteractions(academic);
    }

    @Test
    void readOnlyHomeworkReadRemainsAvailable() {
        when(requestContext.claims()).thenReturn(new InternalJwtClaims(
                100L, SESSION_ID, 1L, 1L, "STUDENT", "EXPELLED", 10L, false, true));
        when(academic.homeworks(anyLong(), anyLong(), anyLong(), anyString(), anyString()))
                .thenReturn(HomeworksForWeekResponse.getDefaultInstance());

        var result = service.homework("2026-09-02", "2026-09-03");

        assertThat(result.items()).isEmpty();
        verify(academic).homeworks(10L, 5L, 100L, "2026-09-02", "2026-09-03");
    }

    private static HomeworkInfo homework(long id, String date, int lessonNumber,
                                         boolean completed, String link) {
        HomeworkInfo.Builder builder = HomeworkInfo.newBuilder()
                .setHomeworkId(id).setSubjectId(11L).setSubjectName("Математика")
                .setTitle("Задание " + id).setDescription("Описание")
                .setLink(link).setLessonDate(date).setLessonNumber(lessonNumber)
                .setCompleted(completed);
        if (completed) {
            builder.setCompletedAt("2026-09-07T09:00:00Z");
        }
        return builder.build();
    }
}
