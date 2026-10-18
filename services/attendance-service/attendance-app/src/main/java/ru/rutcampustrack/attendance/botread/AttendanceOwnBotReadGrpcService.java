package ru.rutcampustrack.attendance.botread;

import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import io.grpc.stub.StreamObserver;

import net.devh.boot.grpc.server.service.GrpcService;

import ru.rutcampustrack.academic.grpc.GroupMembersResponse;
import ru.rutcampustrack.academic.grpc.StudentInfo;
import ru.rutcampustrack.attendance.exception.AccessDeniedException;
import ru.rutcampustrack.attendance.journal.JournalLessonPolicy;
import ru.rutcampustrack.attendance.report.ReportService;
import ru.rutcampustrack.authauthority.grpc.*;
import ru.rutcampustrack.botreads.grpc.*;
import ru.rutcampustrack.schedule.grpc.LessonResponse;
import ru.rutcampustrack.shared.web.time.EducationalTime;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Current own lesson only; it cannot serve a broad journal, report, edit, or excuse payload. */
@GrpcService(interceptors = AttendanceBotIdentityInterceptor.class)
public final class AttendanceOwnBotReadGrpcService
        extends AttendanceOwnBotReadServiceGrpc.AttendanceOwnBotReadServiceImplBase {
    private final AttendanceBotReadDependencies dependencies;
    private final ReportService reports;
    private final EducationalTime time;

    public AttendanceOwnBotReadGrpcService(
            AttendanceBotReadDependencies dependencies,
            ReportService reports,
            EducationalTime time) {
        this.dependencies = dependencies;
        this.reports = reports;
        this.time = time;
    }

    @Override
    public void getOwnBotLessonAttendance(
            BotOwnLessonAttendanceRequest request,
            StreamObserver<BotOwnLessonAttendanceResponse> observer) {
        try {
            AttendanceBotIdentityInterceptor.requireBff();
            var result = read(request);
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
                            .withDescription("current bot attendance unavailable")
                            .asRuntimeException());
        } catch (AccessDeniedException denied) {
            observer.onError(
                    Status.PERMISSION_DENIED
                            .withDescription("current own lesson required")
                            .asRuntimeException());
        } catch (RuntimeException error) {
            observer.onError(unavailable());
        }
    }

    BotOwnLessonAttendanceResponse read(BotOwnLessonAttendanceRequest request) {
        var identity = request.getIdentity();
        if (request.getLessonId() <= 0
                || identity.getTelegramSenderId() <= 0
                || identity.getPrivateChatId() != identity.getTelegramSenderId()
                || identity.getExpectedUserId() <= 0
                || identity.getExpectedBindingVersion() <= 0
                || identity.getExpectedRolesVersion() <= 0)
            throw Status.INVALID_ARGUMENT
                    .withDescription("current private bot identity required")
                    .asRuntimeException();
        Instant now = time.clock().instant();
        LocalDate today = now.atZone(JournalLessonPolicy.MOSCOW).toLocalDate();
        var authority = admit(identity);
        var scope = scope(identity, today);
        if (!authority.getAuthorizedGroupIdsList().contains(scope.getOwnGroupId()))
            throw unavailable();
        var lesson = dependencies.lesson(request.getLessonId());
        requireCurrent(lesson, request.getLessonId(), scope, now);
        GroupMembersResponse roster =
                GroupMembersResponse.newBuilder()
                        .setAsOfDate(today.toString())
                        .setSemesterId(scope.getSemesterId())
                        .addAllStudents(
                                scope.getRosterList().stream()
                                        .map(
                                                member ->
                                                        StudentInfo.newBuilder()
                                                                .setUserId(member.getStudentId())
                                                                .setDisplayName(
                                                                        member.getDisplayName())
                                                                .build())
                                        .toList())
                        .build();
        var read = reports.getDirectedBotLessonAttendance(lesson, scope.getOwnGroupId(), roster);
        List<BotAttendanceRow> rows = new ArrayList<>();
        for (var entry : read.getEntries()) {
            rows.add(
                    BotAttendanceRow.newBuilder()
                            .setStudentId(entry.getUserId())
                            .setDisplayName(entry.getDisplayName())
                            .setRecordPresent(entry.getStatus() != null)
                            .setStatus(entry.getStatus() == null ? "" : entry.getStatus())
                            .setSource(entry.getSource() == null ? "" : entry.getSource())
                            .build());
        }
        var result =
                BotOwnLessonAttendanceResponse.newBuilder()
                        .setLesson(summary(lesson, scope))
                        .addAllRows(rows)
                        .setStatistics(statistics(rows));
        var afterAdmission = admit(identity);
        var afterScope = scope(identity, today);
        if (!afterAdmission.getAuthorizedGroupIdsList().contains(scope.getOwnGroupId())
                || !scope.toBuilder()
                        .clearServerNow()
                        .build()
                        .equals(afterScope.toBuilder().clearServerNow().build())
                || !lesson.equals(dependencies.lesson(request.getLessonId())))
            throw Status.FAILED_PRECONDITION
                    .withDescription("current bot scope changed; refresh")
                    .asRuntimeException();
        Instant asOf = time.clock().instant();
        requireCurrent(lesson, request.getLessonId(), afterScope, asOf);
        return result.setServerNow(
                        asOf.atZone(JournalLessonPolicy.MOSCOW).toOffsetDateTime().toString())
                .setEducationalDate(today.toString())
                .build();
    }

    private CheckBotOwnAuthorityResponse admit(DirectedBotIdentity identity) {
        var authority = dependencies.check(identity);
        if (authority.getUserId() != identity.getExpectedUserId()
                || authority.getTelegramBindingVersion() != identity.getExpectedBindingVersion()
                || authority.getRolesVersion() != identity.getExpectedRolesVersion()
                || authority.getCapability()
                        != BotReadCapability.BOT_READ_CAPABILITY_HEADMAN_CURRENT_ATTENDANCE_READ)
            throw unavailable();
        return authority;
    }

    private BotAcademicScopeResponse scope(DirectedBotIdentity identity, LocalDate today) {
        var scope = dependencies.scope(identity);
        if (scope.getUserId() != identity.getExpectedUserId()
                || scope.getRolesVersion() != identity.getExpectedRolesVersion()
                || scope.getTelegramBindingVersion() != identity.getExpectedBindingVersion()
                || scope.getAction()
                        != BotAcademicReadAction.BOT_ACADEMIC_READ_ACTION_HEADMAN_CURRENT
                || scope.getEligibilityRevision() <= 0
                || scope.getOwnGroupId() <= 0
                || scope.getOwnGroupName().isBlank()
                || scope.getSemesterId() <= 0
                || !scope.getEducationalDate().equals(today.toString())
                || !Instant.parse(scope.getServerNow())
                        .atZone(JournalLessonPolicy.MOSCOW)
                        .toLocalDate()
                        .equals(today)) throw unavailable();
        return scope;
    }

    private static void requireCurrent(
            LessonResponse lesson, long lessonId, BotAcademicScopeResponse scope, Instant now) {
        if (lesson == null
                || lesson.getId() != lessonId
                || lesson.getGroupId() != scope.getOwnGroupId()
                || lesson.getSemesterId() != scope.getSemesterId()
                || !lesson.getDate().equals(scope.getEducationalDate())
                || !"active".equalsIgnoreCase(lesson.getStatus())
                || !List.of("", "COMPLETED").contains(lesson.getTransferState())) throw denied();
        var timing = JournalLessonPolicy.requireTiming(lesson);
        Instant end =
                timing.date()
                        .atTime(LocalTime.parse(lesson.getEndTime()))
                        .atZone(JournalLessonPolicy.MOSCOW)
                        .toInstant();
        if (!now.atZone(JournalLessonPolicy.MOSCOW).toLocalDate().equals(timing.date())
                || !end.isAfter(timing.startsAt())
                || now.isBefore(timing.startsAt())
                || !now.isBefore(end)) throw denied();
    }

    private static BotLessonSummary summary(LessonResponse lesson, BotAcademicScopeResponse scope) {
        var subject =
                scope.getSubjectsList().stream()
                        .filter(value -> value.getSubjectId() == lesson.getSubjectId())
                        .findFirst()
                        .orElseThrow(AttendanceOwnBotReadGrpcService::unavailable);
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

    private static BotAttendanceStatistics statistics(List<BotAttendanceRow> rows) {
        var counts = BotAttendanceStatistics.newBuilder().setRosterCount(rows.size());
        int marked = 0, present = 0, absent = 0, excused = 0, free = 0, cancelled = 0;
        for (var row : rows) {
            if (!row.getRecordPresent()) continue;
            marked++;
            switch (row.getStatus().toLowerCase(Locale.ROOT)) {
                case "present" -> present++;
                case "absent" -> absent++;
                case "excused" -> excused++;
                case "free_attendance" -> free++;
                case "cancelled" -> cancelled++;
                default -> throw unavailable();
            }
        }
        return counts.setMarkedCount(marked)
                .setPresentCount(present)
                .setAbsentCount(absent)
                .setExcusedCount(excused)
                .setFreeAttendanceCount(free)
                .setCancelledCount(cancelled)
                .setUnmarkedCount(rows.size() - marked)
                .build();
    }

    private static StatusRuntimeException denied() {
        return Status.PERMISSION_DENIED
                .withDescription("current own lesson required")
                .asRuntimeException();
    }

    private static StatusRuntimeException unavailable() {
        return Status.UNAVAILABLE
                .withDescription("current bot attendance unavailable")
                .asRuntimeException();
    }
}
