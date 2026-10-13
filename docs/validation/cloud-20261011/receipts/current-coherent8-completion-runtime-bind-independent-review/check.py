"""Finite parameter/SAFE consumer join, no native filesystem/namespace operation."""
import ast
import copy
import datetime
import hashlib
import json
from pathlib import Path
import re
import types
import zipfile

OWN = Path(__file__).parent
SOURCE = Path('/workspace/scratch/rct-validation/current_host_runtime_bind_20261012')
ROOT_RECEIPTS = Path('/workspace/rutcampustrack/docs/validation/cloud-20261011/receipts')
groups = []


def passed(name, **facts):
    groups.append(dict(name=name, outcome='PASS_SOURCE_SAFE_RAM', **facts))


with zipfile.ZipFile(ROOT_RECEIPTS / 'academic-current-stage-completion-actual/actual-academic-completion01.safe.zip') as z:
    assert z.testzip() is None
    manifest = json.loads(z.read('manifest.json'))
    for r in manifest['files']:
        data = z.read(r['name'])
        assert len(data) == r['bytes'] and hashlib.sha256(data).hexdigest() == r['sha256']
    proof = json.loads(z.read('stage-result.safe.json'))
    commit = json.loads(z.read('stage-committed.safe.json'))
    specs = json.loads(z.read('host-service-specs.safe.json'))
    template = json.loads((SOURCE / 'restart-grant.template.safe.json').read_text())
    assert hashlib.sha256(z.read('stage-result.safe.json')).hexdigest() == template['academic_stage_receipt_sha256']
    assert hashlib.sha256(z.read('stage-committed.safe.json')).hexdigest() == template['academic_stage_commit_receipt_sha256']
    assert hashlib.sha256(z.read('host-service-specs.safe.json')).hexdigest() == template['effective_host_service_specs_sha256']
    assert commit['stage_result_sha256'] == template['academic_stage_receipt_sha256']
    assert proof['host_service_specs_sha256'] == template['effective_host_service_specs_sha256']
passed('actual229e_result_commit_spec_and_template_SHA_join')

# Reuse ONLY inspected author's public/RAM setup, not its24 assertions/campaign.
# The prefix up to the consumer definition contains no target main, writes,
# artifact/stat/namespace/private operations; reads only public SAFE/source/specs.
setup = ast.parse((SOURCE / 'binding-controls.py').read_bytes())
setup_nodes = [n for n in setup.body if n.end_lineno <= 130]
fixture = {'__file__': str(SOURCE / 'binding-controls.py')}
exec(compile(ast.Module(body=setup_nodes, type_ignores=[]), 'inspected_SAFE_RAM_fixture_only', 'exec'), fixture)
assert fixture['proof'] == proof and fixture['commit'] == commit and fixture['specs'] == specs
source_boundary = ast.parse((fixture['SOURCE'] / 'refresh-boundary.py').read_bytes())
constants = {n.targets[0].id: ast.literal_eval(n.value) for n in source_boundary.body
             if isinstance(n, ast.Assign) and isinstance(n.targets[0], ast.Name)
             and n.targets[0].id in ('FIX_SHA', 'FIX_REVISION')}
fixture['boundary'].FIX_SHA = constants['FIX_SHA']
fixture['boundary'].FIX_REVISION = constants['FIX_REVISION']
verify = fixture['consumer']['verify']
grant = fixture['grant']
runner = fixture['SOURCE'] / 'run-academic-restart.py'
assert verify(grant, runner) == specs and fixture['state']['namespace_closed'] == 1
passed('actual_unchanged_verify_package_rebind_runtime_consumer_returns_real_SAFE_specs_RAM_artifacts')


def deny(name, g, message):
    before = fixture['state']['namespace_closed']
    try:
        verify(g, runner)
    except RuntimeError as error:
        assert str(error) == message
        assert fixture['state']['namespace_closed'] == before + 1
        passed(name, exact_denial=message, modeled_namespace_closed=True)
    else:
        raise AssertionError(name)


deny('wrong_exact_commit_digest', dict(grant, academic_stage_commit_receipt_sha256='0' * 64),
     'Exact final reconciled stage commit required')
fixture['state']['bad_meta'] = {'st_ino': -1}
deny('changed_member_inode', grant, 'Frozen new Academic payload changed')
fixture['state']['bad_meta'] = None
fixture['state']['late'] = True
deny('late_final_clock', grant, 'RAM expiry after full verification')
fixture['state']['late'] = False

# Original durable policy constants are from real source, not the new template.
old = SOURCE.parent / 'current_host_restart_data157_docker_correction/restart-boundary.py'
old_tree = ast.parse(old.read_bytes())
nodes = [n for n in old_tree.body if
         (isinstance(n, ast.Assign) and isinstance(n.targets[0], ast.Name) and n.targets[0].id in ('DB', 'KEY'))
         or (isinstance(n, ast.FunctionDef) and n.name == 'validate_restart_fields')]
policy = {'Path': Path, 'datetime': datetime, 're': re}
exec(compile(ast.Module(body=nodes, type_ignores=[]), str(old), 'exec'), policy)
now = datetime.datetime(2026, 10, 12, 23, tzinfo=datetime.timezone.utc)
g = dict(template, not_before_utc=now.isoformat(), stop_utc=(now + datetime.timedelta(seconds=1800)).isoformat())
assert policy['validate_restart_fields'](g, now) == 1800
passed('actual_original_DB_KEY_policy_newcohort1800_positive_RAM')
for name, value in [('repeat_bootstrap_authorized', True), ('data_reset_authorized', True),
                    ('reuse_existing_mongo_key', '/RAM/foreign'), ('cohort_name', '../foreign')]:
    try:
        policy['validate_restart_fields'](dict(g, **{name: value}), now)
    except RuntimeError as error:
        assert str(error) == 'New exact durable restart grant required'
        passed('original_policy_' + name + '_denied')
    else:
        raise AssertionError(name)

assert [s for s in specs if s['service'] != 'academic'] == [s for s in
    json.loads(fixture['table'][str(fixture['old_specs'])]) if s['service'] != 'academic']
assert proof['producer'] == template['frozen_source_revision'] == constants['FIX_REVISION']
assert len(proof['files']) == 1797 and len(proof['library_order']) == 182 and proof['copied_this_operation'] == 0
assert len(proof['effective8_artifacts']) == 8
assert all(r['producer'] == ('96c7aee0a114e978ce3ed2976127a2a59c1a69e3' if r['service'] == 'academic'
                           else '908fc8c27be82e4a0d7db7bf4d7b2c4a62225916') for r in proof['effective8_artifacts'])
passed('other6_specs_equal_mixed8_producers_and_zero_copy_actual_SAFE_facts')

result = {'scope': 'FINITE_COHERENT8_BIND_PARAMETERS_ONLY', 'groups': groups,
          'author24_campaign': 'INSPECTED_NOT_EXECUTED', 'fixture_setup_only_reused': True,
          'artifact_FS_namespace_JAR_tool_hash_clock': 'RAM_MODELED',
          'actual_private_prefix_JAR_data_ports_resources_operations': False,
          'actual_runtime': 'NOT_RUN', 'actual_capacity_namespace_data_readiness': 'PREREQUISITE_NOT_PASSED'}
(OWN / 'checks.safe.json').write_text(json.dumps(result, indent=2) + '\n')
print(json.dumps({'finite_SOURCE_SAFE_RAM_groups': len(groups), 'actual_runtime': 'NOT_RUN',
                  'fresh_runtime_prerequisites': 'NOT_PASSED'}))
