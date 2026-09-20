package ru.rutcampustrack.academic.studentprojection;

import com.google.protobuf.Any;
import io.grpc.Status;
import io.grpc.protobuf.StatusProto;
import org.springframework.dao.DataAccessException;
import org.springframework.transaction.TransactionException;
import ru.rutcampustrack.academic.grpc.AcademicProjectionErrorCode;
import ru.rutcampustrack.academic.grpc.AcademicProjectionErrorDetail;

/** Maps resolver failures to stable gRPC status codes and typed details. */
public final class StudentProjectionGrpcErrors {

    private StudentProjectionGrpcErrors() {
    }

    public static RuntimeException toStatus(Throwable error) {
        StudentProjectionException.Code code = codeFor(error);
        AcademicProjectionErrorCode detailCode = toProtoCode(code);
        Status.Code grpcCode = toGrpcCode(code);
        com.google.rpc.Status status = com.google.rpc.Status.newBuilder()
                .setCode(grpcCode.value())
                .setMessage(messageFor(code))
                .addDetails(Any.pack(AcademicProjectionErrorDetail.newBuilder()
                        .setCode(detailCode)
                        .build()))
                .build();
        return StatusProto.toStatusRuntimeException(status);
    }

    private static StudentProjectionException.Code codeFor(Throwable error) {
        if (error instanceof StudentProjectionException projection) {
            return projection.code();
        }
        for (Throwable current = error; current != null; current = current.getCause()) {
            if (current instanceof DataAccessException || current instanceof TransactionException) {
                return StudentProjectionException.Code.DEPENDENCY_UNAVAILABLE;
            }
        }
        return null;
    }

    private static AcademicProjectionErrorCode toProtoCode(StudentProjectionException.Code code) {
        if (code == null) {
            return AcademicProjectionErrorCode.ACADEMIC_PROJECTION_ERROR_CODE_UNSPECIFIED;
        }
        return switch (code) {
            case INVALID_REQUEST -> AcademicProjectionErrorCode.ACADEMIC_PROJECTION_ERROR_CODE_INVALID_REQUEST;
            case INVALID_SESSION -> AcademicProjectionErrorCode.ACADEMIC_PROJECTION_ERROR_CODE_INVALID_SESSION;
            case WRONG_ROLE -> AcademicProjectionErrorCode.ACADEMIC_PROJECTION_ERROR_CODE_WRONG_ROLE;
            case OUT_OF_SCOPE -> AcademicProjectionErrorCode.ACADEMIC_PROJECTION_ERROR_CODE_OUT_OF_SCOPE;
            case STUDENT_SCOPE_UNRESOLVED ->
                    AcademicProjectionErrorCode.ACADEMIC_PROJECTION_ERROR_CODE_STUDENT_SCOPE_UNRESOLVED;
            case DEPENDENCY_UNAVAILABLE ->
                    AcademicProjectionErrorCode.ACADEMIC_PROJECTION_ERROR_CODE_DEPENDENCY_UNAVAILABLE;
            case INCONSISTENT_SOURCE ->
                    AcademicProjectionErrorCode.ACADEMIC_PROJECTION_ERROR_CODE_INCONSISTENT_SOURCE;
        };
    }

    private static Status.Code toGrpcCode(StudentProjectionException.Code code) {
        if (code == null) {
            return Status.Code.INTERNAL;
        }
        return switch (code) {
            case INVALID_REQUEST -> Status.Code.INVALID_ARGUMENT;
            case INVALID_SESSION -> Status.Code.UNAUTHENTICATED;
            case WRONG_ROLE, OUT_OF_SCOPE -> Status.Code.PERMISSION_DENIED;
            case STUDENT_SCOPE_UNRESOLVED -> Status.Code.PERMISSION_DENIED;
            case DEPENDENCY_UNAVAILABLE, INCONSISTENT_SOURCE -> Status.Code.UNAVAILABLE;
        };
    }

    private static String messageFor(StudentProjectionException.Code code) {
        return switch (code) {
            case INVALID_REQUEST -> "Academic projection request is invalid";
            case INVALID_SESSION -> "Academic projection session is invalid";
            case WRONG_ROLE -> "Academic projection requires a student identity";
            case OUT_OF_SCOPE -> "Academic projection is outside the signed scope";
            case STUDENT_SCOPE_UNRESOLVED -> "Academic student scope is unresolved";
            case DEPENDENCY_UNAVAILABLE -> "Academic projection dependency is unavailable";
            case INCONSISTENT_SOURCE -> "Academic projection source is inconsistent";
            case null -> "Academic projection failed";
        };
    }
}
