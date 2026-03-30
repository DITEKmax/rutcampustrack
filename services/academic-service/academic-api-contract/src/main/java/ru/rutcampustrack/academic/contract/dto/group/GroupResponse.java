package ru.rutcampustrack.academic.contract.dto.group;

import org.springframework.hateoas.RepresentationModel;

import java.time.OffsetDateTime;

public class GroupResponse extends RepresentationModel<GroupResponse> {

    private Long id;
    private String name;
    private String code;
    private boolean active;
    private OffsetDateTime createdAt;

    public GroupResponse() {}

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getCode() { return code; }
    public void setCode(String code) { this.code = code; }

    public boolean isActive() { return active; }
    public void setActive(boolean active) { this.active = active; }

    public OffsetDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(OffsetDateTime createdAt) { this.createdAt = createdAt; }
}
