package ru.rutcampustrack.academic.dashboard;

import io.grpc.*;
import io.grpc.stub.MetadataUtils;
import java.util.concurrent.TimeUnit;
import net.devh.boot.grpc.client.inject.GrpcClient;
import org.springframework.stereotype.Component;
import ru.rutcampustrack.attendance.grpc.*;
import ru.rutcampustrack.shared.security.admin.AdminAccessException;

@Component
public class AttendanceAdminActivityClient {
    private static final Metadata.Key<String> TOKEN =
            Metadata.Key.of("x-internal-token", Metadata.ASCII_STRING_MARSHALLER);

    @GrpcClient("attendance-service")
    private AttendanceAdminActivityGrpcServiceGrpc.AttendanceAdminActivityGrpcServiceBlockingStub
            stub;

    public AdminActivitySnapshot read(ActivityWindow window, String token) {
        if (token == null || token.isBlank()) throw AdminAccessException.denied();
        Metadata headers = new Metadata();
        headers.put(TOKEN, token);
        try {
            AdminActivitySnapshot result =
                    stub.withInterceptors(MetadataUtils.newAttachHeadersInterceptor(headers))
                            .withDeadlineAfter(15, TimeUnit.SECONDS)
                            .getActivity(window.query());
            if (!result.hasSummary()
                    || !valid(result.getSummary())
                    || window.previous() != null
                            && (!result.hasPrevious() || !valid(result.getPrevious()))
                    || result.getSeriesCount() != window.series().size()) {
                throw new ActivityUnavailableException(
                        new IllegalStateException("Incomplete activity snapshot"));
            }
            for (int i = 0; i < result.getSeriesCount(); i++) {
                var row = result.getSeries(i);
                var range = window.series().get(i);
                if (!row.hasCounts()
                        || !valid(row.getCounts())
                        || row.getFromEpochMs() != range.from().toEpochMilli()
                        || row.getToEpochMs() != range.to().toEpochMilli()) {
                    throw new ActivityUnavailableException(
                            new IllegalStateException("Inconsistent activity window"));
                }
            }
            return result;
        } catch (StatusRuntimeException error) {
            if (error.getStatus().getCode() == Status.Code.PERMISSION_DENIED
                    || error.getStatus().getCode() == Status.Code.UNAUTHENTICATED)
                throw AdminAccessException.denied();
            throw new ActivityUnavailableException(error);
        }
    }

    private static boolean valid(AdminActivityCounts counts) {
        return counts.getPositiveMarks() >= 0
                && counts.getGeoMarks() >= 0
                && counts.getGeoMarks() <= counts.getPositiveMarks()
                && counts.getRequests() >= 0
                && counts.getHomeworkPublications() >= 0;
    }

    public static final class ActivityUnavailableException extends RuntimeException {
        ActivityUnavailableException(Throwable cause) {
            super("Activity dependency unavailable", cause);
        }
    }
}
