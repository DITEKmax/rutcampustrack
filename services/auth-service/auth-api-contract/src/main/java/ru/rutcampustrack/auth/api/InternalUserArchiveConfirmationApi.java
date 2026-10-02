package ru.rutcampustrack.auth.api;

import io.swagger.v3.oas.annotations.Hidden;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import ru.rutcampustrack.auth.dto.ConfirmUserArchiveRequest;

@Hidden
@RequestMapping("/internal/auth")
public interface InternalUserArchiveConfirmationApi {
    @PostMapping("/confirm-user-archive")
    ResponseEntity<Void> confirmUserArchive(@Valid @RequestBody ConfirmUserArchiveRequest request);
}
