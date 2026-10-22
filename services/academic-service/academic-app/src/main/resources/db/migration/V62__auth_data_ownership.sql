-- One PG transaction, two data owners. Bootstrap alone provisions roles; Flyway fails closed.
DO $roles$
BEGIN
    IF current_schema()<>'public' THEN
        RAISE EXCEPTION 'Auth ownership requires the public application schema';
    END IF;
    IF NOT EXISTS(SELECT FROM pg_catalog.pg_roles WHERE rolname='rct_auth_runtime')
       OR NOT EXISTS(SELECT FROM pg_catalog.pg_roles WHERE rolname='rct_academic_runtime')
       OR NOT EXISTS(SELECT FROM pg_catalog.pg_roles WHERE rolname='rct_auth_command_owner' AND NOT rolcanlogin
                     AND NOT rolsuper AND NOT rolcreaterole AND NOT rolcreatedb AND NOT rolbypassrls)
       OR NOT pg_catalog.pg_has_role(current_user,'rct_auth_command_owner','MEMBER') THEN
        RAISE EXCEPTION 'Auth ownership roles must be provisioned before migration';
    END IF;
    IF EXISTS(SELECT FROM pg_catalog.pg_roles WHERE rolname IN ('rct_auth_runtime','rct_academic_runtime')
              AND (rolsuper OR rolcreatedb OR rolcreaterole OR rolreplication OR rolbypassrls))
       OR pg_catalog.pg_has_role('rct_auth_runtime','rct_auth_command_owner','MEMBER')
       OR pg_catalog.pg_has_role('rct_academic_runtime','rct_auth_command_owner','MEMBER') THEN
        RAISE EXCEPTION 'Runtime identities must not inherit command-owner authority';
    END IF;
END $roles$;

-- PostgreSQL's global default PUBLIC EXECUTE must be removed at each actual function-creating owner.
-- Per-schema REVOKE alone cannot override that global default.
ALTER DEFAULT PRIVILEGES FOR ROLE rct_migrator REVOKE EXECUTE ON FUNCTIONS FROM PUBLIC;
ALTER DEFAULT PRIVILEGES FOR ROLE rct_auth_command_owner REVOKE EXECUTE ON FUNCTIONS FROM PUBLIC;
CREATE SCHEMA auth_api AUTHORIZATION rct_auth_command_owner;
REVOKE ALL ON SCHEMA auth_api FROM PUBLIC;
GRANT USAGE ON SCHEMA auth_api TO rct_auth_runtime, rct_academic_runtime;

-- An Academic profile INSERT gets its stable ID before Auth initialization, in this transaction only.
-- Existing fully initialized inserts remain compatible. No incomplete profile may commit.
ALTER TABLE public.users ALTER COLUMN login DROP NOT NULL;
CREATE FUNCTION auth_api.require_complete_user_identity() RETURNS trigger LANGUAGE plpgsql SECURITY DEFINER SET search_path=pg_catalog AS $$
BEGIN
    IF NEW.login IS NULL AND EXISTS(SELECT FROM public.users u WHERE u.id=NEW.id) THEN
        IF NOT EXISTS(SELECT FROM public.users u WHERE u.id=NEW.id AND u.login IS NOT NULL
                      AND u.password_hash IS NOT NULL AND u.initial_password IS NOT NULL
                      AND NOT u.password_changed AND u.generated_password_origin='initial'
                      AND EXISTS(SELECT FROM public.user_role_grants g WHERE g.user_id=u.id
                                 AND g.role=u.role::text)) THEN
            RAISE EXCEPTION 'complete Auth identity and grant required' USING ERRCODE='23514';
        END IF;
    END IF;
    RETURN NULL;
END $$;
CREATE CONSTRAINT TRIGGER users_complete_identity AFTER INSERT OR UPDATE ON public.users
    DEFERRABLE INITIALLY DEFERRED FOR EACH ROW EXECUTE FUNCTION auth_api.require_complete_user_identity();

CREATE OR REPLACE FUNCTION public.bump_user_roles_version()
RETURNS TRIGGER
LANGUAGE plpgsql
AS $$
BEGIN
    IF TG_OP = 'UPDATE' AND NEW.user_id IS DISTINCT FROM OLD.user_id THEN
        RAISE EXCEPTION 'user_role_grants.user_id is immutable';
    END IF;

    IF TG_OP = 'INSERT' THEN
        UPDATE public.users
        SET roles_version = roles_version + 1,
            updated_at = updated_at
        WHERE id = NEW.user_id;
        RETURN NEW;
    END IF;

    IF TG_OP = 'DELETE' THEN
        UPDATE public.users
        SET roles_version = roles_version + 1,
            updated_at = updated_at
        WHERE id = OLD.user_id;
        RETURN OLD;
    END IF;

    IF NEW.role IS DISTINCT FROM OLD.role
       OR NEW.status IS DISTINCT FROM OLD.status
       OR NEW.group_id IS DISTINCT FROM OLD.group_id THEN
        UPDATE public.users
        SET roles_version = roles_version + 1,
            updated_at = updated_at
        WHERE id = NEW.user_id;
    END IF;
    RETURN NEW;
END
$$;

CREATE OR REPLACE FUNCTION public.admin_access_profile_version_guard() RETURNS TRIGGER LANGUAGE plpgsql AS $$
BEGIN
 IF TG_OP='UPDATE' THEN
   IF NEW.user_id IS DISTINCT FROM OLD.user_id OR NEW.version<>OLD.version+1 THEN
      RAISE EXCEPTION 'admin access version must increase';
   END IF;
 END IF;
 UPDATE public.users SET roles_version=roles_version+1 WHERE id=NEW.user_id;
 RETURN NEW;
END $$;

CREATE OR REPLACE FUNCTION public.admin_access_grant_lifecycle() RETURNS TRIGGER LANGUAGE plpgsql AS $$
BEGIN
 IF NEW.role='admin' THEN
   IF NEW.status='active' AND NOT EXISTS(SELECT 1 FROM public.admin_access_profiles p
      WHERE p.user_id=NEW.user_id AND p.assigned) THEN
     RAISE EXCEPTION 'explicit admin access profile required';
   END IF;
   IF NEW.status<>'active' THEN
     UPDATE public.admin_access_profiles SET assigned=FALSE,mode=NULL,permissions='{}',version=version+1,updated_at=NOW()
      WHERE user_id=NEW.user_id AND assigned;
   END IF;
 END IF;
 RETURN NEW;
END $$;

CREATE OR REPLACE FUNCTION public.advance_user_credential_generations() RETURNS TRIGGER
LANGUAGE plpgsql AS $$
BEGIN
    IF NEW.id IS DISTINCT FROM OLD.id THEN
        RAISE EXCEPTION 'user credential owner identity is immutable';
    END IF;
    IF NEW.credentials_version IS DISTINCT FROM OLD.credentials_version
       OR NEW.telegram_binding_version IS DISTINCT FROM OLD.telegram_binding_version THEN
        RAISE EXCEPTION 'user credential generations are maintained by tuple changes';
    END IF;
    -- First initialization is the original INSERT generation, not a password reset or rebinding.
    IF OLD.login IS NULL AND NEW.login IS NOT NULL THEN
        RETURN NEW;
    END IF;
    IF ROW(NEW.password_hash, NEW.initial_password, NEW.password_changed)
       IS DISTINCT FROM ROW(OLD.password_hash, OLD.initial_password, OLD.password_changed) THEN
        IF OLD.credentials_version = 9223372036854775807 THEN
            RAISE EXCEPTION 'user credential generation exhausted';
        END IF;
        NEW.credentials_version := OLD.credentials_version + 1;
    END IF;
    IF NEW.telegram_id IS DISTINCT FROM OLD.telegram_id THEN
        IF OLD.telegram_binding_version = 9223372036854775807 THEN
            RAISE EXCEPTION 'user Telegram binding generation exhausted';
        END IF;
        NEW.telegram_binding_version := OLD.telegram_binding_version + 1;
    END IF;
    RETURN NEW;
