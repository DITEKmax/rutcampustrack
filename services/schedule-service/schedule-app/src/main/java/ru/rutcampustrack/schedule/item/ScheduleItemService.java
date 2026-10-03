package ru.rutcampustrack.schedule.item;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.rutcampustrack.schedule.contract.dto.item.CreateScheduleItemRequest;
import ru.rutcampustrack.schedule.contract.dto.item.UpdateScheduleItemRequest;
import ru.rutcampustrack.schedule.contract.dto.item.ScheduleItemResponse;
import ru.rutcampustrack.schedule.contract.dto.item.ScheduleItemLifecyclePreviewResponse;
import ru.rutcampustrack.schedule.contract.enums.UserRole;
import ru.rutcampustrack.schedule.exception.AccessDeniedException;
import ru.rutcampustrack.schedule.exception.RecurringLifecycleNotReadyException;
import ru.rutcampustrack.schedule.exception.ResourceNotFoundException;
import ru.rutcampustrack.schedule.grpc.AcademicGrpcClient;
import ru.rutcampustrack.schedule.item.entity.ScheduleItem;
import ru.rutcampustrack.schedule.item.repository.ScheduleItemRepository;
import ru.rutcampustrack.schedule.lesson.LessonGenerationService;
import ru.rutcampustrack.schedule.recurring.RecurringScheduleItemCoordinator;
import ru.rutcampustrack.schedule.security.RequestContext;

import java.time.Clock;
import java.util.UUID;

/**
 * Template access and lifecycle gates. Recurring creation delegates to a
 * non-transactional authority coordinator and its separate local writer.
 */
@Service
public class ScheduleItemService {

    private final ScheduleItemRepository scheduleItemRepository;
    private final AcademicGrpcClient academicGrpcClient;
    private final RequestContext requestContext;
    @SuppressWarnings("unused")
    private final LessonGenerationService lessonGenerationService;
    @SuppressWarnings("unused")
    private final Clock clock;
    private final RecurringScheduleItemCoordinator recurringCoordinator;

    @Autowired
    public ScheduleItemService(ScheduleItemRepository scheduleItemRepository,
                               AcademicGrpcClient academicGrpcClient,
                               RequestContext requestContext,
                               LessonGenerationService lessonGenerationService,
                               Clock clock,
                               RecurringScheduleItemCoordinator recurringCoordinator) {
        this.scheduleItemRepository = scheduleItemRepository;
        this.academicGrpcClient = academicGrpcClient;
        this.requestContext = requestContext;
        this.lessonGenerationService = lessonGenerationService;
        this.clock = clock;
        this.recurringCoordinator = recurringCoordinator;
    }

    /** Compatibility constructor for narrow pre-recurring security fixtures. */
    public ScheduleItemService(ScheduleItemRepository scheduleItemRepository,
                               AcademicGrpcClient academicGrpcClient,
                               RequestContext requestContext,
                               LessonGenerationService lessonGenerationService,
                               Clock clock) {
        this(scheduleItemRepository, academicGrpcClient, requestContext,
                lessonGenerationService, clock, null);
    }

    private void requireHeadmanForGroup(Long targetGroupId) {
        UserRole role = requestContext.getRole();
        if (role == UserRole.ADMIN) return;
        if (!requestContext.isHeadman()) {
            throw new AccessDeniedException("Only headman or admin can perform this action");
        }
        Long actor = requestContext.getUserId();
        if (actor == null || !academicGrpcClient.isHeadman(actor, targetGroupId)) {
            throw new AccessDeniedException("You are not headman of group " + targetGroupId);
        }
    }

    private void requireGroupReadAccess(Long targetGroupId) {
        UserRole role = requestContext.getRole();
        if (role == UserRole.ADMIN || role == UserRole.TEACHER) return;
        Long ownGroupId = requestContext.getGroupId();
        if (ownGroupId == null || !ownGroupId.equals(targetGroupId)) {
            throw new AccessDeniedException("Шаблон расписания принадлежит другой группе");
        }
    }

    /** Legacy direct entrypoint is retained only as a fail-closed gate. */
    public ScheduleItem createScheduleItem(CreateScheduleItemRequest request) {
        requireHeadmanForGroup(request.groupId());
        throw new RecurringLifecycleNotReadyException("recurring create requires Idempotency-Key");
    }

    public ScheduleItem createScheduleItem(CreateScheduleItemRequest request, UUID idempotencyKey) {
        if (recurringCoordinator == null) {
            requireHeadmanForGroup(request.groupId());
            throw new RecurringLifecycleNotReadyException("recurring coordinator unavailable");
        }
        return recurringCoordinator.create(request, idempotencyKey);
    }

    @Transactional(readOnly = true)
    public ScheduleItem getScheduleItem(Long id) {
        ScheduleItem item = scheduleItemRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("ScheduleItem", "id", id));
        requireGroupReadAccess(item.getGroupId());
        return item;
    }

    @Transactional(readOnly = true)
    public Page<ScheduleItem> listScheduleItems(Long groupId, Long semesterId, Pageable pageable) {
        requireGroupReadAccess(groupId);
        return scheduleItemRepository.findByGroupIdAndSemesterIdAndIsActiveTrue(groupId, semesterId, pageable);
    }

    public ScheduleItemResponse updateScheduleItem(Long id, UpdateScheduleItemRequest request, UUID key) {
        return recurringCoordinator.update(id, request, key);
    }

    public void deleteScheduleItem(Long id, UUID key, String revision) { recurringCoordinator.delete(id, key, revision); }

    public ScheduleItemLifecyclePreviewResponse previewScheduleItem(Long id, UpdateScheduleItemRequest request, boolean delete) {
        return recurringCoordinator.preview(id, request, delete);
    }

    public ScheduleItemResponse createReplayResponse(UUID key) { return recurringCoordinator.createReplayResponse(key); }

    /**
     * Legacy direct entrypoint requires the canonical replay and preview contract. Resource and
     * authorization checks happen first so a caller cannot use the gate to
     * probe a foreign/nonexistent template.
     */
    public ScheduleItem updateScheduleItem(Long id, UpdateScheduleItemRequest request) {
        ScheduleItem existing = scheduleItemRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("ScheduleItem", "id", id));
        requireHeadmanForGroup(existing.getGroupId());
        throw new RecurringLifecycleNotReadyException("updateScheduleItem");
    }

    /** See {@link #updateScheduleItem(Long, UpdateScheduleItemRequest)}. */
    public void deleteScheduleItem(Long id) {
        ScheduleItem existing = scheduleItemRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("ScheduleItem", "id", id));
        requireHeadmanForGroup(existing.getGroupId());
        throw new RecurringLifecycleNotReadyException("deleteScheduleItem");
    }
}
