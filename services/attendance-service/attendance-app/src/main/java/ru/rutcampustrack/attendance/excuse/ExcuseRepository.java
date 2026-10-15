package ru.rutcampustrack.attendance.excuse;

import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.data.mongodb.repository.Aggregation;
import ru.rutcampustrack.attendance.contract.enums.ExcuseTicketStatus;
import ru.rutcampustrack.attendance.excuse.entity.ExcuseTicket;

/**
 * Spring Data MongoDB repository for excuse tickets (Phase 59, Wave 1). Query methods cover
 * duplicate detection (D-11), student self-view (D-05), and headman group view (D-06).
 */
public interface ExcuseRepository extends MongoRepository<ExcuseTicket, String> {

    /** Submitted identities, not covered lessons or approved attendance cells. */
    @Aggregation(
            pipeline = {
                "{ '$match': { 'group_id': ?0, 'semester_id': ?1, 'status': { '$in': ['submitted', 'approved', 'rejected', 'cancelled'] } } }",
                "{ '$group': { '_id': '$excuse_type', 'count': { '$sum': 1 } } }",
                "{ '$project': { '_id': 0, 'code': { '$toUpper': '$_id' }, 'count': 1 } }",
                "{ '$limit': 10 }"
            })
    List<SubmittedReasonCount> submittedReasonCounts(long groupId, long semesterId);

    interface SubmittedReasonCount {
        String getCode();

        Long getCount();
    }

    /** Read-side lookup for roster pending/approved ticket indicators. */
    List<ExcuseTicket> findByLessonIdsInAndStatusIn(
            List<Long> lessonIds, List<ExcuseTicketStatus> statuses);

    Page<ExcuseTicket> findByGroupIdAndSemesterIdAndStudentIdAndLessonIdsInAndStatusIn(
            Long groupId,
            Long semesterId,
            Long studentId,
            List<Long> lessonIds,
            List<ExcuseTicketStatus> statuses,
            Pageable pageable);

    /**
     * D-11 duplicate check: does this student already have an active ticket (status in {SUBMITTED,
     * APPROVED}) containing any of these lessonIds?
     */
    boolean existsByStudentIdAndLessonIdsInAndStatusIn(
            Long studentId, List<Long> lessonIds, List<ExcuseTicketStatus> statuses);

    /** D-05: list student's own tickets, sorted via Pageable. */
    Page<ExcuseTicket> findByStudentId(Long studentId, Pageable pageable);

    /** D-05 (filtered): student's tickets filtered by status. */
    Page<ExcuseTicket> findByStudentIdAndStatus(
            Long studentId, ExcuseTicketStatus status, Pageable pageable);

    /** D-06: headman lists all group tickets. */
    Page<ExcuseTicket> findByGroupId(Long groupId, Pageable pageable);

    /** D-06 (filtered): headman lists group tickets by status. */
    Page<ExcuseTicket> findByGroupIdAndStatus(
            Long groupId, ExcuseTicketStatus status, Pageable pageable);
}
