package ru.rutcampustrack.schedule.item;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PagedResourcesAssembler;
import org.springframework.hateoas.EntityModel;
import org.springframework.hateoas.PagedModel;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;
import ru.rutcampustrack.schedule.contract.api.ScheduleItemApi;
import ru.rutcampustrack.schedule.contract.dto.item.CreateScheduleItemRequest;
import ru.rutcampustrack.schedule.contract.dto.item.ScheduleItemResponse;
import ru.rutcampustrack.schedule.contract.dto.item.UpdateScheduleItemRequest;
import ru.rutcampustrack.schedule.contract.enums.UserRole;
import ru.rutcampustrack.schedule.item.entity.ScheduleItem;
import ru.rutcampustrack.schedule.security.RequireRole;

import static org.springframework.hateoas.server.mvc.WebMvcLinkBuilder.linkTo;
import static org.springframework.hateoas.server.mvc.WebMvcLinkBuilder.methodOn;

/**
 * REST controller for schedule template (ScheduleItem) management.
 * Implements ScheduleItemApi contract interface — HTTP mappings are defined in the interface only.
 * Delegates all business logic to ScheduleItemService.
 */
@RestController
public class ScheduleItemController implements ScheduleItemApi {

    private final ScheduleItemService scheduleItemService;
    private final ScheduleItemAssembler scheduleItemAssembler;

    public ScheduleItemController(ScheduleItemService scheduleItemService,
                                   ScheduleItemAssembler scheduleItemAssembler) {
        this.scheduleItemService = scheduleItemService;
        this.scheduleItemAssembler = scheduleItemAssembler;
    }

    @Override
    @RequireRole({UserRole.ADMIN, UserRole.STUDENT})
    public ResponseEntity<EntityModel<ScheduleItemResponse>> createScheduleItem(
            CreateScheduleItemRequest request) {
        ScheduleItem result = scheduleItemService.createScheduleItem(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(scheduleItemAssembler.toModel(result));
    }

    @Override
    public ResponseEntity<EntityModel<ScheduleItemResponse>> getScheduleItem(Long id) {
        ScheduleItem result = scheduleItemService.getScheduleItem(id);
        return ResponseEntity.ok(scheduleItemAssembler.toModel(result));
    }

    @Override
    public ResponseEntity<PagedModel<EntityModel<ScheduleItemResponse>>> listScheduleItems(
            Long groupId,
            Long semesterId,
            Pageable pageable,
            PagedResourcesAssembler<ScheduleItemResponse> assembler) {
        Page<ScheduleItem> page = scheduleItemService.listScheduleItems(groupId, semesterId, pageable);
        Page<ScheduleItemResponse> responsePage = page.map(item ->
                scheduleItemAssembler.toModel(item).getContent());
        PagedModel<EntityModel<ScheduleItemResponse>> pagedModel =
                assembler.toModel(responsePage, response -> EntityModel.of(response,
                        linkTo(methodOn(ScheduleItemController.class)
                                .getScheduleItem(response.getId())).withSelfRel()));
        return ResponseEntity.ok(pagedModel);
    }

    @Override
    @RequireRole({UserRole.ADMIN, UserRole.STUDENT})
    public ResponseEntity<EntityModel<ScheduleItemResponse>> updateScheduleItem(
            Long id,
            UpdateScheduleItemRequest request) {
        ScheduleItem result = scheduleItemService.updateScheduleItem(id, request);
        return ResponseEntity.ok(scheduleItemAssembler.toModel(result));
    }

    @Override
    @RequireRole({UserRole.ADMIN, UserRole.STUDENT})
    public ResponseEntity<Void> deleteScheduleItem(Long id) {
        scheduleItemService.deleteScheduleItem(id);
        return ResponseEntity.noContent().build();
    }
}
