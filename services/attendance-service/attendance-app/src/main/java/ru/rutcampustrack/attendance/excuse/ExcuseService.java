package ru.rutcampustrack.attendance.excuse;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import ru.rutcampustrack.attendance.contract.dto.excuse.CreateExcuseRequest;
import ru.rutcampustrack.attendance.contract.dto.excuse.UpdateExcuseStatusRequest;
import ru.rutcampustrack.attendance.contract.enums.ExcuseTicketStatus;
import ru.rutcampustrack.attendance.contract.exception.ResourceNotFoundException;
import ru.rutcampustrack.attendance.exception.AccessDeniedException;
import ru.rutcampustrack.attendance.exception.BadRequestException;
import ru.rutcampustrack.attendance.exception.ConflictException;
import ru.rutcampustrack.attendance.excuse.entity.ExcuseTicket;
import ru.rutcampustrack.attendance.grpc.AcademicGrpcClient;
import ru.rutcampustrack.attendance.security.RequestContext;

import java.time.Instant;
import java.util.List;

/**
 * Business logic for excuse tickets (Phase 59, Wave 2).
 *
 * Implements decisions D-04..D-18 from 59-CONTEXT.md:
 * - D-10..D-11: duplicate detection (active ticket per lesson per student)
 * - D-12: headman does NOT create tickets — goes through normal markAttendance flow
 * - D-13: headman cannot approve/reject their own ticket
 * - D-14: read access restricted to owner or headman of same group
 * - D-18: status decision is final — no re-approval/re-rejection
 *
 * Cascade on approval (D-16) is delegated to plan 59-04 via AttendanceWritePort.
 * Event publishing (D-19, D-20) is delegated to plan 59-05 via ExcuseEventPublisher.
 * Both are injected as optional (required=false) so this service compiles in isolation.
 */
@Service
public class ExcuseService {

    private static final List<ExcuseTicketStatus> ACTIVE_STATUSES =
            List.of(ExcuseTicketStatus.SUBMITTED, ExcuseTicketStatus.APPROVED);

    private final ExcuseRepository excuseRepository;
    private final RequestContext requestContext;
    private final AcademicGrpcClient academicGrpcClient;

    public ExcuseService(ExcuseRepository excuseRepository,
                         RequestContext requestContext,
                         AcademicGrpcClient academicGrpcClient) {
        this.excuseRepository = excuseRepository;
        this.requestContext = requestContext;
        this.academicGrpcClient = academicGrpcClient;
    }

    /**
     * D-04 + D-10..D-12, D-15: STUDENT creates an excuse ticket for lessons in their own group.
     *
     * @throws ConflictException   headman attempted to create (D-12) or a lesson already has an active ticket (D-11)
     * @throws BadRequestException defensive validation guard (comment length, empty list) — primary validation lives in DTO
     */
    public ExcuseTicket createExcuse(CreateExcuseRequest request) {
        // D-12: headman does not submit tickets — they self-mark via the journal
        if (requestContext.isHeadman()) {
            throw new ConflictException(
                    "Старосты проставляют пропуски через журнал посещаемости, а не через тикеты");
        }

        // D-15 defensive guard (jakarta validation should already have caught these)
        if (request.lessonIds() == null || request.lessonIds().isEmpty()) {
            throw new BadRequestException("lessonIds не может быть пустым");
        }
        if (request.comment() != null && request.comment().length() > 1000) {
            throw new BadRequestException("Комментарий не должен превышать 1000 символов");
        }

        // D-11: duplicate lesson in an active ticket for this student
        boolean duplicate = excuseRepository.existsByStudentIdAndLessonIdsInAndStatusIn(
                requestContext.getUserId(), request.lessonIds(), ACTIVE_STATUSES);
        if (duplicate) {
            throw new ConflictException(
                    "На один из выбранных уроков уже существует активный тикет");
        }

        // D-26: snapshot studentName via gRPC
        String studentName = academicGrpcClient.getUserDisplayName(requestContext.getUserId());

        Instant now = Instant.now();
        ExcuseTicket ticket = ExcuseTicket.builder()
                .studentId(requestContext.getUserId())
                .groupId(requestContext.getGroupId())
                .studentName(studentName)
                .lessonIds(List.copyOf(request.lessonIds()))
                .excuseType(request.excuseType())
                .comment(request.comment())
                .status(ExcuseTicketStatus.SUBMITTED)
                .createdAt(now)
                .updatedAt(now)
                .build();

        return excuseRepository.save(ticket);
    }

