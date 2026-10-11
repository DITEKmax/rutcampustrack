import csv
import hashlib
import json
import re
from collections import Counter
from pathlib import Path

REPO = Path('/workspace/rutcampustrack')
OUT = Path('/workspace/scratch/rct-validation/scenario_sources')
SHA = '3908fad9624f97aec8891fc79338ceb6b0616bf3'

def read(path):
    return (REPO / path).read_text(encoding='utf-8-sig')

def dataset(path):
    return json.loads(read(path))

def dump(name, data):
    (OUT / name).write_text(json.dumps(data, ensure_ascii=False, indent=2) + '\n', encoding='utf-8')

stories = dataset('docs/product/job-stories.yaml')
requests = dataset('docs/implementation/backend-delta.yaml')
mapping = dataset('docs/implementation/story-backend-map.yaml')
story_by_id = {x['id']: x for x in stories['stories']}
request_by_id = {x['request_id']: x for x in requests['requests']}
canonical = stories['canonical_source']['copied_path']
canonical_lines = read(canonical).splitlines()

# R decisions supersede source story wording. These associations identify affected
# clauses; they are not newly accepted request/story links or feature counts.
r_story = {
 1:['JS-HEADMAN-23','JS-ADMIN-01','JS-ADMIN-20'],
 2:['JS-STUDENT-02','JS-STUDENT-06','JS-STUDENT-23','JS-HEADMAN-24','JS-HEADMAN-WEB-07'],
 3:['JS-HEADMAN-19','JS-STUDENT-16','JS-TEACHER-07','JS-ADMIN-13'],
 4:['JS-STUDENT-14','JS-HEADMAN-18','JS-SYSTEM-16'],
 5:['JS-STUDENT-24','JS-TEACHER-11'],
 6:['JS-ADMIN-02','JS-ADMIN-19','JS-ADMIN-26','JS-SYSTEM-02','JS-SYSTEM-14'],
 7:['JS-HEADMAN-09','JS-HEADMAN-42','JS-SYSTEM-08'],
 8:['JS-HEADMAN-16','JS-HEADMAN-17','JS-STUDENT-22'],
 9:['JS-STUDENT-27','JS-STUDENT-WEB-07'],
 10:['JS-SYSTEM-13','JS-STUDENT-25'],
 11:['JS-HEADMAN-02','JS-STUDENT-12','JS-STUDENT-06'],
 12:['JS-ADMIN-01'], 13:[],
 14:['JS-HEADMAN-07','JS-HEADMAN-43','JS-HEADMAN-44','JS-TEACHER-12'],
 15:['JS-HEADMAN-12','JS-HEADMAN-13','JS-TEACHER-06','JS-TEACHER-14'],
 16:['JS-HEADMAN-30','JS-HEADMAN-32','JS-TEACHER-03','JS-TEACHER-13'],
 17:['JS-HEADMAN-30','JS-TEACHER-13','JS-STUDENT-07'],
 18:['JS-HEADMAN-14','JS-STUDENT-03','JS-ADMIN-01','JS-HEADMAN-19'],
 19:['JS-HEADMAN-23','JS-ADMIN-20'], 20:['JS-SYSTEM-16','JS-HEADMAN-30'],
 21:['JS-ADMIN-02','JS-ADMIN-19'], 22:['JS-ADMIN-06','JS-ADMIN-07','JS-ADMIN-15'],
 23:['JS-HEADMAN-19','JS-HEADMAN-30','JS-TEACHER-07','JS-ADMIN-13'],
 24:['JS-HEADMAN-30','JS-ADMIN-01'], 25:['JS-HEADMAN-21','JS-HEADMAN-16','JS-SYSTEM-15'],
 26:['JS-HEADMAN-12','JS-HEADMAN-38','JS-TEACHER-01','JS-TEACHER-09','JS-TEACHER-10'],
 27:['JS-TEACHER-10'], 28:['JS-TEACHER-03','JS-TEACHER-06','JS-TEACHER-13','JS-TEACHER-14','JS-SYSTEM-16'],
 29:['JS-ADMIN-22'], 30:['JS-STUDENT-24','JS-TEACHER-11'], 31:['JS-ADMIN-11'],
}
decision_links = {
 '2026-09-20-retrospective-attendance.md':['JS-HEADMAN-03','JS-HEADMAN-08','JS-SYSTEM-05','JS-SYSTEM-07'],
 '2026-09-23-admin-active-dashboard-counts.md':['JS-ADMIN-11'],
 '2026-09-23-teacher-replacement-and-group-attendance.md':['JS-HEADMAN-12','JS-HEADMAN-38','JS-TEACHER-01','JS-TEACHER-03','JS-TEACHER-06','JS-TEACHER-14'],
 '2026-09-24-assistant-report-export.md':['JS-HEADMAN-14','JS-HEADMAN-18','JS-HEADMAN-19'],
 '2026-09-25-headman-student-stats-detail.md':['JS-HEADMAN-18','JS-HEADMAN-33'],
 '2026-09-25-notification-history-membership.md':['JS-STUDENT-WEB-06','JS-SYSTEM-09','JS-SYSTEM-10','JS-ADMIN-03'],
 '2026-09-25-semester-academic-year-name.md':['JS-ADMIN-08'],
 '2026-09-27-group-promotion-cycle.md':['JS-ADMIN-16','JS-ADMIN-17'],
 '2026-09-28-restored-lesson-teacher.md':['JS-HEADMAN-42'],
 '2026-09-28-figma-conformance-stage.md':['JS-TEACHER-08'],
 '2026-09-29-semester-archive-write-lock.md':['JS-ADMIN-09','JS-ADMIN-10','JS-HEADMAN-01','JS-HEADMAN-08','JS-HEADMAN-16','JS-HEADMAN-43'],
 '2026-10-01-group-roster-export.md':['JS-HEADMAN-32'],
 '2026-10-02-user-restore-without-rights.md':['JS-ADMIN-04'],
 '2026-10-02-unbound-homework-archive.md':['JS-HEADMAN-16','JS-HEADMAN-17','JS-HEADMAN-28','JS-HEADMAN-29'],
 '2026-10-02-qr-login-backend-now.md':['JS-SYSTEM-13'],
 '2026-10-02-offsite-backup-deferred-to-deploy.md':[],
 '2026-10-02-notification-preferences-authority.md':['JS-SYSTEM-09','JS-SYSTEM-10','JS-SYSTEM-11'],
 '2026-10-02-map-scheme-final-delete.md':['JS-ADMIN-23','JS-ADMIN-24','JS-ADMIN-25'],
 '2026-10-02-durable-notification-preferences.md':['JS-SYSTEM-09','JS-SYSTEM-10','JS-SYSTEM-11'],
 '2026-10-04-canonical-lesson-times.md':['JS-HEADMAN-08','JS-HEADMAN-21','JS-HEADMAN-39','JS-HEADMAN-41','JS-SYSTEM-05'],
 '2026-10-06-teacher-removal-without-replacement.md':['JS-HEADMAN-12','JS-HEADMAN-38','JS-TEACHER-03'],
 '2026-10-08-admin-activity-action-time.md':['JS-ADMIN-22'],
 '2026-10-08-limited-administrators.md':['JS-ADMIN-01','JS-ADMIN-02','JS-ADMIN-04','JS-ADMIN-06','JS-ADMIN-10','JS-ADMIN-18','JS-ADMIN-19','JS-ADMIN-20','JS-ADMIN-26'],
 '2026-10-09-archived-semester-date-extension.md':['JS-ADMIN-08','JS-ADMIN-10','JS-ADMIN-16','JS-ADMIN-17'],
 '2026-10-11-pwa-offline-read-only-session.md':['JS-STUDENT-10','JS-STUDENT-20','JS-STUDENT-25'],
 '2026-10-11-tma-init-data-reuse.md':['JS-SYSTEM-02','JS-SYSTEM-13','JS-STUDENT-08'],
 '2026-10-11-map-legend-deferred.md':['JS-STUDENT-24','JS-TEACHER-11'],
}
overlays=[]
conflict_path='docs/architecture/reference-rutcampustrack-design/backend-conflicts.md'
conflicts=read(conflict_path)
matches=list(re.finditer(r'^### Р-(\d+)\.\s*(.+)$',conflicts,re.M))
for index, match in enumerate(matches):
    n=int(match.group(1)); end=matches[index+1].start() if index+1<len(matches) else conflicts.index('\n## 3.',match.start())
    text=conflicts[match.start():end].strip()
    overlays.append({'id':f'R-{n:02d}','kind':'owner-source-decision','source':conflict_path,
                     'line':conflicts[:match.start()].count('\n')+1,'title':match.group(2),'text':text,
                     'affected_story_ids':r_story[n],'association_kind':'reviewed affected clause, not feature equivalence',
                     'status':'UNKNOWN','runtime_evidence':[],'revision':SHA})
