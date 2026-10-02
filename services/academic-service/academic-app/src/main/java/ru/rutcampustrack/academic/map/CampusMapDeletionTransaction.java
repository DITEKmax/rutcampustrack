package ru.rutcampustrack.academic.map;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;
import ru.rutcampustrack.academic.contract.dto.map.CampusMapAdminModels.*;
import ru.rutcampustrack.academic.contract.exception.ResourceNotFoundException;
import ru.rutcampustrack.academic.exception.ConflictException;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

@Service
public class CampusMapDeletionTransaction {
    private final CampusMapAdminRepository catalog;
    private final CampusMapDeletionRepository deletion;

    public CampusMapDeletionTransaction(CampusMapAdminRepository catalog, CampusMapDeletionRepository deletion) {
        this.catalog = catalog;
        this.deletion = deletion;
    }

    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public DeletionPreview preview(DeletionTarget type, long id) {
        return snapshot(type, id, false);
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public DeletionResult delete(DeletionTarget type, long id, long ownerId, DeleteRequest request) {
        catalog.lockCatalogForWrite();
        var prior = deletion.receipt(request.operationId());
        if (prior.isPresent()) {
            var receipt = prior.get();
            if (receipt.ownerId() != ownerId || receipt.type() != type || receipt.targetId() != id
                    || !receipt.digest().equals(request.previewDigest())) {
                throw new ConflictException("operationId", request.operationId(), "Операция удаления уже занята");
            }
            return completed(type, id, request);
        }
        DeletionPreview current = snapshot(type, id, true);
        if (!current.previewDigest().equals(request.previewDigest())) {
            throw new ConflictException("previewDigest", request.previewDigest(),
                    "Данные изменились. Запроси новый предварительный просмотр удаления");
        }
        if (type == DeletionTarget.FLOOR) deletion.deleteFloor(id);
        else deletion.deleteEmptyBuilding(id);
        catalog.advanceCatalogRevision();
        deletion.insertReceipt(request.operationId(), ownerId, type, id, request.previewDigest());
        return completed(type, id, request);
    }

    private DeletionPreview snapshot(DeletionTarget type, long id, boolean lock) {
        long buildingId;
        String label;
        Long currentVersion = null;
        if (type == DeletionTarget.FLOOR) {
            var floor = (lock ? catalog.findFloorForUpdate(id)
                    : catalog.findActiveFloors(null).stream().filter(row -> row.id() == id).findFirst())
                    .filter(CampusMapAdminRepository.FloorRow::active)
                    .orElseThrow(() -> new ResourceNotFoundException("Этаж", "id", id));
            buildingId = floor.buildingId();
            label = floor.label();
            currentVersion = floor.currentVersionId();
        } else {
            buildingId = id;
            label = catalog.findActiveBuilding(id)
                    .orElseThrow(() -> new ResourceNotFoundException("Корпус", "id", id)).label();
        }
        catalog.findActiveBuilding(buildingId)
                .orElseThrow(() -> new ResourceNotFoundException("Корпус", "id", buildingId));
        var counts = deletion.counts(type == DeletionTarget.FLOOR ? id : 0, buildingId);
        if (type == DeletionTarget.BUILDING && counts.remainingFloors() != 0) {
            throw new ConflictException("buildingId", id, "Сначала удали все этажи корпуса");
        }
        String digest = digest(type + ":" + id + ":" + buildingId + ":" + currentVersion + ":"
                + label.length() + ":" + label + ":" + counts);
        return new DeletionPreview(type, Long.toString(id), Long.toString(buildingId), label,
                counts.versions(), counts.assets(), counts.bytes(), counts.opens(), counts.remainingFloors(), digest);
    }

    private static String digest(String value) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                .digest(value.getBytes(StandardCharsets.UTF_8))); }
        catch (NoSuchAlgorithmException impossible) { throw new IllegalStateException(impossible); }
    }

    private static DeletionResult completed(DeletionTarget type, long id, DeleteRequest request) {
        return new DeletionResult(request.operationId(), type, Long.toString(id), "COMPLETED");
    }
}
