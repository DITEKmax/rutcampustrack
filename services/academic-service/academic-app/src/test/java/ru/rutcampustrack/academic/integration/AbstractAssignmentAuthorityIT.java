package ru.rutcampustrack.academic.integration;

import com.fasterxml.jackson.databind.ObjectMapper;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import ru.rutcampustrack.academic.contract.dto.user.CreateUserRequest;
import ru.rutcampustrack.academic.contract.dto.user.RoleGrantUpdateRequest;
import ru.rutcampustrack.academic.contract.enums.RoleGrantStatus;
import ru.rutcampustrack.academic.contract.enums.UserRole;
import ru.rutcampustrack.academic.identityoperation.AcademicUserIdentityOperations;
import ru.rutcampustrack.auth.session.model.AuthRole;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Real database fixtures shared by the L5A assignment authority integration tests. Producers still
 * use the REST entrypoints. Auth identities, grants and selected sessions use the real split
 * coordinator; SQL seeds only Academic-owned groups, semesters and canonical subject type rows.
 */
public abstract class AbstractAssignmentAuthorityIT extends AbstractAcademicIntegrationTest {

    private static final ZoneId MOSCOW = ZoneId.of("Europe/Moscow");
    private static final AtomicLong TELEGRAM_IDS = new AtomicLong(6_400_000_000L);

    private static final String CURRENT_SEMESTER_NAME =
            "L5A current authority semester " + LocalDate.now(MOSCOW);

    @Autowired protected JdbcTemplate jdbcTemplate;

    @Autowired protected ObjectMapper objectMapper;

    @Autowired protected ru.rutcampustrack.shared.security.InternalJwtTestFactory internalTokens;

    @Autowired private AcademicUserIdentityOperations identityOperations;

    @Autowired private PlatformTransactionManager fixtureTransactions;

    protected org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder
            signedHeadman(
                    org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder
                            request,
                    long userId,
                    long groupId) {
        var actor = identityFixture.select(userId, AuthRole.HEADMAN);
        if (!java.util.Objects.equals(actor.claims().groupId(), groupId))
            throw new IllegalArgumentException("Fixture HEADMAN must own the requested group");
        return request.with(identityFixture.signed(actor));
    }

    protected org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder
            signedTeacher(
                    org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder
                            request,
                    long userId) {
        return request.with(
                identityFixture.signed(identityFixture.select(userId, AuthRole.TEACHER)));
    }

    protected void successfulAdoptionReceipts(
            ru.rutcampustrack.academic.grpc.ScheduleGrpcClient schedule) {
        org.mockito.Mockito.when(
                        schedule.installAssignmentLifecycle(
                                org.mockito.ArgumentMatchers.any(UUID.class),
                                org.mockito.ArgumentMatchers.any(),
                                org.mockito.ArgumentMatchers.isNull(),
                                org.mockito.ArgumentMatchers.any(),
                                org.mockito.ArgumentMatchers.any(),
                                org.mockito.ArgumentMatchers.eq("CLOSE_UNASSIGN")))
                .thenAnswer(
                        call -> {
                            org.assertj.core.api.Assertions.assertThat(
                                            org.springframework.transaction.support
                                                    .TransactionSynchronizationManager
                                                    .isActualTransactionActive())
                                    .isFalse();
                            return ru.rutcampustrack.schedule.grpc.AssignmentCloseReceipt
                                    .newBuilder()
                                    .setOperationId(call.getArgument(0, UUID.class).toString())
                                    .setState("APPLIED")
                                    .build();
                        });
        org.mockito.Mockito.when(
                        schedule.installAssignmentLifecycle(
                                org.mockito.ArgumentMatchers.any(UUID.class),
                                org.mockito.ArgumentMatchers.any(),
                                org.mockito.ArgumentMatchers.any(),
                                org.mockito.ArgumentMatchers.any(),
                                org.mockito.ArgumentMatchers.any(),
                                org.mockito.ArgumentMatchers.eq("ADOPT_UNASSIGNED")))
                .thenAnswer(
                        call -> {
                            org.assertj.core.api.Assertions.assertThat(
                                            org.springframework.transaction.support
                                                    .TransactionSynchronizationManager
                                                    .isActualTransactionActive())
                                    .isFalse();
                            Long target = call.getArgument(2);
                            org.assertj.core.api.Assertions.assertThat(
                                            jdbcTemplate.queryForObject(
                                                    "SELECT lifecycle_state FROM assignments WHERE"
                                                            + " id=?",
                                                    String.class,
                                                    target))
                                    .isEqualTo("PREPARED");
                            return ru.rutcampustrack.schedule.grpc.AssignmentCloseReceipt
                                    .newBuilder()
                                    .setOperationId(call.getArgument(0, UUID.class).toString())
                                    .setState("APPLIED")
                                    .build();
                        });
        org.mockito.Mockito.when(
                        schedule.commitAssignmentClose(
                                org.mockito.ArgumentMatchers.any(UUID.class),
                                org.mockito.ArgumentMatchers.any()))
                .thenAnswer(
                        call -> {
                            org.assertj.core.api.Assertions.assertThat(
                                            org.springframework.transaction.support
                                                    .TransactionSynchronizationManager
                                                    .isActualTransactionActive())
                                    .isFalse();
                            UUID operation = call.getArgument(0);
                            org.assertj.core.api.Assertions.assertThat(
                                            jdbcTemplate.queryForObject(
                                                    "SELECT state FROM"
                                                        + " assignment_replacement_operations WHERE"
                                                        + " operation_id=?",
                                                    String.class,
                                                    operation))
                                    .isEqualTo("APPLIED");
                            return ru.rutcampustrack.schedule.grpc.AssignmentCloseReceipt
                                    .newBuilder()
                                    .setOperationId(operation.toString())
                                    .setState("COMMITTED")
                                    .build();
                        });
    }

