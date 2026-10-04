package ru.rutcampustrack.schedule.recurring;

import org.springframework.stereotype.Service;
import org.springframework.beans.factory.annotation.Autowired;
import ru.rutcampustrack.academic.grpc.AssignmentInfo;
import ru.rutcampustrack.academic.grpc.GroupResponse;
import ru.rutcampustrack.academic.grpc.SemesterResponse;
import ru.rutcampustrack.schedule.contract.dto.item.CreateScheduleItemRequest;
import ru.rutcampustrack.schedule.contract.dto.item.UpdateScheduleItemRequest;
import ru.rutcampustrack.schedule.contract.dto.item.ScheduleItemResponse;
import ru.rutcampustrack.schedule.contract.dto.item.ScheduleItemLifecyclePreviewResponse;
import ru.rutcampustrack.schedule.grpc.ScheduleSemesterArchiveWriteFence;
import ru.rutcampustrack.schedule.exception.ResourceNotFoundException;
import ru.rutcampustrack.schedule.contract.enums.UserRole;
import ru.rutcampustrack.schedule.exception.AccessDeniedException;
import ru.rutcampustrack.schedule.exception.RecurringProtocolConflictException;
import ru.rutcampustrack.schedule.grpc.AcademicGrpcClient;
import ru.rutcampustrack.schedule.item.entity.ScheduleItem;
import ru.rutcampustrack.schedule.item.repository.ScheduleItemRepository;
import ru.rutcampustrack.schedule.security.RequestContext;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import java.util.Map;
import java.util.LinkedHashMap;

/**
 * Non-transactional authority/authentication coordinator. The separate writer
 * bean starts the local transaction only after all remote checks complete.
 */
@Service
public class RecurringScheduleItemCoordinator {

    private final AcademicGrpcClient academicGrpcClient;
    private final RequestContext requestContext;
    private final RecurringScheduleItemWriter writer;
    private final ScheduleItemRepository scheduleItemRepository;

    private final RecurringScheduleItemLifecycleWriter lifecycle;
    private final ScheduleSemesterArchiveWriteFence archiveFence;

    @Autowired
    public RecurringScheduleItemCoordinator(AcademicGrpcClient academicGrpcClient,
                                            RequestContext requestContext,
                                            RecurringScheduleItemWriter writer,
                                            ScheduleItemRepository scheduleItemRepository,
                                            RecurringScheduleItemLifecycleWriter lifecycle,
                                            ScheduleSemesterArchiveWriteFence archiveFence) {
        this.academicGrpcClient = academicGrpcClient;
        this.requestContext = requestContext;
        this.writer = writer;
        this.scheduleItemRepository = scheduleItemRepository;
        this.lifecycle = lifecycle;
        this.archiveFence = archiveFence;
    }

    public RecurringScheduleItemCoordinator(AcademicGrpcClient academicGrpcClient, RequestContext requestContext,
            RecurringScheduleItemWriter writer, ScheduleItemRepository repository) {
        this(academicGrpcClient, requestContext, writer, repository, null, null);
    }

    public ScheduleItem create(CreateScheduleItemRequest request, UUID requestKey) {
        if (request == null || request.groupId() == null || request.groupId() <= 0) {
            throw new RecurringProtocolConflictException("groupId is required and must be positive");
        }
        requireHeadmanForGroup(request.groupId());
        if (request.assignmentId() == null || request.assignmentId() <= 0) {
            throw new RecurringProtocolConflictException("assignmentId must be positive");
        }
        GroupResponse group = academicGrpcClient.validateGroup(request.groupId());
        if (group == null || group.getId() <= 0 || group.getId() != request.groupId()) {
            throw new RecurringProtocolConflictException("group authority response is inconsistent");
        }
        if (!group.getIsActive()) {
            throw new RecurringProtocolConflictException("group is inactive");
        }
        if (request.subjectId() == null || request.semesterId() == null) {
            throw new RecurringProtocolConflictException("subjectId and semesterId are required");
        }
        SemesterResponse semester = academicGrpcClient.getActiveSemester();
        if (semester == null || semester.getId() <= 0 || semester.getId() != request.semesterId()) {
            throw new RecurringProtocolConflictException("semester is not active");
        }
        List<AssignmentInfo> assignments = academicGrpcClient
                .getAssignmentsByIds(List.of(request.assignmentId()));
        if (assignments == null || assignments.size() != 1 || assignments.get(0) == null
                || assignments.get(0).getId() != request.assignmentId()) {
            throw new RecurringProtocolConflictException("assignment authority response is incomplete");
        }
        AssignmentInfo raw = assignments.get(0);
        RecurringAssignmentAuthority authority = toAuthority(raw);
        assertRequestMatches(request, authority);
        LocalDate semesterStart = parseDate(semester.getDateFrom(), "semester date_from");
        LocalDate semesterEnd = parseDate(semester.getDateTo(), "semester date_to");
        if (authority.semesterId() != semester.getId()
                || !semesterStart.isBefore(semesterEnd.plusDays(1))) {
            throw new RecurringProtocolConflictException("semester authority is inconsistent");
        }
        RecurringCreateResult result;
        if (lifecycle == null) {
            result = writer.write(request, requestKey, requireActor(), authority, semesterStart, semesterEnd);
        } else {
            var prepared = archiveFence.prepareBusinessWrite(request.semesterId());
            Long inactive = writer.inactiveTemplateId(request);
            if (inactive != null) {
                result = lifecycle.reactivate(inactive, request, requestKey, requireActor(),
                        authorities(inactive), semesterStart, semesterEnd, prepared);
            } else {
                result = writer.writePrepared(request, requestKey, requireActor(), authority, semesterStart, semesterEnd, prepared);
            }
        }
        return scheduleItemRepository.findById(result.scheduleItemId())
                .orElseThrow(() -> new RecurringProtocolConflictException("created schedule item disappeared"));
    }

