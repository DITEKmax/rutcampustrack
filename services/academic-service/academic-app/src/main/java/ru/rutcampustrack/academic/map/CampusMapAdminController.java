package ru.rutcampustrack.academic.map;

import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import ru.rutcampustrack.academic.contract.api.CampusMapAdminApi;
import ru.rutcampustrack.academic.contract.dto.map.CampusMapAdminModels.BuildingResponse;
import ru.rutcampustrack.academic.contract.dto.map.CampusMapAdminModels.CreateBuildingRequest;
import ru.rutcampustrack.academic.contract.dto.map.CampusMapAdminModels.CreateFloorRequest;
import ru.rutcampustrack.academic.contract.dto.map.CampusMapAdminModels.FloorResponse;
import ru.rutcampustrack.academic.contract.dto.map.CampusMapAdminModels.PlanResponse;
import ru.rutcampustrack.academic.contract.dto.map.CampusMapAdminModels.UpdateInventoryRequest;
import ru.rutcampustrack.academic.contract.dto.map.CampusMapAdminModels.DeletionPreview;
import ru.rutcampustrack.academic.contract.dto.map.CampusMapAdminModels.DeleteRequest;
import ru.rutcampustrack.academic.contract.dto.map.CampusMapAdminModels.DeletionResult;
import ru.rutcampustrack.academic.contract.dto.map.CampusMapAdminModels.DeletionTarget;
import ru.rutcampustrack.academic.security.RequireRole;

import java.util.List;

import static ru.rutcampustrack.academic.contract.enums.UserRole.ADMIN;

/** REST adapter for the admin campus-map inventory and upload commands. */
@RestController
public class CampusMapAdminController implements CampusMapAdminApi {
    private final CampusMapAdminService service;
    private final CampusMapDeletionService deletion;

    public CampusMapAdminController(CampusMapAdminService service, CampusMapDeletionService deletion) {
        this.service = service;
        this.deletion = deletion;
    }

    @Override
    @RequireRole({ADMIN})
    public ResponseEntity<List<BuildingResponse>> listBuildings() {
        return ResponseEntity.ok(service.listBuildings());
    }

    @Override
    @RequireRole({ADMIN})
    public ResponseEntity<BuildingResponse> createBuilding(CreateBuildingRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.createBuilding(request));
    }

    @Override
    @RequireRole({ADMIN})
    public ResponseEntity<List<FloorResponse>> listFloors(String buildingId) {
        return ResponseEntity.ok(service.listFloors(buildingId));
    }

    @Override
    @RequireRole({ADMIN})
    public ResponseEntity<FloorResponse> createFloor(CreateFloorRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.createFloor(request));
    }

    @Override
    @RequireRole({ADMIN})
    public ResponseEntity<BuildingResponse> updateBuilding(String buildingId, UpdateInventoryRequest request) {
        return ResponseEntity.ok(service.updateBuilding(buildingId, request));
    }

    @Override
    @RequireRole({ADMIN})
    public ResponseEntity<FloorResponse> updateFloor(String floorId, UpdateInventoryRequest request) {
        return ResponseEntity.ok(service.updateFloor(floorId, request));
    }

    @Override
    @RequireRole({ADMIN})
    public ResponseEntity<DeletionPreview> previewFloorDeletion(String floorId) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .body(deletion.preview(DeletionTarget.FLOOR, floorId));
    }

    @Override
    @RequireRole({ADMIN})
    public ResponseEntity<DeletionPreview> previewBuildingDeletion(String buildingId) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .body(deletion.preview(DeletionTarget.BUILDING, buildingId));
    }

    @Override
    @RequireRole({ADMIN})
    public ResponseEntity<DeletionResult> deleteFloor(String floorId, DeleteRequest request) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .body(deletion.delete(DeletionTarget.FLOOR, floorId, request));
    }

    @Override
    @RequireRole({ADMIN})
    public ResponseEntity<DeletionResult> deleteBuilding(String buildingId, DeleteRequest request) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .body(deletion.delete(DeletionTarget.BUILDING, buildingId, request));
    }

    @Override
    @RequireRole({ADMIN})
    public ResponseEntity<PlanResponse> uploadVersion(String floorId,
                                                      String label,
                                                      MultipartFile png,
                                                      MultipartFile svg) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(service.uploadVersion(floorId, label, png, svg));
    }

    @Override
    @RequireRole({ADMIN})
    public ResponseEntity<PlanResponse> getVersion(String floorId, String version) {
        return ResponseEntity.ok(service.getVersion(floorId, version));
    }

    @Override
    @RequireRole({ADMIN})
    public ResponseEntity<byte[]> downloadAsset(String floorId,
                                                String version,
                                                String format,
                                                String assetId) {
        CampusMapAdminService.Download download = service.downloadAsset(floorId, version, format, assetId);
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.parseMediaType(download.contentType()));
        headers.setContentLength(download.content().length);
        headers.set(HttpHeaders.CONTENT_DISPOSITION,
                "attachment; filename=\"floor-" + floorId + "-v" + version + "-"
                        + download.format().name().toLowerCase(java.util.Locale.ROOT) + "\"");
        headers.setCacheControl(CacheControl.noStore());
        return new ResponseEntity<>(download.content(), headers, HttpStatus.OK);
    }
}
