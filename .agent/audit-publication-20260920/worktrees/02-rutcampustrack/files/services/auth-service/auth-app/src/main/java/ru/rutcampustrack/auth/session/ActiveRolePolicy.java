package ru.rutcampustrack.auth.session;

import ru.rutcampustrack.auth.session.model.AuthRole;
import ru.rutcampustrack.auth.session.model.RoleGrant;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/** Pure role-grant validation and selection rules. */
public final class ActiveRolePolicy {

    public Evaluation evaluate(long userId, Collection<RoleGrant> grants) {
        if (userId <= 0 || grants == null) {
            return Evaluation.failure(Code.INVALID_GRANT);
        }

        List<RoleGrant> copy = new ArrayList<>();
        Set<Long> grantIds = new HashSet<>();
        Set<AuthRole> roles = new HashSet<>();
        for (RoleGrant grant : grants) {
            if (grant == null || !grant.belongsTo(userId)) {
                return Evaluation.failure(grant == null ? Code.INVALID_GRANT : Code.FOREIGN_GRANT);
            }
            if (!grantIds.add(grant.grantId()) || !roles.add(grant.role())) {
                return Evaluation.failure(Code.DUPLICATE_GRANT);
            }
            copy.add(grant);
        }

        RoleGrant defaultGrant = copy.stream()
                .filter(RoleGrant::isSelectable)
                .filter(grant -> grant.role() == AuthRole.STUDENT)
                .findFirst()
                .orElseGet(() -> copy.stream()
                        .filter(RoleGrant::isSelectable)
                        .filter(grant -> grant.role() == AuthRole.TEACHER)
                        .findFirst()
                        .orElse(null));
        return Evaluation.success(copy, defaultGrant);
    }

    public Selection select(long userId, AuthRole role, Collection<RoleGrant> grants) {
        Evaluation evaluation = evaluate(userId, grants);
        return select(evaluation, role);
    }

    private Selection select(Evaluation evaluation, AuthRole role) {
        Objects.requireNonNull(evaluation, "evaluation");
        if (evaluation.code() != Code.OK) {
            return Selection.failure(evaluation.code());
        }
        if (role == null) {
            return Selection.failure(Code.ROLE_NOT_GRANTED);
        }
        RoleGrant grant = evaluation.grants().stream()
                .filter(candidate -> candidate.role() == role)
                .findFirst()
                .orElse(null);
        if (grant == null) {
            return Selection.failure(Code.ROLE_NOT_GRANTED);
        }
        if (!grant.isSelectable()) {
            return Selection.failure(Code.ROLE_NOT_SELECTABLE);
        }
        return Selection.success(grant);
    }

    public enum Code {
        OK,
        INVALID_GRANT,
        FOREIGN_GRANT,
        DUPLICATE_GRANT,
        ROLE_NOT_GRANTED,
        ROLE_NOT_SELECTABLE
    }

    public record Evaluation(Code code, List<RoleGrant> grants, RoleGrant defaultGrant) {
        public Evaluation {
            code = Objects.requireNonNull(code, "code");
            grants = List.copyOf(Objects.requireNonNull(grants, "grants"));
            if (code != Code.OK && (!grants.isEmpty() || defaultGrant != null)) {
                throw new IllegalArgumentException("failed evaluation cannot expose grants");
            }
            if (defaultGrant != null && grants.stream().noneMatch(grant -> grant.equals(defaultGrant))) {
                throw new IllegalArgumentException("default grant must be in grants");
            }
        }

        public static Evaluation success(List<RoleGrant> grants, RoleGrant defaultGrant) {
            return new Evaluation(Code.OK, grants, defaultGrant);
        }

        public static Evaluation failure(Code code) {
            return new Evaluation(Objects.requireNonNull(code, "code"), List.of(), null);
        }

        public boolean succeeded() {
            return code == Code.OK;
        }
    }

    public record Selection(Code code, RoleGrant grant) {
        public Selection {
            code = Objects.requireNonNull(code, "code");
            if ((code == Code.OK) != (grant != null)) {
                throw new IllegalArgumentException("selection must contain grant only on success");
            }
        }

        public static Selection success(RoleGrant grant) {
            return new Selection(Code.OK, Objects.requireNonNull(grant, "grant"));
        }

        public static Selection failure(Code code) {
            return new Selection(Objects.requireNonNull(code, "code"), null);
        }

        public boolean succeeded() {
            return code == Code.OK;
        }
    }
}
