package ru.rutcampustrack.shared.security;

import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;

import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CharsetDecoder;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Validates the frozen internal JWT wire produced by auth-service.
 *
 * <p>The validator is deliberately strict about representation as well as
 * signature and time. Downstream code receives only the immutable identity
 * record and never reads raw claims again.</p>
 */
public class InternalJwtValidator {

    private static final int MAX_COMPACT_TOKEN_LENGTH = 16 * 1024;
    private static final int MAX_ENCODED_SECTION_LENGTH = 8 * 1024;
    private static final int MAX_DECODED_JSON_LENGTH = 8 * 1024;
    private static final int MAX_JSON_NESTING = 32;
    private static final Set<String> JOSE_HEADER_NAMES = Set.of("alg", "kid", "typ");
    private static final Set<String> ROLES = Set.of("STUDENT", "TEACHER", "HEADMAN", "ADMIN");
    private static final Set<String> STATUSES =
            Set.of("ACTIVE", "SUSPENDED", "EXPELLED", "GRADUATED", "ARCHIVED");
    private static final Set<String> TERMINAL_STATUSES =
            Set.of("EXPELLED", "GRADUATED", "ARCHIVED");

    private final PublicKeyProvider publicKeyProvider;
    private final InternalJwtProperties properties;

    public InternalJwtValidator(PublicKeyProvider publicKeyProvider, InternalJwtProperties properties) {
        this.publicKeyProvider = publicKeyProvider;
        this.properties = properties;
    }

    /**
     * @throws InternalJwtException if any signature, purpose, identity,
     *                              representation, or time invariant fails.
     */
    public InternalJwtClaims validate(String token) {
        if (token == null || token.isBlank()) {
            throw new InternalJwtException("Token is missing");
        }
        try {
            RawJwtToken raw = RawJwtToken.parse(token);
            requireJoseHeader(raw.header());

            Jwts.parser()
                    .verifyWith(publicKeyProvider.getPublicKey())
                    .requireIssuer(properties.expectedIssuer())
                    .requireAudience(properties.expectedAudience())
                    .require("token_use", "internal")
                    .clockSkewSeconds(properties.clockSkewSeconds())
                    .build()
                    .parseSignedClaims(token);

            requireWire(raw.payload());
            long userId = parsePositiveDecimal(raw.payload().requiredPlainString("sub"), "sub");
            UUID sessionId = parseCanonicalUuid(raw.payload().requiredPlainString("sid"), "sid");
            long sessionVersion = parsePositiveDecimal(raw.payload().requiredPlainString("sv"), "sv");
            long rolesVersion = parsePositiveDecimal(raw.payload().requiredPlainString("rv"), "rv");
            String role = parseEnum(raw.payload().requiredPlainString("role"), "role", ROLES);
            String status = parseEnum(raw.payload().requiredPlainString("status"), "status", STATUSES);
            Long groupId = raw.payload().has("group_id")
                    ? parsePositiveDecimal(raw.payload().requiredPlainString("group_id"), "group_id")
                    : null;
            boolean isHeadman = raw.payload().requiredBoolean("is_headman");
            boolean readOnly = raw.payload().requiredBoolean("readOnly");
            requireSemanticIdentity(role, status, isHeadman, readOnly);
            requireTimes(raw.payload());

            return new InternalJwtClaims(
                    userId,
                    sessionId,
                    sessionVersion,
                    rolesVersion,
                    role,
                    status,
                    groupId,
                    isHeadman,
                    readOnly
            );
        } catch (ExpiredJwtException exception) {
            throw new InternalJwtException("Token expired", exception);
        } catch (InternalJwtException exception) {
            throw exception;
        } catch (JwtException | IllegalArgumentException exception) {
            throw new InternalJwtException("Invalid internal token", exception);
        }
    }

    private static void requireJoseHeader(RawJwtPayload header) {
        if (!header.has("alg") || !header.has("kid") || !header.namesWithin(JOSE_HEADER_NAMES)) {
            throw new InternalJwtException("Invalid JOSE header");
        }
        if (!"RS256".equals(header.requiredPlainString("alg"))
                || header.requiredPlainString("kid").isBlank()
                || (header.has("typ") && !"JWT".equals(header.requiredPlainString("typ")))) {
            throw new InternalJwtException("Invalid JOSE header");
        }
    }

    private void requireWire(RawJwtPayload raw) {
        if (!properties.expectedIssuer().equals(raw.requiredPlainString("iss"))) {
            throw new InternalJwtException("Invalid issuer");
        }
        if (!Set.of(properties.expectedAudience()).equals(raw.requiredSingletonStringArray("aud"))) {
            throw new InternalJwtException("Invalid audience");
        }
        if (!"internal".equals(raw.requiredPlainString("token_use"))) {
            throw new InternalJwtException("Invalid token purpose");
        }
    }

