package ru.rutcampustrack.academic.contract.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import ru.rutcampustrack.academic.contract.dto.semester.SemesterArchiveOperationResponse;

import java.util.UUID;

/** Read-only lookup for the immutable history/progress of an archive command. */
@Tag(name = "Semester archive operations", description = "История команд архивации семестров")
@RequestMapping("/academic/semester-archive-operations")
public interface SemesterArchiveOperationApi {

    @Operation(summary = "Прочитать сохранённую операцию архивации (ADMIN)")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Операция найдена"),
            @ApiResponse(responseCode = "403", description = "Нет прав доступа"),
            @ApiResponse(responseCode = "404", description = "Операция не найдена")
    })
    @GetMapping("/{operationId}")
    ResponseEntity<SemesterArchiveOperationResponse> getSemesterArchiveOperation(
            @PathVariable UUID operationId);
}
