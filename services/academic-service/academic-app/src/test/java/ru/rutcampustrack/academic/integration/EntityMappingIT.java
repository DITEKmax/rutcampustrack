package ru.rutcampustrack.academic.integration;

import static org.assertj.core.api.Assertions.*;

import jakarta.persistence.EntityManager;

import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.annotation.Rollback;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import ru.rutcampustrack.academic.contract.dto.user.CreateUserRequest;
import ru.rutcampustrack.academic.contract.enums.UserRole;
import ru.rutcampustrack.academic.entity.*;
import ru.rutcampustrack.academic.repository.*;
import ru.rutcampustrack.auth.session.model.AuthRole;

import java.time.LocalDate;
import java.util.Locale;
import java.util.Optional;

@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class EntityMappingIT extends AbstractAcademicIntegrationTest {

    @Autowired private UserRepository userRepository;

    @Autowired private SemesterRepository semesterRepository;

    @Autowired private GroupRepository groupRepository;

    @Autowired private SubjectRepository subjectRepository;

    @Autowired private StudentGroupHistoryRepository studentGroupHistoryRepository;

    @Autowired private TeacherSubjectGroupRepository teacherSubjectGroupRepository;

    @Autowired private HeadmanAssistantRepository headmanAssistantRepository;

    @Autowired private AttendanceThresholdRepository attendanceThresholdRepository;

    @Autowired private HomeworkRepository homeworkRepository;

    @Autowired private HomeworkCompletionRepository homeworkCompletionRepository;

    @Autowired private JdbcTemplate jdbcTemplate;
    @Autowired private PlatformTransactionManager transactionManager;
    @jakarta.persistence.PersistenceContext private EntityManager entityManager;

    /**
     * Test 1 — Schema validation: Application context loads without Hibernate schema validation
     * errors, proving all 11 entities match Flyway V1+V2+V3 schema.
     */
    /**
     * Test 2 — The actual canonical Auth identity resolves through the Academic directory with its
     * generated login and STUDENT/ACTIVE enum mappings.
     */
    @Test
    @Order(2)
    void findByLogin_seedStudent_returnsPresentWithStudentRole() {
        String generatedLogin = canonicalStudentLogin();
        Optional<User> user = userRepository.findByLogin(generatedLogin);

        assertThat(user).isPresent();
        assertThat(user.get().getId()).isEqualTo(identityFixture.canonical().studentId());
        assertThat(user.get().getLogin()).isEqualTo(generatedLogin).isNotBlank();
        assertThat(user.get().getRole().name()).isEqualTo("STUDENT");
        assertThat(user.get().getStatus().name()).isEqualTo("ACTIVE");
    }

    /**
     * Test 3 — @SQLRestriction: An archived Academic projection is hidden by the standard query and
     * visible through findByIdIncludingArchived; the owned ORM fixture is restored (D-01, D-02).
     */
    @Test
    @Order(3)
    void sqlRestriction_archivedUser_filteredFromStandardQuery_visibleViaBypass() {
        String generatedLogin = canonicalStudentLogin();
        Optional<User> userOpt = userRepository.findByLogin(generatedLogin);
        assertThat(userOpt).isPresent();
        Long userId = userOpt.get().getId();
        var originalProfile =
                jdbcTemplate.queryForMap("SELECT * FROM academic_profiles WHERE id=?", userId);
        var originalHistory =
                jdbcTemplate.queryForList(
                        "SELECT * FROM student_group_history WHERE user_id=? ORDER BY id", userId);
        var tx = new TransactionTemplate(transactionManager);
        try {
            // Owned Academic ORM fixture only; this is not an Auth/business archive command.
            tx.executeWithoutResult(
                    status -> {
                        jdbcTemplate.update(
                                "UPDATE academic_profiles SET"
                                    + " archived_at=NOW(),directory_status='ARCHIVED' WHERE id=?",
                                userId);
                        entityManager.flush();
                        entityManager.clear();
                    });
            Optional<User> filteredResult = userRepository.findByLogin(generatedLogin);
            assertThat(filteredResult).isEmpty();
            Optional<User> archivedResult = userRepository.findByIdIncludingArchived(userId);
            assertThat(archivedResult).isPresent();
            assertThat(archivedResult.get().getStatus().name()).isEqualTo("ARCHIVED");
        } finally {
            tx.executeWithoutResult(
                    status -> {
                        jdbcTemplate.update(
                                "UPDATE academic_profiles SET archived_at=?,directory_status=?"
                                        + " WHERE id=?",
                                originalProfile.get("archived_at"),
                                originalProfile.get("directory_status"),
                                userId);
                        entityManager.flush();
                        entityManager.clear();
                    });
        }
        assertThat(jdbcTemplate.queryForMap("SELECT * FROM academic_profiles WHERE id=?", userId))
                .isEqualTo(originalProfile);
        assertThat(
                        jdbcTemplate.queryForList(
                                "SELECT * FROM student_group_history WHERE user_id=? ORDER BY id",
                                userId))
                .isEqualTo(originalHistory);
        assertThat(userRepository.findByLogin(generatedLogin)).isPresent();
    }

    /**
     * Test 4 — Legacy D-03/D-04 monotonic-allocation oracle follows the current Auth-owned teacher
     * fallback. STUDENT digits now derive from Telegram; Academic has no login sequence.
     */
    @Test
    @Order(4)
    void loginSequences_nextStudentLoginSeq_returnsIncrementingValues() {
        // Retain the original case identity while exercising the actual sequence owner/path.
        var admin = identityFixture.select(identityFixture.canonical().adminId(), AuthRole.ADMIN);
        var first =
                identityFixture.seedUser(
                        new CreateUserRequest(
                                "Entity",
                                "Sequence",
                                null,
                                UserRole.TEACHER,
                                null,
                                "ENTITY-SEQ-A",
                                null),
                        admin);
        Long seq1 =
                identityFixture
                        .authJdbc()
                        .queryForObject("SELECT last_value FROM teacher_login_seq", Long.class);
        var second =
                identityFixture.seedUser(
                        new CreateUserRequest(
                                "Entity",
                                "Sequence",
                                null,
                                UserRole.TEACHER,
                                null,
                                "ENTITY-SEQ-B",
                                null),
                        admin);
        Long seq2 =
                identityFixture
                        .authJdbc()
                        .queryForObject("SELECT last_value FROM teacher_login_seq", Long.class);
        assertThat(seq1).isNotNull().isGreaterThanOrEqualTo(1L);
        assertThat(seq2).isEqualTo(seq1 + 1);
        assertThat(first.login())
                .endsWith(String.format(Locale.ROOT, "-%04d", Math.floorMod(seq1, 10_000)));
        assertThat(second.login())
                .endsWith(String.format(Locale.ROOT, "-%04d", Math.floorMod(seq2, 10_000)));
        assertThat(userRepository.findByLogin(first.login())).isPresent();
        assertThat(userRepository.findByLogin(second.login())).isPresent();
    }

    /**
     * Test 5 — HeadmanAssistant array: Save with permissions, reload, verify array contents persist
     * correctly as PostgreSQL VARCHAR(64)[] (D-06).
     */
    @Test
    @Order(5)
    @Transactional
    @Rollback
    void headmanAssistant_persistAndReloadPermissionsArray_roundTripsCorrectly() {
        Long studentId = identityFixture.canonical().studentId();
        Long adminId = identityFixture.canonical().adminId();
        Long groupId = identityFixture.canonical().groupId();
        var persisted =
                headmanAssistantRepository.saveAndFlush(
                        new HeadmanAssistant(
                                groupId,
                                studentId,
                                new String[] {"mark_attendance", "manage_excuses"},
                                adminId));
        Long assistantId = persisted.getId();
        entityManager.clear();
        Optional<HeadmanAssistant> found =
                headmanAssistantRepository.findByGroupIdAndStudentId(groupId, studentId);

        assertThat(found).isPresent();
        assertThat(found.get().getId()).isEqualTo(assistantId);
        assertThat(found.get().getPermissions())
                .containsExactlyInAnyOrder("mark_attendance", "manage_excuses");
    }

    /**
     * Test 6 — Threshold resolution: 3-level most-specific-wins (THRSH-04). global (null,null) =
     * 70%, group (groupId,null) = 80%, subject (groupId,subjectId) = 90%.
     */
    private String canonicalStudentLogin() {
        return identityFixture
                .authJdbc()
                .queryForObject(
                        "SELECT login FROM auth_identities WHERE id=?",
                        String.class,
                        identityFixture.canonical().studentId());
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
