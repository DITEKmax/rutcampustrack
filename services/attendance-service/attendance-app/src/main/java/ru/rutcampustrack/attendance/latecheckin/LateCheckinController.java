package ru.rutcampustrack.attendance.latecheckin;

import org.springframework.hateoas.EntityModel;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;
import ru.rutcampustrack.attendance.contract.api.LateCheckinApi;
import ru.rutcampustrack.attendance.contract.dto.latecheckin.LateCheckinRequestResponse;
import ru.rutcampustrack.attendance.contract.enums.UserRole;
import ru.rutcampustrack.attendance.latecheckin.entity.LateCheckinRequest;
import ru.rutcampustrack.attendance.security.RequireRole;

@RestController
public class LateCheckinController implements LateCheckinApi {

    private final LateCheckinService service;
    private final LateCheckinAssembler assembler;

    public LateCheckinController(LateCheckinService service, LateCheckinAssembler assembler) {
        this.service = service;
        this.assembler = assembler;
    }

    @Override
    @RequireRole(UserRole.STUDENT)
    public ResponseEntity<EntityModel<LateCheckinRequestResponse>> createRequest(Long lessonId) {
        LateCheckinRequest request = service.createRequest(lessonId);
        return ResponseEntity.status(HttpStatus.CREATED).body(assembler.toModel(request));
    }
}
