package ru.rutcampustrack.schedule.lesson;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import jakarta.persistence.EntityManager;
import ru.rutcampustrack.schedule.event.LessonBlockedEvent;
import ru.rutcampustrack.schedule.event.LessonCancelledEvent;
import ru.rutcampustrack.schedule.contract.dto.lesson.CancelLessonRequest;
import ru.rutcampustrack.schedule.contract.dto.lesson.GeoBlockRequest;
import ru.rutcampustrack.schedule.contract.enums.LessonStatus;
import ru.rutcampustrack.schedule.contract.enums.UserRole;
import ru.rutcampustrack.schedule.exception.AccessDeniedException;
import ru.rutcampustrack.schedule.exception.InvalidLessonStateException;
import ru.rutcampustrack.schedule.exception.ResourceNotFoundException;
import ru.rutcampustrack.schedule.grpc.AcademicGrpcClient;
import ru.rutcampustrack.schedule.grpc.ScheduleSemesterArchiveWriteFence;
import ru.rutcampustrack.schedule.item.entity.ScheduleItem;
import ru.rutcampustrack.schedule.item.repository.ScheduleItemRepository;
import ru.rutcampustrack.schedule.lesson.entity.Lesson;
import ru.rutcampustrack.schedule.lesson.repository.LessonRepository;
import ru.rutcampustrack.schedule.security.RequestContext;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Business logic for lesson operations: cancel, restore, geo-block toggle, and schedule view.
 * All write operations require headman authorization via gRPC group ownership check.
 */
@Service
@Transactional
public class LessonService {

    private final LessonRepository lessonRepository;
    private final ScheduleItemRepository scheduleItemRepository;
    private final AcademicGrpcClient academicGrpcClient;
    private final RequestContext requestContext;
    private final ApplicationEventPublisher eventPublisher;
    private final RecurringLessonLifecycleWriter recurringLifecycleWriter;
    private final EntityManager entityManager;
    private final ScheduleSemesterArchiveWriteFence archiveWriteFence;

    @Autowired
    public LessonService(LessonRepository lessonRepository,
                         ScheduleItemRepository scheduleItemRepository,
                         AcademicGrpcClient academicGrpcClient,
                         RequestContext requestContext,
                         ApplicationEventPublisher eventPublisher,
                         RecurringLessonLifecycleWriter recurringLifecycleWriter,
                         EntityManager entityManager,
                         ScheduleSemesterArchiveWriteFence archiveWriteFence) {
        this.lessonRepository = lessonRepository;
        this.scheduleItemRepository = scheduleItemRepository;
        this.academicGrpcClient = academicGrpcClient;
        this.requestContext = requestContext;
        this.eventPublisher = eventPublisher;
        this.recurringLifecycleWriter = recurringLifecycleWriter;
        this.entityManager = entityManager;
        this.archiveWriteFence = archiveWriteFence;
    }

    /**
     * Verifies the current user is either ADMIN or headman of the target group.
     * ADMIN bypasses the check entirely (D-08/D-10).
     */
    private void requireHeadmanForGroup(Long targetGroupId) {
        requireHeadmanForGroup(targetGroupId, null);
    }

    private void requireHeadmanForGroup(Long targetGroupId, String assistantPermission) {
        UserRole role = requestContext.getRole();
        if (role == UserRole.ADMIN) return;
        if (!requestContext.isHeadman()) {
            if (assistantPermission == null
                    || !java.util.Objects.equals(requestContext.getGroupId(), targetGroupId)
                    || !academicGrpcClient.hasAssistantPermission(targetGroupId, assistantPermission)) {
                throw new AccessDeniedException("Only headman or authorized assistant can perform this action");
            }
            return;
        }
        boolean confirmed = academicGrpcClient.isHeadman(requestContext.getUserId(), targetGroupId);
        if (!confirmed) {
            throw new AccessDeniedException("You are not headman of group " + targetGroupId);
        }
    }

    /**
     * M13 G9 — read-доступ к ресурсам группы. STUDENT видит только свою группу;
     * ADMIN/TEACHER — любую (TEACHER ведёт занятия в нескольких группах).
     */
    private void requireGroupReadAccess(Long targetGroupId) {
        UserRole role = requestContext.getRole();
        if (role == UserRole.ADMIN || role == UserRole.TEACHER) {
            return;
        }
        Long ownGroupId = requestContext.getGroupId();
        if (ownGroupId == null || !ownGroupId.equals(targetGroupId)) {
            throw new AccessDeniedException("Расписание принадлежит другой группе");
        }
    }

    /**
     * Resolves a lesson by ID and validates headman ownership for the lesson's group.
     */
    private LessonWithItem findLessonAndValidateGroup(Long lessonId) {
        return findLessonAndValidateGroup(lessonId, null);
    }

