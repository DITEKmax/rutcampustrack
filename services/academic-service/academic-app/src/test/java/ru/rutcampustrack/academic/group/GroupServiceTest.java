package ru.rutcampustrack.academic.group;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import ru.rutcampustrack.academic.contract.dto.group.CreateGroupRequest;
import ru.rutcampustrack.academic.contract.dto.group.GroupResponse;
import ru.rutcampustrack.academic.contract.dto.group.UpdateGroupRequest;
import ru.rutcampustrack.academic.entity.Group;
import ru.rutcampustrack.academic.exception.BadRequestException;
import ru.rutcampustrack.academic.exception.ConflictException;
import ru.rutcampustrack.academic.repository.GroupRepository;
import ru.rutcampustrack.academic.repository.UserRepository;
import ru.rutcampustrack.academic.security.RequestContext;

import java.lang.reflect.Field;
import java.time.OffsetDateTime;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for GroupService and Group DTO validation.
 *
 * BUG-006-5 / план 58-04: единое поле {@code name}, активный формат
 * {@code ^[А-ЯЁ][А-ЯЁа-яё]{1,3}-\d{3}$}. CreateGroupRequest отвергает
 * старый формат (латиница / {@code ИВТ11-001}) и архивный суффикс.
 */
@ExtendWith(MockitoExtension.class)
class GroupServiceTest {

    private static Validator validator;

    @Mock private GroupRepository groupRepository;
    @Mock private UserRepository userRepository;
    @Mock private RequestContext requestContext;
    @Mock private ApplicationEventPublisher eventPublisher;
    // 58-06: real parser, чтобы GroupService.createGroup мог извлечь тип программы
    // без мокирования чистой логики.
    @Spy private GroupNameParser nameParser = new GroupNameParser();

    @InjectMocks private GroupService groupService;

    @BeforeEach
    void setUpValidator() {
        if (validator == null) {
            try (ValidatorFactory factory = Validation.buildDefaultValidatorFactory()) {
                validator = factory.getValidator();
            }
        }
    }

    // =========================================================================
    // CreateGroupRequest validation (active-format only)
    // =========================================================================

    @Test
    void createRequest_activeFormat_isValid() {
        Set<ConstraintViolation<CreateGroupRequest>> v =
                validator.validate(new CreateGroupRequest("УИТ-311"));
        assertThat(v).isEmpty();
    }

    @Test
    void createRequest_subspecializationLowercase_isValid() {
        Set<ConstraintViolation<CreateGroupRequest>> v =
                validator.validate(new CreateGroupRequest("УВПв-511"));
        assertThat(v).isEmpty();
    }

    @Test
    void createRequest_garbage_isInvalid() {
        Set<ConstraintViolation<CreateGroupRequest>> v =
                validator.validate(new CreateGroupRequest("invalid"));
        assertThat(v).isNotEmpty();
    }

    @Test
    void createRequest_oldLatinFormat_isInvalid() {
        Set<ConstraintViolation<CreateGroupRequest>> v =
                validator.validate(new CreateGroupRequest("ИВТ11-001"));
        assertThat(v).isNotEmpty();
    }

    @Test
    void createRequest_archivedFormat_isInvalid() {
        // Архивный формат не разрешён в CreateRequest (только сервис архивации
        // может ставить суффикс "(выпуск YYYY)").
        Set<ConstraintViolation<CreateGroupRequest>> v =
                validator.validate(new CreateGroupRequest("УИТ-411 (выпуск 2026)"));
        assertThat(v).isNotEmpty();
    }

    // =========================================================================
    // UpdateGroupRequest validation
    // =========================================================================

    @Test
    void updateRequest_activeFormat_isValid() {
        Set<ConstraintViolation<UpdateGroupRequest>> v =
                validator.validate(new UpdateGroupRequest("УИТ-312", true));
        assertThat(v).isEmpty();
    }

    @Test
    void updateRequest_archivedFormat_isInvalid() {
        Set<ConstraintViolation<UpdateGroupRequest>> v =
                validator.validate(new UpdateGroupRequest("УИТ-411 (выпуск 2026)", false));
        assertThat(v).isNotEmpty();
    }

    // =========================================================================
    // GroupService.createGroup pre-check
    // =========================================================================

    @Test
    void createGroup_newName_savesEntityWithOnlyName() {
        when(groupRepository.existsByName("УИТ-311")).thenReturn(false);
        when(groupRepository.save(any(Group.class))).thenAnswer(inv -> inv.getArgument(0));

        Group saved = groupService.createGroup(new CreateGroupRequest("УИТ-311"));

        ArgumentCaptor<Group> captor = ArgumentCaptor.forClass(Group.class);
        verify(groupRepository).save(captor.capture());
        assertThat(captor.getValue().getName()).isEqualTo("УИТ-311");
        assertThat(captor.getValue().isActive()).isTrue();
        assertThat(saved.getName()).isEqualTo("УИТ-311");
    }

