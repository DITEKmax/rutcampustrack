"""Closed core15 plus nine untrusted reported hints. SOURCE ONLY; future producer/source binding remains UNBOUND until independent review and actual push."""
from pathlib import Path
import json,re
SOURCE_HEAD='597716c3cae9f7d7f601cb5b5279baf598d841ef'
REPORTER_SHA='c69e4c46c2a03a285b11af11c03337bd2fd6472707148dd43bd4b928a757491c'
PRIVACY_SHA='25ed1ffc57462e0c6d0ef37dc355e89cd85236cb0afb7f90a41ec68610585a82'
STAGES={'UNKNOWN':'UNKNOWN','ARTIFACT_REPORTER_GUARD':'ARTIFACT_PRIVACY','ARTIFACT_CAPTURE_GUARD':'ARTIFACT_PRIVACY',
 'DATASET_TARGET_GUARD':'DATASET_TARGET','DATASET_CREDENTIALS_MISSING':'DATASET_USER','ACTOR_LOGIN_HTTP':'ACTOR_LOGIN',
 'ACTOR_SESSION_HTTP':'ACTOR_SESSION','ACTOR_ROLE_HTTP':'ACTOR_ROLE','BROWSER_ROLE_DENIED':'BROWSER_ROLE',
 'CLOCK_TARGET_GUARD':'TEACHING_CLOCK','CLOCK_DISPOSABLE_GUARD':'TEACHING_CLOCK','CLOCK_FORMAT_GUARD':'TEACHING_CLOCK',
 'CLOCK_INSTANT_GUARD':'TEACHING_CLOCK','CLOCK_WINDOW_GUARD':'TEACHING_CLOCK','INVALID_TIME_VALUE':'UNKNOWN'}
REPORTED_HINTS={'REPORTED_CLASS_ERROR','REPORTED_CLASS_TYPE_ERROR','REPORTED_CLASS_RANGE_ERROR',
 'REPORTED_CLASS_REFERENCE_ERROR','REPORTED_CLASS_SYNTAX_ERROR','REPORTED_CLASS_TIMEOUT_ERROR',
 'REPORTED_FRAME_DATASET_CALL','REPORTED_FRAME_DATASET_DATE','REPORTED_FRAME_DATASET_INITIALIZE'}
HTTP={0,400,401,403,404,409,410,422,423,429,500,502,503,504}
HTTP_CAUSES={'ACTOR_LOGIN_HTTP','ACTOR_SESSION_HTTP','ACTOR_ROLE_HTTP'}
CODES={14:'STUDENT',61:'HEADMAN',142:'HELPER'}
class SchemaBlocked(ValueError):
    def __init__(self,code):self.code=code;super().__init__('CLOSED_SCHEMA_REJECTION')
def need(value,code):
    if not value:raise SchemaBlocked(code)
def integer(value,cap=1000000):return type(value) is int and 0<=value<=cap
def decode(data):
    def pairs(items):
        result={}
        for key,value in items:
            need(key not in result,'DUPLICATE_JSON_KEY');result[key]=value
        return result
    try:return json.loads(data,object_pairs_hook=pairs,parse_constant=lambda _:(_ for _ in ()).throw(SchemaBlocked('NONFINITE_JSON_NUMBER')))
    except SchemaBlocked:raise
    except (ValueError,UnicodeDecodeError):raise SchemaBlocked('INVALID_JSON_ENCODING_OR_SYNTAX') from None
def validate_diagnostics(value,error_count):
    need(type(value) is list and len(value)<=8,'DIAGNOSTIC_ARRAY_BUDGET')
    need(not error_count or len(value)>0,'DIAGNOSTIC_ERROR_FALLBACK_MISSING')
    seen=set();reported_categories=set()
    for row in value:
        need(type(row) is dict and set(row)=={'stage','cause','httpStatus'},'DIAGNOSTIC_EXACT_KEYS')
        cause=row['cause'];status=row['httpStatus']
        need(type(cause) is str and (cause in STAGES or cause in REPORTED_HINTS),'DIAGNOSTIC_CAUSE_UNKNOWN')
        need(type(row['stage']) is str and row['stage']==STAGES.get(cause,'UNKNOWN'),'DIAGNOSTIC_STAGE_MISMATCH')
        need(type(status) is int and status in HTTP,'DIAGNOSTIC_HTTP_STATUS')
        need((cause in HTTP_CAUSES)==(status!=0),'DIAGNOSTIC_HTTP_CAUSE_PAIR')
        key=(cause,status);need(key not in seen,'DIAGNOSTIC_DUPLICATE');seen.add(key)
        if cause in REPORTED_HINTS:
            category='CLASS' if cause.startswith('REPORTED_CLASS_') else 'FRAME'
            need(category not in reported_categories,'DIAGNOSTIC_REPORTED_CATEGORY_DUPLICATE');reported_categories.add(category)
    return value
