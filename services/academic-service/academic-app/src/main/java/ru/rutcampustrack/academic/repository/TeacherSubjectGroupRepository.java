package ru.rutcampustrack.academic.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import ru.rutcampustrack.academic.entity.TeacherSubjectGroup;
import java.util.List;
import java.util.Optional;

public interface TeacherSubjectGroupRepository extends JpaRepository<TeacherSubjectGroup, Long> {
    List<TeacherSubjectGroup> findByGroupIdAndSemesterId(Long groupId, Long semesterId);
    List<TeacherSubjectGroup> findByTeacherIdAndSemesterId(Long teacherId, Long semesterId);
    Optional<TeacherSubjectGroup> findByTeacherIdAndSubjectIdAndGroupIdAndSemesterId(
        Long teacherId, Long subjectId, Long groupId, Long semesterId);
    void deleteByTeacherIdAndSubjectIdAndGroupIdAndSemesterId(
        Long teacherId, Long subjectId, Long groupId, Long semesterId);
    long deleteBySubjectId(Long subjectId);
}
