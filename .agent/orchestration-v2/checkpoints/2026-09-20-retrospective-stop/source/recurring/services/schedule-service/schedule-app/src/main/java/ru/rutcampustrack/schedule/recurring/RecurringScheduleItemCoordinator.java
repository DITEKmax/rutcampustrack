package ru.rutcampustrack.schedule.recurring;

import org.springframework.stereotype.Service;
import ru.rutcampustrack.academic.grpc.AssignmentInfo;
import ru.rutcampustrack.academic.grpc.GroupResponse;
import ru.rutcampustrack.academic.grpc.SemesterResponse;
import ru.rutcampustrack.schedule.contract.dto.item.CreateScheduleItemRequest;
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

    public RecurringScheduleItemCoordinator(AcademicGrpcClient academicGrpcClient,
                                            RequestContext requestContext,
                                            RecurringScheduleItemWriter writer,
                                            ScheduleItemRepository scheduleItemRepository) {
        this.academicGrpcClient = academicGrpcClient;
        this.requestContext = requestContext;
        this.writer = writer;
        this.scheduleItemRepository = scheduleItemRepository;
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
        RecurringCreateResult result = writer.write(request, requestKey,
                requireActor(), authority, semesterStart, semesterEnd);
        return scheduleItemRepository.findById(result.scheduleItemId())
                .orElseThrow(() -> new RecurringProtocolConflictException("created schedule item disappeared"));
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
                || request.lessonNumber() == null || request.lessonNumber() < 1 || request.lessonNumber() > 8
                || request.startTime() == null || request.endTime() == null
                || !request.endTime().isAfter(request.startTime())
                || request.weekType() == null) {
            throw new RecurringProtocolConflictException("recurring slot/time tuple is invalid");
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
