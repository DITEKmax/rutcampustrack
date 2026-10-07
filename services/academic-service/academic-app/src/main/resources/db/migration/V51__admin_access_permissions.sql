-- Additive explicit backfill. Missing future profiles never imply FULL.
CREATE TABLE admin_access_profiles (
 user_id BIGINT PRIMARY KEY REFERENCES users(id) ON DELETE RESTRICT,
 assigned BOOLEAN NOT NULL,
 mode VARCHAR(16),
 permissions TEXT[] NOT NULL DEFAULT '{}',
 version BIGINT NOT NULL CHECK(version>0),
 updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
 CONSTRAINT admin_access_mode_presence CHECK ((assigned AND mode IS NOT NULL) OR (NOT assigned AND mode IS NULL)),
 CONSTRAINT admin_access_state CHECK (
   (NOT assigned AND mode IS NULL AND cardinality(permissions)=0) OR
   (assigned AND mode='FULL' AND cardinality(permissions)=0) OR
   (assigned AND mode='LIMITED' AND cardinality(permissions)>0
     AND NOT ('ADMIN_ACCESS_MANAGE'=ANY(permissions))))
);
INSERT INTO admin_access_profiles(user_id,assigned,mode,permissions,version)
 SELECT g.user_id,TRUE,'FULL','{}',1 FROM user_role_grants g JOIN users u ON u.id=g.user_id
 WHERE g.role='admin' AND g.status='active' AND u.status<>'archived';
-- Retain a tombstone for previously assigned roles, including archived accounts.
INSERT INTO admin_access_profiles(user_id,assigned,mode,permissions,version)
 SELECT g.user_id,FALSE,NULL,'{}',1 FROM user_role_grants g
 WHERE g.role='admin' ON CONFLICT(user_id) DO NOTHING;

CREATE FUNCTION admin_access_profile_version_guard() RETURNS TRIGGER LANGUAGE plpgsql AS $$
BEGIN
 IF TG_OP='UPDATE' THEN
   IF NEW.user_id IS DISTINCT FROM OLD.user_id OR NEW.version<>OLD.version+1 THEN
      RAISE EXCEPTION 'admin access version must increase';
   END IF;
 END IF;
 UPDATE users SET roles_version=roles_version+1 WHERE id=NEW.user_id;
 RETURN NEW;
END $$;
CREATE TRIGGER admin_access_profile_version BEFORE INSERT OR UPDATE ON admin_access_profiles
 FOR EACH ROW EXECUTE FUNCTION admin_access_profile_version_guard();

CREATE FUNCTION admin_access_grant_lifecycle() RETURNS TRIGGER LANGUAGE plpgsql AS $$
BEGIN
 IF NEW.role='admin' THEN
   IF NEW.status='active' AND NOT EXISTS(SELECT 1 FROM admin_access_profiles p
      WHERE p.user_id=NEW.user_id AND p.assigned) THEN
     RAISE EXCEPTION 'explicit admin access profile required';
   END IF;
   IF NEW.status<>'active' THEN
     UPDATE admin_access_profiles SET assigned=FALSE,mode=NULL,permissions='{}',version=version+1,updated_at=NOW()
      WHERE user_id=NEW.user_id AND assigned;
   END IF;
 END IF;
 RETURN NEW;
END $$;
CREATE TRIGGER admin_access_grant_lifecycle BEFORE INSERT OR UPDATE ON user_role_grants
 FOR EACH ROW EXECUTE FUNCTION admin_access_grant_lifecycle();
