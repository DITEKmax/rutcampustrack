package ru.rutcampustrack.academic.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import ru.rutcampustrack.academic.contract.enums.SubjectType;
import ru.rutcampustrack.academic.entity.SubjectLessonType;
import ru.rutcampustrack.academic.entity.SubjectLessonTypeId;

import java.util.List;

public interface SubjectLessonTypeRepository extends JpaRepository<SubjectLessonType, SubjectLessonTypeId> {

    List<SubjectLessonType> findBySubjectId(Long subjectId);

    boolean existsBySubjectIdAndLessonType(Long subjectId, SubjectType lessonType);

    long countBySubjectId(Long subjectId);

    long deleteBySubjectId(Long subjectId);
}
