package ru.rutcampustrack.auth.config;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class InternalIssuerPropertiesTest {

    @Test
    void emptySecret_failsFast() {
        InternalIssuerProperties props = new InternalIssuerProperties();
        assertThatThrownBy(props::validate)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("INTERNAL_ISSUER_SECRET");
    }

    @Test
    void blankSecret_failsFast() {
        InternalIssuerProperties props = new InternalIssuerProperties();
        props.setSecret("   ");
        assertThatThrownBy(props::validate)
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void shortSecret_failsFast() {
        InternalIssuerProperties props = new InternalIssuerProperties();
        props.setSecret("short");
        assertThatThrownBy(props::validate)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("at least 32");
    }

    @Test
    void validSecret_passes() {
        InternalIssuerProperties props = new InternalIssuerProperties();
        props.setSecret("a".repeat(32));
        assertThatCode(props::validate).doesNotThrowAnyException();
    }

    @Test
    void ttl_zero_fails() {
        InternalIssuerProperties props = new InternalIssuerProperties();
        props.setSecret("a".repeat(32));
        props.setTokenTtlSeconds(0);
        assertThatThrownBy(props::validate)
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void ttl_over3600_fails() {
        InternalIssuerProperties props = new InternalIssuerProperties();
        props.setSecret("a".repeat(32));
        props.setTokenTtlSeconds(3601);
        assertThatThrownBy(props::validate)
                .isInstanceOf(IllegalStateException.class);
    }
}
