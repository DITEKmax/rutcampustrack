package ru.rutcampustrack.academic.contract.dto.group;

import io.swagger.v3.oas.annotations.media.Schema;

/** Candidate from the server-owned active student roster. */
@Schema(description = "Кандидат в старосты")
public record HeadmanCandidateResponse(
        Long id,
        String fio,
        boolean current
) {}
