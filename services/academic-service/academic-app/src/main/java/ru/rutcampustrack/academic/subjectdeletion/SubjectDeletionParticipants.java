package ru.rutcampustrack.academic.subjectdeletion;

import net.devh.boot.grpc.client.inject.GrpcClient;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

import ru.rutcampustrack.shared.security.grpc.DirectedServiceCredential;
import ru.rutcampustrack.shared.security.grpc.ServicePrincipal;
import ru.rutcampustrack.shared.security.grpc.ServiceTokenCallCredentials;
import ru.rutcampustrack.subjectdeletion.grpc.*;

import java.util.concurrent.TimeUnit;

@Component
public class SubjectDeletionParticipants {
    @GrpcClient("schedule-service")
    private SubjectDeletionParticipantGrpc.SubjectDeletionParticipantBlockingStub schedule;

    @GrpcClient("attendance-service")
    private SubjectDeletionParticipantGrpc.SubjectDeletionParticipantBlockingStub attendance;

    private final String scheduleToken, attendanceToken;
    private final String scheduleAuthority, attendanceAuthority;

    public SubjectDeletionParticipants(
            @Value("${grpc.service-identity.academic-to-schedule-token:}") String scheduleToken,
            @Value("${grpc.service-identity.academic-to-attendance-token:}") String attendanceToken,
            @Value("${grpc.client.schedule-service.security.authority-override:schedule-service}")
                    String scheduleAuthority,
            @Value("${grpc.service-identity.attendance-authority:attendance-service}")
                    String attendanceAuthority) {
        this.scheduleToken = scheduleToken;
        this.attendanceToken = attendanceToken;
        this.scheduleAuthority = scheduleAuthority;
        this.attendanceAuthority = attendanceAuthority;
    }

    public SubjectDeletionReceipt schedule(SubjectDeletionCommand command) {
        return invoke(
                schedule,
                scheduleToken,
                ServicePrincipal.SCHEDULE_SERVICE,
                scheduleAuthority,
                command,
                SubjectDeletionUnavailableException.Phase.SCHEDULE_PREVIEW);
    }

    public SubjectDeletionReceipt attendance(SubjectDeletionCommand command) {
        return invoke(
                attendance,
                attendanceToken,
                new ServicePrincipal("attendance-service"),
                attendanceAuthority,
                command,
                SubjectDeletionUnavailableException.Phase.ATTENDANCE_PREVIEW);
    }

    private SubjectDeletionReceipt invoke(
            SubjectDeletionParticipantGrpc.SubjectDeletionParticipantBlockingStub stub,
            String token,
            ServicePrincipal target,
            String authority,
            SubjectDeletionCommand command,
            SubjectDeletionUnavailableException.Phase phase) {
        var credential =
                DirectedServiceCredential.tryCreate(
                                ServicePrincipal.ACADEMIC_SERVICE, target, token)
                        .orElseThrow(
                                () ->
                                        unavailable(
                                                command,
                                                phase,
                                                SubjectDeletionUnavailableException.Reason
                                                        .CREDENTIAL_CONFIGURATION,
                                                SubjectDeletionUnavailableException.RpcStatus
                                                        .NOT_APPLICABLE));
        if (authority == null || authority.isBlank())
            throw unavailable(
                    command,
                    phase,
                    SubjectDeletionUnavailableException.Reason.AUTHORITY_CONFIGURATION,
                    SubjectDeletionUnavailableException.RpcStatus.NOT_APPLICABLE);
        if (stub == null && command.getAction() == SubjectDeletionAction.PREVIEW)
            throw unavailable(
                    command,
                    phase,
                    SubjectDeletionUnavailableException.Reason.UNEXPECTED,
                    SubjectDeletionUnavailableException.RpcStatus.NOT_APPLICABLE);
        try {
            var response =
                    stub.withCallCredentials(
                                    ServiceTokenCallCredentials.forMethod(
                                            credential,
                                            authority,
                                            SubjectDeletionParticipantGrpc.getApplyMethod()
                                                    .getFullMethodName()))
                            .withDeadlineAfter(3, TimeUnit.SECONDS)
                            .apply(command);
            if (response.getSubjectId() != command.getSubjectId()
                    || response.getGroupId() != command.getGroupId()
                    || response.getSemesterId() != command.getSemesterId()
                    || response.getAction() != command.getAction()
                    || !response.getOperationId().equals(command.getOperationId())
                    || response.getActorId() != command.getActorId()
                    || !response.getManifestDigest().equals(command.getManifestDigest())
                    || command.getAction() != SubjectDeletionAction.PREVIEW
                            && !response.getParticipantDigest()
                                    .equals(command.getParticipantDigest())
                    || !response.getParticipantDigest().matches("[0-9a-f]{64}"))
                throw unavailable(
                        command,
                        phase,
                        SubjectDeletionUnavailableException.Reason.RECEIPT_MISMATCH,
                        SubjectDeletionUnavailableException.RpcStatus.NOT_APPLICABLE);
            return response;
        } catch (io.grpc.StatusRuntimeException rejected) {
            if (rejected.getStatus().getCode() == io.grpc.Status.Code.FAILED_PRECONDITION
                    || rejected.getStatus().getCode() == io.grpc.Status.Code.ABORTED)
                throw new ResponseStatusException(
                        HttpStatus.CONFLICT, "Состав данных предмета изменился");
            throw unavailable(
                    command,
                    phase,
                    SubjectDeletionUnavailableException.Reason.RPC_FAILURE,
                    SubjectDeletionUnavailableException.RpcStatus.from(
                            rejected.getStatus().getCode()));
        }
    }

    private static ResponseStatusException unavailable(
            SubjectDeletionCommand command,
            SubjectDeletionUnavailableException.Phase phase,
            SubjectDeletionUnavailableException.Reason reason,
            SubjectDeletionUnavailableException.RpcStatus rpcStatus) {
        return command.getAction() == SubjectDeletionAction.PREVIEW
                ? new SubjectDeletionUnavailableException(phase, reason, rpcStatus)
                : unavailable();
    }

    private static ResponseStatusException unavailable() {
        return new ResponseStatusException(
                HttpStatus.SERVICE_UNAVAILABLE, "Участник удаления недоступен");
    }
}
