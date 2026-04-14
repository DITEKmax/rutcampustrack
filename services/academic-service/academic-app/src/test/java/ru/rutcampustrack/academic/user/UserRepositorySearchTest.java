package ru.rutcampustrack.academic.user;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.transaction.annotation.Transactional;
import ru.rutcampustrack.academic.contract.enums.AccountStatus;
import ru.rutcampustrack.academic.contract.enums.UserRole;
import ru.rutcampustrack.academic.entity.User;
import ru.rutcampustrack.academic.integration.AbstractAcademicIntegrationTest;
import ru.rutcampustrack.academic.repository.UserRepository;

import java.time.OffsetDateTime;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration tests for {@link UserSpecifications} against PostgreSQL (Testcontainers).
 *
 * <p>BUG-006 / Plan 58-01. Проверяет case-insensitive LIKE, escape спецсимволов,
 * комбинацию с role-фильтром, null/blank → без ограничений.
 *
 * <p>Каждый тест в @Transactional — откатывается, БД восстанавливается.
 */
@Transactional
class UserRepositorySearchTest extends AbstractAcademicIntegrationTest {

    @Autowired
    private UserRepository userRepository;

    private Long ivanovId;
    private Long petrovId;
    private Long student00001Id;
    private Long percentUserId;

    @BeforeEach
    void seed() {
        ivanovId = insertUser("search_ivanov", "Иванов", "Иван", "Иванович",
                UserRole.STUDENT, 111111111L);
        petrovId = insertUser("search_petrov", "Петров", "Пётр", "Петрович",
                UserRole.TEACHER, 222222222L);
        student00001Id = insertUser("student00001_srch", "Сидоров", "Семён", null,
                UserRole.STUDENT, 123456789L);
        // Спецсимвол "%" в фамилии — проверка escape
        percentUserId = insertUser("search_pct", "Про50%", "Пётр", null,
                UserRole.STUDENT, 999999999L);
    }

    private Long insertUser(String login, String lastName, String firstName,
                            String middleName, UserRole role, Long telegramId) {
        User u = new User();
        u.setLogin(login);
        u.setPasswordHash("x");
        u.setLastName(lastName);
        u.setFirstName(firstName);
        u.setMiddleName(middleName);
        u.setRole(role);
        u.setStatus(AccountStatus.ACTIVE);
        u.setTelegramId(telegramId);
        u.setHeadman(false);
        u.setPasswordChanged(false);
        OffsetDateTime now = OffsetDateTime.now();
        u.setCreatedAt(now);
        u.setUpdatedAt(now);
        return userRepository.save(u).getId();
    }

    @Test
    void matchesSearch_findsByLastName_caseInsensitive() {
        Specification<User> spec = UserSpecifications.matchesSearch("Иван");
        Page<User> page = userRepository.findAll(spec, PageRequest.of(0, 50));

        assertThat(page.getContent())
                .extracting(User::getId)
                .contains(ivanovId)
                .doesNotContain(petrovId);
    }

    @Test
    void matchesSearch_findsByLogin_caseInsensitive() {
        Specification<User> spec = UserSpecifications.matchesSearch("STUDENT00001");
        Page<User> page = userRepository.findAll(spec, PageRequest.of(0, 50));

        assertThat(page.getContent())
                .extracting(User::getId)
                .contains(student00001Id);
    }

    @Test
    void matchesSearch_findsByTelegramIdSubstring() {
        Specification<User> spec = UserSpecifications.matchesSearch("12345");
        Page<User> page = userRepository.findAll(spec, PageRequest.of(0, 50));

        assertThat(page.getContent())
                .extracting(User::getId)
                .contains(student00001Id);
    }

    @Test
    void matchesSearch_blank_returnsAll() {
        Specification<User> empty = UserSpecifications.matchesSearch("");
        Specification<User> nullSpec = UserSpecifications.matchesSearch(null);
        assertThat(empty).isNull();
        assertThat(nullSpec).isNull();

        // Specification.where(null) — без ограничений → хотя бы 4 засидированных видны.
        Page<User> all = userRepository.findAll(
                Specification.where(empty), PageRequest.of(0, 500));
        assertThat(all.getContent()).extracting(User::getId)
                .contains(ivanovId, petrovId, student00001Id, percentUserId);
    }

    @Test
    void matchesSearch_escapesLikeWildcard() {
        // "50%" должен найти пользователя с фамилией "Про50%" (literal),
        // но НЕ работать как wildcard, т.е. "Иван" не должен попасть.
        Specification<User> spec = UserSpecifications.matchesSearch("50%");
        Page<User> page = userRepository.findAll(spec, PageRequest.of(0, 50));

        assertThat(page.getContent())
                .extracting(User::getId)
                .contains(percentUserId)
                .doesNotContain(ivanovId);
    }

    @Test
    void matchesSearch_combinedWithRoleViaAnd() {
        Specification<User> spec = Specification
                .where(UserSpecifications.matchesSearch("Пётр"))
                .and(UserSpecifications.matchesRole(UserRole.STUDENT));
        Page<User> page = userRepository.findAll(spec, PageRequest.of(0, 50));

        // percentUser — STUDENT + firstName="Пётр" → попадает.
        // petrov — TEACHER → не попадает.
        assertThat(page.getContent())
                .extracting(User::getId)
                .contains(percentUserId)
                .doesNotContain(petrovId);
    }

    @Test
    void escapeLike_escapesUnderscoreAndPercentAndBackslash() {
        assertThat(UserSpecifications.escapeLike("abc")).isEqualTo("abc");
        assertThat(UserSpecifications.escapeLike("50%")).isEqualTo("50\\%");
        assertThat(UserSpecifications.escapeLike("a_b")).isEqualTo("a\\_b");
        assertThat(UserSpecifications.escapeLike("x\\y")).isEqualTo("x\\\\y");
    }
}
