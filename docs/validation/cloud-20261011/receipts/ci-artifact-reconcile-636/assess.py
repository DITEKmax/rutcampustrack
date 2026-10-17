import collections
import csv
import hashlib
import json
import pathlib
import subprocess

ROOT = pathlib.Path('/workspace/rutcampustrack')
OUT = pathlib.Path(__file__).parent
HEAD = '63647fc9b63175e1e32b0773d57affc637ea1065'
CURRENT = 'eec3e0a864626160d33ae29da2cf3b0640e30caa'
MERGE = 'a004234b5e8dcc3659f6211eaa2c2aa323e6ab83'
CASEID = 'CASE-DATA-L05-PAGE-CURRENT-DEPENDENCY'
METHOD = 'legalPageSizesPreserveExactOwnRowsTypesAssignmentsAndTotals'
DESTINATION = 'receipts/ci-artifact-reconcile-636'

def write(name, value):
    (OUT / name).write_text(json.dumps(value, indent=2) + '\n')

def sha(data):
    return hashlib.sha256(data).hexdigest()

def git_bytes(revision,path):
    r=subprocess.run(['git','show',revision+':'+path],cwd=ROOT,capture_output=True,check=True)
    return r.stdout

allcases=json.loads((OUT/'all-safe-cases.json').read_text())
selected=[c for c in allcases if c.get('method')==METHOD]
assert len(selected)==1
case=selected[0]
assert case['status']=='PASSED' and case['identity']=='SOURCE_METHOD'
assert case['artifactId']==11719102777
assert case['metadata']['checkoutSha']==MERGE
original_test=(OUT/'source-SubjectListDataIT-actual-checkout.java').read_bytes()
assert sha(original_test)==case['sourceSha256']
source_bridges=[]
paths=subprocess.run(['git','ls-files','-z','--','services/academic-service/academic-app/src/main/java','services/academic-service/academic-api','services/academic-service/academic-app/src/test/java/ru/rutcampustrack/academic/integration','services/academic-service/academic-app/src/test/java/ru/rutcampustrack/academic/subject/SubjectListDataIT.java','services/shared/shared-security','services/shared/shared-web','scripts/collect-ci-test-outcomes.py','.github/workflows/ci.yml'],cwd=ROOT,capture_output=True,check=True).stdout.decode().split('\0')
for path in paths:
    if not path or not path.endswith(('.java','.py','.yml','.kts')):
        continue
    before=git_bytes(HEAD,path)
    now=git_bytes(CURRENT,path)
    work=(ROOT/path).read_bytes()
    source_bridges.append({'path':path,'producerSha256':sha(before),'currentSha256':sha(now),'workingSha256':sha(work),'byteIdentical':before==now==work})
write('l05-source-bridge.json',{'testedHead':HEAD,'actualCImerge':MERGE,'reviewedCurrent':CURRENT,'mergeVsHeadTree':'Native compare ahead_by1, behind_by0, files[]; tested merge full tree identical to head636','nativeTestSourceSha256':sha(original_test),'paths':source_bridges,'changedPaths':[b['path'] for b in source_bridges if not b['byteIdentical']],'currentRuntime':'NOT_RERUN; producer636 reused for exact unchanged L05 subject/assignment path. Current global startup/educational-clock changes remain separate validation.','clockQualification':'ClockConfig adds EducationalTime bean without changing Clock bean; existing SubjectService/Assignment services do not use EducationalTime. StudentProjectionScopeService changed separately. Neither new behavior nor current application startup is proven by this prior CI.'})
with (ROOT/'docs/validation/cloud-20261011/verified-cases.csv').open() as f:
    reader=csv.DictReader(f);fields=reader.fieldnames;rows=list(reader)
