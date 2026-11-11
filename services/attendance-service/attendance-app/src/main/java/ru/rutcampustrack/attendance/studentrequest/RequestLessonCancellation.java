package ru.rutcampustrack.attendance.studentrequest;

import com.mongodb.MongoException;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Date;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.bson.Document;
import org.bson.types.ObjectId;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.mongodb.MongoTransactionManager;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;
import ru.rutcampustrack.attendance.audit.RequestActionRecorder;
import ru.rutcampustrack.attendance.contract.enums.ExcuseTicketStatus;
import ru.rutcampustrack.attendance.contract.enums.LateCheckinRequestStatus;
import ru.rutcampustrack.attendance.contract.enums.StudentRequestKind;
import ru.rutcampustrack.attendance.contract.exception.ResourceNotFoundException;
import ru.rutcampustrack.attendance.event.LessonCancellationMarker;
import ru.rutcampustrack.attendance.event.SemesterArchiveEffectRejectedException;
import ru.rutcampustrack.attendance.exception.ScheduleServiceUnavailableException;
import ru.rutcampustrack.attendance.excuse.ExcuseEventPublisher;
import ru.rutcampustrack.attendance.excuse.entity.ExcuseTicket;
import ru.rutcampustrack.attendance.grpc.ScheduleGrpcClient;
import ru.rutcampustrack.attendance.latecheckin.LateCheckinEventPublisher;
import ru.rutcampustrack.attendance.latecheckin.entity.LateCheckinRequest;
import ru.rutcampustrack.attendance.student.PairWriteCoordinator;
import ru.rutcampustrack.schedule.grpc.LessonResponse;

/** Applies Schedule's cancellation to pending request coverage under the lesson write fence. */
@Component
public class RequestLessonCancellation {
    private static final List<String> OPEN_EXCUSE = List.of("submitted", "SUBMITTED");
    private static final List<String> OPEN_LATE = List.of("PENDING", "pending");

    private final MongoTemplate mongo;
    private final PairWriteCoordinator writes;
    private final ScheduleGrpcClient schedule;
    private final RequestActionRecorder actions;
    private final ExcuseEventPublisher excuseEvents;
    private final LateCheckinEventPublisher lateEvents;
    private final Clock clock;
    private final TransactionTemplate transactions;

    public RequestLessonCancellation(
            MongoTemplate mongo,
            PairWriteCoordinator writes,
            ScheduleGrpcClient schedule,
            RequestActionRecorder actions,
            ExcuseEventPublisher excuseEvents,
            LateCheckinEventPublisher lateEvents,
            Clock clock,
            @Qualifier("mongoTransactionManager") MongoTransactionManager manager) {
        this.mongo = mongo;
        this.writes = writes;
        this.schedule = schedule;
        this.actions = actions;
        this.excuseEvents = excuseEvents;
        this.lateEvents = lateEvents;
        this.clock = clock;
        this.transactions = new TransactionTemplate(manager);
    }

    /** The cancellation consumer already owns the semester and lesson fences in this transaction. */
    public void apply(long lessonId, long semesterId, Long groupId, Instant cancelledAt) {
        apply(new CancelledTarget(lessonId, semesterId, groupId, cancelledAt), null);
    }

    public void reconcileStudent(long studentId) {
        reconcile(new Scope("student_id", studentId), null);
    }

    public void reconcileGroup(long groupId) {
        reconcile(new Scope("group_id", groupId), null);
    }

    public boolean reconcileRequest(StudentRequestKind kind, String requestId) {
        Object id = ObjectId.isValid(requestId) ? List.of(new ObjectId(requestId), requestId) : requestId;
        return reconcile(new Scope("_id", id), kind);
    }

    public void reconcileExcusesForLesson(long lessonId) {
        reconcile(new Scope("lesson_ids", lessonId), StudentRequestKind.EXCUSE);
    }

