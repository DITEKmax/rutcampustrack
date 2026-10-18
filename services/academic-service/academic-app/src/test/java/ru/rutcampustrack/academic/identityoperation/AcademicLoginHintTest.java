package ru.rutcampustrack.academic.identityoperation;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

import ru.rutcampustrack.academic.contract.dto.user.CreateUserRequest;
import ru.rutcampustrack.academic.contract.enums.UserRole;

class AcademicLoginHintTest {
    @Test
    void truncationDoesNotLeaveInvalidTrailingSeparator() {
        var hint =
                AcademicLoginHint.from(
                        new CreateUserRequest(
                                "a".repeat(31), "B", null, UserRole.STUDENT, 7L, null, 12345678L));
        assertThat(hint.stem()).isEqualTo("a".repeat(31));
        assertThat(hint.digits()).isEqualTo("5678");
    }

    @Test
    void unicodeNameKeepsExistingDigitsOnlyLoginHint() {
        var hint =
                AcademicLoginHint.from(
                        new CreateUserRequest(
                                "王", "明", null, UserRole.TEACHER, null, "EMP-00123", null));
        assertThat(hint.stem()).isEmpty();
        assertThat(hint.digits()).isEqualTo("0123");
    }

    @Test
    void telegramDigitsTakePriorityAndOnlyAuthAllocatesMissingDigits() {
        var withTelegram =
                AcademicLoginHint.from(
                        new CreateUserRequest(
                                "Иванов", "Иван", null, UserRole.TEACHER, null, "EMP-00123", 9L));
        assertThat(withTelegram.stem()).isEqualTo("ivanov-ivan");
        assertThat(withTelegram.digits()).isEqualTo("0009");
        var missing =
                AcademicLoginHint.from(
                        new CreateUserRequest(
                                "Иванов", "Иван", null, UserRole.TEACHER, null, "EMP", null));
        assertThat(missing.digits()).isEmpty();
    }
}
