package ru.rutcampustrack.mobilebff.map;

import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.Resource;
import org.springframework.http.CacheControl;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import ru.rutcampustrack.mobilebff.contract.model.StudentMapModels.Format;
import ru.rutcampustrack.mobilebff.contract.model.StudentMapModels.ManifestResponse;
import ru.rutcampustrack.mobilebff.contract.model.StudentMapModels.PlanResponse;
import ru.rutcampustrack.mobilebff.grpc.MapAcademicClient;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

/** Compatibility read/download alias for clients using the original student path. */
@RestController
@RequestMapping("/api/v1/student/map")
public class StudentMapCompatibilityController {
    private final MapQueryFacade maps;

    public StudentMapCompatibilityController(MapQueryFacade maps) {
        this.maps = maps;
    }

    @GetMapping("/manifest")
    public ResponseEntity<ManifestResponse> getManifest(
            @RequestHeader(name = "If-None-Match", required = false) String ifNoneMatch) {
        MapAcademicClient.ManifestResult result = maps.manifest(ifNoneMatch);
        String etag = "\"" + result.revision() + "\"";
        if (result.unchanged()) {
            return ResponseEntity.status(HttpStatus.NOT_MODIFIED)
                    .cacheControl(CacheControl.noCache().cachePrivate())
                    .eTag(etag)
                    .build();
        }
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noCache().cachePrivate())
                .eTag(etag)
                .body(result.manifest());
    }

    @GetMapping("/buildings/{buildingId}/floors/{floorId}/plan")
    public ResponseEntity<PlanResponse> getFloorPlan(@PathVariable String buildingId,
                                                      @PathVariable String floorId) {
        PlanResponse response = maps.floorPlan(buildingId, floorId);
        if (response.plan() == null) {
            return ResponseEntity.status(HttpStatus.NO_CONTENT)
                    .cacheControl(CacheControl.noStore())
                    .build();
        }
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(response);
    }

    @GetMapping("/buildings/{buildingId}/floors/{floorId}/plans/{version}/assets/{format}/{assetId}")
    public ResponseEntity<Resource> readAsset(@PathVariable String buildingId,
                                               @PathVariable String floorId,
                                               @PathVariable String version,
                                               @PathVariable Format format,
                                               @PathVariable String assetId) {
        MapAcademicClient.Download download = maps.asset(buildingId, floorId, version, format, assetId);
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.parseMediaType(download.contentType()));
        headers.setContentLength(download.bytes().length);
        headers.setContentDisposition(ContentDisposition.inline()
                .filename("campus-map." + format.name(), StandardCharsets.UTF_8)
                .build());
        headers.setCacheControl(CacheControl.noStore());
        return new ResponseEntity<>(new ByteArrayResource(download.bytes()), headers, HttpStatus.OK);
    }

    @PostMapping("/buildings/{buildingId}/floors/{floorId}/opens")
    public ResponseEntity<Void> recordFloorOpen(@PathVariable String buildingId,
                                                @PathVariable String floorId,
                                                @RequestHeader(name = "Idempotency-Key") UUID intentId) {
        maps.recordFloorOpen(buildingId, floorId, intentId);
        return ResponseEntity.noContent().build();
    }
}
