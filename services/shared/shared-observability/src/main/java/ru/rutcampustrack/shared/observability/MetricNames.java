package ru.rutcampustrack.shared.observability;

/**
 * Единые имена метрик. Конвенция: {@code <domain>.<entity>.<verb>} либо
 * {@code <domain>.<entity>.<noun>} для gauge.
 *
 * <p>QA4 — список фиксирован, чтобы Grafana dashboards и Prometheus
 * alert-rules ссылались на стабильные имена. Добавление новой метрики
 * требует обновления этого класса + dashboards + docs/alerts.md.
 */
public final class MetricNames {

    /** Counter: успешный логин. Tags: {@code role}. */
    public static final String AUTH_LOGIN = "auth.login";
    /** Counter: logout (M03b отложенная метрика). Tags: {@code cause}. */
    public static final String AUTH_LOGOUT = "auth.logout";
    /** Counter: запрос OTP. Tags: {@code channel}. */
    public static final String OTP_REQUEST = "otp.request";
    /** Counter: верификация OTP. Tags: {@code outcome}. */
    public static final String OTP_VERIFY = "otp.verify";
    /** Counter: чек-ин на пару. Tags: {@code status} (present/late/free_attendance). */
    public static final String ATTENDANCE_CHECKIN = "attendance.checkin";
    /** Counter: создание excuse-тикета. Tags: {@code kind}. */
    public static final String EXCUSE_CREATED = "excuse.created";
    /** Counter: late-checkin записи. */
    public static final String LATE_CHECKIN_CREATED = "late_checkin.created";

    /**
     * Counter: KI-2 — dual-mode internal JWT silent fallback на legacy header'ы.
     * Tags: {@code from}, {@code to}.
     */
    public static final String INTERNAL_JWT_FALLBACK = "internal_jwt.fallback";

    /** Gauge: число студентов в красной зоне (≥ N пропусков подряд). */
    public static final String STUDENTS_IN_RED_ZONE = "attendance.students_in_red_zone";
    /** Gauge: число активных STOMP WebSocket-сессий. */
    public static final String ACTIVE_WS_SESSIONS = "notification.active_ws_sessions";
    /** Gauge: max(now - created_at) для unsent событий в outbox (seconds). */
    public static final String OUTBOX_LAG_SECONDS = "outbox.lag.seconds";

    private MetricNames() {
    }
}
