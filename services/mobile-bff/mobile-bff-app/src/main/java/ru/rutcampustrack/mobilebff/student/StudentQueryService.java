package ru.rutcampustrack.mobilebff.student;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import ru.rutcampustrack.academic.grpc.GroupResponse;
import ru.rutcampustrack.academic.grpc.HomeworkInfo;
import ru.rutcampustrack.academic.grpc.HomeworksForWeekResponse;
import ru.rutcampustrack.academic.grpc.SemesterResponse;
import ru.rutcampustrack.academic.grpc.SetHomeworkCompletionResponse;
import ru.rutcampustrack.academic.grpc.SubjectInfo;
import ru.rutcampustrack.academic.grpc.UserResponse;
import ru.rutcampustrack.attendance.grpc.AutomaticCheckinRequest;
import ru.rutcampustrack.attendance.grpc.StudentAttendanceEntry;
import ru.rutcampustrack.attendance.grpc.StudentAttendanceSnapshotResponse;
import ru.rutcampustrack.attendance.grpc.StudentAttendanceProjectionResponse;
import ru.rutcampustrack.mobilebff.contract.model.StudentApiModels.*;
import ru.rutcampustrack.mobilebff.error.MobileBffException;
import ru.rutcampustrack.mobilebff.grpc.MobileAcademicClient;
import ru.rutcampustrack.mobilebff.grpc.MobileAttendanceClient;
import ru.rutcampustrack.mobilebff.grpc.MobileScheduleClient;
import ru.rutcampustrack.mobilebff.security.MobileRequestContext;
import ru.rutcampustrack.schedule.grpc.LessonResponse;
import ru.rutcampustrack.schedule.grpc.LessonsResponse;
import ru.rutcampustrack.shared.security.InternalJwtClaims;