END
$$;

CREATE OR REPLACE FUNCTION public.invalidate_user_bot_credential_messages() RETURNS TRIGGER
LANGUAGE plpgsql AS $$
DECLARE
    original_user_id BIGINT;
    invalidating_generation BIGINT;
BEGIN
    IF TG_OP = 'DELETE' THEN
        original_user_id := OLD.id;
        invalidating_generation := OLD.credentials_version;
    ELSE
        IF NEW.credentials_version = OLD.credentials_version
           AND NEW.telegram_binding_version = OLD.telegram_binding_version
           AND NOT (NEW.status::text = 'archived' AND OLD.status::text <> 'archived') THEN
            RETURN NEW;
        END IF;
        original_user_id := NEW.id;
        invalidating_generation := NEW.credentials_version;
    END IF;
    -- The user row lock is already held; consumers acquire user before card locks.
    -- Preserve the first fence so an earlier event still finds an older invalidated card.
    -- In-flight/uncertain render state and its stable attempt revision survive invalidation.
    UPDATE public.bot_credential_messages
       SET cleanup_state = 'PENDING',
           invalidated_by_credentials_version = invalidating_generation,
           invalidated_at = NOW(),
           record_revision = record_revision + 1,
           updated_at = NOW()
     WHERE user_id = original_user_id AND cleanup_state = 'NOT_REQUIRED';
    IF TG_OP = 'DELETE' THEN
        RETURN OLD;
    END IF;
    RETURN NEW;
END
$$;

-- Same closed profile catalogue/dependencies as AdminPermission.validate; malformed state grants nothing.
CREATE FUNCTION auth_api.valid_profile(access_mode text, permissions text[]) RETURNS boolean
LANGUAGE sql IMMUTABLE SET search_path=pg_catalog AS $$
    SELECT coalesce(permissions IS NOT NULL AND (
        (access_mode='FULL' AND cardinality(permissions)=0) OR
        (access_mode='LIMITED' AND cardinality(permissions)>0
         AND array_position(permissions,NULL) IS NULL
         AND cardinality(permissions)=(SELECT count(DISTINCT p) FROM unnest(permissions) p)
         AND permissions <@ ARRAY['DASHBOARD_VIEW','USERS_VIEW','USERS_MANAGE','USER_CREDENTIALS_MANAGE',
             'USERS_ARCHIVE','GROUPS_VIEW','GROUPS_MANAGE','SEMESTERS_VIEW','SEMESTERS_MANAGE',
             'CAMPUS_MAP_MANAGE','DATA_DELETE']::text[]
         AND (NOT permissions && ARRAY['USERS_MANAGE','USER_CREDENTIALS_MANAGE','USERS_ARCHIVE']::text[] OR 'USERS_VIEW'=ANY(permissions))
         AND (NOT 'GROUPS_MANAGE'=ANY(permissions) OR 'GROUPS_VIEW'=ANY(permissions))
         AND (NOT 'SEMESTERS_MANAGE'=ANY(permissions) OR 'SEMESTERS_VIEW'=ANY(permissions)))),false)
$$;

-- Admission returns one boolean, never a session or refresh-token projection.
CREATE FUNCTION auth_api.current_admin(actor bigint, roles_version bigint, sid uuid, session_version bigint)
RETURNS boolean LANGUAGE sql STABLE SECURITY DEFINER SET search_path=pg_catalog AS $$
    SELECT actor>0 AND roles_version>0 AND session_version>0 AND EXISTS(
        SELECT FROM public.users u JOIN public.auth_sessions s ON s.user_id=u.id
        JOIN public.user_role_grants g ON g.id=s.active_role_grant_id AND g.user_id=u.id
        JOIN public.admin_access_profiles p ON p.user_id=u.id
        WHERE u.id=actor AND u.login IS NOT NULL AND u.status::text<>'archived'
          AND u.roles_version=current_admin.roles_version AND s.sid=current_admin.sid
          AND s.session_version=current_admin.session_version AND s.revoked_at IS NULL
          AND s.refresh_expires_at>now() AND g.role='admin' AND g.status='active' AND p.assigned
          AND auth_api.valid_profile(p.mode,p.permissions))
$$;

-- Private helpers have no runtime EXECUTE grants. Lock order is global -> ordered users -> grants/sessions/cards.
CREATE FUNCTION auth_api.require_actor(actor bigint, rv bigint, sid uuid, sv bigint, target bigint, permission text,
                                      full_only boolean DEFAULT false)
RETURNS void LANGUAGE plpgsql SECURITY DEFINER SET search_path=pg_catalog AS $$
DECLARE full_access boolean; permissions text[];
BEGIN
    PERFORM pg_advisory_xact_lock(724830210081::bigint);
    PERFORM u.id FROM public.users u WHERE u.id=actor OR u.id=target ORDER BY u.id FOR UPDATE;
    IF NOT coalesce(auth_api.current_admin(actor,rv,sid,sv),false) THEN
        RAISE EXCEPTION 'ADMIN_PERMISSION_DENIED' USING ERRCODE='42501';
    END IF;
    SELECT p.mode='FULL',p.permissions INTO full_access,permissions
      FROM public.admin_access_profiles p WHERE p.user_id=actor AND p.assigned;
    IF NOT full_access AND (full_only OR permission='ADMIN_ACCESS_MANAGE') THEN
        RAISE EXCEPTION 'ADMIN_FULL_REQUIRED' USING ERRCODE='42501';
    END IF;
    IF NOT full_access AND NOT (coalesce(permission=ANY(permissions),false)
        OR (permission='HEADMAN_RELEASE' AND permissions && ARRAY['USERS_MANAGE','GROUPS_MANAGE']::text[])) THEN
        RAISE EXCEPTION 'ADMIN_PERMISSION_DENIED' USING ERRCODE='42501';
    END IF;
    IF NOT full_access AND EXISTS(SELECT FROM public.user_role_grants g
                                 WHERE g.user_id=target AND g.role='admin' AND g.status='active') THEN
        RAISE EXCEPTION 'ADMIN_TARGET_PROTECTED' USING ERRCODE='42501';
    END IF;
END $$;

CREATE FUNCTION auth_api.protect_last_full(target bigint) RETURNS void
LANGUAGE plpgsql SECURITY DEFINER SET search_path=pg_catalog AS $$
BEGIN
    IF EXISTS(SELECT FROM public.admin_access_profiles p JOIN public.user_role_grants g ON g.user_id=p.user_id
              WHERE p.user_id=target AND p.assigned AND p.mode='FULL' AND g.role='admin' AND g.status='active')
       AND (SELECT count(*) FROM public.admin_access_profiles p JOIN public.users u ON u.id=p.user_id
            JOIN public.user_role_grants g ON g.user_id=p.user_id AND g.role='admin'
            WHERE p.assigned AND p.mode='FULL' AND g.status='active' AND u.status::text<>'archived')<=1 THEN
        RAISE EXCEPTION 'LAST_FULL_ADMIN' USING ERRCODE='23514';
    END IF;
END $$;

CREATE FUNCTION auth_api.guard_admin_deactivation(actor bigint, target bigint) RETURNS void
LANGUAGE plpgsql SECURITY DEFINER SET search_path=pg_catalog AS $$
BEGIN
    IF EXISTS(SELECT FROM public.user_role_grants g WHERE g.user_id=target AND g.role='admin' AND g.status='active') THEN
        IF actor=target THEN
            RAISE EXCEPTION 'ADMIN_ACCESS_SELF_CHANGE_DENIED' USING ERRCODE='42501';
        END IF;
        PERFORM auth_api.protect_last_full(target);
    END IF;
END $$;

CREATE FUNCTION auth_api.clear_selections(target bigint) RETURNS void
LANGUAGE sql SECURITY DEFINER SET search_path=pg_catalog AS $$
    UPDATE public.auth_sessions s SET active_role_grant_id=NULL,session_version=s.session_version+1
    FROM public.user_role_grants g WHERE s.user_id=target AND s.revoked_at IS NULL
      AND s.active_role_grant_id=g.id AND g.user_id=s.user_id AND g.status<>'active'
