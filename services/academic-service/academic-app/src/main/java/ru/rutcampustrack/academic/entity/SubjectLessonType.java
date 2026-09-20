package ru.rutcampustrack.academic.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;
import org.hibernate.annotations.Type;
import ru.rutcampustrack.academic.config.SubjectTypeUserType;
import lombok.Getter;
import lombok.NoArgsConstructor;
import ru.rutcampustrack.academic.contract.enums.SubjectType;

/** Canonical subject lesson type row from V25. */
@Entity
@Table(name = "subject_lesson_types")
@IdClass(SubjectLessonTypeId.class)
@Getter
@NoArgsConstructor
public class SubjectLessonType {

    @Id
    @Column(name = "subject_id", nullable = false)
    private Long subjectId;

    @Id
    @Type(SubjectTypeUserType.class)
    @Column(name = "lesson_type", nullable = false, columnDefinition = "subject_type")
    private SubjectType lessonType;

    public SubjectLessonType(Long subjectId, SubjectType lessonType) {
        this.subjectId = subjectId;
        this.lessonType = lessonType;
    }
}
