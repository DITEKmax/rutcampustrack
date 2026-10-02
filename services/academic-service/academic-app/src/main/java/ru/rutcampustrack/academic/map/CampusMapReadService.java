package ru.rutcampustrack.academic.map;

import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;
import ru.rutcampustrack.academic.entity.User;
import ru.rutcampustrack.academic.repository.UserRepository;
import ru.rutcampustrack.shared.security.InternalJwtClaims;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.BooleanSupplier;

import static ru.rutcampustrack.academic.map.CampusMapReadException.Code.DATA_LOSS;
import static ru.rutcampustrack.academic.map.CampusMapReadException.Code.FAILED_PRECONDITION;
import static ru.rutcampustrack.academic.map.CampusMapReadException.Code.INVALID_ARGUMENT;
import static ru.rutcampustrack.academic.map.CampusMapReadException.Code.NOT_FOUND;
import static ru.rutcampustrack.academic.map.CampusMapReadException.Code.PERMISSION_DENIED;
import static ru.rutcampustrack.academic.map.CampusMapReadException.Code.RESOURCE_EXHAUSTED;
import static ru.rutcampustrack.academic.map.CampusMapReadException.Code.UNAVAILABLE;
import static ru.rutcampustrack.academic.map.CampusMapReadModels.Asset;
import static ru.rutcampustrack.academic.map.CampusMapReadModels.AssetMetadata;
import static ru.rutcampustrack.academic.map.CampusMapReadModels.Building;
import static ru.rutcampustrack.academic.map.CampusMapReadModels.Catalog;
import static ru.rutcampustrack.academic.map.CampusMapReadModels.Floor;
import static ru.rutcampustrack.academic.map.CampusMapReadModels.FormatSlot;
import static ru.rutcampustrack.academic.map.CampusMapReadModels.Manifest;
import static ru.rutcampustrack.academic.map.CampusMapReadModels.ManifestBuilding;
import static ru.rutcampustrack.academic.map.CampusMapReadModels.ManifestFloor;
import static ru.rutcampustrack.academic.map.CampusMapReadModels.ManifestPlan;
import static ru.rutcampustrack.academic.map.CampusMapReadModels.ManifestResult;
import static ru.rutcampustrack.academic.map.CampusMapReadModels.Plan;
import static ru.rutcampustrack.academic.map.CampusMapReadModels.PlanResult;

/**
 * Authenticated, fail-closed read service for the Academic campus-map graph.
 *
 * <p>The manifest is assembled inside one repeatable-read transaction.  Plan
 * and asset reads use the same immutable row models, so the gRPC handler never
 * has to inspect JPA entities or raw database values.</p>
 */
@Service
public class CampusMapReadService {
    private static final String STUDENT = "STUDENT";
    private static final String TEACHER = "TEACHER";
    private static final String ACTIVE = "ACTIVE";
    private static final String EXPELLED = "EXPELLED";
    private static final String GRADUATED = "GRADUATED";
    private static final String ARCHIVED = "ARCHIVED";
    private static final long EMPTY_REVISION = 0L;

    private final CampusMapReadRepository repository;
    private final UserRepository userRepository;

    public CampusMapReadService(CampusMapReadRepository repository,
                                UserRepository userRepository) {
        this.repository = repository;
        this.userRepository = userRepository;
    }