$$;

CREATE FUNCTION auth_api.put_grant(target bigint, role_code text, status_code text, group_scope bigint)
RETURNS void LANGUAGE plpgsql SECURITY DEFINER SET search_path=pg_catalog AS $$
BEGIN
    IF role_code NOT IN ('student','teacher','headman','admin')
       OR status_code NOT IN ('active','expelled','graduated','suspended','archived') THEN
        RAISE EXCEPTION 'INVALID_ROLE_GRANT' USING ERRCODE='22023';
    END IF;
    INSERT INTO public.user_role_grants(user_id,role,status,group_id,created_at,updated_at)
      VALUES(target,role_code,status_code,group_scope,now(),now())
      ON CONFLICT(user_id,role) DO UPDATE SET status=excluded.status,group_id=excluded.group_id,updated_at=excluded.updated_at;
END $$;

CREATE FUNCTION auth_api.initialize_identity(actor bigint, rv bigint, sid uuid, sv bigint, target bigint,
    new_login text, prepared_hash text, initial text, role_code text, status_code text, telegram bigint, telegram_name text)
RETURNS void LANGUAGE plpgsql SECURITY DEFINER SET search_path=pg_catalog AS $$
BEGIN
    PERFORM auth_api.require_actor(actor,rv,sid,sv,target,'USERS_MANAGE',role_code='admin');
    IF new_login IS NULL OR length(new_login) NOT BETWEEN 1 AND 32
       OR prepared_hash IS NULL OR length(prepared_hash) NOT BETWEEN 1 AND 255
       OR initial IS NULL OR length(initial) NOT BETWEEN 1 AND 128
       OR role_code NOT IN ('admin','teacher','student') OR status_code<>'active' THEN
        RAISE EXCEPTION 'INVALID_INITIAL_IDENTITY' USING ERRCODE='22023';
    END IF;
    UPDATE public.users SET login=new_login,password_hash=prepared_hash,initial_password=initial,
        password_changed=false,generated_password_origin='initial',role=role_code::public.user_role,
        status=status_code::public.account_status,telegram_id=telegram,telegram_username=telegram_name,is_headman=false
      WHERE id=target AND login IS NULL;
    IF NOT FOUND THEN RAISE EXCEPTION 'IDENTITY_ALREADY_INITIALIZED' USING ERRCODE='23514'; END IF;
END $$;

CREATE FUNCTION auth_api.project_identity(actor bigint, rv bigint, sid uuid, sv bigint, target bigint,
    role_code text, status_code text, telegram bigint, telegram_name text)
RETURNS void LANGUAGE plpgsql SECURITY DEFINER SET search_path=pg_catalog AS $$
BEGIN
    PERFORM auth_api.require_actor(actor,rv,sid,sv,target,'USERS_MANAGE',role_code='admin');
    IF role_code NOT IN ('admin','teacher','student')
       OR status_code NOT IN ('active','expelled','suspended','archived')
       OR NOT EXISTS(SELECT FROM public.users u WHERE u.id=target AND u.login IS NOT NULL) THEN
        RAISE EXCEPTION 'INVALID_IDENTITY' USING ERRCODE='22023';
    END IF;
    IF status_code<>'active' THEN PERFORM auth_api.guard_admin_deactivation(actor,target); END IF;
    IF status_code='archived' THEN
        RAISE EXCEPTION 'EXPLICIT_ARCHIVE_REQUIRED' USING ERRCODE='42501';
    END IF;
    UPDATE public.users SET role=role_code::public.user_role,status=status_code::public.account_status,
        telegram_id=telegram,telegram_username=telegram_name WHERE id=target;
END $$;

CREATE FUNCTION auth_api.synchronize_identity(actor bigint, rv bigint, sid uuid, sv bigint, target bigint,
    role_code text, status_code text, telegram bigint, telegram_name text, headman boolean, archive boolean)
RETURNS void LANGUAGE plpgsql SECURITY DEFINER SET search_path=pg_catalog AS $$
DECLARE base_status text; student_group bigint; profile_group bigint;
BEGIN
    -- Archive is a distinct permission; never infer it from a broad profile-write grant.
    IF archive THEN
        PERFORM auth_api.require_actor(actor,rv,sid,sv,target,'USERS_ARCHIVE');
        IF actor=target THEN RAISE EXCEPTION 'USER_ARCHIVE_SELF_DENIED' USING ERRCODE='42501'; END IF;
        PERFORM auth_api.protect_last_full(target);
        IF status_code<>'archived' THEN RAISE EXCEPTION 'INVALID_ARCHIVE' USING ERRCODE='22023'; END IF;
        UPDATE public.users SET status='archived',is_headman=false WHERE id=target;
    ELSE
        IF headman AND NOT EXISTS(SELECT FROM public.users u WHERE u.id=target AND u.is_headman) THEN
            PERFORM auth_api.require_actor(actor,rv,sid,sv,target,'GROUPS_MANAGE');
        END IF;
        PERFORM auth_api.project_identity(actor,rv,sid,sv,target,role_code,status_code,telegram,telegram_name);
    END IF;
    SELECT u.group_id INTO profile_group FROM public.users u WHERE u.id=target;
    base_status:=status_code;
    IF NOT archive AND status_code='active' THEN
        SELECT coalesce((SELECT g.status FROM public.user_role_grants g WHERE g.user_id=target AND g.role=role_code),'active') INTO base_status;
    END IF;
    PERFORM auth_api.put_grant(target,role_code,base_status,profile_group);
    IF archive THEN
        UPDATE public.user_role_grants SET status='archived',updated_at=now() WHERE user_id=target;
    ELSE
        SELECT g.group_id INTO student_group FROM public.user_role_grants g JOIN public.users u ON u.id=g.user_id
          WHERE g.user_id=target AND g.role='student' AND g.status='active' AND g.group_id IS NOT NULL AND u.status::text<>'archived';
        IF headman AND student_group IS NOT NULL THEN
            PERFORM auth_api.put_grant(target,'headman','active',student_group);
        ELSE
            UPDATE public.user_role_grants SET status='suspended',updated_at=now() WHERE user_id=target AND role='headman';
        END IF;
        UPDATE public.users SET is_headman=(headman AND student_group IS NOT NULL) WHERE id=target;
    END IF;
    PERFORM auth_api.clear_selections(target);
END $$;

CREATE FUNCTION auth_api.write_grant(actor bigint, rv bigint, sid uuid, sv bigint, target bigint,
    role_code text, status_code text, group_scope bigint)
RETURNS void LANGUAGE plpgsql SECURITY DEFINER SET search_path=pg_catalog AS $$
BEGIN
    PERFORM auth_api.require_actor(actor,rv,sid,sv,target,'USERS_MANAGE');
    IF role_code='admin' THEN
        PERFORM auth_api.require_actor(actor,rv,sid,sv,target,'ADMIN_ACCESS_MANAGE',true);
        IF status_code<>'active' THEN PERFORM auth_api.guard_admin_deactivation(actor,target); END IF;
    ELSIF role_code NOT IN ('student','teacher') THEN
        RAISE EXCEPTION 'INVALID_MANAGED_ROLE' USING ERRCODE='22023';
    END IF;
    PERFORM auth_api.put_grant(target,role_code,status_code,group_scope);
    IF role_code='student' THEN
        IF status_code='active' AND group_scope IS NOT NULL AND EXISTS(
            SELECT FROM public.users u WHERE u.id=target AND u.is_headman AND u.status::text<>'archived') THEN
            PERFORM auth_api.put_grant(target,'headman','active',group_scope);
        ELSE
            UPDATE public.user_role_grants SET status='suspended',updated_at=now() WHERE user_id=target AND role='headman';
            UPDATE public.users SET is_headman=false WHERE id=target;
        END IF;
    END IF;
    PERFORM auth_api.clear_selections(target);
END $$;

