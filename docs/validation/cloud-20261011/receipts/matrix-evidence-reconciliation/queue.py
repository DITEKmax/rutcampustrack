import importlib.util, json, re
from collections import defaultdict, Counter
s=importlib.util.spec_from_file_location('rec', '/workspace/scratch/rct-validation/matrix_evidence_reconciliation/reconcile.py');m=importlib.util.module_from_spec(s);s.loader.exec_module(m)
O=m.OUT;R=m.ROOT;S=m.SCR
# Queue entries are proposed commands/oracles, never executed-run claims.
remaining={
'AUTH-SESSION':'Real selected-role/multirole login, refresh one winner/conflict, cookie attributes and foreign-session isolation, grant/password revoke/restart/authority outage; durable state+outbox rollback. Client admission separate.',
'AUTH-PASSWORD-OTP':'Accept only stable reviewed SECURITY2 corrections. OTP purpose/TTL/attempt/single-use, password policy/hash+clear initial SQL, permitted initial/reset display vs ordinary DTO/export/log denial, sensitive proof/error/no-store; actual public endpoints.',
'AUTH-QR-TMA':'Reuse accepted TMA malformed/window/reuse and QR safe-error children. Remaining guest proof ownership, authenticated approver/current grants, one exchange winner/expired proof/revoked issuer, rate/body bounds and current TMA parser tamper/duplicates; actual host separate.',
'AUTH-WS-TICKET':'Actual purpose/audience token denial, ticket absoluteTTL/singlewinner/logout-before-consume, cookie origin/security attributes; open socket revocation belongs notification fixture.',
'ACADEMIC-ADMIN':'Actual routes with FULL/LIMITED distinct12-grant guards/dependencies/protected targets; live revoke and waiting stale actor; last FULL concurrent winner; user restore leaves no active grants; stable single role-independent login/add-role collisions.',
'ACADEMIC-GROUPS':'Actual dated roster transfer/backfill, current HEADMAN replacement atomically revokes helpers, draft reasons/group parser/registry sort+filter+count; spring promotion once/catchup/new-group exclusion/graduate archive; avoid wall-clock claim from manually invoking job only.',
'ACADEMIC-SEMESTER':'Activation/archival/extension/restore with PG coordinator and actual Schedule/Attendance barriers and durable participant retry; original IDs/history preserved; deletion preview/digest/foreign rows, replay cannot bypass grants. No destructive production target.',
'ACADEMIC-ASSIGNMENT':'Actual user/subject/type/assignment commands and competing assignment race201/409; replacement closes history and removes eligible future lessons via real participant; current active teacher group history authority vs historical-only denial; subject durable delete intent retry.',
'ACADEMIC-HOMEWORK':'Helper real gateway→Schedule→Academic create/edit nonauthor/history/completion; bind/archive/transfer semantics and rollback/outbox; next lesson permission cases already reviewed at separate seams; overdue/completed retained and unbound stays put.',
'ACADEMIC-MAP':'Real upload/storage/read/download sanitized SVG+PNG availability/version, invalid file/foreign scope, failed partial replacement/delete retry preserves old links/shared references; actual browser SVG execution negative. Legend only D004 excluded.',
'ACADEMIC-CACHE-DTO':'Actual DTO/date/collection cache roundtrip, foreign polymorphic @class rejection, actor-scoped cold/warm/mutation/rollback/role-revoke isolation; new enum coverage across remaining consumers. L05 query optimization optional, no count/SLA campaign.',
'SCHEDULE-CLOCK-GENERATION':'Isolated educational clock after full security-clock isolation recheck. Eight Moscow slots, exact time/date/parity bounds, retrospective first generation, remove/readd preserving lesson IDs/history and omitted interval, oneoff uniqueness/retry, real start/close/catchup outcomes.',
'SCHEDULE-TRANSFER-CANCEL':'Actual transfer participant recovery/epochs, cancellation immutable history/metric exclusion/restoration empty current teacher, room-only ID preserved+new scopedETag; full chain live schedule notification/browser updates. Keep protective per-item checks during transfer.',
'ATTENDANCE-CHECKIN':'Real Mongo transaction public geo successful/failed/error/window inclusive boundaries; D005 manual HEADMAN ABSENT blocks before geofence and auto-failed request, no effects; concurrent geo/manual state coherent. Idempotentreceipt/outbox rollback and no optimistic client mark.',
'ATTENDANCE-REQUESTS':'Current public manual/automatic/excuse origin, manual quota5pairs/concurrent active winner/charged once/no refund, late approval and present-preservation races/reasons, retry/cancel terminal outboxonce; attachments own/crossscope bytes/year retention; legacy direct ExcuseService is not current negative oracle.',
'ATTENDANCE-READ-REPORT':'Domain-calculated metrics zero-denominator/rounding/current+historical cohort, actual types and teacher/group/subject isolation, named helperVIEW_STATS allowed but roster denied; real distinct export model/file assertions in export fixture.',
'ATTENDANCE-PARTICIPANTS':'Real Mongo+Rabbit transfer/archive/delete barriers and owned row/file cleanup, delayedtrusted event/drain/epoch fences, duplicate and mismatched identity/payload, retry/DLQ; interrupted durable operation resumes after fresh restore. External authority mock limit retained.',
'ATTENDANCE-CACHE':'Reuse L02 authority-read and DR01 optional projection children. Remaining real Academic DB activation/archive without consumer event and subsequent current snapshot/command, request eligibility, geofence ownTTL/coldoutage behavior; actual client degradedstate.',
'NOTIFICATION-PREFERENCES-HISTORY':'Real Mongo canonical preferences/binding adoption survives restart/Redis outage, dated audience per lesson/action, durableintent+history dedup/tombstones, foreign-owner privacy. Fresh admission retry after authority outage and account binding change before external send.',
'NOTIFICATION-WS-PUSH':'Actual ticket handshake, private user/group subscribe rejection, opened socket revoke then new private event no emission, reconnect; durable push subscription user isolation and cleanup. Mock push provider ≠ actual device delivery.',
'BOT-DELIVERY':'Real isolated Rabbit/Mongo/Redis retry/kill/redelivery/ACK-after-durable-effect, preferences outage defer, fresh audience/binding after429 and prepared-intent restart. Python mocked publisher/provider tests are supporting. No real private bot.',
'BFF-CONTRACT-HTTP':'Actual current-user HTTP→signed gRPC context with current grants, privateETag200→304→newversion and no-store sensitive routes, read-only teacher/student negatives/no writes, DTO unknown/null/format and upload/download metadata. Fake upstream IT is not deployed chain.',
'RENDERER':'Reuse accepted RF01–04 and native children; no unchanged renderer rerun. Remaining actual domain/template→renderer→download chain and current static-secret/private-network config; native artifact alone does not close exportmodels.',
'SHARED-SECURITY-ERRORS':'Targeted JWTpurpose/issuer/audience/algorithm and caller→target→method actual protectedTLS policies; safe RFC9457 fields/log markers/traces/no secret echo. Preserve renderer existing distinct boundary; no blanket TLS-protectedRPC claim.',
'SHARED-OUTBOX':'Reuse broker-acceptance child; local transaction rollback and real consumer durable claim/effect with restart/redelivery/ACK ordering; independent consumers only once each, routing binding semantics actual. Cleanup tests are not broker/consumer proof.',
'MIGRATIONS':'Disposable clean and synthetic historical upgrade schemas/indices/constraints/sequence values, stable enum roundtrip/unknown/null/legacy+new consumers. Single Academic Flyway owner for shared Auth schema; no repair/clean to conceal failures.',
'HEALTH-OUTAGE':'Actual routed API behavior during authority/Rabbit/DB/renderer outage and subsequent recovery, targeted deadlines/status, readiness/private Actuator boundary; healthUP alone insufficient and no invented uptime/drainSLA.',
'RECOVERY':'Actual scripts/recovery.py disposable PG/Mongo/files capture+fresh restore, schema/indices/sequences/history/blob checksum; real application pending transfer retry once on restored stack after writer quiescence. Validator/loopback mocks are not application recovery.'}
priority={g:1 for g in remaining}
for g in ['ACADEMIC-CACHE-DTO','HEALTH-OUTAGE','RENDERER']:priority[g]=2
queue=[]
for g in m.groups:
 gid=g['group_id']; samples=[]
 for t in g['named_tests']:
  methods=[f for f in t['methods'] if f.get('assertion_evidence')]
  # Preserve all assertion-bearing source candidates in a separate appendix;
  # bounded queue selects one representative per relevant fixture class.
  if methods:
   methods.sort(key=lambda f:-sum(w in f['method'].lower() for w in ['revoke','race','rollback','unknown','foreign','manual','conflict','expired','transaction']))
   samples.append({'path':t['path'],'class':t['class'],'method':methods[0]['method'],'assertion_evidence':methods[0]['assertion_evidence'],'fixture_resource_limits':t.get('required_execution_resources'),'level':t.get('level')})
 queue.append({'id':gid,'priority':priority[gid],'status':'UNKNOWN','required':True,'criterion':g['criterion'],'remaining_observable_oracle':remaining[gid],'source_story_ids':g['related_source_story_ids'],'research_ids':g['research_ids'],'known_assertion_candidates':samples,'proposed_commands_NOT_EXECUTED':g['intended_current_checks'],'baseline_map_revision':m.BASE,'existing_source_map':str(S/'test_evidence_map/test-evidence-map.json'),'verification_limits':g['verification_limits'],'run_admission':'Root assigns isolated resources/lease, supported launcher, stable revision. First reconcile existing actual selected receipts; execute only uncovered/change-dependent oracles. XML nonempty/no skips required; failOnNoMatchingTests=false is not proof.'})
