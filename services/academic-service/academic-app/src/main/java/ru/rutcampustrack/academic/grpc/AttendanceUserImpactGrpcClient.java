package ru.rutcampustrack.academic.grpc;

import net.devh.boot.grpc.client.inject.GrpcClient;
import org.springframework.stereotype.Component;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import ru.rutcampustrack.attendance.grpc.*;
import java.util.concurrent.TimeUnit;

@Component
public class AttendanceUserImpactGrpcClient {
    @GrpcClient("attendance-service")
    private AttendanceUserImpactGrpcServiceGrpc.AttendanceUserImpactGrpcServiceBlockingStub stub;
    public UserImpactSnapshot preview(long userId) {
        try {
            UserImpactSnapshot result = stub.withDeadlineAfter(3, TimeUnit.SECONDS)
                    .previewUserImpact(UserImpactRequest.newBuilder().setUserId(userId).build());
            if (result.getUserId() != userId || result.getAttendanceMarksCount() < 0
                    || result.getObservedAtEpochMs() <= 0 || !result.getSnapshotDigest().matches("[0-9a-f]{64}")) {
                throw new IllegalStateException("Invalid impact snapshot");
            }
            return result;
        } catch (RuntimeException error) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Предпросмотр посещаемости недоступен");
        }
    }
}