CREATE FUNCTION auth_api.write_headman(actor bigint, rv bigint, sid uuid, sv bigint, target bigint, active boolean)
RETURNS void LANGUAGE plpgsql SECURITY DEFINER SET search_path=pg_catalog AS $$
DECLARE student_group bigint;
BEGIN
    -- Groups manages derived authority. Users may remove it while disabling a STUDENT grant.
    PERFORM auth_api.require_actor(actor,rv,sid,sv,target,
      CASE WHEN NOT active THEN 'HEADMAN_RELEASE'
           WHEN EXISTS(SELECT FROM public.users u WHERE u.id=target AND u.is_headman) THEN 'USERS_MANAGE'
           ELSE 'GROUPS_MANAGE' END);
    SELECT g.group_id INTO student_group FROM public.user_role_grants g JOIN public.users u ON u.id=g.user_id
      WHERE g.user_id=target AND g.role='student' AND g.status='active' AND g.group_id IS NOT NULL AND u.status::text<>'archived';
    IF active AND student_group IS NOT NULL THEN
        PERFORM auth_api.put_grant(target,'headman','active',student_group);
    ELSE
        UPDATE public.user_role_grants SET status='suspended',updated_at=now() WHERE user_id=target AND role='headman';
    END IF;
    UPDATE public.users SET is_headman=(active AND student_group IS NOT NULL) WHERE id=target;
    PERFORM auth_api.clear_selections(target);
END $$;

CREATE FUNCTION auth_api.restore_identity(actor bigint, rv bigint, sid uuid, sv bigint, target bigint)
RETURNS void LANGUAGE plpgsql SECURITY DEFINER SET search_path=pg_catalog AS $$
BEGIN
    PERFORM auth_api.require_actor(actor,rv,sid,sv,target,'USERS_ARCHIVE');
    UPDATE public.users SET status='active',is_headman=false,updated_at=now() WHERE id=target AND status::text='archived';
    IF NOT FOUND THEN RAISE EXCEPTION 'ARCHIVED_IDENTITY_REQUIRED' USING ERRCODE='23514'; END IF;
    UPDATE public.user_role_grants SET status='archived',updated_at=now() WHERE user_id=target AND status<>'archived';
    UPDATE public.user_role_grants SET group_id=NULL,updated_at=now() WHERE user_id=target AND role='student' AND group_id IS NOT NULL;
    UPDATE public.auth_sessions SET active_role_grant_id=NULL,session_version=session_version+1
      WHERE user_id=target AND revoked_at IS NULL AND active_role_grant_id IS NOT NULL;
END $$;

CREATE FUNCTION auth_api.write_admin_access(actor bigint, rv bigint, sid uuid, sv bigint, target bigint,
    access_mode text, permissions text[], expected_version bigint, revoke boolean)
RETURNS void LANGUAGE plpgsql SECURITY DEFINER SET search_path=pg_catalog AS $$
DECLARE actual_version bigint; was_full boolean;
BEGIN
    PERFORM auth_api.require_actor(actor,rv,sid,sv,target,'ADMIN_ACCESS_MANAGE',true);
    IF actor=target THEN RAISE EXCEPTION 'ADMIN_ACCESS_SELF_CHANGE_DENIED' USING ERRCODE='42501'; END IF;
    IF NOT EXISTS(SELECT FROM public.users u WHERE u.id=target AND u.login IS NOT NULL AND u.status::text<>'archived') THEN
        RAISE EXCEPTION 'ADMIN_TARGET_PROTECTED' USING ERRCODE='42501';
    END IF;
    SELECT p.version,p.assigned AND p.mode='FULL' INTO actual_version,was_full FROM public.admin_access_profiles p WHERE p.user_id=target;
    IF expected_version IS NULL OR expected_version<0 OR expected_version<>coalesce(actual_version,0) THEN
        RAISE EXCEPTION 'ADMIN_ACCESS_CONFLICT' USING ERRCODE='40001';
    END IF;
    IF revoke THEN
        PERFORM auth_api.protect_last_full(target);
        PERFORM auth_api.put_grant(target,'admin','archived',NULL);
    ELSE
        IF access_mode IS NULL OR permissions IS NULL OR access_mode NOT IN ('FULL','LIMITED')
           OR array_position(permissions,NULL) IS NOT NULL
           OR cardinality(permissions)<>(SELECT count(DISTINCT p) FROM unnest(permissions) p)
           OR (access_mode='FULL' AND cardinality(permissions)<>0)
           OR (access_mode='LIMITED' AND (cardinality(permissions)=0 OR NOT permissions <@ ARRAY[
               'DASHBOARD_VIEW','USERS_VIEW','USERS_MANAGE','USER_CREDENTIALS_MANAGE','USERS_ARCHIVE',
               'GROUPS_VIEW','GROUPS_MANAGE','SEMESTERS_VIEW','SEMESTERS_MANAGE','CAMPUS_MAP_MANAGE','DATA_DELETE']::text[]))
           OR (permissions && ARRAY['USERS_MANAGE','USER_CREDENTIALS_MANAGE','USERS_ARCHIVE']::text[] AND NOT 'USERS_VIEW'=ANY(permissions))
           OR ('GROUPS_MANAGE'=ANY(permissions) AND NOT 'GROUPS_VIEW'=ANY(permissions))
           OR ('SEMESTERS_MANAGE'=ANY(permissions) AND NOT 'SEMESTERS_VIEW'=ANY(permissions)) THEN
            RAISE EXCEPTION 'INVALID_ADMIN_ACCESS' USING ERRCODE='22023';
        END IF;
        IF was_full AND access_mode<>'FULL' THEN PERFORM auth_api.protect_last_full(target); END IF;
        INSERT INTO public.admin_access_profiles(user_id,assigned,mode,permissions,version)
          VALUES(target,true,access_mode,permissions,coalesce(actual_version,0)+1)
          ON CONFLICT(user_id) DO UPDATE SET assigned=true,mode=excluded.mode,permissions=excluded.permissions,
              version=excluded.version,updated_at=now();
        PERFORM auth_api.put_grant(target,'admin','active',NULL);
    END IF;
    PERFORM auth_api.clear_selections(target);
END $$;

CREATE FUNCTION auth_api.read_initial_password(actor bigint, rv bigint, sid uuid, sv bigint, target bigint)
RETURNS text LANGUAGE plpgsql SECURITY DEFINER SET search_path=pg_catalog AS $$
BEGIN
    -- Read stays fresh without a write lock. No field is returned before live admission and target protection.
    IF NOT coalesce(auth_api.current_admin(actor,rv,sid,sv),false) OR NOT EXISTS(
        SELECT FROM public.admin_access_profiles p WHERE p.user_id=actor AND p.assigned
        AND (p.mode='FULL' OR ('USER_CREDENTIALS_MANAGE'=ANY(p.permissions) AND NOT EXISTS(
            SELECT FROM public.user_role_grants g WHERE g.user_id=target AND g.role='admin' AND g.status='active')))) THEN
        RAISE EXCEPTION 'ADMIN_PERMISSION_DENIED' USING ERRCODE='42501';
    END IF;
    RETURN (SELECT u.initial_password FROM public.users u WHERE u.id=target AND NOT u.password_changed);
END $$;

-- The bot service-identity interceptor remains the caller boundary; these functions enforce the exact own-chat tuple.
-- Metadata lookup never returns credentials. The credential projection is separate and uncached.
CREATE FUNCTION auth_api.bot_user_state(target bigint)
RETURNS TABLE(id bigint,telegram_id bigint,status text,password_changed boolean,credentials_version bigint,
              telegram_binding_version bigint,eligible boolean)
LANGUAGE sql SECURITY DEFINER SET search_path=pg_catalog AS $$
    SELECT u.id,u.telegram_id,u.status::text,u.password_changed,u.credentials_version,u.telegram_binding_version,
           (NOT u.password_changed AND u.initial_password IS NOT NULL AND length(u.initial_password)>0
            AND u.login IS NOT NULL AND length(u.login)>0)
      FROM public.users u WHERE u.id=target FOR UPDATE
$$;

