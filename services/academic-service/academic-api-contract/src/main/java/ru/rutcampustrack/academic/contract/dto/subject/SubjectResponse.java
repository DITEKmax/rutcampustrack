package ru.rutcampustrack.academic.contract.dto.subject;

import org.springframework.hateoas.RepresentationModel;
import ru.rutcampustrack.academic.contract.enums.SubjectType;

import java.time.OffsetDateTime;

/**
 * Response DTO for a subject with HATEOAS links.
 */
public class SubjectResponse extends RepresentationModel<SubjectResponse> {

    private Long id;
    private String name;
    private SubjectType type;
    private OffsetDateTime createdAt;

    public SubjectResponse() {}

    public SubjectResponse(Long id, String name, SubjectType type, OffsetDateTime createdAt) {
        this.id = id;
        this.name = name;
        this.type = type;
        this.createdAt = createdAt;
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public SubjectType getType() { return type; }
    public void setType(SubjectType type) { this.type = type; }

    public OffsetDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(OffsetDateTime createdAt) { this.createdAt = createdAt; }
}
