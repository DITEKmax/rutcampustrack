package ru.rutcampustrack.schedule.lesson;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.rutcampustrack.schedule.contract.dto.lesson.CancelLessonRequest;
import ru.rutcampustrack.schedule.contract.dto.lesson.GeoBlockRequest;
import ru.rutcampustrack.schedule.contract.dto.lesson.MassCancelRequest;
import ru.rutcampustrack.schedule.contract.enums.LessonStatus;
import ru.rutcampustrack.schedule.contract.enums.UserRole;
import ru.rutcampustrack.schedule.exception.AccessDeniedException;
import ru.rutcampustrack.schedule.exception.InvalidLessonStateException;
import ru.rutcampustrack.schedule.exception.ResourceNotFoundException;
import ru.rutcampustrack.schedule.grpc.AcademicGrpcClient;
import ru.rutcampustrack.schedule.item.entity.ScheduleItem;
import ru.rutcampustrack.schedule.item.repository.ScheduleItemRepository;
import ru.rutcampustrack.schedule.lesson.entity.Lesson;
import ru.rutcampustrack.schedule.lesson.repository.LessonRepository;
import ru.rutcampustrack.schedule.security.RequestContext;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Business logic for lesson operations: cancel, restore, mass-cancel, geo-block toggle, and schedule view.
 * All write operations require headman authorization via gRPC group ownership check.
 */
@Service
@Transactional
public class LessonService {

    private final LessonRepository lessonRepository;
    private final ScheduleItemRepository scheduleItemRepository;
    private final AcademicGrpcClient academicGrpcClient;
    private final RequestContext requestContext;

    public LessonService(LessonRepository lessonRepository,
                         ScheduleItemRepository scheduleItemRepository,
                         AcademicGrpcClient academicGrpcClient,
                         RequestContext requestContext) {
        this.lessonRepository = lessonRepository;
        this.scheduleItemRepository = scheduleItemRepository;
        this.academicGrpcClient = academicGrpcClient;
        this.requestContext = requestContext;
    }

    /**
     * Verifies the current user is either ADMIN or headman of the target group.
     * ADMIN bypasses the check entirely (D-08/D-10).
     */
    private void requireHeadmanForGroup(Long targetGroupId) {
        UserRole role = requestContext.getRole();
        if (role == UserRole.ADMIN) return;
        if (!requestContext.isHeadman()) {
            throw new AccessDeniedException("Only headman or admin can perform this action");
        }
        boolean confirmed = academicGrpcClient.isHeadman(requestContext.getUserId(), targetGroupId);
        if (!confirmed) {
            throw new AccessDeniedException("You are not headman of group " + targetGroupId);
        }
    }

    /**
     * Resolves a lesson by ID and validates headman ownership for the lesson's group.
     */
    private LessonWithItem findLessonAndValidateGroup(Long lessonId) {
        Lesson lesson = lessonRepository.findById(lessonId)
                .orElseThrow(() -> new ResourceNotFoundException("Lesson", "id", lessonId));
        ScheduleItem item = scheduleItemRepository.findById(lesson.getScheduleItemId())
                .orElseThrow(() -> new ResourceNotFoundException("ScheduleItem", "id", lesson.getScheduleItemId()));
        requireHeadmanForGroup(item.getGroupId());
        return new LessonWithItem(lesson, item);
    }

    /**
     * Cancels a planned lesson with a required reason (LSSN-04, D-13).
     * Only PLANNED lessons can be cancelled — throws 422 for any other status.
     */
    public LessonWithItem cancelLesson(Long lessonId, CancelLessonRequest request) {
        LessonWithItem lwi = findLessonAndValidateGroup(lessonId);
        Lesson lesson = lwi.lesson();
        if (lesson.getStatus() != LessonStatus.PLANNED) {
            throw new InvalidLessonStateException(
                    "Only planned lessons can be cancelled, current status: " + lesson.getStatus());
        }
        lesson.setStatus(LessonStatus.CANCELLED);
        lesson.setCancelReason(request.reason());
        return new LessonWithItem(lessonRepository.save(lesson), lwi.scheduleItem());
    }

