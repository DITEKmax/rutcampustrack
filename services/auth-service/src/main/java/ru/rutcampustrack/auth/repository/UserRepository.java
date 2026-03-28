package ru.rutcampustrack.auth.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import ru.rutcampustrack.auth.entity.User;

import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Long> {
    Optional<User> findByLogin(String login);
    Optional<User> findByTelegramId(Long telegramId);
}
