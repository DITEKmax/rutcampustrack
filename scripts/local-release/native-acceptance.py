"""One fresh original seven-actor case plus the distinct common-Nginx checks."""
from pathlib import Path
import importlib.util
import json
import os
import shutil
import signal
import socket
import subprocess
import sys
import time

ROOT=Path(__file__).resolve().parents[2]
AUDIT=Path(os.environ['RCT_LOCAL_RELEASE_AUDIT'])
REVISION=os.environ['RCT_LOCAL_RELEASE_REVISION']
NONCE=os.environ['RCT_LOCAL_RELEASE_NONCE']
WORK=AUDIT/'native'
SOURCE=WORK/'native-source'
DOCKER=['docker','--host=unix:///var/run/docker.sock']
CHILDREN=[]

def run(args,seconds=300,tty=False,cwd=None):
    if tty:
        with open('/dev/tty','r+') as console:
            return subprocess.run(args,cwd=cwd or SOURCE,stdin=console,stderr=console,check=True,timeout=seconds)
    return subprocess.run(args,cwd=cwd or SOURCE,check=True,timeout=seconds)

def data(args):
    return subprocess.check_output(args,cwd=SOURCE,text=True,stderr=subprocess.PIPE,timeout=20).strip()

def save(value):
    target=WORK/'acceptance.safe.json'
    raw=json.dumps(value,indent=2)+'\n';temp=WORK/'acceptance.tmp'
    with temp.open('x') as out:out.write(raw)
    temp.replace(target)

def close_children():
    for child in reversed(CHILDREN):
        # Registered own PGIDs remain ours even after their leaders exit.
        try:
            try:os.killpg(child.pid,signal.SIGINT)
            except ProcessLookupError:pass
            try:child.wait(timeout=20)
            except subprocess.TimeoutExpired:pass
        finally:
            try:os.killpg(child.pid,signal.SIGTERM)
            except ProcessLookupError:pass
            time.sleep(.25)
            try:os.killpg(child.pid,signal.SIGKILL)
            except ProcessLookupError:pass
            child.wait(timeout=5)

def main():
    WORK.mkdir(mode=0o700)
    result={'schema':1,'revision':REVISION,'phase':'FRESH_SOURCE','result':'FAIL','commonNginx':'NOT_RUN','group':'NOT_RUN','frontendChildrenClosed':False,'ownServicesStopped':False,'oldResourcesTouched':False}
    local=SOURCE/'tests/e2e/pr3-local/local.py'
    common=None;common_id=None
    try:
        run(['git','worktree','add','--detach',str(SOURCE),REVISION],cwd=ROOT,seconds=60)
        for service,module in {'auth':'auth-service/auth-app','academic':'academic-service/academic-app','schedule':'schedule-service/schedule-app','attendance':'attendance-service/attendance-app','notification':'notification-service/notification-app','mobile-bff':'mobile-bff/mobile-bff-app','api-gateway':'api-gateway'}.items():
            name=service+'-app-0.1.0.jar' if service!='api-gateway' else 'api-gateway-0.1.0.jar'
            relative=Path('services')/module/'build/libs'/name
            source=ROOT/relative;target=SOURCE/relative
            if source.is_symlink() or not source.is_file():raise ValueError('Fresh built JAR required')
            target.parent.mkdir(mode=0o700,parents=True,exist_ok=True);shutil.copyfile(source,target)
        for relative in ('frontends/node_modules','tests/e2e/node_modules'):
            shutil.copytree(ROOT/relative,SOURCE/relative,symlinks=True)
        shutil.copytree(ROOT/'frontends/web-vue/dist',SOURCE/'frontends/web-vue/dist')
        binding={'repo':str(SOURCE),'project':'rct-pr3-native-'+NONCE[:12],'ports':[15350,15351,15352,19443],'subnet':'172.31.36.0/24','nonce':NONCE}
        (SOURCE/'.pr3-release-config.json').write_text(json.dumps(binding)+'\n')
        for port in binding['ports']+[15440,15443]:
            with socket.socket() as s:s.bind(('127.0.0.1',port))
        # Resolve only the original finite image tags, then original prepare freezes their actual IDs.
        compose=json.loads((SOURCE/'tests/e2e/pr3-local/templates/compose.template.json').read_text())
        for image in sorted({s['image'] for s in compose['services'].values()}):
            probe=subprocess.run(DOCKER+['image','inspect','--format','{{.Id}}',image],stdout=subprocess.DEVNULL,stderr=subprocess.DEVNULL,timeout=15)
            if probe.returncode:run(DOCKER+['pull',image],seconds=180)
        result['phase']='PREPARE';save(result)
        # Keep both original TTY requirements. The owner enters and confirms one new synthetic password.
        run(['/usr/bin/python3',str(local),'prepare','--source-sha',REVISION,'--allow-12gb-wsl'],tty=True)
        result['phase']='START';save(result)
        run(['/usr/bin/python3',str(local),'start'],seconds=900)
        # Prefix compilation and common static checks do not consume the finite Vite lifetime.
        result['phase']='COMMON_STATIC_PREPARE';save(result)
        spec=importlib.util.spec_from_file_location('common_acceptance',SOURCE/'scripts/local-release/common-nginx.py')
        helper=importlib.util.module_from_spec(spec);spec.loader.exec_module(helper)
        common=helper.Common(SOURCE,WORK,REVISION,NONCE,AUDIT)
        common.prepare_start_check()
        front=subprocess.Popen(['/usr/bin/python3',str(local),'frontends'],cwd=SOURCE,start_new_session=True)
        CHILDREN.append(front)
        deadline=time.monotonic()+120
        while True:
            if front.poll() is not None:raise ValueError('Owned frontend leader exited')
            ready=True
            for port in binding['ports'][:3]:
                with socket.socket() as s:ready=ready and s.connect_ex(('127.0.0.1',port))==0
            if ready:break
            if time.monotonic()>=deadline:raise TimeoutError('Frontend readiness')
            time.sleep(.25)
        result['phase']='ORIGINAL_FIXTURE';save(result)
        run(['/usr/bin/python3',str(local),'fixture'],seconds=600)
        result['phase']='COMMON_NGINX';save(result)
        common.browser()
        common.close();common=None
        result['commonNginx']='PASS';result['phase']='ORIGINAL_GROUP_UI';save(result)
        run(['/usr/bin/python3',str(local),'case'],seconds=650)
        receipt=json.loads((SOURCE/'.pr3-local/runtime/private/native-case-result.json').read_text())
        if receipt.get('allPassed') is not True or receipt.get('cleanupComplete') is not True:raise ValueError('Original group acceptance incomplete')
        result['group']='PASS';result['phase']='ORIGINAL_SCOPED_STOP';save(result)
        run(['/usr/bin/python3',str(local),'stop'],seconds=360)
        result['ownServicesStopped']=True;result['result']='PASS';result['phase']='COMPLETE'
    except BaseException as error:
        result['errorKind']=type(error).__name__ if type(error).__name__ in {'ValueError','TimeoutError','CalledProcessError','KeyboardInterrupt','AssertionError','OSError'} else 'OTHER'
        raise
    finally:
        try:
            if common is not None:common.close()
        except Exception:result['commonCleanup']='HOLD'
        try:close_children();result['frontendChildrenClosed']=True
        finally:save(result)
    print(json.dumps({'result':result['result'],'commonNginx':result['commonNginx'],'group':result['group']}))

if __name__=='__main__':main()
