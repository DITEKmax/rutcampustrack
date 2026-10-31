package ru.rutcampustrack.academic.repository;

import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import ru.rutcampustrack.academic.entity.User;

public interface UserRepository extends JpaRepository<User, Long>, JpaSpecificationExecutor<User> {

    String SAFE_COLUMNS =
            "id, login, last_name, first_name, middle_name, email, phone, telegram_id,"
                    + " telegram_username, employee_number, role, status, is_headman, group_id,"
                    + " password_changed, avatar_id, created_at, updated_at";
    String SAFE_ALIASED_COLUMNS =
            "u.id, u.login, u.last_name, u.first_name, u.middle_name, u.email, u.phone,"
                    + " u.telegram_id, u.telegram_username, u.employee_number, u.role, u.status,"
                    + " u.is_headman, u.group_id, u.password_changed, u.avatar_id, u.created_at,"
                    + " u.updated_at";

    String ADMIN_ARCHIVE_FILTERS =
            """
            (CAST(:searchPattern AS text) IS NULL
             OR lower(u.login) LIKE :searchPattern ESCAPE '\\'
             OR lower(u.last_name) LIKE :searchPattern ESCAPE '\\'
             OR lower(u.first_name) LIKE :searchPattern ESCAPE '\\'
             OR lower(coalesce(u.middle_name, '')) LIKE :searchPattern ESCAPE '\\'
             OR lower(coalesce(u.employee_number, '')) LIKE :searchPattern ESCAPE '\\'
             OR CAST(coalesce(u.telegram_id, 0) AS text) LIKE :searchPattern ESCAPE '\\')
            AND (CAST(:role AS text) IS NULL OR EXISTS (
                SELECT 1 FROM academic_role_directory g
                 WHERE g.user_id = u.id AND g.role = :role
                   AND (CAST(:roleStatus AS text) IS NULL OR g.status = :roleStatus)))
            AND (CAST(:groupId AS bigint) IS NULL OR EXISTS (
                SELECT 1 FROM academic_role_directory gg
                 WHERE gg.user_id = u.id AND gg.role IN ('student', 'headman')
                   AND gg.group_id = :groupId
                   AND (CAST(:role AS text) IS NULL OR :role NOT IN ('student', 'headman') OR gg.role = :role)
                   AND (CAST(:roleStatus AS text) IS NULL OR :role IN ('teacher', 'admin') OR gg.status = :roleStatus)))
            AND (CAST(:accountStatus AS text) IS NULL OR CAST(u.status AS text) = :accountStatus)
            """;

    /** Explicit ADMIN archive filters only; ordinary lookups retain User's SQL restriction. */
    @Query(
            value =
                    "SELECT "
                            + SAFE_ALIASED_COLUMNS
                            + " FROM academic_user_directory u WHERE "
                            + ADMIN_ARCHIVE_FILTERS,
            countQuery =
                    "SELECT count(*) FROM academic_user_directory u WHERE " + ADMIN_ARCHIVE_FILTERS,
            nativeQuery = true)
    Page<User> findAdminRegistryIncludingArchived(
            @Param("searchPattern") String searchPattern,
            @Param("role") String role,
            @Param("roleStatus") String roleStatus,
            @Param("accountStatus") String accountStatus,
            @Param("groupId") Long groupId,
            Pageable pageable);

    Optional<User> findByLogin(String login);

    Optional<User> findByTelegramId(Long telegramId);

    Optional<User> findByEmployeeNumber(String employeeNumber);

    // --- Existence checks for conflict pre-validation (BUG-006-2, D-07). ---
    // Use native queries to bypass the @SQLRestriction filter on User — a user
    // archived yesterday still owns the login/email/telegramId/employeeNumber
    // until admin explicitly reactivates/purges, so duplicate creation must
    // 409 regardless of the archived flag.

    @Query(
            value = "SELECT EXISTS (SELECT 1 FROM academic_user_directory WHERE login = :login)",
            nativeQuery = true)
    boolean existsByLogin(@Param("login") String login);

    @Query(
            value = "SELECT EXISTS (SELECT 1 FROM academic_user_directory WHERE email = :email)",
            nativeQuery = true)
    boolean existsByEmail(@Param("email") String email);

    @Query(
            value =
                    "SELECT EXISTS (SELECT 1 FROM academic_user_directory WHERE telegram_id ="
                            + " :telegramId)",
            nativeQuery = true)
    boolean existsByTelegramId(@Param("telegramId") Long telegramId);

