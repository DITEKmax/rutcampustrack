#!/usr/bin/env python3
"""Visible serial full local CI replay, with fresh source and per-gate evidence."""
import argparse
import datetime
import fcntl
import importlib.util
import json
import os
from pathlib import Path
import re
import selectors
import secrets
import shutil
import signal
import subprocess
import sys
import time

HERE=Path(__file__).resolve().parent
ROOT=HERE.parent
sys.path.insert(0,str(HERE/'local-release'))
from catalog import catalog, NOT_APPLICABLE
from diagnostics import project, admit_gate

MAX_LOG=512*1024*1024

def write(path,value):
    raw=(json.dumps(value,sort_keys=True,indent=2,allow_nan=False)+'\n').encode()
    if len(raw)>8*1024*1024:raise ValueError('Public outcome bound')
    fd=os.open(path,os.O_WRONLY|os.O_CREAT|os.O_EXCL|os.O_NOFOLLOW,0o600)
    with os.fdopen(fd,'wb') as out:out.write(raw)

def git(*args):
    return subprocess.check_output(['git',*args],cwd=ROOT,stderr=subprocess.DEVNULL,text=True,timeout=15).strip()

def foreground(fd,pgid):
    previous=signal.pthread_sigmask(signal.SIG_BLOCK,{signal.SIGTTOU})
    try:os.tcsetpgrp(fd,pgid)
    finally:signal.pthread_sigmask(signal.SIG_SETMASK,previous)

def group_absent(pgid):
    try:os.killpg(pgid,0)
    except ProcessLookupError:return True
    return False


def command(gate,env,audit):
    """Own the entire foreground process group; no test output reaches the public console."""
    log=audit/(gate.name+'.private.log');start=time.monotonic();next_heartbeat=start+30;total=0;cause=None;child=None;closed=False;terminal=None;parent_group=os.getpgrp()
    with log.open('xb') as out:
        transition=False;deferred=False
        def cancel(*_):
            nonlocal deferred
            if transition:deferred=True
            else:raise KeyboardInterrupt()
        previous={kind:signal.signal(kind,cancel) for kind in (signal.SIGINT,signal.SIGTERM)}
        try:
            interactive=gate.name in {'native-group-and-nginx','observability-apps'} or gate.name.startswith('browser-')
            if interactive:
                terminal=open('/dev/tty','r+')
                if os.tcgetpgrp(terminal.fileno())!=parent_group:raise ValueError('Foreign foreground terminal owner')
            transition=True
            try:child=subprocess.Popen(gate.command,cwd=ROOT,env=env,stdin=terminal if terminal is not None else subprocess.DEVNULL,stdout=subprocess.PIPE,stderr=subprocess.STDOUT,process_group=0)
            finally:transition=False
            if deferred:raise KeyboardInterrupt()
            if terminal is not None:
                foreground(terminal.fileno(),child.pid)
                # A new process group can reach its first TTY read before foreground transfer.
                try:os.killpg(child.pid,signal.SIGCONT)
                except ProcessLookupError:pass

            with selectors.DefaultSelector() as selector:
                selector.register(child.stdout,selectors.EVENT_READ)
                while selector.get_map():
                    if time.monotonic()>=next_heartbeat:
                        print('RUN '+gate.name+' '+str(int(time.monotonic()-start))+'s',flush=True);next_heartbeat+=30
                    if time.monotonic()-start>gate.seconds:cause='TIME_LIMIT';raise TimeoutError()
                    for key,_ in selector.select(.25):
                        chunk=os.read(key.fd,65536)
                        if not chunk:selector.unregister(key.fileobj);continue
                        total+=len(chunk)
                        if total>MAX_LOG:cause='BYTE_LIMIT';raise ValueError('Private log bound')
                        out.write(chunk);out.flush()
                rc=child.wait(timeout=5)
                if rc in (-signal.SIGINT,130):cause="CANCELLED"
        except BaseException as error:
            cause=cause or ('CANCELLED' if isinstance(error,KeyboardInterrupt) else 'COMMAND_ERROR')
            if child is not None:
                # INT reaches Python finally blocks AND nested Gradle/native children in this session.
                # TERM/KILL are last resorts; their result is HOLD, never safe continuation.
                for sig,seconds in ((signal.SIGINT,30),(signal.SIGTERM,5),(signal.SIGKILL,5)):
                    try:os.killpg(child.pid,sig)
                    except ProcessLookupError:pass
                    try:child.wait(timeout=seconds);closed=sig==signal.SIGINT;break
                    except subprocess.TimeoutExpired:continue
                rc=child.returncode
            else:rc=None
        else:closed=True
        finally:
            # Cleanup is finite and cannot be interrupted between own-group signals and TTY restore.
            transition=True
            if child is not None:
                if not group_absent(child.pid):
                    # A successful leader does not qualify surviving descendants as graceful closure.
                    cause=cause or 'OWNED_GROUP_ESCALATION';closed=False
                    try:
                        try:os.killpg(child.pid,signal.SIGTERM)
                        except ProcessLookupError:pass
                        time.sleep(.25)
                    finally:
                        try:os.killpg(child.pid,signal.SIGKILL)
                        except ProcessLookupError:pass
                if child.stdout is not None:child.stdout.close()
                deadline=time.monotonic()+1
                while not group_absent(child.pid) and time.monotonic()<deadline:time.sleep(.05)
                closed=closed and group_absent(child.pid)
            if terminal is not None:
                try:
                    current=os.tcgetpgrp(terminal.fileno())
                    if child is not None and current==child.pid:foreground(terminal.fileno(),parent_group)
                    elif current!=parent_group:cause='TTY_OWNERSHIP_CHANGED';closed=False
                except OSError:cause=cause or 'TTY_RESTORE_UNCERTAIN';closed=False
                finally:terminal.close()
            for kind,handler in previous.items():signal.signal(kind,handler)
            if deferred:cause=cause or 'CANCELLED'
    return {'nativeExit':rc,'cause':cause,'childrenReaped':closed,'elapsedMs':round((time.monotonic()-start)*1000),'privateLogBytes':min(total,MAX_LOG),'privateLog':log.name}