m.save('known-test-assertion-catalog.json',{'baseline_revision':m.BASE,'use':'Full inherited assertion map reused, not new runtime or personal reread claim for every class. Queue commands remain proposals. New reviewed actual assertions are in proposed-case-updates.json.','groups':m.groups})

extra={
'SECURITY2-ACCEPTANCE':(1,'Author stable stronger initial-password fixture + fresh independent review/recheck; actual scoped partial greens stay UNKNOWN until accepted. Includes OTP/report-proof/remaining locale/Mongo/BFF/no-store; do not replay reviewed8a9 cases just to recover metadata.'),
'SCHEMA-OWNER-L08':(1,'Single Academic Flyway pipeline owns users/role_grants/auth_sessions/reset/QR tables and shared schema migrations; Auth Flyway disabled. Preserve safe table/column owner-consumer manifest and actual atomic credentials/grants/session rollback/concurrency and synthetic upgrade.'),
'DASHBOARD-ACTIVITY':(2,'FULL/current allowed ADMIN dashboard with active counts/per-semester and alltime activity/action timestamp Moscow yesterday, retries once/no false zero on outage, actor/stat-group fields as accepted. Existing LessonActionAuditIT covers attendance counts/rollback only; actual Academic/BFF/dashboard composite and client missing.'),
'PUBLIC-RETIRED-NEGATIVE':(1,'Current public catalog/create/mark reject FREE_ATTENDANCE and unknown enum typed4xx/no DB/outbox changes; legacy create410; historical row retained with compatible mapping. Positive freeattendancefeature excluded, not this denial.'),
'EXPORT-ASYNC-CORE':(1,'Accepted async initiate→jobidentity queued/preparing→generation→ready event→authorised download; no sync file in initiation; actual renderer failure→error, retry/revoke/foreign job deny with unchanged data; private existing WS hint and live authority at initiation/read/download. No extra retention/cross-session/exactly-once-provider promise.'),
'EXPORT-HEADMAN-MODELS':(1,'Distinct weekly and subject×actualtypes journal; own live HEADMAN or helperVIEW_STATS; semesterstart→currentweek, dated roster/multiple sameday types/cancelled/future exclusions; selectedweeks exact. Five table formats each distinct model once with content parity; current readonly/native converter reuse.'),
'EXPORT-STATS-TEACHER-MODELS':(1,'Headman groupsubject summaries+graph and teacher subject journal/student summary/group summary: current activegroup teacher authority incl earlierpast, historic assignmentalone denied, fullsemester default blank future columns metrics excluded; current pastOnly client selector independent. Full filtered sorted unpaged rows/context/metrics; five tableformats and graphPNG/HTML only.'),
'EXPORT-ROSTER-ADMIN-MODELS':(1,'HEADMAN own current roster exactly number/name/login/group-role+group/Moscowdate noTelegram/password/recovery; helperVIEW_STATS denied. FULL/LIMITED registry users/groups fullfilteredorder/serverformats; D006 ordinaryfields TelegramID FULLonly +safe auditactor/time/kind/rowcount; D007 liveUSERS_VIEW/GROUPS_VIEW/init-read-download-revoke.'),
'CLIENT-AUTH-ROLES':(1,'Real production Vue PWA/TMA login/multirole selection(defaultneverADMIN)/reload/access-expiry/refresh singlewinner/logoutallsessions/password/OTP/reset/binding; real denied old access and current state after revoke. Existing auth smoke and bounded currentVue spec candidates; old Angular scalar-role specs cannot stand in.'),
'CLIENT-STUDENT-GEO-REQUESTS':(1,'Real current Moscow geo window serverack beforemark, GPSdeny/networkretry/D005 manualabsence disable, automatic/manual requestorigin/quota/reason/file upload; student mark/request persisted and correct headman decision. No unlimited clock fixture reaches session/token/attachment security expiry.'),
'CLIENT-HOMEWORK':(1,'Real student persisted own completion/linkescape and optimisticrollback/sort/focus; helper/headman nonauthor create/edit/history/cutoff/nextlesson/transfer. Existing bounded completion candidate proves only its exact data; missing remaining browser flows.'),
'CLIENT-HEADMAN':(1,'Actual mobile journal marking/live counters/queue optimisticfailure/retry, recurring/oneoff/room/transfer/cancelrestore, subject/types/teacherassignment/helpers permissions/roster/resetlink; current rights revoked after render rejected server and clientupdated. No unaccepted adjacent studentprofileedit or masscancel.'),
'CLIENT-TEACHER':(1,'Actual mobile teacher today/daily selectedlesson/historical subjectjournal/scrollpagination/sort/filter/stats read and request/fileview; foreigngroup/historical-only scope deny, any write deny; journal/export current selectors and error/empty/retry.'),
'CLIENT-ADMIN':(1,'Actual ready mobile FULL/LIMITED dashboard/registry user+group/semester/maps/accountcredentials+binding; granted guardgroups, missing/revokedgrant negatives/servernoeffect; no future webpanelUI added. Adminselfreset not implied by reset-other link.'),
'CLIENT-MAP-FILES-EXPORTS':(1,'Actual ready map download/version/browserSVGnegative, accepted request PNG/document preview/ticketexpiry/forbiddenbytes, distinct export selection→ready→download MIME/content/Cyrillicfilename/permissions; no legend/student selfexport/newteacherroster requirement.'),
'CLIENT-NOTIFY':(1,'Actual private inbox settings/read/target links/preferences binding +openWS grantrevoke newprivateevent denied, live scheduleversion mutation updates ownVue. Actual push/device provider distinct from mocked send.'),
'CLIENT-OFFLINE-D002':(1,'Production serviceWorkers allow/warm+reload with persistent own schedule/homework; expiry/revoke offline only read until reconnectauthority, no writes/newattachments/roleswitch/TMAoffline; explicitlogout and userA→B including latecallbacks expose no old scope. Loaded-tab offline/unit worker mocks do not close.'),
'CLIENT-TMA-PROVIDER':(2,'Synthetic real TMA Vue host signed admission/reuse/deeplink/Back/download online; native Telegram launch/deeplink/download separately actual provider if available. Exact inaccessible provider only BLOCKED with missing access proof, not all client work.'),
'CLIENT-INSTALL-PUSH':(2,'Ready PWA production manifest/serviceworker install/update/standalone launch/iOS instructions with browser and native device capabilities explicit; pushactualsubscription/private notification/device scope and denial/unbind. Chromium is not Safari/nativeprovider.'),
'FINAL-FORMAT-PARSER-GATES':(1,'Complete tracked authored language inventory(Java/Vue/TSJS/HTML/PCSSCSS/Python/SQL/config), deterministic formatter/check and relevant parser/lint/type/build no generated/vendor exactfixtures changes. Semantic freeze and clock fixture classpath/security isolation independently checked. Then exact final local=remote=PRhead, current CI/Coverage/Security actualcheckout mergeSHA and draftPR main/handoff; checkpoint Actions not final.'),
}
for id,(p,oracle) in extra.items():
 queue.append({'id':id,'priority':p,'status':'UNKNOWN','required':True,'remaining_observable_oracle':oracle,'known_assertion_candidates':[],'proposed_commands_NOT_EXECUTED':[],'missing_oracle':'Exact observable state above; source/fixture existence does not prove execution. Use existing current templates/harness below, extending only uncovered accepted criterion.','actual_runtime_this_receipt':'NOT_RUN'})