    @Query(
            value =
                    "SELECT EXISTS (SELECT 1 FROM academic_user_directory WHERE employee_number ="
                            + " :employeeNumber)",
            nativeQuery = true)
    boolean existsByEmployeeNumber(@Param("employeeNumber") String employeeNumber);

    // Group/role listings: ORDER BY ... COLLATE "ru_icu" so Cyrillic surnames
    // sort alphabetically (Ё between Е and Ж, not at end). Index: idx_users_lastname_icu (V22).
    // Native queries — JPQL can't express COLLATE clauses.

    @Query(
            value =
                    "SELECT "
                            + SAFE_COLUMNS
                            + " FROM academic_user_directory WHERE group_id = :groupId AND status"
                            + " <> 'archived' ORDER BY last_name COLLATE \"ru_icu\", first_name"
                            + " COLLATE \"ru_icu\", middle_name COLLATE \"ru_icu\"",
            nativeQuery = true)
    List<User> findByGroupId(@Param("groupId") Long groupId);

    /** Student roster source for mixed-role accounts: authority comes from the grant. */
    @Query(
            value =
                    "SELECT "
                            + SAFE_ALIASED_COLUMNS
                            + " FROM academic_user_directory u JOIN academic_role_eligibility g ON"
                            + " g.user_id = u.id WHERE g.role = 'STUDENT' AND g.status = 'ACTIVE'"
                            + " AND g.group_id = :groupId AND u.status <> 'archived' ORDER BY"
                            + " u.last_name COLLATE \"ru_icu\", u.first_name COLLATE \"ru_icu\","
                            + " u.middle_name COLLATE \"ru_icu\"",
            nativeQuery = true)
    List<User> findActiveStudentsByGrantGroupId(@Param("groupId") Long groupId);

    /**
     * Durable active HEADMAN authority for one group; the partial V33 index normally makes this
     * list contain at most one row. Keeping a list here lets the service fail closed if old data
     * predates that invariant.
     */
    @Query(
            value =
                    "SELECT "
                            + SAFE_ALIASED_COLUMNS
                            + " FROM academic_user_directory u JOIN academic_role_eligibility g ON"
                            + " g.user_id = u.id WHERE g.role = 'HEADMAN' AND g.status = 'ACTIVE'"
                            + " AND g.group_id = :groupId AND u.status <> 'archived' ORDER BY u.id",
            nativeQuery = true)
    List<User> findActiveHeadmenByGrantGroupId(@Param("groupId") Long groupId);

    /** ID-only variant used while rechecking the group CAS after row locks. */
    @Query(
            value =
                    "SELECT g.user_id FROM academic_role_eligibility g JOIN academic_user_directory"
                            + " u ON u.id = g.user_id WHERE g.role = 'HEADMAN' AND g.status = 'ACTIVE'"
                            + " AND g.group_id = :groupId AND u.status <> 'archived' ORDER BY"
                            + " g.user_id",
            nativeQuery = true)
    List<Long> findActiveHeadmanIdsByGrantGroupId(@Param("groupId") Long groupId);

    @Query(
            value =
                    "SELECT "
                            + SAFE_ALIASED_COLUMNS
                            + " FROM academic_user_directory u JOIN academic_role_eligibility g ON"
                            + " g.user_id = u.id WHERE g.role = 'STUDENT' AND g.status = 'ACTIVE'"
                            + " AND g.group_id = :groupId AND u.status <> 'archived' ORDER BY"
                            + " u.last_name COLLATE \"ru_icu\", u.first_name COLLATE \"ru_icu\","
                            + " u.middle_name COLLATE \"ru_icu\"",
            countQuery =
                    "SELECT COUNT(*) FROM academic_user_directory u JOIN academic_role_eligibility"
                            + " g ON g.user_id = u.id WHERE g.role = 'STUDENT' AND g.status = 'ACTIVE'"
                            + " AND g.group_id = :groupId AND u.status <> 'archived'",
            nativeQuery = true)
    Page<User> findActiveStudentsByGrantGroupId(@Param("groupId") Long groupId, Pageable pageable);

    @Query(
            value =
                    "SELECT g.group_id FROM academic_role_eligibility g WHERE g.user_id = :userId"
                            + " AND g.role = 'STUDENT' AND g.status = 'ACTIVE'",
            nativeQuery = true)
    Optional<Long> findActiveStudentGrantGroupId(@Param("userId") Long userId);

