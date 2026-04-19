package ru.rutcampustrack.shared.security;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class InternalJwtValidatorTest {

    private InternalJwtTestFactory factory;
    private InternalJwtValidator validator;

    @BeforeEach
    void setUp() {
        factory = new InternalJwtTestFactory();
        InternalJwtProperties props = new InternalJwtProperties(
                null, 0, 0, true, null, null, null);
        PublicKeyProvider keyProvider = new PublicKeyProvider(props) {
            @Override
            public java.security.PublicKey getPublicKey() {
                return factory.publicKey();
            }
        };
        validator = new InternalJwtValidator(keyProvider, props);
    }

    @Test
    void validToken_returnsClaims() {
        String token = factory.validToken(42L, "ADMIN", 7L, true);

        InternalJwtClaims claims = validator.validate(token);

        assertThat(claims.userId()).isEqualTo(42L);
        assertThat(claims.role()).isEqualTo("ADMIN");
        assertThat(claims.groupId()).isEqualTo(7L);
        assertThat(claims.isHeadman()).isTrue();
    }

    @Test
    void validToken_nullGroup_returnsNullGroup() {
        String token = factory.validToken(1L, "ADMIN", null, false);

        InternalJwtClaims claims = validator.validate(token);

        assertThat(claims.groupId()).isNull();
        assertThat(claims.isHeadman()).isFalse();
    }

    @Test
    void expiredToken_throws() {
        String token = factory.expiredToken(1L, "STUDENT");

        assertThatThrownBy(() -> validator.validate(token))
                .isInstanceOf(InternalJwtException.class)
                .hasMessageContaining("expired");
    }

    @Test
    void invalidSignature_throws() {
        String token = factory.invalidSignature(1L, "STUDENT");

        assertThatThrownBy(() -> validator.validate(token))
                .isInstanceOf(InternalJwtException.class);
    }

    @Test
    void wrongIssuer_throws() {
        String token = factory.wrongIssuer(1L, "STUDENT");

        assertThatThrownBy(() -> validator.validate(token))
                .isInstanceOf(InternalJwtException.class);
    }

    @Test
    void wrongAudience_throws() {
        String token = factory.wrongAudience(1L, "STUDENT");

        assertThatThrownBy(() -> validator.validate(token))
                .isInstanceOf(InternalJwtException.class);
    }

    @Test
    void missingRole_throws() {
        String token = factory.missingRole(1L);

        assertThatThrownBy(() -> validator.validate(token))
                .isInstanceOf(InternalJwtException.class)
                .hasMessageContaining("role");
    }

    @Test
    void blankToken_throws() {
        assertThatThrownBy(() -> validator.validate(""))
                .isInstanceOf(InternalJwtException.class)
                .hasMessageContaining("missing");
    }

    @Test
    void nullToken_throws() {
        assertThatThrownBy(() -> validator.validate(null))
                .isInstanceOf(InternalJwtException.class);
    }
}
