package ru.rutcampustrack.attendance.excuse;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import ru.rutcampustrack.attendance.contract.enums.ExcuseTicketStatus;
import ru.rutcampustrack.attendance.contract.enums.ExcuseType;
import ru.rutcampustrack.attendance.excuse.entity.ExcuseTicket;
import ru.rutcampustrack.attendance.integration.AbstractAttendanceIntegrationTest;

/** Verifies D-11 duplicate-check query and basic persistence for ExcuseTicket. */
class ExcuseRepositoryIT extends AbstractAttendanceIntegrationTest {

    @Autowired private ExcuseRepository excuseRepository;

    @AfterEach
    void cleanup() {
        excuseRepository.deleteAll();
    }

    @Test
    void submittedReasonCountsIncludeDecisionsAndCancellationButNotDraftsOrForeignSemesters() {
        for (var status :
                List.of(
                        ExcuseTicketStatus.SUBMITTED,
                        ExcuseTicketStatus.APPROVED,
                        ExcuseTicketStatus.REJECTED,
                        ExcuseTicketStatus.CANCELLED,
                        ExcuseTicketStatus.DRAFT)) {
            excuseRepository.save(
                    ExcuseTicket.builder()
                            .studentId(100L)
                            .groupId(10L)
                            .semesterId(20L)
                            .lessonIds(List.of(900L, 901L))
                            .excuseType(
                                    status == ExcuseTicketStatus.CANCELLED
                                            ? ExcuseType.OTHER
                                            : ExcuseType.ILLNESS)
                            .status(status)
                            .createdAt(Instant.parse("2026-04-01T00:00:00Z"))
                            .build());
        }
        excuseRepository.save(
                ExcuseTicket.builder()
                        .groupId(11L)
                        .studentId(101L)
                        .lessonIds(List.of(1900L, 1901L))
                        .createdAt(Instant.parse("2026-04-01T00:00:00Z"))
                        .semesterId(20L)
                        .excuseType(ExcuseType.ILLNESS)
                        .status(ExcuseTicketStatus.SUBMITTED)
                        .build());
        excuseRepository.save(
                ExcuseTicket.builder()
                        .groupId(10L)
                        .semesterId(21L)
                        .excuseType(ExcuseType.ILLNESS)
                        .status(ExcuseTicketStatus.SUBMITTED)
                        .build());
        var counts = excuseRepository.submittedReasonCounts(10L, 20L);
        org.assertj.core.api.Assertions.assertThat(counts).hasSize(2);
        var byCode =
                counts.stream()
                        .collect(
                                java.util.stream.Collectors.toMap(
                                        ExcuseRepository.SubmittedReasonCount::getCode,
                                        ExcuseRepository.SubmittedReasonCount::getCount));
        org.assertj.core.api.Assertions.assertThat(byCode)
                .containsExactlyInAnyOrderEntriesOf(java.util.Map.of("ILLNESS", 3L, "OTHER", 1L));
    }

    @Test
    void existsByStudentIdAndLessonIdsInAndStatusIn_returnsTrue_whenActiveTicketOverlaps() {
        ExcuseTicket ticket =
                ExcuseTicket.builder()
                        .studentId(100L)
                        .groupId(10L)
                        .studentName("Ivanov I.I.")
                        .lessonIds(List.of(1L, 2L, 3L))
                        .excuseType(ExcuseType.ILLNESS)
                        .status(ExcuseTicketStatus.SUBMITTED)
                        .createdAt(Instant.now())
                        .updatedAt(Instant.now())
                        .build();
        excuseRepository.save(ticket);

        boolean conflict =
                excuseRepository.existsByStudentIdAndLessonIdsInAndStatusIn(
                        100L,
                        List.of(2L, 99L),
                        List.of(ExcuseTicketStatus.SUBMITTED, ExcuseTicketStatus.APPROVED));

        assertTrue(conflict);
    }

    @Test
    void existsByStudentIdAndLessonIdsInAndStatusIn_returnsFalse_whenRejectedTicketOnly() {
        ExcuseTicket ticket =
                ExcuseTicket.builder()
                        .studentId(100L)
                        .groupId(10L)
                        .studentName("Ivanov I.I.")
                        .lessonIds(List.of(1L, 2L))
                        .excuseType(ExcuseType.ILLNESS)
                        .status(ExcuseTicketStatus.REJECTED)
                        .createdAt(Instant.now())
                        .updatedAt(Instant.now())
                        .build();
        excuseRepository.save(ticket);

        boolean conflict =
                excuseRepository.existsByStudentIdAndLessonIdsInAndStatusIn(
                        100L,
                        List.of(1L),
                        List.of(ExcuseTicketStatus.SUBMITTED, ExcuseTicketStatus.APPROVED));

        assertFalse(conflict);
    }

    @Test
    void existsByStudentIdAndLessonIdsInAndStatusIn_returnsFalse_whenOtherStudent() {
        ExcuseTicket ticket =
                ExcuseTicket.builder()
                        .studentId(100L)
                        .groupId(10L)
                        .studentName("Ivanov I.I.")
                        .lessonIds(List.of(1L))
                        .excuseType(ExcuseType.ILLNESS)
                        .status(ExcuseTicketStatus.SUBMITTED)
                        .createdAt(Instant.now())
                        .updatedAt(Instant.now())
                        .build();
        excuseRepository.save(ticket);

        boolean conflict =
                excuseRepository.existsByStudentIdAndLessonIdsInAndStatusIn(
                        200L,
                        List.of(1L),
                        List.of(ExcuseTicketStatus.SUBMITTED, ExcuseTicketStatus.APPROVED));

        assertFalse(conflict);
    }
}
