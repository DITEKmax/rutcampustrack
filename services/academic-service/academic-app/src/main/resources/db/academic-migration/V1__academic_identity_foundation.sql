-- Clean Academic foundation. This location never executes the historical combined chain.
-- Domain tables/guards and role grants are completed in subsequent own migrations.
-- Auth allocates every profile ID; no identity/credential/session/admission-grant copy is stored.
CREATE TABLE academic_profiles (
    id bigint PRIMARY KEY CHECK (id > 0),
    -- Stable directory classification for the existing scalar presentation field.
    -- It never admits a session; role eligibility/grants retain their separate owners.
    directory_role varchar(16) NOT NULL CHECK (directory_role IN ('STUDENT','TEACHER','ADMIN')),
    directory_status varchar(16) NOT NULL CHECK (directory_status IN ('ACTIVE','EXPELLED','SUSPENDED','ARCHIVED')),
    last_name varchar(128) NOT NULL CHECK (length(last_name) > 0),
    first_name varchar(128) NOT NULL CHECK (length(first_name) > 0),
    middle_name varchar(128),
    email varchar(255),
    phone varchar(32),
    employee_number varchar(32) UNIQUE,
    avatar_id varchar(32),
    archived_at timestamptz,
    academic_revision bigint NOT NULL CHECK (academic_revision > 0),
    applied_auth_roles_version bigint NOT NULL CHECK (applied_auth_roles_version > 0),
    created_at timestamptz NOT NULL,
    updated_at timestamptz NOT NULL
);

-- These are domain eligibility facts. They cannot authorize a session or issue credentials.
CREATE TABLE academic_role_eligibility (
    user_id bigint NOT NULL REFERENCES academic_profiles(id) ON DELETE RESTRICT,
    role varchar(16) NOT NULL CHECK (role IN ('STUDENT','TEACHER','HEADMAN')),
    status varchar(16) NOT NULL,
    group_id bigint CHECK (group_id > 0),
    updated_at timestamptz NOT NULL,
    PRIMARY KEY (user_id,role),
    CHECK ((role='STUDENT' AND status IN ('ACTIVE','EXPELLED','GRADUATED','SUSPENDED','ARCHIVED'))
        OR (role='TEACHER' AND status IN ('ACTIVE','DISMISSED','SUSPENDED','ARCHIVED'))
        OR (role='HEADMAN' AND status IN ('ACTIVE','SUSPENDED','ARCHIVED'))),
    CHECK (role NOT IN ('STUDENT','HEADMAN') OR status<>'ACTIVE' OR group_id IS NOT NULL),
    CHECK (role<>'TEACHER' OR group_id IS NULL)
);
CREATE UNIQUE INDEX academic_one_active_headman_per_group
    ON academic_role_eligibility(group_id) WHERE role='HEADMAN' AND status='ACTIVE';
CREATE INDEX academic_eligibility_group_members
    ON academic_role_eligibility(group_id,user_id) WHERE role='STUDENT' AND status='ACTIVE';