    @Test
    void createGroup_duplicateName_throwsConflictWithFieldName() {
        when(groupRepository.existsByName("УИТ-311")).thenReturn(true);

        assertThatThrownBy(() -> groupService.createGroup(new CreateGroupRequest("УИТ-311")))
                .isInstanceOf(ConflictException.class)
                .satisfies(e -> {
                    ConflictException ce = (ConflictException) e;
                    assertThat(ce.getField()).isEqualTo("name");
                    assertThat(ce.getMessage()).contains("уже существует");
                });

        verify(groupRepository, never()).save(any());
    }

    // =========================================================================
    // 58-06 / BUG-006-6 — ProgramType guard + archived guard
    // =========================================================================

    @Test
    void createGroup_unknownProgramType_throwsBadRequestWithFieldName() {
        // УИТ-351 — middle digit 5 не зарегистрирован в ProgramType.
        when(groupRepository.existsByName("УИТ-351")).thenReturn(false);

        assertThatThrownBy(() -> groupService.createGroup(new CreateGroupRequest("УИТ-351")))
                .isInstanceOf(BadRequestException.class)
                .satisfies(e -> {
                    BadRequestException be = (BadRequestException) e;
                    assertThat(be.getField()).isEqualTo("name");
                    assertThat(be.getMessage()).contains("Неизвестный тип программы").contains("5");
                });

        verify(groupRepository, never()).save(any());
    }

    @Test
    void updateGroup_archivedGroup_throwsConflict() {
        // Архивная группа (is_active=false) — PUT блокирован.
        Group archived = new Group();
        // reflection: id + active setter
        try {
            Field idField = Group.class.getDeclaredField("id");
            idField.setAccessible(true);
            idField.set(archived, 42L);
        } catch (ReflectiveOperationException e) {
            throw new RuntimeException(e);
        }
        archived.setName("УИТ-411 (выпуск 2026)");
        archived.setActive(false);
        archived.setCreatedAt(OffsetDateTime.now());

        when(groupRepository.findById(42L)).thenReturn(java.util.Optional.of(archived));

        assertThatThrownBy(() -> groupService.updateGroup(42L, new UpdateGroupRequest("УИТ-211", true)))
                .isInstanceOf(ConflictException.class)
                .satisfies(e -> {
                    ConflictException ce = (ConflictException) e;
                    assertThat(ce.getField()).isEqualTo("archived");
                    assertThat(ce.getMessage()).contains("архивную");
                });

        verify(groupRepository, never()).save(any());
    }

    // =========================================================================
    // GroupResponse — сериализуется только с name (без code)
    // =========================================================================

    @Test
    void groupResponse_hasNoCodeField() throws NoSuchFieldException {
        GroupResponse response = new GroupResponse(
                1L, "УИТ-311", true, OffsetDateTime.parse("2026-04-14T00:00:00Z"));
        assertThat(response.getName()).isEqualTo("УИТ-311");
        // Reflection check: code field removed from GroupResponse entirely.
        assertThatThrownBy(() -> GroupResponse.class.getDeclaredField("code"))
                .isInstanceOf(NoSuchFieldException.class);
    }

    @Test
    void groupEntity_hasNoCodeField() {
        // Entity Group поле code удалено — @Column(name="name", unique, length=32)
        assertThatThrownBy(() -> Group.class.getDeclaredField("code"))
                .isInstanceOf(NoSuchFieldException.class);

        // Поле name по-прежнему присутствует.
        assertThat(java.util.Arrays.stream(Group.class.getDeclaredFields())
                .map(Field::getName))
                .contains("name")
                .doesNotContain("code");
    }

    @Test
    void groupEntity_pattern_allowsArchivedFormat() {
        // Entity @Pattern разрешает активный ИЛИ архивный формат
        // (сервис архивации в плане 06 ставит суффикс через прямой save).
        Group g = new Group();
        g.setName("УИТ-411 (выпуск 2026)");
        Set<ConstraintViolation<Group>> v = validator.validate(g);
        // @NotBlank/@Pattern проходят; остальная валидация (createdAt=null) нас не волнует для pattern-теста.
        assertThat(v).allSatisfy(violation ->
                assertThat(violation.getPropertyPath().toString()).isNotEqualTo("name"));
    }
}
