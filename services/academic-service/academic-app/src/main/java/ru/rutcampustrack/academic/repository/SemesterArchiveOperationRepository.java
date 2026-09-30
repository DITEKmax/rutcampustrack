package ru.rutcampustrack.academic.repository;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import ru.rutcampustrack.academic.entity.SemesterArchiveOperation;

import java.util.Optional;
import java.util.List;
import java.util.UUID;

public interface SemesterArchiveOperationRepository extends JpaRepository<SemesterArchiveOperation, UUID> {

    Optional<SemesterArchiveOperation> findByIdempotencyKey(UUID idempotencyKey);

    Optional<SemesterArchiveOperation> findTopBySemesterIdOrderByCreatedAtDescOperationIdDesc(long semesterId);

    List<SemesterArchiveOperation> findTop8ByRetryableTrueAndOperationStateInOrderByUpdatedAtAsc(
            List<ru.rutcampustrack.academic.contract.enums.SemesterArchiveOperationState> states);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select o from SemesterArchiveOperation o where o.operationId = :id")
    Optional<SemesterArchiveOperation> findByIdForUpdate(@Param("id") UUID operationId);
}
