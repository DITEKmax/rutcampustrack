package ru.rutcampustrack.academic.homework;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PagedResourcesAssembler;
import org.springframework.hateoas.EntityModel;
import org.springframework.hateoas.PagedModel;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;
import ru.rutcampustrack.academic.contract.api.HomeworkApi;
import ru.rutcampustrack.academic.contract.dto.homework.CreateHomeworkRequest;
import ru.rutcampustrack.academic.contract.dto.homework.HomeworkResponse;
import ru.rutcampustrack.academic.contract.dto.homework.UpdateHomeworkRequest;
import ru.rutcampustrack.academic.contract.enums.UserRole;
import ru.rutcampustrack.academic.entity.Homework;
import ru.rutcampustrack.academic.security.RequireRole;

@RestController
public class HomeworkController implements HomeworkApi {

    private final HomeworkService homeworkService;
    private final HomeworkAssembler homeworkAssembler;

    public HomeworkController(HomeworkService homeworkService,
                               HomeworkAssembler homeworkAssembler) {
        this.homeworkService = homeworkService;
        this.homeworkAssembler = homeworkAssembler;
    }

    @Override
    @RequireRole({UserRole.STUDENT})
    public ResponseEntity<EntityModel<HomeworkResponse>> createHomework(CreateHomeworkRequest request) {
        Homework homework = homeworkService.createHomework(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(homeworkAssembler.toModel(homework));
    }

    @Override
    public ResponseEntity<EntityModel<HomeworkResponse>> getHomework(Long id) {
        Homework homework = homeworkService.getHomework(id);
        return ResponseEntity.ok(homeworkAssembler.toModel(homework));
    }

    @Override
    @RequireRole({UserRole.STUDENT, UserRole.ADMIN})
    public ResponseEntity<PagedModel<EntityModel<HomeworkResponse>>> listHomeworks(
            Long groupId, Long semesterId, Pageable pageable,
            PagedResourcesAssembler<HomeworkResponse> assembler) {
        Page<Homework> page = homeworkService.listHomeworks(groupId, semesterId, pageable);
        // For student context, annotate completion status per homework
        Page<HomeworkResponse> responsePage = page.map(
                hw -> homeworkAssembler.toModel(hw, homeworkService.isCompleted(hw.getId())).getContent());
        return ResponseEntity.ok(assembler.toModel(responsePage,
                response -> EntityModel.of(response)));
    }

    @Override
    @RequireRole({UserRole.STUDENT})
    public ResponseEntity<EntityModel<HomeworkResponse>> updateHomework(Long id, UpdateHomeworkRequest request) {
        Homework homework = homeworkService.updateHomework(id, request);
        return ResponseEntity.ok(homeworkAssembler.toModel(homework));
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
