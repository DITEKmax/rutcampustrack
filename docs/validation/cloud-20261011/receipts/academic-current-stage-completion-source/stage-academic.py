"""Future full Academic /app copies and exact seven-host spec binding; no child."""
import argparse
import copy
import hashlib
import json
import os
from pathlib import Path
import re
import stat
import types
import datetime
import time
import zipfile

OWN = Path(__file__).parent
boundary = types.ModuleType('refresh_boundary')
boundary.__file__ = str(OWN / 'refresh-boundary.py')
if hashlib.sha256((OWN / 'refresh-boundary.py').read_bytes()).hexdigest() != 'a6c56c607d4797e94de0c8e6b5b61b10e255c7308d904940ae1b242695f78272':
    raise RuntimeError('Exact reviewed refresh boundary bytes required')
exec(compile((OWN / 'refresh-boundary.py').read_bytes(), boundary.__file__, 'exec'), boundary.__dict__)
BASE_SPECS = OWN.parent / 'current_host_config_v5/host-service-specs.json'
BASE_ARTIFACTS = OWN.parent / 'current_client_artifacts/eight-production-jars.json'
OLD_APP = OWN.parent / 'current_host_stage/payload/academic/app'
CLOCK = OWN.parent / 'real_client_runtime/teaching-clock-eec3/clock-fixture.jar'


def rebind_specs(specs, app, libraries):
    updated = copy.deepcopy(specs)
    original = next(row for row in specs if row['service'] == 'academic')
    target = next(row for row in updated if row['service'] == 'academic')
    argv = target['argv']
    index = argv.index('-cp') + 1
    old_cp = original['argv'][index].split(':')
    if (original['cwd'] != str(OLD_APP) or old_cp[0] != str(OLD_APP / 'BOOT-INF/classes')
            or old_cp[-1] != str(CLOCK)
            or any(not p.startswith(str(OLD_APP / 'BOOT-INF/lib') + '/') for p in old_cp[1:-1])
            or original['argv'][index + 1] != 'ru.rutcampustrack.academic.AcademicApplication'):
        raise RuntimeError('Exact existing genuine Academic host representation required')
    if not libraries or len(libraries) != len(set(libraries)):
        raise RuntimeError('Complete distinct canonical classpath libraries required')
    if any(not re.fullmatch(r'BOOT-INF/lib/[A-Za-z0-9_.+-]+\.jar', p) for p in libraries):
        raise RuntimeError('Canonical direct production library required')
    target['cwd'] = str(app)
    argv[index] = ':'.join([str(app / 'BOOT-INF/classes')]
                            + [str(app / n) for n in libraries] + [str(CLOCK)])
    if [s for s in updated if s['service'] != 'academic'] != [s for s in specs if s['service'] != 'academic']:
        raise RuntimeError('Other six host specs changed')
    if {k: v for k, v in target.items() if k not in ['cwd', 'argv']} != {k: v for k, v in original.items() if k not in ['cwd', 'argv']}:
        raise RuntimeError('Academic ENV/ports/resource/security settings changed')
    if [a for i, a in enumerate(argv) if i != index] != [a for i, a in enumerate(original['argv']) if i != index]:
        raise RuntimeError('Only Academic classpath/cwd may change')
    return updated


def canonical_libraries(lines, entries):
    libraries = []
    for line in lines:
        match = re.fullmatch(r'- "(BOOT-INF/lib/[A-Za-z0-9_.+-]+\.jar)"', line)
        if match is None:
            raise RuntimeError('Canonical standard classpath line required')
        libraries.append(match.group(1))
    all_libraries = {row['path'] for row in entries
                     if row['path'].startswith('BOOT-INF/lib/') and row['path'].endswith('.jar')}
    tools = 'BOOT-INF/lib/spring-boot-jarmode-tools-3.5.16.jar'
    if (len(libraries) != 182 or len(set(libraries)) != 182 or len(all_libraries) != 183
            or set(libraries) - all_libraries or all_libraries - set(libraries) != {tools}
            or tools in libraries):
        raise RuntimeError('Exact canonical182 application libs plus sole bundled183rd tools required')
    return libraries


