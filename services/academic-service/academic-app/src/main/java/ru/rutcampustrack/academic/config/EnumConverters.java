package ru.rutcampustrack.academic.config;

import jakarta.persistence.Converter;
import ru.rutcampustrack.academic.contract.enums.AccountStatus;
import ru.rutcampustrack.academic.contract.enums.AssistantPermission;
import ru.rutcampustrack.academic.contract.enums.SubjectType;
import ru.rutcampustrack.academic.contract.enums.UserRole;

/**
 * Конкретные JPA-конвертеры для enum-ов Academic Service.
 * autoApply = true — не нужно указывать @Convert на каждом поле entity.
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

    @Converter(autoApply = true)
    public static class SubjectTypeConverter extends LowercaseEnumConverter<SubjectType> {
        public SubjectTypeConverter() { super(SubjectType.class); }
    }

    @Converter(autoApply = true)
    public static class AssistantPermissionConverter extends LowercaseEnumConverter<AssistantPermission> {
        public AssistantPermissionConverter() { super(AssistantPermission.class); }
    }
}
