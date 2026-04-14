package ru.rutcampustrack.attendance.excuse;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import ru.rutcampustrack.attendance.checkin.AttendanceDocument;
import ru.rutcampustrack.attendance.contract.dto.excuse.UpdateExcuseStatusRequest;
import ru.rutcampustrack.attendance.contract.enums.AttendanceSource;
import ru.rutcampustrack.attendance.contract.enums.AttendanceStatus;
import ru.rutcampustrack.attendance.contract.enums.ExcuseTicketStatus;
import ru.rutcampustrack.attendance.contract.enums.ExcuseType;
import ru.rutcampustrack.attendance.contract.enums.UserRole;
import ru.rutcampustrack.attendance.excuse.entity.ExcuseTicket;
import ru.rutcampustrack.attendance.integration.AbstractAttendanceIntegrationTest;
import ru.rutcampustrack.attendance.security.RequestContext;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration test for the D-16 cascade (AC-5).
 *
 * Verifies that approving an excuse ticket:
 *   - creates/updates an AttendanceDocument for every lessonId;
 *   - maps ILLNESS/SUMMONS/etc. → EXCUSED and FREE_ATTENDANCE → FREE_ATTENDANCE;
 *   - overwrites a pre-existing document's status;
 *   - leaves attendance untouched on REJECTED.
 *
 * Uses real MongoDB via Testcontainers (requires Docker Desktop).
 */
class ExcuseServiceApproveIT extends AbstractAttendanceIntegrationTest {

    private static final Long STUDENT_ID = 100L;
    private static final Long HEADMAN_ID = 777L;
    private static final Long GROUP_ID = 10L;

    @Autowired
    private ExcuseService excuseService;

    @Autowired
    private ExcuseRepository excuseRepository;

    @MockitoBean
    private RequestContext requestContext;

    @BeforeEach
    void setUp() {
        // Wipe both collections between tests
        mongoTemplate.remove(new Query(), AttendanceDocument.class);
        mongoTemplate.remove(new Query(), ExcuseTicket.class);

        // Default caller is the headman of STUDENT_ID's group
        Mockito.when(requestContext.getUserId()).thenReturn(HEADMAN_ID);
        Mockito.when(requestContext.getGroupId()).thenReturn(GROUP_ID);
        Mockito.when(requestContext.isHeadman()).thenReturn(true);
        Mockito.when(requestContext.getRole()).thenReturn(UserRole.STUDENT);
    }

    private ExcuseTicket persistSubmittedTicket(ExcuseType type, List<Long> lessonIds) {
        Instant now = Instant.now();
        ExcuseTicket ticket = ExcuseTicket.builder()
                .studentId(STUDENT_ID)
                .groupId(GROUP_ID)
                .studentName("Иванов Иван")
                .lessonIds(lessonIds)
                .excuseType(type)
                .status(ExcuseTicketStatus.SUBMITTED)
                .createdAt(now)
                .updatedAt(now)
                .build();
        return excuseRepository.save(ticket);
    }

    // -----------------------------------------------------------------------
    // Test 1 (AC-5): ILLNESS approve → 3 AttendanceDocuments EXCUSED
    // -----------------------------------------------------------------------
    @Test
    void approve_illnessTicket_createsExcusedAttendanceForEachLesson() {
        ExcuseTicket ticket = persistSubmittedTicket(ExcuseType.ILLNESS, List.of(11L, 12L, 13L));

        ExcuseTicket result = excuseService.updateStatus(
                ticket.getId(),
                new UpdateExcuseStatusRequest(ExcuseTicketStatus.APPROVED, "ok"));

        assertThat(result.getStatus()).isEqualTo(ExcuseTicketStatus.APPROVED);

        List<AttendanceDocument> docs = attendanceRepository.findAll();
        assertThat(docs).hasSize(3);
        assertThat(docs).allSatisfy(d -> {
            assertThat(d.getUserId()).isEqualTo(STUDENT_ID);
            assertThat(d.getGroupId()).isEqualTo(GROUP_ID);
            assertThat(d.getStatus()).isEqualTo(AttendanceStatus.EXCUSED);
            assertThat(d.getSource()).isEqualTo(AttendanceSource.HEADMAN_EXCUSE);
            assertThat(d.getCreatedAt()).isNotNull();
            assertThat(d.getUpdatedAt()).isNotNull();
        });
        assertThat(docs.stream().map(AttendanceDocument::getLessonId).toList())
                .containsExactlyInAnyOrder(11L, 12L, 13L);
    }

