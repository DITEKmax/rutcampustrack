package ru.rutcampustrack.attendance.grpc;

import io.grpc.stub.StreamObserver;
import net.devh.boot.grpc.server.service.GrpcService;
import ru.rutcampustrack.attendance.contract.enums.StudentRequestKind;
import ru.rutcampustrack.attendance.studentrequest.StudentRequestService;
import ru.rutcampustrack.attendance.studentrequest.StudentRequestModels.NotificationResolution;

/** Exact bot-only attachment read adapter; authorization remains in the domain. */
@GrpcService
public class AttendanceRequestBotGrpcServiceImpl
        extends AttendanceRequestBotGrpcServiceGrpc.AttendanceRequestBotGrpcServiceImplBase {
    private final StudentRequestService requestService;

    public AttendanceRequestBotGrpcServiceImpl(StudentRequestService requestService) {
        this.requestService = requestService;
    }

    @Override
    public void fetchExcuseAttachment(FetchExcuseAttachmentRequest request,
                                      StreamObserver<FetchExcuseAttachmentResponse> observer) {
        try {
            if (request.getActorUserId() <= 0 || request.getRequestId().isBlank()
                    || request.getAttachmentId().isBlank()) {
                throw new StudentRequestTransportException(
                        StudentRequestErrorCode.STUDENT_REQUEST_ERROR_CODE_INVALID_REQUEST,
                        "Attachment identifiers are required");
            }
            var result = requestService.fetchExcuseAttachmentForBot(
                    request.getActorUserId(), request.getRequestId(), request.getAttachmentId());
            observer.onNext(FetchExcuseAttachmentResponse.newBuilder()
                    .setData(com.google.protobuf.ByteString.copyFrom(result.bytes()))
                    .setContentType(result.contentType() == null ? "" : result.contentType())
                    .setFilename(result.filename() == null ? "attachment" : result.filename())
                    .setSize(result.bytes().length)
                    .build());
            observer.onCompleted();
        } catch (RuntimeException error) {
            observer.onError(StudentRequestGrpcErrors.toStatus(error));
        }
    }

    @Override
    public void resolveRequestNotification(ResolveRequestNotificationRequest request,
                                            StreamObserver<ResolveRequestNotificationResponse> observer) {
        try {
            if (request.getKind() == ru.rutcampustrack.attendance.grpc.StudentRequestKind.STUDENT_REQUEST_KIND_UNSPECIFIED
                    || request.getKind() == ru.rutcampustrack.attendance.grpc.StudentRequestKind.UNRECOGNIZED
                    || request.getRequestId().isBlank()) {
                throw new StudentRequestTransportException(
                        StudentRequestErrorCode.STUDENT_REQUEST_ERROR_CODE_INVALID_REQUEST,
                        "Notification request identifiers are required");
            }
            StudentRequestKind kind = switch (request.getKind()) {
                case STUDENT_REQUEST_KIND_EXCUSE -> StudentRequestKind.EXCUSE;
                case STUDENT_REQUEST_KIND_LATE_CHECKIN -> StudentRequestKind.LATE_CHECKIN;
                case STUDENT_REQUEST_KIND_UNSPECIFIED, UNRECOGNIZED -> throw new StudentRequestTransportException(
                        StudentRequestErrorCode.STUDENT_REQUEST_ERROR_CODE_INVALID_REQUEST,
                        "Notification request kind is unsupported");
            };
            NotificationResolution result = requestService.resolveRequestNotification(
                    kind, request.getRequestId());
            if (result == null || result.groupId() <= 0 || result.studentId() <= 0
                    || result.studentName() == null || result.studentName().isBlank()
                    || result.detail() == null || result.detail().summary() == null) {
                throw new StudentRequestTransportException(
                        StudentRequestErrorCode.STUDENT_REQUEST_ERROR_CODE_INVALID_REQUEST,
                        "Canonical notification context is incomplete");
            }
            observer.onNext(ResolveRequestNotificationResponse.newBuilder()
                    .setGroupId(result.groupId())
                    .setStudentId(result.studentId())
                    .setStudentName(result.studentName())
                    .setDetail(StudentRequestGrpcMapper.detail(result.detail()))
                    .build());
            observer.onCompleted();
        } catch (RuntimeException error) {
            observer.onError(StudentRequestGrpcErrors.toStatus(error));
        }
    }
}
