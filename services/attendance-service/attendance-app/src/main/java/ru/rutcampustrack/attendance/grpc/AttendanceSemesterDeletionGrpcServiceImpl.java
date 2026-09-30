package ru.rutcampustrack.attendance.grpc;

import io.grpc.Status;
import io.grpc.stub.StreamObserver;
import net.devh.boot.grpc.server.service.GrpcService;
import ru.rutcampustrack.attendance.event.AttendanceSemesterDeletionData;

/** Secret-protected, read-only preview of Attendance data owned by one semester. */
@GrpcService
public class AttendanceSemesterDeletionGrpcServiceImpl
        extends AttendanceSemesterDeletionGrpcServiceGrpc.AttendanceSemesterDeletionGrpcServiceImplBase {

    private final AttendanceSemesterDeletionData deletionData;

    public AttendanceSemesterDeletionGrpcServiceImpl(AttendanceSemesterDeletionData deletionData) {
        this.deletionData = deletionData;
    }

    @Override
    public void previewSemesterDeletion(SemesterDeletionPreviewRequest request,
                                        StreamObserver<SemesterDeletionParticipantPreview> observer) {
        try {
            long semesterId = request.getSemesterId();
            if (semesterId <= 0) {
                throw Status.INVALID_ARGUMENT.withDescription("semester_id must be positive")
                        .asRuntimeException();
            }
            AttendanceSemesterDeletionData.Preview preview = deletionData.previewWithFenceVersion(semesterId);
            AttendanceSemesterDeletionData.Snapshot snapshot = preview.snapshot();
            observer.onNext(SemesterDeletionParticipantPreview.newBuilder()
                    .setSemesterId(semesterId)
                    .setParticipantDigest(snapshot.participantDigest())
                    .setAttendanceMarksCount(snapshot.attendanceMarks())
                    .setStudentRequestsCount(snapshot.studentRequests())
                    .setObservedFenceVersion(preview.observedFenceVersion())
                    .build());
            observer.onCompleted();
        } catch (io.grpc.StatusRuntimeException error) {
            observer.onError(error);
        } catch (IllegalArgumentException error) {
            observer.onError(Status.INVALID_ARGUMENT.withDescription(error.getMessage())
                    .withCause(error).asRuntimeException());
        } catch (IllegalStateException error) {
            observer.onError(Status.FAILED_PRECONDITION.withDescription(error.getMessage())
                    .withCause(error).asRuntimeException());
        } catch (RuntimeException error) {
            observer.onError(Status.INTERNAL.withDescription("Attendance semester preview failed")
                    .withCause(error).asRuntimeException());
        }
    }
}
