package ru.rutcampustrack.mobilebff.botread;

import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import io.grpc.stub.StreamObserver;

import net.devh.boot.grpc.server.service.GrpcService;

import ru.rutcampustrack.authauthority.grpc.*;
import ru.rutcampustrack.botreads.grpc.*;
import ru.rutcampustrack.mobilebff.contract.model.TeacherApiModels;
import ru.rutcampustrack.mobilebff.teacher.TeacherReadFacade;
import ru.rutcampustrack.schedule.grpc.LessonResponse;
import ru.rutcampustrack.shared.web.time.EducationalTime;
import ru.rutcampustrack.teacher.grpc.TeacherAssignment;
import ru.rutcampustrack.teacher.grpc.TeacherAssignmentsResponse;

import java.time.Instant;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

/** Fixed personal bot reads composed from fresh Auth admission and Academic object eligibility. */
@GrpcService(interceptors = DirectedBotIdentityInterceptor.class)
public final class DirectedBotReadGrpcService
        extends DirectedBotReadServiceGrpc.DirectedBotReadServiceImplBase {
    private final BotReadDependencies dependencies;
    private final TeacherReadFacade teacher;
    private final EducationalTime time;

    public DirectedBotReadGrpcService(
            BotReadDependencies dependencies, TeacherReadFacade teacher, EducationalTime time) {
        this.dependencies = dependencies;
        this.teacher = teacher;
        this.time = time;
    }

    @Override
    public void getOwnTeacherToday(
            DirectedBotIdentity identity, StreamObserver<BotTeacherTodayResponse> observer) {
        answer(observer, () -> teacherToday(identity));
    }

    @Override
    public void getOwnHeadmanCurrentAttendance(
            BotHeadmanCurrentAttendanceRequest request,
            StreamObserver<BotHeadmanCurrentAttendanceResponse> observer) {
        answer(observer, () -> headman(request));
    }

    BotTeacherTodayResponse teacherToday(DirectedBotIdentity identity) {
        validateIdentity(identity);
        Instant now = time.clock().instant();
        LocalDate date = date(now);
        BotReadCapability capability = BotReadCapability.BOT_READ_CAPABILITY_TEACHER_TODAY_READ;
        admit(identity, capability);
        var scope =
                scope(identity, BotAcademicReadAction.BOT_ACADEMIC_READ_ACTION_TEACHER_TODAY, date);
        var assignments = TeacherAssignmentsResponse.newBuilder();
        var ids = new HashSet<Long>();
        for (var assignment : scope.getAssignmentsList()) {
            if (assignment.getAssignmentId() <= 0
                    || !ids.add(assignment.getAssignmentId())
                    || assignment.getGroupId() <= 0
                    || assignment.getSubjectId() <= 0
                    || assignment.getSemesterId() != scope.getSemesterId()
                    || assignment.getGroupName().isBlank()
                    || assignment.getSubjectName().isBlank()
                    || assignment.getLessonType().isBlank()) throw unavailable();
            assignments.addAssignments(
                    TeacherAssignment.newBuilder()
                            .setAssignmentId(assignment.getAssignmentId())
                            .setTeacherId(scope.getUserId())
                            .setGroupId(assignment.getGroupId())
                            .setSubjectId(assignment.getSubjectId())
                            .setSemesterId(assignment.getSemesterId())
                            .setLessonType(assignment.getLessonType())
                            .setValidFrom(assignment.getValidFrom())
                            .setValidUntilExclusive(assignment.getValidUntilExclusive())
                            .setGroupName(assignment.getGroupName())
                            .setSubjectName(assignment.getSubjectName()));
        }
        var result =
                teacher.directedBotDay(
                        scope.getUserId(),
                        scope.getSemesterId(),
                        date,
                        assignments.build(),
                        group -> dependencies.day(group, scope.getSemesterId(), date));
        recheck(identity, capability, scope, date);
        return BotTeacherTodayResponse.newBuilder()
                .setServerNow(offset(now))
                .setEducationalDate(date.toString())
                .addAllLessons(
                        result.lessons().stream().map(DirectedBotReadGrpcService::summary).toList())
                .build();
    }

    BotHeadmanCurrentAttendanceResponse headman(BotHeadmanCurrentAttendanceRequest request) {
        var identity = request.getIdentity();
        validateIdentity(identity);
        if (request.getSelectedLessonId() < 0) throw Status.INVALID_ARGUMENT.asRuntimeException();
        Instant now = time.clock().instant();
        LocalDate date = date(now);
        BotReadCapability capability =
                BotReadCapability.BOT_READ_CAPABILITY_HEADMAN_CURRENT_ATTENDANCE_READ;
        var admission = admit(identity, capability);
        var scope =
                scope(
                        identity,
                        BotAcademicReadAction.BOT_ACADEMIC_READ_ACTION_HEADMAN_CURRENT,
                        date);
        if (scope.getOwnGroupId() <= 0
                || scope.getOwnGroupName().isBlank()
                || !admission.getAuthorizedGroupIdsList().contains(scope.getOwnGroupId()))
            throw unavailable();
        var lessons = dependencies.day(scope.getOwnGroupId(), scope.getSemesterId(), date);
        var selection =
                CurrentHeadmanLessonPolicy.select(
                        scope.getOwnGroupId(),
                        scope.getSemesterId(),
                        now,
                        lessons.getLessonsList(),
                        request.getSelectedLessonId());
        var response =
                BotHeadmanCurrentAttendanceResponse.newBuilder()
                        .setServerNow(offset(now))
                        .setEducationalDate(date.toString());
        switch (selection.state()) {
            case NO_CURRENT ->
                    response.setState(
                            BotCurrentAttendanceState.BOT_CURRENT_ATTENDANCE_STATE_NO_CURRENT);
            case CHOOSE ->
                    response.setState(BotCurrentAttendanceState.BOT_CURRENT_ATTENDANCE_STATE_CHOOSE)
                            .addAllChoices(
                                    selection.choices().stream()
                                            .map(lesson -> summary(lesson, scope))
                                            .toList());
            case READY -> {
                var selected = summary(selection.lesson(), scope);
                var attendance = dependencies.attendance(identity, selected.getLessonId());
                if (!attendance.hasLesson()
                        || !attendance.getLesson().equals(selected)
                        || !attendance.getEducationalDate().equals(date.toString())
                        || !date(Instant.parse(attendance.getServerNow())).equals(date))
                    throw unavailable();
                validateRoster(scope, attendance);
                response.setState(BotCurrentAttendanceState.BOT_CURRENT_ATTENDANCE_STATE_READY)
                        .setLesson(attendance.getLesson())
                        .addAllRows(attendance.getRowsList())
                        .setStatistics(attendance.getStatistics())
                        .setServerNow(attendance.getServerNow());
            }
        }
        recheck(identity, capability, scope, date);
        // Do not return a lesson or callback choices after their current time window ended.
        var latest =
                CurrentHeadmanLessonPolicy.select(
                        scope.getOwnGroupId(),
                        scope.getSemesterId(),
                        time.clock().instant(),
                        lessons.getLessonsList(),
                        request.getSelectedLessonId());
        if (!selection.equals(latest))
            throw Status.FAILED_PRECONDITION
                    .withDescription("current lesson changed; refresh")
                    .asRuntimeException();
        return response.build();
    }

    private CheckBotOwnAuthorityResponse admit(
            DirectedBotIdentity identity, BotReadCapability capability) {
        var authority = dependencies.check(identity, capability);
        if (authority.getUserId() != identity.getExpectedUserId()
                || authority.getTelegramBindingVersion() != identity.getExpectedBindingVersion()
                || authority.getRolesVersion() != identity.getExpectedRolesVersion()
                || authority.getCapability() != capability) throw unavailable();
        return authority;
    }

    private BotAcademicScopeResponse scope(
            DirectedBotIdentity identity, BotAcademicReadAction action, LocalDate date) {
        var scope = dependencies.scope(identity, action);
        if (scope.getUserId() != identity.getExpectedUserId()
                || scope.getTelegramBindingVersion() != identity.getExpectedBindingVersion()
                || scope.getRolesVersion() != identity.getExpectedRolesVersion()
                || scope.getAction() != action
                || scope.getEligibilityRevision() <= 0
                || scope.getSemesterId() <= 0
                || !scope.getEducationalDate().equals(date.toString())
                || !date(Instant.parse(scope.getServerNow())).equals(date)) throw unavailable();
        if (LocalDate.parse(scope.getSemesterDateTo())
                .isBefore(LocalDate.parse(scope.getSemesterDateFrom()))) throw unavailable();
        return scope;
    }

    private void recheck(
            DirectedBotIdentity identity,
            BotReadCapability capability,
            BotAcademicScopeResponse before,
            LocalDate date) {
        var admission = admit(identity, capability);
        if (before.getOwnGroupId() > 0
                && !admission.getAuthorizedGroupIdsList().contains(before.getOwnGroupId()))
            throw unavailable();
        var after = scope(identity, before.getAction(), date);
        if (!before.toBuilder()
                        .clearServerNow()
                        .build()
                        .equals(after.toBuilder().clearServerNow().build())
                || !date(time.clock().instant()).equals(date)) {
            throw Status.FAILED_PRECONDITION
                    .withDescription("current bot scope changed; refresh")
                    .asRuntimeException();
        }
    }

    private static void validateIdentity(DirectedBotIdentity identity) {
        if (identity.getTelegramSenderId() <= 0
                || identity.getPrivateChatId() != identity.getTelegramSenderId()
                || identity.getExpectedUserId() <= 0
                || identity.getExpectedBindingVersion() <= 0
                || identity.getExpectedRolesVersion() <= 0) {
            throw Status.INVALID_ARGUMENT
                    .withDescription("current private bot identity required")
                    .asRuntimeException();
        }
    }

    private static void validateRoster(
            BotAcademicScopeResponse scope, BotOwnLessonAttendanceResponse response) {
        Map<Long, String> expected = new HashMap<>();
        for (var member : scope.getRosterList()) {
            if (member.getStudentId() <= 0
                    || member.getDisplayName().isBlank()
                    || expected.put(member.getStudentId(), member.getDisplayName()) != null)
                throw unavailable();
        }
        var seen = new HashSet<Long>();
        for (var row : response.getRowsList()) {
            if (!seen.add(row.getStudentId())
                    || !row.getDisplayName().equals(expected.get(row.getStudentId()))
                    || row.getRecordPresent() == row.getStatus().isBlank()) throw unavailable();
        }
        if (seen.size() != expected.size()
                || !response.hasStatistics()
                || response.getStatistics().getRosterCount() != seen.size()) throw unavailable();
    }

    private static BotLessonSummary summary(TeacherApiModels.DayLesson lesson) {
        return BotLessonSummary.newBuilder()
                .setLessonId(Long.parseLong(lesson.id()))
                .setGroupId(Long.parseLong(lesson.groupId()))
                .setSubjectId(Long.parseLong(lesson.subjectId()))
                .setSemesterId(Long.parseLong(lesson.semesterId()))
                .setGroupName(lesson.groupName())
                .setSubjectName(lesson.subjectName())
                .setLessonType(lesson.lessonType())
                .setLessonDate(lesson.date().toString())
                .setLessonNumber(lesson.lessonNumber())
                .setStartTime(lesson.startsAt() == null ? "" : lesson.startsAt().toString())
                .setEndTime(lesson.endsAt() == null ? "" : lesson.endsAt().toString())
                .setRoom(lesson.room() == null ? "" : lesson.room())
                .setStatus(lesson.status())
                .setCancelled(lesson.cancelled())
                .setMoved(lesson.moved())
                .setOneOff(lesson.oneOff())
                .build();
    }

    private static BotLessonSummary summary(LessonResponse lesson, BotAcademicScopeResponse scope) {
        var subject =
                scope.getSubjectsList().stream()
                        .filter(value -> value.getSubjectId() == lesson.getSubjectId())
                        .findFirst()
                        .orElseThrow(DirectedBotReadGrpcService::unavailable);
        if (subject.getSubjectName().isBlank()
                || !subject.getLessonTypesList().stream()
                        .anyMatch(type -> type.equalsIgnoreCase(lesson.getLessonType())))
            throw unavailable();
        return BotLessonSummary.newBuilder()
                .setLessonId(lesson.getId())
                .setGroupId(lesson.getGroupId())
                .setSubjectId(lesson.getSubjectId())
                .setSemesterId(lesson.getSemesterId())
                .setGroupName(scope.getOwnGroupName())
                .setSubjectName(subject.getSubjectName())
                .setLessonType(lesson.getLessonType())
                .setLessonDate(lesson.getDate())
                .setLessonNumber(lesson.getLessonNumber())
                .setStartTime(lesson.getStartTime())
                .setEndTime(lesson.getEndTime())
                .setRoom(lesson.getRoom())
                .setStatus(lesson.getStatus())
                .setCancelled(false)
                .setMoved(lesson.getRevision() > 1 || lesson.getGeneration() > 1)
                .setOneOff(lesson.getScheduleItemId() <= 0)
                .build();
    }

    private static LocalDate date(Instant now) {
        return now.atZone(CurrentHeadmanLessonPolicy.MOSCOW).toLocalDate();
    }

    private static String offset(Instant now) {
        return now.atZone(CurrentHeadmanLessonPolicy.MOSCOW).toOffsetDateTime().toString();
    }

    private static StatusRuntimeException unavailable() {
        return Status.UNAVAILABLE
                .withDescription("current bot read unavailable")
                .asRuntimeException();
    }

    private static <T> void answer(StreamObserver<T> observer, Supplier<T> operation) {
        try {
            DirectedBotIdentityInterceptor.requireBot();
            T result = operation.get();
            observer.onNext(result);
            observer.onCompleted();
        } catch (StatusRuntimeException error) {
            var code = error.getStatus().getCode();
            if (!List.of(
                            Status.Code.INVALID_ARGUMENT,
                            Status.Code.UNAUTHENTICATED,
                            Status.Code.PERMISSION_DENIED,
                            Status.Code.FAILED_PRECONDITION)
                    .contains(code)) {
                code = Status.Code.UNAVAILABLE;
            }
            observer.onError(
                    Status.fromCode(code)
                            .withDescription("current bot read unavailable")
                            .asRuntimeException());
        } catch (RuntimeException error) {
            observer.onError(unavailable());
        }
    }
}
