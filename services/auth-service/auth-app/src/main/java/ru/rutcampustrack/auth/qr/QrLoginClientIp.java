package ru.rutcampustrack.auth.qr;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.stereotype.Component;
import java.net.InetAddress;
import java.net.UnknownHostException;

/** QR-only explicit proxy trust. Literal validation happens before InetAddress, so no DNS is performed. */
@Component
public final class QrLoginClientIp {
    private final QrLoginProperties properties;
    public QrLoginClientIp(QrLoginProperties properties) { this.properties = properties; }
    public String resolve(HttpServletRequest request) {
        String peer = literal(request.getRemoteAddr());
        if (peer == null) return "unknown";
        boolean trusted = properties.getTrustedProxyAddresses().stream().map(QrLoginClientIp::literal).anyMatch(peer::equals);
        if (trusted) {
            String forwarded = literal(request.getHeader("X-Forwarded-For"));
            if (forwarded != null) return forwarded;
        }
        return peer;
    }
    static String literal(String value) {
        if (value == null || value.isBlank() || !value.equals(value.trim())) return null;
        boolean ipv4 = value.matches("[0-9]{1,3}(\\.[0-9]{1,3}){3}");
        if (ipv4) {
            for (String octet : value.split("\\.")) {
                if (Integer.parseInt(octet) > 255 || (octet.length() > 1 && octet.startsWith("0"))) return null;
            }
        } else if (!value.contains(":") || !value.matches("[0-9a-fA-F:.]+")) return null;
        try { return InetAddress.getByName(value).getHostAddress(); }
        catch (UnknownHostException malformed) { return null; }
    }
}