for name, start_heading, end_heading, ids in [
    ('ATTENDANCE-PRIORITY-WINDOW','## 3. Механика','## 4.', ['JS-STUDENT-01','JS-STUDENT-02','JS-STUDENT-06','JS-STUDENT-23','JS-HEADMAN-01','JS-HEADMAN-03','JS-HEADMAN-24','JS-HEADMAN-44','JS-SYSTEM-07']),
    ('REQUEST-DELTAS','## 4. Дельты','## 5.', ['JS-STUDENT-06','JS-HEADMAN-19','JS-HEADMAN-24','JS-SYSTEM-16']),
]:
    begin=conflicts.index(start_heading); end=conflicts.index(end_heading,begin)
    overlays.append({'id':'OWNER-'+name,'kind':'owner-source-combined-rules','source':conflict_path,
                     'line':conflicts[:begin].count('\n')+1,'title':start_heading,
                     'text':conflicts[begin:end].strip(),'affected_story_ids':ids,
                     'association_kind':'direct combined accepted rules, not new feature count',
                     'status':'UNKNOWN','runtime_evidence':[],'revision':SHA})
for path in sorted((REPO/'docs/product/decisions').glob('*.md')):
    if path.name=='2026-09-20-delivery-policy.md': continue
    overlays.append({'id':'DEC-'+path.stem,'kind':'dated-owner-decision','source':str(path.relative_to(REPO)),
                     'line':1,'title':path.read_text(encoding='utf-8-sig').splitlines()[0],
                     'text':path.read_text(encoding='utf-8-sig'),'affected_story_ids':decision_links.get(path.name,[]),
                     'association_kind':'reviewed affected clause, not feature equivalence',
                     'status':'UNKNOWN','runtime_evidence':[],'revision':SHA})
