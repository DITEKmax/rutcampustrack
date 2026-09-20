package ru.rutcampustrack.attendance.grpc;

import com.google.protobuf.Any;
import io.grpc.Status;
import io.grpc.protobuf.StatusProto;
import org.springframework.web.server.ResponseStatusException;
import ru.rutcampustrack.attendance.contract.exception.ResourceNotFoundException;
import ru.rutcampustrack.attendance.exception.AccessDeniedException;
import ru.rutcampustrack.attendance.exception.AcademicServiceUnavailableException;
import ru.rutcampustrack.attendance.exception.BadRequestException;
import ru.rutcampustrack.attendance.exception.ConflictException;
import ru.rutcampustrack.attendance.exception.InvalidIdempotencyKeyException;
import ru.rutcampustrack.attendance.exception.PayloadTooLargeException;
import ru.rutcampustrack.attendance.exception.ScheduleServiceUnavailableException;

/** Typed gRPC status mapping for the student Requests transport. */
final class StudentRequestGrpcErrors {
    private StudentRequestGrpcErrors() {
    }

    static RuntimeException toStatus(RuntimeException error) {
        StudentRequestErrorCode code = code(error);
        Status.Code grpcCode = grpcCode(code);
        StudentRequestErrorDetail detail = StudentRequestErrorDetail.newBuilder().setCode(code).build();
        com.google.rpc.Status status = com.google.rpc.Status.newBuilder()
                .setCode(grpcCode.value())
                .setMessage(safeMessage(error))
                .addDetails(Any.pack(detail))
                .build();
        return StatusProto.toStatusRuntimeException(status);
    }

    static StudentRequestErrorCode code(RuntimeException error) {
        if (error instanceof StudentRequestTransportException transport) {
            return transport.code();
        }
        if (error instanceof InvalidIdempotencyKeyException) {
            return StudentRequestErrorCode.STUDENT_REQUEST_ERROR_CODE_INVALID_IDEMPOTENCY_KEY;
        }
        if (error instanceof PayloadTooLargeException) {
            return StudentRequestErrorCode.STUDENT_REQUEST_ERROR_CODE_PAYLOAD_TOO_LARGE;
        }
        if (error instanceof ResourceNotFoundException notFound) {
            if ("Attachment".equals(notFound.getResourceName())) {
                return StudentRequestErrorCode.STUDENT_REQUEST_ERROR_CODE_ATTACHMENT_NOT_FOUND;
            }
            if ("StudentRequest".equals(notFound.getResourceName())
                    || "ExcuseTicket".equals(notFound.getResourceName())
                    || "LateCheckinRequest".equals(notFound.getResourceName())) {
                return StudentRequestErrorCode.STUDENT_REQUEST_ERROR_CODE_REQUEST_NOT_FOUND;
            }
            return StudentRequestErrorCode.STUDENT_REQUEST_ERROR_CODE_DEPENDENCY_UNAVAILABLE;
        }
        if (error instanceof ResponseStatusException status && status.getStatusCode().value() == 410) {
            return StudentRequestErrorCode.STUDENT_REQUEST_ERROR_CODE_ATTACHMENT_EXPIRED;
        }
        if (error instanceof AccessDeniedException) {
            return StudentRequestErrorCode.STUDENT_REQUEST_ERROR_CODE_OUT_OF_SCOPE;
        }
        if (error instanceof ConflictException) {
            return StudentRequestErrorCode.STUDENT_REQUEST_ERROR_CODE_REQUEST_CONFLICT;
        }
        if (error instanceof BadRequestException) {
            return StudentRequestErrorCode.STUDENT_REQUEST_ERROR_CODE_INVALID_REQUEST;
        }
        if (error instanceof ScheduleServiceUnavailableException
                || error instanceof AcademicServiceUnavailableException) {
            return StudentRequestErrorCode.STUDENT_REQUEST_ERROR_CODE_DEPENDENCY_UNAVAILABLE;
        }
        return StudentRequestErrorCode.STUDENT_REQUEST_ERROR_CODE_DEPENDENCY_UNAVAILABLE;
    }

    private static Status.Code grpcCode(StudentRequestErrorCode code) {
        return switch (code) {
            case STUDENT_REQUEST_ERROR_CODE_INVALID_REQUEST,
                 STUDENT_REQUEST_ERROR_CODE_INVALID_IDEMPOTENCY_KEY -> Status.Code.INVALID_ARGUMENT;
            case STUDENT_REQUEST_ERROR_CODE_INVALID_SESSION -> Status.Code.UNAUTHENTICATED;
            case STUDENT_REQUEST_ERROR_CODE_WRONG_ROLE,
                 STUDENT_REQUEST_ERROR_CODE_OUT_OF_SCOPE -> Status.Code.PERMISSION_DENIED;
            case STUDENT_REQUEST_ERROR_CODE_REQUEST_NOT_FOUND,
                 STUDENT_REQUEST_ERROR_CODE_ATTACHMENT_NOT_FOUND -> Status.Code.NOT_FOUND;
            case STUDENT_REQUEST_ERROR_CODE_REQUEST_CONFLICT -> Status.Code.ABORTED;
            case STUDENT_REQUEST_ERROR_CODE_ATTACHMENT_EXPIRED -> Status.Code.FAILED_PRECONDITION;
            case STUDENT_REQUEST_ERROR_CODE_PAYLOAD_TOO_LARGE -> Status.Code.RESOURCE_EXHAUSTED;
            case STUDENT_REQUEST_ERROR_CODE_DEPENDENCY_UNAVAILABLE,
                 STUDENT_REQUEST_ERROR_CODE_UNSPECIFIED -> Status.Code.UNAVAILABLE;
            case UNRECOGNIZED -> Status.Code.INTERNAL;
        };
    }

    private static String safeMessage(RuntimeException error) {
        String message = error.getMessage();
        return message == null || message.isBlank() ? "Student request operation failed" : message;
    }
}
