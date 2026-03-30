package ru.rutcampustrack.academic.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import ru.rutcampustrack.academic.entity.Semester;
import java.util.Optional;

public interface SemesterRepository extends JpaRepository<Semester, Long> {
    Optional<Semester> findByIsActiveTrue();

    @Modifying
    @Query("UPDATE Semester s SET s.isActive = false WHERE s.isActive = true")
    int deactivateAllActive();
}
