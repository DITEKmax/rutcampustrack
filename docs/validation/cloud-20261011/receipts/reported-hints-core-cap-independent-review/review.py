from pathlib import Path
import ast,hashlib,json,os,zipfile
P=Path(__file__).parent
A=Path('/workspace/scratch/rct-validation/client_real_acceptance/ci-reported-hints-48124')
def sha(p):return hashlib.sha256(p.read_bytes()).hexdigest()
def segment(s,marker):
 start=s.index(marker);position=s.index('{',start);depth=1;i=position+1
 while depth:
  if s[i]=='{':depth+=1
  elif s[i]=='}':depth-=1
  i+=1
 return s[start:i]
before={n:(A/'before'/n).read_text() for n in ['safe-reporter.ts','artifact-privacy.ts']}
after={n:(A/'after'/n).read_text() for n in before}
checks={}
for name in ['failureStages','failureHttpStatuses','knownFailureMessages']:
 if name=='failureHttpStatuses':
  def block(s):return s[s.index('const failureHttpStatuses'):s.index('type FailureHttpStatus')]
 else:
  def block(s,n=name):return segment(s,'const '+n)
 checks[name+'_byte_exact']=block(before['safe-reporter.ts'])==block(after['safe-reporter.ts'])
for name,file in [('classifySafeFailure','safe-reporter.ts'),('requirePrivateArtifacts','artifact-privacy.ts'),('recordSafeFailure','artifact-privacy.ts')]:
 marker=('export ' if name!='recordSafeFailure' else '')+'function '+name
 checks[name+'_byte_exact']=segment(before[file],marker)==segment(after[file],marker)
wipe='    const record = error as Record<string, unknown>;'
checks['sanitizer_wipe_tail_byte_exact']=before['artifact-privacy.ts'][before['artifact-privacy.ts'].index(wipe):]==after['artifact-privacy.ts'][after['artifact-privacy.ts'].index(wipe):]
row='interface SafeResult {'
checks['result_fields_reporter_stdio_attachment_tail_byte_exact']=before['safe-reporter.ts'][before['safe-reporter.ts'].index(row):]==after['safe-reporter.ts'][after['safe-reporter.ts'].index(row):]
assert all(checks.values())
manifest=json.loads((A/'manifest.safe.json').read_text())
items=manifest.get('files',[])
for item in items:
 p=A/item['path'];assert p.stat().st_size==item['bytes'] and sha(p)==item['sha256']