def status(gate,results,hold):
    if hold:return 'HOLD'
    if any(results.get(n,{}).get('status')!='PASS' for n in gate.requires):return 'BLOCKED'
    return 'RUN'

def archive_old(gate,audit):
    for i,name in enumerate(gate.reports):
        path=ROOT/name
        if not path.exists():continue
        if path.is_symlink() or not path.resolve().is_relative_to(ROOT):raise ValueError('Report path boundary')
        target=audit/'previous'/gate.name/str(i);target.parent.mkdir(mode=0o700,parents=True,exist_ok=True)
        path.rename(target)

def preserve_reports(gate,audit):
    """Freeze native reports before another gate can overwrite shared build outputs."""
    destination=audit/'native-artifacts'/gate.name
    destination.mkdir(mode=0o700,parents=True)
    manifest=[];total=0
    for name in gate.reports:
        source=ROOT/name
        if not source.exists():continue
        paths=sorted(source.rglob('*')) if source.is_dir() else [source]
        for path in paths:
            if path.is_symlink():raise ValueError('Linked native report')
            if not path.is_file():continue
            relative=path.relative_to(ROOT);size=path.stat().st_size;total+=size
            if len(manifest)>=8192 or total>MAX_LOG:raise ValueError('Native artifact bound')
            target=destination/relative;target.parent.mkdir(mode=0o700,parents=True,exist_ok=True)
            shutil.copyfile(path,target);target.chmod(0o600)
            import hashlib
            manifest.append({'path':str(relative),'bytes':size,'sha256':hashlib.sha256(target.read_bytes()).hexdigest()})
    write(destination/'manifest.safe.json',manifest)
    return str(destination.relative_to(audit))

