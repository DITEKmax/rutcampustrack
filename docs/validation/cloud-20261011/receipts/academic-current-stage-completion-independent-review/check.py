"""Targeted completion source/SAFE/RAM only. No live prefix or JAR read."""
import argparse
import ast
import copy
import contextlib
import hashlib
import io
import json
from pathlib import Path, PurePosixPath
import re
import types
import zipfile

OWN = Path(__file__).parent
SOURCE = Path('/workspace/scratch/rct-validation/current_academic_stage_completion_prepare')
OLD = Path('/workspace/scratch/rct-validation/current_academic_stage_resume_guard_correction')
RECEIPTS = Path('/workspace/rutcampustrack/docs/validation/cloud-20261011/receipts')
groups = []


def passed(name, **facts):
    groups.append(dict(name=name, outcome='PASS_SOURCE_SAFE_RAM', **facts))


def deny(name, action, message):
    try:
        action()
    except RuntimeError as e:
        assert str(e) == message
        passed(name, exact_denial=message)
    else:
        raise AssertionError(name)


with zipfile.ZipFile(RECEIPTS / 'academic-current-build-actual/actual-academic-build01.safe.zip') as z:
    b = z.read('build-result.safe.json')
    assert hashlib.sha256(b).hexdigest() == '08dd08de01d3c4fe5c0ebcea7cd53e22c41f36fb4d65e94b8d216b8aacda80ac'
    built = json.loads(b)
with zipfile.ZipFile(RECEIPTS / 'academic-current-stage-resume02-fullcopy/actual-stage-resume02.safe.zip') as z:
    full_bytes = z.read('full-payload-readonly.safe.json')
    authority_bytes = z.read('manifest.json')
    assert hashlib.sha256(full_bytes).hexdigest() == '01a6f122bff2a4f20c2f6ef1a7e836ffcd46953e409c277e2418ae573e8223cd'
    assert hashlib.sha256(authority_bytes).hexdigest() == '15963c993dca297d0d8ffc6e5bda25dc2c478b20821cef6ec6dfa627c27982d6'
    doc = json.loads(full_bytes)
    authority = json.loads(authority_bytes)
expected = {r['path']: r for r in built['regular_entries']}
assert len(expected) == len(doc['files']) == 1797
assert {r['path']: {k: r[k] for k in ('path', 'bytes', 'sha256')} for r in doc['files']} == expected
passed('independent_JOIN_full1797_SAFE_rows_to_original_BUILD08dd_not_self_expected')

tree = ast.parse((SOURCE / 'stage-academic.py').read_bytes())
main = next(n for n in tree.body if isinstance(n, ast.FunctionDef) and n.name == 'main')
attrs = {n.attr for n in ast.walk(main) if isinstance(n, ast.Attribute)}
assert not attrs & {'copy', 'mkdir', 'extract', 'extractall', 'chmod', 'unlink', 'remove', 'rmdir', 'AppWriter'}
declared = main.body[:3]
parser_ns = {'argparse': argparse}
exec(compile(ast.Module(body=declared, type_ignores=[]), 'actual_parser_declarations', 'exec'), parser_ns)
with contextlib.redirect_stderr(io.StringIO()):
    try:
        parser_ns['parser'].parse_args(['--grant', '/RAM/grant'])
    except SystemExit as e:
        assert e.code == 2
    else:
        raise AssertionError('Completion flag was not required')
assert parser_ns['parser'].parse_args(['--grant', '/RAM/grant', '--complete-only']).complete_only is True
passed('actual_argparse_requires_completion_flag_and_main_has_no_recopy_mutation_dispatch')

selected = [n for n in tree.body if isinstance(n, ast.FunctionDef) and n.name in ('canonical_libraries', 'complete_existing')]
state = {'doc': copy.deepcopy(doc), 'authority': authority, 'bad_hash': None, 'fd_calls': 0}
base = SOURCE.parent / 'current_academic_stage_resume_guard_correction/actual-stage-resume02'
manifest_path, full_path = base / 'manifest.json', base / 'full-payload-readonly.safe.json'


