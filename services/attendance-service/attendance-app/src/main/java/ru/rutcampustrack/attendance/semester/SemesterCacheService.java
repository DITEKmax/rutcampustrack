package ru.rutcampustrack.attendance.semester;

import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import ru.rutcampustrack.academic.grpc.SemesterResponse;
import ru.rutcampustrack.attendance.contract.exception.ResourceNotFoundException;
import ru.rutcampustrack.attendance.grpc.AcademicGrpcClient;

/**
 * Resolves the active semester from Academic before current-semester operations.
 * The historical cache API remains available to startup and archive-event callers,
 * but a missed event must not leave requests using an archived semester indefinitely.
 */
@Service
public class SemesterCacheService {

    private static final Logger log = LoggerFactory.getLogger(SemesterCacheService.class);

    private final AcademicGrpcClient academicGrpcClient;

    public SemesterCacheService(AcademicGrpcClient academicGrpcClient) {
        this.academicGrpcClient = academicGrpcClient;
    }

    @PostConstruct
    public void init() {
        // Catch any exception so startup is not blocked if Academic Service is temporarily unavailable
        try {
            refresh();
        } catch (Exception e) {
            log.warn("Could not load active semester at startup: {}", e.getMessage());
        }
    }

    public Long getActiveSemesterId() {
        // Event delivery is asynchronous: each request needs authoritative state.
        // Dependency failure propagates instead of silently admitting stale options.
        return resolveActiveSemesterId();
    }

    public void refresh() {
        Long semesterId = resolveActiveSemesterId();
        log.info("Refreshed active semester: id={}", semesterId);
    }

    private Long resolveActiveSemesterId() {
        try {
            SemesterResponse response = academicGrpcClient.getActiveSemester();
            return response.getId();
        } catch (ResourceNotFoundException e) {
            // Authoritative absence after archive; do not select another semester.
            return null;
        }
    }
}
