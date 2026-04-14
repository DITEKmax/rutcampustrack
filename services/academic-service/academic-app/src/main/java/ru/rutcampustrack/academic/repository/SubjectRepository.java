package ru.rutcampustrack.academic.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import ru.rutcampustrack.academic.contract.enums.SubjectType;
import ru.rutcampustrack.academic.entity.Subject;

import java.util.List;

public interface SubjectRepository extends JpaRepository<Subject, Long> {
    List<Subject> findByType(SubjectType type);

    List<Subject> findByNameContainingIgnoreCase(String name);

    /** Phase 60-01: предметы конкретной группы. */
    Page<Subject> findByGroupId(Long groupId, Pageable pageable);

    List<Subject> findByGroupId(Long groupId);

    boolean existsByIdAndGroupId(Long id, Long groupId);
}
