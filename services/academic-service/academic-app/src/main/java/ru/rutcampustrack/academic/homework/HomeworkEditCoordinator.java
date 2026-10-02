package ru.rutcampustrack.academic.homework;

import org.springframework.stereotype.Service;
import ru.rutcampustrack.academic.contract.dto.homework.HomeworkSnapshot;
import ru.rutcampustrack.academic.contract.dto.homework.UpdateHomeworkRequest;
import ru.rutcampustrack.academic.contract.enums.HomeworkBindingMode;
import ru.rutcampustrack.academic.entity.Homework;
import ru.rutcampustrack.academic.exception.BadRequestException;
import ru.rutcampustrack.academic.exception.ConflictException;
import ru.rutcampustrack.academic.exception.ScheduleServiceUnavailableException;
import ru.rutcampustrack.academic.grpc.ScheduleGrpcClient;
import ru.rutcampustrack.academic.security.RequestContext;
import ru.rutcampustrack.schedule.grpc.HomeworkEditReceipt;
import ru.rutcampustrack.schedule.grpc.LessonResponse;
import java.util.Arrays;

/** Orchestrates RPCs outside Academic transactions and owns forward-only recovery. */
@Service
public class HomeworkEditCoordinator {
    private final HomeworkEditPersistence persistence;
    private final HomeworkEditHistory history;
    private final HomeworkScopeValidator scopes;
    private final ScheduleGrpcClient schedule;
    private final RequestContext context;

    public HomeworkEditCoordinator(HomeworkEditPersistence persistence, HomeworkEditHistory history,
                                   HomeworkScopeValidator scopes, ScheduleGrpcClient schedule, RequestContext context) {
        this.persistence = persistence; this.history = history; this.scopes = scopes; this.schedule = schedule; this.context = context;
    }

    public Homework update(long id, UpdateHomeworkRequest request) {
        if (request.requestKey() == null || request.expectedRevision() == null || request.expectedRevision() <= 0) {
            throw new BadRequestException("requestKey", "Изменение ДЗ требует requestKey и expectedRevision");
        }
        Homework replay = persistence.replay(id, request);
        HomeworkEditOperation prior = persistence.find(id, context.getUserId(), request.requestKey());
        if (replay != null) {
            if (prior != null && "FINALIZED".equals(prior.state())) acknowledge(prior);
            return replay;
        }
        if (prior != null) {
            if (!Arrays.equals(prior.hash(), history.commandHash(request))) throw new ConflictException("requestKey использован с другим содержимым");
            if ("CANCELLED".equals(prior.state())) throw new ConflictException("Изменение не было принято; используй новый requestKey");
            if ("FINALIZED".equals(prior.state()) || "ACKNOWLEDGED".equals(prior.state())) {
                // Terminal-aborted edits have no content receipt or update notification.
                acknowledge(prior);
                throw new ConflictException("ДЗ стало архивным до завершения изменения");
            }
            return moveAndFinish(prior);
        }
        if (!request.hasPlacementFields()) return persistence.content(id, request);
        Homework current = persistence.currentForEdit(id);
        HomeworkBindingMode mode = request.bindingMode() == null ? current.getBindingMode() : request.bindingMode();
        var date = request.lessonDate() == null ? current.getLessonDate() : request.lessonDate();
        Integer number = mode == HomeworkBindingMode.DATE ? null
                : request.lessonNumber() == null ? current.getLessonNumber() : request.lessonNumber();
        if (mode == HomeworkBindingMode.DATE && request.lessonNumber() != null
                || mode == HomeworkBindingMode.LESSON && (number == null || number < 1 || number > 8)) {
            throw new BadRequestException("lessonNumber", "LESSON требует номер пары, DATE не допускает номер пары");
        }
        scopes.validate(current.getGroupId(), current.getSubjectId(), current.getSemesterId(), date);
        HomeworkSnapshot desired = new HomeworkSnapshot(request.title(), request.description(), request.link(), mode, date, number);
        boolean placementChanged = current.getBindingMode() != mode || !current.getLessonDate().equals(date)
                || !java.util.Objects.equals(current.getLessonNumber(), number);
        if (!placementChanged) return persistence.content(id, request);
        var binding = schedule.getHomeworkBinding(current.getBindingId());
        Long occurrence = null, lessonRevision = null;
        if (mode == HomeworkBindingMode.LESSON) {
            LessonResponse lesson = schedule.resolveLesson(current.getGroupId(), date, number)
                    .orElseThrow(() -> new BadRequestException("lessonNumber", "На выбранную пару нельзя назначить ДЗ"));
            if (lesson.getGroupId() != current.getGroupId() || lesson.getSubjectId() != current.getSubjectId()
                    || lesson.getSemesterId() != current.getSemesterId() || lesson.getOccurrenceId() <= 0
                    || lesson.getRevision() <= 0 || !lesson.getDate().equals(date.toString()) || lesson.getLessonNumber() != number) {
                throw new BadRequestException("lessonNumber", "Пара не соответствует группе, предмету или семестру ДЗ");
            }
            occurrence = lesson.getOccurrenceId(); lessonRevision = lesson.getRevision();
        }
        return moveAndFinish(persistence.prepare(id, request, desired, binding, occurrence, lessonRevision));
    }

    private Homework moveAndFinish(HomeworkEditOperation operation) {
        HomeworkEditReceipt accepted;
        try { accepted = schedule.moveHomeworkBinding(operation.move()); }
        catch (ScheduleServiceUnavailableException uncertain) { throw uncertain; }
        catch (RuntimeException rejected) {
            // A durable CAS proves rejection; it cannot undo an accepted move.
            HomeworkEditReceipt settled = schedule.abortUnacceptedHomeworkEdit(operation.identity());
            Homework result = persistence.finalizeReceipt(operation, settled);
            if (!"NOT_ACCEPTED".equals(settled.getState())) {
                acknowledge(operation);
                requireApplied(operation);
                return result;
            }
            throw rejected;
        }
        Homework result = persistence.finalizeReceipt(operation, accepted);
        if ("NOT_ACCEPTED".equals(accepted.getState())) throw new ConflictException("Изменение не было принято; используй новый requestKey");
        acknowledge(operation);
        requireApplied(operation);
        return result;
    }

    public void recover(HomeworkEditOperation operation) {
        HomeworkEditReceipt settled = schedule.continueHomeworkEdit(operation.identity())
                .orElseGet(() -> schedule.abortUnacceptedHomeworkEdit(operation.identity()));
        persistence.finalizeReceipt(operation, settled);
        if (!"NOT_ACCEPTED".equals(settled.getState())) acknowledge(operation);
    }

    private void acknowledge(HomeworkEditOperation operation) {
        if ("ACKNOWLEDGED".equals(operation.state())) return;
        schedule.acknowledgeHomeworkEdit(operation.identity());
        persistence.acknowledged(operation.operationId());
    }

    private void requireApplied(HomeworkEditOperation operation) {
        HomeworkEditOperation current = persistence.find(operation.homeworkId(), operation.actorId(), operation.requestKey());
        if ("TERMINAL_ABORTED".equals(current.outcome())) throw new ConflictException("ДЗ стало архивным до завершения изменения");
    }
}
