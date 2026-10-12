package ru.rutcampustrack.attendance.grpc;

import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import io.grpc.stub.StreamObserver;
import java.time.Clock;
import net.devh.boot.grpc.server.service.GrpcService;
import ru.rutcampustrack.attendance.adminactivity.AdminActivityReadRepository;
import ru.rutcampustrack.attendance.adminactivity.AdminActivityWindow;

@GrpcService
public final class AdminActivityGrpcService
        extends AttendanceAdminActivityGrpcServiceGrpc.AttendanceAdminActivityGrpcServiceImplBase {
    private final AcademicAdminPermissionClient authority;
    private final AdminActivityReadRepository repository;
    private final Clock clock;

    public AdminActivityGrpcService(
            AcademicAdminPermissionClient authority,
            AdminActivityReadRepository repository,
            Clock clock) {
        this.authority = authority;
        this.repository = repository;
        this.clock = clock;
    }

    @Override
    public void getActivity(
            AdminActivityQuery query, StreamObserver<AdminActivitySnapshot> observer) {
        try {
            String token = AdminActivityGrpcIdentity.requireAdminToken();
            authority.requireDashboard(token);
            observer.onNext(repository.read(AdminActivityWindow.of(query, clock)));
            observer.onCompleted();
        } catch (StatusRuntimeException error) {
            observer.onError(error);
        } catch (IllegalArgumentException invalid) {
            observer.onError(
                    Status.INVALID_ARGUMENT
                            .withDescription("Invalid activity window")
                            .asRuntimeException());
        } catch (RuntimeException unavailable) {
            observer.onError(
                    Status.UNAVAILABLE
                            .withDescription("Activity storage unavailable")
                            .asRuntimeException());
        }
    }
}
