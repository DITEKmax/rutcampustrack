package ru.rutcampustrack.mobilebff.grpc;

import com.google.protobuf.Any;
import com.google.protobuf.ByteString;
import com.google.rpc.Status;
import io.grpc.Metadata;
import io.grpc.Status.Code;
import io.grpc.StatusRuntimeException;
import io.grpc.protobuf.StatusProto;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.MethodSource;
import ru.rutcampustrack.attendance.grpc.StudentCheckinErrorCode;
import ru.rutcampustrack.attendance.grpc.StudentCheckinErrorDetail;
import ru.rutcampustrack.attendance.grpc.AttendanceStudentGrpcServiceGrpc;
import ru.rutcampustrack.attendance.grpc.StudentRequestErrorCode;
import ru.rutcampustrack.attendance.grpc.StudentRequestErrorDetail;
import ru.rutcampustrack.mobilebff.contract.model.StudentApiModels.ProblemCode;
import ru.rutcampustrack.mobilebff.error.MobileBffException;

import java.time.Instant;
import java.util.List;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.nullable;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.junit.jupiter.params.provider.Arguments.arguments;

class MobileAttendanceClientErrorTest {

    private static final String INTERNAL_MESSAGE = "Внутренняя ошибка сервера";
    private static final String DEPENDENCY_MESSAGE = "Attendance Service временно недоступен";

    @ParameterizedTest
    @EnumSource(value = Code.class, names = {"INTERNAL", "UNKNOWN", "DATA_LOSS"})
    void rawServerFailuresBecomeGenericInternalProblems(Code statusCode) {
        MobileBffException problem = MobileAttendanceClient.translate(
                grpcError(statusCode, "database details must stay private"));

        assertThat(problem.status()).isEqualTo(org.springframework.http.HttpStatus.INTERNAL_SERVER_ERROR);
        assertThat(problem.code()).isEqualTo(ProblemCode.INTERNAL_ERROR);
        assertThat(problem.getMessage()).isEqualTo(INTERNAL_MESSAGE)
                .doesNotContain("database details");
        assertThat(problem.retryAt()).isNull();
    }

    @ParameterizedTest
    @EnumSource(value = Code.class, names = {"UNAVAILABLE", "DEADLINE_EXCEEDED"})
    void dependencyFailuresRemainServiceUnavailable(Code statusCode) {
        MobileBffException problem = MobileAttendanceClient.translate(
                grpcError(statusCode, "transport diagnostic"));

        assertThat(problem.status()).isEqualTo(org.springframework.http.HttpStatus.SERVICE_UNAVAILABLE);
        assertThat(problem.code()).isEqualTo(ProblemCode.DEPENDENCY_UNAVAILABLE);
        assertThat(problem.getMessage()).isEqualTo(DEPENDENCY_MESSAGE);
    }

    @ParameterizedTest
    @MethodSource("authAndScopeFailures")
    void authenticationAndScopeTransportFailuresKeepTheirPublic4xxMapping(
            Code statusCode, org.springframework.http.HttpStatus expectedStatus,
            ProblemCode expectedCode) {
        MobileBffException problem = MobileAttendanceClient.translate(
                grpcError(statusCode, "auth diagnostic"));

        assertThat(problem.status()).isEqualTo(expectedStatus);
        assertThat(problem.code()).isEqualTo(expectedCode);
    }

    private static Stream<Arguments> authAndScopeFailures() {
        return Stream.of(
                arguments(Code.UNAUTHENTICATED,
                        org.springframework.http.HttpStatus.UNAUTHORIZED, ProblemCode.INVALID_SESSION),
                arguments(Code.PERMISSION_DENIED,
                        org.springframework.http.HttpStatus.FORBIDDEN, ProblemCode.OUT_OF_SCOPE));
    }

    @Test
    void rawUnauthenticatedWinsOverContradictoryTypedConflict() {
        StudentRequestErrorDetail detail = StudentRequestErrorDetail.newBuilder()
                .setCode(StudentRequestErrorCode.STUDENT_REQUEST_ERROR_CODE_REQUEST_CONFLICT)
                .build();

        MobileBffException problem = MobileAttendanceClient.translate(
                grpcError(Code.UNAUTHENTICATED, "auth diagnostic", Any.pack(detail)));

        assertThat(problem.status()).isEqualTo(org.springframework.http.HttpStatus.UNAUTHORIZED);
        assertThat(problem.code()).isEqualTo(ProblemCode.INVALID_SESSION);
    }

