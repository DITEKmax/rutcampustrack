package ru.rutcampustrack.shared.observability;

/**
 * Единые имена MDC-полей. Используются Logback {@code <includeMdcKeyName>}
 * (см. {@code shared/logback-base.xml}) и кодом сервисов через
 * {@link org.slf4j.MDC#put(String, String)}.
 *
 * <p>QA3 (NEW-60/61) — {@link #TRACE_ID} обязательно во всех RabbitMQ-events.
 * KI-2 — {@link #INTERNAL_JWT_FALLBACK} счётчик dual-mode silent fallback.
 */
public final class MdcKeys {

    public static final String TRACE_ID = "traceId";
    public static final String USER_ID = "userId";
    public static final String EVENT_TYPE = "eventType";
    public static final String INTERNAL_JWT_FALLBACK = "internalJwtFallback";

    private MdcKeys() {
    }
}