    /**
     * Builds an ordered coherent manifest from one database snapshot.
     */
    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public ManifestResult readManifest(long knownRevision, InternalJwtClaims claims) {
        authorize(claims);
        if (knownRevision < EMPTY_REVISION) {
            throw failure(INVALID_ARGUMENT, "known revision is invalid");
        }

        Catalog catalog = currentCatalog();
        if (knownRevision > EMPTY_REVISION && knownRevision == catalog.revision()) {
            return new ManifestResult(true, catalog.revision(), null);
        }

        List<Building> buildingRows = repository.findActiveBuildings();
        List<Floor> floorRows = repository.findActiveFloors();
        List<Plan> currentPlanRows = repository.findCurrentPlans();

        Map<Long, Building> buildingsById = validateBuildings(buildingRows);
        Map<Long, List<Floor>> floorsByBuilding = validateFloors(floorRows, buildingsById);
        Map<Long, Plan> plansByFloor = validateCurrentPlans(currentPlanRows, floorRows);

        List<ManifestBuilding> buildings = new ArrayList<>();
        for (Building building : buildingRows) {
            List<Floor> floors = floorsByBuilding.getOrDefault(building.id(), List.of());
            validateFloorOrder(floors);
            List<ManifestFloor> manifestFloors = new ArrayList<>();
            for (Floor floor : floors) {
                Plan plan = planForFloor(floor, plansByFloor);
                ManifestPlan aggregate = plan == null
                        ? null
                        : toPlanAggregate(building.id(), plan, true);
                manifestFloors.add(new ManifestFloor(floor.id(), floor.label(), aggregate));
            }
            buildings.add(new ManifestBuilding(building.id(), building.label(), manifestFloors));
        }

        Manifest manifest = new Manifest(
                catalog.schemaVersion(),
                catalog.validationPolicyVersion(),
                catalog.revision(),
                buildings);
        return new ManifestResult(false, catalog.revision(), manifest);
    }

    /** Reads the current plan for one canonical building/floor pair. */
    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public PlanResult readFloorPlan(String buildingId,
                                    String floorId,
                                    InternalJwtClaims claims) {
        authorize(claims);
        long buildingKey = parseCanonicalId(buildingId, "building id");
        long floorKey = parseCanonicalId(floorId, "floor id");
        Catalog catalog = currentCatalog();

        Building building = repository.findActiveBuilding(buildingKey)
                .orElseThrow(() -> failure(NOT_FOUND, "campus map building was not found"));
        Floor floor = repository.findActiveFloor(floorKey)
                .orElseThrow(() -> failure(NOT_FOUND, "campus map floor was not found"));
        if (floor.buildingId() != building.id()) {
            throw failure(NOT_FOUND, "campus map floor was not found");
        }
        if (floor.currentVersionId() == null) {
            return new PlanResult(null);
        }
        if (floor.currentVersionId() <= 0) {
            throw failure(DATA_LOSS, "campus map current plan identity is invalid");
        }

        Plan plan = repository.findPlanById(floor.currentVersionId(), floor.id())
                .orElseThrow(() -> failure(DATA_LOSS, "campus map current plan graph is inconsistent"));
        if (plan.publishedAt() == null) {
            throw failure(FAILED_PRECONDITION, "campus map current plan is not published");
        }
        validatePlanIdentity(plan, floor.id());
        return new PlanResult(toPlanAggregate(building.id(), plan, true));
    }

