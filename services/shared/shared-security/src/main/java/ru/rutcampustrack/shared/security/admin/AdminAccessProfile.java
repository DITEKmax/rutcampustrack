package ru.rutcampustrack.shared.security.admin;
import java.util.List;
public record AdminAccessProfile(long userId,boolean assigned,String mode,List<String> permissions,long version) {
    public AdminAccessProfile { permissions=List.copyOf(permissions); }
    public boolean full() { return assigned && "FULL".equals(mode); }
    public boolean allows(String permission) { return assigned && permissions.contains(permission); }
}