for overlay in overlays:
    if overlay['id']=='DEC-2026-10-08-limited-administrators':
        overlay['affected_story_ids']=[x['id'] for x in stories['stories'] if x['role']=='admin']
overlays.append({'id':'SOURCE-LOGIN-IDENTITY','kind':'latest-canonical-source-overrides-earlier-clause',
                 'source':canonical,'line':167,'title':'Role-independent single account login supersedes role-prefixed JS-SYSTEM-01',
                 'text':story_by_id['JS-ADMIN-01']['current_accepted_text'],
                 'affected_story_ids':['JS-ADMIN-01','JS-SYSTEM-01'],
                 'association_kind':'direct later source revision overrides role-prefixed login only',
                 'status':'UNKNOWN','runtime_evidence':[],'revision':SHA})

admin_grants = {
 '01':['USERS_MANAGE','USERS_VIEW','FULL when creating ADMIN'],
 '02':['USERS_MANAGE','USERS_VIEW','FULL when changing ADMIN'],
 '03':['USERS_MANAGE','USERS_VIEW'], '04':['USERS_ARCHIVE','USERS_VIEW'],
 '05':['GROUPS_MANAGE','GROUPS_VIEW'], '06':['GROUPS_MANAGE','GROUPS_VIEW'],
 '07':['GROUPS_MANAGE','GROUPS_VIEW'], '08':['SEMESTERS_MANAGE','SEMESTERS_VIEW'],
 '09':['SEMESTERS_MANAGE','SEMESTERS_VIEW'],
 '10':['SEMESTERS_MANAGE','SEMESTERS_VIEW','DATA_DELETE','operation-bound password confirmation'],
 '11':['DASHBOARD_VIEW'], '12':['DASHBOARD_VIEW; exact accepted report route still requires selection'],
 '13':['USERS_VIEW or GROUPS_VIEW for list export; attendance report route unresolved'],
 '14':['external operation only; no in-product ADMIN credential recovery'],
 '15':['GROUPS_VIEW'], '16':['GROUPS_MANAGE','GROUPS_VIEW'], '17':['GROUPS_MANAGE','GROUPS_VIEW'],
 '18':['permission for owning domain + DATA_DELETE for final delete; previews must not bypass domain authority'],
 '19':['USERS_MANAGE','USERS_VIEW','FULL for ADMIN grants'],
 '20':['USER_CREDENTIALS_MANAGE','USERS_VIEW','LIMITED cannot target active ADMIN'],
 '21':['USERS_MANAGE','USERS_VIEW'], '22':['DASHBOARD_VIEW'],
 '23':['CAMPUS_MAP_MANAGE'], '24':['CAMPUS_MAP_MANAGE'], '25':['CAMPUS_MAP_MANAGE'],
 '26':['selected active ADMIN + current FULL/LIMITED authority; other role operations denied until switch'],
}
helper_grants={
 'MARK_ATTENDANCE':['JS-HEADMAN-01','JS-HEADMAN-06','JS-HEADMAN-WEB-05'],
 'MANAGE_EXCUSES':['JS-HEADMAN-04','JS-HEADMAN-05','JS-HEADMAN-07','JS-HEADMAN-43','JS-HEADMAN-44','JS-HEADMAN-WEB-06','JS-HEADMAN-WEB-07'],
 'MANAGE_HOMEWORK':['JS-HEADMAN-16','JS-HEADMAN-17','JS-HEADMAN-27','JS-HEADMAN-28','JS-HEADMAN-29','JS-HEADMAN-WEB-09'],
 'CANCEL_LESSONS':['JS-HEADMAN-09','JS-HEADMAN-41','JS-HEADMAN-42'],
 'VIEW_STATS':['JS-HEADMAN-18','JS-HEADMAN-19','JS-HEADMAN-30','JS-HEADMAN-31','JS-HEADMAN-33','JS-HEADMAN-WEB-08'],
}
grant_source={
 'admin':'services/shared/shared-security/src/main/java/ru/rutcampustrack/shared/security/admin/AdminPermission.java',
 'assistant':'services/academic-service/academic-api-contract/src/main/java/ru/rutcampustrack/academic/contract/enums/AssistantPermission.java',
 'assistant_authority':'services/academic-service/academic-app/src/main/java/ru/rutcampustrack/academic/assistant/AssistantPermissionAuthority.java',
}

