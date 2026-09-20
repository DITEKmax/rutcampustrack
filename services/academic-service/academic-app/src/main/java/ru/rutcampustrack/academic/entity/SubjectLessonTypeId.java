package ru.rutcampustrack.academic.entity;

import java.io.Serializable;
import java.util.Objects;
import org.hibernate.annotations.Type;
import ru.rutcampustrack.academic.config.SubjectTypeUserType;
import ru.rutcampustrack.academic.contract.enums.SubjectType;

/** Composite identity for one canonical lesson type of a subject. */
public class SubjectLessonTypeId implements Serializable {

    private Long subjectId;

    @Type(SubjectTypeUserType.class)
    private SubjectType lessonType;

    public SubjectLessonTypeId() {
    }

    public SubjectLessonTypeId(Long subjectId,
                               SubjectType lessonType) {
        this.subjectId = subjectId;
        this.lessonType = lessonType;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof SubjectLessonTypeId that)) {
            return false;
        }
        return Objects.equals(subjectId, that.subjectId)
                && lessonType == that.lessonType;
    }

    @Override
    public int hashCode() {
        return Objects.hash(subjectId, lessonType);
    }
}
