package ru.rutcampustrack.academic.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
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

    List<User> findByGroupId(Long groupId);
    Page<User> findByGroupId(Long groupId, Pageable pageable);

    @Query(value = "SELECT * FROM users WHERE role = cast(:role AS user_role) AND status <> 'archived'", nativeQuery = true)
    Page<User> findByRole(@Param("role") String role, Pageable pageable);

    @Query(value = "SELECT COUNT(*) FROM users WHERE role = cast(:role AS user_role) AND status <> 'archived'", nativeQuery = true)
    long countByRole(@Param("role") String role);

    @Query(value = "SELECT * FROM users WHERE group_id = :groupId AND role = cast(:role AS user_role) AND status <> 'archived'", nativeQuery = true)
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

    /** Returns all archived users. Bypasses @SQLRestriction (per D-02). */
    @Query(value = "SELECT * FROM users WHERE status = 'archived'", nativeQuery = true)
    List<User> findAllArchived();
}
