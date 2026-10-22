"""Narrow completion-only selected source/RAM controls; no prefix/private/native use."""
import ast
import copy
import hashlib
import json
from pathlib import Path
import re
import types

OWN = Path(__file__).parent
OLD = OWN.parent / 'current_academic_stage_resume_guard_correction'
BASE = OLD / 'actual-stage-resume02'
FULL = BASE / 'full-payload-readonly.safe.json'
full_bytes = FULL.read_bytes()
assert hashlib.sha256(full_bytes).hexdigest() == '01a6f122bff2a4f20c2f6ef1a7e836ffcd46953e409c277e2418ae573e8223cd'
assert hashlib.sha256((BASE / 'manifest.json').read_bytes()).hexdigest() == '15963c993dca297d0d8ffc6e5bda25dc2c478b20821cef6ec6dfa627c27982d6'
doc = json.loads(full_bytes)
built = {'producer_revision': doc['source_producer'], 'jar_sha256': doc['current_JAR_sha256'],
         'regular_entries': [{'path': r['path'], 'sha256': r['sha256'], 'bytes': r['bytes']} for r in doc['files']]}
results = []


def passed(name):
    results.append(name)


def deny(name, action):
    try:
        action()
    except RuntimeError:
        passed(name)
    else:
        raise AssertionError(name)


tree = ast.parse((OWN / 'stage-academic.py').read_text())
nodes = [n for n in tree.body if isinstance(n, ast.FunctionDef)
         and n.name in ['canonical_libraries', 'complete_existing']]
assert len(nodes) == 2
state = {'doc': copy.deepcopy(doc), 'hash_bad': None, 'manifest_bad': False, 'tree_calls': 0}
sealed = json.loads((BASE / 'manifest.json').read_text())


class PublicPath(type(Path())):
    def read_text(self, *args, **kwargs):
        if str(self) == str(FULL):
            return json.dumps(state['doc'])
        if str(self) == str(BASE / 'manifest.json'):
            return json.dumps({'files': []}) if state['manifest_bad'] else json.dumps(sealed)
        raise AssertionError('No actual prefix/private read allowed')


def safe_hash(path):
    if str(path) == str(BASE / 'manifest.json'):
        return '0' * 64 if state['hash_bad'] == 'manifest' else '15963c993dca297d0d8ffc6e5bda25dc2c478b20821cef6ec6dfa627c27982d6'
    if str(path) == str(FULL):
        return '0' * 64 if state['hash_bad'] == 'receipt' else '01a6f122bff2a4f20c2f6ef1a7e836ffcd46953e409c277e2418ae573e8223cd'
    raise AssertionError('No other public hash read allowed')


def tree_verify(ns, app, files, directories):
    state['tree_calls'] += 1
    assert len(files) == 1797 and len(directories) == 60
    assert set(files) == {r['path'] for r in built['regular_entries']}
    assert ns is ns_mock and str(app) == '/RAM/app'


definitions = {'OWN': PublicPath(OWN), 'Path': PublicPath, 'json': json, 're': re,
               'boundary': types.SimpleNamespace(sha=safe_hash)}
exec(compile(ast.Module(body=nodes, type_ignores=[]), 'stage-academic.py', 'exec'), definitions)
canonical = definitions['canonical_libraries']
tools = 'BOOT-INF/lib/spring-boot-jarmode-tools-3.5.16.jar'
alllibs = {r['path'] for r in built['regular_entries']
           if r['path'].startswith('BOOT-INF/lib/') and r['path'].endswith('.jar')}
lines = ['- "' + name + '"' for name in sorted(alllibs - {tools})]
assert len(alllibs) == 183 and len(lines) == 182
assert canonical(lines, built['regular_entries']) == sorted(alllibs - {tools})
assert len(canonical(list(reversed(lines)), built['regular_entries'])) == 182
passed('exact_generated183_payload182_index_tools_exclusion_order_preserved')
# Actual former whole equality condition is selected from the immutable caller.
old_tree = ast.parse((OLD / 'stage-academic.py').read_text())
old_if = next(n for n in ast.walk(old_tree) if isinstance(n, ast.If)
              and isinstance(n.test, ast.Compare) and ast.unparse(n.test).startswith('set(libraries) !='))
deny('old_actual183_equality_rejects_known182_standard_index', lambda: exec(
    compile(ast.Module(body=[old_if], type_ignores=[]), 'old_classpath_gate', 'exec'),
    {'libraries': sorted(alllibs - {tools}), 'proof': {'regular_entries': built['regular_entries']}}))