    private boolean reconcile(Scope scope, StudentRequestKind kind) {
        Set<CancelledTarget> targets = new LinkedHashSet<>();
        if (kind != StudentRequestKind.LATE_CHECKIN)
            findMarkedTargets(targets, ExcuseTicket.class, "lesson_ids", OPEN_EXCUSE, scope);
        if (kind != StudentRequestKind.EXCUSE)
            findMarkedTargets(targets, LateCheckinRequest.class, "lesson_id", OPEN_LATE, scope);
        List<CancelledTarget> ordered = targets.stream()
                .sorted(Comparator.comparing(CancelledTarget::cancelledAt)
                        .thenComparingLong(CancelledTarget::lessonId)
                        .thenComparing(CancelledTarget::groupId))
                .toList();
        Map<Long, Optional<LessonResponse>> lessons = new HashMap<>();
        boolean repaired = false;
        for (CancelledTarget target : ordered) {
            LessonResponse lesson = lessons.computeIfAbsent(target.lessonId(), this::cancelledLesson)
                    .orElse(null);
            if (lesson == null || lesson.getGroupId() != target.groupId()
                    || lesson.getSemesterId() != target.semesterId()) continue;
            for (int attempt = 0; ; attempt++) {
                try {
                    transactions.executeWithoutResult(status -> {
                        if (writes.isSubjectDeleted(target.lessonId(), target.semesterId(), target.groupId())
                                || writes.isTransferredSource(target.lessonId())) return;
                        writes.lockAcceptedScheduleEffectLessons(
                                target.semesterId(), List.of(target.lessonId()), target.groupId(), clock.instant());
                        apply(target, scope, kind);
                    });
                    repaired = true;
                    break;
                } catch (SemesterArchiveEffectRejectedException sealed) {
                    // A sealed archive is immutable.
                    break;
                } catch (RuntimeException failure) {
                    if (attempt == 2 || !isTransient(failure)) throw failure;
                }
            }
        }
        return repaired;
    }

    private Optional<LessonResponse> cancelledLesson(long lessonId) {
        try {
            LessonResponse lesson = schedule.getLessonById(lessonId);
            if (lesson != null && lesson.getId() == lessonId
                    && "cancelled".equalsIgnoreCase(lesson.getStatus())) return Optional.of(lesson);
        } catch (ResourceNotFoundException | ScheduleServiceUnavailableException unavailable) {
            // Without a live scoped snapshot, an old marker cannot prove the cancellation.
        }
        return Optional.empty();
    }

    private void findMarkedTargets(
            Set<CancelledTarget> targets, Class<?> type, String lessonField,
            List<String> statuses, Scope scope) {
        List<Document> conditions = new ArrayList<>(List.of(
                new Document("$eq", List.of("$marker.semester_id", "$semester_id")),
                new Document("$gte", List.of("$marker.marked_at", "$created_at"))));
        if (lessonField.equals(scope.field())) {
            conditions.add(new Document("$eq", List.of("$marker.lesson_id", scope.value())));
        }
        List<Document> pipeline = List.of(
                new Document("$match", new Document(scope.field(), scope.predicate())
                        .append("status", new Document("$in", statuses))),
                new Document("$project", new Document(lessonField, 1)
                        .append("semester_id", 1).append("group_id", 1).append("created_at", 1)),
                new Document("$lookup", new Document("from", mongo.getCollectionName(LessonCancellationMarker.class))
                        .append("localField", lessonField).append("foreignField", "lesson_id").append("as", "marker")),
                new Document("$unwind", "$marker"),
                new Document("$match", new Document("$expr", new Document("$and", conditions))),
                new Document("$group", new Document("_id", new Document("lesson_id", "$marker.lesson_id")
                        .append("semester_id", "$marker.semester_id").append("group_id", "$group_id")
                        .append("marked_at", "$marker.marked_at"))));
        for (Document row : mongo.getCollection(mongo.getCollectionName(type)).aggregate(pipeline)) {
            Document key = row.get("_id", Document.class);
            if (key.get("lesson_id") instanceof Number lesson && lesson.longValue() > 0
                    && key.get("semester_id") instanceof Number semester && semester.longValue() > 0
                    && key.get("group_id") instanceof Number group && group.longValue() > 0
                    && key.get("marked_at") instanceof Date at) {
                targets.add(new CancelledTarget(
                        lesson.longValue(), semester.longValue(), group.longValue(), at.toInstant()));
            }
        }
    }

    private void apply(CancelledTarget target, Scope scope) {
        apply(target, scope, null);
    }

    private void apply(CancelledTarget target, Scope scope, StudentRequestKind kind) {
        Instant now = clock.instant();
        if (kind != StudentRequestKind.LATE_CHECKIN) cancelExcuses(target, scope, now);
        if (kind != StudentRequestKind.EXCUSE) cancelLateCheckins(target, scope, now);
    }

