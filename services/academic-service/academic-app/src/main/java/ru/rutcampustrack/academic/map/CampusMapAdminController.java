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
import ru.rutcampustrack.academic.security.RequireRole;

import java.util.List;

import static ru.rutcampustrack.academic.contract.enums.UserRole.ADMIN;

/** REST adapter for the admin campus-map inventory and upload commands. */
@RestController
public class CampusMapAdminController implements CampusMapAdminApi {
    private final CampusMapAdminService service;

    public CampusMapAdminController(CampusMapAdminService service) {
        this.service = service;
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
