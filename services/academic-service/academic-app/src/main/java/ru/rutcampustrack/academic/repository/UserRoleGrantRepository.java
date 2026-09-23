package ru.rutcampustrack.academic.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import ru.rutcampustrack.academic.entity.UserRoleGrant;

import java.util.List;

public interface UserRoleGrantRepository extends JpaRepository<UserRoleGrant, Long> {

    List<UserRoleGrant> findByUserIdAndRoleAndStatus(Long userId, String role, String status);

    @Query("select count(distinct roleGrant.userId) from UserRoleGrant roleGrant "
            + "where roleGrant.role = :role and roleGrant.status = :status")
    long countDistinctUsersByRoleAndStatus(@Param("role") String role, @Param("status") String status);
}
