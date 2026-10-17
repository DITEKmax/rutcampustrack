package ru.rutcampustrack.attendance.grpc;

import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import io.grpc.stub.StreamObserver;

import net.devh.boot.grpc.server.service.GrpcService;

import ru.rutcampustrack.attendance.report.AdminGroupStatsReadService;

@GrpcService
public final class AdminGroupStatsGrpcService
        extends AttendanceAdminGroupStatsGrpcServiceGrpc
                .AttendanceAdminGroupStatsGrpcServiceImplBase {
    private final AcademicAdminPermissionClient authority;
    private final AdminGroupStatsReadService reports;

    public AdminGroupStatsGrpcService(
            AcademicAdminPermissionClient authority, AdminGroupStatsReadService reports) {
        this.authority = authority;
        this.reports = reports;
    }

    @Override
    public void getGroupSemesterStats(
            AdminGroupStatsQuery query, StreamObserver<AdminGroupStatsSnapshot> observer) {
        try {
            String token = AdminGroupStatsGrpcIdentity.requireAdminToken();
            authority.requireDashboard(token);
            var result = reports.read(query);
            authority.requireDashboard(token);
            observer.onNext(result);
            observer.onCompleted();
        } catch (StatusRuntimeException failure) {
            observer.onError(failure);
        } catch (RuntimeException failure) {
            boolean conflict = false;
            Throwable cause = failure;
            for (int depth = 0; cause != null && depth < 8; depth++) {
                if (cause instanceof ru.rutcampustrack.attendance.exception.ConflictException) {
                    conflict = true;
                    break;
                }
                cause = cause.getCause();
            }
            observer.onError(
                    (conflict ? Status.FAILED_PRECONDITION : Status.UNAVAILABLE)
                            .withDescription(
                                    conflict
                                            ? "Unsupported stored attendance data"
                                            : "Group attendance unavailable")
                            .asRuntimeException());
        }
    }
}
