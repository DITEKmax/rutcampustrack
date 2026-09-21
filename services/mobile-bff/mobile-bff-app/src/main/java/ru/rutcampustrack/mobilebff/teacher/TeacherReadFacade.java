package ru.rutcampustrack.mobilebff.teacher;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import ru.rutcampustrack.mobilebff.contract.model.StudentApiModels.ProblemCode;
import ru.rutcampustrack.mobilebff.contract.model.TeacherApiModels;
import ru.rutcampustrack.mobilebff.error.MobileBffException;
import ru.rutcampustrack.mobilebff.grpc.MobileAcademicClient;
import ru.rutcampustrack.mobilebff.grpc.MobileAttendanceClient;
import ru.rutcampustrack.mobilebff.grpc.MobileScheduleClient;
import ru.rutcampustrack.mobilebff.security.MobileRequestContext;
import ru.rutcampustrack.shared.security.InternalJwtClaims;
import ru.rutcampustrack.teacher.grpc.TeacherAssignment;
import ru.rutcampustrack.teacher.grpc.TeacherAssignmentsResponse;
import ru.rutcampustrack.teacher.grpc.TeacherAttachmentDownload;
import ru.rutcampustrack.teacher.grpc.TeacherExcuseAttachment;
import ru.rutcampustrack.teacher.grpc.TeacherExcuseResponse;
import ru.rutcampustrack.teacher.grpc.TeacherJournalCell;
import ru.rutcampustrack.teacher.grpc.TeacherJournalResponse;
import ru.rutcampustrack.teacher.grpc.TeacherJournalStudent;
import ru.rutcampustrack.teacher.grpc.TeacherLessonResponse;
import ru.rutcampustrack.teacher.grpc.TeacherLessonSummary;
import ru.rutcampustrack.teacher.grpc.TeacherRosterEntry;
import ru.rutcampustrack.teacher.grpc.TeacherSemesterResponse;
import ru.rutcampustrack.schedule.grpc.LessonResponse;
import ru.rutcampustrack.schedule.grpc.LessonsResponse;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/** Composes server-scoped teacher read RPCs into the mobile HTTP contract. */
@Service
public final class TeacherReadFacade {
    private final MobileRequestContext requestContext;
    private final MobileAcademicClient academic;
    private final MobileScheduleClient schedule;
    private final MobileAttendanceClient attendance;

    public TeacherReadFacade(MobileRequestContext requestContext,
                             MobileAcademicClient academic,
                             MobileScheduleClient schedule,
                             MobileAttendanceClient attendance) {
        this.requestContext = requestContext;
        this.academic = academic;
        this.schedule = schedule;
        this.attendance = attendance;
    }

    public TeacherApiModels.SemesterResponse semester() {
        requireTeacher();
        TeacherSemesterResponse value = academic.teacherActiveSemester();
        return new TeacherApiModels.SemesterResponse(
                Long.toString(value.getSemesterId()), value.getName(),
                parseDate(value.getDateFrom()), parseDate(value.getDateTo()));
    }

    public List<TeacherApiModels.Assignment> assignments(long semesterId,
                                                         LocalDate dateFrom,
                                                         LocalDate dateTo) {
        InternalJwtClaims claims = requireTeacher();
        TeacherAssignmentsResponse response = academic.teacherAssignments(
                semesterId, dateFrom.toString(), dateTo.toString());
        return response.getAssignmentsList().stream()
                .filter(value -> value.getTeacherId() == claims.userId())
                .map(TeacherReadFacade::assignment)
                .toList();
    }

