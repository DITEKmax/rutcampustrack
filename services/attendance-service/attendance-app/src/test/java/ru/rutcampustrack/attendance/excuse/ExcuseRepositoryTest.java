package ru.rutcampustrack.attendance.excuse;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import ru.rutcampustrack.attendance.contract.enums.ExcuseTicketStatus;
import ru.rutcampustrack.attendance.contract.enums.ExcuseType;
import ru.rutcampustrack.attendance.excuse.entity.ExcuseTicket;
import ru.rutcampustrack.attendance.integration.AbstractAttendanceIntegrationTest;

import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Verifies D-11 duplicate-check query and basic persistence for ExcuseTicket.
 */
class ExcuseRepositoryTest extends AbstractAttendanceIntegrationTest {

    @Autowired
    private ExcuseRepository excuseRepository;

    @AfterEach
    void cleanup() {
        excuseRepository.deleteAll();
    }

    @Test
    void existsByStudentIdAndLessonIdsInAndStatusIn_returnsTrue_whenActiveTicketOverlaps() {
        ExcuseTicket ticket = ExcuseTicket.builder()
                .studentId(100L).groupId(10L).studentName("Ivanov I.I.")
                .lessonIds(List.of(1L, 2L, 3L))
                .excuseType(ExcuseType.ILLNESS)
                .status(ExcuseTicketStatus.SUBMITTED)
                .createdAt(Instant.now()).updatedAt(Instant.now())
                .build();
        excuseRepository.save(ticket);

        boolean conflict = excuseRepository.existsByStudentIdAndLessonIdsInAndStatusIn(
                100L, List.of(2L, 99L),
                List.of(ExcuseTicketStatus.SUBMITTED, ExcuseTicketStatus.APPROVED));

        assertTrue(conflict);
    }

    @Test
    void existsByStudentIdAndLessonIdsInAndStatusIn_returnsFalse_whenRejectedTicketOnly() {
        ExcuseTicket ticket = ExcuseTicket.builder()
                .studentId(100L).groupId(10L).studentName("Ivanov I.I.")
                .lessonIds(List.of(1L, 2L))
                .excuseType(ExcuseType.ILLNESS)
                .status(ExcuseTicketStatus.REJECTED)
                .createdAt(Instant.now()).updatedAt(Instant.now())
                .build();
        excuseRepository.save(ticket);

        boolean conflict = excuseRepository.existsByStudentIdAndLessonIdsInAndStatusIn(
                100L, List.of(1L),
                List.of(ExcuseTicketStatus.SUBMITTED, ExcuseTicketStatus.APPROVED));

        assertFalse(conflict);
    }

    @Test
    void existsByStudentIdAndLessonIdsInAndStatusIn_returnsFalse_whenOtherStudent() {
        ExcuseTicket ticket = ExcuseTicket.builder()
                .studentId(100L).groupId(10L).studentName("Ivanov I.I.")
                .lessonIds(List.of(1L))
                .excuseType(ExcuseType.ILLNESS)
                .status(ExcuseTicketStatus.SUBMITTED)
                .createdAt(Instant.now()).updatedAt(Instant.now())
                .build();
        excuseRepository.save(ticket);

        boolean conflict = excuseRepository.existsByStudentIdAndLessonIdsInAndStatusIn(
                200L, List.of(1L),
                List.of(ExcuseTicketStatus.SUBMITTED, ExcuseTicketStatus.APPROVED));

        assertFalse(conflict);
    }
}
