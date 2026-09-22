package ru.rutcampustrack.academic.map;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;
import ru.rutcampustrack.shared.security.InternalJwtClaims;

import java.nio.ByteBuffer;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;
import java.util.Map;
import java.util.UUID;

import static ru.rutcampustrack.academic.map.CampusMapReadException.Code.FAILED_PRECONDITION;
import static ru.rutcampustrack.academic.map.CampusMapReadException.Code.INVALID_ARGUMENT;

/**
 * Records one logical, user-facing floor opening and serves the shared admin
 * aggregate.  The database trigger remains the single increment authority.
 */
@Service
public class CampusMapUsageService {
    private final CampusMapReadService mapReadService;
    private final CampusMapUsageRepository repository;
    private final CampusMapOwnerHmacProvider ownerHmacProvider;

    public CampusMapUsageService(CampusMapReadService mapReadService,
                                 CampusMapUsageRepository repository,
                                 CampusMapOwnerHmacProvider ownerHmacProvider) {
        this.mapReadService = mapReadService;
        this.repository = repository;
        this.ownerHmacProvider = ownerHmacProvider;
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public void recordFloorOpen(String buildingId,
                                String floorId,
                                UUID intentId,
                                InternalJwtClaims claims) {
        if (intentId == null) {
            throw CampusMapReadException.of(INVALID_ARGUMENT, "map open intent is required");
        }

        CampusMapReadModels.PlanResult plan = mapReadService.readFloorPlan(buildingId, floorId, claims);
        if (!plan.hasPlan()) {
            throw CampusMapReadException.of(FAILED_PRECONDITION,
                    "campus map floor has no published plan");
        }

        long buildingKey = parseCanonicalId(buildingId, "building id");
        long floorKey = parseCanonicalId(floorId, "floor id");
        byte[] ownerHmac = ownerHmacProvider.forUser(claims.userId());
        byte[] payloadHash = payloadHash(buildingKey, floorKey, intentId);

        repository.insertOpenIntent(ownerHmac, intentId, buildingKey, floorKey, payloadHash);
        CampusMapUsageRepository.OpenIntentRow intent = repository
                .findOpenIntentForUpdate(ownerHmac, intentId)
                .orElseThrow(() -> CampusMapReadException.of(
                        CampusMapReadException.Code.INTERNAL,
                        "campus map open intent was not retained"));
        if (intent.buildingId() != buildingKey
                || intent.floorId() != floorKey
                || !Arrays.equals(intent.payloadHash(), payloadHash)) {
            throw CampusMapReadException.of(FAILED_PRECONDITION,
                    "map open intent is already bound to another floor");
        }

        repository.insertDemandDedupe(ownerHmac, floorKey, intent.acceptedUtcDay(),
                intentId, intent.acceptedAt());
    }

    @Transactional(readOnly = true)
    public Map<Long, Long> allTimeFloorCounts() {
        return repository.findAllTimeFloorCounts();
    }

    @Transactional(readOnly = true)
    public long allTimeTotal() {
        return repository.findAllTimeTotal();
    }

    private static long parseCanonicalId(String raw, String field) {
        if (raw == null || !raw.matches("[1-9][0-9]*")) {
            throw CampusMapReadException.of(INVALID_ARGUMENT, field + " is invalid");
        }
        try {
            return Long.parseLong(raw);
        } catch (NumberFormatException error) {
            throw CampusMapReadException.of(INVALID_ARGUMENT, field + " is invalid", error);
        }
    }

    private static byte[] payloadHash(long buildingId, long floorId, UUID intentId) {
        try {
            ByteBuffer payload = ByteBuffer.allocate(Long.BYTES * 2 + 16)
                    .putLong(buildingId)
                    .putLong(floorId)
                    .putLong(intentId.getMostSignificantBits())
                    .putLong(intentId.getLeastSignificantBits());
            return MessageDigest.getInstance("SHA-256").digest(payload.array());
        } catch (NoSuchAlgorithmException error) {
            throw CampusMapReadException.of(
                    CampusMapReadException.Code.INTERNAL,
                    "campus map open payload hashing failed",
                    error);
        }
    }
}
