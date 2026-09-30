package ru.rutcampustrack.academic.contract.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import ru.rutcampustrack.academic.contract.dto.semester.SemesterDeletionOperationResponse;

import java.util.UUID;

@Tag(name = "Semester deletion", description = "Статус окончательного удаления семестра")
@RequestMapping("/academic/semester-deletions")
public interface SemesterDeletionApi {

    @Operation(summary = "Получить сохранённый результат удаления по operationId (ADMIN)")
    @GetMapping("/{operationId}")
    ResponseEntity<SemesterDeletionOperationResponse> getSemesterDeletionOperation(
            @PathVariable UUID operationId);
}
