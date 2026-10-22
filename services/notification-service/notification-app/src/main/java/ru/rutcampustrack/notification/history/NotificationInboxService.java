package ru.rutcampustrack.notification.history;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.util.*;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.FindAndModifyOptions;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.*;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import ru.rutcampustrack.notification.contract.dto.history.NotificationInboxDto;
import ru.rutcampustrack.notification.contract.enums.NotificationType;
import ru.rutcampustrack.notification.security.RequestContext;

/**
 * Each role projection lives in the original event/user row; commands never publish delivery
 * events.
 */
@Service
public class NotificationInboxService {
    private final MongoTemplate mongo;
    private final NotificationCatalog catalog;
    private final Clock clock;
    private NotificationProjectionSignals signals;

    public NotificationInboxService(MongoTemplate mongo, NotificationCatalog catalog, Clock clock) {
        this.mongo = mongo;
        this.catalog = catalog;
        this.clock = clock;
    }

    @org.springframework.beans.factory.annotation.Autowired
    public NotificationInboxService(
            MongoTemplate mongo,
            NotificationCatalog catalog,
            Clock clock,
            NotificationProjectionSignals signals) {
        this(mongo, catalog, clock);
        this.signals = signals;
    }

    public NotificationInboxDto.Page list(
            RequestContext context, int limit, String cursor, boolean unreadOnly) {
        String role = context.requireSelectedRole();
        if (limit < 1 || limit > 100) throw bad("Notification limit must be between 1 and 100");
        adoptLegacy(context.getUserId());
        Criteria criteria = scope(context.getUserId(), role, unreadOnly);
        if (cursor != null) {
            Cursor previous = decode(cursor, context, unreadOnly);
            criteria =
                    new Criteria()
                            .andOperator(
                                    criteria,
                                    new Criteria()
                                            .orOperator(
                                                    Criteria.where(path(role, "order_at"))
                                                            .lt(previous.orderAt()),
                                                    new Criteria()
                                                            .andOperator(
                                                                    Criteria.where(
                                                                                    path(
                                                                                            role,
                                                                                            "order_at"))
                                                                            .is(previous.orderAt()),
                                                                    Criteria.where("_id")
                                                                            .lt(previous.id()))));
        }
        Query query =
                Query.query(criteria)
                        .with(
                                Sort.by(
                                        Sort.Order.desc(path(role, "order_at")),
                                        Sort.Order.desc("_id")))
                        .limit(limit + 1);
        List<NotificationHistoryDocument> documents =
                mongo.find(query, NotificationHistoryDocument.class);
        boolean hasMore = documents.size() > limit;
        if (hasMore) documents = documents.subList(0, limit);
        String next = hasMore ? encode(documents.getLast(), context, unreadOnly) : null;
        return new NotificationInboxDto.Page(
                role, documents.stream().map(doc -> catalog.present(doc, role)).toList(), next);
    }

    public NotificationInboxDto.Count count(RequestContext context) {
        String role = context.requireSelectedRole();
        adoptLegacy(context.getUserId());
        return new NotificationInboxDto.Count(
                role,
                mongo.count(
                        Query.query(scope(context.getUserId(), role, true)),
                        NotificationHistoryDocument.class));
    }

