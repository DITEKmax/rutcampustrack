package ru.rutcampustrack.notification.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.util.Arrays;
import java.util.Base64;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import ru.rutcampustrack.auth.dto.PublicKeyResponse;
import ru.rutcampustrack.shared.security.InternalJwtClaims;
import ru.rutcampustrack.shared.security.InternalJwtException;
import ru.rutcampustrack.shared.security.InternalJwtProperties;
import ru.rutcampustrack.shared.security.InternalJwtTestFactory;
import ru.rutcampustrack.shared.security.InternalJwtValidator;
import ru.rutcampustrack.shared.security.PublicKeyProvider;

@Timeout(10)
class PublicKeyProvisioningTest {

    private static final UUID SESSION_ID = UUID.fromString("11111111-1111-4111-8111-111111111111");

    private final AtomicInteger responseStatus = new AtomicInteger(200);
    private HttpServer server;
    private InternalJwtTestFactory signer;
    private PublicKeyProvider provider;
    private InternalJwtValidator validator;

    @BeforeEach
    void setUp() throws Exception {
        signer = new InternalJwtTestFactory();
        String publicPem =
                "-----BEGIN PUBLIC KEY-----\n"
                        + Base64.getMimeEncoder(64, new byte[] {'\n'})
                                .encodeToString(signer.publicKey().getEncoded())
                        + "\n-----END PUBLIC KEY-----\n";
        byte[] publicResponse =
                new ObjectMapper().writeValueAsBytes(new PublicKeyResponse(publicPem, "RS256"));
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext(
                "/auth/public-key",
                exchange -> {
                    try (exchange) {
                        int status = responseStatus.get();
                        exchange.getResponseHeaders().set("Content-Type", "application/json");
                        exchange.sendResponseHeaders(
                                status, status == 200 ? publicResponse.length : -1);
                        if (status == 200) {
                            exchange.getResponseBody().write(publicResponse);
                        }
                    }
                });
        server.start();
        InternalJwtProperties properties =
                new InternalJwtProperties(
                        "http://127.0.0.1:" + server.getAddress().getPort(),
                        60,
                        0,
                        false,
                        null,
                        null,
                        null);
        InternalJwtConfig configuration = new InternalJwtConfig();
        provider = configuration.publicKeyProvider(properties);
        validator = configuration.internalJwtValidator(provider, properties);
    }

    @AfterEach
    void stopServer() {
        if (server != null) {
            server.stop(0);
        }
    }

    @Test
    void publicHttpKeyVerifiesOriginalSignerAndRejectsIndependentSigner() {
        provider.init();

        assertSamePublicKey();
        assertThat(validator.validate(token(signer)))
                .isEqualTo(
                        new InternalJwtClaims(
                                42L, SESSION_ID, 3L, 7L, "STUDENT", "ACTIVE", 9L, false, false));
        assertThrows(
                InternalJwtException.class,
                () -> validator.validate(token(new InternalJwtTestFactory())));
    }

    @Test
    void coldHttpFailureDeniesUntilRecoveryAndRefreshFailureKeepsFetchedKey() {
        responseStatus.set(503);
        provider.init();
        assertThrows(IllegalStateException.class, () -> validator.validate(token(signer)));

        responseStatus.set(200);
        assertThat(validator.validate(token(signer)).userId()).isEqualTo(42L);
        assertSamePublicKey();

        responseStatus.set(503);
        provider.refresh();
        assertThat(validator.validate(token(signer)).userId()).isEqualTo(42L);
        assertSamePublicKey();
    }

    private void assertSamePublicKey() {
        // A failed assertion reports a boolean, never key bytes or a compact token.
        assertThat(
                        Arrays.equals(
                                provider.getPublicKey().getEncoded(),
                                signer.publicKey().getEncoded()))
                .isTrue();
    }

    private static String token(InternalJwtTestFactory factory) {
        return factory.validToken(42L, SESSION_ID, 3L, 7L, "STUDENT", "ACTIVE", 9L, false, false);
    }
}
