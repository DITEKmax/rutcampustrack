package ru.rutcampustrack.academic.homework;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PagedResourcesAssembler;
import org.springframework.hateoas.EntityModel;
import org.springframework.hateoas.Link;
import org.springframework.hateoas.PagedModel;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import org.springframework.web.util.UriComponentsBuilder;

import ru.rutcampustrack.academic.contract.api.HomeworkApi;
import ru.rutcampustrack.academic.contract.dto.homework.CreateHomeworkRequest;
import ru.rutcampustrack.academic.contract.dto.homework.HomeworkHistoryResponse;
import ru.rutcampustrack.academic.contract.dto.homework.HomeworkResponse;
import ru.rutcampustrack.academic.contract.dto.homework.UpdateHomeworkRequest;
import ru.rutcampustrack.academic.contract.enums.UserRole;
import ru.rutcampustrack.academic.entity.Homework;
import ru.rutcampustrack.academic.security.RequireRole;

@RestController
public class HomeworkController implements HomeworkApi {

    private final HomeworkService homeworkService;
    private final HomeworkAssembler homeworkAssembler;

    public HomeworkController(
            HomeworkService homeworkService, HomeworkAssembler homeworkAssembler) {
        this.homeworkService = homeworkService;
        this.homeworkAssembler = homeworkAssembler;
    }

    @Override
    @RequireRole({UserRole.STUDENT})
    public ResponseEntity<?> createHomework(CreateHomeworkRequest request) {
        Homework homework = homeworkService.createHomework(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(homeworkAssembler.toModel(homework));
    }

    @Override
    public ResponseEntity<EntityModel<HomeworkResponse>> getHomework(Long id, boolean archived) {
        Homework homework = homeworkService.getHomework(id, archived);
        return ResponseEntity.ok(
                homeworkAssembler.toModel(
                        homework, homeworkService.isCompleted(homework.getId()), archived));
    }

    @Override
    @RequireRole({UserRole.STUDENT, UserRole.ADMIN})
    public ResponseEntity<PagedModel<EntityModel<HomeworkResponse>>> listHomeworks(
            Long groupId,
            Long semesterId,
            boolean archived,
            Pageable pageable,
            PagedResourcesAssembler<HomeworkResponse> assembler) {
        Page<Homework> page =
                homeworkService.listHomeworks(groupId, semesterId, pageable, archived);
        java.util.Set<Long> completed =
                homeworkService.completedHomeworkIds(page.stream().map(Homework::getId).toList());
        // Fetch completion only for this page and the current actor.
        Page<HomeworkResponse> responsePage =
                page.map(
                        hw -> {
                            EntityModel<HomeworkResponse> model =
                                    homeworkAssembler.toModel(
                                            hw, completed.contains(hw.getId()), archived);
                            HomeworkResponse response = model.getContent();
                            if (archived) response.add(model.getLinks());
                            return response;
                        });
        return ResponseEntity.ok(
                assembler.toModel(
                        responsePage,
                        response -> EntityModel.of(response),
                        publicPageLink(responsePage, groupId, semesterId, archived)));
    }

    private static Link publicPageLink(
            Page<?> page, Long groupId, Long semesterId, boolean archived) {
        var uri = UriComponentsBuilder.fromPath("/api/academic/homeworks");
        if (RequestContextHolder.getRequestAttributes()
                instanceof ServletRequestAttributes request) {
            uri.query(request.getRequest().getQueryString());
        }
        // Bound filters and actual page metadata remain authoritative. A public link never
        // inherits the internal servlet authority or trusts forwarding headers.
        return Link.of(
                uri.replaceQueryParam("groupId", groupId)
                        .replaceQueryParam("semesterId", semesterId)
                        .replaceQueryParam("archived", archived)
                        .replaceQueryParam("page", page.getNumber())
                        .replaceQueryParam("size", page.getSize())
                        .build(true)
                        .toUriString());
    }

    @Override
    @RequireRole({UserRole.STUDENT})
    public ResponseEntity<EntityModel<HomeworkResponse>> updateHomework(
            Long id, UpdateHomeworkRequest request) {
        Homework homework = homeworkService.updateHomework(id, request);
        return ResponseEntity.ok(
                homeworkAssembler.toModel(homework, homeworkService.isCompleted(homework.getId())));
    }

    @Override
    public ResponseEntity<Page<HomeworkHistoryResponse>> history(Long id, Pageable pageable) {
        return ResponseEntity.ok(homeworkService.history(id, pageable));
    }

    @Override
    @RequireRole({UserRole.STUDENT})
    public ResponseEntity<Void> deleteHomework(Long id) {
        homeworkService.deleteHomework(id);
        return ResponseEntity.noContent().build();
    }

    @Override
    @RequireRole({UserRole.STUDENT})
    public ResponseEntity<Void> markComplete(Long id) {
        homeworkService.markComplete(id);
        return ResponseEntity.noContent().build();
    }

    @Override
    @RequireRole({UserRole.STUDENT})
    public ResponseEntity<Void> unmarkComplete(Long id) {
        homeworkService.unmarkComplete(id);
        return ResponseEntity.noContent().build();
    }
}