    private static long parsePositiveDecimal(String value, String claim) {
        if (value == null || !value.matches("[1-9][0-9]*")) {
            throw new InternalJwtException("Invalid " + claim + " claim");
        }
        try {
            return Long.parseLong(value);
        } catch (NumberFormatException exception) {
            throw new InternalJwtException("Invalid " + claim + " claim", exception);
        }
    }

    private static UUID parseCanonicalUuid(String string, String claim) {
        try {
            UUID parsed = UUID.fromString(string);
            if (!parsed.toString().equals(string)) {
                throw new IllegalArgumentException("non-canonical");
            }
            return parsed;
        } catch (IllegalArgumentException exception) {
            throw new InternalJwtException("Invalid " + claim + " claim", exception);
        }
    }

    private static String parseEnum(String string, String claim, Set<String> allowed) {
        if (!allowed.contains(string)) {
            throw new InternalJwtException("Invalid " + claim + " claim");
        }
        return string;
    }

    private static void requireSemanticIdentity(
            String role,
            String status,
            boolean isHeadman,
            boolean readOnly
    ) {
        if (isHeadman != "HEADMAN".equals(role)
                || "SUSPENDED".equals(status)
                || ("ACTIVE".equals(status) && readOnly)
                || (TERMINAL_STATUSES.contains(status) && !readOnly)) {
            throw new InternalJwtException("Invalid role status identity");
        }
    }

    private void requireTimes(RawJwtPayload raw) {
        long issuedAt = raw.requiredWholeSecondNumber("iat");
        long expiration = raw.requiredWholeSecondNumber("exp");
        if (issuedAt >= expiration) {
            throw new InternalJwtException("Invalid time claims");
        }
        Instant now = Instant.now();
        long skew = properties.clockSkewSeconds();
        if (issuedAt > now.plusSeconds(skew).getEpochSecond()) {
            throw new InternalJwtException("Invalid issued-at claim");
        }
        if (expiration <= now.minusSeconds(skew).getEpochSecond()) {
            throw new InternalJwtException("Token expired");
        }
    }

    private static final class RawJwtToken {
        private final RawJwtPayload header;
        private final RawJwtPayload payload;

        private RawJwtToken(RawJwtPayload header, RawJwtPayload payload) {
            this.header = header;
            this.payload = payload;
        }

        private static RawJwtToken parse(String compactToken) {
            if (compactToken == null) {
                throw new IllegalArgumentException("JWT is missing");
            }
            if (compactToken.length() > MAX_COMPACT_TOKEN_LENGTH) {
                throw new IllegalArgumentException("JWT compact form is too large");
            }
            String[] parts = compactToken.split("\\.", -1);
            if (parts.length != 3) {
                throw new IllegalArgumentException("JWT compact form is invalid");
            }
            for (String part : parts) {
                if (part.length() > MAX_ENCODED_SECTION_LENGTH) {
                    throw new IllegalArgumentException("JWT compact section is too large");
                }
            }
            return new RawJwtToken(
                    RawJwtPayload.parseSection(parts[0], "header"),
                    RawJwtPayload.parseSection(parts[1], "payload")
            );
        }

        private RawJwtPayload header() {
            return header;
        }

        private RawJwtPayload payload() {
            return payload;
        }
    }

    private static final class RawJwtPayload {
        private final Map<String, RawJsonValue> values;

        private RawJwtPayload(Map<String, RawJsonValue> values) {
            this.values = values;
        }

        private static RawJwtPayload parseSection(String encodedSection, String sectionName) {
            try {
                byte[] decoded = Base64.getUrlDecoder().decode(encodedSection);
                if (decoded.length > MAX_DECODED_JSON_LENGTH) {
                    throw new IllegalArgumentException("JWT " + sectionName + " is too large");
                }
                CharsetDecoder decoder = StandardCharsets.UTF_8.newDecoder()
                        .onMalformedInput(CodingErrorAction.REPORT)
                        .onUnmappableCharacter(CodingErrorAction.REPORT);
                String json = decoder.decode(ByteBuffer.wrap(decoded)).toString();
                return new RawJwtPayload(new JsonObjectParser(json, sectionName).parse());
            } catch (CharacterCodingException exception) {
                throw new IllegalArgumentException("JWT " + sectionName + " is not valid UTF-8", exception);
            } catch (IllegalArgumentException exception) {
                throw exception;
            } catch (RuntimeException exception) {
                throw new IllegalArgumentException("JWT " + sectionName + " is invalid", exception);
            }
        }

        private boolean has(String name) {
            return values.containsKey(name);
        }

        private boolean namesWithin(Set<String> names) {
            return names.containsAll(values.keySet());
        }