    /**
     * D-05: STUDENT views their own tickets.
     * Optional status filter narrows to a single lifecycle bucket.
     */
    public Page<ExcuseTicket> getMyTickets(Pageable pageable, ExcuseTicketStatus status) {
        Long userId = requestContext.getUserId();
        if (status != null) {
            return excuseRepository.findByStudentIdAndStatus(userId, status, pageable);
        }
        return excuseRepository.findByStudentId(userId, pageable);
    }

    /**
     * D-06 + D-14: headman views tickets of THEIR OWN group. Non-headman or wrong group → 403.
     */
    public Page<ExcuseTicket> getGroupTickets(Long groupId, Pageable pageable, ExcuseTicketStatus status) {
        if (!requestContext.isHeadman()) {
            throw new AccessDeniedException("Только староста может просматривать тикеты группы");
        }
        if (!groupId.equals(requestContext.getGroupId())) {
            throw new AccessDeniedException("Нельзя просматривать тикеты чужой группы");
        }
        if (status != null) {
            return excuseRepository.findByGroupIdAndStatus(groupId, status, pageable);
        }
        return excuseRepository.findByGroupId(groupId, pageable);
    }

    /**
     * D-08 + D-14: detail view. Owner may always read. Headman may read tickets of their own group.
     * Everyone else → 403.
     */
    public ExcuseTicket getTicketById(String id) {
        ExcuseTicket ticket = excuseRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("ExcuseTicket", "id", id));

        boolean isOwner = ticket.getStudentId().equals(requestContext.getUserId());
        boolean isGroupHeadman = requestContext.isHeadman()
                && ticket.getGroupId().equals(requestContext.getGroupId());

        if (!isOwner && !isGroupHeadman) {
            throw new AccessDeniedException("Доступ к тикету запрещён");
        }
        return ticket;
    }

    /**
     * D-07 + D-13 + D-14 + D-18: headman approves/rejects a ticket.
     * <ul>
     *   <li>Caller must be headman of the ticket's group (D-07/D-14)</li>
     *   <li>Caller must not be the ticket author (D-13)</li>
     *   <li>Ticket must currently be SUBMITTED — otherwise decision is already final (D-18)</li>
     *   <li>Requested status must be APPROVED or REJECTED (D-07)</li>
     * </ul>
     *
     * Cascade on APPROVED (D-16) will be performed by plan 59-04 once AttendanceWritePort exists.
     */
    public ExcuseTicket updateStatus(String id, UpdateExcuseStatusRequest request) {
        ExcuseTicket ticket = excuseRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("ExcuseTicket", "id", id));

        // D-07: only APPROVED or REJECTED are meaningful here
        ExcuseTicketStatus newStatus = request.status();
        if (newStatus != ExcuseTicketStatus.APPROVED && newStatus != ExcuseTicketStatus.REJECTED) {
            throw new BadRequestException("Допустимые статусы: APPROVED или REJECTED");
        }

        // D-07/D-14: must be headman of the ticket's group
        if (!requestContext.isHeadman()) {
            throw new AccessDeniedException("Только староста может принимать решение по тикету");
        }
        if (!ticket.getGroupId().equals(requestContext.getGroupId())) {
            throw new AccessDeniedException("Нельзя принимать решение по тикету чужой группы");
        }

        // D-13: cannot self-approve/reject
        if (ticket.getStudentId().equals(requestContext.getUserId())) {
            throw new ConflictException("Нельзя одобрить собственный тикет");
        }

        // D-18: decision is final
        if (ticket.getStatus() != ExcuseTicketStatus.SUBMITTED) {
            throw new ConflictException("Решение по тикету уже принято");
        }

        Instant now = Instant.now();
        ticket.setStatus(newStatus);
        ticket.setDecisionBy(requestContext.getUserId());
        ticket.setDecisionComment(request.decisionComment());
        ticket.setDecisionAt(now);
        ticket.setUpdatedAt(now);

        return excuseRepository.save(ticket);
    }
}
