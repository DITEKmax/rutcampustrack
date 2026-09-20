package ru.rutcampustrack.mobilebff.contract.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;
import java.util.Objects;

public final class StudentMapModels {

    private static final long MAX_ASSET_BYTES = 10L * 1024L * 1024L;

    private StudentMapModels() {
    }

    public enum Format {
        png,
        svg
    }

    public enum FormatState {
        absent,
        processing,
        ready,
        failed
    }

    @Schema(name = "StudentMapManifest", requiredProperties = {
            "schemaVersion", "validationPolicyVersion", "revision", "buildings"
    })
    public record ManifestResponse(
            int schemaVersion,
            int validationPolicyVersion,
            String revision,
            List<Building> buildings
    ) {
        public ManifestResponse {
            if (schemaVersion <= 0 || validationPolicyVersion <= 0) {
                throw new IllegalArgumentException("map schema versions must be positive");
            }
            requirePositiveDecimal(revision, "revision");
            buildings = List.copyOf(Objects.requireNonNull(buildings, "buildings"));
        }
    }

    @Schema(name = "StudentMapBuilding", requiredProperties = {"id", "label", "floors"})
    public record Building(
            String id,
            String label,
            List<Floor> floors
    ) {
        public Building {
            requirePositiveDecimal(id, "id");
            Objects.requireNonNull(label, "label");
            floors = List.copyOf(Objects.requireNonNull(floors, "floors"));
        }
    }

    @Schema(name = "StudentMapFloor", requiredProperties = {"id", "label", "plan"})
    public record Floor(
            String id,
            String label,
            @Schema(nullable = true) Plan plan
    ) {
        public Floor {
            requirePositiveDecimal(id, "id");
            Objects.requireNonNull(label, "label");
        }
    }

    @Schema(name = "StudentMapPlan", requiredProperties = {
            "buildingId", "floorId", "version", "label", "png", "svg"
    })
    public record Plan(
            String buildingId,
            String floorId,
            String version,
            String label,
            FormatSlot png,
            FormatSlot svg
    ) {
        public Plan {
            requirePositiveDecimal(buildingId, "buildingId");
            requirePositiveDecimal(floorId, "floorId");
            requirePositiveDecimal(version, "version");
            Objects.requireNonNull(label, "label");
            Objects.requireNonNull(png, "png");
            Objects.requireNonNull(svg, "svg");
            if (png.format() != Format.png) {
                throw new IllegalArgumentException("png slot must use png format");
            }
            if (svg.format() != Format.svg) {
                throw new IllegalArgumentException("svg slot must use svg format");
            }
        }
    }

    @Schema(name = "StudentMapFormatSlot", requiredProperties = {
            "format", "state", "contentType", "id", "bytes", "sha256"
    })
    public record FormatSlot(
            Format format,
            FormatState state,
            String contentType,
            @Schema(nullable = true) String id,
            long bytes,
            @Schema(nullable = true) String sha256,
            @Schema(nullable = true) @JsonInclude(JsonInclude.Include.NON_NULL) Integer width,
            @Schema(nullable = true) @JsonInclude(JsonInclude.Include.NON_NULL) Integer height,
            @Schema(nullable = true) @JsonInclude(JsonInclude.Include.NON_NULL) List<Double> viewBox
    ) {
        public FormatSlot {
            Objects.requireNonNull(format, "format");
            Objects.requireNonNull(state, "state");
            Objects.requireNonNull(contentType, "contentType");
            String expectedContentType = format == Format.png ? "image/png" : "image/svg+xml";
            if (!expectedContentType.equals(contentType)) {
                throw new IllegalArgumentException("contentType does not match format");
            }
            if (bytes < 0 || bytes > MAX_ASSET_BYTES) {
                throw new IllegalArgumentException("bytes must be between 0 and 10 MiB");
            }
            if (state == FormatState.ready) {
                if (id == null || sha256 == null || bytes <= 0) {
                    throw new IllegalArgumentException("ready format requires id, sha256 and positive bytes");
                }
                requirePositiveDecimal(id, "id");
                if (!sha256.matches("[0-9a-f]{64}")) {
                    throw new IllegalArgumentException("ready format requires a lowercase SHA-256 value");
                }
            } else if (id != null || sha256 != null || bytes != 0) {
                throw new IllegalArgumentException("non-ready format requires null id, null sha256 and zero bytes");
            }
            if (width != null && width <= 0 || height != null && height <= 0) {
                throw new IllegalArgumentException("map dimensions must be positive");
            }
            if (viewBox != null) {
                if (viewBox.size() != 4 || viewBox.stream().anyMatch(Objects::isNull)) {
                    throw new IllegalArgumentException("viewBox must contain four values");
                }
                viewBox = List.copyOf(viewBox);
            }
        }
    }

    @Schema(name = "StudentMapPlanResponse", requiredProperties = {"plan"})
    public record PlanResponse(@Schema(nullable = true) Plan plan) {
    }

    private static void requirePositiveDecimal(String value, String name) {
        Objects.requireNonNull(value, name);
        if (!value.matches("[1-9][0-9]*")) {
            throw new IllegalArgumentException(name + " must be a positive decimal string");
        }
    }
}

