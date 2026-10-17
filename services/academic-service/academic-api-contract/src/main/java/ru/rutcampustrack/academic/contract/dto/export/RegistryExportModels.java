package ru.rutcampustrack.academic.contract.dto.export;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.List;
import ru.rutcampustrack.academic.contract.dto.group.*;
import ru.rutcampustrack.academic.contract.enums.AccountStatus;

public final class RegistryExportModels {
    private RegistryExportModels() {}

    public record SortOrder(@NotBlank String field, boolean descending) {}

    public record Groups(
            @JsonDeserialize(using = StrictRegistryEnumDeserializer.class) AdminGroupStatus status,
            @Size(max = 255) String search,
            @Min(1) @Max(5) Integer course,
            @JsonDeserialize(using = StrictRegistryEnumDeserializer.class) StudyMode studyMode,
            @JsonDeserialize(using = StrictRegistryEnumDeserializer.class)
                    EducationLevel educationLevel,
            @Size(max = 8) List<@NotNull @Valid SortOrder> sorts,
            @NotBlank String format) {}

    public record Users(
            @Size(max = 255) String search,
            @Pattern(regexp = "ADMIN|TEACHER|STUDENT|HEADMAN") String role,
            @JsonDeserialize(using = StrictRegistryEnumDeserializer.class) AccountStatus status,
            @Pattern(regexp = "ACTIVE|EXPELLED|GRADUATED|SUSPENDED|DISMISSED|ARCHIVED")
                    String roleStatus,
            @Size(max = 8) List<@NotNull @Valid SortOrder> sorts,
            @NotBlank String format) {}

    public record FormatOption(String code, String label, String contentType, String extension) {}
}
