package ru.rutcampustrack.academic.admin;

import java.util.*;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.RestController;
import ru.rutcampustrack.academic.contract.api.AdminAccessApi;
import ru.rutcampustrack.academic.contract.dto.user.AdminAccessInput;
import ru.rutcampustrack.academic.contract.dto.user.AdminAccessModels.*;
import ru.rutcampustrack.shared.security.admin.*;

@RestController
public class AdminAccessController implements AdminAccessApi {
    private final AdminAccessService service;
    private final AcademicAdminAccess access;

    public AdminAccessController(AdminAccessService service, AcademicAdminAccess access) {
        this.service = service;
        this.access = access;
    }

    @Override
    public ResponseEntity<Catalog> catalog() {
        access.current();
        return ok(
                new Catalog(
                        Arrays.stream(AdminPermission.values())
                                .map(AdminPermission::entry)
                                .map(
                                        e ->
                                                new Entry(
                                                        e.code(),
                                                        e.label(),
                                                        e.description(),
                                                        e.group(),
                                                        e.requires(),
                                                        e.fullOnly()))
                                .toList()));
    }

    @Override
    public ResponseEntity<Profile> me() {
        return ok(wire(access.current()));
    }

    @Override
    public ResponseEntity<Profile> get(long id) {
        return ok(wire(service.get(id)));
    }

    @Override
    public ResponseEntity<Profile> put(long id, Put request) {
        return ok(
                wire(
                        service.put(
                                id,
                                new AdminAccessInput(request.mode(), request.permissions()),
                                request.expectedVersion())));
    }

    @Override
    public ResponseEntity<Void> delete(long id, Delete request) {
        service.revoke(id, request.expectedVersion());
        return ResponseEntity.noContent().cacheControl(CacheControl.noStore()).build();
    }

    private Profile wire(AdminAccessProfile p) {
        return new Profile(p.userId(), p.assigned(), p.mode(), p.permissions(), p.version());
    }

    private <T> ResponseEntity<T> ok(T result) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(result);
    }
}
