package ru.rutcampustrack.attendance.grpc;

import com.google.rpc.Status;
import io.grpc.protobuf.StatusProto;
import org.junit.jupiter.api.Test;
import ru.rutcampustrack.attendance.student.StudentCheckinException;

import static org.assertj.core.api.Assertions.assertThat;

class StudentRequestGrpcErrorsTest {

    @Test
    void unexpectedFailureRemainsInternalInsteadOfDependencyUnavailable() throws Exception {
        RuntimeException transport = StudentRequestGrpcErrors.toStatus(
                new IllegalStateException("programmer failure"));

        Status status = StatusProto.fromThrowable(transport);

        assertThat(status.getCode()).isEqualTo(io.grpc.Status.Code.INTERNAL.value());
        assertThat(status.getDetails(0).unpack(StudentRequestErrorDetail.class).getCode())
                .isEqualTo(StudentRequestErrorCode.STUDENT_REQUEST_ERROR_CODE_UNSPECIFIED);
    }

    @Test
    void missingGrpcIdentityMapsToInvalidSession() throws Exception {
        RuntimeException transport = StudentRequestGrpcErrors.toStatus(
                new StudentCheckinException(StudentCheckinException.Code.INVALID_SESSION,
                        "internal identity is missing"));

        Status status = StatusProto.fromThrowable(transport);

        assertThat(status.getCode()).isEqualTo(io.grpc.Status.Code.UNAUTHENTICATED.value());
        assertThat(status.getDetails(0).unpack(StudentRequestErrorDetail.class).getCode())
                .isEqualTo(StudentRequestErrorCode.STUDENT_REQUEST_ERROR_CODE_INVALID_SESSION);
    }
}