CREATE FUNCTION auth_api.bot_own_authority(target bigint) RETURNS boolean
LANGUAGE sql STABLE SECURITY DEFINER SET search_path=pg_catalog AS $$
    SELECT EXISTS(SELECT FROM public.users u JOIN public.user_role_grants g ON g.user_id=u.id
      WHERE u.id=target AND u.status::text<>'archived' AND u.credentials_version>0 AND u.telegram_binding_version>0
        AND g.status='active' AND (
          (g.role='student' AND g.group_id>0) OR g.role='teacher'
          OR (g.role='headman' AND g.group_id>0 AND EXISTS(SELECT FROM public.user_role_grants s
              WHERE s.user_id=u.id AND s.role='student' AND s.status='active' AND s.group_id=g.group_id))
          OR (g.role='admin' AND EXISTS(SELECT FROM public.admin_access_profiles p WHERE p.user_id=u.id AND p.assigned
              AND auth_api.valid_profile(p.mode,p.permissions)))))
$$;

CREATE FUNCTION auth_api.read_bot_initial_password(target bigint, sender bigint, chat bigint, binding bigint, generation bigint)
RETURNS TABLE(login text,initial_password text)
LANGUAGE plpgsql SECURITY DEFINER SET search_path=pg_catalog AS $$
BEGIN
    IF target<=0 OR sender<=0 OR sender<>chat OR binding<=0 OR generation<=0 THEN
        RAISE EXCEPTION 'BOT_OWN_SCOPE_REQUIRED' USING ERRCODE='42501';
    END IF;
    PERFORM u.id FROM public.users u WHERE u.id=target FOR UPDATE;
    RETURN QUERY SELECT u.login::text,u.initial_password::text FROM public.users u
      WHERE u.id=target AND u.telegram_id=sender AND u.telegram_binding_version=binding
        AND u.credentials_version=generation AND NOT u.password_changed
        AND u.initial_password IS NOT NULL AND length(u.initial_password)>0 AND auth_api.bot_own_authority(target);
END $$;

CREATE FUNCTION auth_api.register_bot_message(target bigint, chat bigint, message bigint, binding bigint, generation bigint)
RETURNS integer LANGUAGE plpgsql SECURITY DEFINER SET search_path=pg_catalog AS $$
DECLARE changed integer;
BEGIN
    PERFORM u.id FROM public.users u WHERE u.id=target FOR UPDATE;
    IF target<=0 OR chat<=0 OR message<=0 OR binding<=0 OR generation<=0 OR NOT EXISTS(
        SELECT FROM auth_api.read_bot_initial_password(target,chat,chat,binding,generation)) THEN
        RAISE EXCEPTION 'BOT_CREDENTIAL_GENERATION_CHANGED' USING ERRCODE='42501';
    END IF;
    INSERT INTO public.bot_credential_messages(user_id,private_chat_id,message_id,telegram_binding_version,credentials_version)
      VALUES(target,chat,message,binding,generation) ON CONFLICT(private_chat_id,message_id) DO NOTHING;
    GET DIAGNOSTICS changed=ROW_COUNT;
    RETURN changed;
END $$;

CREATE FUNCTION auth_api.complete_bot_render(record bigint, revision bigint, render_code text, invalidation bigint)
RETURNS integer LANGUAGE plpgsql SECURITY DEFINER SET search_path=pg_catalog AS $$
DECLARE card public.bot_credential_messages%ROWTYPE; changed integer; live boolean; current_generation bigint;
BEGIN
    -- Same lock order as Auth credential writes. Resolve only this retained card identity.
    SELECT b.* INTO card FROM public.bot_credential_messages b WHERE b.record_id=record;
    IF NOT FOUND THEN RAISE EXCEPTION 'BOT_CARD_SCOPE_REQUIRED' USING ERRCODE='42501'; END IF;
    PERFORM u.id FROM public.users u WHERE u.id=card.user_id FOR UPDATE;
    SELECT b.* INTO card FROM public.bot_credential_messages b WHERE b.record_id=record FOR UPDATE;
    IF card.record_revision<>revision OR render_code NOT IN ('CONFIRMED_EDIT','NO_EDIT','UNCERTAIN')
       OR card.cleanup_state='CLEANED' OR (card.render_state<>'IN_FLIGHT' AND card.render_state<>render_code) THEN
        RAISE EXCEPTION 'BOT_CARD_REVISION_CHANGED' USING ERRCODE='40001';
    END IF;
    SELECT u.credentials_version,
           u.telegram_id=card.private_chat_id AND u.telegram_binding_version=card.telegram_binding_version
           AND u.credentials_version=card.credentials_version AND NOT u.password_changed
           AND u.initial_password IS NOT NULL AND length(u.initial_password)>0 AND auth_api.bot_own_authority(u.id)
      INTO current_generation,live FROM public.users u WHERE u.id=card.user_id;
    IF card.invalidated_by_credentials_version IS NOT NULL THEN
        invalidation:=card.invalidated_by_credentials_version;
    ELSIF NOT coalesce(live,false) THEN
        invalidation:=greatest(card.credentials_version,coalesce(current_generation,0));
    ELSIF invalidation IS NOT NULL THEN
        -- Caller may not invent invalidation of a currently valid card.
        RAISE EXCEPTION 'BOT_CARD_GENERATION_CHANGED' USING ERRCODE='42501';
    END IF;
    UPDATE public.bot_credential_messages SET render_state=render_code,render_completed_at=coalesce(render_completed_at,now()),
      invalidated_by_credentials_version=invalidation,
      invalidated_at=CASE WHEN invalidation IS NOT NULL THEN coalesce(invalidated_at,now()) ELSE invalidated_at END,
      cleanup_state=CASE WHEN invalidation IS NULL THEN 'NOT_REQUIRED' ELSE 'PENDING' END,
      record_revision=record_revision+1,updated_at=now() WHERE record_id=record AND record_revision=revision;
    GET DIAGNOSTICS changed=ROW_COUNT;
    RETURN changed;
END $$;

CREATE FUNCTION auth_api.complete_bot_cleanup(record bigint, revision bigint, event uuid, outcome text)
RETURNS integer LANGUAGE plpgsql SECURITY DEFINER SET search_path=pg_catalog AS $$
DECLARE target bigint; changed integer;
BEGIN
    SELECT b.user_id INTO target FROM public.bot_credential_messages b WHERE b.record_id=record;
    PERFORM u.id FROM public.users u WHERE u.id=target FOR UPDATE;
    IF event IS NULL OR outcome NOT IN ('DELETED','ABSENT','SANITIZED') THEN
        RAISE EXCEPTION 'INVALID_BOT_CLEANUP' USING ERRCODE='22023';
    END IF;
    UPDATE public.bot_credential_messages SET cleanup_state='CLEANED',cleanup_event_id=event,cleanup_outcome=outcome,
        cleaned_at=now(),updated_at=now(),record_revision=record_revision+1
      WHERE record_id=record AND record_revision=revision AND cleanup_state='PENDING'
        AND (outcome<>'SANITIZED' OR render_state IN ('CONFIRMED_EDIT','NO_EDIT'));
    GET DIAGNOSTICS changed=ROW_COUNT;
    RETURN changed;
END $$;

-- Fixed group locking for Auth's two existing security commands, not a writable group API.
CREATE FUNCTION auth_api.lock_group_state(target bigint) RETURNS TABLE(is_active boolean)
LANGUAGE sql SECURITY DEFINER SET search_path=pg_catalog AS $$
    SELECT g.is_active FROM public.groups g WHERE g.id=target FOR UPDATE
$$;

CREATE FUNCTION auth_api.lock_current_session(actor bigint, sid uuid) RETURNS void
LANGUAGE plpgsql SECURITY DEFINER SET search_path=pg_catalog AS $$
BEGIN
    PERFORM s.sid FROM public.auth_sessions s WHERE s.sid=lock_current_session.sid AND s.user_id=actor FOR UPDATE;
END $$;

