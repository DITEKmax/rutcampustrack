package ru.rutcampustrack.shared.security;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class InternalJwtPropertiesTest {

    @Test
    void defaults_populated() {
        InternalJwtProperties props = new InternalJwtProperties(null, 0, -1, true, null, null, null);

        assertThat(props.authServiceUrl()).isEqualTo("http://auth-service:9090");
        assertThat(props.publicKeyRefreshMinutes()).isEqualTo(60);
        assertThat(props.clockSkewSeconds()).isEqualTo(30);
        assertThat(props.expectedIssuer()).isEqualTo("rutcampustrack-auth");
        assertThat(props.expectedAudience()).isEqualTo("rutcampustrack-internal");
        assertThat(props.headerName()).isEqualTo("X-Internal-Token");
        assertThat(props.legacyHeadersEnabled()).isTrue();
    }

    @Test
    void customValues_preserved() {
        InternalJwtProperties props = new InternalJwtProperties(
                "http://custom:8080", 15, 60, false, "custom-iss", "custom-aud", "X-Custom");

        assertThat(props.authServiceUrl()).isEqualTo("http://custom:8080");
        assertThat(props.publicKeyRefreshMinutes()).isEqualTo(15);
        assertThat(props.clockSkewSeconds()).isEqualTo(60);
        assertThat(props.legacyHeadersEnabled()).isFalse();
        assertThat(props.expectedIssuer()).isEqualTo("custom-iss");
        assertThat(props.expectedAudience()).isEqualTo("custom-aud");
        assertThat(props.headerName()).isEqualTo("X-Custom");
    }
}
