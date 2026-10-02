package ru.rutcampustrack.academic.map;

import org.springframework.stereotype.Service;
import ru.rutcampustrack.academic.contract.dto.map.CampusMapAdminModels.*;
import ru.rutcampustrack.academic.exception.AccessDeniedException;
import ru.rutcampustrack.academic.exception.BadRequestException;
import ru.rutcampustrack.academic.security.RequestContext;

import static ru.rutcampustrack.academic.contract.enums.UserRole.ADMIN;

@Service
public class CampusMapDeletionService {
    private final CampusMapDeletionTransaction transaction;
    private final AuthMapDeletionClient auth;
    private final RequestContext context;

    public CampusMapDeletionService(CampusMapDeletionTransaction transaction,
            AuthMapDeletionClient auth, RequestContext context) {
        this.transaction = transaction; this.auth = auth; this.context = context;
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

    private void requireAdmin() {
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
