package ru.rutcampustrack.academic.map;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;
import ru.rutcampustrack.academic.contract.dto.map.CampusMapAdminModels.*;
import ru.rutcampustrack.academic.contract.exception.ResourceNotFoundException;
import ru.rutcampustrack.academic.exception.ConflictException;

@Service
public class CampusMapDeletionTransaction {
    @org.springframework.beans.factory.annotation.Autowired
    private ru.rutcampustrack.academic.admin.AcademicAdminAccess adminAccess;

    private final CampusMapAdminRepository catalog;
    private final CampusMapDeletionRepository deletion;

    public CampusMapDeletionTransaction(
            CampusMapAdminRepository catalog, CampusMapDeletionRepository deletion) {
        this.catalog = catalog;
        this.deletion = deletion;
    }

    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public DeletionPreview preview(DeletionTarget type, long id) {
        return snapshot(type, id, false);
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public DeletionResult delete(
            DeletionTarget type, long id, long ownerId, DeleteRequest request) {
        adminAccess.mutation(null, "CAMPUS_MAP_MANAGE");
        adminAccess.require("DATA_DELETE");
        catalog.lockCatalogForWrite();
        var prior = deletion.receipt(request.operationId());
        if (prior.isPresent()) {
            var receipt = prior.get();
            if (!"INVENTORY".equals(receipt.actionKind())
                    || receipt.ownerId() != ownerId
                    || receipt.type() != type
                    || receipt.targetId() != id
                    || !receipt.digest().equals(request.previewDigest())) {
                throw new ConflictException(
                        "operationId", request.operationId(), "Операция удаления уже занята");
            }
            return completed(type, id, request);
        }
        DeletionPreview current = snapshot(type, id, true);
        if (!current.previewDigest().equals(request.previewDigest())) {
            throw new ConflictException(
                    "previewDigest",
                    request.previewDigest(),
                    "Данные изменились. Запроси новый предварительный просмотр удаления");
        }
        if (type == DeletionTarget.FLOOR) deletion.deleteFloor(id);
        else deletion.deleteEmptyBuilding(id);
        catalog.advanceCatalogRevision();
        deletion.insertReceipt(request.operationId(), ownerId, type, id, request.previewDigest());
        return completed(type, id, request);
    }

    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public PlanDeletionPreview previewPlan(String buildingCode, String floorCode, Format format) {
        return planSnapshot(buildingCode, floorCode, format).preview();
    }

    /**
     * Resolve the original identity for Auth, including retries after pointer clear or code reuse.
     */
    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public long planConfirmationTarget(long ownerId, PlanDeleteRequest request) {
        var prior = deletion.receipt(request.operationId());
        if (prior.isPresent()) {
            requirePlanReceipt(prior.get(), ownerId, request);
            return prior.get().targetId();
        }
        var snapshot = deletionSnapshot(request);
        return snapshot.floor().id();
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public PlanDeletionResult deletePlan(
            long confirmedFloorId, long ownerId, PlanDeleteRequest request) {
        adminAccess.mutation(null, "CAMPUS_MAP_MANAGE");
        adminAccess.require("DATA_DELETE");
        catalog.lockCatalogForWrite();
        var prior = deletion.receipt(request.operationId());
        if (prior.isPresent()) {
            requirePlanReceipt(prior.get(), ownerId, request);
            if (prior.get().targetId() != confirmedFloorId) throw stalePlan(request);
            return planCompleted(request);
        }
        var snapshot = deletionSnapshot(request);
        if (snapshot.floor().id() != confirmedFloorId) throw stalePlan(request);
        var revision = catalog.advanceCatalogRevision();
        CampusMapFormat removed =
                request.format() == null
                        ? null
                        : CampusMapFormat.valueOf(
                                request.format().name().toUpperCase(java.util.Locale.ROOT));
        boolean hasOther =
                removed != null
                        && snapshot.slots().stream()
                                .anyMatch(
                                        slot ->
                                                slot.format() != removed
                                                        && slot.state()
                                                                == CampusMapFormatState.READY);
        if (removed == null) {
            deletion.purgePlanGraph(confirmedFloorId);
        } else if (!hasOther) {
            catalog.clearCurrentPlan(confirmedFloorId);
        } else {
            long version = catalog.highestPlanVersion(confirmedFloorId) + 1L;
            if (version <= 0)
                throw new ConflictException("version", version, "Номер версии схемы исчерпан");
            long successor =
                    catalog.insertPlan(
                            confirmedFloorId, version, revision.id(), snapshot.plan().label());
            for (CampusMapFormat format : CampusMapFormat.values()) {
                if (format == removed) {
                    catalog.insertSlot(
                            successor,
                            format,
                            CampusMapFormatState.ABSENT,
                            format == CampusMapFormat.PNG ? "image/png" : "image/svg+xml",
                            null,
                            0,
                            null,
                            null,
                            null);
                } else {
                    catalog.copySlot(snapshot.plan().id(), successor, format);
                }
            }
            catalog.publishPlan(successor);
            catalog.pointFloorAt(confirmedFloorId, successor);
        }
        deletion.insertPlanReceipt(
                request.operationId(),
                ownerId,
                confirmedFloorId,
                request.previewDigest(),
                request.buildingCode(),
                request.floorCode(),
                request.format() == null ? null : request.format().name());
        return planCompleted(request);
    }

    private PlanSnapshot deletionSnapshot(PlanDeleteRequest request) {
        try {
            var snapshot =
                    planSnapshot(request.buildingCode(), request.floorCode(), request.format());
            if (!snapshot.preview().previewDigest().equals(request.previewDigest()))
                throw stalePlan(request);
            return snapshot;
        } catch (ResourceNotFoundException missing) {
            throw stalePlan(request);
        }
    }

    private PlanSnapshot planSnapshot(String buildingCode, String floorCode, Format format) {
        var floor =
                catalog.findActiveFloorByCodes(buildingCode, floorCode)
                        .orElseThrow(
                                () ->
                                        new ResourceNotFoundException(
                                                "Схема", "floorCode", floorCode));
        if (format == null) return wholePlanSnapshot(buildingCode, floorCode, floor);
        var plan =
                catalog.findCurrentPlan(floor.id())
                        .filter(row -> row.publishedAt() != null)
                        .orElseThrow(
                                () ->
                                        new ResourceNotFoundException(
                                                "Схема", "floorCode", floorCode));
        var slots = catalog.findSlots(plan.id());
        var selected =
                slots.stream()
                        .filter(
                                slot ->
                                        slot.state() == CampusMapFormatState.READY
                                                && (format == null
                                                        || slot.format()
                                                                .name()
                                                                .equalsIgnoreCase(format.name())))
                        .toList();
        if (selected.isEmpty())
            throw new ResourceNotFoundException("Формат схемы", "format", format);
        // Codes, immutable identity and all current slots make replacement/rename stale. Counts are
        // excluded.
        String value =
                "PLAN_CLEAR:"
                        + buildingCode.length()
                        + ":"
                        + buildingCode
                        + ":"
                        + floorCode.length()
                        + ":"
                        + floorCode
                        + ":"
                        + floor.id()
                        + ":"
                        + floor.buildingId()
                        + ":"
                        + plan.id()
                        + ":"
                        + format
                        + ":"
                        + plan.label().length()
                        + ":"
                        + plan.label()
                        + ":"
                        + catalog.latestCatalogRevision();
        for (var slot :
                slots.stream()
                        .sorted(java.util.Comparator.comparing(row -> row.format().name()))
                        .toList()) {
            value +=
                    ":"
                            + slot.format()
                            + ":"
                            + slot.state()
                            + ":"
                            + slot.assetId()
                            + ":"
                            + slot.bytes()
                            + ":"
                            + (slot.sha256() == null
                                    ? "null"
                                    : HexFormat.of().formatHex(slot.sha256()));
        }
        var preview =
                new PlanDeletionPreview(
                        buildingCode,
                        floorCode,
                        format,
                        plan.label(),
                        selected.size(),
                        selected.stream().mapToLong(CampusMapAdminRepository.SlotRow::bytes).sum(),
                        digest(value),
                        PlanDeletionScope.CURRENT_FORMAT);
        return new PlanSnapshot(floor, plan, slots, preview);
    }

    private PlanSnapshot wholePlanSnapshot(
            String buildingCode, String floorCode, CampusMapAdminRepository.FloorRow floor) {
        var plans = catalog.findPlans(floor.id());
        if (plans.isEmpty()) throw new ResourceNotFoundException("Схема", "floorCode", floorCode);
        var latest = plans.getLast();
        var assets = deletion.planAssets(floor.id());
        // A distinct domain prevents an old current-slot preview from authorising a whole purge.
        var value =
                new StringBuilder("PLAN_PURGE_ALL_V1:")
                        .append(buildingCode.length())
                        .append(':')
                        .append(buildingCode)
                        .append(':')
                        .append(floorCode.length())
                        .append(':')
                        .append(floorCode)
                        .append(':')
                        .append(floor.id())
                        .append(':')
                        .append(floor.buildingId())
                        .append(':')
                        .append(floor.currentVersionId())
                        .append(':')
                        .append(catalog.highestPlanVersion(floor.id()))
                        .append(':')
                        .append(catalog.latestCatalogRevision());
        for (var plan : plans) {
            value.append(":P:")
                    .append(plan.id())
                    .append(':')
                    .append(plan.version())
                    .append(':')
                    .append(plan.catalogRevisionId())
                    .append(':')
                    .append(plan.publishedAt())
                    .append(':')
                    .append(plan.label().length())
                    .append(':')
                    .append(plan.label());
            for (var slot : catalog.findSlots(plan.id())) {
                value.append(":S:")
                        .append(slot.id())
                        .append(':')
                        .append(slot.format())
                        .append(':')
                        .append(slot.state())
                        .append(':')
                        .append(slot.assetId())
                        .append(':')
                        .append(slot.bytes())
                        .append(':')
                        .append(
                                slot.sha256() == null
                                        ? "null"
                                        : HexFormat.of().formatHex(slot.sha256()));
            }
        }
        for (var asset : assets) {
            value.append(":A:")
                    .append(asset.id())
                    .append(':')
                    .append(asset.planVersionId())
                    .append(':')
                    .append(asset.format())
                    .append(':')
                    .append(asset.bytes())
                    .append(':')
                    .append(HexFormat.of().formatHex(asset.sha256()));
        }
        var preview =
                new PlanDeletionPreview(
                        buildingCode,
                        floorCode,
                        null,
                        latest.label(),
                        assets.size(),
                        assets.stream()
                                .mapToLong(CampusMapDeletionRepository.PlanAsset::bytes)
                                .sum(),
                        digest(value.toString()),
                        PlanDeletionScope.ALL_VERSIONS);
        return new PlanSnapshot(floor, latest, java.util.List.of(), preview);
    }

    private static void requirePlanReceipt(
            CampusMapDeletionRepository.Receipt receipt, long ownerId, PlanDeleteRequest request) {
        if (!"PLAN_CLEAR".equals(receipt.actionKind())
                || receipt.ownerId() != ownerId
                || receipt.type() != DeletionTarget.FLOOR
                || !java.util.Objects.equals(receipt.buildingCode(), request.buildingCode())
                || !java.util.Objects.equals(receipt.floorCode(), request.floorCode())
                || !java.util.Objects.equals(
                        receipt.format(), request.format() == null ? null : request.format().name())
                || !receipt.digest().equals(request.previewDigest())) {
            throw new ConflictException(
                    "operationId", request.operationId(), "Операция удаления уже занята");
        }
    }

    private static ConflictException stalePlan(PlanDeleteRequest request) {
        return new ConflictException(
                "previewDigest",
                request.previewDigest(),
                "Схема изменилась. Запроси новый предварительный просмотр удаления");
    }

    private static PlanDeletionResult planCompleted(PlanDeleteRequest request) {
        return new PlanDeletionResult(
                request.operationId(),
                request.buildingCode(),
                request.floorCode(),
                request.format(),
                "COMPLETED");
    }

    private record PlanSnapshot(
            CampusMapAdminRepository.FloorRow floor,
            CampusMapAdminRepository.PlanRow plan,
            java.util.List<CampusMapAdminRepository.SlotRow> slots,
            PlanDeletionPreview preview) {}

    private DeletionPreview snapshot(DeletionTarget type, long id, boolean lock) {
        long buildingId;
        String label;
        Long currentVersion = null;
        if (type == DeletionTarget.FLOOR) {
            var floor =
                    (lock
                                    ? catalog.findFloorForUpdate(id)
                                    : catalog.findActiveFloors(null).stream()
                                            .filter(row -> row.id() == id)
                                            .findFirst())
                            .filter(CampusMapAdminRepository.FloorRow::active)
                            .orElseThrow(() -> new ResourceNotFoundException("Этаж", "id", id));
            buildingId = floor.buildingId();
            label = floor.label();
            currentVersion = floor.currentVersionId();
        } else {
            buildingId = id;
            label =
                    catalog.findActiveBuilding(id)
                            .orElseThrow(() -> new ResourceNotFoundException("Корпус", "id", id))
                            .label();
        }
        catalog.findActiveBuilding(buildingId)
                .orElseThrow(() -> new ResourceNotFoundException("Корпус", "id", buildingId));
        var counts = deletion.counts(type == DeletionTarget.FLOOR ? id : 0, buildingId);
        if (type == DeletionTarget.BUILDING && counts.remainingFloors() != 0) {
            throw new ConflictException("buildingId", id, "Сначала удали все этажи корпуса");
        }
        String digest =
                digest(
                        type
                                + ":"
                                + id
                                + ":"
                                + buildingId
                                + ":"
                                + currentVersion
                                + ":"
                                + label.length()
                                + ":"
                                + label
                                + ":"
                                + counts);
        return new DeletionPreview(
                type,
                Long.toString(id),
                Long.toString(buildingId),
                label,
                counts.versions(),
                counts.assets(),
                counts.bytes(),
                counts.opens(),
                counts.remainingFloors(),
                digest);
    }

    private static String digest(String value) {
        try {
            return HexFormat.of()
                    .formatHex(
                            MessageDigest.getInstance("SHA-256")
                                    .digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException(impossible);
        }
    }

    private static DeletionResult completed(DeletionTarget type, long id, DeleteRequest request) {
        return new DeletionResult(request.operationId(), type, Long.toString(id), "COMPLETED");
    }
}
