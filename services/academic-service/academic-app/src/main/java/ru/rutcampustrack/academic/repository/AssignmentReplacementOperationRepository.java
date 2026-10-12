package ru.rutcampustrack.academic.repository;

import jakarta.persistence.LockModeType;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import ru.rutcampustrack.academic.entity.AssignmentReplacementOperation;

public interface AssignmentReplacementOperationRepository
        extends JpaRepository<AssignmentReplacementOperation, UUID> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select o from AssignmentReplacementOperation o where o.operationId = :id")
    Optional<AssignmentReplacementOperation> findByIdForUpdate(@Param("id") UUID id);

    Optional<AssignmentReplacementOperation> findByActorIdAndRequestKey(
            Long actorId, UUID requestKey);

    java.util.List<AssignmentReplacementOperation>
            findBySourceAssignmentIdAndRequestKeyAndOperationKind(
                    Long sourceAssignmentId, UUID requestKey, String operationKind);

    Optional<AssignmentReplacementOperation> findFirstBySourceAssignmentIdAndStateIn(
            Long sourceAssignmentId, java.util.Collection<String> states);

    Optional<AssignmentReplacementOperation> findFirstByTargetAssignmentIdAndStateIn(
            Long targetAssignmentId, java.util.Collection<String> states);

    Optional<AssignmentReplacementOperation> findByTargetAssignmentIdAndOperationKind(
            Long targetAssignmentId, String operationKind);
}
