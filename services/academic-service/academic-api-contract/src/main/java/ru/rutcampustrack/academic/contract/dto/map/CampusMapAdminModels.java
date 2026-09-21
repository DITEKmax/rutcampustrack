package ru.rutcampustrack.academic.contract.dto.map;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.util.List;
import java.util.Objects;

/** Wire models for the small, admin-owned campus map catalog API. */
public final class CampusMapAdminModels {
    private CampusMapAdminModels() {
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

    @Schema(name = "CampusMapCreateBuildingRequest")
    public record CreateBuildingRequest(
            @Schema(description = "Numeric campus building code", example = "20", requiredMode = Schema.RequiredMode.REQUIRED)
            @NotBlank(message = "Код корпуса обязателен")
            @Pattern(regexp = "[1-9][0-9]*", message = "Код корпуса должен быть положительным числом")
            String code,
            @Schema(description = "Human-readable label; defaults to the code")
            @Size(max = 255, message = "Название корпуса не должно превышать 255 символов")
            String label
    ) {
    }

    @Schema(name = "CampusMapCreateFloorRequest")
    public record CreateFloorRequest(
            @Schema(description = "Owning building id", example = "1", requiredMode = Schema.RequiredMode.REQUIRED)
            String buildingId,
            @Schema(description = "Numeric floor code", example = "2", requiredMode = Schema.RequiredMode.REQUIRED)
            @NotBlank(message = "Код этажа обязателен")
            @Pattern(regexp = "[1-9][0-9]*", message = "Код этажа должен быть положительным числом")
            String code,
            @Schema(description = "Human-readable label; defaults to the code")
            @Size(max = 255, message = "Название этажа не должно превышать 255 символов")
            String label
    ) {
    }

    @Schema(name = "CampusMapBuilding")
    public record BuildingResponse(
            String id,
            String code,
            String label,
            List<FloorResponse> floors
    ) {
        public BuildingResponse {
            floors = List.copyOf(Objects.requireNonNull(floors, "floors"));
        }
    }

    @Schema(name = "CampusMapFloor")
    public record FloorResponse(
            String id,
            String buildingId,
            String code,
            String label,
            PlanResponse currentPlan
    ) {
    }

    @Schema(name = "CampusMapPlan")
    public record PlanResponse(
            String buildingId,
            String floorId,
            String version,
            String label,
            String catalogRevision,
            FormatSlot png,
            FormatSlot svg
    ) {
        public PlanResponse {
            Objects.requireNonNull(png, "png");
            Objects.requireNonNull(svg, "svg");
        }
    }

    @Schema(name = "CampusMapFormatSlot")
    public record FormatSlot(
            Format format,
            FormatState state,
            String contentType,
            @JsonInclude(JsonInclude.Include.NON_NULL) String id,
            long bytes,
            @JsonInclude(JsonInclude.Include.NON_NULL) String sha256,
            @JsonInclude(JsonInclude.Include.NON_NULL) Integer width,
            @JsonInclude(JsonInclude.Include.NON_NULL) Integer height
    ) {
        public FormatSlot {
            Objects.requireNonNull(format, "format");
            Objects.requireNonNull(state, "state");
            Objects.requireNonNull(contentType, "contentType");
            if (bytes < 0 || bytes > 10L * 1024L * 1024L) {
                throw new IllegalArgumentException("map bytes must be between 0 and 10 MiB");
            }
            if (state == FormatState.ready && (id == null || sha256 == null || bytes == 0)) {
                throw new IllegalArgumentException("ready slot requires id, sha256 and positive bytes");
            }
            if (state != FormatState.ready && (id != null || sha256 != null || bytes != 0)) {
                throw new IllegalArgumentException("non-ready slot cannot expose asset metadata");
            }
        }
    }
}
