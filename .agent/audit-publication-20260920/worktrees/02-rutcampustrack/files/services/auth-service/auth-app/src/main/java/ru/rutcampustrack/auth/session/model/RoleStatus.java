package ru.rutcampustrack.auth.session.model;

/**
 * Status belongs to one role grant. Terminal statuses remain selectable for
 * the owner's read-only view; suspended grants are fail-closed.
 */
public enum RoleStatus {
    ACTIVE(false, true),
    EXPELLED(true, true),
    GRADUATED(true, true),
    SUSPENDED(false, false),
    ARCHIVED(true, true);

    private final boolean readOnly;
    private final boolean selectable;

    RoleStatus(boolean readOnly, boolean selectable) {
        this.readOnly = readOnly;
        this.selectable = selectable;
    }

    public boolean isReadOnly() {
        return readOnly;
    }

    public boolean isSelectable() {
        return selectable;
    }
}
