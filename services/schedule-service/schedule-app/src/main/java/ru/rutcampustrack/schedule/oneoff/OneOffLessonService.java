package ru.rutcampustrack.schedule.oneoff;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.rutcampustrack.academic.grpc.SemesterResponse;
import ru.rutcampustrack.schedule.contract.dto.oneoff.CreateOneOffLessonRequest;
import ru.rutcampustrack.schedule.contract.enums.UserRole;
import ru.rutcampustrack.schedule.contract.enums.WeekType;
import ru.rutcampustrack.schedule.exception.AccessDeniedException;
import ru.rutcampustrack.schedule.exception.ConflictException;
import ru.rutcampustrack.schedule.exception.ResourceNotFoundException;
import ru.rutcampustrack.schedule.grpc.AcademicGrpcClient;
import ru.rutcampustrack.schedule.item.repository.ScheduleItemRepository;
import ru.rutcampustrack.schedule.oneoff.entity.OneOffLesson;
import ru.rutcampustrack.schedule.oneoff.repository.OneOffLessonRepository;
import ru.rutcampustrack.schedule.security.RequestContext;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.time.temporal.TemporalAdjusters;
import java.util.List;

/**
 * Business logic for one-off lesson CRUD (Phase 60-03).
 *
 * Implements:
 *  - D-08: one-off can be inserted on any date (past/today/future).
 *  - D-09: POST returns 409 if an active template slot occupies (group, date, lesson).
 *  - D-11: only headman of the group (or ADMIN) can mutate.
 *  - D-22: DELETE allowed on any date, including past.
 *  - D-23: semester_id auto-resolved by the date (active semester used; out-of-range rejected).
 *  - UNIQUE(group_id, date, lesson_number) DB constraint → 409 on duplicate.
 *
 * Events (lesson.one_off.created / lesson.one_off.cancelled) are intentionally NOT
 * published here — they land in plan 60-04.
 */
@Service
@Transactional
public class OneOffLessonService {

    private final OneOffLessonRepository oneOffLessonRepository;
    private final ScheduleItemRepository scheduleItemRepository;
    private final AcademicGrpcClient academicGrpcClient;
    private final RequestContext requestContext;

    public OneOffLessonService(OneOffLessonRepository oneOffLessonRepository,
                               ScheduleItemRepository scheduleItemRepository,
                               AcademicGrpcClient academicGrpcClient,
                               RequestContext requestContext) {
        this.oneOffLessonRepository = oneOffLessonRepository;
        this.scheduleItemRepository = scheduleItemRepository;
        this.academicGrpcClient = academicGrpcClient;
        this.requestContext = requestContext;
    }

    /**
     * Verifies the current user is either ADMIN or headman of the target group (D-11).
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
     * Creates a one-off lesson after RBAC, semester lookup (D-23), and conflict check (D-09).
     */
    public OneOffLesson createOneOffLesson(CreateOneOffLessonRequest request) {
        requireHeadmanForGroup(request.groupId());
        academicGrpcClient.validateGroup(request.groupId());

        // D-23: semester is resolved automatically by date.
        SemesterResponse activeSemester = academicGrpcClient.getActiveSemester();
        LocalDate semesterFrom = LocalDate.parse(activeSemester.getDateFrom());
        LocalDate semesterTo = LocalDate.parse(activeSemester.getDateTo());
        LocalDate date = request.date();
        if (date.isBefore(semesterFrom) || date.isAfter(semesterTo)) {
            throw new ConflictException(
                    "Дата " + date + " не входит в активный семестр ["
                            + semesterFrom + " .. " + semesterTo + "]");
        }
        Long semesterId = activeSemester.getId();

        // D-09: conflict check against active template.
        short dayOfWeekZeroBased = (short) (date.getDayOfWeek().getValue() - 1); // 0=Mon..6=Sun
        WeekType weekTypeForDate = computeWeekTypeForDate(
                date,
                semesterFrom,
                academicGrpcClient.parseSemesterFirstWeekType(activeSemester));
        boolean hasTemplateConflict = scheduleItemRepository.existsActiveTemplateSlot(
                request.groupId(), request.lessonNumber(),
                dayOfWeekZeroBased, weekTypeForDate.name().toLowerCase(), semesterId);
        if (hasTemplateConflict) {
            throw new ConflictException(
                    "Слот занят активной шаблонной парой на эту дату. "
                            + "Сначала отмените её через POST /api/schedule/lessons/{id}/cancel");
        }

        OneOffLesson oneOff = new OneOffLesson();
        oneOff.setGroupId(request.groupId());
        oneOff.setSubjectId(request.subjectId());
        oneOff.setSemesterId(semesterId);
        oneOff.setDate(date);
        oneOff.setLessonNumber(request.lessonNumber());
        oneOff.setClassroom(request.classroom());
        oneOff.setCreatedBy(requestContext.getUserId());
        return oneOffLessonRepository.save(oneOff);
    }

    /**
     * Returns all one-off lessons for a group within [dateFrom, dateTo] (inclusive).
     */
    @Transactional(readOnly = true)
    public List<OneOffLesson> listOneOffLessons(Long groupId, LocalDate dateFrom, LocalDate dateTo) {
        return oneOffLessonRepository.findByGroupIdAndDateBetween(groupId, dateFrom, dateTo);
    }

    /**
     * Deletes a one-off lesson (D-22: any date, including past).
     */
    public void deleteOneOffLesson(Long id) {
        OneOffLesson oneOff = oneOffLessonRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("OneOffLesson", "id", id));
        requireHeadmanForGroup(oneOff.getGroupId());
        oneOffLessonRepository.delete(oneOff);
    }

    /**
     * Computes parity of the week a {@code date} falls into (ODD or EVEN), mirroring
     * {@code LessonGenerationService.computeLessonDates} algorithm:
     * <ul>
     *   <li>Anchor = Monday of the week containing semesterStart.</li>
     *   <li>Weeks 0, 2, 4... have parity = firstWeekType; odd weeks flip parity.</li>
     * </ul>
     * Returned value is always ODD or EVEN (never ALL).
     */
    static WeekType computeWeekTypeForDate(LocalDate date,
                                           LocalDate semesterStart,
                                           WeekType firstWeekType) {
        LocalDate anchor = semesterStart.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
        LocalDate targetWeekMonday = date.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
        long weeks = ChronoUnit.WEEKS.between(anchor, targetWeekMonday);
        boolean evenIndex = weeks % 2 == 0;
        return evenIndex
                ? firstWeekType
                : (firstWeekType == WeekType.ODD ? WeekType.EVEN : WeekType.ODD);
    }
}
