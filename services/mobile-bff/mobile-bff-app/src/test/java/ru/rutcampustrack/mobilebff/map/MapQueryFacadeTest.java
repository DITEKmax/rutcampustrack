package ru.rutcampustrack.mobilebff.map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import ru.rutcampustrack.mobilebff.contract.model.StudentApiModels.ProblemCode;
import ru.rutcampustrack.mobilebff.error.MobileBffException;
import ru.rutcampustrack.mobilebff.grpc.MapAcademicClient;
import ru.rutcampustrack.mobilebff.security.MobileRequestContext;
import ru.rutcampustrack.shared.security.InternalJwtClaims;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MapQueryFacadeTest {
    private static final UUID SESSION_ID = UUID.fromString("33333333-3333-4333-8333-333333333333");

    @Mock private MapAcademicClient academic;
    @Mock private MobileRequestContext requestContext;

    private MapQueryFacade facade;

    @BeforeEach
    void setUp() {
        facade = new MapQueryFacade(academic, requestContext);
    }

    @Test
    void activeTeacherCanUseManifestAndRevisionEtagIsForwarded() {
        when(requestContext.claims()).thenReturn(
                new InternalJwtClaims(100L, SESSION_ID, 1L, 1L,
                        "TEACHER", "ACTIVE", null, false, false));
        when(academic.manifest(7L)).thenReturn(new MapAcademicClient.ManifestResult(true, 7L, null));

        facade.manifest("W/\"7\"");

        verify(academic).manifest(7L);
    }

    @Test
    void headmanUsesStudentRepresentationAndStudentScopeStillApplies() {
        when(requestContext.claims()).thenReturn(
                new InternalJwtClaims(101L, SESSION_ID, 1L, 1L,
                        "STUDENT", "ACTIVE", 11L, true, false));
        when(academic.manifest(0L)).thenReturn(new MapAcademicClient.ManifestResult(true, 3L, null));

        facade.manifest(null);

        verify(academic).manifest(0L);
    }

    @Test
    void adminCannotUseMobileMapReadScope() {
        when(requestContext.claims()).thenReturn(
                new InternalJwtClaims(102L, SESSION_ID, 1L, 1L,
                        "ADMIN", "ACTIVE", null, false, false));

        assertThatThrownBy(() -> facade.manifest(null))
                .isInstanceOf(MobileBffException.class)
                .extracting(error -> ((MobileBffException) error).code())
                .isEqualTo(ProblemCode.WRONG_ROLE);
        verifyNoInteractions(academic);
    }
}
