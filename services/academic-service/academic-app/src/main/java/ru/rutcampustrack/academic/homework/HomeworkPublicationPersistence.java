package ru.rutcampustrack.academic.homework;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import ru.rutcampustrack.academic.contract.enums.HomeworkPublicationState;
import ru.rutcampustrack.academic.entity.Homework;
import ru.rutcampustrack.academic.event.HomeworkPublishedEvent;
import ru.rutcampustrack.academic.exception.ConflictException;
import ru.rutcampustrack.academic.repository.HomeworkRepository;

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
    private final org.springframework.context.ApplicationEventPublisher eventPublisher;

    public HomeworkPublicationPersistence(HomeworkRepository homeworkRepository,
                                          org.springframework.context.ApplicationEventPublisher eventPublisher) {
        this.homeworkRepository = homeworkRepository;
        this.eventPublisher = eventPublisher;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public Homework persistPending(Long groupId, Long subjectId, Long semesterId,
                                   String title, String description, String link,
                                   Long actorId, LocalDate lessonDate, Integer lessonNumber,
                                   Long bindingId, UUID requestKey, byte[] payloadHash) {
        Optional<Homework> existing = homeworkRepository.findByActorIdAndRequestKey(actorId, requestKey);
        if (existing.isPresent()) {
            Homework homework = existing.get();
            verifyIdentity(homework, actorId, requestKey, payloadHash, bindingId);
            return homework;
        }

        Homework homework = new Homework(
                groupId, subjectId, semesterId, title, description, link,
                actorId, lessonDate, lessonNumber,
                bindingId, actorId, requestKey, payloadHash);
        Homework saved = homeworkRepository.save(homework);
        homeworkRepository.flush();
        return saved;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public Homework activate(long homeworkId, Long actorId, UUID requestKey,
                             Long bindingId, byte[] payloadHash) {
        Homework homework = homeworkRepository.findById(homeworkId)
                .orElseThrow(() -> new ConflictException(
                        "confirmed binding points to missing Academic content"));
        verifyIdentity(homework, actorId, requestKey, payloadHash, bindingId);
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
    public Homework archive(long homeworkId, Long actorId, UUID requestKey, Long bindingId) {
        // actorId is authenticated by Academic's permission guard and by the
        // directed Schedule binding call; archive is intentionally not tied to
        // the original creator's actor_id.
        Homework homework = homeworkRepository.findById(homeworkId)
                .orElseThrow(() -> new ConflictException(
                        "homework content disappeared before terminal archive"));
        if (!requestKey.equals(homework.getRequestKey())
                || !bindingId.equals(homework.getBindingId())) {
            throw new ConflictException("homework archive identity does not match the binding");
        }
        if (homework.getPublicationState() != HomeworkPublicationState.ARCHIVED) {
            homework.archivePublication();
            homework = homeworkRepository.save(homework);
            homeworkRepository.flush();
        }
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
