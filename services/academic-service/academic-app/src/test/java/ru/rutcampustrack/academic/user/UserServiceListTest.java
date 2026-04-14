package ru.rutcampustrack.academic.user;

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
import ru.rutcampustrack.academic.repository.HeadmanAssistantRepository;
import ru.rutcampustrack.academic.repository.StudentGroupHistoryRepository;
import ru.rutcampustrack.academic.repository.UserRepository;
import ru.rutcampustrack.academic.security.RequestContext;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link UserService#listUsers(String, UserRole, AccountStatus, Pageable)}.
 *
 * <p>BUG-006 / Plan 58-01 Task 2. Моки вокруг UserRepository — проверяем, что сервис
 * собирает Specification и делегирует вызов repository.findAll(spec, pageable).
 */
@ExtendWith(MockitoExtension.class)
class UserServiceListTest {

    @Mock UserRepository userRepository;
    @Mock HeadmanAssistantRepository headmanAssistantRepository;
    @Mock StudentGroupHistoryRepository studentGroupHistoryRepository;
    @Mock RequestContext requestContext;
    @Mock UserAssembler userAssembler;
    @Mock ApplicationEventPublisher eventPublisher;

    @InjectMocks
    UserService service;

    Pageable pageable;

    @BeforeEach
    void setUp() {
        service = new UserService(userRepository, headmanAssistantRepository,
                studentGroupHistoryRepository, requestContext, userAssembler,
                null, eventPublisher);
        pageable = PageRequest.of(0, 20);
    }

    @Test
    @SuppressWarnings("unchecked")
    void listUsers_withSearch_delegatesToSpecificationFindAll() {
        Page<User> expected = new PageImpl<>(List.of());
        when(userRepository.findAll(any(Specification.class), any(Pageable.class)))
                .thenReturn(expected);

        Page<User> result = service.listUsers("иван", UserRole.STUDENT,
                AccountStatus.ACTIVE, pageable);

        assertThat(result).isSameAs(expected);

        ArgumentCaptor<Specification<User>> specCaptor = ArgumentCaptor.forClass(Specification.class);
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
}
