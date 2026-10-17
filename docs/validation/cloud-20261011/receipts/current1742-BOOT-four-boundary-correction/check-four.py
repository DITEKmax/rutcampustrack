"""Four directed corrections; actual selected AST with public RAM adapters only."""
import ast
import copy
import datetime
import hashlib
import json
from pathlib import Path
import stat
import sys
from types import ModuleType, SimpleNamespace

ROOT=Path(__file__).absolute().parent
tree=ast.parse((ROOT/'private-bridge.py').read_text())
origin=Path('/workspace/scratch/rct-validation/p10_failure_frame_privacy_review/boot-private-consumer-source-review/review-controls.py')
old=ast.parse(origin.read_text())
fixtures={}
exec(compile(ast.Module(body=[n for n in old.body if isinstance(n,ast.ClassDef)
     and n.name in {'RAMOS','ParserRAM','PhaseOS','Clock','FakePath'}],type_ignores=[]),'RAM-fixtures','exec'),
     {'stat':stat,'SimpleNamespace':SimpleNamespace,'datetime':datetime},fixtures)
names={'Gate','need','identity','own_leaf','cleanup','save_new','read_once','instant','run_phase'}
selected=ast.Module(body=[n for n in tree.body if isinstance(n,(ast.FunctionDef,ast.ClassDef))
                         and n.name in names],type_ignores=[])
def namespace(o):
    n={'os':o,'stat':stat,'sys':sys,'hashlib':hashlib,'json':json,'Path':Path,
       'ModuleType':ModuleType,'SimpleNamespace':SimpleNamespace,'dt':datetime,
       'CAP':33554432,'PRIVATE':Path('/RAM/private'),'OLD':Path('/RAM/old'),'R2':Path('/RAM/r2')}
    exec(compile(selected,'selected-four-boundaries','exec'),n)
    return n
groups=[]

# 1. A close-only failure stays failed and owns/removes the completed leaf.
checks=[]
for drift in (None,('st_uid',999)):
    o=fixtures['RAMOS'](close_error=True,drift=drift); o.fsync=lambda _:None
    n=namespace(o)
    try:n['save_new'](3,'job.log.private',b'synthetic',lambda *a:None)
    except BaseException as e:
        checks.append(e is o.close_failure and e.bridge_leaf_identity==[1,2,1000,1000,0o600]
            and o.unlinks==(1 if drift is None else 0)
            and e.bridge_leaf_disposition==('REMOVED' if drift is None else 'RECONCILIATION_REQUIRED'))
groups.append({'id':'CLOSE_ONLY_OWNERSHIP','passed':all(checks),'known_cleanup_and_drift_retention':True})

# 2. Use exact immutable consumer AST plus only the new nested close adapter.
parser_path=Path('/workspace/scratch/rct-validation/current1742_e2e_boot_diagnostic_source_r2/closed-boot-parser.py')
parser_tree=ast.parse(parser_path.read_text())
consumer_nodes=[n for n in parser_tree.body if isinstance(n,(ast.FunctionDef,ast.ClassDef))
                and n.name in {'Blocked','require','consume_private'}]
close_node=next(n for n in ast.walk(tree) if isinstance(n,ast.FunctionDef) and n.name=='parser_close')
factory=ast.parse('def factory():\n close_error=None\n return None\n').body[0]
factory.body=[factory.body[0],copy.deepcopy(close_node),ast.parse('return parser_close, lambda: close_error').body[0]]
checks=[]
for read_error in (True,False):
    o=fixtures['ParserRAM']()
    if not read_error:o.read=lambda *a:b'x'
    n={'os':o,'sys':sys}
    exec(compile(ast.Module(body=[factory],type_ignores=[]),'new-close-adapter','exec'),n)
    close,closed=n['factory']()
    pn={'stat':stat,'sys':sys,'MAX_BYTES':33554432,'parse':lambda *_:{'state':'UNKNOWN'}}
    exec(compile(ast.Module(body=consumer_nodes,type_ignores=[]),'immutable-consumer-selected','exec'),pn)
    pn['os']=SimpleNamespace(O_RDONLY=o.O_RDONLY,O_NOFOLLOW=o.O_NOFOLLOW,open=o.open,
        fstat=o.fstat,read=o.read,close=close,stat=o.stat,unlink=o.unlink)
    try:
        projection=pn['consume_private'](3,{})
        # Exact post-consume close-only guard, before any projection publication.
        close_error=closed()
        if close_error is not None:raise close_error
    except BaseException as e:
        checks.append(e is (o.primary if read_error else o.secondary)
                      and o.stats==1 and o.unlinks==1)
