#!/usr/bin/env bash
# PostgreSQL16 first-volume initialization only. Existing volumes are never modified.
# Values come from mounted deployment secrets; never enable shell tracing here.
set -Eeuo pipefail
export RCT_RUNTIME_PASSWORD="$(cat /run/secrets/rct-runtime-password)"
export RCT_MIGRATION_PASSWORD="$(cat /run/secrets/rct-migration-password)"
export RCT_MONITOR_PASSWORD="$(cat /run/secrets/rct-monitor-password)"
if [[ "$POSTGRES_DB" == "academic_db" ]]; then
    export RCT_AUTH_RUNTIME_PASSWORD="$(cat /run/secrets/rct-auth-runtime-password)"
    export RCT_ACADEMIC_RUNTIME_PASSWORD="$(cat /run/secrets/rct-academic-runtime-password)"
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
-- The shared legacy principal stays until the new ownership migration revokes
-- its Academic/Auth table access. Schedule uses its independent cluster role.
SELECT current_database() = 'academic_db' AS academic_database \gset
\if :academic_database
\getenv auth_runtime_password RCT_AUTH_RUNTIME_PASSWORD
\getenv academic_runtime_password RCT_ACADEMIC_RUNTIME_PASSWORD
CREATE ROLE rct_auth_runtime LOGIN NOSUPERUSER NOCREATEDB NOCREATEROLE NOREPLICATION NOBYPASSRLS;
CREATE ROLE rct_academic_runtime LOGIN NOSUPERUSER NOCREATEDB NOCREATEROLE NOREPLICATION NOBYPASSRLS;
CREATE ROLE rct_auth_command_owner NOLOGIN NOSUPERUSER NOCREATEDB NOCREATEROLE NOREPLICATION NOBYPASSRLS;
SELECT format('ALTER ROLE rct_auth_runtime PASSWORD %L', :'auth_runtime_password') \gexec
SELECT format('ALTER ROLE rct_academic_runtime PASSWORD %L', :'academic_runtime_password') \gexec
SELECT format('GRANT CONNECT ON DATABASE %I TO rct_auth_runtime, rct_academic_runtime', current_database()) \gexec
GRANT USAGE ON SCHEMA public TO rct_auth_runtime, rct_academic_runtime, rct_auth_command_owner;
REVOKE CREATE ON SCHEMA public FROM rct_auth_runtime, rct_academic_runtime, rct_auth_command_owner;
GRANT rct_auth_command_owner TO rct_migrator;
ALTER ROLE rct_auth_runtime SET search_path TO pg_catalog, public;
ALTER ROLE rct_academic_runtime SET search_path TO pg_catalog, public;
\endif
SQL
unset RCT_RUNTIME_PASSWORD RCT_MIGRATION_PASSWORD RCT_MONITOR_PASSWORD
unset RCT_AUTH_RUNTIME_PASSWORD RCT_ACADEMIC_RUNTIME_PASSWORD