    private LessonWithItem findLessonAndValidateGroup(Long lessonId, String assistantPermission) {
        Lesson lesson = lessonRepository.findById(lessonId)
                .orElseThrow(() -> new ResourceNotFoundException("Lesson", "id", lessonId));
        ScheduleItem item = scheduleItemRepository.findById(lesson.getScheduleItemId())
                .orElseThrow(() -> new ResourceNotFoundException("ScheduleItem", "id", lesson.getScheduleItemId()));
        requireHeadmanForGroup(lesson.getGroupId() != null ? lesson.getGroupId() : item.getGroupId(),
                assistantPermission);
        return new LessonWithItem(lesson, item);
    }

    /**
     * Cancels a lesson with a required reason (LSSN-04, D-13).
     *
     * Allowed source statuses: PLANNED, ACTIVE, CLOSED.
     * The CLOSED transition lets HEADMAN/ADMIN ретроспективно отменить уже
     * прошедшую пару — например, чтобы заменить её другой и проставить
     * посещаемость задним числом (UX-требование старосты).
     *
     * Cancelled lessons can be restored via {@link #restoreLesson(Long)}.
     */
    public LessonWithItem cancelLesson(Long lessonId, CancelLessonRequest request) {
        LessonWithItem lwi = findLessonAndValidateGroup(lessonId, "CANCEL_LESSONS");
        Lesson lesson = lwi.lesson();
        ScheduleItem item = lwi.scheduleItem();
        if (lesson.getStatus() == LessonStatus.CANCELLED) {
            throw new InvalidLessonStateException(
                    "Lesson is already cancelled");
        }
        Lesson saved;
        if (lesson.getOccurrenceId() != null && lesson.getScheduleItemId() != null) {
            recurringLifecycleWriter.cancel(lessonId, request.reason(), requestContext.getUserId());
            entityManager.refresh(lesson);
            saved = lesson;
        } else {
            // Legacy non-recurring lessons still use the existing entity path.
            archiveWriteFence.lockForBusinessWrite(lesson.getSemesterId());
            OffsetDateTime cancelledAt = OffsetDateTime.now();
            lesson.setStatus(LessonStatus.CANCELLED);
            lesson.setCancelReason(request.reason());
            lesson.setCancelledBy(requestContext.getUserId());
            lesson.setCancelledAt(cancelledAt);
            saved = lessonRepository.save(lesson);
        }
        java.time.LocalTime startTime = saved.getStartTime() != null
                ? saved.getStartTime() : item.getStartTime();
        java.time.LocalTime endTime = saved.getEndTime() != null
                ? saved.getEndTime() : item.getEndTime();
        eventPublisher.publishEvent(new LessonCancelledEvent(this,
                saved.getId(), saved.getGroupId() != null ? saved.getGroupId() : item.getGroupId(),
                saved.getSubjectId() != null ? saved.getSubjectId() : item.getSubjectId(),
                saved.getDate(), startTime, endTime,
                saved.getLessonNumber() != null ? saved.getLessonNumber().intValue()
                        : (item.getLessonNumber() != null ? item.getLessonNumber().intValue() : null),
                saved.getCancelReason(), saved.getCancelledBy(), saved.getCancelledAt(),
                saved.getSemesterId()));
        return new LessonWithItem(saved, item);
    }

    /**
     * Restores a cancelled lesson (LSSN-05, D-14).
     * Only CANCELLED lessons can be restored — throws 422 for any other status.
     * Restored lesson goes back to PLANNED; the LessonStatusTransitionJob
     * will re-promote it to ACTIVE/CLOSED on the next tick if its time has
     * already passed. Clears cancel_reason on restore.
     */
    public LessonWithItem restoreLesson(Long lessonId) {
        LessonWithItem lwi = findLessonAndValidateGroup(lessonId, "CANCEL_LESSONS");
        Lesson lesson = lwi.lesson();
        if (lesson.getStatus() != LessonStatus.CANCELLED) {
            throw new InvalidLessonStateException(
                    "Only cancelled lessons can be restored, current status: " + lesson.getStatus());
        }
        if (lesson.getOccurrenceId() != null && lesson.getScheduleItemId() != null) {
            long currentLessonId = recurringLifecycleWriter.restore(lessonId, requestContext.getUserId());
            if (currentLessonId == lessonId) {
                entityManager.refresh(lesson);
                return new LessonWithItem(lesson, lwi.scheduleItem());
            }
            entityManager.clear();
            Lesson restored = lessonRepository.findById(currentLessonId)
                    .orElseThrow(() -> new ResourceNotFoundException("Lesson", "id", currentLessonId));
            ScheduleItem restoredItem = scheduleItemRepository.findById(restored.getScheduleItemId())
                    .orElseThrow(() -> new ResourceNotFoundException(
                            "ScheduleItem", "id", restored.getScheduleItemId()));
            return new LessonWithItem(restored, restoredItem);
        }
        archiveWriteFence.lockForBusinessWrite(lesson.getSemesterId());
        lesson.setStatus(LessonStatus.PLANNED);
        lesson.setCancelReason(null);
        // M09 G5 — restore очищает весь audit-tuple cancellation'а.
        lesson.setCancelledBy(null);
        lesson.setCancelledAt(null);
        return new LessonWithItem(lessonRepository.save(lesson), lwi.scheduleItem());
    }

