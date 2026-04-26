package ru.rutcampustrack.shared.web.autoconfigure;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.SpringApplication;
import org.springframework.mock.env.MockEnvironment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.assertThatNoException;

/**
 * M14 G4 v2 — unit tests для {@link RequiredSecretsValidator}.
 *
 * <p>Покрывают:
 * <ul>
 *   <li>missing required vars → IllegalStateException с явным сообщением
 *   <li>blank required vars → trated as missing
 *   <li>all present → no exception
 *   <li>profile=test → skip validation (даже если missing)
 *   <li>profile=local → skip validation
 *   <li>required-env-vars property не задан → no validation (opt-in)
 *   <li>blank required-env-vars → no validation
 *   <li>multiple missing — все указаны в exception message
 * </ul>
 */
class RequiredSecretsValidatorTest {

    private final RequiredSecretsValidator validator = new RequiredSecretsValidator();
    private final SpringApplication app = new SpringApplication();

    @Test
    @DisplayName("missing required var → IllegalStateException с actionable сообщением")
    void missingRequiredVar_throws() {
        MockEnvironment env = new MockEnvironment();
        env.setProperty("rutcampustrack.security.required-env-vars", "REDIS_PASSWORD");
        // REDIS_PASSWORD не задан

        assertThatThrownBy(() -> validator.postProcessEnvironment(env, app))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("M14 G4")
                .hasMessageContaining("CSO HIGH-06")
                .hasMessageContaining("REDIS_PASSWORD")
                .hasMessageContaining("--env-file")
                .hasMessageContaining("@ActiveProfiles(\"test\")");
    }

    @Test
    @DisplayName("blank required var → treated as missing")
    void blankRequiredVar_treatedAsMissing() {
        MockEnvironment env = new MockEnvironment();
        env.setProperty("rutcampustrack.security.required-env-vars", "GRPC_SECRET");
        env.setProperty("GRPC_SECRET", "   ");  // whitespace-only

        assertThatThrownBy(() -> validator.postProcessEnvironment(env, app))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("GRPC_SECRET");
    }

    @Test
    @DisplayName("all required vars present → no exception")
    void allPresent_noException() {
        MockEnvironment env = new MockEnvironment();
        env.setProperty("rutcampustrack.security.required-env-vars",
                "REDIS_PASSWORD,RABBITMQ_PASSWORD,INTERNAL_ISSUER_SECRET");
        env.setProperty("REDIS_PASSWORD", "real-redis-pass");
        env.setProperty("RABBITMQ_PASSWORD", "real-rabbit-pass");
        env.setProperty("INTERNAL_ISSUER_SECRET", "real-internal-secret-32-bytes-or-more");

        assertThatNoException().isThrownBy(() -> validator.postProcessEnvironment(env, app));
    }

    @Test
    @DisplayName("active profile=test → skip validation даже если vars missing")
    void testProfile_skipsValidation() {
        MockEnvironment env = new MockEnvironment();
        env.setActiveProfiles("test");
        env.setProperty("rutcampustrack.security.required-env-vars", "REDIS_PASSWORD");
        // REDIS_PASSWORD не задан — но мы в test profile

        assertThatNoException().isThrownBy(() -> validator.postProcessEnvironment(env, app));
    }

    @Test
    @DisplayName("active profile=local → skip validation даже если vars missing")
    void localProfile_skipsValidation() {
        MockEnvironment env = new MockEnvironment();
        env.setActiveProfiles("local");
        env.setProperty("rutcampustrack.security.required-env-vars", "REDIS_PASSWORD");

        assertThatNoException().isThrownBy(() -> validator.postProcessEnvironment(env, app));
    }

    @Test
    @DisplayName("required-env-vars property не задан → no validation (opt-in mechanism)")
    void noOptIn_noValidation() {
        MockEnvironment env = new MockEnvironment();
        // rutcampustrack.security.required-env-vars не задан

        assertThatNoException().isThrownBy(() -> validator.postProcessEnvironment(env, app));
    }

    @Test
    @DisplayName("blank required-env-vars → no validation")
    void blankRequiredList_noValidation() {
        MockEnvironment env = new MockEnvironment();
        env.setProperty("rutcampustrack.security.required-env-vars", "   ");

        assertThatNoException().isThrownBy(() -> validator.postProcessEnvironment(env, app));
    }

    @Test
    @DisplayName("multiple missing vars — все указаны в exception message")
    void multipleMissing_allInMessage() {
        MockEnvironment env = new MockEnvironment();
        env.setProperty("rutcampustrack.security.required-env-vars",
                "REDIS_PASSWORD,RABBITMQ_PASSWORD,INTERNAL_ISSUER_SECRET");
        env.setProperty("REDIS_PASSWORD", "set");
        // RABBITMQ_PASSWORD и INTERNAL_ISSUER_SECRET — missing

        assertThatThrownBy(() -> validator.postProcessEnvironment(env, app))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("RABBITMQ_PASSWORD")
                .hasMessageContaining("INTERNAL_ISSUER_SECRET")
                .satisfies(ex -> assertThat(ex.getMessage()).doesNotContain("REDIS_PASSWORD,"));
        // REDIS_PASSWORD не должен быть в списке missing (он set)
    }

    @Test
    @DisplayName("CSV с пробелами и пустыми элементами — корректно парсится")
    void csvParsing_robustToWhitespace() {
        MockEnvironment env = new MockEnvironment();
        env.setProperty("rutcampustrack.security.required-env-vars",
                " REDIS_PASSWORD ,  , RABBITMQ_PASSWORD ");
        env.setProperty("REDIS_PASSWORD", "set");
        env.setProperty("RABBITMQ_PASSWORD", "set");

        assertThatNoException().isThrownBy(() -> validator.postProcessEnvironment(env, app));
    }
}
