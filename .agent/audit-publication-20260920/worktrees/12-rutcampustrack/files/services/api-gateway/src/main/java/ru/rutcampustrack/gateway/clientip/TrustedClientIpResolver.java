package ru.rutcampustrack.gateway.clientip;

import org.springframework.http.HttpHeaders;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;

import java.net.InetSocketAddress;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/** Resolves the client address from the raw peer and one strictly validated XFF value. */
@Component
public class TrustedClientIpResolver {

    private static final String X_FORWARDED_FOR = "X-Forwarded-For";
    public static final String CLIENT_IP_ATTRIBUTE = TrustedClientIpResolver.class.getName() + ".clientIp";
    public static final String PEER_IP_ATTRIBUTE = TrustedClientIpResolver.class.getName() + ".peerIp";
    public static final String UNKNOWN = "unknown";

    private final TrustedClientIpProperties properties;

    public TrustedClientIpResolver(TrustedClientIpProperties properties) {
        this.properties = properties;
    }

    public Resolution resolve(ServerHttpRequest request) {
        String peer = canonicalizeRemoteAddress(request.getRemoteAddress());
        if (!properties.isTrusted(peer)) {
            return new Resolution(peer, peer, false);
        }

        HttpHeaders headers = request.getHeaders();
        // getValuesAsList() may split comma-delimited values. Read the raw
        // header lines so a comma chain and a duplicate line remain rejectable.
        List<String> forwarded = headers.get(X_FORWARDED_FOR);
        if (forwarded == null) {
            forwarded = List.of();
        }
        if (forwarded.size() != 1) {
            return new Resolution(peer, peer, true);
        }

        String client = canonicalizeLiteral(forwarded.get(0)).orElse(peer);
        return new Resolution(peer, client, true);
    }

    public static String canonicalizeRemoteAddress(InetSocketAddress remoteAddress) {
        if (remoteAddress == null || remoteAddress.getAddress() == null) {
            return UNKNOWN;
        }
        byte[] bytes = remoteAddress.getAddress().getAddress();
        return canonicalizeBytes(bytes).orElse(UNKNOWN);
    }

    /**
     * Canonicalizes a single IPv4/IPv6 literal without performing DNS lookup.
     * Outer whitespace is accepted for an XFF line; all other characters must
     * belong to the literal grammar.
     */
    public static Optional<String> canonicalizeLiteral(String candidate) {
        if (candidate == null) {
            return Optional.empty();
        }
        String value = candidate.strip();
        if (value.isEmpty() || value.indexOf('%') >= 0 || value.indexOf('/') >= 0
                || value.indexOf(',') >= 0 || value.indexOf('[') >= 0 || value.indexOf(']') >= 0) {
            return Optional.empty();
        }

        Optional<byte[]> ipv4 = parseIpv4(value);
        if (ipv4.isPresent()) {
            return Optional.of(canonicalizeIpv4(ipv4.get()));
        }
        if (value.indexOf(':') < 0) {
            return Optional.empty();
        }

        return parseIpv6(value).flatMap(TrustedClientIpResolver::canonicalizeBytes);
    }

    private static Optional<byte[]> parseIpv4(String value) {
        String[] parts = value.split("\\.", -1);
        if (parts.length != 4) {
            return Optional.empty();
        }
        byte[] address = new byte[4];
        for (int i = 0; i < parts.length; i++) {
            String part = parts[i];
            if (part.isEmpty() || part.length() > 3 || (part.length() > 1 && part.charAt(0) == '0')) {
                return Optional.empty();
            }
            int valuePart = 0;
            for (int j = 0; j < part.length(); j++) {
                char digit = part.charAt(j);
                if (digit < '0' || digit > '9') {
                    return Optional.empty();
                }
                valuePart = valuePart * 10 + digit - '0';
            }
            if (valuePart > 255) {
                return Optional.empty();
            }
            address[i] = (byte) valuePart;
        }
        return Optional.of(address);
    }

