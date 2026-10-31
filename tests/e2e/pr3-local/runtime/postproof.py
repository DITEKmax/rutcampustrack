# Portable local adaptation; NOT_RUN until owner output.
"""Scoped read-only before/after evidence: SQL produces digests, never credentials/row dumps."""
import argparse,hashlib,json,re,sys,uuid
import runtime
R=runtime.ROOT;P=runtime.PRIVATE


MONGO_SCHEMA='rct.local-postproof.mongo.v1'

def mongo_javascript(case_id,database,user,password,group,lesson):
    parameters=json.dumps({'schema':MONGO_SCHEMA,'caseId':case_id,'database':database,
                           'user':user,'password':password,'group':str(group),'lesson':str(lesson)})
    return 'const p='+parameters+';\n'+r"""
async function main(){
  const out={schema:p.schema,caseId:p.caseId,ok:false,stage:'AUTH',codes:{},result:null};
  try{
    const d=db.getSiblingDB(p.database);
    const auth=await d.auth(p.user,p.password);
    if(!(auth===1||auth===true||(auth&&auth.ok===1)))throw Error('Scoped auth denied');
    out.stage='READ_ONLY_PROOF';
    if(p.caseId==='ATTENDANCE'){
      const group=NumberLong(p.group),lesson=NumberLong(p.lesson);
      const collections=['attendances','late_checkin_requests','excuse_tickets',
        'request_attachments','lesson_actions','student_checkin_pairs',
        'student_checkin_receipts','student_request_receipts','lesson_cancellation_markers'];
      let own=0;const foreign=[];
      for(const name of collections){
        own+=await d[name].countDocuments({$or:[{group_id:group},{lesson_id:lesson}]},{maxTimeMS:10000});
        const rows=await d[name].find({group_id:{$ne:group},lesson_id:{$ne:lesson}})
          .sort({_id:1}).maxTimeMS(10000).toArray();
        foreign.push([name,rows]);
      }
      const crypto=require('crypto');
      out.result={targetRows:own,
        foreignDigest:crypto.createHash('sha256').update(EJSON.stringify(foreign)).digest('hex'),
        deletedReceipt:await d.group_deletion_participants.countDocuments({group_id:group,state:'DELETED'},{maxTimeMS:10000})};
    }else if(p.caseId==='NOTIFICATION'){
      out.result={
        history:await d.notification_history.countDocuments({},{maxTimeMS:10000}),
        intents:await d.notification_webpush_intents.countDocuments({},{maxTimeMS:10000}),
        audiences:await d.notification_event_audiences.countDocuments({},{maxTimeMS:10000}),
        pushSubscriptions:await d.push_subscriptions.countDocuments({},{maxTimeMS:10000}),
      };
    }else throw Error('Unadmitted proof case');
    out.stage='DONE';out.ok=true;print(JSON.stringify(out));
  }catch(error){
    if(Number.isInteger(error&&error.code))out.codes.mongo=error.code;
    print(JSON.stringify(out));quit(2);
  }
}
main();
"""


def mongo_result(stdout,process_exit,case_id):
    # Never strip prompts/noise to force PASS; raw child output can include secrets.
    diagnostic={'schema':MONGO_SCHEMA,'caseId':case_id,'stage':'PROTOCOL_OR_SCHEMA',
                'code':'INVALID_SINGLE_JSON','processExit':process_exit}
    try:
        row=json.loads(stdout)
        assert type(row) is dict and set(row)=={'schema','caseId','ok','stage','codes','result'}
        assert row['schema']==MONGO_SCHEMA and row['caseId']==case_id and type(row['ok']) is bool
        assert row['stage'] in ['AUTH','READ_ONLY_PROOF','DONE']
        assert type(row['codes']) is dict and set(row['codes'])<={'mongo'}
        assert all(type(value) is int for value in row['codes'].values())
        diagnostic.update(stage=row['stage'],code='MONGO_PROOF_NOT_ACKNOWLEDGED')
        assert row['ok'] is True and row['stage']=='DONE' and process_exit==0
        value=row['result']
        keys={'targetRows','foreignDigest','deletedReceipt'} if case_id=='ATTENDANCE' else {'history','intents','audiences','pushSubscriptions'}
        assert type(value) is dict and set(value)==keys
        for key,item in value.items():
            if key=='foreignDigest':
                assert type(item) is str and re.fullmatch('[0-9a-f]{64}',item)
            else:
                assert type(item) is int and item>=0
        return value,None
    except Exception:
        return None,diagnostic


def mongo_read(case_id,database,user,password,group,lesson):
    result=runtime.docker('exec','-i',runtime.PROJECT+'-mongo-attendance-1','mongosh',
                          '--norc','--quiet','--host','127.0.0.1','--file','/dev/stdin',
                          input=mongo_javascript(case_id,database,user,password,group,lesson),expected=None)
    value,diagnostic=mongo_result(result.stdout,result.returncode,case_id)
    if diagnostic is not None:
        print(json.dumps(diagnostic),file=sys.stderr)
        raise RuntimeError('Scoped Mongo proof HOLD; child values withheld; no retry') from None
    return value

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
 # Same scoped app principals and unchanged before/after counts/digests.
 uri=(P/'SPRING_DATA_MONGODB_URI').read_text();password=re.search(r':([^:@]+)@',uri).group(1)
 mongo=mongo_read('ATTENDANCE','attendance_db','rct_group_attendance',password,g,lesson)
 notification_password=re.search(r':([^:@]+)@',(P/'NOTIFICATION_SPRING_DATA_MONGODB_URI').read_text()).group(1)
 notification=mongo_read('NOTIFICATION','notification_db','rct_group_notification',notification_password,g,lesson)
 return {'auth':json.loads(auth),'academic':json.loads(academic),'schedule':json.loads(schedule),'attendance':mongo,'notification':notification,'groupId':g,'lessonId':lesson}

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
