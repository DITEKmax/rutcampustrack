package ru.rutcampustrack.academic.contract.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.multipart.MultipartFile;
import ru.rutcampustrack.academic.contract.dto.map.CampusMapAdminModels.BuildingResponse;
import ru.rutcampustrack.academic.contract.dto.map.CampusMapAdminModels.CreateBuildingRequest;
import ru.rutcampustrack.academic.contract.dto.map.CampusMapAdminModels.CreateFloorRequest;
import ru.rutcampustrack.academic.contract.dto.map.CampusMapAdminModels.FloorResponse;
import ru.rutcampustrack.academic.contract.dto.map.CampusMapAdminModels.PlanResponse;

import java.util.List;

/** Contract-first admin surface for campus inventory and immutable map versions. */
@Tag(name = "Campus map", description = "Управление корпусами, этажами и схемами")
@SecurityRequirement(name = "bearerAuth")
@RequestMapping("/academic/map")
public interface CampusMapAdminApi {

    @Operation(summary = "Список корпусов и этажей")
    @ApiResponse(responseCode = "200", description = "Ordered campus map inventory")
    @GetMapping("/buildings")
    ResponseEntity<List<BuildingResponse>> listBuildings();

    @Operation(summary = "Добавить корпус")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Корпус создан"),
            @ApiResponse(responseCode = "403", description = "Нужна роль ADMIN"),
            @ApiResponse(responseCode = "409", description = "Код корпуса уже используется")
    })
    @PostMapping("/buildings")
    ResponseEntity<BuildingResponse> createBuilding(@Valid @RequestBody CreateBuildingRequest request);

    @Operation(summary = "Список этажей корпуса")
    @ApiResponse(responseCode = "200", description = "Ordered floor inventory")
    @GetMapping("/floors")
    ResponseEntity<List<FloorResponse>> listFloors(@RequestParam(required = false) String buildingId);

    @Operation(summary = "Добавить этаж")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Этаж создан"),
            @ApiResponse(responseCode = "403", description = "Нужна роль ADMIN"),
            @ApiResponse(responseCode = "404", description = "Корпус не найден"),
            @ApiResponse(responseCode = "409", description = "Этаж с таким кодом уже существует")
    })
    @PostMapping("/floors")
    ResponseEntity<FloorResponse> createFloor(@Valid @RequestBody CreateFloorRequest request);

    @Operation(summary = "Загрузить новую версию схемы этажа")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Версия опубликована; отсутствующий формат остаётся explicit absent"),
            @ApiResponse(responseCode = "400", description = "Файл не прошёл MIME/signature/SVG validation"),
            @ApiResponse(responseCode = "403", description = "Нужна роль ADMIN"),
            @ApiResponse(responseCode = "404", description = "Этаж не найден"),
            @ApiResponse(responseCode = "409", description = "Конфликт публикации")
    })
    @PostMapping(value = "/floors/{floorId}/versions", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    ResponseEntity<PlanResponse> uploadVersion(
            @PathVariable String floorId,
            @RequestPart(name = "label", required = false) String label,
            @RequestPart(name = "png", required = false) MultipartFile png,
            @RequestPart(name = "svg", required = false) MultipartFile svg);

    @Operation(summary = "Прочитать версию схемы")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Version metadata"),
            @ApiResponse(responseCode = "404", description = "Версия не найдена")
    })
    @GetMapping("/floors/{floorId}/versions/{version}")
    ResponseEntity<PlanResponse> getVersion(@PathVariable String floorId, @PathVariable String version);

    @Operation(summary = "Скачать администраторскую копию ассета")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Validated asset bytes"),
            @ApiResponse(responseCode = "404", description = "Ассет не найден"),
            @ApiResponse(responseCode = "409", description = "Ассет не готов")
    })
    @GetMapping("/floors/{floorId}/versions/{version}/assets/{format}/{assetId}")
    ResponseEntity<byte[]> downloadAsset(
            @PathVariable String floorId,
            @PathVariable String version,
            @PathVariable String format,
            @PathVariable String assetId);
}
