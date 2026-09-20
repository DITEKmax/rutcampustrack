package ru.rutcampustrack.gateway.clientip;

import org.junit.jupiter.api.Test;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.mock.web.server.MockServerWebExchange;

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class TrustedClientIpResolverTest {

    @Test
    void canonicalizesIpv4Ipv6AndMappedIpv6Literals() {
        assertThat(TrustedClientIpResolver.canonicalizeLiteral("203.0.113.7"))
                .contains("203.0.113.7");
        assertThat(TrustedClientIpResolver.canonicalizeLiteral("2001:0DB8:0:0:0:0:0:1"))
                .contains("2001:db8::1");
        assertThat(TrustedClientIpResolver.canonicalizeLiteral("0:0:0:0:0:ffff:192.0.2.128"))
                .contains("192.0.2.128");
        assertThat(TrustedClientIpResolver.canonicalizeLiteral("::ffff:192.0.2.128"))
                .contains("192.0.2.128");
    }

    @Test
    void rejectsNonLiteralAndAmbiguousForwardedForms() {
        assertThat(List.of(
                "unknown", "203.0.113.7:443", "[2001:db8::1]", "2001:db8::1%eth0",
                "203.0.113.7, 198.51.100.4", "", "203.0.113.999"))
                .allSatisfy(value -> assertThat(TrustedClientIpResolver.canonicalizeLiteral(value))
                        .isEmpty());
    }

    @Test
    void trustedPeerAcceptsOneRawXffLineAndCanonicalizesIt() throws Exception {
        TrustedClientIpResolver resolver = resolverWithTrustedProxy("192.0.2.10");
        MockServerHttpRequest request = MockServerHttpRequest.get("/api/x")
                .remoteAddress(address("192.0.2.10"))
                .header("X-Forwarded-For", " 2001:0db8::1 ")
                .build();

        TrustedClientIpResolver.Resolution resolution = resolver.resolve(request);

        assertThat(resolution.peerIp()).isEqualTo("192.0.2.10");
        assertThat(resolution.clientIp()).isEqualTo("2001:db8::1");
        assertThat(resolution.trustedPeer()).isTrue();
    }

    @Test
    void trustedPeerRejectsCommaChainAndBlankDuplicateLine() throws Exception {
        TrustedClientIpResolver resolver = resolverWithTrustedProxy("192.0.2.10");
        MockServerHttpRequest commaChain = MockServerHttpRequest.get("/api/x")
                .remoteAddress(address("192.0.2.10"))
                .header("X-Forwarded-For", "203.0.113.7, 198.51.100.4")
                .build();
        MockServerHttpRequest blankDuplicate = MockServerHttpRequest.get("/api/x")
                .remoteAddress(address("192.0.2.10"))
                .header("X-Forwarded-For", "203.0.113.7")
                .header("X-Forwarded-For", "")
                .build();

        assertThat(resolver.resolve(commaChain).clientIp()).isEqualTo("192.0.2.10");
        assertThat(resolver.resolve(blankDuplicate).clientIp()).isEqualTo("192.0.2.10");
    }

    @Test
    void untrustedOrNullPeerIgnoresForwardingHeaders() throws Exception {
        TrustedClientIpResolver resolver = resolverWithTrustedProxy("192.0.2.10");
        MockServerHttpRequest untrusted = MockServerHttpRequest.get("/api/x")
                .remoteAddress(address("198.51.100.9"))
                .header("X-Forwarded-For", "203.0.113.7")
                .header("X-Real-IP", "203.0.113.8")
                .build();
        MockServerHttpRequest noPeer = MockServerHttpRequest.get("/api/x")
                .header("X-Forwarded-For", "203.0.113.7")
                .build();

        assertThat(resolver.resolve(untrusted).clientIp()).isEqualTo("198.51.100.9");
        assertThat(resolver.resolve(untrusted).trustedPeer()).isFalse();
        assertThat(resolver.resolve(noPeer).clientIp()).isEqualTo(TrustedClientIpResolver.UNKNOWN);
        assertThat(resolver.resolve(noPeer).trustedPeer()).isFalse();
    }

    @Test
    void propertiesRejectBlankInvalidAndRequiredEmptyTrust() {
        TrustedClientIpProperties invalid = new TrustedClientIpProperties();
        invalid.setTrustedProxyAddresses(List.of("proxy.internal"));
        assertThat(org.assertj.core.api.Assertions.catchThrowable(invalid::validateAndInitialize))
                .isInstanceOf(IllegalStateException.class);

        TrustedClientIpProperties requiredEmpty = new TrustedClientIpProperties();
        requiredEmpty.setRequired(true);
        assertThat(org.assertj.core.api.Assertions.catchThrowable(requiredEmpty::validateAndInitialize))
                .isInstanceOf(IllegalStateException.class);
    }

    private static TrustedClientIpResolver resolverWithTrustedProxy(String address) {
        TrustedClientIpProperties properties = new TrustedClientIpProperties();
        properties.setTrustedProxyAddresses(List.of(address));
        properties.setRequired(true);
        properties.validateAndInitialize();
        return new TrustedClientIpResolver(properties);
    }

    private static InetSocketAddress address(String value) throws Exception {
        return new InetSocketAddress(InetAddress.getByName(value), 8080);
    }
}
