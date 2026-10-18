package ru.rutcampustrack.academic.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.OffsetDateTime;

/** NONAUTHORITATIVE directory projection for registry filters and display; never admission. */
@Entity
@Table(name = "academic_role_directory")
@Getter
@NoArgsConstructor
public class UserRoleGrant {

    @Id private Long id;

    @Column(name = "user_id", nullable = false, updatable = false)
    private Long userId;

    @Column(name = "role", nullable = false, updatable = false)
    private String role;

    @Column(name = "status", nullable = false)
    private String status;

    @Column(name = "group_id")
    private Long groupId;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;
}
