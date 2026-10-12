package ru.rutcampustrack.attendance.audit;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.mongodb.MongoTransactionManager;
import org.springframework.stereotype.Service;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;
import ru.rutcampustrack.attendance.grpc.AcademicGrpcClient;
import ru.rutcampustrack.attendance.grpc.ScheduleGrpcClient;
import ru.rutcampustrack.attendance.security.RequestContext;
import ru.rutcampustrack.attendance.student.StudentCheckinModels.Coordinates;
import ru.rutcampustrack.attendance.student.StudentCheckinModels.Identity;
import ru.rutcampustrack.attendance.student.StudentCheckinModels.Lesson;
import ru.rutcampustrack.schedule.grpc.LessonResponse;

/** Only explicit domain rejection sites enroll an attempt. No request body is retained. */
@Service
public class RejectedAttendanceActionService {
    public enum Code {
        CHECKIN_NOT_ELIGIBLE,
        CHECKIN_COOLDOWN,
        MANUAL_ABSENCE_REQUIRES_APPEAL,
        IDEMPOTENCY_PAYLOAD_MISMATCH,
        LESSON_NOT_EDITABLE,
        ATTENDANCE_REVISION_CONFLICT,
        ATTENDANCE_WRITE_CONFLICT
    }

    private static final Logger log =
            LoggerFactory.getLogger(RejectedAttendanceActionService.class);
    private final LessonActionService actions;
    private final ScheduleGrpcClient schedule;
    private final AcademicGrpcClient academic;
    private final TransactionTemplate independent;
    private final Clock clock;
    private final RequestContext requestContext;

    public RejectedAttendanceActionService(
            LessonActionService actions,
            ScheduleGrpcClient schedule,
            AcademicGrpcClient academic,
            MongoTransactionManager manager,
            Clock clock,
            RequestContext requestContext) {
        this.actions = actions;
        this.schedule = schedule;
        this.academic = academic;
        this.clock = clock;
        this.independent = new TransactionTemplate(manager);
        this.independent.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        this.requestContext = requestContext;
    }

    public void geo(
            Identity actor, Lesson lesson, long lessonId, Code code, Coordinates coordinates) {
        if (actor == null || actor.readOnly() || !"STUDENT".equalsIgnoreCase(actor.role())) return;
        enroll(
                new Attempt(
                        UUID.randomUUID().toString(),
                        clock.instant(),
                        lessonId,
                        actor.groupId(),
                        lesson == null ? null : lesson.semesterId(),
                        actor.userId(),
                        actor.role(),
                        actor.userId(),
                        "GEO_ATTEMPT",
                        code,
                        coordinates));
    }

    public void manual(
            LessonResponse lesson,
            Long actorId,
            String actorRole,
            Long studentId,
            String action,
            Code code) {
        if (lesson == null
                || !("ATTENDANCE_SET".equals(action) || "ATTENDANCE_CLEARED".equals(action)))
            return;
        if (actorId == null
                || actorId <= 0
                || lesson.getGroupId() <= 0
                || lesson.getSemesterId() <= 0) return;
        try {
            // Only the validating HTTP filter sets this accepted token; legacy headers clear it.
            // The token stays in the synchronous request scope and is never captured or persisted.
            String acceptedToken = requestContext.getInternalToken();
            if (acceptedToken == null
                    || acceptedToken.isBlank()
                    || !Objects.equals(actorId, requestContext.getUserId())
                    || !Objects.equals(lesson.getGroupId(), requestContext.getGroupId())) return;
            // Assistant admission uses the current signed request context now, never a later
            // callback's context.
            if (!academic.isHeadman(actorId, lesson.getGroupId()).getIsHeadman()
                    && !academic.hasAssistantPermission(lesson.getGroupId(), "MARK_ATTENDANCE")
                    && !academic.hasAssistantPermission(lesson.getGroupId(), "MANAGE_EXCUSES"))
                return;
            enroll(
                    new Attempt(
                            UUID.randomUUID().toString(),
                            clock.instant(),
                            lesson.getId(),
                            lesson.getGroupId(),
                            lesson.getSemesterId(),
                            actorId,
                            actorRole,
                            studentId,
                            action,
                            code,
                            null));
        } catch (RuntimeException unavailable) {
            dropped(action, code);
        }
    }