    protected Fixture newFixture(String label) {
        String suffix = UUID.randomUUID().toString().replace("-", "");
        // V8 caps groups.name at 32 characters; keep enough UUID material to
        // make concurrently-created fixture groups distinct without using the
        // caller label in the persisted name.
        long groupId =
                insertGroup("L5A-" + suffix.substring(0, 12) + "-" + suffix.substring(12, 24));
        long headmanId = insertUser("Headman", "Student", UserRole.STUDENT, groupId, null);
        assignHeadman(headmanId, groupId);
        String employee1 = "L5A-" + suffix.substring(0, 12) + "-1";
        String employee2 = "L5A-" + suffix.substring(0, 12) + "-2";
        String employeeWithoutGrant = "L5A-" + suffix.substring(0, 12) + "-3";
        long teacher1 = insertUser("Teacher", "One", UserRole.TEACHER, null, employee1);
        long teacher2 = insertUser("Teacher", "Two", UserRole.TEACHER, null, employee2);
        long teacherWithoutGrant =
                insertUser("Teacher", "Three", UserRole.TEACHER, null, employeeWithoutGrant);
        // The negative fixture retains an ACTIVE directory profile and employee number but
        // no ACTIVE TEACHER authority. Canonical dismissal preserves that exact admission denial.
        setTeacherStatus(teacherWithoutGrant, RoleGrantStatus.DISMISSED);

        LocalDate dateFrom = allocateSemesterFrom();
        long semesterId =
                jdbcTemplate.queryForObject(
                        "INSERT INTO semesters (name, date_from, date_to, is_active, created_at) "
                                + "VALUES (?, ?, ?, false, NOW()) RETURNING id",
                        Long.class,
                        "L5A " + label + " semester " + suffix,
                        dateFrom,
                        dateFrom.plusDays(29));
        LocalDate dateTo = dateFrom.plusDays(29);
        return new Fixture(
                groupId,
                headmanId,
                teacher1,
                teacher2,
                teacherWithoutGrant,
                employee1,
                employee2,
                employeeWithoutGrant,
                semesterId,
                dateFrom,
                dateTo);
    }

    protected long insertSubject(long groupId, String name, String... lessonTypes) {
        if (lessonTypes.length == 0 || lessonTypes.length > 3) {
            throw new IllegalArgumentException("fixture subject needs one to three lesson types");
        }
        StringBuilder sql =
                new StringBuilder(
                        "WITH inserted_subject AS (INSERT INTO subjects (name, type, group_id)"
                            + " VALUES (?, ?::subject_type, ?) RETURNING id), inserted_lesson_types"
                            + " AS (INSERT INTO subject_lesson_types (subject_id, lesson_type) ");
        for (int index = 0; index < lessonTypes.length; index++) {
            if (index > 0) {
                sql.append(" UNION ALL ");
            }
            sql.append("SELECT id, ?::subject_type FROM inserted_subject");
        }
        sql.append(" RETURNING subject_id) SELECT subject_id FROM inserted_lesson_types LIMIT 1");
        Object[] arguments = new Object[3 + lessonTypes.length];
        arguments[0] = name;
        arguments[1] = lessonTypes[0].toLowerCase();
        arguments[2] = groupId;
        for (int index = 0; index < lessonTypes.length; index++) {
            arguments[3 + index] = lessonTypes[index].toLowerCase();
        }
        return jdbcTemplate.queryForObject(sql.toString(), Long.class, arguments);
    }

