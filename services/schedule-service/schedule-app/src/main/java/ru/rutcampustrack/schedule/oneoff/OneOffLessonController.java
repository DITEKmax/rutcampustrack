package ru.rutcampustrack.schedule.oneoff;

import org.springframework.hateoas.CollectionModel;
import org.springframework.hateoas.EntityModel;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;
import ru.rutcampustrack.schedule.contract.api.OneOffLessonApi;
import ru.rutcampustrack.schedule.contract.dto.oneoff.CreateOneOffLessonRequest;
import ru.rutcampustrack.schedule.contract.dto.oneoff.OneOffLessonResponse;
import ru.rutcampustrack.schedule.contract.enums.UserRole;
import ru.rutcampustrack.schedule.oneoff.entity.OneOffLesson;
import ru.rutcampustrack.schedule.security.RequireRole;

import java.time.LocalDate;
import java.util.List;

/**
 * REST controller for one-off lesson management (Phase 60-03).
 * Implements {@link OneOffLessonApi}; HTTP mappings stay on the contract interface.
 */
@RestController
public class OneOffLessonController implements OneOffLessonApi {

    private final OneOffLessonService oneOffLessonService;
    private final OneOffLessonAssembler assembler;

    public OneOffLessonController(OneOffLessonService oneOffLessonService,
                                  OneOffLessonAssembler assembler) {
        this.oneOffLessonService = oneOffLessonService;
        this.assembler = assembler;
    }

    @Override
    @RequireRole({UserRole.ADMIN, UserRole.STUDENT})
    public ResponseEntity<EntityModel<OneOffLessonResponse>> createOneOffLesson(
            CreateOneOffLessonRequest request) {
        OneOffLesson saved = oneOffLessonService.createOneOffLesson(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(assembler.toModel(saved));
    }

    @Override
    public ResponseEntity<CollectionModel<EntityModel<OneOffLessonResponse>>> listOneOffLessons(
            Long groupId, LocalDate dateFrom, LocalDate dateTo) {
        List<OneOffLesson> items = oneOffLessonService.listOneOffLessons(groupId, dateFrom, dateTo);
        List<EntityModel<OneOffLessonResponse>> models = items.stream()
                .map(assembler::toModel)
                .toList();
        return ResponseEntity.ok(CollectionModel.of(models));
    }

    @Override
    @RequireRole({UserRole.ADMIN, UserRole.STUDENT})
    public ResponseEntity<Void> deleteOneOffLesson(Long id) {
        oneOffLessonService.deleteOneOffLesson(id);
        return ResponseEntity.noContent().build();
    }
}
