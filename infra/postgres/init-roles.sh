#!/usr/bin/env bash
# PostgreSQL16 first-volume initialization only. Existing volumes are never modified.
# Values come from mounted deployment secrets; never enable shell tracing here.
set -Eeuo pipefail
read_password() {
    local secret_path="/run/secrets/$1" fallback_name="$2" value
    if [[ -r "$secret_path" ]]; then
        value="$(cat "$secret_path")"
    else
        value="${!fallback_name-}"
    fi
    [[ -n "$value" ]] || { echo "Required database credential input is missing: $1" >&2; return 1; }
    printf '%s' "$value"
}
RCT_RUNTIME_PASSWORD="$(read_password rct-runtime-password POSTGRES_RUNTIME_PASSWORD)"
export RCT_RUNTIME_PASSWORD
RCT_MIGRATION_PASSWORD="$(read_password rct-migration-password POSTGRES_MIGRATION_PASSWORD)"
export RCT_MIGRATION_PASSWORD
RCT_MONITOR_PASSWORD="$(read_password rct-monitor-password POSTGRES_MONITOR_PASSWORD)"
export RCT_MONITOR_PASSWORD
if [[ "$POSTGRES_DB" == "academic_db" ]]; then
    RCT_AUTH_RUNTIME_PASSWORD="$(read_password rct-auth-runtime-password POSTGRES_AUTH_PASSWORD)"
    export RCT_AUTH_RUNTIME_PASSWORD
    RCT_AUTH_MIGRATION_PASSWORD="$(read_password rct-auth-migration-password POSTGRES_AUTH_MIGRATION_PASSWORD)"
    export RCT_AUTH_MIGRATION_PASSWORD
    RCT_ACADEMIC_RUNTIME_PASSWORD="$(read_password rct-academic-runtime-password POSTGRES_ACADEMIC_RUNTIME_PASSWORD)"
    export RCT_ACADEMIC_RUNTIME_PASSWORD
