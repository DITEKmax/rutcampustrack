"""Future exact Academic bootJar only; actual work requires fresh Root BUILD grant."""
import argparse
import datetime
import hashlib
import json
import os
from pathlib import Path
import subprocess
import types

OWN = Path(__file__).parent
boundary = types.ModuleType('refresh_boundary')
boundary.__file__ = str(OWN / 'refresh-boundary.py')
if hashlib.sha256((OWN / 'refresh-boundary.py').read_bytes()).hexdigest() != 'a6c56c607d4797e94de0c8e6b5b61b10e255c7308d904940ae1b242695f78272':
    raise RuntimeError('Exact reviewed refresh boundary bytes required')
exec(compile((OWN / 'refresh-boundary.py').read_bytes(), boundary.__file__, 'exec'), boundary.__dict__)


def git_read(*args):
    # Public immutable Git objects only; synchronously waited, fast births not claimed captured.
    p = subprocess.run(['/usr/bin/git', '-C', str(boundary.ROOT), *args],
                       env={'PATH': '/usr/bin:/bin', 'LANG': 'C.UTF-8'},
                       stdin=subprocess.DEVNULL, stdout=subprocess.PIPE,
                       stderr=subprocess.DEVNULL, timeout=30, check=True)
    if len(p.stdout) > 4194304:
        raise RuntimeError('Bounded public Git metadata required')
    return p.stdout


def tracked_inputs(revision):
    rows = []
    for entry in git_read('ls-tree', '-rlz', revision).split(b'\0'):
        if not entry:
            continue
        meta, raw = entry.split(b'\t', 1)
        path = raw.decode('utf8')
        if not boundary.selected(path):
            continue
        mode, kind, oid, size = meta.decode().split()
        if kind != 'blob' or mode not in ['100644', '100755']:
            raise RuntimeError('Tracked source symlink/gitlink denied')
        rows.append({'path': path, 'git_blob': oid, 'git_mode': mode, 'bytes': int(size)})
    if not any(r['path'] == boundary.FIX_PATH for r in rows):
        raise RuntimeError('Exact Academic fixed source required')
    if hashlib.sha256(git_read('show', revision + ':' + boundary.FIX_PATH)).hexdigest() != boundary.FIX_SHA:
        raise RuntimeError('Frozen Git tree must contain reviewed fixed source')
    return sorted(rows, key=lambda r: r['path'])


