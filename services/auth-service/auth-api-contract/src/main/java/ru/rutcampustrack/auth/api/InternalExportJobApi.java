package ru.rutcampustrack.auth.api;

import io.swagger.v3.oas.annotations.Hidden;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import ru.rutcampustrack.auth.dto.*;

@Hidden
@RequestMapping("/internal/export-jobs")
public interface InternalExportJobApi {
    @PostMapping("/claim") ResponseEntity<ExportJobModels.Claim> claim();
    @PostMapping("/{id}/complete") ResponseEntity<ExportJobModels.Status> complete(@PathVariable UUID id, @Valid @RequestBody ExportJobModels.Complete result);
    @PostMapping("/{id}/fail") ResponseEntity<Void> fail(@PathVariable UUID id, @Valid @RequestBody ExportJobModels.Fail failure);
    @PostMapping("/admit") ResponseEntity<Void> admit(@Valid @RequestBody ExportJobModels.Admit request);
    @PostMapping("/{id}/file") ResponseEntity<byte[]> file(@PathVariable UUID id, @Valid @RequestBody WsSessionAdmissionRequest actor);
}
