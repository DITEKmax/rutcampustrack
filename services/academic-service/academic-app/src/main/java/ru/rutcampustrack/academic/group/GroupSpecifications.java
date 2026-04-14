package ru.rutcampustrack.academic.group;

import jakarta.persistence.criteria.Predicate;
import org.springframework.data.jpa.domain.Specification;
import ru.rutcampustrack.academic.contract.dto.group.GroupStatus;
import ru.rutcampustrack.academic.entity.Group;

import java.util.ArrayList;
import java.util.List;

/**
 * JPA Specification helpers для {@link Group}.
 *
 * <p>BUG-006-6 / план 58-06: комбинированный фильтр {@code status} + {@code search} ILIKE.
 */
public final class GroupSpecifications {

    private GroupSpecifications() {}

    public static Specification<Group> statusAndSearch(GroupStatus status, String search) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (status == null || status == GroupStatus.ACTIVE) {
                predicates.add(cb.isTrue(root.get("isActive")));
            } else if (status == GroupStatus.ARCHIVED) {
                predicates.add(cb.isFalse(root.get("isActive")));
            }
            if (search != null && !search.isBlank()) {
                String pattern = "%" + search.trim().toLowerCase() + "%";
                predicates.add(cb.like(cb.lower(root.get("name")), pattern));
            }
            if (predicates.isEmpty()) {
                return cb.conjunction();
            }
            return cb.and(predicates.toArray(new Predicate[0]));
        };
    }
}