    public TeacherApiModels.DayResponse day(long semesterId, LocalDate date) {
        InternalJwtClaims claims = requireTeacher();
        TeacherAssignmentsResponse assignments = academic.teacherAssignments(
                semesterId, date.toString(), date.toString());
        Map<Long, TeacherAssignment> assignmentById = new HashMap<>();
        Map<AssignmentKey, TeacherAssignment> assignmentByKey = new HashMap<>();
        Set<Long> groupIds = new HashSet<>();
        for (TeacherAssignment assignment : assignments.getAssignmentsList()) {
            if (assignment.getTeacherId() != claims.userId()
                    || !inRange(assignment, date)
                    || assignment.getGroupId() <= 0) {
                continue;
            }
            assignmentById.put(assignment.getAssignmentId(), assignment);
            assignmentByKey.put(new AssignmentKey(assignment.getGroupId(), assignment.getSubjectId(),
                    assignment.getLessonType().toLowerCase(Locale.ROOT)), assignment);
            groupIds.add(assignment.getGroupId());
        }

        List<LessonResponse> lessons = new ArrayList<>();
        for (Long groupId : groupIds) {
            lessons.addAll(schedule.lessons(groupId, semesterId, date, date).getLessonsList());
        }
        List<TeacherApiModels.DayLesson> result = lessons.stream()
                .filter(lesson -> lesson.getAssignedTeacherId() == claims.userId())
                .filter(lesson -> lesson.getSemesterId() == semesterId
                        && date.toString().equals(lesson.getDate()))
                .filter(lesson -> assignedToTeacher(lesson, assignmentById, assignmentByKey))
                .map(lesson -> dayLesson(lesson, assignmentById, assignmentByKey))
                .sorted(Comparator.comparing(TeacherApiModels.DayLesson::date)
                        .thenComparing(TeacherApiModels.DayLesson::startsAt,
                                Comparator.nullsLast(Comparator.naturalOrder()))
                        .thenComparingInt(TeacherApiModels.DayLesson::lessonNumber)
                        .thenComparing(TeacherApiModels.DayLesson::groupName,
                                Comparator.nullsLast(String.CASE_INSENSITIVE_ORDER))
                        .thenComparing(TeacherApiModels.DayLesson::id))
                .toList();
        return new TeacherApiModels.DayResponse(Long.toString(semesterId), date, result, Instant.now());
    }

    public TeacherApiModels.LessonResponse lesson(long lessonId) {
        requireTeacher();
        return lesson(attendance.teacherLesson(lessonId));
    }

