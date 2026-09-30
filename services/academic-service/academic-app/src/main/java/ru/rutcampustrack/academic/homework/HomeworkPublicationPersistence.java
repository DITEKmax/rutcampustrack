package ru.rutcampustrack.academic.homework;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import ru.rutcampustrack.academic.contract.enums.HomeworkPublicationState;
import ru.rutcampustrack.academic.entity.Homework;
import ru.rutcampustrack.academic.entity.HomeworkBindingArchiveMarker;
import ru.rutcampustrack.academic.event.HomeworkPublishedEvent;
import ru.rutcampustrack.academic.exception.ConflictException;
import ru.rutcampustrack.academic.repository.HomeworkBindingArchiveMarkerRepository;
import ru.rutcampustrack.academic.repository.HomeworkRepository;
import ru.rutcampustrack.academic.semester.AcademicSemesterArchiveBarrierTransaction;

import java.time.LocalDate;
import java.util.Arrays;
import java.util.Optional;
import java.util.UUID;

/**
 * Owns the two Academic database boundaries of the reserve/confirm protocol.
 * A gRPC call to Schedule must never run inside either transaction: the
 * pending content is committed before confirmation, and activation is a new
 * transaction after confirmation succeeds (or is replayed).
 */
@Service
public class HomeworkPublicationPersistence {

    private final HomeworkRepository homeworkRepository;
    private final HomeworkBindingArchiveMarkerRepository archiveMarkerRepository;
    private final HomeworkBindingArchiveCoordinator archiveCoordinator;
    private final HomeworkBindingTransferCoordinator transferCoordinator;
    private final org.springframework.context.ApplicationEventPublisher eventPublisher;
    private final AcademicSemesterArchiveBarrierTransaction archiveBarrier;

