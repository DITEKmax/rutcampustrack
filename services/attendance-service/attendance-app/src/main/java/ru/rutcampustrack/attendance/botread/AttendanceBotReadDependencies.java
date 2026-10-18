package ru.rutcampustrack.attendance.botread;

import io.grpc.Status;

import net.devh.boot.grpc.client.inject.GrpcClient;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import ru.rutcampustrack.authauthority.grpc.*;
import ru.rutcampustrack.botreads.grpc.*;
import ru.rutcampustrack.schedule.grpc.*;
import ru.rutcampustrack.shared.security.grpc.*;

import java.util.concurrent.TimeUnit;

/** The Attendance bot read has three distinct exact-method directed credentials. */
@Component
public class AttendanceBotReadDependencies {
    private static final ServicePrincipal ATTENDANCE = new ServicePrincipal("attendance-service");

    @GrpcClient("bot-read-auth")
    private AuthIdentityGrpcServiceGrpc.AuthIdentityGrpcServiceBlockingStub auth;

    @GrpcClient("bot-read-academic")
    private AcademicOwnBotReadServiceGrpc.AcademicOwnBotReadServiceBlockingStub academic;

    @GrpcClient("bot-read-schedule")
    private ScheduleGrpcServiceGrpc.ScheduleGrpcServiceBlockingStub schedule;

    private final String authToken;
    private final String academicToken;
    private final String scheduleToken;
    private final String authAuthority;
    private final String academicAuthority;
    private final String scheduleAuthority;

    public AttendanceBotReadDependencies(
            @Value("${grpc.service-identity.attendance-to-auth-token:}") String authToken,
            @Value("${grpc.service-identity.attendance-bot-read-to-academic-token:}")
                    String academicToken,
            @Value("${grpc.service-identity.attendance-bot-read-to-schedule-token:}")
                    String scheduleToken,
            @Value("${grpc.client.bot-read-auth.security.authority-override:auth-service}")
                    String authAuthority,
            @Value("${grpc.client.bot-read-academic.security.authority-override:academic-service}")
                    String academicAuthority,
            @Value("${grpc.client.bot-read-schedule.security.authority-override:schedule-service}")
                    String scheduleAuthority) {
        this.authToken = authToken;
        this.academicToken = academicToken;
        this.scheduleToken = scheduleToken;
        this.authAuthority = authAuthority;
        this.academicAuthority = academicAuthority;
        this.scheduleAuthority = scheduleAuthority;
    }

    public CheckBotOwnAuthorityResponse check(DirectedBotIdentity identity) {
        return auth.withCallCredentials(
                        credentials(
                                "auth-service",
                                authToken,
                                authAuthority,
                                AuthIdentityGrpcServiceGrpc
                                        .getCheckAttendanceBotOwnAuthorityMethod()
                                        .getFullMethodName()))
                .withDeadlineAfter(3, TimeUnit.SECONDS)
                .checkAttendanceBotOwnAuthority(
                        CheckBotOwnAuthorityRequest.newBuilder()
                                .setTelegramSenderId(identity.getTelegramSenderId())
                                .setPrivateChatId(identity.getPrivateChatId())
                                .setExpectedUserId(identity.getExpectedUserId())
                                .setExpectedTelegramBindingVersion(
                                        identity.getExpectedBindingVersion())
                                .setExpectedRolesVersion(identity.getExpectedRolesVersion())
                                .setRequiredCapability(
                                        BotReadCapability
                                                .BOT_READ_CAPABILITY_HEADMAN_CURRENT_ATTENDANCE_READ)
                                .build());
    }

    public BotAcademicScopeResponse scope(DirectedBotIdentity identity) {
        return academic.withCallCredentials(
                        credentials(
                                "academic-service",
                                academicToken,
                                academicAuthority,
                                AcademicOwnBotReadServiceGrpc
                                        .getResolveAttendanceOwnBotReadScopeMethod()
                                        .getFullMethodName()))
                .withDeadlineAfter(3, TimeUnit.SECONDS)
                .resolveAttendanceOwnBotReadScope(
                        BotAcademicScopeRequest.newBuilder()
                                .setIdentity(identity)
                                .setAction(
                                        BotAcademicReadAction
                                                .BOT_ACADEMIC_READ_ACTION_HEADMAN_CURRENT)
                                .build());
    }

    public LessonResponse lesson(long lessonId) {
        return schedule.withCallCredentials(
                        credentials(
                                "schedule-service",
                                scheduleToken,
                                scheduleAuthority,
                                ScheduleGrpcServiceGrpc.getGetLessonByIdMethod()
                                        .getFullMethodName()))
                .withDeadlineAfter(3, TimeUnit.SECONDS)
                .getLessonById(LessonByIdRequest.newBuilder().setLessonId(lessonId).build());
    }

    private static ServiceTokenCallCredentials credentials(
            String target, String token, String authority, String method) {
        var credential =
                DirectedServiceCredential.tryCreate(ATTENDANCE, new ServicePrincipal(target), token)
                        .orElseThrow(
                                () ->
                                        Status.UNAVAILABLE
                                                .withDescription("bot read dependency unavailable")
                                                .asRuntimeException());
        return ServiceTokenCallCredentials.forMethod(credential, authority, method);
    }
}