    public TeacherApiModels.JournalResponse journal(long semesterId,
                                                     long groupId,
                                                     long subjectId,
                                                     String lessonType,
                                                     int page,
                                                     int pageSize) {
        InternalJwtClaims claims = requireTeacher();
        if (semesterId <= 0 || groupId <= 0 || subjectId <= 0
                || lessonType == null || lessonType.isBlank()
                || page < 0 || pageSize < 1 || pageSize > 100) {
            throw new MobileBffException(HttpStatus.BAD_REQUEST, ProblemCode.INVALID_REQUEST,
                    "Некорректный контекст журнала");
        }

        List<TeacherAssignment> assignments = academic.teacherAssignmentsForSemester(semesterId)
                .getAssignmentsList().stream()
                .filter(assignment -> assignment.getTeacherId() == claims.userId())
                .filter(assignment -> assignment.getGroupId() == groupId)
                .filter(assignment -> assignment.getSubjectId() == subjectId)
                .filter(assignment -> lessonType.equalsIgnoreCase(assignment.getLessonType()))
                .toList();
        if (assignments.isEmpty()) {
            throw new MobileBffException(HttpStatus.NOT_FOUND, ProblemCode.OUT_OF_SCOPE,
                    "Журнал недоступен в текущем scope");
        }

        Map<Long, TeacherAssignment> assignmentById = new HashMap<>();
        Map<AssignmentKey, TeacherAssignment> assignmentByKey = new HashMap<>();
        LocalDate dateFrom = null;
        LocalDate dateTo = null;
        for (TeacherAssignment assignment : assignments) {
            assignmentById.put(assignment.getAssignmentId(), assignment);
            assignmentByKey.put(new AssignmentKey(assignment.getGroupId(), assignment.getSubjectId(),
                    assignment.getLessonType().toLowerCase(Locale.ROOT)), assignment);
            LocalDate validFrom = parseDate(assignment.getValidFrom());
            LocalDate validUntil = parseNullableDate(assignment.getValidUntilExclusive());
            dateFrom = dateFrom == null || validFrom.isBefore(dateFrom) ? validFrom : dateFrom;
            if (validUntil != null) {
                dateTo = dateTo == null || validUntil.isAfter(dateTo) ? validUntil : dateTo;
            }
        }
        if (dateFrom == null || dateTo == null || !dateFrom.isBefore(dateTo)) {
            throw new MobileBffException(HttpStatus.SERVICE_UNAVAILABLE,
                    ProblemCode.DEPENDENCY_UNAVAILABLE, "Сервис вернул неполный срок назначения");
        }

        LessonsResponse scheduled = schedule.lessons(groupId, semesterId,
                dateFrom, dateTo.minusDays(1));
        List<LessonResponse> concrete = scheduled.getLessonsList().stream()
                .filter(lesson -> lesson.getAssignedTeacherId() == claims.userId())
                .filter(lesson -> lesson.getSemesterId() == semesterId
                        && lesson.getGroupId() == groupId
                        && lesson.getSubjectId() == subjectId
                        && lessonType.equalsIgnoreCase(lesson.getLessonType()))
                .filter(lesson -> assignedToJournalContext(lesson, assignments))
                .sorted(Comparator.comparing(LessonResponse::getDate)
                        .thenComparing(LessonResponse::getStartTime,
                                Comparator.nullsLast(Comparator.naturalOrder()))
                        .thenComparingInt(LessonResponse::getLessonNumber)
                        .thenComparingLong(LessonResponse::getId))
                .toList();

        long offset = (long) page * pageSize;
        int start = offset >= concrete.size() ? concrete.size() : (int) offset;
        int end = Math.min(concrete.size(), start + pageSize);
        List<LessonResponse> pageLessons = concrete.subList(start, end);
        if (pageLessons.isEmpty()) {
            return new TeacherApiModels.JournalResponse(
                    List.of(), List.of(), Instant.now(), page, pageSize,
                    concrete.size(), false);
        }
        TeacherJournalResponse response = attendance.teacherJournal(
                pageLessons.stream().map(LessonResponse::getId).toList());
        return new TeacherApiModels.JournalResponse(
                response.getLessonsList().stream().map(TeacherReadFacade::dayLesson).toList(),
                response.getStudentsList().stream().map(TeacherReadFacade::journalStudent).toList(),
                parseInstant(response.getServerNow()), page, pageSize,
                concrete.size(), end < concrete.size());
    }

    public TeacherApiModels.ExcuseResponse excuse(String requestId) {
        requireTeacher();
        return excuse(attendance.teacherExcuse(requestId));
    }

    public Download attachment(String requestId, String attachmentId) {
        requireTeacher();
        TeacherAttachmentDownload download = attendance.teacherExcuseAttachment(requestId, attachmentId);
        return new Download(download.getFileName(), download.getContentType(), download.getData().toByteArray());
    }

    private InternalJwtClaims requireTeacher() {
        InternalJwtClaims claims = requestContext.claims();
        if (!"TEACHER".equals(claims.domainRole()) || !"ACTIVE".equals(claims.status())) {
            throw new MobileBffException(HttpStatus.FORBIDDEN, ProblemCode.WRONG_ROLE,
                    "Операция доступна только преподавателю");
        }
        return claims;
    }

    private static boolean assignedToTeacher(LessonResponse lesson,
                                             Map<Long, TeacherAssignment> byId,
                                             Map<AssignmentKey, TeacherAssignment> byKey) {
        TeacherAssignment exact = byId.get(lesson.getAssignmentId());
        if (exact != null) {
            return exact.getGroupId() == lesson.getGroupId()
                    && exact.getSubjectId() == lesson.getSubjectId()
                    && exact.getLessonType().equalsIgnoreCase(lesson.getLessonType());
        }
        return byKey.containsKey(new AssignmentKey(lesson.getGroupId(), lesson.getSubjectId(),
                lesson.getLessonType().toLowerCase(Locale.ROOT)));
    }