fi
psql --username "$POSTGRES_USER" --dbname "$POSTGRES_DB" --set ON_ERROR_STOP=1 <<'SQL'
\getenv runtime_password RCT_RUNTIME_PASSWORD
\getenv migration_password RCT_MIGRATION_PASSWORD
\getenv monitor_password RCT_MONITOR_PASSWORD
CREATE ROLE rct_runtime LOGIN NOSUPERUSER NOCREATEDB NOCREATEROLE NOREPLICATION NOBYPASSRLS;
CREATE ROLE rct_migrator LOGIN NOSUPERUSER NOCREATEDB NOCREATEROLE NOREPLICATION NOBYPASSRLS;
CREATE ROLE rct_monitor LOGIN NOSUPERUSER NOCREATEDB NOCREATEROLE NOREPLICATION NOBYPASSRLS;
SELECT format('ALTER ROLE rct_runtime PASSWORD %L', :'runtime_password') \gexec
SELECT format('ALTER ROLE rct_migrator PASSWORD %L', :'migration_password') \gexec
SELECT format('ALTER ROLE rct_monitor PASSWORD %L', :'monitor_password') \gexec
SELECT format('REVOKE ALL ON DATABASE %I FROM PUBLIC', current_database()) \gexec
SELECT format('GRANT CONNECT ON DATABASE %I TO rct_runtime, rct_monitor', current_database()) \gexec
SELECT format('GRANT CONNECT, CREATE, TEMPORARY ON DATABASE %I TO rct_migrator', current_database()) \gexec
REVOKE CREATE ON SCHEMA public FROM PUBLIC;
ALTER SCHEMA public OWNER TO rct_migrator;
GRANT USAGE ON SCHEMA public TO rct_runtime, rct_monitor;
GRANT pg_monitor TO rct_monitor;
ALTER ROLE rct_runtime SET search_path TO pg_catalog, public;
-- Schedule retains its independent cluster roles. Academic and Auth share only
-- a PostgreSQL process: database CONNECT and schema ownership remain separate.
SELECT current_database() = 'academic_db' AS academic_database \gset
\if :academic_database
-- Restrict the maintenance databases as well; own-database runtime isolation
-- must not rely only on the two application database grants.
REVOKE CONNECT ON DATABASE postgres,template1 FROM PUBLIC;
\getenv auth_runtime_password RCT_AUTH_RUNTIME_PASSWORD
\getenv academic_runtime_password RCT_ACADEMIC_RUNTIME_PASSWORD
CREATE ROLE rct_auth_runtime LOGIN NOSUPERUSER NOCREATEDB NOCREATEROLE NOREPLICATION NOBYPASSRLS;
CREATE ROLE rct_academic_runtime LOGIN NOSUPERUSER NOCREATEDB NOCREATEROLE NOREPLICATION NOBYPASSRLS;
CREATE ROLE rct_auth_migrator LOGIN NOSUPERUSER NOCREATEDB NOCREATEROLE NOREPLICATION NOBYPASSRLS;
\getenv auth_migration_password RCT_AUTH_MIGRATION_PASSWORD
SELECT format('ALTER ROLE rct_auth_migrator PASSWORD %L', :'auth_migration_password') \gexec
SELECT format('ALTER ROLE rct_auth_runtime PASSWORD %L', :'auth_runtime_password') \gexec
SELECT format('ALTER ROLE rct_academic_runtime PASSWORD %L', :'academic_runtime_password') \gexec
SELECT format('GRANT CONNECT ON DATABASE %I TO rct_academic_runtime', current_database()) \gexec
SELECT format('REVOKE ALL ON DATABASE %I FROM rct_auth_runtime,rct_auth_migrator,rct_runtime', current_database()) \gexec
GRANT USAGE ON SCHEMA public TO rct_academic_runtime;
REVOKE ALL ON SCHEMA public FROM rct_auth_runtime,rct_auth_migrator,rct_runtime;
REVOKE CREATE ON SCHEMA public FROM rct_academic_runtime;
ALTER DEFAULT PRIVILEGES FOR ROLE rct_migrator IN SCHEMA public GRANT SELECT,INSERT,UPDATE,DELETE ON TABLES TO rct_academic_runtime;
ALTER DEFAULT PRIVILEGES FOR ROLE rct_migrator IN SCHEMA public GRANT USAGE,SELECT ON SEQUENCES TO rct_academic_runtime;
ALTER DEFAULT PRIVILEGES FOR ROLE rct_migrator IN SCHEMA public REVOKE EXECUTE ON FUNCTIONS FROM PUBLIC;
CREATE DATABASE auth_db OWNER rct_auth_migrator;
REVOKE ALL ON DATABASE auth_db FROM PUBLIC;
GRANT CONNECT ON DATABASE auth_db TO rct_auth_runtime,rct_monitor;
GRANT CONNECT,CREATE,TEMPORARY ON DATABASE auth_db TO rct_auth_migrator;
\connect auth_db
REVOKE ALL ON SCHEMA public FROM PUBLIC;
ALTER SCHEMA public OWNER TO rct_auth_migrator;
GRANT USAGE ON SCHEMA public TO rct_auth_runtime,rct_monitor;
ALTER DEFAULT PRIVILEGES FOR ROLE rct_auth_migrator IN SCHEMA public GRANT SELECT,INSERT,UPDATE,DELETE ON TABLES TO rct_auth_runtime;
ALTER DEFAULT PRIVILEGES FOR ROLE rct_auth_migrator IN SCHEMA public GRANT USAGE,SELECT ON SEQUENCES TO rct_auth_runtime;
ALTER DEFAULT PRIVILEGES FOR ROLE rct_auth_migrator IN SCHEMA public REVOKE EXECUTE ON FUNCTIONS FROM PUBLIC;
ALTER ROLE rct_auth_runtime SET search_path TO pg_catalog, public;
ALTER ROLE rct_academic_runtime SET search_path TO pg_catalog, public;
\endif
SQL
unset RCT_RUNTIME_PASSWORD RCT_MIGRATION_PASSWORD RCT_MONITOR_PASSWORD
unset RCT_AUTH_RUNTIME_PASSWORD RCT_AUTH_MIGRATION_PASSWORD RCT_ACADEMIC_RUNTIME_PASSWORD