def role_variants(st):
    role=st['role']; sid=st['id']
    if role=='admin':
        return [{'role':'ADMIN','mode':'FULL','grants':['current active selected ADMIN','live authority']},
                {'role':'ADMIN','mode':'LIMITED','grants':admin_grants[sid.rsplit('-',1)[1]],
                 'negative':'missing grant / revoked grant / self escalation / active ADMIN target must be rejected'}]
    if role=='headman':
        vals=[{'role':'HEADMAN','grants':['active selected HEADMAN','durable current headman of target group']}]
        for grant, ids in helper_grants.items():
            if sid in ids:
                vals.append({'role':'STUDENT helper','grants':['active student grant in same group','current active helper',grant],
                             'negative':'foreign group or revoked helper/grant denied',
                             'source_scope':'runtime route grant requires verification; schedule add/restore delegated scope not inferred as new product policy'})
        return vals
    if role=='student': return [{'role':'STUDENT','grants':['active selected STUDENT','own user/group','current durable authority']},
                                 {'role':'terminal STUDENT','grants':['read-only only where permitted by R-06'],'negative':'all mutations denied'}]
    if role=='teacher': return [{'role':'TEACHER','grants':['active selected TEACHER','current active group assignment for attendance read'],
                                'negative':'no attendance writes or ticket decisions; no credentials/foreign-group data; ticket detail remains subject-filtered'}]
    return [{'role':'SYSTEM / user of selected role','grants':['service identity purpose/audience and live end-user authority where applicable'],
             'negative':'forged identity; revoked session; historical authority does not confer current rights'}]

