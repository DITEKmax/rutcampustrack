package ru.rutcampustrack.academic.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import ru.rutcampustrack.academic.contract.enums.UserRole;
import ru.rutcampustrack.academic.entity.User;
import java.util.List;
import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Long> {

    Optional<User> findByLogin(String login);

    Optional<User> findByTelegramId(Long telegramId);

    Optional<User> findByEmployeeNumber(String employeeNumber);

    List<User> findByGroupId(Long groupId);
    Page<User> findByGroupId(Long groupId, Pageable pageable);

    Page<User> findByRole(UserRole role, Pageable pageable);

    Page<User> findByGroupIdAndRole(Long groupId, UserRole role, Pageable pageable);

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