-- Fixed operation protobuf contains no password, password hash or transport token.
-- The accepted actor tuple is evidence, never reconstructed into a live user JWT on recovery.
CREATE TABLE academic_identity_operations (
    operation_id uuid PRIMARY KEY,
    command_hash bytea NOT NULL CHECK (octet_length(command_hash)=32),
    local_command_hash bytea NOT NULL CHECK (octet_length(local_command_hash)=32),
    command_payload bytea NOT NULL CHECK (octet_length(command_payload) BETWEEN 1 AND 262144),
    local_payload bytea NOT NULL CHECK (octet_length(local_payload) BETWEEN 1 AND 262144),
    actor_id bigint NOT NULL CHECK (actor_id > 0),
    actor_session_id uuid NOT NULL,
    actor_session_version bigint NOT NULL CHECK (actor_session_version > 0),
    actor_roles_version bigint NOT NULL CHECK (actor_roles_version > 0),
    kind varchar(24) NOT NULL CHECK (kind IN
        ('CREATE','ROLE','TRANSFER','HEADMAN_REPLACE','ARCHIVE','RESTORE',
         'ADMIN_ACCESS','TELEGRAM_BINDING','RECONCILE')),
    phase varchar(24) NOT NULL CHECK (phase IN
        ('ACCEPTED','AUTH_PREPARED','LOCAL_COMMITTED','SUCCEEDED','FAILED_SAFE','REJECTED')),
    rejection_reason varchar(24) CHECK (rejection_reason IN ('ACTOR_DENIED','TARGET_CONFLICT','ACADEMIC_REJECTED')),
    revision bigint NOT NULL DEFAULT 1 CHECK (revision > 0),
    auth_operation_revision bigint CHECK (auth_operation_revision > 0),
    auth_prepared_payload bytea CHECK (octet_length(auth_prepared_payload) BETWEEN 1 AND 262144),
    committed_catalog_version bigint CHECK (committed_catalog_version > 0),
    headman_assistants_revoked integer CHECK (headman_assistants_revoked >= 0),
    attempt_count integer NOT NULL DEFAULT 0 CHECK (attempt_count >= 0),
    next_attempt_at timestamptz,
    last_failure varchar(32) CHECK (last_failure IN
        ('AUTH_UNAVAILABLE','LOCAL_CONFLICT','LOCAL_REJECTED','AUTH_CONFLICT','RECOVERY_REQUIRED')),
    created_at timestamptz NOT NULL,
    updated_at timestamptz NOT NULL,
    UNIQUE(operation_id,actor_id),
    CHECK (phase='ACCEPTED' OR auth_operation_revision IS NOT NULL)
);

CREATE TABLE academic_identity_operation_targets (
    operation_id uuid NOT NULL REFERENCES academic_identity_operations(operation_id) ON DELETE RESTRICT,
    target_index integer NOT NULL CHECK (target_index >= 0),
    -- NULL exists only before Auth PREPARE allocates the single CREATE identity.
    user_id bigint CHECK (user_id > 0),
    previous_academic_revision bigint NOT NULL CHECK (previous_academic_revision >= 0),
    committed_academic_revision bigint CHECK (committed_academic_revision > 0),
    applied_auth_roles_version bigint CHECK (applied_auth_roles_version > 0),
    fenced boolean NOT NULL DEFAULT false,
    PRIMARY KEY (operation_id,target_index),
    UNIQUE (operation_id,user_id),
    CHECK ((committed_academic_revision IS NULL)=(applied_auth_roles_version IS NULL)),
    CHECK (committed_academic_revision IS NULL
        OR committed_academic_revision=previous_academic_revision+1)
);
CREATE UNIQUE INDEX academic_identity_one_fenced_operation_per_user
    ON academic_identity_operation_targets(user_id) WHERE fenced AND user_id IS NOT NULL;
CREATE INDEX academic_identity_recovery_work
    ON academic_identity_operations(next_attempt_at,operation_id)
    WHERE phase IN ('ACCEPTED','AUTH_PREPARED','LOCAL_COMMITTED','FAILED_SAFE');

-- Local group/CAS fences cover membership moves and old+new headman replacement.
-- Their local FK is installed with the retained groups domain baseline.
CREATE TABLE academic_identity_operation_groups (
    operation_id uuid NOT NULL REFERENCES academic_identity_operations(operation_id) ON DELETE RESTRICT,
    group_id bigint NOT NULL CHECK (group_id > 0),
    expected_group_revision bigint NOT NULL CHECK (expected_group_revision > 0),
    fenced boolean NOT NULL DEFAULT false,
    PRIMARY KEY (operation_id,group_id)
);
CREATE UNIQUE INDEX academic_identity_one_fenced_operation_per_group
    ON academic_identity_operation_groups(group_id) WHERE fenced;