def clauses(text):
    return [line.strip() for line in text.splitlines() if line.strip() not in ('','>')]

def action_parts(text, st):
    main=text.split('\n')[0]
    when=re.search(r'Когда (.*?)(?:, я|, система|, восстановление)',main)
    want=re.search(r'(?:я хочу|я \(администратор\) хочу|я \(.*?\) хочу) (.*?)(?:, чтобы|$)',main)
    result=re.search(r'чтобы (.*)',main)
    return ((when.group(1) if when else 'Source prestate: '+main),
            want.group(1) if want else '; '.join(x.get('action','') for x in st.get('commands',[])) or main,
            result.group(1) if result else main)

source_checks=[]
rows=[]
for st in stories['stories']:
    sid=st['id']; occ=st['source_definition_occurrences']; occ=occ if isinstance(occ,list) else [occ]
    refs=[f"{canonical}:{x['line']}" for x in occ if x]
    source_ok=all(sid in canonical_lines[x['line']-1] for x in occ if x)
    source_checks.append({'id':sid,'refs':refs,'stable_id_at_line':source_ok})
    text=st['current_accepted_text'] or st['source_statement_raw'] or ''
    linked=[x for x in mapping['links'] if x['story_id']==sid]
    own_overlays=[x for x in overlays if sid in x['affected_story_ids']]
    prestate,action,result=action_parts(text,st)
    forbidden=[x for x in clauses(text) if re.search(r'не может|нельзя|не выда|не проект|отмен|запр|не использу|не возвращ',x,re.I)]
    rows.append({'id':sid,'kind':'story','revision':SHA,'source_refs':refs,'source_identity_verified':source_ok,
                 'decision_status_in_preparation_registry':st['decision_status'],
                 'status':'EXCLUDED' if st['decision_status']=='cancelled' else 'UNKNOWN',
                 'exclusion_source':refs if st['decision_status']=='cancelled' else [],
                 'excluded_criteria':[{'criterion':'textual icon legend and MAP-06 common icon catalogue only',
                                       'owner_decision':'D004','source':'docs/product/decisions/2026-10-11-map-legend-deferred.md:1',
                                       'status':'EXCLUDED','retained':'access/upload/sanitization/view/download/version/history remain required'}]
                                     if sid in ['JS-STUDENT-24','JS-TEACHER-11'] else [],
                 'role':st['role'],'roles_and_grants':role_variants(st),'grant_source_refs':grant_source,
                 'client':{'source_surface':st['surface'],'scope':'backend semantics + existing PWA/TMA equivalents; new web UI excluded by TASK-CONTRACT section 3',
                           'source_mentions':sorted(set(re.findall(r'PWA|Telegram|Mini App|веб-панел\w*|веб|бот\w*|мобил\w*',text,re.I)))},
                 'prestate':prestate,'action':action,'expected_result':result,'accepted_source_text':text,
                 'accepted_criteria_before_overlay':st['acceptance_criteria'],
                 'required_states_from_registry':st['required_states'],'required_data_from_registry':st['required_data'],
                 'forbidden_cases':forbidden+['foreign scope','missing/revoked grant','terminal read-only mutation','malformed input must not cause unexpected 5xx'],
                 'owner_overlay_ids':[x['id'] for x in own_overlays],
                 'owner_overlay_refs':[f"{x['source']}:{x['line']}" for x in own_overlays],
                 'effective_criteria':{'source_clauses':st['acceptance_criteria'],
                                       'precedence':'later owner decisions override relevant original clauses; R source decisions override preparation registry text',
                                       'owner_rule_overlays':[{'id':x['id'],'source_ref':f"{x['source']}:{x['line']}",'rules':x['text']} for x in own_overlays]},
                 'interpretation':'Use owner overlays before literal story wording; backend findings need current symbols and runtime evidence.',
                 'request_ids_direct_source_associations':[x['request_id'] for x in linked if x['evidence_kind']!='reconciliation-semantic-candidate'],
                 'request_ids_candidate_associations':[x['request_id'] for x in linked if x['evidence_kind']=='reconciliation-semantic-candidate'],
                 'verification':'real API/DB/events plus actual PWA/TMA user path where implemented; positive/negative/boundary/retry/revoke; pinned synthetic data and Europe/Moscow educational clock',
                 'runtime_evidence':[],'runtime_status':'NOT_RUN',
                 'existing_test_evidence':'preparation test paths unassigned; tests must be linked by concrete runtime packet; no PASS inferred'})

