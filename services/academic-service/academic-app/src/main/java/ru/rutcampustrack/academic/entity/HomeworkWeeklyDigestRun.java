package ru.rutcampustrack.academic.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.OffsetDateTime;

@Entity
@Table(name = "homework_weekly_digest_runs",
        uniqueConstraints = @UniqueConstraint(columnNames = {"group_id", "week_start"}))
@Getter
@NoArgsConstructor
public class HomeworkWeeklyDigestRun {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "group_id", nullable = false)
    private Long groupId;

    @Column(name = "semester_id", nullable = false)
    private Long semesterId;

    @Column(name = "week_start", nullable = false)
    private LocalDate weekStart;

    @Column(name = "sent_at", nullable = false)
    private OffsetDateTime sentAt;

    public HomeworkWeeklyDigestRun(Long groupId, Long semesterId, LocalDate weekStart, OffsetDateTime sentAt) {
        this.groupId = groupId;
        this.semesterId = semesterId;
        this.weekStart = weekStart;
        this.sentAt = sentAt;
    }
}
