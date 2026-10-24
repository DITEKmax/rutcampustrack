package ru.rutcampustrack.academic.groupdeletion;

import org.springframework.http.*;
import org.springframework.web.bind.annotation.RestController;

import ru.rutcampustrack.academic.contract.api.GroupDeletionApi;
import ru.rutcampustrack.academic.contract.dto.group.GroupDeletionModels.*;

import java.util.UUID;

@RestController
public class GroupDeletionController implements GroupDeletionApi {
    private final GroupDeletionService service;

    public GroupDeletionController(GroupDeletionService service) {
        this.service = service;
    }

    @Override
    public ResponseEntity<Preview> preview(long id) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(service.preview(id));
    }

    @Override
    public ResponseEntity<Operation> confirm(long id, UUID key, ConfirmRequest request) {
        var result = service.confirm(id, key, request);
        return ResponseEntity.status(result.phase() == Phase.COMPLETED ? 200 : 202)
                .cacheControl(CacheControl.noStore())
                .body(result);
    }

    @Override
    public ResponseEntity<Operation> resolve(long id, UUID operationId) {
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(service.resolve(id, operationId));
    }

    @Override
    public ResponseEntity<Operation> status(UUID id) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(service.status(id));
    }
}
