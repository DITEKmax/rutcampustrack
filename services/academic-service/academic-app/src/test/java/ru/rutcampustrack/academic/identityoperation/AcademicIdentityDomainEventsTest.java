package ru.rutcampustrack.academic.identityoperation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

import ru.rutcampustrack.academic.event.AcademicPersonalNotificationEvent;
import ru.rutcampustrack.academic.event.DashboardInvalidatedEvent;
import ru.rutcampustrack.academic.repository.SemesterRepository;
import ru.rutcampustrack.academic.repository.StudentGroupHistoryRepository;
import ru.rutcampustrack.authoperations.grpc.*;

import java.sql.ResultSet;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

class AcademicIdentityDomainEventsTest {
    @Test
    void archivingHeadmanRetainsPersonalRevocationsWithAcceptedActorAndOneHelperEvent()
            throws Exception {
        var activeHelper = new AtomicBoolean(true);
        var student = mock(ResultSet.class);
        when(student.getString("status")).thenReturn("ACTIVE");
        when(student.getObject("group_id", Long.class)).thenReturn(7L);
        var assistant = mock(ResultSet.class);
        when(assistant.getLong("id")).thenReturn(22L);
        when(assistant.getLong("student_id")).thenReturn(9L);
        var permissions = mock(java.sql.Array.class);
        when(permissions.getArray()).thenReturn(new String[] {"HOMEWORK"});
        when(assistant.getArray("permissions")).thenReturn(permissions);
        JdbcTemplate jdbc =
                mock(
                        JdbcTemplate.class,
                        invocation -> {
                            String sql = (String) invocation.getArgument(0);
                            return switch (invocation.getMethod().getName()) {
                                case "queryForList" ->
                                        sql.contains("SELECT group_id FROM student_group_history")
                                                ? List.of(7L)
                                                : sql.contains("student_group_history")
                                                        ? List.of(33L)
                                                        : sql.contains("academic_profiles")
                                                                ? List.of(2L)
                                                                : List.of(1L);
                                case "queryForObject" -> true;
                                case "query" -> {
                                    RowMapper<?> mapper = invocation.getArgument(1);
                                    if (sql.contains("role='STUDENT'"))
                                        yield List.of(mapper.mapRow(student, 0));
                                    if (sql.contains("headman_assistants") && activeHelper.get())
                                        yield List.of(mapper.mapRow(assistant, 0));
                                    yield List.of();
                                }
                                case "update" -> {
                                    if (sql.contains("UPDATE headman_assistants"))
                                        activeHelper.set(false);
                                    yield 1;
                                }
                                default -> org.mockito.Answers.RETURNS_DEFAULTS.answer(invocation);
                            };
                        });
        var publisher = mock(ApplicationEventPublisher.class);
        var emitted = new ArrayList<Object>();
        doAnswer(
                        i -> {
                            emitted.add(i.getArgument(0));
                            return null;
                        })
                .when(publisher)
                .publishEvent(any(Object.class));
        doAnswer(
                        i -> {
                            emitted.add(i.getArgument(0));
                            return null;
                        })
                .when(publisher)
                .publishEvent(any(org.springframework.context.ApplicationEvent.class));
        var writer =
                new AcademicIdentityDomainWriter(
                        jdbc,
                        mock(SemesterRepository.class),
                        publisher,
                        mock(StudentGroupHistoryRepository.class));
        var command =
                new AcademicIdentityLocalCommand(
                        new AcademicIdentityLocalCommand.ApiIntent("ARCHIVE", 8, "0".repeat(64)),
                        null,
                        LocalDate.of(2026, 4, 20),
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        List.of(new AcademicIdentityLocalCommand.GroupRevision(7, 1)));
        var prepared =
                IdentityOperationResult.newBuilder()
                        .addTargets(
                                PreparedIdentityTarget.newBuilder()
                                        .setUserId(8)
                                        .setExpectedAcademicRevision(2)
                                        .setFinalRolesVersion(5)
                                        .addGrants(
                                                AdmissionGrant.newBuilder()
                                                        .setGrantId(11)
                                                        .setRole("HEADMAN")
                                                        .setStatus("ARCHIVED")
                                                        .setGroupId(7))
                                        .addGrants(
                                                AdmissionGrant.newBuilder()
                                                        .setGrantId(12)
                                                        .setRole("STUDENT")
                                                        .setStatus("ARCHIVED")
                                                        .setGroupId(7)))
                        .build();
        var receipts =
                writer.apply(
                        command,
                        IdentityOperationKind.IDENTITY_OPERATION_KIND_ARCHIVE,
                        prepared,
                        99);
        var facts =
                emitted.stream()
                        .filter(AcademicPersonalNotificationEvent.class::isInstance)
                        .map(AcademicPersonalNotificationEvent.class::cast)
                        .toList();
        assertThat(facts).hasSize(2);
        assertThat(facts.stream().map(e -> (Map<?, ?>) e.getPayload()))
                .allSatisfy(
                        payload -> {
                            assertThat(payload.get("actor_user_id")).isEqualTo(99L);
                            assertThat(payload.get("after_active")).isEqualTo(false);
                            assertThat(payload.get("change")).isEqualTo("REVOKED");
                        });
        assertThat(facts.stream().map(e -> (Long) ((Map<?, ?>) e.getPayload()).get("user_id")))
                .containsExactly(9L, 8L);
        assertThat(emitted.stream().filter(DashboardInvalidatedEvent.class::isInstance)).hasSize(1);
        assertThat(receipts)
                .singleElement()
                .satisfies(
                        receipt -> assertThat(receipt.getCommittedAcademicRevision()).isEqualTo(3));
    }
}
