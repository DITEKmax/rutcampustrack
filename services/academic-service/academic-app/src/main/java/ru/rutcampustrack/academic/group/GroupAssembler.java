package ru.rutcampustrack.academic.group;

import org.springframework.hateoas.EntityModel;
import org.springframework.hateoas.server.RepresentationModelAssembler;
import org.springframework.stereotype.Component;
import ru.rutcampustrack.academic.contract.dto.group.GroupResponse;
import ru.rutcampustrack.academic.contract.dto.group.AdminGroupResponse;
import ru.rutcampustrack.academic.contract.dto.group.AdminGroupStatus;
import ru.rutcampustrack.academic.entity.Group;
import ru.rutcampustrack.academic.repository.GroupRegistryReadRepository;

import static org.springframework.hateoas.server.mvc.WebMvcLinkBuilder.linkTo;
import static org.springframework.hateoas.server.mvc.WebMvcLinkBuilder.methodOn;

/**
 * Assembles Group entity into HATEOAS EntityModel<GroupResponse>.
 */
@Component
public class GroupAssembler implements RepresentationModelAssembler<Group, EntityModel<GroupResponse>> {

    private final GroupNameParser nameParser;

    public GroupAssembler(GroupNameParser nameParser) {
        this.nameParser = nameParser;
    }

    @Override
    public EntityModel<GroupResponse> toModel(Group entity) {
        GroupResponse response = toResponse(entity);
        return EntityModel.of(response,
                linkTo(methodOn(GroupController.class).getGroup(entity.getId())).withSelfRel());
    }

    public GroupResponse toResponse(Group entity) {
        return new GroupResponse(
                entity.getId(),
                entity.getName(),
                entity.getAlphabeticCode(),
                entity.getNumericCode(),
                entity.getCurrentCourse(),
                entity.getTrainingDurationYears(),
                entity.getDurationStatus(),
                entity.isActive(),
                entity.getCreatedAt()
        );
    }

    /** Convert the server-side registry projection without client-side status rules. */
    public AdminGroupResponse toRegistryResponse(GroupRegistryReadRepository.GroupRegistryRow row) {
        LegacyCode fallback = fallback(row);
        LegacyCode stored = storedCode(row);
        // A valid persisted tuple that matches the active part of the name is
        // authoritative, including custom durations. Only rows without that
        // reliable tuple use the bounded legacy inference.
        LegacyCode effective = stored.isKnown() ? stored : fallback;
        String alphabeticCode = effective.alphabeticCode();
        String numericCode = effective.numericCode();
        Integer currentCourse = effective.currentCourse();
        Integer duration = effective.trainingDurationYears();
        String durationStatus = effective.durationStatus(row.durationStatus());
        AdminGroupStatus status = !row.active()
                ? AdminGroupStatus.ARCHIVED
                : row.headmanFio() == null ? AdminGroupStatus.DRAFT : AdminGroupStatus.ACTIVE;
        String draftReason = status == AdminGroupStatus.DRAFT
                ? "Староста не назначен" : null;
        return new AdminGroupResponse(
                row.id(), row.name(), alphabeticCode, numericCode, currentCourse,
                duration, durationStatus, status, draftReason, row.studentCount(),
                row.headmanFio(), row.createdAt());
    }

    public AdminGroupResponse toCreatedRegistryResponse(Group entity) {
        return new AdminGroupResponse(
                entity.getId(), entity.getName(), entity.getAlphabeticCode(),
                entity.getNumericCode(), entity.getCurrentCourse(),
                entity.getTrainingDurationYears(), entity.getDurationStatus(),
                AdminGroupStatus.DRAFT, "Староста не назначен", 0L, null,
                entity.getCreatedAt());
    }

    private LegacyCode fallback(GroupRegistryReadRepository.GroupRegistryRow row) {
        String source = row.name();
        if (source == null) return LegacyCode.empty();
        int archivedSuffix = source.indexOf(" (выпуск ");
        String activeName = archivedSuffix > 0 ? source.substring(0, archivedSuffix) : source;
        return GroupCodeRules.legacy(activeName)
                .map(code -> new LegacyCode(
                        code.alphabeticCode(), code.numericCode(), code.currentCourse(),
                        code.trainingDurationYears(), "KNOWN"))
                .orElseGet(LegacyCode::empty);
    }

    private LegacyCode storedCode(GroupRegistryReadRepository.GroupRegistryRow row) {
        if (!"KNOWN".equals(row.durationStatus())
                || row.alphabeticCode() == null
                || row.numericCode() == null
                || row.currentCourse() == null
                || row.trainingDurationYears() == null) {
            return LegacyCode.empty();
        }
        try {
            GroupCodeRules.CanonicalCode code = GroupCodeRules.fromParts(
                    row.alphabeticCode(), row.numericCode(), row.trainingDurationYears());
            if (code.currentCourse() != row.currentCourse()) {
                return LegacyCode.empty();
            }
            if (!code.name().equals(activeName(row.name()))) {
                return LegacyCode.empty();
            }
            return new LegacyCode(code.alphabeticCode(), code.numericCode(),
                    code.currentCourse(), code.trainingDurationYears(), "KNOWN");
        } catch (RuntimeException ignored) {
            return LegacyCode.empty();
        }
    }

    private static String activeName(String name) {
        if (name == null) return null;
        int archivedSuffix = name.indexOf(" (выпуск ");
        return archivedSuffix > 0 ? name.substring(0, archivedSuffix) : name;
    }

    private record LegacyCode(String alphabeticCode, String numericCode,
                              Integer currentCourse, Integer trainingDurationYears,
                              String legacyStatus) {
        static LegacyCode empty() {
            return new LegacyCode(null, null, null, null, "LEGACY_UNKNOWN");
        }

        boolean isKnown() {
            return trainingDurationYears != null;
        }

        String durationStatus(String stored) {
            return trainingDurationYears != null ? "KNOWN"
                    : (stored == null || stored.isBlank() ? legacyStatus : stored);
        }
    }
}