for name, changed_lines, changed_entries in [
    ('duplicate_index', lines[:-1] + [lines[0]], built['regular_entries']),
    ('missing_index_member', lines[:-1], built['regular_entries']),
    ('additional_index_member', lines + ['- "BOOT-INF/lib/foreign.jar"'], built['regular_entries']),
    ('indexed_tools', lines[:-1] + ['- "' + tools + '"'], built['regular_entries']),
    ('unexpected_unindexed_library', lines[:-1], built['regular_entries']),
    ('path_traversal', lines[:-1] + ['- "BOOT-INF/lib/../foreign.jar"'], built['regular_entries']),
    ('bad_line_syntax', lines[:-1] + ['BOOT-INF/lib/foreign.jar'], built['regular_entries']),
    ('wrong_tools_version', lines, [dict(r, path='BOOT-INF/lib/spring-boot-jarmode-tools-3.5.15.jar') if r['path'] == tools else r for r in built['regular_entries']]),
    ('missing_bundled_tools', lines, [r for r in built['regular_entries'] if r['path'] != tools]),
    ('additional_bundled_library', lines, built['regular_entries'] + [{'path': 'BOOT-INF/lib/foreign.jar'}]),
]:
    deny('canonical_' + name, lambda: canonical(changed_lines, changed_entries))
grant = json.loads((OWN / 'stage-grant.template.safe.json').read_text())
ns_mock = object()
directories = {'.'}
directories.update(str(parent) for r in built['regular_entries'] for parent in Path(r['path']).parents if str(parent) != '.')
records = {('app' if name == '.' else 'app/' + name):
           {'kind': 'directory', 'uid': 1000, 'gid': 1000, 'mode': 0o755,
            'device': 33, 'inode': i + 1} for i, name in enumerate(sorted(directories))}
monitor = types.SimpleNamespace(base_records=records)
resume = types.SimpleNamespace(path_name=lambda p: p, verify_tree=tree_verify)


def invoke(g=grant, m=monitor):
    return definitions['complete_existing'](g, built, ns_mock, PublicPath('/RAM/app'), m, resume)


files, dirs = invoke()
assert len(files) == 1797 and len(dirs) == 60 and state['tree_calls'] == 1
passed('whole_completion_grant_seals_actual_full1797_proof_directory_set_FD_dispatch_positive')
for key in ['stage_completion_authorized', 'stage_resume_authorized', 'stage_full_payload_manifest',
            'stage_full_payload_manifest_sha256', 'stage_full_payload_receipt', 'stage_full_payload_receipt_sha256']:
    g = copy.deepcopy(grant)
    g[key] = not g[key] if type(g[key]) is bool else 'WRONG_FIXED_BINDING'
    deny('completion_grant_' + key, lambda: invoke(g))
for name in ['manifest', 'receipt']:
    state['hash_bad'] = name
    deny('completion_authority_' + name + '_SHA_denied', invoke)
state['hash_bad'] = None
state['manifest_bad'] = True
deny('completion_fullproof_manifest_membership_denied', invoke)
state['manifest_bad'] = False
for field, value in [('sha256', '0' * 64), ('bytes', 0), ('mode', 0o600),
                     ('uid', 0), ('gid', 0), ('nlink', 2), ('path', '../foreign'),
                     ('matches_current_JAR_member', False)]:
    state['doc'] = copy.deepcopy(doc)
    state['doc']['files'][0][field] = value
    deny('completion_proof_member_' + field, invoke)
state['doc'] = copy.deepcopy(doc)
state['doc']['files'].pop()
deny('completion_partial1796_denied_no_copy_resume', invoke)
state['doc'] = copy.deepcopy(doc)
state['doc']['files'].append(copy.deepcopy(state['doc']['files'][0]))
deny('completion_duplicate1798_denied', invoke)
state['doc'] = copy.deepcopy(doc)
foreign = copy.deepcopy(records); foreign['app/foreign'] = dict(next(iter(records.values())))
deny('completion_extra_directory_denied', lambda: invoke(m=types.SimpleNamespace(base_records=foreign)))
foreign = copy.deepcopy(records); foreign['app']['uid'] = 0
deny('completion_foreign_directory_owner_denied', lambda: invoke(m=types.SimpleNamespace(base_records=foreign)))
main = next(n for n in tree.body if isinstance(n, ast.FunctionDef) and n.name == 'main')
assert not any(isinstance(n, ast.Attribute) and n.attr in ['copy', 'mkdir', 'unlink', 'chmod', 'AppWriter'] for n in ast.walk(main))
assert any(isinstance(n, ast.Constant) and n.value == '--complete-only' for n in ast.walk(main))
passed('actual_completion_caller_has_no_copy_extract_mkdir_unlink_chmod_path')
print(json.dumps({'scope': 'NARROW_COMPLETION_ONLY_SOURCE_RAM', 'passed': len(results),
                  'controls': results, 'actual_prefix_scan_private_native_copy': False,
                  'original43_24_18_reused_not_rerun': True}, indent=2))
