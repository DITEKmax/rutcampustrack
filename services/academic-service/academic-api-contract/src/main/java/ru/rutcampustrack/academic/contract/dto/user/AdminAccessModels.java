package ru.rutcampustrack.academic.contract.dto.user;
import java.util.List;
public final class AdminAccessModels {
    private AdminAccessModels() {}
    public record Profile(long userId,boolean assigned,String mode,List<String> permissions,long version) {}
    public record Entry(String code,String label,String description,String group,List<String> requires,boolean fullOnly) {}
    public record Catalog(List<Entry> permissions) {}
    public record Put(String mode,List<String> permissions,Long expectedVersion) {}
    public record Delete(Long expectedVersion) {}
}
