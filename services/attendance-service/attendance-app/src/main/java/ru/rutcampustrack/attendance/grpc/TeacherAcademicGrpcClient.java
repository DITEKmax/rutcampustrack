package ru.rutcampustrack.attendance.grpc;

import io.grpc.Metadata;
import io.grpc.StatusRuntimeException;
import io.grpc.stub.MetadataUtils;
import java.util.concurrent.TimeUnit;
import net.devh.boot.grpc.client.inject.GrpcClient;
import org.springframework.stereotype.Component;
import ru.rutcampustrack.attendance.contract.exception.ResourceNotFoundException;
import ru.rutcampustrack.attendance.exception.AcademicServiceUnavailableException;
import ru.rutcampustrack.attendance.exception.AccessDeniedException;
import ru.rutcampustrack.attendance.exception.BadRequestException;
import ru.rutcampustrack.teacher.grpc.TeacherAcademicReadServiceGrpc;
import ru.rutcampustrack.teacher.grpc.TeacherAssignmentsRequest;
import ru.rutcampustrack.teacher.grpc.TeacherAssignmentsResponse;

/** Forwards the validated teacher identity to the teacher-specific Academic read API. */
@Component
public final class TeacherAcademicGrpcClient {
    @org.springframework.beans.factory.annotation.Autowired
    private org.springframework.beans.factory.ObjectProvider<ru.rutcampustrack.attendance.security.RequestContext> httpContext;
    private static final Metadata.Key<String> INTERNAL_TOKEN =
            Metadata.Key.of("x-internal-token", Metadata.ASCII_STRING_MARSHALLER);

    @GrpcClient("academic-service")
    private TeacherAcademicReadServiceGrpc.TeacherAcademicReadServiceBlockingStub stub;

    public TeacherAssignmentsResponse fullSemesterAssignments(long semesterId) {
        if (semesterId <= 0) throw new BadRequestException("semester_id must be positive");
        String token = TeacherAttendanceGrpcIdentity.SIGNED_TOKEN.get();
        if (token == null && httpContext != null) {
            try { token = httpContext.getObject().getInternalToken(); }
            catch (org.springframework.beans.factory.support.ScopeNotActiveException noHttpRequest) { /* gRPC uses its context above */ }
        }
        if (token == null || token.isBlank()) throw new AccessDeniedException("Signed teacher identity required");
        Metadata headers = new Metadata();
        headers.put(INTERNAL_TOKEN, token);
        try {
            return stub.withInterceptors(MetadataUtils.newAttachHeadersInterceptor(headers))
                    .withDeadlineAfter(3, TimeUnit.SECONDS)
                    .listTeacherAssignments(
                            TeacherAssignmentsRequest.newBuilder()
                                    .setSemesterId(semesterId)
                                    .setFullSemester(true)
                                    .build());
        } catch (StatusRuntimeException error) {
            switch (error.getStatus().getCode()) {
                case PERMISSION_DENIED, UNAUTHENTICATED ->
                        throw new AccessDeniedException(
                                "Teacher semester is outside the signed scope");
                case NOT_FOUND -> throw new ResourceNotFoundException("Semester", "id", semesterId);
                case INVALID_ARGUMENT ->
                        throw new BadRequestException("Academic rejected semester_id");
                default ->
                        throw new AcademicServiceUnavailableException(
                                "Academic Service unavailable: " + error.getStatus());
            }
        }
    }
}