CREATE FUNCTION auth_api.subject_proof_admitted(proof uuid, operation uuid, digest text, subject bigint,
    semester bigint, group_scope bigint, actor bigint, sid uuid, cutoff timestamptz)
RETURNS boolean LANGUAGE plpgsql SECURITY DEFINER SET search_path=pg_catalog AS $$
BEGIN
    PERFORM p.receipt_id FROM public.subject_deletion_proofs p WHERE p.receipt_id=proof FOR UPDATE;
    RETURN EXISTS(SELECT FROM public.subject_deletion_proofs p JOIN public.users u ON u.id=p.actor_id
        JOIN public.groups g ON g.id=p.group_id JOIN public.auth_sessions s ON s.sid=p.session_id AND s.user_id=p.actor_id
        JOIN public.user_role_grants h ON h.id=s.active_role_grant_id AND h.user_id=p.actor_id
        WHERE p.receipt_id=proof AND p.operation_id=operation AND p.preview_digest=digest AND p.subject_id=subject
          AND p.semester_id=semester AND p.group_id=group_scope AND p.actor_id=actor AND p.session_id=sid
          AND p.consumed_at IS NOT NULL AND p.expires_at>cutoff
          AND p.credentials_version=u.credentials_version AND p.binding_version=u.telegram_binding_version
          AND p.telegram_id=u.telegram_id AND p.roles_version=u.roles_version AND p.session_version=s.session_version
          AND s.revoked_at IS NULL AND s.refresh_expires_at>cutoff
          AND u.status::text<>'archived' AND g.is_active AND h.role='headman' AND h.status='active' AND h.group_id=p.group_id
          AND NOT EXISTS(SELECT FROM public.user_role_grants peer WHERE peer.role='headman' AND peer.status='active'
              AND peer.group_id=p.group_id AND peer.user_id<>p.actor_id)
          AND EXISTS(SELECT FROM public.user_role_grants student WHERE student.user_id=p.actor_id AND student.role='student'
              AND student.status='active' AND student.group_id=p.group_id));
END $$;

CREATE FUNCTION auth_api.read_export_selector(actor bigint, rv bigint, sid uuid, sv bigint,
    job uuid, lease uuid, kind text, selector text) RETURNS TABLE(selector_json text)
LANGUAGE sql STABLE SECURITY DEFINER SET search_path=pg_catalog AS $$
    SELECT (j.identity->'report'->selector)::text FROM public.auth_export_jobs j
    WHERE auth_api.current_admin(actor,rv,sid,sv)
      AND ((kind='ADMIN_GROUP_ATTENDANCE' AND selector='adminGroupAttendance')
        OR (kind='ADMIN_USERS' AND selector='adminUsers')
        OR (kind='ADMIN_GROUPS' AND selector='adminGroups'))
      AND j.job_id=job AND j.lease_id=lease AND j.user_id=actor AND j.session_id=sid
      AND j.state='preparing' AND j.lease_until>now() AND j.expires_at>now() AND j.identity->'report'->>'kind'=kind
$$;

-- One admission for both old and new headman; an actor may itself lose a derived grant here.
CREATE FUNCTION auth_api.replace_headman(actor bigint, rv bigint, sid uuid, sv bigint,
    previous bigint, candidate bigint, group_scope bigint, legacy_sync boolean, telegram bigint, status_code text)
RETURNS void LANGUAGE plpgsql SECURITY DEFINER SET search_path=pg_catalog AS $$
DECLARE current_headman bigint; identity_row public.users%ROWTYPE; base_status text;
BEGIN
    PERFORM pg_advisory_xact_lock(724830210081::bigint);
    PERFORM u.id FROM public.users u WHERE u.id IN (actor,previous,candidate) ORDER BY u.id FOR UPDATE;
    PERFORM auth_api.require_actor(actor,rv,sid,sv,candidate,'GROUPS_MANAGE');
    IF previous IS NOT NULL THEN PERFORM auth_api.require_actor(actor,rv,sid,sv,previous,'GROUPS_MANAGE'); END IF;
    PERFORM g.id FROM public.groups g WHERE g.id=group_scope AND g.is_active FOR UPDATE;
    IF NOT FOUND OR NOT EXISTS(SELECT FROM public.user_role_grants g JOIN public.users u ON u.id=g.user_id
        WHERE g.user_id=candidate AND g.role='student' AND g.status='active' AND g.group_id=group_scope
          AND u.status::text<>'archived') THEN
        RAISE EXCEPTION 'ACTIVE_HEADMAN_CANDIDATE_REQUIRED' USING ERRCODE='23514';
    END IF;
    SELECT g.user_id INTO current_headman FROM public.user_role_grants g
      WHERE g.role='headman' AND g.status='active' AND g.group_id=group_scope;
    IF current_headman IS DISTINCT FROM previous THEN
        RAISE EXCEPTION 'HEADMAN_ASSIGNMENT_CONFLICT' USING ERRCODE='40001';
    END IF;
    IF legacy_sync THEN
        PERFORM auth_api.require_actor(actor,rv,sid,sv,candidate,'USERS_MANAGE');
        IF status_code IS NOT NULL AND status_code<>'active' THEN
            RAISE EXCEPTION 'ACTIVE_HEADMAN_CANDIDATE_REQUIRED' USING ERRCODE='22023';
        END IF;
        UPDATE public.users SET telegram_id=coalesce(telegram,telegram_id),
            status=coalesce(status_code::public.account_status,status) WHERE id=candidate;
        SELECT u.* INTO identity_row FROM public.users u WHERE u.id=candidate;
        base_status:=identity_row.status::text;
        IF base_status='active' THEN
            SELECT coalesce((SELECT g.status FROM public.user_role_grants g
                WHERE g.user_id=candidate AND g.role=identity_row.role::text),'active') INTO base_status;
        END IF;
        PERFORM auth_api.put_grant(candidate,identity_row.role::text,base_status,identity_row.group_id);
    END IF;
    IF previous IS NOT NULL AND previous<>candidate THEN
        UPDATE public.user_role_grants SET status='suspended',updated_at=now() WHERE user_id=previous AND role='headman';
        UPDATE public.users SET is_headman=false WHERE id=previous;
        PERFORM auth_api.clear_selections(previous);
    END IF;
    PERFORM auth_api.put_grant(candidate,'headman','active',group_scope);
    UPDATE public.users SET is_headman=true WHERE id=candidate;
    PERFORM auth_api.clear_selections(candidate);
END $$;

CREATE FUNCTION auth_api.transfer_identity(actor bigint, rv bigint, sid uuid, sv bigint, target bigint,
    role_code text, status_code text, telegram bigint, telegram_name text, headman boolean, student_status text, group_scope bigint)
RETURNS void LANGUAGE plpgsql SECURITY DEFINER SET search_path=pg_catalog AS $$
BEGIN
    PERFORM auth_api.synchronize_identity(actor,rv,sid,sv,target,role_code,status_code,telegram,telegram_name,headman,false);
    IF student_status IS NOT NULL THEN
        PERFORM auth_api.put_grant(target,'student',student_status,group_scope);
        PERFORM auth_api.clear_selections(target);
    END IF;
END $$;

CREATE FUNCTION auth_api.projection_session(actor bigint, requested_sid uuid)
RETURNS TABLE(sid uuid,user_id bigint,active_role_grant_id bigint,session_version bigint,refresh_expires_at timestamptz,revoked_at timestamptz)
LANGUAGE sql STABLE SECURITY DEFINER SET search_path=pg_catalog AS $$
    SELECT s.sid,s.user_id,s.active_role_grant_id,s.session_version,s.refresh_expires_at,s.revoked_at
      FROM public.auth_sessions s WHERE s.user_id=actor AND s.sid=requested_sid
$$;