    public HomeworkPublicationPersistence(HomeworkRepository homeworkRepository,
                                          HomeworkBindingArchiveMarkerRepository archiveMarkerRepository,
                                          HomeworkBindingArchiveCoordinator archiveCoordinator,
                                          HomeworkBindingTransferCoordinator transferCoordinator,
                                          org.springframework.context.ApplicationEventPublisher eventPublisher,
                                          AcademicSemesterArchiveBarrierTransaction archiveBarrier) {
        this.homeworkRepository = homeworkRepository;
        this.archiveMarkerRepository = archiveMarkerRepository;
        this.archiveCoordinator = archiveCoordinator;
        this.transferCoordinator = transferCoordinator;
        this.eventPublisher = eventPublisher;
        this.archiveBarrier = archiveBarrier;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public Homework persistPending(Long groupId, Long subjectId, Long semesterId,
                                   String title, String description, String link,
                                   Long actorId, LocalDate lessonDate, Integer lessonNumber,
                                   Long bindingId, UUID requestKey, byte[] payloadHash) {
        archiveBarrier.admitPendingPublication(requestKey, semesterId, bindingId, actorId, payloadHash);
        archiveCoordinator.lock(bindingId);
        Optional<HomeworkBindingArchiveMarker> terminalMarker = archiveCoordinator.findMarker(
                bindingId, actorId, requestKey);
        Optional<Homework> existing = homeworkRepository.findByActorIdAndRequestKey(actorId, requestKey);
        if (existing.isPresent()) {
            Homework homework = existing.get();
            verifyIdentity(homework, actorId, requestKey, payloadHash, bindingId);
            transferCoordinator.applyPendingMarker(homework, bindingId, actorId, requestKey, payloadHash);
            if (terminalMarker.isPresent()) {
                archiveCoordinator.ensureMarker(bindingId, actorId, requestKey, homework.getId(),
                        semesterId, terminalMarker.get().getSourceEventId());
                if (homework.getPublicationState() != HomeworkPublicationState.ARCHIVED) {
                    homework.archivePublication();
                    homeworkRepository.save(homework);
                }
                archiveMarkerRepository.flush();
            }
            homeworkRepository.save(homework);
            homeworkRepository.flush();
            transferCoordinator.associateMaterialized(bindingId, actorId, requestKey, payloadHash,
                    homework.getId(), homework.getGroupId(), homework.getSubjectId(), homework.getSemesterId());
            return homework;
        }
        if (homeworkRepository.existsByBindingId(bindingId)) {
            throw new ConflictException("binding already points to different Academic content");
        }

        Homework homework = new Homework(
                groupId, subjectId, semesterId, title, description, link,
                actorId, lessonDate, lessonNumber,
                bindingId, actorId, requestKey, payloadHash);
        transferCoordinator.applyPendingMarker(homework, bindingId, actorId, requestKey, payloadHash);
        if (terminalMarker.isPresent()) {
            // Cancellation can commit before the corresponding publication
            // content exists. Preserve the original command as history, already
            // terminal, so retries cannot create a fresh active publication.
            homework.archivePublication();
        }
        Homework saved = homeworkRepository.save(homework);
        homeworkRepository.flush();
        transferCoordinator.associateMaterialized(bindingId, actorId, requestKey, payloadHash,
                saved.getId(), saved.getGroupId(), saved.getSubjectId(), saved.getSemesterId());
        if (terminalMarker.isPresent()) {
            archiveCoordinator.ensureMarker(bindingId, actorId, requestKey, saved.getId(),
                    semesterId, terminalMarker.get().getSourceEventId());
            archiveMarkerRepository.flush();
        }
        return saved;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public Homework activate(long homeworkId, Long actorId, UUID requestKey,
                             Long bindingId, byte[] payloadHash) {
        Homework before = homeworkRepository.findById(homeworkId)
                .orElseThrow(() -> new ConflictException("confirmed binding points to missing Academic content"));
        archiveBarrier.admitPendingPublication(requestKey, before.getSemesterId(), bindingId,
                actorId, payloadHash);
        archiveCoordinator.lock(bindingId);
        Homework homework = homeworkRepository.findById(homeworkId)
                .orElseThrow(() -> new ConflictException(
                        "confirmed binding points to missing Academic content"));
        verifyIdentity(homework, actorId, requestKey, payloadHash, bindingId);
        Optional<HomeworkBindingArchiveMarker> terminalMarker = archiveCoordinator.findMarker(
                bindingId, actorId, requestKey);
        if (terminalMarker.isPresent()) {
            archiveCoordinator.ensureMarker(bindingId, actorId, requestKey, homework.getId(),
                    homework.getSemesterId(), terminalMarker.get().getSourceEventId());
            if (homework.getPublicationState() != HomeworkPublicationState.ARCHIVED) {
                homework.archivePublication();
                homeworkRepository.save(homework);
            }
            homeworkRepository.flush();
            archiveMarkerRepository.flush();
            return homework;
        }
        if (homework.getPublicationState() == HomeworkPublicationState.ARCHIVED) {
            throw new ConflictException("archived homework cannot be activated");
        }
        if (homework.getPublicationState() == HomeworkPublicationState.PENDING) {
            homework.activatePublication();
            homework = homeworkRepository.save(homework);
            homeworkRepository.flush();
            eventPublisher.publishEvent(new HomeworkPublishedEvent(
                    this, homework.getId(), homework.getGroupId(), homework.getSubjectId(),
                    homework.getTitle(), homework.getDescription(), homework.getLink(),
                    homework.getLessonDate().toString(), homework.getLessonNumber()));
        }
        return homework;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public Homework currentPublication(long homeworkId, Long actorId, UUID requestKey,
                                       Long bindingId, byte[] payloadHash) {
        Homework before = homeworkRepository.findById(homeworkId)
                .orElseThrow(() -> new ConflictException("homework binding points to missing Academic content"));
        archiveBarrier.lockOrdinaryWrite(before.getSemesterId());
        archiveCoordinator.lock(bindingId);
        Homework homework = homeworkRepository.findById(homeworkId)
                .orElseThrow(() -> new ConflictException(
                        "homework binding points to missing Academic content"));
        verifyIdentity(homework, actorId, requestKey, payloadHash, bindingId);

        Optional<HomeworkBindingArchiveMarker> terminalMarker = archiveCoordinator.findMarker(
                bindingId, actorId, requestKey);
        if (terminalMarker.isPresent()) {
            archiveCoordinator.ensureMarker(bindingId, actorId, requestKey, homework.getId(),
                    homework.getSemesterId(), terminalMarker.get().getSourceEventId());
            if (homework.getPublicationState() != HomeworkPublicationState.ARCHIVED) {
                homework.archivePublication();
                homeworkRepository.save(homework);
            }
            homeworkRepository.flush();
            archiveMarkerRepository.flush();
        }
        return homework;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public Homework archive(long homeworkId, Long actorId, UUID requestKey, Long bindingId) {
        // actorId is authenticated by Academic's permission guard and by the
        // directed Schedule binding call; archive is intentionally not tied to
        // the original creator's actor_id.
        Homework before = homeworkRepository.findById(homeworkId)
                .orElseThrow(() -> new ConflictException(
                        "homework content disappeared before terminal archive"));
        if (archiveBarrier.mustWaitForTrackedArchiveEffect(before.getSemesterId())) {
            return before;
        }
        archiveBarrier.lockOrdinaryWrite(before.getSemesterId());
        archiveCoordinator.lock(bindingId);
        Homework homework = homeworkRepository.findById(homeworkId)
                .orElseThrow(() -> new ConflictException(
                        "homework content disappeared before terminal archive"));
        if (!requestKey.equals(homework.getRequestKey())
                || !bindingId.equals(homework.getBindingId())) {
            throw new ConflictException("homework archive identity does not match the binding");
        }
        archiveCoordinator.archiveExistingHomework(homework, bindingId, requestKey);
        return homework;
    }

    @Transactional(readOnly = true)
    public Optional<Homework> findByActorAndRequest(Long actorId, UUID requestKey) {
        return homeworkRepository.findByActorIdAndRequestKey(actorId, requestKey);
    }

    private static void verifyIdentity(Homework homework, Long actorId, UUID requestKey,
                                      byte[] payloadHash, Long bindingId) {
        if (!actorId.equals(homework.getActorId())
                || !requestKey.equals(homework.getRequestKey())
                || !bindingId.equals(homework.getBindingId())
                || !Arrays.equals(payloadHash, homework.getPayloadHash())) {
            throw new ConflictException("idempotency key resolves to another homework content");
        }
    }
}
