package ru.rutcampustrack.mobilebff.student;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import ru.rutcampustrack.academic.grpc.HomeworkInfo;
import ru.rutcampustrack.academic.grpc.HomeworksForWeekResponse;
import ru.rutcampustrack.academic.grpc.SemesterResponse;
import ru.rutcampustrack.mobilebff.contract.model.StudentApiModels.HomeworkItem;
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class StudentQueryHomeworkTest {
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
                new InternalJwtClaims(100L, "STUDENT", 10L, false));
        when(academic.activeSemester()).thenReturn(SemesterResponse.newBuilder()
                .setId(5L).setName("Осень 2026")
                .setDateFrom("2026-09-01").setDateTo("2026-09-30").build());
    }

    @Test
    void defaultRangeUsesMoscowTodayAndDeterministicUncompletedFirstOrder() {
        when(academic.homeworks(anyLong(), anyLong(), anyLong(), anyString(), anyString()))
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
        assertThat(result.items()).extracting(HomeworkItem::id)
                .containsExactly("4", "1", "2", "3");
        assertThat(result.items().get(0).link()).isEqualTo("https://example.test/4");
        assertThat(result.items().get(2).link()).isNull();
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
    void explicitOutOfSemesterRangeIsBadRequest() {
        assertThatThrownBy(() -> service.homework("2026-08-31", "2026-09-10"))
                .isInstanceOf(MobileBffException.class)
                .satisfies(error -> assertThat(((MobileBffException) error).status().value()).isEqualTo(400));
    }

    private static HomeworkInfo homework(long id, String date, int lessonNumber,
                                         boolean completed, String link) {
        return HomeworkInfo.newBuilder()
                .setHomeworkId(id).setSubjectId(11L).setSubjectName("Математика")
                .setTitle("Задание " + id).setDescription("Описание")
                .setLink(link).setLessonDate(date).setLessonNumber(lessonNumber)
                .setCompleted(completed).build();
    }
}