    /**
     * Reads an immutable published asset.  An empty result means that the
     * stream was cancelled before completion; all other failures are typed.
     */
    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public Optional<Asset> readAsset(String buildingId,
                                     String floorId,
                                     long version,
                                     CampusMapFormat format,
                                     String assetId,
                                     InternalJwtClaims claims,
                                     BooleanSupplier cancelled) {
        BooleanSupplier cancellation = cancelled == null ? () -> false : cancelled;
        if (cancellation.getAsBoolean()) {
            return Optional.empty();
        }
        authorize(claims);
        if (cancellation.getAsBoolean()) {
            return Optional.empty();
        }
        long buildingKey = parseCanonicalId(buildingId, "building id");
        long floorKey = parseCanonicalId(floorId, "floor id");
        long assetKey = parseCanonicalId(assetId, "asset id");
        if (version <= 0) {
            throw failure(INVALID_ARGUMENT, "plan version is invalid");
        }
        if (format == null) {
            throw failure(INVALID_ARGUMENT, "map format is invalid");
        }

        Building building = repository.findActiveBuilding(buildingKey)
                .orElseThrow(() -> failure(NOT_FOUND, "campus map building was not found"));
        Floor floor = repository.findActiveFloor(floorKey)
                .orElseThrow(() -> failure(NOT_FOUND, "campus map floor was not found"));
        if (floor.buildingId() != building.id()) {
            throw failure(NOT_FOUND, "campus map floor was not found");
        }
        if (cancellation.getAsBoolean()) {
            return Optional.empty();
        }

        Plan plan = repository.findPlan(floor.id(), version)
                .orElseThrow(() -> failure(NOT_FOUND, "campus map plan was not found"));
        if (plan.publishedAt() == null) {
            throw failure(FAILED_PRECONDITION, "campus map plan is not published");
        }
        validatePlanIdentity(plan, floor.id());
        if (cancellation.getAsBoolean()) {
            return Optional.empty();
        }

        FormatSlot slot = selectedSlot(repository.findFormatSlots(plan.id()), plan.id(), format);
        if (!Objects.equals(slot.assetId(), assetKey)) {
            throw failure(NOT_FOUND, "campus map asset was not found");
        }
        if (cancellation.getAsBoolean()) {
            return Optional.empty();
        }
        AssetMetadata metadata = validateReadySlot(slot, plan, true);
        if (cancellation.getAsBoolean()) {
            return Optional.empty();
        }

        Asset asset = repository.loadAsset(assetKey, plan.id(), format)
                .orElseThrow(() -> failure(DATA_LOSS, "campus map asset content is unavailable"));
        byte[] content = asset.content();
        if (content == null || content.length == 0 || content.length > CampusMapReadRepository.MAX_ASSET_BYTES) {
            if (content != null && content.length > CampusMapReadRepository.MAX_ASSET_BYTES) {
                throw failure(RESOURCE_EXHAUSTED, "campus map asset is too large");
            }
            throw failure(DATA_LOSS, "campus map asset content is invalid");
        }
        if (content.length != metadata.contentLength() || content.length != metadata.bytes()) {
            throw failure(DATA_LOSS, "campus map asset size is inconsistent");
        }
        if (!digestMatches(content, metadata.sha256(), cancellation)) {
            if (cancellation.getAsBoolean()) {
                return Optional.empty();
            }
            throw failure(DATA_LOSS, "campus map asset digest is inconsistent");
        }
        if (cancellation.getAsBoolean()) {
            return Optional.empty();
        }
        return Optional.of(asset);
    }

    private void authorize(InternalJwtClaims claims) {
        boolean student = claims != null && STUDENT.equalsIgnoreCase(claims.domainRole());
        boolean teacher = claims != null && TEACHER.equalsIgnoreCase(claims.domainRole());
        boolean validCommonClaims = claims != null
                && claims.userId() > 0
                && claims.sessionId() != null
                && claims.sessionVersion() > 0
                && claims.rolesVersion() > 0;
        boolean validStudentClaims = student
                && claims.groupId() != null
                && claims.groupId() > 0
                && allowedStatus(claims.status(), claims.readOnly());
        boolean validTeacherClaims = teacher
                && !claims.readOnly()
                && ACTIVE.equalsIgnoreCase(claims.status());
        if (!validCommonClaims || (!validStudentClaims && !validTeacherClaims)) {
            throw failure(PERMISSION_DENIED, "signed campus-map identity is not allowed");
        }
        try {
            Optional<User> user = userRepository.findByIdIncludingArchived(claims.userId());
            if (user == null || user.isEmpty()) {
                throw failure(PERMISSION_DENIED, "signed campus-map identity is not allowed");
            }
        } catch (DataAccessException error) {
            throw failure(CampusMapReadException.Code.INTERNAL,
                    "campus map identity lookup failed",
                    error);
        }
    }

    private static boolean allowedStatus(String status, boolean readOnly) {
        if (!readOnly) {
            return ACTIVE.equalsIgnoreCase(status);
        }
        return EXPELLED.equalsIgnoreCase(status)
                || GRADUATED.equalsIgnoreCase(status)
                || ARCHIVED.equalsIgnoreCase(status);
    }

    private Catalog currentCatalog() {
        List<Catalog> rows = repository.findCurrentCatalogs();
        if (rows == null || rows.isEmpty()) {
            throw failure(UNAVAILABLE, "campus map catalog is unavailable");
        }
        if (rows.size() != 1) {
            throw failure(DATA_LOSS, "campus map catalog graph is inconsistent");
        }
        Catalog catalog = rows.get(0);
        if (catalog == null
                || !catalog.current()
                || catalog.id() <= 0
                || catalog.revision() <= 0
                || catalog.schemaVersion() <= 0
                || catalog.validationPolicyVersion() <= 0) {
            throw failure(DATA_LOSS, "campus map catalog metadata is invalid");
        }
        return catalog;
    }

