package ru.rutcampustrack.academic.map;

import static ru.rutcampustrack.academic.contract.enums.UserRole.ADMIN;

import org.springframework.stereotype.Service;
import ru.rutcampustrack.academic.contract.dto.map.CampusMapAdminModels.*;
import ru.rutcampustrack.academic.exception.AccessDeniedException;
import ru.rutcampustrack.academic.exception.BadRequestException;
import ru.rutcampustrack.academic.security.RequestContext;

@Service
public class CampusMapDeletionService {
    @org.springframework.beans.factory.annotation.Autowired
    private ru.rutcampustrack.academic.admin.AcademicAdminAccess adminAccess;

    private final CampusMapDeletionTransaction transaction;
    private final AuthMapDeletionClient auth;
    private final RequestContext context;

    public CampusMapDeletionService(
            CampusMapDeletionTransaction transaction,
            AuthMapDeletionClient auth,
            RequestContext context) {
        this.transaction = transaction;
        this.auth = auth;
        this.context = context;
    }

    public DeletionPreview preview(DeletionTarget type, String rawId) {
        requireAdmin();
        return transaction.preview(type, id(rawId));
    }

    public DeletionResult delete(DeletionTarget type, String rawId, DeleteRequest request) {
        requireAdmin();
        long id = id(rawId);
        // Includes retries: a receipt cannot bypass live session and current password proof.
        auth.confirm(type, id, request.operationId(), request.previewDigest(), request.password());
        return transaction.delete(type, id, context.getUserId(), request);
    }

    public PlanDeletionPreview previewPlan(
            String buildingCode, String floorCode, String rawFormat) {
        requireAdmin();
        String building = CampusMapAdminService.requireUploadCode(buildingCode, "buildingCode");
        String floor = CampusMapAdminService.requireUploadCode(floorCode, "floorCode");
        Format format = null;
        if (rawFormat != null) {
            try {
                format = Format.valueOf(rawFormat);
            } catch (IllegalArgumentException invalid) {
                throw new BadRequestException("format", "Поддерживаются только png и svg");
            }
        }
        return transaction.previewPlan(building, floor, format);
    }

    public PlanDeletionResult deletePlan(PlanDeleteRequest request) {
        requireAdmin();
        CampusMapAdminService.requireUploadCode(request.buildingCode(), "buildingCode");
        CampusMapAdminService.requireUploadCode(request.floorCode(), "floorCode");
        long originalFloorId = transaction.planConfirmationTarget(context.getUserId(), request);
        // Every replay still proves the live session/current password for the original identity.
        auth.confirm(
                DeletionTarget.FLOOR,
                originalFloorId,
                request.operationId(),
                request.previewDigest(),
                request.password());
        return transaction.deletePlan(originalFloorId, context.getUserId(), request);
    }

    private void requireAdmin() {
        adminAccess.require("CAMPUS_MAP_MANAGE", "DATA_DELETE");
        if (context.getRole() != ADMIN || context.getUserId() == null || context.getUserId() <= 0) {
            throw new AccessDeniedException("Нужна роль ADMIN");
        }
    }

    private static long id(String raw) {
        try {
            if (raw == null || !raw.matches("[1-9][0-9]*")) throw new NumberFormatException();
            return Long.parseLong(raw);
        } catch (NumberFormatException invalid) {
            throw new BadRequestException("id", "Идентификатор должен быть положительным числом");
        }
    }
}
