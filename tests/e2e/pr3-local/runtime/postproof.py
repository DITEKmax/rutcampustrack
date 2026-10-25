# Portable local adaptation; NOT_RUN until owner output.
"""Scoped read-only before/after evidence: SQL produces digests, never credentials/row dumps."""
import argparse,hashlib,json,uuid
import runtime
R=runtime.ROOT;P=runtime.PRIVATE

def collect():
 s=json.loads((P/'fixture-state.json').read_text());g=int(s['groupId']);control=int(s['controlGroupId']);assert control>1 and control!=g;member=int(s['accounts'][0]['id']);helper=int(s['accounts'][1]['id']);lesson=int(s['lessonId']);assert g>1 and member>3 and helper>3
 assert str(uuid.UUID(s['headmanSessionId']))==s['headmanSessionId'] and str(uuid.UUID(s['teacherSessionId']))==s['teacherSessionId'] and s['headmanSessionId']!=s['teacherSessionId']
 auth=runtime.pg(f"""BEGIN READ ONLY; SELECT json_build_object(
 'accountRows',(SELECT md5(coalesce(jsonb_agg(to_jsonb(t) ORDER BY id)::text,'[]')) FROM auth_identities t WHERE id IN({member},{helper})),
 'foreignAccounts',(SELECT md5(coalesce(jsonb_agg(to_jsonb(t) ORDER BY id)::text,'[]')) FROM auth_identities t WHERE id NOT IN({member},{helper})),
 'otherRoleGrants',(SELECT md5(coalesce(jsonb_agg(to_jsonb(t) ORDER BY id)::text,'[]')) FROM user_role_grants t WHERE group_id IS DISTINCT FROM {g}),
 'targetGrants',(SELECT count(*) FROM user_role_grants WHERE group_id={g}),
 'headmanSession',(SELECT json_build_object('version',session_version,'selected',active_role_grant_id IS NOT NULL,'revoked',revoked_at IS NOT NULL) FROM auth_sessions WHERE sid='{s['headmanSessionId']}'),
 'teacherSession',(SELECT json_build_object('version',session_version,'selected',active_role_grant_id IS NOT NULL,'revoked',revoked_at IS NOT NULL) FROM auth_sessions WHERE sid='{s['teacherSessionId']}')); ROLLBACK;""",'auth_db').stdout
 academic=runtime.pg(f"""BEGIN READ ONLY; SELECT json_build_object(
 'profiles',(SELECT md5(coalesce(jsonb_agg(to_jsonb(t) ORDER BY id)::text,'[]')) FROM academic_profiles t),
 'otherEligibility',(SELECT md5(coalesce(jsonb_agg(to_jsonb(t) ORDER BY user_id,role)::text,'[]')) FROM academic_role_eligibility t WHERE group_id IS DISTINCT FROM {g}),
 'foreignGroup1',(SELECT md5(coalesce(jsonb_agg(jsonb_build_object('kind',kind,'data',data) ORDER BY kind,data::text)::text,'[]')) FROM group_deletion_rows(1)),
 'ownedControl',(SELECT md5(coalesce(jsonb_agg(jsonb_build_object('kind',kind,'data',data) ORDER BY kind,data::text)::text,'[]')) FROM group_deletion_rows({control})),
 'targetGroup',(SELECT count(*) FROM groups WHERE id={g}),
 'targetRows',(SELECT count(*) FROM group_deletion_rows({g})),
 'operationPhase',(SELECT phase FROM group_deletion_operations WHERE group_id={g} ORDER BY created_at DESC LIMIT 1)); ROLLBACK;""",'academic_db').stdout
 schedule=runtime.pg(f"""BEGIN READ ONLY; SELECT json_build_object('foreignGroup1',(SELECT md5(coalesce(jsonb_agg(jsonb_build_object('kind',kind,'data',data) ORDER BY kind,data::text)::text,'[]')) FROM group_deletion_rows(1)),'ownedControl',(SELECT md5(coalesce(jsonb_agg(jsonb_build_object('kind',kind,'data',data) ORDER BY kind,data::text)::text,'[]')) FROM group_deletion_rows({control})),'targetRows',(SELECT count(*) FROM group_deletion_rows({g})),'deletedReceipt',(SELECT count(*) FROM group_deletion_participants WHERE group_id={g} AND state='DELETED')); ROLLBACK;""",'schedule_db').stdout
 # Mongo app role; no admin capability or values printed. Public evidence only counts/digests.
 import re
 uri=(P/'SPRING_DATA_MONGODB_URI').read_text();password=re.search(r':([^:@]+)@',uri).group(1)
 script="const password="+json.dumps(password)+";const d=db.getSiblingDB('attendance_db');if(!d.auth('rct_group_attendance',password))throw Error('Scoped Mongo auth failed');const crypto=require('crypto');const collections=['attendances','late_checkin_requests','excuse_tickets','request_attachments','lesson_actions','student_checkin_pairs','student_checkin_receipts','student_request_receipts','lesson_cancellation_markers'];let own=0;let foreign=[];for(const c of collections){own+=d[c].countDocuments({$or:[{group_id:"+str(g)+"},{lesson_id:"+str(lesson)+"}]});foreign.push([c,d[c].find({group_id:{$ne:"+str(g)+"},lesson_id:{$ne:"+str(lesson)+"}}).sort({_id:1}).toArray()]);}print(JSON.stringify({targetRows:own,foreignDigest:crypto.createHash('sha256').update(EJSON.stringify(foreign)).digest('hex'),deletedReceipt:d.group_deletion_participants.countDocuments({group_id:"+str(g)+",state:'DELETED'})}));"
 mongo=runtime.docker('exec','-i',runtime.PROJECT+'-mongo-attendance-1','mongosh','--quiet','--host','127.0.0.1',input=script).stdout.strip()
 notification_password=re.search(r':([^:@]+)@',(P/'NOTIFICATION_SPRING_DATA_MONGODB_URI').read_text()).group(1)
 notification_script="const d=db.getSiblingDB('notification_db');if(!d.auth('rct_group_notification',"+json.dumps(notification_password)+"))throw Error('Scoped notification auth denied');print(JSON.stringify({history:d.notification_history.countDocuments({}),intents:d.notification_webpush_intents.countDocuments({}),audiences:d.notification_event_audiences.countDocuments({}),pushSubscriptions:d.push_subscriptions.countDocuments({})}));"
 notification=json.loads(runtime.docker('exec','-i',runtime.PROJECT+'-mongo-attendance-1','mongosh','--quiet','--host','127.0.0.1',input=notification_script).stdout.strip())
 return {'auth':json.loads(auth),'academic':json.loads(academic),'schedule':json.loads(schedule),'attendance':json.loads(mongo),'notification':notification,'groupId':g,'lessonId':lesson}