    public NotificationInboxDto.ReadResult readState(
            RequestContext context, String id, NotificationInboxDto.ReadState request) {
        String role = context.requireSelectedRole();
        if (request == null
                || request.read() == null
                || request.expectedRevision() == null
                || !request.expectedRevision().matches("0|[1-9][0-9]{0,18}")) {
            throw bad("Notification revision is required");
        }
        long expected;
        try {
            expected = Long.parseLong(request.expectedRevision());
        } catch (NumberFormatException error) {
            throw bad("Invalid revision");
        }
        adoptLegacy(context.getUserId());
        Criteria own =
                new Criteria()
                        .andOperator(
                                scope(context.getUserId(), role, false),
                                Criteria.where("_id").is(id));
        NotificationHistoryDocument before =
                mongo.findOne(Query.query(own), NotificationHistoryDocument.class);
        if (before == null)
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Notification is unavailable");
        var view = before.getRoleViews().get(role);
        if (view.getRevision() != expected) {
            if (Objects.equals(view.getLastExpected(), expected)
                    && Objects.equals(view.getLastRead(), request.read())) {
                return result(before, context, role);
            }
            throw conflict();
        }
        if ((view.getReadAt() != null) == request.read()) return result(before, context, role);
        Instant now = Instant.now(clock);
        Update update =
                new Update()
                        .inc(path(role, "revision"), 1)
                        .set(path(role, "last_expected"), expected)
                        .set(path(role, "last_read"), request.read());
        if (request.read()) update.set(path(role, "read_at"), now);
        else update.unset(path(role, "read_at")).set(path(role, "order_at"), now);
        Criteria cas =
                new Criteria()
                        .andOperator(own, Criteria.where(path(role, "revision")).is(expected));
        NotificationHistoryDocument saved =
                mongo.findAndModify(
                        Query.query(cas),
                        update,
                        FindAndModifyOptions.options().returnNew(true),
                        NotificationHistoryDocument.class);
        boolean changed = saved != null;
        if (saved == null) {
            saved = mongo.findOne(Query.query(own), NotificationHistoryDocument.class);
            if (saved == null
                    || !Objects.equals(saved.getRoleViews().get(role).getLastExpected(), expected)
                    || !Objects.equals(
                            saved.getRoleViews().get(role).getLastRead(), request.read()))
                throw conflict();
        }
        if (signals != null && changed) signals.changed(context.getUserId());
        return result(saved, context, role);
    }

    private NotificationInboxDto.ReadResult result(
            NotificationHistoryDocument doc, RequestContext context, String role) {
        return new NotificationInboxDto.ReadResult(
                role, catalog.present(doc, role), count(context).count());
    }

    public long markAllRead(RequestContext context) {
        String role = context.requireSelectedRole();
        adoptLegacy(context.getUserId());
        var updateResult =
                mongo.updateMulti(
                        Query.query(scope(context.getUserId(), role, true)),
                        new Update()
                                .set(path(role, "read_at"), Instant.now(clock))
                                .inc(path(role, "revision"), 1)
                                .unset(path(role, "last_expected"))
                                .unset(path(role, "last_read")),
                        NotificationHistoryDocument.class);
        if (signals != null && updateResult.getModifiedCount() > 0)
            signals.changed(context.getUserId());
        return count(context).count();
    }

    public boolean legacyRead(RequestContext context, String id) {
        String role = context.requireSelectedRole();
        adoptLegacy(context.getUserId());
        NotificationHistoryDocument doc =
                mongo.findOne(
                        Query.query(
                                new Criteria()
                                        .andOperator(
                                                scope(context.getUserId(), role, false),
                                                Criteria.where("_id").is(id))),
                        NotificationHistoryDocument.class);
        if (doc == null) return false;
        readState(
                context,
                id,
                new NotificationInboxDto.ReadState(
                        true, Long.toString(doc.getRoleViews().get(role).getRevision())));
        return true;
    }

    public org.springframework.data.domain.Page<
                    ru.rutcampustrack.notification.contract.dto.history.NotificationHistoryDto>
            legacyList(
                    RequestContext context,
                    boolean unreadOnly,
                    org.springframework.data.domain.Pageable pageable) {
        String role = context.requireSelectedRole();
        adoptLegacy(context.getUserId());
        Query query = Query.query(scope(context.getUserId(), role, unreadOnly));
        long count = mongo.count(query, NotificationHistoryDocument.class);
        query.with(
                org.springframework.data.domain.PageRequest.of(
                        pageable.getPageNumber(),
                        Math.min(pageable.getPageSize(), 100),
                        Sort.by(Sort.Order.desc(path(role, "order_at")), Sort.Order.desc("_id"))));
        List<ru.rutcampustrack.notification.contract.dto.history.NotificationHistoryDto> items =
                mongo.find(query, NotificationHistoryDocument.class).stream()
                        .map(
                                doc ->
                                        new ru.rutcampustrack.notification.contract.dto.history
                                                .NotificationHistoryDto(
                                                doc.getId(),
                                                doc.getUserId(),
                                                doc.getType(),
                                                doc.getPayload(),
                                                doc.getSentAt(),
                                                doc.getRoleViews().get(role).getReadAt()))
                        .toList();
        return new org.springframework.data.domain.PageImpl<>(items, pageable, count);
    }

