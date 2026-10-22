"""Academic-only current payload boundary; no service or private ENV on import."""
import datetime
import hashlib
import json
import os
from pathlib import Path
import stat
import types

OWN = Path(__file__).parent
boundary = types.ModuleType('refresh_boundary')
boundary.__file__ = str(OWN / 'refresh-boundary.py')
if hashlib.sha256((OWN / 'refresh-boundary.py').read_bytes()).hexdigest() != 'a6c56c607d4797e94de0c8e6b5b61b10e255c7308d904940ae1b242695f78272':
    raise RuntimeError('Exact reviewed refresh boundary bytes required')
exec(compile((OWN / 'refresh-boundary.py').read_bytes(), boundary.__file__, 'exec'), boundary.__dict__)


def verify(grant, source):
    if (grant.get('academic_refresh_runtime_authorized') is not True
            or grant.get('fixed_UserSpecifications_sha256') != boundary.FIX_SHA
            or grant.get('frozen_source_revision') != boundary.FIX_REVISION
            or grant.get('academic_full_app_path') != str(boundary.PREFIX / 'app')
            or grant.get('academic_stage_receipt_path') != str(boundary.PREFIX / 'stage-result.safe.json')):
        raise RuntimeError('NEW exact current Academic runtime binding required')
    boundary.verify_package(grant, source)
    ns = boundary.Namespace(lambda: None)
    try:
        receipt = boundary.PREFIX / 'stage-result.safe.json'
        if boundary.sha(receipt) != grant.get('academic_stage_receipt_sha256'):
            raise RuntimeError('Exact actual complete Academic stage receipt required')
        committed = boundary.PREFIX / 'stage-committed.safe.json'
        if (grant.get('academic_stage_commit_receipt_path') != str(committed)
                or boundary.sha(committed) != grant.get('academic_stage_commit_receipt_sha256')):
            raise RuntimeError('Exact final reconciled stage commit required')
        commit = json.loads(committed.read_text())
        if (commit['status'] != 'COMMITTED_FULL_CURRENT_ACADEMIC_STAGE'
                or commit['stage_result_sha256'] != grant['academic_stage_receipt_sha256']
                or commit['producer'] != grant['frozen_source_revision']):
            raise RuntimeError('Final full inventory/commit lineage required')
        proof = json.loads(receipt.read_text())
        if (proof['status'] != 'FULL_CURRENT_ACADEMIC_APP_STAGED_ONLY'
                or proof['producer'] != grant['frozen_source_revision']
                or proof['academic_refresh_manifest_sha256'] != grant['academic_refresh_manifest_sha256']
                or proof['app'] != str(boundary.PREFIX / 'app')
                or proof['jar'] != str(boundary.PREFIX / 'checkout/services/academic-service/academic-app/build/libs/academic-app-0.1.0.jar')):
            raise RuntimeError('Exact source/JAR/app lineage required')
        if boundary.sha(proof['jar']) != proof['jar_sha256']:
            raise RuntimeError('New Academic JAR changed')
        app = boundary.PREFIX / 'app'
        actual_paths = set()
        for parent, dirs, files in os.walk(app, followlinks=False):
            for name in dirs:
                s = (Path(parent) / name).lstat()
                if not stat.S_ISDIR(s.st_mode) or s.st_uid != 1000 or s.st_gid != 1000 or stat.S_IMODE(s.st_mode) != 0o755:
                    raise RuntimeError('Frozen public755 app directories required')
            actual_paths.update(str((Path(parent) / n).relative_to(app)) for n in files)
        if actual_paths != {row['path'] for row in proof['files']}:
            raise RuntimeError('Exact full app regular file inventory required')
        for row in proof['files']:
            path = boundary.regular(app / row['path'])
            if not path.is_relative_to(app):
                raise RuntimeError('Contained full Academic payload required')
            s = path.lstat()
            if (s.st_dev != row['device'] or s.st_ino != row['inode'] or s.st_size != row['bytes']
                    or s.st_uid != 1000 or s.st_gid != 1000 or s.st_nlink != 1
                    or stat.S_IMODE(s.st_mode) != 0o644 or boundary.sha(path) != row['sha256']):
                raise RuntimeError('Frozen new Academic payload changed')
        specs_path = boundary.PREFIX / 'host-service-specs.safe.json'
        if (proof['host_service_specs'] != str(specs_path)
                or boundary.sha(specs_path) != proof['host_service_specs_sha256']):
            raise RuntimeError('Frozen effective Academic host specs changed')
        specs = json.loads(specs_path.read_text())
        staging = boundary.load(OWN / 'stage-academic.py', 'staging')
        if specs != staging.rebind_specs(json.loads(staging.BASE_SPECS.read_text()), app, proof['library_order']):
            raise RuntimeError('Only current Academic cwd/classpath delta permitted')
        ns.check()
        # All hashing/spec collection precedes this fresh runtime use-time clock.
        stop = datetime.datetime.fromisoformat(grant['stop_utc'])
        boundary.fresh(stop)
        return specs
    finally:
        ns.close()


def require_runtime_resources():
    snapshot = boundary.resource_snapshot()
    if (snapshot['workspace_free'] < 4294967296 or snapshot['tmp_free'] < 5368709120
            or snapshot['shared'] >= 16106127360 or snapshot['maximum'] - snapshot['shared'] < 1073741824):
        raise RuntimeError('Academic refreshed stand resource stop')
    return snapshot
