-- A NONAUTHORITATIVE registry index for exact local filtering/sorting/pagination.
-- It never admits a caller or supplies a credential/permission/session decision.
CREATE TABLE academic_identity_read_model (
    user_id bigint PRIMARY KEY CHECK (user_id>0),
    catalog_version bigint NOT NULL CHECK (catalog_version>0),
    login varchar(32) NOT NULL,
    account_state varchar(16) NOT NULL CHECK (account_state IN ('PENDING','ACTIVE','ARCHIVED','FENCED')),
    roles_version bigint NOT NULL CHECK (roles_version>0),
    telegram_binding_version bigint NOT NULL CHECK (telegram_binding_version>0),
    telegram_id bigint CHECK (telegram_id>0),
    telegram_username varchar(64) NOT NULL DEFAULT '',
    password_changed boolean NOT NULL,
    academic_revision bigint NOT NULL CHECK (academic_revision>=0),
    updated_at timestamptz NOT NULL DEFAULT now()
);
CREATE TABLE academic_identity_read_model_grants (
    grant_id bigint PRIMARY KEY CHECK (grant_id>0),
    user_id bigint NOT NULL REFERENCES academic_identity_read_model(user_id) ON DELETE RESTRICT,
    role varchar(16) NOT NULL CHECK (role IN ('STUDENT','TEACHER','HEADMAN','ADMIN')),
    status varchar(16) NOT NULL,
    group_id bigint CHECK (group_id>0),
    UNIQUE(user_id,role),
    CHECK ((role='STUDENT' AND status IN ('ACTIVE','EXPELLED','GRADUATED','SUSPENDED','ARCHIVED'))
        OR (role='TEACHER' AND status IN ('ACTIVE','DISMISSED','SUSPENDED','ARCHIVED'))
        OR (role='HEADMAN' AND status IN ('ACTIVE','SUSPENDED','ARCHIVED'))
        OR (role='ADMIN' AND status IN ('ACTIVE','ARCHIVED')))
);
CREATE TABLE academic_identity_catalog_cursor (
    singleton boolean PRIMARY KEY CHECK (singleton),
    applied_catalog_version bigint NOT NULL CHECK (applied_catalog_version>=0)
);
INSERT INTO academic_identity_catalog_cursor(singleton,applied_catalog_version) VALUES(true,0);
CREATE INDEX academic_identity_registry_login ON academic_identity_read_model(lower(login),user_id);
CREATE INDEX academic_identity_registry_telegram ON academic_identity_read_model(telegram_id,user_id);
CREATE INDEX academic_identity_registry_role_status ON academic_identity_read_model_grants(role,status,user_id);
-- Auth catalog rows may precede Academic CREATE APPLY, so projection UID deliberately
-- has no profile FK. A profile join controls registry membership; Auth remains sole authority.

-- These own-DB views preserve existing scalar display/search semantics. They
-- never authorize admission, return credentials, or write Auth's identity/grants.
CREATE VIEW academic_user_directory AS
SELECT p.id,c.login,p.last_name,p.first_name,p.middle_name,p.email,p.phone,
       c.telegram_id,c.telegram_username,p.employee_number,
       lower(p.directory_role)::user_role AS role,
       (CASE WHEN p.archived_at IS NOT NULL OR c.account_state='ARCHIVED' THEN 'archived'
             ELSE lower(p.directory_status) END)::account_status AS status,
       EXISTS(SELECT 1 FROM academic_role_eligibility h JOIN groups g ON g.id=h.group_id
               WHERE h.user_id=p.id AND h.role='HEADMAN' AND h.status='ACTIVE'
                 AND s.status='ACTIVE' AND s.group_id=h.group_id
                 AND p.archived_at IS NULL AND g.is_active) AS is_headman,
       CASE WHEN p.directory_role='STUDENT' AND s.status IN ('ACTIVE','SUSPENDED') THEN s.group_id ELSE NULL END AS group_id,
       c.password_changed,p.avatar_id,p.created_at,p.updated_at,c.roles_version,
       c.telegram_binding_version,p.academic_revision,p.applied_auth_roles_version
  FROM academic_profiles p JOIN academic_identity_read_model c ON c.user_id=p.id
  LEFT JOIN academic_role_eligibility s ON s.user_id=p.id AND s.role='STUDENT';

CREATE VIEW academic_role_directory AS
SELECT g.grant_id AS id,g.user_id,lower(g.role) AS role,lower(g.status) AS status,g.group_id,
       c.updated_at AS created_at,c.updated_at
  FROM academic_identity_read_model_grants g JOIN academic_identity_read_model c ON c.user_id=g.user_id;
