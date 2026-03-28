package ru.rutcampustrack.auth.config;

import jakarta.persistence.Converter;
import ru.rutcampustrack.auth.entity.enums.AccountStatus;
import ru.rutcampustrack.auth.entity.enums.UserRole;

/**
 * Concrete JPA converters for Auth Service enums.
 * autoApply = true — no need to annotate each entity field with @Convert.
 */
public class EnumConverters {

    @Converter(autoApply = true)
    public static class UserRoleConverter extends LowercaseEnumConverter<UserRole> {
        public UserRoleConverter() { super(UserRole.class); }
    }

    @Converter(autoApply = true)
    public static class AccountStatusConverter extends LowercaseEnumConverter<AccountStatus> {
        public AccountStatusConverter() { super(AccountStatus.class); }
    }
}