    private static boolean assignedToJournalContext(LessonResponse lesson,
                                                    List<TeacherAssignment> assignments) {
        LocalDate lessonDate = parseDate(lesson.getDate());
        TeacherAssignment exact = assignments.stream()
                .filter(assignment -> assignment.getAssignmentId() == lesson.getAssignmentId())
                .findFirst()
                .orElse(null);
        if (exact != null) {
            return matchesContext(lesson, exact) && inRange(exact, lessonDate);
        }
        return assignments.stream()
                .filter(assignment -> matchesContext(lesson, assignment))
                .anyMatch(assignment -> inRange(assignment, lessonDate));
    }

    private static boolean matchesContext(LessonResponse lesson, TeacherAssignment assignment) {
        return assignment.getTeacherId() == lesson.getAssignedTeacherId()
                && assignment.getGroupId() == lesson.getGroupId()
                && assignment.getSubjectId() == lesson.getSubjectId()
                && assignment.getLessonType().equalsIgnoreCase(lesson.getLessonType());
    }

    private static TeacherApiModels.DayLesson dayLesson(LessonResponse lesson,
                                                         Map<Long, TeacherAssignment> byId,
                                                         Map<AssignmentKey, TeacherAssignment> byKey) {
        TeacherAssignment assignment = byId.get(lesson.getAssignmentId());
        if (assignment == null) {
            assignment = byKey.get(new AssignmentKey(lesson.getGroupId(), lesson.getSubjectId(),
                    lesson.getLessonType().toLowerCase(Locale.ROOT)));
        }
        return new TeacherApiModels.DayLesson(
                Long.toString(lesson.getId()),
                Long.toString(lesson.getAssignmentId()),
                Long.toString(lesson.getGroupId()),
                assignment == null ? "" : assignment.getGroupName(),
                Long.toString(lesson.getSubjectId()),
                assignment == null ? "" : assignment.getSubjectName(),
                Long.toString(lesson.getSemesterId()),
                lesson.getLessonType(),
                parseDate(lesson.getDate()),
                lesson.getLessonNumber(),
                parseTime(lesson.getStartTime()),
                parseTime(lesson.getEndTime()),
                emptyToNull(lesson.getRoom()),
                lesson.getStatus(),
                lesson.getScheduleItemId() <= 0,
                lesson.getRevision() > 1 || lesson.getGeneration() > 1,
                "cancelled".equalsIgnoreCase(lesson.getStatus()));
    }

    private static TeacherApiModels.Assignment assignment(TeacherAssignment value) {
        return new TeacherApiModels.Assignment(
                Long.toString(value.getAssignmentId()), Long.toString(value.getTeacherId()),
                Long.toString(value.getGroupId()), value.getGroupName(),
                Long.toString(value.getSubjectId()), value.getSubjectName(),
                Long.toString(value.getSemesterId()), value.getLessonType(),
                parseDate(value.getValidFrom()), parseNullableDate(value.getValidUntilExclusive()));
    }

    private static TeacherApiModels.LessonResponse lesson(TeacherLessonResponse response) {
        return new TeacherApiModels.LessonResponse(
                dayLesson(response.getLesson()),
                response.getRosterList().stream().map(TeacherReadFacade::roster).toList(),
                parseInstant(response.getServerNow()));
    }

    private static TeacherApiModels.RosterEntry roster(TeacherRosterEntry entry) {
        return new TeacherApiModels.RosterEntry(
                Long.toString(entry.getStudentId()), entry.getDisplayName(), nullable(entry.getStatus()),
                nullable(entry.getSymbol()), nullable(entry.getSource()), entry.getRecordPresent(),
                entry.getPendingTicket(), entry.getAutoAbsent(), nullable(entry.getTicketId()),
                nullable(entry.getExcuseType()), nullable(entry.getExcuseReason()), nullable(entry.getExcuseComment()),
                nullable(entry.getAttachmentId()), nullable(entry.getAttachmentName()),
                nullable(entry.getAttachmentContentType()), entry.getAttachmentSize());
    }