for q in queue:
 if q['id'].startswith('CLIENT-'):
  q['fixture_candidates']=['tests/e2e/specs/mobile-real-server.spec.ts','tests/e2e/playwright.real-mobile.config.ts','tests/e2e/REAL-MOBILE.md','tests/e2e/specs/auth-token-lifecycle.spec.ts','tests/e2e/specs/mini-app.spec.ts']
  q['proposed_commands_NOT_EXECUTED']=[{'cwd':str(R/'tests/e2e'),'command':'RCT_E2E_SYNTHETIC_SEED=true E2E_BASE_URL=https://localhost:8443 npm run test:real-mobile','limit':'Existing eight bounded current Vue flows only; missing accepted paths above require source-derived extension. Admission only after corrected clock fixture complete audit + isolated real backend seed/lease; do not claim discovered/skipped/mock results PASS.'}]
 if q['id'].startswith('EXPORT-'):
  q['fixture_candidates']=['HeadmanWeeklyReportServiceTest','HeadmanWeeklyReportControllerIT','HeadmanStatsServiceTest','HeadmanStatsRendererTest','HeadmanStatsTrendRendererTest','HeadmanGroupCompositionServiceTest','TeacherAttendanceExportServiceTest','TeacherStatsExportServiceTest','ReportDownloadTicketServiceTest','ReportDownloadTicketValidationTest','TeacherReadFacadeTest']
  q['fixture_data']='One shared synthetic fixture: multiple sameday lesson types/cancelled/future, historical transferred/new student, archivedsemester, filteredrows beyond onepage, safe XML/HTMLcontrol chars. Each distinct accepted model once; no role×format×filter crossproduct or artificial timing SLA.'
  q['dependency']='Author new accepted async/headman subject/admin export package stable+review first. Current c898 source-only absence, not runtime FAIL.'
