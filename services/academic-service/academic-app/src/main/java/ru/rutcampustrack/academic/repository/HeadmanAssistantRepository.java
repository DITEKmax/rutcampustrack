package ru.rutcampustrack.academic.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import ru.rutcampustrack.academic.entity.HeadmanAssistant;
import java.util.List;
import java.util.Optional;

public interface HeadmanAssistantRepository extends JpaRepository<HeadmanAssistant, Long> {
    List<HeadmanAssistant> findByGroupIdAndIsActiveTrue(Long groupId);
    Optional<HeadmanAssistant> findByGroupIdAndStudentId(Long groupId, Long studentId);

    /** Revoke all assistants in a group — used when headman is revoked (USER-04) */
    @Modifying
    @Query("UPDATE HeadmanAssistant h SET h.isActive = false, h.revokedAt = CURRENT_TIMESTAMP WHERE h.groupId = :groupId AND h.isActive = true")
    int revokeAllByGroupId(@Param("groupId") Long groupId);
}