import java.net.URI;
import java.time.Instant;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class StudentQueryService {
    private static final ZoneId MOSCOW = ZoneId.of("Europe/Moscow");

    private final MobileRequestContext requestContext;
    private final MobileAcademicClient academic;
    private final MobileScheduleClient schedule;
    private final MobileAttendanceClient attendance;
    private final Clock clock;

    public StudentQueryService(MobileRequestContext requestContext, MobileAcademicClient academic,
                               MobileScheduleClient schedule, MobileAttendanceClient attendance, Clock clock) {
        this.requestContext = requestContext;
        this.academic = academic;
        this.schedule = schedule;
        this.attendance = attendance;
        this.clock = clock;
    }

    public SessionResponse session() {
        InternalJwtClaims claims = requireStudent(false);
        UserResponse user = academic.user(claims.userId());
        GroupSummary group = claims.groupId() == null ? null : group(academic.group(claims.groupId()));
        SemesterSummary semester = claims.groupId() == null ? null : semester(academic.activeSemester());
        return new SessionResponse(
                claims.sessionId().toString(),
                Long.toString(claims.sessionVersion()),
                Long.toString(claims.rolesVersion()),
                claims.readOnly(),
                new StudentUser(Long.toString(user.getId()), user.getDisplayName()),
                ActiveRole.STUDENT, group, semester,
                List.of(Capability.TODAY, Capability.GEO_CHECKIN, Capability.OFFLINE_SEMESTER_SCHEDULE),
                clock.instant(), links("today", "/api/v1/student/today", "schedule", "/api/v1/student/schedule"));
    }

    public TodayResponse today() {
        InternalJwtClaims claims = requireStudent(true);
        SemesterResponse semester = academic.activeSemester();
        LocalDate date = LocalDate.now(clock);
        LessonsResponse lessons = schedule.lessons(claims.groupId(), semester.getId(), date, date);
        List<LessonResponse> ordered = ordered(lessons.getLessonsList());
        StudentAttendanceSnapshotResponse snapshot = attendance.snapshot(
                ordered.stream().map(LessonResponse::getId).toList());
        Map<Long, StudentAttendanceEntry> entryByLesson = snapshot.getEntriesList().stream()
                .collect(Collectors.toMap(StudentAttendanceEntry::getLessonId, Function.identity()));
        Map<Long, SubjectInfo> subjects = subjects(ordered);
        List<TodayLessonResponse> result = ordered.stream()
                .map(lesson -> new TodayLessonResponse(
                        lesson(lesson, subjects.get(lesson.getSubjectId())),
                        attendance(entryByLesson.get(lesson.getId())),
                        eligibility(entryByLesson.get(lesson.getId())),
                        request(entryByLesson.get(lesson.getId()))))
                .toList();
        Instant serverNow = Instant.parse(snapshot.getServerNow());
        return new TodayResponse(LocalDate.ofInstant(serverNow, MOSCOW), MOSCOW.getId(), serverNow,
                result, links("self", "/api/v1/student/today", "schedule", "/api/v1/student/schedule"));
    }

    public ScheduleResponse schedule(long requestedSemesterId) {
        InternalJwtClaims claims = requireStudent(true);
        SemesterResponse active = academic.activeSemester();
        if (active.getId() != requestedSemesterId) {
            throw new MobileBffException(HttpStatus.FORBIDDEN, ProblemCode.OUT_OF_SCOPE,
                    "Семестр не входит в scope студента");
        }
        LocalDate from = LocalDate.parse(active.getDateFrom());
        LocalDate to = LocalDate.parse(active.getDateTo());
        LessonsResponse lessons = schedule.lessons(claims.groupId(), active.getId(), from, to);
        List<LessonResponse> ordered = ordered(lessons.getLessonsList());
        Map<Long, SubjectInfo> subjects = subjects(ordered);
        Instant updatedAt = lessons.hasUpdatedAt() ? Instant.parse(lessons.getUpdatedAt()) : Instant.EPOCH;
        return new ScheduleResponse(semester(active), from, to, updatedAt,
                ordered.stream().map(lesson -> lesson(lesson, subjects.get(lesson.getSubjectId()))).toList(),
                links("self", "/api/v1/student/schedule?semesterId=" + requestedSemesterId,
                        "today", "/api/v1/student/today"));
    }

    public StudentAttendanceResponse attendance(long requestedSemesterId) {
        SemesterResponse active = requireActiveSemester(requestedSemesterId);
        StudentAttendanceProjectionResponse response = attendance.projection(
                requestedSemesterId, null, "days", List.of());
        return attendanceResponse(response, active);
    }

    public StudentStatisticsResponse statistics(long requestedSemesterId) {
        SemesterResponse active = requireActiveSemester(requestedSemesterId);
        StudentAttendanceProjectionResponse response = attendance.projection(
                requestedSemesterId, null, "weeks", List.of());
        return statisticsResponse(response, active);
    }

    public StudentStatisticsSubjectDetailResponse statisticsSubject(
            long requestedSemesterId,
            long subjectId,
            String range,
            List<String> types) {
        SemesterResponse active = requireActiveSemester(requestedSemesterId);
        StudentAttendanceProjectionResponse response = attendance.projection(
                requestedSemesterId, subjectId, range, types == null ? List.of() : types);
        ru.rutcampustrack.attendance.grpc.StudentAttendanceSubject subject = response.getSubjectsList().stream()
                .filter(value -> value.getSubjectId() == subjectId)
                .findFirst()
                .orElseThrow(() -> new MobileBffException(HttpStatus.NOT_FOUND,
                        ProblemCode.LESSON_NOT_FOUND, "Предмет не найден"));
        return new StudentStatisticsSubjectDetailResponse(
                Long.toString(subject.getSubjectId()),
                subject.getName(),
                subject.getAvailableTypesList().stream().map(StudentQueryService::lessonType).toList(),
                subject.getSelectedTypesList().stream().map(StudentQueryService::lessonType).toList(),
                metricSet(subject.getSelectedAggregate()),
                subject.getSeriesList().stream().map(StudentQueryService::seriesPoint).toList(),
                subject.getTypeCardsList().stream().map(StudentQueryService::typeCard).toList());
    }

    public HomeworkResponse homework(String fromRaw, String toRaw) {
        InternalJwtClaims claims = requireStudent(true);
        SemesterResponse active = academic.activeSemesterForHomework();
        LocalDate activeFrom = LocalDate.parse(active.getDateFrom());
        LocalDate activeTo = LocalDate.parse(active.getDateTo());
        Instant serverNow = clock.instant();
        LocalDate today = LocalDate.ofInstant(serverNow, MOSCOW);

        LocalDate from = fromRaw == null ? clamp(today, activeFrom, activeTo)
                : parseHomeworkDate(fromRaw, "from");
        LocalDate to = toRaw == null ? activeTo : parseHomeworkDate(toRaw, "to");
        if (from.isBefore(activeFrom) || to.isAfter(activeTo) || from.isAfter(to)) {
            throw new MobileBffException(HttpStatus.BAD_REQUEST, ProblemCode.INVALID_REQUEST,
                    "Диапазон ДЗ должен входить в активный семестр");
        }

        boolean includeCompletedToday = !today.isBefore(from) && !today.isAfter(to);
        HomeworksForWeekResponse response;
        if (includeCompletedToday) {
            ZonedDateTime completedTodayFrom = today.atStartOfDay(MOSCOW);
            ZonedDateTime completedTodayTo = today.plusDays(1).atStartOfDay(MOSCOW);
            response = academic.homeworks(
                    claims.groupId(), active.getId(), claims.userId(), from.toString(), to.toString(),
                    true, completedTodayFrom.toInstant().toString(), completedTodayTo.toInstant().toString());
        } else {
            response = academic.homeworks(
                    claims.groupId(), active.getId(), claims.userId(), from.toString(), to.toString());
        }
        List<HomeworkItem> items = response.getHomeworksList().stream()
                .sorted(Comparator.comparing((HomeworkInfo item) -> LocalDate.parse(item.getLessonDate()))
                        .thenComparingInt(item -> item.getCompleted() ? 1 : 0)
                        .thenComparingInt(HomeworkInfo::getLessonNumber)
                        .thenComparingLong(HomeworkInfo::getHomeworkId))
                .map(StudentQueryService::homeworkItem)
                .toList();
        return new HomeworkResponse(
                new HomeworkSemester(Long.toString(active.getId()), active.getName(), activeFrom, activeTo),
                from, to, serverNow, items);
    }

    public HomeworkCompletionResponse setHomeworkCompletion(String homeworkId,
                                                             HomeworkCompletionRequest request) {
        InternalJwtClaims claims = requireStudent(false);
        if (claims.readOnly()) {
            throw new MobileBffException(HttpStatus.FORBIDDEN, ProblemCode.ROLE_READ_ONLY,
                    "Терминальная student-сессия доступна только для чтения");
        }
        if (claims.groupId() == null || claims.groupId() <= 0) {
            throw new MobileBffException(HttpStatus.FORBIDDEN, ProblemCode.OUT_OF_SCOPE,
                    "Не хватает student/group scope");
        }
        long id = parseHomeworkId(homeworkId);
        SemesterResponse active = academic.activeSemesterForHomework();
        SetHomeworkCompletionResponse response = academic.setHomeworkCompletion(
                id, active.getId(), request.completed());
        Instant completedAt = completionAt(response.getCompleted(), response.hasCompletedAt(),
                response.getCompletedAt());
        return new HomeworkCompletionResponse(
                Long.toString(response.getHomeworkId()), response.getCompleted(), completedAt);
    }

    private InternalJwtClaims requireStudent(boolean groupRequired) {
        InternalJwtClaims claims = requestContext.claims();
        if (!"STUDENT".equalsIgnoreCase(claims.domainRole())) {
            throw new MobileBffException(HttpStatus.FORBIDDEN, ProblemCode.WRONG_ROLE,
                    "Мобильный student API доступен роли STUDENT");
        }
        if (claims.userId() <= 0
                || groupRequired && (claims.groupId() == null || claims.groupId() <= 0)) {
            throw new MobileBffException(HttpStatus.FORBIDDEN, ProblemCode.OUT_OF_SCOPE,
                    "Не хватает student/group scope");
        }
        return claims;
    }

    private SemesterResponse requireActiveSemester(long requestedSemesterId) {
        requireStudent(false);
        SemesterResponse active = academic.activeSemester();
        if (active.getId() != requestedSemesterId) {
            throw new MobileBffException(HttpStatus.FORBIDDEN, ProblemCode.OUT_OF_SCOPE,
                    "Семестр не входит в scope студента");
        }
        return active;
    }

    private Map<Long, SubjectInfo> subjects(List<LessonResponse> lessons) {
        return academic.subjects(lessons.stream().map(LessonResponse::getSubjectId).distinct().toList());
    }

    private static List<LessonResponse> ordered(List<LessonResponse> lessons) {
        return lessons.stream().sorted(Comparator
                .comparing((LessonResponse l) -> LocalDate.parse(l.getDate()))
                .thenComparingInt(LessonResponse::getLessonNumber)
                .thenComparingLong(LessonResponse::getId)).toList();
    }

    private static LocalDate parseHomeworkDate(String value, String field) {
        try {
            return LocalDate.parse(value);
        } catch (RuntimeException error) {
            throw new MobileBffException(HttpStatus.BAD_REQUEST, ProblemCode.INVALID_REQUEST,
                    "Параметр " + field + " должен быть датой ISO-8601");
        }
    }

    private static long parseHomeworkId(String value) {
        try {
            long id = Long.parseLong(value);
            if (id <= 0) {
                throw new NumberFormatException("non-positive homework id");
            }
            return id;
        } catch (NumberFormatException error) {
            throw new MobileBffException(HttpStatus.BAD_REQUEST, ProblemCode.INVALID_REQUEST,
                    "Идентификатор ДЗ должен быть положительным целым числом");
        }
    }

    private static LocalDate clamp(LocalDate value, LocalDate from, LocalDate to) {
        if (value.isBefore(from)) return from;
        if (value.isAfter(to)) return to;
        return value;
    }

    private static HomeworkItem homeworkItem(HomeworkInfo item) {
        String link = item.getLink().isBlank() ? null : item.getLink();
        Instant completedAt = completionAt(item.getCompleted(), item.hasCompletedAt(), item.getCompletedAt());
        return new HomeworkItem(
                Long.toString(item.getHomeworkId()),
                new HomeworkSubject(Long.toString(item.getSubjectId()), item.getSubjectName()),
                item.getTitle(), item.getDescription(), link,
                LocalDate.parse(item.getLessonDate()), item.getLessonNumber(), item.getCompleted(), completedAt);
    }

    private static Instant completionAt(boolean completed, boolean hasCompletedAt, String rawCompletedAt) {
        if (!completed) {
            if (hasCompletedAt) {
                throw new MobileBffException(HttpStatus.SERVICE_UNAVAILABLE, ProblemCode.DEPENDENCY_UNAVAILABLE,
                        "Academic Service вернул timestamp для незавершённого ДЗ");
            }
            return null;
        }
        if (!hasCompletedAt) {
            throw new MobileBffException(HttpStatus.SERVICE_UNAVAILABLE, ProblemCode.DEPENDENCY_UNAVAILABLE,
                    "Academic Service не вернул timestamp завершения ДЗ");
        }
        try {
            return Instant.parse(rawCompletedAt);
        } catch (RuntimeException error) {
            throw new MobileBffException(HttpStatus.SERVICE_UNAVAILABLE, ProblemCode.DEPENDENCY_UNAVAILABLE,
                    "Academic Service вернул некорректный timestamp завершения ДЗ");
        }
    }

    private static LessonScheduleProjection lesson(LessonResponse lesson, SubjectInfo subject) {
        if (subject == null) {
            throw new MobileBffException(HttpStatus.SERVICE_UNAVAILABLE, ProblemCode.DEPENDENCY_UNAVAILABLE,
                    "Не удалось получить предмет для пары");
        }
        String roomState = lesson.getRoomChangeState().isBlank() ? "UNKNOWN" : lesson.getRoomChangeState();
        return new LessonScheduleProjection(
                Long.toString(lesson.getId()), LocalDate.parse(lesson.getDate()), lesson.getLessonNumber(),
                LocalTime.parse(lesson.getStartTime()), LocalTime.parse(lesson.getEndTime()),
                LessonStatus.valueOf(lesson.getStatus().toUpperCase()),
                new SubjectProjection(Long.toString(subject.getSubjectId()), subject.getSubjectName(),
                        LessonType.valueOf(subject.getSubjectType().toUpperCase())),
                new RoomProjection(lesson.getRoom().isBlank() ? null : lesson.getRoom(),
                        lesson.hasPreviousRoom() ? lesson.getPreviousRoom() : null,
                        RoomChangeState.valueOf(roomState.toUpperCase())));
    }

    private static AttendanceProjection attendance(StudentAttendanceEntry entry) {
        if (entry == null || entry.getStatus()
                == ru.rutcampustrack.attendance.grpc.AttendanceStatus.ATTENDANCE_STATUS_UNSPECIFIED) return null;
        return new AttendanceProjection(
                AttendanceStatus.valueOf(entry.getStatus().name().replace("ATTENDANCE_STATUS_", "")),
                AttendanceSource.valueOf(entry.getSource().name().replace("ATTENDANCE_SOURCE_", "")),
                entry.hasMarkedAt() ? Instant.parse(entry.getMarkedAt()) : null);
    }

    private static CheckinEligibility eligibility(StudentAttendanceEntry entry) {
        if (entry == null || !entry.hasEligibility()) {
            throw new MobileBffException(HttpStatus.SERVICE_UNAVAILABLE, ProblemCode.DEPENDENCY_UNAVAILABLE,
                    "Attendance Service не вернул eligibility");
        }
        var value = entry.getEligibility();
        return new CheckinEligibility(value.getAllowed(),
                EligibilityReason.valueOf(value.getReason().name()
                        .replace("STUDENT_CHECKIN_ELIGIBILITY_REASON_", "")),
                value.hasRetryAt() ? Instant.parse(value.getRetryAt()) : null);
    }

    private static AutomaticRequestProjection request(StudentAttendanceEntry entry) {
        return entry == null || !entry.hasRequest() ? null : request(entry.getRequest());
    }

    static AutomaticRequestProjection request(AutomaticCheckinRequest request) {
        ResolutionReason resolution = request.getResolutionReason()
                == ru.rutcampustrack.attendance.grpc.AutomaticCheckinResolutionReason
                .AUTOMATIC_CHECKIN_RESOLUTION_REASON_UNSPECIFIED ? null
                : ResolutionReason.valueOf(request.getResolutionReason().name()
                .replace("AUTOMATIC_CHECKIN_RESOLUTION_REASON_", ""));
        return new AutomaticRequestProjection(request.getId(),
                RequestStatus.valueOf(request.getStatus().name()
                        .replace("AUTOMATIC_CHECKIN_REQUEST_STATUS_", "")),
                RequestOrigin.AUTO_GEO_FAILURE, resolution);
    }

    private static GroupSummary group(GroupResponse group) {
        return new GroupSummary(Long.toString(group.getId()), group.getName());
    }

    private static SemesterSummary semester(SemesterResponse semester) {
        return new SemesterSummary(Long.toString(semester.getId()), semester.getName(),
                LocalDate.parse(semester.getDateFrom()), LocalDate.parse(semester.getDateTo()));
    }

    private static StudentAttendanceResponse attendanceResponse(
            StudentAttendanceProjectionResponse response,
            SemesterResponse semester) {
        return new StudentAttendanceResponse(
                semester(semester),
                LocalDate.parse(response.getDateFrom()),
                LocalDate.parse(response.getDateTo()),
                Instant.parse(response.getServerNow()),
                response.getTerminalReadOnly(),
                metricSet(response.getMetrics()),
                response.getDaysList().stream().map(StudentQueryService::day).toList(),
                response.getSubjectsList().stream().map(StudentQueryService::subject).toList(),
                graph(response.getGraph()),
                rank(response.getOwnRank()),
                links("self", "/api/v1/student/attendance?semesterId=" + response.getSemesterId(),
                        "statistics", "/api/v1/student/statistics?semesterId=" + response.getSemesterId()));
    }

    private static StudentStatisticsResponse statisticsResponse(
            StudentAttendanceProjectionResponse response,
            SemesterResponse semester) {
        return new StudentStatisticsResponse(
                metricSet(response.getMetrics()),
                rank(response.getOwnRank()),
                response.getGraph().getWeeksList().stream().map(StudentQueryService::seriesPoint).toList(),
                response.getSubjectsList().stream()
                        .map(subject -> new StudentStatisticsSubject(
                                Long.toString(subject.getSubjectId()), subject.getName(), metricSet(subject.getMetrics())))
                        .toList(),
                links("self", "/api/v1/student/statistics?semesterId=" + response.getSemesterId(),
                        "attendance", "/api/v1/student/attendance?semesterId=" + response.getSemesterId()));
    }

    private static StudentAttendanceDay day(
            ru.rutcampustrack.attendance.grpc.StudentAttendanceDay source) {
        return new StudentAttendanceDay(
                LocalDate.parse(source.getDate()), source.getWeekday(), source.getDayNumber(),
                StudentAttendanceDayState.valueOf(source.getState()),
                source.getLessonsList().stream().map(StudentQueryService::lesson).toList());
    }

    private static StudentAttendanceLesson lesson(
            ru.rutcampustrack.attendance.grpc.StudentAttendanceLesson source) {
        return new StudentAttendanceLesson(
                Long.toString(source.getLessonId()), LocalDate.parse(source.getDate()),
                Integer.toString(source.getLessonNumber()),
                new SubjectProjection(Long.toString(source.getSubjectId()), source.getSubjectName(),
                        lessonType(source.getLessonType())),
                lessonType(source.getLessonType()),
                new AttendanceLessonSchedule(LocalTime.parse(source.getStartsAt()),
                        LocalTime.parse(source.getEndsAt()), source.hasRoom() ? source.getRoom() : null),
                StudentAttendanceLessonStatus.valueOf(source.getStatus()),
                source.getRequestOptionsList().stream().map(option ->
                        new StudentAttendanceRequestOption(option.getId(), option.getKind(), option.getLabel(),
                                option.getEnabled(), option.hasReason() ? option.getReason() : null)).toList());
    }

    private static StudentAttendanceSubject subject(
            ru.rutcampustrack.attendance.grpc.StudentAttendanceSubject source) {
        return new StudentAttendanceSubject(
                Long.toString(source.getSubjectId()), source.getName(), metricSet(source.getMetrics()),
                source.getAvailableTypesList().stream().map(StudentQueryService::lessonType).toList(),
                source.getSelectedTypesList().stream().map(StudentQueryService::lessonType).toList(),
                metricSet(source.getSelectedAggregate()),
                source.getTypeCardsList().stream().map(StudentQueryService::typeCard).toList(),
                source.getSeriesList().stream().map(StudentQueryService::seriesPoint).toList());
    }

    private static StudentAttendanceTypeCard typeCard(
            ru.rutcampustrack.attendance.grpc.StudentAttendanceTypeCard source) {
        return new StudentAttendanceTypeCard(
                lessonType(source.getLessonType()), metricSet(source.getMetrics()),
                source.getHistoryList().stream().map(StudentQueryService::history).toList());
    }

    private static StudentAttendanceHistorySegment history(
            ru.rutcampustrack.attendance.grpc.StudentAttendanceHistorySegment source) {
        return new StudentAttendanceHistorySegment(
                source.getId(), StudentAttendanceHistoryStatus.valueOf(source.getStatus()));
    }

    private static StudentAttendanceGraph graph(
            ru.rutcampustrack.attendance.grpc.StudentAttendanceGraph source) {
        return new StudentAttendanceGraph(
                source.getDaysList().stream().map(StudentQueryService::seriesPoint).toList(),
                source.getWeeksList().stream().map(StudentQueryService::seriesPoint).toList());
    }

    private static StudentAttendanceSeriesPoint seriesPoint(
            ru.rutcampustrack.attendance.grpc.StudentAttendanceSeriesPoint source) {
        return new StudentAttendanceSeriesPoint(
                source.getId(), source.getLabel(), LocalDate.parse(source.getDateFrom()),
                LocalDate.parse(source.getDateTo()),
                StudentAttendanceGraphState.valueOf(source.getState()), metricSet(source.getMetrics()));
    }

    private static StudentAttendanceOwnRank rank(
            ru.rutcampustrack.attendance.grpc.StudentAttendanceOwnRank source) {
        return new StudentAttendanceOwnRank(
                source.hasPosition() ? source.getPosition() : null,
                source.getParticipantCount(), source.getAvailable());
    }

    private static StudentAttendanceMetricSet metricSet(
            ru.rutcampustrack.attendance.grpc.StudentAttendanceMetricSet source) {
        return new StudentAttendanceMetricSet(
                metric(source.getPresent()), metric(source.getPresentOrExcused()),
                metric(source.getExcused()), metric(source.getAbsent()),
                source.getHeld(), source.getPlanned());
    }

    private static StudentAttendanceMetricValue metric(
            ru.rutcampustrack.attendance.grpc.StudentAttendanceMetric source) {
        return new StudentAttendanceMetricValue(source.getCount(), source.hasPercent() ? source.getPercent() : null);
    }

    private static LessonType lessonType(String value) {
        try {
            return LessonType.valueOf(value.trim().toUpperCase());
        } catch (RuntimeException error) {
            throw new MobileBffException(HttpStatus.SERVICE_UNAVAILABLE,
                    ProblemCode.DEPENDENCY_UNAVAILABLE, "Attendance Service вернул неизвестный тип пары");
        }
    }

    private static Map<String, Link> links(String... values) {
        Map<String, Link> links = new LinkedHashMap<>();
        for (int index = 0; index < values.length; index += 2) {
            links.put(values[index], new Link(URI.create(values[index + 1])));
        }
        return Map.copyOf(links);
    }
}
