package ru.rutcampustrack.academic.grpc;

import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import net.devh.boot.grpc.client.inject.GrpcClient;
import org.springframework.stereotype.Component;
import ru.rutcampustrack.academic.exception.SemesterDeletionDependencyUnavailableException;
import ru.rutcampustrack.attendance.grpc.AttendanceSemesterDeletionGrpcServiceGrpc;
import ru.rutcampustrack.attendance.grpc.SemesterDeletionPreviewRequest;
import ru.rutcampustrack.attendance.grpc.SemesterDeletionParticipantPreview;

import java.util.concurrent.TimeUnit;

/** Secret-protected, read-only Attendance contribution to the semester deletion preview. */
@Component
public class AttendanceSemesterDeletionGrpcClient {

    @GrpcClient("attendance-service")
    private AttendanceSemesterDeletionGrpcServiceGrpc.AttendanceSemesterDeletionGrpcServiceBlockingStub stub;

    public SemesterDeletionParticipantPreview preview(long semesterId) {
        if (semesterId <= 0) throw new IllegalArgumentException("semesterId must be positive");
        try {
            return stub.withDeadlineAfter(3, TimeUnit.SECONDS)
                    .previewSemesterDeletion(SemesterDeletionPreviewRequest.newBuilder()
                            .setSemesterId(semesterId)
                            .build());
        } catch (StatusRuntimeException error) {
            Status.Code code = error.getStatus().getCode();
            if (code == Status.Code.NOT_FOUND) {
                throw new SemesterDeletionDependencyUnavailableException(
                        "Attendance не подтвердил предварительный расчёт удаления семестра", error);
            }
            throw new SemesterDeletionDependencyUnavailableException(
                    "Не удалось получить предварительный расчёт Attendance для удаления семестра", error);
        }
    }
}
