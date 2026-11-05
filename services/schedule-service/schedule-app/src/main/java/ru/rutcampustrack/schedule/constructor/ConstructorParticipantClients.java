package ru.rutcampustrack.schedule.constructor;

import io.grpc.Channel;
import io.grpc.Metadata;
import io.grpc.stub.MetadataUtils;

import net.devh.boot.grpc.client.inject.GrpcClient;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import ru.rutcampustrack.constructor.grpc.ConstructorAcademicParticipantGrpc;
import ru.rutcampustrack.constructor.grpc.ConstructorAttendanceParticipantGrpc;
import ru.rutcampustrack.constructor.grpc.ConstructorDeletionCommand;
import ru.rutcampustrack.constructor.grpc.ConstructorParticipantReceipt;
import ru.rutcampustrack.constructor.grpc.PastLessonMaterializationCommand;
import ru.rutcampustrack.constructor.grpc.PastLessonMaterializationReceipt;
import ru.rutcampustrack.shared.security.grpc.DirectedServiceCredential;
import ru.rutcampustrack.shared.security.grpc.ServicePrincipal;
import ru.rutcampustrack.shared.security.grpc.ServiceTokenCallCredentials;
import ru.rutcampustrack.shared.security.grpc.ServiceTokenClientInterceptor;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.TimeUnit;

/** Only the new directed channels receive these credentials; recovery never stores a user JWT. */
@Component
public class ConstructorParticipantClients {
    private static final Metadata.Key<String> ACTOR =
            Metadata.Key.of("x-internal-token", Metadata.ASCII_STRING_MARSHALLER);
    private static final String ATTENDANCE =
            "rutcampustrack.constructor.ConstructorAttendanceParticipant/";
    private static final String ACADEMIC =
            "rutcampustrack.constructor.ConstructorAcademicParticipant/";

    @GrpcClient("constructor-attendance")
    private Channel attendance;

    @GrpcClient("constructor-academic")
    private Channel academic;

    @Value("${constructor.rpc.schedule-to-attendance-token-file:}")
    private String attendanceFile;

    @Value("${constructor.rpc.schedule-to-academic-token-file:}")
    private String academicFile;

    @Value("${grpc.client.constructor-attendance.security.authority-override:attendance-service}")
    private String attendanceAuthority;

    @Value("${grpc.client.constructor-academic.security.authority-override:academic-service}")
    private String academicAuthority;

    public ConstructorParticipantReceipt attendance(
            ConstructorDeletionCommand command, String signedActor) {
        return ConstructorAttendanceParticipantGrpc.newBlockingStub(attendance)
                .withInterceptors(
                        credentials(
                                attendanceFile,
                                "attendance-service",
                                attendanceAuthority,
                                ATTENDANCE + "ApplyDeletion",
                                ATTENDANCE + "MaterializePast"),
                        actor(signedActor))
                .withDeadlineAfter(5, TimeUnit.SECONDS)
                .applyDeletion(command);
    }

    public ConstructorParticipantReceipt academic(
            ConstructorDeletionCommand command, String signedActor) {
        return ConstructorAcademicParticipantGrpc.newBlockingStub(academic)
                .withInterceptors(
                        credentials(
                                academicFile,
                                "academic-service",
                                academicAuthority,
                                ACADEMIC + "ApplyDeletion"),
                        actor(signedActor))
                .withDeadlineAfter(5, TimeUnit.SECONDS)
                .applyDeletion(command);
    }

    public PastLessonMaterializationReceipt materialize(PastLessonMaterializationCommand command) {
        return ConstructorAttendanceParticipantGrpc.newBlockingStub(attendance)
                .withInterceptors(
                        credentials(
                                attendanceFile,
                                "attendance-service",
                                attendanceAuthority,
                                ATTENDANCE + "ApplyDeletion",
                                ATTENDANCE + "MaterializePast"))
                .withDeadlineAfter(15, TimeUnit.SECONDS)
                .materializePast(command);
    }

    private static io.grpc.ClientInterceptor actor(String token) {
        Metadata metadata = new Metadata();
        if (token != null && !token.isBlank()) metadata.put(ACTOR, token);
        return MetadataUtils.newAttachHeadersInterceptor(metadata);
    }

    private static ServiceTokenClientInterceptor credentials(
            String file, String target, String authority, String... methods) {
        try {
            if (file == null || file.isBlank() || Files.size(Path.of(file)) > 128)
                throw new IllegalArgumentException("Missing constructor credential");
            String token = Files.readString(Path.of(file), StandardCharsets.US_ASCII).strip();
            var credential =
                    new DirectedServiceCredential(
                            ServicePrincipal.SCHEDULE_SERVICE, new ServicePrincipal(target), token);
            return new ServiceTokenClientInterceptor(
                    new ServiceTokenCallCredentials(credential, authority, methods));
        } catch (Exception unavailable) {
            throw new ConstructorLifecycleException(
                    503,
                    "CONSTRUCTOR_PARTICIPANT_UNAVAILABLE",
                    "Участник операции временно недоступен");
        }
    }
}