old=next(r for r in rows if r['id']==CASEID)
new=dict(old)
new.update(status='PASS',revision=HEAD,client='Academic in-process MockMvc HTTP + actual disposable PostgreSQL/JPA; controlled Schedule receipt seam',action='Execute exact source legalPageSizesPreserveExactOwnRowsTypesAssignmentsAndTotals through CI integrationTest; page sizes1/10/50 with59 requests',verification='Reused exact CI38184430139 integrationTest receipt; job114603624669 success; native safe artifact11719102777, suite55/1 case1 PASSED, declared2/observed2/no skips; native actual merge tree equals636; concrete source assertions independently opened. No test rerun.',decision='DLRV-DEP-01 verification dependency satisfied for exact source test',blocking_scope='Parent families/boundary remain UNKNOWN; only exact L05 current-source method dependency closed',limits='Actual executed producer636/mergea004; source test33a92858 exact at636/eec and direct subject/assignment dependencies unchanged. ClockConfig/StudentProjection/shared EducationalTime changes qualify reuse: no current global runtime claim. Signed random-session JWT fixture does not prove live Auth/durable selected session. Schedule/Rabbit controlled; no latency SLA/query-count receipt or full-family claim.')
evidence={'artifact_id':case['artifactId'],'artifact_archive':DESTINATION+'/immutable-artifacts/11719102777.zip','artifact_sha256':'8ff450fb0a1370dea997d92db27e0a4fa3863a8dfb496eba492810bc52dfa14f','workflow_run_id':38184430139,'workflow_url':'https://github.com/DITEKmax/rutcampustrack/actions/runs/38184430139','workflow_job_id':114603624669,'event':'pull_request','producer_head_sha':HEAD,'actual_checkout_merge_sha':MERGE,'test_source':case['source'],'test_source_sha256':case['sourceSha256'],'case':{'xml_ordinal':55,'suite_ordinal':1,'case_ordinal':1,'method':METHOD,'status':'PASSED','original_xml_sha256':case['originalXmlSha256']},'safe_case_receipt':DESTINATION+'/l05-reconciliation.json','source_bridge':DESTINATION+'/l05-source-bridge.json','native_merge_bridge':DESTINATION+'/native-merge-bridge.json','original_xml_in_archive':False}
new['evidence']=json.dumps(evidence,ensure_ascii=False)
with (OUT/'proposed-verified-cases.csv').open('w',newline='') as f:
    writer=csv.DictWriter(f,fieldnames=fields);writer.writeheader();writer.writerow(new)
write('l05-reconciliation.json',{'caseId':CASEID,'previousStatus':'UNKNOWN','proposedStatus':'PASS','dependency':'DLRV-DEP-01','dependencyDisposition':'SATISFIED producer636 exact source; targeted rerun unnecessary for this unchanged method','requiredObservableOracle':'51 own subjects and foreign same-name control; each page size1/10/50 preserves totalElements51, exact totalPages/size; all59 pages cumulative IDs equal own ordered IDs; each row own group and LECTURE/PRACTICE; assigned row exact teacherId/assignmentId; unassigned rows no teachers/assignments.','sourceAssertionLines':[62,65,69,76,79,95,96,98,102,103,105,106,107,108,110,111,115],'runtimeLevel':'MockMvc/controller/domain/JPA with real PostgreSQL, mocked Schedule receipts and Rabbit; synthetic signed token authority seam','receipt':case,'evidence':evidence,'exactSourceCurrent':True,'wholeFamilyPassPromotions':0,'wholeBoundaryPassPromotions':0,'remainingOracles':['Auth live selected-session/grants/revocation admission','Real Schedule/Rabbit/provider paths','Other accepted subject registry roles/filter/sort/unknown-input/history and export/mobile paths','Global current runtime and changed educational clock behavior'],'historicalReceiptPolicy':'Initial1b058 missing exact source is preserved and not relabeled. New current-source CI33a928 independently closes original specific dependency.'})
totals=collections.Counter(c['status'] for c in allcases)
write('verdict.json',{'state':'BOUNDED_RECONCILIATION_COMPLETE','producerHead':HEAD,'actualCIcheckout':MERGE,'currentSourceReviewed':CURRENT,'nativeArchives':12,'nativeArchivesTotalBytes':sum(p.stat().st_size for p in (OUT/'immutable-artifacts').glob('*.zip')),'caseOutcomes':dict(totals),'namedSourceMethods':sum(c['identity']=='SOURCE_METHOD' for c in allcases),'ordinalOnly':sum(c['identity']=='ORDINAL_ONLY' for c in allcases),'uniqueIdentifiedTestSources':len(json.loads((OUT/'test-source-identity.json').read_text())),'proposedCaseUpdates':[{'id':CASEID,'from':'UNKNOWN','to':'PASS','producerRevision':HEAD}],'unmappedResults':'Catalogue only; no source/oracle acceptance for uninspected tests. Gateway3 skips remain no-PASS; ordinal-only cases cannot be mapped to named methods.','currentRuntime':'NOT_RUN','fullAcceptance':'UNKNOWN','suiteReruns':0,'wholeFamilyPassPromotions':0,'wholeBoundaryPassPromotions':0,'trackedWrites':0,'parentMatrixWrites':0,'transportBlockers':[]})
print(json.dumps({'case':CASEID,'proposed':'PASS','xmlSha256':case['originalXmlSha256'],'sourceBridgePaths':len(source_bridges),'changed':[b['path'] for b in source_bridges if not b['byteIdentical']],'totals':dict(totals)}))
