package ru.rutcampustrack.gateway.config;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;

import com.github.tomakehurst.wiremock.WireMockServer;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.PublicKey;
import java.time.Duration;
import java.util.Base64;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;
import reactor.core.publisher.Sinks;

class PublicKeyConfigTest {

    // Test 1: parsePemPublicKey correctly parses a valid PEM-encoded RSA public key
    @Test
    void parsePemPublicKey_validPem_returnsPublicKey() throws Exception {
        KeyPairGenerator gen = KeyPairGenerator.getInstance("RSA");
        gen.initialize(2048);
        KeyPair keyPair = gen.generateKeyPair();

        byte[] encoded = keyPair.getPublic().getEncoded();
        String base64 = Base64.getMimeEncoder(64, new byte[] {'\n'}).encodeToString(encoded);
        String pem = "-----BEGIN PUBLIC KEY-----\n" + base64 + "\n-----END PUBLIC KEY-----\n";

        PublicKeyConfig config = new PublicKeyConfig();
        var result = config.parsePemPublicKey(pem);

        assertThat(result).isNotNull();
        assertThat(result).isEqualTo(keyPair.getPublic());
    }

    // Test 2: getPublicKey throws IllegalStateException when no key has been loaded
    @Test
    void getPublicKey_whenKeyNotLoaded_throwsIllegalStateException() {
        PublicKeyConfig config = new PublicKeyConfig();

        assertThatThrownBy(config::getPublicKey)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Public key not yet loaded");
    }

    @Test
    void coldStartFailureRecoversOnBoundedRetryAndSuccessfulFetchesRemainHourly() throws Exception {
        WireMockServer server = new WireMockServer(wireMockConfig().dynamicPort());
        server.start();
        try {
            AtomicLong time = new AtomicLong();
            PublicKeyConfig config =
                    new PublicKeyConfig(WebClient.create(), server.baseUrl(), time::get);
            server.stubFor(
                    get(urlEqualTo("/auth/public-key")).willReturn(aResponse().withStatus(503)));
            config.init();
            assertThat(config.isReady()).isFalse();
            assertThatThrownBy(config::getPublicKey).isInstanceOf(IllegalStateException.class);

            PublicKey first = rsaKey();
            serveKey(server, keyResponse(first));
            time.set(Duration.ofSeconds(29).toNanos());
            config.refresh();
            server.verify(1, getRequestedFor(urlEqualTo("/auth/public-key")));
            time.set(Duration.ofSeconds(30).toNanos());
            config.refresh();
            assertThat(config.isReady()).isTrue();
            assertThat(config.getPublicKey()).isEqualTo(first);

            PublicKey rotated = rsaKey();
            serveKey(server, keyResponse(rotated));
            time.addAndGet(Duration.ofHours(1).minusSeconds(1).toNanos());
            config.refresh();
            server.verify(2, getRequestedFor(urlEqualTo("/auth/public-key")));
            assertThat(config.getPublicKey()).isEqualTo(first);
            time.addAndGet(Duration.ofSeconds(1).toNanos());
            config.refresh();
            assertThat(config.getPublicKey()).isEqualTo(rotated);
            server.verify(3, getRequestedFor(urlEqualTo("/auth/public-key")));
        } finally {
            server.stop();
        }
    }

    @Test
    void invalidReplacementKeepsLastGoodKeyAndRetriesThirtySecondsAfterCompletion()
            throws Exception {
        WireMockServer server = new WireMockServer(wireMockConfig().dynamicPort());
        server.start();
        try {
            AtomicLong time = new AtomicLong();
            PublicKeyConfig config =
                    new PublicKeyConfig(WebClient.create(), server.baseUrl(), time::get);
            PublicKey first = rsaKey();
            serveKey(server, keyResponse(first));
            config.init();

            time.addAndGet(Duration.ofHours(1).toNanos());
            serveKey(server, "{\"publicKey\":\"invalid-PEM\",\"algorithm\":\"RSA\"}");
            config.refresh();
            assertThat(config.isReady()).isTrue();
            assertThat(config.getPublicKey()).isEqualTo(first);

            PublicKey replacement = rsaKey();
            serveKey(server, keyResponse(replacement));
            time.addAndGet(Duration.ofSeconds(29).toNanos());
            config.refresh();
            server.verify(2, getRequestedFor(urlEqualTo("/auth/public-key")));
            time.addAndGet(Duration.ofSeconds(1).toNanos());
            config.refresh();
            assertThat(config.getPublicKey()).isEqualTo(replacement);
        } finally {
            server.stop();
        }
    }

    @Test
    void overlappingRefreshIsSkippedAndDeadlineStartsWhenFetchCompletes() throws Exception {
        AtomicLong time = new AtomicLong();
        AtomicInteger requests = new AtomicInteger();
        CountDownLatch started = new CountDownLatch(1);
        Sinks.One<ClientResponse> response = Sinks.one();
        WebClient client =
                WebClient.builder()
                        .exchangeFunction(
                                request ->
                                        Mono.defer(
                                                () -> {
                                                    requests.incrementAndGet();
                                                    started.countDown();
                                                    return response.asMono();
                                                }))
                        .build();
        PublicKeyConfig config = new PublicKeyConfig(client, "http://auth-test", time::get);
        var executor = Executors.newSingleThreadExecutor();
        try {
            var initialFetch = executor.submit(config::init);
            assertThat(started.await(5, TimeUnit.SECONDS)).isTrue();
            assertTimeoutPreemptively(Duration.ofSeconds(1), config::refresh);
            assertThat(requests).hasValue(1);
            time.set(Duration.ofSeconds(30).toNanos());
            response.tryEmitValue(ClientResponse.create(HttpStatus.SERVICE_UNAVAILABLE).build());
            initialFetch.get(5, TimeUnit.SECONDS);
            time.set(Duration.ofSeconds(59).toNanos());
            config.refresh();
            assertThat(requests).hasValue(1);
            time.set(Duration.ofSeconds(60).toNanos());
            config.refresh();
            assertThat(requests).hasValue(2);
            assertThat(config.isReady()).isFalse();
        } finally {
            executor.shutdownNow();
        }
    }

    private static PublicKey rsaKey() throws Exception {
        KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(2048);
        return generator.generateKeyPair().getPublic();
    }

    private static String keyResponse(PublicKey key) {
        String encoded = Base64.getEncoder().encodeToString(key.getEncoded());
        return "{\"publicKey\":\"-----BEGIN PUBLIC KEY-----\\n"
                + encoded
                + "\\n-----END PUBLIC KEY-----\",\"algorithm\":\"RSA\"}";
    }

    private static void serveKey(WireMockServer server, String body) {
        server.stubFor(
                get(urlEqualTo("/auth/public-key"))
                        .willReturn(
                                aResponse()
                                        .withStatus(200)
                                        .withHeader("Content-Type", "application/json")
                                        .withBody(body)));
    }
}
