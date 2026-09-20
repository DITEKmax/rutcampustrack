package ru.rutcampustrack.attendance.grpc;

import com.google.rpc.Status;
import io.grpc.Metadata;
import io.grpc.MethodDescriptor;
import io.grpc.ServerCall;
import io.grpc.ServerCallHandler;
import io.grpc.protobuf.StatusProto;
import io.grpc.stub.StreamObserver;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import ru.rutcampustrack.attendance.contract.enums.ExcuseType;
import ru.rutcampustrack.attendance.contract.enums.StudentRequestOrigin;
import ru.rutcampustrack.attendance.contract.enums.StudentRequestStatus;
import ru.rutcampustrack.attendance.studentrequest.StudentRequestModels;
import ru.rutcampustrack.attendance.studentrequest.StudentRequestService;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class AttendanceRequestBotGrpcServiceTest {

    private static final String REQUEST_ID = "0123456789abcdef01234567";
    private AttendanceRequestBotGrpcSecretInterceptor secretInterceptor;

    @BeforeEach
    void setUpSecretInterceptor() {
        secretInterceptor = new AttendanceRequestBotGrpcSecretInterceptor();
        ReflectionTestUtils.setField(secretInterceptor, "expectedSecret", "configured-secret");
    }

    @Test
    void resolveReturnsCanonicalProjectionWithoutEventFields() {
        StudentRequestService requestService = mock(StudentRequestService.class);
        StudentRequestModels.RequestSummary summary = new StudentRequestModels.RequestSummary(
                REQUEST_ID,
                ru.rutcampustrack.attendance.contract.enums.StudentRequestKind.EXCUSE,
                StudentRequestStatus.PENDING,
                StudentRequestOrigin.MANUAL,
                List.of(new StudentRequestModels.LessonSnapshot(
                        101L, 5L, 6L, "Математический анализ", "LECTURE", 7L, 3,
                        LocalDate.of(2026, 9, 8), LocalTime.of(9, 0), LocalTime.of(10, 30),
                        "active", false)),
                Instant.parse("2026-09-08T07:00:00Z"),
                Instant.parse("2026-09-08T07:00:00Z"));
        StudentRequestModels.RequestDetail detail = new StudentRequestModels.RequestDetail(
                summary, ExcuseType.ILLNESS, "Канонический комментарий", List.of(), null);
        StudentRequestModels.NotificationResolution resolution =
                new StudentRequestModels.NotificationResolution(5L, 10L, "Канонический студент", detail);
        when(requestService.resolveRequestNotification(
                ru.rutcampustrack.attendance.contract.enums.StudentRequestKind.EXCUSE, REQUEST_ID))
                .thenReturn(resolution);

        AttendanceRequestBotGrpcServiceImpl service = new AttendanceRequestBotGrpcServiceImpl(requestService);
        RecordingObserver<ResolveRequestNotificationResponse> observer = new RecordingObserver<>();
        service.resolveRequestNotification(
                ResolveRequestNotificationRequest.newBuilder()
                        .setKind(StudentRequestKind.STUDENT_REQUEST_KIND_EXCUSE)
                        .setRequestId(REQUEST_ID)
                        .build(),
                observer);

        assertThat(observer.error).isNull();
        assertThat(observer.value.getGroupId()).isEqualTo(5L);
        assertThat(observer.value.getStudentId()).isEqualTo(10L);
        assertThat(observer.value.getStudentName()).isEqualTo("Канонический студент");
        assertThat(observer.value.getDetail().getSummary().getId()).isEqualTo(REQUEST_ID);
        assertThat(observer.value.getDetail().getReason())
                .isEqualTo(StudentExcuseReason.STUDENT_EXCUSE_REASON_ILLNESS);
        assertThat(observer.value.getDetail().getSummary().getLessons(0).getSubjectName())
                .isEqualTo("Математический анализ");
        verify(requestService).resolveRequestNotification(
                ru.rutcampustrack.attendance.contract.enums.StudentRequestKind.EXCUSE, REQUEST_ID);
    }

    @Test
    void invalidResolveLookupFailsBeforeDomainCallWithTypedStatus() throws Exception {
        StudentRequestService requestService = mock(StudentRequestService.class);
        AttendanceRequestBotGrpcServiceImpl service = new AttendanceRequestBotGrpcServiceImpl(requestService);
        RecordingObserver<ResolveRequestNotificationResponse> observer = new RecordingObserver<>();

        service.resolveRequestNotification(
                ResolveRequestNotificationRequest.newBuilder().setRequestId(REQUEST_ID).build(), observer);

        Status status = StatusProto.fromThrowable(observer.error);
        assertThat(status.getCode()).isEqualTo(io.grpc.Status.Code.INVALID_ARGUMENT.value());
        assertThat(status.getDetails(0).unpack(StudentRequestErrorDetail.class).getCode())
                .isEqualTo(StudentRequestErrorCode.STUDENT_REQUEST_ERROR_CODE_INVALID_REQUEST);
        assertThat(observer.value).isNull();
        verifyNoInteractions(requestService);
    }

    @Test
    void invalidAttachmentLookupFailsBeforeDomainCallWithTypedStatus() throws Exception {
        StudentRequestService requestService = mock(StudentRequestService.class);
        AttendanceRequestBotGrpcServiceImpl service = new AttendanceRequestBotGrpcServiceImpl(requestService);
        RecordingObserver<FetchExcuseAttachmentResponse> observer = new RecordingObserver<>();

        service.fetchExcuseAttachment(
                FetchExcuseAttachmentRequest.newBuilder()
                        .setActorUserId(0L)
                        .setRequestId(REQUEST_ID)
                        .setAttachmentId(REQUEST_ID)
                        .build(),
                observer);

        Status status = StatusProto.fromThrowable(observer.error);
        assertThat(status.getCode()).isEqualTo(io.grpc.Status.Code.INVALID_ARGUMENT.value());
        assertThat(status.getDetails(0).unpack(StudentRequestErrorDetail.class).getCode())
                .isEqualTo(StudentRequestErrorCode.STUDENT_REQUEST_ERROR_CODE_INVALID_REQUEST);
        assertThat(observer.value).isNull();
        verifyNoInteractions(requestService);
    }

    @Test
    void missingSecretIsRejectedForGeneratedResolveDescriptorBeforeHandler() {
        ServerCall<ResolveRequestNotificationRequest, ResolveRequestNotificationResponse> call = callFor(
                AttendanceRequestBotGrpcServiceGrpc.getResolveRequestNotificationMethod());
        ServerCallHandler<ResolveRequestNotificationRequest, ResolveRequestNotificationResponse> next = mock(
                ServerCallHandler.class);

        secretInterceptor.interceptCall(call, new Metadata(), next);

        verify(call).close(org.mockito.ArgumentMatchers.argThat(
                status -> status.getCode() == io.grpc.Status.Code.UNAUTHENTICATED),
                org.mockito.ArgumentMatchers.any());
        verifyNoInteractions(next);
    }

    @Test
    void wrongSecretIsRejectedForGeneratedFetchDescriptorBeforeHandler() {
        ServerCall<FetchExcuseAttachmentRequest, FetchExcuseAttachmentResponse> call = callFor(
                AttendanceRequestBotGrpcServiceGrpc.getFetchExcuseAttachmentMethod());
        ServerCallHandler<FetchExcuseAttachmentRequest, FetchExcuseAttachmentResponse> next = mock(
                ServerCallHandler.class);
        Metadata headers = new Metadata();
        headers.put(AttendanceRequestBotGrpcSecretInterceptor.SECRET_KEY, "wrong-secret");

        secretInterceptor.interceptCall(call, headers, next);

        verify(call).close(org.mockito.ArgumentMatchers.argThat(
                status -> status.getCode() == io.grpc.Status.Code.UNAUTHENTICATED),
                org.mockito.ArgumentMatchers.any());
        verifyNoInteractions(next);
    }

    @Test
    void blankConfiguredSecretIsRejectedEvenWhenHeaderIsPresent() {
        ReflectionTestUtils.setField(secretInterceptor, "expectedSecret", " ");
        ServerCall<ResolveRequestNotificationRequest, ResolveRequestNotificationResponse> call = callFor(
                AttendanceRequestBotGrpcServiceGrpc.getResolveRequestNotificationMethod());
        ServerCallHandler<ResolveRequestNotificationRequest, ResolveRequestNotificationResponse> next = mock(
                ServerCallHandler.class);
        Metadata headers = new Metadata();
        headers.put(AttendanceRequestBotGrpcSecretInterceptor.SECRET_KEY, "configured-secret");

        secretInterceptor.interceptCall(call, headers, next);

        verify(call).close(org.mockito.ArgumentMatchers.argThat(
                status -> status.getCode() == io.grpc.Status.Code.UNAUTHENTICATED),
                org.mockito.ArgumentMatchers.any());
        verifyNoInteractions(next);
    }

    @Test
    void correctSecretAllowsGeneratedFetchDescriptor() {
        ServerCall<FetchExcuseAttachmentRequest, FetchExcuseAttachmentResponse> call = callFor(
                AttendanceRequestBotGrpcServiceGrpc.getFetchExcuseAttachmentMethod());
        ServerCallHandler<FetchExcuseAttachmentRequest, FetchExcuseAttachmentResponse> next = mock(
                ServerCallHandler.class);
        ServerCall.Listener<FetchExcuseAttachmentRequest> listener = new ServerCall.Listener<>() { };
        when(next.startCall(org.mockito.ArgumentMatchers.eq(call), org.mockito.ArgumentMatchers.any()))
                .thenReturn(listener);
        Metadata headers = new Metadata();
        headers.put(AttendanceRequestBotGrpcSecretInterceptor.SECRET_KEY, "configured-secret");

        assertThat(secretInterceptor.interceptCall(call, headers, next)).isSameAs(listener);
        verify(call, org.mockito.Mockito.never()).close(org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any());
    }

    @Test
    void publicStudentDescriptorDoesNotRequireBotSecret() {
        ServerCall<StudentAttendanceSnapshotRequest, StudentAttendanceSnapshotResponse> call = callFor(
                AttendanceStudentGrpcServiceGrpc.getGetStudentAttendanceSnapshotMethod());
        ServerCallHandler<StudentAttendanceSnapshotRequest, StudentAttendanceSnapshotResponse> next = mock(
                ServerCallHandler.class);
        ServerCall.Listener<StudentAttendanceSnapshotRequest> listener = new ServerCall.Listener<>() { };
        when(next.startCall(org.mockito.ArgumentMatchers.eq(call), org.mockito.ArgumentMatchers.any()))
                .thenReturn(listener);

        assertThat(secretInterceptor.interceptCall(call, new Metadata(), next)).isSameAs(listener);
        verify(call, org.mockito.Mockito.never()).close(org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any());
    }

    private static <ReqT, RespT> ServerCall<ReqT, RespT> callFor(
            MethodDescriptor<ReqT, RespT> descriptor) {
        ServerCall<ReqT, RespT> call = mock(ServerCall.class);
        when(call.getMethodDescriptor()).thenReturn(descriptor);
        return call;
    }

    private static final class RecordingObserver<T> implements StreamObserver<T> {
        private T value;
        private Throwable error;

        @Override
        public void onNext(T value) {
            this.value = value;
        }

        @Override
        public void onError(Throwable error) {
            this.error = error;
        }

        @Override
        public void onCompleted() {
        }
    }
}