def verify_checkout(checkout, rows):
    result = []
    for row in rows:
        p = boundary.regular(checkout / row['path'])
        s = p.lstat()
        if s.st_uid != 1000 or s.st_gid != 1000 or s.st_nlink != 1 or s.st_size != row['bytes']:
            raise RuntimeError('Exact own unaliased frozen source required')
        content = p.read_bytes()
        oid = hashlib.sha1(('blob ' + str(len(content)) + '\0').encode() + content).hexdigest()
        if oid != row['git_blob']:
            raise RuntimeError('Frozen source Git object mismatch')
        result.append(dict(row, sha256=hashlib.sha256(content).hexdigest()))
    return result


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument('--grant', required=True)
    args = parser.parse_args()
    g, stop = boundary.verify_grant(args.grant, 'BUILD', __file__)
    initial = boundary.resource_snapshot()
    baseline = initial['workspace_free']
    rows = tracked_inputs(g['frozen_source_revision'])
    pinned = json.loads((OWN / 'tracked-inputs96.safe.json').read_text())
    if pinned['revision'] != g['frozen_source_revision'] or rows != pinned['tracked']:
        raise RuntimeError('Exact immutable96 sparse production input ledger required')
    if sum(r['bytes'] for r in rows) > 134217728:
        raise RuntimeError('Selected sparse source logical cap128MiB')
    guard = boundary.load(boundary.GUARD, 'guard')
    # No parent Java/Spring/credential overrides, no proxy required in offline build.
    env = {'PATH': '/workspace/rct-cloud-setup/jdk21/bin:/usr/bin:/bin',
           'LANG': 'C.UTF-8', 'LC_ALL': 'C.UTF-8', 'CI': 'true',
           'JAVA_HOME': '/workspace/rct-cloud-setup/jdk21',
           'JAVA_OPTS': '', 'GRADLE_OPTS': '',
           'HOME': str(boundary.PREFIX / 'home'), 'TMPDIR': str(boundary.PREFIX / 'tmp'),
           'GRADLE_USER_HOME': '/workspace/.gradle'}
    use = lambda: boundary.gate(g, stop, baseline)
    ns = boundary.Namespace(use, create=True)
    supervisor = guard.process_table()[os.getpid()]
    ns.save('build-supervisor.safe.json', supervisor, new=True)
    print(json.dumps({'phase': 'BUILD', 'supervisor': supervisor, 'namespace': str(boundary.PREFIX)}), flush=True)
    latest = {}
    phases = []

    def observe():
        try:
            ns.check()
            use()
            table = guard.process_table()
            rss = 0
            for row in latest.get('members_including_Z', []):
                if table.get(row['pid'], {}).get('start_ticks') != row['start_ticks']:
                    continue
                try:
                    lines = Path('/proc/' + str(row['pid']) + '/status').read_text().splitlines()
                except OSError as e:
                    if e.errno in (2, 3):
                        continue
                    raise
                rss += next((int(line.split()[1]) * 1024 for line in lines if line.startswith('VmRSS:')), 0)
            if rss > 2147483648:
                return 'OWN_COMBINED_RSS_2GiB'
        except RuntimeError:
            return 'REFRESH_RESOURCE_OR_DEADLINE_STOP'
        return None

    def run(phase, argv, cwd):
        latest.clear()
        def status(value):
            latest.clear()
            latest.update(value)
            ns.save(phase + '-process.safe.json', value)
        ns.check()
        use()
        remaining = (stop - datetime.datetime.now(datetime.timezone.utc)).total_seconds() - 60
        result = guard.run_owned(argv, str(cwd), env, deadline_seconds=min(900, remaining),
                                 observe=observe, status=status)
        ns.save(phase + '-terminal.safe.json', result, new=True)
        phases.append({'phase': phase, 'native_exit': result['native_exit'],
                       'settled': result['settled'], 'phase_success': result['phase_success']})
        if not result['phase_success'] or not result['settled']:
            raise RuntimeError('Bounded own native phase failed')

    try:
        for name in ['home', 'tmp']:
            ns.check()
            use()
            os.mkdir(name, 0o700, dir_fd=ns.fds[-1])
        checkout = boundary.PREFIX / 'checkout'
        run('clone', ['/usr/bin/git', 'clone', '--shared', '--no-checkout',
                      str(boundary.ROOT), str(checkout)], boundary.PREFIX)
        # Only Git immutable object sharing; no hardlinked working source files.
        run('sparse-config', ['/usr/bin/git', '-C', str(checkout), 'config',
                              'core.sparseCheckout', 'true'], checkout)
        use()
        with (checkout / '.git/info/sparse-checkout').open('x') as stream:
            stream.write(''.join('/' + r['path'] + '\n' for r in rows))
        run('checkout', ['/usr/bin/git', '-C', str(checkout), 'checkout', '--detach',
                         g['frozen_source_revision']], checkout)
        inputs = verify_checkout(checkout, rows)
        ns.save('source-before.safe.json', {'revision': g['frozen_source_revision'], 'tracked': inputs}, new=True)
        run('academic-bootjar', boundary.BUILD_ARGV, checkout)
        if verify_checkout(checkout, rows) != inputs:
            raise RuntimeError('Tracked build source changed')
        inspector = boundary.load(OWN / 'inspect-academic.py', 'inspector')
        proof = inspector.inspect(checkout, g['frozen_source_revision'])
        proof.update({'status': 'CURRENT_ACADEMIC_PRODUCTION_JAR_ONLY', 'phases': phases,
                      'resource_after': boundary.require_resources(g, baseline),
                      'fixture_inspector_hash': boundary.sha(checkout / 'tests/e2e/backend-clock/verify-jar-isolation.py'),
                      'tracked_source_after': 'EXACT', 'stage_app_browser': 'NOT_RUN',
                      'fast_public_Git_metadata_children': 'SYNCHRONOUS_WAIT; birth ticks not captured',
                      'shared_git_objects': 'Readonly alternate to Root; all working inputs nlink1; source objects must stay retained',
                      'global_gradle_cache': 'Existing pinned cache reused; net workspace growth stop8MiB is measured, not exact cache-isolation proof'})
        ns.check()
        use()
        ns.save('build-result.safe.json', proof, new=True)
        print(json.dumps({'phase': 'BUILD', 'native': 0, 'artifact': 'CURRENT_ACADEMIC_JAR_ONLY', 'runtime': False}))
        return 0
    except BaseException as e:
        ns.save('build-error.safe.json', {'status': 'PARTIAL_STOPPED', 'error_type': type(e).__name__,
                                         'phases': phases, 'runtime': False})
        print(json.dumps({'phase': 'BUILD', 'status': 'PARTIAL_STOPPED', 'error_type': type(e).__name__}))
        return 1
    finally:
        ns.close()


if __name__ == '__main__':
    raise SystemExit(main())
