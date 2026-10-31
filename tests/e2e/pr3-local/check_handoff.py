#!/usr/bin/env python3
"""Public syntax and adversarial ownership checks; no Docker/runtime import or real data."""
import ast,copy,datetime,hashlib,json,os,pathlib,signal,subprocess,sys,tempfile,types

HERE=pathlib.Path(__file__).resolve().parent

def memory_controls():
 # Execute only finite pure/extracted functions; no real resource reads or runtime import.
 import argparse,contextlib,io
 local_tree=ast.parse((HERE/'local.py').read_text());runtime_tree=ast.parse((HERE/'runtime/runtime.py').read_text())
 def extracted(tree,names,env):
  exec(compile(ast.Module(body=[x for x in tree.body if isinstance(x,ast.FunctionDef) and x.name in names],type_ignores=[]),'<memory-policy-only>','exec'),env)
  return env
 local=extracted(local_tree,['prepare_admission','available'],{})
 floor=12884901888;optfloor=12000000000;owner=12544364544
 budget={'freeBytes':7516192768,'memoryMaximum':floor,'memoryAvailable':5368709120,'cpuCount':4};cases=[]
 def rejects(call):
  try:call()
  except AssertionError:return
  raise AssertionError('Invalid memory admission was accepted')
 for opted in [False,True]:
  minimum=optfloor if opted else floor
  assert local['prepare_admission']({**budget,'memoryMaximum':minimum},opted)['minimumTotalBytes']==minimum
  rejects(lambda:local['prepare_admission']({**budget,'memoryMaximum':minimum-1},opted))
  for key,value in [('freeBytes',7516192767),('memoryAvailable',5368709119),('cpuCount',3)]:
   rejects(lambda:local['prepare_admission']({**budget,key:value},opted))
  cases.append(('opt-in' if opted else 'default')+'-boundary-and-unchanged-reserves')
 rejects(lambda:local['prepare_admission']({**budget,'memoryMaximum':owner}))
 assert local['prepare_admission']({**budget,'memoryMaximum':owner},True)['explicit12gbWsl'] is True
 policies=[local['prepare_admission'](budget),local['prepare_admission'](budget,True)]
 runtime=extracted(runtime_tree,['minimum_memory_total','admission'],{'S':{},'P':{}})
 assert runtime['minimum_memory_total']()==floor
 for policy in policies:
  runtime.update(S={'memoryAdmission':copy.deepcopy(policy)},P={'memoryAdmission':copy.deepcopy(policy)})
  assert runtime['minimum_memory_total']()==policy['minimumTotalBytes']
 for settings,pins in [({},policies[1]),(policies[1],{}),(policies[0],policies[1]),(None,None),({'name':'unknown'},policies[1]),({**policies[1],'minimumTotalBytes':0},policies[1]),({**policies[1],'explicit12gbWsl':1},policies[1]),(policies[1],{**policies[1],'explicit12gbWsl':1}),(policies[1],{**policies[1],'minimumTotalBytes':12000000000.0}),(policies[1],{**policies[1],'extra':True})]:
  runtime.update(S={'memoryAdmission':settings},P={'memoryAdmission':pins});rejects(runtime['minimum_memory_total'])
 cases.extend(['owner-observation-opt-in-only','legacy-default-and-two-valid-frozen-policies','missing-malformed-mismatched-policy-rejected'])
 class FakePath:
  def __init__(self,value):self.value=value
  def __truediv__(self,name):return FakePath(self.value+'/'+name)
  def exists(self):return self.value in files
  def read_text(self):return files[self.value]
 files={'/proc/meminfo':'MemTotal: 16777216 kB'+chr(10)+'MemAvailable: 12582912 kB'+chr(10),'/sys/fs/cgroup/memory.max':str(owner),'/sys/fs/cgroup/memory.current':'0'}
 local.update(pathlib=types.SimpleNamespace(Path=FakePath),shutil=types.SimpleNamespace(disk_usage=lambda _:types.SimpleNamespace(free=7516192768)),REPO=None,os=types.SimpleNamespace(cpu_count=lambda:4))
 observed=local['available']();assert observed['memoryMaximum']==owner;local['prepare_admission'](observed,True);rejects(lambda:local['prepare_admission'](observed))
 files['/sys/fs/cgroup/memory.max']=str(optfloor-1);rejects(lambda:local['prepare_admission'](local['available'](),True))
 files['/sys/fs/cgroup/memory.max']=str(floor);files['/sys/fs/cgroup/memory.current']=str(floor-5368709119);rejects(lambda:local['prepare_admission'](local['available'](),True))
 cases.append('cgroup-total-and-current-headroom-remain-binding')
 cli_calls=[]
 class Parser(argparse.ArgumentParser):
  def parse_args(self,*args,**kwargs):return super().parse_args(argv)
 cli=extracted(local_tree,['main'],{'argparse':types.SimpleNamespace(ArgumentParser=Parser),'__doc__':'synthetic CLI','install_handlers':lambda:None,'prepare':lambda args:cli_calls.append(args),'platform':lambda:cli_calls.append('forbidden-platform')})
 for opted in [False,True]:
  argv=['prepare','--source-sha','f'*40]+(['--allow-12gb-wsl'] if opted else []);cli['main']()
  assert cli_calls[-1].allow_12gb_wsl is opted
 for action in ['start','frontends','fixture','case','inspect','summary','stop']:
  argv=[action,'--allow-12gb-wsl'];before=len(cli_calls)
  with contextlib.redirect_stderr(io.StringIO()):
   try:cli['main']()
   except SystemExit as error:assert error.code==2
   else:raise AssertionError('Non-prepare opt-in accepted')
  assert len(cli_calls)==before
 cases.append('prepare-only-cli-no-runtime-actions')
 # Existing phase-specific free-RAM guard must still reject before any domain work.
 for policy in policies:
  runtime.update(S={'memoryAdmission':policy,'sourceSha':'f'*40},P={'memoryAdmission':policy})
  observed={'diskAvailable':2684354560,'memoryMaximum':policy['minimumTotalBytes'],'minimumMemoryMaximumBytes':policy['minimumTotalBytes'],'memoryAvailable':1073741824}
  go={'approved':True,'phase':'fixture-api','rootHead':'f'*40,'sourceCommit':'f'*40,'packageSha256':'synthetic','ownOrigin':'http://127.0.0.1:14250'}
  runtime.update(json=json,pathlib=types.SimpleNamespace(Path=lambda _:types.SimpleNamespace(read_text=lambda:json.dumps(go))),package_hash=lambda:'synthetic',source=lambda:None,ready=lambda:None,resources=lambda:observed)
  runtime['admission']('fixture-api','synthetic-go')
  observed['memoryAvailable']-=1;rejects(lambda:runtime['admission']('fixture-api','synthetic-go'))
  observed['memoryAvailable']=1073741824;observed['memoryMaximum']-=1;rejects(lambda:runtime['admission']('fixture-api','synthetic-go'))
 cases.append('runtime-policy-total-and-existing-1gib-free-boundaries')
 return cases

