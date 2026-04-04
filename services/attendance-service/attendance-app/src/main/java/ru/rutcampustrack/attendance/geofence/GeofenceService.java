package ru.rutcampustrack.attendance.geofence;

import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import ru.rutcampustrack.academic.grpc.GeofenceResponse;
import ru.rutcampustrack.attendance.grpc.AcademicGrpcClient;

import java.time.Duration;
import java.time.Instant;

/**
 * Service for campus geofence validation.
 * Follows SemesterCacheService pattern: volatile cache with TTL, @PostConstruct init with try/catch.
 * Per D-07, D-08: loaded at startup, refreshed every 30 minutes.
 * In test profile, @MockitoBean replaces this bean so @PostConstruct never runs.
 */
@Service
public class GeofenceService {

    private static final Logger log = LoggerFactory.getLogger(GeofenceService.class);
    private static final Duration CACHE_TTL = Duration.ofMinutes(30);

    private volatile GeofenceData cachedGeofence;
    private volatile Instant lastRefreshed;

    private final AcademicGrpcClient academicGrpcClient;

    public GeofenceService(AcademicGrpcClient academicGrpcClient) {
        this.academicGrpcClient = academicGrpcClient;
    }

    @PostConstruct
    public void init() {
        // Catch any exception so startup is not blocked if Academic Service is temporarily unavailable
        try {
            refresh();
        } catch (Exception e) {
            log.warn("Could not load campus geofence at startup: {}", e.getMessage());
        }
    }

    /**
     * Checks whether the given coordinates are within the campus geofence.
     *
     * @param lat latitude in degrees
     * @param lng longitude in degrees
     * @return true if within geofence radius
     */
    public boolean isWithinCampus(double lat, double lng) {
        GeofenceData geofence = getOrRefresh();
        return GeoUtils.isWithinRadius(lat, lng, geofence.lat(), geofence.lng(), geofence.radiusMeters());
    }

    /**
     * Forces a refresh of the cached geofence from Academic Service via gRPC.
     */
    public void refresh() {
        GeofenceResponse response = academicGrpcClient.getCampusGeofence();
        this.cachedGeofence = new GeofenceData(response.getLat(), response.getLng(), response.getRadiusM());
        this.lastRefreshed = Instant.now();
        log.info("Refreshed campus geofence: lat={}, lng={}, radius={}m",
                cachedGeofence.lat(), cachedGeofence.lng(), cachedGeofence.radiusMeters());
    }

    private GeofenceData getOrRefresh() {
        if (cachedGeofence == null || isCacheExpired()) {
            refresh();
        }
        return cachedGeofence;
    }

    private boolean isCacheExpired() {
        return lastRefreshed == null || Instant.now().isAfter(lastRefreshed.plus(CACHE_TTL));
    }

    /**
     * Internal value type holding geofence center and radius.
     */
    record GeofenceData(double lat, double lng, double radiusMeters) {}
}
