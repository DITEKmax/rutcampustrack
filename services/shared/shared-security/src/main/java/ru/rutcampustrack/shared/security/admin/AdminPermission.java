package ru.rutcampustrack.shared.security.admin;

import java.util.*;

/** Closed, server-owned R1 catalogue. FULL is a separate authority mode. */
public enum AdminPermission {
    DASHBOARD_VIEW("Просмотр сводки", "Сводная статистика", "overview"),
    USERS_VIEW("Просмотр пользователей", "Карточки и списки без начальных паролей", "users"),
    USERS_MANAGE("Управление пользователями", "Создание и изменение обычных пользователей", "users", USERS_VIEW),
    USER_CREDENTIALS_MANAGE("Начальные пароли и восстановление доступа", "Начальные пароли и ссылки восстановления обычных пользователей", "users", USERS_VIEW),
    USERS_ARCHIVE("Архивирование и восстановление пользователей", "Защищённое архивирование и восстановление обычных пользователей", "users", USERS_VIEW),
    GROUPS_VIEW("Просмотр групп", "Реестр и карточки групп", "groups"),
    GROUPS_MANAGE("Управление группами", "Создание, изменение, старосты, продвижение и архив групп", "groups", GROUPS_VIEW),
    SEMESTERS_VIEW("Просмотр семестров", "Список и карточки семестров", "semesters"),
    SEMESTERS_MANAGE("Управление семестрами", "Создание, изменение, активация и архив семестров", "semesters", SEMESTERS_VIEW),
    CAMPUS_MAP_MANAGE("Управление картой", "Здания, этажи и планы кампуса", "map"),
    DATA_DELETE("Окончательное удаление данных", "Дополнительно требует управление областью и защищённое подтверждение", "dangerous"),
    ADMIN_ACCESS_MANAGE("Управление администраторами и их правами", "Доступно только полному администратору", "administration");
    private final String label, description, group;
    private final List<String> requires;
    AdminPermission(String label, String description, String group, AdminPermission... requires) {
        this.label=label; this.description=description; this.group=group;
        this.requires=Arrays.stream(requires).map(Enum::name).toList();
    }
    public Entry entry() { return new Entry(name(),label,description,group,requires,this==ADMIN_ACCESS_MANAGE); }
    public record Entry(String code,String label,String description,String group,List<String> requires,boolean fullOnly) {}
    public static List<String> allCodes() { return Arrays.stream(values()).map(Enum::name).toList(); }
    public static List<String> validate(String mode,List<String> input) {
        if (input==null || !("FULL".equals(mode)||"LIMITED".equals(mode))) throw AdminAccessException.invalid();
        if ("FULL".equals(mode)) {
            if (!input.isEmpty()) throw AdminAccessException.invalid();
            return List.of();
        }
        if (input.isEmpty() || input.stream().anyMatch(Objects::isNull)) throw AdminAccessException.invalid();
        Set<String> unique=new HashSet<>(input);
        if (unique.size()!=input.size() || unique.contains(ADMIN_ACCESS_MANAGE.name())) throw AdminAccessException.invalid();
        for(String code:input) {
            AdminPermission value;
            try { value=valueOf(code); } catch(IllegalArgumentException e) { throw AdminAccessException.invalid(); }
            if(!unique.containsAll(value.requires)) throw AdminAccessException.invalid();
        }
        return allCodes().stream().filter(unique::contains).toList();
    }
}
