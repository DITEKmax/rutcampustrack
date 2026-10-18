package ru.rutcampustrack.academic.user;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import org.junit.jupiter.api.Test;

import ru.rutcampustrack.academic.contract.dto.user.PatchUserRequest;
import ru.rutcampustrack.academic.exception.BadRequestException;
import ru.rutcampustrack.academic.exception.ConflictException;

class UserServicePatchProfileTest {
    private final UserIdentityCommandFixture fixture = new UserIdentityCommandFixture();

    @Test
    void profilePatchTrimsNamesClearsBlankMiddleNameAndLeavesRoleGrantsUntouched() {
        fixture.service.patchUser(42L, request("  Иванов ", " Иван ", "  ", null));
        var profile = fixture.local().profile();
        assertThat(profile.lastName()).isEqualTo("Иванов");
        assertThat(profile.firstName()).isEqualTo("Иван");
        assertThat(profile.middleName()).isNull();
        verify(fixture.oldWriter, never()).synchronize(any());
        assertThat(fixture.accepted.authCommand().getTargets(0).getDesiredGrantsList())
                .extracting(ru.rutcampustrack.authoperations.grpc.AdmissionGrant::getRole)
                .containsExactly("STUDENT", "TEACHER");
        assertThat(fixture.accepted.authCommand().getTargets(0).getDesiredGrantsList())
                .extracting(ru.rutcampustrack.authoperations.grpc.AdmissionGrant::getGrantId)
                .containsExactly(12L, 11L);
        assertThat(fixture.accepted.authCommand().getTargets(0).getDesiredGrantsList())
                .allSatisfy(grant -> assertThat(grant.getStatus()).isEqualTo("ACTIVE"));
        verify(fixture.jdbc, never())
                .queryForObject(contains("employee_number=?"), eq(Boolean.class), any(), any());
    }

    @Test
    void teacherGrantOnMultiRoleAccountPreventsClearingEmployeeNumber() {
        assertThatThrownBy(() -> fixture.service.patchUser(42L, request(null, null, null, "  ")))
                .isInstanceOfSatisfying(
                        BadRequestException.class,
                        error -> assertThat(error.getField()).isEqualTo("employeeNumber"));
        fixture.notAccepted();
        verify(fixture.users, never()).save(any());
        verify(fixture.jdbc, never())
                .queryForObject(contains("employee_number=?"), eq(Boolean.class), any(), any());
    }

    @Test
    void unchangedEmployeeNumberDoesNotSelfConflict() {
        fixture.service.patchUser(42L, request(null, null, null, " EMP-001 "));
        verify(fixture.jdbc, never())
                .queryForObject(contains("employee_number=?"), eq(Boolean.class), any(), any());
        assertThat(fixture.local().profile().employeeNumber()).isEqualTo("EMP-001");
    }

    @Test
    void duplicateEmployeeNumberReturnsFieldSpecificConflict() {
        when(fixture.jdbc.queryForObject(
                        contains("employee_number=?"), eq(Boolean.class), eq("EMP-002"), eq(42L)))
                .thenReturn(true);
        assertThatThrownBy(
                        () -> fixture.service.patchUser(42L, request(null, null, null, "EMP-002")))
                .isInstanceOfSatisfying(
                        ConflictException.class,
                        error -> assertThat(error.getField()).isEqualTo("employeeNumber"));
        fixture.notAccepted();
        verify(fixture.users, never()).save(any());
    }

    @Test
    void blankRequiredNameIsRejectedBeforeSave() {
        assertThatThrownBy(() -> fixture.service.patchUser(42L, request("  ", null, null, null)))
                .isInstanceOfSatisfying(
                        BadRequestException.class,
                        error -> assertThat(error.getField()).isEqualTo("lastName"));
        fixture.notAccepted();
        verify(fixture.users, never()).save(any());
    }

    private static PatchUserRequest request(
            String lastName, String firstName, String middleName, String employeeNumber) {
        return new PatchUserRequest(
                lastName, firstName, middleName, null, null, employeeNumber, null, null);
    }
}
