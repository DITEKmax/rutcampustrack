package ru.rutcampustrack.academic.contract.dto.subject;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import ru.rutcampustrack.academic.contract.enums.SubjectType;

import java.time.LocalDate;

/** Complete immutable assignment identity for the subject teacher route. */
@Schema(name = "AddSubjectTeacherRequest")
public record AddSubjectTeacherRequest(

        @NotNull @Positive Long semesterId,
        @NotNull SubjectType lessonType,
        @NotNull LocalDate validFrom,
        @Schema(nullable = true) LocalDate validUntilExclusive
) {
}