    private void enroll(Attempt attempt) {
        if (attempt.lessonId() <= 0
                || attempt.groupId() == null
                || attempt.groupId() <= 0
                || attempt.actorId() == null
                || attempt.actorId() <= 0
                || attempt.studentId() == null
                || attempt.studentId() <= 0) return;
        try {
            if (TransactionSynchronizationManager.isActualTransactionActive()) {
                if (!TransactionSynchronizationManager.isSynchronizationActive()) return;
                TransactionSynchronizationManager.registerSynchronization(
                        new TransactionSynchronization() {
                            @Override
                            public void afterCompletion(int status) {
                                if (status == STATUS_ROLLED_BACK) persist(attempt);
                            }
                        });
            } else {
                persist(attempt);
            }
        } catch (RuntimeException unavailable) {
            dropped(attempt);
        }
    }

    private void persist(Attempt attempt) {
        try {
            independent.executeWithoutResult(
                    status -> {
                        // Re-resolve authoritative scope after the original transaction has
                        // released its locks.
                        LessonResponse lesson = schedule.getLessonById(attempt.lessonId());
                        if (lesson == null
                                || lesson.getId() != attempt.lessonId()
                                || lesson.getGroupId() != attempt.groupId()
                                || lesson.getSemesterId() <= 0
                                || attempt.semesterId() != null
                                        && lesson.getSemesterId() != attempt.semesterId()) return;
                        LocalDate date = LocalDate.parse(lesson.getDate());
                        var roster =
                                academic.getGroupMembers(
                                        lesson.getGroupId(), date, lesson.getSemesterId());
                        if (roster == null
                                || !roster.hasSemesterId()
                                || !roster.hasAsOfDate()
                                || roster.getSemesterId() != lesson.getSemesterId()
                                || !date.toString().equals(roster.getAsOfDate())) return;
                        var ids =
                                roster.getStudentsList().stream().map(s -> s.getUserId()).toList();
                        if (ids.stream().anyMatch(id -> id <= 0)
                                || ids.stream().distinct().count() != ids.size()
                                || !ids.contains(attempt.studentId())) return;
                        var payload =
                                LessonActionService.payload(
                                        lesson.getGroupId(),
                                        lesson.getSemesterId(),
                                        lesson.getId(),
                                        attempt.action(),
                                        LessonActionService.person(
                                                attempt.actorId(), attempt.actorRole(), null),
                                        LessonActionService.person(
                                                attempt.studentId(), "STUDENT", null),
                                        null,
                                        null,
                                        attempt.coordinates() == null
                                                ? null
                                                : Map.of(
                                                        "latitude",
                                                        attempt.coordinates().latitude(),
                                                        "longitude",
                                                        attempt.coordinates().longitude()),
                                        null,
                                        null,
                                        null);
                        payload.put("outcome", "REJECTED");
                        payload.put("error_code", attempt.code().name());
                        actions.append(attempt.id(), attempt.at(), "attendance-service", payload);
                    });
        } catch (RuntimeException unavailable) {
            dropped(attempt);
        }
    }

    private static void dropped(Attempt attempt) {
        dropped(attempt.action(), attempt.code());
    }

    private static void dropped(String action, Code code) {
        // Never include exceptions, messages, coordinates, identities, or receipt keys.
        log.warn("Rejected attendance audit unavailable: action={}, code={}", action, code);
    }

    private record Attempt(
            String id,
            Instant at,
            long lessonId,
            Long groupId,
            Long semesterId,
            Long actorId,
            String actorRole,
            Long studentId,
            String action,
            Code code,
            Coordinates coordinates) {}
}
