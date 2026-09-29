package ru.rutcampustrack.attendance.event;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.rutcampustrack.attendance.checkin.AttendanceDocument;
import ru.rutcampustrack.attendance.contract.enums.ExcuseTicketStatus;
import ru.rutcampustrack.attendance.contract.enums.LateCheckinRequestStatus;
import ru.rutcampustrack.attendance.excuse.entity.ExcuseTicket;
import ru.rutcampustrack.attendance.latecheckin.entity.LateCheckinRequest;
import ru.rutcampustrack.attendance.marking.AttendanceAttachmentService;
import ru.rutcampustrack.attendance.student.PairWriteCoordinator;
import ru.rutcampustrack.attendance.studentrequest.entity.RequestAttachmentDocument;
import ru.rutcampustrack.shared.outbox.OutboxStorage;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;

/** Applies a Schedule transfer batch as one local Mongo transaction. */
@Service
public class LessonTransferParticipantService {

    private static final String RESULT_APPLIED = "APPLIED";
    private static final String RESULT_ERROR = "ERROR";

    private final MongoTemplate mongoTemplate;
    private final PairWriteCoordinator pairWriteCoordinator;
    private final AttendanceAttachmentService attachmentService;
    private final OutboxStorage outboxStorage;
    private final ObjectMapper objectMapper;

    public LessonTransferParticipantService(
            MongoTemplate mongoTemplate,
            PairWriteCoordinator pairWriteCoordinator,
            AttendanceAttachmentService attachmentService,
            OutboxStorage outboxStorage,
            ObjectMapper objectMapper) {
        this.mongoTemplate = mongoTemplate;
        this.pairWriteCoordinator = pairWriteCoordinator;
        this.attachmentService = attachmentService;
        this.outboxStorage = outboxStorage;
        this.objectMapper = objectMapper;
    }