m.save('remaining-execution-queue.json',{'schema':1,'baseline':m.BASE,'integrated_input_revision':m.TARGET,'status':'EXECUTABLE_PROPOSAL_NOT_RUN','root_sole_runtime_scheduler':True,'minimality':'28 existing real fixture groups +20 specific missing-oracle groups. Existing assertions reused, no count/SLA/exhaustive matrix, no unchanged accepted renderer/broker/cache repeats; choose suite/method by exact remaining assertion and actual new receipt before run. One level per invariant, second only distinct transport/client/data risk.','entries':queue})

# Complete story coverage: source associations are candidate routing, not acceptance.
gm=defaultdict(set)
for g in m.groups:
 for sid in g['related_source_story_ids']:gm[sid].add(g['group_id'])
supp={
'JS-ADMIN-11':['DASHBOARD-ACTIVITY'],'JS-ADMIN-12':['DASHBOARD-ACTIVITY','ATTENDANCE-READ-REPORT'],'JS-ADMIN-14':['AUTH-PASSWORD-OTP'],'JS-ADMIN-21':['ACADEMIC-ADMIN','AUTH-QR-TMA'],'JS-ADMIN-22':['DASHBOARD-ACTIVITY'],
'JS-HEADMAN-03':['SCHEDULE-CLOCK-GENERATION','ATTENDANCE-READ-REPORT'],'JS-HEADMAN-04':['ATTENDANCE-REQUESTS','BOT-DELIVERY'],'JS-HEADMAN-05':['ATTENDANCE-CHECKIN','ATTENDANCE-REQUESTS'],'JS-HEADMAN-06':['ATTENDANCE-REQUESTS'],'JS-HEADMAN-14':['ACADEMIC-GROUPS','ACADEMIC-ADMIN'],'JS-HEADMAN-15':['ACADEMIC-GROUPS','ACADEMIC-ADMIN'],'JS-HEADMAN-20':['CLIENT-HEADMAN'],'JS-HEADMAN-25':['ATTENDANCE-READ-REPORT','CLIENT-HEADMAN'],'JS-HEADMAN-26':['ATTENDANCE-REQUESTS','DASHBOARD-ACTIVITY'],'JS-HEADMAN-33':['ATTENDANCE-READ-REPORT'],'JS-HEADMAN-35':['ACADEMIC-ADMIN','CLIENT-HEADMAN'],'JS-HEADMAN-36':['ACADEMIC-ASSIGNMENT','ATTENDANCE-PARTICIPANTS'],'JS-HEADMAN-37':['ATTENDANCE-READ-REPORT','SCHEDULE-CLOCK-GENERATION'],
'JS-HEADMAN-WEB-01':['CLIENT-HEADMAN'],'JS-HEADMAN-WEB-02':['CLIENT-HEADMAN','ATTENDANCE-READ-REPORT'],'JS-HEADMAN-WEB-03':['ACADEMIC-GROUPS','CLIENT-HEADMAN'],'JS-HEADMAN-WEB-04':['ACADEMIC-ASSIGNMENT','CLIENT-HEADMAN'],'JS-HEADMAN-WEB-05':['EXPORT-HEADMAN-MODELS','CLIENT-HEADMAN'],'JS-HEADMAN-WEB-06':['ATTENDANCE-REQUESTS','CLIENT-HEADMAN'],'JS-HEADMAN-WEB-07':['ATTENDANCE-REQUESTS','CLIENT-HEADMAN'],'JS-HEADMAN-WEB-08':['ATTENDANCE-READ-REPORT','CLIENT-HEADMAN'],'JS-HEADMAN-WEB-09':['ACADEMIC-HOMEWORK','CLIENT-HOMEWORK'],
'JS-STUDENT-09':['CLIENT-INSTALL-PUSH'],'JS-STUDENT-10':['CLIENT-OFFLINE-D002'],'JS-STUDENT-13':['ATTENDANCE-READ-REPORT','CLIENT-STUDENT-GEO-REQUESTS'],'JS-STUDENT-15':['ATTENDANCE-REQUESTS'],'JS-STUDENT-18':['ATTENDANCE-READ-REPORT'],'JS-STUDENT-22':['ACADEMIC-HOMEWORK','CLIENT-HOMEWORK'],
'JS-STUDENT-WEB-01':['ATTENDANCE-READ-REPORT','CLIENT-STUDENT-GEO-REQUESTS'],'JS-STUDENT-WEB-02':['SCHEDULE-CLOCK-GENERATION','CLIENT-STUDENT-GEO-REQUESTS'],'JS-STUDENT-WEB-03':['ATTENDANCE-CHECKIN','CLIENT-STUDENT-GEO-REQUESTS'],'JS-STUDENT-WEB-05':['ATTENDANCE-READ-REPORT','CLIENT-STUDENT-GEO-REQUESTS'],'JS-STUDENT-WEB-07':['ATTENDANCE-REQUESTS','CLIENT-MAP-FILES-EXPORTS'],'JS-STUDENT-WEB-08':['ATTENDANCE-REQUESTS','CLIENT-STUDENT-GEO-REQUESTS'],'JS-STUDENT-WEB-09':['AUTH-PASSWORD-OTP','CLIENT-AUTH-ROLES'],'JS-STUDENT-WEB-10':['CLIENT-INSTALL-PUSH'],
'JS-SYSTEM-01':['ACADEMIC-ADMIN','AUTH-SESSION'],'JS-SYSTEM-12':['CLIENT-INSTALL-PUSH'],'JS-SYSTEM-16':['DASHBOARD-ACTIVITY','ATTENDANCE-READ-REPORT'],
'JS-TEACHER-02':['ATTENDANCE-READ-REPORT','CLIENT-TEACHER'],'JS-TEACHER-04':['BFF-CONTRACT-HTTP','CLIENT-TEACHER'],'JS-TEACHER-05':['ACADEMIC-ADMIN','AUTH-QR-TMA'],'JS-TEACHER-08':['CLIENT-TEACHER'],'JS-TEACHER-10':['ATTENDANCE-READ-REPORT','CLIENT-TEACHER'],'JS-TEACHER-13':['ATTENDANCE-READ-REPORT','CLIENT-TEACHER'],'JS-TEACHER-15':['AUTH-PASSWORD-OTP','CLIENT-AUTH-ROLES']}
for sid,gs in supp.items():gm[sid].update(gs)
exportstory={'JS-ADMIN-13':['EXPORT-ASYNC-CORE','EXPORT-ROSTER-ADMIN-MODELS'],'JS-HEADMAN-19':['EXPORT-ASYNC-CORE','EXPORT-HEADMAN-MODELS','EXPORT-STATS-TEACHER-MODELS'],'JS-HEADMAN-32':['EXPORT-ROSTER-ADMIN-MODELS'],'JS-TEACHER-07':['EXPORT-ASYNC-CORE','EXPORT-STATS-TEACHER-MODELS']}
for sid,gs in exportstory.items():gm[sid].update(gs)
roleclient={'ADMIN':'CLIENT-ADMIN','HEADMAN':'CLIENT-HEADMAN','TEACHER':'CLIENT-TEACHER','STUDENT':'CLIENT-STUDENT-GEO-REQUESTS','SYSTEM':'CLIENT-AUTH-ROLES'}
rc={r['id']:r['verification_groups'] for r in m.testmap['research_map']}; rc['SD-TMA']=['AUTH-QR-TMA','CLIENT-TMA-PROVIDER']
fc=[]
stories={x['id']:x for x in m.source_stories}
for f in m.families:
 sid=f['id']; gs=set();d={'id':sid,'existing_family_status':f['status'],'proposed_family_status':f['status'],'source':json.loads(f['source']) if f['source'].startswith('[') else f['source'],'raw_action':f['action'],'raw_expected':f['expected'],'effective_overlay_required':[],'partial_pass_child_ids':[c['id'] for c in m.updates if sid in c.get('parent_family_ids',[])],'family_pass_rule':'All applicable finite significant oracles and current ready-client paths PASS or exact sourced exclusion; a mapped class is not evidence.'}
 if f['status']=='EXCLUDED':
  d['disposition']='Existing explicit source/owner exclusion retained';d['exclusion']=f['exclusion'];d['required']=False
  if sid=='JS-STUDENT-04':gs.add('PUBLIC-RETIRED-NEGATIVE');d['required_negative_child']='BOUNDARY-32 remains required despite excluded positive feature'
 else:
  d['required']=True
  if sid.startswith('JS-'):
   gs.update(gm[sid]);src=stories[sid];d['effective_source_criteria']=src['effective_criteria'];d['source_overlays']=src['owner_overlay_ids'];d['source_caveat']=src['interpretation']
   # Ready PWA/TMA equivalence is a real path only where currently accepted carrier exists.
   role=sid.split('-')[1]
   if role in roleclient:gs.add(roleclient[role])
  elif sid.startswith('RESEARCH-'):gs.update(rc.get(sid[9:],[]))
  elif sid.startswith('CLIENT-FE-'):
   name=sid[len('CLIENT-FE-'):]; first=name.split('-')[0]
   cmap={'AUTH':'CLIENT-AUTH-ROLES','TMA':'CLIENT-TMA-PROVIDER','OFFLINE':'CLIENT-OFFLINE-D002','STUDENT':'CLIENT-STUDENT-GEO-REQUESTS','HEADMAN':'CLIENT-HEADMAN','TEACHER':'CLIENT-TEACHER','ADMIN':'CLIENT-ADMIN','MAP':'CLIENT-MAP-FILES-EXPORTS','NOTIFY':'CLIENT-NOTIFY','PWA':'CLIENT-INSTALL-PUSH'}
   gs.add(cmap[first])
   if name=='STUDENT-05' or name=='HEADMAN-07':gs.add('CLIENT-HOMEWORK')
   if name in ['HEADMAN-08','HEADMAN-09','TEACHER-02']:gs.add('CLIENT-MAP-FILES-EXPORTS')
 if sid in ['JS-SYSTEM-01','JS-STUDENT-10','RESEARCH-SD-TMA','CLIENT-FE-STUDENT-04','JS-HEADMAN-35','JS-STUDENT-WEB-10','JS-HEADMAN-11','JS-TEACHER-03','JS-TEACHER-07']:d['effective_overlay_required'].append('See effective-source-overlays.json; raw family prose is provenance, not executable oracle.')
 d['remaining_queue_ids']=sorted(gs);d['execution_oracle_location']='remaining-execution-queue.json plus source-derived boundary-coverage.json; raw/effective source criteria retained here to avoid generic test-name PASS'
 if sid in ['RESEARCH-L05','RESEARCH-P01']:d['required']=False;d['disposition']='Optional architecture/performance hypothesis only; accepted domain behavior remains required through related families'
 assert gs or f['status']=='EXCLUDED',sid
 fc.append(d)
