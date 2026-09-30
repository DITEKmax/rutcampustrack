package ru.rutcampustrack.schedule.grpc;

import io.grpc.Status;
import org.springframework.stereotype.Service;
import ru.rutcampustrack.academic.grpc.SemesterStateResponse;

/** Admits barrier mutations only while Academic's uncached authority is at the exact epoch. */
@Service
public class ScheduleSemesterArchiveBarrierService {

    private final AcademicGrpcClient academicGrpcClient;
    private final ScheduleSemesterArchiveBarrierTransaction transaction;

    public ScheduleSemesterArchiveBarrierService(AcademicGrpcClient academicGrpcClient,
                                                 ScheduleSemesterArchiveBarrierTransaction transaction) {
        this.academicGrpcClient = academicGrpcClient;
        this.transaction = transaction;
    }

    public SetSemesterArchiveBarrierResponse set(SetSemesterArchiveBarrierRequest request) {
        if (request.getSemesterId() <= 0 || request.getStateVersion() < 0) {
            throw Status.INVALID_ARGUMENT.withDescription("invalid semester archive barrier identity")
                    .asRuntimeException();
        }
        final SemesterStateResponse authority;
        try {
            authority = academicGrpcClient.getSemesterArchiveAuthorityState(request.getSemesterId());
        } catch (RuntimeException unavailable) {
            throw Status.UNAVAILABLE.withDescription("Academic archive authority could not be verified")
                    .withCause(unavailable).asRuntimeException();
        }
        if (authority.getId() != request.getSemesterId()
                || authority.getStateVersion() != request.getStateVersion()
                || requiresOperationMatch(request, authority)
                && !request.getOperationId().equals(authority.getTransitionOperationId())
                || !matchesAuthority(request.getCommand(), authority)) {
            throw Status.FAILED_PRECONDITION
                    .withDescription("Academic authority does not match the requested archive barrier epoch")
                    .asRuntimeException();
        }
        return transaction.apply(request);
    }

    private static boolean matchesAuthority(SemesterArchiveBarrierCommand command,
                                            SemesterStateResponse authority) {
        return switch (command) {
            case SEMESTER_ARCHIVE_BARRIER_PREPARE_ARCHIVE ->
                    authority.getTransition() == ru.rutcampustrack.academic.grpc.SemesterTransition.ARCHIVING
                            && !authority.getArchived() && !authority.getActive() && !authority.getReleasePending();
            case SEMESTER_ARCHIVE_BARRIER_PREPARE_RESTORE ->
                    authority.getTransition() == ru.rutcampustrack.academic.grpc.SemesterTransition.RESTORING
                            && authority.getArchived() && !authority.getActive() && !authority.getReleasePending();
            case SEMESTER_ARCHIVE_BARRIER_RELEASE_RESTORE ->
                    authority.getTransition() == ru.rutcampustrack.academic.grpc.SemesterTransition.NONE
                            && !authority.getArchived() && !authority.getActive() && authority.getReleasePending();
            case SEMESTER_ARCHIVE_BARRIER_RECONCILE_HOMEWORK_BINDING ->
                    !authority.getActive() && !authority.getReleasePending()
                            && (!authority.getArchived() && authority.getTransition() == ru.rutcampustrack.academic.grpc.SemesterTransition.ARCHIVING
                            || authority.getTransition() == ru.rutcampustrack.academic.grpc.SemesterTransition.DELETING
                            && "PREPARING".equals(authority.getDeletionPhase()));
            case SEMESTER_ARCHIVE_BARRIER_PREPARE_DELETE,
                 SEMESTER_ARCHIVE_BARRIER_SEAL_DELETE ->
                    authority.getTransition() == ru.rutcampustrack.academic.grpc.SemesterTransition.DELETING
                            && "PREPARING".equals(authority.getDeletionPhase())
                            && !authority.getActive() && !authority.getReleasePending();
            case SEMESTER_ARCHIVE_BARRIER_RELEASE_DELETE ->
                    authority.getTransition() == ru.rutcampustrack.academic.grpc.SemesterTransition.DELETING
                            && "RELEASING".equals(authority.getDeletionPhase())
                            && !authority.getActive() && !authority.getReleasePending();
            case SEMESTER_ARCHIVE_BARRIER_COMMIT_DELETE ->
                    authority.getTransition() == ru.rutcampustrack.academic.grpc.SemesterTransition.DELETING
                            && "DELETING".equals(authority.getDeletionPhase())
                            && !authority.getActive() && !authority.getReleasePending();
            default -> false;
        };
    }

    private static boolean isDeletionCommand(SemesterArchiveBarrierCommand command) {
        return command == SemesterArchiveBarrierCommand.SEMESTER_ARCHIVE_BARRIER_PREPARE_DELETE
                || command == SemesterArchiveBarrierCommand.SEMESTER_ARCHIVE_BARRIER_SEAL_DELETE
                || command == SemesterArchiveBarrierCommand.SEMESTER_ARCHIVE_BARRIER_RELEASE_DELETE
                || command == SemesterArchiveBarrierCommand.SEMESTER_ARCHIVE_BARRIER_COMMIT_DELETE;
    }

    private static boolean requiresOperationMatch(SetSemesterArchiveBarrierRequest request,
                                                  SemesterStateResponse authority) {
        return isDeletionCommand(request.getCommand())
                || request.getCommand()
                    == SemesterArchiveBarrierCommand.SEMESTER_ARCHIVE_BARRIER_RECONCILE_HOMEWORK_BINDING
                && authority.getTransition() == ru.rutcampustrack.academic.grpc.SemesterTransition.DELETING;
    }
}
