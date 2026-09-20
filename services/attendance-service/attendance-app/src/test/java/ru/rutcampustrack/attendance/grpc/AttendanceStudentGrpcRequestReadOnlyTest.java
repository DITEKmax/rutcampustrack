package ru.rutcampustrack.attendance.grpc;

import com.google.rpc.Status;
import io.grpc.Context;
import io.grpc.protobuf.StatusProto;
import io.grpc.stub.StreamObserver;
import org.junit.jupiter.api.Test;
import ru.rutcampustrack.attendance.semester.SemesterCacheService;
import ru.rutcampustrack.attendance.student.StudentAttendanceSnapshotService;
import ru.rutcampustrack.attendance.student.StudentCheckinService;
import ru.rutcampustrack.shared.security.InternalJwtClaims;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class AttendanceStudentGrpcRequestReadOnlyTest {

    private static final InternalJwtClaims READ_ONLY = new InternalJwtClaims(
            42L,
            UUID.fromString("22222222-2222-4222-8222-222222222222"),
            3L,
            4L,
            "STUDENT",
            "EXPELLED",
            7L,
            false,
            true);

    @Test
    void readOnlySubmitExcuseIsRejectedBeforeRequestService() throws Exception {
        RecordingObserver observer = new RecordingObserver();
        AttendanceStudentGrpcServiceImpl service = service();

        Context.current().withValue(StudentGrpcIdentity.CLAIMS, READ_ONLY).run(() ->
                service.submitStudentExcuse(SubmitStudentExcuseCommand.newBuilder()
                        .setIdempotencyKey("read-only-excuse")
                        .build(), observer));

        assertReadOnly(observer);
    }

    @Test
    void readOnlySubmitLateCheckinIsRejectedBeforeRequestService() throws Exception {
        RecordingObserver observer = new RecordingObserver();
        AttendanceStudentGrpcServiceImpl service = service();

        Context.current().withValue(StudentGrpcIdentity.CLAIMS, READ_ONLY).run(() ->
                service.submitStudentLateCheckin(SubmitStudentLateCheckinCommand.newBuilder()
                        .setLessonId(77L)
                        .setIdempotencyKey("read-only-late")
                        .build(), observer));

        assertReadOnly(observer);
    }

    @Test
    void readOnlyCancelIsRejectedBeforeRequestService() throws Exception {
        RecordingObserver observer = new RecordingObserver();
        AttendanceStudentGrpcServiceImpl service = service();

        Context.current().withValue(StudentGrpcIdentity.CLAIMS, READ_ONLY).run(() ->
                service.cancelStudentRequest(StudentRequestId.newBuilder()
                        .setRequestId("0123456789abcdef01234567")
                        .build(), observer));

        assertReadOnly(observer);
    }

    private static AttendanceStudentGrpcServiceImpl service() {
        return new AttendanceStudentGrpcServiceImpl(
                mock(StudentCheckinService.class),
                mock(StudentAttendanceSnapshotService.class),
                mock(ScheduleGrpcClient.class),
                mock(AcademicGrpcClient.class),
                mock(SemesterCacheService.class));
    }

    private static void assertReadOnly(RecordingObserver observer) throws Exception {
        assertThat(observer.value).isNull();
        assertThat(observer.error).isNotNull();
        Status status = StatusProto.fromThrowable(observer.error);
        assertThat(status.getCode()).isEqualTo(io.grpc.Status.Code.PERMISSION_DENIED.value());
        assertThat(status.getDetails(0).unpack(StudentRequestErrorDetail.class).getCode())
                .isEqualTo(StudentRequestErrorCode.STUDENT_REQUEST_ERROR_CODE_OUT_OF_SCOPE);
    }

    private static final class RecordingObserver implements StreamObserver<StudentRequestDetail> {
        private StudentRequestDetail value;
        private Throwable error;

        @Override
        public void onNext(StudentRequestDetail value) {
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
