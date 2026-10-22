package ru.rutcampustrack.academic.user;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import ru.rutcampustrack.academic.contract.enums.AccountStatus;
import ru.rutcampustrack.academic.contract.enums.UserRole;
import ru.rutcampustrack.academic.entity.User;
import ru.rutcampustrack.academic.exception.BadRequestException;
import ru.rutcampustrack.academic.identitycatalog.IdentityRegistryQueryBarrier;
import ru.rutcampustrack.academic.repository.HeadmanAssistantRepository;
import ru.rutcampustrack.academic.repository.StudentGroupHistoryRepository;
import ru.rutcampustrack.academic.repository.UserRepository;
import ru.rutcampustrack.academic.security.RequestContext;

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
    void roleStatusFilterRejectsStatusFromAnotherRole() {
        assertThatThrownBy(() -> service.listUsers(null, "STUDENT", null, "DISMISSED", pageable))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("не применим");
    }
}