    private static Optional<byte[]> parseIpv6(String value) {
        if (!value.matches("[0-9A-Fa-f:.]+") || value.contains(":::")) {
            return Optional.empty();
        }

        int compression = value.indexOf("::");
        if (compression >= 0 && value.indexOf("::", compression + 2) >= 0) {
            return Optional.empty();
        }

        String left = compression >= 0 ? value.substring(0, compression) : value;
        String right = compression >= 0 ? value.substring(compression + 2) : "";
        Optional<List<Integer>> leftGroups = parseHextets(left, compression < 0);
        Optional<List<Integer>> rightGroups = parseHextets(right, true);
        if (leftGroups.isEmpty() || rightGroups.isEmpty()) {
            return Optional.empty();
        }

        List<Integer> groups = new ArrayList<>(leftGroups.get());
        groups.addAll(rightGroups.get());
        if (compression < 0 && groups.size() != 8) {
            return Optional.empty();
        }
        if (compression >= 0 && groups.size() >= 8) {
            return Optional.empty();
        }

        List<Integer> expanded = new ArrayList<>(8);
        expanded.addAll(leftGroups.get());
        if (compression >= 0) {
            for (int i = groups.size(); i < 8; i++) {
                expanded.add(0);
            }
        }
        expanded.addAll(rightGroups.get());

        if (expanded.size() != 8) {
            return Optional.empty();
        }
        byte[] bytes = new byte[16];
        for (int i = 0; i < expanded.size(); i++) {
            int group = expanded.get(i);
            bytes[i * 2] = (byte) (group >>> 8);
            bytes[i * 2 + 1] = (byte) group;
        }
        return Optional.of(bytes);
    }

    private static Optional<List<Integer>> parseHextets(String value, boolean allowIpv4Tail) {
        if (value.isEmpty()) {
            return Optional.of(List.of());
        }
        String[] parts = value.split(":", -1);
        List<Integer> groups = new ArrayList<>();
        for (int i = 0; i < parts.length; i++) {
            String part = parts[i];
            if (part.isEmpty()) {
                return Optional.empty();
            }
            if (part.indexOf('.') >= 0) {
                if (!allowIpv4Tail || i != parts.length - 1) {
                    return Optional.empty();
                }
                Optional<byte[]> ipv4 = parseIpv4(part);
                if (ipv4.isEmpty()) {
                    return Optional.empty();
                }
                groups.add(((ipv4.get()[0] & 0xff) << 8) | (ipv4.get()[1] & 0xff));
                groups.add(((ipv4.get()[2] & 0xff) << 8) | (ipv4.get()[3] & 0xff));
                continue;
            }
            if (part.length() > 4) {
                return Optional.empty();
            }
            int group = 0;
            for (int j = 0; j < part.length(); j++) {
                int digit = Character.digit(part.charAt(j), 16);
                if (digit < 0) {
                    return Optional.empty();
                }
                group = (group << 4) | digit;
            }
            groups.add(group);
        }
        return Optional.of(groups);
    }

    private static Optional<String> canonicalizeBytes(byte[] bytes) {
        if (bytes == null || (bytes.length != 4 && bytes.length != 16)) {
            return Optional.empty();
        }
        if (bytes.length == 4) {
            return Optional.of(canonicalizeIpv4(bytes));
        }
        if (isIpv4Mapped(bytes)) {
            return Optional.of(canonicalizeIpv4(new byte[]{bytes[12], bytes[13], bytes[14], bytes[15]}));
        }

        int[] groups = new int[8];
        for (int i = 0; i < groups.length; i++) {
            groups[i] = ((bytes[i * 2] & 0xff) << 8) | (bytes[i * 2 + 1] & 0xff);
        }
        int bestStart = -1;
        int bestLength = 0;
        for (int i = 0; i < groups.length; i++) {
            if (groups[i] != 0) {
                continue;
            }
            int end = i;
            while (end < groups.length && groups[end] == 0) {
                end++;
            }
            if (end - i > bestLength && end - i >= 2) {
                bestStart = i;
                bestLength = end - i;
            }
            i = end - 1;
        }

        StringBuilder result = new StringBuilder();
        for (int i = 0; i < groups.length;) {
            if (i == bestStart) {
                result.append("::");
                i += bestLength;
                continue;
            }
            if (result.length() > 0 && result.charAt(result.length() - 1) != ':') {
                result.append(':');
            }
            result.append(Integer.toHexString(groups[i]));
            i++;
        }
        return Optional.of(result.toString());
    }

    private static boolean isIpv4Mapped(byte[] bytes) {
        for (int i = 0; i < 10; i++) {
            if (bytes[i] != 0) {
                return false;
            }
        }
        return (bytes[10] & 0xff) == 0xff && (bytes[11] & 0xff) == 0xff;
    }

    private static String canonicalizeIpv4(byte[] bytes) {
        return (bytes[0] & 0xff) + "." + (bytes[1] & 0xff) + "."
                + (bytes[2] & 0xff) + "." + (bytes[3] & 0xff);
    }

    public record Resolution(String peerIp, String clientIp, boolean trustedPeer) {
    }
}
