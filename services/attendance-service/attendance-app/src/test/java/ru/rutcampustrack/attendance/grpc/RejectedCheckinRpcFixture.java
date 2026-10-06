package ru.rutcampustrack.attendance.grpc;

import io.grpc.Context;
import io.grpc.stub.StreamObserver;
import ru.rutcampustrack.shared.security.InternalJwtClaims;

/** Supplies the authenticated interceptor context to real-Mongo rejection scenarios. */
public final class RejectedCheckinRpcFixture {
    private RejectedCheckinRpcFixture() { }
    public static void checkin(AttendanceStudentGrpcServiceImpl service, InternalJwtClaims claims,
                               StudentCheckinCommand command, StreamObserver<StudentCheckinResult> observer) {
        Context.current().withValue(StudentGrpcIdentity.CLAIMS, claims).run(() -> service.checkin(command, observer));
    }
}
