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
import ru.rutcampustrack.teacher.grpc.TeacherStatsFilter;
import ru.rutcampustrack.teacher.grpc.TeacherStatsResponse;
import ru.rutcampustrack.teacher.grpc.TeacherStatsScope;
import ru.rutcampustrack.teacher.grpc.TeacherStatsSort;
import ru.rutcampustrack.teacher.grpc.TeacherStudentStats;
import ru.rutcampustrack.teacher.grpc.TeacherGroupStats;
import ru.rutcampustrack.teacher.grpc.TeacherStatsSubjectOption;
import ru.rutcampustrack.schedule.grpc.LessonResponse;
import ru.rutcampustrack.schedule.grpc.LessonsResponse;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
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
    private static final ZoneId MOSCOW = ZoneId.of("Europe/Moscow");
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

        if (!currentTeacherGroupIds(claims).contains(groupId)) {
            throw new MobileBffException(HttpStatus.NOT_FOUND, ProblemCode.OUT_OF_SCOPE,
                    "Журнал недоступен в текущем scope");
        }
        DateRange range = semesterRange(academic.teacherAssignmentsForSemester(semesterId));
        LessonsResponse scheduled = schedule.lessons(groupId, semesterId,
                range.from(), range.to());
        List<LessonResponse> concrete = scheduled.getLessonsList().stream()
                .filter(lesson -> lesson.getId() > 0)
                .filter(lesson -> lesson.getSemesterId() == semesterId
                        && lesson.getGroupId() == groupId
                        && lesson.getSubjectId() == subjectId
                        && lessonType.equalsIgnoreCase(lesson.getLessonType()))
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

    public TeacherApiModels.StatsResponse stats(long semesterId,
                                                String scopeValue,
                                                Long selectedGroupId,
                                                Long selectedSubjectId,
                                                List<String> lessonTypes,
                                                List<String> sortValues,
                                                List<String> filterValues) {
        InternalJwtClaims claims = requireTeacher();
        if (semesterId <= 0) {
            throw invalidStats("semesterId должен быть положительным");
        }
        TeacherStatsScope scope = parseStatsScope(scopeValue);
        if (scope == TeacherStatsScope.TEACHER_STATS_STUDENTS
                && (selectedGroupId == null || selectedGroupId <= 0
                || selectedSubjectId == null || selectedSubjectId <= 0)) {
            throw invalidStats("Для студентов нужны группа и предмет");
        }
        if (scope == TeacherStatsScope.TEACHER_STATS_GROUPS
                && (selectedGroupId != null || selectedSubjectId != null)) {
            throw invalidStats("Разрез групп не принимает предмет или группу");
        }

        Set<String> selectedTypes = lessonTypes == null ? Set.of() : lessonTypes.stream()
                .filter(value -> value != null && !value.isBlank())
                .map(value -> value.trim().toLowerCase(Locale.ROOT))
                .collect(java.util.stream.Collectors.toUnmodifiableSet());
        Set<Long> activeGroupIds = currentTeacherGroupIds(claims);
        if (activeGroupIds.isEmpty()
                || scope == TeacherStatsScope.TEACHER_STATS_STUDENTS
                && !activeGroupIds.contains(selectedGroupId)) {
            throw new MobileBffException(HttpStatus.NOT_FOUND, ProblemCode.OUT_OF_SCOPE,
                    "Статистика недоступна в текущем scope");
        }
        DateRange range = semesterRange(academic.teacherAssignmentsForSemester(semesterId));
        Map<Long, LessonResponse> concreteById = new HashMap<>();
        for (Long groupId : activeGroupIds) {
            LessonsResponse scheduled = schedule.lessons(groupId, semesterId,
                    range.from(), range.to());
            for (LessonResponse lesson : scheduled.getLessonsList()) {
                if (lesson.getId() <= 0
                        || lesson.getSemesterId() != semesterId
                        || lesson.getGroupId() != groupId) {
                    continue;
                }
                if (scope == TeacherStatsScope.TEACHER_STATS_STUDENTS
                        && lesson.getGroupId() != selectedGroupId) {
                    continue;
                }
                concreteById.putIfAbsent(lesson.getId(), lesson);
            }
        }

        List<TeacherStatsSort> sorts = parseStatsSorts(sortValues, scope);
        List<TeacherStatsFilter> filters = parseStatsFilters(filterValues, scope);
        TeacherStatsResponse response = attendance.teacherStats(
                semesterId,
                concreteById.values().stream()
                        .sorted(Comparator.comparing(LessonResponse::getDate)
                                .thenComparingInt(LessonResponse::getLessonNumber)
                                .thenComparingLong(LessonResponse::getId))
                        .map(LessonResponse::getId).toList(),
                scope,
                selectedGroupId == null ? 0 : selectedGroupId,
                selectedSubjectId == null ? 0 : selectedSubjectId,
                selectedTypes.stream().toList(),
                sorts,
                filters);
        return stats(response);
    }

    private Set<Long> currentTeacherGroupIds(InternalJwtClaims claims) {
        TeacherSemesterResponse active = academic.teacherActiveSemester();
        TeacherAssignmentsResponse assignments = academic.teacherAssignmentsForSemester(active.getSemesterId());
        return assignments.getAssignmentsList().stream()
                .filter(value -> value.getTeacherId() == claims.userId())
                .filter(value -> inRange(value, LocalDate.now(MOSCOW)))
                .map(TeacherAssignment::getGroupId)
                .filter(value -> value > 0)
                .collect(java.util.stream.Collectors.toUnmodifiableSet());
    }

    private static DateRange semesterRange(TeacherAssignmentsResponse response) {
        if (response == null || response.getSemesterDateFrom().isBlank()
                || response.getSemesterDateTo().isBlank()) {
            throw new MobileBffException(HttpStatus.SERVICE_UNAVAILABLE,
                    ProblemCode.DEPENDENCY_UNAVAILABLE, "Сервис вернул неполный срок семестра");
        }
        LocalDate from = parseDate(response.getSemesterDateFrom());
        LocalDate to = parseDate(response.getSemesterDateTo());
        if (to.isBefore(from)) {
            throw new MobileBffException(HttpStatus.SERVICE_UNAVAILABLE,
                    ProblemCode.DEPENDENCY_UNAVAILABLE, "Сервис вернул некорректный срок семестра");
        }
        return new DateRange(from, to);
    }

    public TeacherApiModels.ExcuseResponse excuse(String requestId) {
        requireTeacher();
        return excuse(attendance.teacherExcuse(requestId));
    }

    private static TeacherStatsScope parseStatsScope(String value) {
        if (value == null) throw invalidStats("scope обязателен");
        return switch (value.trim().toLowerCase(Locale.ROOT)) {
            case "students", "student" -> TeacherStatsScope.TEACHER_STATS_STUDENTS;
            case "groups", "group" -> TeacherStatsScope.TEACHER_STATS_GROUPS;
            default -> throw invalidStats("Неизвестный разрез статистики");
        };
    }

    private static List<TeacherStatsSort> parseStatsSorts(List<String> values, TeacherStatsScope scope) {
        if (values == null || values.isEmpty()) return List.of();
        List<TeacherStatsSort> result = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        for (String raw : values) {
            if (raw == null || raw.isBlank()) throw invalidStats("Пустая сортировка");
            String value = raw.trim();
            boolean descending = value.startsWith("-");
            if (descending) value = value.substring(1);
            if (value.endsWith(":asc") || value.endsWith(":desc")) {
                descending = value.endsWith(":desc");
                value = value.substring(0, value.length() - 5);
            }
            if (!statsColumn(value, scope) || !seen.add(value)) throw invalidStats("Недопустимая сортировка");
            result.add(TeacherStatsSort.newBuilder().setColumn(value).setDescending(descending).build());
        }
        return result;
    }

    private static List<TeacherStatsFilter> parseStatsFilters(List<String> values, TeacherStatsScope scope) {
        if (values == null || values.isEmpty()) return List.of();
        List<TeacherStatsFilter> result = new ArrayList<>();
        for (String raw : values) {
            if (raw == null || raw.isBlank()) throw invalidStats("Пустой фильтр");
            String value = raw.trim();
            String operator = value.contains("~") ? "~"
                    : value.contains(">=") ? ">=" : value.contains("<=") ? "<=" : null;
            if (operator == null) throw invalidStats("Фильтр имеет недопустимый формат");
            int separator = value.indexOf(operator);
            String column = value.substring(0, separator).trim();
            String operand = value.substring(separator + operator.length()).trim();
            if (!statsColumn(column, scope) || operand.isBlank()) throw invalidStats("Недопустимый фильтр");
            TeacherStatsFilter.Builder filter = TeacherStatsFilter.newBuilder().setColumn(column);
            if (operator.equals("~")) {
                if (!(column.equals("displayName") || column.equals("groupName"))) {
                    throw invalidStats("Текстовый фильтр доступен только для имени");
                }
                filter.setContains(operand);
            } else {
                try {
                    if (column.equals("lessonsCount")) {
                        int count = Integer.parseInt(operand);
                        if (operator.equals(">=")) filter.setMinValue(count);
                        else filter.setMaxValue(count);
                    } else {
                        double percent = Double.parseDouble(operand);
                        if (operator.equals(">=")) filter.setMinPercent(percent);
                        else filter.setMaxPercent(percent);
                    }
                } catch (NumberFormatException error) {
                    throw invalidStats("Числовой фильтр имеет недопустимое значение");
                }
            }
            result.add(filter.build());
        }
        return result;
    }

    private static boolean statsColumn(String column, TeacherStatsScope scope) {
        if (scope == TeacherStatsScope.TEACHER_STATS_STUDENTS && column.equals("displayName")) return true;
        if (scope == TeacherStatsScope.TEACHER_STATS_GROUPS
                && (column.equals("groupName") || column.equals("lessonsCount"))) return true;
        return Set.of("present", "presentOrExcused", "excused", "absent").contains(column);
    }

    private static TeacherApiModels.StatsResponse stats(TeacherStatsResponse response) {
        return new TeacherApiModels.StatsResponse(
                response.getScope() == TeacherStatsScope.TEACHER_STATS_GROUPS ? "groups" : "students",
                Long.toString(response.getSemesterId()), parseNullableDate(response.getPeriodFrom()),
                parseNullableDate(response.getPeriodTo()), response.getLessonsCount(),
                response.getStudentsList().stream().map(TeacherReadFacade::statsStudent).toList(),
                response.getGroupsList().stream().map(TeacherReadFacade::statsGroup).toList(),
                response.getSubjectOptionsList().stream().map(TeacherReadFacade::statsSubjectOption).toList(),
                parseInstant(response.getServerNow()));
    }

    private static TeacherApiModels.StatsStudent statsStudent(TeacherStudentStats value) {
        return new TeacherApiModels.StatsStudent(Long.toString(value.getStudentId()), value.getDisplayName(),
                metric(value.getPresent()), metric(value.getPresentOrExcused()), metric(value.getExcused()),
                metric(value.getAbsent()));
    }

    private static TeacherApiModels.StatsGroup statsGroup(TeacherGroupStats value) {
        return new TeacherApiModels.StatsGroup(Long.toString(value.getGroupId()), value.getGroupName(),
                value.getLessonsCount(), metric(value.getPresent()), metric(value.getPresentOrExcused()),
                metric(value.getExcused()), metric(value.getAbsent()));
    }

    private static TeacherApiModels.StatsSubjectOption statsSubjectOption(TeacherStatsSubjectOption value) {
        return new TeacherApiModels.StatsSubjectOption(
                Long.toString(value.getGroupId()), Long.toString(value.getSubjectId()),
                value.getSubjectName(), value.getLessonTypesList());
    }

    private static TeacherApiModels.StatsMetric metric(ru.rutcampustrack.teacher.grpc.TeacherStatsMetric value) {
        return new TeacherApiModels.StatsMetric(value.getNumerator(), value.getDenominator(), value.getPercent());
    }

    private static MobileBffException invalidStats(String message) {
        return new MobileBffException(HttpStatus.BAD_REQUEST, ProblemCode.INVALID_REQUEST, message);
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

    private record DateRange(LocalDate from, LocalDate to) {
    }

    public record Download(String filename, String contentType, byte[] bytes) {
    }
}
