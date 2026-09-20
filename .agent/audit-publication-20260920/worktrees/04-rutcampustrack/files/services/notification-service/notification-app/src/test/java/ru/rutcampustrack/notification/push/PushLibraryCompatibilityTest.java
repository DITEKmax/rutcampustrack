package ru.rutcampustrack.notification.push;

import com.sun.net.httpserver.Headers;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import nl.martijndwars.webpush.Notification;
import nl.martijndwars.webpush.PushService;
import nl.martijndwars.webpush.Utils;
import org.apache.http.HttpResponse;
import org.apache.http.util.EntityUtils;
import org.bouncycastle.jce.interfaces.ECPublicKey;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.jose4j.jws.JsonWebSignature;
import org.jose4j.jwt.JwtClaims;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.Security;
import java.security.spec.ECGenParameterSpec;
import java.util.Base64;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Verifies the real web-push 5.1.2 request path against a task-owned endpoint.
 *
 * <p>The delivery service unit suite mocks {@link PushService}, so it cannot
 * detect a jose4j/Apache HttpClient incompatibility. This test keeps all key
 * material in memory and sends only to a loopback {@link HttpServer}.</p>
 */
class PushLibraryCompatibilityTest {

    private static final String SUBJECT = "mailto:push-test@example.test";
    private static final byte[] AUTH = {
            0x01, 0x02, 0x03, 0x04, 0x05, 0x06, 0x07, 0x08,
            0x09, 0x0a, 0x0b, 0x0c, 0x0d, 0x0e, 0x0f, 0x10
    };
    private static final byte[] PAYLOAD = "{\"title\":\"Тест\",\"body\":\"loopback\"}"
            .getBytes(StandardCharsets.UTF_8);

    @BeforeAll
    static void installBouncyCastle() {
        if (Security.getProvider("BC") == null) {
            Security.addProvider(new BouncyCastleProvider());
        }
    }

    @Test
    void sendEncryptsAndSignsRequestAndReturnsEndpointResponse() throws Exception {
        KeyPair vapidKeyPair = ecKeyPair("ECDSA");
        KeyPair subscriptionKeyPair = ecKeyPair("ECDH");
        AtomicReference<CapturedRequest> captured = new AtomicReference<>();
        ExecutorService executor = Executors.newSingleThreadExecutor();
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.setExecutor(executor);
        server.createContext("/push", exchange -> capture(exchange, captured));
        server.start();

        String origin = "http://127.0.0.1:" + server.getAddress().getPort();
        String endpoint = origin + "/push";
        try {
            PushService pushService = new PushService(vapidKeyPair, SUBJECT);
            Notification notification = new Notification(
                    endpoint,
                    subscriptionKeyPair.getPublic(),
                    AUTH,
                    PAYLOAD,
                    60);

            HttpResponse response = pushService.send(notification);

            assertThat(response.getStatusLine().getStatusCode()).isEqualTo(201);
            assertThat(response.getFirstHeader("X-Push-Test").getValue()).isEqualTo("accepted");
            assertThat(EntityUtils.toString(response.getEntity(), StandardCharsets.UTF_8))
                    .isEqualTo("accepted");

            CapturedRequest request = captured.get();
            assertThat(request).isNotNull();
            assertThat(request.method()).isEqualTo("POST");
            assertThat(request.path()).isEqualTo("/push");
            assertThat(request.body()).isNotEmpty();
            assertThat(request.body().length).isGreaterThan(PAYLOAD.length);
            assertThat(new String(request.body(), StandardCharsets.UTF_8)).doesNotContain("loopback");

            Headers headers = request.headers();
            assertThat(headers.getFirst("TTL")).isEqualTo("60");
            assertThat(headers.getFirst("Content-Encoding")).isEqualTo("aesgcm");
            assertThat(headers.getFirst("Content-Type")).isEqualTo("application/octet-stream");
            assertThat(headers.getFirst("Encryption")).startsWith("salt=");
            assertThat(Base64.getUrlDecoder().decode(parameter(headers.getFirst("Encryption"), "salt")))
                    .hasSize(16);

            String cryptoKey = headers.getFirst("Crypto-Key");
            assertThat(cryptoKey).startsWith("dh=").contains(";p256ecdsa=");
            String dh = parameter(cryptoKey, "dh");
            String p256ecdsa = parameter(cryptoKey, "p256ecdsa");
            assertThat(Base64.getUrlDecoder().decode(dh)).hasSize(65);
            assertThat(Base64.getUrlDecoder().decode(p256ecdsa))
                    .containsExactly(Utils.encode((ECPublicKey) vapidKeyPair.getPublic()));

            String authorization = headers.getFirst("Authorization");
            assertThat(authorization).startsWith("WebPush ");
            String token = authorization.substring("WebPush ".length());

            JsonWebSignature signature = new JsonWebSignature();
            signature.setCompactSerialization(token);
            signature.setKey(vapidKeyPair.getPublic());
            assertThat(signature.getAlgorithmHeaderValue()).isEqualTo("ES256");
            assertThat(signature.verifySignature()).isTrue();
            JwtClaims claims = JwtClaims.parse(signature.getUnverifiedPayload());
            URI endpointUri = URI.create(endpoint);
            assertThat(claims.getAudience()).containsExactly(
                    endpointUri.getScheme() + "://" + endpointUri.getHost());
            assertThat(claims.getSubject()).isEqualTo(SUBJECT);
        } finally {
            server.stop(0);
            executor.shutdownNow();
        }
    }

    private static KeyPair ecKeyPair(String algorithm) throws GeneralSecurityException {
        KeyPairGenerator generator = KeyPairGenerator.getInstance(algorithm, "BC");
        generator.initialize(new ECGenParameterSpec("prime256v1"));
        return generator.generateKeyPair();
    }

    private static String parameter(String header, String name) {
        String prefix = name + "=";
        for (String part : header.split(";")) {
            if (part.startsWith(prefix)) {
                return part.substring(prefix.length());
            }
        }
        throw new AssertionError("Missing " + name + " in Crypto-Key: " + header);
    }

    private static void capture(HttpExchange exchange,
                                AtomicReference<CapturedRequest> captured) throws IOException {
        byte[] body;
        try (exchange) {
            body = exchange.getRequestBody().readAllBytes();
            captured.set(new CapturedRequest(
                    exchange.getRequestMethod(),
                    exchange.getRequestURI().getPath(),
                    exchange.getRequestHeaders(),
                    body));
            exchange.getResponseHeaders().add("X-Push-Test", "accepted");
            byte[] response = "accepted".getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(201, response.length);
            try (OutputStream output = exchange.getResponseBody()) {
                output.write(response);
            }
        }
    }

    private record CapturedRequest(String method, String path, Headers headers, byte[] body) {
    }

}
