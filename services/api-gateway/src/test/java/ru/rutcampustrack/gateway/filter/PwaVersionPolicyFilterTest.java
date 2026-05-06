package ru.rutcampustrack.gateway.filter;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class PwaVersionPolicyFilterTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void disabledPolicy_passesThrough() {
        PwaVersionPolicyFilter filter = new PwaVersionPolicyFilter(props(false, "new-sha", true), objectMapper);
        var exchange = exchangeWithVersion("old-sha");
        var chain = mock(GatewayFilterChain.class);
        when(chain.filter(any())).thenReturn(Mono.empty());

        filter.filter(exchange, chain).block();

        verify(chain).filter(any());
        assertThat(exchange.getResponse().getStatusCode()).isNull();
    }

    @Test
    void missingPwaVersionHeader_passesThrough() {
        PwaVersionPolicyFilter filter = new PwaVersionPolicyFilter(props(true, "new-sha", true), objectMapper);
        var request = MockServerHttpRequest.get("/api/academic/groups").build();
        var exchange = MockServerWebExchange.from(request);
        var chain = mock(GatewayFilterChain.class);
        when(chain.filter(any())).thenReturn(Mono.empty());

        filter.filter(exchange, chain).block();

        verify(chain).filter(any());
        assertThat(exchange.getResponse().getStatusCode()).isNull();
    }

    @Test
    void forcePolicy_blocksAnyNonLatestCommitVersion() {
        PwaVersionPolicyFilter filter = new PwaVersionPolicyFilter(props(true, "1-new-sha", true), objectMapper);
        var exchange = exchangeWithVersion("1-old-sha");
        var chain = mock(GatewayFilterChain.class);

        filter.filter(exchange, chain).block();

        verify(chain, never()).filter(any());
        assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.UPGRADE_REQUIRED);
        assertThat(exchange.getResponse().getHeaders().getContentType()).isEqualTo(MediaType.APPLICATION_PROBLEM_JSON);
        assertThat(exchange.getResponse().getHeaders().getCacheControl()).isEqualTo("no-store");
        assertThat(exchange.getResponse().getHeaders().getFirst(PwaVersionPolicyFilter.PWA_LATEST_HEADER))
                .isEqualTo("1-new-sha");
        assertThat(exchange.getResponse().getHeaders().getFirst(PwaVersionPolicyFilter.PWA_MINIMUM_SUPPORTED_HEADER))
                .isEqualTo("1-new-sha");
        assertThat(exchange.getResponse().getBodyAsString().block())
                .contains("\"status\":426")
                .contains("\"latest\":\"1-new-sha\"");
    }

    @Test
    void forcePolicy_allowsExactLatestVersion() {
        PwaVersionPolicyFilter filter = new PwaVersionPolicyFilter(props(true, "1-new-sha", true), objectMapper);
        var exchange = exchangeWithVersion("1-new-sha");
        var chain = mock(GatewayFilterChain.class);
        ArgumentCaptor<ServerWebExchange> captor = ArgumentCaptor.forClass(ServerWebExchange.class);
        when(chain.filter(captor.capture())).thenReturn(Mono.empty());

        filter.filter(exchange, chain).block();

        verify(chain).filter(any());
        assertThat(captor.getValue().getRequest().getHeaders().getFirst(PwaVersionPolicyFilter.PWA_VERSION_HEADER))
                .isEqualTo("1-new-sha");
    }

    @Test
    void forcePolicy_doesNotApplyMinimumWhenLatestMatchesExactly() {
        PwaVersionPolicyProperties props = props(true, "1-new-sha", true);
        props.setMinimumSupported("2.0.0");
        PwaVersionPolicyFilter filter = new PwaVersionPolicyFilter(props, objectMapper);
        var exchange = exchangeWithVersion("1-new-sha");
        var chain = mock(GatewayFilterChain.class);
        when(chain.filter(any())).thenReturn(Mono.empty());

        filter.filter(exchange, chain).block();

        verify(chain).filter(any());
    }

    @Test
    void minimumSupportedVersion_blocksOlderSemanticVersionWithoutForce() {
        PwaVersionPolicyProperties props = props(true, "1.2.5", false);
        props.setMinimumSupported("1.2.4");
        PwaVersionPolicyFilter filter = new PwaVersionPolicyFilter(props, objectMapper);
        var exchange = exchangeWithVersion("1.2.3");
        var chain = mock(GatewayFilterChain.class);

        filter.filter(exchange, chain).block();

        verify(chain, never()).filter(any());
        assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.UPGRADE_REQUIRED);
        assertThat(exchange.getResponse().getHeaders().getFirst(PwaVersionPolicyFilter.PWA_MINIMUM_SUPPORTED_HEADER))
                .isEqualTo("1.2.4");
    }

    @Test
    void optionsRequest_passesThrough() {
        PwaVersionPolicyFilter filter = new PwaVersionPolicyFilter(props(true, "1-new-sha", true), objectMapper);
        var request = MockServerHttpRequest.method(HttpMethod.OPTIONS, "/api/academic/groups")
                .header(PwaVersionPolicyFilter.PWA_VERSION_HEADER, "1-old-sha")
                .build();
        var exchange = MockServerWebExchange.from(request);
        var chain = mock(GatewayFilterChain.class);
        when(chain.filter(any())).thenReturn(Mono.empty());

        filter.filter(exchange, chain).block();

        verify(chain).filter(any());
        assertThat(exchange.getResponse().getStatusCode()).isNull();
    }

    @Test
    void nonApiRequest_passesThrough() {
        PwaVersionPolicyFilter filter = new PwaVersionPolicyFilter(props(true, "1-new-sha", true), objectMapper);
        var request = MockServerHttpRequest.get("/actuator/health")
                .header(PwaVersionPolicyFilter.PWA_VERSION_HEADER, "1-old-sha")
                .build();
        var exchange = MockServerWebExchange.from(request);
        var chain = mock(GatewayFilterChain.class);
        when(chain.filter(any())).thenReturn(Mono.empty());

        filter.filter(exchange, chain).block();

        verify(chain).filter(any());
    }

    @Test
    void orderRunsBeforeJwtAuth() {
        PwaVersionPolicyFilter filter = new PwaVersionPolicyFilter(props(true, "1-new-sha", true), objectMapper);
        assertThat(filter.getOrder()).isEqualTo(-110);
    }

    private static MockServerWebExchange exchangeWithVersion(String version) {
        var request = MockServerHttpRequest.get("/api/academic/groups")
                .header(PwaVersionPolicyFilter.PWA_VERSION_HEADER, version)
                .header(HttpHeaders.AUTHORIZATION, "Bearer token")
                .build();
        return MockServerWebExchange.from(request);
    }

    private static PwaVersionPolicyProperties props(boolean enabled, String latest, boolean force) {
        PwaVersionPolicyProperties props = new PwaVersionPolicyProperties();
        props.setEnabled(enabled);
        props.setLatest(latest);
        props.setForce(force);
        return props;
    }
}
