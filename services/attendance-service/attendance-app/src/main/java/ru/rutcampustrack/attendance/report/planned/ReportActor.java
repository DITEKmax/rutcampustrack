package ru.rutcampustrack.attendance.report.planned;

import ru.rutcampustrack.attendance.contract.enums.UserRole;
import ru.rutcampustrack.attendance.security.RequestContext;

/** Read-only actor facts. An admitted plan never creates or installs an HTTP RequestContext. */
public interface ReportActor {
    Long getUserId();

    UserRole getRole();

    Long getGroupId();

    boolean isHeadman();

    static ReportActor live(RequestContext context) {
        if (context == null) return null;
        return new ReportActor() {
            @Override
            public Long getUserId() {
                return context.getUserId();
            }

            @Override
            public UserRole getRole() {
                return context.getRole();
            }

            @Override
            public Long getGroupId() {
                return context.getGroupId();
            }

            @Override
            public boolean isHeadman() {
                return context.isHeadman();
            }
        };
    }
}
