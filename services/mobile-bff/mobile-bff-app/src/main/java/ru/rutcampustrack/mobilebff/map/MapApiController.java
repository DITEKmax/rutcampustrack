package ru.rutcampustrack.mobilebff.map;

import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.Resource;
import org.springframework.http.CacheControl;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;
import ru.rutcampustrack.mobilebff.contract.api.MapApi;
import ru.rutcampustrack.mobilebff.contract.model.StudentMapModels;
import ru.rutcampustrack.mobilebff.contract.model.StudentMapModels.Format;
import ru.rutcampustrack.mobilebff.contract.model.StudentMapModels.ManifestResponse;
import ru.rutcampustrack.mobilebff.contract.model.StudentMapModels.PlanResponse;
import ru.rutcampustrack.mobilebff.grpc.MapAcademicClient;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

/** Canonical map REST adapter shared by all supported mobile roles. */
@RestController
public class MapApiController implements MapApi {
    private final MapQueryFacade maps;

    public MapApiController(MapQueryFacade maps) {
        this.maps = maps;
    }

    @Override
    public ResponseEntity<ManifestResponse> getManifest(String ifNoneMatch) {
        MapAcademicClient.ManifestResult result = maps.manifest(ifNoneMatch);
        String etag = etag(result.revision());
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

    @Override
    public ResponseEntity<PlanResponse> getFloorPlan(String buildingId, String floorId) {
        PlanResponse response = maps.floorPlan(buildingId, floorId);
        if (response.plan() == null) {
            return ResponseEntity.status(HttpStatus.NO_CONTENT)
                    .cacheControl(CacheControl.noStore())
                    .build();
        }
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(response);
    }

    @Override
    public ResponseEntity<Resource> readAsset(String buildingId,
                                               String floorId,
                                               String version,
                                               Format format,
                                               String assetId) {
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

    @Override
    public ResponseEntity<Void> recordFloorOpen(String buildingId,
                                                String floorId,
                                                UUID intentId) {
        maps.recordFloorOpen(buildingId, floorId, intentId);
        return ResponseEntity.noContent().build();
    }

    private static String etag(long revision) {
        return "\"" + revision + "\"";
    }
}
