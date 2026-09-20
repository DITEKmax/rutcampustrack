package ru.rutcampustrack.academic.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import ru.rutcampustrack.academic.entity.UserRoleGrant;

import java.util.List;

public interface UserRoleGrantRepository extends JpaRepository<UserRoleGrant, Long> {

    List<UserRoleGrant> findByUserIdAndRoleAndStatus(Long userId, String role, String status);
}