    /**
     * Toggles geo-blocking on a lesson (LSSN-07, D-15).
     * Any headman of the group can toggle — no state restriction.
     */
    public LessonWithItem toggleGeoBlock(Long lessonId, GeoBlockRequest request) {
        LessonWithItem lwi = findLessonAndValidateGroup(lessonId);
        Lesson lesson = lwi.lesson();
        archiveWriteFence.lockForBusinessWrite(lesson.getSemesterId());
        lesson.setGeoBlocked(request.blocked());
        return new LessonWithItem(lessonRepository.save(lesson), lwi.scheduleItem());
    }

    /**
     * Headman hard-lock: blocks geo-checkin on a lesson so that attendance
     * can only be set manually by the headman. Planned and active lessons
     * can be blocked; closed or cancelled lessons reject with 422.
     */
    public LessonWithItem blockLessonByHeadman(Long lessonId) {
        LessonWithItem lwi = findLessonAndValidateGroup(lessonId);
        Lesson lesson = lwi.lesson();
        archiveWriteFence.lockForBusinessWrite(lesson.getSemesterId());
        if (lesson.getStatus() == LessonStatus.CANCELLED) {
            throw new InvalidLessonStateException("Cannot block a cancelled lesson");
        }
        if (lesson.getStatus() != LessonStatus.PLANNED && lesson.getStatus() != LessonStatus.ACTIVE) {
            throw new InvalidLessonStateException(
                    "Only planned or active lessons can be blocked, current status: " + lesson.getStatus());
        }
        lesson.setBlockedByHeadman(true);
        lesson.setBlockedByUserId(requestContext.getUserId());
        lesson.setBlockedAt(OffsetDateTime.now());
        Lesson saved = lessonRepository.save(lesson);
        ScheduleItem item = lwi.scheduleItem();
        eventPublisher.publishEvent(new LessonBlockedEvent(this,
                saved.getId(), item.getGroupId(), item.getSubjectId(),
                saved.getDate(), item.getStartTime(), item.getEndTime(),
                item.getLessonNumber() != null ? item.getLessonNumber().intValue() : null,
                item.getRoom(), saved.getBlockedByUserId(), saved.getBlockedAt()));
        return new LessonWithItem(saved, item);
    }

    /**
     * Headman hard-lock removal: lifts the manual-only attendance mode so
     * students can geo-check-in again.
     */
    public LessonWithItem unblockLessonByHeadman(Long lessonId) {
        LessonWithItem lwi = findLessonAndValidateGroup(lessonId);
        Lesson lesson = lwi.lesson();
        archiveWriteFence.lockForBusinessWrite(lesson.getSemesterId());
        lesson.setBlockedByHeadman(false);
        lesson.setBlockedByUserId(null);
        lesson.setBlockedAt(null);
        return new LessonWithItem(lessonRepository.save(lesson), lwi.scheduleItem());
    }

    /**
     * Returns paginated lessons for a group within a date range (VIEW-01, VIEW-02, D-18).
     * Uses ALL schedule items for the group (no isActive filter — Pitfall 3).
     * Defaults to excluding CANCELLED lessons when status param is null/empty (D-18).
     *
     * <p>M05 D9 / P2-10/5: SQL-pagination через {@code Pageable}. Ранее
     * pagination делалась in-memory (`all.subList(offset, end)`) после
     * загрузки всего дата-range, что создавало OOM-risk на 2000+ lessons/
     * semester. Теперь PostgreSQL применяет LIMIT/OFFSET в плане запроса.
     */
    @Transactional(readOnly = true)
    public Page<LessonWithItem> getLessonsForGroup(Long groupId,
                                                    LocalDate from,
                                                    LocalDate to,
                                                    List<LessonStatus> statuses,
                                                    Pageable pageable) {
        // M13 G9 — STUDENT видит расписание только своей группы
        requireGroupReadAccess(groupId);
        List<String> effectiveStatuses = (statuses == null || statuses.isEmpty())
                ? List.of(LessonStatus.PLANNED.name().toLowerCase(),
                          LessonStatus.ACTIVE.name().toLowerCase(),
                          LessonStatus.CLOSED.name().toLowerCase())
                : statuses.stream().map(s -> s.name().toLowerCase()).toList();

        Page<Lesson> lessonPage = lessonRepository
                .pageByGroupIdAndDateBetweenAndStatusIn(
                        groupId, from, to,
                        effectiveStatuses, pageable);

        List<ScheduleItem> items = scheduleItemRepository.findByGroupId(groupId);

        Map<Long, ScheduleItem> itemMap = items.stream()
                .collect(Collectors.toMap(ScheduleItem::getId, si -> si));

        return lessonPage.map(l -> new LessonWithItem(l, itemMap.get(l.getScheduleItemId())));
    }

}
