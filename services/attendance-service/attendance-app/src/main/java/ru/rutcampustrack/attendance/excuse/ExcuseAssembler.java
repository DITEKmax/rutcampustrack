package ru.rutcampustrack.attendance.excuse;

import org.springframework.data.domain.Page;
import org.springframework.hateoas.EntityModel;
import org.springframework.hateoas.Link;
import org.springframework.hateoas.PagedModel;
import org.springframework.stereotype.Component;
import ru.rutcampustrack.attendance.contract.dto.excuse.ExcuseTicketResponse;
import ru.rutcampustrack.attendance.excuse.entity.ExcuseTicket;

import java.util.List;

import static org.springframework.hateoas.server.mvc.WebMvcLinkBuilder.linkTo;
import static org.springframework.hateoas.server.mvc.WebMvcLinkBuilder.methodOn;

/**
 * Maps {@link ExcuseTicket} entities into HATEOAS {@link ExcuseTicketResponse} models
 * (Phase 59-02). Status is serialised as lowercase to match Mongo storage and the
 * event payloads defined in D-19 / D-20.
 */
@Component
public class ExcuseAssembler {

    public ExcuseTicketResponse toResponse(ExcuseTicket ticket) {
        return new ExcuseTicketResponse(
                ticket.getId(),
                ticket.getStudentId(),
                ticket.getGroupId(),
                ticket.getStudentName(),
                ticket.getLessonIds(),
                ticket.getExcuseType(),
                ticket.getComment(),
                ticket.getStatus() == null ? null : ticket.getStatus().name().toLowerCase(),
                ticket.getDecisionBy(),
                ticket.getDecisionComment(),
                ticket.getDecisionAt(),
                ticket.getCreatedAt(),
                ticket.getUpdatedAt()
        );
    }

    public EntityModel<ExcuseTicketResponse> toModel(ExcuseTicket ticket) {
        ExcuseTicketResponse response = toResponse(ticket);
        Link self = linkTo(methodOn(ExcuseController.class).getTicketById(ticket.getId())).withSelfRel();
        return EntityModel.of(response, self);
    }

    public PagedModel<EntityModel<ExcuseTicketResponse>> toPagedModel(Page<ExcuseTicket> page) {
        List<EntityModel<ExcuseTicketResponse>> content = page.getContent().stream()
                .map(this::toModel)
                .toList();
        PagedModel.PageMetadata metadata = new PagedModel.PageMetadata(
                page.getSize(),
                page.getNumber(),
                page.getTotalElements(),
                page.getTotalPages()
        );
        return PagedModel.of(content, metadata);
    }
}
