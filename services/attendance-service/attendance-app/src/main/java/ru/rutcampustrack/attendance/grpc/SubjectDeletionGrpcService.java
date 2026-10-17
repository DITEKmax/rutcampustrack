package ru.rutcampustrack.attendance.grpc;

import io.grpc.Status;
import io.grpc.stub.StreamObserver;

import net.devh.boot.grpc.server.service.GrpcService;

import ru.rutcampustrack.attendance.subjectdeletion.AttendanceSubjectDeletionParticipant;
import ru.rutcampustrack.subjectdeletion.grpc.*;

@GrpcService
public class SubjectDeletionGrpcService
        extends SubjectDeletionParticipantGrpc.SubjectDeletionParticipantImplBase {
    private final AttendanceSubjectDeletionParticipant participant;

    public SubjectDeletionGrpcService(AttendanceSubjectDeletionParticipant participant) {
        this.participant = participant;
    }

    @Override
    public void apply(
            SubjectDeletionCommand command, StreamObserver<SubjectDeletionReceipt> observer) {
        try {
            observer.onNext(participant.apply(command));
            observer.onCompleted();
        } catch (IllegalStateException | IllegalArgumentException conflict) {
            observer.onError(
                    Status.FAILED_PRECONDITION
                            .withDescription("Subject deletion scope or phase conflict")
                            .asRuntimeException());
        } catch (RuntimeException unavailable) {
            observer.onError(
                    Status.UNAVAILABLE
                            .withDescription("Subject deletion participant unavailable")
                            .asRuntimeException());
        }
    }
}
