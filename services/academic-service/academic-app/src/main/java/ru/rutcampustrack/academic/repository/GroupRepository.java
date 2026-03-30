package ru.rutcampustrack.academic.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import ru.rutcampustrack.academic.entity.Group;
import java.util.List;
import java.util.Optional;

public interface GroupRepository extends JpaRepository<Group, Long> {
    List<Group> findByIsActive(boolean isActive);
    Page<Group> findByIsActive(boolean isActive, Pageable pageable);
    Optional<Group> findByCode(String code);
    boolean existsByCode(String code);
}
