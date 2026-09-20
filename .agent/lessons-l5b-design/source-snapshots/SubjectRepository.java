package ru.rutcampustrack.academic.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Lock;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import ru.rutcampustrack.academic.contract.enums.SubjectType;
import ru.rutcampustrack.academic.entity.Subject;

import java.time.LocalDate;
import java.util.List;

public interface SubjectRepository extends JpaRepository<Subject, Long> {
    List<Subject> findByType(SubjectType type);

    List<Subject> findByNameContainingIgnoreCase(String name);

    /** Phase 60-01: предметы конкретной группы. */
    Page<Subject> findByGroupId(Long groupId, Pageable pageable);

    @Query("""
            select s
            from Subject s
            where s.id in (
                select assignment.subjectId
                from Assignment assignment
                where assignment.teacherId = :teacherId
                  and assignment.semesterId = :semesterId
                  and assignment.validFrom <= :today
                  and (assignment.validUntilExclusive is null or assignment.validUntilExclusive > :today)
            )
            """)
    Page<Subject> findAssignedToTeacher(@Param("teacherId") Long teacherId,
                                        @Param("semesterId") Long semesterId,
                                        @Param("today") LocalDate today,
                                        Pageable pageable);

    List<Subject> findByGroupId(Long groupId);

    boolean existsByIdAndGroupId(Long id, Long groupId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select s from Subject s where s.id = :id")
    java.util.Optional<Subject> findByIdForUpdate(@Param("id") Long id);
}
