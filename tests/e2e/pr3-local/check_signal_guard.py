#!/usr/bin/env python3
"""Isolated Python signal/launch/poll controls. Docker/Auth/DB/browser are forbidden."""
import ast,importlib.util,json,os,pathlib,signal,subprocess,sys,tempfile,time,types,uuid

HERE=pathlib.Path(__file__).resolve().parent

def worker(mode,directory,nonce):
 spec=importlib.util.spec_from_file_location('pr3_local_signal_control',HERE/'local.py');module=importlib.util.module_from_spec(spec);spec.loader.exec_module(module)
 root=pathlib.Path(directory);assert root.name.startswith('pr3-signal-control-') and root.is_dir() and not root.is_symlink() and not any(root.iterdir()),'Only a fresh empty synthetic control directory is admitted'
 private=root/'private';private.mkdir(mode=0o700);module.ROOT=root;module.PRIVATE=private;module.DOCKER=['FORBIDDEN_REAL_DOCKER']
 (root/'compose.json').write_text(json.dumps({'services':{'synthetic':{'image':'synthetic-image'}}}))
 calls=[];resource_calls=[0];original_popen=subprocess.Popen
 sleep_code='import time; time.sleep(60) # pr3-synthetic-'+nonce
 def fake_command(args,**kwargs):
  assert args[:3]==['FORBIDDEN_REAL_DOCKER','image','inspect'] or args==['FORBIDDEN_REAL_DOCKER','stop','--time','2','synthetic-owned-id']
  calls.append(args);return ''
 def spawn_with_signal(*args,**kwargs):
  child=original_popen(*args,**kwargs)
  (root/'owned-child.json').write_text(json.dumps({'pid':child.pid,'code':sleep_code}))
  if mode=='launch-window':os.kill(os.getpid(),signal.SIGTERM)
  return child
 def resources():
  resource_calls[0]+=1
  if mode=='poll-exception' and resource_calls[0]>1:raise RuntimeError('SYNTHETIC_RESOURCE_POLL_FAILURE')
  return {'diskAvailable':7516192768,'memoryAvailable':5368709120}
 module.run=fake_command;module.ports_free=lambda:None;module.subprocess.Popen=spawn_with_signal
 runtime=types.SimpleNamespace(ENV=module.ENV,S={'sourceSha':'f'*40,'ownerNonce':'synthetic-only'},source=lambda:None,ownership=lambda:[{'Id':'synthetic-owned-id','Project':'rct-pr3-local'}],resources=resources,compose=lambda *args:[sys.executable,'-c',sleep_code])
 module.install_handlers();outcome=None
 try:module.start(runtime)
 except BaseException as error:outcome=type(error).__name__
 (root/'result.json').write_text(json.dumps({'outcome':outcome,'fakeDockerCalls':calls,'servicesReady':(private/'services-ready.json').exists()}))

def run_controls():
 tree=ast.parse((HERE/'local.py').read_text());main=next(n for n in tree.body if isinstance(n,ast.FunctionDef) and n.name=='main')
 assert isinstance(main.body[0],ast.Expr) and isinstance(main.body[0].value,ast.Call) and main.body[0].value.func.id=='install_handlers','Handler must precede child launch'
 results=[]
 for mode in ['sigterm-after-register','launch-window','poll-exception']:
  with tempfile.TemporaryDirectory(prefix='pr3-signal-control-') as temporary:
   root=pathlib.Path(temporary);nonce=uuid.uuid4().hex
   process=subprocess.Popen([sys.executable,str(__file__),'--worker',mode,temporary,nonce],stdout=subprocess.PIPE,stderr=subprocess.PIPE,start_new_session=True)
   try:
    if mode=='sigterm-after-register':
     deadline=time.monotonic()+5
     while not list((root/'private').glob('start-process-*.json')):
      if process.poll() is not None:
       diagnostic=json.loads((root/'result.json').read_text()) if (root/'result.json').exists() else {'outcome':'NO_CONTROL_RECEIPT'}
       raise AssertionError('Signal worker exited before registration: '+str(diagnostic['outcome']))
      if time.monotonic()>deadline:raise AssertionError('Child registration unavailable')
      time.sleep(.01)
     os.kill(process.pid,signal.SIGTERM)
    output,error=process.communicate(timeout=20);assert process.returncode==0,'Signal control failed: '+error.decode()[-500:]
    result=json.loads((root/'result.json').read_text());expected='RuntimeError' if mode=='poll-exception' else 'KeyboardInterrupt'
    assert result['outcome']==expected and not result['servicesReady']
    receipts=list((root/'private').glob('start-receipt-*.json'));assert len(receipts)==1
    receipt=json.loads(receipts[0].read_text());child=json.loads((root/'owned-child.json').read_text())
    assert receipt['children']==[{'pid':child['pid'],'exit':-signal.SIGTERM,'reaped':True}]
    assert receipt['guardStopped'] and receipt['error']==expected and receipt['interrupted']==(mode!='poll-exception')
    stops=[x for x in result['fakeDockerCalls'] if 'stop' in x];assert stops==[['FORBIDDEN_REAL_DOCKER','stop','--time','2','synthetic-owned-id']]
    closures=list((root/'private').glob('startup-guard-closure-*.json'));assert len(closures)==1
    closure=json.loads(closures[0].read_text());assert closure['exactOwnedIdsStopped']==['synthetic-owned-id'] and not closure['volumesDeleted']
    assert list((root/'private').glob('startup-stop-intent-*.json'))
    results.append(mode+'-child-reaped-and-qualified-startup-closed')
   finally:
    # Failure-only cleanup is limited to this control's exact fresh process/code.
    if (root/'owned-child.json').exists():
     child=json.loads((root/'owned-child.json').read_text());cmd=pathlib.Path('/proc')/str(child['pid'])/'cmdline'
     if cmd.exists() and child['code'].encode() in cmd.read_bytes().split(b'\0'):
      try:os.killpg(child['pid'],signal.SIGKILL)
      except ProcessLookupError:pass
    if process.poll() is None:
     os.killpg(process.pid,signal.SIGKILL);process.wait(timeout=5)
 return {'signalAndExceptionControls':results,'realSignalsToSyntheticProcesses':5,'realServiceSignals':0,'DockerAuthApiDbBrowserActions':0,'ownerLocalGate':'NOT_RUN'}

if __name__=='__main__':
 if len(sys.argv)>1 and sys.argv[1]=='--worker':worker(*sys.argv[2:])
 else:print(json.dumps(run_controls(),indent=2))