-- Metadata readers may lock one exact retained card through Auth, without any card-column UPDATE privilege.
CREATE FUNCTION auth_api.lock_bot_card(target bigint, record bigint, chat bigint, message bigint) RETURNS void
LANGUAGE plpgsql SECURITY DEFINER SET search_path=pg_catalog AS $$
DECLARE owner_id bigint;
BEGIN
    IF record>0 AND target>0 THEN
        SELECT b.user_id INTO owner_id FROM public.bot_credential_messages b WHERE b.record_id=record AND b.user_id=target;
    ELSIF record=0 AND target=0 AND chat>0 AND message>0 THEN
        SELECT b.user_id INTO owner_id FROM public.bot_credential_messages b WHERE b.private_chat_id=chat AND b.message_id=message;
    ELSE
        RAISE EXCEPTION 'BOT_CARD_SCOPE_REQUIRED' USING ERRCODE='42501';
    END IF;
    PERFORM u.id FROM public.users u WHERE u.id=owner_id FOR UPDATE;
    PERFORM b.record_id FROM public.bot_credential_messages b WHERE b.user_id=owner_id
      AND ((record>0 AND b.record_id=record) OR (record=0 AND b.private_chat_id=chat AND b.message_id=message)) FOR UPDATE;
END $$;

-- Explicit allowlists: future tables, sequences and commands receive no runtime privilege by default.
REVOKE ALL ON ALL TABLES IN SCHEMA public FROM rct_runtime,rct_auth_runtime,rct_academic_runtime,rct_auth_command_owner;
REVOKE ALL ON ALL SEQUENCES IN SCHEMA public FROM rct_runtime,rct_auth_runtime,rct_academic_runtime,rct_auth_command_owner;
REVOKE CREATE ON SCHEMA public FROM PUBLIC,rct_runtime,rct_auth_runtime,rct_academic_runtime,rct_auth_command_owner;
GRANT USAGE ON SCHEMA public TO rct_auth_runtime,rct_academic_runtime,rct_auth_command_owner;
GRANT SELECT,INSERT,UPDATE,DELETE ON TABLE public.academic_initial_membership_backfills,
    public.academic_outbox,
    public.academic_semester_archive_barriers,
    public.academic_semester_archive_effect_receipts,
    public.academic_semester_archive_publication_admissions,
    public.assignment_replacement_operations,
    public.assignments,
    public.attendance_thresholds,
    public.audit_log,
    public.campus_map_activity_coverage,
    public.campus_map_asset,
    public.campus_map_building,
    public.campus_map_catalog_revision,
    public.campus_map_deletion_receipt,
    public.campus_map_floor,
    public.campus_map_floor_daily_demand,
    public.campus_map_floor_demand_dedupe,
    public.campus_map_floor_hourly_activity,
    public.campus_map_open_intent,
    public.campus_map_plan_format,
    public.campus_map_plan_version,
    public.campus_settings,
    public.dashboard_scale_observations,
    public.event_consumer_processed,
    public.group_history_coverage,
    public.group_promotion_cycle_record,
    public.group_promotion_notification_cycle,
    public.groups,
    public.headman_assistants,
    public.homework_binding_archives,
    public.homework_binding_transfer_history,
    public.homework_binding_transfer_markers,
    public.homework_completions,
    public.homework_edit_archive_admissions,
    public.homework_edit_history,
    public.homework_edit_operations,
    public.homework_edit_receipts,
    public.homework_weekly_digest_runs,
    public.homeworks,
    public.lesson_transfer_receipts,
    public.semester_archive_operations,
    public.semesters,
    public.shedlock,
    public.student_group_history,
    public.subject_deletion_operations,
    public.subject_lesson_types,
    public.subjects,
    public.teacher_subject_groups,
    public.user_archive_preview,
    public.user_archive_receipt TO rct_academic_runtime;
GRANT SELECT,INSERT,UPDATE,DELETE ON TABLE public.admin_access_profiles,
    public.auth_export_jobs,
    public.auth_outbox,
    public.auth_sessions,
    public.bot_credential_messages,
    public.password_reset_tokens,
    public.qr_login_challenges,
    public.qr_login_issuers,
    public.subject_deletion_proofs,
    public.user_role_grants TO rct_auth_runtime;
GRANT SELECT,INSERT ON public.account_security_events TO rct_auth_runtime;
GRANT SELECT ON public.groups,public.subjects,public.semesters,public.assignments TO rct_auth_runtime;
GRANT INSERT ON public.audit_log TO rct_auth_runtime;
GRANT SELECT ON public.users TO rct_auth_runtime,rct_auth_command_owner;
GRANT SELECT ON public.user_role_grants,public.admin_access_profiles,public.bot_credential_messages TO rct_academic_runtime;
GRANT SELECT (id, login, last_name, first_name, middle_name, email, phone, telegram_id, telegram_username, employee_number, role, status, is_headman, group_id, password_changed, avatar_id, created_at, updated_at, roles_version, credentials_version, telegram_binding_version, generated_password_origin) ON public.users TO rct_academic_runtime;
GRANT INSERT (last_name,first_name,middle_name,email,phone,employee_number,avatar_id,group_id,created_at,updated_at) ON public.users TO rct_academic_runtime;
GRANT UPDATE (last_name,first_name,middle_name,email,phone,employee_number,avatar_id,group_id,updated_at) ON public.users TO rct_academic_runtime;
GRANT UPDATE (login,password_hash,initial_password,password_changed,generated_password_origin,telegram_id,telegram_username,credentials_version,telegram_binding_version,roles_version,role,status,is_headman,updated_at) ON public.users TO rct_auth_runtime,rct_auth_command_owner;
GRANT SELECT,INSERT,UPDATE ON public.user_role_grants,public.admin_access_profiles,public.bot_credential_messages TO rct_auth_command_owner;
GRANT SELECT,UPDATE ON public.auth_sessions TO rct_auth_command_owner;
GRANT SELECT ON public.auth_export_jobs,public.subject_deletion_proofs,public.groups TO rct_auth_command_owner;
GRANT UPDATE (id) ON public.groups TO rct_auth_command_owner;
GRANT UPDATE (receipt_id) ON public.subject_deletion_proofs TO rct_auth_command_owner;
GRANT USAGE,SELECT ON SEQUENCE public.users_id_seq,public.student_login_seq,public.teacher_login_seq TO rct_academic_runtime;
DO $sequences$
DECLARE item record;
BEGIN
    FOR item IN SELECT ns.nspname,seq.relname,t.relname AS table_name
        FROM pg_catalog.pg_class seq JOIN pg_catalog.pg_namespace ns ON ns.oid=seq.relnamespace
        JOIN pg_catalog.pg_depend dep ON dep.classid='pg_class'::regclass AND dep.objid=seq.oid AND dep.deptype IN ('a','i')
        JOIN pg_catalog.pg_class t ON t.oid=dep.refobjid WHERE seq.relkind='S' AND ns.nspname='public'
    LOOP
        IF item.table_name=ANY(ARRAY['academic_initial_membership_backfills','academic_outbox','academic_semester_archive_barriers','academic_semester_archive_effect_receipts','academic_semester_archive_publication_admissions','assignment_replacement_operations','assignments','attendance_thresholds','audit_log','campus_map_activity_coverage','campus_map_asset','campus_map_building','campus_map_catalog_revision','campus_map_deletion_receipt','campus_map_floor','campus_map_floor_daily_demand','campus_map_floor_demand_dedupe','campus_map_floor_hourly_activity','campus_map_open_intent','campus_map_plan_format','campus_map_plan_version','campus_settings','dashboard_scale_observations','event_consumer_processed','group_history_coverage','group_promotion_cycle_record','group_promotion_notification_cycle','groups','headman_assistants','homework_binding_archives','homework_binding_transfer_history','homework_binding_transfer_markers','homework_completions','homework_edit_archive_admissions','homework_edit_history','homework_edit_operations','homework_edit_receipts','homework_weekly_digest_runs','homeworks','lesson_transfer_receipts','semester_archive_operations','semesters','shedlock','student_group_history','subject_deletion_operations','subject_lesson_types','subjects','teacher_subject_groups','user_archive_preview','user_archive_receipt']) THEN
            EXECUTE format('GRANT USAGE,SELECT ON SEQUENCE %I.%I TO rct_academic_runtime',item.nspname,item.relname);
        END IF;
        IF item.table_name=ANY(ARRAY['account_security_events','admin_access_profiles','auth_export_jobs','auth_outbox','auth_sessions','bot_credential_messages','password_reset_tokens','qr_login_challenges','qr_login_issuers','subject_deletion_proofs','user_role_grants','audit_log']) THEN
            EXECUTE format('GRANT USAGE,SELECT ON SEQUENCE %I.%I TO rct_auth_runtime',item.nspname,item.relname);
        END IF;
        IF item.table_name IN ('user_role_grants','bot_credential_messages') THEN
            EXECUTE format('GRANT USAGE,SELECT ON SEQUENCE %I.%I TO rct_auth_command_owner',item.nspname,item.relname);
        END IF;
    END LOOP;