assert len(fc)==218
m.save('family-coverage.json',fc)

bg={
1:['AUTH-SESSION','CLIENT-AUTH-ROLES'],2:['AUTH-SESSION','SHARED-SECURITY-ERRORS','CLIENT-AUTH-ROLES'],3:['AUTH-SESSION','CLIENT-AUTH-ROLES'],4:['AUTH-PASSWORD-OTP'],5:['AUTH-QR-TMA','CLIENT-AUTH-ROLES'],6:['AUTH-QR-TMA','CLIENT-TMA-PROVIDER'],7:['AUTH-PASSWORD-OTP','ACADEMIC-ADMIN','EXPORT-ROSTER-ADMIN-MODELS'],8:['SHARED-SECURITY-ERRORS','AUTH-PASSWORD-OTP','AUTH-QR-TMA'],
9:['ACADEMIC-ADMIN','CLIENT-ADMIN'],10:['ACADEMIC-ADMIN'],11:['ACADEMIC-ADMIN','AUTH-SESSION'],12:['ACADEMIC-ADMIN','AUTH-PASSWORD-OTP'],13:['ACADEMIC-GROUPS','ATTENDANCE-READ-REPORT'],14:['ACADEMIC-GROUPS','CLIENT-HEADMAN'],15:['ACADEMIC-GROUPS'],
16:['ACADEMIC-SEMESTER','ATTENDANCE-CACHE','CLIENT-STUDENT-GEO-REQUESTS'],17:['ACADEMIC-SEMESTER','ATTENDANCE-PARTICIPANTS'],18:['ACADEMIC-SEMESTER','SCHEDULE-CLOCK-GENERATION'],19:['ACADEMIC-SEMESTER','ATTENDANCE-PARTICIPANTS','RECOVERY'],20:['SCHEDULE-CLOCK-GENERATION','CLIENT-HEADMAN'],21:['SCHEDULE-CLOCK-GENERATION'],22:['ACADEMIC-ASSIGNMENT','SCHEDULE-TRANSFER-CANCEL','CLIENT-TEACHER'],23:['SCHEDULE-TRANSFER-CANCEL','ACADEMIC-HOMEWORK','CLIENT-HEADMAN'],24:['SCHEDULE-TRANSFER-CANCEL','ATTENDANCE-PARTICIPANTS','ACADEMIC-HOMEWORK'],25:['SCHEDULE-TRANSFER-CANCEL','BFF-CONTRACT-HTTP','CLIENT-NOTIFY'],
26:['ATTENDANCE-CHECKIN','CLIENT-STUDENT-GEO-REQUESTS'],27:['ATTENDANCE-CHECKIN','ATTENDANCE-REQUESTS','CLIENT-STUDENT-GEO-REQUESTS'],28:['ATTENDANCE-REQUESTS'],29:['ATTENDANCE-REQUESTS'],30:['ATTENDANCE-REQUESTS','CLIENT-HEADMAN'],31:['ATTENDANCE-READ-REPORT','ATTENDANCE-REQUESTS','CLIENT-TEACHER'],32:['PUBLIC-RETIRED-NEGATIVE'],33:['ATTENDANCE-READ-REPORT','CLIENT-TEACHER'],34:['DASHBOARD-ACTIVITY','CLIENT-ADMIN'],35:['ACADEMIC-GROUPS','ACADEMIC-ADMIN','CLIENT-ADMIN'],36:['ACADEMIC-HOMEWORK','CLIENT-HOMEWORK'],37:['ACADEMIC-HOMEWORK'],38:['ACADEMIC-HOMEWORK','CLIENT-HOMEWORK'],39:['ACADEMIC-HOMEWORK','CLIENT-HOMEWORK'],40:['SHARED-OUTBOX','ATTENDANCE-PARTICIPANTS','NOTIFICATION-PREFERENCES-HISTORY','BOT-DELIVERY'],41:['NOTIFICATION-PREFERENCES-HISTORY','CLIENT-NOTIFY'],42:['NOTIFICATION-PREFERENCES-HISTORY','BOT-DELIVERY','CLIENT-NOTIFY'],43:['AUTH-WS-TICKET','NOTIFICATION-WS-PUSH','CLIENT-NOTIFY'],44:['ATTENDANCE-REQUESTS','CLIENT-MAP-FILES-EXPORTS'],45:['ACADEMIC-MAP','CLIENT-MAP-FILES-EXPORTS'],46:['EXPORT-ASYNC-CORE','EXPORT-HEADMAN-MODELS','EXPORT-STATS-TEACHER-MODELS','EXPORT-ROSTER-ADMIN-MODELS','CLIENT-MAP-FILES-EXPORTS'],47:['CLIENT-OFFLINE-D002'],48:['CLIENT-INSTALL-PUSH','CLIENT-TMA-PROVIDER'],49:['CLIENT-HEADMAN','CLIENT-TEACHER','CLIENT-ADMIN'],50:['MIGRATIONS','ACADEMIC-CACHE-DTO','SHARED-SECURITY-ERRORS'],51:['RECOVERY','HEALTH-OUTAGE'],52:['FINAL-FORMAT-PARSER-GATES']}
bc=[]
for b in m.boundaries:
 n=int(b['id'].split('-')[1]);d=dict(b);d['remaining_queue_ids']=bg[n];d['partial_pass_child_ids']=[c['id'] for c in m.updates if b['id'] in c.get('parent_boundary_ids',[])];d['status']='UNKNOWN';d['effective_oracle_overlay']='D005 prevents geo/auto_failed_geo after HEADMAN manualABSENT; ordinary allowed failedgeo still creates unlimited nonexpiring auto request with no manual quota' if n==27 else None;bc.append(d)
