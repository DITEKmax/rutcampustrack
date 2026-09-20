package ru.rutcampustrack.attendance.grpc;

import com.google.protobuf.Any;
import io.grpc.Status;
import io.grpc.protobuf.StatusProto;
import ru.rutcampustrack.attendance.student.StudentCheckinException;

final class StudentCheckinGrpcErrors {

    private StudentCheckinGrpcErrors() {
    }

    static RuntimeException toStatus(StudentCheckinException error) {
        StudentCheckinErrorDetail.Builder detail = StudentCheckinErrorDetail.newBuilder()
                .setCode(StudentCheckinErrorCode.valueOf(
                        "STUDENT_CHECKIN_ERROR_CODE_" + error.code().name()));
        if (error.retryAt() != null) detail.setRetryAt(error.retryAt().toString());

        com.google.rpc.Status status = com.google.rpc.Status.newBuilder()
                .setCode(grpcCode(error.code()).value())
                .setMessage(error.getMessage())
                .addDetails(Any.pack(detail.build()))
                .build();
        return StatusProto.toStatusRuntimeException(status);
    }

    private static Status.Code grpcCode(StudentCheckinException.Code code) {
        return switch (code) {
            case INVALID_REQUEST, INVALID_IDEMPOTENCY_KEY -> Status.Code.INVALID_ARGUMENT;
            case INVALID_SESSION -> Status.Code.UNAUTHENTICATED;
            case WRONG_ROLE, OUT_OF_SCOPE -> Status.Code.PERMISSION_DENIED;
            case LESSON_NOT_FOUND -> Status.Code.NOT_FOUND;
            case CHECKIN_COOLDOWN -> Status.Code.RESOURCE_EXHAUSTED;
            case MANUAL_ABSENCE_REQUIRES_APPEAL, CHECKIN_NOT_ELIGIBLE -> Status.Code.FAILED_PRECONDITION;
            case IDEMPOTENCY_PAYLOAD_MISMATCH -> Status.Code.ALREADY_EXISTS;
            case DEPENDENCY_UNAVAILABLE -> Status.Code.UNAVAILABLE;
        };
    }
}
