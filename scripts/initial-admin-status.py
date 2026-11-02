#!/usr/bin/env python3
"""Read-only durable bootstrap completion; never creates or restores an administrator."""
import argparse
import hashlib
import json
import re
import subprocess
import sys
import uuid


class Hold(Exception):
    def __init__(self, reason):
        self.reason = reason


def require(value, reason='BINDING'):
    if not value:
        raise Hold(reason)


def sql(database, query):
    # Reuse the production postflight's local read authority, without selecting credentials.
    statement = "BEGIN READ ONLY; SET LOCAL statement_timeout='15s'; " + query + '; COMMIT;'
    try:
        result = subprocess.run(
            ['docker', 'exec', '-i', '--user', 'postgres', 'rct-postgres-academic',
             'psql', '-X', '-qAt', '-v', 'ON_ERROR_STOP=1', '-U', 'rct_bootstrap', '-d', database],
            input=statement.encode(), capture_output=True, timeout=20, check=False)
        require(result.returncode == 0 and len(result.stdout) <= 16384
                and len(result.stderr) <= 16384, 'DB_READ')
        return result.stdout.decode().strip()
    except (OSError, subprocess.TimeoutExpired, UnicodeError):
        raise Hold('DB_READ') from None


AUTH_COUNTS = """SELECT json_build_object(
 'receipts',(SELECT count(*) FROM public.auth_initial_admin_bootstrap),
 'operations',(SELECT count(*) FROM public.auth_identity_operations WHERE initial_admin_bootstrap),
 'administrators',(SELECT count(*) FROM public.user_role_grants WHERE role='admin')
   +(SELECT count(*) FROM public.admin_access_profiles WHERE assigned))"""
ACADEMIC_COUNTS = """SELECT json_build_object(
 'receipts',(SELECT count(*) FROM public.academic_initial_admin_bootstrap),
 'administrators',(SELECT count(*) FROM public.academic_profiles WHERE directory_role='ADMIN'))"""
AUTH_BINDING = """SELECT json_build_object('operation',b.operation_id,'user',b.user_id,
 'command',encode(b.command_hash,'hex'),'profile',encode(b.profile_digest,'hex'),
 'catalog',o.committed_catalog_version) FROM public.auth_initial_admin_bootstrap b
 JOIN public.auth_identity_operations o USING(operation_id)
 WHERE b.singleton AND o.initial_admin_bootstrap AND o.phase='COMMITTED'
 AND o.committed_catalog_version>0 AND o.command_hash=b.command_hash
 AND EXISTS(SELECT 1 FROM public.auth_identity_operation_targets t
   WHERE t.operation_id=b.operation_id AND t.user_id=b.user_id
   AND t.committed_academic_revision=1)"""


def completion(read=sql, *, require_ready=False):
    try:
        auth = json.loads(read('auth_db', AUTH_COUNTS))
        academic = json.loads(read('academic_db', ACADEMIC_COUNTS))
        require(isinstance(auth, dict) and set(auth) == {'receipts', 'operations', 'administrators'})
        require(isinstance(academic, dict) and set(academic) == {'receipts', 'administrators'})
        require(all(type(value) is int and value >= 0 for value in [*auth.values(), *academic.values()]))
        if not any([*auth.values(), *academic.values()]):
            require(not require_ready, 'ABSENT')
            return 'ABSENT'
        require(auth['receipts'] == auth['operations'] == academic['receipts'] == 1, 'INCOMPLETE')
        binding = json.loads(read('auth_db', AUTH_BINDING))
        require(isinstance(binding, dict) and set(binding) == {'operation', 'user', 'command', 'profile', 'catalog'})
        require(isinstance(binding['operation'], str)
                and str(uuid.UUID(binding['operation'])) == binding['operation'])
        require(type(binding['user']) is int and binding['user'] > 0
                and type(binding['catalog']) is int and binding['catalog'] > 0)
        require(all(isinstance(binding[key], str) and re.fullmatch('[0-9a-f]{64}', binding[key])
                    for key in ('command', 'profile')))
        # Values are validated before SQL interpolation and remain only in private process memory.
        operation, user, catalog = binding['operation'], binding['user'], binding['catalog']
        receipt = hashlib.sha256(json.dumps(
            {'version': 1, 'operationId': operation, 'commandHash': binding['command'],
             'profileDigest': binding['profile'], 'userId': user, 'rolesVersion': 1,
             'academicRevision': 1}, sort_keys=True, separators=(',', ':')).encode()).hexdigest()
        query = f"""SELECT count(*) FROM public.academic_initial_admin_bootstrap b
 WHERE b.singleton AND b.operation_id='{operation}'::uuid AND b.user_id={user}
 AND encode(b.command_hash,'hex')='{binding['command']}'
 AND encode(b.profile_digest,'hex')='{binding['profile']}' AND encode(b.receipt_hash,'hex')='{receipt}'
 AND b.finalized_catalog_version={catalog} AND b.academic_revision=1 AND b.applied_auth_roles_version=1"""
        require(read('academic_db', query) == '1', 'INCOMPLETE')
        # Historical COMMITTED/FINALIZED receipts remain authoritative after legitimate edits,
        # demotion or archival. Never regrant roles or reset a password on a later deployment.
        if require_ready:
            query = f"""SELECT count(*) FROM public.auth_identities i
 JOIN public.admin_access_profiles a ON a.user_id=i.id WHERE i.id={user}
 AND i.provision_operation_id='{operation}'::uuid AND i.account_state='ACTIVE'
 AND i.fence_operation_id IS NULL AND a.assigned AND a.mode='FULL'
 AND cardinality(a.permissions)=0 AND EXISTS(SELECT 1 FROM public.user_role_grants g
   WHERE g.user_id=i.id AND g.role='admin' AND g.status='active' AND g.group_id IS NULL)"""
            require(read('auth_db', query) == '1', 'NOT_READY')
            query = f"""SELECT count(*) FROM public.academic_profiles p WHERE p.id={user}
 AND p.directory_role='ADMIN' AND p.directory_status='ACTIVE' AND p.academic_revision>=1
 AND p.applied_auth_roles_version>=1 AND p.archived_at IS NULL"""
            require(read('academic_db', query) == '1', 'NOT_READY')
        return 'COMPLETE'
    except (ValueError, KeyError, TypeError, OverflowError):
        raise Hold('BINDING') from None


def main(argv=None):
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--require-ready', action='store_true')
    args = parser.parse_args(argv)
    try:
        print(completion(require_ready=args.require_ready))
        return 0
    except Hold as failure:
        print('HOLD')
        print('INITIAL_ADMIN_STATUS_V1 state=HOLD reason=' + failure.reason, file=sys.stderr)
        return 2


if __name__ == '__main__':
    sys.exit(main())