    @Test
    void rawUnavailableWinsOverContradictoryTypedNotFound() {
        StudentRequestErrorDetail detail = StudentRequestErrorDetail.newBuilder()
                .setCode(StudentRequestErrorCode.STUDENT_REQUEST_ERROR_CODE_REQUEST_NOT_FOUND)
                .build();

        MobileBffException problem = MobileAttendanceClient.translate(
                grpcError(Code.UNAVAILABLE, "dependency diagnostic", Any.pack(detail)));

        assertThat(problem.status()).isEqualTo(org.springframework.http.HttpStatus.SERVICE_UNAVAILABLE);
        assertThat(problem.code()).isEqualTo(ProblemCode.DEPENDENCY_UNAVAILABLE);
    }

    @Test
    void rawDataLossWinsOverTypedClientCode() {
        StudentRequestErrorDetail detail = StudentRequestErrorDetail.newBuilder()
                .setCode(StudentRequestErrorCode.STUDENT_REQUEST_ERROR_CODE_REQUEST_CONFLICT)
                .build();

        MobileBffException problem = MobileAttendanceClient.translate(
                grpcError(Code.DATA_LOSS, "storage diagnostic", Any.pack(detail)));

        assertGenericInternalProblem(problem);
    }

    @Test
    void unspecifiedTypedCheckinCodeIsGenericInternalProblem() {
        StudentCheckinErrorDetail detail = StudentCheckinErrorDetail.newBuilder()
                .setCode(StudentCheckinErrorCode.STUDENT_CHECKIN_ERROR_CODE_UNSPECIFIED)
                .build();

        MobileBffException problem = MobileAttendanceClient.translate(
                grpcError(Code.INVALID_ARGUMENT, "server detail", Any.pack(detail)));

        assertGenericInternalProblem(problem);
    }

    @Test
    void unrecognizedTypedCheckinCodeIsGenericInternalProblem() {
        StudentCheckinErrorDetail detail = StudentCheckinErrorDetail.newBuilder()
                .setCodeValue(999)
                .build();

        MobileBffException problem = MobileAttendanceClient.translate(
                grpcError(Code.INVALID_ARGUMENT, "unknown server detail", Any.pack(detail)));

        assertGenericInternalProblem(problem);
    }

    @Test
    void unrecognizedTypedRequestCodeIsGenericInternalProblem() {
        StudentRequestErrorDetail detail = StudentRequestErrorDetail.newBuilder()
                .setCodeValue(999)
                .build();

        MobileBffException problem = MobileAttendanceClient.translate(
                grpcError(Code.INVALID_ARGUMENT, "unknown request detail", Any.pack(detail)));

        assertGenericInternalProblem(problem);
    }

    @Test
    void malformedTypedDetailIsGenericInternalProblem() {
        Any validType = Any.pack(StudentCheckinErrorDetail.getDefaultInstance());
        Any malformed = validType.toBuilder()
                .setValue(ByteString.copyFrom(new byte[]{0x7f}))
                .build();

        MobileBffException problem = MobileAttendanceClient.translate(
                grpcError(Code.INVALID_ARGUMENT, "malformed server detail", malformed));

        assertGenericInternalProblem(problem);
    }

    @ParameterizedTest
    @EnumSource(value = Code.class, names = {
            "UNAUTHENTICATED", "PERMISSION_DENIED", "UNAVAILABLE", "DEADLINE_EXCEEDED"
    })
    void malformedStatusMetadataCannotOverrideRawAuthOrTransportStatus(Code statusCode) {
        Metadata trailers = new Metadata();
        Metadata.Key<byte[]> rawStatus = Metadata.Key.of(
                "grpc-status-details-bin", Metadata.BINARY_BYTE_MARSHALLER);
        trailers.put(rawStatus, new byte[]{0x7f});

        MobileBffException problem = MobileAttendanceClient.translate(
                io.grpc.Status.fromCodeValue(statusCode.value()).withDescription("malformed status details")
                        .asRuntimeException(trailers));

        if (statusCode == Code.UNAUTHENTICATED) {
            assertThat(problem.status()).isEqualTo(org.springframework.http.HttpStatus.UNAUTHORIZED);
            assertThat(problem.code()).isEqualTo(ProblemCode.INVALID_SESSION);
        } else if (statusCode == Code.PERMISSION_DENIED) {
            assertThat(problem.status()).isEqualTo(org.springframework.http.HttpStatus.FORBIDDEN);
            assertThat(problem.code()).isEqualTo(ProblemCode.OUT_OF_SCOPE);
        } else {
            assertThat(problem.status()).isEqualTo(org.springframework.http.HttpStatus.SERVICE_UNAVAILABLE);
            assertThat(problem.code()).isEqualTo(ProblemCode.DEPENDENCY_UNAVAILABLE);
        }
    }

