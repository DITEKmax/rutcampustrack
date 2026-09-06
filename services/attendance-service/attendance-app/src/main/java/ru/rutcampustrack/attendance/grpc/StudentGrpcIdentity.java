package ru.rutcampustrack.attendance.grpc;

import io.grpc.Context;
import ru.rutcampustrack.shared.security.InternalJwtClaims;

final class StudentGrpcIdentity {
    static final Context.Key<InternalJwtClaims> CLAIMS = Context.key("student-internal-jwt-claims");

    private StudentGrpcIdentity() {
    }
}
