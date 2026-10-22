package ru.rutcampustrack.academic.identityoperation;

import java.time.LocalDate;
import java.util.List;

/**
 * Fixed nonsecret local material needed to replay the six existing identity business operations.
 */
public record AcademicIdentityLocalCommand(
        ApiIntent intent,
        Profile profile,
        LocalDate effectiveDate,
        LocalDate initialMembershipDate,
        Long sourceGroupId,
        Long destinationGroupId,
        Long headmanGroupId,
        Long expectedHeadmanId,
        Long candidateUserId,
        Long enrollmentSemesterId,
        String reason,
        List<GroupRevision> groups,
        ArchiveAdmission archiveAdmission) {
    public AcademicIdentityLocalCommand {
        if (intent == null) throw new IllegalArgumentException("nonsecret API intent required");
        groups = groups == null ? List.of() : List.copyOf(groups);
        long previous = 0;
        for (var group : groups) {
            if (group.groupId() <= previous || group.expectedRevision() <= 0) {
                throw new IllegalArgumentException("Canonical group revisions required");
            }
            previous = group.groupId();
        }
        if (reason != null && reason.length() > 1000)
            throw new IllegalArgumentException("Reason too long");
    }

    public AcademicIdentityLocalCommand(
            ApiIntent intent,
            Profile profile,
            LocalDate effectiveDate,
            LocalDate initialMembershipDate,
            Long sourceGroupId,
            Long destinationGroupId,
            Long headmanGroupId,
            Long expectedHeadmanId,
            Long candidateUserId,
            Long enrollmentSemesterId,
            String reason,
            List<GroupRevision> groups) {
        this(
                intent,
                profile,
                effectiveDate,
                initialMembershipDate,
                sourceGroupId,
                destinationGroupId,
                headmanGroupId,
                expectedHeadmanId,
                candidateUserId,
                enrollmentSemesterId,
                reason,
                groups,
                null);
    }

    public record ArchiveAdmission(
            String previewDigest, String expectedLocalDigest, boolean requiresPassword) {
        public ArchiveAdmission {
            if (previewDigest == null
                    || !previewDigest.matches("[0-9a-f]{64}")
                    || expectedLocalDigest == null
                    || !expectedLocalDigest.matches("[0-9a-f]{64}"))
                throw new IllegalArgumentException("exact archive preview admission required");
        }
    }

    /** A retry may refresh admission evidence, never the saved domain action or dated intent. */
    public AcademicIdentityLocalCommand forReconciliation(ApiIntent retry, ArchiveAdmission proof) {
        return new AcademicIdentityLocalCommand(
                retry,
                profile,
                effectiveDate,
                initialMembershipDate,
                sourceGroupId,
                destinationGroupId,
                headmanGroupId,
                expectedHeadmanId,
                candidateUserId,
                enrollmentSemesterId,
                reason,
                groups,
                proof);
    }

    public record Profile(
            long userId,
            String lastName,
            String firstName,
            String middleName,
            String email,
            String phone,
            String employeeNumber,
            String avatarId,
            String directoryStatus) {
        public Profile(
                long userId,
                String lastName,
                String firstName,
                String middleName,
                String email,
                String phone,
                String employeeNumber,
                String avatarId) {
            this(
                    userId,
                    lastName,
                    firstName,
                    middleName,
                    email,
                    phone,
                    employeeNumber,
                    avatarId,
                    "ACTIVE");
        }

        public Profile {
            if (directoryStatus == null
                    || !List.of("ACTIVE", "EXPELLED", "SUSPENDED", "ARCHIVED")
                            .contains(directoryStatus))
                throw new IllegalArgumentException("valid directory status required");
            if (userId < 0
                    || lastName == null
                    || lastName.isBlank()
                    || lastName.length() > 128
                    || firstName == null
                    || firstName.isBlank()
                    || firstName.length() > 128
                    || middleName != null && middleName.length() > 128
                    || employeeNumber != null && employeeNumber.length() > 32
                    || avatarId != null && avatarId.length() > 32) {
                throw new IllegalArgumentException("Valid profile fields required");
            }
        }
    }

    public record GroupRevision(long groupId, long expectedRevision) {}

    public record ApiIntent(
            String action, long targetId, String requestDigest, String admissionPermission) {
        public ApiIntent(String action, long targetId, String requestDigest) {
            this(action, targetId, requestDigest, null);
        }

        public ApiIntent withAdmissionPermission(String capability) {
            return new ApiIntent(action, targetId, requestDigest, capability);
        }

        public ApiIntent {
            if (!List.of(
                                    "CREATE",
                                    "PROFILE",
                                    "ROLE",
                                    "TRANSFER",
                                    "HEADMAN_REPLACE",
                                    "ARCHIVE",
                                    "RESTORE",
                                    "ADMIN_ACCESS",
                                    "TELEGRAM_BINDING",
                                    "RECONCILE",
                                    "GROUP_DEACTIVATE",
                                    "GROUP_RESTORE",
                                    "GROUP_MANUAL_CYCLE",
                                    "GROUP_AUTOMATIC_CYCLE",
                                    "GROUP_RECONCILE")
                            .contains(action)
                    || targetId < 0
                    || requestDigest == null
                    || !requestDigest.matches("[0-9a-f]{64}")
                    || admissionPermission != null
                            && !List.of("USERS_MANAGE", "GROUPS_MANAGE")
                                    .contains(admissionPermission))
                throw new IllegalArgumentException("canonical nonsecret request intent required");
        }
    }
}
