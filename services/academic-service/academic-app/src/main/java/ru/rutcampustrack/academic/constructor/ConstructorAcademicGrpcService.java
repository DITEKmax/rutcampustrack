package ru.rutcampustrack.academic.constructor;

import io.grpc.Status;
import io.grpc.stub.StreamObserver;
import net.devh.boot.grpc.server.service.GrpcService;
import ru.rutcampustrack.constructor.grpc.ConstructorAcademicParticipantGrpc;
import ru.rutcampustrack.constructor.grpc.ConstructorDeletionCommand;
import ru.rutcampustrack.constructor.grpc.ConstructorParticipantReceipt;

@GrpcService
public class ConstructorAcademicGrpcService
        extends ConstructorAcademicParticipantGrpc.ConstructorAcademicParticipantImplBase {
    private final ConstructorAcademicParticipant participant;

    public ConstructorAcademicGrpcService(ConstructorAcademicParticipant participant) {
        this.participant = participant;
    }

    @Override
    public void applyDeletion(
            ConstructorDeletionCommand command, StreamObserver<ConstructorParticipantReceipt> reply) {
        try {
            reply.onNext(participant.apply(command));
            reply.onCompleted();
        } catch (io.grpc.StatusRuntimeException rejected) {
            reply.onError(rejected);
        } catch (RuntimeException unavailable) {
            reply.onError(Status.UNAVAILABLE.withDescription("constructor participant unavailable").asRuntimeException());
        }
    }
}