    @Test
    void invalidTypedRetryTimestampIsGenericInternalProblem() {
        StudentCheckinErrorDetail detail = StudentCheckinErrorDetail.newBuilder()
                .setCode(StudentCheckinErrorCode.STUDENT_CHECKIN_ERROR_CODE_CHECKIN_COOLDOWN)
                .setRetryAt("not-an-instant")
                .build();

        MobileBffException problem = MobileAttendanceClient.translate(
                grpcError(Code.RESOURCE_EXHAUSTED, "retryAt diagnostic", Any.pack(detail)));

        assertGenericInternalProblem(problem);
    }

    @Test
    void validCheckinCooldownKeepsTypedCodeAndRetryAt() {
        Instant retryAt = Instant.parse("2026-09-08T12:34:56Z");
        StudentCheckinErrorDetail detail = StudentCheckinErrorDetail.newBuilder()
                .setCode(StudentCheckinErrorCode.STUDENT_CHECKIN_ERROR_CODE_CHECKIN_COOLDOWN)
                .setRetryAt(retryAt.toString())
                .build();

        MobileBffException problem = MobileAttendanceClient.translate(
                grpcError(Code.RESOURCE_EXHAUSTED, "Повторите позже", Any.pack(detail)));

        assertThat(problem.status()).isEqualTo(org.springframework.http.HttpStatus.TOO_MANY_REQUESTS);
        assertThat(problem.code()).isEqualTo(ProblemCode.CHECKIN_COOLDOWN);
        assertThat(problem.getMessage()).isEqualTo("Повторите позже");
        assertThat(problem.retryAt()).isEqualTo(retryAt);
    }

    @Test
    void validRequestErrorKeepsPublicRequestCode() {
        StudentRequestErrorDetail detail = StudentRequestErrorDetail.newBuilder()
                .setCode(StudentRequestErrorCode.STUDENT_REQUEST_ERROR_CODE_REQUEST_NOT_FOUND)
                .build();

        MobileBffException problem = MobileAttendanceClient.translate(
                grpcError(Code.NOT_FOUND, "internal request identifier", Any.pack(detail)));

        assertThat(problem.status()).isEqualTo(org.springframework.http.HttpStatus.NOT_FOUND);
        assertThat(problem.code()).isEqualTo(ProblemCode.REQUEST_NOT_FOUND);
        assertThat(problem.getMessage()).isEqualTo("Заявка не найдена");
        assertThat(problem.getMessage()).doesNotContain("internal request identifier");
    }

    @Test
    void validTypedRequestConflictKeepsConflictMapping() {
        StudentRequestErrorDetail detail = StudentRequestErrorDetail.newBuilder()
                .setCode(StudentRequestErrorCode.STUDENT_REQUEST_ERROR_CODE_REQUEST_CONFLICT)
                .build();

        MobileBffException problem = MobileAttendanceClient.translate(
                grpcError(Code.ABORTED, "internal conflict detail", Any.pack(detail)));

        assertThat(problem.status()).isEqualTo(org.springframework.http.HttpStatus.CONFLICT);
        assertThat(problem.code()).isEqualTo(ProblemCode.REQUEST_CONFLICT);
        assertThat(problem.getMessage()).isEqualTo("Операция конфликтует с текущим состоянием заявки");
        assertThat(problem.getMessage()).doesNotContain("internal conflict detail");
    }

    @Test
    void localClientFailureBecomesGenericInternalProblem() {
        MobileGrpcAuth auth = mock(MobileGrpcAuth.class);
        when(auth.attach(nullable(AttendanceStudentGrpcServiceGrpc.AttendanceStudentGrpcServiceBlockingStub.class)))
                .thenThrow(new IllegalStateException("local diagnostic must stay private"));

        MobileAttendanceClient client = new MobileAttendanceClient(auth);

        assertThatThrownBy(() -> client.snapshot(List.of(1L)))
                .isInstanceOfSatisfying(MobileBffException.class, problem -> {
                    assertGenericInternalProblem(problem);
                    assertThat(problem.getMessage()).doesNotContain("local diagnostic");
                });
    }

    private static void assertGenericInternalProblem(MobileBffException problem) {
        assertThat(problem.status()).isEqualTo(org.springframework.http.HttpStatus.INTERNAL_SERVER_ERROR);
        assertThat(problem.code()).isEqualTo(ProblemCode.INTERNAL_ERROR);
        assertThat(problem.getMessage()).isEqualTo(INTERNAL_MESSAGE);
        assertThat(problem.retryAt()).isNull();
    }

    private static StatusRuntimeException grpcError(Code code, String message, Any... details) {
        Status.Builder status = Status.newBuilder().setCode(code.value()).setMessage(message);
        for (Any detail : details) {
            status.addDetails(detail);
        }
        return StatusProto.toStatusRuntimeException(status.build());
    }
}
