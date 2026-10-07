package ru.rutcampustrack.academic.admin;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.http.HttpStatus;
import ru.rutcampustrack.shared.security.admin.*;
import ru.rutcampustrack.academic.contract.dto.user.AdminAccessInput;
import ru.rutcampustrack.academic.security.RequestContext;
import ru.rutcampustrack.academic.repository.UserRoleGrantWriter;

@Service
public class AdminAccessService {
    private final JdbcTemplate jdbc;
    private final AdminAccessJdbcAuthority authority;
    private final AcademicAdminAccess access;
    private final RequestContext context;
    private final UserRoleGrantWriter grants;
    public AdminAccessService(JdbcTemplate jdbc,AdminAccessJdbcAuthority authority,AcademicAdminAccess access,
                              RequestContext context,UserRoleGrantWriter grants) {
        this.jdbc=jdbc;this.authority=authority;this.access=access;this.context=context;this.grants=grants;
    }
    public AdminAccessProfile get(long target) { access.requireFull();return authority.profile(target); }
    @Transactional
    public AdminAccessProfile put(long target,AdminAccessInput input,Long expectedVersion) {
        var permissions=validate(input);
        lock(target);
        var current=authority.profile(target);
        compare(current,expectedVersion);
        if(current.full() && !"FULL".equals(input.mode())) authority.protectLastFull(target);
        save(target,input.mode(),permissions,current.version()+1);
        grants.upsertRole(target,"admin","active",null);
        return authority.profile(target);
    }
    @Transactional
    public void revoke(long target,Long expectedVersion) {
        lock(target);
        var current=authority.profile(target);
        compare(current,expectedVersion);
        if(!current.assigned()) return;
        authority.protectLastFull(target);
        // Grant trigger persists the tombstone/version; writer clears only selected ADMIN sessions.
        grants.upsertRole(target,"admin","archived",null);
    }
    public java.util.List<String> validate(AdminAccessInput input) {
        if(input==null) throw AdminAccessException.invalid();
        return AdminPermission.validate(input.mode(),input.permissions());
    }
    /** Invoked inside createUser's global-locked transaction after the new user is flushed. */
    public void assignCreated(long target,AdminAccessInput input) {
        access.requireFull();
        save(target,input.mode(),validate(input),1);
        grants.upsertRole(target,"admin","active",null);
    }
    private void lock(long target) {
        if(target<=0) throw AdminAccessException.invalid();
        authority.lockGlobal();
        if(context.getUserId()==null) throw AdminAccessException.denied();
        authority.lockUsers(context.getUserId(),target);
        access.requireFull();
        if(target==context.getUserId()) throw new AdminAccessException(HttpStatus.FORBIDDEN,"ADMIN_ACCESS_SELF_CHANGE_DENIED");
        String status=jdbc.query("SELECT status::text FROM users WHERE id=?",rs->rs.next()?rs.getString(1):null,target);
        if(status==null) throw new org.springframework.web.server.ResponseStatusException(HttpStatus.NOT_FOUND,"Пользователь не найден");
        if("archived".equals(status)) throw new AdminAccessException(HttpStatus.FORBIDDEN,"ADMIN_TARGET_PROTECTED");
    }
    private void compare(AdminAccessProfile current,Long expected) {
        if(expected==null || expected<0) throw AdminAccessException.invalid();
        if(current.version()!=expected) throw new AdminAccessException(HttpStatus.CONFLICT,"ADMIN_ACCESS_CONFLICT",current);
    }
    private void save(long target,String mode,java.util.List<String> permissions,long version) {
        jdbc.update(con -> {
            var statement=con.prepareStatement("""
              INSERT INTO admin_access_profiles(user_id,assigned,mode,permissions,version) VALUES (?,TRUE,?,?,?)
              ON CONFLICT(user_id) DO UPDATE SET assigned=TRUE,mode=EXCLUDED.mode,permissions=EXCLUDED.permissions,
                version=EXCLUDED.version,updated_at=NOW()
              """);
            statement.setLong(1,target);statement.setString(2,mode);
            statement.setArray(3,con.createArrayOf("text",permissions.toArray(String[]::new)));statement.setLong(4,version);
            return statement;
        });
    }
}