    public ScheduleItemLifecyclePreviewResponse preview(long id, UpdateScheduleItemRequest request, boolean delete) {
        ScheduleItem item = authorizeItem(id);
        SemesterResponse semester = activeSemester(item);
        archiveFence.prepareBusinessWrite(item.getSemesterId());
        return lifecycle.preview(id, request, delete, authorities(id), parseDate(semester.getDateFrom(), "semester from"),
                parseDate(semester.getDateTo(), "semester to"));
    }

    public ScheduleItemResponse update(long id, UpdateScheduleItemRequest request, UUID key) {
        return mutate(id, request, false, request == null ? null : request.expectedRevision(), key);
    }

    public void delete(long id, UUID key, String revision) { mutate(id, null, true, revision, key); }

    private ScheduleItemResponse mutate(long id, UpdateScheduleItemRequest request, boolean delete, String revision, UUID key) {
        ScheduleItem item = authorizeItem(id);
        SemesterResponse semester = activeSemester(item);
        var prepared = archiveFence.prepareBusinessWrite(item.getSemesterId());
        return lifecycle.mutate(id, request, delete, revision, key, requireActor(), authorities(id),
                parseDate(semester.getDateFrom(), "semester from"), parseDate(semester.getDateTo(), "semester to"), prepared);
    }

    public ScheduleItemResponse createReplayResponse(UUID key) {
        return lifecycle == null ? null : lifecycle.createReplayResponse(requireActor(), key);
    }

    private ScheduleItem authorizeItem(long id) {
        ScheduleItem item = scheduleItemRepository.findById(id).orElseThrow(() -> new ResourceNotFoundException("ScheduleItem", "id", id));
        requireHeadmanForGroup(item.getGroupId());
        GroupResponse group = academicGrpcClient.validateGroup(item.getGroupId());
        if (group == null || group.getId() != item.getGroupId() || !group.getIsActive()) {
            throw new RecurringProtocolConflictException("group is inactive or inconsistent");
        }
        return item;
    }

    private SemesterResponse activeSemester(ScheduleItem item) {
        SemesterResponse semester = academicGrpcClient.getActiveSemester();
        if (semester == null || semester.getId() != item.getSemesterId()) {
            throw new RecurringProtocolConflictException("semester is not active");
        }
        return semester;
    }

    private Map<Long, RecurringAssignmentAuthority> authorities(long itemId) {
        List<Long> ids = lifecycle.assignmentIds(itemId);
        List<AssignmentInfo> raw = academicGrpcClient.getAssignmentsByIds(ids);
        Map<Long, RecurringAssignmentAuthority> result = new LinkedHashMap<>();
        if (raw == null) throw new RecurringProtocolConflictException("assignment authority response is incomplete");
        for (AssignmentInfo assignment : raw) {
            if (assignment == null || !ids.contains(assignment.getId()) || result.containsKey(assignment.getId())) {
                throw new RecurringProtocolConflictException("assignment authority response is inconsistent");
            }
            result.put(assignment.getId(), toAuthority(assignment));
        }
        if (result.size() != ids.size()) throw new RecurringProtocolConflictException("assignment authority response is incomplete");
        return result;
    }

    private long requireActor() {
        Long actor = requestContext.getUserId();
        if (actor == null || actor <= 0) {
            throw new AccessDeniedException("Authenticated actor is required");
        }
        return actor;
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

    private static RecurringAssignmentAuthority toAuthority(AssignmentInfo raw) {
        try {
            return new RecurringAssignmentAuthority(raw.getId(), raw.getTeacherId(), raw.getSubjectId(),
                    raw.getGroupId(), raw.getSemesterId(), raw.getLessonType(),
                    parseDate(raw.getValidFrom(), "assignment valid_from"),
                    parseDate(raw.getValidUntilExclusive(), "assignment valid_until_exclusive"));
        } catch (RuntimeException ex) {
            throw new RecurringProtocolConflictException("assignment authority is invalid: " + ex.getMessage());
        }
    }

    private static void assertRequestMatches(CreateScheduleItemRequest request,
                                             RecurringAssignmentAuthority authority) {
        if (request.groupId() == null || request.subjectId() == null || request.semesterId() == null
                || request.groupId() != authority.groupId()
                || request.subjectId() != authority.subjectId()
                || request.semesterId() != authority.semesterId()) {
            throw new RecurringProtocolConflictException("request tuple conflicts with assignment authority");
        }
        if (request.dayOfWeek() == null || request.dayOfWeek() < 1 || request.dayOfWeek() > 7
                || request.weekType() == null) {
            throw new RecurringProtocolConflictException("recurring slot/time tuple is invalid");
        }
        ru.rutcampustrack.schedule.contract.enums.LessonSlot.forNumber(request.lessonNumber());
        if ((request.startTime() == null) != (request.endTime() == null)) {
            throw new ru.rutcampustrack.schedule.contract.enums.LessonSlot.ValidationException(
                    "Время начала и окончания пары нужно передать вместе");
        }
    }

    private static LocalDate parseDate(String raw, String field) {
        try {
            if (raw == null || raw.isBlank()) throw new IllegalArgumentException("blank");
            return LocalDate.parse(raw);
        } catch (RuntimeException ex) {
            throw new RecurringProtocolConflictException(field + " is invalid");
        }
    }
}