    private static Map<Long, Building> validateBuildings(List<Building> rows) {
        if (rows == null) {
            throw failure(DATA_LOSS, "campus map building graph is unavailable");
        }
        Map<Long, Building> byId = new LinkedHashMap<>();
        int previousOrder = -1;
        long previousId = -1;
        for (Building building : rows) {
            if (building == null
                    || building.id() <= 0
                    || building.label() == null
                    || building.displayOrder() < 0
                    || !building.active()
                    || byId.putIfAbsent(building.id(), building) != null
                    || building.displayOrder() < previousOrder
                    || (building.displayOrder() == previousOrder && building.id() <= previousId)) {
                throw failure(DATA_LOSS, "campus map building graph is inconsistent");
            }
            previousOrder = building.displayOrder();
            previousId = building.id();
        }
        return byId;
    }

    private static Map<Long, List<Floor>> validateFloors(List<Floor> rows,
                                                          Map<Long, Building> buildingsById) {
        if (rows == null) {
            throw failure(DATA_LOSS, "campus map floor graph is unavailable");
        }
        Map<Long, List<Floor>> byBuilding = new LinkedHashMap<>();
        Set<Long> floorIds = new HashSet<>();
        for (Floor floor : rows) {
            if (floor == null
                    || floor.id() <= 0
                    || floor.buildingId() <= 0
                    || floor.label() == null
                    || floor.displayOrder() < 0
                    || !floor.active()
                    || !floorIds.add(floor.id())
                    || !buildingsById.containsKey(floor.buildingId())) {
                throw failure(DATA_LOSS, "campus map floor graph is inconsistent");
            }
            byBuilding.computeIfAbsent(floor.buildingId(), ignored -> new ArrayList<>()).add(floor);
        }
        return byBuilding;
    }

    private static void validateFloorOrder(List<Floor> rows) {
        int previousOrder = -1;
        long previousId = -1;
        for (Floor floor : rows) {
            if (floor.displayOrder() < previousOrder
                    || (floor.displayOrder() == previousOrder && floor.id() <= previousId)) {
                throw failure(DATA_LOSS, "campus map floor ordering is inconsistent");
            }
            previousOrder = floor.displayOrder();
            previousId = floor.id();
        }
    }

    private Map<Long, Plan> validateCurrentPlans(List<Plan> rows,
                                                  List<Floor> floorRows) {
        if (rows == null) {
            throw failure(DATA_LOSS, "campus map plan graph is unavailable");
        }
        Map<Long, Floor> floorsById = new HashMap<>();
        for (Floor floor : floorRows) {
            floorsById.put(floor.id(), floor);
        }
        Map<Long, Plan> byFloor = new HashMap<>();
        Set<Long> planIds = new HashSet<>();
        for (Plan plan : rows) {
            if (plan == null
                    || plan.id() <= 0
                    || plan.floorId() <= 0
                    || plan.version() <= 0
                    || plan.label() == null
                    || plan.publishedAt() == null
                    || !planIds.add(plan.id())
                    || byFloor.putIfAbsent(plan.floorId(), plan) != null) {
                throw failure(DATA_LOSS, "campus map current plan graph is inconsistent");
            }
            Floor floor = floorsById.get(plan.floorId());
            if (floor == null
                    || floor.currentVersionId() == null
                    || floor.currentVersionId() != plan.id()) {
                throw failure(DATA_LOSS, "campus map current plan parent is inconsistent");
            }
            validatePlanIdentity(plan, floor.id());
        }
        for (Floor floor : floorRows) {
            if (floor.currentVersionId() != null
                    && (floor.currentVersionId() <= 0 || !byFloor.containsKey(floor.id()))) {
                throw failure(DATA_LOSS, "campus map current plan pointer is inconsistent");
            }
        }
        return byFloor;
    }

    private static Plan planForFloor(Floor floor, Map<Long, Plan> plansByFloor) {
        if (floor.currentVersionId() == null) {
            return null;
        }
        Plan plan = plansByFloor.get(floor.id());
        if (plan == null || plan.id() != floor.currentVersionId()) {
            throw failure(DATA_LOSS, "campus map current plan pointer is inconsistent");
        }
        return plan;
    }