    @Query(
            value =
                    "SELECT "
                            + SAFE_COLUMNS
                            + " FROM academic_user_directory WHERE group_id = :groupId AND status"
                            + " <> 'archived' ORDER BY last_name COLLATE \"ru_icu\", first_name"
                            + " COLLATE \"ru_icu\", middle_name COLLATE \"ru_icu\"",
            countQuery =
                    "SELECT COUNT(*) FROM academic_user_directory WHERE group_id = :groupId AND"
                            + " status <> 'archived'",
            nativeQuery = true)
    Page<User> findByGroupId(@Param("groupId") Long groupId, Pageable pageable);

    @Query(
            value =
                    "SELECT "
                            + SAFE_COLUMNS
                            + " FROM academic_user_directory WHERE role = cast(:role AS user_role)"
                            + " AND status <> 'archived' ORDER BY last_name COLLATE \"ru_icu\","
                            + " first_name COLLATE \"ru_icu\", middle_name COLLATE \"ru_icu\"",
            countQuery =
                    "SELECT COUNT(*) FROM academic_user_directory WHERE role = cast(:role AS"
                            + " user_role) AND status <> 'archived'",
            nativeQuery = true)
    Page<User> findByRole(@Param("role") String role, Pageable pageable);

    @Query(
            value =
                    "SELECT COUNT(*) FROM academic_user_directory WHERE role = cast(:role AS"
                            + " user_role) AND status <> 'archived'",
            nativeQuery = true)
    long countByRole(@Param("role") String role);

    @Query(
            value =
                    "SELECT "
                            + SAFE_COLUMNS
                            + " FROM academic_user_directory WHERE group_id = :groupId AND role ="
                            + " cast(:role AS user_role) AND status <> 'archived' ORDER BY"
                            + " last_name COLLATE \"ru_icu\", first_name COLLATE \"ru_icu\","
                            + " middle_name COLLATE \"ru_icu\"",
            countQuery =
                    "SELECT COUNT(*) FROM academic_user_directory WHERE group_id = :groupId AND"
                            + " role = cast(:role AS user_role) AND status <> 'archived'",
            nativeQuery = true)
    Page<User> findByGroupIdAndRole(
            @Param("groupId") Long groupId, @Param("role") String role, Pageable pageable);

    /** Login generation — atomic via PostgreSQL sequence (per D-03/D-04) */
    @Query(value = "SELECT nextval('student_login_seq')", nativeQuery = true)
    Long nextStudentLoginSeq();

    @Query(value = "SELECT nextval('teacher_login_seq')", nativeQuery = true)
    Long nextTeacherLoginSeq();

    /**
     * Bypasses @SQLRestriction to find any user by ID including archived. Required for admin
     * reactivation and audit operations (per D-02).
     */
    @Query(
            value = "SELECT " + SAFE_COLUMNS + " FROM academic_user_directory WHERE id = :id",
            nativeQuery = true)
    Optional<User> findByIdIncludingArchived(@Param("id") Long id);

    @Query(
            value =
                    "SELECT "
                            + SAFE_ALIASED_COLUMNS
                            + " FROM academic_user_directory u JOIN academic_profiles p ON"
                            + " p.id=u.id WHERE u.id=:id AND p.archived_at IS NULL FOR UPDATE OF p",
            nativeQuery = true)
    Optional<User> findByIdForUpdate(@Param("id") Long id);

    /**
     * Locks a row even after it has been archived so status/grant revocation remains atomic and
     * idempotent.
     */
    @Query(
            value =
                    "SELECT "
                            + SAFE_ALIASED_COLUMNS
                            + " FROM academic_user_directory u JOIN academic_profiles p ON"
                            + " p.id=u.id WHERE u.id=:id FOR UPDATE OF p",
            nativeQuery = true)
    Optional<User> findByIdIncludingArchivedForUpdate(@Param("id") Long id);

    @Query(
            value = "SELECT " + SAFE_COLUMNS + " FROM academic_user_directory WHERE id IN (:ids)",
            nativeQuery = true)
    List<User> findAllIncludingArchivedByIds(@Param("ids") List<Long> ids);

    @Query(
            value =
                    "SELECT "
                            + SAFE_COLUMNS
                            + " FROM academic_user_directory WHERE group_id = :groupId AND role ="
                            + " 'student'",
            nativeQuery = true)
    List<User> findAllStudentAssignmentsIncludingArchived(@Param("groupId") Long groupId);

    /** Returns all archived users. Bypasses @SQLRestriction (per D-02). */
    @Query(
            value =
                    "SELECT "
                            + SAFE_COLUMNS
                            + " FROM academic_user_directory WHERE status = 'archived'",
            nativeQuery = true)
    List<User> findAllArchived();
}