    private static TeacherApiModels.JournalStudent journalStudent(TeacherJournalStudent student) {
        return new TeacherApiModels.JournalStudent(
                Long.toString(student.getStudentId()), student.getDisplayName(),
                student.getCellsList().stream().map(TeacherReadFacade::journalCell).toList());
    }

    private static TeacherApiModels.JournalCell journalCell(TeacherJournalCell cell) {
        return new TeacherApiModels.JournalCell(
                Long.toString(cell.getLessonId()), nullable(cell.getStatus()), nullable(cell.getSymbol()),
                nullable(cell.getSource()), cell.getRecordPresent(), cell.getPendingTicket(),
                cell.getAutoAbsent(), nullable(cell.getTicketId()), nullable(cell.getExcuseType()),
                nullable(cell.getExcuseReason()));
    }

    private static TeacherApiModels.ExcuseResponse excuse(TeacherExcuseResponse response) {
        return new TeacherApiModels.ExcuseResponse(
                response.getRequestId(), response.getRequestKind(), response.getStatus(),
                Long.toString(response.getStudentId()), response.getStudentName(),
                Long.toString(response.getGroupId()), response.getGroupName(), response.getExcuseType(),
                nullable(response.getReason()), nullable(response.getComment()), parseInstant(response.getCreatedAt()),
                response.getDecisionBy() <= 0 ? null : Long.toString(response.getDecisionBy()),
                parseInstant(response.getDecisionAt()), nullable(response.getDecisionComment()),
                response.getLessonsList().stream().map(TeacherReadFacade::dayLesson).toList(),
                response.getAttachmentsList().stream().map(TeacherReadFacade::attachment).toList(),
                parseInstant(response.getServerNow()));
    }

    private static TeacherApiModels.ExcuseAttachment attachment(TeacherExcuseAttachment value) {
        return new TeacherApiModels.ExcuseAttachment(
                value.getAttachmentId(), value.getFileName(), value.getContentType(), value.getSize(),
                parseInstant(value.getUploadedAt()), parseInstant(value.getExpiresAt()));
    }

    private static TeacherApiModels.DayLesson dayLesson(TeacherLessonSummary value) {
        return new TeacherApiModels.DayLesson(
                Long.toString(value.getLessonId()), Long.toString(value.getAssignmentId()),
                Long.toString(value.getGroupId()), value.getGroupName(), Long.toString(value.getSubjectId()),
                value.getSubjectName(), Long.toString(value.getSemesterId()), value.getLessonType(),
                parseDate(value.getLessonDate()), value.getLessonNumber(), parseTime(value.getStartTime()),
                parseTime(value.getEndTime()), emptyToNull(value.getRoom()), value.getStatus(),
                value.getOneOff(), value.getMoved(), value.getCancelled());
    }

    private static boolean inRange(TeacherAssignment value, LocalDate date) {
        LocalDate from = parseDate(value.getValidFrom());
        LocalDate until = parseNullableDate(value.getValidUntilExclusive());
        return !date.isBefore(from) && (until == null || date.isBefore(until));
    }

    private static LocalDate parseDate(String value) {
        try {
            return LocalDate.parse(value);
        } catch (RuntimeException error) {
            throw new MobileBffException(HttpStatus.SERVICE_UNAVAILABLE,
                    ProblemCode.DEPENDENCY_UNAVAILABLE, "Сервис вернул некорректную дату");
        }
    }

    private static LocalDate parseNullableDate(String value) {
        return value == null || value.isBlank() ? null : parseDate(value);
    }

    private static LocalTime parseTime(String value) {
        return value == null || value.isBlank() ? null : LocalTime.parse(value);
    }

    private static Instant parseInstant(String value) {
        return value == null || value.isBlank() ? null : Instant.parse(value);
    }

    private static String nullable(String value) {
        return value == null || value.isBlank() ? null : value;
    }

    private static String emptyToNull(String value) {
        return nullable(value);
    }

    private record AssignmentKey(long groupId, long subjectId, String lessonType) {
    }

    public record Download(String filename, String contentType, byte[] bytes) {
    }
}