    private ManifestPlan toPlanAggregate(long buildingId, Plan plan, boolean validateAssets) {
        List<FormatSlot> rows = repository.findFormatSlots(plan.id());
        if (rows == null || rows.size() != 2) {
            throw failure(DATA_LOSS, "campus map plan format graph is inconsistent");
        }
        Map<CampusMapFormat, FormatSlot> slots = new LinkedHashMap<>();
        Set<Long> ids = new HashSet<>();
        for (FormatSlot slot : rows) {
            if (slot == null || !ids.add(slot.id()) || slots.putIfAbsent(slot.format(), slot) != null) {
                throw failure(DATA_LOSS, "campus map plan format graph is inconsistent");
            }
            validateSlot(slot, plan, validateAssets);
        }
        FormatSlot png = slots.get(CampusMapFormat.PNG);
        FormatSlot svg = slots.get(CampusMapFormat.SVG);
        if (png == null || svg == null) {
            throw failure(DATA_LOSS, "campus map plan requires both formats");
        }
        return new ManifestPlan(buildingId, plan.floorId(), plan.version(), plan.label(), png, svg);
    }

    private void validateSlot(FormatSlot slot, Plan plan, boolean validateAssets) {
        if (slot.id() <= 0
                || slot.planVersionId() != plan.id()
                || slot.format() == null
                || slot.state() == null
                || slot.contentType() == null
                || !expectedMime(slot.format()).equals(slot.contentType())
                || slot.bytes() < 0
                || slot.width() != null && slot.width() <= 0
                || slot.height() != null && slot.height() <= 0) {
            throw failure(DATA_LOSS, "campus map format metadata is invalid");
        }
        if (slot.bytes() > CampusMapReadRepository.MAX_ASSET_BYTES) {
            throw failure(RESOURCE_EXHAUSTED, "campus map asset is too large");
        }
        validateViewBox(slot.viewBox());
        if (slot.state() == CampusMapFormatState.READY) {
            if (slot.assetId() == null || slot.assetId() <= 0 || slot.bytes() <= 0
                    || slot.sha256() == null || slot.sha256().length != 32) {
                throw failure(DATA_LOSS, "ready campus map format metadata is invalid");
            }
            if (validateAssets) {
                AssetMetadata metadata = repository.findAssetMetadata(
                                slot.assetId(), slot.planVersionId(), slot.format())
                        .orElseThrow(() -> failure(DATA_LOSS, "ready campus map asset is missing"));
                validateAssetMetadata(slot, metadata);
            }
        } else if (slot.assetId() != null || slot.bytes() != 0 || slot.sha256() != null) {
            throw failure(DATA_LOSS, "non-ready campus map format has asset metadata");
        }
    }

    private AssetMetadata validateReadySlot(FormatSlot slot, Plan plan, boolean validateAssets) {
        if (slot == null) {
            throw failure(NOT_FOUND, "campus map format was not found");
        }
        if (slot.state() == null) {
            throw failure(DATA_LOSS, "campus map format metadata is invalid");
        }
        if (slot.state() != CampusMapFormatState.READY) {
            throw failure(FAILED_PRECONDITION, "campus map asset is not ready");
        }
        validateSlot(slot, plan, false);
        if (validateAssets) {
            AssetMetadata metadata = repository.findAssetMetadata(
                            slot.assetId(), slot.planVersionId(), slot.format())
                    .orElseThrow(() -> failure(DATA_LOSS, "ready campus map asset is missing"));
            validateAssetMetadata(slot, metadata);
            return metadata;
        }
        return null;
    }

    private static FormatSlot selectedSlot(List<FormatSlot> rows,
                                           long planVersionId,
                                           CampusMapFormat format) {
        if (rows == null) {
            throw failure(DATA_LOSS, "campus map plan format graph is unavailable");
        }
        FormatSlot selected = null;
        Set<Long> slotIds = new HashSet<>();
        Set<CampusMapFormat> formats = new HashSet<>();
        for (FormatSlot row : rows) {
            if (row == null
                    || row.id() <= 0
                    || row.planVersionId() != planVersionId
                    || row.format() == null
                    || !slotIds.add(row.id())
                    || !formats.add(row.format())) {
                throw failure(DATA_LOSS, "campus map plan format parent is inconsistent");
            }
            if (row.format() == format) {
                if (selected != null) {
                    throw failure(DATA_LOSS, "campus map plan format is duplicated");
                }
                selected = row;
            }
        }
        if (selected == null) {
            throw failure(NOT_FOUND, "campus map format was not found");
        }
        return selected;
    }