assert len(bc)==52
m.save('boundary-coverage.json',bc)

rq=[]
for r in m.source_requests:
 ids=r.get('verified_source_association_story_ids',[]) or r.get('related_story_ids',[])
 qs=sorted(set(x for sid in ids for x in gm.get(sid,[])))
 original_link=bool(qs)
 if not qs:
  pref=r['request_id'].split('-')[0]
  domain={'AU':['AUTH-SESSION','AUTH-PASSWORD-OTP','AUTH-QR-TMA','AUTH-WS-TICKET'],'AC':['ACADEMIC-ADMIN','ACADEMIC-GROUPS','ACADEMIC-SEMESTER','ACADEMIC-ASSIGNMENT','ACADEMIC-HOMEWORK','DASHBOARD-ACTIVITY'],'SC':['SCHEDULE-CLOCK-GENERATION','SCHEDULE-TRANSFER-CANCEL'],'AT':['ATTENDANCE-CHECKIN','ATTENDANCE-REQUESTS','ATTENDANCE-READ-REPORT','ATTENDANCE-PARTICIPANTS'],'NT':['NOTIFICATION-PREFERENCES-HISTORY','NOTIFICATION-WS-PUSH','BOT-DELIVERY'],'MAP':['ACADEMIC-MAP','CLIENT-MAP-FILES-EXPORTS'],'X':['SHARED-SECURITY-ERRORS','BFF-CONTRACT-HTTP','SHARED-OUTBOX','RECOVERY','EXPORT-ASYNC-CORE','DASHBOARD-ACTIVITY']}
  qs=domain.get(pref,[])
 if r['request_id']=='AT-17':qs=[]
 if r['request_id']=='MAP-06':qs=[]
 rq.append({'request_id':r['request_id'],'source':r['source'],'historical_product_decision_status':r['product_decision_status'],'historical_action_classification':r['action_classification'],'source_status':r['source_status'],'owner_overlay_ids':r['owner_overlay_ids'],'excluded_by':r['excluded_by'],'current_assessment':r['current_assessment'],'verified_story_associations':r.get('verified_source_association_story_ids',[]),'candidate_story_associations':r.get('candidate_story_ids',[]),'associated_queue_ids':qs,'queue_association_kind':'existing reviewed story relation' if original_link else 'domain-only candidate routing, not acceptance assertion','effective_disposition':'AT-17 unconfirmed selfexport proposal not mandatory' if r['request_id']=='AT-17' else 'MAP-06 legend deferred by D004; other map requests retained' if r['request_id']=='MAP-06' else 'Historical open/needs-decision labels do not imply current owner question; resolve accepted portion through effective source criteria/owner overlays and queue. Optional proposed schema/API is not automatically required.','rule':'Exact request row retained for provenance. Proposal/deferred/candidate links are not autonomous acceptance or separate mandatory feature; execution is through effective accepted story/boundary. No147+175 sum.'})
m.save('request-scope-reconciliation.json',rq)
known={q['id'] for q in queue}
assert all(set(x['remaining_queue_ids'])<=known for x in fc+bc)
parentids={f['id'] for f in m.families}
for c in m.updates:
 for sid in c.get('parent_family_ids',[]):
  if sid.startswith('CLIENT-TMA-'):sid=sid.replace('CLIENT-TMA-','CLIENT-FE-TMA-')
  assert sid in parentids,(c['id'],sid)
m.save('coverage-checks.json',{'family_rows':len(fc),'required_family_rows':sum(x['required'] for x in fc),'explicit_existing_excluded_families':sum(x['existing_family_status']=='EXCLUDED' for x in fc),'boundary_rows':len(bc),'request_source_rows':len(rq),'queue_groups':len(queue),'missing_required_family_mapping':0,'missing_boundary_mapping':0,'dangling_queue_ids':0,'product_PASS_children':14,'supporting_harness_PASS':1,'whole_family_PASS_promotions':0,'whole_boundary_PASS_promotions':0,'runtime':'NOT_RUN','check_kind':'light receipt referential/finite coverage checks; not product test'})
print('queue:',len(queue),'coverage:',len(fc),'families/',len(bc),'boundaries/',len(rq),'source requests')
