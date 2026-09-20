package ru.rutcampustrack.gateway.security;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class InternalIssuerClientPropertiesTest {

    @Test
    void emptySecret_failsFast() {
        InternalIssuerClientProperties props = new InternalIssuerClientProperties();
        assertThatThrownBy(props::validate)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("INTERNAL_ISSUER_SECRET");
    }

    @Test
    void shortSecret_failsFast() {
        InternalIssuerClientProperties props = new InternalIssuerClientProperties();
        props.setSecret("short");
        assertThatThrownBy(props::validate)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("32 bytes");
    }

    @Test
    void nonPositiveTimeout_failsFast() {
        InternalIssuerClientProperties props = validProperties();
        props.setTimeoutMillis(0);
        assertThatThrownBy(props::validate)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("timeout-millis");
    }

    @Test
    void validSecretAndTimeout_passes() {
        assertThatCode(() -> validProperties().validate()).doesNotThrowAnyException();
    }

    @Test
    void defaults_haveNoCacheOrLegacyToggle() {
        InternalIssuerClientProperties props = validProperties();
        assertThat(props.getAuthServiceUrl()).isEqualTo("http://auth-service:9090");
        assertThat(props.getTimeoutMillis()).isEqualTo(3_000);
    }

    private static InternalIssuerClientProperties validProperties() {
        InternalIssuerClientProperties props = new InternalIssuerClientProperties();
        props.setSecret("a".repeat(32));
        return props;
    }
}