END $sequences$;

-- Preserve the published public-function access when earlier migrations replay under already-hardened role defaults.
-- This is a closed V1..V61 catalogue, not a grant on unknown future functions or extensions.
GRANT EXECUTE ON FUNCTION
    public.academic_homework_edit_continuation_admitted(jsonb,jsonb,bigint),
    public.academic_homework_edit_epoch_admitted(uuid,bigint),
    public.academic_semester_delete_authorized(bigint),
    public.academic_semester_delete_preparing(bigint),
    public.academic_subject_delete_row_authorized(text,jsonb),
    public.account_status_eq_text(public.account_status,text),
    public.account_status_ne_text(public.account_status,text),
    public.admin_access_grant_lifecycle(),
    public.admin_access_profile_version_guard(),
    public.advance_user_credential_generations(),
    public.bump_user_roles_version(),
    public.campus_map_final_delete_allowed(bigint),
    public.enforce_subject_lesson_type_child(),
    public.enforce_subject_lesson_type_parent(),
    public.guard_academic_semester_archive_business_write(),
    public.guard_academic_semester_delete_authority(),
    public.guard_academic_semester_deletion_write(),
    public.guard_academic_subject_write(),
    public.guard_bot_credential_message_lifecycle(),
    public.guard_homework_edit_business_write(),
    public.guard_subject_delete_semester_transition(),
    public.guard_subject_semester_identity(),
    public.increment_campus_map_floor_demand(),
    public.increment_campus_map_hourly_activity(),
    public.invalidate_user_bot_credential_messages(),
    public.normalize_generated_password_origin(),
    public.protect_academic_semester_archive_admission(),
    public.protect_assignment_identity(),
    public.protect_assignment_lifecycle_discriminator(),
    public.protect_assignment_replacement_operation(),
    public.protect_campus_map_asset(),
    public.protect_campus_map_demand_dedupe(),
    public.protect_campus_map_open_intent(),
    public.protect_campus_map_plan_version(),
    public.protect_homework_action_actor_roles(),
    public.protect_homework_binding_archive_identity(),
    public.protect_homework_binding_identity(),
    public.protect_homework_edit_operation(),
    public.protect_homework_placement_identity(),
    public.protect_homework_transfer_edit_supersession(),
    public.protect_qr_login_challenge(),
    public.protect_qr_login_issuer(),
    public.protect_subject_lesson_type_subject(),
    public.reject_academic_lesson_transfer_history_mutation(),
    public.reject_account_security_event_mutation(),
    public.retain_homework_edit_evidence(),
    public.retain_unproven_archive_effect_outbox_rows(),
    public.subject_type_eq_text(public.subject_type,text),
    public.subject_type_ne_text(public.subject_type,text),
    public.user_role_eq_text(public.user_role,text),
    public.user_role_ne_text(public.user_role,text),
    public.validate_assignment_insert_interval(),
    public.validate_assignment_replacement_operation_insert(),
    public.validate_campus_map_ready_asset()
    TO rct_academic_runtime,rct_auth_runtime,rct_auth_command_owner;

DO $function_owners$
DECLARE item record;
BEGIN
    FOR item IN SELECT p.oid::regprocedure AS signature FROM pg_catalog.pg_proc p
        JOIN pg_catalog.pg_namespace n ON n.oid=p.pronamespace WHERE n.nspname='auth_api'
    LOOP
        EXECUTE format('ALTER FUNCTION %s OWNER TO rct_auth_command_owner',item.signature);
        EXECUTE format('REVOKE ALL ON FUNCTION %s FROM PUBLIC,rct_auth_runtime,rct_academic_runtime,rct_runtime',item.signature);
    END LOOP;
END $function_owners$;
GRANT EXECUTE ON FUNCTION auth_api.current_admin(bigint,bigint,uuid,bigint) TO rct_auth_runtime,rct_academic_runtime;
GRANT EXECUTE ON FUNCTION auth_api.lock_group_state(bigint) TO rct_auth_runtime;
GRANT EXECUTE ON FUNCTION auth_api.initialize_identity(bigint,bigint,uuid,bigint,bigint,text,text,text,text,text,bigint,text) TO rct_academic_runtime;
GRANT EXECUTE ON FUNCTION auth_api.project_identity(bigint,bigint,uuid,bigint,bigint,text,text,bigint,text) TO rct_academic_runtime;
GRANT EXECUTE ON FUNCTION auth_api.synchronize_identity(bigint,bigint,uuid,bigint,bigint,text,text,bigint,text,boolean,boolean) TO rct_academic_runtime;
GRANT EXECUTE ON FUNCTION auth_api.write_grant(bigint,bigint,uuid,bigint,bigint,text,text,bigint) TO rct_academic_runtime;
GRANT EXECUTE ON FUNCTION auth_api.write_headman(bigint,bigint,uuid,bigint,bigint,boolean) TO rct_academic_runtime;
GRANT EXECUTE ON FUNCTION auth_api.restore_identity(bigint,bigint,uuid,bigint,bigint) TO rct_academic_runtime;
GRANT EXECUTE ON FUNCTION auth_api.write_admin_access(bigint,bigint,uuid,bigint,bigint,text,text[],bigint,boolean) TO rct_academic_runtime;
GRANT EXECUTE ON FUNCTION auth_api.read_initial_password(bigint,bigint,uuid,bigint,bigint) TO rct_academic_runtime;
GRANT EXECUTE ON FUNCTION auth_api.bot_user_state(bigint) TO rct_academic_runtime;
GRANT EXECUTE ON FUNCTION auth_api.read_bot_initial_password(bigint,bigint,bigint,bigint,bigint) TO rct_auth_runtime;
GRANT EXECUTE ON FUNCTION auth_api.register_bot_message(bigint,bigint,bigint,bigint,bigint) TO rct_academic_runtime;
GRANT EXECUTE ON FUNCTION auth_api.complete_bot_render(bigint,bigint,text,bigint) TO rct_academic_runtime;
GRANT EXECUTE ON FUNCTION auth_api.complete_bot_cleanup(bigint,bigint,uuid,text) TO rct_academic_runtime;
GRANT EXECUTE ON FUNCTION auth_api.lock_current_session(bigint,uuid) TO rct_academic_runtime;
GRANT EXECUTE ON FUNCTION auth_api.subject_proof_admitted(uuid,uuid,text,bigint,bigint,bigint,bigint,uuid,timestamptz) TO rct_academic_runtime;
GRANT EXECUTE ON FUNCTION auth_api.read_export_selector(bigint,bigint,uuid,bigint,uuid,uuid,text,text) TO rct_academic_runtime;
GRANT EXECUTE ON FUNCTION auth_api.replace_headman(bigint,bigint,uuid,bigint,bigint,bigint,bigint,boolean,bigint,text) TO rct_academic_runtime;
GRANT EXECUTE ON FUNCTION auth_api.transfer_identity(bigint,bigint,uuid,bigint,bigint,text,text,bigint,text,boolean,text,bigint) TO rct_academic_runtime;
GRANT EXECUTE ON FUNCTION auth_api.projection_session(bigint,uuid) TO rct_academic_runtime;
GRANT EXECUTE ON FUNCTION auth_api.lock_bot_card(bigint,bigint,bigint,bigint) TO rct_academic_runtime;
