package ru.rutcampustrack.gateway.clientip;

import jakarta.annotation.PostConstruct;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Explicit address list for the single trusted edge in front of Gateway.
 *
 * <p>The list is deliberately address based. Host-name resolution, CIDR
 * matching and private-range inference would make the source of the forwarded
 * identity ambiguous, so values are canonicalized once during startup.</p>
 */
@Component
@ConfigurationProperties(prefix = "rutcampustrack.gateway.client-ip")
public class TrustedClientIpProperties {

    private List<String> trustedProxyAddresses = new ArrayList<>();
    private boolean required;
    private Set<String> canonicalTrustedProxyAddresses = Set.of();

    public List<String> getTrustedProxyAddresses() {
        return Collections.unmodifiableList(trustedProxyAddresses);
    }

    public void setTrustedProxyAddresses(List<String> trustedProxyAddresses) {
        this.trustedProxyAddresses = trustedProxyAddresses == null
                ? new ArrayList<>()
                : new ArrayList<>(trustedProxyAddresses);
    }

    public boolean isRequired() {
        return required;
    }

    public void setRequired(boolean required) {
        this.required = required;
    }

    public Set<String> getCanonicalTrustedProxyAddresses() {
        return canonicalTrustedProxyAddresses;
    }

    public boolean isTrusted(String canonicalPeer) {
        return canonicalPeer != null && canonicalTrustedProxyAddresses.contains(canonicalPeer);
    }

    @PostConstruct
    public void validateAndInitialize() {
        Set<String> canonical = new LinkedHashSet<>();
        for (String configuredAddress : trustedProxyAddresses) {
            if (configuredAddress == null || configuredAddress.isBlank()) {
                throw new IllegalStateException(
                        "rutcampustrack.gateway.client-ip.trusted-proxy-addresses contains a blank value");
            }
            String canonicalAddress = TrustedClientIpResolver.canonicalizeLiteral(configuredAddress)
                    .orElseThrow(() -> new IllegalStateException(
                            "rutcampustrack.gateway.client-ip.trusted-proxy-addresses accepts IP literals only"));
            canonical.add(canonicalAddress);
        }
        if (required && canonical.isEmpty()) {
            throw new IllegalStateException(
                    "rutcampustrack.gateway.client-ip.trusted-proxy-addresses must be non-empty");
        }
        canonicalTrustedProxyAddresses = Set.copyOf(canonical);
    }
}
