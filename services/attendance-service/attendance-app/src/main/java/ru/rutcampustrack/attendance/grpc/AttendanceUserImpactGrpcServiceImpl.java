package ru.rutcampustrack.attendance.grpc;

import io.grpc.Status;
import io.grpc.stub.StreamObserver;
import net.devh.boot.grpc.server.service.GrpcService;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.HexFormat;

/** One authoritative count of all retained marks, including historical semesters. */
@GrpcService
public class AttendanceUserImpactGrpcServiceImpl
        extends AttendanceUserImpactGrpcServiceGrpc.AttendanceUserImpactGrpcServiceImplBase {
    private final MongoTemplate mongo;
    public AttendanceUserImpactGrpcServiceImpl(MongoTemplate mongo) { this.mongo = mongo; }

    @Override public void previewUserImpact(UserImpactRequest request, StreamObserver<UserImpactSnapshot> observer) {
        if (request.getUserId() <= 0) {
            observer.onError(Status.INVALID_ARGUMENT.withDescription("user_id must be positive").asRuntimeException());
            return;
        }
        try {
            long count = mongo.count(Query.query(Criteria.where("user_id").is(request.getUserId())), "attendances");
            long observedAt = Instant.now().toEpochMilli();
            // Content digest of this count only; not a durable database version.
            String digest = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(
                    ("user-impact-v1:" + request.getUserId() + ":" + count).getBytes(StandardCharsets.UTF_8)));
            observer.onNext(UserImpactSnapshot.newBuilder().setUserId(request.getUserId())
                    .setAttendanceMarksCount(count).setObservedAtEpochMs(observedAt).setSnapshotDigest(digest).build());
            observer.onCompleted();
        } catch (Exception error) {
            observer.onError(Status.UNAVAILABLE.withDescription("Attendance user impact unavailable").asRuntimeException());
        }
    }
}
