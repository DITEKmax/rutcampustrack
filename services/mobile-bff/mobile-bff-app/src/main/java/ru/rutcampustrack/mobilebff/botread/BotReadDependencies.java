package ru.rutcampustrack.mobilebff.botread;

import io.grpc.Status;

import net.devh.boot.grpc.client.inject.GrpcClient;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import ru.rutcampustrack.authauthority.grpc.*;
import ru.rutcampustrack.botreads.grpc.*;
import ru.rutcampustrack.schedule.grpc.*;
import ru.rutcampustrack.shared.security.grpc.*;

import java.time.LocalDate;
import java.util.concurrent.TimeUnit;

/** Each credential is directed to one receiver and an exact read method. */
@Component
public class BotReadDependencies {
    private static final ServicePrincipal BFF = new ServicePrincipal("mobile-bff");

    @GrpcClient("bot-read-auth")
    private AuthIdentityGrpcServiceGrpc.AuthIdentityGrpcServiceBlockingStub auth;

    @GrpcClient("bot-read-academic")
    private AcademicOwnBotReadServiceGrpc.AcademicOwnBotReadServiceBlockingStub academic;

    @GrpcClient("bot-read-attendance")
    private AttendanceOwnBotReadServiceGrpc.AttendanceOwnBotReadServiceBlockingStub attendance;

    @GrpcClient("bot-read-schedule")
    private ScheduleGrpcServiceGrpc.ScheduleGrpcServiceBlockingStub schedule;

    private final String authToken;
    private final String academicToken;
    private final String attendanceToken;
    private final String authAuthority;
    private final String academicAuthority;
    private final String attendanceAuthority;
    private final String scheduleToken;
    private final String scheduleAuthority;

    public BotReadDependencies(
            @Value("${grpc.service-identity.mobile-bff-to-auth-token:}") String authToken,
            @Value("${grpc.service-identity.mobile-bff-to-academic-token:}") String academicToken,
            @Value("${grpc.service-identity.mobile-bff-to-attendance-token:}")
                    String attendanceToken,
            @Value("${grpc.client.bot-read-auth.security.authority-override:auth-service}")
                    String authAuthority,
            @Value("${grpc.client.bot-read-academic.security.authority-override:academic-service}")
                    String academicAuthority,
            @Value(
                            "${grpc.client.bot-read-attendance.security.authority-override:attendance-service}")
                    String attendanceAuthority,
            @Value("${grpc.service-identity.mobile-bff-to-schedule-token:}") String scheduleToken,
            @Value("${grpc.client.bot-read-schedule.security.authority-override:schedule-service}")
                    String scheduleAuthority) {
        this.authToken = authToken;
        this.academicToken = academicToken;
        this.attendanceToken = attendanceToken;
        this.authAuthority = authAuthority;
        this.academicAuthority = academicAuthority;
        this.attendanceAuthority = attendanceAuthority;
        this.scheduleToken = scheduleToken;
        this.scheduleAuthority = scheduleAuthority;
    }

    public CheckBotOwnAuthorityResponse check(
            DirectedBotIdentity identity, BotReadCapability capability) {
        var request =
                CheckBotOwnAuthorityRequest.newBuilder()
                        .setTelegramSenderId(identity.getTelegramSenderId())
                        .setPrivateChatId(identity.getPrivateChatId())
                        .setExpectedUserId(identity.getExpectedUserId())
                        .setExpectedTelegramBindingVersion(identity.getExpectedBindingVersion())
                        .setExpectedRolesVersion(identity.getExpectedRolesVersion())
                        .setRequiredCapability(capability)
                        .build();
        return auth.withCallCredentials(
                        credentials(
                                "auth-service",
                                authToken,
                                authAuthority,
                                AuthIdentityGrpcServiceGrpc.getCheckBotOwnAuthorityMethod()
                                        .getFullMethodName()))
                .withDeadlineAfter(3, TimeUnit.SECONDS)
                .checkBotOwnAuthority(request);
    }

    public BotAcademicScopeResponse scope(
            DirectedBotIdentity identity, BotAcademicReadAction action) {
        return academic.withCallCredentials(
                        credentials(
                                "academic-service",
                                academicToken,
                                academicAuthority,
                                AcademicOwnBotReadServiceGrpc.getResolveOwnBotReadScopeMethod()
                                        .getFullMethodName()))
                .withDeadlineAfter(3, TimeUnit.SECONDS)
                .resolveOwnBotReadScope(
                        BotAcademicScopeRequest.newBuilder()
                                .setIdentity(identity)
                                .setAction(action)
                                .build());
    }

    public LessonsResponse day(long groupId, long semesterId, LocalDate date) {
        return schedule.withCallCredentials(
                        credentials(
                                "schedule-service",
                                scheduleToken,
                                scheduleAuthority,
                                ScheduleGrpcServiceGrpc.getGetLessonsByGroupMethod()
                                        .getFullMethodName()))
                .withDeadlineAfter(3, TimeUnit.SECONDS)
                .getLessonsByGroup(
                        LessonsByGroupRequest.newBuilder()
                                .setGroupId(groupId)
                                .setSemesterId(semesterId)
                                .setDateFrom(date.toString())
                                .setDateTo(date.toString())
                                .build());
    }

    public BotOwnLessonAttendanceResponse attendance(DirectedBotIdentity identity, long lessonId) {
        return attendance
                .withCallCredentials(
                        credentials(
                                "attendance-service",
                                attendanceToken,
                                attendanceAuthority,
                                AttendanceOwnBotReadServiceGrpc.getGetOwnBotLessonAttendanceMethod()
                                        .getFullMethodName()))
                .withDeadlineAfter(3, TimeUnit.SECONDS)
                .getOwnBotLessonAttendance(
                        BotOwnLessonAttendanceRequest.newBuilder()
                                .setIdentity(identity)
                                .setLessonId(lessonId)
                                .build());
    }

    private static ServiceTokenCallCredentials credentials(
            String target, String token, String authority, String method) {
        var credential =
                DirectedServiceCredential.tryCreate(BFF, new ServicePrincipal(target), token)
                        .orElseThrow(BotReadDependencies::unavailable);
        return ServiceTokenCallCredentials.forMethod(credential, authority, method);
    }

    private static io.grpc.StatusRuntimeException unavailable() {
        return Status.UNAVAILABLE
                .withDescription("bot read dependency unavailable")
                .asRuntimeException();
    }
}