def run(mode,go):
 runtime.admission('native-case',go,continuation=True);value=collect()
 if mode=='before':
  assert value['auth']['targetGrants']==3 and value['academic']['targetGroup']==1 and value['academic']['targetRows']>0 and value['schedule']['targetRows']>0 and value['attendance']['targetRows']>0
  runtime.private_write(P/'native-before.json',value);print('Scoped native before-proof recorded; row digests only')
 else:
  before=json.loads((P/'native-before.json').read_text());checks={}
  for key in ['accountRows','foreignAccounts','otherRoleGrants']:checks['auth-'+key+'Preserved']=value['auth'][key]==before['auth'][key]
  for key in ['profiles','otherEligibility','foreignGroup1']:checks['academic-'+key+'Preserved']=value['academic'][key]==before['academic'][key]
  checks['ownedControlPreserved']=value['academic']['ownedControl']==before['academic']['ownedControl'] and value['schedule']['ownedControl']==before['schedule']['ownedControl'];checks['foreignSchedulePreserved']=value['schedule']['foreignGroup1']==before['schedule']['foreignGroup1'];checks['foreignMongoPreserved']=value['attendance']['foreignDigest']==before['attendance']['foreignDigest']
  checks['hiddenHintNoHistoryPushAudience']=value['notification']==before['notification'];checks['targetRemoved']=value['auth']['targetGrants']==0 and value['academic']['targetGroup']==0 and value['academic']['targetRows']==0 and value['schedule']['targetRows']==0 and value['attendance']['targetRows']==0
  checks['durableReceipts']=value['academic']['operationPhase']=='COMPLETED' and value['schedule']['deletedReceipt']==1 and value['attendance']['deletedReceipt']==1
  h=value['auth']['headmanSession'];old=before['auth']['headmanSession'];t=value['auth']['teacherSession'];oldt=before['auth']['teacherSession'];checks['onlyTargetSelectionWithdrawn']=not h['selected'] and not h['revoked'] and h['version']==old['version']+1 and t==oldt and t['selected'] and not t['revoked']
  runtime.private_write(P/'native-after.json',{'checks':checks,'allPassed':all(checks.values()),'snapshot':value});assert all(checks.values()),'Native post-proof mismatch; no replay/reset';print('Scoped native post-proof passed; only selected target effects, durable receipts retained')
if __name__=='__main__':
 p=argparse.ArgumentParser();p.add_argument('mode',choices=['before','after']);p.add_argument('--go',required=True);a=p.parse_args();run(a.mode,a.go)
