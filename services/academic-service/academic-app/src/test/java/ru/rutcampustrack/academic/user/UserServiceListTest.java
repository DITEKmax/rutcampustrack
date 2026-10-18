package ru.rutcampustrack.academic.user;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;

import ru.rutcampustrack.academic.contract.enums.AccountStatus;
import ru.rutcampustrack.academic.contract.enums.UserRole;
import ru.rutcampustrack.academic.entity.User;
import ru.rutcampustrack.academic.exception.BadRequestException;
import ru.rutcampustrack.academic.identitycatalog.IdentityRegistryQueryBarrier;
import ru.rutcampustrack.academic.repository.HeadmanAssistantRepository;
import ru.rutcampustrack.academic.repository.StudentGroupHistoryRepository;
import ru.rutcampustrack.academic.repository.UserRepository;
import ru.rutcampustrack.academic.security.RequestContext;

import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

/**
 * Unit tests for {@link UserService#listUsers(String, UserRole, AccountStatus, Pageable)}.
 *
 * <p>BUG-006 / Plan 58-01 Task 2. Моки вокруг UserRepository — проверяем, что сервис собирает
 * Specification внутри registry barrier и делегирует вызов repository.findAll(spec, pageable).
 */
@ExtendWith(MockitoExtension.class)
class UserServiceListTest {

    @Mock UserRepository userRepository;
    @Mock IdentityRegistryQueryBarrier registryBarrier;
    @Mock HeadmanAssistantRepository headmanAssistantRepository;
    @Mock StudentGroupHistoryRepository studentGroupHistoryRepository;
    @Mock RequestContext requestContext;
    @Mock UserAssembler userAssembler;
    @Mock ApplicationEventPublisher eventPublisher;

    @InjectMocks UserService service;

    Pageable pageable;

    @BeforeEach
    void setUp() {
        service =
                new UserService(
                        userRepository,
                        headmanAssistantRepository,
                        studentGroupHistoryRepository,
                        requestContext,
                        userAssembler,
                        null,
                        eventPublisher,
                        org.mockito.Mockito.mock(
                                org.springframework.security.crypto.password.PasswordEncoder
                                        .class));
        org.springframework.test.util.ReflectionTestUtils.setField(
                service, "registryBarrier", registryBarrier);
        org.mockito.Mockito.lenient()
                .when(registryBarrier.read(any()))
                .thenAnswer(
                        call -> {
                            Supplier<Page<User>> query = call.getArgument(0);
                            return new IdentityRegistryQueryBarrier.Result(
                                    query.get(), Map.of(), true);
                        });
        pageable = PageRequest.of(0, 20);
    }

    @Test
    @SuppressWarnings("unchecked")
    void listUsers_withSearch_delegatesToSpecificationFindAll() {
        Page<User> expected = new PageImpl<>(List.of());
        when(userRepository.findAll(any(Specification.class), any(Pageable.class)))
                .thenReturn(expected);

        Page<User> result =
                service.listUsers("иван", UserRole.STUDENT, AccountStatus.ACTIVE, pageable);

        assertThat(result).isSameAs(expected);

        ArgumentCaptor<Specification<User>> specCaptor =
                ArgumentCaptor.forClass(Specification.class);
        verify(userRepository).findAll(specCaptor.capture(), any(Pageable.class));
        // Specification.where(matchesSearch(...)).and(matchesRole(...)).and(matchesStatus(...))
        // всегда non-null как root wrapper даже если inner — null.
        assertThat(specCaptor.getValue()).isNotNull();
    }

    @Test
    @SuppressWarnings("unchecked")
    void listUsers_allParamsNull_stillCallsSpecificationFindAll() {
        when(userRepository.findAll(any(Specification.class), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of()));

        service.listUsers(null, null, null, pageable);

        // Даже с null-параметрами — вызывается findAll(Specification, Pageable),
        // а не findAll(Pageable): Specification.where(null) → wrapper, не-null.
        verify(userRepository).findAll(any(Specification.class), any(Pageable.class));
    }

    @Test
    @SuppressWarnings("unchecked")
    void listUsers_legacyOverload_delegatesToFullListUsers() {
        when(userRepository.findAll(any(Specification.class), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of()));

        service.listUsers(UserRole.TEACHER, pageable);

        verify(userRepository).findAll(any(Specification.class), any(Pageable.class));
    }

    @Test
    void matchesSearch_nullOrBlank_returnsNullSpec() {
        assertThat(UserSpecifications.matchesSearch(null)).isNull();
        assertThat(UserSpecifications.matchesSearch("")).isNull();
        assertThat(UserSpecifications.matchesSearch("   ")).isNull();
    }

    @Test
    void matchesRoleAndStatus_null_returnsNullSpec() {
        assertThat(UserSpecifications.matchesRole(null)).isNull();
        assertThat(UserSpecifications.matchesStatus(null)).isNull();
    }

    @Test
    void roleStatusFilterRejectsStatusFromAnotherRole() {
        assertThatThrownBy(() -> service.listUsers(null, "STUDENT", null, "DISMISSED", pageable))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("не применим");
    }

    @Test
    void archivedRoleStatusRemainsFilterableAsReadOnlyHistory() {
        when(userRepository.findAdminRegistryIncludingArchived(
                        isNull(), eq("teacher"), eq("archived"), isNull(), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of()));

        service.listUsers(null, "TEACHER", null, "ARCHIVED", pageable);

        verify(userRepository)
                .findAdminRegistryIncludingArchived(
                        isNull(), eq("teacher"), eq("archived"), isNull(), any(Pageable.class));
    }
}