    private void cancelExcuses(CancelledTarget target, Scope scope, Instant now) {
        for (ExcuseTicket ticket :
                mongo.find(pending(target, scope, "lesson_ids", OPEN_EXCUSE), ExcuseTicket.class)) {
            var before = RequestActionRecorder.snapshot(ticket);
            Update change = new Update().set("updated_at", now);
            List<Long> remaining = ticket.getLessonIds().stream()
                    .filter(id -> !Long.valueOf(target.lessonId()).equals(id))
                    .toList();
            if (remaining.isEmpty()) {
                ticket.setStatus(ExcuseTicketStatus.CANCELLED);
                ticket.setDecisionAt(target.cancelledAt());
                change.set("status", ticket.getStatus()).set("decision_at", target.cancelledAt());
            } else {
                if (ticket.getOriginalLessonIds() == null) {
                    ticket.setOriginalLessonIds(List.copyOf(ticket.getLessonIds()));
                    change.set("original_lesson_ids", ticket.getOriginalLessonIds());
                }
                ticket.setLessonIds(remaining);
                change.set("lesson_ids", remaining);
            }
            ticket.setUpdatedAt(now);
            updateRequest(ticket.getId(), change, ExcuseTicket.class);
            actions.excuseTargetCancelled(ticket, before, target.lessonId(), now);
            if (remaining.isEmpty()) excuseEvents.publishDecided(ticket);
        }
    }

    private void cancelLateCheckins(CancelledTarget target, Scope scope, Instant now) {
        for (Document stored : mongo.find(
                pending(target, scope, "lesson_id", OPEN_LATE), Document.class,
                mongo.getCollectionName(LateCheckinRequest.class))) {
            // Older union views admit either case, while LateCheckinRequest's enum mapper expects uppercase.
            LateCheckinRequest request = mongo.getConverter().read(
                    LateCheckinRequest.class, stored.append("status", LateCheckinRequestStatus.PENDING.name()));
            var before = RequestActionRecorder.snapshot(request);
            request.setStatus(LateCheckinRequestStatus.CANCELLED);
            request.setDecisionAt(target.cancelledAt());
            request.setUpdatedAt(now);
            updateRequest(
                    stored.get("_id"),
                    new Update().set("status", request.getStatus())
                            .set("decision_at", target.cancelledAt()).set("updated_at", now),
                    LateCheckinRequest.class);
            actions.late(request, before, null, null, now);
            lateEvents.publishDecided(
                    request, request.getLessonDate(), request.getLessonNumber(), request.getSubjectId(), null);
        }
    }

    private void updateRequest(Object id, Update change, Class<?> type) {
        if (mongo.updateFirst(Query.query(Criteria.where("_id").is(id)), change, type)
                .getMatchedCount() != 1) {
            throw new IllegalStateException("Request changed while the cancellation fence was held");
        }
    }

    private static Query pending(
            CancelledTarget target, Scope scope, String lessonField, List<String> statuses) {
        Criteria criteria = Criteria.where(lessonField).is(target.lessonId())
                .and("semester_id").is(target.semesterId())
                .and("status").in(statuses);
        if (target.groupId() != null) criteria.and("group_id").is(target.groupId());
        Query query = Query.query(new Criteria().andOperator(
                criteria, new Criteria().orOperator(Criteria.where("created_at").lte(target.cancelledAt()),
                        Criteria.where("created_at").is(null))));
        if (scope != null && !Set.of("group_id", lessonField).contains(scope.field())) {
            query.addCriteria(Criteria.where(scope.field()).is(scope.predicate()));
        }
        return query;
    }

    private static boolean isTransient(Throwable failure) {
        for (Throwable cause = failure; cause != null; cause = cause.getCause()) {
            if (cause instanceof MongoException mongoFailure
                    && mongoFailure.hasErrorLabel(MongoException.TRANSIENT_TRANSACTION_ERROR_LABEL)) {
                return true;
            }
        }
        return false;
    }

    private record Scope(String field, Object value) {
        Object predicate() {
            return value instanceof List<?> values ? new Document("$in", values) : value;
        }
    }

    private record CancelledTarget(long lessonId, long semesterId, Long groupId, Instant cancelledAt) {}
}