    // -----------------------------------------------------------------------
    // Test 2: FREE_ATTENDANCE → FREE_ATTENDANCE status
    // -----------------------------------------------------------------------
    @Test
    void approve_freeAttendanceTicket_createsFreeAttendanceDocuments() {
        ExcuseTicket ticket = persistSubmittedTicket(ExcuseType.FREE_ATTENDANCE, List.of(21L, 22L));

        excuseService.updateStatus(ticket.getId(),
                new UpdateExcuseStatusRequest(ExcuseTicketStatus.APPROVED, "грант"));

        List<AttendanceDocument> docs = attendanceRepository.findAll();
        assertThat(docs).hasSize(2);
        assertThat(docs).allSatisfy(d -> {
            assertThat(d.getStatus()).isEqualTo(AttendanceStatus.FREE_ATTENDANCE);
            assertThat(d.getSource()).isEqualTo(AttendanceSource.HEADMAN_EXCUSE);
        });
    }

    // -----------------------------------------------------------------------
    // Test 3: REJECTED — attendance collection stays empty
    // -----------------------------------------------------------------------
    @Test
    void reject_doesNotTouchAttendance() {
        ExcuseTicket ticket = persistSubmittedTicket(ExcuseType.OTHER, List.of(31L, 32L));

        ExcuseTicket result = excuseService.updateStatus(ticket.getId(),
                new UpdateExcuseStatusRequest(ExcuseTicketStatus.REJECTED, "недостаточно"));

        assertThat(result.getStatus()).isEqualTo(ExcuseTicketStatus.REJECTED);
        assertThat(attendanceRepository.findAll()).isEmpty();
    }

    // -----------------------------------------------------------------------
    // Test 4: pre-existing AttendanceDocument (PRESENT) is overwritten to EXCUSED
    // -----------------------------------------------------------------------
    @Test
    void approve_overwritesExistingPresentAttendance() {
        Instant past = Instant.now().minusSeconds(3600);
        AttendanceDocument preExisting = AttendanceDocument.builder()
                .lessonId(41L)
                .userId(STUDENT_ID)
                .groupId(GROUP_ID)
                .status(AttendanceStatus.PRESENT)
                .source(AttendanceSource.STUDENT_GEO)
                .createdAt(past)
                .updatedAt(past)
                .build();
        attendanceRepository.save(preExisting);

        ExcuseTicket ticket = persistSubmittedTicket(ExcuseType.SUMMONS, List.of(41L));

        excuseService.updateStatus(ticket.getId(),
                new UpdateExcuseStatusRequest(ExcuseTicketStatus.APPROVED, "повестка"));

        List<AttendanceDocument> docs = attendanceRepository.findAll();
        assertThat(docs).hasSize(1);
        AttendanceDocument updated = docs.get(0);
        assertThat(updated.getLessonId()).isEqualTo(41L);
        assertThat(updated.getStatus()).isEqualTo(AttendanceStatus.EXCUSED);
        assertThat(updated.getSource()).isEqualTo(AttendanceSource.HEADMAN_EXCUSE);
        // createdAt preserved, updatedAt advanced
        assertThat(updated.getCreatedAt()).isEqualTo(past);
        assertThat(updated.getUpdatedAt()).isAfter(past);
    }
}
