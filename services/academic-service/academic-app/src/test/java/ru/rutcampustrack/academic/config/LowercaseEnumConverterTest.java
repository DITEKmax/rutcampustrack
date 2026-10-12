package ru.rutcampustrack.academic.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Locale;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.ResourceLock;
import ru.rutcampustrack.academic.contract.enums.AccountStatus;
import ru.rutcampustrack.academic.contract.enums.UserRole;

@ResourceLock("java.util.Locale.default")
class LowercaseEnumConverterTest {

    @Test
    void persistedCodesRemainStableUnderTurkishLocale() {
        Locale previous = Locale.getDefault();
        try {
            Locale.setDefault(Locale.forLanguageTag("tr-TR"));
            assertThat(
                            new EnumConverters.UserRoleConverter()
                                    .convertToDatabaseColumn(UserRole.ADMIN))
                    .isEqualTo("admin");
            assertThat(
                            new EnumConverters.AccountStatusConverter()
                                    .convertToEntityAttribute("active"))
                    .isEqualTo(AccountStatus.ACTIVE);
            assertThat(
                            new EnumConverters.AccountStatusConverter()
                                    .convertToDatabaseColumn(AccountStatus.ARCHIVED))
                    .isEqualTo("archived");
        } finally {
            Locale.setDefault(previous);
        }
    }

    @Test
    void nullAndUnknownValuesDoNotBecomeKnownRoles() {
        var converter = new EnumConverters.UserRoleConverter();
        assertThat(converter.convertToEntityAttribute(null)).isNull();
        assertThat(converter.convertToDatabaseColumn(null)).isNull();
        assertThatThrownBy(() -> converter.convertToEntityAttribute("unknown-role"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThat(converter.convertToEntityAttribute("admin")).isEqualTo(UserRole.ADMIN);
        assertThat(converter.convertToDatabaseColumn(UserRole.TEACHER)).isEqualTo("teacher");
    }
}
