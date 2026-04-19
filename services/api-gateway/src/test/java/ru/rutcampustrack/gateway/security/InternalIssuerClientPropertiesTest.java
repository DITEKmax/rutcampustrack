package ru.rutcampustrack.gateway.security;

import org.junit.jupiter.api.Test;

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
    void validSecret_passes() {
        InternalIssuerClientProperties props = new InternalIssuerClientProperties();
        props.setSecret("a".repeat(32));
        assertThatCode(props::validate).doesNotThrowAnyException();
    }

    @Test
    void cacheTtl_over290_fails() {
        InternalIssuerClientProperties props = new InternalIssuerClientProperties();
        props.setSecret("a".repeat(32));
        props.setCacheTtlSeconds(300);
        assertThatThrownBy(props::validate)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("290");
    }

    @Test
    void cacheTtl_zero_fails() {
        InternalIssuerClientProperties props = new InternalIssuerClientProperties();
        props.setSecret("a".repeat(32));
        props.setCacheTtlSeconds(0);
        assertThatThrownBy(props::validate)
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void defaults_reasonable() {
        InternalIssuerClientProperties props = new InternalIssuerClientProperties();
        assertThatCode(() -> {
            props.setSecret("a".repeat(32));
            props.validate();
        }).doesNotThrowAnyException();
        assert props.getCacheTtlSeconds() == 240;
        assert props.getCacheMaxSize() == 10_000;
        assert props.getTimeoutMillis() == 3_000;
        assert props.getAuthServiceUrl().equals("http://auth-service:9090");
    }
}
