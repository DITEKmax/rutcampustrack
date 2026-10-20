package ru.rutcampustrack.attendance.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import ru.rutcampustrack.attendance.contract.dto.excuse.CreateExcuseRequest;
import ru.rutcampustrack.attendance.contract.enums.ExcuseType;
import ru.rutcampustrack.attendance.contract.enums.UserRole;
import ru.rutcampustrack.attendance.excuse.ExcuseService;
import ru.rutcampustrack.attendance.security.RequestContext;
import ru.rutcampustrack.schedule.grpc.LessonInfo;
import ru.rutcampustrack.schedule.grpc.LessonResponse;
import ru.rutcampustrack.shared.outbox.OutboxStorage;

import java.util.List;

/**
 * Production service transaction proof. Legacy HTTP creation deliberately remains retired (410).
 */
class OutboxAtomicityIT extends AbstractAttendanceIntegrationTest {
    @org.springframework.test.context.DynamicPropertySource
    static void auditIsolation(org.springframework.test.context.DynamicPropertyRegistry registry) {
        AuditTestIsolation.register(registry, "outboxatomicity", 4);
    }

    @Autowired private ExcuseService service;
    @Autowired private RequestContext identity;
    @MockitoSpyBean private OutboxStorage storage;

    @BeforeEach
    void fixtures() {
        for (String collection :
                List.of(
                        "excuse_tickets",
                        "attendance_outbox",
                        "lesson_actions",
                        "student_checkin_pairs",
                        "semester_archive_fences"))
            mongoTemplate.getCollection(collection).deleteMany(new org.bson.Document());
        when(scheduleGrpcClient.getLessonsByIds(List.of(10L, 11L)))
                .thenReturn(List.of(lesson(10L), lesson(11L)));
        for (long id : List.of(10L, 11L))
            when(scheduleGrpcClient.requireAttendanceMutationReady(id, 1L))
                    .thenReturn(
                            LessonResponse.newBuilder()
                                    .setId(id)
                                    .setGroupId(1L)
                                    .setSubjectId(7L)
                                    .setSemesterId(1L)
                                    .setDate("2026-04-01")
                                    .setLessonNumber(1)
                                    .setStatus("closed")
                                    .build());
        when(academicGrpcClient.getUserDisplayName(42L)).thenReturn("Synthetic Student");
        when(academicGrpcClient.getSubjectsByIds(List.of(7L)))
                .thenReturn(java.util.Map.of(7L, "Math"));
    }

    private LessonInfo lesson(long id) {
        return LessonInfo.newBuilder()
                .setLessonId(id)
                .setGroupId(1L)
                .setSubjectId(7L)
                .setSemesterId(1L)
                .setDate("2026-04-01")
                .setLessonNumber(1)
                .setStatus("closed")
                .build();
    }

    private ru.rutcampustrack.attendance.excuse.entity.ExcuseTicket create() {
        var attributes = new ServletRequestAttributes(new MockHttpServletRequest());
        RequestContextHolder.setRequestAttributes(attributes);
        try {
            identity.setUserId(42L);
            identity.setGroupId(1L);
            identity.setRole(UserRole.STUDENT);
            identity.setHeadman(false);
            return service.createExcuse(
                    new CreateExcuseRequest(List.of(10L, 11L), ExcuseType.ILLNESS, "synthetic"));
        } finally {
            attributes.requestCompleted();
            RequestContextHolder.resetRequestAttributes();
        }
    }

    @Test
    void productionCreateFailureAfterActualOutboxInsertRollsBackAndSameCommandRecovers() {
        var inserted = new java.util.concurrent.atomic.AtomicBoolean();
        doAnswer(
                        call -> {
                            call.callRealMethod();
                            if ("excuse.requested".equals(call.getArgument(0))) {
                                inserted.set(true);
                                throw new IllegalStateException(
                                        "owned failure after real outbox insert");
                            }
                            return null;
                        })
                .when(storage)
                .save(anyString(), anyString());
        try {
            assertThatThrownBy(this::create).isInstanceOf(IllegalStateException.class);
            assertThat(inserted).isTrue();
            for (String collection :
                    List.of(
                            "excuse_tickets",
                            "attendance_outbox",
                            "lesson_actions",
                            "student_checkin_pairs"))
                assertThat(mongoTemplate.getCollection(collection).countDocuments())
                        .as(collection)
                        .isZero();
        } finally {
            reset(storage);
        }
        assertCommitted(create());
    }

    @Test
    void productionCreateCommitsDomainAuditAndOneMatchingRequestIntent() {
        assertCommitted(create());
    }

    private void assertCommitted(ru.rutcampustrack.attendance.excuse.entity.ExcuseTicket ticket) {
        assertThat(ticket.getId()).isNotBlank();
        var loaded =
                mongoTemplate.findById(
                        ticket.getId(),
                        ru.rutcampustrack.attendance.excuse.entity.ExcuseTicket.class);
        assertThat(loaded).isNotNull();
        assertThat(loaded.getId()).isEqualTo(ticket.getId());
        assertThat(loaded.getStudentId()).isEqualTo(42L);
        assertThat(loaded.getGroupId()).isEqualTo(1L);
        assertThat(loaded.getSemesterId()).isEqualTo(1L);
        assertThat(loaded.getLessonIds()).containsExactly(10L, 11L);
        assertThat(loaded.getExcuseType()).isEqualTo(ExcuseType.ILLNESS);
        assertThat(loaded.getStatus())
                .isEqualTo(
                        ru.rutcampustrack.attendance.contract.enums.ExcuseTicketStatus.SUBMITTED);
        assertThat(loaded.getCreatedAt())
                .isEqualTo(ticket.getCreatedAt().truncatedTo(java.time.temporal.ChronoUnit.MILLIS));
        assertThat(loaded.getUpdatedAt())
                .isEqualTo(ticket.getUpdatedAt().truncatedTo(java.time.temporal.ChronoUnit.MILLIS));
        var intents =
                mongoTemplate
                        .getCollection("attendance_outbox")
                        .find(new org.bson.Document("event_type", "excuse.requested"))
                        .into(new java.util.ArrayList<>());
        assertThat(intents).hasSize(1);
        assertThat(intents.getFirst().getString("payload")).contains(ticket.getId());
        assertThat(mongoTemplate.getCollection("lesson_actions").countDocuments()).isPositive();
    }
}