class SafePath(type(Path())):
    def read_text(self):
        if str(self) == str(manifest_path): return json.dumps(state['authority'])
        if str(self) == str(full_path): return json.dumps(state['doc'])
        raise AssertionError('Actual prefix/private read prohibited')


def sha(path):
    if str(path) == str(manifest_path):
        return '0' * 64 if state['bad_hash'] == 'manifest' else hashlib.sha256(authority_bytes).hexdigest()
    if str(path) == str(full_path):
        return '0' * 64 if state['bad_hash'] == 'receipt' else hashlib.sha256(full_bytes).hexdigest()
    raise AssertionError('Unapproved hash read')


path_ns = {'PurePosixPath': PurePosixPath}
path_fn = next(n for n in ast.parse((SOURCE / 'partial-resume.py').read_bytes()).body
               if isinstance(n, ast.FunctionDef) and n.name == 'path_name')
exec(compile(ast.Module(body=[path_fn], type_ignores=[]), 'unchanged_path_name', 'exec'), path_ns)
dirs = {'.'} | {str(p) for name in expected for p in Path(name).parents if str(p) != '.'}
records = {('app' if name == '.' else 'app/' + name):
           dict(kind='directory', uid=1000, gid=1000, mode=0o755, device=33, inode=i + 1)
           for i, name in enumerate(sorted(dirs))}
monitor = types.SimpleNamespace(base_records=records)
ns_marker = object()


def verify_dispatch(ns, app, files, directories):
    assert ns is ns_marker and str(app) == '/RAM/app'
    assert len(files) == 1797 and set(files) == set(expected)
    assert set(directories) == dirs and len(dirs) == 60
    for name, row in files.items():
        assert row['sha256'] == expected[name]['sha256'] and row['bytes'] == expected[name]['bytes']
    state['fd_calls'] += 1


defs = {'OWN': SafePath(SOURCE), 'Path': SafePath, 'json': json, 're': re, 'boundary': types.SimpleNamespace(sha=sha)}
exec(compile(ast.Module(body=selected, type_ignores=[]), 'actual_completion_definitions', 'exec'), defs)
grant = json.loads((SOURCE / 'stage-grant.template.safe.json').read_text())
resume = types.SimpleNamespace(path_name=path_ns['path_name'], verify_tree=verify_dispatch)


def invoke(g=grant, current_monitor=monitor):
    return defs['complete_existing'](g, built, ns_marker, SafePath('/RAM/app'), current_monitor, resume)


files, actual_dirs = invoke()
assert len(files) == 1797 and actual_dirs == dirs and state['fd_calls'] == 1
passed('whole_fixed_completion_grant_original_BUILD_fullauthority1797_to_unchanged_FD_dispatch_positive')
for key in ('stage_completion_authorized', 'stage_resume_authorized', 'stage_full_payload_receipt_sha256'):
    g = dict(grant, **{key: not grant[key] if type(grant[key]) is bool else '0' * 64})
    deny('fixed_typed_grant_' + key, lambda: invoke(g), 'Exact1797 completion-only grant binding required')
for key in ('manifest', 'receipt'):
    state['bad_hash'] = key
    deny('full_authority_hash_' + key, invoke, 'Exact actual1797 proof seal required')
state['bad_hash'] = None
state['authority'] = {'files': []}
deny('full_receipt_manifest_membership', invoke, 'Full1797 receipt must belong to actual1596 seal')
state['authority'] = authority
for key, value in [('sha256', '0' * 64), ('mode', 0o600), ('nlink', 2)]:
    state['doc'] = copy.deepcopy(doc)
    state['doc']['files'][0][key] = value
    deny('full_member_' + key, invoke, 'Complete1797 proof member identity required')
state['doc'] = copy.deepcopy(doc)
state['doc']['files'].pop()
deny('incomplete1796_does_not_resume_copy', invoke, 'Exact complete stopped1797 currentJAR authority required')
state['doc'] = copy.deepcopy(doc)
extra = copy.deepcopy(records); extra['app/foreign'] = dict(next(iter(records.values())))
deny('extra_directory', lambda: invoke(current_monitor=types.SimpleNamespace(base_records=extra)),
     'Full current app directory composition missing/extra denied')

