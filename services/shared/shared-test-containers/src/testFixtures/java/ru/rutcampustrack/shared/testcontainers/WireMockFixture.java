package ru.rutcampustrack.shared.testcontainers;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;

/**
 * WireMock fixture (P2-8/2): локальный HTTP-mock для внешних сервисов
 * (Telegram API, FCM, …). Запускается на динамическом порту, чтобы не
 * конфликтовать с параллельными тестами.
 *
 * <p>Использование:
 * <pre>{@code
 *   WireMockFixture wm = new WireMockFixture();
 *   wm.start();
 *   wm.server().stubFor(get(urlEqualTo("/api"))
 *           .willReturn(aResponse().withStatus(200)));
 *   String url = wm.baseUrl();  // http://localhost:<port>
 *   // ...
 *   wm.stop();
 * }</pre>
 */
public class WireMockFixture implements AutoCloseable {

    private final WireMockServer server;

    public WireMockFixture() {
        this.server = new WireMockServer(WireMockConfiguration.options().dynamicPort());
    }

    public void start() {
        if (!server.isRunning()) {
            server.start();
        }
    }

    public void stop() {
        if (server.isRunning()) {
            server.stop();
        }
    }

    /** Прямой доступ к WireMockServer для stubFor(), verify(), ... */
    public WireMockServer server() {
        return server;
    }

    /** Базовый URL вида {@code http://localhost:<dynamic-port>}. */
    public String baseUrl() {
        return server.baseUrl();
    }

    public int port() {
        return server.port();
    }

    @Override
    public void close() {
        stop();
    }
}
