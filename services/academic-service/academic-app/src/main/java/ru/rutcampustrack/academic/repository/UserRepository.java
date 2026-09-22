package ru.rutcampustrack.academic.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import jakarta.persistence.LockModeType;
import ru.rutcampustrack.academic.contract.enums.UserRole;
import ru.rutcampustrack.academic.entity.User;
import java.util.List;
import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Long>, JpaSpecificationExecutor<User> {

    Optional<User> findByLogin(String login);

    Optional<User> findByTelegramId(Long telegramId);

    Optional<User> findByEmployeeNumber(String employeeNumber);

    // --- Existence checks for conflict pre-validation (BUG-006-2, D-07). ---
    // Use native queries to bypass the @SQLRestriction filter on User — a user
    // archived yesterday still owns the login/email/telegramId/employeeNumber
    // until admin explicitly reactivates/purges, so duplicate creation must
    // 409 regardless of the archived flag.

    @Query(value = "SELECT EXISTS (SELECT 1 FROM users WHERE login = :login)", nativeQuery = true)
    boolean existsByLogin(@Param("login") String login);

    @Query(value = "SELECT EXISTS (SELECT 1 FROM users WHERE email = :email)", nativeQuery = true)
    boolean existsByEmail(@Param("email") String email);

    @Query(value = "SELECT EXISTS (SELECT 1 FROM users WHERE telegram_id = :telegramId)", nativeQuery = true)
    boolean existsByTelegramId(@Param("telegramId") Long telegramId);

    @Query(value = "SELECT EXISTS (SELECT 1 FROM users WHERE employee_number = :employeeNumber)", nativeQuery = true)
    boolean existsByEmployeeNumber(@Param("employeeNumber") String employeeNumber);

    // Group/role listings: ORDER BY ... COLLATE "ru_icu" so Cyrillic surnames
    // sort alphabetically (Ё between Е and Ж, not at end). Index: idx_users_lastname_icu (V22).
    // Native queries — JPQL can't express COLLATE clauses.

    @Query(value = "SELECT * FROM users WHERE group_id = :groupId AND status <> 'archived' "
            + "ORDER BY last_name COLLATE \"ru_icu\", first_name COLLATE \"ru_icu\", middle_name COLLATE \"ru_icu\"",
            nativeQuery = true)
    List<User> findByGroupId(@Param("groupId") Long groupId);

    /** Student roster source for mixed-role accounts: authority comes from the grant. */
    @Query(value = "SELECT u.* FROM users u "
            + "JOIN user_role_grants g ON g.user_id = u.id "
            + "WHERE g.role = 'student' AND g.status = 'active' AND g.group_id = :groupId "
            + "AND u.status <> 'archived' "
            + "ORDER BY u.last_name COLLATE \"ru_icu\", u.first_name COLLATE \"ru_icu\", u.middle_name COLLATE \"ru_icu\"",
            nativeQuery = true)
    List<User> findActiveStudentsByGrantGroupId(@Param("groupId") Long groupId);

    /** Durable active HEADMAN authority for one group; the partial V33 index
     * normally makes this list contain at most one row. Keeping a list here
     * lets the service fail closed if old data predates that invariant. */
    @Query(value = "SELECT u.* FROM users u "
            + "JOIN user_role_grants g ON g.user_id = u.id "
            + "WHERE g.role = 'headman' AND g.status = 'active' AND g.group_id = :groupId "
            + "AND u.status <> 'archived' ORDER BY u.id",
            nativeQuery = true)
    List<User> findActiveHeadmenByGrantGroupId(@Param("groupId") Long groupId);

    /** ID-only variant used while rechecking the group CAS after row locks. */
    @Query(value = "SELECT g.user_id FROM user_role_grants g "
            + "JOIN users u ON u.id = g.user_id "
            + "WHERE g.role = 'headman' AND g.status = 'active' AND g.group_id = :groupId "
            + "AND u.status <> 'archived' ORDER BY g.user_id",
            nativeQuery = true)
    List<Long> findActiveHeadmanIdsByGrantGroupId(@Param("groupId") Long groupId);

    @Query(value = "SELECT u.* FROM users u "
            + "JOIN user_role_grants g ON g.user_id = u.id "
            + "WHERE g.role = 'student' AND g.status = 'active' AND g.group_id = :groupId "
            + "AND u.status <> 'archived' "
            + "ORDER BY u.last_name COLLATE \"ru_icu\", u.first_name COLLATE \"ru_icu\", u.middle_name COLLATE \"ru_icu\"",
            countQuery = "SELECT COUNT(*) FROM users u JOIN user_role_grants g ON g.user_id = u.id "
                    + "WHERE g.role = 'student' AND g.status = 'active' AND g.group_id = :groupId "
                    + "AND u.status <> 'archived'",
            nativeQuery = true)
    Page<User> findActiveStudentsByGrantGroupId(@Param("groupId") Long groupId, Pageable pageable);

    @Query(value = "SELECT g.group_id FROM user_role_grants g "
            + "WHERE g.user_id = :userId AND g.role = 'student' AND g.status = 'active'",
            nativeQuery = true)
    Optional<Long> findActiveStudentGrantGroupId(@Param("userId") Long userId);

    @Query(value = "SELECT * FROM users WHERE group_id = :groupId AND status <> 'archived' "
            + "ORDER BY last_name COLLATE \"ru_icu\", first_name COLLATE \"ru_icu\", middle_name COLLATE \"ru_icu\"",
            countQuery = "SELECT COUNT(*) FROM users WHERE group_id = :groupId AND status <> 'archived'",
            nativeQuery = true)
    Page<User> findByGroupId(@Param("groupId") Long groupId, Pageable pageable);

    @Query(value = "SELECT * FROM users WHERE role = cast(:role AS user_role) AND status <> 'archived' "
            + "ORDER BY last_name COLLATE \"ru_icu\", first_name COLLATE \"ru_icu\", middle_name COLLATE \"ru_icu\"",
            countQuery = "SELECT COUNT(*) FROM users WHERE role = cast(:role AS user_role) AND status <> 'archived'",
            nativeQuery = true)
    Page<User> findByRole(@Param("role") String role, Pageable pageable);

    @Query(value = "SELECT COUNT(*) FROM users WHERE role = cast(:role AS user_role) AND status <> 'archived'", nativeQuery = true)
    long countByRole(@Param("role") String role);

    @Query(value = "SELECT * FROM users WHERE group_id = :groupId AND role = cast(:role AS user_role) AND status <> 'archived' "
            + "ORDER BY last_name COLLATE \"ru_icu\", first_name COLLATE \"ru_icu\", middle_name COLLATE \"ru_icu\"",
            countQuery = "SELECT COUNT(*) FROM users WHERE group_id = :groupId AND role = cast(:role AS user_role) AND status <> 'archived'",
            nativeQuery = true)
    Page<User> findByGroupIdAndRole(@Param("groupId") Long groupId, @Param("role") String role, Pageable pageable);

    /** Login generation — atomic via PostgreSQL sequence (per D-03/D-04) */
    @Query(value = "SELECT nextval('student_login_seq')", nativeQuery = true)
    Long nextStudentLoginSeq();

    @Query(value = "SELECT nextval('teacher_login_seq')", nativeQuery = true)
    Long nextTeacherLoginSeq();

    /**
     * Bypasses @SQLRestriction to find any user by ID including archived.
     * Required for admin reactivation and audit operations (per D-02).
     */
    @Query(value = "SELECT * FROM users WHERE id = :id", nativeQuery = true)
    Optional<User> findByIdIncludingArchived(@Param("id") Long id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select u from User u where u.id = :id")
    Optional<User> findByIdForUpdate(@Param("id") Long id);

    /**
     * Locks a row even after it has been archived so status/grant revocation
     * remains atomic and idempotent.
     */
    @Query(value = "SELECT * FROM users WHERE id = :id FOR UPDATE", nativeQuery = true)
    Optional<User> findByIdIncludingArchivedForUpdate(@Param("id") Long id);

    @Query(value = "SELECT * FROM users WHERE id IN (:ids)", nativeQuery = true)
    List<User> findAllIncludingArchivedByIds(@Param("ids") List<Long> ids);

    @Query(value = "SELECT * FROM users WHERE group_id = :groupId AND role = 'student'",
            nativeQuery = true)
    List<User> findAllStudentAssignmentsIncludingArchived(@Param("groupId") Long groupId);

    /** Returns all archived users. Bypasses @SQLRestriction (per D-02). */
    @Query(value = "SELECT * FROM users WHERE status = 'archived'", nativeQuery = true)
    List<User> findAllArchived();
}
