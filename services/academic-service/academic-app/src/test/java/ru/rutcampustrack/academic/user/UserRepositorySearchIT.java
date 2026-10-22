package ru.rutcampustrack.academic.user;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.jpa.domain.Specification;

import ru.rutcampustrack.academic.contract.dto.user.CreateUserRequest;
import ru.rutcampustrack.academic.contract.dto.user.PatchUserRequest;
import ru.rutcampustrack.academic.contract.enums.UserRole;
import ru.rutcampustrack.academic.entity.User;
import ru.rutcampustrack.academic.integration.AbstractAcademicIntegrationTest;
import ru.rutcampustrack.academic.repository.UserRepository;
import ru.rutcampustrack.auth.session.model.AuthRole;

/**
 * Integration tests for {@link UserSpecifications} against PostgreSQL (Testcontainers).
 *
 * <p>BUG-006 / Plan 58-01. Проверяет case-insensitive LIKE, escape спецсимволов, комбинацию с
 * role-фильтром, null/blank → без ограничений.
 *
 * <p>Canonical two-DB fixture resets each test; identities and login allocation use actual Auth.
 */
class UserRepositorySearchIT extends AbstractAcademicIntegrationTest {

    @Autowired private UserRepository userRepository;
    @Autowired private UserService users;

    private Long ivanovId;
    private Long petrovId;
    private Long student00001Id;
    private Long percentUserId;

    @BeforeEach
    void seed() {
        ivanovId =
                insertUser(
                        "search_ivanov",
                        "Иванов",
                        "Иван",
                        "Иванович",
                        UserRole.STUDENT,
                        111111111L);
        petrovId =
                insertUser(
                        "search_petrov",
                        "Петров",
                        "Пётр",
                        "Петрович",
                        UserRole.TEACHER,
                        222222222L);
        student00001Id =
                insertUser(
                        "student00001_srch",
                        "Сидоров",
                        "Семён",
                        null,
                        UserRole.STUDENT,
                        123456789L);
        // Спецсимвол "%" в фамилии — проверка escape
        percentUserId =
                insertUser("search_pct", "Про50%", "Пётр", null, UserRole.STUDENT, 999999999L);
    }

    private Long insertUser(
            String login,
            String lastName,
            String firstName,
            String middleName,
            UserRole role,
            Long telegramId) {
        var actor = identityFixture.select(identityFixture.canonical().adminId(), AuthRole.ADMIN);
        var created =
                identityFixture.seedUser(
                        new CreateUserRequest(
                                login.replace('_', '-'),
                                "Fixture",
                                null,
                                role,
                                role == UserRole.STUDENT
                                        ? identityFixture.canonical().groupId()
                                        : null,
                                role == UserRole.TEACHER ? "EMP-" + login : null,
                                telegramId),
                        actor);
        // Auth's generated login keeps the search-only stem after the actual profile rename.
        try (var ignored = identityFixture.request(actor)) {
            users.patchUser(
                    created.userId(),
                    new PatchUserRequest(
                            lastName,
                            firstName,
                            middleName == null ? "" : middleName,
                            null,
                            null,
                            null,
                            null,
                            null));
        }
        return created.userId();
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

        assertThat(page.getContent()).extracting(User::getId).contains(student00001Id);
    }

    @Test
    void matchesSearch_findsByTelegramIdSubstring() {
        Specification<User> spec = UserSpecifications.matchesSearch("12345");
        Page<User> page = userRepository.findAll(spec, PageRequest.of(0, 50));

        assertThat(page.getContent()).extracting(User::getId).contains(student00001Id);
    }

    @Test
    void matchesSearch_blank_returnsAll() {
        var expected =
                java.util.Set.of(
                        ivanovId,
                        petrovId,
                        student00001Id,
                        percentUserId,
                        identityFixture.canonical().adminId(),
                        identityFixture.canonical().studentId(),
                        identityFixture.canonical().teacherId());
        for (String search : new String[] {null, "", "   "}) {
            var spec =
                    Specification.where(UserSpecifications.matchesSearch(search))
                            .and(UserSpecifications.matchesRole(null))
                            .and(UserSpecifications.matchesStatus(null));
            var page = userRepository.findAll(spec, PageRequest.of(0, 500));
            assertThat(page.getTotalElements()).isEqualTo(expected.size());
            assertThat(page.getContent())
                    .extracting(User::getId)
                    .containsExactlyInAnyOrderElementsOf(expected);
        }
        for (Specification<User> absent :
                java.util.Arrays.asList(
                        UserSpecifications.matchesRole(null),
                        UserSpecifications.matchesStatus(null))) {
            assertThat(
                            userRepository
                                    .findAll(Specification.where(absent), PageRequest.of(0, 500))
                                    .getContent())
                    .extracting(User::getId)
                    .containsExactlyInAnyOrderElementsOf(expected);
        }
    }

    @Test
    void matchesSearch_escapesLikeWildcard() {
        Long wildcardId =
                insertUser(
                        "search_wildcard",
                        "Про500",
                        "Контроль",
                        null,
                        UserRole.STUDENT,
                        888888888L);
        // "50%" должен найти пользователя с фамилией "Про50%" (literal),
        // но НЕ работать как wildcard, т.е. "Иван" не должен попасть.
        Specification<User> spec = UserSpecifications.matchesSearch("50%");
        Page<User> page = userRepository.findAll(spec, PageRequest.of(0, 50));

        assertThat(page.getContent())
                .extracting(User::getId)
                .contains(percentUserId)
                .doesNotContain(ivanovId)
                .doesNotContain(wildcardId);
    }

    @Test
    void matchesSearch_combinedWithRoleViaAnd() {
        Specification<User> spec =
                Specification.where(UserSpecifications.matchesSearch("Пётр"))
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
        long underscore =
                insertUser(
                        "literal_underscore", "a_b", "Literal", null, UserRole.STUDENT, 999999991L);
        insertUser("wild_underscore", "aXb", "Control", null, UserRole.STUDENT, 999999992L);
        long slash =
                insertUser("literal_slash", "x\\y", "Literal", null, UserRole.STUDENT, 999999993L);
        insertUser("plain_slash", "xy", "Control", null, UserRole.STUDENT, 999999994L);
        long plain =
                insertUser(
                        "plain_text", "PlainToken", "Literal", null, UserRole.STUDENT, 999999995L);
        for (var vector :
                java.util.Map.of("a_b", underscore, "x\\y", slash, "PlainToken", plain)
                        .entrySet()) {
            var result =
                    userRepository.findAll(
                            UserSpecifications.matchesSearch(vector.getKey()),
                            PageRequest.of(0, 50));
            assertThat(result.getTotalElements()).isEqualTo(1);
            assertThat(result.getContent())
                    .extracting(User::getId)
                    .containsExactly(vector.getValue());
        }
    }
}
