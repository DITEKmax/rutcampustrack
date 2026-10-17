package ru.rutcampustrack.shared.observability;

import java.util.UUID;
import java.util.regex.Pattern;

/** Request correlation is independent of W3C trace/span IDs and business command IDs. */
public final class RequestCorrelation {
    public static final String HEADER = "X-Request-Id";
    public static final String BAGGAGE_FIELD = "requestId";
    private static final Pattern CANONICAL_ID =
            Pattern.compile("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}");

    private RequestCorrelation() {}

    public static String newId() {
        return UUID.randomUUID().toString();
    }

    public static String canonicalOrNew(String value) {
        return isCanonical(value) ? value : newId();
    }

    public static boolean isCanonical(String value) {
        return value != null && CANONICAL_ID.matcher(value).matches();
    }
}