    /**
     * Restores a cancelled lesson back to planned (LSSN-05, D-14).
     * Only CANCELLED lessons can be restored — throws 422 for any other status.
     * Clears cancel_reason on restore.
     */
    public LessonWithItem restoreLesson(Long lessonId) {
        LessonWithItem lwi = findLessonAndValidateGroup(lessonId);
        Lesson lesson = lwi.lesson();
        if (lesson.getStatus() != LessonStatus.CANCELLED) {
            throw new InvalidLessonStateException(
                    "Only cancelled lessons can be restored, current status: " + lesson.getStatus());
        }
        lesson.setStatus(LessonStatus.PLANNED);
        lesson.setCancelReason(null);
        return new LessonWithItem(lessonRepository.save(lesson), lwi.scheduleItem());
    }

    /**
     * Mass-cancels all PLANNED lessons for a group within a date range (LSSN-06, D-12).
     * Validates headman ownership BEFORE any repository access.
     * Returns the count of cancelled lessons.
     */
    public int massCancelLessons(MassCancelRequest request) {
        requireHeadmanForGroup(request.groupId());
        List<ScheduleItem> items = scheduleItemRepository.findByGroupId(request.groupId());
        List<Long> itemIds = items.stream().map(ScheduleItem::getId).toList();
        if (itemIds.isEmpty()) {
            return 0;
        }
        List<Lesson> toCancel = lessonRepository.findByScheduleItemIdInAndDateBetweenAndStatusIn(
                itemIds, request.dateFrom(), request.dateTo(), List.of(LessonStatus.PLANNED));
        for (Lesson l : toCancel) {
            l.setStatus(LessonStatus.CANCELLED);
            l.setCancelReason(request.reason());
        }
        lessonRepository.saveAll(toCancel);
        return toCancel.size();
    }

    /**
     * Toggles geo-blocking on a lesson (LSSN-07, D-15).
     * Any headman of the group can toggle — no state restriction.
     */
    public LessonWithItem toggleGeoBlock(Long lessonId, GeoBlockRequest request) {
        LessonWithItem lwi = findLessonAndValidateGroup(lessonId);
        Lesson lesson = lwi.lesson();
        lesson.setGeoBlocked(request.blocked());
        return new LessonWithItem(lessonRepository.save(lesson), lwi.scheduleItem());
    }

    /**
     * Returns paginated lessons for a group within a date range (VIEW-01, VIEW-02, D-18).
     * Uses ALL schedule items for the group (no isActive filter — Pitfall 3).
     * Defaults to excluding CANCELLED lessons when status param is null/empty (D-18).
     * Pagination is applied in-memory after fetching.
     */
    @Transactional(readOnly = true)
    public Page<LessonWithItem> getLessonsForGroup(Long groupId,
                                                    LocalDate from,
                                                    LocalDate to,
                                                    List<LessonStatus> statuses,
                                                    Pageable pageable) {
        List<LessonStatus> effectiveStatuses = (statuses == null || statuses.isEmpty())
                ? List.of(LessonStatus.PLANNED, LessonStatus.ACTIVE, LessonStatus.CLOSED)
                : statuses;

        List<ScheduleItem> items = scheduleItemRepository.findByGroupId(groupId);
        List<Long> itemIds = items.stream().map(ScheduleItem::getId).toList();
        if (itemIds.isEmpty()) {
            return Page.empty(pageable);
        }

        List<Lesson> lessons = lessonRepository.findByScheduleItemIdInAndDateBetweenAndStatusIn(
                itemIds, from, to, effectiveStatuses);

        Map<Long, ScheduleItem> itemMap = items.stream()
                .collect(Collectors.toMap(ScheduleItem::getId, si -> si));

        List<LessonWithItem> all = lessons.stream()
                .map(l -> new LessonWithItem(l, itemMap.get(l.getScheduleItemId())))
                .toList();

        int total = all.size();
        int offset = (int) pageable.getOffset();
        int end = Math.min(offset + pageable.getPageSize(), total);
        List<LessonWithItem> sublist = (offset >= total) ? List.of() : all.subList(offset, end);

        return new PageImpl<>(sublist, pageable, total);
    }
}
