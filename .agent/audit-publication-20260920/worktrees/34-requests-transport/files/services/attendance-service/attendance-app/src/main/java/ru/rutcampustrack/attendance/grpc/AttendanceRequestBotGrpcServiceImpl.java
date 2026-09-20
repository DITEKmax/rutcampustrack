package ru.rutcampustrack.attendance.grpc;

import io.grpc.stub.StreamObserver;
import net.devh.boot.grpc.server.service.GrpcService;
import ru.rutcampustrack.attendance.studentrequest.StudentRequestService;

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
}