    protected long ensureCurrentActiveSemester() {
        List<Long> existing =
                jdbcTemplate.query(
                        "SELECT id FROM semesters WHERE name = ? ORDER BY id DESC LIMIT 1",
                        (rs, rowNum) -> rs.getLong(1),
                        CURRENT_SEMESTER_NAME);
        long semesterId;
        if (existing.isEmpty()) {
            LocalDate from = LocalDate.now(MOSCOW).minusDays(5);
            LocalDate to = LocalDate.now(MOSCOW).plusDays(30);
            semesterId =
                    jdbcTemplate.queryForObject(
                            "INSERT INTO semesters (name, date_from, date_to, is_active,"
                                    + " created_at) VALUES (?, ?, ?, false, NOW()) RETURNING id",
                            Long.class,
                            CURRENT_SEMESTER_NAME,
                            from,
                            to);
        } else {
            semesterId = existing.get(0);
        }
        jdbcTemplate.update("UPDATE semesters SET is_active = false WHERE is_active = true");
        jdbcTemplate.update("UPDATE semesters SET is_active = true WHERE id = ?", semesterId);
        return semesterId;
    }

    protected List<Long> activeSemesterIds() {
        return jdbcTemplate.query(
                "SELECT id FROM semesters WHERE is_active = true ORDER BY id",
                (rs, rowNum) -> rs.getLong(1));
    }

    protected void restoreActiveSemesterIds(List<Long> ids) {
        jdbcTemplate.update("UPDATE semesters SET is_active = false WHERE is_active = true");
        for (Long id : ids) {
            jdbcTemplate.update("UPDATE semesters SET is_active = true WHERE id = ?", id);
        }
    }

    protected long assignmentCount(long subjectId) {
        Integer count =
                jdbcTemplate.queryForObject(
                        "SELECT COUNT(*) FROM assignments WHERE subject_id = ?",
                        Integer.class,
                        subjectId);
        return count == null ? 0L : count;
    }

    protected long insertGroup(String name) {
        return new TransactionTemplate(fixtureTransactions)
                .execute(
                        status -> {
                            long groupId =
                                    jdbcTemplate.queryForObject(
                                            "INSERT INTO groups (name, is_active) VALUES (?, true)"
                                                    + " RETURNING id",
                                            Long.class,
                                            name);
                            // This new group has no prior roster. Establish its managed history
                            // boundary before canonical enrollment; no RPC runs under this lock.
                            int coverage =
                                    jdbcTemplate.update(
                                            "INSERT INTO group_history_coverage"
                                                    + " (group_id,coverage_from,writer_version)"
                                                    + " SELECT ?,date_from,? FROM semesters WHERE"
                                                    + " is_active AND NOT is_archived AND"
                                                    + " archive_transition='NONE' AND NOT"
                                                    + " archive_release_pending",
                                            groupId,
                                            ru.rutcampustrack.academic.history
                                                    .HistoricalMembershipService.WRITER_VERSION);
                            if (coverage != 1)
                                throw new IllegalStateException(
                                        "Assignment fixture requires one active semester");
                            return groupId;
                        });
    }

    protected long insertUser(
            String lastName, String firstName, UserRole role, Long groupId, String employeeNumber) {
        var administrator =
                identityFixture.select(identityFixture.canonical().adminId(), AuthRole.ADMIN);
        return identityFixture
                .seedUser(
                        new CreateUserRequest(
                                lastName,
                                firstName,
                                null,
                                role,
                                groupId,
                                employeeNumber,
                                role == UserRole.STUDENT ? TELEGRAM_IDS.incrementAndGet() : null),
                        administrator)
                .userId();
    }

    protected void setTeacherStatus(long userId, RoleGrantStatus status) {
        var administrator =
                identityFixture.select(identityFixture.canonical().adminId(), AuthRole.ADMIN);
        try (var ignored = identityFixture.request(administrator)) {
            identityOperations.role(
                    userId, "TEACHER", new RoleGrantUpdateRequest(status, null, null, null));
        }
        identityFixture.catchUpCatalog();
    }

    protected void assignHeadman(long userId, long groupId) {
        var administrator =
                identityFixture.select(identityFixture.canonical().adminId(), AuthRole.ADMIN);
        try (var ignored = identityFixture.request(administrator)) {
            identityOperations.headman(groupId, userId, null, false, null);
        }
        identityFixture.catchUpCatalog();
    }

    private LocalDate allocateSemesterFrom() {
        LocalDate candidate = LocalDate.of(2200, 1, 1);
        while (Boolean.TRUE.equals(
                jdbcTemplate.queryForObject(
                        "SELECT EXISTS (SELECT 1 FROM semesters WHERE date_from <= ? AND date_to >="
                                + " ?)",
                        Boolean.class,
                        candidate.plusDays(29),
                        candidate))) {
            candidate = candidate.plusDays(31);
        }
        return candidate;
    }

    protected record Fixture(
            long groupId,
            long headmanId,
            long teacher1Id,
            long teacher2Id,
            long teacherWithoutGrantId,
            String employee1,
            String employee2,
            String employeeWithoutGrant,
            long semesterId,
            LocalDate dateFrom,
            LocalDate dateTo) {}
}