        private String requiredPlainString(String name) {
            RawJsonValue value = required(name);
            if (value.kind != RawJsonKind.STRING || value.escaped) {
                throw new IllegalArgumentException("JWT " + name + " must be a plain JSON string");
            }
            return value.stringValue;
        }

        private boolean requiredBoolean(String name) {
            RawJsonValue value = required(name);
            if (value.kind != RawJsonKind.BOOLEAN) {
                throw new IllegalArgumentException("JWT " + name + " must be a JSON boolean");
            }
            return value.booleanValue;
        }

        private Set<String> requiredSingletonStringArray(String name) {
            RawJsonValue value = required(name);
            if (value.kind != RawJsonKind.ARRAY || value.arrayValues.size() != 1) {
                throw new IllegalArgumentException("JWT " + name + " must be a singleton JSON array");
            }
            RawJsonValue item = value.arrayValues.get(0);
            if (item.kind != RawJsonKind.STRING || item.escaped) {
                throw new IllegalArgumentException("JWT " + name + " must contain a plain JSON string");
            }
            return Set.of(item.stringValue);
        }

        private long requiredWholeSecondNumber(String name) {
            RawJsonValue value = required(name);
            if (value.kind != RawJsonKind.NUMBER || !value.raw.matches("[0-9]+")) {
                throw new IllegalArgumentException("JWT " + name + " must be an integer NumericDate");
            }
            try {
                return Long.parseLong(value.raw);
            } catch (NumberFormatException exception) {
                throw new IllegalArgumentException("JWT " + name + " is out of range", exception);
            }
        }

        private RawJsonValue required(String name) {
            RawJsonValue value = values.get(name);
            if (value == null) {
                throw new IllegalArgumentException("JWT " + name + " is required");
            }
            return value;
        }
    }

    private enum RawJsonKind { STRING, NUMBER, BOOLEAN, NULL, OBJECT, ARRAY }

    private static final class RawJsonValue {
        private final RawJsonKind kind;
        private final String raw;
        private final String stringValue;
        private final boolean escaped;
        private final boolean booleanValue;
        private final List<RawJsonValue> arrayValues;

        private RawJsonValue(
                RawJsonKind kind,
                String raw,
                String stringValue,
                boolean escaped,
                boolean booleanValue,
                List<RawJsonValue> arrayValues
        ) {
            this.kind = kind;
            this.raw = raw;
            this.stringValue = stringValue;
            this.escaped = escaped;
            this.booleanValue = booleanValue;
            this.arrayValues = arrayValues;
        }

        private static RawJsonValue string(String raw, String value, boolean escaped) {
            return new RawJsonValue(RawJsonKind.STRING, raw, value, escaped, false, List.of());
        }

        private static RawJsonValue scalar(RawJsonKind kind, String raw) {
            return new RawJsonValue(kind, raw, null, false, "true".equals(raw), List.of());
        }

        private static RawJsonValue array(String raw, List<RawJsonValue> values) {
            return new RawJsonValue(RawJsonKind.ARRAY, raw, null, false, false, List.copyOf(values));
        }
    }

    private static final class JsonObjectParser {
        private final String text;
        private final String sectionName;
        private int index;
        private int nesting;

        private JsonObjectParser(String text, String sectionName) {
            this.text = text;
            this.sectionName = sectionName;
        }

        private Map<String, RawJsonValue> parse() {
            skipWhitespace();
            Map<String, RawJsonValue> object = parseObject();
            skipWhitespace();
            if (index != text.length()) {
                throw error("trailing JSON content");
            }
            return object;
        }

        private Map<String, RawJsonValue> parseObject() {
            enterNesting();
            try {
                expect('{');
                Map<String, RawJsonValue> object = new HashMap<>();
                Set<String> keys = new HashSet<>();
                skipWhitespace();
                if (consume('}')) {
                    return object;
                }
                while (true) {
                    skipWhitespace();
                    ParsedString key = parseString();
                    if (!keys.add(key.value)) {
                        throw error("duplicate JSON key");
                    }
                    skipWhitespace();
                    expect(':');
                    skipWhitespace();
                    object.put(key.value, parseValue());
                    skipWhitespace();
                    if (consume('}')) {
                        return object;
                    }
                    expect(',');
                }
            } finally {
                nesting--;
            }
        }