def main():
 memory=memory_controls()
 py=list(HERE.rglob('*.py'));js=list(HERE.rglob('*.cjs'))
 for file in py:ast.parse(file.read_text(),filename=str(file))
 for file in js:subprocess.run(['node','--check',str(file)],check=True,capture_output=True)
 subprocess.run(['node',str(HERE/'check_prepared_private.cjs')],check=True,capture_output=True)
 contract=json.loads((HERE/'source-contract.json').read_text());compose=json.loads((HERE/'templates/compose.template.json').read_text())
 assert compose['name']=='rct-pr3-local' and len(compose['services'])==12
 assert set(compose['services'])==set(contract['minimumServices'])
 assert [(name,s['ports']) for name,s in compose['services'].items() if 'ports' in s]==[('tls-edge',['127.0.0.1:18443:8443'])]
 assert compose['networks']['private_net']['ipam']['config']==[{'subnet':'172.31.35.0/24'}]
 assert not any('privileged' in s or 'container_name' in s or 'network_mode' in s for s in compose['services'].values())
 for file in [HERE/'local.py',*(HERE/'runtime').iterdir(),*(HERE/'templates').iterdir()]:
  if file.is_file():assert '/workspace/' not in file.read_text(),'Nonportable path: '+file.name
 # Execute only the ownership function extracted from its AST, with fake Docker
 # metadata and synthetic /tmp baseline. No runtime module import or subprocess.
 tree=ast.parse((HERE/'runtime/runtime.py').read_text());function=next(x for x in tree.body if isinstance(x,ast.FunctionDef) and x.name=='ownership')
 foreign={'Id':'foreign','Name':'/old-stand-auth','Image':'immutable-old','Mounts':[],'Running':True,'Project':'other','Service':'auth','Owner':None}
 own={'Id':'own','Name':'/rct-pr3-local-auth-service-1','Image':'immutable-new','Mounts':[{'Type':'bind','Source':'/synthetic/own/app.jar','Destination':'/app/app.jar','RW':False}],'Running':True,'Project':'rct-pr3-local','Service':'auth-service','Owner':'fresh-nonce'}
 cases=[]
 with tempfile.TemporaryDirectory(prefix='pr3-handoff-ownership-') as tmp:
  p=pathlib.Path(tmp);(p/'foreign-baseline.json').write_text(json.dumps([foreign]))
  state={'rows':[copy.deepcopy(foreign),copy.deepcopy(own)],'resources':{'volume':[],'network':[]}}
  def fake_docker(*args):
   if args[1]=='ls':return types.SimpleNamespace(stdout='owned-resource\n' if state['resources'][args[0]] else '')
   assert args[1]=='inspect';return types.SimpleNamespace(stdout=json.dumps(state['resources'][args[0]]))
  env={'json':json,'PRIVATE':p,'PROJECT':'rct-pr3-local','S':{'ownerNonce':'fresh-nonce','services':['auth-service'],'images':{'owned-tag':'immutable-new'},'servicePins':{'auth-service':{'image':'owned-tag','mounts':copy.deepcopy(own['Mounts'])}}},'snapshot':lambda:state['rows'],'docker':fake_docker}
  exec(compile(ast.Module(body=[function],type_ignores=[]),'<ownership-only>','exec'),env)
  assert env['ownership']()==state['rows'];cases.append('known-own-and-foreign-accepted')
  for label,change in [('foreign-image',lambda:state['rows'][0].update(Image='different')),('foreign-mount',lambda:state['rows'][0].update(Mounts=[{'Source':'unexpected'}])),('foreign-running-state',lambda:state['rows'][0].update(Running=False)),('foreign-disappearance',lambda:state['rows'].pop(0)),('same-project-wrong-nonce',lambda:state['rows'][1].update(Owner='foreign-nonce')),('same-project-unknown-service',lambda:state['rows'][1].update(Service='unknown')),('owned-image',lambda:state['rows'][1].update(Image='different')),('owned-mount-binding',lambda:state['rows'][1]['Mounts'][0].update(Source='/foreign/app.jar')),('foreign-volume',lambda:state['resources'].update(volume=[{'Labels':{'rct.pr3.local.owner':'foreign-nonce'}}])),('foreign-network',lambda:state['resources'].update(network=[{'Labels':{'rct.pr3.local.owner':'foreign-nonce'}}]))]:
   state.update(rows=[copy.deepcopy(foreign),copy.deepcopy(own)],resources={'volume':[],'network':[]});change()
   try:env['ownership']()
   except AssertionError:cases.append(label+'-rejected')
   else:raise AssertionError(label+' was incorrectly admitted')
 # An interrupted parent must stop its exact child group even though it started
 # a new session. No signal or real subprocess is used in this adversarial case.
 tree=ast.parse((HERE/'local.py').read_text());guard_names=['guarded','spawn_owned','reap_owned','consume_pending_stop'];guards=[x for x in tree.body if isinstance(x,ast.FunctionDef) and x.name in guard_names]
 process_cases=[]
 with tempfile.TemporaryDirectory(prefix='pr3-handoff-interrupt-') as tmp:
  for number,label in enumerate(['interrupt','disk-early-reserve','timeout']):
   signals=[];receipts=[];ticks=[0]
   def monotonic():
    value=1000 if label=='timeout' and ticks[0] else 0;ticks[0]+=1;return value
   def observed_resources():
    if label=='interrupt':raise KeyboardInterrupt()
    return {'diskAvailable':2147483648 if label=='disk-early-reserve' else 7516192768}
   fake_proc=types.SimpleNamespace(pid=987654,poll=lambda:None,wait=lambda timeout:2)
   fake_runtime=types.SimpleNamespace(ENV={},resources=observed_resources)
   env={'PRIVATE':pathlib.Path(tmp),'ROOT':pathlib.Path(tmp),'_OWNERSHIP_TRANSITION':False,'_STOP_PENDING':False,'time':types.SimpleNamespace(time_ns=lambda:number,monotonic=monotonic,sleep=lambda _:None),'os':types.SimpleNamespace(chmod=os.chmod,killpg=lambda pid,sig:signals.append((pid,sig))),'subprocess':types.SimpleNamespace(Popen=lambda *a,**kw:fake_proc,TimeoutExpired=subprocess.TimeoutExpired,STDOUT=subprocess.STDOUT),'signal':signal,'write':lambda p,v:receipts.append(v)}
   exec(compile(ast.Module(body=guards,type_ignores=[]),'<guarded-only>','exec'),env)
   try:env['guarded'](fake_runtime,['synthetic-child'],'native-case')
   except (KeyboardInterrupt,AssertionError):pass
   else:raise AssertionError('Guard did not propagate after exact child closure')
   receipt=receipts[-1]
   assert signals==[(987654,signal.SIGTERM)] and receipt['guardStopped'] and receipt['exit']==2 and receipt['interrupted']==(label=='interrupt') and receipt['children'][0]['reaped']
   process_cases.append(label+'-stops-exact-owned-child')
 # Private env/config bindings are checked by digest without exporting values.
 runtime_tree=ast.parse((HERE/'runtime/runtime.py').read_text());inputs=next(x for x in runtime_tree.body if isinstance(x,ast.FunctionDef) and x.name=='inputs')
 with tempfile.TemporaryDirectory(prefix='pr3-private-binding-') as tmp:
  root=pathlib.Path(tmp);private=root/'private';private.mkdir();file=private/'local.env';file.write_text('SYNTHETIC_ONLY=one\n')
  digest=lambda p:hashlib.sha256(p.read_bytes()).hexdigest()
  bindings={'sourceSha':'f'*40,'ownerNonce':'fresh-nonce','files':{'local.env':digest(file)}}
  (private/'input-bindings.json').write_text(json.dumps(bindings));(root/'PRIVATE-INPUTS-SHA256').write_text(digest(private/'input-bindings.json'))
  env={'ROOT':root,'PRIVATE':private,'pathlib':pathlib,'json':json,'sha':digest,'S':{'sourceSha':'f'*40,'ownerNonce':'fresh-nonce'}}
  exec(compile(ast.Module(body=[inputs],type_ignores=[]),'<private-inputs-only>','exec'),env);env['inputs']();file.write_text('SYNTHETIC_ONLY=changed\n')
  try:env['inputs']()
  except AssertionError:pass
  else:raise AssertionError('Modified private env was admitted')
 local_tree=ast.parse((HERE/'local.py').read_text());names=['start','frontends','close_startup','stop_owned'];functions=[x for x in local_tree.body if isinstance(x,ast.FunctionDef) and x.name in names]
 def invalid_source():raise AssertionError('modified-package')
 env={};exec(compile(ast.Module(body=functions,type_ignores=[]),'<lifecycle-only>','exec'),env)
 for name in ['start','frontends']:
  try:env[name](types.SimpleNamespace(source=invalid_source))
  except AssertionError as error:assert str(error)=='modified-package'
  else:raise AssertionError('Startup used modified package')
 lifecycle_cases=[]
 for label in ['readiness-reserve','readiness-timeout','readiness-interrupt','unqualified-closure']:
  with tempfile.TemporaryDirectory(prefix='pr3-startup-closure-') as tmp:
   root=pathlib.Path(tmp);private=root/'private';private.mkdir();(root/'compose.json').write_text(json.dumps({'services':{'auth':{'image':'synthetic-image'}}}));commands=[];records=[];stage={'phase':None}
   def metadata():
    if label=='unqualified-closure' and stage['phase']=='readiness':raise AssertionError('unqualified-owner')
    return [copy.deepcopy(own)]
   def fake_guarded(runtime,args,phase,timeout):
    stage['phase']=phase
    if phase=='readiness':
     if label=='readiness-interrupt':raise KeyboardInterrupt()
     raise RuntimeError(label)
   fake_runtime=types.SimpleNamespace(source=lambda:None,ownership=metadata,resources=lambda:{'diskAvailable':7516192768,'memoryAvailable':5368709120},compose=lambda *args:['synthetic-compose'],S={'sourceSha':'f'*40,'ownerNonce':'fresh-nonce'})
   env={'ROOT':root,'PRIVATE':private,'PROJECT':'rct-pr3-local','DOCKER':['synthetic-docker'],'json':json,'datetime':datetime,'sys':sys,'time':types.SimpleNamespace(time_ns=lambda:1),'ports_free':lambda:None,'write':lambda p,v:records.append((p.name,v)),'run':lambda args,**kwargs:commands.append(args),'guarded':fake_guarded,'print':lambda *a,**k:None}
   exec(compile(ast.Module(body=functions,type_ignores=[]),'<lifecycle-only>','exec'),env)
   try:env['start'](fake_runtime)
   except (AssertionError,RuntimeError,KeyboardInterrupt):pass
   else:raise AssertionError('Failed readiness was accepted')
   stops=[x for x in commands if 'stop' in x]
   if label=='unqualified-closure':assert not stops and any('HOLD' in n for n,_ in records)
   else:assert stops==[['synthetic-docker','stop','--time','2','own']] and any(v.get('exactOwnedIdsStopped')==['own'] for _,v in records)
   lifecycle_cases.append(label+'-qualified-close-or-HOLD')
 with tempfile.TemporaryDirectory(prefix='pr3-partial-stop-') as tmp:
  private=pathlib.Path(tmp);commands=[];env={'PRIVATE':private,'PROJECT':'rct-pr3-local','DOCKER':['synthetic-docker'],'json':json,'time':types.SimpleNamespace(time_ns=lambda:1),'write':lambda *a:None,'run':lambda args,**kwargs:commands.append(args),'print':lambda *a,**k:None}
  exec(compile(ast.Module(body=functions,type_ignores=[]),'<stop-only>','exec'),env)
  fake_runtime=types.SimpleNamespace(ownership=lambda:[copy.deepcopy(own)])
  env['stop_owned'](fake_runtime);assert commands==[['synthetic-docker','stop','--time','20','own']]
  commands.clear();(private/'intent.json').write_text('{}')
  try:env['stop_owned'](fake_runtime)
  except FileNotFoundError:pass
  else:raise AssertionError('Unknown business case permitted stop')
  assert not commands;lifecycle_cases.extend(['partial-known-startup-stop-without-ready','unknown-original-case-stop-HOLD'])
 from check_signal_guard import run_controls
 signals=run_controls()
 print(json.dumps({'memoryAdmissionControls':memory,'pythonSyntax':len(py),'nodeSyntax':len(js),'composeServices':12,'ownershipCases':cases,'preparedDirectoryCases':4,'privateInputTamperCases':1,'startupFreezeCases':2,'processGuardCases':process_cases,'lifecycleCases':lifecycle_cases,'signalAndExceptionControls':signals['signalAndExceptionControls'],'runtimeDockerAuthApiDbBrowserActions':0,'realSignalsToSyntheticProcesses':signals['realSignalsToSyntheticProcesses'],'realServiceSignals':0,'localGate':'NOT_RUN'},indent=2))

if __name__=='__main__':main()
