package ru.rutcampustrack.academic.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import ru.rutcampustrack.academic.entity.Group;
import jakarta.persistence.LockModeType;
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
    boolean existsByAlphabeticCodeAndNumericCodeAndIsActiveTrue(String alphabeticCode,
                                                                String numericCode);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select g from Group g where g.id = :id")
    Optional<Group> findByIdForUpdate(@Param("id") Long id);

    /**
     * Restore only the archived row markers after the caller validates the
     * exact reversible suffix. Native SQL also permits old display names that
     * predate the current entity's stricter name validation.
     */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query(value = "UPDATE groups SET name = :name, is_active = true, archived_at = NULL "
            + "WHERE id = :id AND is_active = false", nativeQuery = true)
    int restoreArchivedGroup(@Param("id") Long id, @Param("name") String name);

    /** Все активные группы — используется {@code GroupPromotionService} для planning. */
    List<Group> findAllByIsActiveTrue();

    /** Lock active groups in a stable order while a confirmed promotion plan is applied. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select g from Group g where g.isActive = true order by g.id")
    List<Group> findAllActiveForPromotionUpdate();
}
