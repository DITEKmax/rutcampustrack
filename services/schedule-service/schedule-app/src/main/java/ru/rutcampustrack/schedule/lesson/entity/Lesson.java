package ru.rutcampustrack.schedule.lesson.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import ru.rutcampustrack.schedule.contract.enums.LessonStatus;

import java.time.LocalDate;
import java.time.OffsetDateTime;

/**
 * JPA entity for the lessons table.
 * A concrete lesson occurrence on a specific date, derived from a ScheduleItem.
 *
 * Column mapping notes:
 * - status: PostgreSQL lesson_status custom enum -> LessonStatus via EnumConverters.LessonStatusConverter (autoApply=true).
 * - date: DATE -> LocalDate (no timezone needed — calendar date only).
 * - closed_at: nullable TIMESTAMPTZ -> OffsetDateTime (null until lesson closes).
 * - UNIQUE (schedule_item_id, date): the idempotency anchor for LSSN-03 — insert ON CONFLICT DO NOTHING
 *   prevents duplicate lessons when generation is retried.
 * - No @ManyToOne — scheduleItemId stored as Long per project convention.
 */
@Entity
@Table(name = "lessons")
@Getter
@NoArgsConstructor
public class Lesson {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Setter
    @Column(name = "schedule_item_id", nullable = false)
    private Long scheduleItemId;

    @Setter
    @Column(nullable = false)
    private LocalDate date;

    @Setter
    @Column(nullable = false)
    private LessonStatus status = LessonStatus.PLANNED;

    @Setter
    @Column(name = "is_geo_blocked", nullable = false)
    private boolean isGeoBlocked = false;

    @Setter
    @Column(name = "is_blocked_by_headman", nullable = false)
    private boolean isBlockedByHeadman = false;

    @Setter
    @Column(name = "blocked_by_user_id")
    private Long blockedByUserId;

    @Setter
    @Column(name = "blocked_at")
    private OffsetDateTime blockedAt;

    @Setter
    @Column(name = "cancel_reason", length = 512)
    private String cancelReason;

    @Setter
    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @Setter
    @Column(name = "closed_at")
    private OffsetDateTime closedAt;
}