    @Transactional(transactionManager = "mongoTransactionManager")
    public void apply(Map<String, Object> envelope) {
        LessonTransferRequestedEvent event = LessonTransferRequestedEvent.parse(envelope);
        Instant now = Instant.now();
        pairWriteCoordinator.lockLessons(
                List.of(event.source().lessonId(), event.target().lessonId()), event.groupId(), now);

        LessonTransferReceiptDocument previous = mongoTemplate.findById(
                event.operationId(), LessonTransferReceiptDocument.class);
        if (previous != null) {
            if (!matches(previous, event)) {
                throw new IllegalArgumentException("lesson transfer operation identity changed on replay");
            }
            return;
        }

        LessonTransferFenceDocument sourceFence = findFence(event.source().lessonId());
        LessonTransferFenceDocument targetFence = findFence(event.target().lessonId());
        if (sourceFence != null || targetFence != null) {
            completeError(event, "SOURCE_STATE_CONFLICT", now);
            return;
        }

        List<AttendanceDocument> sourceMarks = marks(event.source().lessonId());
        List<AttendanceDocument> targetMarks = marks(event.target().lessonId());
        List<RequestAttachmentDocument> sourceAttachments = sourcePairAttachments(event);
        List<RequestAttachmentDocument> targetAttachments = targetPairAttachments(event);
        List<LateCheckinRequest> pendingLateRequests = pendingLateRequests(event);
        List<LateCheckinRequest> targetPendingLateRequests = pendingLateRequests(
                event, event.target().lessonId());
        List<ExcuseTicket> submittedExcuses = submittedExcuses(event);
        List<ExcuseTicket> targetSubmittedExcuses = submittedExcuses(event, event.target().lessonId());

        Set<Long> sourceUsers = new HashSet<>();
        for (AttendanceDocument mark : sourceMarks) {
            if (!validMarkScope(mark, event) || mark.getUserId() == null || mark.getUserId() <= 0
                    || !sourceUsers.add(mark.getUserId())) {
                completeError(event, "SCOPE_MISMATCH", now);
                return;
            }
            if ((mark.getLessonDate() != null && !mark.getLessonDate().equals(event.source().date()))
                    || (mark.getLessonNumber() != null
                    && mark.getLessonNumber() != event.source().lessonNumber())) {
                completeError(event, "INVALID_SNAPSHOT", now);
                return;
            }
        }
        for (AttendanceDocument mark : targetMarks) {
            if (!validMarkScope(mark, event) || mark.getUserId() == null || mark.getUserId() <= 0) {
                completeError(event, "SCOPE_MISMATCH", now);
                return;
            }
            if ((mark.getLessonDate() != null && !mark.getLessonDate().equals(event.target().date()))
                    || (mark.getLessonNumber() != null
                    && mark.getLessonNumber() != event.target().lessonNumber())) {
                completeError(event, "INVALID_SNAPSHOT", now);
                return;
            }
            if (mark.getUserId() != null && sourceUsers.contains(mark.getUserId())) {
                completeError(event, "TARGET_DATA_CONFLICT", now);
                return;
            }
        }
        if (conflictingLateRequestUsers(pendingLateRequests, targetPendingLateRequests)
                || conflictingExcuseUsers(submittedExcuses, targetSubmittedExcuses)
                || conflictingAttachmentOwners(sourceAttachments, targetAttachments)) {
            completeError(event, "TARGET_DATA_CONFLICT", now);
            return;
        }
        if (!validRequestScopes(pendingLateRequests, submittedExcuses, sourceAttachments,
                targetAttachments, event)) {
            completeError(event, "SCOPE_MISMATCH", now);
            return;
        }

        TreeSet<Long> owners = new TreeSet<>();
        sourceMarks.stream().map(AttendanceDocument::getUserId).filter(Objects::nonNull).forEach(owners::add);
        sourceAttachments.stream().map(RequestAttachmentDocument::getOwnerStudentId)
                .filter(Objects::nonNull).forEach(owners::add);
        pendingLateRequests.stream().map(LateCheckinRequest::getStudentId)
                .filter(Objects::nonNull).forEach(owners::add);
        submittedExcuses.stream().map(ExcuseTicket::getStudentId).filter(Objects::nonNull).forEach(owners::add);
        for (Long owner : owners) {
            pairWriteCoordinator.lock(owner, event.source().lessonId(), event.groupId(), now);
            pairWriteCoordinator.lock(owner, event.target().lessonId(), event.groupId(), now);
        }

        if (!sourceMarks.isEmpty()) {
            Query sourceQuery = Query.query(Criteria.where("lesson_id").is(event.source().lessonId()));
            Update move = new Update()
                    .set("lesson_id", event.target().lessonId())
                    .set("lesson_date", event.target().date())
                    .set("lesson_number", event.target().lessonNumber());
            long modified = mongoTemplate.updateMulti(sourceQuery, move, AttendanceDocument.class)
                    .getModifiedCount();
            if (modified != sourceMarks.size()) {
                throw new IllegalStateException("Attendance transfer source changed while lesson fence was held");
            }
        }

        attachmentService.remapLessonAccessKeys(
                event.groupId(), event.source().lessonId(), event.target().lessonId(), sourceAttachments);
        moveMutableRequestReferences(event, pendingLateRequests, submittedExcuses);

        mongoTemplate.insert(LessonTransferFenceDocument.builder()
                .id(Long.toString(event.source().lessonId()))
                .operationId(event.operationId())
                .transferPayloadHash(event.transferPayloadHash())
                .sourceLessonId(event.source().lessonId())
                .targetLessonId(event.target().lessonId())
                .groupId(event.groupId())
                .createdAt(now)
                .build());
        LessonTransferReceiptDocument receipt = receipt(event, RESULT_APPLIED, false, null, now);
        mongoTemplate.insert(receipt);
        enqueueAcknowledgement(receipt);
    }

    private List<AttendanceDocument> marks(long lessonId) {
        return mongoTemplate.find(Query.query(Criteria.where("lesson_id").is(lessonId)),
                AttendanceDocument.class);
    }

    private List<RequestAttachmentDocument> sourcePairAttachments(LessonTransferRequestedEvent event) {
        return pairAttachments(event.source().lessonId());
    }

    private List<RequestAttachmentDocument> targetPairAttachments(LessonTransferRequestedEvent event) {
        return pairAttachments(event.target().lessonId());
    }

    private List<RequestAttachmentDocument> pairAttachments(long lessonId) {
        Query query = Query.query(Criteria.where("request_id")
                .regex("^[1-9][0-9]*:" + lessonId + "$"));
        return mongoTemplate.find(query, RequestAttachmentDocument.class);
    }

    private List<LateCheckinRequest> pendingLateRequests(LessonTransferRequestedEvent event) {
        return pendingLateRequests(event, event.source().lessonId());
    }

