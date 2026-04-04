package ru.rutcampustrack.attendance.semester;

import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import ru.rutcampustrack.academic.grpc.SemesterResponse;
import ru.rutcampustrack.attendance.grpc.AcademicGrpcClient;

/**
 * Cache for the active semester ID.
 * Loaded at startup via @PostConstruct; refreshed when semester.archived event arrives.
 * In test profile, the entire bean is replaced by @MockitoBean, so @PostConstruct never runs.
 */
@Service
public class SemesterCacheService {

    private static final Logger log = LoggerFactory.getLogger(SemesterCacheService.class);

    private volatile Long activeSemesterId;
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
        return activeSemesterId;
    }

    public void refresh() {
        SemesterResponse response = academicGrpcClient.getActiveSemester();
        this.activeSemesterId = response.getId();
        log.info("Refreshed active semester: id={}", activeSemesterId);
    }
}
