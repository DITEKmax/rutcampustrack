package ru.rutcampustrack.academic.grpc;

import io.grpc.Context;
import ru.rutcampustrack.shared.security.InternalJwtClaims;

/** Test-only bridge for invoking the student homework service with signed claims. */
public final class StudentHomeworkTestIdentity {
    private StudentHomeworkTestIdentity() {
    }

    public static void run(InternalJwtClaims claims, Runnable action) {
        Context.current()
                .withValue(StudentHomeworkGrpcIdentity.CLAIMS, claims)
                .run(action);
    }
}
