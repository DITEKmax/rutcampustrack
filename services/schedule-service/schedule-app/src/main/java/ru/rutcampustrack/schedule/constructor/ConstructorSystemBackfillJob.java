package ru.rutcampustrack.schedule.constructor;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Set;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/** Explicit deployment manifest only; disabled by default and never a generic constructor sweep. */
@Component
@ConditionalOnProperty(prefix="constructor.materialization.system-backfill",name="enabled",havingValue="true")
public class ConstructorSystemBackfillJob {
    private final SemesterMaterializationCoordinator coordinator;
    private final ObjectMapper json;
    private final String manifestPath;

    public ConstructorSystemBackfillJob(SemesterMaterializationCoordinator coordinator,ObjectMapper json,
            @Value("${constructor.materialization.system-backfill.manifest-path:}") String manifestPath) {
        this.coordinator=coordinator;this.json=json;this.manifestPath=manifestPath;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void acceptManifest() throws Exception {
        Path path=Path.of(manifestPath);
        if (manifestPath.isBlank() || !Files.isRegularFile(path,LinkOption.NOFOLLOW_LINKS) || Files.size(path)>8192)
            throw new IllegalArgumentException("A bounded readonly system backfill manifest is required");
        var manifest=json.readTree(Files.readAllBytes(path));
        var fields=new java.util.HashSet<String>();manifest.fieldNames().forEachRemaining(fields::add);
        if (!fields.equals(Set.of("operationId","groupId","semesterId","itemIds"))
                || !manifest.path("groupId").isIntegralNumber() || !manifest.path("semesterId").isIntegralNumber()
                || !manifest.path("groupId").canConvertToLong() || !manifest.path("semesterId").canConvertToLong()
                || !manifest.path("itemIds").isArray() || manifest.path("itemIds").size()>128)
            throw new IllegalArgumentException("Invalid system backfill manifest");
        var items=new ArrayList<Long>();
        for (var id:manifest.path("itemIds")) {
            if (!id.isIntegralNumber() || !id.canConvertToLong()) throw new IllegalArgumentException("Invalid system backfill item");
            items.add(id.longValue());
        }
        coordinator.materializeSystemBackfill(UUID.fromString(manifest.path("operationId").asText()),
                manifest.path("semesterId").longValue(),manifest.path("groupId").longValue(),items);
    }
}
