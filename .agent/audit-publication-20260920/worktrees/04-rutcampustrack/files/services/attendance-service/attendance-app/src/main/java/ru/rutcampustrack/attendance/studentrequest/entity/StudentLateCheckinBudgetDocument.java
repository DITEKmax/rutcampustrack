package ru.rutcampustrack.attendance.studentrequest.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.Field;

import java.time.Instant;

/** Atomic per-student/per-semester manual late-checkin budget. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "student_late_checkin_budgets")
public class StudentLateCheckinBudgetDocument {

    @Id
    private String id;
    @Field("student_id")
    private Long studentId;
    @Field("semester_id")
    private Long semesterId;
    @Field("limit")
    private Integer limit;
    @Field("used")
    private Integer used;
    @Field("updated_at")
    private Instant updatedAt;
}