groups.append({'id':'IMMUTABLE_PARSER_CLOSE','passed':all(checks),'primary_and_close_only_failures':True})

base=datetime.datetime(2026,10,13,tzinfo=datetime.timezone.utc)
deadline=base+datetime.timedelta(seconds=10)
def phase_scope(late=False,fail_report=False):
    state={'now':base+datetime.timedelta(seconds=5),'carrier_opens':0,'events':[]}
    class Clock:
        @staticmethod
        def now(_):return state['now']
        fromisoformat=staticmethod(datetime.datetime.fromisoformat)
    o=fixtures['PhaseOS'](); n=namespace(o)
    o.O_RDONLY=1; o.O_NOFOLLOW=16
    o.read=lambda *_:b'x'; o.stat=lambda *_args,**_kwargs:None
    o.unlink=lambda *_args,**_kwargs:None
    n.update(dt=SimpleNamespace(datetime=Clock,timezone=datetime.timezone),
             Path=fixtures['FakePath'],directory=lambda _:3,
             admit=lambda _:(base,deadline,base+datetime.timedelta(seconds=40)))
    grant={'private_directory_identity':[1,2,1000,1000,0o700],'head':'1742d1e806faa5c39c6d182a3554daa8f4239801'}
    producer={'state':'ROOT_OBSERVED_TOOL_RETURNED','job_id':115262001797,'run_id':38387263916,
              'head':grant['head'],'utc':'2026-10-13T00:00:01Z','carrier_bytes':1}
    actual_read=n['read_once']
    def read(parent,name,limit,*args):
        if name=='bridge-grant.json':return json.dumps(grant).encode()
        if name=='connector-returned.safe.json':return json.dumps(producer).encode()
        if name=='carrier-owner.safe.json':return b'{"identity":[1,3,1000,1000,384]}'
        if name=='job.carrier.private':
            if late:return actual_read(parent,name,limit,*args)
            args[-1]();state['events'].append('carrier-read');return b'x'
        raise AssertionError
    def opens(*args,**kwargs):state['carrier_opens']+=1;return 7
    o.open=opens
    def save(fd,name,data,guard):
        guard();state['events'].append('save:'+name)
        if name=='consume-attempt.safe.json' and late:
            state['now']=deadline+datetime.timedelta(seconds=1)
        if name=='REPORT.safe.json':
            record=json.loads(data);assert record['cause']=='UNKNOWN' and record['carrier_cleanup']=='PENDING'
            if fail_report:raise OSError()
        return [1,4,1000,1000,0o600]
    parser=SimpleNamespace(validate_binding=lambda _:None,
        consume_private=lambda *_:state['events'].append('parser-once') or {'state':'UNKNOWN','hints':[]})
    n.update(read_once=read,save_new=save,public_bytes=lambda _:b'{}',
        frozen_module=lambda path:SimpleNamespace(decode_carrier=lambda _:b'synthetic')
            if path.name=='decode-carrier.py' else parser,
        cleanup=lambda *_:state['events'].append('carrier-cleanup'))
    return n,state

# 3. The marker crosses cutoff; new actual private read guard refuses before open.
n,s=phase_scope(late=True)
try:n['run_phase']('consume')
except n['Gate']:groups.append({'id':'FRESH_CARRIER_READ','passed':s['carrier_opens']==0,'late_private_open_count':s['carrier_opens']})

# 4. Actual run-phase order with only synthetic parser/file dependencies.
checks=[]
for denied in (False,True):
    n,s=phase_scope(fail_report=denied)
    try:n['run_phase']('consume')
    except OSError:
        checks.append(denied and 'carrier-cleanup' not in s['events'])
    else:
        checks.append(not denied and s['events'].index('save:REPORT.safe.json')<s['events'].index('carrier-cleanup')
                      and s['events'].count('parser-once')==1 and 'save:CLEANUP.safe.json' in s['events'])
groups.append({'id':'SAFE_RECEIPT_BEFORE_CLEANUP','passed':all(checks),'failed_receipt_retains_carrier':True})
assert len(groups)==4 and all(g['passed'] for g in groups),'FOUR_BOUNDARY_CONTROL_FAILURE'
(ROOT/'four-controls.safe.json').write_text(json.dumps({'source_only':True,'native':False,
    'real_private_files':False,'actual_helper_imports':False,'groups':groups},indent=2)+'\n')
print(json.dumps({'four_directed_groups':4,'passed':True,'actual_native':False}))
