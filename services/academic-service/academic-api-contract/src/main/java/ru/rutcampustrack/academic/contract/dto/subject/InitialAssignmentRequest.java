package ru.rutcampustrack.academic.contract.dto.subject;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import ru.rutcampustrack.academic.contract.enums.SubjectType;

import java.time.LocalDate;

/** Assignment created atomically together with a new subject. */
@Schema(name = "InitialAssignmentRequest")
public record InitialAssignmentRequest(

        @NotNull @Positive Long teacherId,
        @NotNull @Positive Long semesterId,
        @NotNull SubjectType lessonType,
        @NotNull LocalDate validFrom,
        @Schema(nullable = true) LocalDate validUntilExclusive
) {
}
