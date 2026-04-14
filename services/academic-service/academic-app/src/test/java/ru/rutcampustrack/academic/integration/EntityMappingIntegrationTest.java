package ru.rutcampustrack.academic.integration;

import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.annotation.Rollback;
import org.springframework.transaction.annotation.Transactional;
import ru.rutcampustrack.academic.entity.*;
import ru.rutcampustrack.academic.repository.*;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;

@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class EntityMappingIntegrationTest extends AbstractAcademicIntegrationTest {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private SemesterRepository semesterRepository;

    @Autowired
    private GroupRepository groupRepository;

    @Autowired
    private SubjectRepository subjectRepository;

    @Autowired
    private StudentGroupHistoryRepository studentGroupHistoryRepository;

    @Autowired
    private TeacherSubjectGroupRepository teacherSubjectGroupRepository;

    @Autowired
    private HeadmanAssistantRepository headmanAssistantRepository;

    @Autowired
    private AttendanceThresholdRepository attendanceThresholdRepository;

    @Autowired
    private HomeworkRepository homeworkRepository;

    @Autowired
    private HomeworkCompletionRepository homeworkCompletionRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    /**
     * Test 1 — Schema validation: Application context loads without Hibernate schema
     * validation errors, proving all 11 entities match Flyway V1+V2+V3 schema.
     */
    @Test
    @Order(1)
    void contextLoads_allEntitiesValidateAgainstSchema() {
        // If Spring context starts successfully (reaching this line), all 11 entities
        // have passed Hibernate ddl-auto: validate against the Flyway-migrated schema.
        assertThat(userRepository).isNotNull();
        assertThat(semesterRepository).isNotNull();
        assertThat(groupRepository).isNotNull();
        assertThat(subjectRepository).isNotNull();
        assertThat(studentGroupHistoryRepository).isNotNull();
        assertThat(teacherSubjectGroupRepository).isNotNull();
        assertThat(headmanAssistantRepository).isNotNull();
        assertThat(attendanceThresholdRepository).isNotNull();
        assertThat(homeworkRepository).isNotNull();
        assertThat(homeworkCompletionRepository).isNotNull();
    }

    /**
     * Test 2 — V2 seed data: UserRepository.findByLogin("student") returns a user
     * with role STUDENT, proving V2 seed data is intact and enum converter works.
     */
    @Test
    @Order(2)
    void findByLogin_seedStudent_returnsPresentWithStudentRole() {
        Optional<User> user = userRepository.findByLogin("student");

        assertThat(user).isPresent();
        assertThat(user.get().getLogin()).isEqualTo("student");
        assertThat(user.get().getRole().name()).isEqualTo("STUDENT");
        assertThat(user.get().getStatus().name()).isEqualTo("ACTIVE");
    }

    /**
     * Test 3 — @SQLRestriction: Archive a user via native query, then findById returns
     * empty, then findByIdIncludingArchived returns the user (D-01, D-02).
     */
    @Test
    @Order(3)
    void sqlRestriction_archivedUser_filteredFromStandardQuery_visibleViaBypass() {
        Optional<User> userOpt = userRepository.findByLogin("student");
        assertThat(userOpt).isPresent();
        Long userId = userOpt.get().getId();

        // Archive via native query to bypass @SQLRestriction
        jdbcTemplate.update("UPDATE users SET status = 'archived' WHERE login = 'student'");

        // Standard find should return empty (filtered by @SQLRestriction)
        Optional<User> filteredResult = userRepository.findByLogin("student");
        assertThat(filteredResult).isEmpty();

        // findByIdIncludingArchived bypasses @SQLRestriction
        Optional<User> archivedResult = userRepository.findByIdIncludingArchived(userId);
        assertThat(archivedResult).isPresent();
        assertThat(archivedResult.get().getStatus().name()).isEqualTo("ARCHIVED");

        // Restore for other tests
        jdbcTemplate.update("UPDATE users SET status = 'active' WHERE login = 'student'");
    }

    /**
     * Test 4 — Login sequences: nextStudentLoginSeq returns incrementing values from V3
     * PostgreSQL sequences (D-03/D-04).
     */
    @Test
    @Order(4)
    void loginSequences_nextStudentLoginSeq_returnsIncrementingValues() {
        Long seq1 = userRepository.nextStudentLoginSeq();
        Long seq2 = userRepository.nextStudentLoginSeq();

        assertThat(seq1).isNotNull().isGreaterThanOrEqualTo(1L);
        assertThat(seq2).isEqualTo(seq1 + 1);
    }

    /**
     * Test 5 — HeadmanAssistant array: Save with permissions, reload, verify array
     * contents persist correctly as PostgreSQL VARCHAR(64)[] (D-06).
     */
    @Test
    @Order(5)
    @Transactional
    @Rollback
    void headmanAssistant_persistAndReloadPermissionsArray_roundTripsCorrectly() {
        // Get seed data references
        Long studentId = jdbcTemplate.queryForObject(
            "SELECT id FROM users WHERE login = 'student'", Long.class);
        Long adminId = jdbcTemplate.queryForObject(
            "SELECT id FROM users WHERE login = 'admin'", Long.class);
        Long groupId = jdbcTemplate.queryForObject(
            "SELECT id FROM groups WHERE name = 'IVT-21-1'", Long.class);

        // Insert HeadmanAssistant via native query to avoid @SQLRestriction issues
        // (student is active, so we can also use repository but native ensures isolation)
        jdbcTemplate.update(
            "INSERT INTO headman_assistants (group_id, student_id, permissions, assigned_by, is_active, assigned_at) " +
            "VALUES (?, ?, ARRAY['mark_attendance','manage_excuses']::varchar(64)[], ?, true, NOW())",
            groupId, studentId, adminId
        );

        Optional<HeadmanAssistant> found = headmanAssistantRepository
            .findByGroupIdAndStudentId(groupId, studentId);

        assertThat(found).isPresent();
        assertThat(found.get().getPermissions())
            .containsExactlyInAnyOrder("mark_attendance", "manage_excuses");
    }

    /**
     * Test 6 — Threshold resolution: 3-level most-specific-wins (THRSH-04).
     * global (null,null) = 70%, group (groupId,null) = 80%, subject (groupId,subjectId) = 90%.
     */
    @Test
    @Order(6)
    @Transactional
    @Rollback
    void attendanceThreshold_threeLevel_mostSpecificWins() {
        Long adminId = jdbcTemplate.queryForObject(
            "SELECT id FROM users WHERE login = 'admin'", Long.class);
        Long groupId = jdbcTemplate.queryForObject(
            "SELECT id FROM groups WHERE name = 'IVT-21-1'", Long.class);

        // Create a subject for this test
        Long subjectId = jdbcTemplate.queryForObject(
            "INSERT INTO subjects (name, type) VALUES ('Test Subject', 'lecture') RETURNING id",
            Long.class
        );

        // Global threshold (null, null) = 70%
        jdbcTemplate.update(
            "INSERT INTO attendance_thresholds (group_id, subject_id, threshold_pct, set_by, created_at) " +
            "VALUES (NULL, NULL, 70, ?, NOW())",
            adminId
        );

        // Group threshold (groupId, null) = 80%
        jdbcTemplate.update(
            "INSERT INTO attendance_thresholds (group_id, subject_id, threshold_pct, set_by, created_at) " +
            "VALUES (?, NULL, 80, ?, NOW())",
            groupId, adminId
        );

        // Subject threshold (groupId, subjectId) = 90%
        jdbcTemplate.update(
            "INSERT INTO attendance_thresholds (group_id, subject_id, threshold_pct, set_by, created_at) " +
            "VALUES (?, ?, 90, ?, NOW())",
            groupId, subjectId, adminId
        );

        // Verify most-specific-wins resolution
        Optional<AttendanceThreshold> subjectLevel =
            attendanceThresholdRepository.findByGroupIdAndSubjectId(groupId, subjectId);
        assertThat(subjectLevel).isPresent();
        assertThat(subjectLevel.get().getThresholdPct()).isEqualTo(90);

        Optional<AttendanceThreshold> groupLevel =
            attendanceThresholdRepository.findByGroupIdAndSubjectIdIsNull(groupId);
        assertThat(groupLevel).isPresent();
        assertThat(groupLevel.get().getThresholdPct()).isEqualTo(80);

        Optional<AttendanceThreshold> globalLevel =
            attendanceThresholdRepository.findByGroupIdIsNullAndSubjectIdIsNull();
        assertThat(globalLevel).isPresent();
        assertThat(globalLevel.get().getThresholdPct()).isEqualTo(70);
    }

    /**
     * Test 7 — Semester EXCLUDE constraint: Saving two active semesters throws
     * DataIntegrityViolationException (GSEM-03).
     */
    @Test
    @Order(7)
    void semester_twoActiveSemesters_throwsDataIntegrityViolation() {
        // V2 already inserted one active semester ('Spring 2026').
        // Trying to insert another active semester should fail.
        Semester second = new Semester();
        second.setName("Fall 2026");
        second.setDateFrom(LocalDate.of(2026, 9, 1));
        second.setDateTo(LocalDate.of(2027, 1, 31));
        second.setActive(true);

        assertThatThrownBy(() -> semesterRepository.saveAndFlush(second))
            .isInstanceOf(DataIntegrityViolationException.class);
    }
}
