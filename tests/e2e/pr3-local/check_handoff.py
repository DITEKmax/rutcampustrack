#!/usr/bin/env python3
"""Public syntax and adversarial ownership checks; no Docker/runtime import or real data."""
import ast,copy,json,os,pathlib,signal,subprocess,tempfile,types

HERE=pathlib.Path(__file__).resolve().parent

def main():
 py=list(HERE.rglob('*.py'));js=list((HERE/'runtime').glob('*.cjs'))
 for file in py:ast.parse(file.read_text(),filename=str(file))
 for file in js:subprocess.run(['node','--check',str(file)],check=True,capture_output=True)
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
 own={'Id':'own','Name':'/rct-pr3-local-auth-service-1','Image':'immutable-new','Mounts':[],'Running':True,'Project':'rct-pr3-local','Service':'auth-service','Owner':'fresh-nonce'}
 cases=[]
 with tempfile.TemporaryDirectory(prefix='pr3-handoff-ownership-') as tmp:
  p=pathlib.Path(tmp);(p/'foreign-baseline.json').write_text(json.dumps([foreign]))
  state={'rows':[copy.deepcopy(foreign),copy.deepcopy(own)],'resources':{'volume':[],'network':[]}}
  def fake_docker(*args):
   if args[1]=='ls':return types.SimpleNamespace(stdout='owned-resource\n' if state['resources'][args[0]] else '')
   assert args[1]=='inspect';return types.SimpleNamespace(stdout=json.dumps(state['resources'][args[0]]))
  env={'json':json,'PRIVATE':p,'PROJECT':'rct-pr3-local','S':{'ownerNonce':'fresh-nonce','services':['auth-service']},'snapshot':lambda:state['rows'],'docker':fake_docker}
  exec(compile(ast.Module(body=[function],type_ignores=[]),'<ownership-only>','exec'),env)
  assert env['ownership']()==state['rows'];cases.append('known-own-and-foreign-accepted')
  for label,change in [('foreign-image',lambda:state['rows'][0].update(Image='different')),('foreign-mount',lambda:state['rows'][0].update(Mounts=[{'Source':'unexpected'}])),('foreign-running-state',lambda:state['rows'][0].update(Running=False)),('foreign-disappearance',lambda:state['rows'].pop(0)),('same-project-wrong-nonce',lambda:state['rows'][1].update(Owner='foreign-nonce')),('same-project-unknown-service',lambda:state['rows'][1].update(Service='unknown')),('foreign-volume',lambda:state['resources'].update(volume=[{'Labels':{'rct.pr3.local.owner':'foreign-nonce'}}])),('foreign-network',lambda:state['resources'].update(network=[{'Labels':{'rct.pr3.local.owner':'foreign-nonce'}}]))]:
   state.update(rows=[copy.deepcopy(foreign),copy.deepcopy(own)],resources={'volume':[],'network':[]});change()
   try:env['ownership']()
   except AssertionError:cases.append(label+'-rejected')
   else:raise AssertionError(label+' was incorrectly admitted')
 # An interrupted parent must stop its exact child group even though it started
 # a new session. No signal or real subprocess is used in this adversarial case.
 tree=ast.parse((HERE/'local.py').read_text());guard=next(x for x in tree.body if isinstance(x,ast.FunctionDef) and x.name=='guarded')
 signals=[];receipts=[]
 def interrupted_resources():raise KeyboardInterrupt()
 fake_proc=types.SimpleNamespace(pid=987654,poll=lambda:None,wait=lambda timeout:2)
 fake_runtime=types.SimpleNamespace(ENV={},resources=interrupted_resources)
 with tempfile.TemporaryDirectory(prefix='pr3-handoff-interrupt-') as tmp:
  env={'PRIVATE':pathlib.Path(tmp),'ROOT':pathlib.Path(tmp),'time':types.SimpleNamespace(time_ns=lambda:1,monotonic=lambda:0,sleep=lambda _:None),'os':types.SimpleNamespace(chmod=os.chmod,killpg=lambda pid,sig:signals.append((pid,sig))),'subprocess':types.SimpleNamespace(Popen=lambda *a,**kw:fake_proc,TimeoutExpired=subprocess.TimeoutExpired,STDOUT=subprocess.STDOUT),'signal':signal,'write':lambda p,v:receipts.append(v)}
  exec(compile(ast.Module(body=[guard],type_ignores=[]),'<guarded-only>','exec'),env)
  try:env['guarded'](fake_runtime,['synthetic-child'],'native-case')
  except KeyboardInterrupt:pass
  else:raise AssertionError('Interrupt was not propagated after exact child closure')
 assert signals==[(987654,signal.SIGTERM)] and receipts[0]['interrupted'] and receipts[0]['guardStopped'] and receipts[0]['exit']==2
 print(json.dumps({'pythonSyntax':len(py),'nodeSyntax':len(js),'composeServices':12,'ownershipCases':cases,'processGuardCases':['interrupt-stops-exact-owned-child-group-and-preserves-unknown'],'runtimeDockerAuthApiDbBrowserActions':0,'realProcessSignals':0,'localGate':'NOT_RUN'},indent=2))

if __name__=='__main__':main()