def resources():
    memory={}
    for line in Path('/proc/meminfo').read_text().splitlines():
        if line.startswith(('MemTotal:','MemAvailable:')):
            key,value,*_=line.split();memory[key.rstrip(':')]=int(value)*1024
    cgroup={}
    for name in ('cpu.max','memory.max'):
        path=Path('/sys/fs/cgroup')/name
        cgroup[name]=path.read_text().strip() if path.is_file() else None
    return {'cpuCount':os.cpu_count(),'affinity':sorted(os.sched_getaffinity(0)), 'memoryBytes':memory,
            'cgroup':cgroup,'workspaceFreeBytes':shutil.disk_usage(ROOT).free,'processTimezone':'UTC','locale':'C.UTF-8',
            'qualification':'Local WSL analogue; fresh serial cohorts differ from independent GitHub VMs and shared browser job order'}

def main():
    parser=argparse.ArgumentParser(description=__doc__);parser.add_argument('--expected-sha',required=True);parser.add_argument('--audit',required=True,type=Path);parser.add_argument('--list',action='store_true');args=parser.parse_args()
    gates=catalog()
    if args.list:
        print(json.dumps({'gates':[{'name':g.name,'command':list(g.command),'requires':list(g.requires),'seconds':g.seconds} for g in gates],'inapplicable':NOT_APPLICABLE,'fullReplay':True},indent=2));return 0
    if sys.platform!='linux' or not sys.stdin.isatty():raise ValueError('Foreground owner Linux TTY required')
    if re.fullmatch('[a-f0-9]{40}',args.expected_sha) is None or git('rev-parse','HEAD')!=args.expected_sha or git('status','--porcelain'):
        raise ValueError('Exact clean owner source required')
    audit=args.audit
    if not audit.is_absolute() or audit.exists() or audit.is_symlink():raise ValueError('New exact private audit directory required')
    audit.mkdir(mode=0o700,parents=True);os.umask(0o077)
    lock=Path.home()/'.local/share/rct-pr3-local/local-release.lock';lock.parent.mkdir(mode=0o700,parents=True,exist_ok=True)
    lease=lock.open('a');fcntl.flock(lease,fcntl.LOCK_EX|fcntl.LOCK_NB)
    # The browser adapter owns the canonical port lease only during its own native cohort.
    nonce=secrets.token_hex(12);started=time.time_ns()
    observed=resources()
    if len(observed['affinity'])>4:os.sched_setaffinity(0,set(observed['affinity'][:4]))
    observed['executionAffinity']=sorted(os.sched_getaffinity(0))
    env={k:v for k,v in os.environ.items() if k in {'PATH','HOME','USER','LOGNAME','LANG','LC_ALL','TERM','JAVA_HOME','TMPDIR','HTTP_PROXY','HTTPS_PROXY','http_proxy','https_proxy','ALL_PROXY','all_proxy'}}
    bypass=os.environ.get('NO_PROXY',os.environ.get('no_proxy',''))
    env['NO_PROXY']=env['no_proxy']=','.join(filter(None,[bypass,'localhost','127.0.0.1','::1']))
    env.update(RCT_LOCAL_RELEASE_REVISION=args.expected_sha,RCT_LOCAL_RELEASE_NONCE=nonce,RCT_LOCAL_RELEASE_AUDIT=str(audit),
               RUNNER_TEMP=str(audit/'tmp'),PYTHONDONTWRITEBYTECODE='1',CI='true',TZ='UTC',LANG='C.UTF-8',LC_ALL='C.UTF-8',PIP_CONFIG_FILE='/dev/null',RCT_MONITORING_RUNTIME='isolated-local',RCT_OBSERVABILITY_APPS_RUNTIME='isolated-local',
               GRADLE_USER_HOME=str(audit/'cache/gradle'),npm_config_cache=str(audit/'cache/npm'),npm_config_userconfig='/dev/null',
               DOCKER_HOST='unix:///var/run/docker.sock',DOCKER_CONFIG=str(audit/'docker-config'),TRIVY_CACHE_DIR=str(audit/'cache/trivy'),
               PLAYWRIGHT_BROWSERS_PATH=str(audit/'cache/browsers'),NP01_PYTHON_EXECUTABLE=str(audit/'venv/bin/python'),RCT_GITLEAKS_REPORT=str(audit/'gitleaks.sarif'))
    for name in ['tmp','docker-config','cache']: (audit/name).mkdir(mode=0o700)
    wrapper_mode=(ROOT/'gradlew').stat().st_mode & 0o777
    results={};hold=False;cancelled=False
    write(audit/'identity.safe.json',{'revision':args.expected_sha,'nonce':nonce,'event':'LOCAL_CONTROL','runId':None,'runAttempt':None,'fullReplay':True,'resources':observed,'startedUtc':datetime.datetime.now(datetime.timezone.utc).isoformat()})
    try:
        for gate in gates:
            state=status(gate,results,hold)
            if state!='RUN':results[gate.name]={'status':state};continue
            print('RUN '+gate.name,flush=True)
            archive_old(gate,audit)
            if gate.name!='setup':
                env['PATH']=str(audit/'venv/bin')+':'+str(audit/'tools')+':'+os.environ['PATH']
                env.update(PROMTOOL=str(audit/'tools/promtool'),PROMETHEUS_BIN=str(audit/'tools/prometheus'),ALERTMANAGER_BIN=str(audit/'tools/alertmanager'))
            gate_started=time.time_ns()
            outcome=command(gate,env,audit)
            clean=git('rev-parse','HEAD')==args.expected_sha and not git('-c','core.fileMode=false','diff','--name-only','HEAD')
            artifacts=admit_gate(gate.name,ROOT,audit,gate_started,args.expected_sha,nonce)
            native_artifacts=preserve_reports(gate,audit)
            projection=audit/'gate-diagnostics'/gate.name;projection.mkdir(mode=0o700,parents=True)
            try:gate_diagnostics=project(audit/native_artifacts,projection,gate_started)
            except Exception:gate_diagnostics={'status':'UNAVAILABLE'}
            passed=outcome['nativeExit']==0 and outcome['cause'] is None and outcome['childrenReaped'] and clean and artifacts
            results[gate.name]={'status':'PASS' if passed else 'FAIL',**outcome,'command':list(gate.command),'revision':args.expected_sha,'secondsLimit':gate.seconds,'sourceRestoredExact':clean,'freshNativeArtifacts':artifacts,'nativeArtifacts':native_artifacts,'diagnostics':gate_diagnostics}
            print(results[gate.name]['status']+' '+gate.name,flush=True)
            # A resource/probe failure needs original saved-state diagnosis before any further test.
            safely_closed=False
            if gate.name.startswith('browser-'):
                try:
                    receipt=json.loads((audit/gate.name/'result.safe.json').read_text())
                    safely_closed=receipt.get('cleanup') is True and receipt.get('revision')==args.expected_sha and receipt.get('nonce')==nonce
                except Exception:pass
            hold=not clean or not outcome['childrenReaped'] or (not passed and ((gate.resource and not safely_closed) or outcome['cause'] is not None))
            cancelled=outcome['cause']=='CANCELLED'
            write(audit/(gate.name+'.safe.json'),results[gate.name])
            if cancelled:break
    except KeyboardInterrupt:
        cancelled=True;hold=True
    finally:
        os.chmod(ROOT/'gradlew',wrapper_mode)
        for gate in gates:results.setdefault(gate.name,{'status':'HOLD' if hold else 'BLOCKED'})
        try:diagnostics=project(ROOT,audit,started)
        except Exception:diagnostics={'status':'UNAVAILABLE'}
        accepted=not hold and not cancelled and all(r['status']=='PASS' for r in results.values())
        value={'schema':1,'revision':args.expected_sha,'event':'LOCAL_CONTROL','accepted':accepted,'cancelled':cancelled,'hold':hold,'gates':results,'inapplicable':NOT_APPLICABLE,'fullReplay':True,'resources':observed,'diagnostics':diagnostics}
        write(audit/'result.safe.json',value)
        print(json.dumps({'accepted':accepted,'pass':sum(r['status']=='PASS' for r in results.values()),'total':len(gates),'hold':hold,'cancelled':cancelled}),flush=True)
        fcntl.flock(lease,fcntl.LOCK_UN);lease.close()
    return 0 if accepted else 130 if cancelled else 1

if __name__=='__main__':raise SystemExit(main())
