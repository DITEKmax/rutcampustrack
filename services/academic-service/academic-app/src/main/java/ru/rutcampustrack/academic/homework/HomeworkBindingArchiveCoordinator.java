package ru.rutcampustrack.academic.homework;

import jakarta.persistence.EntityManager;
import org.springframework.jdbc.core.ConnectionCallback;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import ru.rutcampustrack.academic.contract.enums.HomeworkPublicationState;
import ru.rutcampustrack.academic.entity.Homework;
import ru.rutcampustrack.academic.entity.HomeworkBindingArchiveMarker;
import ru.rutcampustrack.academic.exception.ConflictException;
import ru.rutcampustrack.academic.repository.HomeworkBindingArchiveMarkerRepository;
import ru.rutcampustrack.academic.repository.HomeworkRepository;

import java.sql.PreparedStatement;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * Serializes all Academic mutations for a Schedule binding and keeps its
 * terminal marker consistent with any persisted content.
 */
@Service
public class HomeworkBindingArchiveCoordinator {

    private final JdbcTemplate jdbcTemplate;
    private final EntityManager entityManager;
    private final HomeworkBindingArchiveMarkerRepository markerRepository;
    private final HomeworkRepository homeworkRepository;

    public HomeworkBindingArchiveCoordinator(
            JdbcTemplate jdbcTemplate,
            EntityManager entityManager,
            HomeworkBindingArchiveMarkerRepository markerRepository,
            HomeworkRepository homeworkRepository) {
        this.jdbcTemplate = jdbcTemplate;
        this.entityManager = entityManager;
        this.markerRepository = markerRepository;
        this.homeworkRepository = homeworkRepository;
    }

    /** Must be called before reading publication state or marker state. */
    public void lock(long bindingId) {
        if (bindingId <= 0) {
            throw new IllegalArgumentException("bindingId must be positive");
        }
        if (!TransactionSynchronizationManager.isActualTransactionActive()) {
            throw new IllegalStateException("homework binding lock requires an active transaction");
        }
        jdbcTemplate.execute((ConnectionCallback<Void>) connection -> {
            try (PreparedStatement statement = connection.prepareStatement(
                    "SELECT pg_advisory_xact_lock(?)")) {
                statement.setLong(1, bindingId);
                statement.execute();
            }
            return null;
        });
    }

    /**
     * Acquires the binding lock before refreshing a previously selected entity.
     * Callers must not inspect mutable Homework fields between the selection and
     * this method; refresh prevents the persistence context from flushing a stale
     * publication state over a cancellation committed while the caller waited.
     */
    public void lockAndRefresh(Homework homework) {
        Long bindingId = homework.getBindingId();
        if (bindingId == null || bindingId <= 0) {
            throw new ConflictException("homework has no durable binding identity");
        }
        lock(bindingId);
        entityManager.refresh(homework);
    }

    /** Caller must hold {@link #lock(long)} before looking up the marker. */
    public Optional<HomeworkBindingArchiveMarker> findMarker(
            long bindingId, long actorId, UUID requestKey) {
        Optional<HomeworkBindingArchiveMarker> marker = markerRepository.findById(bindingId);
        marker.ifPresent(found -> requireMarkerIdentity(found, bindingId, actorId, requestKey));
        return marker;
    }

    /** Caller must hold {@link #lock(long)} before creating/updating the marker. */
    public HomeworkBindingArchiveMarker ensureMarker(
            long bindingId, long actorId, UUID requestKey, Long homeworkId) {
        HomeworkBindingArchiveMarker marker = markerRepository.findById(bindingId)
                .orElseGet(() -> new HomeworkBindingArchiveMarker(
                        bindingId, actorId, requestKey, homeworkId));
        requireMarkerIdentity(marker, bindingId, actorId, requestKey);
        if (homeworkId != null) {
            associateHomework(marker, homeworkId);
        }
        return markerRepository.save(marker);
    }

    /**
     * Applies the Schedule cancellation under the same lock used by the
     * publication transactions. Lookup is by binding_id only; event homework_id
     * is an identity assertion, never a fallback lookup key.
     */
    public void archiveCancelledBinding(long bindingId, long actorId, UUID requestKey,
                                        Long eventHomeworkId) {
        lock(bindingId);

        Homework homework = homeworkRepository.findByBindingId(bindingId).orElse(null);
        if (eventHomeworkId != null
                && (homework == null || !eventHomeworkId.equals(homework.getId()))) {
            throw new ConflictException(
                    "homework.binding.archived points to missing or different Academic content");
        }
        if (homework != null) {
            requireHomeworkIdentity(homework, bindingId, actorId, requestKey);
        }

        HomeworkBindingArchiveMarker marker = ensureMarker(
                bindingId, actorId, requestKey,
                homework == null ? null : homework.getId());
        if (homework != null && homework.getPublicationState() != HomeworkPublicationState.ARCHIVED) {
            homework.archivePublication();
            homeworkRepository.save(homework);
        }
        homeworkRepository.flush();
        markerRepository.flush();
    }

    /** Caller must hold the binding lock before recording a direct archive. */
    public void archiveExistingHomework(Homework homework, long bindingId, UUID requestKey) {
        requireHomeworkIdentity(homework, bindingId, homework.getActorId(), requestKey);
        HomeworkBindingArchiveMarker marker = ensureMarker(
                bindingId, homework.getActorId(), requestKey, homework.getId());
        if (homework.getPublicationState() != HomeworkPublicationState.ARCHIVED) {
            homework.archivePublication();
            homeworkRepository.save(homework);
        }
        homeworkRepository.flush();
        markerRepository.flush();
    }

    private void requireMarkerIdentity(HomeworkBindingArchiveMarker marker,
                                       long bindingId, long actorId, UUID requestKey) {
        if (!Objects.equals(marker.getBindingId(), bindingId)
                || !marker.hasIdentity(actorId, requestKey)) {
            throw new ConflictException("homework archive identity does not match the binding");
        }
    }

    private void associateHomework(HomeworkBindingArchiveMarker marker, long homeworkId) {
        try {
            marker.associateHomework(homeworkId);
        } catch (IllegalStateException mismatch) {
            throw new ConflictException("homework archive identity points to different content");
        }
    }

    private void requireHomeworkIdentity(Homework homework, long bindingId,
                                         long actorId, UUID requestKey) {
        if (!Objects.equals(homework.getBindingId(), bindingId)
                || !Objects.equals(homework.getActorId(), actorId)
                || !Objects.equals(homework.getRequestKey(), requestKey)) {
            throw new ConflictException("homework content identity does not match the binding");
        }
    }
}
