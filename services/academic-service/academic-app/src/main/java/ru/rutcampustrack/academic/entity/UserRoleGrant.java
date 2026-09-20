package ru.rutcampustrack.academic.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.OffsetDateTime;

/** V24 durable role grant read model owned by auth-service. */
@Entity
@Table(name = "user_role_grants")
@Getter
@NoArgsConstructor
public class UserRoleGrant {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

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
