from pathlib import Path
import ast,hashlib,json,os,types,zipfile
P=Path(__file__).parent;BASE=Path('/workspace/scratch/rct-validation/client_session_offline_acceptance/p07-existing-validator-plan');OLD=BASE/'execution-bindings';NEW=BASE/'execution-bindings-parser-seal-r1'
def sha(p):return hashlib.sha256(p.read_bytes()).hexdigest()
assert sha(OLD/'manifest.safe.json')=='d4ad25f898f7abf43971bb57ea460c3ed57f79376486e61cf58dad153b463419'
assert sha(NEW/'manifest.safe.json')=='0dffb44ce67684a2ad6e78299741120221f811334ea2da4cfaf322dbdf29f18a'
for n,h in json.loads((NEW/'manifest.safe.json').read_text())['files'].items():assert sha(NEW/n)==h
guard='if digest(parser_path) != plan["parser_sha256"]:\n    raise ValueError("Pinned closed XML parser changed before execution")\n'
new=(NEW/'logged-validator-child.py').read_text();old=(OLD/'logged-validator-child.py').read_text();assert new.replace(guard,'',1)==old
for n in ['run-validator-owned.py','closed-validator-xml.py','execution-plan.safe.json','source17.safe.json']:assert (NEW/n).read_bytes()==(OLD/n).read_bytes()
def selected(src,changed):
 t=ast.parse(src);begin=next(i for i,n in enumerate(t.body) if isinstance(n,ast.Assign) and any(isinstance(x,ast.Name) and x.id=='spec' for x in n.targets));end=next(i for i,n in enumerate(t.body) if isinstance(n,ast.Expr) and isinstance(n.value,ast.Call) and isinstance(n.value.func,ast.Attribute) and n.value.func.attr=='exec_module')
 calls=[];spec=types.SimpleNamespace(loader=types.SimpleNamespace(exec_module=lambda module:calls.append(True)))
 ns={'parser_path':'PUBLIC_PARSER','digest':lambda path:'CHANGED_PUBLIC_HASH' if changed else 'EXACT_PUBLIC_HASH','plan':{'parser_sha256':'EXACT_PUBLIC_HASH'},'importlib':types.SimpleNamespace(util=types.SimpleNamespace(spec_from_file_location=lambda *a:spec,module_from_spec=lambda *a:object()))}
 try:exec(compile(ast.fix_missing_locations(ast.Module(body=t.body[begin:end+1],type_ignores=[])),'<selected-actual-helper-pin-exec>','exec'),ns);refused=False
 except ValueError:refused=True
 return {'loader_entered':bool(calls),'refused':refused}
controls={'old_changed':selected(old,True),'new_changed':selected(new,True),'new_exact':selected(new,False)}
assert controls=={'old_changed':{'loader_entered':True,'refused':False},'new_changed':{'loader_entered':False,'refused':True},'new_exact':{'loader_entered':True,'refused':False}}
r={'verdict':'APPROVE_SOURCE','scope':'P07-PARSER-SEAL-BEFORE-EXEC_ONLY','child_sha256':sha(NEW/'logged-validator-child.py'),'author_manifest_sha256':sha(NEW/'manifest.safe.json'),'whole_inverse_old_child_byte_exact':True,'unchanged_parent_parser_plan_source17':True,'selected_actual_AST_RAM_controls':controls,'old_review_manifest':'db7e7fd878c205d09f138fb7af5e06cb6eb9ef6df973ce6159a85685bf397312','native_private_XML_helper_execution':'NOT_RUN','qualification':'Same pinned helper checked immediately before execution. Existing Root grant/actual capacity/fresh native15/source preservation/reap requirements remain; P07 parent UNKNOWN.'}
os.umask(0o077)
with (P/'recheck.safe.json').open('x') as f:json.dump(r,f,indent=2);f.write('\n')
with (P/'REPORT.md').open('x') as f:f.write('APPROVE_SOURCE: only exact parser pin guard added immediately before exec_module; whole inverse restores original child. Independent selected actual AST publicRAM control: old changed helper entered; new changed helper refused; new exact pin entered. No real helper/privateXML/native executed. Parent/parser/plan/source17 and original seal unchanged; native grant and exact15 execution/reap evidence remain external.\n')
files=[p for p in P.iterdir() if p.is_file()]
with (P/'manifest.safe.json').open('x') as f:json.dump({'verdict':'APPROVE_SOURCE','files':[{'path':p.name,'bytes':p.stat().st_size,'sha256':sha(p)} for p in sorted(files)]},f,indent=2);f.write('\n')
with zipfile.ZipFile(P/'safe-recheck.zip','x',zipfile.ZIP_DEFLATED) as z:
 for p in sorted(files)+[P/'manifest.safe.json']:z.write(p,p.name)
print(json.dumps({'verdict':'APPROVE_SOURCE','manifest_sha256':sha(P/'manifest.safe.json'),'zip_sha256':sha(P/'safe-recheck.zip'),'zip_bytes':(P/'safe-recheck.zip').stat().st_size}))