tools = 'BOOT-INF/lib/spring-boot-jarmode-tools-3.5.16.jar'
libs = sorted(r['path'] for r in built['regular_entries'] if r['path'].startswith('BOOT-INF/lib/') and r['path'].endswith('.jar') and r['path'] != tools)
assert len(libs) == 182
lines = ['- "' + name + '"' for name in libs]
canonical = defs['canonical_libraries']
assert canonical(lines, built['regular_entries']) == libs
assert canonical(list(reversed(lines)), built['regular_entries']) == list(reversed(libs))
passed('canonical182_of183_positive_preserves_supplied_index_order_tools_retained')
old_tree = ast.parse((OLD / 'stage-academic.py').read_bytes())
old_gate = next(n for n in ast.walk(old_tree) if isinstance(n, ast.If)
    and isinstance(n.test, ast.Compare) and ast.unparse(n.test).startswith('set(libraries) !='))
deny('original183_equality_counterexample', lambda: exec(compile(ast.Module(body=[old_gate], type_ignores=[]),
    'old_index_gate', 'exec'), {'libraries': libs, 'proof': built}), 'Exact complete classpath index required')
rule_error = 'Exact canonical182 application libs plus sole bundled183rd tools required'
for name, candidate_lines, candidate_entries, message in [
    ('duplicate182', lines[:-1] + [lines[0]], built['regular_entries'], rule_error),
    ('missing181', lines[:-1], built['regular_entries'], rule_error),
    ('indexed_tools182', lines[:-1] + ['- "' + tools + '"'], built['regular_entries'], rule_error),
    ('unknown_unindexed_same183', lines,
     [dict(r, path='BOOT-INF/lib/foreign.jar') if r['path'] == tools else r for r in built['regular_entries']], rule_error),
    ('wrong_fixed_tools_version', lines,
     [dict(r, path='BOOT-INF/lib/spring-boot-jarmode-tools-3.5.15.jar') if r['path'] == tools else r for r in built['regular_entries']], rule_error),
    ('traversal', lines[:-1] + ['- "BOOT-INF/lib/../foreign.jar"'], built['regular_entries'], 'Canonical standard classpath line required'),
]:
    deny('canonical_' + name, lambda: canonical(candidate_lines, candidate_entries), message)

proof = json.loads((SOURCE / 'boot-packager-proof.safe.json').read_text())
for method in proof['methods']:
    code = bytes.fromhex(method['code_hex'])
    assert hashlib.sha256(code).hexdigest() == method['code_sha256']
    assert all(code[ref['offset']] == ref['opcode'] for ref in method['field_method_references'])
methods = {(m['method'], m['descriptor']): m for m in proof['methods']}
finish = methods[('finish', '()V')]['field_method_references']
assert finish[1]['reference'].endswith('.writeJarToolsIfNecessary()V')
assert finish[3]['reference'].endswith('.writeClassPathIndexIfNecessary()V')
tools_method = next(m for m in proof['methods'] if m['method'] == 'writeJarModeLibrary')
assert bytes.fromhex(tools_method['code_hex'])[20] == 3  # iconst_0 library flag, before writeEntry.
index_method = next(m for m in proof['methods'] if m['method'] == 'writeClassPathIndexIfNecessary')
assert any('.writtenLibraries' in ref['reference'] for ref in index_method['field_method_references'])
passed('provided_cached_packager_SAFE_code_SHA_refs_false_tools_flag_and_writtenLibraries_index',
       actual_cached_JAR_classfile_read=False, new_official_vendor_authentication=False)

(OWN / 'checks.safe.json').write_text(json.dumps({'scope': 'INDEPENDENT_TARGETED_COMPLETION_SOURCE', 'groups': groups,
    'native_stage': 'NOT_RUN', 'actual_prefix_JAR_private_reads': False,
    'original43_24_18_rerun': False, 'unchanged_FD_dispatch_mocked_not_actual_scan': True,
    'actual_index_line_body_not_read_synthetic_order_controls': True}, indent=2) + '\n')
print(json.dumps({'selected_SOURCE_SAFE_RAM_groups': len(groups), 'native_stage': 'NOT_RUN', 'actual_prefix_scan': False}))
