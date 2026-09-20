package ru.rutcampustrack.academic.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;
import java.time.OffsetDateTime;

/**
 * Explicit provenance for the part of a group's student history that is
 * managed by the current enrollment writer.
 *
 * <p>The marker is deliberately group scoped.  An empty legacy group does not
 * become complete merely because no history rows happen to exist.</p>
 */
@Entity
@Table(name = "group_history_coverage")
@Getter
@NoArgsConstructor
public class GroupHistoryCoverage {

    @Id
    @Column(name = "group_id")
    private Long groupId;

    @Setter
    @Column(name = "coverage_from", nullable = false)
    private LocalDate coverageFrom;

    @Setter
    @Column(name = "writer_version", nullable = false, length = 32)
    private String writerVersion;

    @Setter
    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    public GroupHistoryCoverage(Long groupId, LocalDate coverageFrom,
                                String writerVersion, OffsetDateTime createdAt) {
        this.groupId = groupId;
        this.coverageFrom = coverageFrom;
        this.writerVersion = writerVersion;
        this.createdAt = createdAt;
    }
}
