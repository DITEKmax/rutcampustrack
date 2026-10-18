package ru.rutcampustrack.auth.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import ru.rutcampustrack.auth.entity.User;

import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Long> {
    @Query(
            value =
                    "SELECT * FROM auth_identities WHERE login = :login AND account_state ="
                            + " 'ACTIVE' AND fence_operation_id IS NULL",
            nativeQuery = true)
    Optional<User> findByLogin(@Param("login") String login);

    @Query(
            value =
                    "SELECT * FROM auth_identities WHERE telegram_id = :telegramId AND"
                            + " account_state = 'ACTIVE' AND fence_operation_id IS NULL",
            nativeQuery = true)
    Optional<User> findByTelegramId(@Param("telegramId") Long telegramId);

    @Override
    @Query(
            value =
                    "SELECT * FROM auth_identities WHERE id = :id AND account_state = 'ACTIVE' AND"
                            + " fence_operation_id IS NULL",
            nativeQuery = true)
    Optional<User> findById(@Param("id") Long id);

    @Override
    @Query(
            value =
                    "SELECT EXISTS(SELECT 1 FROM auth_identities WHERE id = :id AND account_state ="
                            + " 'ACTIVE' AND fence_operation_id IS NULL)",
            nativeQuery = true)
    boolean existsById(@Param("id") Long id);

    @Query(
            value =
                    "SELECT EXISTS(SELECT 1 FROM user_role_grants WHERE user_id = :id AND role ="
                            + " 'admin' AND status = 'active')",
            nativeQuery = true)
    boolean hasActiveAdminGrant(@Param("id") Long id);

    @Transactional
    @Modifying
    @Query(
            value =
                    "UPDATE auth_identities SET password_hash = :hash, password_changed = true,"
                        + " initial_password = NULL, generated_password_origin = NULL WHERE id ="
                        + " :id AND account_state = 'ACTIVE' AND fence_operation_id IS NULL",
            nativeQuery = true)
    void updatePassword(@Param("id") Long id, @Param("hash") String hash);
}