with zipfile.ZipFile(A/'source.safe.zip') as z:assert z.testzip() is None;zip_members=len(z.infolist())
counterexample={'execution':'functions V8 pure RAM on selected actual TS functions; only type annotations/as assertions erased, injected public static path adapter; no Node/helper/browser/API runtime','before':{'total':8,'core':8,'hints':0,'core_statuses':[401,403,404,409,410,422,423]},'after':{'total':8,'core':6,'hints':2,'core_statuses':[401,403,404,409,410]},'private_values_read':False,'full_producer_native':'NOT_RUN'}
result={'verdict':'CHANGES_REQUESTED','source_ready_sha256':sha(A/'READY.safe.json'),'author_manifest_sha256':sha(A/'manifest.safe.json'),'author_zip_sha256':sha(A/'source.safe.zip'),'zip_members':zip_members,'zip_crc_errors':0,'source2':{n:{'before':sha(A/'before'/n),'after':sha(A/'after'/n)} for n in before},'independent_preservation_checks':checks,'counterexample':counterexample,
 'findings':[{'id':'HINTS-CORE-CAP-EVICTION','priority':2,'source':'after/artifact-privacy.ts','lines':[50,69,74,87],'effect':'Early untrusted class/frame hints occupy the same max8 decoder result used by unchanged recordSafeFailure; later known core diagnoses are rejected when previous.length reaches8. Baseline preserves UNKNOWN+7core statuses while candidate preserves UNKNOWN+5core+2hints.','required':'Preserve original core priority under early/late/interleaved and repeated errors; reported hints may use only remaining slots in final total8. Add meaningful reverse-order/interleaving sensitivity controls, preserving original bounded privacy/32scan/core dedup.','control_gap':'EXISTING_MAX_EIGHT_NO_HINT_EVICTION preloads eight cores before hints and misses hints-before-late-core ordering.'}],
 'other_source_findings':[],
 'privacy_source_qualification':['Own descriptor-only message/stack reads; no name/prototype/getter/conversion read; message<=512 UTF16/singleline/control-free; stack<=8192 UTF16/16lines/eachframe<=1024/exact public symbol and canonical path grammar; no raw path/line/value/name/error/body/hash exported.','At most one standard class and one frame are exported by decoder; all nine reported enums force UNKNOWN/http0. Forged exact text remains untrusted; reported frame is not an authenticated source location or operation proof.','Original15 cause classifier/14statuses/privateguard/core recorder/wipe/result fields/reporterstdioattachments byte exact; byte equality alone does not preserve core capacity semantics after shared decoder change.','No actual FIRST3/D002 cause inferred from source or clock BEGINEND. Root strict collector must separately allow only exact9 reported enums at UNKNOWN/0 before integration/current collection.'],
 'planned_controls_qualification':['Old actual pinned serializer adapter requires exactly two class/frame-delivery failures among four predicates, parser0/native1; candidate23 predicates plus genuine browser-free3 expected-failed workers/protected producer privacy/formatter/types planned but NOT_RUN.','Current controls strongly target untrusted data/privacy/serialization but need cap-order regression above. Marker absence/error counts/statuses/bodies-not-started and closed rows saved before cleanup are meaningful native requirements.'],
 'caller_source_qualification':['Same pinned b78/status/observe derivative; explicit oldRED causal predicate(native1+normalterminal+settled/reaped/no supervision error/no signals), newall23/native0/worker3/privacy+format/types gates. Seven sequential phases180 with overall900+60;512MiBmanagedheap/1GiB RSS32MiB cache are sampled soft limits.','Source-only review does not grant Node/browser/API or actual capacity. Existing fixture private guards/current source ownership must remain intact; only explicit fresh grant permits own2 formatter writes.'],
 'private_native_errors_logs_grants_credentials_read':False,'native_product_acceptance':'NOT_RUN'}
os.umask(0o077)
with (P/'review.safe.json').open('x') as f:json.dump(result,f,indent=2);f.write('\n')
with (P/'REPORT.md').open('x') as f:f.write('CHANGES_REQUESTED — HINTS-CORE-CAP-EVICTION (P2). Early reported class/frame hints consume the shared cap8 used by core recorder. Selected actual source logic in RAM preserves8 baseline cores but only6 candidate cores+2hints; known statuses422/423 disappear. Existing cap control only tests core-before-hints. Preserve core priority for reverse/interleaved/repeated order and add a causal control.\n\nRemaining finite privacy delta has no additional concrete blocker: reported enums UNKNOWN/0, bounded own message/stack, exact public grammar and unchanged guards/wipe/reporter fields. Full producer/native controls NOT_RUN; no Node/API/helper/private data read. Original15 byte preservation does not prove capacity semantics after decoder changes. Source/runtime cause remains UNKNOWN.\n')
files=[p for p in P.iterdir() if p.is_file()]
with (P/'manifest.safe.json').open('x') as f:json.dump({'verdict':'CHANGES_REQUESTED','files':[{'path':p.name,'bytes':p.stat().st_size,'sha256':sha(p)} for p in sorted(files)]},f,indent=2);f.write('\n')
with zipfile.ZipFile(P/'safe-source-review.zip','x',zipfile.ZIP_DEFLATED) as z:
 for p in sorted(files)+[P/'manifest.safe.json']:z.write(p,p.name)
print(json.dumps({'verdict':'CHANGES_REQUESTED','manifest_sha256':sha(P/'manifest.safe.json'),'zip_sha256':sha(P/'safe-source-review.zip'),'zip_bytes':(P/'safe-source-review.zip').stat().st_size,'preservation_checks':len(checks)}))