    private void validatePlanIdentity(Plan plan, long floorId) {
        if (plan == null
                || plan.id() <= 0
                || plan.floorId() != floorId
                || plan.version() <= 0
                || plan.label() == null) {
            throw failure(DATA_LOSS, "campus map plan identity is invalid");
        }
        if (plan.catalogRevisionId() == null || plan.catalogRevisionId() <= 0) {
            throw failure(DATA_LOSS, "campus map plan catalog identity is invalid");
        }
        Optional<Catalog> parent = repository.findCatalogById(plan.catalogRevisionId());
        if (parent == null
                || parent.isEmpty()
                || parent.get().id() != plan.catalogRevisionId()) {
            throw failure(DATA_LOSS, "campus map plan catalog parent is unavailable");
        }
    }

    private static void validateAssetMetadata(FormatSlot slot, AssetMetadata metadata) {
        if (metadata == null
                || metadata.id() != slot.assetId()
                || metadata.planVersionId() != slot.planVersionId()
                || metadata.format() != slot.format()
                || !expectedMime(slot.format()).equals(metadata.contentType())
                || metadata.bytes() <= 0
                || metadata.contentLength() <= 0
                || metadata.bytes() != metadata.contentLength()
                || metadata.bytes() != slot.bytes()
                || metadata.sha256() == null
                || metadata.sha256().length != 32
                || !Arrays.equals(metadata.sha256(), slot.sha256())
                || !Objects.equals(metadata.width(), slot.width())
                || !Objects.equals(metadata.height(), slot.height())) {
            throw failure(DATA_LOSS, "campus map asset metadata is inconsistent");
        }
        if (metadata.bytes() > CampusMapReadRepository.MAX_ASSET_BYTES) {
            throw failure(RESOURCE_EXHAUSTED, "campus map asset is too large");
        }
    }

    private static void validateViewBox(List<Double> viewBox) {
        if (viewBox == null || viewBox.isEmpty()) {
            return;
        }
        if (viewBox.size() != 4 || viewBox.stream().anyMatch(value -> value == null || !Double.isFinite(value))) {
            throw failure(DATA_LOSS, "campus map view box is invalid");
        }
    }

    private static boolean digestMatches(byte[] content,
                                         byte[] expected,
                                         BooleanSupplier cancelled) {
        if (expected == null || expected.length != 32) {
            throw failure(DATA_LOSS, "campus map asset digest is invalid");
        }
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            for (int offset = 0; offset < content.length; offset += 65_536) {
                if (cancelled.getAsBoolean()) {
                    return false;
                }
                int length = Math.min(65_536, content.length - offset);
                digest.update(content, offset, length);
            }
            return MessageDigest.isEqual(expected, digest.digest());
        } catch (NoSuchAlgorithmException error) {
            throw failure(CampusMapReadException.Code.INTERNAL,
                    "campus map asset digest is unavailable",
                    error);
        }
    }

    private static String expectedMime(CampusMapFormat format) {
        return switch (format) {
            case PNG -> "image/png";
            case SVG -> "image/svg+xml";
        };
    }

    private static long parseCanonicalId(String raw, String field) {
        if (raw == null || raw.isEmpty()) {
            throw failure(INVALID_ARGUMENT, "campus map " + field + " is invalid");
        }
        final long parsed;
        try {
            parsed = Long.parseLong(raw);
        } catch (NumberFormatException error) {
            throw failure(INVALID_ARGUMENT, "campus map " + field + " is invalid");
        }
        if (parsed <= 0 || !Long.toString(parsed).equals(raw)) {
            throw failure(INVALID_ARGUMENT, "campus map " + field + " is invalid");
        }
        return parsed;
    }

    private static CampusMapReadException failure(CampusMapReadException.Code code, String message) {
        return CampusMapReadException.of(code, message);
    }

    private static CampusMapReadException failure(CampusMapReadException.Code code,
                                                    String message,
                                                    Throwable cause) {
        return CampusMapReadException.of(code, message, cause);
    }
}
