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


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument('--grant', required=True)
    args = parser.parse_args()
    g, stop = boundary.verify_grant(args.grant, 'STAGE', __file__)
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
    print(json.dumps({'phase': 'STAGE_RESUME', 'state': 'VALIDATING_PARTIAL', 'supervisor': supervisor}), flush=True)
    writer = None
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
        partial = resume.validate_binding(g, boundary)
        partial_files, partial_dirs = resume.validate_document(partial, built, app)
        verified_existing = resume.verify_tree(ns, app, partial_files, partial_dirs)
        inspector = boundary.load(OWN / 'inspect-academic.py', 'inspector')
        proof = inspector.inspect(boundary.PREFIX / 'checkout', g['frozen_source_revision'])
        if proof['regular_entries'] != built['regular_entries'] or proof['jar_sha256'] != built['jar_sha256']:
            raise RuntimeError('Production JAR changed since build')
        missing = [row for row in proof['regular_entries'] if row['path'] not in verified_existing]
        if len(missing) != 903 or len(verified_existing) != 894:
            raise RuntimeError('Exact903 remaining/894 immutable members required')
        forecast = sum(((r['bytes'] + 4095) // 4096) * 4096 for r in missing) + 8388608
        complete_dirs = {str(p) for row in proof['regular_entries'] for p in Path(row['path']).parents if str(p) != '.'}
        monitor.configure(missing, complete_dirs)
        entry_snapshot = monitor.gate(force=True, entry=True)
        if entry_snapshot['new_tmp_allocated'] + forecast + monitor_module.HEADROOM > g['all_new_tmp_cap']:
            raise RuntimeError('Conservative remaining fullpayload/sample headroom unavailable')
        print(json.dumps({'phase': 'STAGE_RESUME', 'state': 'COPY_MISSING_ONLY', 'verified_existing': len(verified_existing), 'missing': len(missing)}), flush=True)
        writer_module = boundary.load(OWN / 'stage-app-writer.py', 'stage_app_writer')
        writer = writer_module.AppWriter(ns, use, boundary, partial_dirs, monitor_module.METADATA_ALLOWANCE, monitor_module.write_upper)
        with zipfile.ZipFile(jar) as archive:
            for row in missing:
                with archive.open(row['path']) as stream:
                    writer.copy(row, stream)
            lines = archive.read('BOOT-INF/classpath.idx').decode('utf8').splitlines()
        libraries = []
        for line in lines:
            if not line.startswith('- "BOOT-INF/lib/') or not line.endswith('.jar"'):
                raise RuntimeError('Canonical standard classpath index required')
            libraries.append(line[3:-1])
        if set(libraries) != {r['path'] for r in proof['regular_entries'] if r['path'].startswith('BOOT-INF/lib/') and r['path'].endswith('.jar')}:
            raise RuntimeError('Exact complete classpath index required')
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
        specs = rebind_specs(json.loads(BASE_SPECS.read_text()), app, libraries)
        ns.check()
        encoded_spec = json.dumps(specs, indent=2).encode()
        use(effect_bytes=monitor_module.write_upper(len(encoded_spec) + 1))
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
                  'resume_original_partial_sha256': resume.PARTIAL_SHA,
                  'existing894_files_never_modified': True, 'copied_missing903_only': True,
                  'missing_copy_forecast_bytes': forecast,
                  'new_directories_created_once': writer.created_directories,
                  'directory_FDs_opened_once': len(writer.cache), 'runtime': 'NOT_RUN'}
        # Final complete copy/hash/spec proof is prepared BEFORE forced allocation inventory and clock.
        monitor.gate(force=True)
        result['resources'] = dict(boundary.cheap_resources(), new_tmp_allocated=monitor.actual)
        result['allocation_monitor'] = monitor.facts()
        encoded_result = json.dumps(result, indent=2).encode()
        ns.check()
        use(effect_bytes=monitor_module.write_upper(len(encoded_result) + 1))
        ns.save('stage-result.safe.json', result, new=True, before_effect=use)
        # Reconcile actual whole namespace AFTER proof publication, then publish a small commit marker.
        final_snapshot = monitor.gate(force=True)
        commit = {'status': 'COMMITTED_FULL_CURRENT_ACADEMIC_STAGE',
                  'stage_result_sha256': boundary.sha(boundary.PREFIX / 'stage-result.safe.json'),
                  'producer': g['frozen_source_revision'], 'final_actual_inventory': final_snapshot,
                  'allocation_monitor': monitor.facts()}
        encoded_commit = json.dumps(commit, indent=2).encode()
        ns.check()
        use(effect_bytes=monitor_module.write_upper(len(encoded_commit) + 1))
        ns.save('stage-committed.safe.json', commit, new=True, before_effect=use)
        print(json.dumps({'phase': 'STAGE', 'full_app': True, 'original7_artifacts_unchanged': True, 'runtime': False}))
        return 0
    except BaseException as e:
        # Never mutate app/namespace after expiry just to publish an error. Outer tool/owner persists stdout.
        code = e.code if isinstance(e, monitor_module.StageStop) else 'COARSE_ERROR_ONLY'
        print(json.dumps({'phase': 'STAGE_RESUME', 'status': 'PARTIAL_STOPPED', 'error_type': type(e).__name__, 'error_code': code, 'runtime': False}))
        return 1
    finally:
        if writer is not None:
            writer.close()
        os.umask(previous_umask)
        ns.close()


if __name__ == '__main__':
    raise SystemExit(main())
