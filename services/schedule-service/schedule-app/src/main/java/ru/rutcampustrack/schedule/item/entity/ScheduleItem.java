package ru.rutcampustrack.schedule.item.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import ru.rutcampustrack.schedule.contract.enums.WeekType;

import java.time.LocalTime;
import java.time.OffsetDateTime;

/**
 * JPA entity for the schedule_items table.
 * Represents a recurring weekly schedule template (e.g., "Group 1A has Math every Monday at 08:30, odd weeks").
 *
 * Column mapping notes:
 * - start_time / end_time: TIME (no timezone) -> LocalTime. Timezone context comes from
 *   hibernate.jdbc.time_zone=Europe/Moscow in application.yml (CRON-04).
 * - week_type: PostgreSQL custom enum -> WeekType via EnumConverters.WeekTypeConverter (autoApply=true).
 *   Do NOT add @Convert — autoApply handles it automatically.
 * - No @ManyToOne associations — FKs are stored as Long IDs (project-wide convention).
 */
@Entity
@Table(name = "schedule_items")
@Getter
@NoArgsConstructor
public class ScheduleItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Setter
    @Column(name = "group_id", nullable = false)
    private Long groupId;

    @Setter
    @Column(name = "subject_id", nullable = false)
    private Long subjectId;

    // D-16: teacherId удалён. Препод видит журнал через JOIN с TeacherSubjectGroup
    // (ScheduleItem × TSG WHERE TSG.teacher_id = :me). На слоте преподавателя нет.

    @Setter
    @Column(name = "semester_id", nullable = false)
    private Long semesterId;

    @Setter
    @Column(name = "day_of_week", nullable = false)
    private Short dayOfWeek;

    @Setter
    @Column(name = "lesson_number", nullable = false)
    private Short lessonNumber;

    @Setter
    @Column(name = "start_time", nullable = false)
    private LocalTime startTime;

    @Setter
    @Column(name = "end_time", nullable = false)
    private LocalTime endTime;

    @Setter
    @Column(name = "week_type", nullable = false)
    private WeekType weekType;

    @Setter
    @Column(length = 64)
    private String room;

    @Setter
    @Column(name = "is_active", nullable = false)
    private boolean isActive = true;

    @Setter
    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;
}