    private List<LateCheckinRequest> pendingLateRequests(LessonTransferRequestedEvent event, long lessonId) {
        return mongoTemplate.find(Query.query(Criteria.where("lesson_id").is(lessonId)
                .and("status").is(LateCheckinRequestStatus.PENDING)), LateCheckinRequest.class);
    }

    private List<ExcuseTicket> submittedExcuses(LessonTransferRequestedEvent event) {
        return submittedExcuses(event, event.source().lessonId());
    }

    private List<ExcuseTicket> submittedExcuses(LessonTransferRequestedEvent event, long lessonId) {
        return mongoTemplate.find(Query.query(Criteria.where("lesson_ids").is(lessonId)
                .and("status").is(ExcuseTicketStatus.SUBMITTED)), ExcuseTicket.class);
    }

    private boolean conflictingLateRequestUsers(List<LateCheckinRequest> source,
                                                List<LateCheckinRequest> target) {
        Set<Long> targetUsers = target.stream().map(LateCheckinRequest::getStudentId)
                .filter(Objects::nonNull).collect(java.util.stream.Collectors.toSet());
        return source.stream().map(LateCheckinRequest::getStudentId)
                .filter(Objects::nonNull).anyMatch(targetUsers::contains);
    }

    private boolean conflictingExcuseUsers(List<ExcuseTicket> source, List<ExcuseTicket> target) {
        Set<Long> targetUsers = target.stream().map(ExcuseTicket::getStudentId)
                .filter(Objects::nonNull).collect(java.util.stream.Collectors.toSet());
        return source.stream().map(ExcuseTicket::getStudentId)
                .filter(Objects::nonNull).anyMatch(targetUsers::contains);
    }

    private boolean conflictingAttachmentOwners(List<RequestAttachmentDocument> source,
                                                 List<RequestAttachmentDocument> target) {
        Set<Long> targetOwners = target.stream().map(RequestAttachmentDocument::getOwnerStudentId)
                .filter(Objects::nonNull).collect(java.util.stream.Collectors.toSet());
        return source.stream().map(RequestAttachmentDocument::getOwnerStudentId)
                .filter(Objects::nonNull).anyMatch(targetOwners::contains);
    }

    private boolean validMarkScope(AttendanceDocument mark, LessonTransferRequestedEvent event) {
        return Objects.equals(mark.getGroupId(), event.groupId())
                && (mark.getSemesterId() == null || Objects.equals(mark.getSemesterId(), event.semesterId()));
    }

    private boolean validRequestScopes(List<LateCheckinRequest> lateRequests,
                                       List<ExcuseTicket> excuses,
                                       List<RequestAttachmentDocument> attachments,
                                       List<RequestAttachmentDocument> targetAttachments,
                                       LessonTransferRequestedEvent event) {
        for (LateCheckinRequest request : lateRequests) {
            if (request.getStudentId() == null || request.getStudentId() <= 0
                    || !Objects.equals(request.getGroupId(), event.groupId())
                    || !Objects.equals(request.getSemesterId(), event.semesterId())) return false;
        }
        for (ExcuseTicket ticket : excuses) {
            if (ticket.getStudentId() == null || ticket.getStudentId() <= 0
                    || !Objects.equals(ticket.getGroupId(), event.groupId())
                    || (ticket.getSemesterId() != null
                    && !Objects.equals(ticket.getSemesterId(), event.semesterId()))) return false;
        }
        if (!validAttachmentScopes(attachments, event.groupId(), event.source().lessonId())
                || !validAttachmentScopes(targetAttachments, event.groupId(), event.target().lessonId())) {
            return false;
        }
        return true;
    }

    private boolean validAttachmentScopes(List<RequestAttachmentDocument> attachments,
                                          long groupId,
                                          long lessonId) {
        for (RequestAttachmentDocument attachment : attachments) {
            Long owner = attachment.getOwnerStudentId();
            String expectedKey = owner == null ? null
                    : PairWriteCoordinator.pairId(owner, lessonId);
            if (!Objects.equals(attachment.getGroupId(), groupId)
                    || owner == null || owner <= 0
                    || !Objects.equals(attachment.getRequestId(), expectedKey)) return false;
        }
        return true;
    }