def complete_existing(g, built, ns, app, monitor, resume):
    base = OWN.parent / 'current_academic_stage_resume_guard_correction/actual-stage-resume02'
    manifest = base / 'manifest.json'
    receipt = base / 'full-payload-readonly.safe.json'
    manifest_sha = '15963c993dca297d0d8ffc6e5bda25dc2c478b20821cef6ec6dfa627c27982d6'
    receipt_sha = '01a6f122bff2a4f20c2f6ef1a7e836ffcd46953e409c277e2418ae573e8223cd'
    wanted = {'stage_completion_authorized': True, 'stage_resume_authorized': False,
              'stage_full_payload_manifest': str(manifest), 'stage_full_payload_manifest_sha256': manifest_sha,
              'stage_full_payload_receipt': str(receipt), 'stage_full_payload_receipt_sha256': receipt_sha}
    if any(type(g.get(k)) is not type(v) or g[k] != v for k, v in wanted.items()):
        raise RuntimeError('Exact1797 completion-only grant binding required')
    if boundary.sha(manifest) != manifest_sha or boundary.sha(receipt) != receipt_sha:
        raise RuntimeError('Exact actual1797 proof seal required')
    sealed = json.loads(manifest.read_text())
    if not any(r['path'] == str(receipt) and r['sha256'] == receipt_sha for r in sealed['files']):
        raise RuntimeError('Full1797 receipt must belong to actual1596 seal')
    doc = json.loads(receipt.read_text())
    expected = {r['path']: r for r in built['regular_entries']}
    files = {r['path']: r for r in doc['files']}
    if (doc['scope'] != 'READONLY_AFTER_RESUME02_TERMINAL_FULL_MEMBER_PROOF'
            or doc['source_producer'] != built['producer_revision']
            or doc['current_JAR_sha256'] != built['jar_sha256'] or len(expected) != 1797
            or len(doc['files']) != 1797 or set(files) != set(expected)
            or doc['all1797_member_SHA_equal'] is not True
            or doc['all1797_644_UIDGID1000_nlink1_size_equal'] is not True
            or doc['all894_original_device_inode_equal'] is not True):
        raise RuntimeError('Exact complete stopped1797 currentJAR authority required')
    for name, row in files.items():
        resume.path_name(name)
        if (row['sha256'] != expected[name]['sha256'] or row['bytes'] != expected[name]['bytes']
                or row['mode'] != 0o644 or row['uid'] != 1000 or row['gid'] != 1000
                or row['nlink'] != 1 or row['matches_current_JAR_member'] is not True):
            raise RuntimeError('Complete1797 proof member identity required')
    wanted_dirs = {'.'}
    wanted_dirs.update(str(parent) for name in expected for parent in Path(name).parents if str(parent) != '.')
    directories = {}
    for name, row in monitor.base_records.items():
        if row['kind'] == 'directory' and (name == 'app' or name.startswith('app/')):
            rel = '.' if name == 'app' else name[4:]
            if row['uid'] != 1000 or row['gid'] != 1000 or row['mode'] != 0o755:
                raise RuntimeError('Exact current public755 directories required')
            directories[rel] = dict(row, path=rel)
    if set(directories) != wanted_dirs:
        raise RuntimeError('Full current app directory composition missing/extra denied')
    resume.verify_tree(ns, app, files, directories)  # Existing1797 FD hashes; no writer/copy invocation.
    return files, wanted_dirs


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument('--grant', required=True)
    parser.add_argument('--complete-only', action='store_true', required=True)
    args = parser.parse_args()
    g, stop = boundary.verify_grant(args.grant, 'STAGE', __file__)
    started_monotonic = time.monotonic()
    started_utc = datetime.datetime.now(datetime.timezone.utc).isoformat()
    baseline = boundary.cheap_resources()['workspace_free']
    global monitor_module
    monitor_module = boundary.load(OWN / 'stage-monitor.py', 'stage_monitor')
    resume = boundary.load(OWN / 'partial-resume.py', 'partial_resume')
    monitor = monitor_module.Monitor(boundary, g, stop, baseline, inventory_reader=monitor_module.full_inventory)
    use = lambda effect_bytes=monitor_module.METADATA_ALLOWANCE: monitor.gate(effect_bytes=effect_bytes)
    ns = boundary.Namespace(use)
    monitor.gate(force=True, entry=True)
    fields = Path('/proc/self/stat').read_text().rsplit(')', 1)[1].split()
    supervisor = {'pid': os.getpid(), 'start_ticks': int(fields[19]), 'ppid': int(fields[1])}
    print(json.dumps({'phase': 'STAGE_COMPLETION', 'state': 'VALIDATING_ALL1797', 'supervisor': supervisor}), flush=True)
    writer = None
    error_stage = 'VERIFY_BUILD_RECEIPT'
    previous_umask = os.umask(0o022)  # NEW public copies only; existing files never chmod.
    try:
        receipt = boundary.PREFIX / 'build-result.safe.json'
        if boundary.sha(receipt) != g['build_result_sha256']:
            raise RuntimeError('Exact actual fresh build receipt required')
        built = json.loads(receipt.read_text())
        if built['producer_revision'] != g['frozen_source_revision'] or built['status'] != 'CURRENT_ACADEMIC_PRODUCTION_JAR_ONLY':
            raise RuntimeError('Fresh exact source-bound build required')
        jar = boundary.PREFIX / 'checkout/services/academic-service/academic-app/build/libs/academic-app-0.1.0.jar'
        if built['jar'] != str(jar) or boundary.sha(jar) != built['jar_sha256']:
            raise RuntimeError('Exact new own production JAR identity required')
        app = boundary.PREFIX / 'app'
        error_stage = 'VERIFY_EXISTING1797'
        verified_existing, complete_dirs = complete_existing(g, built, ns, app, monitor, resume)
        inspector = boundary.load(OWN / 'inspect-academic.py', 'inspector')
        proof = inspector.inspect(boundary.PREFIX / 'checkout', g['frozen_source_revision'])
        if proof['regular_entries'] != built['regular_entries'] or proof['jar_sha256'] != built['jar_sha256']:
            raise RuntimeError('Production JAR changed since build')
        monitor.configure([], complete_dirs)
        monitor.gate(force=True, entry=True)
        with zipfile.ZipFile(jar) as archive:
            lines = archive.read('BOOT-INF/classpath.idx').decode('utf8').splitlines()
        error_stage = 'VERIFY_STANDARD_INDEX'
        libraries = canonical_libraries(lines, proof['regular_entries'])
        print(json.dumps({'phase': 'STAGE_COMPLETION', 'state': 'FINALIZE_EXISTING_ONLY',
                          'verified_existing': len(verified_existing), 'copied': 0}), flush=True)
        error_stage = 'VERIFY_FINAL_MEMBERS'
        metadata = []
        for row in proof['regular_entries']:
            path = boundary.regular(app / row['path'])
            s = path.lstat()
            if boundary.sha(path) != row['sha256'] or s.st_uid != 1000 or s.st_gid != 1000 or s.st_nlink != 1 or stat.S_IMODE(s.st_mode) != 0o644:
                raise RuntimeError('Full current copied bytes/readability required')
            metadata.append(dict(row, allocated=s.st_blocks * 512, device=s.st_dev, inode=s.st_ino, mode=0o644))
        actual_names = set()
        for parent, dirs, files in os.walk(app, followlinks=False):
            for name in dirs:
                directory = Path(parent) / name
                sd = directory.lstat()
                if not stat.S_ISDIR(sd.st_mode) or sd.st_uid != 1000 or sd.st_gid != 1000 or stat.S_IMODE(sd.st_mode) != 0o755:
                    raise RuntimeError('Final public directory composition denied')
            actual_names.update(str((Path(parent) / n).relative_to(app)) for n in files)
        if actual_names != {r['path'] for r in proof['regular_entries']}:
            raise RuntimeError('Final full app composition missing/extra denied')
        if boundary.sha(jar) != built['jar_sha256']:
            raise RuntimeError('Current production JAR changed during copy')
        error_stage = 'VERIFY_ACADEMIC_SPEC'
        specs = rebind_specs(json.loads(BASE_SPECS.read_text()), app, libraries)
        ns.check()
        encoded_spec = json.dumps(specs, indent=2).encode()
        use(effect_bytes=monitor_module.write_upper(len(encoded_spec) + 1))
        error_stage = 'PUBLISH_SPEC'
        ns.save('host-service-specs.safe.json', specs, new=True, before_effect=use)
        old = json.loads(BASE_ARTIFACTS.read_text())
        mappings = []
        for row in old['jars']:
            if row['service'] == 'academic':
                mappings.append({'service': 'academic', 'producer': g['frozen_source_revision'], 'jar': str(jar), 'sha256': built['jar_sha256'], 'app': str(app)})
            else:
                mappings.append({'service': row['service'], 'producer': old['revision'], 'jar': row['jar'], 'sha256': row['sha256'], 'unchanged': True})
        result = {'status': 'FULL_CURRENT_ACADEMIC_APP_STAGED_ONLY', 'producer': g['frozen_source_revision'],
                  'academic_refresh_manifest_sha256': g['academic_refresh_manifest_sha256'],
                  'build_result_sha256': g['build_result_sha256'], 'app': str(app),
                  'jar': str(jar), 'jar_sha256': built['jar_sha256'], 'files': metadata,
                  'host_service_specs': str(boundary.PREFIX / 'host-service-specs.safe.json'),
                  'host_service_specs_sha256': boundary.sha(boundary.PREFIX / 'host-service-specs.safe.json'),
                  'library_order': libraries, 'effective8_artifacts': mappings,
                  'completion_original_full_receipt_sha256': g['stage_full_payload_receipt_sha256'],
                  'all1797_files_never_modified': True, 'copied_this_operation': 0,
                  'nested_library_count': 183, 'application_index_library_count': 182,
                  'unindexed_tools': 'BOOT-INF/lib/spring-boot-jarmode-tools-3.5.16.jar',
                  'started_utc': started_utc,
                  'elapsed_before_final_inventory_seconds': time.monotonic() - started_monotonic,
                  'runtime': 'NOT_RUN'}
        # Final complete copy/hash/spec proof is prepared BEFORE forced allocation inventory and clock.
        monitor.gate(force=True)
        result['resources'] = dict(boundary.cheap_resources(), new_tmp_allocated=monitor.actual)
        result['allocation_monitor'] = monitor.facts()
        encoded_result = json.dumps(result, indent=2).encode()
        ns.check()
        use(effect_bytes=monitor_module.write_upper(len(encoded_result) + 1))
        error_stage = 'PUBLISH_RESULT'
        ns.save('stage-result.safe.json', result, new=True, before_effect=use)
        # Reconcile actual whole namespace AFTER proof publication, then publish a small commit marker.
        final_snapshot = monitor.gate(force=True)
        commit = {'status': 'COMMITTED_FULL_CURRENT_ACADEMIC_STAGE',
                  'stage_result_sha256': boundary.sha(boundary.PREFIX / 'stage-result.safe.json'),
                  'producer': g['frozen_source_revision'], 'final_actual_inventory': final_snapshot,
                  'allocation_monitor': monitor.facts(),
                  'started_utc': started_utc,
                  'elapsed_before_commit_seconds': time.monotonic() - started_monotonic}
        encoded_commit = json.dumps(commit, indent=2).encode()
        ns.check()
        use(effect_bytes=monitor_module.write_upper(len(encoded_commit) + 1))
        error_stage = 'PUBLISH_COMMIT'
        ns.save('stage-committed.safe.json', commit, new=True, before_effect=use)
        print(json.dumps({'phase': 'STAGE', 'full_app': True, 'original7_artifacts_unchanged': True, 'runtime': False}))
        return 0
    except BaseException as e:
        # Never mutate app/namespace after expiry just to publish an error. Outer tool/owner persists stdout.
        code = e.code if isinstance(e, monitor_module.StageStop) else 'COARSE_ERROR_ONLY'
        print(json.dumps({'phase': 'STAGE_COMPLETION', 'status': 'PARTIAL_STOPPED', 'error_type': type(e).__name__, 'error_code': code, 'error_stage': error_stage, 'runtime': False}))
        return 1
    finally:
        if writer is not None:
            writer.close()
        os.umask(previous_umask)
        ns.close()


if __name__ == '__main__':
    raise SystemExit(main())