# Review of all 62 associations. Source pointer validity and semantic support are
# separate evidence dimensions. Candidates remain candidates after review.
gap_pairs={
 ('AC-05','JS-HEADMAN-12'):'Cross-group identity is not assignment type selection; AC-05 is explicitly retired by R-15.',
 ('AT-25','JS-STUDENT-12'):'Active-ticket status is distinct from headman blockage. Story directly proves blockage only.',
 ('MAP-07','JS-ADMIN-23'):'Uploading a plan does not require structured room catalogue; R-05 defers MAP-07.',
 ('MAP-07','JS-ADMIN-24'):'PNG/SVG completeness does not define structured room catalogue; R-05 defers MAP-07.',
 ('MAP-07','JS-ADMIN-25'):'Replacing a plan is not room catalogue; R-05 defers MAP-07.',
 ('NT-03','JS-SYSTEM-10'):'Direct row association explicitly says these aggregate WS events are absent from story catalogue; transport choice must not become new event acceptance.',
 ('AT-12','JS-HEADMAN-05'):'Request is student personal history; story is headman receive/decide. Source audience differs.',
 ('AT-11','JS-STUDENT-02'):'R-02 automatic geo escalation is unlimited; manual petition limit is separate. Shared limit must not be inferred.',
 ('AC-05','JS-TEACHER-14'):'Story wanted cross-group comparison but R-15 removes one cross-group discipline selection. Preserve per-group subject IDs.',
}
links=[]
for link in mapping['links']:
    item=dict(link); req=request_by_id[link['request_id']]; st=story_by_id[link['story_id']]
    rp=link['request_evidence']; sp=link['story_evidence']; rl=read(rp['source']).splitlines()[int(rp['line'])-1]; sl=read(sp['source']).splitlines()[int(sp['line'])-1]
    pair=(link['request_id'],link['story_id'])
    residual=gap_pairs.get(pair,'Association supports a related clause only; full request semantics/roles/client/errors require concrete contract packet. No candidate is promoted to accepted.')
    links.append({**item,'revision':SHA,'request_id_at_line':link['request_id'] in rl,
                  'story_id_at_line':link['story_id'] in sl,'request_source_fragment':rl,'story_source_fragment':sl,
                  'source_identity_status':'VERIFIED' if link['request_id'] in rl and link['story_id'] in sl else 'MISMATCH',
                  'accepted_link_status':'CANDIDATE' if link['evidence_kind']=='reconciliation-semantic-candidate' else 'EXPLICIT_SOURCE_ASSOCIATION',
                  'semantic_residual_gap':residual})

retired={'AC-05':('R-15','Cross-group discipline identity explicitly removed.'),
         'AC-06':('R-15','Common name suggestion explicitly removed; free manual entry remains.'),
         'MAP-07':('R-05','Room catalogue deferred explicitly; image-only accepted A11Y limitation.'),
         'MAP-06':('DEC-2026-10-11-map-legend-deferred','Icon catalogue and textual legend only explicitly deferred by D004.')}
request_overlay={'AU-01':['DEC-2026-10-02-qr-login-backend-now'],
                 'X-04':['DEC-2026-09-29-semester-archive-write-lock'],
                 'X-07':['R-29','DEC-2026-10-08-admin-activity-action-time'],
                 'X-09':['DEC-2026-10-08-admin-activity-action-time'],
                 'X-10':['R-15','DEC-2026-09-23-teacher-replacement-and-group-attendance'],
                 'MAP-05':['DEC-2026-10-02-map-scheme-final-delete'],
                 'AC-11':['DEC-2026-10-08-limited-administrators'],
                 'MAP-06':['DEC-2026-10-11-map-legend-deferred']}