    private static Criteria scope(long user, String role, boolean unreadOnly) {
        Criteria result = Criteria.where("user_id").is(user).and("role_views." + role).exists(true);
        if (unreadOnly) result.and(path(role, "read_at")).is(null);
        return result;
    }

    private static String path(String role, String field) {
        return "role_views." + role + "." + field;
    }

    /**
     * One-time additive adoption. Old rows cannot prove past headman status for ordinary group
     * events.
     */
    void adoptLegacy(long userId) {
        List<NotificationHistoryDocument> old =
                mongo.find(
                        Query.query(
                                Criteria.where("user_id")
                                        .is(userId)
                                        .and("role_views")
                                        .exists(false)),
                        NotificationHistoryDocument.class);
        for (var doc : old) {
            String role = legacyRole(doc);
            if (role == null || doc.getSentAt() == null) continue;
            Map<String, NotificationHistoryDocument.RoleView> view =
                    Map.of(
                            role,
                            NotificationHistoryDocument.RoleView.builder()
                                    .readAt(doc.getReadAt())
                                    .orderAt(doc.getSentAt())
                                    .revision(0)
                                    .build());
            mongo.updateFirst(
                    Query.query(
                            Criteria.where("_id")
                                    .is(doc.getId())
                                    .and("user_id")
                                    .is(userId)
                                    .and("role_views")
                                    .exists(false)),
                    new Update().set("role_views", view),
                    NotificationHistoryDocument.class);
        }
    }

    static String legacyRole(NotificationHistoryDocument doc) {
        if (doc.getType() == null || !NotificationCatalog.typeIds().contains(doc.getType().name()))
            return null;
        if (doc.getType() == NotificationType.LESSON_CLOSED) return "HEADMAN";
        if (doc.getType() == NotificationType.EXCUSE_REQUESTED
                || doc.getType() == NotificationType.LATE_CHECKIN_REQUESTED) {
            Map<String, Object> payload = doc.getPayload();
            if (payload == null) return null;
            String field =
                    doc.getType() == NotificationType.EXCUSE_REQUESTED ? "ticket_id" : "request_id";
            if (Objects.equals(
                    NotificationCatalog.positiveId(payload.get("user_id")),
                    Objects.toString(doc.getUserId(), ""))) return "STUDENT";
            if (payload.keySet().equals(Set.of("group_id", field))
                    && NotificationCatalog.positiveId(payload.get("group_id")) != null
                    && payload.get(field) instanceof String id
                    && id.matches("[A-Za-z0-9_:-]{1,128}")) return "HEADMAN";
            return null;
        }
        return "STUDENT";
    }

    private record Cursor(Instant orderAt, String id) {}

    private static String encode(
            NotificationHistoryDocument doc, RequestContext context, boolean unreadOnly) {
        String value =
                context.getUserId()
                        + "|"
                        + context.requireSelectedRole()
                        + "|"
                        + unreadOnly
                        + "|"
                        + doc.getRoleViews().get(context.requireSelectedRole()).getOrderAt()
                        + "|"
                        + doc.getId();
        return Base64.getUrlEncoder()
                .withoutPadding()
                .encodeToString(value.getBytes(StandardCharsets.UTF_8));
    }

    private static Cursor decode(String cursor, RequestContext context, boolean unreadOnly) {
        try {
            if (cursor.length() > 512) throw bad("Invalid cursor");
            String[] fields =
                    new String(Base64.getUrlDecoder().decode(cursor), StandardCharsets.UTF_8)
                            .split("\\|", -1);
            if (fields.length != 5
                    || !fields[0].equals(context.getUserId().toString())
                    || !fields[1].equals(context.requireSelectedRole())
                    || !fields[2].equals(Boolean.toString(unreadOnly))
                    || !fields[4].matches("[A-Za-z0-9_-]{1,128}")) throw bad("Invalid cursor");
            return new Cursor(Instant.parse(fields[3]), fields[4]);
        } catch (RuntimeException error) {
            throw bad("Invalid cursor");
        }
    }

    private static ResponseStatusException bad(String message) {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, message);
    }

    private static ResponseStatusException conflict() {
        return new ResponseStatusException(HttpStatus.CONFLICT, "Notification revision conflicts");
    }
}
