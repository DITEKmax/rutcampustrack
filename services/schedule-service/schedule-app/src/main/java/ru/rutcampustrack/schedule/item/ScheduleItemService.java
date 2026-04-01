package ru.rutcampustrack.schedule.item;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.rutcampustrack.academic.grpc.SemesterResponse;
import ru.rutcampustrack.schedule.contract.dto.item.CreateScheduleItemRequest;
import ru.rutcampustrack.schedule.contract.dto.item.UpdateScheduleItemRequest;
import ru.rutcampustrack.schedule.contract.enums.UserRole;
import ru.rutcampustrack.schedule.exception.AccessDeniedException;
import ru.rutcampustrack.schedule.exception.ResourceNotFoundException;
import ru.rutcampustrack.schedule.grpc.AcademicGrpcClient;
import ru.rutcampustrack.schedule.item.entity.ScheduleItem;
import ru.rutcampustrack.schedule.item.repository.ScheduleItemRepository;
import ru.rutcampustrack.schedule.security.RequestContext;

import java.time.OffsetDateTime;

/**
 * Business logic for schedule template (ScheduleItem) CRUD.
 * All write operations require headman authorization via gRPC group ownership check.
 */
@Service
@Transactional
public class ScheduleItemService {

    private final ScheduleItemRepository scheduleItemRepository;
    private final AcademicGrpcClient academicGrpcClient;
    private final RequestContext requestContext;

    public ScheduleItemService(ScheduleItemRepository scheduleItemRepository,
                               AcademicGrpcClient academicGrpcClient,
                               RequestContext requestContext) {
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
     * Creates a new schedule template.
     * Validates headman ownership, group existence, and active semester.
     */
    public ScheduleItem createScheduleItem(CreateScheduleItemRequest request) {
        requireHeadmanForGroup(request.groupId());
        academicGrpcClient.validateGroup(request.groupId());
        SemesterResponse activeSemester = academicGrpcClient.getActiveSemester();
        if (activeSemester.getId() != request.semesterId()) {
            throw new IllegalArgumentException(
                    "Semester " + request.semesterId() + " is not the active semester");
        }

        ScheduleItem item = new ScheduleItem();
        item.setGroupId(request.groupId());
        item.setSubjectId(request.subjectId());
        item.setTeacherId(request.teacherId());
        item.setSemesterId(request.semesterId());
        item.setDayOfWeek(request.dayOfWeek());
        item.setLessonNumber(request.lessonNumber());
        item.setStartTime(request.startTime());
        item.setEndTime(request.endTime());
        item.setWeekType(request.weekType());
        item.setRoom(request.room());
        item.setActive(true);
        item.setCreatedAt(OffsetDateTime.now());

        return scheduleItemRepository.save(item);
    }

    /**
     * Returns a single schedule template by ID or throws 404.
     */
    @Transactional(readOnly = true)
    public ScheduleItem getScheduleItem(Long id) {
        return scheduleItemRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("ScheduleItem", "id", id));
    }

    /**
     * Returns active templates for a group and semester, paginated.
     */
    @Transactional(readOnly = true)
    public Page<ScheduleItem> listScheduleItems(Long groupId, Long semesterId, Pageable pageable) {
        return scheduleItemRepository.findByGroupIdAndSemesterIdAndIsActiveTrue(groupId, semesterId, pageable);
    }

    /**
     * Full update (PUT) of a schedule template.
     * groupId and semesterId are immutable — taken from existing entity (D-09).
     */
    public ScheduleItem updateScheduleItem(Long id, UpdateScheduleItemRequest request) {
        ScheduleItem existing = scheduleItemRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("ScheduleItem", "id", id));
        requireHeadmanForGroup(existing.getGroupId());

        existing.setSubjectId(request.subjectId());
        existing.setTeacherId(request.teacherId());
        existing.setDayOfWeek(request.dayOfWeek());
        existing.setLessonNumber(request.lessonNumber());
        existing.setStartTime(request.startTime());
        existing.setEndTime(request.endTime());
        existing.setWeekType(request.weekType());
        existing.setRoom(request.room());

        return scheduleItemRepository.save(existing);
    }

    /**
     * Soft-deletes a schedule template (sets is_active = false).
     * Never physically deletes — consistent with project-wide soft delete convention (D-07).
     */
    public void deleteScheduleItem(Long id) {
        ScheduleItem existing = scheduleItemRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("ScheduleItem", "id", id));
        requireHeadmanForGroup(existing.getGroupId());
        existing.setActive(false);
        scheduleItemRepository.save(existing);
    }
}