request_rows=[]
for req in requests['requests']:
    item=dict(req); rid=req['request_id']; src=req['source']; actual=read(src['path']).splitlines()[src['line']-1]
    associated=[x for x in links if x['request_id']==rid]
    request_rows.append({**item,'revision':SHA,'source_row_exact':actual==src['row'],
                         'status':'EXCLUDED' if rid in retired else 'UNKNOWN',
                         'excluded_by':retired.get(rid),
                         'owner_overlay_ids':request_overlay.get(rid,[]),
                         'current_assessment':'Source provenance verified; preparation open/not-started/static symbol claims are not current runtime verdicts.',
                         'verified_source_association_story_ids':[x['story_id'] for x in associated if x['accepted_link_status']=='EXPLICIT_SOURCE_ASSOCIATION'],
                         'candidate_story_ids':[x['story_id'] for x in associated if x['accepted_link_status']=='CANDIDATE'],
                         'runtime_evidence':[]})

source_meta={
 'revision':SHA,'canonical_sha256_observed':hashlib.sha256((REPO/canonical).read_bytes()).hexdigest(),
 'canonical_sha256_expected':stories['canonical_source']['copied_sha256'],
 'story_count':len(rows),'story_roles':dict(Counter(x['role'] for x in rows)),
 'story_verdicts':dict(Counter(x['status'] for x in rows)),
 'request_count':len(request_rows),'request_verdicts':dict(Counter(x['status'] for x in request_rows)),
 'original_request_product_statuses':dict(Counter(x['product_decision_status'] for x in request_rows)),
 'links_count':len(links),'link_kinds':dict(Counter(x['evidence_kind'] for x in links)),
 'link_source_identity_verdicts':dict(Counter(x['source_identity_status'] for x in links)),
 'unique_linked_stories':len({x['story_id'] for x in links}),
 'unique_linked_requests':len({x['request_id'] for x in links}),
 'unlinked_story_ids':[x['id'] for x in rows if not x['request_ids_direct_source_associations'] and not x['request_ids_candidate_associations']],
 'no_link_request_ids':[x['request_id'] for x in request_rows if not x['verified_source_association_story_ids'] and not x['candidate_story_ids']],
 'runtime_tests_performed':False,'feature_count':'NOT_COMPUTED; stories, request associations and overlay clauses overlap',
}
dump('scenarios.json',rows)
dump('requests.json',request_rows)
dump('decision-overlays.json',overlays)
dump('mapping-reconciliation.json',links)
dump('source-validation.json',{'metadata':source_meta,'story_checks':source_checks})
dump('grants-catalog.json',{'revision':SHA,'source_refs':grant_source,'admin_story_grants':admin_grants,
                          'assistant_story_grants':helper_grants,
                          'catalog_sources':[{'path':path,'text':read(path)} for path in grant_source.values()],
                          'global_constraints':['current active selected role','durable authority and target scope','revocation visible on subsequent request','authority unavailable fail closed',
                                                'LIMITED does not imply FULL','ADMIN_ACCESS_MANAGE FULL only','last active FULL admin preserved including concurrency',
                                                'USERS_VIEW does not grant credentials','VIEW_STATS does not grant roster personal data export','teacher read expansion does not grant writes or unfiltered ticket details'],
                          'runtime_status':'NOT_RUN'})
with (OUT/'scenarios.csv').open('w',encoding='utf-8',newline='') as file:
    fields=['id','kind','revision','source_refs','role','roles_and_grants','client','prestate','action','expected_result','forbidden_cases','owner_overlay_ids','status','verification','runtime_status']
    writer=csv.DictWriter(file,fieldnames=fields); writer.writeheader()
    for row in rows:
        writer.writerow({key:json.dumps(row[key],ensure_ascii=False) if isinstance(row[key],(dict,list)) else row[key] for key in fields})
print(json.dumps(source_meta,ensure_ascii=False,indent=2))
