package ru.rutcampustrack.auth.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;
import ru.rutcampustrack.auth.entity.User;

import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Long> {
    Optional<User> findByLogin(String login);
    Optional<User> findByTelegramId(Long telegramId);

    @Transactional
    @Modifying
    @Query(value = "UPDATE users SET password_hash = :hash, password_changed = true, initial_password = NULL WHERE id = :id",
           nativeQuery = true)
    void updatePassword(@Param("id") Long id, @Param("hash") String hash);
}
