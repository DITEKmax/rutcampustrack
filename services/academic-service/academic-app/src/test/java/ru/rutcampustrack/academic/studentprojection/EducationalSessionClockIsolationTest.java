package ru.rutcampustrack.academic.studentprojection;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.support.GenericApplicationContext;

import ru.rutcampustrack.shared.security.InternalJwtClaims;
import ru.rutcampustrack.shared.web.time.EducationalTime;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Actual mixed resolver and Spring constructor selection, with controlled authority port data. */
class EducationalSessionClockIsolationTest {
    private static final ZoneId MOSCOW = ZoneId.of("Europe/Moscow");
    private static final UUID SESSION = UUID.fromString("22222222-2222-4222-8222-222222222222");
    private static final InternalJwtClaims CLAIMS =
            new InternalJwtClaims(100L, SESSION, 1L, 7L, "STUDENT", "ACTIVE", 10L, false, false);

    @ParameterizedTest
    @ValueSource(strings = {"2025-09-15T06:00:00Z", "2030-09-15T06:00:00Z"})
    void liveSessionUsesRealExpiryWhileReturnedEducationalDateMoves(String teachingInstant) {
        Clock realClock = at("2026-09-15T06:00:00Z");
        Clock teachingClock = at(teachingInstant);
        try (var context = context(realClock, teachingClock)) {
            assertThat(context.getBean(Clock.class)).isSameAs(realClock);
            var scope = context.getBean(StudentProjectionScopeService.class).resolve(42L, CLAIMS);
            assertThat(scope.serverDate()).isEqualTo(LocalDate.now(teachingClock));
            assertThat(scope.studentId()).isEqualTo(100L);
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"2025-09-15T06:00:00Z", "2030-09-15T06:00:00Z"})
    void expiredRealSessionIsDeniedRegardlessOfTeachingDate(String teachingInstant) {
        try (var context = context(at("2026-09-17T06:00:00Z"), at(teachingInstant))) {
            assertThatThrownBy(
                            () ->
                                    context.getBean(StudentProjectionScopeService.class)
                                            .resolve(42L, CLAIMS))
                    .isInstanceOfSatisfying(
                            StudentProjectionException.class,
                            error ->
                                    assertThat(error.code())
                                            .isEqualTo(
                                                    StudentProjectionException.Code
                                                            .INVALID_SESSION));
        }
    }

    private static GenericApplicationContext context(Clock realClock, Clock teachingClock) {
        StudentProjectionQuery query = mock(StudentProjectionQuery.class);
        when(query.findAuthority(100L))
                .thenReturn(
                        Optional.of(
                                new StudentProjectionQuery.AuthoritySnapshot(
                                        100L,
                                        7L,
                                        "ACTIVE",
                                        List.of(
                                                new StudentProjectionQuery.RoleGrantSnapshot(
                                                        11L, "STUDENT", "ACTIVE", 10L)))));
        when(query.findSession(CLAIMS))
                .thenReturn(
                        Optional.of(
                                new StudentProjectionQuery.SessionSnapshot(
                                        SESSION,
                                        100L,
                                        11L,
                                        1L,
                                        Instant.parse("2026-09-16T06:00:00Z"),
                                        null)));
        when(query.findSemester(42L))
                .thenReturn(
                        Optional.of(
                                new StudentProjectionQuery.SemesterSnapshot(
                                        42L, LocalDate.of(2026, 9, 1), LocalDate.of(2026, 9, 30))));
        when(query.findGroupHistory(100L))
                .thenReturn(
                        List.of(
                                new StudentProjectionQuery.GroupHistorySnapshot(
                                        1L, 100L, 10L, LocalDate.of(2024, 1, 1), null)));
        when(query.findAssignments(anyLong(), anyCollection(), any(), any())).thenReturn(List.of());
        when(query.findActiveStudentIds(anyLong(), anyLong(), any(), any()))
                .thenReturn(List.of(100L));
        when(query.readSnapshot(any(), any()))
                .thenAnswer(call -> ((java.util.function.Supplier<?>) call.getArgument(1)).get());
        GenericApplicationContext context = new AnnotationConfigApplicationContext();
        context.registerBean(StudentProjectionQuery.class, () -> query);
        context.registerBean(Clock.class, () -> realClock);
        context.registerBean(
                "defaultEducationalTime",
                EducationalTime.class,
                () -> new EducationalTime(realClock));
        context.registerBean(
                "fixtureEducationalTime",
                EducationalTime.class,
                () -> new EducationalTime(teachingClock),
                definition -> definition.setPrimary(true));
        context.registerBean(StudentProjectionScopeService.class);
        context.refresh();
        return context;
    }

    private static Clock at(String instant) {
        return Clock.fixed(Instant.parse(instant), MOSCOW);
    }
}