def validate_results(value,source_files):
    need(type(value) is dict and set(value)=={'status','runnerErrors','unsafeArtifact','suppressedStdoutBytes','suppressedStderrBytes','results'},'RESULT_EXACT_KEYS')
    need(value['status'] in {'passed','failed','timedout','interrupted'},'RESULT_STATUS')
    need(type(value['unsafeArtifact']) is bool,'RESULT_CAPTURE_BOOLEAN')
    need(all(integer(value[k],2**53-1) for k in ['runnerErrors','suppressedStdoutBytes','suppressedStderrBytes']),'RESULT_COUNTS')
    need(type(value['results']) is list and len(value['results'])<=3,'RESULT_CASE_BUDGET')
    seen=set();qualification=[]
    keys={'file','line','project','status','expectedStatus','durationMs','errorCount','errorLocations'}
    for row in value['results']:
        need(type(row) is dict and set(row) in (keys,keys|{'diagnostics'}),'CASE_EXACT_OPTIONAL_DIAGNOSTIC_KEYS')
        need(row['file']=='mobile-real-server.spec.ts' and type(row['line']) is int and row['line'] in CODES and row['project']=='chromium','CASE_SOURCE_JOIN')
        need(row['line'] not in seen,'CASE_DUPLICATE');seen.add(row['line'])
        need(row['status'] in {'passed','failed','timedOut','skipped','interrupted'} and row['expectedStatus']=='passed','CASE_STATUS')
        need(integer(row['errorCount']),'CASE_ERROR_COUNT')
        need(type(row['durationMs']) in (int,float) and 0<=row['durationMs']<=1800000,'CASE_DURATION')
        need(type(row['errorLocations']) is list and len(row['errorLocations'])<=row['errorCount'],'CASE_FRAME_BUDGET')
        for frame in row['errorLocations']:
            need(type(frame) is dict and set(frame)=={'file','line'} and frame['file'] in source_files and integer(frame['line']) and frame['line']>0,'CASE_SOURCE_FRAME')
        if 'diagnostics' in row:
            validate_diagnostics(row['diagnostics'],row['errorCount']);qualification.append({'caseCode':CODES[row['line']],'schema':'CANONICAL_CLOSED_CORE_AND_UNTRUSTED_REPORTED_HINTS'})
        else:qualification.append({'caseCode':CODES[row['line']],'schema':'LEGACY_DIAGNOSTICS_ABSENT_NOT_CAPTURED'})
    return qualification
def load_binding(directory):
    path=Path(directory)/'run-binding.actual.safe.json'
    need(path.exists(),'CURRENT_PUSH_BINDING_NOT_AVAILABLE')
    value=decode(path.read_bytes())
    need(type(value) is dict and set(value)=={'state','producer','event','run_attempt','runs','schema_source_sha','reporter_sha256','privacy_sha256'},'CURRENT_BINDING_SCHEMA')
    need(value['state']=='ROOT_ACTUAL_PUSH_AND_CURRENT_METADATA_VERIFIED' and value['event']=='pull_request' and value['run_attempt']==1,'CURRENT_METADATA_NOT_AUTHORIZED')
    need(type(value['producer']) is str and re.fullmatch('[0-9a-f]{40}',value['producer']) is not None,'CURRENT_PRODUCER_ID')
    need(re.fullmatch('[0-9a-f]{40}',SOURCE_HEAD) is not None and re.fullmatch('[0-9a-f]{64}',REPORTER_SHA) is not None and re.fullmatch('[0-9a-f]{64}',PRIVACY_SHA) is not None,'CURRENT_SCHEMA_SOURCE_UNBOUND')
    need(value['schema_source_sha']==SOURCE_HEAD and value['reporter_sha256']==REPORTER_SHA and value['privacy_sha256']==PRIVACY_SHA,'CURRENT_SCHEMA_SOURCE_CHANGED')
    need(type(value['runs']) is dict and set(value['runs'])=={'ci','coverage','security','openapi'} and all(type(x) is int and x>0 for x in value['runs'].values()) and len(set(value['runs'].values()))==4,'CURRENT_RUN_IDS')
    return value