    private void moveMutableRequestReferences(LessonTransferRequestedEvent event,
                                              List<LateCheckinRequest> lateRequests,
                                              List<ExcuseTicket> excuses) {
        for (LateCheckinRequest request : lateRequests) {
            request.setLessonId(event.target().lessonId());
            request.setLessonDate(event.target().date());
            request.setLessonNumber(event.target().lessonNumber());
            request.setSemesterId(event.semesterId());
            // created/updated and decision metadata are historical provenance.
            mongoTemplate.save(request);
        }
        for (ExcuseTicket ticket : excuses) {
            List<Long> lessonIds = ticket.getLessonIds() == null
                    ? new ArrayList<>() : new ArrayList<>(ticket.getLessonIds());
            for (int index = 0; index < lessonIds.size(); index++) {
                if (lessonIds.get(index) == event.source().lessonId()) {
                    lessonIds.set(index, event.target().lessonId());
                }
            }
            ticket.setLessonIds(lessonIds);
            // lesson_snapshots intentionally remain the immutable original request evidence.
            mongoTemplate.save(ticket);
        }
    }

    private LessonTransferFenceDocument findFence(long lessonId) {
        return mongoTemplate.findById(Long.toString(lessonId), LessonTransferFenceDocument.class);
    }

    private boolean matches(LessonTransferReceiptDocument receipt, LessonTransferRequestedEvent event) {
        return Objects.equals(receipt.getRequestKey(), event.requestKey())
                && Objects.equals(receipt.getActorId(), event.actorId())
                && Objects.equals(receipt.getGroupId(), event.groupId())
                && Objects.equals(receipt.getSemesterId(), event.semesterId())
                && Objects.equals(receipt.getOccurrenceId(), event.occurrenceId())
                && Objects.equals(receipt.getTransferPayloadHash(), event.transferPayloadHash())
                && Objects.equals(receipt.getTransferRevision(), event.transferRevision())
                && Objects.equals(receipt.getSourceSnapshot(), LessonTransferSnapshotDocument.from(event.source()))
                && Objects.equals(receipt.getTargetSnapshot(), LessonTransferSnapshotDocument.from(event.target()));
    }

    private void completeError(LessonTransferRequestedEvent event, String errorCode, Instant now) {
        LessonTransferReceiptDocument receipt = receipt(event, RESULT_ERROR, false, errorCode, now);
        mongoTemplate.insert(receipt);
        enqueueAcknowledgement(receipt);
    }

    private LessonTransferReceiptDocument receipt(LessonTransferRequestedEvent event,
                                                 String result,
                                                 boolean retryable,
                                                 String errorCode,
                                                 Instant now) {
        return LessonTransferReceiptDocument.builder()
                .id(event.operationId())
                .requestKey(event.requestKey())
                .actorId(event.actorId())
                .groupId(event.groupId())
                .semesterId(event.semesterId())
                .occurrenceId(event.occurrenceId())
                .transferPayloadHash(event.transferPayloadHash())
                .transferRevision(event.transferRevision())
                .sourceSnapshot(LessonTransferSnapshotDocument.from(event.source()))
                .targetSnapshot(LessonTransferSnapshotDocument.from(event.target()))
                .result(result)
                .retryable(retryable)
                .errorCode(errorCode)
                .createdAt(now)
                .build();
    }

    private void enqueueAcknowledgement(LessonTransferReceiptDocument receipt) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("operation_id", receipt.getId());
        payload.put("participant", "ATTENDANCE");
        payload.put("participant_receipt_id", receipt.getId());
        payload.put("transfer_payload_hash", receipt.getTransferPayloadHash());
        payload.put("group_id", receipt.getGroupId());
        payload.put("semester_id", receipt.getSemesterId());
        payload.put("occurrence_id", receipt.getOccurrenceId());
        payload.put("source_lesson_id", receipt.getSourceSnapshot().getLessonId());
        payload.put("target_lesson_id", receipt.getTargetSnapshot().getLessonId());
        payload.put("transfer_revision", receipt.getTransferRevision());
        payload.put("result", receipt.getResult());
        payload.put("retryable", receipt.getRetryable());
        payload.put("error_code", receipt.getErrorCode());
        payload.put("batch_index", -1);
        try {
            String json = objectMapper.writeValueAsString(
                    EventEnvelope.build("lesson.transfer.participant.applied", payload));
            outboxStorage.save("lesson.transfer.participant.applied", json);
        } catch (JsonProcessingException error) {
            throw new IllegalStateException("Failed to serialize lesson transfer acknowledgement", error);
        }
    }
}