        private RawJsonValue parseValue() {
            skipWhitespace();
            if (index >= text.length()) {
                throw error("missing JSON value");
            }
            char current = text.charAt(index);
            if (current == '"') {
                ParsedString parsed = parseString();
                return RawJsonValue.string(parsed.raw, parsed.value, parsed.escaped);
            }
            if (current == '{') {
                int start = index;
                parseObject();
                return RawJsonValue.scalar(RawJsonKind.OBJECT, text.substring(start, index));
            }
            if (current == '[') {
                return parseArray();
            }
            if (current == 't' && consumeLiteral("true")) {
                return RawJsonValue.scalar(RawJsonKind.BOOLEAN, "true");
            }
            if (current == 'f' && consumeLiteral("false")) {
                return RawJsonValue.scalar(RawJsonKind.BOOLEAN, "false");
            }
            if (current == 'n' && consumeLiteral("null")) {
                return RawJsonValue.scalar(RawJsonKind.NULL, "null");
            }
            if (current == '-' || isAsciiDigit(current)) {
                return RawJsonValue.scalar(RawJsonKind.NUMBER, parseNumber());
            }
            throw error("invalid JSON value");
        }

        private RawJsonValue parseArray() {
            enterNesting();
            try {
                int start = index;
                expect('[');
                List<RawJsonValue> values = new ArrayList<>();
                skipWhitespace();
                if (consume(']')) {
                    return RawJsonValue.array(text.substring(start, index), values);
                }
                while (true) {
                    values.add(parseValue());
                    skipWhitespace();
                    if (consume(']')) {
                        return RawJsonValue.array(text.substring(start, index), values);
                    }
                    expect(',');
                }
            } finally {
                nesting--;
            }
        }

        private void enterNesting() {
            if (++nesting > MAX_JSON_NESTING) {
                nesting--;
                throw error("JSON nesting is too deep");
            }
        }

        private String parseNumber() {
            int start = index;
            if (consume('-') && index >= text.length()) {
                throw error("invalid JSON number");
            }
            if (consume('0')) {
                if (index < text.length() && isAsciiDigit(text.charAt(index))) {
                    throw error("leading zero in JSON number");
                }
            } else {
                requireDigits();
            }
            if (consume('.')) {
                requireDigits();
            }
            if (index < text.length() && (text.charAt(index) == 'e' || text.charAt(index) == 'E')) {
                index++;
                if (index < text.length() && (text.charAt(index) == '+' || text.charAt(index) == '-')) {
                    index++;
                }
                requireDigits();
            }
            return text.substring(start, index);
        }

        private ParsedString parseString() {
            int start = index;
            expect('"');
            StringBuilder value = new StringBuilder();
            boolean escaped = false;
            while (index < text.length()) {
                char current = text.charAt(index++);
                if (current == '"') {
                    return new ParsedString(text.substring(start, index), value.toString(), escaped);
                }
                if (current < 0x20) {
                    throw error("control character in JSON string");
                }
                if (current != '\\') {
                    value.append(current);
                    continue;
                }
                escaped = true;
                if (index >= text.length()) {
                    throw error("unterminated JSON escape");
                }
                char escape = text.charAt(index++);
                switch (escape) {
                    case '"' -> value.append('"');
                    case '\\' -> value.append('\\');
                    case '/' -> value.append('/');
                    case 'b' -> value.append('\b');
                    case 'f' -> value.append('\f');
                    case 'n' -> value.append('\n');
                    case 'r' -> value.append('\r');
                    case 't' -> value.append('\t');
                    case 'u' -> value.append(parseUnicodeEscape());
                    default -> throw error("invalid JSON escape");
                }
            }
            throw error("unterminated JSON string");
        }

        private char parseUnicodeEscape() {
            if (index + 4 > text.length()) {
                throw error("short unicode escape");
            }
            String hex = text.substring(index, index + 4);
            if (!hex.matches("[0-9a-fA-F]{4}")) {
                throw error("invalid unicode escape");
            }
            index += 4;
            return (char) Integer.parseInt(hex, 16);
        }

        private void requireDigits() {
            int start = index;
            while (index < text.length() && isAsciiDigit(text.charAt(index))) {
                index++;
            }
            if (start == index) {
                throw error("JSON number requires digits");
            }
        }

        private boolean consumeLiteral(String literal) {
            if (text.startsWith(literal, index)) {
                index += literal.length();
                return true;
            }
            return false;
        }

        private boolean consume(char expected) {
            if (index < text.length() && text.charAt(index) == expected) {
                index++;
                return true;
            }
            return false;
        }

        private void expect(char expected) {
            if (!consume(expected)) {
                throw error("expected '" + expected + "'");
            }
        }

        private void skipWhitespace() {
            while (index < text.length() && isJsonWhitespace(text.charAt(index))) {
                index++;
            }
        }

        private static boolean isAsciiDigit(char value) {
            return value >= '0' && value <= '9';
        }

        private static boolean isJsonWhitespace(char value) {
            return value == ' ' || value == '\t' || value == '\n' || value == '\r';
        }

        private IllegalArgumentException error(String message) {
            return new IllegalArgumentException("JWT " + sectionName + " " + message + " at offset " + index);
        }

        private record ParsedString(String raw, String value, boolean escaped) {
        }
    }
}
