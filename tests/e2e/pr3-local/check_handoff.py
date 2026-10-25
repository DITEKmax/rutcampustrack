#!/usr/bin/env python3
"""Public syntax and adversarial ownership checks; no Docker/runtime import or real data."""
import ast,copy,datetime,hashlib,json,os,pathlib,signal,subprocess,sys,tempfile,types

HERE=pathlib.Path(__file__).resolve().parent

def main():
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
 tree=ast.parse((HERE/'local.py').read_text());guard=next(x for x in tree.body if isinstance(x,ast.FunctionDef) and x.name=='guarded')
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
   env={'PRIVATE':pathlib.Path(tmp),'ROOT':pathlib.Path(tmp),'time':types.SimpleNamespace(time_ns=lambda:number,monotonic=monotonic,sleep=lambda _:None),'os':types.SimpleNamespace(chmod=os.chmod,killpg=lambda pid,sig:signals.append((pid,sig))),'subprocess':types.SimpleNamespace(Popen=lambda *a,**kw:fake_proc,TimeoutExpired=subprocess.TimeoutExpired,STDOUT=subprocess.STDOUT),'signal':signal,'write':lambda p,v:receipts.append(v)}
   exec(compile(ast.Module(body=[guard],type_ignores=[]),'<guarded-only>','exec'),env)
   try:env['guarded'](fake_runtime,['synthetic-child'],'native-case')
   except (KeyboardInterrupt,AssertionError):pass
   else:raise AssertionError('Guard did not propagate after exact child closure')
   assert signals==[(987654,signal.SIGTERM)] and receipts[0]['guardStopped'] and receipts[0]['exit']==2 and receipts[0]['interrupted']==(label=='interrupt')
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
 print(json.dumps({'pythonSyntax':len(py),'nodeSyntax':len(js),'composeServices':12,'ownershipCases':cases,'preparedDirectoryCases':4,'privateInputTamperCases':1,'startupFreezeCases':2,'processGuardCases':process_cases,'lifecycleCases':lifecycle_cases,'runtimeDockerAuthApiDbBrowserActions':0,'realProcessSignals':0,'localGate':'NOT_RUN'},indent=2))

if __name__=='__main__':main()
