package ru.rutcampustrack.schedule.grpc;

import io.grpc.stub.StreamObserver;

import net.devh.boot.grpc.server.service.GrpcService;

import ru.rutcampustrack.groupdeletion.grpc.*;
import ru.rutcampustrack.schedule.groupdeletion.ScheduleGroupDeletionParticipant;

@GrpcService
public class GroupDeletionGrpcService
        extends GroupDeletionParticipantGrpc.GroupDeletionParticipantImplBase {
    private final ScheduleGroupDeletionParticipant participant;

    public GroupDeletionGrpcService(ScheduleGroupDeletionParticipant participant) {
        this.participant = participant;
    }

    @Override
    public void apply(GroupDeletionCommand c, StreamObserver<GroupDeletionReceipt> observer) {
        try {
            observer.onNext(participant.apply(c));
            observer.onCompleted();
        } catch (io.grpc.StatusRuntimeException exact) {
            observer.onError(exact);
        } catch (RuntimeException unavailable) {
            observer.onError(
                    io.grpc.Status.UNAVAILABLE
                            .withDescription("Group deletion participant unavailable")
                            .asRuntimeException());
        }
    }
}
