package ru.rutcampustrack.schedule.item;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.rutcampustrack.academic.grpc.SemesterResponse;
import ru.rutcampustrack.schedule.contract.dto.item.CreateScheduleItemRequest;
import ru.rutcampustrack.schedule.contract.dto.item.UpdateScheduleItemRequest;
import ru.rutcampustrack.schedule.contract.enums.UserRole;
import ru.rutcampustrack.schedule.contract.enums.WeekType;
import ru.rutcampustrack.schedule.exception.AccessDeniedException;
import ru.rutcampustrack.schedule.exception.ResourceNotFoundException;
import ru.rutcampustrack.schedule.grpc.AcademicGrpcClient;
import ru.rutcampustrack.schedule.item.entity.ScheduleItem;
import ru.rutcampustrack.schedule.item.repository.ScheduleItemRepository;
import ru.rutcampustrack.schedule.lesson.LessonGenerationService;
import ru.rutcampustrack.schedule.security.RequestContext;

import java.time.Clock;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.Objects;

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
    private final LessonGenerationService lessonGenerationService;
    private final Clock clock;

    public ScheduleItemService(ScheduleItemRepository scheduleItemRepository,
                               AcademicGrpcClient academicGrpcClient,
                               RequestContext requestContext,
                               LessonGenerationService lessonGenerationService,
                               Clock clock) {
        this.scheduleItemRepository = scheduleItemRepository;
        this.academicGrpcClient = academicGrpcClient;
        this.requestContext = requestContext;
        this.lessonGenerationService = lessonGenerationService;
        this.clock = clock;
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
        item.setSemesterId(request.semesterId());
        item.setDayOfWeek(request.dayOfWeek());
        item.setLessonNumber(request.lessonNumber());
        item.setStartTime(request.startTime());
        item.setEndTime(request.endTime());
        item.setWeekType(request.weekType());
        item.setRoom(request.room());
        item.setActive(true);
        item.setCreatedAt(OffsetDateTime.now());

        ScheduleItem saved = scheduleItemRepository.save(item);

        // LSSN-01: Synchronous lesson generation in same transaction (D-01)
        WeekType firstWeekType = academicGrpcClient.parseSemesterFirstWeekType(activeSemester);
        lessonGenerationService.generateLessons(
                saved,
                LocalDate.parse(activeSemester.getDateFrom()),
                LocalDate.parse(activeSemester.getDateTo()),
                firstWeekType);

        return saved;
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

        // Capture old values BEFORE applying updates (D-06, D-07)
        boolean scheduleAffected =
                !Objects.equals(existing.getDayOfWeek(), request.dayOfWeek())
                || !Objects.equals(existing.getWeekType(), request.weekType())
                || !Objects.equals(existing.getStartTime(), request.startTime())
                || !Objects.equals(existing.getEndTime(), request.endTime())
                || !Objects.equals(existing.getLessonNumber(), request.lessonNumber());

        existing.setSubjectId(request.subjectId());
        existing.setDayOfWeek(request.dayOfWeek());
        existing.setLessonNumber(request.lessonNumber());
        existing.setStartTime(request.startTime());
        existing.setEndTime(request.endTime());
        existing.setWeekType(request.weekType());
        existing.setRoom(request.room());

        ScheduleItem saved = scheduleItemRepository.save(existing);

        // LSSN-01/LSSN-02: Re-generate future planned lessons if schedule fields changed (D-06)
        if (scheduleAffected) {
            SemesterResponse activeSemester = academicGrpcClient.getActiveSemester();
            WeekType firstWeekType = academicGrpcClient.parseSemesterFirstWeekType(activeSemester);
            LocalDate today = LocalDate.now(clock);
            lessonGenerationService.regenerateFromDate(
                    saved,
                    LocalDate.parse(activeSemester.getDateFrom()),
                    LocalDate.parse(activeSemester.getDateTo()),
                    firstWeekType,
                    today);
        }

        return saved;
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
