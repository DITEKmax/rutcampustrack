package ru.rutcampustrack.auth.session.model;

/**
 * Status belongs to one role grant. Only ACTIVE is selectable; terminal and
 * suspended grants remain visible for the owner's read-only view.
 */
public enum RoleStatus {
    ACTIVE(false, true),
    EXPELLED(true, false),
    GRADUATED(true, false),
    SUSPENDED(true, false),
    DISMISSED(true, false),
    ARCHIVED(true, false);

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
