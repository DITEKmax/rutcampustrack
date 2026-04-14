package ru.rutcampustrack.academic.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import ru.rutcampustrack.academic.entity.Group;
import java.util.List;
import java.util.Optional;

/**
 * Spring Data repository для {@link Group}.
 *
 * <p>BUG-006-6 / план 58-06: добавлен {@link JpaSpecificationExecutor} — для комбинированного
 * фильтра {@code status} + {@code search} ILIKE (см. GroupSpecifications).
 */
public interface GroupRepository extends JpaRepository<Group, Long>, JpaSpecificationExecutor<Group> {
    List<Group> findByIsActive(boolean isActive);
    Page<Group> findByIsActive(boolean isActive, Pageable pageable);
    long countByIsActive(boolean isActive);
    Optional<Group> findByName(String name);
    boolean existsByName(String name);

    /** Все активные группы — используется {@code GroupPromotionService} для planning. */
    List<Group> findAllByIsActiveTrue();
}
